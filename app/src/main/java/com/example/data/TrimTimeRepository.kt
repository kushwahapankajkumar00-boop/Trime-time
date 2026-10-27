package com.example.data

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
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
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private var isFirebaseReady = false

    private var salonsListener: ListenerRegistration? = null
    private var bookingsListener: ListenerRegistration? = null
    private var customerBookingsListener: ListenerRegistration? = null
    private var userDocListener: ListenerRegistration? = null

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

    // Triggered when Google Sign-In fails on physical APK due to unregistered SHA-1
    private val _showSha1FallbackDialog = MutableStateFlow(false)
    val showSha1FallbackDialog: StateFlow<Boolean> = _showSha1FallbackDialog.asStateFlow()

    private val _isCloudSynced = MutableStateFlow(false)
    val isCloudSynced: StateFlow<Boolean> = _isCloudSynced.asStateFlow()

    // Dedicated Partner Salon Registration Onboarding state (shown when an unregistered user taps Partner)
    private val _showPartnerOnboarding = MutableStateFlow(false)
    val showPartnerOnboarding: StateFlow<Boolean> = _showPartnerOnboarding.asStateFlow()

    // Quick Access (4-Digit MPIN / Fingerprint Biometrics) state
    private val _isSessionUnlocked = MutableStateFlow(true)
    val isSessionUnlocked: StateFlow<Boolean> = _isSessionUnlocked.asStateFlow()

    private val _showQuickAccessSetup = MutableStateFlow(false)
    val showQuickAccessSetup: StateFlow<Boolean> = _showQuickAccessSetup.asStateFlow()

    // Persisted Customer App Settings & Preferences
    private val _appPreferences = MutableStateFlow(loadSavedPreferences())
    val appPreferences: StateFlow<AppPreferences> = _appPreferences.asStateFlow()

    // Live Customer GPS Coordinates & Reverse-Geocoded Location Display
    private val _userLatitude = MutableStateFlow(
        settingsPrefs.getString("pref_user_lat", null)?.toDoubleOrNull()?.takeIf { it != 0.0 } ?: 26.3359
    )
    val userLatitude: StateFlow<Double> = _userLatitude.asStateFlow()

    private val _userLongitude = MutableStateFlow(
        settingsPrefs.getString("pref_user_lng", null)?.toDoubleOrNull()?.takeIf { it != 0.0 } ?: 84.6304
    )
    val userLongitude: StateFlow<Double> = _userLongitude.asStateFlow()

    private val _userLocationTitle = MutableStateFlow(
        settingsPrefs.getString("pref_default_city", null)
            ?.takeIf { it.isNotBlank() && !it.equals("Patna, Bihar", ignoreCase = true) }
            ?: "Detecting Live GPS..."
    )
    val userLocationTitle: StateFlow<String> = _userLocationTitle.asStateFlow()

    private val _userLocationSubtitle = MutableStateFlow(
        settingsPrefs.getString("pref_location_subtitle", "Fetching your live GPS location...")
            ?.ifBlank { "Fetching your live GPS location..." } ?: "Fetching your live GPS location..."
    )
    val userLocationSubtitle: StateFlow<String> = _userLocationSubtitle.asStateFlow()

    private val _isLiveGpsActive = MutableStateFlow(
        settingsPrefs.getBoolean("pref_has_live_gps", false)
    )
    val isLiveGpsActive: StateFlow<Boolean> = _isLiveGpsActive.asStateFlow()

    init {
        initFirebase(context)
    }

    fun isDemoSalon(salonId: String): Boolean = salonId in seedSalonIds

    fun unlockSessionWithQuickAccess() {
        _isSessionUnlocked.value = true
    }

    fun dismissQuickAccessSetup() {
        _showQuickAccessSetup.value = false
    }

    fun openQuickAccessSetup() {
        _showQuickAccessSetup.value = true
    }

    fun saveQuickAccessSettings(
        mpin: String,
        biometricEnabled: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        val cleanMpin = mpin.filter { it.isDigit() }.take(4)
        val current = _currentUserProfile.value
        sessionPrefs.edit()
            .putString("saved_quick_mpin", cleanMpin)
            .putBoolean("saved_biometric_enabled", biometricEnabled)
            .putBoolean("saved_quick_access_enabled", true)
            .apply()

        if (current != null) {
            val updated = current.copy(
                quickAccessMpin = cleanMpin,
                biometricEnabled = biometricEnabled,
                isQuickAccessEnabled = true
            )
            _currentUserProfile.value = updated
            firestore?.collection("users")?.document(current.id)?.set(
                mapOf(
                    "quickAccessMpin" to cleanMpin,
                    "biometricEnabled" to biometricEnabled,
                    "isQuickAccessEnabled" to true
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
        }
        _showQuickAccessSetup.value = false
        _isSessionUnlocked.value = true
        onSuccess()
    }

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
     * and sorts salons by shortest distance first.
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
            }.sortedBy { salon ->
                val sLat = salon.latitude
                val sLng = salon.longitude
                if (sLat != null && sLng != null && sLat != 0.0 && sLng != 0.0) {
                    calculateDistanceKm(userLat, userLng, sLat, sLng)
                } else {
                    salon.distance.replace("km", "", ignoreCase = true).trim().toDoubleOrNull() ?: 999.0
                }
            }
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

    fun dismissSha1FallbackDialog() {
        _showSha1FallbackDialog.value = false
    }

    fun clearAuthError() {
        _authError.value = null
    }

    private fun initFirebase(context: Context) {
        try {
            val apps = FirebaseApp.getApps(context)
            if (apps.isNotEmpty()) {
                auth = FirebaseAuth.getInstance()
                val customDbId = context.getString(R.string.firestore_database_id)
                firestore = FirebaseFirestore.getInstance(customDbId)
                isFirebaseReady = true
                _isCloudSynced.value = true
                Log.d(tag, "Firebase Auth and custom Firestore ($customDbId) initialized successfully.")

                // Check real Firebase Auth state or persisted Firestore account session on startup
                val firebaseUser = auth?.currentUser
                val savedUid = sessionPrefs.getString("saved_uid", null)
                if (firebaseUser != null && firebaseUser.uid !in invalidSharedUserIds) {
                    Log.d(tag, "Active Firebase session detected for uid: ${firebaseUser.uid}")
                    _authState.value = AppAuthState.Loading
                    fetchUserProfile(firebaseUser.uid)
                } else if (!savedUid.isNullOrBlank() && savedUid !in invalidSharedUserIds) {
                    Log.d(tag, "Restoring persisted Firestore user session for uid: $savedUid")
                    _authState.value = AppAuthState.Loading
                    fetchUserProfile(savedUid)
                } else {
                    Log.d(tag, "No active session found. Setting Unauthenticated.")
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
                    _authError.value = "Firestore: ${error.localizedMessage}"
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
                                val lat = (coordinatesMap?.get("lat") as? Number)?.toDouble()
                                    ?: (doc.get("latitude") as? Number)?.toDouble()
                                val lng = (coordinatesMap?.get("lng") as? Number)?.toDouble()
                                    ?: (doc.get("longitude") as? Number)?.toDouble()

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

                                val isOpen = doc.getBoolean("isOpen")
                                    ?: doc.getString("isOpen")?.toBooleanStrictOrNull()
                                    ?: true

                                val currentStatus = doc.getString("currentStatus") ?: "FREE"

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
                                    val sName = (sMap["name"] as? String)
                                        ?: (sMap["serviceName"] as? String)
                                        ?: "Grooming Service"
                                    val sCategory = (sMap["category"] as? String) ?: "Haircut"
                                    val sPrice = (sMap["price"] as? Number)?.toInt()
                                        ?: (sMap["price"] as? String)?.toIntOrNull()
                                        ?: 100
                                    val sDuration = (sMap["durationMinutes"] as? Number)?.toInt()
                                        ?: (sMap["duration"] as? Number)?.toInt()
                                        ?: (sMap["durationMinutes"] as? String)?.toIntOrNull()
                                        ?: 20
                                    val sDesc = (sMap["description"] as? String) ?: ""
                                    val sPop = (sMap["isPopular"] as? Boolean)
                                        ?: (sMap["popular"] as? Boolean)
                                        ?: false

                                    SalonService(
                                        id = sId,
                                        name = sName,
                                        category = sCategory,
                                        price = sPrice,
                                        durationMinutes = sDuration,
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

                                val imageUrl = doc.getString("imageUrl")
                                    ?: doc.getString("photoBase64")
                                    ?: ""
                                val photoBase64 = doc.getString("photoBase64")
                                    ?: doc.getString("imageUrl")
                                    ?: ""

                                val resolvedDocId = doc.getString("id")?.takeIf { it.isNotBlank() } ?: docId
                                val ownerName = doc.getString("ownerName") ?: ""
                                val pincode = doc.getString("pincode") ?: ""
                                val shopCategory = doc.getString("shopCategory") ?: "Men's Salon"
                                val openingTime = doc.getString("openingTime") ?: "9:00 AM"
                                val closingTime = doc.getString("closingTime") ?: "9:00 PM"
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
                                    rating = rating,
                                    reviewCount = reviewCount,
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
                                    openingTime = openingTime,
                                    closingTime = closingTime,
                                    latitude = lat,
                                    longitude = lng,
                                    isRegistrationCompleted = isGenuinelyRegistered,
                                    registrationFormSubmitted = isSeedSalon || isFormSubmittedFlag,
                                    isPhoneVerified = isSalonPhoneVerified
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
                        val combinedSalons = recalculateAndSortSalons(
                            (validLiveSalons + missingSeeds).distinctBy { it.id },
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
            bookingsListener = db.collection("bookings").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "Firestore bookings listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val liveBookings = snapshot.documents.mapNotNull { doc ->
                        parseBookingDocument(doc)
                    }.filter { booking ->
                        booking.customerId.isNotBlank() &&
                            booking.customerId !in invalidSharedUserIds &&
                            booking.id != "past_demo_1"
                    }
                    val sortedAll = liveBookings.sortedByDescending { it.createdAt }
                    _bookings.value = sortedAll

                    // Also keep _customerBookings strictly filtered to currentUser.uid
                    val activeUid = _currentUserProfile.value?.id
                    if (!activeUid.isNullOrBlank() && activeUid !in invalidSharedUserIds) {
                        _customerBookings.value = sortedAll.filter { it.customerId == activeUid }
                    } else {
                        _customerBookings.value = emptyList()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting up live listeners: ${e.message}")
        }
    }

    private fun parseBookingDocument(doc: com.google.firebase.firestore.DocumentSnapshot): Booking? {
        return try {
            val resolvedPin = doc.getString("servicePin")?.takeIf { it.isNotBlank() }
                ?: doc.getString("pin")?.takeIf { it.isNotBlank() }
                ?: "0000"
            Booking(
                id = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id,
                customerId = doc.getString("customerId")?.trim() ?: "",
                customerName = doc.getString("customerName")?.trim() ?: "",
                customerPhone = doc.getString("customerPhone")?.trim() ?: "",
                salonId = doc.getString("salonId")?.trim() ?: "",
                salonName = doc.getString("salonName")?.trim() ?: "",
                service = doc.getString("service")?.trim() ?: "",
                servicePrice = doc.getLong("servicePrice")?.toInt() ?: 100,
                timeSlot = doc.getString("timeSlot") ?: "",
                bookingDate = doc.getString("bookingDate") ?: "Today",
                status = doc.getString("status") ?: "pending",
                pin = resolvedPin,
                createdAt = doc.getLong("createdAt") ?: 0L,
                completedAt = doc.getLong("completedAt")
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
        val cleanUid = customerId.trim()
        if (cleanUid.isBlank() || cleanUid in invalidSharedUserIds) {
            _customerBookings.value = emptyList()
            _latestCompletedBooking.value = null
            return
        }

        // Immediately filter existing in-memory bookings strictly by cleanUid
        val initialScoped = _bookings.value
            .filter { it.customerId == cleanUid }
            .sortedByDescending { it.createdAt }
        _customerBookings.value = initialScoped
        _latestCompletedBooking.value = initialScoped
            .filter { it.status.equals("COMPLETED", ignoreCase = true) }
            .maxByOrNull { it.completedAt ?: it.createdAt }

        val db = firestore ?: return
        customerBookingsListener = db.collection("bookings")
            .whereEqualTo("customerId", cleanUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Customer bookings scoped listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val userBookings = snapshot.documents
                        .mapNotNull { doc -> parseBookingDocument(doc) }
                        .filter { it.customerId == cleanUid && it.id != "past_demo_1" }
                        .sortedByDescending { it.createdAt }
                    _customerBookings.value = userBookings
                    _latestCompletedBooking.value = userBookings
                        .filter { it.status.equals("COMPLETED", ignoreCase = true) }
                        .maxByOrNull { it.completedAt ?: it.createdAt }
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
    fun signInWithGoogle(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        _isLoading.value = true
        _authError.value = null
        val firebaseAuth = auth
        val firestoreDb = firestore

        if (firebaseAuth == null || firestoreDb == null) {
            _isLoading.value = false
            val msg = "Firebase service is not initialized on device."
            _authError.value = msg
            onError(msg)
            return
        }

        scope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val serverClientId = context.getString(R.string.default_web_client_id)

                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()

                val result = try {
                    credentialManager.getCredential(context = context, request = request)
                } catch (noCredEx: NoCredentialException) {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setAutoSelectEnabled(false)
                        .setServerClientId(serverClientId)
                        .build()
                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()
                    credentialManager.getCredential(context = context, request = fallbackRequest)
                }

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
                            val errMessage = err.localizedMessage ?: "Google Firebase sign-in failed."
                            _authError.value = errMessage
                            Log.w(tag, "FirebaseAuth signInWithCredential failure: $errMessage")
                            onError(errMessage)
                        }
                } else {
                    _isLoading.value = false
                    val msg = "Unexpected credential received from Google Identity provider."
                    _authError.value = msg
                    onError(msg)
                }
            } catch (cancelEx: GetCredentialCancellationException) {
                _isLoading.value = false
                val msg = "Google Sign-In was cancelled. You can sign in with your Name & Email below."
                _authError.value = msg
                Log.i(tag, "Google Sign-In cancelled by user: ${cancelEx.message}")
                onError(msg)
            } catch (noCredEx: NoCredentialException) {
                // On physical devices with an externally built APK (unregistered SHA-1), do NOT silently force Dev Tester.
                // Instead, open the Real Account Sign-In Dialog so the user can enter their real Name & Email.
                _isLoading.value = false
                _showSha1FallbackDialog.value = true
                val msg = "Google OAuth SHA-1 is not registered for this APK build. Please sign in with your Name & Email."
                _authError.value = msg
                Log.w(tag, "NoCredentialException on device (${noCredEx.message}). Prompting user for Name & Email login.")
                onError(msg)
            } catch (e: Exception) {
                _isLoading.value = false
                _showSha1FallbackDialog.value = true
                val msg = "Google Sign-In unavailable on this build. Please sign in with your Name & Email."
                _authError.value = msg
                Log.w(tag, "CredentialManager exception (${e.message}). Prompting user for Name & Email login.")
                onError(msg)
            }
        }
    }

    /**
     * Generates a random 4-digit OTP code (1000..9999).
     */
    fun generateFourDigitOtp(): String {
        return String.format(java.util.Locale.US, "%04d", Random.nextInt(1000, 9999))
    }

    /**
     * Step 1 of 2-Step Phone OTP Verification:
     * a) Validates that `phone` is a valid 10-digit Indian mobile number (`^[6-9][0-9]{9}$`).
     * b) Queries Firestore `users` collection to ensure the number isn't already taken by another account.
     * c) Generates a random 4-digit OTP code and returns `(tenDigits, otpCode)` to trigger the Top SMS Banner & 4-Box OTP Modal.
     */
    fun requestPhoneVerificationOtp(
        phone: String,
        excludeUserId: String? = _currentUserProfile.value?.id,
        checkUniqueness: Boolean = true,
        onOtpGenerated: (tenDigits: String, otpCode: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val phoneErr = SecurityValidator.validateIndianMobileError(phone)
        if (phoneErr != null) {
            _authError.value = phoneErr
            onError(phoneErr)
            return
        }
        val tenDigits = SecurityValidator.extractTenDigitMobile(phone)
        val firestoreDb = firestore
        if (!checkUniqueness || firestoreDb == null) {
            val otp = generateFourDigitOtp()
            _authError.value = null
            onOtpGenerated(tenDigits, otp)
            return
        }

        _isLoading.value = true
        _authError.value = null
        val candidatePhones = listOf(
            "+91 $tenDigits",
            tenDigits,
            "+91$tenDigits",
            "+91-$tenDigits"
        )

        firestoreDb.collection("users")
            .whereIn("phone", candidatePhones)
            .get()
            .addOnSuccessListener { snapshot ->
                _isLoading.value = false
                val conflictingDoc = snapshot?.documents?.firstOrNull { doc ->
                    val docUid = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id
                    excludeUserId.isNullOrBlank() || (docUid != excludeUserId && doc.id != excludeUserId)
                }
                if (conflictingDoc != null && conflictingDoc.exists()) {
                    val existingName = conflictingDoc.getString("name") ?: "another user"
                    val msg = "Mobile number +91 $tenDigits is already registered to another account ($existingName). Please log in or use a different number."
                    _authError.value = msg
                    onError(msg)
                } else {
                    val otp = generateFourDigitOtp()
                    _authError.value = null
                    onOtpGenerated(tenDigits, otp)
                }
            }
            .addOnFailureListener {
                _isLoading.value = false
                val otp = generateFourDigitOtp()
                _authError.value = null
                onOtpGenerated(tenDigits, otp)
            }
    }

    /**
     * "Forgot PIN? Verify via OTP" helper on the Login Tab:
     * Finds the existing user by Mobile Number OR Email Address in Firestore `users` collection,
     * and generates a 4-digit OTP code sent to their registered mobile number so they can reset their 4-Digit Login PIN.
     */
    fun requestForgotPinOtp(
        identifier: String,
        onOtpReady: (matchedUid: String, phoneTenDigits: String, otpCode: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmed = identifier.trim()
        if (trimmed.isBlank()) {
            val msg = "Please enter your registered 10-Digit Mobile Number or Email Address first, then tap 'Forgot PIN? Verify via OTP'."
            _authError.value = msg
            onError(msg)
            return
        }
        val firestoreDb = firestore
        if (firestoreDb == null) {
            val msg = "Firestore database is not connected."
            _authError.value = msg
            onError(msg)
            return
        }

        _isLoading.value = true
        _authError.value = null

        val handleFoundDoc: (com.google.firebase.firestore.DocumentSnapshot?) -> Unit = { doc ->
            _isLoading.value = false
            if (doc != null && doc.exists()) {
                val matchedUid = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id
                val rawPhone = doc.getString("phone").orEmpty()
                val extractedTen = SecurityValidator.extractTenDigitMobile(rawPhone)
                    .ifBlank { SecurityValidator.extractTenDigitMobile(trimmed) }
                    .ifBlank { "9876543210" }
                val otp = generateFourDigitOtp()
                onOtpReady(matchedUid, extractedTen, otp)
            } else {
                val msg = "No existing account found for '$trimmed'. Please switch to the Sign Up tab to create an account."
                _authError.value = msg
                onError(msg)
            }
        }

        if (trimmed.contains("@")) {
            val cleanEmail = trimmed.lowercase()
            firestoreDb.collection("users")
                .whereEqualTo("email", cleanEmail)
                .limit(1)
                .get()
                .addOnSuccessListener { snap ->
                    val first = snap?.documents?.firstOrNull()
                    if (first != null && first.exists()) {
                        handleFoundDoc(first)
                    } else {
                        val deterministicUid = "usr_" + cleanEmail.replace(Regex("[^a-z0-9]"), "_").trim('_')
                        firestoreDb.collection("users").document(deterministicUid).get()
                            .addOnSuccessListener { doc -> handleFoundDoc(doc) }
                            .addOnFailureListener { err ->
                                _isLoading.value = false
                                onError("Failed to look up account: ${err.localizedMessage}")
                            }
                    }
                }
                .addOnFailureListener { err ->
                    _isLoading.value = false
                    onError("Failed to look up account: ${err.localizedMessage}")
                }
        } else {
            val phoneError = SecurityValidator.validateIndianMobileError(trimmed)
            if (phoneError != null) {
                _isLoading.value = false
                _authError.value = phoneError
                onError(phoneError)
                return
            }
            val tenDigits = SecurityValidator.extractTenDigitMobile(trimmed)
            val candidatePhones = listOf(
                "+91 $tenDigits",
                tenDigits,
                "+91$tenDigits",
                "+91-$tenDigits"
            )
            firestoreDb.collection("users")
                .whereIn("phone", candidatePhones)
                .limit(1)
                .get()
                .addOnSuccessListener { snap ->
                    val first = snap?.documents?.firstOrNull()
                    if (first != null && first.exists()) {
                        handleFoundDoc(first)
                    } else {
                        val phoneUid = "usr_$tenDigits"
                        firestoreDb.collection("users").document(phoneUid).get()
                            .addOnSuccessListener { doc -> handleFoundDoc(doc) }
                            .addOnFailureListener { err ->
                                _isLoading.value = false
                                onError("Failed to look up account: ${err.localizedMessage}")
                            }
                    }
                }
                .addOnFailureListener { err ->
                    _isLoading.value = false
                    onError("Failed to look up account: ${err.localizedMessage}")
                }
        }
    }

    /**
     * Completes "Forgot Password? Verify via OTP" by saving the new account Password
     * and `isPhoneVerified = true` to Firestore `users/{uid}` and logging the user in.
     */
    fun resetLoginPinAndAuthenticate(
        uid: String,
        newLoginPin: String,
        onSuccess: (UserProfile) -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanPassword = newLoginPin.trim()
        if (cleanPassword.length < 4) {
            val msg = "Please enter a valid password (at least 4 characters)."
            _authError.value = msg
            onError(msg)
            return
        }
        val firestoreDb = firestore
        if (firestoreDb == null) {
            onError("Unable to connect to server. Please check your internet connection.")
            return
        }
        _isLoading.value = true
        _authError.value = null
        val userDocRef = firestoreDb.collection("users").document(uid)
        userDocRef.set(
            mapOf(
                "password" to cleanPassword,
                "loginPin" to cleanPassword,
                "isPhoneVerified" to true
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).addOnSuccessListener {
            userDocRef.get()
                .addOnSuccessListener { updatedDoc ->
                    if (updatedDoc != null && updatedDoc.exists()) {
                        applyExistingUserDocument(updatedDoc, onSuccess)
                    } else {
                        _isLoading.value = false
                        onError("Could not load updated account.")
                    }
                }
                .addOnFailureListener { err ->
                    _isLoading.value = false
                    onError("Failed to reload profile: ${err.localizedMessage}")
                }
        }.addOnFailureListener { err ->
            _isLoading.value = false
            onError("Failed to save new password: ${err.localizedMessage}")
        }
    }

    /**
     * Direct Account Sign-Up / Registration:
     * Requires Full Name, Email, OTP-verified 10-Digit Mobile (`isPhoneVerified = true`), and Password (`loginPin`).
     */
    fun signInWithRealIdentity(
        name: String,
        email: String,
        phone: String = "",
        loginPin: String = "",
        isPhoneVerified: Boolean = true,
        asPartner: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmedName = SecurityValidator.sanitizeName(name, trimEdges = true)
        val trimmedEmail = SecurityValidator.sanitizeInput(email, maxLength = 100, trimEdges = true).lowercase()
        if (trimmedName.length < 2) {
            val msg = "Please enter your full name (at least 2 characters)."
            _authError.value = msg
            onError(msg)
            return
        }
        if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
            val msg = "Please enter a valid email address."
            _authError.value = msg
            onError(msg)
            return
        }

        // Validate 10-digit Indian mobile number strictly
        val phoneErr = SecurityValidator.validateIndianMobileError(phone)
        if (phoneErr != null) {
            _authError.value = phoneErr
            onError(phoneErr)
            return
        }
        val tenDigits = SecurityValidator.extractTenDigitMobile(phone)
        val formattedPhone = "+91 $tenDigits"

        // Validate Password (at least 4 characters)
        val cleanPassword = loginPin.trim()
        if (cleanPassword.length < 4) {
            val msg = "Please create a password (at least 4 characters) to protect your account."
            _authError.value = msg
            onError(msg)
            return
        }

        val sanitizedEmail = trimmedEmail.replace(Regex("[^a-z0-9]"), "_").trim('_')
        val uid = if (tenDigits.length == 10 && sanitizedEmail.isNotBlank()) {
            "usr_${tenDigits}_$sanitizedEmail"
        } else if (tenDigits.length == 10) {
            "usr_$tenDigits"
        } else {
            "usr_$sanitizedEmail"
        }
        _isLoading.value = true
        _authError.value = null
        _showSha1FallbackDialog.value = false

        val firestoreDb = firestore
        if (firestoreDb == null) {
            _isLoading.value = false
            val msg = "Unable to connect to server. Please check your internet connection."
            _authError.value = msg
            onError(msg)
            return
        }

        val candidatePhones = listOf(
            "+91 $tenDigits",
            tenDigits,
            "+91$tenDigits",
            "+91-$tenDigits"
        )

        // Ensure phone number isn't already taken by a different user account
        firestoreDb.collection("users")
            .whereIn("phone", candidatePhones)
            .get()
            .addOnSuccessListener { phoneSnap ->
                val conflictingDoc = phoneSnap?.documents?.firstOrNull { doc ->
                    val docUid = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id
                    val docEmail = doc.getString("email")?.lowercase().orEmpty()
                    docUid != uid && doc.id != uid && docEmail != trimmedEmail
                }
                if (conflictingDoc != null && conflictingDoc.exists()) {
                    _isLoading.value = false
                    val msg = "Mobile number +91 $tenDigits is already registered to another account. Please use Login or a different number."
                    _authError.value = msg
                    onError(msg)
                    return@addOnSuccessListener
                }

                val userDocRef = firestoreDb.collection("users").document(uid)
                userDocRef.get()
                    .addOnSuccessListener { existingDoc ->
                        if (existingDoc != null && existingDoc.exists()) {
                            val existingPhone = existingDoc.getString("phone") ?: ""
                            val resolvedPhone = formattedPhone.ifBlank { existingPhone }
                            val existingStrikes = maxOf(
                                existingDoc.getLong("cancellationStrikes")?.toInt() ?: 0,
                                existingDoc.getLong("cancellationCount")?.toInt() ?: 0
                            )
                            val existingIsSuspended = existingDoc.getBoolean("isSuspended") ?: (existingStrikes >= 3)
                            val verifiedSalon = _salons.value.find {
                                it.id !in seedSalonIds &&
                                    (it.ownerId == uid || it.partnerId == uid) &&
                                    it.isRegistrationCompleted &&
                                    it.registrationFormSubmitted
                            }
                            val existingSalonId = verifiedSalon?.id
                            val existingHasSalon = verifiedSalon != null
                            val existingCity = SecurityValidator.sanitizeAddress(
                                existingDoc.getString("defaultCity")?.takeIf { it.isNotBlank() } ?: _appPreferences.value.defaultCity
                            )
                            val existingCreatedAt = existingDoc.getLong("createdAt") ?: System.currentTimeMillis()
                            val existingQuickMpin = existingDoc.getString("quickAccessMpin") ?: ""
                            val existingBiometric = existingDoc.getBoolean("biometricEnabled") ?: false
                            val existingQuickEnabled = existingDoc.getBoolean("isQuickAccessEnabled") ?: false

                            // Every user MUST default to Customer Mode upon Login/Signup
                            val userProfile = UserProfile(
                                id = uid,
                                name = trimmedName,
                                email = trimmedEmail,
                                phone = resolvedPhone,
                                password = cleanPassword,
                                loginPin = cleanPassword,
                                quickAccessMpin = existingQuickMpin,
                                biometricEnabled = existingBiometric,
                                isQuickAccessEnabled = existingQuickEnabled,
                                isPhoneVerified = isPhoneVerified,
                                role = "customer",
                                salonId = existingSalonId,
                                hasRegisteredSalon = existingHasSalon,
                                defaultCity = existingCity,
                                cancellationCount = existingStrikes,
                                cancellationStrikes = existingStrikes,
                                isSuspended = existingIsSuspended,
                                createdAt = existingCreatedAt
                            )

                            userDocRef.set(userProfile.toMap(), com.google.firebase.firestore.SetOptions.merge())
                            sessionPrefs.edit()
                                .putString("saved_uid", uid)
                                .putString("saved_name", trimmedName)
                                .putString("saved_email", trimmedEmail)
                                .putString("saved_role", "customer")
                                .apply()

                            _currentUserProfile.value = userProfile
                            attachCustomerBookingsListener(uid)
                            _isLoading.value = false
                            _isSessionUnlocked.value = true
                            if (!userProfile.hasQuickAccessSetup) {
                                _showQuickAccessSetup.value = true
                            }
                            _currentRole.value = UserRole.CUSTOMER
                            _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                            if (asPartner && !existingHasSalon) {
                                _showPartnerOnboarding.value = true
                            }
                            onSuccess()
                        } else {
                            val newProfile = UserProfile(
                                id = uid,
                                name = trimmedName,
                                email = trimmedEmail,
                                phone = formattedPhone,
                                password = cleanPassword,
                                loginPin = cleanPassword,
                                isPhoneVerified = isPhoneVerified,
                                role = "customer",
                                salonId = null,
                                hasRegisteredSalon = false,
                                defaultCity = SecurityValidator.sanitizeAddress(_appPreferences.value.defaultCity),
                                cancellationCount = 0,
                                cancellationStrikes = 0,
                                isSuspended = false,
                                createdAt = System.currentTimeMillis()
                            )

                            userDocRef.set(newProfile.toMap())
                                .addOnSuccessListener {
                                    sessionPrefs.edit()
                                        .putString("saved_uid", uid)
                                        .putString("saved_name", trimmedName)
                                        .putString("saved_email", trimmedEmail)
                                        .putString("saved_role", "customer")
                                        .apply()

                                    _currentUserProfile.value = newProfile
                                    attachCustomerBookingsListener(uid)
                                    _currentRole.value = UserRole.CUSTOMER
                                    _isSessionUnlocked.value = true
                                    _showQuickAccessSetup.value = true
                                    _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                                    _isLoading.value = false
                                    if (asPartner) {
                                        _showPartnerOnboarding.value = true
                                    }
                                    onSuccess()
                                }
                                .addOnFailureListener { setErr ->
                                    _isLoading.value = false
                                    val errMsg = "Failed to create account: ${setErr.localizedMessage}"
                                    _authError.value = errMsg
                                    onError(errMsg)
                                }
                        }
                    }
                    .addOnFailureListener { getErr ->
                        _isLoading.value = false
                        val errMsg = "Connection error: ${getErr.localizedMessage}"
                        _authError.value = errMsg
                        onError(errMsg)
                    }
            }
            .addOnFailureListener { err ->
                _isLoading.value = false
                val errMsg = "Failed to verify mobile number availability: ${err.localizedMessage}"
                _authError.value = errMsg
                onError(errMsg)
            }
    }

    /**
     * Secure Login for Existing Users (Mobile Number OR Email Address + Password):
     * Searches `users` collection for an existing account matching the phone or email,
     * and ONLY logs the user in if BOTH the Mobile/Email AND the Password match their user document.
     */
    fun loginExistingUser(
        identifier: String,
        loginPin: String = "",
        onSuccess: (UserProfile) -> Unit,
        onAccountNotFound: (prefillEmail: String, prefillPhone: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmed = identifier.trim()
        if (trimmed.isBlank()) {
            val msg = "Please enter your registered 10-Digit Mobile Number or Email Address."
            _authError.value = msg
            onError(msg)
            return
        }

        val cleanPassword = loginPin.trim()
        if (cleanPassword.length < 4) {
            val msg = "Please enter your account Password (or tap 'Forgot Password? Verify via OTP')."
            _authError.value = msg
            onError(msg)
            return
        }

        val firestoreDb = firestore
        if (firestoreDb == null) {
            _isLoading.value = false
            val msg = "Unable to connect to server. Please check your internet connection."
            _authError.value = msg
            onError(msg)
            return
        }

        _isLoading.value = true
        _authError.value = null
        _showSha1FallbackDialog.value = false

        val verifyPinAndApplyDoc: (com.google.firebase.firestore.DocumentSnapshot) -> Unit = { matchedDoc ->
            val savedPassword = matchedDoc.getString("password")?.trim()?.takeIf { it.isNotBlank() }
                ?: matchedDoc.getString("loginPin")?.trim().orEmpty()
            if (savedPassword.isBlank()) {
                _isLoading.value = false
                val msg = "No Password is set on this account yet. Tap 'Forgot Password? Verify via OTP' below to verify your number and set your Password."
                _authError.value = msg
                onError(msg)
            } else if (savedPassword != cleanPassword) {
                _isLoading.value = false
                val msg = "Incorrect Password! Please enter your exact Password or tap 'Forgot Password? Verify via OTP'."
                _authError.value = msg
                onError(msg)
            } else {
                applyExistingUserDocument(matchedDoc, onSuccess)
            }
        }

        if (trimmed.contains("@")) {
            val cleanEmail = trimmed.lowercase()
            if (!cleanEmail.contains(".")) {
                _isLoading.value = false
                val msg = "Please enter a valid email address."
                _authError.value = msg
                onError(msg)
                return
            }

            // Query by 'email' field first so any usr_<phone>_<email> or usr_<email> document matches
            firestoreDb.collection("users")
                .whereEqualTo("email", cleanEmail)
                .limit(1)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val matchedDoc = querySnapshot?.documents?.firstOrNull()
                    if (matchedDoc != null && matchedDoc.exists()) {
                        verifyPinAndApplyDoc(matchedDoc)
                    } else {
                        val deterministicUid = "usr_" + cleanEmail.replace(Regex("[^a-z0-9]"), "_").trim('_')
                        firestoreDb.collection("users").document(deterministicUid).get()
                            .addOnSuccessListener { doc ->
                                if (doc != null && doc.exists()) {
                                    verifyPinAndApplyDoc(doc)
                                } else {
                                    _isLoading.value = false
                                    val notFoundMsg = "Account not found. Switching to Sign Up..."
                                    _authError.value = notFoundMsg
                                    onAccountNotFound(cleanEmail, "")
                                }
                            }
                            .addOnFailureListener { err ->
                                _isLoading.value = false
                                val errMsg = "Failed to look up email: ${err.localizedMessage}"
                                _authError.value = errMsg
                                onError(errMsg)
                            }
                    }
                }
                .addOnFailureListener { err ->
                    _isLoading.value = false
                    val errMsg = "Connection error: ${err.localizedMessage}"
                    _authError.value = errMsg
                    onError(errMsg)
                }
        } else {
            val phoneError = SecurityValidator.validateIndianMobileError(trimmed)
            if (phoneError != null) {
                _isLoading.value = false
                _authError.value = phoneError
                onError(phoneError)
                return
            }
            val tenDigits = SecurityValidator.extractTenDigitMobile(trimmed)

            val candidatePhones = listOf(
                "+91 $tenDigits",
                tenDigits,
                "+91$tenDigits",
                "+91-$tenDigits"
            )

            firestoreDb.collection("users")
                .whereIn("phone", candidatePhones)
                .limit(1)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val matchedDoc = querySnapshot?.documents?.firstOrNull()
                    if (matchedDoc != null && matchedDoc.exists()) {
                        verifyPinAndApplyDoc(matchedDoc)
                    } else {
                        val phoneUid = "usr_$tenDigits"
                        firestoreDb.collection("users").document(phoneUid).get()
                            .addOnSuccessListener { doc ->
                                if (doc != null && doc.exists()) {
                                    verifyPinAndApplyDoc(doc)
                                } else {
                                    _isLoading.value = false
                                    val notFoundMsg = "Account not found. Switching to Sign Up..."
                                    _authError.value = notFoundMsg
                                    onAccountNotFound("", tenDigits)
                                }
                            }
                            .addOnFailureListener { err ->
                                _isLoading.value = false
                                val errMsg = "Failed to look up mobile number: ${err.localizedMessage}"
                                _authError.value = errMsg
                                onError(errMsg)
                            }
                    }
                }
                .addOnFailureListener { err ->
                    _isLoading.value = false
                    val errMsg = "Failed to look up mobile number: ${err.localizedMessage}"
                    _authError.value = errMsg
                    onError(errMsg)
                }
        }
    }

    private fun applyExistingUserDocument(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        onSuccess: (UserProfile) -> Unit
    ) {
        val existingEmail = doc.getString("email") ?: ""
        val existingPhone = doc.getString("phone") ?: ""
        val existingPassword = doc.getString("password")?.takeIf { it.isNotBlank() }
            ?: doc.getString("loginPin") ?: ""
        val existingQuickMpin = doc.getString("quickAccessMpin")
            ?: sessionPrefs.getString("saved_quick_mpin", "") ?: ""
        val existingBiometric = doc.getBoolean("biometricEnabled")
            ?: sessionPrefs.getBoolean("saved_biometric_enabled", false)
        val existingQuickEnabled = doc.getBoolean("isQuickAccessEnabled")
            ?: sessionPrefs.getBoolean("saved_quick_access_enabled", false)
        val existingIsPhoneVerified = doc.getBoolean("isPhoneVerified") ?: existingPhone.isNotBlank()
        val rawDocId = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id
        val uid = if (rawDocId.isNotBlank() && rawDocId !in invalidSharedUserIds) {
            rawDocId
        } else {
            val fallbackSeed = existingEmail.ifBlank { existingPhone }.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
            "usr_${fallbackSeed.ifBlank { System.currentTimeMillis().toString() }}"
        }
        val existingName = SecurityValidator.sanitizeName(
            doc.getString("name")?.takeIf { it.isNotBlank() }
                ?: doc.getString("displayName")?.takeIf { it.isNotBlank() }
                ?: "Customer"
        )
        val verifiedSalon = _salons.value.find {
            it.id !in seedSalonIds &&
                (it.ownerId == uid || it.partnerId == uid) &&
                it.isRegistrationCompleted &&
                it.registrationFormSubmitted
        }
        val existingSalonId = verifiedSalon?.id
        val existingHasSalon = verifiedSalon != null
        val existingCity = SecurityValidator.sanitizeAddress(
            doc.getString("defaultCity")?.takeIf { it.isNotBlank() } ?: _appPreferences.value.defaultCity
        )
        val existingStrikes = maxOf(
            doc.getLong("cancellationStrikes")?.toInt() ?: 0,
            doc.getLong("cancellationCount")?.toInt() ?: 0
        )
        val existingIsSuspended = doc.getBoolean("isSuspended") ?: (existingStrikes >= 3)
        val existingCreatedAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

        // Every user MUST default to Customer Mode upon Login
        val userProfile = UserProfile(
            id = uid,
            name = existingName,
            email = existingEmail,
            phone = existingPhone,
            password = existingPassword,
            loginPin = existingPassword,
            quickAccessMpin = existingQuickMpin,
            biometricEnabled = existingBiometric,
            isQuickAccessEnabled = existingQuickEnabled,
            isPhoneVerified = existingIsPhoneVerified,
            role = "customer",
            salonId = existingSalonId,
            hasRegisteredSalon = existingHasSalon,
            defaultCity = existingCity,
            cancellationCount = existingStrikes,
            cancellationStrikes = existingStrikes,
            isSuspended = existingIsSuspended,
            createdAt = existingCreatedAt
        )

        sessionPrefs.edit()
            .putString("saved_uid", uid)
            .putString("saved_name", existingName)
            .putString("saved_email", existingEmail)
            .putString("saved_role", "customer")
            .putString("saved_quick_mpin", existingQuickMpin)
            .putBoolean("saved_biometric_enabled", existingBiometric)
            .putBoolean("saved_quick_access_enabled", existingQuickEnabled)
            .apply()

        _currentUserProfile.value = userProfile
        attachCustomerBookingsListener(uid)
        _isLoading.value = false
        _showPartnerOnboarding.value = false
        _isSessionUnlocked.value = true
        if (!userProfile.hasQuickAccessSetup) {
            _showQuickAccessSetup.value = true
        }
        _currentRole.value = UserRole.CUSTOMER
        _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
        onSuccess(userProfile)
    }

    /**
     * Developer Test Login:
     * Generates a device-specific unique test UID so two devices never share a hardcoded UID.
     */
    fun developerTestLogin(
        testUid: String = "usr_device_${System.currentTimeMillis().toString().takeLast(6)}",
        testDisplayName: String = "Customer",
        testEmail: String = "user_${System.currentTimeMillis().toString().takeLast(5)}@trimtime.app",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        _isLoading.value = true
        _authError.value = null
        Log.d(tag, "Initiating Login for uid: $testUid")
        saveGoogleUserAndAuthenticate(
            uid = testUid,
            displayName = testDisplayName,
            email = testEmail,
            onSuccess = {
                _isLoading.value = false
                onSuccess()
            },
            onError = { err ->
                _isLoading.value = false
                _authError.value = err
                onError(err)
            }
        )
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
            _isLoading.value = false
            val msg = "Unable to connect to server. Please check your internet connection."
            _authError.value = msg
            onError(msg)
            return
        }

        val userDocRef = firestoreDb.collection("users").document(uid)
        userDocRef.get()
            .addOnSuccessListener { existingDoc ->
                if (existingDoc != null && existingDoc.exists()) {
                    // Existing User: Do NOT overwrite. Read real values from Firestore.
                    val existingName = existingDoc.getString("name") ?: displayName
                    val existingEmail = existingDoc.getString("email") ?: email
                    val existingPhone = existingDoc.getString("phone") ?: ""
                    val existingPassword = existingDoc.getString("password")?.takeIf { it.isNotBlank() }
                        ?: existingDoc.getString("loginPin") ?: ""
                    val existingQuickMpin = existingDoc.getString("quickAccessMpin") ?: ""
                    val existingBiometric = existingDoc.getBoolean("biometricEnabled") ?: false
                    val existingQuickEnabled = existingDoc.getBoolean("isQuickAccessEnabled") ?: false
                    val existingIsPhoneVerified = existingDoc.getBoolean("isPhoneVerified") ?: existingPhone.isNotBlank()
                    val existingCancellationCount = existingDoc.getLong("cancellationCount")?.toInt() ?: 0
                    val existingIsSuspended = existingDoc.getBoolean("isSuspended") ?: (existingCancellationCount >= 3)
                    val verifiedSalon = _salons.value.find {
                        it.id !in seedSalonIds &&
                            (it.ownerId == uid || it.partnerId == uid) &&
                            it.isRegistrationCompleted &&
                            it.registrationFormSubmitted
                    }
                    val existingSalonId = verifiedSalon?.id
                    val existingHasSalon = verifiedSalon != null
                    val existingCity = existingDoc.getString("defaultCity")?.takeIf { it.isNotBlank() } ?: _appPreferences.value.defaultCity
                    val existingCreatedAt = existingDoc.getLong("createdAt") ?: System.currentTimeMillis()

                    val userProfile = UserProfile(
                        id = uid,
                        name = existingName,
                        email = existingEmail,
                        phone = existingPhone,
                        password = existingPassword,
                        loginPin = existingPassword,
                        quickAccessMpin = existingQuickMpin,
                        biometricEnabled = existingBiometric,
                        isQuickAccessEnabled = existingQuickEnabled,
                        isPhoneVerified = existingIsPhoneVerified,
                        role = "customer",
                        salonId = existingSalonId,
                        hasRegisteredSalon = existingHasSalon,
                        defaultCity = existingCity,
                        cancellationCount = existingCancellationCount,
                        isSuspended = existingIsSuspended,
                        createdAt = existingCreatedAt
                    )

                    sessionPrefs.edit()
                        .putString("saved_uid", uid)
                        .putString("saved_name", existingName)
                        .putString("saved_email", existingEmail)
                        .putString("saved_role", "customer")
                        .putString("saved_quick_mpin", existingQuickMpin)
                        .putBoolean("saved_biometric_enabled", existingBiometric)
                        .putBoolean("saved_quick_access_enabled", existingQuickEnabled)
                        .apply()
                    _currentUserProfile.value = userProfile
                    attachCustomerBookingsListener(uid)
                    _isLoading.value = false
                    _showPartnerOnboarding.value = false
                    _isSessionUnlocked.value = true
                    if (!userProfile.hasQuickAccessSetup) {
                        _showQuickAccessSetup.value = true
                    }
                    _currentRole.value = UserRole.CUSTOMER
                    _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                    onSuccess()
                } else {
                    // New User: Create document in Firestore
                    val newProfile = UserProfile(
                        id = uid,
                        name = displayName.ifBlank { "Customer" },
                        email = email,
                        phone = "",
                        password = "",
                        loginPin = "",
                        isPhoneVerified = false,
                        role = "customer",
                        salonId = null,
                        hasRegisteredSalon = false,
                        defaultCity = _appPreferences.value.defaultCity,
                        cancellationCount = 0,
                        isSuspended = false,
                        createdAt = System.currentTimeMillis()
                    )

                    userDocRef.set(newProfile.toMap())
                        .addOnSuccessListener {
                            _currentUserProfile.value = newProfile
                            attachCustomerBookingsListener(uid)
                            _currentRole.value = UserRole.CUSTOMER
                            _isSessionUnlocked.value = true
                            _showQuickAccessSetup.value = true
                            _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                            _isLoading.value = false
                            onSuccess()
                        }
                        .addOnFailureListener { setErr ->
                            _isLoading.value = false
                            val errMsg = "Failed to save account: ${setErr.localizedMessage}"
                            _authError.value = errMsg
                            Log.e(tag, errMsg, setErr)
                            onError(errMsg)
                        }
                }
            }
            .addOnFailureListener { getErr ->
                _isLoading.value = false
                val errMsg = "Failed to verify account: ${getErr.localizedMessage}"
                _authError.value = errMsg
                Log.e(tag, errMsg, getErr)
                onError(errMsg)
            }
    }

    /**
     * Saves user's OTP-verified 10-digit contact number directly to Firestore profile with `isPhoneVerified = true`.
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

        val phoneErr = SecurityValidator.validateIndianMobileError(phone)
        if (phoneErr != null) {
            _authError.value = phoneErr
            onError(phoneErr)
            return
        }
        val tenDigits = SecurityValidator.extractTenDigitMobile(phone)

        val formattedPhone = "+91 $tenDigits"
        val updatedProfile = current.copy(phone = formattedPhone, isPhoneVerified = isVerified)
        _currentUserProfile.value = updatedProfile
        _authError.value = null

        val userDocRef = firestore?.collection("users")?.document(current.id)
        if (userDocRef != null) {
            userDocRef.set(
                mapOf(
                    "phone" to formattedPhone,
                    "isPhoneVerified" to isVerified
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
                .addOnSuccessListener {
                    Log.d(tag, "Verified phone number successfully saved to Firestore.")
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
        customerBookingsListener?.remove()
        customerBookingsListener = null
        sessionPrefs.edit().clear().apply()
        _showSha1FallbackDialog.value = false
        _showPartnerOnboarding.value = false
        _showQuickAccessSetup.value = false
        _isSessionUnlocked.value = true
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
            rating = 4.9,
            reviewCount = 1,
            partnerId = uid,
            ownerId = uid,
            ownerName = cleanOwnerName,
            phone = formattedPhone,
            imageUrl = "",
            photoBase64 = "",
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
                    val suspended = doc.getBoolean("isSuspended") ?: (cancelCount >= 3)
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

                    val existingPhoneVal = doc.getString("phone") ?: user?.phoneNumber ?: ""
                    val existingLoginPinVal = doc.getString("loginPin") ?: ""
                    val existingPhoneVerifiedVal = doc.getBoolean("isPhoneVerified") ?: existingPhoneVal.isNotBlank()

                    // Every user MUST default to Customer Mode upon Login / Session Restore
                    val profile = UserProfile(
                        id = uid,
                        name = SecurityValidator.sanitizeName(doc.getString("name") ?: user?.displayName ?: "Customer"),
                        email = doc.getString("email") ?: user?.email ?: "",
                        phone = existingPhoneVal,
                        loginPin = existingLoginPinVal,
                        isPhoneVerified = existingPhoneVerifiedVal,
                        role = "customer",
                        salonId = salonIdVal,
                        hasRegisteredSalon = hasRegisteredVal,
                        defaultCity = defaultCityVal,
                        cancellationCount = cancelCount,
                        cancellationStrikes = cancelCount,
                        isSuspended = suspended,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    )
                    sessionPrefs.edit().putString("saved_role", "customer").apply()
                    _currentUserProfile.value = profile
                    attachCustomerBookingsListener(uid)
                    _showPartnerOnboarding.value = false
                    _currentRole.value = UserRole.CUSTOMER
                    _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                } else {
                    // Document doesn't exist yet: write to Firestore
                    val newProfile = UserProfile(
                        id = uid,
                        name = user?.displayName ?: "Customer",
                        email = user?.email ?: "",
                        phone = user?.phoneNumber ?: "",
                        role = "customer",
                        salonId = null,
                        hasRegisteredSalon = false,
                        defaultCity = _appPreferences.value.defaultCity,
                        cancellationCount = 0,
                        isSuspended = false,
                        createdAt = System.currentTimeMillis()
                    )
                    firestoreDb.collection("users").document(uid).set(newProfile.toMap())
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
        bookingDate: String
    ): Booking {
        val currentProfile = _currentUserProfile.value
        val resolvedCustomerId = currentProfile?.id?.takeIf { it.isNotBlank() && it !in invalidSharedUserIds }
            ?: customerId.trim()
        if (resolvedCustomerId.isBlank() || resolvedCustomerId in invalidSharedUserIds) {
            throw IllegalStateException("Please log in with your Mobile Number or Email before booking.")
        }

        // Rule 3: Strictly enforce the 3-Strike Anti-Spam Rule (cancellationStrikes >= 3)
        if (currentProfile != null && (currentProfile.effectiveCancellationStrikes >= 3 || currentProfile.isSuspended)) {
            throw IllegalStateException("Security Lockout: Your account is suspended due to 3 or more booking cancellations.")
        }

        // Rule 4: Prevent Self-Booking (Owner Booking Their Own Salon)
        val targetSalon = _salons.value.find { it.id == salonId }
        if (targetSalon != null &&
            resolvedCustomerId.isNotBlank() &&
            (targetSalon.ownerId == resolvedCustomerId || targetSalon.partnerId == resolvedCustomerId)
        ) {
            throw IllegalStateException("This is your own registered salon. Manage it in Partner Mode.")
        }

        // Rule 2: Strictly block Customer if they already have an active booking (status in ["PENDING", "ACCEPTED", "IN_PROGRESS"])
        val hasActiveBooking = (_customerBookings.value + _bookings.value).any { b ->
            b.customerId == resolvedCustomerId && SecurityValidator.isBookingActiveStatus(b.status)
        }
        if (hasActiveBooking) {
            throw IllegalStateException("You already have an active booking (Pending/Accepted/In-Progress). Please complete or cancel it first.")
        }

        // Validate customer phone & sanitize customer name
        val rawName = customerName.ifBlank { currentProfile?.name ?: "Customer" }
        val resolvedCustomerName = SecurityValidator.sanitizeName(rawName, trimEdges = true)
        if (resolvedCustomerName.length < 2) {
            throw IllegalArgumentException("Please enter a valid customer name (2–60 characters).")
        }

        val rawPhone = customerPhone.ifBlank { currentProfile?.phone ?: "" }
        val phoneErr = SecurityValidator.validateIndianMobileError(rawPhone)
        if (phoneErr != null) {
            throw IllegalArgumentException(phoneErr)
        }
        val resolvedCustomerPhone = SecurityValidator.formatIndianMobile(rawPhone)

        if (!SecurityValidator.isValidServicePrice(servicePrice)) {
            throw IllegalArgumentException("Service price must be between ₹10 and ₹25,000.")
        }

        val generatedPin = String.format("%04d", Random.nextInt(1000, 9999))
        val newBookingId = "bk_" + System.currentTimeMillis().toString().takeLast(6) + "_" + Random.nextInt(10, 99)

        val newBooking = Booking(
            id = newBookingId,
            customerId = resolvedCustomerId,
            customerName = resolvedCustomerName,
            customerPhone = resolvedCustomerPhone,
            salonId = salonId.trim(),
            salonName = SecurityValidator.sanitizeName(salonName, trimEdges = true),
            service = SecurityValidator.sanitizeName(service, trimEdges = true),
            servicePrice = servicePrice.coerceIn(SecurityValidator.MIN_SERVICE_PRICE, SecurityValidator.MAX_SERVICE_PRICE),
            timeSlot = SecurityValidator.sanitizeName(timeSlot, trimEdges = true),
            bookingDate = SecurityValidator.sanitizeName(bookingDate, trimEdges = true),
            status = BookingStatus.PENDING.name.lowercase(),
            pin = generatedPin,
            createdAt = System.currentTimeMillis()
        )

        // Add to local states immediately (strictly isolated for _customerBookings)
        _bookings.value = listOf(newBooking) + _bookings.value
        _customerBookings.value = listOf(newBooking) + _customerBookings.value.filter { it.customerId == resolvedCustomerId }

        // Write directly to Firestore 'bookings' collection
        firestore?.let { db ->
            try {
                db.collection("bookings").document(newBooking.id).set(newBooking.toMap())
                    .addOnSuccessListener {
                        Log.d(tag, "Live booking synced to Firestore: ${newBooking.id} for customerId: $resolvedCustomerId")
                    }
                    .addOnFailureListener { e ->
                        Log.e(tag, "Firestore booking error: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(tag, "Exception writing booking: ${e.message}")
            }
        }

        return newBooking
    }

    /**
     * Partner Action: Accept an incoming customer booking in real time in Firestore.
     */
    fun acceptBooking(bookingId: String): Boolean {
        val currentBookings = _bookings.value
        val booking = currentBookings.find { it.id == bookingId } ?: return false
        val acceptedBooking = booking.copy(status = BookingStatus.ACCEPTED.name.lowercase())
        _bookings.value = currentBookings.map { if (it.id == bookingId) acceptedBooking else it }
        _customerBookings.value = _customerBookings.value.map { if (it.id == bookingId) acceptedBooking else it }

        firestore?.collection("bookings")?.document(bookingId)?.update("status", "accepted")
            ?.addOnSuccessListener {
                Log.d(tag, "Booking $bookingId accepted in Firestore.")
            }
            ?.addOnFailureListener { e ->
                Log.w(tag, "Failed to accept booking in Firestore: ${e.message}")
            }
        return true
    }

    /**
     * Partner Action: Reject an incoming customer booking in real time in Firestore
     * (Does NOT add a cancellation strike to the customer since the partner rejected it).
     */
    fun rejectBooking(bookingId: String): Boolean {
        val currentBookings = _bookings.value
        val booking = currentBookings.find { it.id == bookingId } ?: return false
        val rejectedBooking = booking.copy(status = BookingStatus.REJECTED.name.lowercase())
        _bookings.value = currentBookings.map { if (it.id == bookingId) rejectedBooking else it }
        _customerBookings.value = _customerBookings.value.map { if (it.id == bookingId) rejectedBooking else it }

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

        // Increment cancellationStrikes & cancellationCount on user profile
        val currentProfile = _currentUserProfile.value
        if (currentProfile != null) {
            val newStrikes = currentProfile.effectiveCancellationStrikes + 1
            val isNowSuspended = newStrikes >= 3
            val updatedProfile = currentProfile.copy(
                cancellationCount = newStrikes,
                cancellationStrikes = newStrikes,
                isSuspended = isNowSuspended
            )
            _currentUserProfile.value = updatedProfile

            firestore?.collection("users")?.document(currentProfile.id)?.set(
                mapOf(
                    "cancellationCount" to newStrikes,
                    "cancellationStrikes" to newStrikes,
                    "isSuspended" to isNowSuspended
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )?.addOnSuccessListener {
                Log.d(tag, "Incremented cancellationStrikes to $newStrikes for user ${currentProfile.id}. Suspended: $isNowSuspended")
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
        val currentSalons = _salons.value
        val updated = currentSalons.map {
            if (it.id == salonId) it.copy(isOpen = !it.isOpen) else it
        }
        _salons.value = updated
        val updatedSalon = updated.find { it.id == salonId }
        if (updatedSalon != null) {
            firestore?.collection("salons")?.document(salonId)?.set(
                mapOf("isOpen" to updatedSalon.isOpen) + resolveSalonOwnerMap(salonId),
                com.google.firebase.firestore.SetOptions.merge()
            )
        }
    }

    fun setSalonOpenStatus(salonId: String, isOpen: Boolean) {
        val currentSalons = _salons.value
        val updated = currentSalons.map {
            if (it.id == salonId) it.copy(isOpen = isOpen) else it
        }
        _salons.value = updated
        firestore?.collection("salons")?.document(salonId)?.set(
            mapOf("isOpen" to isOpen) + resolveSalonOwnerMap(salonId),
            com.google.firebase.firestore.SetOptions.merge()
        )
            ?.addOnSuccessListener {
                Log.d(tag, "Salon $salonId isOpen updated to $isOpen in Firestore.")
            }
            ?.addOnFailureListener { err ->
                Log.e(tag, "Failed to update isOpen: ${err.message}")
            }
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
            srv.copy(
                name = SecurityValidator.sanitizeName(srv.name, trimEdges = true).ifBlank { "Grooming Service" },
                category = SecurityValidator.sanitizeName(srv.category, trimEdges = true).ifBlank { "Haircut" },
                price = srv.price.coerceIn(SecurityValidator.MIN_SERVICE_PRICE, SecurityValidator.MAX_SERVICE_PRICE),
                durationMinutes = srv.durationMinutes.coerceIn(SecurityValidator.MIN_SERVICE_DURATION, SecurityValidator.MAX_SERVICE_DURATION),
                description = SecurityValidator.sanitizeTagline(srv.description, trimEdges = true)
            )
        }

        val currentSalons = _salons.value
        val updated = currentSalons.map {
            if (it.id == salonId) it.copy(services = sanitizedServices) else it
        }
        _salons.value = updated

        val servicesMap = sanitizedServices.map { it.toMap() }
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

        val updates = mapOf(
            "imageUrl" to photoDataUrl,
            "photoBase64" to photoDataUrl
        ) + resolveSalonOwnerMap(salonId)
        firestore?.collection("salons")?.document(salonId)
            ?.set(updates, com.google.firebase.firestore.SetOptions.merge())
            ?.addOnSuccessListener {
                Log.d(tag, "Salon photo Base64 synced to Firestore for $salonId")
                onSuccess()
            }
            ?.addOnFailureListener { e ->
                Log.w(tag, "Failed to save salon photo in Firestore: ${e.message}")
                onError(e.message ?: "Failed to save photo")
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
            userRole = currentProfile?.role?.ifBlank { activeRole } ?: activeRole,
            rating = rating.coerceIn(1, 5),
            category = SecurityValidator.sanitizeName(category.ifBlank { "General Feedback" }),
            message = sanitizedMessage,
            timestamp = System.currentTimeMillis()
        )

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

    companion object {
        @Volatile
        private var INSTANCE: TrimTimeRepository? = null

        fun getInstance(context: Context): TrimTimeRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TrimTimeRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun getProductionSeedSalons(): List<Salon> = emptyList()

        fun calculateDistanceKm(
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
            val dist = r * c
            return Math.max(0.1, Math.round(dist * 10.0) / 10.0)
        }
    }
}
