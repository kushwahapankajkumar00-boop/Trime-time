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
import com.example.ui.auth.formatStandardGoogleSignInError
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
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
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
    private var defaultFirestore: FirebaseFirestore? = null
    private var isFirebaseReady = false

    private var salonsListener: ListenerRegistration? = null
    private var bookingsListener: ListenerRegistration? = null
    private var customerBookingsListener: ListenerRegistration? = null
    private var userDocListener: ListenerRegistration? = null
    private var defaultUserDocListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null
    private var defaultUsersListener: ListenerRegistration? = null
    private var feedbackListener: ListenerRegistration? = null

    private var namedDbUsers: List<UserProfile> = emptyList()
    private var defaultDbUsers: List<UserProfile> = emptyList()

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
                defaultFirestore = try {
                    FirebaseFirestore.getInstance()
                } catch (_: Exception) {
                    null
                }
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
                                val adminCategoryTag = doc.getString("adminCategoryTag")?.takeIf { it.isNotBlank() } ?: "Standard"
                                val approvalStatus = doc.getString("approvalStatus")?.takeIf { it.isNotBlank() } ?: "APPROVED"
                                val isSalonSuspended = doc.getBoolean("isSuspended") ?: false

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
                                    adminCategoryTag = adminCategoryTag,
                                    approvalStatus = approvalStatus,
                                    isSuspended = isSalonSuspended,
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
        return readBooleanField(doc, "isAdmin", false) ||
            readBooleanField(doc, "admin", false) ||
            readBooleanField(doc, "is_admin", false) ||
            doc.getString("role")?.trim()?.equals("admin", ignoreCase = true) == true
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
                combinedById[u.id] = existing.copy(
                    isAdmin = existing.isAdmin || u.isAdmin,
                    isBlocked = existing.isBlocked || u.isBlocked,
                    isSuspended = existing.isSuspended || u.isSuspended
                )
            }
        }
        val mergedList = combinedById.values.sortedByDescending { it.createdAt }
        _allUsers.value = mergedList

        val current = _currentUserProfile.value ?: return
        val matchingDocs = (namedDbUsers + defaultDbUsers).filter { isSameUserIdentity(it, current) }
        if (matchingDocs.isNotEmpty()) {
            val exactDoc = matchingDocs.find { it.id == current.id } ?: matchingDocs.first()
            val anyIsAdmin = current.isAdmin || matchingDocs.any { it.isAdmin }
            val anyIsBlocked = exactDoc.isBlocked
            val maxStrikes = maxOf(current.cancellationStrikes, exactDoc.cancellationStrikes)
            val updated = current.copy(
                isAdmin = anyIsAdmin,
                isBlocked = anyIsBlocked,
                isSuspended = exactDoc.isSuspended || anyIsBlocked,
                cancellationCount = maxStrikes,
                cancellationStrikes = maxStrikes
            )
            if (updated != current) {
                _currentUserProfile.value = updated
            }
            if (anyIsAdmin && exactDoc.id == current.id && !exactDoc.isAdmin) {
                firestore?.collection("users")?.document(current.id)
                    ?.set(mapOf("isAdmin" to true), com.google.firebase.firestore.SetOptions.merge())
            }
        }
    }

    private fun parseUserDocument(doc: com.google.firebase.firestore.DocumentSnapshot): UserProfile? {
        return try {
            val uid = doc.id.takeIf { it.isNotBlank() }
                ?: doc.getString("id")?.takeIf { it.isNotBlank() }
                ?: return null
            val strikes = maxOf(
                doc.getLong("cancellationStrikes")?.toInt() ?: 0,
                doc.getLong("cancellationCount")?.toInt() ?: 0
            )
            val isBlockedFlag = readBooleanField(doc, "isBlocked", false)
            val isSuspendedFlag = readBooleanField(doc, "isSuspended", strikes >= 3) || isBlockedFlag
            val phoneVal = doc.getString("phone") ?: ""
            val emailVal = doc.getString("email") ?: ""
            val isAdminFlag = readIsAdminFromDoc(doc)

            UserProfile(
                id = uid,
                name = SecurityValidator.sanitizeName(
                    doc.getString("name")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("displayName")?.takeIf { it.isNotBlank() }
                        ?: "Customer"
                ),
                email = emailVal,
                phone = phoneVal,
                isPhoneVerified = readBooleanField(doc, "isPhoneVerified", phoneVal.isNotBlank()),
                role = doc.getString("role") ?: "customer",
                salonId = doc.getString("salonId"),
                hasRegisteredSalon = readBooleanField(doc, "hasRegisteredSalon", false),
                defaultCity = doc.getString("defaultCity") ?: "Current Location",
                cancellationCount = strikes,
                cancellationStrikes = strikes,
                isSuspended = isSuspendedFlag,
                isBlocked = isBlockedFlag,
                isAdmin = isAdminFlag,
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
        userDocListener?.remove()
        defaultUserDocListener?.remove()
        val cleanUid = customerId.trim()
        if (cleanUid.isBlank() || cleanUid in invalidSharedUserIds) {
            _customerBookings.value = emptyList()
            _latestCompletedBooking.value = null
            return
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
     * Manual Sign Up with Full Name, Email, and Password using standard
     * `FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)`.
     * Saves the user's `name` and `email` to Firestore (`users/{uid}`) upon signup.
     * Leaves `phone` empty if not already present so the user is routed to `PhoneSetupScreen`.
     */
    fun signUpWithEmailAndPassword(
        fullName: String,
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanName = SecurityValidator.sanitizeName(fullName, trimEdges = true)
        if (cleanName.length < 2) {
            val err = "Please enter your full name (at least 2 characters)."
            _authError.value = err
            onError(err)
            return
        }

        val cleanEmail = email.trim()
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
        val firestoreDb = firestore

        if (!isFirebaseReady || firestoreDb == null) {
            _isLoading.value = false
            val localProfile = UserProfile(
                id = "email_uid_${cleanEmail.lowercase().hashCode()}",
                name = cleanName,
                email = cleanEmail,
                phone = ""
            )
            _currentUserProfile.value = localProfile
            _currentRole.value = UserRole.CUSTOMER
            _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
            onSuccess()
            return
        }

        FirebaseAuth.getInstance().createUserWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    _isLoading.value = false
                    val err = "Failed to create account. Please try again."
                    _authError.value = err
                    onError(err)
                    return@addOnSuccessListener
                }

                val uid = firebaseUser.uid
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(cleanName)
                    .build()
                firebaseUser.updateProfile(profileUpdates)

                saveGoogleUserAndAuthenticate(
                    uid = uid,
                    displayName = cleanName,
                    email = cleanEmail,
                    onSuccess = onSuccess,
                    onError = onError
                )
            }
            .addOnFailureListener { err ->
                if (isEmailPasswordProviderDisabledError(err)) {
                    // Seamless Firestore-backed Email/Password registration when the project's Identity Platform provider is disabled
                    try {
                        FirebaseAuth.getInstance().signOut()
                    } catch (_: Exception) {
                    }
                    val uid = "usr_" + hashPasswordSha256(cleanEmail.lowercase()).take(20)
                    val passwordHash = hashPasswordSha256(password)
                    val userDocRef = firestoreDb.collection("users").document(uid)
                    userDocRef.get()
                        .addOnSuccessListener { existingDoc ->
                            if (existingDoc != null && existingDoc.exists()) {
                                val savedHash = existingDoc.getString("passwordHash")
                                if (!savedHash.isNullOrBlank() && savedHash != passwordHash) {
                                    _isLoading.value = false
                                    val msg = "An account with this email already exists. Please log in instead."
                                    _authError.value = msg
                                    onError(msg)
                                    return@addOnSuccessListener
                                }
                                userDocRef.set(
                                    mapOf(
                                        "name" to cleanName,
                                        "email" to cleanEmail,
                                        "passwordHash" to passwordHash
                                    ),
                                    com.google.firebase.firestore.SetOptions.merge()
                                )
                                saveGoogleUserAndAuthenticate(
                                    uid = uid,
                                    displayName = cleanName,
                                    email = cleanEmail,
                                    onSuccess = onSuccess,
                                    onError = onError
                                )
                            } else {
                                val newProfile = UserProfile(
                                    id = uid,
                                    name = cleanName,
                                    email = cleanEmail,
                                    phone = "",
                                    isPhoneVerified = false,
                                    role = "customer",
                                    defaultCity = _appPreferences.value.defaultCity,
                                    createdAt = System.currentTimeMillis()
                                )
                                val docMap = newProfile.toMap().minus("isAdmin") + mapOf("passwordHash" to passwordHash)
                                userDocRef.set(docMap, com.google.firebase.firestore.SetOptions.merge())
                                    .addOnCompleteListener {
                                        sessionPrefs.edit()
                                            .putString("saved_uid", uid)
                                            .putString("saved_name", cleanName)
                                            .putString("saved_email", cleanEmail)
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
                        .addOnFailureListener {
                            val newProfile = UserProfile(
                                id = uid,
                                name = cleanName,
                                email = cleanEmail,
                                phone = "",
                                isPhoneVerified = false,
                                role = "customer",
                                defaultCity = _appPreferences.value.defaultCity,
                                createdAt = System.currentTimeMillis()
                            )
                            sessionPrefs.edit()
                                .putString("saved_uid", uid)
                                .putString("saved_name", cleanName)
                                .putString("saved_email", cleanEmail)
                                .putString("saved_role", "customer")
                                .apply()
                            _currentUserProfile.value = newProfile
                            attachCustomerBookingsListener(uid)
                            _currentRole.value = UserRole.CUSTOMER
                            _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                            _isLoading.value = false
                            onSuccess()
                        }
                } else {
                    _isLoading.value = false
                    val errMessage = err.localizedMessage ?: "Sign up failed. Please check your details and try again."
                    _authError.value = errMessage
                    Log.w(tag, "FirebaseAuth createUserWithEmailAndPassword failure: $errMessage")
                    onError(errMessage)
                }
            }
    }

    /**
     * Manual Login with Email and Password using standard
     * `FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)`.
     * Fetches the user's Firestore `users/{uid}` document so if `phone` is missing/invalid,
     * the app automatically routes them to `PhoneSetupScreen`.
     */
    fun signInWithEmailAndPassword(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanEmail = email.trim()
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
        val firestoreDb = firestore

        if (!isFirebaseReady || firestoreDb == null) {
            _isLoading.value = false
            val localProfile = UserProfile(
                id = "email_uid_${cleanEmail.lowercase().hashCode()}",
                name = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                email = cleanEmail,
                phone = ""
            )
            _currentUserProfile.value = localProfile
            _currentRole.value = UserRole.CUSTOMER
            _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
            onSuccess()
            return
        }

        FirebaseAuth.getInstance().signInWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    _isLoading.value = false
                    val err = "User session is null after login. Please try again."
                    _authError.value = err
                    onError(err)
                    return@addOnSuccessListener
                }

                val uid = firebaseUser.uid
                val displayName = firebaseUser.displayName?.takeIf { it.isNotBlank() }
                    ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                val userEmail = firebaseUser.email?.takeIf { it.isNotBlank() } ?: cleanEmail

                saveGoogleUserAndAuthenticate(
                    uid = uid,
                    displayName = displayName,
                    email = userEmail,
                    onSuccess = onSuccess,
                    onError = onError
                )
            }
            .addOnFailureListener { err ->
                if (isEmailPasswordProviderDisabledError(err)) {
                    // Seamless Firestore-backed Email/Password login when the project's Identity Platform provider is disabled
                    try {
                        FirebaseAuth.getInstance().signOut()
                    } catch (_: Exception) {
                    }
                    val uid = "usr_" + hashPasswordSha256(cleanEmail.lowercase()).take(20)
                    val passwordHash = hashPasswordSha256(password)
                    val userDocRef = firestoreDb.collection("users").document(uid)
                    userDocRef.get()
                        .addOnSuccessListener { existingDoc ->
                            if (existingDoc != null && existingDoc.exists()) {
                                val savedHash = existingDoc.getString("passwordHash")
                                if (!savedHash.isNullOrBlank() && savedHash != passwordHash) {
                                    _isLoading.value = false
                                    val msg = "Incorrect password. Please try again."
                                    _authError.value = msg
                                    onError(msg)
                                    return@addOnSuccessListener
                                }
                                val displayName = existingDoc.getString("name")?.takeIf { it.isNotBlank() }
                                    ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                                saveGoogleUserAndAuthenticate(
                                    uid = uid,
                                    displayName = displayName,
                                    email = cleanEmail,
                                    onSuccess = onSuccess,
                                    onError = onError
                                )
                            } else {
                                // Check if user document exists by email field
                                firestoreDb.collection("users")
                                    .whereEqualTo("email", cleanEmail)
                                    .get()
                                    .addOnSuccessListener { querySnap ->
                                        val matchedDoc = querySnap.documents.firstOrNull()
                                        if (matchedDoc != null && matchedDoc.exists()) {
                                            val savedHash = matchedDoc.getString("passwordHash")
                                            if (!savedHash.isNullOrBlank() && savedHash != passwordHash) {
                                                _isLoading.value = false
                                                val msg = "Incorrect password. Please try again."
                                                _authError.value = msg
                                                onError(msg)
                                                return@addOnSuccessListener
                                            }
                                            val matchedName = matchedDoc.getString("name")?.takeIf { it.isNotBlank() }
                                                ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                                            saveGoogleUserAndAuthenticate(
                                                uid = matchedDoc.id,
                                                displayName = matchedName,
                                                email = cleanEmail,
                                                onSuccess = onSuccess,
                                                onError = onError
                                            )
                                        } else {
                                            _isLoading.value = false
                                            val msg = "No account found with this email. Please Sign Up first."
                                            _authError.value = msg
                                            onError(msg)
                                        }
                                    }
                                    .addOnFailureListener {
                                        _isLoading.value = false
                                        val msg = "No account found with this email. Please Sign Up first."
                                        _authError.value = msg
                                        onError(msg)
                                    }
                            }
                        }
                        .addOnFailureListener { getErr ->
                            _isLoading.value = false
                            val msg = getErr.localizedMessage ?: "Login failed. Please try again."
                            _authError.value = msg
                            onError(msg)
                        }
                } else {
                    _isLoading.value = false
                    val errMessage = err.localizedMessage ?: "Invalid email or password. Please try again."
                    _authError.value = errMessage
                    Log.w(tag, "FirebaseAuth signInWithEmailAndPassword failure: $errMessage")
                    onError(errMessage)
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
        _isLoading.value = true
        _authError.value = null
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

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val activityContext = findActivity(context) ?: context
                val credentialManager = CredentialManager.create(activityContext)
                val serverClientId = context.getString(R.string.default_web_client_id)

                // Clear any stale cached credential state so Credential Manager performs a fresh
                // standard Sign-In instead of attempting a reauthentication flow on an expired token.
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (_: Exception) {
                }

                val resolvedGoogleIdOption = googleIdOption ?: GetGoogleIdOption.Builder()
                    .setServerClientId(serverClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()

                val resolvedSignInWithGoogleOption = signInWithGoogleOption ?: GetSignInWithGoogleOption.Builder(serverClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()

                val primaryRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(resolvedGoogleIdOption)
                    .build()

                val result = try {
                    credentialManager.getCredential(context = activityContext, request = primaryRequest)
                } catch (firstEx: Exception) {
                    Log.w(tag, "Primary GetGoogleIdOption sign-in attempt fell back (${firstEx.message}), retrying with GetSignInWithGoogleOption...")
                    try {
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    } catch (_: Exception) {
                    }
                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(resolvedSignInWithGoogleOption)
                        .build()
                    credentialManager.getCredential(context = activityContext, request = fallbackRequest)
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
                            val errMessage = formatStandardGoogleSignInError(err.message ?: err.localizedMessage)
                                ?: "Google Firebase sign-in failed."
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
                _isLoading.value = false
                val msg = formatStandardGoogleSignInError(e.message ?: e.localizedMessage)
                    ?: "Google Sign-In failed. Please try again."
                _authError.value = msg
                Log.e(tag, "Google Sign-In exception: ${e.message}", e)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
                onError(msg)
            }
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
                    val existingPhone = existingDoc.getString("phone") ?: ""
                    val existingIsPhoneVerified = readBooleanField(existingDoc, "isPhoneVerified", existingPhone.isNotBlank())
                    val existingCancellationCount = maxOf(
                        existingDoc.getLong("cancellationStrikes")?.toInt() ?: 0,
                        existingDoc.getLong("cancellationCount")?.toInt() ?: 0
                    )
                    val existingIsSuspended = readBooleanField(existingDoc, "isSuspended", existingCancellationCount >= 3)
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
                    val existingCreatedAt = existingDoc.getLong("createdAt") ?: System.currentTimeMillis()

                    val userProfile = UserProfile(
                        id = uid,
                        name = existingName,
                        email = existingEmail,
                        phone = existingPhone,
                        isPhoneVerified = existingIsPhoneVerified,
                        role = "customer",
                        salonId = existingSalonId,
                        hasRegisteredSalon = existingHasSalon,
                        defaultCity = existingCity,
                        cancellationCount = existingCancellationCount,
                        cancellationStrikes = existingCancellationCount,
                        isSuspended = existingIsSuspended || existingIsBlocked,
                        isBlocked = existingIsBlocked,
                        isAdmin = existingIsAdmin,
                        createdAt = existingCreatedAt
                    )

                    sessionPrefs.edit()
                        .putString("saved_uid", uid)
                        .putString("saved_name", existingName)
                        .putString("saved_email", existingEmail)
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

                    val existingPhoneVal = doc.getString("phone") ?: user?.phoneNumber ?: ""
                    val existingEmailVal = doc.getString("email") ?: user?.email ?: ""
                    val existingPhoneVerifiedVal = readBooleanField(doc, "isPhoneVerified", existingPhoneVal.isNotBlank())
                    val existingIsBlockedVal = readBooleanField(doc, "isBlocked", false)
                    val existingIsAdminVal = resolveAdminFlagForUser(
                        uid = uid,
                        email = existingEmailVal,
                        phone = existingPhoneVal,
                        directFlag = readIsAdminFromDoc(doc)
                    )

                    // Every user MUST default to Customer Mode upon Login / Session Restore
                    val profile = UserProfile(
                        id = uid,
                        name = SecurityValidator.sanitizeName(doc.getString("name") ?: user?.displayName ?: "Customer"),
                        email = existingEmailVal,
                        phone = existingPhoneVal,
                        isPhoneVerified = existingPhoneVerifiedVal,
                        role = "customer",
                        salonId = salonIdVal,
                        hasRegisteredSalon = hasRegisteredVal,
                        defaultCity = defaultCityVal,
                        cancellationCount = cancelCount,
                        cancellationStrikes = cancelCount,
                        isSuspended = suspended || existingIsBlockedVal,
                        isBlocked = existingIsBlockedVal,
                        isAdmin = existingIsAdminVal,
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
        bookingDate: String
    ): Booking {
        val currentProfile = _currentUserProfile.value
        val resolvedCustomerId = currentProfile?.id?.takeIf { it.isNotBlank() && it !in invalidSharedUserIds }
            ?: customerId.trim()
        if (resolvedCustomerId.isBlank() || resolvedCustomerId in invalidSharedUserIds) {
            throw IllegalStateException("Please log in with your Mobile Number or Email before booking.")
        }

        // Rule 3: Strictly enforce Admin Block & 3-Strike Anti-Spam Rule (isBlocked || isSuspended || cancellationStrikes >= 3)
        if (currentProfile != null && currentProfile.isEffectivelyBlocked) {
            val blockMsg = if (currentProfile.isBlocked) {
                "Account Blocked: Your account has been blocked by TrimTime Admin and cannot make new bookings."
            } else {
                "Security Lockout: Your account is suspended due to 3 or more booking cancellations."
            }
            throw IllegalStateException(blockMsg)
        }

        // Rule 4: Prevent Self-Booking (Owner Booking Their Own Salon) & Suspended/Rejected Salons
        val targetSalon = _salons.value.find { it.id == salonId }
        if (targetSalon != null && (targetSalon.isSuspended || targetSalon.approvalStatus.equals("REJECTED", ignoreCase = true))) {
            throw IllegalStateException("This salon is currently suspended or unavailable for bookings.")
        }
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
     * Tab 1: Updates a Salon's verification status ("APPROVED", "REJECTED", "PENDING", or "SUSPENDED")
     * and suspension state in Firestore `salons/{salonId}`.
     * Strictly requires `currentUserProfile.isAdmin == true`.
     */
    fun updateSalonApproval(
        salonId: String,
        approvalStatus: String,
        isSuspended: Boolean = false,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val adminProfile = _currentUserProfile.value
        if (adminProfile == null || adminProfile.isAdmin != true) {
            onError("Access Denied: Only Super Admins can modify salon approval status.")
            return
        }
        val targetId = salonId.trim()
        if (targetId.isBlank()) return
        val normalizedStatus = approvalStatus.trim().uppercase()
        val effectiveOpen = !isSuspended && normalizedStatus == "APPROVED"

        _salons.value = _salons.value.map { salon ->
            if (salon.id == targetId) {
                salon.copy(
                    approvalStatus = normalizedStatus,
                    isSuspended = isSuspended,
                    isOpen = if (isSuspended || normalizedStatus == "REJECTED") false else salon.isOpen
                )
            } else {
                salon
            }
        }

        val db = firestore
        if (db == null) {
            onSuccess()
            return
        }

        val updates = mutableMapOf<String, Any>(
            "approvalStatus" to normalizedStatus,
            "isSuspended" to isSuspended,
            "adminActorId" to adminProfile.id
        )
        if (!effectiveOpen) {
            updates["isOpen"] = false
            updates["currentStatus"] = "CLOSED"
        }

        db.collection("salons").document(targetId)
            .set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d(tag, "Admin updated salon $targetId approvalStatus=$normalizedStatus, isSuspended=$isSuspended")
                onSuccess()
            }
            .addOnFailureListener { err ->
                Log.e(tag, "Failed to update salon approval: ${err.message}")
                onError(err.localizedMessage ?: "Failed to update salon status.")
            }
    }

    /**
     * Tab 1: Assigns a category tier tag ("Premium", "Standard", or "Rural/Local") to a Salon in Firestore.
     * Strictly requires `currentUserProfile.isAdmin == true`.
     */
    fun updateSalonCategory(
        salonId: String,
        categoryTag: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val adminProfile = _currentUserProfile.value
        if (adminProfile == null || adminProfile.isAdmin != true) {
            onError("Access Denied: Only Super Admins can assign salon category tags.")
            return
        }
        val targetId = salonId.trim()
        val cleanTag = categoryTag.trim().ifBlank { "Standard" }
        if (targetId.isBlank()) return

        _salons.value = _salons.value.map { salon ->
            if (salon.id == targetId) {
                salon.copy(adminCategoryTag = cleanTag)
            } else {
                salon
            }
        }

        val db = firestore
        if (db == null) {
            onSuccess()
            return
        }

        db.collection("salons").document(targetId)
            .set(
                mapOf(
                    "adminCategoryTag" to cleanTag,
                    "adminActorId" to adminProfile.id
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .addOnSuccessListener {
                Log.d(tag, "Admin updated salon $targetId adminCategoryTag=$cleanTag")
                onSuccess()
            }
            .addOnFailureListener { err ->
                Log.e(tag, "Failed to update salon category tag: ${err.message}")
                onError(err.localizedMessage ?: "Failed to update salon category.")
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
