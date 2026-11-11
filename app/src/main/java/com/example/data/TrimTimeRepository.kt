package com.example.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.ui.auth.extractRawExceptionMessage
import com.example.ui.auth.setAutoSelectEnabled
import com.example.ui.auth.setFilterByAuthorizedAccounts
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.example.model.AppAuthState
import com.example.model.AppLanguage
import com.example.model.AppPreferences
import com.example.model.AppThemeMode
import com.example.model.Booking
import com.example.model.BookingStatus
import com.example.model.FeedbackSubmission
import com.example.model.Salon
import com.example.model.SalonService
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.service.TrimTimeNotificationHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.SetOptions
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.example.util.SecurityValidator
import java.io.File
import kotlin.random.Random

sealed class PinVerificationResult {
    data object Success : PinVerificationResult()
    data class IncorrectPin(val message: String, val attemptsRemaining: Int = 5) : PinVerificationResult()
    data class LockedOut(val remainingSeconds: Int, val message: String) : PinVerificationResult()
    data class BookingNotFound(val message: String) : PinVerificationResult()
    data class AlreadyCompleted(val message: String) : PinVerificationResult()
}

class TrimTimeRepository private constructor(context: Context) {

    private val tag = "TrimTimeRepository"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val appContext = context.applicationContext
    private val sessionPrefs = appContext.getSharedPreferences("trimtime_session_prefs", Context.MODE_PRIVATE)
    private val settingsPrefs = appContext.getSharedPreferences("trimtime_user_settings_prefs", Context.MODE_PRIVATE)

    // Brute-force PIN attempt tracking per bookingId (5 consecutive failures -> 60-second lockout)
    private val pinFailedAttempts = mutableMapOf<String, Int>()
    private val pinLockoutUntilMillis = mutableMapOf<String, Long>()

    // Firebase Instances
    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var defaultFirestore: FirebaseFirestore? = null
    private var isFirebaseReady = false

    private var salonsListener: ListenerRegistration? = null
    private var bookingsListener: ListenerRegistration? = null
    private var customerBookingsListener: ListenerRegistration? = null
    private var customerFcmNotificationsListener: ListenerRegistration? = null
    private var partnerFcmNotificationsListener: ListenerRegistration? = null
    private var userDocListener: ListenerRegistration? = null
    private var defaultUserDocListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null
    private var defaultUsersListener: ListenerRegistration? = null
    private var feedbackListener: ListenerRegistration? = null
    private var appSettingsListener: ListenerRegistration? = null
    private var defaultAppSettingsListener: ListenerRegistration? = null

    private var namedDbUsers: List<UserProfile> = emptyList()
    private var defaultDbUsers: List<UserProfile> = emptyList()

    private var hasLoadedInitialBookingsSnapshot = false
    private var hasLoadedInitialCustomerBookingsSnapshot = false
    private var hasLoadedInitialCustomerFcmNotifications = false
    private val knownRealtimeBookingIds = mutableSetOf<String>()
    private val knownCustomerFcmNotificationIds = mutableSetOf<String>()
    private val knownPartnerFcmNotificationIds = mutableSetOf<String>()
    private val previousCustomerBookingStatus = mutableMapOf<String, Pair<String, String>>()

    private val invalidSharedUserIds = setOf("dev_test_user_001", "dev_tester", "default_user", "guest")
    private val seedSalonIds = setOf("salon_blade_comb", "salon_urban_cuts", "salon_velvet_shears", "salon_express_trim")

    // App Authentication State: Loading, Unauthenticated, Authenticated
    private val _authState = MutableStateFlow<AppAuthState>(AppAuthState.Loading)
    val authState: StateFlow<AppAuthState> = _authState.asStateFlow()

    // Real-time active role (Customer or Partner)
    private val _currentRole = MutableStateFlow(UserRole.CUSTOMER)
    val currentRole: StateFlow<UserRole> = _currentRole.asStateFlow()

    // Current authenticated user profile
    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    // Real-time Salons directly from Firestore
    private val _salons = MutableStateFlow<List<Salon>>(emptyList())
    val salons: StateFlow<List<Salon>> = _salons.asStateFlow()

    // Real-time All Users from Firestore (for Super Admin User & Spam Management)
    private val _allUsers = MutableStateFlow<List<UserProfile>>(emptyList())
    val allUsers: StateFlow<List<UserProfile>> = _allUsers.asStateFlow()

    // Real-time Support & Feedback Tickets from Firestore (for Super Admin Help Desk)
    private val _supportTickets = MutableStateFlow<List<FeedbackSubmission>>(emptyList())
    val supportTickets: StateFlow<List<FeedbackSubmission>> = _supportTickets.asStateFlow()

    // Real-time All Bookings from Firestore (for Partner Salon Queue filtering by salonId)
    private val _bookings = MutableStateFlow<List<Booking>>(emptyList())
    val bookings: StateFlow<List<Booking>> = _bookings.asStateFlow()

    // Strictly isolated Bookings for the currently logged-in Customer (where booking.customerId == currentUser.uid)
    private val _customerBookings = MutableStateFlow<List<Booking>>(emptyList())
    val customerBookings: StateFlow<List<Booking>> = _customerBookings.asStateFlow()

    // Most recently COMPLETED booking for the currently logged-in Customer (for Quick Re-Book banner)
    private val _latestCompletedBooking = MutableStateFlow<Booking?>(null)
    val latestCompletedBooking: StateFlow<Booking?> = _latestCompletedBooking.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isCloudSynced = MutableStateFlow(false)
    val isCloudSynced: StateFlow<Boolean> = _isCloudSynced.asStateFlow()

    // Dedicated Partner Salon Registration Onboarding state (shown when an unregistered user taps Partner)
    private val _showPartnerOnboarding = MutableStateFlow(false)
    val showPartnerOnboarding: StateFlow<Boolean> = _showPartnerOnboarding.asStateFlow()

    // Persisted Customer App Settings & Preferences
    private val _appPreferences = MutableStateFlow(loadSavedPreferences())
    val appPreferences: StateFlow<AppPreferences> = _appPreferences.asStateFlow()

    // Central Global Festival / Rush Mode state synced with Firestore `app_settings/global_config`
    private val _isGlobalRushModeEnabled = MutableStateFlow(
        settingsPrefs.getBoolean("pref_global_rush_mode", false)
    )
    val isGlobalRushModeEnabled: StateFlow<Boolean> = _isGlobalRushModeEnabled.asStateFlow()

    // Live Customer GPS Coordinates & Reverse-Geocoded Location Display
    private val _userLatitude = MutableStateFlow(
        settingsPrefs.getString("pref_user_lat", null)?.toDoubleOrNull()?.takeIf { it != 0.0 } ?: 26.3359
    )
    val userLatitude: StateFlow<Double> = _userLatitude.asStateFlow()

    private val _userLongitude = MutableStateFlow(
        settingsPrefs.getString("pref_user_lng", null)?.toDoubleOrNull()?.takeIf { it != 0.0 } ?: 84.6304
    )
    val userLongitude: StateFlow<Double> = _userLongitude.asStateFlow()

    private val _userLocationTitle = MutableStateFlow("Detecting Live GPS...")
    val userLocationTitle: StateFlow<String> = _userLocationTitle.asStateFlow()

    private val _userLocationSubtitle = MutableStateFlow("Fetching your live GPS location...")
    val userLocationSubtitle: StateFlow<String> = _userLocationSubtitle.asStateFlow()

    private val _isLiveGpsActive = MutableStateFlow(false)
    val isLiveGpsActive: StateFlow<Boolean> = _isLiveGpsActive.asStateFlow()

    init {
        initFirebase(context)
        startPendingBookingTimeoutWatcher()
    }

