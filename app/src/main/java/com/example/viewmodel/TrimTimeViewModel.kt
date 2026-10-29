package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PinVerificationResult
import com.example.data.TrimTimeRepository
import com.example.model.AppAuthState
import com.example.model.AppLanguage
import com.example.model.AppPreferences
import com.example.model.AppThemeMode
import com.example.model.Booking
import com.example.model.Salon
import com.example.model.SalonService
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.util.SecurityValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val showPartnerOnboarding: StateFlow<Boolean> = repository.showPartnerOnboarding
    val appPreferences: StateFlow<AppPreferences> = repository.appPreferences

    // Live Customer GPS Coordinates & Reverse-Geocoded Location Display
    val userLatitude: StateFlow<Double> = repository.userLatitude
    val userLongitude: StateFlow<Double> = repository.userLongitude
    val userLocationTitle: StateFlow<String> = repository.userLocationTitle
    val userLocationSubtitle: StateFlow<String> = repository.userLocationSubtitle
    val isLiveGpsActive: StateFlow<Boolean> = repository.isLiveGpsActive

    // Real-Time Salons, Bookings, Users & Support Tickets from Firestore
    val allSalons = repository.salons
    val allBookings = repository.bookings
    val allUsers = repository.allUsers
    val supportTickets = repository.supportTickets

    // Secret Super Admin Access State
    private val _showAdminDashboard = MutableStateFlow(false)
    val showAdminDashboard: StateFlow<Boolean> = _showAdminDashboard.asStateFlow()

    val isSuperAdmin: StateFlow<Boolean> = currentUserProfile.combine(allUsers) { profile, _ ->
        repository.isUserSuperAdmin(profile)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Whether the currently logged-in user already owns a registered salon in Firestore
    // (strictly currentUser.uid == salon.ownerId && isRegistrationCompleted && registrationFormSubmitted)
    val hasRegisteredPartnerSalon: StateFlow<Boolean> = combine(
        allSalons,
        currentUserProfile
    ) { salons, profile ->
        val uid = profile?.id?.trim().orEmpty()
        if (uid.isBlank()) {
            false
        } else {
            salons.any {
                !repository.isDemoSalon(it.id) &&
                    (it.ownerId == uid || it.partnerId == uid) &&
                    it.isRegistrationCompleted &&
                    it.registrationFormSubmitted
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Customer-isolated bookings: strictly filtered where booking.customerId == currentUser.uid
    val customerBookings: StateFlow<List<Booking>> = combine(
        repository.customerBookings,
        currentUserProfile
    ) { bookings, profile ->
        val uid = profile?.id?.trim().orEmpty()
        if (uid.isBlank()) {
            emptyList()
        } else {
            bookings.filter { it.customerId == uid }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Most recently COMPLETED booking for the logged-in customer (for "Quick Re-Book" banner)
    val latestCompletedBooking: StateFlow<Booking?> = combine(
        repository.latestCompletedBooking,
        customerBookings,
        currentUserProfile
    ) { repoLatestCompleted, bookings, profile ->
        val uid = profile?.id?.trim().orEmpty()
        if (uid.isBlank()) {
            null
        } else {
            val fromBookings = bookings
                .filter { it.customerId == uid && it.status.equals("COMPLETED", ignoreCase = true) }
                .maxByOrNull { it.completedAt ?: it.createdAt }
            val candidate = fromBookings ?: repoLatestCompleted?.takeIf {
                it.customerId == uid && it.status.equals("COMPLETED", ignoreCase = true)
            }
            candidate
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Customer Navigation (Bottom Bar)
    private val _currentNavTab = MutableStateFlow(CustomerNavTab.SALONS)
    val currentNavTab: StateFlow<CustomerNavTab> = _currentNavTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Filtered Salons for Customer View: dynamically calculates distance from live (userLatitude, userLongitude),
    // prioritizes real Firestore-registered salons at the top, and hides Demo Salons unless showDemoSalons is enabled!
    val filteredSalons: StateFlow<List<Salon>> = combine(
        combine(allSalons, _searchQuery, _selectedCategory) { s, q, c -> Triple(s, q, c) },
        combine(userLatitude, userLongitude, appPreferences) { lat, lng, prefs -> Triple(lat, lng, prefs) }
    ) { (salons, query, category), (uLat, uLng, prefs) ->
        val recalculated = repository.recalculateAndSortSalons(salons, uLat, uLng)
        recalculated.filter { salon ->
            val includeByDemoSetting = prefs.showDemoSalons || !repository.isDemoSalon(salon.id)

            val matchesQuery = query.isBlank() ||
                    salon.name.contains(query, ignoreCase = true) ||
                    salon.location.contains(query, ignoreCase = true) ||
                    salon.shopAddress.contains(query, ignoreCase = true) ||
                    salon.services.any { it.name.contains(query, ignoreCase = true) }

            val isNotRejectedOrSuspended = !salon.isSuspended &&
                    !salon.approvalStatus.equals("REJECTED", ignoreCase = true)

            val matchesCategory = category == "All" ||
                    salon.adminCategoryTag.equals(category, ignoreCase = true) ||
                    salon.services.any { it.category.equals(category, ignoreCase = true) }

            includeByDemoSetting && isNotRejectedOrSuspended && matchesQuery && matchesCategory
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

    private val _pinLockoutSeconds = MutableStateFlow(0)
    val pinLockoutSeconds: StateFlow<Int> = _pinLockoutSeconds.asStateFlow()
    private var pinLockoutTimerJob: Job? = null

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
        if (service != null) {
            val profile = currentUserProfile.value
            if (profile != null && profile.isEffectivelyBlocked) {
                _userMessage.value = if (profile.isBlocked) {
                    "Account Blocked: Your account has been blocked by Admin and cannot make new bookings."
                } else {
                    "Security Lockout: Your account is suspended due to 3 cancellation strikes."
                }
                return
            }
            val currentUid = profile?.id?.trim().orEmpty()
            val salon = _selectedSalon.value
            if (salon != null && (salon.isSuspended || salon.approvalStatus.equals("REJECTED", ignoreCase = true))) {
                _userMessage.value = "This salon is currently suspended or unavailable for bookings."
                return
            }
            if (currentUid.isNotBlank() && salon != null && (salon.ownerId == currentUid || salon.partnerId == currentUid)) {
                _userMessage.value = "This is your own registered salon. Manage it in Partner Mode."
                return
            }
        }
        _serviceToBook.value = service
    }

    fun setSelectedTimeSlot(slot: String) {
        _selectedTimeSlot.value = slot
    }

    // Rule 1: Mandatory Phone Collection with 2-Step OTP Verification
    fun savePhoneNumber(
        phone: String,
        isPhoneVerified: Boolean = true,
        onSuccess: () -> Unit = {}
    ) {
        repository.updateUserPhone(
            phone = phone,
            onSuccess = {
                _userMessage.value = "✓ Mobile number verified & saved to your profile!"
                onSuccess()
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    fun generateFourDigitOtp(): String = repository.generateFourDigitOtp()

    fun requestPhoneVerificationOtp(
        rawPhone: String,
        excludeUserId: String? = null,
        onOtpGenerated: (cleanTenDigits: String, otpCode: String) -> Unit,
        onError: (String) -> Unit = { _userMessage.value = it }
    ) {
        repository.requestPhoneVerificationOtp(
            phone = rawPhone,
            excludeUserId = excludeUserId,
            onOtpGenerated = onOtpGenerated,
            onError = onError
        )
    }

    fun requestForgotPinOtp(
        rawIdentifier: String,
        onOtpGenerated: (cleanTenDigits: String, otpCode: String, userId: String) -> Unit,
        onError: (String) -> Unit = { _userMessage.value = it }
    ) {
        repository.requestForgotPinOtp(
            identifier = rawIdentifier,
            onOtpReady = { matchedUid, phoneTenDigits, otpCode ->
                onOtpGenerated(phoneTenDigits, otpCode, matchedUid)
            },
            onError = onError
        )
    }

    fun resetLoginPinAndAuthenticate(
        rawIdentifier: String,
        newPin: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = { _userMessage.value = it }
    ) {
        repository.requestForgotPinOtp(
            identifier = rawIdentifier,
            onOtpReady = { matchedUid, _, _ ->
                repository.resetLoginPinAndAuthenticate(
                    uid = matchedUid,
                    newLoginPin = newPin,
                    onSuccess = { profile ->
                        _userMessage.value = "✓ Password updated! Welcome back, ${profile.name}."
                        _currentNavTab.value = CustomerNavTab.SALONS
                        onSuccess()
                    },
                    onError = onError
                )
            },
            onError = onError
        )
    }

    // Rule 2: One Active Booking Rule (strictly scoped to currentUser.uid; status in PENDING, ACCEPTED, IN_PROGRESS)
    val hasActivePendingBooking: StateFlow<Boolean> = combine(
        customerBookings,
        currentUserProfile
    ) { bookings, profile ->
        val currentUid = profile?.id?.trim().orEmpty()
        currentUid.isNotBlank() && bookings.any { booking ->
            booking.customerId == currentUid && SecurityValidator.isBookingActiveStatus(booking.status)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Rule 3: Admin Block & 3-Strike Anti-Spam Rule (isBlocked || isSuspended || cancellationStrikes >= 3 = blocked)
    val isUserSuspended: StateFlow<Boolean> = currentUserProfile.combine(customerBookings) { profile, _ ->
        profile?.isEffectivelyBlocked == true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun confirmBooking(customerName: String, customerPhone: String) {
        val profile = currentUserProfile.value
        val custId = profile?.id?.trim().orEmpty()
        if (custId.isBlank()) {
            _userMessage.value = "Please log in to confirm your booking."
            return
        }

        // Rule 3 Auto-Block / Admin-Block check
        if (profile != null && profile.isEffectivelyBlocked) {
            _userMessage.value = if (profile.isBlocked) {
                "Account Blocked: Your account has been blocked by Admin and cannot make new bookings."
            } else {
                "Security Lockout: Your account is blocked from new bookings due to 3 cancellation strikes."
            }
            return
        }

        val salon = _selectedSalon.value ?: return

        // Rule 4: Prevent Self-Booking (Owner Booking Their Own Salon)
        if (custId.isNotBlank() && (salon.ownerId == custId || salon.partnerId == custId)) {
            _userMessage.value = "This is your own registered salon. Manage it in Partner Mode."
            return
        }

        // Rule 2 One Active Booking check (PENDING, ACCEPTED, IN_PROGRESS)
        if (hasActivePendingBooking.value) {
            _userMessage.value = "You already have an active booking (Pending/Accepted/In Progress). Please complete or cancel it first."
            return
        }

        val service = _serviceToBook.value ?: return
        val slot = _selectedTimeSlot.value

        try {
            val newBooking = repository.createBooking(
                customerId = custId,
                customerName = customerName.ifBlank { profile?.name ?: "Customer" },
                customerPhone = customerPhone.ifBlank { profile?.phone ?: "" },
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
     * Direct Account Sign-In / Registration with 2-Step Phone OTP & Password:
     */
    fun signInWithRealIdentity(
        name: String,
        email: String,
        phone: String = "",
        asPartner: Boolean = false,
        loginPin: String = "",
        isPhoneVerified: Boolean = true,
        onSuccess: () -> Unit = {}
    ) {
        repository.signInWithRealIdentity(
            name = name,
            email = email,
            phone = phone,
            asPartner = asPartner,
            loginPin = loginPin,
            isPhoneVerified = isPhoneVerified,
            onSuccess = {
                _userMessage.value = "✓ Mobile verified & account created for $name"
                _currentNavTab.value = CustomerNavTab.SALONS
                onSuccess()
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    /**
     * Login for Existing Users (Mobile Number OR Email Address + Password):
     */
    fun loginExistingUser(
        identifier: String,
        loginPin: String = "",
        onAccountNotFound: (prefillEmail: String, prefillPhone: String) -> Unit = { _, _ -> },
        onSuccess: () -> Unit = {}
    ) {
        repository.loginExistingUser(
            identifier = identifier,
            loginPin = loginPin,
            onSuccess = { profile ->
                _userMessage.value = "Welcome back, ${profile.name}!"
                _currentNavTab.value = CustomerNavTab.SALONS
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

    fun developerTestLogin(onSuccess: () -> Unit = {}) {
        repository.developerTestLogin(
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

    fun openAuth(mode: AuthMode) {
        _authMode.value = mode
    }

    fun dismissAuth() {
        _authMode.value = AuthMode.NONE
    }

    fun dismissPartnerOnboarding() {
        repository.dismissPartnerOnboarding()
    }

    fun openPartnerRegistrationForm() {
        _authMode.value = AuthMode.NONE
        _selectedSalon.value = null
        _serviceToBook.value = null
        repository.openPartnerRegistrationForm()
    }

    fun switchToPartnerRole() {
        _authMode.value = AuthMode.NONE
        _selectedSalon.value = null
        _serviceToBook.value = null
        repository.switchToPartnerRole(
            onSalonReady = { salonId ->
                _partnerSelectedSalonId.value = salonId
            },
            onRequireRegistration = {
                _userMessage.value = "Please complete the Salon Partner Registration Form to unlock Partner Mode."
            }
        )
    }

    fun resetPartnerSalonRegistration(salonId: String? = null) {
        _partnerSelectedSalonId.value = null
        repository.resetOrDeletePartnerSalon(
            salonId = salonId,
            onSuccess = {
                _userMessage.value = "Salon registration removed. Complete the registration form to register a new salon."
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    fun registerPartnerSalon(
        salonName: String,
        ownerName: String,
        businessPhone: String,
        shopAddress: String,
        cityArea: String,
        pincode: String,
        landmark: String,
        shopCategory: String,
        availableChairs: Int,
        openingTime: String,
        closingTime: String,
        initialServices: List<SalonService>,
        latitude: Double,
        longitude: Double,
        isPhoneVerified: Boolean = true,
        onSuccess: () -> Unit = {}
    ) {
        repository.registerPartnerSalon(
            salonName = salonName,
            ownerName = ownerName,
            businessPhone = businessPhone,
            shopAddress = shopAddress,
            cityArea = cityArea,
            pincode = pincode,
            landmark = landmark,
            shopCategory = shopCategory,
            availableChairs = availableChairs,
            openingTime = openingTime,
            closingTime = closingTime,
            initialServices = initialServices,
            latitude = latitude,
            longitude = longitude,
            onSuccess = { salonId ->
                _partnerSelectedSalonId.value = salonId
                _userMessage.value = "🎉 '$salonName' registered! Welcome to Partner Mode."
                onSuccess()
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    fun switchToCustomerRole() {
        repository.switchToCustomerRole()
        _currentNavTab.value = CustomerNavTab.SALONS
    }

    // ==========================================
    // Customer App Settings & Personal Profile Actions
    // ==========================================

    fun setThemeMode(mode: AppThemeMode) {
        repository.setThemeMode(mode)
        _userMessage.value = "Appearance set to ${mode.label}"
    }

    fun setLanguage(language: AppLanguage) {
        repository.setLanguage(language)
        _userMessage.value = if (language == AppLanguage.HINDI_HINGLISH) {
            "Language switched to Hindi / Hinglish"
        } else {
            "Language switched to English"
        }
    }

    fun setNotificationPreferences(
        bookingStatusAlerts: Boolean,
        queueTurnReminders: Boolean,
        salonOffersDiscounts: Boolean
    ) {
        repository.setNotificationPreferences(
            bookingStatusAlerts = bookingStatusAlerts,
            queueTurnReminders = queueTurnReminders,
            salonOffersDiscounts = salonOffersDiscounts
        )
    }

    fun setShowDemoSalons(show: Boolean) {
        repository.setShowDemoSalons(false)
    }

    fun updatePersonalProfile(
        name: String,
        phone: String,
        defaultCity: String,
        isPhoneVerified: Boolean = true,
        onSuccess: () -> Unit = {}
    ) {
        repository.updatePersonalProfile(
            name = name,
            phone = phone,
            defaultCity = defaultCity,
            onSuccess = {
                viewModelScope.launch {
                    val geo = com.example.ui.components.forwardGeocodeLocation(getApplication(), defaultCity)
                    if (geo != null) {
                        repository.updateUserGpsLocation(
                            latitude = geo.first,
                            longitude = geo.second,
                            title = defaultCity,
                            subtitle = geo.third,
                            isLiveGps = false
                        )
                    }
                }
                _userMessage.value = "✓ Personal profile updated!"
                onSuccess()
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    /**
     * Called when Customer Mode detects live GPS coordinates (automatically on launch or via "Use My Current GPS Location").
     * Immediately updates userLatitude & userLongitude, recalculates all salon distances, and reverse-geocodes the town/road name.
     */
    fun onLiveGpsDetected(
        latitude: Double,
        longitude: Double,
        showConfirmationToast: Boolean = false
    ) {
        if (latitude == 0.0 || longitude == 0.0) return
        val coordLabel = String.format(java.util.Locale.US, "%.4f, %.4f", latitude, longitude)
        repository.updateUserGpsLocation(
            latitude = latitude,
            longitude = longitude,
            subtitle = "Live GPS ($coordLabel) • Calculating nearest salons...",
            isLiveGps = true
        )
        viewModelScope.launch {
            val (resolvedTitle, resolvedSubtitle) = com.example.ui.components.reverseGeocodeLocation(
                context = getApplication(),
                latitude = latitude,
                longitude = longitude
            )
            repository.updateUserGpsLocation(
                latitude = latitude,
                longitude = longitude,
                title = resolvedTitle,
                subtitle = resolvedSubtitle,
                isLiveGps = true
            )
            if (showConfirmationToast) {
                _userMessage.value = "📍 Live GPS location set to $resolvedTitle"
            }
        }
    }

    /**
     * Called when the customer manually enters a City/Area or picks a custom pin on the OpenStreetMap in the Location Sheet.
     */
    fun updateCustomerLocationManually(
        cityOrAreaInput: String,
        pickedLatitude: Double? = null,
        pickedLongitude: Double? = null
    ) {
        val cleanInput = cityOrAreaInput.trim()
        viewModelScope.launch {
            if (pickedLatitude != null && pickedLongitude != null && pickedLatitude != 0.0 && pickedLongitude != 0.0) {
                val (revTitle, revSub) = com.example.ui.components.reverseGeocodeLocation(
                    context = getApplication(),
                    latitude = pickedLatitude,
                    longitude = pickedLongitude
                )
                val finalTitle = cleanInput.ifBlank { revTitle }
                repository.updateUserGpsLocation(
                    latitude = pickedLatitude,
                    longitude = pickedLongitude,
                    title = finalTitle,
                    subtitle = revSub,
                    isLiveGps = true
                )
                _userMessage.value = "📍 Location updated to $finalTitle"
            } else if (cleanInput.isNotBlank()) {
                val geo = com.example.ui.components.forwardGeocodeLocation(getApplication(), cleanInput)
                if (geo != null) {
                    repository.updateUserGpsLocation(
                        latitude = geo.first,
                        longitude = geo.second,
                        title = cleanInput,
                        subtitle = geo.third,
                        isLiveGps = false
                    )
                    _userMessage.value = "📍 Location set to $cleanInput"
                } else {
                    repository.updateUserGpsLocation(
                        latitude = userLatitude.value,
                        longitude = userLongitude.value,
                        title = cleanInput,
                        subtitle = "$cleanInput • Custom Location",
                        isLiveGps = false
                    )
                    _userMessage.value = "📍 Location set to $cleanInput"
                }
            }
        }
    }

    fun clearAppCache() {
        _searchQuery.value = ""
        _selectedCategory.value = "All"
        repository.clearAppCache { msg ->
            _userMessage.value = msg
        }
    }

    fun logout() {
        _showAdminDashboard.value = false
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

    private fun startPinLockoutCountdown(bookingId: String, initialSeconds: Int) {
        pinLockoutTimerJob?.cancel()
        _pinLockoutSeconds.value = initialSeconds
        if (initialSeconds <= 0) return
        pinLockoutTimerJob = viewModelScope.launch {
            while (true) {
                val remaining = repository.getPinLockoutRemainingSeconds(bookingId)
                _pinLockoutSeconds.value = remaining
                if (remaining <= 0) {
                    _pinErrorMessage.value = null
                    break
                } else {
                    _pinErrorMessage.value = "Security Lockout: Too many wrong PIN attempts (5/5). Please wait ${remaining}s before trying again."
                }
                delay(1000L)
            }
        }
    }

    fun openPinVerificationDialog(booking: Booking) {
        _bookingToVerify.value = booking
        _enteredPin.value = ""
        _verificationSuccess.value = false
        val remainingLockout = repository.getPinLockoutRemainingSeconds(booking.id)
        if (remainingLockout > 0) {
            startPinLockoutCountdown(booking.id, remainingLockout)
        } else {
            pinLockoutTimerJob?.cancel()
            _pinLockoutSeconds.value = 0
            _pinErrorMessage.value = null
        }
    }

    fun setEnteredPin(pin: String) {
        if (_pinLockoutSeconds.value > 0) return
        if (pin.length <= 4 && pin.all { it.isDigit() }) {
            _enteredPin.value = pin
            _pinErrorMessage.value = null
        }
    }

    fun verifyAndCompleteService() {
        val booking = _bookingToVerify.value ?: return
        if (_pinLockoutSeconds.value > 0) {
            _pinErrorMessage.value = "Security Lockout: Please wait ${_pinLockoutSeconds.value}s before trying again."
            return
        }
        val pin = _enteredPin.value

        if (pin.length != 4) {
            _pinErrorMessage.value = "Please enter the complete 4-digit PIN."
            return
        }

        when (val result = repository.verifyAndCompleteBooking(booking.id, pin)) {
            is PinVerificationResult.Success -> {
                pinLockoutTimerJob?.cancel()
                _pinLockoutSeconds.value = 0
                _verificationSuccess.value = true
                _pinErrorMessage.value = null
                _userMessage.value = "Service verified & completed! ₹${booking.servicePrice} collected."
                viewModelScope.launch {
                    delay(1200)
                    _bookingToVerify.value = null
                    _verificationSuccess.value = false
                    _enteredPin.value = ""
                }
            }
            is PinVerificationResult.LockedOut -> {
                _enteredPin.value = ""
                _pinErrorMessage.value = result.message
                startPinLockoutCountdown(booking.id, result.remainingSeconds)
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
        pinLockoutTimerJob?.cancel()
        _pinLockoutSeconds.value = 0
        _bookingToVerify.value = null
        _enteredPin.value = ""
        _pinErrorMessage.value = null
        _verificationSuccess.value = false
    }

    fun cancelBooking(bookingId: String) {
        val success = repository.cancelBooking(bookingId)
        if (success) {
            val profile = currentUserProfile.value
            val strikes = profile?.effectiveCancellationStrikes ?: 0
            if (strikes >= 3 || profile?.isSuspended == true) {
                _userMessage.value = "Security Lockout: Your account has been suspended for 3 cancellation strikes."
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
        _userMessage.value = "Live Chairs ($availableChairs) & Wait Time (${waitingTimeMinutes}m) updated!"
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
                _userMessage.value = "Added '${SecurityValidator.sanitizeName(name)}' (₹$price • ${durationMinutes}m) to menu!"
            },
            onError = { err ->
                _userMessage.value = err
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
                _userMessage.value = "Updated '${SecurityValidator.sanitizeName(updatedService.name)}' (₹${updatedService.price} • ${updatedService.durationMinutes}m)!"
            },
            onError = { err ->
                _userMessage.value = err
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
                _userMessage.value = "Removed '$serviceName' from menu!"
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
        val allPrices = listOf(haircutPrice, shavingPrice, facialPrice, headMassagePrice)
        val invalidPrice = allPrices.firstOrNull { !SecurityValidator.isValidServicePrice(it) }
        if (invalidPrice != null) {
            _userMessage.value = SecurityValidator.validateServicePriceError(invalidPrice)
                ?: "Service prices must be between ₹10 and ₹25,000."
            return
        }
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
                _userMessage.value = "Service prices saved!"
                onSuccess()
            },
            onError = { err ->
                _userMessage.value = err
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
                _userMessage.value = "✓ Shop details & location saved!"
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
                _userMessage.value = "✓ Shop cover photo updated!"
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
                _userMessage.value = "✓ Shop location saved!"
                onSuccess()
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    fun submitFeedback(
        rating: Int,
        category: String,
        message: String,
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        repository.submitFeedback(
            rating = rating,
            category = category,
            message = message,
            onSuccess = {
                val msg = "Thank you! Your $rating-star feedback ($category) has been submitted."
                _userMessage.value = msg
                onDone(true, msg)
            },
            onError = { err ->
                _userMessage.value = err
                onDone(false, err)
            }
        )
    }

    // ==========================================
    // Super Admin Dashboard Actions
    // ==========================================

    fun openAdminDashboard() {
        if (isSuperAdmin.value || repository.isUserSuperAdmin()) {
            _showAdminDashboard.value = true
        } else {
            _userMessage.value = "Access Denied: Super Admin privileges required."
        }
    }

    fun closeAdminDashboard() {
        _showAdminDashboard.value = false
    }

    fun enableSuperAdminAccess() {
        repository.enableSuperAdminForCurrentUser(
            onSuccess = {
                _userMessage.value = "👑 Super Admin privileges activated for your account!"
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    fun updateSalonApproval(
        salonId: String,
        approvalStatus: String,
        isSuspended: Boolean = false
    ) {
        repository.updateSalonApproval(
            salonId = salonId,
            approvalStatus = approvalStatus,
            isSuspended = isSuspended,
            onSuccess = {
                val actionLabel = when {
                    isSuspended -> "SUSPENDED"
                    approvalStatus.equals("APPROVED", ignoreCase = true) -> "APPROVED"
                    approvalStatus.equals("REJECTED", ignoreCase = true) -> "REJECTED"
                    else -> approvalStatus
                }
                _userMessage.value = "✓ Salon status updated to $actionLabel"
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    fun updateSalonCategory(
        salonId: String,
        categoryTag: String
    ) {
        repository.updateSalonCategory(
            salonId = salonId,
            categoryTag = categoryTag,
            onSuccess = {
                _userMessage.value = "✓ Salon tagged as '$categoryTag'"
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    fun updateUserBlockStatus(
        userId: String,
        isBlocked: Boolean
    ) {
        repository.updateUserBlockStatus(
            userId = userId,
            isBlocked = isBlocked,
            onSuccess = {
                _userMessage.value = if (isBlocked) {
                    "🚫 User blocked from making new bookings."
                } else {
                    "✓ User unblocked & cancellation strikes reset."
                }
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    fun resolveSupportTicket(
        ticketId: String,
        isResolved: Boolean = true
    ) {
        repository.resolveSupportTicket(
            ticketId = ticketId,
            isResolved = isResolved,
            onSuccess = {
                _userMessage.value = if (isResolved) {
                    "✓ Support ticket marked as Resolved."
                } else {
                    "Support ticket reopened."
                }
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
