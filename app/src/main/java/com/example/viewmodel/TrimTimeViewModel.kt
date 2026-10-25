package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PinVerificationResult
import com.example.data.TrimTimeRepository
import com.example.model.AppAuthState
import com.example.model.Booking
import com.example.model.Salon
import com.example.model.SalonService
import com.example.model.UserProfile
import com.example.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CustomerNavTab {
    SALONS,
    MY_BOOKINGS,
    PROFILE
}

enum class AuthMode {
    NONE,
    CUSTOMER_AUTH,
    PARTNER_AUTH
}

class TrimTimeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TrimTimeRepository.getInstance(application)

    // Auth State and User Roles
    val authState: StateFlow<AppAuthState> = repository.authState
    val currentRole: StateFlow<UserRole> = repository.currentRole
    val currentUserProfile: StateFlow<UserProfile?> = repository.currentUserProfile
    val isCloudSynced: StateFlow<Boolean> = repository.isCloudSynced
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val authError: StateFlow<String?> = repository.authError
    val showSha1FallbackDialog: StateFlow<Boolean> = repository.showSha1FallbackDialog

    // Real-Time Salons & Bookings from Firestore
    val allSalons = repository.salons
    val allBookings = repository.bookings

    // Customer Navigation (Bottom Bar)
    private val _currentNavTab = MutableStateFlow(CustomerNavTab.SALONS)
    val currentNavTab: StateFlow<CustomerNavTab> = _currentNavTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Filtered Salons for Customer View
    val filteredSalons: StateFlow<List<Salon>> = combine(
        allSalons,
        _searchQuery,
        _selectedCategory
    ) { salons, query, category ->
        salons.filter { salon ->
            val matchesQuery = query.isBlank() ||
                    salon.name.contains(query, ignoreCase = true) ||
                    salon.location.contains(query, ignoreCase = true) ||
                    salon.services.any { it.name.contains(query, ignoreCase = true) }

            val matchesCategory = category == "All" ||
                    salon.services.any { it.category.equals(category, ignoreCase = true) }

            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Salon for Detail Screen
    private val _selectedSalon = MutableStateFlow<Salon?>(null)
    val selectedSalon: StateFlow<Salon?> = _selectedSalon.asStateFlow()

    // Customer Booking Flow
    private val _serviceToBook = MutableStateFlow<SalonService?>(null)
    val serviceToBook: StateFlow<SalonService?> = _serviceToBook.asStateFlow()

    private val _selectedTimeSlot = MutableStateFlow("In 15 mins (⚡ Instant)")
    val selectedTimeSlot: StateFlow<String> = _selectedTimeSlot.asStateFlow()

    private val _newlyConfirmedBooking = MutableStateFlow<Booking?>(null)
    val newlyConfirmedBooking: StateFlow<Booking?> = _newlyConfirmedBooking.asStateFlow()

    // Auth Dialog state
    private val _authMode = MutableStateFlow(AuthMode.NONE)
    val authMode: StateFlow<AuthMode> = _authMode.asStateFlow()

    // Partner Dashboard State
    private val _partnerSelectedSalonId = MutableStateFlow<String?>(null)
    val partnerSelectedSalonId: StateFlow<String?> = _partnerSelectedSalonId.asStateFlow()

    private val _partnerBookingFilter = MutableStateFlow("All") // "All", "Pending", "Completed"
    val partnerBookingFilter: StateFlow<String> = _partnerBookingFilter.asStateFlow()

    // Partner Anti-Fake PIN Verification Modal State
    private val _bookingToVerify = MutableStateFlow<Booking?>(null)
    val bookingToVerify: StateFlow<Booking?> = _bookingToVerify.asStateFlow()

    private val _enteredPin = MutableStateFlow("")
    val enteredPin: StateFlow<String> = _enteredPin.asStateFlow()

    private val _pinErrorMessage = MutableStateFlow<String?>(null)
    val pinErrorMessage: StateFlow<String?> = _pinErrorMessage.asStateFlow()

    private val _verificationSuccess = MutableStateFlow(false)
    val verificationSuccess: StateFlow<Boolean> = _verificationSuccess.asStateFlow()

    // Feedback Toast/Snackbar
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun setNavTab(tab: CustomerNavTab) {
        _currentNavTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectSalon(salon: Salon?) {
        _selectedSalon.value = salon
        if (salon == null) {
            _serviceToBook.value = null
        }
    }

    fun setServiceToBook(service: SalonService?) {
        _serviceToBook.value = service
    }

    fun setSelectedTimeSlot(slot: String) {
        _selectedTimeSlot.value = slot
    }

    // Rule 1: Mandatory Phone Collection (No OTP)
    fun savePhoneNumber(phone: String, onSuccess: () -> Unit = {}) {
        repository.updateUserPhone(
            phone = phone,
            onSuccess = {
                _userMessage.value = "Contact number saved successfully."
                onSuccess()
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    // Rule 2: One Active Booking Rule
    val hasActivePendingBooking: StateFlow<Boolean> = combine(
        allBookings,
        currentUserProfile
    ) { bookings, profile ->
        val currentUid = profile?.id
        val currentPhone = profile?.phone
        val currentName = profile?.name
        bookings.any { booking ->
            (booking.status.equals("pending", ignoreCase = true) ||
                booking.status.equals("accepted", ignoreCase = true)) && (
                (!currentUid.isNullOrBlank() && booking.customerId == currentUid) ||
                (!currentPhone.isNullOrBlank() && booking.customerPhone == currentPhone) ||
                (!currentName.isNullOrBlank() && booking.customerName == currentName)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Rule 3: Strike / Auto-Block System (3 cancellations = blocked)
    val isUserSuspended: StateFlow<Boolean> = currentUserProfile.combine(allBookings) { profile, _ ->
        (profile?.cancellationCount ?: 0) >= 3 || (profile?.isSuspended == true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun confirmBooking(customerName: String, customerPhone: String) {
        val profile = currentUserProfile.value
        // Rule 3 Auto-Block check
        if (profile != null && (profile.cancellationCount >= 3 || profile.isSuspended)) {
            _userMessage.value = "Your account has been suspended for too many cancellations"
            return
        }

        // Rule 2 One Active Booking check
        if (hasActivePendingBooking.value) {
            _userMessage.value = "You already have an active booking. Please complete or cancel it first."
            return
        }

        val salon = _selectedSalon.value ?: return
        val service = _serviceToBook.value ?: return
        val slot = _selectedTimeSlot.value
        val custId = profile?.id ?: "guest_${System.currentTimeMillis()}"

        try {
            val newBooking = repository.createBooking(
                customerId = custId,
                customerName = customerName.ifBlank { profile?.name ?: "Customer" },
                customerPhone = customerPhone.ifBlank { profile?.phone ?: "+91 98765 43210" },
                salonId = salon.id,
                salonName = salon.name,
                service = service.name,
                servicePrice = service.price,
                timeSlot = slot,
                bookingDate = "Today"
            )

            _serviceToBook.value = null
            _newlyConfirmedBooking.value = newBooking
        } catch (e: Exception) {
            _userMessage.value = e.message
        }
    }

    fun dismissConfirmationDialog() {
        _newlyConfirmedBooking.value = null
    }

    // ==========================================
    // Real Firebase Google Sign-In & Role Switching
    // ==========================================

    fun signInWithGoogle(context: Context, onSuccess: () -> Unit = {}) {
        repository.signInWithGoogle(
            context = context,
            onSuccess = {
                _userMessage.value = "Welcome to TrimTime!"
                _currentNavTab.value = CustomerNavTab.SALONS
                onSuccess()
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    fun dismissSha1FallbackDialog() {
        repository.dismissSha1FallbackDialog()
    }

    /**
     * Direct Firestore Account Sign-In / Registration (Works on ANY APK without SHA-1 restrictions):
     */
    fun signInWithRealIdentity(
        name: String,
        email: String,
        phone: String = "",
        asPartner: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        repository.signInWithRealIdentity(
            name = name,
            email = email,
            phone = phone,
            asPartner = asPartner,
            onSuccess = {
                _userMessage.value = "Signed in as $name"
                if (asPartner) {
                    currentUserProfile.value?.salonId?.let { _partnerSelectedSalonId.value = it }
                } else {
                    _currentNavTab.value = CustomerNavTab.SALONS
                }
                onSuccess()
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    /**
     * Simple 1-Identifier Login for Existing Users (Mobile Number OR Email Address):
     */
    fun loginExistingUser(
        identifier: String,
        onAccountNotFound: (prefillEmail: String, prefillPhone: String) -> Unit = { _, _ -> },
        onSuccess: () -> Unit = {}
    ) {
        repository.loginExistingUser(
            identifier = identifier,
            onSuccess = { profile ->
                _userMessage.value = "Welcome back, ${profile.name}!"
                if (profile.role.equals("partner", ignoreCase = true)) {
                    profile.salonId?.let { _partnerSelectedSalonId.value = it }
                } else {
                    _currentNavTab.value = CustomerNavTab.SALONS
                }
                onSuccess()
            },
            onAccountNotFound = { prefillEmail, prefillPhone ->
                _userMessage.value = "Account not found. Switching to Sign Up..."
                onAccountNotFound(prefillEmail, prefillPhone)
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    /**
     * Developer Test Login:
     * Directly runs Firestore logic (check/create 'users' document) with dummy UID.
     */
    fun developerTestLogin(onSuccess: () -> Unit = {}) {
        repository.developerTestLogin(
            onSuccess = {
                _userMessage.value = "Logged in with Developer Test Account"
                _currentNavTab.value = CustomerNavTab.SALONS
                onSuccess()
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    fun openAuth(mode: AuthMode) {
        _authMode.value = mode
    }

    fun dismissAuth() {
        _authMode.value = AuthMode.NONE
    }

    fun switchToPartnerRole() {
        repository.switchToPartnerRole { salonId ->
            _partnerSelectedSalonId.value = salonId
        }
        _authMode.value = AuthMode.NONE
        _userMessage.value = "Switched to Partner / Salon Owner Mode"
    }

    fun switchToCustomerRole() {
        repository.switchToCustomerRole()
        _currentNavTab.value = CustomerNavTab.SALONS
        _userMessage.value = "Switched to Customer Mode"
    }

    fun logout() {
        repository.signOut()
        _currentNavTab.value = CustomerNavTab.SALONS
        _selectedSalon.value = null
        _serviceToBook.value = null
        _partnerSelectedSalonId.value = null
        _userMessage.value = "Logged out successfully"
    }

    // ==========================================
    // Partner Verification & Controls
    // ==========================================

    fun setPartnerSelectedSalonId(salonId: String) {
        _partnerSelectedSalonId.value = salonId
    }

    fun setPartnerBookingFilter(filter: String) {
        _partnerBookingFilter.value = filter
    }

    fun acceptBooking(bookingId: String) {
        if (repository.acceptBooking(bookingId)) {
            _userMessage.value = "Booking ACCEPTED! Customer notified in real time."
        }
    }

    fun rejectBooking(bookingId: String) {
        if (repository.rejectBooking(bookingId)) {
            _userMessage.value = "Booking REJECTED and removed from active queue."
        }
    }

    fun openPinVerificationDialog(booking: Booking) {
        _bookingToVerify.value = booking
        _enteredPin.value = ""
        _pinErrorMessage.value = null
        _verificationSuccess.value = false
    }

    fun setEnteredPin(pin: String) {
        if (pin.length <= 4 && pin.all { it.isDigit() }) {
            _enteredPin.value = pin
            _pinErrorMessage.value = null
        }
    }

    fun verifyAndCompleteService() {
        val booking = _bookingToVerify.value ?: return
        val pin = _enteredPin.value

        if (pin.length != 4) {
            _pinErrorMessage.value = "Please enter the complete 4-digit PIN."
            return
        }

        when (val result = repository.verifyAndCompleteBooking(booking.id, pin)) {
            is PinVerificationResult.Success -> {
                _verificationSuccess.value = true
                _pinErrorMessage.value = null
                _userMessage.value = "Service verified & completed! ₹${booking.servicePrice} collected."
                viewModelScope.launch {
                    kotlinx.coroutines.delay(1200)
                    _bookingToVerify.value = null
                    _verificationSuccess.value = false
                    _enteredPin.value = ""
                }
            }
            is PinVerificationResult.IncorrectPin -> {
                _pinErrorMessage.value = result.message
            }
            is PinVerificationResult.BookingNotFound -> {
                _pinErrorMessage.value = result.message
            }
            is PinVerificationResult.AlreadyCompleted -> {
                _pinErrorMessage.value = result.message
            }
        }
    }

    fun dismissPinVerificationDialog() {
        _bookingToVerify.value = null
        _enteredPin.value = ""
        _pinErrorMessage.value = null
        _verificationSuccess.value = false
    }

    fun cancelBooking(bookingId: String) {
        val success = repository.cancelBooking(bookingId)
        if (success) {
            val profile = currentUserProfile.value
            val strikes = profile?.cancellationCount ?: 0
            if (strikes >= 3 || profile?.isSuspended == true) {
                _userMessage.value = "Your account has been suspended for too many cancellations"
            } else {
                _userMessage.value = "Booking cancelled. (Cancellation $strikes/3 strikes)"
            }
        }
    }

    fun toggleSalonOpenStatus(salonId: String) {
        repository.toggleSalonOpenStatus(salonId)
    }

    fun setSalonOpenStatus(salonId: String, isOpen: Boolean) {
        repository.setSalonOpenStatus(salonId, isOpen)
        _userMessage.value = if (isOpen) "Shop is now OPEN for live bookings!" else "Shop is now CLOSED. Bookings paused."
    }

    fun setSalonBusyStatus(salonId: String, status: String) {
        repository.setSalonBusyStatus(salonId, status)
        _userMessage.value = if (status.equals("BUSY", ignoreCase = true)) {
            "Barber status set to BUSY (Cutting Hair ✂️)"
        } else {
            "Barber status set to FREE (Available Now 🟢)"
        }
    }

    fun updateSalonChairAvailability(
        salonId: String,
        currentStatus: String,
        availableChairs: Int,
        waitingTimeMinutes: Int
    ) {
        repository.updateSalonChairAvailability(
            salonId = salonId,
            currentStatus = currentStatus,
            availableChairs = availableChairs,
            waitingTimeMinutes = waitingTimeMinutes
        )
        _userMessage.value = "Live Chairs ($availableChairs) & Wait Time (${waitingTimeMinutes}m) synced to Firestore!"
    }

    fun addSalonService(
        salonId: String,
        name: String,
        category: String,
        price: Int,
        durationMinutes: Int,
        description: String = ""
    ) {
        repository.addSalonService(
            salonId = salonId,
            name = name,
            category = category,
            price = price,
            durationMinutes = durationMinutes,
            description = description,
            onSuccess = {
                _userMessage.value = "Added '$name' (₹$price • ${durationMinutes}m) to Firestore!"
            }
        )
    }

    fun editSalonService(
        salonId: String,
        updatedService: SalonService
    ) {
        repository.editSalonService(
            salonId = salonId,
            updatedService = updatedService,
            onSuccess = {
                _userMessage.value = "Updated '${updatedService.name}' (₹${updatedService.price} • ${updatedService.durationMinutes}m) in Firestore!"
            }
        )
    }

    fun deleteSalonService(
        salonId: String,
        serviceId: String,
        serviceName: String = "Service"
    ) {
        repository.deleteSalonService(
            salonId = salonId,
            serviceId = serviceId,
            onSuccess = {
                _userMessage.value = "Removed '$serviceName' from Firestore!"
            }
        )
    }

    fun saveSalonMenuPrices(
        salonId: String,
        haircutPrice: Int,
        shavingPrice: Int,
        facialPrice: Int,
        headMassagePrice: Int,
        onSuccess: () -> Unit = {}
    ) {
        val currentSalon = allSalons.value.find { it.id == salonId } ?: return
        val customServices = currentSalon.services.filterNot {
            it.id == "${salonId}_s1" ||
                it.id == "${salonId}_s2" ||
                it.id == "${salonId}_s3" ||
                it.id == "${salonId}_s4" ||
                it.name.equals("Haircut", ignoreCase = true) ||
                it.name.equals("Shaving / Beard", ignoreCase = true) ||
                it.name.equals("Facial", ignoreCase = true) ||
                it.name.equals("Head Massage", ignoreCase = true)
        }
        val updatedServices = listOf(
            SalonService(
                id = "${salonId}_s1",
                name = "Haircut",
                category = "Haircut",
                price = haircutPrice,
                durationMinutes = 20,
                description = "Standard haircut & styling",
                isPopular = true
            ),
            SalonService(
                id = "${salonId}_s2",
                name = "Shaving / Beard",
                category = "Beard",
                price = shavingPrice,
                durationMinutes = 15,
                description = "Clean shave & edge lineup",
                isPopular = true
            ),
            SalonService(
                id = "${salonId}_s3",
                name = "Facial",
                category = "Facial",
                price = facialPrice,
                durationMinutes = 25,
                description = "Deep cleanse & glow facial",
                isPopular = false
            ),
            SalonService(
                id = "${salonId}_s4",
                name = "Head Massage",
                category = "Massage",
                price = headMassagePrice,
                durationMinutes = 15,
                description = "Relaxing herbal oil head massage",
                isPopular = false
            )
        ) + customServices

        repository.updateSalonServices(
            salonId = salonId,
            updatedServices = updatedServices,
            onSuccess = {
                _userMessage.value = "Menu prices saved to profile & Firestore!"
                onSuccess()
            }
        )
    }

    fun saveSalonFullProfile(
        salonId: String,
        name: String,
        phone: String,
        tagline: String,
        shopAddress: String,
        landmark: String,
        latitude: Double,
        longitude: Double,
        availableChairs: Int? = null,
        onSuccess: () -> Unit = {}
    ) {
        repository.updateSalonFullProfile(
            salonId = salonId,
            name = name,
            phone = phone,
            tagline = tagline,
            shopAddress = shopAddress,
            landmark = landmark,
            latitude = latitude,
            longitude = longitude,
            availableChairs = availableChairs,
            onSuccess = {
                _userMessage.value = "Shop Details & GPS saved to Cloud Firestore!"
                onSuccess()
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    fun saveSalonPhoto(
        salonId: String,
        photoDataUrl: String,
        onSuccess: () -> Unit = {}
    ) {
        repository.updateSalonPhoto(
            salonId = salonId,
            photoDataUrl = photoDataUrl,
            onSuccess = {
                _userMessage.value = "Shop photo uploaded & synced to Firestore!"
                onSuccess()
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    fun saveSalonLocation(
        salonId: String,
        shopAddress: String,
        landmark: String,
        latitude: Double,
        longitude: Double,
        onSuccess: () -> Unit = {}
    ) {
        repository.updateSalonLocation(
            salonId = salonId,
            shopAddress = shopAddress,
            landmark = landmark,
            latitude = latitude,
            longitude = longitude,
            onSuccess = {
                _userMessage.value = "Shop location & GPS saved to Firestore!"
                onSuccess()
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