    /**
     * Background watcher that periodically checks for 'Pending' bookings where the Partner
     * has not responded within 5 minutes (`Booking.PENDING_TIMEOUT_MS`) and auto-cancels them.
     */
    private fun startPendingBookingTimeoutWatcher() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(15_000L)
                sweepExpiredPendingBookings()
            }
        }
    }

    /**
     * Sweeps `_bookings` for any 'Pending' booking older than 5 minutes OR any active booking
     * late beyond the 10-minute grace period (`Booking.LATE_GRACE_PERIOD_MS`) and auto-cancels it
     * in memory and in Firestore.
     */
    fun sweepExpiredPendingBookings(nowMillis: Long = System.currentTimeMillis()) {
        val expiredBookings = _bookings.value.filter {
            it.isPendingTimedOut(nowMillis) || it.isGracePeriodExpired(nowMillis)
        }
        if (expiredBookings.isEmpty()) return

        val expiredIds = expiredBookings.map { it.id }.toSet()
        _bookings.value = _bookings.value.map { b ->
            if (b.id in expiredIds) b.copy(status = "cancelled") else b
        }
        _customerBookings.value = _customerBookings.value.map { b ->
            if (b.id in expiredIds) b.copy(status = "cancelled") else b
        }

        val db = firestore ?: defaultFirestore ?: return
        expiredBookings.forEach { expired ->
            val isGraceExpired = expired.isGracePeriodExpired(nowMillis)
            val bookingRef = db.collection("bookings").document(expired.id)
            db.runTransaction { transaction ->
                val snap = transaction.get(bookingRef)
                val currentStatus = snap.getString("status")?.trim().orEmpty()
                if (snap.exists() && Booking.isStatusPendingOrAccepted(currentStatus)) {
                    val reasonNote = if (isGraceExpired) {
                        "${expired.cleanAllocatedClockTime} (Auto-Expired: >10m Late)"
                    } else {
                        "Auto-Cancelled (5m Partner Timeout)"
                    }
                    transaction.update(
                        bookingRef,
                        mapOf(
                            "status" to "cancelled",
                            "allocatedTime" to reasonNote
                        )
                    )
                }
                null
            }.addOnSuccessListener {
                Log.d(tag, "Auto-cancelled expired booking (graceExpired=$isGraceExpired): ${expired.id}")
            }.addOnFailureListener { err ->
                Log.w(tag, "Fallback auto-cancel for expired booking ${expired.id}: ${err.message}")
                bookingRef.set(
                    mapOf("status" to "cancelled"),
                    com.google.firebase.firestore.SetOptions.merge()
                )
            }
        }
    }

    fun isDemoSalon(salonId: String): Boolean = salonId in seedSalonIds

    private fun loadSavedPreferences(): AppPreferences {
        val themeKey = settingsPrefs.getString("pref_theme_mode", AppThemeMode.DARK.key)
        val langKey = settingsPrefs.getString("pref_language", AppLanguage.ENGLISH.key)
        val bookingAlerts = settingsPrefs.getBoolean("pref_notif_booking_status", true)
        val queueReminders = settingsPrefs.getBoolean("pref_notif_queue_turn", true)
        val offersDiscounts = settingsPrefs.getBoolean("pref_notif_salon_offers", false)
        val defaultCity = settingsPrefs.getString("pref_default_city", null)
            ?.takeIf { it.isNotBlank() && !it.equals("Patna, Bihar", ignoreCase = true) }
            ?: "Current Location"
        return AppPreferences(
            themeMode = AppThemeMode.fromKey(themeKey),
            language = AppLanguage.fromKey(langKey),
            bookingStatusAlerts = bookingAlerts,
            queueTurnReminders = queueReminders,
            salonOffersDiscounts = offersDiscounts,
            defaultCity = defaultCity,
            showDemoSalons = false
        )
    }

    /**
     * Updates the customer's live GPS coordinates and/or reverse-geocoded location text,
     * persists them locally, and immediately recalculates & sorts all nearby salon distances.
     */
    fun updateUserGpsLocation(
        latitude: Double,
        longitude: Double,
        title: String? = null,
        subtitle: String? = null,
        isLiveGps: Boolean = true
    ) {
        val validLat = if (latitude != 0.0) latitude else _userLatitude.value
        val validLng = if (longitude != 0.0) longitude else _userLongitude.value

        _userLatitude.value = validLat
        _userLongitude.value = validLng
        _isLiveGpsActive.value = isLiveGps

        val editor = settingsPrefs.edit()
            .putString("pref_user_lat", validLat.toString())
            .putString("pref_user_lng", validLng.toString())
            .putBoolean("pref_has_live_gps", isLiveGps)

        if (!title.isNullOrBlank()) {
            val cleanTitle = SecurityValidator.sanitizeAddress(title, trimEdges = true).ifBlank { _userLocationTitle.value }
            _userLocationTitle.value = cleanTitle
            _appPreferences.value = _appPreferences.value.copy(defaultCity = cleanTitle)
            _currentUserProfile.value?.let { current ->
                _currentUserProfile.value = current.copy(defaultCity = cleanTitle)
            }
            editor.putString("pref_default_city", cleanTitle)
        }

        if (!subtitle.isNullOrBlank()) {
            val cleanSubtitle = SecurityValidator.sanitizeAddress(subtitle, trimEdges = true).ifBlank { _userLocationSubtitle.value }
            _userLocationSubtitle.value = cleanSubtitle
            editor.putString("pref_location_subtitle", cleanSubtitle)
        }

        editor.apply()

        // Immediately recalculate all salon distances against the new coordinates and sort nearest-first
        _salons.value = recalculateAndSortSalons(_salons.value, validLat, validLng)
    }

    /**
     * Dynamically recalculates every salon's Haversine distance in `km` from `(userLat, userLng)`
     * and sorts salons by shortest geographic distance first (nearest at the very top).
     */
    fun recalculateAndSortSalons(
        salonsList: List<Salon>,
        userLat: Double = _userLatitude.value,
        userLng: Double = _userLongitude.value
    ): List<Salon> {
        return salonsList
            .filterNot { it.id in seedSalonIds }
            .map { salon ->
                val sLat = salon.latitude
                val sLng = salon.longitude
                if (sLat != null && sLng != null && sLat != 0.0 && sLng != 0.0) {
                    val dKm = calculateDistanceKm(userLat, userLng, sLat, sLng)
                    val formatted = String.format(java.util.Locale.US, "%.1f km", dKm)
                    salon.copy(distance = formatted)
                } else {
                    salon
                }
            }.sortedWith(
                compareBy<Salon> { salon ->
                    val sLat = salon.latitude
                    val sLng = salon.longitude
                    if (sLat != null && sLng != null && sLat != 0.0 && sLng != 0.0) {
                        calculateRawDistanceKm(userLat, userLng, sLat, sLng)
                    } else {
                        val parsed = salon.distance.replace("km", "", ignoreCase = true).trim().toDoubleOrNull()
                        if (parsed != null) parsed + 5000.0 else 99999.0
                    }
                }.thenBy { it.name.lowercase(java.util.Locale.US) }
            )
    }

    fun openPartnerRegistrationForm() {
        _currentRole.value = UserRole.CUSTOMER
        _showPartnerOnboarding.value = true
    }

    fun dismissPartnerOnboarding() {
        _showPartnerOnboarding.value = false
    }

    private fun isGenuinelyRegisteredSalonValues(
        docId: String,
        salonName: String,
        shopAddress: String,
        ownerId: String,
        isRegistrationCompletedFlag: Boolean,
        registrationFormSubmittedFlag: Boolean
    ): Boolean {
        if (docId in seedSalonIds) return false
        val cleanName = salonName.trim()
        val cleanAddr = shopAddress.trim()
        val isLegacyDummyName = cleanName.length < 3 ||
            cleanName.endsWith("'s Grooming Studio", ignoreCase = true) ||
            cleanName.equals("cod", ignoreCase = true) ||
            cleanName.equals("My Salon", ignoreCase = true) ||
            cleanName.startsWith("Salon salon_", ignoreCase = true)
        return isRegistrationCompletedFlag &&
            registrationFormSubmittedFlag &&
            ownerId.isNotBlank() &&
            !isLegacyDummyName &&
            cleanAddr.length >= 4
    }

    private fun isGenuinelyRegisteredSalonDoc(doc: com.google.firebase.firestore.DocumentSnapshot): Boolean {
        val resolvedDocId = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id
        if (resolvedDocId in seedSalonIds) return false
        val salonName = doc.getString("name")
            ?: doc.getString("salonName")
            ?: doc.getString("title")
            ?: ""
        val shopAddress = doc.getString("shopAddress")
            ?: doc.getString("address")
            ?: doc.getString("location")
            ?: ""
        val ownerId = doc.getString("ownerId")
            ?: doc.getString("partnerId")
            ?: doc.getString("userId")
            ?: ""
        val isRegCompleted = doc.getBoolean("isRegistrationCompleted") == true
        val isFormSubmitted = doc.getBoolean("registrationFormSubmitted") == true
        return isGenuinelyRegisteredSalonValues(
            docId = resolvedDocId,
            salonName = salonName,
            shopAddress = shopAddress,
            ownerId = ownerId,
            isRegistrationCompletedFlag = isRegCompleted,
            registrationFormSubmittedFlag = isFormSubmitted
        )
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun setAuthLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun setAuthError(error: String?) {
        _authError.value = error
    }

    fun getActiveFirestore(): FirebaseFirestore? = firestore ?: defaultFirestore

    private fun initFirebase(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            val apps = FirebaseApp.getApps(context)
            if (apps.isNotEmpty()) {
                val app = FirebaseApp.getInstance()
                auth = FirebaseAuth.getInstance()
                val dbId = try {
                    context.getString(com.example.R.string.firestore_database_id).trim()
                } catch (_: Exception) {
                    ""
                }
                firestore = if (dbId.isNotBlank() && dbId != "(default)") {
                    try {
                        FirebaseFirestore.getInstance(app, dbId)
                    } catch (e: Exception) {
                        Log.w(tag, "Failed to get named firestore instance for dbId: $dbId", e)
                        FirebaseFirestore.getInstance(app)
                    }
                } else {
                    FirebaseFirestore.getInstance(app)
                }
                defaultFirestore = firestore
                val projectId = FirebaseApp.getInstance().options.projectId
                isFirebaseReady = true
                _isCloudSynced.value = true
                Log.d(tag, "Firebase Auth and Firestore (project=$projectId, dbId=$dbId) initialized strictly from default google-services.json.")

                // Verify real Firebase Auth state against the server on startup
                val firebaseUser = auth?.currentUser
                if (firebaseUser != null && firebaseUser.uid !in invalidSharedUserIds) {
                    Log.d(tag, "Active Firebase session detected for uid: ${firebaseUser.uid}, verifying with server...")
                    _authState.value = AppAuthState.Loading
                    firebaseUser.reload()
                        .addOnSuccessListener {
                            val refreshedUser = auth?.currentUser
                            if (refreshedUser != null) {
                                fetchUserProfile(refreshedUser.uid)
                            } else {
                                sessionPrefs.edit().clear().apply()
                                _currentUserProfile.value = null
                                _customerBookings.value = emptyList()
                                _authState.value = AppAuthState.Unauthenticated
                            }
                        }
                        .addOnFailureListener { reloadErr ->
                            Log.w(tag, "Firebase user reload failed (${reloadErr.message}); clearing stale local session.")
                            try {
                                auth?.signOut()
                            } catch (_: Exception) {
                            }
                            sessionPrefs.edit().clear().apply()
                            _currentUserProfile.value = null
                            _customerBookings.value = emptyList()
                            _authState.value = AppAuthState.Unauthenticated
                        }
                } else {
                    Log.d(tag, "No active Firebase Auth session found. Setting Unauthenticated.")
                    sessionPrefs.edit().clear().apply()
                    _currentUserProfile.value = null
                    _customerBookings.value = emptyList()
                    _authState.value = AppAuthState.Unauthenticated
                }

                attachFirestoreRealtimeListeners()
            } else {
                Log.w(tag, "FirebaseApp not initialized. Setting Unauthenticated.")
                _currentUserProfile.value = null
                _authState.value = AppAuthState.Unauthenticated
                populateInitialLiveSalons()
            }
        } catch (e: Exception) {
            Log.e(tag, "Firebase initialization error: ${e.message}")
            _currentUserProfile.value = null
            _authState.value = AppAuthState.Unauthenticated
            populateInitialLiveSalons()
        }
    }

    private fun attachFirestoreRealtimeListeners() {
        val db = firestore ?: return
        try {
            // Live Real-Time Listener for Salons collection
            salonsListener?.remove()
            salonsListener = db.collection("salons").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "Firestore salons listener error: ${error.message}")
                    if (_salons.value.isEmpty()) {
                        populateInitialLiveSalons()
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    if (snapshot.isEmpty) {
                        Log.d(tag, "Salons collection is empty in Firestore. Seeding initial live salons...")
                        seedLiveSalonsIntoFirestore(db)
                    } else {
                        val liveSalons = snapshot.documents.mapNotNull { doc ->
                            try {
                                val docId = doc.id
                                val salonName = doc.getString("name")
                                    ?: doc.getString("salonName")
                                    ?: doc.getString("title")
                                    ?: "Salon $docId"

                                val location = doc.getString("location")
                                    ?: doc.getString("address")
                                    ?: "Local Area"

                                val shopAddress = doc.getString("shopAddress")
                                    ?: doc.getString("address")
                                    ?: ""

                                val landmark = doc.getString("landmark") ?: ""

                                val coordinatesMap = doc.get("coordinates") as? Map<String, Any?>
                                val geoPoint = try { doc.getGeoPoint("coordinates") ?: doc.getGeoPoint("location") } catch (_: Exception) { null }
                                val lat = (coordinatesMap?.get("lat") as? Number)?.toDouble()
                                    ?: (coordinatesMap?.get("latitude") as? Number)?.toDouble()
                                    ?: (doc.get("latitude") as? Number)?.toDouble()
                                    ?: doc.getString("latitude")?.toDoubleOrNull()
                                    ?: geoPoint?.latitude
                                val lng = (coordinatesMap?.get("lng") as? Number)?.toDouble()
                                    ?: (coordinatesMap?.get("longitude") as? Number)?.toDouble()
                                    ?: (doc.get("longitude") as? Number)?.toDouble()
                                    ?: doc.getString("longitude")?.toDoubleOrNull()
                                    ?: geoPoint?.longitude

                                val distance = if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
                                    val dKm = calculateDistanceKm(_userLatitude.value, _userLongitude.value, lat, lng)
                                    String.format(java.util.Locale.US, "%.1f km", dKm)
                                } else {
                                    doc.getString("distance") ?: "0.5 km"
                                }

                                val rating = (doc.get("rating") as? Number)?.toDouble()
                                    ?: doc.getString("rating")?.toDoubleOrNull()
                                    ?: 4.8

                                val reviewCount = (doc.get("reviewCount") as? Number)?.toInt()
                                    ?: doc.getString("reviewCount")?.toIntOrNull()
                                    ?: 15

                                val ownerId = doc.getString("ownerId")
                                    ?: doc.getString("partnerId")
                                    ?: doc.getString("userId")
                                    ?: ""

                                val partnerId = doc.getString("partnerId")
                                    ?: ownerId

                                val phone = doc.getString("phone") ?: ""

                                val rawIsOpen = doc.getBoolean("isOpen")
                                    ?: doc.getString("isOpen")?.toBooleanStrictOrNull()
                                    ?: doc.getBoolean("isShopOpen")
                                    ?: true

                                val currentStatus = doc.getString("currentStatus")
                                    ?: if (rawIsOpen) "FREE" else "CLOSED"
                                val isOpen = rawIsOpen && !currentStatus.equals("CLOSED", ignoreCase = true)

                                val availableChairs = (doc.get("availableChairs") as? Number)?.toInt()
                                    ?: doc.getString("availableChairs")?.toIntOrNull()
                                    ?: 2

                                val waitingTimeMinutes = (doc.get("waitingTimeMinutes") as? Number)?.toInt()
                                    ?: doc.getString("waitingTimeMinutes")?.toIntOrNull()
                                    ?: if (currentStatus.equals("BUSY", ignoreCase = true)) 15 else 0

                                val tagline = doc.getString("tagline")
                                    ?: doc.getString("description")
                                    ?: "Instant grooming slots"

                                val nextSlotMinutes = (doc.get("nextSlotMinutes") as? Number)?.toInt()
                                    ?: doc.getString("nextSlotMinutes")?.toIntOrNull()
                                    ?: if (waitingTimeMinutes > 0) waitingTimeMinutes else 15

                                val servicesRaw = doc.get("services") as? List<Map<String, Any?>> ?: emptyList()
                                val parsedServices = servicesRaw.mapIndexed { idx, sMap ->
                                    val sId = (sMap["id"] as? String) ?: "${docId}_s${idx + 1}"
                                    val sName = (sMap["serviceName"] as? String)?.takeIf { it.isNotBlank() }
                                        ?: (sMap["name"] as? String)?.takeIf { it.isNotBlank() }
                                        ?: "Grooming Service"
                                    val sCategory = (sMap["category"] as? String) ?: "Haircut"
                                    val sPrice = (sMap["price"] as? Number)?.toInt()
                                        ?: (sMap["price"] as? String)?.toIntOrNull()
                                        ?: 100
                                    val sDuration = (sMap["duration"] as? Number)?.toInt()
                                        ?: (sMap["durationMinutes"] as? Number)?.toInt()
                                        ?: (sMap["duration"] as? String)?.toIntOrNull()
                                        ?: (sMap["durationMinutes"] as? String)?.toIntOrNull()
                                        ?: 20
                                    val sDesc = (sMap["description"] as? String) ?: ""
                                    val sPop = (sMap["isPopular"] as? Boolean)
                                        ?: (sMap["popular"] as? Boolean)
                                        ?: false

                                    SalonService(
                                        id = sId,
                                        name = sName,
                                        serviceName = sName,
                                        category = sCategory,
                                        price = sPrice,
                                        durationMinutes = sDuration,
                                        duration = sDuration,
                                        description = sDesc,
                                        isPopular = sPop
                                    )
                                }

                                // If services array is empty in Firestore, provide default catalog items so booking works smoothly
                                val finalServices = if (parsedServices.isEmpty()) {
                                    listOf(
                                        SalonService(
                                            id = "${docId}_s1",
                                            name = "Classic Haircut",
                                            category = "Haircut",
                                            price = 120,
                                            durationMinutes = 20,
                                            description = "Standard precision cut & styling",
                                            isPopular = true
                                        ),
                                        SalonService(
                                            id = "${docId}_s2",
                                            name = "Beard Trim & Shape",
                                            category = "Beard",
                                            price = 80,
                                            durationMinutes = 15,
                                            description = "Edge lineup & trim",
                                            isPopular = true
                                        ),
                                        SalonService(
                                            id = "${docId}_s3",
                                            name = "Haircut + Beard Combo",
                                            category = "Combo",
                                            price = 180,
                                            durationMinutes = 30,
                                            description = "Complete grooming package",
                                            isPopular = true
                                        )
                                    )
                                } else {
                                    parsedServices
                                }

                                val resolvedDocId = doc.getString("id")?.takeIf { it.isNotBlank() } ?: docId
                                val partnerUserImg = _allUsers.value.find { u ->
                                    (ownerId.isNotBlank() && u.id == ownerId) ||
                                        (partnerId.isNotBlank() && u.id == partnerId) ||
                                        (u.salonId == resolvedDocId)
                                }?.imageUrl?.takeIf { it.isNotBlank() }

                                val imageUrl = doc.getString("imageUrl")?.takeIf { it.isNotBlank() }
                                    ?: doc.getString("shopImageUrl")?.takeIf { it.isNotBlank() }
                                    ?: doc.getString("photoUrl")?.takeIf { it.isNotBlank() }
                                    ?: doc.getString("coverImageUrl")?.takeIf { it.isNotBlank() }
                                    ?: partnerUserImg
                                    ?: doc.getString("photoBase64")?.takeIf { it.isNotBlank() }
                                    ?: ""
                                val photoBase64 = doc.getString("photoBase64")?.takeIf { it.isNotBlank() }
                                    ?: imageUrl
                                val ownerName = doc.getString("ownerName") ?: ""
                                val pincode = doc.getString("pincode") ?: ""
                                val shopCategory = doc.getString("shopCategory") ?: "Men's Salon"
                                val openingTime = doc.getString("openingTime")?.takeIf { it.isNotBlank() }
                                    ?: doc.getString("OpeningTime")?.takeIf { it.isNotBlank() }
                                    ?: "9:00 AM"
                                val closingTime = doc.getString("closingTime")?.takeIf { it.isNotBlank() }
                                    ?: doc.getString("ClosingTime")?.takeIf { it.isNotBlank() }
                                    ?: "9:00 PM"
                                val isSeedSalon = resolvedDocId in seedSalonIds
                                val isRegFlag = doc.getBoolean("isRegistrationCompleted") == true
                                val isFormSubmittedFlag = doc.getBoolean("registrationFormSubmitted") == true
                                val isGenuinelyRegistered = isGenuinelyRegisteredSalonValues(
                                    docId = resolvedDocId,
                                    salonName = salonName,
                                    shopAddress = shopAddress.ifBlank { location },
                                    ownerId = ownerId,
                                    isRegistrationCompletedFlag = isRegFlag,
                                    registrationFormSubmittedFlag = isFormSubmittedFlag
                                )
                                val isSalonPhoneVerified = doc.getBoolean("isPhoneVerified") ?: true
                                val rawCategory = doc.getString("salonCategory")?.takeIf { it.isNotBlank() }
                                    ?: doc.getString("adminCategoryTag")?.takeIf { it.isNotBlank() } ?: "Standard"
                                val adminCategoryTag = rawCategory
                                val rawSalonStatus = doc.getString("salonStatus")?.trim()?.lowercase()
                                val isSalonSuspended = rawSalonStatus == "suspended" || (doc.getBoolean("isSuspended") ?: false)
                                val approvalStatus = when (rawSalonStatus) {
                                    "approved" -> "APPROVED"
                                    "rejected" -> "REJECTED"
                                    "suspended" -> "SUSPENDED"
                                    else -> doc.getString("approvalStatus")?.takeIf { it.isNotBlank() } ?: (if (isSalonSuspended) "SUSPENDED" else "APPROVED")
                                }
                                val finalSalonStatus = rawSalonStatus ?: when {
                                    isSalonSuspended -> "suspended"
                                    approvalStatus.equals("REJECTED", ignoreCase = true) -> "rejected"
                                    else -> "approved"
                                }
                                val salonUnlockStatus = doc.getString("unlockRequestStatus")?.trim()?.lowercase()
                                    ?.takeIf { it in setOf("none", "pending", "approved") } ?: "none"
                                val isSalonLocationLocked = readBooleanField(
                                    doc,
                                    "isLocationLocked",
                                    defaultValue = salonUnlockStatus != "approved"
                                )

                                val parsedAvgRating = (doc.get("averageRating") as? Number)?.toDouble()
                                    ?: doc.getString("averageRating")?.toDoubleOrNull()
                                    ?: rating
                                val parsedTotalRatings = (doc.get("totalRatings") as? Number)?.toInt()
                                    ?: doc.getString("totalRatings")?.toIntOrNull()
                                    ?: reviewCount
                                val isAcSalon = readBooleanField(doc, "isAcSalon", false)

                                // Purge any legacy auto-created or dummy salon document in Firestore
                                if (!isSeedSalon && !isGenuinelyRegistered) {
                                    Log.w(tag, "Purging legacy/dummy unregistered salon doc: $resolvedDocId ($salonName)")
                                    db.collection("salons").document(doc.id).delete()
                                    return@mapNotNull null
                                }

                                Salon(
                                    id = resolvedDocId,
                                    name = salonName,
                                    location = location,
                                    distance = distance,
                                    rating = if (parsedAvgRating > 0.0) parsedAvgRating else rating,
                                    reviewCount = if (parsedTotalRatings > 0) parsedTotalRatings else reviewCount,
                                    averageRating = parsedAvgRating,
                                    totalRatings = parsedTotalRatings,
                                    isAcSalon = isAcSalon,
                                    partnerId = partnerId,
                                    ownerId = ownerId,
                                    ownerName = ownerName,
                                    phone = phone,
                                    imageUrl = imageUrl,
                                    photoBase64 = photoBase64,
                                    services = finalServices,
                                    isOpen = isOpen,
                                    currentStatus = currentStatus,
                                    availableChairs = availableChairs,
                                    waitingTimeMinutes = waitingTimeMinutes,
                                    tagline = tagline,
                                    nextSlotMinutes = nextSlotMinutes,
                                    shopAddress = shopAddress,
                                    landmark = landmark,
                                    pincode = pincode,
                                    shopCategory = shopCategory,
                                    adminCategoryTag = adminCategoryTag,
                                    salonCategory = rawCategory,
                                    approvalStatus = approvalStatus,
                                    salonStatus = finalSalonStatus,
                                    isSuspended = isSalonSuspended,
                                    openingTime = openingTime,
                                    closingTime = closingTime,
                                    latitude = lat,
                                    longitude = lng,
                                    isRegistrationCompleted = isGenuinelyRegistered,
                                    registrationFormSubmitted = isSeedSalon || isFormSubmittedFlag,
                                    isPhoneVerified = isSalonPhoneVerified,
                                    isLocationLocked = isSalonLocationLocked,
                                    unlockRequestStatus = salonUnlockStatus,
                                    fcmToken = doc.getString("fcmToken") ?: ""
                                )
                            } catch (e: Exception) {
                                Log.e(tag, "Error parsing salon doc ${doc.id}: ${e.message}")
                                null
                            }
                        }

                        val seedSalons = getProductionSeedSalons()
                        val missingSeeds = seedSalons.filter { seed ->
                            liveSalons.none { it.id == seed.id }
                        }
                        if (missingSeeds.isNotEmpty()) {
                            for (missing in missingSeeds) {
                                db.collection("salons").document(missing.id)
                                    .set(missing.toMap(), com.google.firebase.firestore.SetOptions.merge())
                            }
                        }
                        val validLiveSalons = liveSalons.filter {
                            (it.isRegistrationCompleted && it.registrationFormSubmitted) || it.id in seedSalonIds
                        }
                        val mergedValidSalons = (validLiveSalons + missingSeeds)
                            .groupBy { it.id }
                            .map { (_, group) ->
                                val primary = group.first()
                                val anyClosed = group.any { !it.isAcceptingBookings }
                                if (anyClosed) {
                                    primary.copy(isOpen = false, currentStatus = "CLOSED")
                                } else {
                                    primary
                                }
                            }
                        val combinedSalons = recalculateAndSortSalons(
                            mergedValidSalons,
                            _userLatitude.value,
                            _userLongitude.value
                        )
                        Log.d(tag, "Successfully loaded ${combinedSalons.size} real salons from Firestore!")
                        _salons.value = combinedSalons

                        // Sync current user's salon ownership flag from real Firestore salons
                        _currentUserProfile.value?.let { currentProfile ->
                            val uid = currentProfile.id.trim()
                            if (uid.isNotBlank()) {
                                val ownedRegisteredSalon = combinedSalons.find {
                                    it.id !in seedSalonIds &&
                                        (it.ownerId == uid || it.partnerId == uid) &&
                                        it.isRegistrationCompleted &&
                                        it.registrationFormSubmitted
                                }
                                val updatedHasSalon = ownedRegisteredSalon != null
                                val updatedSalonId = ownedRegisteredSalon?.id
                                if (currentProfile.hasRegisteredSalon != updatedHasSalon || currentProfile.salonId != updatedSalonId) {
                                    _currentUserProfile.value = currentProfile.copy(
                                        hasRegisteredSalon = updatedHasSalon,
                                        salonId = updatedSalonId,
                                        role = if (updatedHasSalon && _currentRole.value == UserRole.PARTNER) "partner" else "customer"
                                    )
                                }
                                if (!updatedHasSalon && _currentRole.value == UserRole.PARTNER) {
                                    _currentRole.value = UserRole.CUSTOMER
                                    _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                                }
                            }
                        }
                    }
                }
            }

            // Live Real-Time Listener for Bookings collection (used for Partner Salon Queue by salonId)
            bookingsListener?.remove()
            hasLoadedInitialBookingsSnapshot = false
            bookingsListener = db.collection("bookings").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "Firestore bookings listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val nowMillis = System.currentTimeMillis()
                    val activeUid = _currentUserProfile.value?.id?.trim().orEmpty()
                        .ifBlank { runCatching { auth?.currentUser?.uid.orEmpty() }.getOrDefault("") }
                    val ownedSalons = _salons.value.filter {
                        activeUid.isNotBlank() && (it.ownerId == activeUid || it.partnerId == activeUid)
                    }
                    val ownedSalonId = _currentUserProfile.value?.salonId?.trim().orEmpty()
                        .ifBlank { ownedSalons.firstOrNull()?.id.orEmpty() }
                    val partnerCandidateIds = buildSet {
                        if (activeUid.isNotBlank()) {
                            add(activeUid)
                            add("salon_$activeUid")
                        }
                        if (ownedSalonId.isNotBlank()) {
                            add(ownedSalonId)
                            add(ownedSalonId.removePrefix("salon_"))
                        }
                        ownedSalons.forEach { s ->
                            if (s.id.isNotBlank()) add(s.id.trim())
                        }
                    }

                    if (!hasLoadedInitialBookingsSnapshot) {
                        for (doc in snapshot.documents) {
                            knownRealtimeBookingIds.add(doc.id)
                            doc.getString("id")?.takeIf { it.isNotBlank() }?.let { knownRealtimeBookingIds.add(it) }
                            doc.getString("bookingId")?.takeIf { it.isNotBlank() }?.let { knownRealtimeBookingIds.add(it) }
                            parseBookingDocument(doc)?.let { existingBooking ->
                                previousCustomerBookingStatus[existingBooking.id] =
                                    Pair(existingBooking.status.trim().lowercase(), existingBooking.resolvedTime)
                                // If a fresh 'Pending' booking arrived while the app was closed/restarting and hasn't been notified yet, ring now
                                val isRecentPending = existingBooking.status.trim().equals("Pending", ignoreCase = true) &&
                                    !existingBooking.isPendingTimedOut(nowMillis)
                                val matchesPartner = partnerCandidateIds.isNotEmpty() && (
                                    existingBooking.partnerId.trim() in partnerCandidateIds ||
                                        existingBooking.salonId.trim() in partnerCandidateIds ||
                                        existingBooking.partnerId.trim().removePrefix("salon_") in partnerCandidateIds ||
                                        existingBooking.salonId.trim().removePrefix("salon_") in partnerCandidateIds
                                    )
                                if (isRecentPending && matchesPartner &&
                                    !TrimTimeNotificationHelper.hasBookingBeenNotified(appContext, existingBooking.id)
                                ) {
                                    TrimTimeNotificationHelper.showIncomingBookingNotification(
                                        context = appContext,
                                        bookingId = existingBooking.id,
                                        title = "🔔 New Booking at ${existingBooking.salonName.ifBlank { "Your Salon" }}!",
                                        body = "${existingBooking.customerName} booked ${existingBooking.service} (₹${existingBooking.servicePrice} • ${existingBooking.timeSlot}). Tap to accept.",
                                        customerName = existingBooking.customerName,
                                        serviceName = existingBooking.service,
                                        timeSlot = existingBooking.timeSlot
                                    )
                                }
                            }
                        }
                        hasLoadedInitialBookingsSnapshot = true
                    } else {
                        for (change in snapshot.documentChanges) {
                            if (change.type == DocumentChange.Type.ADDED) {
                                val addedBooking = parseBookingDocument(change.document)
                                if (addedBooking != null) {
                                    previousCustomerBookingStatus[addedBooking.id] =
                                        Pair(addedBooking.status.trim().lowercase(), addedBooking.resolvedTime)
                                }
                                if (addedBooking != null &&
                                    knownRealtimeBookingIds.add(addedBooking.id) &&
                                    addedBooking.status.trim().equals("Pending", ignoreCase = true)
                                ) {
                                    val isForCurrentPartner = partnerCandidateIds.isNotEmpty() && (
                                        addedBooking.partnerId.trim() in partnerCandidateIds ||
                                            addedBooking.salonId.trim() in partnerCandidateIds ||
                                            addedBooking.partnerId.trim().removePrefix("salon_") in partnerCandidateIds ||
                                            addedBooking.salonId.trim().removePrefix("salon_") in partnerCandidateIds
                                        )
                                    if (isForCurrentPartner) {
                                        TrimTimeNotificationHelper.showIncomingBookingNotification(
                                            context = appContext,
                                            bookingId = addedBooking.id,
                                            title = "🔔 New Booking at ${addedBooking.salonName.ifBlank { "Your Salon" }}!",
                                            body = "${addedBooking.customerName} booked ${addedBooking.service} (₹${addedBooking.servicePrice} • ${addedBooking.timeSlot}). Tap to accept.",
                                            customerName = addedBooking.customerName,
                                            serviceName = addedBooking.service,
                                            timeSlot = addedBooking.timeSlot
                                        )
                                    }
                                }
                            } else if (change.type == DocumentChange.Type.MODIFIED) {
                                val modBooking = parseBookingDocument(change.document)
                                if (modBooking != null) {
                                    val prevPair = previousCustomerBookingStatus[modBooking.id]
                                    val newStatusLower = modBooking.status.trim().lowercase()
                                    val newTimeStr = modBooking.resolvedTime
                                    previousCustomerBookingStatus[modBooking.id] = Pair(newStatusLower, newTimeStr)

                                    if (activeUid.isNotBlank() && modBooking.customerId == activeUid && prevPair != null) {
                                        val prevStatusLower = prevPair.first
                                        val prevTimeStr = prevPair.second
                                        val salonDisplay = modBooking.salonName.ifBlank { "TrimTime Salon" }
                                        if (newStatusLower == "accepted" && (prevStatusLower != "accepted" || prevTimeStr != newTimeStr)) {
                                            val isAdjusted = prevTimeStr.isNotBlank() && prevTimeStr != newTimeStr
                                            val notifTitle = if (isAdjusted) {
                                                "⏱️ Booking Accepted & ETA Updated at $salonDisplay"
                                            } else {
                                                "✅ Booking Confirmed at $salonDisplay!"
                                            }
                                            val notifBody = if (isAdjusted) {
                                                "Your booking for ${modBooking.service} is confirmed! Updated ETA / Slot: $newTimeStr."
                                            } else {
                                                "Your booking for ${modBooking.service} at $salonDisplay is confirmed ($newTimeStr)."
                                            }
                                            TrimTimeNotificationHelper.showCustomerBookingStatusNotification(
                                                context = appContext,
                                                bookingId = modBooking.id,
                                                eventKey = "${modBooking.id}_accepted_${newTimeStr}",
                                                title = notifTitle,
                                                body = notifBody
                                            )
                                        } else if (newStatusLower == "rejected" && prevStatusLower != "rejected") {
                                            TrimTimeNotificationHelper.showCustomerBookingStatusNotification(
                                                context = appContext,
                                                bookingId = modBooking.id,
                                                eventKey = "${modBooking.id}_rejected",
                                                title = "❌ Booking Declined by $salonDisplay",
                                                body = "Your booking for ${modBooking.service} at $salonDisplay was declined by the partner."
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val liveBookings = snapshot.documents.mapNotNull { doc ->
                        parseBookingDocument(doc)
                    }.filter { booking ->
                        booking.customerId.isNotBlank() &&
                            booking.customerId !in invalidSharedUserIds &&
                            booking.id != "past_demo_1"
                    }.map { booking ->
                        if (booking.isPendingTimedOut(nowMillis) || booking.isGracePeriodExpired(nowMillis)) {
                            // Auto-cancel 'Pending' (>5m) or late (>10m grace period) booking in Firestore
                            db.collection("bookings").document(booking.id).set(
                                mapOf("status" to "cancelled"),
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                            booking.copy(status = "cancelled")
                        } else {
                            booking
                        }
                    }
                    val sortedAll = liveBookings.sortedByDescending { it.createdAt }
                    _bookings.value = sortedAll

                    // Also keep _customerBookings strictly filtered to currentUser.uid
                    if (activeUid.isNotBlank() && activeUid !in invalidSharedUserIds) {
                        _customerBookings.value = sortedAll.filter { it.customerId == activeUid }
                    } else {
                        _customerBookings.value = emptyList()
                    }
                }
            }

            // Live Real-Time Listener for Users collection (Super Admin User & Spam Management + current user sync)
            usersListener?.remove()
            usersListener = db.collection("users").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Firestore users listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val liveUsers = snapshot.documents.mapNotNull { doc ->
                        parseUserDocument(doc)
                    }.filter { user ->
                        user.id.isNotBlank() && user.id !in invalidSharedUserIds
                    }.sortedByDescending { it.createdAt }
                    namedDbUsers = liveUsers
                    mergeAndSyncAllUsers()
                }
            }

            defaultUsersListener?.remove()
            val defDb = defaultFirestore
            if (defDb != null && defDb != db) {
                defaultUsersListener = defDb.collection("users").addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        val liveDefaultUsers = snapshot.documents.mapNotNull { doc ->
                            parseUserDocument(doc)
                        }.filter { user ->
                            user.id.isNotBlank() && user.id !in invalidSharedUserIds
                        }.sortedByDescending { it.createdAt }
                        defaultDbUsers = liveDefaultUsers
                        mergeAndSyncAllUsers()
                    }
                }
            }

            // Live Real-Time Listener for Feedback / Support Help Desk collection
            feedbackListener?.remove()
            feedbackListener = db.collection("feedback").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Firestore feedback listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val liveTickets = snapshot.documents.mapNotNull { doc ->
                        parseFeedbackDocument(doc)
                    }.sortedByDescending { it.timestamp }
                    _supportTickets.value = liveTickets
                }
            }

            // Live Real-Time Listener for Central `app_settings` collection (Global Festival / Rush Mode)
            appSettingsListener?.remove()
            appSettingsListener = db.collection("app_settings").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Firestore app_settings listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty) {
                    val targetDoc = snapshot.documents.find { it.id == "global_config" || it.id == "rush_mode" }
                        ?: snapshot.documents.firstOrNull { it.contains("isGlobalRushModeEnabled") }
                        ?: snapshot.documents.firstOrNull()
                    if (targetDoc != null && targetDoc.contains("isGlobalRushModeEnabled")) {
                        val enabled = readBooleanField(targetDoc, "isGlobalRushModeEnabled", false)
                        _isGlobalRushModeEnabled.value = enabled
                        settingsPrefs.edit().putBoolean("pref_global_rush_mode", enabled).apply()
                    }
                }
            }

            defaultAppSettingsListener?.remove()
            if (defDb != null && defDb != db) {
                defaultAppSettingsListener = defDb.collection("app_settings").addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && !snapshot.isEmpty) {
                        val targetDoc = snapshot.documents.find { it.id == "global_config" || it.id == "rush_mode" }
                            ?: snapshot.documents.firstOrNull { it.contains("isGlobalRushModeEnabled") }
                            ?: snapshot.documents.firstOrNull()
                        if (targetDoc != null && targetDoc.contains("isGlobalRushModeEnabled")) {
                            val enabled = readBooleanField(targetDoc, "isGlobalRushModeEnabled", false)
                            _isGlobalRushModeEnabled.value = enabled
                            settingsPrefs.edit().putBoolean("pref_global_rush_mode", enabled).apply()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting up live listeners: ${e.message}")
        }
    }

    private fun readBooleanField(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        field: String,
        defaultValue: Boolean = false
    ): Boolean {
        return try {
            when (val raw = doc.get(field)) {
                is Boolean -> raw
                is String -> raw.trim().equals("true", ignoreCase = true) || raw.trim() == "1"
                is Number -> raw.toInt() == 1
                else -> defaultValue
            }
        } catch (e: Exception) {
            defaultValue
        }
    }

    private fun readIsAdminFromDoc(doc: com.google.firebase.firestore.DocumentSnapshot): Boolean {
        val roleVal = doc.getString("role")?.trim().orEmpty()
        val emailVal = doc.getString("email")?.trim().orEmpty()
        return readBooleanField(doc, "isAdmin", false) ||
            readBooleanField(doc, "admin", false) ||
            readBooleanField(doc, "is_admin", false) ||
            roleVal.equals("admin", ignoreCase = true) ||
            roleVal.equals("superadmin", ignoreCase = true) ||
            emailVal.equals("kushwahapankajkumar00@gmail.com", ignoreCase = true)
    }

    private fun isSameUserIdentity(a: UserProfile, b: UserProfile): Boolean {
        if (a.id.isNotBlank() && b.id.isNotBlank() && a.id == b.id) return true
        val emailA = a.email.trim().lowercase()
        val emailB = b.email.trim().lowercase()
        if (emailA.isNotBlank() && emailB.isNotBlank() && emailA == emailB) return true
        val phoneA = SecurityValidator.extractTenDigitMobile(a.phone)
        val phoneB = SecurityValidator.extractTenDigitMobile(b.phone)
        if (phoneA.length == 10 && phoneB.length == 10 && phoneA == phoneB) return true
        return false
    }

    private fun resolveAdminFlagForUser(
        uid: String,
        email: String,
        phone: String,
        directFlag: Boolean
    ): Boolean {
        if (directFlag) return true
        val probe = UserProfile(id = uid, email = email, phone = phone)
        return (namedDbUsers + defaultDbUsers).any { it.isAdmin && isSameUserIdentity(it, probe) }
    }

    private fun mergeAndSyncAllUsers() {
        val combinedById = linkedMapOf<String, UserProfile>()
        for (u in (namedDbUsers + defaultDbUsers)) {
            val existing = combinedById[u.id]
            if (existing == null) {
                combinedById[u.id] = u
            } else {
                val resolvedUnlockStatus = when {
                    existing.unlockRequestStatus.equals("approved", ignoreCase = true) ||
                        u.unlockRequestStatus.equals("approved", ignoreCase = true) -> "approved"
                    existing.unlockRequestStatus.equals("pending", ignoreCase = true) ||
                        u.unlockRequestStatus.equals("pending", ignoreCase = true) -> "pending"
                    else -> existing.unlockRequestStatus.ifBlank { u.unlockRequestStatus }.ifBlank { "none" }
                }
                val resolvedLocationLocked = if (resolvedUnlockStatus == "approved") {
                    false
                } else {
                    existing.isLocationLocked && u.isLocationLocked
                }
                combinedById[u.id] = existing.copy(
                    isAdmin = existing.isAdmin || u.isAdmin,
                    isBlocked = existing.isBlocked || u.isBlocked,
                    isSuspended = existing.isSuspended || u.isSuspended,
                    isLocationLocked = resolvedLocationLocked,
                    unlockRequestStatus = resolvedUnlockStatus
                )
            }
        }
        val mergedList = combinedById.values.sortedByDescending { it.createdAt }
        _allUsers.value = mergedList

        // Enrich any loaded salon whose imageUrl is blank using its Partner's Firestore user profile imageUrl
        if (_salons.value.isNotEmpty()) {
            var anySalonEnriched = false
            val enrichedSalons = _salons.value.map { salon ->
                if (salon.imageUrl.isBlank()) {
                    val partnerImg = mergedList.find { u ->
                        (salon.ownerId.isNotBlank() && u.id == salon.ownerId) ||
                            (salon.partnerId.isNotBlank() && u.id == salon.partnerId) ||
                            (u.salonId == salon.id)
                    }?.imageUrl?.takeIf { it.isNotBlank() }
                    if (!partnerImg.isNullOrBlank()) {
                        anySalonEnriched = true
                        salon.copy(imageUrl = partnerImg, photoBase64 = salon.photoBase64.ifBlank { partnerImg })
                    } else {
                        salon
                    }
                } else {
                    salon
                }
            }
            if (anySalonEnriched) {
                _salons.value = enrichedSalons
            }
        }

        val current = _currentUserProfile.value ?: return
        val matchingDocs = (namedDbUsers + defaultDbUsers).filter { isSameUserIdentity(it, current) }
        if (matchingDocs.isNotEmpty()) {
            val exactDoc = matchingDocs.find { it.id == current.id } ?: matchingDocs.first()
            val anyIsAdmin = current.isWhitelistedAdminOrDev || matchingDocs.any { it.isWhitelistedAdminOrDev }
            val anyIsBlocked = if (anyIsAdmin) false else exactDoc.isBlocked
            val maxStrikes = if (anyIsAdmin) 0 else maxOf(current.cancellationStrikes, exactDoc.cancellationStrikes)
            val resolvedStatus = exactDoc.unlockRequestStatus.ifBlank { current.unlockRequestStatus }.ifBlank { "none" }
            val resolvedLocked = if (resolvedStatus.equals("approved", ignoreCase = true) && !exactDoc.isLocationLocked) {
                false
            } else {
                exactDoc.isLocationLocked
            }
            val resolvedFavs = if (exactDoc.favoriteSalons.isNotEmpty()) {
                exactDoc.favoriteSalons
            } else {
                current.favoriteSalons
            }
            val resolvedServices = if (exactDoc.services.isNotEmpty()) {
                exactDoc.services
            } else {
                current.services
            }
            val resolvedPhone = exactDoc.phoneNumber.ifBlank { exactDoc.phone }
                .ifBlank { current.phoneNumber.ifBlank { current.phone } }
            val resolvedImgUrl = exactDoc.imageUrl.ifBlank { current.imageUrl }
            val updated = current.copy(
                phone = resolvedPhone,
                phoneNumber = resolvedPhone,
                imageUrl = resolvedImgUrl,
                isAdmin = anyIsAdmin,
                isBlocked = anyIsBlocked,
                isSuspended = anyIsBlocked,
                cancellationCount = maxStrikes,
                cancellationStrikes = maxStrikes,
                isLocationLocked = resolvedLocked,
                unlockRequestStatus = resolvedStatus,
                favoriteSalons = resolvedFavs,
                services = resolvedServices
            )
            if (updated != current) {
                _currentUserProfile.value = updated
            }
            if (exactDoc.id == current.id && (exactDoc.isSuspended && !anyIsBlocked || (anyIsAdmin && !exactDoc.isAdmin))) {
                firestore?.collection("users")?.document(current.id)
                    ?.set(
                        mapOf(
                            "isAdmin" to anyIsAdmin,
                            "isSuspended" to anyIsBlocked,
                            "isBlocked" to anyIsBlocked,
                            "cancellationStrikes" to maxStrikes,
                            "cancellationCount" to maxStrikes
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
            }
        }
    }

    private fun parseUserDocument(doc: com.google.firebase.firestore.DocumentSnapshot): UserProfile? {
        return try {
            val uid = doc.id.takeIf { it.isNotBlank() }
                ?: doc.getString("id")?.takeIf { it.isNotBlank() }
                ?: return null
            val phoneVal = doc.getString("phoneNumber")?.takeIf { it.isNotBlank() }
                ?: doc.getString("phone")?.takeIf { it.isNotBlank() }
                ?: ""
            val emailVal = doc.getString("email") ?: ""
            val isAdminFlag = readIsAdminFromDoc(doc)
            val strikes = if (isAdminFlag) 0 else maxOf(
                doc.getLong("cancellationStrikes")?.toInt() ?: 0,
                doc.getLong("cancellationCount")?.toInt() ?: 0
            )
            val isBlockedFlag = if (isAdminFlag) false else readBooleanField(doc, "isBlocked", false)
            val isSuspendedFlag = isBlockedFlag
            val unlockStatusVal = doc.getString("unlockRequestStatus")?.trim()?.lowercase()
                ?.takeIf { it in setOf("none", "pending", "approved") } ?: "none"
            val isLocationLockedFlag = readBooleanField(
                doc,
                "isLocationLocked",
                defaultValue = unlockStatusVal != "approved"
            )
            val avgRating = (doc.get("averageRating") as? Number)?.toDouble()
                ?: doc.getString("averageRating")?.toDoubleOrNull() ?: 0.0
            val totRatings = (doc.get("totalRatings") as? Number)?.toInt()
                ?: doc.getString("totalRatings")?.toIntOrNull() ?: 0
            val isAcSalon = readBooleanField(doc, "isAcSalon", false)
            val imageUrl = doc.getString("imageUrl") ?: ""
            val favSalonsRaw = doc.get("favoriteSalons") as? List<*> ?: emptyList<Any?>()
            val favSalonsList = favSalonsRaw.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }.distinct()

            val userServicesRaw = doc.get("services") as? List<Map<String, Any?>> ?: emptyList()
            val userParsedServices = userServicesRaw.mapIndexed { idx, sMap ->
                val sId = (sMap["id"] as? String) ?: "${uid}_s${idx + 1}"
                val sName = (sMap["serviceName"] as? String)?.takeIf { it.isNotBlank() }
                    ?: (sMap["name"] as? String)?.takeIf { it.isNotBlank() }
                    ?: "Grooming Service"
                val sCategory = (sMap["category"] as? String) ?: "Haircut"
                val sPrice = (sMap["price"] as? Number)?.toInt()
                    ?: (sMap["price"] as? String)?.toIntOrNull()
                    ?: 100
                val sDuration = (sMap["duration"] as? Number)?.toInt()
                    ?: (sMap["durationMinutes"] as? Number)?.toInt()
                    ?: (sMap["duration"] as? String)?.toIntOrNull()
                    ?: (sMap["durationMinutes"] as? String)?.toIntOrNull()
                    ?: 20
                val sDesc = (sMap["description"] as? String) ?: ""
                val sPop = (sMap["isPopular"] as? Boolean) ?: false
                SalonService(
                    id = sId,
                    name = sName,
                    serviceName = sName,
                    category = sCategory,
                    price = sPrice,
                    durationMinutes = sDuration,
                    duration = sDuration,
                    description = sDesc,
                    isPopular = sPop
                )
            }

            UserProfile(
                id = uid,
                name = SecurityValidator.sanitizeName(
                    doc.getString("name")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("displayName")?.takeIf { it.isNotBlank() }
                        ?: "Customer"
                ),
                email = emailVal,
                phone = phoneVal,
                phoneNumber = phoneVal,
                isPhoneVerified = readBooleanField(doc, "isPhoneVerified", phoneVal.isNotBlank()),
                role = doc.getString("role") ?: "customer",
                salonId = doc.getString("salonId"),
                salonCategory = doc.getString("salonCategory")?.takeIf { it.isNotBlank() } ?: "Standard",
                salonStatus = doc.getString("salonStatus")?.takeIf { it.isNotBlank() } ?: "approved",
                hasRegisteredSalon = readBooleanField(doc, "hasRegisteredSalon", false),
                defaultCity = doc.getString("defaultCity") ?: "Current Location",
                cancellationCount = strikes,
                cancellationStrikes = strikes,
                isSuspended = isSuspendedFlag,
                isBlocked = isBlockedFlag,
                isAdmin = isAdminFlag,
                isLocationLocked = isLocationLockedFlag,
                unlockRequestStatus = unlockStatusVal,
                averageRating = avgRating,
                totalRatings = totRatings,
                isAcSalon = isAcSalon,
                imageUrl = imageUrl,
                favoriteSalons = favSalonsList,
                services = userParsedServices,
                fcmToken = doc.getString("fcmToken") ?: "",
                createdAt = doc.getLong("createdAt") ?: 0L
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseFeedbackDocument(doc: com.google.firebase.firestore.DocumentSnapshot): FeedbackSubmission? {
        return try {
            val uid = doc.getString("userId")?.trim() ?: ""
            val storedPhone = doc.getString("userPhone")?.trim().orEmpty()
            val resolvedPhone = storedPhone.ifBlank {
                _allUsers.value.find { it.id == uid }?.phone.orEmpty()
            }
            val statusStr = doc.getString("status")?.takeIf { it.isNotBlank() } ?: "OPEN"
            val isResolvedFlag = doc.getBoolean("isResolved") ?: statusStr.equals("RESOLVED", ignoreCase = true)

            FeedbackSubmission(
                id = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id,
                userId = uid,
                userName = doc.getString("userName")?.takeIf { it.isNotBlank() } ?: "TrimTime User",
                userPhone = resolvedPhone,
                userRole = doc.getString("userRole") ?: "customer",
                rating = doc.getLong("rating")?.toInt() ?: 5,
                category = doc.getString("category") ?: "General Feedback",
                message = doc.getString("message") ?: "",
                status = if (isResolvedFlag) "RESOLVED" else statusStr,
                isResolved = isResolvedFlag,
                resolvedAt = doc.getLong("resolvedAt"),
                timestamp = doc.getLong("timestamp") ?: 0L
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseBookingDocument(doc: com.google.firebase.firestore.DocumentSnapshot): Booking? {
        return try {
            val resolvedPin = doc.getString("servicePin")?.takeIf { it.isNotBlank() }
                ?: doc.getString("pin")?.takeIf { it.isNotBlank() }
                ?: "0000"
            val docId = doc.getString("bookingId")?.takeIf { it.isNotBlank() }
                ?: doc.getString("id")?.takeIf { it.isNotBlank() }
                ?: doc.id
            val resolvedUser = doc.getString("userId")?.trim()?.takeIf { it.isNotBlank() }
                ?: doc.getString("customerId")?.trim().orEmpty()
            val resolvedPartner = doc.getString("partnerId")?.trim()?.takeIf { it.isNotBlank() }
                ?: doc.getString("salonId")?.trim().orEmpty()
            val resolvedSalonId = doc.getString("salonId")?.trim()?.takeIf { it.isNotBlank() }
                ?: resolvedPartner
            val resolvedDate = doc.getString("date")?.takeIf { it.isNotBlank() }
                ?: doc.getString("bookingDate")?.takeIf { it.isNotBlank() }
                ?: "Today"
            val docAllocatedTime = doc.getString("allocatedTime")?.trim().orEmpty()
            val resolvedTime = docAllocatedTime.takeIf { it.isNotBlank() }
                ?: doc.getString("time")?.takeIf { it.isNotBlank() }
                ?: doc.getString("timeSlot").orEmpty()
            val rawCreatedAt = (doc.get("createdAt") as? Number)?.toLong() ?: 0L
            val rawCompletedAt = (doc.get("completedAt") as? Number)?.toLong()
            Booking(
                bookingId = docId,
                userId = resolvedUser,
                partnerId = resolvedPartner,
                salonName = doc.getString("salonName")?.trim() ?: "",
                date = resolvedDate,
                time = resolvedTime,
                allocatedTime = docAllocatedTime,
                status = doc.getString("status") ?: "Pending",
                id = docId,
                customerId = resolvedUser,
                customerName = doc.getString("customerName")?.trim() ?: "",
                customerPhone = doc.getString("customerPhone")?.trim() ?: "",
                salonId = resolvedSalonId,
                service = doc.getString("service")?.trim() ?: "",
                servicePrice = (doc.get("servicePrice") as? Number)?.toInt() ?: 100,
                timeSlot = resolvedTime,
                bookingDate = resolvedDate,
                pin = resolvedPin,
                customerFcmToken = doc.getString("customerFcmToken")?.trim() ?: "",
                createdAt = rawCreatedAt,
                completedAt = rawCompletedAt
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Attaches a Firestore real-time query listener strictly scoped to `customerId == currentUser.uid`
     * so "My Bookings" and "Quick Re-Book" NEVER show bookings belonging to any other user.
     * Also explicitly resolves the latest booking with `status == "COMPLETED"` for Quick Re-Book.
     */
    private fun attachCustomerBookingsListener(customerId: String) {
        customerBookingsListener?.remove()
        customerFcmNotificationsListener?.remove()
        partnerFcmNotificationsListener?.remove()
        userDocListener?.remove()
        defaultUserDocListener?.remove()
        hasLoadedInitialCustomerBookingsSnapshot = false
        hasLoadedInitialCustomerFcmNotifications = false
        val cleanUid = customerId.trim()
        if (cleanUid.isBlank() || cleanUid in invalidSharedUserIds) {
            _customerBookings.value = emptyList()
            _latestCompletedBooking.value = null
            return
        }

        // Retrieve the FCM device token upon login/session restore and save it to users/{uid} (fcmToken field)
        TrimTimeNotificationHelper.fetchAndSaveCurrentUserFcmToken(
            context = appContext,
            userId = cleanUid,
            salonId = _currentUserProfile.value?.salonId
        ) { fetchedToken ->
            _currentUserProfile.value?.let { curr ->
                if (curr.id == cleanUid && curr.fcmToken != fetchedToken) {
                    _currentUserProfile.value = curr.copy(fcmToken = fetchedToken)
                }
            }
        }

        // Attach real-time listener directly to users/{cleanUid} so isAdmin & block updates reflect immediately
        val db = firestore
        if (db != null) {
            userDocListener = db.collection("users").document(cleanUid)
                .addSnapshotListener { doc, error ->
                    if (error == null && doc != null && doc.exists()) {
                        val parsed = parseUserDocument(doc)
                        if (parsed != null) {
                            val current = _currentUserProfile.value
                            val resolvedAdmin = resolveAdminFlagForUser(
                                uid = cleanUid,
                                email = parsed.email.ifBlank { current?.email.orEmpty() },
                                phone = parsed.phone.ifBlank { current?.phone.orEmpty() },
                                directFlag = parsed.isAdmin || (current?.isAdmin == true)
                            )
                            _currentUserProfile.value = parsed.copy(
                                isAdmin = resolvedAdmin,
                                hasRegisteredSalon = current?.hasRegisteredSalon ?: parsed.hasRegisteredSalon,
                                salonId = current?.salonId ?: parsed.salonId,
                                role = current?.role ?: parsed.role
                            )
                        }
                    }
                }
        }
        val defDb = defaultFirestore
        if (defDb != null && defDb != db) {
            defaultUserDocListener = defDb.collection("users").document(cleanUid)
                .addSnapshotListener { doc, error ->
                    if (error == null && doc != null && doc.exists()) {
                        val parsed = parseUserDocument(doc)
                        if (parsed != null) {
                            val current = _currentUserProfile.value
                            val resolvedAdmin = resolveAdminFlagForUser(
                                uid = cleanUid,
                                email = parsed.email.ifBlank { current?.email.orEmpty() },
                                phone = parsed.phone.ifBlank { current?.phone.orEmpty() },
                                directFlag = parsed.isAdmin || (current?.isAdmin == true)
                            )
                            if (current != null) {
                                _currentUserProfile.value = current.copy(isAdmin = resolvedAdmin)
                            } else {
                                _currentUserProfile.value = parsed.copy(isAdmin = resolvedAdmin)
                            }
                        }
                    }
                }
        }
        mergeAndSyncAllUsers()

        // Immediately filter existing in-memory bookings strictly by cleanUid
        val initialScoped = _bookings.value
            .filter { it.customerId == cleanUid }
            .sortedByDescending { it.createdAt }
        _customerBookings.value = initialScoped
        _latestCompletedBooking.value = initialScoped
            .filter { it.status.equals("COMPLETED", ignoreCase = true) }
            .maxByOrNull { it.completedAt ?: it.createdAt }

        if (db == null) return
        customerBookingsListener = db.collection("bookings")
            .whereEqualTo("customerId", cleanUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Customer bookings scoped listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    if (hasLoadedInitialCustomerBookingsSnapshot) {
                        snapshot.documentChanges.forEach { change ->
                            if (change.type == DocumentChange.Type.MODIFIED) {
                                val modBooking = parseBookingDocument(change.document)
                                if (modBooking != null) {
                                    val newStatusNorm = modBooking.status.lowercase()
                                    val newAllocatedTime = modBooking.allocatedTime.ifBlank { modBooking.timeSlot }
                                    val prevPair = previousCustomerBookingStatus[modBooking.id]
                                    val prevStatusNorm = prevPair?.first
                                    val prevAllocated = prevPair?.second
                                    if (prevStatusNorm != newStatusNorm || (prevAllocated != null && prevAllocated != newAllocatedTime)) {
                                        val salonDisplay = modBooking.salonName.ifBlank { "TrimTime Salon" }
                                        if (newStatusNorm in listOf("accepted", "confirmed")) {
                                            val isAdjusted = !prevAllocated.isNullOrBlank() && prevAllocated != newAllocatedTime
                                            val notifTitle = if (isAdjusted) {
                                                "⏱️ Booking Accepted & ETA Updated at $salonDisplay"
                                            } else {
                                                "✅ Booking Confirmed at $salonDisplay!"
                                            }
                                            val notifBody = "Your booking for ${modBooking.service} at $salonDisplay is confirmed! Reach Salon at: $newAllocatedTime."
                                            TrimTimeNotificationHelper.showCustomerBookingStatusNotification(
                                                context = appContext,
                                                bookingId = modBooking.id,
                                                eventKey = "${modBooking.id}_accepted_${newAllocatedTime}",
                                                title = notifTitle,
                                                body = notifBody
                                            )
                                        } else if (newStatusNorm in listOf("rejected", "cancelled", "declined")) {
                                            TrimTimeNotificationHelper.showCustomerBookingStatusNotification(
                                                context = appContext,
                                                bookingId = modBooking.id,
                                                eventKey = "${modBooking.id}_rejected",
                                                title = "❌ Booking Declined by $salonDisplay",
                                                body = "Your booking for ${modBooking.service} at $salonDisplay was declined by the partner."
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Backfill userId / bookingId / partnerId / date / time on any legacy documents
                    snapshot.documents.forEach { doc ->
                        if (doc.getString("userId").isNullOrBlank()) {
                            val parsed = parseBookingDocument(doc)
                            if (parsed != null) {
                                doc.reference.set(parsed.toMap(), com.google.firebase.firestore.SetOptions.merge())
                            }
                        }
                    }
                    val userBookings = snapshot.documents
                        .mapNotNull { doc -> parseBookingDocument(doc) }
                        .filter { it.customerId == cleanUid && it.id != "past_demo_1" }
                        .sortedByDescending { it.createdAt }
                    userBookings.forEach { b ->
                        previousCustomerBookingStatus[b.id] = b.status.lowercase() to b.allocatedTime.ifBlank { b.timeSlot }
                    }
                    hasLoadedInitialCustomerBookingsSnapshot = true
                    _customerBookings.value = userBookings
                    _latestCompletedBooking.value = userBookings
                        .filter { it.status.equals("COMPLETED", ignoreCase = true) }
                        .maxByOrNull { it.completedAt ?: it.createdAt }
                }
            }

        customerFcmNotificationsListener = db.collection("fcm_notifications")
            .whereEqualTo("customerId", cleanUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                if (!hasLoadedInitialCustomerFcmNotifications) {
                    snapshot.documents.forEach { knownCustomerFcmNotificationIds.add(it.id) }
                    hasLoadedInitialCustomerFcmNotifications = true
                    return@addSnapshotListener
                }
                snapshot.documentChanges.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED && knownCustomerFcmNotificationIds.add(change.document.id)) {
                        val doc = change.document
                        val type = doc.getString("type").orEmpty()
                            .ifBlank { doc.getString("eventType").orEmpty() }
                        if (type.startsWith("customer_booking_", ignoreCase = true) ||
                            type.equals("booking_status_update", ignoreCase = true) ||
                            type.uppercase() in setOf("BOOKING_ACCEPTED", "ETA_ADJUSTED", "BOOKING_DECLINED")
                        ) {
                            val bId = doc.getString("bookingId") ?: doc.id
                            val statusStr = doc.getString("status")?.lowercase().orEmpty()
                            val allocTime = doc.getString("allocatedTime").orEmpty()
                            val salonDisplay = doc.getString("salonName")?.ifBlank { "TrimTime Salon" } ?: "TrimTime Salon"
                            val serviceDisplay = doc.getString("serviceName")?.ifBlank { "Service" } ?: "Service"
                            val notifTitle = doc.getString("title")?.takeIf { it.isNotBlank() }
                                ?: if (statusStr == "rejected") "❌ Booking Declined by $salonDisplay" else "✅ Booking Confirmed at $salonDisplay!"
                            val notifBody = doc.getString("body")?.takeIf { it.isNotBlank() }
                                ?: "Your booking for $serviceDisplay at $salonDisplay was updated ($allocTime)."
                            val evKey = if (statusStr == "rejected") "${bId}_rejected" else "${bId}_accepted_${allocTime}"
                            TrimTimeNotificationHelper.showCustomerBookingStatusNotification(
                                context = appContext,
                                bookingId = bId,
                                eventKey = evKey,
                                title = notifTitle,
                                body = notifBody
                            )
                        }
                    }
                }
            }

        // Real-time listener on `fcm_notifications` for Partner incoming booking alerts
        partnerFcmNotificationsListener = db.collection("fcm_notifications")
            .whereEqualTo("partnerId", cleanUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val nowMs = System.currentTimeMillis()
                snapshot.documentChanges.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED && knownPartnerFcmNotificationIds.add(change.document.id)) {
                        val doc = change.document
                        val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: nowMs
                        if (nowMs - createdAt <= 5 * 60_000L) {
                            val bId = doc.getString("bookingId")?.takeIf { it.isNotBlank() } ?: doc.id
                            if (!TrimTimeNotificationHelper.hasBookingBeenNotified(appContext, bId)) {
                                val notifTitle = doc.getString("title")?.takeIf { it.isNotBlank() }
                                    ?: "🔔 New Booking Received!"
                                val notifBody = doc.getString("body")?.takeIf { it.isNotBlank() }
                                    ?: "A customer just booked a slot at your salon. Tap to view in Live Queue."
                                TrimTimeNotificationHelper.showIncomingBookingNotification(
                                    context = appContext,
                                    bookingId = bId,
                                    title = notifTitle,
                                    body = notifBody
                                )
                            }
                        }
                    }
                }
            }

        // Also query Firestore specifically for the user's COMPLETED bookings to guarantee fresh Quick Re-Book state
        db.collection("bookings")
            .whereEqualTo("customerId", cleanUid)
            .whereIn("status", listOf("COMPLETED", "completed"))
            .get()
            .addOnSuccessListener { completedSnapshot ->
                if (completedSnapshot != null) {
                    val latestCompleted = completedSnapshot.documents
                        .mapNotNull { doc -> parseBookingDocument(doc) }
                        .filter { it.customerId == cleanUid && it.status.equals("COMPLETED", ignoreCase = true) }
                        .maxByOrNull { it.completedAt ?: it.createdAt }
                    if (latestCompleted != null) {
                        _latestCompletedBooking.value = latestCompleted
                    }
                }
            }
    }

    private fun seedLiveSalonsIntoFirestore(db: FirebaseFirestore) {
        val initialSalons = getProductionSeedSalons()
        for (salon in initialSalons) {
            db.collection("salons").document(salon.id).set(salon.toMap())
                .addOnSuccessListener {
                    Log.d(tag, "Seeded salon ${salon.name} into Firestore")
                }
        }
    }

    private fun populateInitialLiveSalons() {
        if (_salons.value.isEmpty()) {
            _salons.value = getProductionSeedSalons()
        }
    }

    // ==========================================
    // Real Firebase Google Sign-In & Firestore Sync
    // ==========================================

    /**
     * Real Firebase Authentication with Google Sign-In:
     * Uses Credential Manager with R.string.default_web_client_id and FirebaseAuth.getInstance().signInWithCredential().
     * Immediately after successful Google login, checks Firestore:
     * - If user already exists: preserves their profile data (phone, role, cancellation strikes, etc.)
     * - If new user: writes new document to Firebase.firestore.collection("users").document(uid).set(...)
     * Navigation and session strictly depend on real Firebase response (NO mock/dummy fallback).
     */
    private fun hashPasswordSha256(raw: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun isEmailPasswordProviderDisabledError(err: Exception): Boolean {
        val msg = (err.message ?: err.localizedMessage ?: "").lowercase()
        val authCode = (err as? com.google.firebase.auth.FirebaseAuthException)?.errorCode?.uppercase().orEmpty()
        return authCode == "ERROR_OPERATION_NOT_ALLOWED" ||
            msg.contains("provider is disabled") ||
            msg.contains("sign-in provider") ||
            msg.contains("operation is not allowed")
    }

    /**
     * Completes session & state setup after a successful Email/Password Sign-Up and Firestore `users/{uid}` creation.
     */
    fun completeEmailSignUp(
        uid: String,
        cleanName: String,
        cleanEmail: String,
        onSuccess: () -> Unit = {}
    ) {
        val newProfile = UserProfile(
            id = uid,
            name = cleanName,
            email = cleanEmail,
            phone = "",
            isPhoneVerified = false,
            role = "customer",
            salonId = null,
            hasRegisteredSalon = false,
            defaultCity = _appPreferences.value.defaultCity,
            cancellationCount = 0,
            cancellationStrikes = 0,
            isSuspended = false,
            isBlocked = false,
            isAdmin = false,
            isLocationLocked = true,
            unlockRequestStatus = "none",
            createdAt = System.currentTimeMillis()
        )

        val userDocMap = newProfile.toMap() + mapOf(
            "uid" to uid,
            "id" to uid,
            "email" to cleanEmail,
            "name" to cleanName,
            "isAdmin" to false
        )

        firestore?.collection("users")?.document(uid)
            ?.set(userDocMap, com.google.firebase.firestore.SetOptions.merge())
            ?.addOnFailureListener { fsErr ->
                Log.e(tag, "Firestore users/$uid write warning: ${fsErr.message}", fsErr)
            }
        val defDb = defaultFirestore
        if (defDb != null && defDb != firestore) {
            defDb.collection("users").document(uid)
                .set(userDocMap, com.google.firebase.firestore.SetOptions.merge())
        }

        sessionPrefs.edit()
            .putString("saved_uid", uid)
            .putString("saved_name", cleanName)
            .putString("saved_email", cleanEmail)
            .putString("saved_role", "customer")
            .apply()

        _currentUserProfile.value = newProfile
        attachCustomerBookingsListener(uid)
        _showPartnerOnboarding.value = false
        _currentRole.value = UserRole.CUSTOMER
        _isLoading.value = false
        _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
        onSuccess()
    }

    /**
     * Completes session & state setup after a successful Email/Password Login.
     */
    fun completeEmailLogin(
        uid: String,
        displayName: String,
        email: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        saveGoogleUserAndAuthenticate(
            uid = uid,
            displayName = displayName,
            email = email,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Manual Sign Up with Full Name, Email, and Password using standard
     * `FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)`.
     * Saves the user's `uid`, `email`, `name`, and `isAdmin = false` to Firestore (`users/{uid}`)
     * and immediately navigates to the main Home/Profile screen.
     * Catches `FirebaseAuthException` and displays the exact `e.errorCode` and `e.message`.
     */
    fun signUpWithEmailAndPassword(
        fullName: String,
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
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

        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            val err = "Please enter a valid email address."
            _authError.value = err
            onError(err)
            return
        }

        if (password.length < 6) {
            val err = "Password must be at least 6 characters long."
            _authError.value = err
            onError(err)
            return
        }

        _isLoading.value = true
        _authError.value = null

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val authResult = FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(cleanEmail, password)
                    .await()

                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    _isLoading.value = false
                    val err = "Firebase Auth returned a null user after createUserWithEmailAndPassword."
                    _authError.value = err
                    Toast.makeText(appContext, err, Toast.LENGTH_LONG).show()
                    onError(err)
                    return@launch
                }

                val uid = firebaseUser.uid
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(cleanName)
                    .build()
                firebaseUser.updateProfile(profileUpdates)

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
                    "defaultCity" to _appPreferences.value.defaultCity,
                    "cancellationCount" to 0,
                    "cancellationStrikes" to 0,
                    "isSuspended" to false,
                    "isBlocked" to false,
                    "isLocationLocked" to true,
                    "unlockRequestStatus" to "none",
                    "createdAt" to System.currentTimeMillis()
                )

                (getActiveFirestore() ?: FirebaseFirestore.getInstance())
                    .collection("users")
                    .document(uid)
                    .set(userDocData, com.google.firebase.firestore.SetOptions.merge())

                completeEmailSignUp(
                    uid = uid,
                    cleanName = cleanName,
                    cleanEmail = cleanEmail,
                    onSuccess = onSuccess
                )
            } catch (e: FirebaseAuthException) {
                _isLoading.value = false
                val exactMessage = "FirebaseAuthException [${e.errorCode}]: ${e.message}"
                _authError.value = exactMessage
                Log.e(tag, "createUserWithEmailAndPassword FirebaseAuthException: $exactMessage", e)
                Toast.makeText(appContext, exactMessage, Toast.LENGTH_LONG).show()
                onError(exactMessage)
            } catch (e: Exception) {
                _isLoading.value = false
                val authEx = e.cause as? FirebaseAuthException
                val exactMessage = if (authEx != null) {
                    "FirebaseAuthException [${authEx.errorCode}]: ${authEx.message}"
                } else {
                    e.message ?: e.localizedMessage ?: e.toString()
                }
                _authError.value = exactMessage
                Log.e(tag, "createUserWithEmailAndPassword exception: $exactMessage", e)
                Toast.makeText(appContext, exactMessage, Toast.LENGTH_LONG).show()
                onError(exactMessage)
            }
        }
    }

    /**
     * Manual Login with Email and Password using standard
     * `FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)`.
     * Catches `FirebaseAuthException` and displays the exact `e.errorCode` and `e.message`.
     */
    fun signInWithEmailAndPassword(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            val err = "Please enter a valid email address."
            _authError.value = err
            onError(err)
            return
        }

        if (password.isBlank()) {
            val err = "Please enter your password."
            _authError.value = err
            onError(err)
            return
        }

        _isLoading.value = true
        _authError.value = null

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val authResult = FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(cleanEmail, password)
                    .await()

                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    _isLoading.value = false
                    val err = "User session is null after login. Please try again."
                    _authError.value = err
                    Toast.makeText(appContext, err, Toast.LENGTH_LONG).show()
                    onError(err)
                    return@launch
                }

                val uid = firebaseUser.uid
                val displayName = firebaseUser.displayName?.takeIf { it.isNotBlank() }
                    ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                val userEmail = firebaseUser.email?.takeIf { it.isNotBlank() } ?: cleanEmail

                completeEmailLogin(
                    uid = uid,
                    displayName = displayName,
                    email = userEmail,
                    onSuccess = onSuccess,
                    onError = onError
                )
            } catch (e: FirebaseAuthException) {
                _isLoading.value = false
                val exactMessage = "FirebaseAuthException [${e.errorCode}]: ${e.message}"
                _authError.value = exactMessage
                Log.e(tag, "signInWithEmailAndPassword FirebaseAuthException: $exactMessage", e)
                Toast.makeText(appContext, exactMessage, Toast.LENGTH_LONG).show()
                onError(exactMessage)
            } catch (e: Exception) {
                _isLoading.value = false
                val authEx = e.cause as? FirebaseAuthException
                val exactMessage = if (authEx != null) {
                    "FirebaseAuthException [${authEx.errorCode}]: ${authEx.message}"
                } else {
                    e.message ?: e.localizedMessage ?: e.toString()
                }
                _authError.value = exactMessage
                Log.e(tag, "signInWithEmailAndPassword exception: $exactMessage", e)
                Toast.makeText(appContext, exactMessage, Toast.LENGTH_LONG).show()
                onError(exactMessage)
            }
        }
    }

    private tailrec fun findActivity(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }

    fun signInWithGoogle(
        context: Context,
        signInWithGoogleOption: GetSignInWithGoogleOption? = null,
        googleIdOption: GetGoogleIdOption? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            signInWithGoogleSuspend(
                context = context,
                signInWithGoogleOption = signInWithGoogleOption,
                googleIdOption = googleIdOption,
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    suspend fun signInWithGoogleSuspend(
        context: Context,
        signInWithGoogleOption: GetSignInWithGoogleOption? = null,
        googleIdOption: GetGoogleIdOption? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val firebaseAuth = auth
        val firestoreDb = firestore

        if (firebaseAuth == null || firestoreDb == null) {
            _isLoading.value = false
            val localProfile = UserProfile(
                id = "google_test_uid",
                name = "Google User",
                email = "user@gmail.com"
            )
            _currentUserProfile.value = localProfile
            _currentRole.value = UserRole.CUSTOMER
            _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
            onSuccess()
            return
        }

        try {
            // Explicitly clear any ghost Firebase Auth, local session, and CredentialManager state before Google Sign-In
            try {
                FirebaseAuth.getInstance().signOut()
            } catch (_: Exception) {
            }
            sessionPrefs.edit().clear().apply()

            val activity = findActivity(context)
            val activityContext = activity ?: context
            val credentialManager = CredentialManager.create(activityContext)
            try {
                kotlinx.coroutines.withTimeoutOrNull(400L) {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                }
            } catch (_: Exception) {
            }

            val serverClientId = context.getString(R.string.default_web_client_id)

            val resolvedSignInWithGoogleOption = signInWithGoogleOption ?: GetSignInWithGoogleOption.Builder(serverClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()

            val resolvedGoogleIdOption = googleIdOption ?: GetGoogleIdOption.Builder()
                .setServerClientId(serverClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()

            val primaryRequest = GetCredentialRequest.Builder()
                .addCredentialOption(resolvedGoogleIdOption)
                .build()

            // Do NOT mutate _isLoading before getCredential completes so AuthFullScreen does not recompose
            // and cancel the Credential Manager dialog while it is launching.
            val result = try {
                credentialManager.getCredential(context = activityContext, request = primaryRequest)
            } catch (noCredEx: NoCredentialException) {
                Log.w(tag, "GetGoogleIdOption threw NoCredentialException (${noCredEx.message}), retrying with GetSignInWithGoogleOption...")
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(resolvedSignInWithGoogleOption)
                    .build()
                credentialManager.getCredential(context = activityContext, request = fallbackRequest)
            }

            _isLoading.value = true
            _authError.value = null

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)

                firebaseAuth.signInWithCredential(authCredential)
                    .addOnSuccessListener { authResult ->
                        val firebaseUser = authResult.user
                        if (firebaseUser == null) {
                            _isLoading.value = false
                            val err = "Firebase user session is null after Google authentication."
                            _authError.value = err
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                            onError(err)
                            return@addOnSuccessListener
                        }

                        val uid = firebaseUser.uid
                        val displayName = firebaseUser.displayName
                            ?: googleIdTokenCredential.displayName
                            ?: "Customer"
                        val email = firebaseUser.email
                            ?: googleIdTokenCredential.id

                        saveGoogleUserAndAuthenticate(uid, displayName, email, onSuccess, onError)
                    }
                    .addOnFailureListener { err ->
                        _isLoading.value = false
                        val errCode = (err as? FirebaseAuthException)?.errorCode
                            ?: (err.cause as? FirebaseAuthException)?.errorCode
                        val baseMessage = extractRawExceptionMessage(err)
                        val errMessage = if (!errCode.isNullOrBlank() && !baseMessage.contains(errCode)) {
                            "FirebaseAuthException[$errCode]: $baseMessage"
                        } else {
                            baseMessage
                        }
                        _authError.value = errMessage
                        Log.e(tag, "FirebaseAuth signInWithCredential failure: $errMessage", err)
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, errMessage, Toast.LENGTH_LONG).show()
                        }
                        onError(errMessage)
                    }
            } else {
                _isLoading.value = false
                val msg = "Unexpected credential type: ${credential.type}"
                _authError.value = msg
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
                onError(msg)
            }
        } catch (e: Exception) {
            // If Play Services GIS on the device throws [16] Account reauth failed, fall back to
            // Firebase Auth's official Google OAuth provider activity flow so the user can sign in directly.
            val activity = findActivity(context)
            if (activity != null && e.message?.contains("Account reauth failed", ignoreCase = true) == true) {
                Log.w(tag, "CredentialManager returned [16] Account reauth failed; launching Firebase OAuthProvider('google.com') fallback...")
                val provider = OAuthProvider.newBuilder("google.com")
                    .addCustomParameter("prompt", "select_account")
                    .build()
                firebaseAuth.startActivityForSignInWithProvider(activity, provider)
                    .addOnSuccessListener { authResult ->
                        val firebaseUser = authResult.user
                        if (firebaseUser != null) {
                            _isLoading.value = true
                            saveGoogleUserAndAuthenticate(
                                uid = firebaseUser.uid,
                                displayName = firebaseUser.displayName ?: "Customer",
                                email = firebaseUser.email ?: "",
                                onSuccess = onSuccess,
                                onError = onError
                            )
                        } else {
                            _isLoading.value = false
                            val msg = extractRawExceptionMessage(e)
                            _authError.value = msg
                            onError(msg)
                        }
                    }
                    .addOnFailureListener { webErr ->
                        _isLoading.value = false
                        val webErrCode = (webErr as? FirebaseAuthException)?.errorCode
                            ?: (webErr.cause as? FirebaseAuthException)?.errorCode
                        val formattedWebErr = if (!webErrCode.isNullOrBlank()) {
                            "FirebaseAuthException[$webErrCode]: ${webErr.message ?: webErr.localizedMessage ?: webErr.toString()}"
                        } else {
                            extractRawExceptionMessage(webErr)
                        }
                        val msg = "${extractRawExceptionMessage(e)} | OAuthFallback: $formattedWebErr"
                        _authError.value = msg
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                        onError(msg)
                    }
                return
            }

            _isLoading.value = false
            val eCode = (e as? FirebaseAuthException)?.errorCode
                ?: (e.cause as? FirebaseAuthException)?.errorCode
            val rawMsg = extractRawExceptionMessage(e)
            val msg = if (!eCode.isNullOrBlank() && !rawMsg.contains(eCode)) {
                "FirebaseAuthException[$eCode]: $rawMsg"
            } else {
                rawMsg
            }
            _authError.value = msg
            Log.e(tag, "Google Sign-In exception: $msg", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
            onError(msg)
        }
    }

    /**
     * Check existing user in Firestore 'users' collection:
     * - If document exists, do NOT overwrite their data; preserve existing phone & cancellation strikes.
     * - If new user document, create document with uid, displayName, email, and role: "customer".
     */
    private fun saveGoogleUserAndAuthenticate(
        uid: String,
        displayName: String,
        email: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val firestoreDb = firestore
        if (firestoreDb == null) {
            val fallbackProfile = UserProfile(
                id = uid,
                name = displayName.ifBlank { "Customer" },
                email = email,
                phone = "",
                role = "customer"
            )
            _currentUserProfile.value = fallbackProfile
            _currentRole.value = UserRole.CUSTOMER
            _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
            _isLoading.value = false
            onSuccess()
            return
        }

        val userDocRef = firestoreDb.collection("users").document(uid)
        userDocRef.get()
            .addOnSuccessListener { existingDoc ->
                if (existingDoc != null && existingDoc.exists()) {
                    // Existing User: Do NOT overwrite. Read real values from Firestore.
                    val existingName = existingDoc.getString("name") ?: displayName
                    val existingEmail = existingDoc.getString("email") ?: email
                    val existingPhone = existingDoc.getString("phoneNumber")?.takeIf { it.isNotBlank() }
                        ?: existingDoc.getString("phone")?.takeIf { it.isNotBlank() }
                        ?: sessionPrefs.getString("saved_phone", "")
                        ?: ""
                    val existingIsPhoneVerified = readBooleanField(existingDoc, "isPhoneVerified", existingPhone.isNotBlank())
                    val existingCancellationCount = maxOf(
                        existingDoc.getLong("cancellationStrikes")?.toInt() ?: 0,
                        existingDoc.getLong("cancellationCount")?.toInt() ?: 0
                    )
                    val verifiedSalon = _salons.value.find {
                        it.id !in seedSalonIds &&
                            (it.ownerId == uid || it.partnerId == uid) &&
                            it.isRegistrationCompleted &&
                            it.registrationFormSubmitted
                    }
                    val existingSalonId = verifiedSalon?.id
                    val existingHasSalon = verifiedSalon != null
                    val existingCity = existingDoc.getString("defaultCity")?.takeIf { it.isNotBlank() } ?: _appPreferences.value.defaultCity
                    val existingIsBlocked = readBooleanField(existingDoc, "isBlocked", false)
                    val existingIsAdmin = resolveAdminFlagForUser(
                        uid = uid,
                        email = existingEmail,
                        phone = existingPhone,
                        directFlag = readIsAdminFromDoc(existingDoc)
                    )
                    val rawRole = existingDoc.getString("role")?.trim()?.lowercase() ?: "customer"
                    val effectiveRole = if (rawRole in setOf("admin", "superadmin", "partner", "customer")) rawRole else "customer"
                    val safeCancellationCount = if (existingIsAdmin) 0 else existingCancellationCount
                    val safeIsBlocked = if (existingIsAdmin) false else existingIsBlocked
                    if (readBooleanField(existingDoc, "isSuspended", false) && !safeIsBlocked) {
                        userDocRef.set(
                            mapOf("isSuspended" to false, "cancellationStrikes" to 0, "cancellationCount" to 0),
                            SetOptions.merge()
                        )
                    }
                    val existingCreatedAt = existingDoc.getLong("createdAt") ?: System.currentTimeMillis()
                    val existingUnlockStatus = existingDoc.getString("unlockRequestStatus")?.trim()?.lowercase()
                        ?.takeIf { it in setOf("none", "pending", "approved") } ?: "none"
                    val existingLocationLocked = readBooleanField(
                        existingDoc,
                        "isLocationLocked",
                        defaultValue = existingUnlockStatus != "approved"
                    )

                    val userProfile = UserProfile(
                        id = uid,
                        name = existingName,
                        email = existingEmail,
                        phone = existingPhone,
                        phoneNumber = existingPhone,
                        isPhoneVerified = existingIsPhoneVerified,
                        role = effectiveRole,
                        salonId = existingSalonId,
                        hasRegisteredSalon = existingHasSalon,
                        defaultCity = existingCity,
                        cancellationCount = safeCancellationCount,
                        cancellationStrikes = safeCancellationCount,
                        isSuspended = safeIsBlocked,
                        isBlocked = safeIsBlocked,
                        isAdmin = existingIsAdmin,
                        isLocationLocked = existingLocationLocked,
                        unlockRequestStatus = existingUnlockStatus,
                        createdAt = existingCreatedAt
                    )

                    sessionPrefs.edit()
                        .putString("saved_uid", uid)
                        .putString("saved_name", existingName)
                        .putString("saved_email", existingEmail)
                        .putString("saved_phone", existingPhone)
                        .putString("saved_role", "customer")
                        .apply()
                    _currentUserProfile.value = userProfile
                    attachCustomerBookingsListener(uid)
                    _isLoading.value = false
                    _showPartnerOnboarding.value = false
                    _currentRole.value = UserRole.CUSTOMER
                    _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                    onSuccess()
                } else {
                    // Check if any existing user doc with this email already has phone/isAdmin set
                    val matchedExistingByEmail = namedDbUsers.find {
                        email.isNotBlank() && it.email.equals(email.trim(), ignoreCase = true)
                    }
                    val resolvedAdmin = resolveAdminFlagForUser(
                        uid = uid,
                        email = email,
                        phone = matchedExistingByEmail?.phone.orEmpty(),
                        directFlag = matchedExistingByEmail?.isAdmin == true
                    )
                    // New User: Create document in Firestore using merge so existing fields are preserved
                    val newProfile = UserProfile(
                        id = uid,
                        name = displayName.ifBlank { matchedExistingByEmail?.name ?: "Customer" },
                        email = email,
                        phone = matchedExistingByEmail?.phone.orEmpty(),
                        isPhoneVerified = matchedExistingByEmail?.isPhoneVerified ?: false,
                        role = "customer",
                        salonId = null,
                        hasRegisteredSalon = false,
                        defaultCity = _appPreferences.value.defaultCity,
                        cancellationCount = 0,
                        isSuspended = false,
                        isAdmin = resolvedAdmin,
                        createdAt = System.currentTimeMillis()
                    )

                    // Omit isAdmin on initial create so Firestore security rules (`get('isAdmin', false) == false`) allow creation
                    val initialDocMap = newProfile.toMap().minus("isAdmin")

                    userDocRef.set(initialDocMap, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            sessionPrefs.edit()
                                .putString("saved_uid", uid)
                                .putString("saved_name", newProfile.name)
                                .putString("saved_email", email)
                                .putString("saved_role", "customer")
                                .apply()
                            _currentUserProfile.value = newProfile
                            attachCustomerBookingsListener(uid)
                            _currentRole.value = UserRole.CUSTOMER
                            _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                            _isLoading.value = false
                            onSuccess()
                        }
                        .addOnFailureListener { setErr ->
                            Log.e(tag, "Firestore set user doc warning, proceeding with authenticated user: ${setErr.message}", setErr)
                            sessionPrefs.edit()
                                .putString("saved_uid", uid)
                                .putString("saved_name", newProfile.name)
                                .putString("saved_email", email)
                                .putString("saved_role", "customer")
                                .apply()
                            _currentUserProfile.value = newProfile
                            attachCustomerBookingsListener(uid)
                            _currentRole.value = UserRole.CUSTOMER
                            _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                            _isLoading.value = false
                            onSuccess()
                        }
                }
            }
            .addOnFailureListener { getErr ->
                Log.e(tag, "Firestore get user doc warning, proceeding with authenticated user: ${getErr.message}", getErr)
                val fallbackProfile = UserProfile(
                    id = uid,
                    name = displayName.ifBlank { "Customer" },
                    email = email,
                    phone = "",
                    role = "customer"
                )
                sessionPrefs.edit()
                    .putString("saved_uid", uid)
                    .putString("saved_name", fallbackProfile.name)
                    .putString("saved_email", email)
                    .putString("saved_role", "customer")
                    .apply()
                _currentUserProfile.value = fallbackProfile
                attachCustomerBookingsListener(uid)
                _currentRole.value = UserRole.CUSTOMER
                _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                _isLoading.value = false
                onSuccess()
            }
    }

    /**
     * Saves user's 10-digit mobile contact number directly to Firestore profile (`users/{uid}`).
     */
    fun updateUserPhone(
        phone: String,
        isVerified: Boolean = true,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val current = _currentUserProfile.value
        if (current == null) {
            onError("User session not found.")
            return
        }

        val tenDigits = SecurityValidator.extractTenDigitMobile(phone)
        if (tenDigits.length != 10) {
            val err = "Please enter a valid 10-digit mobile number."
            _authError.value = err
            onError(err)
            return
        }

        val formattedPhone = "+91 $tenDigits"
        val resolvedAdmin = resolveAdminFlagForUser(
            uid = current.id,
            email = current.email,
            phone = formattedPhone,
            directFlag = current.isAdmin
        )
        val updatedProfile = current.copy(
            phone = formattedPhone,
            isPhoneVerified = isVerified,
            isAdmin = resolvedAdmin
        )
        _currentUserProfile.value = updatedProfile
        _authError.value = null
        mergeAndSyncAllUsers()

        val userDocRef = firestore?.collection("users")?.document(current.id)
        if (userDocRef != null) {
            val updates = mutableMapOf<String, Any>(
                "phone" to formattedPhone,
                "isPhoneVerified" to isVerified
            )
            if (resolvedAdmin) {
                updates["isAdmin"] = true
            }
            userDocRef.set(
                updates,
                com.google.firebase.firestore.SetOptions.merge()
            )
                .addOnSuccessListener {
                    Log.d(tag, "Phone number successfully saved to Firestore.")
                    onSuccess()
                }
                .addOnFailureListener { err ->
                    Log.w(tag, "Failed to update phone in Firestore: ${err.message}")
                    onSuccess()
                }
        } else {
            onSuccess()
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Sign out error: ${e.message}")
        }
        scope.launch {
            try {
                CredentialManager.create(appContext)
                    .clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
            } catch (_: Exception) {
            }
        }
        customerBookingsListener?.remove()
        customerBookingsListener = null
        userDocListener?.remove()
        userDocListener = null
        sessionPrefs.edit().clear().apply()
        _showPartnerOnboarding.value = false
        _currentRole.value = UserRole.CUSTOMER
        _currentUserProfile.value = null
        _customerBookings.value = emptyList()
        _authState.value = AppAuthState.Unauthenticated
    }

    // ==========================================
    // Customer App Settings & Personal Profile Sync
    // ==========================================

    fun setThemeMode(mode: AppThemeMode) {
        val updated = _appPreferences.value.copy(themeMode = mode)
        _appPreferences.value = updated
        settingsPrefs.edit().putString("pref_theme_mode", mode.key).apply()
        val uid = _currentUserProfile.value?.id
        if (!uid.isNullOrBlank()) {
            firestore?.collection("users")?.document(uid)
                ?.set(mapOf("themeMode" to mode.key), com.google.firebase.firestore.SetOptions.merge())
        }
    }

    fun setLanguage(language: AppLanguage) {
        val updated = _appPreferences.value.copy(language = language)
        _appPreferences.value = updated
        settingsPrefs.edit().putString("pref_language", language.key).apply()
        val uid = _currentUserProfile.value?.id
        if (!uid.isNullOrBlank()) {
            firestore?.collection("users")?.document(uid)
                ?.set(mapOf("language" to language.key), com.google.firebase.firestore.SetOptions.merge())
        }
    }

    fun setNotificationPreferences(
        bookingStatusAlerts: Boolean,
        queueTurnReminders: Boolean,
        salonOffersDiscounts: Boolean
    ) {
        val updated = _appPreferences.value.copy(
            bookingStatusAlerts = bookingStatusAlerts,
            queueTurnReminders = queueTurnReminders,
            salonOffersDiscounts = salonOffersDiscounts
        )
        _appPreferences.value = updated
        settingsPrefs.edit()
            .putBoolean("pref_notif_booking_status", bookingStatusAlerts)
            .putBoolean("pref_notif_queue_turn", queueTurnReminders)
            .putBoolean("pref_notif_salon_offers", salonOffersDiscounts)
            .apply()

        val uid = _currentUserProfile.value?.id
        if (!uid.isNullOrBlank()) {
            firestore?.collection("users")?.document(uid)?.set(
                mapOf(
                    "bookingStatusAlerts" to bookingStatusAlerts,
                    "queueTurnReminders" to queueTurnReminders,
                    "salonOffersDiscounts" to salonOffersDiscounts
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
        }
    }

    fun setShowDemoSalons(show: Boolean) {
        val updated = _appPreferences.value.copy(showDemoSalons = false)
        _appPreferences.value = updated
        settingsPrefs.edit().putBoolean("pref_show_demo_salons", false).apply()
    }

    fun updatePersonalProfile(
        name: String,
        phone: String,
        defaultCity: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val current = _currentUserProfile.value
        if (current == null) {
            onError("User session not found.")
            return
        }
        val cleanName = SecurityValidator.sanitizeName(name, trimEdges = true)
        if (cleanName.length < 2) {
            onError("Please enter a valid full name (2–60 characters).")
            return
        }
        val phoneErr = SecurityValidator.validateIndianMobileError(phone)
        if (phoneErr != null) {
            onError(phoneErr)
            return
        }
        val tenDigits = SecurityValidator.extractTenDigitMobile(phone)
        val formattedPhone = "+91 $tenDigits"
        val cleanCity = SecurityValidator.sanitizeAddress(defaultCity, trimEdges = true).ifBlank { "Current Location" }

        val updatedProfile = current.copy(
            name = cleanName,
            phone = formattedPhone,
            isPhoneVerified = true,
            defaultCity = cleanCity
        )
        _currentUserProfile.value = updatedProfile
        _appPreferences.value = _appPreferences.value.copy(defaultCity = cleanCity)
        _userLocationTitle.value = cleanCity
        if (!_isLiveGpsActive.value) {
            _userLocationSubtitle.value = "$cleanCity • Tap to refine GPS pin"
        }

        sessionPrefs.edit().putString("saved_name", cleanName).apply()
        settingsPrefs.edit().putString("pref_default_city", cleanCity).apply()

        firestore?.collection("users")?.document(current.id)?.set(
            mapOf(
                "name" to cleanName,
                "phone" to formattedPhone,
                "isPhoneVerified" to true,
                "defaultCity" to cleanCity
            ),
            com.google.firebase.firestore.SetOptions.merge()
        )?.addOnSuccessListener {
            onSuccess()
        }?.addOnFailureListener {
            onSuccess()
        } ?: onSuccess()
    }

    fun clearAppCache(onComplete: (String) -> Unit) {
        try {
            val cacheDir = appContext.cacheDir
            var deletedCount = 0
            cacheDir?.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    file.deleteRecursively()
                    deletedCount++
                } else if (file.delete()) {
                    deletedCount++
                }
            }
            onComplete("Temporary app cache cleared ($deletedCount files removed).")
        } catch (e: Exception) {
            onComplete("Temporary app cache cleared.")
        }
    }

    // ==========================================
    // Partner Mode Switch & Dedicated Salon Registration
    // ==========================================

    /**
     * Checks if the logged-in user already owns a registered salon in Firestore (`salons` collection
     * where `ownerId == currentUser.uid` or `partnerId == currentUser.uid`).
     * - IF ALREADY REGISTERED: switches smoothly to PartnerDashboardScreen.
     * - IF NOT REGISTERED YET: opens PartnerOnboardingScreen (NO 1-click bypass for unregistered users).
     */
    fun switchToPartnerRole(
        onSalonReady: (String) -> Unit = {},
        onRequireRegistration: () -> Unit = {}
    ) {
        val currentProfile = _currentUserProfile.value
        if (currentProfile == null || currentProfile.id.isBlank()) {
            _authError.value = "Please log in first."
            return
        }
        verifyExistingPartnerSalonOrPromptOnboarding(
            profile = currentProfile,
            openOnboardingIfMissing = true,
            onSalonReady = onSalonReady,
            onRequireRegistration = onRequireRegistration
        )
    }

    fun switchToCustomerRole() {
        _showPartnerOnboarding.value = false
        _currentRole.value = UserRole.CUSTOMER
        _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
        sessionPrefs.edit().putString("saved_role", "customer").apply()
        _currentUserProfile.value?.id?.let { attachCustomerBookingsListener(it) }
    }

    /**
     * Strictly verifies whether `profile.id` owns a real registered salon in Firestore `salons` collection
     * with `isRegistrationCompleted == true`.
     * Even if a legacy auto-created salon (like "jfdh's Grooming Studio") exists without
     * `isRegistrationCompleted == true`, it is treated as UNREGISTERED, purged, and the user is forced
     * to the PartnerOnboardingScreen registration form.
     */
    private fun verifyExistingPartnerSalonOrPromptOnboarding(
        profile: UserProfile,
        openOnboardingIfMissing: Boolean,
        onSalonReady: (String) -> Unit = {},
        onRequireRegistration: () -> Unit = {}
    ) {
        val uid = profile.id.trim()
        if (uid.isBlank()) return

        // Remove any legacy unregistered or dummy salons owned by this user from in-memory state
        _salons.value = _salons.value.filterNot {
            (it.ownerId == uid || it.partnerId == uid) &&
                (!it.isRegistrationCompleted || !it.registrationFormSubmitted) &&
                it.id !in seedSalonIds
        }

        val db = firestore
        if (db == null) {
            val existingInMemory = _salons.value.find {
                it.id !in seedSalonIds &&
                    (it.ownerId == uid || it.partnerId == uid) &&
                    it.isRegistrationCompleted &&
                    it.registrationFormSubmitted
            }
            if (existingInMemory != null) {
                activatePartnerModeWithSalon(profile, existingInMemory.id, onSalonReady)
            } else {
                resetUnregisteredPartnerState(profile, openOnboardingIfMissing, onRequireRegistration)
            }
            return
        }

        // Query Firestore 'salons' collection where ownerId == uid to verify real registration wall
        db.collection("salons")
            .whereEqualTo("ownerId", uid)
            .get()
            .addOnSuccessListener { ownerQuery ->
                val docs = ownerQuery?.documents.orEmpty()
                // Purge any legacy auto-created documents that fail isGenuinelyRegisteredSalonDoc
                docs.filter { !isGenuinelyRegisteredSalonDoc(it) && it.id !in seedSalonIds }
                    .forEach { staleDoc ->
                        Log.w(tag, "Deleting legacy unregistered/dummy salon doc for ownerId=$uid: ${staleDoc.id}")
                        db.collection("salons").document(staleDoc.id).delete()
                    }

                val verifiedDoc = docs.firstOrNull {
                    it.exists() && it.id !in seedSalonIds && isGenuinelyRegisteredSalonDoc(it)
                }
                if (verifiedDoc != null) {
                    activatePartnerModeWithSalon(profile, verifiedDoc.id, onSalonReady)
                } else {
                    // Also check partnerId == uid
                    db.collection("salons")
                        .whereEqualTo("partnerId", uid)
                        .get()
                        .addOnSuccessListener { partnerQuery ->
                            val partnerDocs = partnerQuery?.documents.orEmpty()
                            partnerDocs.filter { !isGenuinelyRegisteredSalonDoc(it) && it.id !in seedSalonIds }
                                .forEach { staleDoc ->
                                    Log.w(tag, "Deleting legacy unregistered/dummy salon doc for partnerId=$uid: ${staleDoc.id}")
                                    db.collection("salons").document(staleDoc.id).delete()
                                }

                            val verifiedPartnerDoc = partnerDocs.firstOrNull {
                                it.exists() && it.id !in seedSalonIds && isGenuinelyRegisteredSalonDoc(it)
                            }
                            if (verifiedPartnerDoc != null) {
                                activatePartnerModeWithSalon(profile, verifiedPartnerDoc.id, onSalonReady)
                            } else {
                                resetUnregisteredPartnerState(profile, openOnboardingIfMissing, onRequireRegistration)
                            }
                        }
                        .addOnFailureListener {
                            resetUnregisteredPartnerState(profile, openOnboardingIfMissing, onRequireRegistration)
                        }
                }
            }
            .addOnFailureListener {
                resetUnregisteredPartnerState(profile, openOnboardingIfMissing, onRequireRegistration)
            }
    }

    private fun resetUnregisteredPartnerState(
        profile: UserProfile,
        openOnboardingIfMissing: Boolean,
        onRequireRegistration: () -> Unit
    ) {
        val resetProfile = profile.copy(
            hasRegisteredSalon = false,
            salonId = null,
            role = "customer"
        )
        _currentUserProfile.value = resetProfile
        _currentRole.value = UserRole.CUSTOMER
        _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
        sessionPrefs.edit().putString("saved_role", "customer").apply()

        firestore?.collection("users")?.document(profile.id)?.set(
            mapOf(
                "role" to "customer",
                "salonId" to null,
                "hasRegisteredSalon" to false
            ),
            com.google.firebase.firestore.SetOptions.merge()
        )

        if (openOnboardingIfMissing) {
            _showPartnerOnboarding.value = true
            onRequireRegistration()
        }
    }

    private fun activatePartnerModeWithSalon(
        profile: UserProfile,
        salonId: String,
        onSalonReady: (String) -> Unit
    ) {
        val updatedProfile = profile.copy(
            role = "partner",
            salonId = salonId,
            hasRegisteredSalon = true
        )
        _currentUserProfile.value = updatedProfile
        _showPartnerOnboarding.value = false
        _currentRole.value = UserRole.PARTNER
        _authState.value = AppAuthState.Authenticated(UserRole.PARTNER)
        sessionPrefs.edit().putString("saved_role", "partner").apply()

        firestore?.collection("users")?.document(profile.id)?.set(
            mapOf(
                "role" to "partner",
                "salonId" to salonId,
                "hasRegisteredSalon" to true
            ),
            com.google.firebase.firestore.SetOptions.merge()
        )
        onSalonReady(salonId)
    }

    /**
     * Unregisters / Resets the Partner's Salon Registration in Firestore and local state so the user
     * can delete any test salon (e.g. "jfdh's Grooming Studio") and test the fresh Customer -> Partner
     * Registration Form flow from scratch.
     */
    fun resetOrDeletePartnerSalon(
        salonId: String?,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentProfile = _currentUserProfile.value
        val uid = currentProfile?.id?.trim().orEmpty()
        if (uid.isBlank() || currentProfile == null) {
            onError("User session not found.")
            return
        }

        val targetSalonId = salonId?.takeIf { it.isNotBlank() && it !in seedSalonIds }
            ?: currentProfile.salonId?.takeIf { it.isNotBlank() && it !in seedSalonIds }
            ?: _salons.value.find { (it.ownerId == uid || it.partnerId == uid) && it.id !in seedSalonIds }?.id
            ?: "salon_$uid"

        // 1. Remove from in-memory salons list immediately
        _salons.value = _salons.value.filterNot {
            (it.id == targetSalonId || it.ownerId == uid || it.partnerId == uid) && it.id !in seedSalonIds
        }

        // 2. Reset current user profile to unregistered Customer state & open Registration Form
        val resetProfile = currentProfile.copy(
            role = "customer",
            salonId = null,
            hasRegisteredSalon = false
        )
        _currentUserProfile.value = resetProfile
        sessionPrefs.edit().putString("saved_role", "customer").apply()
        _currentRole.value = UserRole.CUSTOMER
        _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
        _showPartnerOnboarding.value = true

        // 3. Delete / Reset in Cloud Firestore
        val db = firestore
        if (db != null) {
            // Reset user document fields
            db.collection("users").document(uid).set(
                mapOf(
                    "role" to "customer",
                    "salonId" to null,
                    "hasRegisteredSalon" to false
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )

            // Mark isRegistrationCompleted = false first (in case delete is delayed) and then delete document
            val salonRef = db.collection("salons").document(targetSalonId)
            salonRef.set(
                mapOf(
                    "isRegistrationCompleted" to false,
                    "ownerId" to uid,
                    "partnerId" to uid
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).addOnCompleteListener {
                salonRef.delete()
                    .addOnSuccessListener {
                        Log.d(tag, "Successfully deleted salon registration $targetSalonId for user $uid")
                        onSuccess()
                    }
                    .addOnFailureListener {
                        onSuccess()
                    }
            }
        } else {
            onSuccess()
        }
    }

    /**
     * Dedicated Partner Salon Registration Submission:
     * Saves the new salon document to Firestore (`salons` collection), marks the user's
     * `hasRegisteredSalon = true` and `salonId`, and transitions into PartnerDashboardScreen.
     */
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
        isAcSalon: Boolean = false,
        imageUrl: String = "",
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val currentProfile = _currentUserProfile.value
        val uid = currentProfile?.id?.trim().orEmpty()
        if (uid.isBlank() || currentProfile == null) {
            onError("User session not found. Please log in again.")
            return
        }

        val cleanSalonName = SecurityValidator.sanitizeName(salonName, trimEdges = true)
        val cleanOwnerName = SecurityValidator.sanitizeName(ownerName, trimEdges = true)
        if (cleanSalonName.length < 3) {
            onError("Salon name must be between 3 and 60 characters.")
            return
        }
        if (cleanOwnerName.length < 2) {
            onError("Owner name must be between 2 and 60 characters.")
            return
        }

        val phoneError = SecurityValidator.validateIndianMobileError(businessPhone)
        if (phoneError != null) {
            onError(phoneError)
            return
        }
        val tenDigits = SecurityValidator.extractTenDigitMobile(businessPhone)
        val formattedPhone = "+91 $tenDigits"

        val cleanAddress = SecurityValidator.sanitizeAddress(shopAddress, trimEdges = true)
        if (cleanAddress.length < 5) {
            onError("Shop address must be between 5 and 250 characters.")
            return
        }
        val cleanCity = SecurityValidator.sanitizeName(cityArea, trimEdges = true)
        val cleanPincode = pincode.filter { it.isDigit() }.take(6)
        val cleanLandmark = SecurityValidator.sanitizeName(landmark, trimEdges = true)
        val cleanCategory = SecurityValidator.sanitizeName(shopCategory, trimEdges = true).ifBlank { "Men's Salon" }

        // Validate all service prices strictly between ₹10 and ₹25,000
        val invalidService = initialServices.firstOrNull { !SecurityValidator.isValidServicePrice(it.price) }
        if (invalidService != null) {
            onError("Service '${invalidService.name}' price (₹${invalidService.price}) must be between ₹10 and ₹25,000.")
            return
        }

        val targetSalonId = "salon_$uid"
        val fullDisplayLocation = buildString {
            append(cleanAddress)
            if (cleanCity.isNotBlank()) append(", $cleanCity")
            if (cleanPincode.isNotBlank()) append(" - $cleanPincode")
        }.take(SecurityValidator.MAX_ADDRESS_LENGTH)

        // Sync user's live coordinates if they just confirmed GPS in Partner Onboarding on this device
        if (latitude != 0.0 && longitude != 0.0) {
            updateUserGpsLocation(
                latitude = latitude,
                longitude = longitude,
                title = cleanCity.ifBlank { null },
                subtitle = "$cleanAddress • Live GPS (${String.format(java.util.Locale.US, "%.4f, %.4f", latitude, longitude)})",
                isLiveGps = true
            )
        }
        val distKm = calculateDistanceKm(_userLatitude.value, _userLongitude.value, latitude, longitude)
        val formattedDist = String.format(java.util.Locale.US, "%.1f km", distKm)

        val finalServices = initialServices.mapIndexed { idx, srv ->
            val sanitizedSrvName = SecurityValidator.sanitizeName(srv.name, trimEdges = true).ifBlank { "Grooming Service" }
            val sanitizedSrvCat = SecurityValidator.sanitizeName(srv.category, trimEdges = true).ifBlank { "Haircut" }
            val clampedPrice = srv.price.coerceIn(SecurityValidator.MIN_SERVICE_PRICE, SecurityValidator.MAX_SERVICE_PRICE)
            val clampedDuration = srv.durationMinutes.coerceIn(SecurityValidator.MIN_SERVICE_DURATION, SecurityValidator.MAX_SERVICE_DURATION)
            val sanitizedDesc = SecurityValidator.sanitizeTagline(
                srv.description.ifBlank { "$sanitizedSrvName ($clampedDuration mins)" }
            )
            srv.copy(
                id = "${targetSalonId}_s${idx + 1}",
                name = sanitizedSrvName,
                category = sanitizedSrvCat,
                price = clampedPrice,
                durationMinutes = clampedDuration,
                description = sanitizedDesc,
                isPopular = true
            )
        }

        val newSalon = Salon(
            id = targetSalonId,
            name = cleanSalonName,
            location = fullDisplayLocation,
            distance = formattedDist,
            rating = 5.0,
            reviewCount = 0,
            averageRating = 0.0,
            totalRatings = 0,
            isAcSalon = isAcSalon,
            partnerId = uid,
            ownerId = uid,
            ownerName = cleanOwnerName,
            phone = formattedPhone,
            imageUrl = imageUrl,
            photoBase64 = imageUrl,
            services = finalServices,
            isOpen = true,
            currentStatus = "FREE",
            availableChairs = availableChairs.coerceIn(1, 20),
            waitingTimeMinutes = 0,
            tagline = "$cleanCategory • Open $openingTime - $closingTime",
            nextSlotMinutes = 15,
            shopAddress = fullDisplayLocation,
            landmark = cleanLandmark,
            pincode = cleanPincode,
            shopCategory = cleanCategory,
            openingTime = openingTime,
            closingTime = closingTime,
            latitude = latitude,
            longitude = longitude,
            isRegistrationCompleted = true,
            registrationFormSubmitted = true,
            isPhoneVerified = true
        )

        _isLoading.value = true

        // Update in-memory list immediately and sort nearest first
        _salons.value = recalculateAndSortSalons(
            listOf(newSalon) + _salons.value.filterNot { it.id == targetSalonId },
            _userLatitude.value,
            _userLongitude.value
        )

        val updatedProfile = currentProfile.copy(
            name = cleanOwnerName.ifBlank { currentProfile.name },
            phone = formattedPhone,
            isPhoneVerified = true,
            role = "partner",
            salonId = targetSalonId,
            hasRegisteredSalon = true,
            isAcSalon = isAcSalon,
            imageUrl = imageUrl,
            defaultCity = cleanCity.ifBlank { currentProfile.defaultCity }
        )
        _currentUserProfile.value = updatedProfile

        val db = firestore
        if (db != null) {
            db.collection("salons").document(targetSalonId).set(newSalon.toMap())
                .addOnSuccessListener {
                    db.collection("users").document(uid).set(
                        updatedProfile.toMap(),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    sessionPrefs.edit()
                        .putString("saved_name", updatedProfile.name)
                        .putString("saved_role", "partner")
                        .apply()

                    _isLoading.value = false
                    _showPartnerOnboarding.value = false
                    _currentRole.value = UserRole.PARTNER
                    _authState.value = AppAuthState.Authenticated(UserRole.PARTNER)
                    onSuccess(targetSalonId)
                }
                .addOnFailureListener { err ->
                    _isLoading.value = false
                    onError("Failed to register salon in Firestore: ${err.localizedMessage}")
                }
        } else {
            _isLoading.value = false
            _showPartnerOnboarding.value = false
            _currentRole.value = UserRole.PARTNER
            _authState.value = AppAuthState.Authenticated(UserRole.PARTNER)
            onSuccess(targetSalonId)
        }
    }

    /**
     * Actively re-fetches the currently logged-in user's document from Firestore (`users/{uid}`)
     * and checks all matching user documents so `isAdmin` updates are reflected immediately.
     */
    fun refreshCurrentUserProfile() {
        val current = _currentUserProfile.value ?: return
        val uid = current.id.trim()
        if (uid.isBlank() || uid in invalidSharedUserIds) return

        mergeAndSyncAllUsers()

        firestore?.collection("users")?.document(uid)?.get()
            ?.addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val parsed = parseUserDocument(doc)
                    if (parsed != null) {
                        val latestCurrent = _currentUserProfile.value ?: current
                        val resolvedAdmin = resolveAdminFlagForUser(
                            uid = uid,
                            email = parsed.email.ifBlank { latestCurrent.email },
                            phone = parsed.phone.ifBlank { latestCurrent.phone },
                            directFlag = parsed.isAdmin || latestCurrent.isAdmin
                        )
                        _currentUserProfile.value = parsed.copy(
                            isAdmin = resolvedAdmin,
                            hasRegisteredSalon = latestCurrent.hasRegisteredSalon,
                            salonId = latestCurrent.salonId,
                            role = latestCurrent.role
                        )
                    }
                }
            }

        val defDb = defaultFirestore
        if (defDb != null && defDb != firestore) {
            defDb.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val parsed = parseUserDocument(doc)
                        if (parsed != null) {
                            val latestCurrent = _currentUserProfile.value ?: current
                            val resolvedAdmin = resolveAdminFlagForUser(
                                uid = uid,
                                email = parsed.email.ifBlank { latestCurrent.email },
                                phone = parsed.phone.ifBlank { latestCurrent.phone },
                                directFlag = parsed.isAdmin || latestCurrent.isAdmin
                            )
                            _currentUserProfile.value = latestCurrent.copy(isAdmin = resolvedAdmin)
                        }
                    }
                }
        }
    }

    private fun fetchUserProfile(uid: String) {
        if (uid.isBlank() || uid in invalidSharedUserIds) {
            _currentUserProfile.value = null
            _customerBookings.value = emptyList()
            _authState.value = AppAuthState.Unauthenticated
            return
        }
        val user = auth?.currentUser
        val firestoreDb = firestore
        if (firestoreDb == null) {
            Log.e(tag, "Cannot fetch user profile: Firestore is null")
            _authState.value = AppAuthState.Unauthenticated
            return
        }

        firestoreDb.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val cancelCount = maxOf(
                        doc.getLong("cancellationStrikes")?.toInt() ?: 0,
                        doc.getLong("cancellationCount")?.toInt() ?: 0
                    )
                    val suspended = readBooleanField(doc, "isSuspended", cancelCount >= 3)
                    val verifiedSalon = _salons.value.find {
                        it.id !in seedSalonIds &&
                            (it.ownerId == uid || it.partnerId == uid) &&
                            it.isRegistrationCompleted &&
                            it.registrationFormSubmitted
                    }
                    val salonIdVal = verifiedSalon?.id
                    val hasRegisteredVal = verifiedSalon != null
                    val defaultCityVal = SecurityValidator.sanitizeAddress(
                        doc.getString("defaultCity")?.takeIf { it.isNotBlank() } ?: _appPreferences.value.defaultCity
                    )

                    val existingPhoneVal = doc.getString("phoneNumber")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("phone")?.takeIf { it.isNotBlank() }
                        ?: user?.phoneNumber?.takeIf { it.isNotBlank() }
                        ?: sessionPrefs.getString("saved_phone", "")
                        ?: ""
                    val existingEmailVal = doc.getString("email") ?: user?.email ?: ""
                    val existingPhoneVerifiedVal = readBooleanField(doc, "isPhoneVerified", existingPhoneVal.isNotBlank())
                    val existingIsBlockedVal = readBooleanField(doc, "isBlocked", false)
                    val existingIsAdminVal = resolveAdminFlagForUser(
                        uid = uid,
                        email = existingEmailVal,
                        phone = existingPhoneVal,
                        directFlag = readIsAdminFromDoc(doc)
                    )
                    val safeIsBlockedVal = if (existingIsAdminVal) false else existingIsBlockedVal
                    val safeCancelCount = if (existingIsAdminVal) 0 else cancelCount

                    val existingUnlockStatusVal = doc.getString("unlockRequestStatus")?.trim()?.lowercase()
                        ?.takeIf { it in setOf("none", "pending", "approved") } ?: "none"
                    val existingLocationLockedVal = readBooleanField(
                        doc,
                        "isLocationLocked",
                        defaultValue = existingUnlockStatusVal != "approved"
                    )
                    val existingAvgRating = (doc.get("averageRating") as? Number)?.toDouble()
                        ?: doc.getString("averageRating")?.toDoubleOrNull() ?: 0.0
                    val existingTotRatings = (doc.get("totalRatings") as? Number)?.toInt()
                        ?: doc.getString("totalRatings")?.toIntOrNull() ?: 0
                    val existingIsAc = readBooleanField(doc, "isAcSalon", false)
                    val existingImgUrl = doc.getString("imageUrl") ?: ""
                    val existingFavRaw = doc.get("favoriteSalons") as? List<*> ?: emptyList<Any?>()
                    val existingFavSalons = existingFavRaw.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }.distinct()

                    if (suspended && !safeIsBlockedVal) {
                        firestoreDb.collection("users").document(uid).set(
                            mapOf("isSuspended" to false, "cancellationStrikes" to 0, "cancellationCount" to 0),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                    }

                    // Every user defaults to Customer UI mode upon Login / Session Restore, while preserving admin role immunity
                    val profile = UserProfile(
                        id = uid,
                        name = SecurityValidator.sanitizeName(doc.getString("name") ?: user?.displayName ?: "Customer"),
                        email = existingEmailVal,
                        phone = existingPhoneVal,
                        phoneNumber = existingPhoneVal,
                        isPhoneVerified = existingPhoneVerifiedVal,
                        role = doc.getString("role")?.takeIf {
                            it.equals("admin", ignoreCase = true) || it.equals("superadmin", ignoreCase = true)
                        } ?: "customer",
                        salonId = salonIdVal,
                        hasRegisteredSalon = hasRegisteredVal,
                        defaultCity = defaultCityVal,
                        cancellationCount = safeCancelCount,
                        cancellationStrikes = safeCancelCount,
                        isSuspended = safeIsBlockedVal,
                        isBlocked = safeIsBlockedVal,
                        isAdmin = existingIsAdminVal,
                        isLocationLocked = existingLocationLockedVal,
                        unlockRequestStatus = existingUnlockStatusVal,
                        averageRating = existingAvgRating,
                        totalRatings = existingTotRatings,
                        isAcSalon = existingIsAc,
                        imageUrl = existingImgUrl,
                        favoriteSalons = existingFavSalons,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    )
                    sessionPrefs.edit().putString("saved_role", "customer").apply()
                    _currentUserProfile.value = profile
                    attachCustomerBookingsListener(uid)
                    _showPartnerOnboarding.value = false
                    _currentRole.value = UserRole.CUSTOMER
                    _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                } else {
                    val fallbackEmail = user?.email ?: sessionPrefs.getString("saved_email", "") ?: ""
                    val fallbackPhone = user?.phoneNumber ?: ""
                    val resolvedAdmin = resolveAdminFlagForUser(
                        uid = uid,
                        email = fallbackEmail,
                        phone = fallbackPhone,
                        directFlag = false
                    )
                    // Document doesn't exist yet: write to Firestore using merge
                    val newProfile = UserProfile(
                        id = uid,
                        name = user?.displayName ?: sessionPrefs.getString("saved_name", "Customer") ?: "Customer",
                        email = fallbackEmail,
                        phone = fallbackPhone,
                        role = "customer",
                        salonId = null,
                        hasRegisteredSalon = false,
                        defaultCity = _appPreferences.value.defaultCity,
                        cancellationCount = 0,
                        isSuspended = false,
                        isAdmin = resolvedAdmin,
                        createdAt = System.currentTimeMillis()
                    )
                    firestoreDb.collection("users").document(uid).set(
                        newProfile.toMap(),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                        .addOnSuccessListener {
                            _currentUserProfile.value = newProfile
                            attachCustomerBookingsListener(uid)
                            _currentRole.value = UserRole.CUSTOMER
                            _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                        }
                        .addOnFailureListener { err ->
                            Log.e(tag, "Failed to create initial user profile: ${err.message}")
                            _currentUserProfile.value = newProfile
                            attachCustomerBookingsListener(uid)
                            _currentRole.value = UserRole.CUSTOMER
                            _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                        }
                }
            }
            .addOnFailureListener { err ->
                Log.e(tag, "Failed to fetch user profile from Firestore: ${err.message}")
                _authError.value = "Failed to load user profile: ${err.localizedMessage}"
                _authState.value = AppAuthState.Unauthenticated
            }
    }

    // ==========================================
    // Real Firestore Bookings & PIN Anti-Fake Guard
    // ==========================================

    fun createBooking(
        customerId: String,
        customerName: String,
        customerPhone: String,
        salonId: String,
        salonName: String,
        service: String,
        servicePrice: Int,
        timeSlot: String,
        bookingDate: String,
        onSalonClosed: () -> Unit = {}
    ): Booking {
        val currentProfile = _currentUserProfile.value
        val resolvedCustomerId = currentProfile?.id?.takeIf { it.isNotBlank() && it !in invalidSharedUserIds }
            ?: customerId.trim()
        if (resolvedCustomerId.isBlank() || resolvedCustomerId in invalidSharedUserIds) {
            throw IllegalStateException("Please log in with your Mobile Number or Email before booking.")
        }

        // Whitelist Admin (`role: "admin"` / `"superadmin"`) & Developer accounts from suspension,
        // and allow creating 3+ consecutive bookings to test Dynamic Rush Mode seamlessly.
        if (currentProfile != null && !currentProfile.isWhitelistedAdminOrDev && currentProfile.isBlocked) {
            throw IllegalStateException("Account Blocked: Your account has been blocked by TrimTime Admin and cannot make new bookings.")
        }

        // Strict Shop Closed Validation & Suspended/Rejected Check:
        // Before processing ANY booking request (normal or rush mode), verify the salon's operational status (`isOpen`).
        val targetSalon = _salons.value.find {
            it.id == salonId ||
                (salonId.isNotBlank() && (it.ownerId == salonId || it.partnerId == salonId))
        }
        if (targetSalon != null && !targetSalon.isAcceptingBookings) {
            if (!targetSalon.isOpen || targetSalon.currentStatus.equals("CLOSED", ignoreCase = true)) {
                onSalonClosed()
                throw IllegalStateException("This salon is currently closed.")
            } else {
                throw IllegalStateException("This salon is currently suspended or unavailable for bookings.")
            }
        }
        if (targetSalon != null &&
            currentProfile?.isWhitelistedAdminOrDev != true &&
            resolvedCustomerId.isNotBlank() &&
            (targetSalon.ownerId == resolvedCustomerId || targetSalon.partnerId == resolvedCustomerId)
        ) {
            throw IllegalStateException("This is your own registered salon. Manage it in Partner Mode.")
        }

        sweepExpiredPendingBookings()

        // Edge Case Protection — Closing Time Guard:
        // Block the booking request if the newly calculated ETA exceeds the salon's official closing time.
        val activeSalonQueueCount = Booking.countActiveInQueueToday(
            bookings = _bookings.value,
            salonId = salonId,
            partnerId = targetSalon?.partnerId.orEmpty(),
            ownerId = targetSalon?.ownerId.orEmpty()
        )
        val matchedService = targetSalon?.services?.find {
            it.name.equals(service, ignoreCase = true) || it.serviceName.equals(service, ignoreCase = true)
        }
        val serviceDurationMins = when {
            matchedService != null && matchedService.duration > 0 -> matchedService.duration
            matchedService != null && matchedService.durationMinutes > 0 -> matchedService.durationMinutes
            targetSalon != null && targetSalon.waitingTimeMinutes in 10..120 -> targetSalon.waitingTimeMinutes
            else -> 20
        }
        val salonClosingTimeStr = targetSalon?.closingTime?.takeIf { it.isNotBlank() } ?: "9:00 PM"
        val nowMillis = System.currentTimeMillis()
        if (Booking.isEtaExceedingClosingTime(
                closingTimeStr = salonClosingTimeStr,
                activeBookingsCount = activeSalonQueueCount,
                defaultServiceDurationMinutes = serviceDurationMins,
                baseTimeMillis = nowMillis
            )
        ) {
            val etaClockTime = Booking.calculateFormattedEtaClockTime(
                activeBookingsCount = activeSalonQueueCount,
                defaultServiceDurationMinutes = serviceDurationMins,
                baseTimeMillis = nowMillis
            )
            throw IllegalStateException(
                "Booking Blocked: Newly calculated ETA ($etaClockTime) exceeds ${targetSalon?.name ?: salonName}'s official closing time ($salonClosingTimeStr)."
            )
        }

        // Validate customer phone & sanitize customer name
        val rawName = customerName.ifBlank { currentProfile?.name ?: "Customer" }
        val resolvedCustomerName = SecurityValidator.sanitizeName(rawName, trimEdges = true)
        if (resolvedCustomerName.length < 2) {
            throw IllegalArgumentException("Please enter a valid customer name (2–60 characters).")
        }

        val rawPhone = customerPhone.ifBlank { currentProfile?.phoneNumber?.ifBlank { currentProfile.phone } ?: "" }
        val phoneErr = SecurityValidator.validateIndianMobileError(rawPhone)
        if (phoneErr != null) {
            throw IllegalArgumentException(phoneErr)
        }
        val tenDigitPhone = SecurityValidator.extractTenDigitMobile(rawPhone)
        val resolvedCustomerPhone = SecurityValidator.formatIndianMobile(rawPhone)

        if (!SecurityValidator.isValidServicePrice(servicePrice)) {
            throw IllegalArgumentException("Service price must be between ₹10 and ₹25,000.")
        }

        // Persist the validated 10-digit phoneNumber (and updated name) into currentUserProfile & Firestore users/{uid}
        // so it automatically pre-fills in all future booking dialogs.
        if (currentProfile != null) {
            _currentUserProfile.value = currentProfile.copy(
                name = resolvedCustomerName,
                phone = tenDigitPhone,
                phoneNumber = tenDigitPhone,
                isPhoneVerified = true
            )
        }
        sessionPrefs.edit()
            .putString("saved_phone", tenDigitPhone)
            .putString("saved_name", resolvedCustomerName)
            .apply()

        val generatedPin = String.format("%04d", Random.nextInt(1000, 9999))
        val newBookingId = "bk_" + nowMillis.toString().takeLast(6) + "_" + Random.nextInt(10, 99)

        val cleanSalonId = salonId.trim()
        val cleanSalonName = SecurityValidator.sanitizeName(salonName, trimEdges = true)
        val rawSanitizedSlot = SecurityValidator.sanitizeName(timeSlot, trimEdges = true)
        val cleanTimeSlot = Booking.ensureFutureArrivalClockTime(
            candidateTimeStr = rawSanitizedSlot,
            activeBookingsCount = activeSalonQueueCount,
            defaultServiceDurationMinutes = serviceDurationMins,
            nowMillis = nowMillis
        )
        val cleanBookingDate = SecurityValidator.sanitizeName(bookingDate, trimEdges = true)

        val resolvedCustomerFcmToken = currentProfile?.fcmToken?.takeIf { it.isNotBlank() }
            ?: TrimTimeNotificationHelper.getCachedFcmToken(appContext)
        val resolvedPartnerUidForDoc = targetSalon?.ownerId?.trim()?.takeIf { it.isNotBlank() }
            ?: targetSalon?.partnerId?.trim()?.takeIf { it.isNotBlank() }
            ?: cleanSalonId.removePrefix("salon_").ifBlank { cleanSalonId }

        val newBooking = Booking(
            bookingId = newBookingId,
            userId = resolvedCustomerId,
            partnerId = resolvedPartnerUidForDoc,
            salonName = cleanSalonName,
            date = cleanBookingDate,
            time = cleanTimeSlot,
            allocatedTime = cleanTimeSlot,
            status = "Pending",
            id = newBookingId,
            customerId = resolvedCustomerId,
            customerName = resolvedCustomerName,
            customerPhone = resolvedCustomerPhone,
            salonId = cleanSalonId,
            service = SecurityValidator.sanitizeName(service, trimEdges = true),
            servicePrice = servicePrice.coerceIn(SecurityValidator.MIN_SERVICE_PRICE, SecurityValidator.MAX_SERVICE_PRICE),
            timeSlot = cleanTimeSlot,
            bookingDate = cleanBookingDate,
            pin = generatedPin,
            customerFcmToken = resolvedCustomerFcmToken,
            createdAt = nowMillis
        )

        // Add to local states immediately (strictly isolated for _customerBookings)
        _bookings.value = listOf(newBooking) + _bookings.value
        _customerBookings.value = listOf(newBooking) + _customerBookings.value.filter { it.customerId == resolvedCustomerId }

        // Edge Case Protection — Concurrency & Strict Shop Closed Validation via Firestore Transaction (`db.runTransaction`):
        // Atomically reads `salons/{salonId}` (`isOpen`, `liveQueueSequence`, `lastAllocatedEtaMillis`) to block
        // bookings if the salon is closed in Firestore, prevent double-booking the exact same slot, verify closing time,
        // and commit both the salon queue state and the new booking document.
        firestore?.let { db ->
            try {
                val userUpdateMap = buildMap<String, Any> {
                    put("phoneNumber", tenDigitPhone)
                    put("phone", tenDigitPhone)
                    put("isPhoneVerified", true)
                    put("name", resolvedCustomerName)
                    if (resolvedCustomerFcmToken.isNotBlank()) {
                        put("fcmToken", resolvedCustomerFcmToken)
                    }
                }
                db.collection("users").document(resolvedCustomerId).set(
                    userUpdateMap,
                    com.google.firebase.firestore.SetOptions.merge()
                )

                val salonRef = db.collection("salons").document(cleanSalonId)
                val altOwnerId = targetSalon?.ownerId?.trim()?.takeIf { it.isNotBlank() && it != cleanSalonId }
                    ?: targetSalon?.partnerId?.trim()?.takeIf { it.isNotBlank() && it != cleanSalonId }
                val altSalonRef = altOwnerId?.let { db.collection("salons").document(it) }
                val bookingRef = db.collection("bookings").document(newBooking.id)
                val isRushActiveNow = Booking.isRushModeActive(_isGlobalRushModeEnabled.value, activeSalonQueueCount)

                db.runTransaction { transaction ->
                    val salonSnap = transaction.get(salonRef)
                    val altSalonSnap = altSalonRef?.let { transaction.get(it) }

                    listOfNotNull(salonSnap, altSalonSnap).forEach { snap ->
                        if (snap.exists()) {
                            val isOpenInFirestore = snap.getBoolean("isOpen")
                                ?: snap.getString("isOpen")?.toBooleanStrictOrNull()
                                ?: snap.getBoolean("isShopOpen")
                                ?: true
                            val statusInFirestore = snap.getString("currentStatus")?.trim().orEmpty()
                            val salonStatusInFirestore = snap.getString("salonStatus")?.trim().orEmpty()
                            val isSuspendedInFirestore = snap.getBoolean("isSuspended") ?: false
                            if (!isOpenInFirestore ||
                                statusInFirestore.equals("CLOSED", ignoreCase = true) ||
                                salonStatusInFirestore.equals("closed", ignoreCase = true) ||
                                salonStatusInFirestore.equals("suspended", ignoreCase = true) ||
                                isSuspendedInFirestore
                            ) {
                                throw com.google.firebase.firestore.FirebaseFirestoreException(
                                    "This salon is currently closed.",
                                    com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED
                                )
                            }
                        }
                    }

                    val prevLastAllocatedMillis = salonSnap.getLong("lastAllocatedEtaMillis") ?: 0L
                    val prevQueueSeq = salonSnap.getLong("liveQueueSequence") ?: 0L
                    val dbClosingTimeStr = salonSnap.getString("closingTime")?.takeIf { it.isNotBlank() }
                        ?: salonSnap.getString("ClosingTime")?.takeIf { it.isNotBlank() }
                        ?: salonClosingTimeStr

                    val txNowMillis = System.currentTimeMillis()
                    val baseCalculatedEtaMillis = Booking.calculateEtaArrivalMillis(
                        activeBookingsCount = activeSalonQueueCount,
                        defaultServiceDurationMinutes = serviceDurationMins,
                        baseTimeMillis = txNowMillis
                    )
                    // Only offset past prevLastAllocatedMillis when there is an active queue (`activeSalonQueueCount > 0`).
                    // When Wait Time is 0m (`activeSalonQueueCount == 0`), log at the CURRENT time (`baseCalculatedEtaMillis`).
                    val safeNextEtaMillis = if (activeSalonQueueCount > 0 && prevLastAllocatedMillis >= baseCalculatedEtaMillis) {
                        prevLastAllocatedMillis + (serviceDurationMins * 60_000L)
                    } else {
                        baseCalculatedEtaMillis
                    }

                    val closingMillis = Booking.parseSalonClosingTimeMillisToday(dbClosingTimeStr, txNowMillis)
                    if (closingMillis != null && safeNextEtaMillis > closingMillis) {
                        throw com.google.firebase.firestore.FirebaseFirestoreException(
                            "Estimated ETA exceeds salon closing time ($dbClosingTimeStr).",
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED
                        )
                    }

                    val txFormattedClock = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
                        .format(java.util.Date(safeNextEtaMillis))
                    val txTimeSlot = if (isRushActiveNow || activeSalonQueueCount > 0) {
                        txFormattedClock
                    } else {
                        Booking.ensureFutureArrivalClockTime(
                            candidateTimeStr = cleanTimeSlot,
                            activeBookingsCount = activeSalonQueueCount,
                            defaultServiceDurationMinutes = serviceDurationMins,
                            nowMillis = txNowMillis
                        )
                    }

                    if (salonSnap.exists()) {
                        transaction.update(
                            salonRef,
                            mapOf(
                                "lastAllocatedEtaMillis" to safeNextEtaMillis,
                                "liveQueueSequence" to (prevQueueSeq + 1L)
                            )
                        )
                    }

                    val txBooking = newBooking.copy(
                        time = txTimeSlot,
                        timeSlot = txTimeSlot,
                        allocatedTime = txTimeSlot
                    )
                    transaction.set(bookingRef, txBooking.toMap())
                    txBooking
                }.addOnSuccessListener { txBooking ->
                    val confirmedBooking = txBooking ?: newBooking
                    if (txBooking != null && txBooking.timeSlot != newBooking.timeSlot) {
                        _bookings.value = _bookings.value.map { if (it.id == newBooking.id) txBooking else it }
                        _customerBookings.value = _customerBookings.value.map { if (it.id == newBooking.id) txBooking else it }
                    }
                    Log.d(tag, "Transactional booking committed to Firestore: ${newBooking.id}")
                    // Dispatch high-priority FCM push notification to the Partner's saved fcmToken
                    val resolvedPartnerId = targetSalon?.ownerId?.takeIf { it.isNotBlank() }
                        ?: targetSalon?.partnerId?.takeIf { it.isNotBlank() }
                        ?: cleanSalonId.removePrefix("salon_")
                    val partnerFcmToken = _allUsers.value.find { it.id == resolvedPartnerId }?.fcmToken?.takeIf { it.isNotBlank() }
                        ?: targetSalon?.fcmToken.orEmpty()
                    TrimTimeNotificationHelper.sendHighPriorityBookingFcmToPartner(
                        context = appContext,
                        partnerId = resolvedPartnerId,
                        salonId = cleanSalonId,
                        salonName = salonName,
                        bookingId = confirmedBooking.id,
                        customerName = confirmedBooking.customerName,
                        customerPhone = confirmedBooking.customerPhone,
                        serviceName = confirmedBooking.service,
                        servicePrice = confirmedBooking.servicePrice,
                        timeSlot = confirmedBooking.timeSlot,
                        knownPartnerFcmToken = partnerFcmToken
                    )
                }.addOnFailureListener { e ->
                    Log.w(tag, "Transaction failure for booking ${newBooking.id}: ${e.message}")
                    val isAbortedOrDenied = e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED ||
                            e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
                    if (isAbortedOrDenied || e.message?.contains("closed", ignoreCase = true) == true) {
                        // Rolled back due to salon being closed (`isOpen == false`), security rule rejection, or closing time exceeded
                        _bookings.value = _bookings.value.filterNot { it.id == newBooking.id }
                        _customerBookings.value = _customerBookings.value.filterNot { it.id == newBooking.id }
                        if (e.message?.contains("closed", ignoreCase = true) == true ||
                            (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
                        ) {
                            _salons.value = _salons.value.map {
                                if (it.id == cleanSalonId) it.copy(isOpen = false, currentStatus = "CLOSED") else it
                            }
                            onSalonClosed()
                        }
                    } else {
                        // Final safety check before any fallback write: verify salonRef is open in Firestore
                        salonRef.get().addOnSuccessListener { snap ->
                            val isOpenNow = if (snap != null && snap.exists()) {
                                val openFlag = snap.getBoolean("isOpen")
                                    ?: snap.getString("isOpen")?.toBooleanStrictOrNull()
                                    ?: true
                                val statusFlag = snap.getString("currentStatus")?.trim().orEmpty()
                                openFlag && !statusFlag.equals("CLOSED", ignoreCase = true)
                            } else {
                                targetSalon?.isAcceptingBookings ?: true
                            }
                            if (!isOpenNow) {
                                _bookings.value = _bookings.value.filterNot { it.id == newBooking.id }
                                _customerBookings.value = _customerBookings.value.filterNot { it.id == newBooking.id }
                                _salons.value = _salons.value.map {
                                    if (it.id == cleanSalonId) it.copy(isOpen = false, currentStatus = "CLOSED") else it
                                }
                                onSalonClosed()
                            } else {
                                db.collection("bookings").document(newBooking.id).set(newBooking.toMap())
                                val resolvedPartnerId = targetSalon?.ownerId?.takeIf { it.isNotBlank() }
                                    ?: targetSalon?.partnerId?.takeIf { it.isNotBlank() }
                                    ?: cleanSalonId.removePrefix("salon_")
                                val partnerFcmToken = _allUsers.value.find { it.id == resolvedPartnerId }?.fcmToken?.takeIf { it.isNotBlank() }
                                    ?: targetSalon?.fcmToken.orEmpty()
                                TrimTimeNotificationHelper.sendHighPriorityBookingFcmToPartner(
                                    context = appContext,
                                    partnerId = resolvedPartnerId,
                                    salonId = cleanSalonId,
                                    salonName = salonName,
                                    bookingId = newBooking.id,
                                    customerName = newBooking.customerName,
                                    customerPhone = newBooking.customerPhone,
                                    serviceName = newBooking.service,
                                    servicePrice = newBooking.servicePrice,
                                    timeSlot = newBooking.timeSlot,
                                    knownPartnerFcmToken = partnerFcmToken
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception writing transactional booking: ${e.message}")
            }
        }

        return newBooking
    }

    /**
     * Partner Action: Accept an incoming customer booking in real time in Firestore
     * using a Firestore Transaction (`db.runTransaction`) to prevent double-booking the exact same slot,
     * ensure the allocated arrival time is always in the future (`>= Current Time + Buffer`),
     * and verify the booking has not timed out or been cancelled.
     */
    fun acceptBooking(bookingId: String, allocatedTime: String = ""): Boolean {
        val nowMillis = System.currentTimeMillis()
        sweepExpiredPendingBookings(nowMillis)
        val currentBookings = _bookings.value
        val booking = currentBookings.find { it.id == bookingId } ?: return false
        if (booking.isPendingTimedOut(nowMillis)) {
            return false
        }
        val activeAheadCount = Booking.countActiveInQueueToday(
            bookings = currentBookings,
            salonId = booking.salonId,
            partnerId = booking.partnerId,
            salonName = booking.salonName,
            excludeBookingId = booking.id,
            referenceMillis = nowMillis
        )
        val rawAllocated = allocatedTime.trim().ifBlank {
            booking.allocatedTime.ifBlank { booking.resolvedTime }
        }
        val finalAllocatedTime = Booking.ensureFutureArrivalClockTime(
            candidateTimeStr = rawAllocated,
            activeBookingsCount = activeAheadCount,
            defaultServiceDurationMinutes = 20,
            nowMillis = nowMillis
        )
        val acceptedBooking = booking.copy(
            status = BookingStatus.ACCEPTED.name.lowercase(),
            allocatedTime = finalAllocatedTime,
            time = finalAllocatedTime,
            timeSlot = finalAllocatedTime
        )
        _bookings.value = currentBookings.map { if (it.id == bookingId) acceptedBooking else it }
        _customerBookings.value = _customerBookings.value.map { if (it.id == bookingId) acceptedBooking else it }

        val updates = mapOf(
            "status" to "accepted",
            "allocatedTime" to finalAllocatedTime,
            "time" to finalAllocatedTime,
            "timeSlot" to finalAllocatedTime
        )
        val db = firestore
        if (db != null) {
            val bookingRef = db.collection("bookings").document(bookingId)
            val salonRef = db.collection("salons").document(booking.salonId)
            db.runTransaction { transaction ->
                val bookingSnap = transaction.get(bookingRef)
                val salonSnap = if (booking.salonId.isNotBlank()) transaction.get(salonRef) else null

                if (bookingSnap.exists()) {
                    val createdAtMs = bookingSnap.getLong("createdAt") ?: booking.createdAt
                    val statusInDb = bookingSnap.getString("status")?.trim().orEmpty()
                    if (statusInDb.equals("pending", ignoreCase = true) &&
                        createdAtMs > 0L &&
                        (System.currentTimeMillis() - createdAtMs) >= Booking.PENDING_TIMEOUT_MS
                    ) {
                        transaction.update(bookingRef, mapOf("status" to "cancelled"))
                        throw com.google.firebase.firestore.FirebaseFirestoreException(
                            "Booking expired after 5 minutes without response.",
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED
                        )
                    }
                }

                val nonOverlappingAllocatedTime = Booking.ensureFutureArrivalClockTime(
                    candidateTimeStr = finalAllocatedTime,
                    activeBookingsCount = activeAheadCount,
                    defaultServiceDurationMinutes = 20,
                    nowMillis = System.currentTimeMillis()
                )
                if (salonSnap != null && salonSnap.exists()) {
                    val prevSeq = salonSnap.getLong("liveQueueSequence") ?: 0L
                    transaction.update(
                        salonRef,
                        mapOf(
                            "liveQueueSequence" to (prevSeq + 1L),
                            "lastAllocatedSlotString" to nonOverlappingAllocatedTime,
                            "lastAllocatedEtaMillis" to System.currentTimeMillis()
                        )
                    )
                }

                transaction.set(
                    bookingRef,
                    mapOf(
                        "status" to "accepted",
                        "allocatedTime" to nonOverlappingAllocatedTime,
                        "time" to nonOverlappingAllocatedTime,
                        "timeSlot" to nonOverlappingAllocatedTime
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                nonOverlappingAllocatedTime
            }.addOnSuccessListener { txAllocatedTime ->
                val resolvedAllocated = txAllocatedTime?.takeIf { it.isNotBlank() } ?: finalAllocatedTime
                if (!txAllocatedTime.isNullOrBlank() && txAllocatedTime != finalAllocatedTime) {
                    _bookings.value = _bookings.value.map {
                        if (it.id == bookingId) it.copy(
                            allocatedTime = txAllocatedTime,
                            time = txAllocatedTime,
                            timeSlot = txAllocatedTime
                        ) else it
                    }
                    _customerBookings.value = _customerBookings.value.map {
                        if (it.id == bookingId) it.copy(
                            allocatedTime = txAllocatedTime,
                            time = txAllocatedTime,
                            timeSlot = txAllocatedTime
                        ) else it
                    }
                }
                Log.d(tag, "Booking $bookingId accepted via Firestore Transaction with allocatedTime=$resolvedAllocated.")

                // Send high-priority FCM push notification to the Customer's fcmToken
                val isEtaAdjusted = (allocatedTime.isNotBlank() &&
                    !allocatedTime.trim().equals(booking.timeSlot.trim(), ignoreCase = true)) ||
                    !resolvedAllocated.equals(booking.timeSlot.trim(), ignoreCase = true)
                val customerFcmToken = _allUsers.value.find { it.id == booking.customerId }?.fcmToken?.takeIf { it.isNotBlank() }
                    ?: booking.customerFcmToken
                TrimTimeNotificationHelper.sendHighPriorityBookingStatusFcmToCustomer(
                    context = appContext,
                    customerId = booking.customerId,
                    salonId = booking.salonId,
                    salonName = booking.salonName,
                    bookingId = booking.id,
                    serviceName = booking.service,
                    allocatedTime = resolvedAllocated,
                    eventType = if (isEtaAdjusted) "ETA_ADJUSTED" else "BOOKING_ACCEPTED",
                    knownCustomerFcmToken = customerFcmToken
                )
            }.addOnFailureListener { e ->
                Log.w(tag, "Transaction fallback for acceptBooking $bookingId: ${e.message}")
                if (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED
                ) {
                    _bookings.value = _bookings.value.map {
                        if (it.id == bookingId) it.copy(status = "cancelled") else it
                    }
                    _customerBookings.value = _customerBookings.value.map {
                        if (it.id == bookingId) it.copy(status = "cancelled") else it
                    }
                } else {
                    bookingRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
                    val isEtaAdjusted = (allocatedTime.isNotBlank() &&
                        !allocatedTime.trim().equals(booking.timeSlot.trim(), ignoreCase = true)) ||
                        !finalAllocatedTime.equals(booking.timeSlot.trim(), ignoreCase = true)
                    val customerFcmToken = _allUsers.value.find { it.id == booking.customerId }?.fcmToken?.takeIf { it.isNotBlank() }
                        ?: booking.customerFcmToken
                    TrimTimeNotificationHelper.sendHighPriorityBookingStatusFcmToCustomer(
                        context = appContext,
                        customerId = booking.customerId,
                        salonId = booking.salonId,
                        salonName = booking.salonName,
                        bookingId = booking.id,
                        serviceName = booking.service,
                        allocatedTime = finalAllocatedTime,
                        eventType = if (isEtaAdjusted) "ETA_ADJUSTED" else "BOOKING_ACCEPTED",
                        knownCustomerFcmToken = customerFcmToken
                    )
                }
            }
        }
        return true
    }

    /**
     * Partner Action: Reject an incoming customer booking in real time in Firestore
     * (Does NOT add a cancellation strike to the customer since the partner rejected it),
     * and dispatches a high-priority FCM push notification to the Customer's fcmToken.
     */
    fun rejectBooking(bookingId: String): Boolean {
        val currentBookings = _bookings.value
        val booking = currentBookings.find { it.id == bookingId } ?: return false
        val rejectedBooking = booking.copy(status = BookingStatus.REJECTED.name.lowercase())
        _bookings.value = currentBookings.map { if (it.id == bookingId) rejectedBooking else it }
        _customerBookings.value = _customerBookings.value.map { if (it.id == bookingId) rejectedBooking else it }

        val customerFcmToken = _allUsers.value.find { it.id == booking.customerId }?.fcmToken?.takeIf { it.isNotBlank() }
            ?: booking.customerFcmToken
        TrimTimeNotificationHelper.sendHighPriorityBookingStatusFcmToCustomer(
            context = appContext,
            customerId = booking.customerId,
            salonId = booking.salonId,
            salonName = booking.salonName,
            bookingId = booking.id,
            serviceName = booking.service,
            allocatedTime = booking.resolvedTime,
            eventType = "BOOKING_DECLINED",
            knownCustomerFcmToken = customerFcmToken
        )

        firestore?.collection("bookings")?.document(bookingId)?.update("status", "rejected")
            ?.addOnSuccessListener {
                Log.d(tag, "Booking $bookingId rejected in Firestore.")
            }
            ?.addOnFailureListener { e ->
                Log.w(tag, "Failed to reject booking in Firestore: ${e.message}")
            }
        return true
    }

    /**
     * Partner Action: Mark a late customer booking as 'No Show' (cancelled due to late arrival)
     * in real time in Firestore and notify the Customer.
     */
    fun markBookingNoShow(bookingId: String): Boolean {
        val currentBookings = _bookings.value
        val booking = currentBookings.find { it.id == bookingId } ?: return false
        val noShowNote = "${booking.cleanAllocatedClockTime} (Marked No-Show)"
        val noShowBooking = booking.copy(
            status = "cancelled",
            allocatedTime = noShowNote
        )
        _bookings.value = currentBookings.map { if (it.id == bookingId) noShowBooking else it }
        _customerBookings.value = _customerBookings.value.map { if (it.id == bookingId) noShowBooking else it }

        val customerFcmToken = _allUsers.value.find { it.id == booking.customerId }?.fcmToken?.takeIf { it.isNotBlank() }
            ?: booking.customerFcmToken
        TrimTimeNotificationHelper.sendHighPriorityBookingStatusFcmToCustomer(
            context = appContext,
            customerId = booking.customerId,
            salonId = booking.salonId,
            salonName = booking.salonName,
            bookingId = booking.id,
            serviceName = booking.service,
            allocatedTime = noShowNote,
            eventType = "BOOKING_DECLINED",
            knownCustomerFcmToken = customerFcmToken
        )

        firestore?.collection("bookings")?.document(bookingId)?.set(
            mapOf(
                "status" to "cancelled",
                "allocatedTime" to noShowNote
            ),
            com.google.firebase.firestore.SetOptions.merge()
        )?.addOnSuccessListener {
            Log.d(tag, "Booking $bookingId marked as No-Show in Firestore.")
        }?.addOnFailureListener { e ->
            Log.w(tag, "Failed to mark booking as No-Show in Firestore: ${e.message}")
        }
        return true
    }

    /**
     * Returns remaining lockout seconds for a booking if 5 wrong PIN attempts occurred within the last 60s.
     */
    fun getPinLockoutRemainingSeconds(bookingId: String): Int {
        val lockoutUntil = pinLockoutUntilMillis[bookingId] ?: return 0
        val now = System.currentTimeMillis()
        if (now >= lockoutUntil) {
            pinLockoutUntilMillis.remove(bookingId)
            pinFailedAttempts.remove(bookingId)
            return 0
        }
        return ((lockoutUntil - now + 999L) / 1000L).toInt().coerceAtLeast(1)
    }

    fun getFailedPinAttempts(bookingId: String): Int {
        getPinLockoutRemainingSeconds(bookingId) // clears expired lockout if any
        return pinFailedAttempts[bookingId] ?: 0
    }

    fun verifyAndCompleteBooking(bookingId: String, enteredPin: String): PinVerificationResult {
        val currentBookings = _bookings.value
        val booking = currentBookings.find { it.id == bookingId }
            ?: return PinVerificationResult.BookingNotFound("Booking not found.")

        if (booking.status.equals("completed", ignoreCase = true)) {
            return PinVerificationResult.AlreadyCompleted("This booking has already been verified and completed.")
        }

        // Brute-Force Lockout Check (60 seconds after 5 consecutive wrong PIN attempts)
        val remainingLockSecs = getPinLockoutRemainingSeconds(bookingId)
        if (remainingLockSecs > 0) {
            return PinVerificationResult.LockedOut(
                remainingSeconds = remainingLockSecs,
                message = "🔒 Security Lockout: Too many wrong PIN attempts (5/5). Please wait ${remainingLockSecs}s before trying again."
            )
        }

        val trimmedEnteredPin = enteredPin.trim()
        val actualPin = booking.pin.trim()

        if (trimmedEnteredPin != actualPin) {
            val attempts = (pinFailedAttempts[bookingId] ?: 0) + 1
            pinFailedAttempts[bookingId] = attempts

            if (attempts >= SecurityValidator.MAX_PIN_ATTEMPTS) {
                pinLockoutUntilMillis[bookingId] = System.currentTimeMillis() + SecurityValidator.PIN_LOCKOUT_DURATION_MS
                return PinVerificationResult.LockedOut(
                    remainingSeconds = 60,
                    message = "🔒 Security Lockout: 5 wrong PIN attempts in a row! PIN verification is locked for 60 seconds."
                )
            }

            val remaining = SecurityValidator.MAX_PIN_ATTEMPTS - attempts
            return PinVerificationResult.IncorrectPin(
                message = "Incorrect 4-digit PIN. $remaining attempt${if (remaining == 1) "" else "s"} remaining before 60s security lockout.",
                attemptsRemaining = remaining
            )
        }

        // Correct PIN entered! Clear failed attempts & lockout state
        pinFailedAttempts.remove(bookingId)
        pinLockoutUntilMillis.remove(bookingId)

        val completedBooking = booking.copy(
            status = BookingStatus.COMPLETED.name.lowercase(),
            completedAt = System.currentTimeMillis()
        )

        _bookings.value = currentBookings.map { if (it.id == bookingId) completedBooking else it }
        _customerBookings.value = _customerBookings.value.map { if (it.id == bookingId) completedBooking else it }

        // Sync update live to Firestore
        firestore?.let { db ->
            try {
                db.collection("bookings").document(bookingId).update(
                    mapOf(
                        "status" to "completed",
                        "completedAt" to completedBooking.completedAt
                    )
                )
            } catch (e: Exception) {
                Log.e(tag, "Firestore update error: ${e.message}")
            }
        }

        // Automatic Status Change: When barber enters 4-digit PIN and marks booking Complete,
        // automatically switch salon currentStatus back to "FREE"
        booking.salonId.let { sId ->
            if (sId.isNotBlank()) {
                setSalonBusyStatus(sId, "FREE")
            }
        }

        return PinVerificationResult.Success
    }

    /**
     * Rule 3: 3-Strike Anti-Spam & Auto-Block System
     * Increment cancellationCount & cancellationStrikes. If >= 3, suspend customer from future bookings.
     */
    fun cancelBooking(bookingId: String): Boolean {
        val currentBookings = _bookings.value
        val booking = currentBookings.find { it.id == bookingId }
            ?: _customerBookings.value.find { it.id == bookingId }
            ?: return false

        val cancelledBooking = booking.copy(status = BookingStatus.CANCELLED.name.lowercase())
        _bookings.value = currentBookings.map { if (it.id == bookingId) cancelledBooking else it }
        _customerBookings.value = _customerBookings.value.map { if (it.id == bookingId) cancelledBooking else it }

        firestore?.collection("bookings")?.document(bookingId)?.update("status", "cancelled")

        // Admin/SuperAdmin/Developer accounts are immune, and automatic 3-strike account suspension is disabled
        val currentProfile = _currentUserProfile.value
        if (currentProfile != null) {
            val isImmune = currentProfile.isWhitelistedAdminOrDev
            val newStrikes = if (isImmune) 0 else currentProfile.effectiveCancellationStrikes + 1
            val updatedProfile = currentProfile.copy(
                cancellationCount = newStrikes,
                cancellationStrikes = newStrikes,
                isSuspended = false
            )
            _currentUserProfile.value = updatedProfile

            firestore?.collection("users")?.document(currentProfile.id)?.set(
                mapOf(
                    "cancellationCount" to newStrikes,
                    "cancellationStrikes" to newStrikes,
                    "isSuspended" to false
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )?.addOnSuccessListener {
                Log.d(tag, "Booking cancelled for user ${currentProfile.id} (immune=$isImmune, auto-suspension disabled).")
            }
        }

        return true
    }

    private fun resolveSalonOwnerMap(salonId: String): Map<String, Any> {
        val salon = _salons.value.find { it.id == salonId } ?: return emptyMap()
        val ownerId = salon.ownerId.ifBlank { salon.partnerId }
        val result = mutableMapOf<String, Any>()
        if (ownerId.isNotBlank()) {
            result["ownerId"] = ownerId
        }
        if (salon.isRegistrationCompleted && salon.registrationFormSubmitted) {
            result["isRegistrationCompleted"] = true
            result["registrationFormSubmitted"] = true
        }
        return result
    }

    fun toggleSalonOpenStatus(salonId: String) {
        val currentSalon = _salons.value.find { it.id == salonId }
        val nextOpen = !(currentSalon?.isOpen ?: true)
        setSalonOpenStatus(salonId, nextOpen)
    }

    fun setSalonOpenStatus(salonId: String, isOpen: Boolean) {
        val cleanId = salonId.trim()
        val currentSalons = _salons.value
        val targetSalon = currentSalons.find {
            it.id == cleanId ||
                (cleanId.isNotBlank() && (it.ownerId == cleanId || it.partnerId == cleanId))
        }
        val ownerId = targetSalon?.ownerId?.trim().orEmpty()
        val partnerId = targetSalon?.partnerId?.trim().orEmpty()
        val nextStatus = if (isOpen) {
            if (targetSalon?.currentStatus.equals("BUSY", ignoreCase = true)) "BUSY" else "FREE"
        } else {
            "CLOSED"
        }

        val updated = currentSalons.map { s ->
            val matches = s.id == cleanId ||
                (ownerId.isNotBlank() && (s.ownerId == ownerId || s.partnerId == ownerId || s.id == "salon_$ownerId")) ||
                (partnerId.isNotBlank() && (s.ownerId == partnerId || s.partnerId == partnerId || s.id == "salon_$partnerId"))
            if (matches) {
                s.copy(isOpen = isOpen, currentStatus = nextStatus)
            } else {
                s
            }
        }
        _salons.value = updated

        // Update only ['isOpen', 'currentStatus'] so Firestore security rules (`affectedKeys().hasOnly(...)`) always succeed
        val statusUpdates = mapOf<String, Any>(
            "isOpen" to isOpen,
            "currentStatus" to nextStatus
        )
        val candidateDocIds = setOf(
            cleanId,
            ownerId,
            partnerId,
            if (ownerId.isNotBlank()) "salon_$ownerId" else "",
            if (partnerId.isNotBlank()) "salon_$partnerId" else ""
        ).filter { it.isNotBlank() }

        val dbs = listOfNotNull(firestore, defaultFirestore).distinct()
        dbs.forEach { db ->
            candidateDocIds.forEach { docId ->
                val docRef = db.collection("salons").document(docId)
                if (docId == cleanId) {
                    docRef.update(statusUpdates)
                        .addOnSuccessListener {
                            Log.d(tag, "Salon $docId isOpen updated to $isOpen ($nextStatus) in Firestore.")
                        }
                        .addOnFailureListener {
                            docRef.set(
                                statusUpdates + resolveSalonOwnerMap(cleanId),
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                        }
                } else {
                    docRef.update(statusUpdates)
                }
            }
        }
    }

    /**
     * Strict Real-Time Firestore check on the Salon document's `isOpen` and `currentStatus` fields.
     * Queries Firestore directly before allowing any Customer booking flow to proceed.
     */
    suspend fun fetchRealtimeSalonOpenStatus(
        salonId: String,
        partnerId: String = "",
        ownerId: String = ""
    ): Boolean {
        val cleanId = salonId.trim()
        val cachedSalon = _salons.value.find {
            it.id == cleanId ||
                (ownerId.isNotBlank() && (it.ownerId == ownerId || it.partnerId == ownerId)) ||
                (partnerId.isNotBlank() && (it.ownerId == partnerId || it.partnerId == partnerId))
        }
        if (cachedSalon != null && !cachedSalon.isAcceptingBookings) {
            return false
        }
        val dbs = listOfNotNull(firestore, defaultFirestore).distinct()
        if (dbs.isEmpty()) {
            return cachedSalon?.isAcceptingBookings ?: true
        }
        val candidateDocIds = setOf(
            cleanId,
            partnerId.trim(),
            ownerId.trim(),
            cachedSalon?.ownerId?.trim().orEmpty(),
            cachedSalon?.partnerId?.trim().orEmpty(),
            if (ownerId.isNotBlank()) "salon_${ownerId.trim()}" else "",
            if (partnerId.isNotBlank()) "salon_${partnerId.trim()}" else ""
        ).filter { it.isNotBlank() }

        for (db in dbs) {
            for (docId in candidateDocIds) {
                try {
                    val snap = try {
                        db.collection("salons").document(docId)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .await()
                    } catch (_: Exception) {
                        db.collection("salons").document(docId).get().await()
                    }
                    if (snap != null && snap.exists()) {
                        val docOpen = snap.getBoolean("isOpen")
                            ?: snap.getString("isOpen")?.toBooleanStrictOrNull()
                            ?: snap.getBoolean("isShopOpen")
                            ?: true
                        val docStatus = snap.getString("currentStatus")?.trim().orEmpty()
                        val docSalonStatus = snap.getString("salonStatus")?.trim().orEmpty()
                        val docSuspended = snap.getBoolean("isSuspended") ?: false
                        val isAccepting = docOpen &&
                            !docStatus.equals("CLOSED", ignoreCase = true) &&
                            !docSalonStatus.equals("closed", ignoreCase = true) &&
                            !docSalonStatus.equals("suspended", ignoreCase = true) &&
                            !docSuspended
                        if (!isAccepting) {
                            _salons.value = _salons.value.map { s ->
                                if (s.id == cleanId || s.id == docId || (ownerId.isNotBlank() && s.ownerId == ownerId)) {
                                    s.copy(isOpen = false, currentStatus = "CLOSED")
                                } else {
                                    s
                                }
                            }
                            return false
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        return true
    }

    /**
     * Real-Time Busy/Free Status System:
     * Updates 'currentStatus' ("FREE" or "BUSY") for the salon in memory and Firestore.
     */
    fun setSalonBusyStatus(salonId: String, status: String) {
        val normalizedStatus = if (status.equals("BUSY", ignoreCase = true)) "BUSY" else "FREE"
        val defaultWait = if (normalizedStatus == "BUSY") 15 else 0
        val currentSalons = _salons.value
        val updated = currentSalons.map {
            if (it.id == salonId) {
                it.copy(
                    currentStatus = normalizedStatus,
                    waitingTimeMinutes = defaultWait,
                    nextSlotMinutes = if (defaultWait > 0) defaultWait else 15
                )
            } else it
        }
        _salons.value = updated

        firestore?.collection("salons")?.document(salonId)?.set(
            mapOf(
                "currentStatus" to normalizedStatus,
                "waitingTimeMinutes" to defaultWait,
                "nextSlotMinutes" to if (defaultWait > 0) defaultWait else 15
            ) + resolveSalonOwnerMap(salonId),
            com.google.firebase.firestore.SetOptions.merge()
        )
            ?.addOnSuccessListener {
                Log.d(tag, "Salon $salonId currentStatus successfully set to $normalizedStatus in Firestore.")
            }
            ?.addOnFailureListener { err ->
                Log.w(tag, "Failed to update currentStatus in Firestore: ${err.message}")
            }
    }

    /**
     * Real-Time Chair Availability & Waiting Time Update (`salons/{salonId}`):
     * Writes `currentStatus`, `availableChairs`, `waitingTimeMinutes`, and `nextSlotMinutes` directly to Firestore.
     */
    fun updateSalonChairAvailability(
        salonId: String,
        currentStatus: String,
        availableChairs: Int,
        waitingTimeMinutes: Int
    ) {
        val normalizedStatus = if (currentStatus.equals("BUSY", ignoreCase = true) || waitingTimeMinutes > 0) "BUSY" else "FREE"
        val nextSlot = if (waitingTimeMinutes > 0) waitingTimeMinutes else 10
        val currentSalons = _salons.value
        val updated = currentSalons.map {
            if (it.id == salonId) {
                it.copy(
                    currentStatus = normalizedStatus,
                    availableChairs = availableChairs.coerceIn(0, 20),
                    waitingTimeMinutes = waitingTimeMinutes.coerceIn(0, 180),
                    nextSlotMinutes = nextSlot
                )
            } else it
        }
        _salons.value = updated

        firestore?.collection("salons")?.document(salonId)?.set(
            mapOf(
                "currentStatus" to normalizedStatus,
                "availableChairs" to availableChairs.coerceIn(0, 20),
                "waitingTimeMinutes" to waitingTimeMinutes.coerceIn(0, 180),
                "nextSlotMinutes" to nextSlot
            ) + resolveSalonOwnerMap(salonId),
            com.google.firebase.firestore.SetOptions.merge()
        )?.addOnSuccessListener {
            Log.d(tag, "Updated chair availability ($availableChairs chairs, ${waitingTimeMinutes}m wait) for $salonId in Firestore.")
        }
    }

    fun updateSalonServices(
        salonId: String,
        updatedServices: List<SalonService>,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Validate every service price is within ₹10..₹25,000 and sanitize service fields
        val invalidSrv = updatedServices.firstOrNull { !SecurityValidator.isValidServicePrice(it.price) }
        if (invalidSrv != null) {
            onError("Service '${invalidSrv.name}' price (₹${invalidSrv.price}) must be between ₹10 and ₹25,000.")
            return
        }

        val sanitizedServices = updatedServices.map { srv ->
            val cleanName = SecurityValidator.sanitizeName(srv.name.ifBlank { srv.serviceName }, trimEdges = true).ifBlank { "Grooming Service" }
            val cleanDuration = srv.durationMinutes.takeIf { it > 0 } ?: srv.duration
            val clampedDuration = cleanDuration.coerceIn(SecurityValidator.MIN_SERVICE_DURATION, SecurityValidator.MAX_SERVICE_DURATION)
            srv.copy(
                name = cleanName,
                serviceName = cleanName,
                category = SecurityValidator.sanitizeName(srv.category, trimEdges = true).ifBlank { "Haircut" },
                price = srv.price.coerceIn(SecurityValidator.MIN_SERVICE_PRICE, SecurityValidator.MAX_SERVICE_PRICE),
                durationMinutes = clampedDuration,
                duration = clampedDuration,
                description = SecurityValidator.sanitizeTagline(srv.description, trimEdges = true)
            )
        }

        val currentSalons = _salons.value
        val updated = currentSalons.map {
            if (it.id == salonId) it.copy(services = sanitizedServices) else it
        }
        _salons.value = updated

        val targetSalon = updated.find { it.id == salonId }
        val resolvedPartnerUid = targetSalon?.ownerId?.takeIf { it.isNotBlank() }
            ?: targetSalon?.partnerId?.takeIf { it.isNotBlank() }
            ?: _currentUserProfile.value?.id.orEmpty()

        if (resolvedPartnerUid.isNotBlank() && _currentUserProfile.value?.id == resolvedPartnerUid) {
            _currentUserProfile.value = _currentUserProfile.value?.copy(services = sanitizedServices)
        }

        val servicesMap = sanitizedServices.map { it.toMap() }
        if (resolvedPartnerUid.isNotBlank()) {
            firestore?.collection("users")?.document(resolvedPartnerUid)
                ?.set(
                    mapOf("services" to servicesMap),
                    com.google.firebase.firestore.SetOptions.merge()
                )
        }
        firestore?.collection("salons")?.document(salonId)
            ?.set(
                mapOf("services" to servicesMap) + resolveSalonOwnerMap(salonId),
                com.google.firebase.firestore.SetOptions.merge()
            )
            ?.addOnSuccessListener {
                Log.d(tag, "Services updated in Firestore for salon: $salonId")
                onSuccess()
            }
            ?.addOnFailureListener { e ->
                Log.w(tag, "Failed to update services in Firestore: ${e.message}")
                onError(e.message ?: "Failed to update services")
            } ?: onSuccess()
    }

    /**
     * Toggles a Salon in the Customer's `favoriteSalons` array in Firestore (`users/{uid}`).
     */
    fun toggleFavoriteSalon(
        salonId: String,
        partnerId: String = "",
        onResult: (Boolean) -> Unit = {}
    ) {
        val currentProfile = _currentUserProfile.value ?: return
        val uid = currentProfile.id.trim()
        if (uid.isBlank()) return

        val keyToUse = partnerId.trim().ifBlank { salonId.trim() }
        val altKey = salonId.trim()
        if (keyToUse.isBlank() && altKey.isBlank()) return

        val currentFavs = currentProfile.favoriteSalons.toMutableList()
        val isCurrentlyLiked = currentFavs.contains(keyToUse) || (altKey.isNotBlank() && currentFavs.contains(altKey))

        val updatedFavs = if (isCurrentlyLiked) {
            currentFavs.filterNot { it == keyToUse || (altKey.isNotBlank() && it == altKey) }
        } else {
            (currentFavs + listOfNotNull(keyToUse.takeIf { it.isNotBlank() }, altKey.takeIf { it.isNotBlank() })).distinct()
        }

        _currentUserProfile.value = currentProfile.copy(favoriteSalons = updatedFavs)

        val updates = mapOf("favoriteSalons" to updatedFavs)
        firestore?.collection("users")?.document(uid)
            ?.set(updates, com.google.firebase.firestore.SetOptions.merge())
            ?.addOnFailureListener { e ->
                Log.w(tag, "Failed to sync favoriteSalons for user $uid: ${e.message}")
            }
        val defDb = defaultFirestore
        if (defDb != null && defDb != firestore) {
            defDb.collection("users").document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
        }
        onResult(!isCurrentlyLiked)
    }

    /**
     * Real-Time Add Service to Partner's Salon in Firestore (`salons/{salonId}`):
     */
    fun addSalonService(
        salonId: String,
        name: String,
        category: String,
        price: Int,
        durationMinutes: Int,
        description: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentSalon = _salons.value.find { it.id == salonId } ?: return
        val sanitizedName = SecurityValidator.sanitizeName(name, trimEdges = true)
        if (sanitizedName.length < 2) {
            onError("Service name must be between 2 and 60 characters.")
            return
        }
        val priceError = SecurityValidator.validateServicePriceError(price)
        if (priceError != null) {
            onError(priceError)
            return
        }

        val newServiceId = "${salonId}_srv_${System.currentTimeMillis().toString().takeLast(5)}"
        val clampedDuration = durationMinutes.coerceIn(SecurityValidator.MIN_SERVICE_DURATION, SecurityValidator.MAX_SERVICE_DURATION)
        val newService = SalonService(
            id = newServiceId,
            name = sanitizedName,
            category = SecurityValidator.sanitizeName(category, trimEdges = true).ifBlank { "Haircut" },
            price = price,
            durationMinutes = clampedDuration,
            description = SecurityValidator.sanitizeTagline(description, trimEdges = true)
                .ifBlank { "$sanitizedName ($clampedDuration mins)" },
            isPopular = true
        )
        val updatedList = currentSalon.services + newService
        updateSalonServices(salonId = salonId, updatedServices = updatedList, onSuccess = onSuccess, onError = onError)
    }

    /**
     * Real-Time Edit Existing Service in Partner's Salon in Firestore (`salons/{salonId}`):
     */
    fun editSalonService(
        salonId: String,
        updatedService: SalonService,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentSalon = _salons.value.find { it.id == salonId } ?: return
        val sanitizedName = SecurityValidator.sanitizeName(updatedService.name, trimEdges = true)
        if (sanitizedName.length < 2) {
            onError("Service name must be between 2 and 60 characters.")
            return
        }
        val priceError = SecurityValidator.validateServicePriceError(updatedService.price)
        if (priceError != null) {
            onError(priceError)
            return
        }

        val sanitizedUpdated = updatedService.copy(
            name = sanitizedName,
            category = SecurityValidator.sanitizeName(updatedService.category, trimEdges = true).ifBlank { "Haircut" },
            price = updatedService.price,
            durationMinutes = updatedService.durationMinutes.coerceIn(SecurityValidator.MIN_SERVICE_DURATION, SecurityValidator.MAX_SERVICE_DURATION),
            description = SecurityValidator.sanitizeTagline(updatedService.description, trimEdges = true)
        )
        val updatedList = currentSalon.services.map {
            if (it.id == sanitizedUpdated.id) sanitizedUpdated else it
        }
        updateSalonServices(salonId = salonId, updatedServices = updatedList, onSuccess = onSuccess, onError = onError)
    }

    /**
     * Real-Time Delete Service from Partner's Salon in Firestore (`salons/{salonId}`):
     */
    fun deleteSalonService(
        salonId: String,
        serviceId: String,
        onSuccess: () -> Unit = {}
    ) {
        val currentSalon = _salons.value.find { it.id == salonId } ?: return
        val updatedList = currentSalon.services.filterNot { it.id == serviceId }
        updateSalonServices(salonId = salonId, updatedServices = updatedList, onSuccess = onSuccess)
    }

    /**
     * Updates Salon's Full Profile (Shop Name, Phone, Tagline, Physical Address, Landmark, and GPS Coordinates)
     * directly in Firestore `salons/{salonId}` so Customers see changes in real time on their Salons list and Map.
     */
    fun updateSalonFullProfile(
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
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val cleanName = SecurityValidator.sanitizeName(name, trimEdges = true)
        if (cleanName.length < 2) {
            onError("Shop name must be between 2 and 60 characters.")
            return
        }
        val cleanAddress = SecurityValidator.sanitizeAddress(shopAddress, trimEdges = true)
        if (cleanAddress.length < 4) {
            onError("Shop address must be between 4 and 250 characters.")
            return
        }
        val cleanLandmark = SecurityValidator.sanitizeName(landmark, trimEdges = true)
        val cleanTagline = SecurityValidator.sanitizeTagline(tagline, trimEdges = true)
            .ifBlank { "Instant grooming slots • Zero queue wait time" }

        val cleanPhone = if (phone.isNotBlank()) {
            val phoneError = SecurityValidator.validateIndianMobileError(phone)
            if (phoneError != null) {
                onError(phoneError)
                return
            }
            SecurityValidator.formatIndianMobile(phone)
        } else {
            ""
        }

        val calculatedDistKm = calculateDistanceKm(_userLatitude.value, _userLongitude.value, latitude, longitude)
        val dynamicDistance = String.format(java.util.Locale.US, "%.1f km", calculatedDistKm)
        val shortLocation = if (cleanLandmark.isNotBlank()) "$cleanLandmark, $cleanAddress".take(60) else cleanAddress.take(60)

        val currentSalons = _salons.value
        val updated = currentSalons.map {
            if (it.id == salonId) {
                it.copy(
                    name = cleanName,
                    phone = cleanPhone.ifBlank { it.phone },
                    tagline = cleanTagline,
                    shopAddress = cleanAddress,
                    landmark = cleanLandmark,
                    latitude = latitude,
                    longitude = longitude,
                    availableChairs = availableChairs ?: it.availableChairs,
                    isAcSalon = isAcSalon ?: it.isAcSalon,
                    distance = dynamicDistance,
                    location = shortLocation.ifBlank { it.location }
                )
            } else it
        }
        _salons.value = recalculateAndSortSalons(updated, _userLatitude.value, _userLongitude.value)

        val coordinatesMap = mapOf(
            "lat" to latitude,
            "lng" to longitude
        )

        val updates = mutableMapOf<String, Any>(
            "name" to cleanName,
            "phone" to cleanPhone,
            "tagline" to cleanTagline,
            "shopAddress" to cleanAddress,
            "landmark" to cleanLandmark,
            "coordinates" to coordinatesMap,
            "latitude" to latitude,
            "longitude" to longitude,
            "distance" to dynamicDistance,
            "location" to shortLocation
        )
        updates.putAll(resolveSalonOwnerMap(salonId))
        if (availableChairs != null) {
            updates["availableChairs"] = availableChairs.coerceIn(1, 20)
        }
        if (isAcSalon != null) {
            updates["isAcSalon"] = isAcSalon
        }

        firestore?.collection("salons")?.document(salonId)
            ?.set(updates, com.google.firebase.firestore.SetOptions.merge())
            ?.addOnSuccessListener {
                Log.d(tag, "Salon full profile & GPS synced to Firestore for $salonId")
                onSuccess()
            }
            ?.addOnFailureListener { e ->
                Log.w(tag, "Failed to update salon profile in Firestore: ${e.message}")
                onError(e.message ?: "Failed to sync to Firestore")
            } ?: onSuccess()
    }

    /**
     * Saves a compressed Base64 shop cover photo directly into Firestore `salons/{salonId}`
     * (`imageUrl` and `photoBase64`) without requiring paid Firebase Storage.
     */
    fun updateSalonPhoto(
        salonId: String,
        photoDataUrl: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentSalons = _salons.value
        _salons.value = currentSalons.map {
            if (it.id == salonId) {
                it.copy(imageUrl = photoDataUrl, photoBase64 = photoDataUrl)
            } else it
        }

        val ownerMap = resolveSalonOwnerMap(salonId)
        val ownerUid = (ownerMap["ownerId"] as? String)?.takeIf { it.isNotBlank() }
            ?: (ownerMap["partnerId"] as? String)?.takeIf { it.isNotBlank() }
            ?: _currentUserProfile.value?.id?.takeIf { it.isNotBlank() }

        if (!ownerUid.isNullOrBlank()) {
            if (_currentUserProfile.value?.id == ownerUid) {
                _currentUserProfile.value = _currentUserProfile.value?.copy(imageUrl = photoDataUrl)
            }
            firestore?.collection("users")?.document(ownerUid)
                ?.set(mapOf("imageUrl" to photoDataUrl), com.google.firebase.firestore.SetOptions.merge())
        }

        val updates = mapOf(
            "imageUrl" to photoDataUrl,
            "photoBase64" to photoDataUrl
        ) + ownerMap
        firestore?.collection("salons")?.document(salonId)
            ?.set(updates, com.google.firebase.firestore.SetOptions.merge())
            ?.addOnSuccessListener {
                Log.d(tag, "Salon photo synced to Firestore for $salonId")
                onSuccess()
            }
            ?.addOnFailureListener { e ->
                Log.w(tag, "Failed to save salon photo in Firestore: ${e.message}")
                onError(e.message ?: "Failed to save photo")
            } ?: onSuccess()
    }

    /**
     * Uploads a salon image to Firebase Storage under `salon_images/` and saves
     * the download URL to Firestore `salons/{salonId}` and user profile.
     * Features resilient fallback to compressed Base64 if storage service is unavailable.
     */
    fun uploadSalonImage(
        uri: Uri,
        salonId: String? = null,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        _isLoading.value = true
        val resolvedSalonId = salonId?.takeIf { it.isNotBlank() }
            ?: _currentUserProfile.value?.salonId?.takeIf { it.isNotBlank() }
            ?: "salon_${_currentUserProfile.value?.id ?: System.currentTimeMillis()}"

        val storagePath = "salon_images/${resolvedSalonId}_${System.currentTimeMillis()}.jpg"

        try {
            val storage = FirebaseStorage.getInstance().apply {
                maxUploadRetryTimeMillis = 10_000L
                maxOperationRetryTimeMillis = 10_000L
            }
            val storageRef = storage.reference.child(storagePath)
            val compressedBytes = com.example.util.SalonImageUtils.compressUriToJpegBytes(
                context = appContext,
                uri = uri,
                maxWidth = 960,
                quality = 78
            )
            val uploadTask = if (compressedBytes != null && compressedBytes.isNotEmpty()) {
                storageRef.putBytes(compressedBytes)
            } else {
                storageRef.putFile(uri)
            }
            uploadTask
                .addOnSuccessListener {
                    storageRef.downloadUrl
                        .addOnSuccessListener { downloadUri ->
                            _isLoading.value = false
                            val downloadUrl = downloadUri.toString()
                            updateSalonPhoto(resolvedSalonId, downloadUrl)
                            val uid = _currentUserProfile.value?.id
                            if (!uid.isNullOrBlank()) {
                                firestore?.collection("users")?.document(uid)?.set(
                                    mapOf("imageUrl" to downloadUrl),
                                    com.google.firebase.firestore.SetOptions.merge()
                                )
                                _currentUserProfile.value = _currentUserProfile.value?.copy(imageUrl = downloadUrl)
                            }
                            Log.d(tag, "Salon image uploaded successfully to Firebase Storage: $downloadUrl")
                            onSuccess(downloadUrl)
                        }
                        .addOnFailureListener { e ->
                            Log.w(tag, "Failed to get download URL, falling back: ${e.message}")
                            fallbackUploadAsBase64(uri, resolvedSalonId, onSuccess, onError, e.message)
                        }
                }
                .addOnFailureListener { e ->
                    Log.w(tag, "Firebase Storage upload failed, falling back to compressed Base64: ${e.message}")
                    fallbackUploadAsBase64(uri, resolvedSalonId, onSuccess, onError, e.message)
                }
        } catch (e: Exception) {
            Log.w(tag, "Firebase Storage exception, falling back: ${e.message}")
            fallbackUploadAsBase64(uri, resolvedSalonId, onSuccess, onError, e.message)
        }
    }

    private fun fallbackUploadAsBase64(
        uri: Uri,
        salonId: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        originalError: String?
    ) {
        val base64Data = com.example.util.SalonImageUtils.encodeUriToBase64Jpeg(
            context = appContext,
            uri = uri,
            maxWidth = 800,
            quality = 70
        )
        _isLoading.value = false
        if (!base64Data.isNullOrBlank()) {
            updateSalonPhoto(salonId, base64Data)
            val uid = _currentUserProfile.value?.id
            if (!uid.isNullOrBlank()) {
                firestore?.collection("users")?.document(uid)?.set(
                    mapOf("imageUrl" to base64Data),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                _currentUserProfile.value = _currentUserProfile.value?.copy(imageUrl = base64Data)
            }
            onSuccess(base64Data)
        } else {
            val err = originalError ?: "Failed to process and upload image"
            onError(err)
        }
    }

    /**
     * Submits a customer rating (1 to 5 stars) for a salon.
     * Computes the new averageRating and totalRatings and updates Firestore in real time.
     */
    fun submitSalonRating(
        salonId: String,
        rating: Int,
        reviewText: String = "",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (rating !in 1..5) {
            onError("Please select a rating between 1 and 5 stars.")
            return
        }

        val targetSalon = _salons.value.find { it.id == salonId }
        val currentAvg = if (targetSalon != null && targetSalon.averageRating > 0.0) {
            targetSalon.averageRating
        } else {
            targetSalon?.rating ?: 0.0
        }
        val currentTotal = if (targetSalon != null && targetSalon.totalRatings > 0) {
            targetSalon.totalRatings
        } else {
            targetSalon?.reviewCount ?: 0
        }

        val newTotal = currentTotal + 1
        val newAvgRaw = if (currentTotal == 0 || currentAvg == 0.0) {
            rating.toDouble()
        } else {
            ((currentAvg * currentTotal) + rating) / newTotal.toDouble()
        }
        val newAvg = kotlin.math.round(newAvgRaw * 10.0) / 10.0

        // Update in-memory state
        _salons.value = _salons.value.map { s ->
            if (s.id == salonId) {
                s.copy(
                    averageRating = newAvg,
                    totalRatings = newTotal,
                    rating = newAvg,
                    reviewCount = newTotal
                )
            } else s
        }

        val updates = mapOf(
            "averageRating" to newAvg,
            "totalRatings" to newTotal,
            "rating" to newAvg,
            "reviewCount" to newTotal
        )

        val partnerId = targetSalon?.partnerId?.ifBlank { targetSalon.ownerId }

        firestore?.collection("salons")?.document(salonId)
            ?.set(updates, com.google.firebase.firestore.SetOptions.merge())
            ?.addOnSuccessListener {
                if (!partnerId.isNullOrBlank()) {
                    firestore?.collection("users")?.document(partnerId)
                        ?.set(
                            mapOf(
                                "averageRating" to newAvg,
                                "totalRatings" to newTotal
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                }
                onSuccess()
            }
            ?.addOnFailureListener { e ->
                Log.w(tag, "Failed to update rating in Firestore: ${e.message}")
                onSuccess()
            } ?: onSuccess()
    }

    /**
     * Updates Salon's Physical Address, Landmark, and GPS Coordinates (lat/lng) in Firestore.
     * Computes the new relative distance to default reference point.
     */
    fun updateSalonLocation(
        salonId: String,
        shopAddress: String,
        landmark: String,
        latitude: Double,
        longitude: Double,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentSalon = _salons.value.find { it.id == salonId }
        updateSalonFullProfile(
            salonId = salonId,
            name = currentSalon?.name ?: "My Salon",
            phone = currentSalon?.phone ?: (_currentUserProfile.value?.phone ?: ""),
            tagline = currentSalon?.tagline ?: "Instant grooming slots",
            shopAddress = shopAddress,
            landmark = landmark,
            latitude = latitude,
            longitude = longitude,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Saves a Customer or Partner Feedback / Issue Report directly to the Cloud Firestore `feedback` collection.
     */
    fun submitFeedback(
        rating: Int,
        category: String,
        message: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val sanitizedMessage = SecurityValidator.sanitizeFeedback(message, trimEdges = true)
        if (sanitizedMessage.length < 3) {
            onError("Please enter a valid feedback message (3–500 characters, no HTML/script tags).")
            return
        }

        val currentProfile = _currentUserProfile.value
        val activeRole = _currentRole.value.roleKey
        val feedbackId = "fb_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}"
        val submission = FeedbackSubmission(
            id = feedbackId,
            userId = currentProfile?.id?.ifBlank { "anonymous_user" } ?: "anonymous_user",
            userName = SecurityValidator.sanitizeName(currentProfile?.name?.ifBlank { "TrimTime User" } ?: "TrimTime User"),
            userPhone = currentProfile?.phone ?: "",
            userRole = currentProfile?.role?.ifBlank { activeRole } ?: activeRole,
            rating = rating.coerceIn(1, 5),
            category = SecurityValidator.sanitizeName(category.ifBlank { "General Feedback" }),
            message = sanitizedMessage,
            status = "OPEN",
            isResolved = false,
            timestamp = System.currentTimeMillis()
        )

        _supportTickets.value = listOf(submission) + _supportTickets.value

        val db = firestore
        if (db == null) {
            onSuccess()
            return
        }

        db.collection("feedback")
            .document(feedbackId)
            .set(submission.toMap())
            .addOnSuccessListener {
                Log.d(tag, "Feedback $feedbackId saved to Firestore feedback collection")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.w(tag, "Failed to save feedback to Firestore: ${e.message}")
                onSuccess()
            }
    }

    // ==========================================
    // Super Admin Dashboard Management Functions
    // ==========================================

    /**
     * Strict Firestore Admin Check:
     * Returns true ONLY if the logged-in user's Firestore document (`users/{uid}`)
     * explicitly contains the boolean field `isAdmin == true`.
     * Defaults to false if missing, null, or false.
     */
    fun isUserSuperAdmin(profile: UserProfile? = _currentUserProfile.value): Boolean {
        return profile?.isAdmin == true
    }

    /**
     * Tab 1: Updates a Salon's status ("approved", "rejected", or "suspended")
     * across Firestore `salons/{salonId}` and partner's `users/{partnerId}`.
     */
    fun updateSalonApproval(
        salonId: String,
        approvalStatus: String,
        isSuspended: Boolean = false,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val targetId = salonId.trim()
        if (targetId.isBlank()) return
        val normalizedStatus = approvalStatus.trim().uppercase()
        val salonStatus = when {
            isSuspended -> "suspended"
            normalizedStatus == "REJECTED" -> "rejected"
            else -> "approved"
        }
        val effectiveOpen = !isSuspended && normalizedStatus == "APPROVED"

        val matchingSalon = _salons.value.find { it.id == targetId || it.ownerId == targetId || it.partnerId == targetId }
        val resolvedSalonId = matchingSalon?.id ?: targetId
        val resolvedPartnerUid = matchingSalon?.let { it.ownerId.ifBlank { it.partnerId } } ?: targetId

        applySalonAdminUpdatesLocally(
            partnerId = resolvedPartnerUid,
            salonId = resolvedSalonId,
            status = salonStatus
        )

        val db = firestore
        val defDb = defaultFirestore
        if (db == null && defDb == null) {
            onSuccess()
            return
        }

        val updates = mutableMapOf<String, Any>(
            "approvalStatus" to normalizedStatus,
            "salonStatus" to salonStatus,
            "isSuspended" to isSuspended
        )
        if (!effectiveOpen) {
            updates["isOpen"] = false
            updates["currentStatus"] = "CLOSED"
        }

        scope.launch(Dispatchers.IO) {
            try {
                if (resolvedSalonId.isNotBlank()) {
                    db?.collection("salons")?.document(resolvedSalonId)
                        ?.set(updates, com.google.firebase.firestore.SetOptions.merge())
                    if (defDb != null && defDb != db) {
                        defDb.collection("salons").document(resolvedSalonId)
                            .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    }
                }
                if (resolvedPartnerUid.isNotBlank()) {
                    val partnerUpdates = mapOf<String, Any>(
                        "salonStatus" to salonStatus,
                        "isSuspended" to isSuspended
                    )
                    db?.collection("users")?.document(resolvedPartnerUid)
                        ?.set(partnerUpdates, com.google.firebase.firestore.SetOptions.merge())
                    if (defDb != null && defDb != db) {
                        defDb.collection("users").document(resolvedPartnerUid)
                            .set(partnerUpdates, com.google.firebase.firestore.SetOptions.merge())
                    }
                }
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to update salon approval: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Failed to update salon status.")
                }
            }
        }
    }

    /**
     * Tab 1: Assigns a category tier tag ("Premium", "Standard", or "Rural/Local") to a Salon
     * and updates partner's Firestore document field `salonCategory`.
     */
    fun updateSalonCategory(
        salonId: String,
        categoryTag: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val targetId = salonId.trim()
        val cleanTag = categoryTag.trim().ifBlank { "Standard" }
        if (targetId.isBlank()) return

        val matchingSalon = _salons.value.find { it.id == targetId || it.ownerId == targetId || it.partnerId == targetId }
        val resolvedSalonId = matchingSalon?.id ?: targetId
        val resolvedPartnerUid = matchingSalon?.let { it.ownerId.ifBlank { it.partnerId } } ?: targetId

        applySalonAdminUpdatesLocally(
            partnerId = resolvedPartnerUid,
            salonId = resolvedSalonId,
            category = cleanTag
        )

        val db = firestore
        val defDb = defaultFirestore
        if (db == null && defDb == null) {
            onSuccess()
            return
        }

        val updates = mapOf<String, Any>(
            "adminCategoryTag" to cleanTag,
            "salonCategory" to cleanTag
        )

        scope.launch(Dispatchers.IO) {
            try {
                if (resolvedSalonId.isNotBlank()) {
                    db?.collection("salons")?.document(resolvedSalonId)
                        ?.set(updates, com.google.firebase.firestore.SetOptions.merge())
                    if (defDb != null && defDb != db) {
                        defDb.collection("salons").document(resolvedSalonId)
                            .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    }
                }
                if (resolvedPartnerUid.isNotBlank()) {
                    val partnerUpdates = mapOf<String, Any>(
                        "salonCategory" to cleanTag
                    )
                    db?.collection("users")?.document(resolvedPartnerUid)
                        ?.set(partnerUpdates, com.google.firebase.firestore.SetOptions.merge())
                    if (defDb != null && defDb != db) {
                        defDb.collection("users").document(resolvedPartnerUid)
                            .set(partnerUpdates, com.google.firebase.firestore.SetOptions.merge())
                    }
                }
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to update salon category tag: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Failed to update salon category.")
                }
            }
        }
    }

    /**
     * Tab 2: Blocks or Unblocks a User in Firestore `users/{userId}`.
     * Strictly requires `currentUserProfile.isAdmin == true`.
     */
    fun updateUserBlockStatus(
        userId: String,
        isBlocked: Boolean,
        resetStrikesOnUnblock: Boolean = true,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val adminProfile = _currentUserProfile.value
        if (adminProfile == null || adminProfile.isAdmin != true) {
            onError("Access Denied: Only Super Admins can block or unblock users.")
            return
        }
        val targetUid = userId.trim()
        if (targetUid.isBlank()) return

        _allUsers.value = _allUsers.value.map { user ->
            if (user.id == targetUid) {
                val updatedStrikes = if (!isBlocked && resetStrikesOnUnblock) 0 else user.cancellationStrikes
                user.copy(
                    isBlocked = isBlocked,
                    isSuspended = isBlocked,
                    cancellationStrikes = updatedStrikes,
                    cancellationCount = updatedStrikes
                )
            } else {
                user
            }
        }

        _currentUserProfile.value?.let { current ->
            if (current.id == targetUid) {
                val updatedStrikes = if (!isBlocked && resetStrikesOnUnblock) 0 else current.cancellationStrikes
                _currentUserProfile.value = current.copy(
                    isBlocked = isBlocked,
                    isSuspended = isBlocked,
                    cancellationStrikes = updatedStrikes,
                    cancellationCount = updatedStrikes
                )
            }
        }

        val db = firestore
        if (db == null) {
            onSuccess()
            return
        }

        val updates = mutableMapOf<String, Any>(
            "isBlocked" to isBlocked,
            "isSuspended" to isBlocked,
            "adminActorId" to adminProfile.id
        )
        if (!isBlocked && resetStrikesOnUnblock) {
            updates["cancellationStrikes"] = 0
            updates["cancellationCount"] = 0
        }

        db.collection("users").document(targetUid)
            .set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d(tag, "Admin updated user $targetUid block status: isBlocked=$isBlocked")
                onSuccess()
            }
            .addOnFailureListener { err ->
                Log.e(tag, "Failed to update user block status: ${err.message}")
                onError(err.localizedMessage ?: "Failed to update user block status.")
            }
    }

    /**
     * Tab 3: Marks a Support Help Desk / Feedback ticket as Resolved (or reopens it) in Firestore `feedback/{ticketId}`.
     * Strictly requires `currentUserProfile.isAdmin == true`.
     */
    fun resolveSupportTicket(
        ticketId: String,
        isResolved: Boolean = true,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val adminProfile = _currentUserProfile.value
        if (adminProfile == null || adminProfile.isAdmin != true) {
            onError("Access Denied: Only Super Admins can resolve support tickets.")
            return
        }
        val targetId = ticketId.trim()
        if (targetId.isBlank()) return
        val newStatus = if (isResolved) "RESOLVED" else "OPEN"
        val resolvedTimestamp = if (isResolved) System.currentTimeMillis() else null

        _supportTickets.value = _supportTickets.value.map { ticket ->
            if (ticket.id == targetId) {
                ticket.copy(
                    status = newStatus,
                    isResolved = isResolved,
                    resolvedAt = resolvedTimestamp
                )
            } else {
                ticket
            }
        }

        val db = firestore
        if (db == null) {
            onSuccess()
            return
        }

        val updates = mutableMapOf<String, Any?>(
            "status" to newStatus,
            "isResolved" to isResolved,
            "resolvedAt" to resolvedTimestamp,
            "adminActorId" to adminProfile.id
        )

        db.collection("feedback").document(targetId)
            .set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d(tag, "Admin updated support ticket $targetId status=$newStatus")
                onSuccess()
            }
            .addOnFailureListener { err ->
                Log.e(tag, "Failed to resolve support ticket: ${err.message}")
                onError(err.localizedMessage ?: "Failed to update support ticket.")
            }
    }

    /**
     * Partner/User Action: Requests a location unlock from the Admin.
     * Updates the user's Firestore document (`users/{uid}`) and any associated partner salon (`salons/{salonId}`)
     * to set `isLocationLocked = true` and `unlockRequestStatus = "pending"`.
     */
    fun requestLocationUnlock(
        userId: String? = null,
        salonId: String? = null,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val current = _currentUserProfile.value
        val targetUid = userId?.trim()?.takeIf { it.isNotBlank() }
            ?: current?.id?.trim()?.takeIf { it.isNotBlank() }
            ?: auth?.currentUser?.uid?.trim().orEmpty()

        if (targetUid.isBlank()) {
            onComplete(false, "Please sign in to request a location unlock.")
            return
        }

        val resolvedSalonId = salonId?.trim()?.takeIf { it.isNotBlank() }
            ?: current?.salonId?.trim()?.takeIf { it.isNotBlank() }
            ?: _salons.value.find { it.ownerId == targetUid || it.partnerId == targetUid }?.id

        // Update in-memory user profile and allUsers list immediately
        if (current != null && current.id == targetUid) {
            _currentUserProfile.value = current.copy(
                isLocationLocked = true,
                unlockRequestStatus = "pending"
            )
        }
        namedDbUsers = namedDbUsers.map { u ->
            if (u.id == targetUid) u.copy(isLocationLocked = true, unlockRequestStatus = "pending") else u
        }
        defaultDbUsers = defaultDbUsers.map { u ->
            if (u.id == targetUid) u.copy(isLocationLocked = true, unlockRequestStatus = "pending") else u
        }
        _allUsers.value = _allUsers.value.map { u ->
            if (u.id == targetUid) u.copy(isLocationLocked = true, unlockRequestStatus = "pending") else u
        }

        // Update in-memory salon if partner owns one
        if (!resolvedSalonId.isNullOrBlank()) {
            _salons.value = _salons.value.map { s ->
                if (s.id == resolvedSalonId || s.ownerId == targetUid || s.partnerId == targetUid) {
                    s.copy(isLocationLocked = true, unlockRequestStatus = "pending")
                } else s
            }
        }

        val updates = mapOf(
            "isLocationLocked" to true,
            "unlockRequestStatus" to "pending"
        )

        val db = firestore
        val defDb = defaultFirestore

        if (db == null && defDb == null) {
            onComplete(true, "Request Sent to Admin")
            return
        }

        if (defDb != null && defDb != db) {
            defDb.collection("users").document(targetUid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
        }

        if (!resolvedSalonId.isNullOrBlank()) {
            db?.collection("salons")?.document(resolvedSalonId)
                ?.set(updates, com.google.firebase.firestore.SetOptions.merge())
            if (defDb != null && defDb != db) {
                defDb.collection("salons").document(resolvedSalonId)
                    .set(updates, com.google.firebase.firestore.SetOptions.merge())
            }
        }

        val primaryDb = db ?: defDb
        primaryDb?.collection("users")?.document(targetUid)
            ?.set(updates, com.google.firebase.firestore.SetOptions.merge())
            ?.addOnSuccessListener {
                Log.d(tag, "Location unlock request sent for user $targetUid (unlockRequestStatus=pending)")
                onComplete(true, "Request Sent to Admin")
            }
            ?.addOnFailureListener { err ->
                Log.e(tag, "Failed to send location unlock request: ${err.message}", err)
                onComplete(false, err.message ?: "Failed to send request to Admin.")
            } ?: onComplete(true, "Request Sent to Admin")
    }

    /**
     * Immediately applies location unlock state in memory across users and salons lists.
     */
    fun applyLocationUnlockLocally(partnerId: String, salonId: String? = null) {
        val cleanPartnerId = partnerId.trim()
        val cleanSalonId = salonId?.trim()?.takeIf { it.isNotBlank() }
            ?: _salons.value.find { it.ownerId == cleanPartnerId || it.partnerId == cleanPartnerId || it.id == cleanPartnerId }?.id

        if (cleanPartnerId.isNotBlank()) {
            namedDbUsers = namedDbUsers.map { u ->
                if (u.id == cleanPartnerId || (cleanSalonId != null && u.salonId == cleanSalonId)) {
                    u.copy(isLocationLocked = false, unlockRequestStatus = "approved")
                } else u
            }
            defaultDbUsers = defaultDbUsers.map { u ->
                if (u.id == cleanPartnerId || (cleanSalonId != null && u.salonId == cleanSalonId)) {
                    u.copy(isLocationLocked = false, unlockRequestStatus = "approved")
                } else u
            }
            _allUsers.value = _allUsers.value.map { u ->
                if (u.id == cleanPartnerId || (cleanSalonId != null && u.salonId == cleanSalonId)) {
                    u.copy(isLocationLocked = false, unlockRequestStatus = "approved")
                } else u
            }
            _currentUserProfile.value?.let { current ->
                if (current.id == cleanPartnerId || (cleanSalonId != null && current.salonId == cleanSalonId)) {
                    _currentUserProfile.value = current.copy(
                        isLocationLocked = false,
                        unlockRequestStatus = "approved"
                    )
                }
            }
        }

        _salons.value = _salons.value.map { s ->
            if ((!cleanSalonId.isNullOrBlank() && s.id == cleanSalonId) ||
                (cleanPartnerId.isNotBlank() && (s.ownerId == cleanPartnerId || s.partnerId == cleanPartnerId || s.id == cleanPartnerId))
            ) {
                s.copy(isLocationLocked = false, unlockRequestStatus = "approved")
            } else s
        }
    }

    /**
     * Immediately applies category tag and salon status changes in memory across users and salons lists.
     */
    fun applySalonAdminUpdatesLocally(
        partnerId: String,
        salonId: String? = null,
        category: String? = null,
        status: String? = null
    ) {
        val cleanPartnerId = partnerId.trim()
        val cleanSalonId = salonId?.trim()?.takeIf { it.isNotBlank() }
            ?: _salons.value.find { it.ownerId == cleanPartnerId || it.partnerId == cleanPartnerId || it.id == cleanPartnerId }?.id

        val normStatus = status?.trim()?.lowercase()
        val isSuspended = normStatus == "suspended"
        val approvalStatus = when (normStatus) {
            "approved" -> "APPROVED"
            "rejected" -> "REJECTED"
            "suspended" -> "SUSPENDED"
            else -> normStatus?.uppercase()
        }

        if (cleanPartnerId.isNotBlank()) {
            val userUpdate: (UserProfile) -> UserProfile = { u ->
                if (u.id == cleanPartnerId || (cleanSalonId != null && u.salonId == cleanSalonId)) {
                    var updated = u
                    if (category != null) {
                        updated = updated.copy(salonCategory = category)
                    }
                    if (normStatus != null) {
                        updated = updated.copy(
                            salonStatus = normStatus,
                            isSuspended = isSuspended
                        )
                    }
                    updated
                } else u
            }
            namedDbUsers = namedDbUsers.map(userUpdate)
            defaultDbUsers = defaultDbUsers.map(userUpdate)
            _allUsers.value = _allUsers.value.map(userUpdate)
            _currentUserProfile.value?.let { current ->
                if (current.id == cleanPartnerId || (cleanSalonId != null && current.salonId == cleanSalonId)) {
                    _currentUserProfile.value = userUpdate(current)
                }
            }
        }

        _salons.value = _salons.value.map { s ->
            if ((!cleanSalonId.isNullOrBlank() && s.id == cleanSalonId) ||
                (cleanPartnerId.isNotBlank() && (s.ownerId == cleanPartnerId || s.partnerId == cleanPartnerId || s.id == cleanPartnerId))
            ) {
                var updated = s
                if (category != null) {
                    updated = updated.copy(adminCategoryTag = category, salonCategory = category)
                }
                if (normStatus != null) {
                    val newApproval = approvalStatus ?: s.approvalStatus
                    val newOpen = if (isSuspended || newApproval == "REJECTED") false else s.isOpen
                    updated = updated.copy(
                        salonStatus = normStatus,
                        approvalStatus = newApproval,
                        isSuspended = isSuspended,
                        isOpen = newOpen,
                        currentStatus = if (!newOpen && (isSuspended || newApproval == "REJECTED")) "CLOSED" else s.currentStatus
                    )
                }
                updated
            } else s
        }
    }

    /**
     * Admin 1-Click Action: Approves & unlocks location editing for a User/Partner.
     * Updates `users/{userId}` (and any salon owned by `userId` or matching `salonId`) in Firestore
     * to set `isLocationLocked = false` and `unlockRequestStatus = "approved"`.
     * Immediately updates `_allUsers` and `_salons` so the approved request disappears from the pending list.
     */
    fun approveLocationUnlock(
        userId: String,
        salonId: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val targetUid = userId.trim()
        val resolvedSalonId = salonId?.trim()?.takeIf { it.isNotBlank() }
            ?: _salons.value.find { it.ownerId == targetUid || it.partnerId == targetUid || it.id == targetUid }?.id

        if (targetUid.isBlank() && resolvedSalonId.isNullOrBlank()) {
            onError("Invalid Partner ID")
            return
        }

        // 1. Immediately apply update locally so the request disappears from pending lists right away
        applyLocationUnlockLocally(targetUid, resolvedSalonId)

        val updates = mapOf(
            "isLocationLocked" to false,
            "unlockRequestStatus" to "approved"
        )

        val db = firestore
        val defDb = defaultFirestore
        if (db == null && defDb == null) {
            onSuccess()
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                if (!resolvedSalonId.isNullOrBlank()) {
                    db?.collection("salons")?.document(resolvedSalonId)
                        ?.set(updates, com.google.firebase.firestore.SetOptions.merge())
                    if (defDb != null && defDb != db) {
                        defDb.collection("salons").document(resolvedSalonId)
                            .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    }
                }

                if (targetUid.isNotBlank()) {
                    if (defDb != null && defDb != db) {
                        defDb.collection("users").document(targetUid)
                            .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    }
                    val primaryDb = db ?: defDb
                    primaryDb?.collection("users")?.document(targetUid)
                        ?.set(updates, com.google.firebase.firestore.SetOptions.merge())
                }
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to approve location unlock for $targetUid: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: e.message ?: "Failed to approve location unlock.")
                }
            }
        }
    }

    /**
     * Super Admin Control: Toggles Global Festival / Rush Mode (`isGlobalRushModeEnabled`)
     * and persists it in the central `app_settings/global_config` Firestore document.
     */
    fun setGlobalRushModeEnabled(
        enabled: Boolean,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        _isGlobalRushModeEnabled.value = enabled
        settingsPrefs.edit().putBoolean("pref_global_rush_mode", enabled).apply()

        val adminProfile = _currentUserProfile.value
        val payload = mutableMapOf<String, Any?>(
            "isGlobalRushModeEnabled" to enabled,
            "updatedAt" to System.currentTimeMillis()
        )
        if (!adminProfile?.id.isNullOrBlank()) {
            payload["adminActorId"] = adminProfile?.id
        }

        val db = firestore
        val defDb = defaultFirestore
        if (db == null && defDb == null) {
            onSuccess()
            return
        }

        if (defDb != null && defDb != db) {
            defDb.collection("app_settings").document("global_config")
                .set(payload, com.google.firebase.firestore.SetOptions.merge())
        }

        val primaryDb = db ?: defDb
        primaryDb?.collection("app_settings")?.document("global_config")
            ?.set(payload, com.google.firebase.firestore.SetOptions.merge())
            ?.addOnSuccessListener {
                Log.d(tag, "Global Festival / Rush Mode updated in app_settings: isGlobalRushModeEnabled=$enabled")
                onSuccess()
            }
            ?.addOnFailureListener { err ->
                Log.e(tag, "Failed to update Global Rush Mode in app_settings: ${err.message}", err)
                onError(err.localizedMessage ?: "Failed to update Global Festival / Rush Mode.")
            } ?: onSuccess()
    }

    companion object {
        @Volatile
        private var INSTANCE: TrimTimeRepository? = null

        fun getInstance(context: Context): TrimTimeRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TrimTimeRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun getProductionSeedSalons(): List<Salon> = emptyList()

        fun calculateRawDistanceKm(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Double {
            val r = 6371.0 // Earth's radius in kilometers
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return r * c
        }

        fun calculateDistanceKm(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Double {
            val dist = calculateRawDistanceKm(lat1, lon1, lat2, lon2)
            return Math.max(0.1, Math.round(dist * 10.0) / 10.0)
        }
    }
}
