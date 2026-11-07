package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
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
import com.example.ui.auth.extractRawExceptionMessage
import com.example.ui.auth.setAutoSelectEnabled
import com.example.ui.auth.setFilterByAuthorizedAccounts
import com.example.util.SecurityValidator
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

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
    val showPartnerOnboarding: StateFlow<Boolean> = repository.showPartnerOnboarding
    val appPreferences: StateFlow<AppPreferences> = repository.appPreferences
    val isGlobalRushModeEnabled: StateFlow<Boolean> = repository.isGlobalRushModeEnabled

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
        profile?.isWhitelistedAdminOrDev == true
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
    // sorts nearest salons at the very top, and when searching places exact salon name matches first followed by recommended nearby salons!
    val filteredSalons: StateFlow<List<Salon>> = combine(
        combine(allSalons, _searchQuery, _selectedCategory) { s, q, c -> Triple(s, q, c) },
        combine(userLatitude, userLongitude, appPreferences) { lat, lng, prefs -> Triple(lat, lng, prefs) }
    ) { (salons, query, category), (uLat, uLng, prefs) ->
        val recalculated = repository.recalculateAndSortSalons(salons, uLat, uLng)
        val trimmedQuery = query.trim()

        val eligibleSalons = recalculated.filter { salon ->
            val includeByDemoSetting = prefs.showDemoSalons || !repository.isDemoSalon(salon.id)
            val isNotRejectedOrSuspended = !salon.isSuspended &&
                !salon.approvalStatus.equals("REJECTED", ignoreCase = true)
            val matchesCategory = category == "All" ||
                salon.adminCategoryTag.equals(category, ignoreCase = true) ||
                salon.salonCategory.equals(category, ignoreCase = true) ||
                salon.services.any { it.category.equals(category, ignoreCase = true) }

            includeByDemoSetting && isNotRejectedOrSuspended && matchesCategory
        }

        if (trimmedQuery.isBlank()) {
            eligibleSalons
        } else {
            eligibleSalons.sortedWith(
                compareBy<Salon> { salon ->
                    val cleanName = salon.name.trim()
                    when {
                        cleanName.equals(trimmedQuery, ignoreCase = true) -> 0
                        cleanName.startsWith(trimmedQuery, ignoreCase = true) -> 1
                        cleanName.contains(trimmedQuery, ignoreCase = true) -> 2
                        salon.location.contains(trimmedQuery, ignoreCase = true) ||
                            salon.shopAddress.contains(trimmedQuery, ignoreCase = true) ||
                            salon.landmark.contains(trimmedQuery, ignoreCase = true) ||
                            salon.pincode.contains(trimmedQuery, ignoreCase = true) ||
                            salon.adminCategoryTag.contains(trimmedQuery, ignoreCase = true) ||
                            salon.salonCategory.contains(trimmedQuery, ignoreCase = true) ||
                            salon.shopCategory.contains(trimmedQuery, ignoreCase = true) ||
                            salon.tagline.contains(trimmedQuery, ignoreCase = true) ||
                            ((trimmedQuery.equals("ac", ignoreCase = true) || trimmedQuery.equals("a/c", ignoreCase = true)) && salon.isAcSalon) ||
                            salon.services.any {
                                it.name.contains(trimmedQuery, ignoreCase = true) ||
                                    it.serviceName.contains(trimmedQuery, ignoreCase = true) ||
                                    it.category.contains(trimmedQuery, ignoreCase = true)
                            } -> 3
                        else -> 4 // Recommended available & nearby salons
                    }
                }.thenBy { salon ->
                    val sLat = salon.latitude
                    val sLng = salon.longitude
                    if (sLat != null && sLng != null && sLat != 0.0 && sLng != 0.0) {
                        com.example.data.TrimTimeRepository.calculateRawDistanceKm(uLat, uLng, sLat, sLng)
                    } else {
                        val parsed = salon.distance.replace("km", "", ignoreCase = true).trim().toDoubleOrNull()
                        if (parsed != null) parsed + 5000.0 else 99999.0
                    }
                }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Customer's Favorite Salon IDs / Partner IDs synced with Firestore `users/{uid}.favoriteSalons`
    val favoriteSalonIds: StateFlow<Set<String>> = combine(
        currentUserProfile,
        allSalons
    ) { profile, _ ->
        profile?.favoriteSalons?.toSet() ?: emptySet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

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

    private val _showConfirmationDialog = MutableStateFlow(false)
    val showConfirmationDialog: StateFlow<Boolean> = _showConfirmationDialog.asStateFlow()

    private val _showSalonClosedDialog = MutableStateFlow(false)
    val showSalonClosedDialog: StateFlow<Boolean> = _showSalonClosedDialog.asStateFlow()

    fun dismissSalonClosedDialog() {
        _showSalonClosedDialog.value = false
    }

    fun triggerSalonClosedDialog() {
        _showSalonClosedDialog.value = true
    }

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
        if (tab == CustomerNavTab.PROFILE) {
            repository.refreshCurrentUserProfile()
        }
    }

    fun refreshCurrentUserProfile() {
        repository.refreshCurrentUserProfile()
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

    fun toggleFavoriteSalon(salon: Salon) {
        val partnerOrOwnerId = salon.partnerId.ifBlank { salon.ownerId }
        repository.toggleFavoriteSalon(
            salonId = salon.id,
            partnerId = partnerOrOwnerId,
            onResult = { isNowFavorite ->
                _userMessage.value = if (isNowFavorite) {
                    "❤️ Added '${salon.name}' to My Favorites"
                } else {
                    "Removed '${salon.name}' from My Favorites"
                }
            }
        )
    }

    fun setServiceToBook(service: SalonService?) {
        if (service != null) {
            val profile = currentUserProfile.value
            val isImmuneAdmin = profile?.isWhitelistedAdminOrDev == true
            if (profile != null && !isImmuneAdmin && profile.isEffectivelyBlocked) {
                _userMessage.value = "Account Blocked: Your account has been blocked by Admin and cannot make new bookings."
                return
            }
            val currentUid = profile?.id?.trim().orEmpty()
            val selected = _selectedSalon.value
            val salon = if (selected != null) {
                allSalons.value.find { it.id == selected.id } ?: selected
            } else {
                null
            }
            if (salon != null && (!salon.isOpen || salon.currentStatus.equals("CLOSED", ignoreCase = true))) {
                _showSalonClosedDialog.value = true
                return
            }
            if (salon != null && (salon.isSuspended || salon.approvalStatus.equals("REJECTED", ignoreCase = true))) {
                _userMessage.value = "This salon is currently suspended or unavailable for bookings."
                return
            }
            if (!isImmuneAdmin && currentUid.isNotBlank() && salon != null && (salon.ownerId == currentUid || salon.partnerId == currentUid)) {
                _userMessage.value = "This is your own registered salon. Manage it in Partner Mode."
                return
            }
            if (salon != null) {
                val activeQueueCount = allBookings.value.count {
                    (it.salonId == salon.id || (salon.partnerId.isNotBlank() && it.partnerId == salon.partnerId)) &&
                        SecurityValidator.isBookingActiveStatus(it.status)
                }
                val isRushActive = Booking.isRushModeActive(isGlobalRushModeEnabled.value, activeQueueCount)
                val durationMins = if (service.duration > 0) service.duration else if (service.durationMinutes > 0) service.durationMinutes else 20
                if (isRushActive) {
                    val waitMins = Booking.calculateSystemWaitMinutes(activeQueueCount, durationMins)
                    val etaTime = Booking.calculateFormattedEtaClockTime(activeQueueCount, durationMins)
                    _selectedTimeSlot.value = if (activeQueueCount > 0) {
                        "Live ETA: $etaTime (~${waitMins}m)"
                    } else {
                        "Live ETA: $etaTime (⚡ Instant)"
                    }
                } else {
                    _selectedTimeSlot.value = if (salon.isBusy) {
                        "Waitlist: Next in Line (~20m)"
                    } else {
                        "In 15 mins (⚡ Instant)"
                    }
                }
            }
        }
        _serviceToBook.value = service
    }

    fun setSelectedTimeSlot(slot: String) {
        _selectedTimeSlot.value = slot
    }

    // Rule 1: Mandatory Phone Collection (Post-Login)
    fun savePhoneNumber(
        phone: String,
        isPhoneVerified: Boolean = true,
        onSuccess: () -> Unit = {}
    ) {
        repository.updateUserPhone(
            phone = phone,
            isVerified = isPhoneVerified,
            onSuccess = {
                _userMessage.value = "✓ Mobile number saved to your profile!"
                onSuccess()
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    // Consecutive bookings enabled for Dynamic Rush Mode queue testing (3+ active bookings)
    val hasActivePendingBooking: StateFlow<Boolean> = combine(
        customerBookings,
        currentUserProfile
    ) { _, _ ->
        false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Rule 3: Admin Block & Anti-Spam Whitelist (Admin/SuperAdmin/Developer immune; automatic 3-strike suspension disabled)
    val isUserSuspended: StateFlow<Boolean> = currentUserProfile.combine(customerBookings) { profile, _ ->
        if (profile?.isWhitelistedAdminOrDev == true) false else profile?.isEffectivelyBlocked == true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun confirmBooking(customerName: String, customerPhone: String) {
        val profile = currentUserProfile.value
        val custId = profile?.id?.trim().orEmpty()
        if (custId.isBlank()) {
            _userMessage.value = "Please log in to confirm your booking."
            return
        }

        val isImmuneAdmin = profile?.isWhitelistedAdminOrDev == true

        // Rule 3 Admin-Block check (Whitelisted Admin/SuperAdmin immune; auto-suspension disabled)
        if (profile != null && !isImmuneAdmin && profile.isEffectivelyBlocked) {
            _userMessage.value = "Account Blocked: Your account has been blocked by Admin and cannot make new bookings."
            return
        }

        val selected = _selectedSalon.value ?: return
        val salon = allSalons.value.find { it.id == selected.id } ?: selected

        // Strict Shop Closed Validation before processing ANY booking request
        if (!salon.isOpen || salon.currentStatus.equals("CLOSED", ignoreCase = true)) {
            _serviceToBook.value = null
            _showSalonClosedDialog.value = true
            return
        }

        // Rule 4: Prevent Self-Booking unless whitelisted Admin/Developer testing queue
        if (!isImmuneAdmin && custId.isNotBlank() && (salon.ownerId == custId || salon.partnerId == custId)) {
            _userMessage.value = "This is your own registered salon. Manage it in Partner Mode."
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
                bookingDate = "Today",
                onSalonClosed = {
                    _showConfirmationDialog.value = false
                    _newlyConfirmedBooking.value = null
                    _serviceToBook.value = null
                    _showSalonClosedDialog.value = true
                }
            )

            _serviceToBook.value = null
            _newlyConfirmedBooking.value = newBooking
            _showConfirmationDialog.value = true
        } catch (e: Exception) {
            if (e.message == "This salon is currently closed.") {
                _serviceToBook.value = null
                _showSalonClosedDialog.value = true
            } else {
                _userMessage.value = e.message
            }
        }
    }

    fun dismissConfirmationDialog() {
        _showConfirmationDialog.value = false
        _newlyConfirmedBooking.value = null
        _serviceToBook.value = null
    }

    // ==========================================
    // Real Firebase Auth (Email/Password + Google Sign-In) & Role Switching
    // ==========================================

    fun clearAuthError() {
        repository.clearAuthError()
    }

    fun signInWithEmail(
        email: String,
        password: String,
        onSuccess: () -> Unit = {}
    ) {
        val cleanEmail = email.trim().lowercase()
        val appContext = getApplication<Application>().applicationContext
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            val err = "Please enter a valid email address."
            repository.setAuthError(err)
            _userMessage.value = err
            return
        }
        if (password.isBlank()) {
            val err = "Please enter your password."
            repository.setAuthError(err)
            _userMessage.value = err
            return
        }

        repository.setAuthLoading(true)
        repository.setAuthError(null)

        viewModelScope.launch {
            try {
                val authResult = FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(cleanEmail, password)
                    .await()

                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    repository.setAuthLoading(false)
                    val err = "User session is null after login. Please try again."
                    repository.setAuthError(err)
                    _userMessage.value = err
                    Toast.makeText(appContext, err, Toast.LENGTH_LONG).show()
                    return@launch
                }

                val uid = firebaseUser.uid
                val displayName = firebaseUser.displayName?.takeIf { it.isNotBlank() }
                    ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                val userEmail = firebaseUser.email?.takeIf { it.isNotBlank() } ?: cleanEmail

                repository.completeEmailLogin(
                    uid = uid,
                    displayName = displayName,
                    email = userEmail,
                    onSuccess = {
                        _userMessage.value = "Welcome back to TrimTime!"
                        _currentNavTab.value = CustomerNavTab.SALONS
                        onSuccess()
                    },
                    onError = { err ->
                        _userMessage.value = err
                    }
                )
            } catch (e: FirebaseAuthException) {
                repository.setAuthLoading(false)
                val hint = when (e.errorCode) {
                    "ERROR_INVALID_CREDENTIAL", "invalid-credential" ->
                        " (Incorrect credentials or account not registered yet. If you are new, tap 'Sign Up'.)"
                    "ERROR_USER_NOT_FOUND", "user-not-found" ->
                        " (No account found. Please switch to 'Sign Up' to create your account.)"
                    "ERROR_WRONG_PASSWORD", "wrong-password" ->
                        " (Incorrect password. Please verify or reset your password.)"
                    else -> ""
                }
                val exactError = "FirebaseAuthException [${e.errorCode}]: ${e.message}$hint"
                repository.setAuthError(exactError)
                _userMessage.value = exactError
                Log.e("TrimTimeViewModel", "Login FirebaseAuthException: $exactError", e)
                Toast.makeText(appContext, exactError, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                repository.setAuthLoading(false)
                val authEx = e.cause as? FirebaseAuthException
                val exactError = if (authEx != null) {
                    val hint = when (authEx.errorCode) {
                        "ERROR_INVALID_CREDENTIAL", "invalid-credential" ->
                            " (Incorrect credentials or account not registered yet. If you are new, tap 'Sign Up'.)"
                        else -> ""
                    }
                    "FirebaseAuthException [${authEx.errorCode}]: ${authEx.message}$hint"
                } else {
                    e.message ?: e.localizedMessage ?: e.toString()
                }
                repository.setAuthError(exactError)
                _userMessage.value = exactError
                Log.e("TrimTimeViewModel", "Login Exception: $exactError", e)
                Toast.makeText(appContext, exactError, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun signUpWithEmail(
        fullName: String,
        email: String,
        password: String,
        onSuccess: () -> Unit = {}
    ) {
        val cleanEmail = email.trim().lowercase()
        val sanitized = SecurityValidator.sanitizeName(fullName, trimEdges = true)
        val cleanName = if (sanitized.length >= 2) {
            sanitized
        } else {
            cleanEmail.substringBefore("@")
                .replace(".", " ")
                .replaceFirstChar { it.uppercase() }
                .ifBlank { "TrimTime User" }
        }
        val appContext = getApplication<Application>().applicationContext

        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            val err = "Please enter a valid email address."
            repository.setAuthError(err)
            _userMessage.value = err
            return
        }
        if (password.length < 6) {
            val err = "Password must be at least 6 characters long."
            repository.setAuthError(err)
            _userMessage.value = err
            return
        }

        repository.setAuthLoading(true)
        repository.setAuthError(null)

        viewModelScope.launch {
            try {
                val authResult = FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(cleanEmail, password)
                    .await()

                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    repository.setAuthLoading(false)
                    val err = "Firebase Auth returned a null user after createUserWithEmailAndPassword."
                    repository.setAuthError(err)
                    _userMessage.value = err
                    Toast.makeText(appContext, err, Toast.LENGTH_LONG).show()
                    return@launch
                }

                val uid = firebaseUser.uid
                try {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanName)
                        .build()
                    firebaseUser.updateProfile(profileUpdates).await()
                } catch (_: Exception) {
                }

                // Immediately create a document in the Firestore `users` collection with uid, email, name, and isAdmin = false
                val userDocData = mapOf(
                    "uid" to uid,
                    "id" to uid,
                    "email" to cleanEmail,
                    "name" to cleanName,
                    "isAdmin" to false,
                    "phone" to "",
                    "isPhoneVerified" to false,
                    "role" to "customer",
                    "hasRegisteredSalon" to false,
                    "defaultCity" to appPreferences.value.defaultCity,
                    "cancellationCount" to 0,
                    "cancellationStrikes" to 0,
                    "isSuspended" to false,
                    "isBlocked" to false,
                    "isLocationLocked" to true,
                    "unlockRequestStatus" to "none",
                    "createdAt" to System.currentTimeMillis()
                )

                try {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(uid)
                        .set(userDocData, SetOptions.merge())
                        .await()
                } catch (fsErr: Exception) {
                    Log.w("TrimTimeViewModel", "Firestore users/$uid write warning: ${fsErr.message}", fsErr)
                }

                repository.completeEmailSignUp(
                    uid = uid,
                    cleanName = cleanName,
                    cleanEmail = cleanEmail,
                    onSuccess = {
                        _userMessage.value = "Account created! Welcome to TrimTime."
                        _currentNavTab.value = CustomerNavTab.SALONS
                        onSuccess()
                    }
                )
            } catch (e: FirebaseAuthException) {
                repository.setAuthLoading(false)
                val hint = if (e.errorCode == "ERROR_EMAIL_ALREADY_IN_USE" || e.errorCode.equals("email-already-in-use", ignoreCase = true)) {
                    " (An account already exists with this email. Please switch to the 'Login' tab.)"
                } else ""
                val exactError = "FirebaseAuthException [${e.errorCode}]: ${e.message}$hint"
                repository.setAuthError(exactError)
                _userMessage.value = exactError
                Log.e("TrimTimeViewModel", "Sign-Up FirebaseAuthException: $exactError", e)
                Toast.makeText(appContext, exactError, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                repository.setAuthLoading(false)
                val authEx = e.cause as? FirebaseAuthException
                val exactError = if (authEx != null) {
                    val hint = if (authEx.errorCode == "ERROR_EMAIL_ALREADY_IN_USE" || authEx.errorCode.equals("email-already-in-use", ignoreCase = true)) {
                        " (An account already exists with this email. Please switch to the 'Login' tab.)"
                    } else ""
                    "FirebaseAuthException [${authEx.errorCode}]: ${authEx.message}$hint"
                } else {
                    e.message ?: e.localizedMessage ?: e.toString()
                }
                repository.setAuthError(exactError)
                _userMessage.value = exactError
                Log.e("TrimTimeViewModel", "Sign-Up Exception: $exactError", e)
                Toast.makeText(appContext, exactError, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun sendPasswordResetEmail(
        email: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val cleanEmail = email.trim().lowercase()
        val appContext = getApplication<Application>().applicationContext
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            val err = "Please enter a valid email address to send reset instructions."
            Toast.makeText(appContext, err, Toast.LENGTH_SHORT).show()
            onError(err)
            return
        }

        FirebaseAuth.getInstance().sendPasswordResetEmail(cleanEmail)
            .addOnSuccessListener {
                val msg = "Password reset email sent to $cleanEmail! Check your inbox or spam folder."
                Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show()
                _userMessage.value = msg
                onSuccess()
            }
            .addOnFailureListener { ex ->
                val err = "Password reset failed: ${ex.message}"
                Toast.makeText(appContext, err, Toast.LENGTH_LONG).show()
                _userMessage.value = err
                onError(err)
            }
    }

    private var googleSignInJob: Job? = null

    fun signInWithGoogle(context: Context, onSuccess: () -> Unit = {}) {
        if (googleSignInJob?.isActive == true) return
        googleSignInJob = viewModelScope.launch {
            try {
                try {
                    FirebaseAuth.getInstance().signOut()
                } catch (_: Exception) {
                }
                try {
                    kotlinx.coroutines.withTimeoutOrNull(400L) {
                        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                    }
                } catch (_: Exception) {
                }

                val serverClientId = context.getString(R.string.default_web_client_id)
                if (serverClientId.isBlank()) {
                    val msg = "Missing default_web_client_id in strings/google-services.json"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    _userMessage.value = msg
                    return@launch
                }
                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(serverClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()

                repository.signInWithGoogleSuspend(
                    context = context,
                    signInWithGoogleOption = signInWithGoogleOption,
                    googleIdOption = googleIdOption,
                    onSuccess = {
                        _userMessage.value = "Welcome to TrimTime!"
                        _currentNavTab.value = CustomerNavTab.SALONS
                        onSuccess()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        _userMessage.value = err
                    }
                )
            } catch (e: Exception) {
                val firebaseErrCode = (e as? FirebaseAuthException)?.errorCode
                    ?: (e.cause as? FirebaseAuthException)?.errorCode
                val errMsg = if (!firebaseErrCode.isNullOrBlank() && !extractRawExceptionMessage(e).contains(firebaseErrCode)) {
                    "FirebaseAuthException[$firebaseErrCode]: ${extractRawExceptionMessage(e)}"
                } else {
                    extractRawExceptionMessage(e)
                }
                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                _userMessage.value = errMsg
            }
        }
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
        isAcSalon: Boolean = false,
        imageUrl: String = "",
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
            isAcSalon = isAcSalon,
            imageUrl = imageUrl,
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

    fun acceptBooking(bookingId: String, allocatedTime: String = "") {
        if (repository.acceptBooking(bookingId, allocatedTime)) {
            val timeSuffix = if (allocatedTime.isNotBlank()) " for $allocatedTime" else ""
            _userMessage.value = "Booking ACCEPTED$timeSuffix! Customer notified in real time."
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
            _userMessage.value = "Booking cancelled."
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
        isAcSalon: Boolean? = null,
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
            isAcSalon = isAcSalon,
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

    fun uploadSalonImage(
        uri: Uri,
        salonId: String? = null,
        onSuccess: (String) -> Unit = {}
    ) {
        repository.uploadSalonImage(
            uri = uri,
            salonId = salonId,
            onSuccess = { downloadUrl ->
                _userMessage.value = "✓ Shop photo uploaded successfully!"
                onSuccess(downloadUrl)
            },
            onError = { err ->
                _userMessage.value = err
            }
        )
    }

    fun submitSalonRating(
        salonId: String,
        rating: Int,
        reviewText: String = "",
        onSuccess: () -> Unit = {}
    ) {
        repository.submitSalonRating(
            salonId = salonId,
            rating = rating,
            reviewText = reviewText,
            onSuccess = {
                _userMessage.value = "🎉 Rating submitted! Thank you."
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

    fun refreshUserProfile() {
        repository.refreshCurrentUserProfile()
    }

    fun openAdminDashboard() {
        repository.refreshCurrentUserProfile()
        if (currentUserProfile.value?.isAdmin == true || isSuperAdmin.value) {
            _showAdminDashboard.value = true
        } else {
            _showAdminDashboard.value = false
            _userMessage.value = "Access Denied: Super Admin privileges required."
        }
    }

    fun closeAdminDashboard() {
        _showAdminDashboard.value = false
    }

    fun updateSalonCategory(
        partnerId: String,
        category: String,
        salonId: String? = null
    ) {
        val app = getApplication<Application>()
        val cleanTag = category.trim().ifBlank { "Standard" }
        val cleanPartnerId = partnerId.trim()
        val cleanSalonId = salonId?.trim()?.takeIf { it.isNotBlank() }
            ?: repository.salons.value.find { it.ownerId == cleanPartnerId || it.partnerId == cleanPartnerId || it.id == cleanPartnerId }?.id
        val resolvedPartnerUid = if (cleanPartnerId.isNotBlank()) cleanPartnerId
            else repository.salons.value.find { it.id == cleanSalonId }?.let { it.ownerId.ifBlank { it.partnerId } }.orEmpty()

        // 1. Immediately apply update locally so UI updates with zero lag
        repository.applySalonAdminUpdatesLocally(
            partnerId = resolvedPartnerUid,
            salonId = cleanSalonId,
            category = cleanTag
        )

        // 2. Direct Firestore update on users and salons collections
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updates = mapOf<String, Any>(
                    "adminCategoryTag" to cleanTag,
                    "salonCategory" to cleanTag
                )
                val fs = try { FirebaseFirestore.getInstance() } catch (_: Exception) { null }
                if (fs != null) {
                    if (resolvedPartnerUid.isNotBlank()) {
                        fs.collection("users").document(resolvedPartnerUid)
                            .set(mapOf("salonCategory" to cleanTag), SetOptions.merge())
                            .await()
                    }
                    if (!cleanSalonId.isNullOrBlank()) {
                        fs.collection("salons").document(cleanSalonId)
                            .set(updates, SetOptions.merge())
                            .await()
                    }
                }

                // 3. Trigger repository sync for secondary DB and real-time listeners
                repository.updateSalonCategory(
                    salonId = cleanSalonId ?: resolvedPartnerUid,
                    categoryTag = cleanTag,
                    onSuccess = {},
                    onError = {}
                )

                withContext(Dispatchers.Main) {
                    val successMsg = "Salon updated to $cleanTag"
                    _userMessage.value = successMsg
                    Toast.makeText(app, successMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("TrimTimeViewModel", "Error updating salon category: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val errorMsg = e.localizedMessage ?: e.message ?: "Failed to update category"
                    _userMessage.value = "Error: $errorMsg"
                    Toast.makeText(app, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun updateSalonStatus(
        partnerId: String,
        status: String,
        salonId: String? = null
    ) {
        val app = getApplication<Application>()
        val cleanStatus = status.trim().lowercase() // "approved", "rejected", or "suspended"
        val cleanPartnerId = partnerId.trim()
        val cleanSalonId = salonId?.trim()?.takeIf { it.isNotBlank() }
            ?: repository.salons.value.find { it.ownerId == cleanPartnerId || it.partnerId == cleanPartnerId || it.id == cleanPartnerId }?.id
        val resolvedPartnerUid = if (cleanPartnerId.isNotBlank()) cleanPartnerId
            else repository.salons.value.find { it.id == cleanSalonId }?.let { it.ownerId.ifBlank { it.partnerId } }.orEmpty()

        val isSuspended = cleanStatus == "suspended"
        val approvalStatus = when (cleanStatus) {
            "approved" -> "APPROVED"
            "rejected" -> "REJECTED"
            "suspended" -> "SUSPENDED"
            else -> cleanStatus.uppercase()
        }

        // 1. Immediately apply update locally so UI updates with zero lag
        repository.applySalonAdminUpdatesLocally(
            partnerId = resolvedPartnerUid,
            salonId = cleanSalonId,
            status = cleanStatus
        )

        // 2. Direct Firestore update on users and salons collections
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val salonUpdates = mutableMapOf<String, Any>(
                    "salonStatus" to cleanStatus,
                    "approvalStatus" to approvalStatus,
                    "isSuspended" to isSuspended
                )
                if (isSuspended || approvalStatus == "REJECTED") {
                    salonUpdates["isOpen"] = false
                    salonUpdates["currentStatus"] = "CLOSED"
                }

                val fs = try { FirebaseFirestore.getInstance() } catch (_: Exception) { null }
                if (fs != null) {
                    if (resolvedPartnerUid.isNotBlank()) {
                        fs.collection("users").document(resolvedPartnerUid)
                            .set(mapOf("salonStatus" to cleanStatus, "isSuspended" to isSuspended), SetOptions.merge())
                            .await()
                    }
                    if (!cleanSalonId.isNullOrBlank()) {
                        fs.collection("salons").document(cleanSalonId)
                            .set(salonUpdates, SetOptions.merge())
                            .await()
                    }
                }

                // 3. Trigger repository sync for secondary DB and real-time listeners
                repository.updateSalonApproval(
                    salonId = cleanSalonId ?: resolvedPartnerUid,
                    approvalStatus = approvalStatus,
                    isSuspended = isSuspended,
                    onSuccess = {},
                    onError = {}
                )

                withContext(Dispatchers.Main) {
                    val toastMsg = when (cleanStatus) {
                        "suspended" -> "Account Suspended"
                        "approved" -> "Salon Approved"
                        "rejected" -> "Salon Rejected"
                        else -> "Salon status updated to $cleanStatus"
                    }
                    _userMessage.value = toastMsg
                    Toast.makeText(app, toastMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("TrimTimeViewModel", "Error updating salon status: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val errorMsg = e.localizedMessage ?: e.message ?: "Failed to update salon status"
                    _userMessage.value = "Error: $errorMsg"
                    Toast.makeText(app, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun updateSalonApproval(
        salonId: String,
        approvalStatus: String,
        isSuspended: Boolean = false
    ) {
        val status = if (isSuspended) "suspended" else approvalStatus.lowercase()
        updateSalonStatus(partnerId = salonId, status = status, salonId = salonId)
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

    fun requestLocationUnlock(
        userId: String? = null,
        salonId: String? = null
    ) {
        repository.requestLocationUnlock(
            userId = userId,
            salonId = salonId,
            onComplete = { _, msg ->
                _userMessage.value = msg
            }
        )
    }

    fun approveLocationUnlock(
        userId: String,
        salonId: String? = null
    ) {
        val app = getApplication<Application>()
        val cleanUserId = userId.trim()
        val cleanSalonId = salonId?.trim()?.takeIf { it.isNotBlank() }

        // Immediately update repository in-memory state so UI updates instantly
        repository.applyLocationUnlockLocally(cleanUserId, cleanSalonId)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updates = mapOf<String, Any>(
                    "isLocationLocked" to false,
                    "unlockRequestStatus" to "approved"
                )

                // 1. Direct Firestore update on users and salons
                val fs = try { FirebaseFirestore.getInstance() } catch (_: Exception) { null }
                if (fs != null) {
                    if (cleanUserId.isNotBlank()) {
                        fs.collection("users").document(cleanUserId)
                            .set(updates, SetOptions.merge())
                            .await()
                    }
                    if (!cleanSalonId.isNullOrBlank()) {
                        fs.collection("salons").document(cleanSalonId)
                            .set(updates, SetOptions.merge())
                            .await()
                    }
                }

                // 2. Also trigger repository update for named/secondary DB sync
                repository.approveLocationUnlock(
                    userId = cleanUserId,
                    salonId = cleanSalonId,
                    onSuccess = {},
                    onError = {}
                )

                withContext(Dispatchers.Main) {
                    _userMessage.value = "Location Unlocked!"
                    Toast.makeText(app, "Location Unlocked!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("TrimTimeViewModel", "Error approving location unlock: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val rawMsg = e.localizedMessage ?: e.message ?: "Failed to unlock location."
                    _userMessage.value = "Error: $rawMsg"
                    Toast.makeText(app, "Error: $rawMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun setGlobalRushModeEnabled(enabled: Boolean) {
        repository.setGlobalRushModeEnabled(
            enabled = enabled,
            onSuccess = {
                _userMessage.value = if (enabled) {
                    "🎉 Global Festival / Rush Mode ENABLED in app_settings!"
                } else {
                    "Global Festival / Rush Mode disabled (Auto Local Rush >= 3 active)."
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
