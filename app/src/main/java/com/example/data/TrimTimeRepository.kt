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
import com.example.model.Booking
import com.example.model.BookingStatus
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
import kotlin.random.Random

sealed class PinVerificationResult {
    data object Success : PinVerificationResult()
    data class IncorrectPin(val message: String) : PinVerificationResult()
    data class BookingNotFound(val message: String) : PinVerificationResult()
    data class AlreadyCompleted(val message: String) : PinVerificationResult()
}

class TrimTimeRepository private constructor(context: Context) {

    private val tag = "TrimTimeRepository"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val appContext = context.applicationContext
    private val sessionPrefs = appContext.getSharedPreferences("trimtime_session_prefs", Context.MODE_PRIVATE)

    // Firebase Instances
    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var isFirebaseReady = false

    private var salonsListener: ListenerRegistration? = null
    private var bookingsListener: ListenerRegistration? = null
    private var userDocListener: ListenerRegistration? = null

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

    // Real-time Bookings directly from Firestore
    private val _bookings = MutableStateFlow<List<Booking>>(emptyList())
    val bookings: StateFlow<List<Booking>> = _bookings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // Triggered when Google Sign-In fails on physical APK due to unregistered SHA-1
    private val _showSha1FallbackDialog = MutableStateFlow(false)
    val showSha1FallbackDialog: StateFlow<Boolean> = _showSha1FallbackDialog.asStateFlow()

    private val _isCloudSynced = MutableStateFlow(false)
    val isCloudSynced: StateFlow<Boolean> = _isCloudSynced.asStateFlow()

    init {
        initFirebase(context)
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
                if (firebaseUser != null) {
                    Log.d(tag, "Active Firebase session detected for uid: ${firebaseUser.uid}")
                    _authState.value = AppAuthState.Loading
                    fetchUserProfile(firebaseUser.uid)
                } else if (!savedUid.isNullOrBlank() && savedUid != "dev_test_user_001") {
                    Log.d(tag, "Restoring persisted Firestore user session for uid: $savedUid")
                    _authState.value = AppAuthState.Loading
                    fetchUserProfile(savedUid)
                } else {
                    Log.d(tag, "No active session found. Setting Unauthenticated.")
                    _currentUserProfile.value = null
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

                                val distance = doc.getString("distance")
                                    ?: if (lat != null && lng != null) {
                                        // Customer demo default GPS: Patna (25.5941, 85.1376)
                                        val dKm = calculateDistanceKm(25.5941, 85.1376, lat, lng)
                                        String.format(java.util.Locale.US, "%.1f km", dKm)
                                    } else "0.5 km"

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

                                Salon(
                                    id = doc.getString("id") ?: docId,
                                    name = salonName,
                                    location = location,
                                    distance = distance,
                                    rating = rating,
                                    reviewCount = reviewCount,
                                    partnerId = partnerId,
                                    ownerId = ownerId,
                                    phone = phone,
                                    services = finalServices,
                                    isOpen = isOpen,
                                    currentStatus = currentStatus,
                                    availableChairs = availableChairs,
                                    waitingTimeMinutes = waitingTimeMinutes,
                                    tagline = tagline,
                                    nextSlotMinutes = nextSlotMinutes,
                                    shopAddress = shopAddress,
                                    landmark = landmark,
                                    latitude = lat,
                                    longitude = lng
                                )
                            } catch (e: Exception) {
                                Log.e(tag, "Error parsing salon doc ${doc.id}: ${e.message}")
                                null
                            }
                        }

                        if (liveSalons.isNotEmpty()) {
                            Log.d(tag, "Successfully loaded ${liveSalons.size} real salons from Firestore!")
                            _salons.value = liveSalons
                        }
                    }
                }
            }

            // Live Real-Time Listener for Bookings collection
            bookingsListener?.remove()
            bookingsListener = db.collection("bookings").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "Firestore bookings listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val liveBookings = snapshot.documents.mapNotNull { doc ->
                        try {
                            val resolvedPin = doc.getString("servicePin")?.takeIf { it.isNotBlank() }
                                ?: doc.getString("pin")?.takeIf { it.isNotBlank() }
                                ?: "0000"
                            Booking(
                                id = doc.getString("id") ?: doc.id,
                                customerId = doc.getString("customerId") ?: "",
                                customerName = doc.getString("customerName") ?: "",
                                customerPhone = doc.getString("customerPhone") ?: "",
                                salonId = doc.getString("salonId") ?: "",
                                salonName = doc.getString("salonName") ?: "",
                                service = doc.getString("service") ?: "",
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
                    _bookings.value = liveBookings.sortedByDescending { it.createdAt }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting up live listeners: ${e.message}")
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
     * Direct Firestore Account Sign-In / Registration (Works on ANY APK without SHA-1 restrictions):
     * Creates or logs into the user's real Firestore 'users' document using their Name, Email, and optional 10-digit Phone.
     */
    fun signInWithRealIdentity(
        name: String,
        email: String,
        phone: String = "",
        asPartner: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim().lowercase()
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

        val rawDigits = phone.filter { it.isDigit() }
        val tenDigits = if (rawDigits.length > 10 && rawDigits.startsWith("91")) {
            rawDigits.drop(2).take(10)
        } else {
            rawDigits.takeLast(10)
        }
        val formattedPhone = if (tenDigits.length == 10) "+91 $tenDigits" else ""

        val uid = "usr_" + trimmedEmail.replace(Regex("[^a-z0-9]"), "_")
        _isLoading.value = true
        _authError.value = null
        _showSha1FallbackDialog.value = false

        val firestoreDb = firestore
        if (firestoreDb == null) {
            _isLoading.value = false
            val msg = "Firestore database is not connected."
            _authError.value = msg
            onError(msg)
            return
        }

        val targetRoleStr = if (asPartner) "partner" else "customer"
        val userDocRef = firestoreDb.collection("users").document(uid)
        userDocRef.get()
            .addOnSuccessListener { existingDoc ->
                if (existingDoc != null && existingDoc.exists()) {
                    val existingPhone = existingDoc.getString("phone") ?: ""
                    val resolvedPhone = formattedPhone.ifBlank { existingPhone }
                    val existingCancellationCount = existingDoc.getLong("cancellationCount")?.toInt() ?: 0
                    val existingIsSuspended = existingDoc.getBoolean("isSuspended") ?: (existingCancellationCount >= 3)
                    val existingRole = if (asPartner) "partner" else (existingDoc.getString("role") ?: "customer")
                    val existingSalonId = existingDoc.getString("salonId")
                    val existingCreatedAt = existingDoc.getLong("createdAt") ?: System.currentTimeMillis()

                    val userProfile = UserProfile(
                        id = uid,
                        name = trimmedName,
                        email = trimmedEmail,
                        phone = resolvedPhone,
                        role = existingRole,
                        salonId = existingSalonId,
                        cancellationCount = existingCancellationCount,
                        isSuspended = existingIsSuspended,
                        createdAt = existingCreatedAt
                    )

                    userDocRef.set(userProfile.toMap(), com.google.firebase.firestore.SetOptions.merge())
                    sessionPrefs.edit()
                        .putString("saved_uid", uid)
                        .putString("saved_name", trimmedName)
                        .putString("saved_email", trimmedEmail)
                        .putString("saved_role", existingRole)
                        .apply()

                    _currentUserProfile.value = userProfile
                    _currentRole.value = if (existingRole.equals("partner", ignoreCase = true)) UserRole.PARTNER else UserRole.CUSTOMER
                    _authState.value = AppAuthState.Authenticated(_currentRole.value)
                    _isLoading.value = false
                    if (_currentRole.value == UserRole.PARTNER) {
                        ensurePartnerSalonInFirestore(userProfile)
                    }
                    onSuccess()
                } else {
                    val assignedSalonId = if (asPartner) "salon_$uid" else null
                    val newProfile = UserProfile(
                        id = uid,
                        name = trimmedName,
                        email = trimmedEmail,
                        phone = formattedPhone,
                        role = targetRoleStr,
                        salonId = assignedSalonId,
                        cancellationCount = 0,
                        isSuspended = false,
                        createdAt = System.currentTimeMillis()
                    )

                    userDocRef.set(newProfile.toMap())
                        .addOnSuccessListener {
                            sessionPrefs.edit()
                                .putString("saved_uid", uid)
                                .putString("saved_name", trimmedName)
                                .putString("saved_email", trimmedEmail)
                                .putString("saved_role", targetRoleStr)
                                .apply()

                            _currentUserProfile.value = newProfile
                            _currentRole.value = if (asPartner) UserRole.PARTNER else UserRole.CUSTOMER
                            _authState.value = AppAuthState.Authenticated(_currentRole.value)
                            _isLoading.value = false
                            if (asPartner) {
                                ensurePartnerSalonInFirestore(newProfile)
                            }
                            onSuccess()
                        }
                        .addOnFailureListener { setErr ->
                            _isLoading.value = false
                            val errMsg = "Failed to save account to Firestore: ${setErr.localizedMessage}"
                            _authError.value = errMsg
                            onError(errMsg)
                        }
                }
            }
            .addOnFailureListener { getErr ->
                _isLoading.value = false
                val errMsg = "Failed to connect to Firestore: ${getErr.localizedMessage}"
                _authError.value = errMsg
                onError(errMsg)
            }
    }

    /**
     * Simple 1-Identifier Login for Existing Users (Mobile Number OR Email Address):
     * Searches Firestore `users` collection for an existing account matching the phone or email.
     * - If found: logs the user in immediately with their saved Name, Role (Customer/Partner), and profile data.
     * - If not found: triggers `onAccountNotFound(prefillEmail, prefillPhone)` with message
     *   "Account not found. Switching to Sign Up..." so the UI switches to the Sign Up tab with pre-filled data.
     */
    fun loginExistingUser(
        identifier: String,
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

        val firestoreDb = firestore
        if (firestoreDb == null) {
            _isLoading.value = false
            val msg = "Firestore database is not connected."
            _authError.value = msg
            onError(msg)
            return
        }

        _isLoading.value = true
        _authError.value = null
        _showSha1FallbackDialog.value = false

        if (trimmed.contains("@")) {
            val cleanEmail = trimmed.lowercase()
            if (!cleanEmail.contains(".")) {
                _isLoading.value = false
                val msg = "Please enter a valid email address."
                _authError.value = msg
                onError(msg)
                return
            }

            val deterministicUid = "usr_" + cleanEmail.replace(Regex("[^a-z0-9]"), "_")
            firestoreDb.collection("users").document(deterministicUid).get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        applyExistingUserDocument(doc, onSuccess)
                    } else {
                        // Also query by 'email' field in case account was created with Google UID
                        firestoreDb.collection("users")
                            .whereEqualTo("email", cleanEmail)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                val matchedDoc = querySnapshot?.documents?.firstOrNull()
                                if (matchedDoc != null && matchedDoc.exists()) {
                                    applyExistingUserDocument(matchedDoc, onSuccess)
                                } else {
                                    _isLoading.value = false
                                    val notFoundMsg = "Account not found. Switching to Sign Up..."
                                    _authError.value = notFoundMsg
                                    onAccountNotFound(cleanEmail, "")
                                }
                            }
                            .addOnFailureListener { err ->
                                _isLoading.value = false
                                val errMsg = "Failed to look up email in Firestore: ${err.localizedMessage}"
                                _authError.value = errMsg
                                onError(errMsg)
                            }
                    }
                }
                .addOnFailureListener { err ->
                    _isLoading.value = false
                    val errMsg = "Failed to connect to Firestore: ${err.localizedMessage}"
                    _authError.value = errMsg
                    onError(errMsg)
                }
        } else {
            val rawDigits = trimmed.filter { it.isDigit() }
            val tenDigits = if (rawDigits.length > 10 && rawDigits.startsWith("91")) {
                rawDigits.drop(2).take(10)
            } else {
                rawDigits.takeLast(10)
            }

            if (tenDigits.length != 10) {
                _isLoading.value = false
                val msg = "Please enter a valid 10-Digit Mobile Number or Email Address."
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

            firestoreDb.collection("users")
                .whereIn("phone", candidatePhones)
                .limit(1)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val matchedDoc = querySnapshot?.documents?.firstOrNull()
                    if (matchedDoc != null && matchedDoc.exists()) {
                        applyExistingUserDocument(matchedDoc, onSuccess)
                    } else {
                        _isLoading.value = false
                        val notFoundMsg = "Account not found. Switching to Sign Up..."
                        _authError.value = notFoundMsg
                        onAccountNotFound("", tenDigits)
                    }
                }
                .addOnFailureListener { err ->
                    _isLoading.value = false
                    val errMsg = "Failed to look up mobile number in Firestore: ${err.localizedMessage}"
                    _authError.value = errMsg
                    onError(errMsg)
                }
        }
    }

    private fun applyExistingUserDocument(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        onSuccess: (UserProfile) -> Unit
    ) {
        val uid = doc.id
        val existingName = doc.getString("name")?.takeIf { it.isNotBlank() }
            ?: doc.getString("displayName")?.takeIf { it.isNotBlank() }
            ?: "Customer"
        val existingEmail = doc.getString("email") ?: ""
        val existingPhone = doc.getString("phone") ?: ""
        val existingRole = doc.getString("role") ?: "customer"
        val existingSalonId = doc.getString("salonId")
        val existingCancellationCount = doc.getLong("cancellationCount")?.toInt() ?: 0
        val existingIsSuspended = doc.getBoolean("isSuspended") ?: (existingCancellationCount >= 3)
        val existingCreatedAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

        val userProfile = UserProfile(
            id = uid,
            name = existingName,
            email = existingEmail,
            phone = existingPhone,
            role = existingRole,
            salonId = existingSalonId,
            cancellationCount = existingCancellationCount,
            isSuspended = existingIsSuspended,
            createdAt = existingCreatedAt
        )

        sessionPrefs.edit()
            .putString("saved_uid", uid)
            .putString("saved_name", existingName)
            .putString("saved_email", existingEmail)
            .putString("saved_role", existingRole)
            .apply()

        _currentUserProfile.value = userProfile
        _currentRole.value = if (existingRole.equals("partner", ignoreCase = true)) {
            UserRole.PARTNER
        } else {
            UserRole.CUSTOMER
        }
        _authState.value = AppAuthState.Authenticated(_currentRole.value)
        _isLoading.value = false
        if (_currentRole.value == UserRole.PARTNER) {
            ensurePartnerSalonInFirestore(userProfile)
        }
        onSuccess(userProfile)
    }

    /**
     * Developer Test Login:
     * Explicitly invoked only when user taps "Developer Test Login".
     */
    fun developerTestLogin(
        testUid: String = "dev_test_user_001",
        testDisplayName: String = "Dev Tester",
        testEmail: String = "devtester@trimtime.local",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        _isLoading.value = true
        _authError.value = null
        Log.d(tag, "Initiating Developer Test Login for uid: $testUid")
        saveGoogleUserAndAuthenticate(
            uid = testUid,
            displayName = testDisplayName,
            email = testEmail,
            onSuccess = {
                _isLoading.value = false
                Log.d(tag, "Developer Test Login successfully connected to real Firestore.")
                onSuccess()
            },
            onError = { err ->
                _isLoading.value = false
                _authError.value = err
                Log.w(tag, "Developer Test Login error: $err")
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
            val msg = "Firestore database is not connected."
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
                    val existingCancellationCount = existingDoc.getLong("cancellationCount")?.toInt() ?: 0
                    val existingIsSuspended = existingDoc.getBoolean("isSuspended") ?: (existingCancellationCount >= 3)
                    val existingRole = existingDoc.getString("role") ?: "customer"
                    val existingSalonId = existingDoc.getString("salonId")
                    val existingCreatedAt = existingDoc.getLong("createdAt") ?: System.currentTimeMillis()

                    val userProfile = UserProfile(
                        id = uid,
                        name = existingName,
                        email = existingEmail,
                        phone = existingPhone,
                        role = existingRole,
                        salonId = existingSalonId,
                        cancellationCount = existingCancellationCount,
                        isSuspended = existingIsSuspended,
                        createdAt = existingCreatedAt
                    )

                    Log.d(tag, "Existing Firestore user verified: ${userProfile.name} (${userProfile.email}), Phone: '${userProfile.phone}', Strikes: ${userProfile.cancellationCount}")
                    _currentUserProfile.value = userProfile
                    _currentRole.value = if (existingRole.equals("partner", ignoreCase = true)) UserRole.PARTNER else UserRole.CUSTOMER
                    _authState.value = AppAuthState.Authenticated(_currentRole.value)
                    _isLoading.value = false
                    if (_currentRole.value == UserRole.PARTNER) {
                        ensurePartnerSalonInFirestore(userProfile)
                    }
                    onSuccess()
                } else {
                    // New User: Create document in Firestore
                    val newProfile = UserProfile(
                        id = uid,
                        name = displayName.ifBlank { "Customer" },
                        email = email,
                        phone = "",
                        role = "customer",
                        salonId = null,
                        cancellationCount = 0,
                        isSuspended = false,
                        createdAt = System.currentTimeMillis()
                    )

                    userDocRef.set(newProfile.toMap())
                        .addOnSuccessListener {
                            Log.d(tag, "New user document successfully written to Firestore 'users' for uid: $uid")
                            _currentUserProfile.value = newProfile
                            _currentRole.value = UserRole.CUSTOMER
                            _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                            _isLoading.value = false
                            onSuccess()
                        }
                        .addOnFailureListener { setErr ->
                            _isLoading.value = false
                            val errMsg = "Failed to write user to Firestore: ${setErr.localizedMessage}"
                            _authError.value = errMsg
                            Log.e(tag, errMsg, setErr)
                            onError(errMsg)
                        }
                }
            }
            .addOnFailureListener { getErr ->
                _isLoading.value = false
                val errMsg = "Failed to verify user from Firestore: ${getErr.localizedMessage}"
                _authError.value = errMsg
                Log.e(tag, errMsg, getErr)
                onError(errMsg)
            }
    }

    /**
     * Rule 1: Mandatory Phone Collection (No OTP)
     * Saves user's 10-digit contact number directly to Firestore profile.
     */
    fun updateUserPhone(
        phone: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val current = _currentUserProfile.value
        if (current == null) {
            onError("User session not found.")
            return
        }

        val allDigits = phone.filter { it.isDigit() }
        val tenDigits = if (allDigits.length > 10 && allDigits.startsWith("91")) {
            allDigits.drop(2).take(10)
        } else {
            allDigits.takeLast(10)
        }

        if (tenDigits.length != 10) {
            onError("Please enter a valid 10-digit phone number.")
            return
        }

        val formattedPhone = "+91 $tenDigits"
        val updatedProfile = current.copy(phone = formattedPhone)
        _currentUserProfile.value = updatedProfile

        val userDocRef = firestore?.collection("users")?.document(current.id)
        if (userDocRef != null) {
            // Use set with merge to guarantee write succeeds even if the document was not pre-created
            userDocRef.set(mapOf("phone" to formattedPhone), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(tag, "Phone number successfully saved to Firestore.")
                    onSuccess()
                }
                .addOnFailureListener { err ->
                    Log.w(tag, "Failed to update phone in Firestore: ${err.message}")
                    onSuccess() // Keep going with local updated profile
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
        sessionPrefs.edit().clear().apply()
        _showSha1FallbackDialog.value = false
        _currentRole.value = UserRole.CUSTOMER
        _currentUserProfile.value = null
        _authState.value = AppAuthState.Unauthenticated
    }

    fun switchToPartnerRole(onSalonReady: (String) -> Unit = {}) {
        _currentRole.value = UserRole.PARTNER
        _authState.value = AppAuthState.Authenticated(UserRole.PARTNER)
        sessionPrefs.edit().putString("saved_role", "partner").apply()
        val currentProfile = _currentUserProfile.value
        if (currentProfile != null) {
            ensurePartnerSalonInFirestore(currentProfile, onSalonReady)
        }
    }

    fun switchToCustomerRole() {
        _currentRole.value = UserRole.CUSTOMER
        _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
        sessionPrefs.edit().putString("saved_role", "customer").apply()
    }

    /**
     * Real-Time Salon Profile & Creation (`salons` collection):
     * When a Partner logs in or switches to Partner Mode, fetches their salon document from Firestore
     * `salons` collection matching their `ownerId` / `partnerId` (or creates a new live document in
     * Firestore if they don't have one yet), and links `salonId` to their `users/{uid}` document.
     */
    fun ensurePartnerSalonInFirestore(
        profile: UserProfile,
        onSalonReady: (String) -> Unit = {}
    ) {
        val db = firestore ?: return
        val uid = profile.id
        if (uid.isBlank()) return

        // 1. Check if already in loaded _salons matching ownerId, partnerId, or profile.salonId
        val existingInMemory = _salons.value.find {
            it.ownerId == uid ||
            it.partnerId == uid ||
            (!profile.salonId.isNullOrBlank() && it.id == profile.salonId) ||
            it.id == "salon_$uid"
        }

        if (existingInMemory != null) {
            if (profile.salonId != existingInMemory.id || !profile.role.equals("partner", ignoreCase = true)) {
                val updatedProfile = profile.copy(role = "partner", salonId = existingInMemory.id)
                _currentUserProfile.value = updatedProfile
                db.collection("users").document(uid).set(
                    mapOf("role" to "partner", "salonId" to existingInMemory.id),
                    com.google.firebase.firestore.SetOptions.merge()
                )
            }
            onSalonReady(existingInMemory.id)
            return
        }

        // 2. Query Firestore 'salons' collection for ownerId == uid
        db.collection("salons")
            .whereEqualTo("ownerId", uid)
            .limit(1)
            .get()
            .addOnSuccessListener { ownerQuery ->
                val matchedDoc = ownerQuery?.documents?.firstOrNull()
                if (matchedDoc != null && matchedDoc.exists()) {
                    val foundSalonId = matchedDoc.id
                    val updatedProfile = profile.copy(role = "partner", salonId = foundSalonId)
                    _currentUserProfile.value = updatedProfile
                    db.collection("users").document(uid).set(
                        mapOf("role" to "partner", "salonId" to foundSalonId),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    onSalonReady(foundSalonId)
                } else {
                    // Check deterministic document ID 'salon_$uid' or profile.salonId
                    val targetSalonId = profile.salonId?.takeIf { it.isNotBlank() } ?: "salon_$uid"
                    db.collection("salons").document(targetSalonId).get()
                        .addOnSuccessListener { doc ->
                            if (doc != null && doc.exists()) {
                                val updatedProfile = profile.copy(role = "partner", salonId = targetSalonId)
                                _currentUserProfile.value = updatedProfile
                                db.collection("users").document(uid).set(
                                    mapOf("role" to "partner", "salonId" to targetSalonId),
                                    com.google.firebase.firestore.SetOptions.merge()
                                )
                                onSalonReady(targetSalonId)
                            } else {
                                // Create a new live Salon document in Firestore for this Partner
                                val cleanOwnerName = profile.name.takeIf { it.isNotBlank() } ?: "Partner"
                                val defaultShopName = if (cleanOwnerName.contains("Salon", ignoreCase = true) ||
                                    cleanOwnerName.contains("Studio", ignoreCase = true)
                                ) {
                                    cleanOwnerName
                                } else {
                                    "$cleanOwnerName's Grooming Studio"
                                }

                                val newSalon = Salon(
                                    id = targetSalonId,
                                    name = defaultShopName,
                                    location = "Kankarbagh Main Road, Patna",
                                    distance = "0.4 km",
                                    rating = 4.9,
                                    reviewCount = 1,
                                    partnerId = uid,
                                    ownerId = uid,
                                    phone = profile.phone.ifBlank { "+91 98765 43210" },
                                    isOpen = true,
                                    currentStatus = "FREE",
                                    availableChairs = 2,
                                    waitingTimeMinutes = 0,
                                    tagline = "Instant grooming slots • Zero queue wait time",
                                    nextSlotMinutes = 15,
                                    shopAddress = "Kankarbagh Main Road, Near Central Park, Patna",
                                    landmark = "Near Central Park",
                                    latitude = 25.5941,
                                    longitude = 85.1376,
                                    services = listOf(
                                        SalonService(
                                            id = "${targetSalonId}_s1",
                                            name = "Haircut",
                                            category = "Haircut",
                                            price = 120,
                                            durationMinutes = 20,
                                            description = "Precision scissor & clipper cut with styling",
                                            isPopular = true
                                        ),
                                        SalonService(
                                            id = "${targetSalonId}_s2",
                                            name = "Beard Trim & Shave",
                                            category = "Beard",
                                            price = 80,
                                            durationMinutes = 15,
                                            description = "Clean foam shave & razor edge lineup",
                                            isPopular = true
                                        ),
                                        SalonService(
                                            id = "${targetSalonId}_s3",
                                            name = "Facial & Detan",
                                            category = "Facial",
                                            price = 250,
                                            durationMinutes = 25,
                                            description = "Deep cleanse & charcoal glow facial",
                                            isPopular = false
                                        ),
                                        SalonService(
                                            id = "${targetSalonId}_s4",
                                            name = "Head Massage",
                                            category = "Massage",
                                            price = 100,
                                            durationMinutes = 15,
                                            description = "Relaxing herbal oil head massage",
                                            isPopular = false
                                        )
                                    )
                                )

                                // Update local state immediately and write to Firestore
                                _salons.value = listOf(newSalon) + _salons.value.filterNot { it.id == targetSalonId }
                                val updatedProfile = profile.copy(role = "partner", salonId = targetSalonId)
                                _currentUserProfile.value = updatedProfile

                                db.collection("salons").document(targetSalonId).set(newSalon.toMap())
                                    .addOnSuccessListener {
                                        Log.d(tag, "Created new live Partner salon in Firestore: $targetSalonId")
                                        db.collection("users").document(uid).set(
                                            mapOf("role" to "partner", "salonId" to targetSalonId),
                                            com.google.firebase.firestore.SetOptions.merge()
                                        )
                                        onSalonReady(targetSalonId)
                                    }
                                    .addOnFailureListener { err ->
                                        Log.e(tag, "Failed to create Partner salon in Firestore: ${err.message}")
                                        onSalonReady(targetSalonId)
                                    }
                            }
                        }
                }
            }
            .addOnFailureListener { err ->
                Log.w(tag, "Error querying Partner salon by ownerId: ${err.message}")
            }
    }

    private fun fetchUserProfile(uid: String) {
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
                    val savedRolePref = sessionPrefs.getString("saved_role", null)
                    val roleStr = savedRolePref ?: doc.getString("role") ?: "customer"
                    val cancelCount = doc.getLong("cancellationCount")?.toInt() ?: 0
                    val suspended = doc.getBoolean("isSuspended") ?: (cancelCount >= 3)
                    val profile = UserProfile(
                        id = uid,
                        name = doc.getString("name") ?: user?.displayName ?: "Customer",
                        email = doc.getString("email") ?: user?.email ?: "",
                        phone = doc.getString("phone") ?: user?.phoneNumber ?: "",
                        role = roleStr,
                        salonId = doc.getString("salonId"),
                        cancellationCount = cancelCount,
                        isSuspended = suspended,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    )
                    _currentUserProfile.value = profile
                    if (roleStr.equals("partner", ignoreCase = true) || roleStr.equals("owner", ignoreCase = true)) {
                        _currentRole.value = UserRole.PARTNER
                        _authState.value = AppAuthState.Authenticated(UserRole.PARTNER)
                        ensurePartnerSalonInFirestore(profile)
                    } else {
                        _currentRole.value = UserRole.CUSTOMER
                        _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                    }
                } else {
                    // Document doesn't exist yet: write to Firestore
                    val newProfile = UserProfile(
                        id = uid,
                        name = user?.displayName ?: "Customer",
                        email = user?.email ?: "",
                        phone = user?.phoneNumber ?: "",
                        role = "customer",
                        salonId = null,
                        cancellationCount = 0,
                        isSuspended = false,
                        createdAt = System.currentTimeMillis()
                    )
                    firestoreDb.collection("users").document(uid).set(newProfile.toMap())
                        .addOnSuccessListener {
                            _currentUserProfile.value = newProfile
                            _currentRole.value = UserRole.CUSTOMER
                            _authState.value = AppAuthState.Authenticated(UserRole.CUSTOMER)
                        }
                        .addOnFailureListener { err ->
                            Log.e(tag, "Failed to create initial user profile: ${err.message}")
                            _currentUserProfile.value = newProfile
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
        // Rule 3: Enforce Strike / Auto-Block check
        val currentProfile = _currentUserProfile.value
        if (currentProfile != null && (currentProfile.cancellationCount >= 3 || currentProfile.isSuspended)) {
            throw IllegalStateException("Your account has been suspended for too many cancellations")
        }

        // Rule 2: Enforce One Active Booking Rule
        val hasActivePending = _bookings.value.any { b ->
            (b.status.equals("pending", ignoreCase = true) || b.status.equals("accepted", ignoreCase = true)) && (
                b.customerId == customerId ||
                (customerPhone.isNotBlank() && b.customerPhone == customerPhone)
            )
        }
        if (hasActivePending) {
            throw IllegalStateException("You already have an active booking. Please complete or cancel it first.")
        }

        val generatedPin = String.format("%04d", Random.nextInt(1000, 9999))
        val newBookingId = "bk_" + System.currentTimeMillis().toString().takeLast(6) + "_" + Random.nextInt(10, 99)

        val newBooking = Booking(
            id = newBookingId,
            customerId = customerId,
            customerName = customerName,
            customerPhone = customerPhone,
            salonId = salonId,
            salonName = salonName,
            service = service,
            servicePrice = servicePrice,
            timeSlot = timeSlot,
            bookingDate = bookingDate,
            status = BookingStatus.PENDING.name.lowercase(),
            pin = generatedPin,
            createdAt = System.currentTimeMillis()
        )

        // Add to local state immediately
        _bookings.value = listOf(newBooking) + _bookings.value

        // Write directly to Firestore 'bookings' collection
        firestore?.let { db ->
            try {
                db.collection("bookings").document(newBooking.id).set(newBooking.toMap())
                    .addOnSuccessListener {
                        Log.d(tag, "Live booking synced to Firestore: ${newBooking.id}")
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

        firestore?.collection("bookings")?.document(bookingId)?.update("status", "rejected")
            ?.addOnSuccessListener {
                Log.d(tag, "Booking $bookingId rejected in Firestore.")
            }
            ?.addOnFailureListener { e ->
                Log.w(tag, "Failed to reject booking in Firestore: ${e.message}")
            }
        return true
    }

    fun verifyAndCompleteBooking(bookingId: String, enteredPin: String): PinVerificationResult {
        val currentBookings = _bookings.value
        val booking = currentBookings.find { it.id == bookingId }
            ?: return PinVerificationResult.BookingNotFound("Booking not found.")

        if (booking.status.equals("completed", ignoreCase = true)) {
            return PinVerificationResult.AlreadyCompleted("This booking has already been verified and completed.")
        }

        val trimmedEnteredPin = enteredPin.trim()
        val actualPin = booking.pin.trim()

        if (trimmedEnteredPin != actualPin) {
            return PinVerificationResult.IncorrectPin(
                "Invalid PIN ($trimmedEnteredPin). Anti-fraud verification failed. Please ask the customer for their 4-digit TrimTime Service PIN."
            )
        }

        // Correct PIN entered! Mark as completed
        val completedBooking = booking.copy(
            status = BookingStatus.COMPLETED.name.lowercase(),
            completedAt = System.currentTimeMillis()
        )

        _bookings.value = currentBookings.map { if (it.id == bookingId) completedBooking else it }

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
     * Rule 3: Strike / Auto-Block System
     * Increment cancellationCount. If reaches 3, block customer from future bookings.
     */
    fun cancelBooking(bookingId: String): Boolean {
        val currentBookings = _bookings.value
        val booking = currentBookings.find { it.id == bookingId } ?: return false

        val cancelledBooking = booking.copy(status = BookingStatus.CANCELLED.name.lowercase())
        _bookings.value = currentBookings.map { if (it.id == bookingId) cancelledBooking else it }

        firestore?.collection("bookings")?.document(bookingId)?.update("status", "cancelled")

        // Increment cancellationCount on user profile
        val currentProfile = _currentUserProfile.value
        if (currentProfile != null) {
            val newCancellationCount = currentProfile.cancellationCount + 1
            val isNowSuspended = newCancellationCount >= 3
            val updatedProfile = currentProfile.copy(
                cancellationCount = newCancellationCount,
                isSuspended = isNowSuspended
            )
            _currentUserProfile.value = updatedProfile

            firestore?.collection("users")?.document(currentProfile.id)?.update(
                mapOf(
                    "cancellationCount" to newCancellationCount,
                    "isSuspended" to isNowSuspended
                )
            )?.addOnSuccessListener {
                Log.d(tag, "Incremented cancellation strikes to $newCancellationCount for user ${currentProfile.id}. Suspended: $isNowSuspended")
            }
        }

        return true
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
                mapOf("isOpen" to updatedSalon.isOpen),
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
            mapOf("isOpen" to isOpen),
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
            ),
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
                    availableChairs = availableChairs.coerceAtLeast(0),
                    waitingTimeMinutes = waitingTimeMinutes.coerceAtLeast(0),
                    nextSlotMinutes = nextSlot
                )
            } else it
        }
        _salons.value = updated

        firestore?.collection("salons")?.document(salonId)?.set(
            mapOf(
                "currentStatus" to normalizedStatus,
                "availableChairs" to availableChairs.coerceAtLeast(0),
                "waitingTimeMinutes" to waitingTimeMinutes.coerceAtLeast(0),
                "nextSlotMinutes" to nextSlot
            ),
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
        val currentSalons = _salons.value
        val updated = currentSalons.map {
            if (it.id == salonId) it.copy(services = updatedServices) else it
        }
        _salons.value = updated

        val servicesMap = updatedServices.map { it.toMap() }
        firestore?.collection("salons")?.document(salonId)
            ?.set(mapOf("services" to servicesMap), com.google.firebase.firestore.SetOptions.merge())
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
        onSuccess: () -> Unit = {}
    ) {
        val currentSalon = _salons.value.find { it.id == salonId } ?: return
        val newServiceId = "${salonId}_srv_${System.currentTimeMillis().toString().takeLast(5)}"
        val newService = SalonService(
            id = newServiceId,
            name = name.trim(),
            category = category.trim().ifBlank { "Haircut" },
            price = price.coerceAtLeast(10),
            durationMinutes = durationMinutes.coerceAtLeast(5),
            description = description.trim().ifBlank { "$name ($durationMinutes mins)" },
            isPopular = true
        )
        val updatedList = currentSalon.services + newService
        updateSalonServices(salonId = salonId, updatedServices = updatedList, onSuccess = onSuccess)
    }

    /**
     * Real-Time Edit Existing Service in Partner's Salon in Firestore (`salons/{salonId}`):
     */
    fun editSalonService(
        salonId: String,
        updatedService: SalonService,
        onSuccess: () -> Unit = {}
    ) {
        val currentSalon = _salons.value.find { it.id == salonId } ?: return
        val updatedList = currentSalon.services.map {
            if (it.id == updatedService.id) updatedService else it
        }
        updateSalonServices(salonId = salonId, updatedServices = updatedList, onSuccess = onSuccess)
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
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val calculatedDistKm = calculateDistanceKm(25.5941, 85.1376, latitude, longitude)
        val dynamicDistance = String.format(java.util.Locale.US, "%.1f km", calculatedDistKm)
        val shortLocation = if (landmark.isNotBlank()) "$landmark, $shopAddress".take(40) else shopAddress.take(40)
        val cleanName = name.trim().ifBlank { "TrimTime Partner Salon" }
        val cleanPhone = phone.trim()
        val cleanTagline = tagline.trim().ifBlank { "Instant grooming slots • Zero queue wait time" }

        val currentSalons = _salons.value
        val updated = currentSalons.map {
            if (it.id == salonId) {
                it.copy(
                    name = cleanName,
                    phone = cleanPhone,
                    tagline = cleanTagline,
                    shopAddress = shopAddress,
                    landmark = landmark,
                    latitude = latitude,
                    longitude = longitude,
                    distance = dynamicDistance,
                    location = shortLocation.ifBlank { it.location }
                )
            } else it
        }
        _salons.value = updated

        val coordinatesMap = mapOf(
            "lat" to latitude,
            "lng" to longitude
        )

        val updates = mapOf(
            "name" to cleanName,
            "phone" to cleanPhone,
            "tagline" to cleanTagline,
            "shopAddress" to shopAddress,
            "landmark" to landmark,
            "coordinates" to coordinatesMap,
            "latitude" to latitude,
            "longitude" to longitude,
            "distance" to dynamicDistance,
            "location" to shortLocation
        )

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

    companion object {
        @Volatile
        private var INSTANCE: TrimTimeRepository? = null

        fun getInstance(context: Context): TrimTimeRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TrimTimeRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun getProductionSeedSalons(): List<Salon> = listOf(
            Salon(
                id = "salon_blade_comb",
                name = "The Blade & Comb Studio",
                location = "27th Main, Sector 1, HSR Layout",
                distance = "0.3 km",
                rating = 4.9,
                reviewCount = 210,
                partnerId = "partner_blade_comb",
                isOpen = true,
                tagline = "Instant grooming slots • Zero queue wait time",
                nextSlotMinutes = 12,
                services = listOf(
                    SalonService(
                        id = "srv_101",
                        name = "Classic Executive Haircut",
                        category = "Haircut",
                        price = 120,
                        durationMinutes = 20,
                        description = "Precision scissor & clipper cut, neck shave, styling pomade",
                        isPopular = true
                    ),
                    SalonService(
                        id = "srv_102",
                        name = "Royal Beard Sculpt & Hot Towel",
                        category = "Beard",
                        price = 80,
                        durationMinutes = 15,
                        description = "Hot towel steam, straight razor edge lineup, conditioning oil",
                        isPopular = true
                    ),
                    SalonService(
                        id = "srv_103",
                        name = "Executive Cut + Beard Combo",
                        category = "Combo",
                        price = 180,
                        durationMinutes = 30,
                        description = "Complete grooming makeover with head wash & styling",
                        isPopular = true
                    ),
                    SalonService(
                        id = "srv_104",
                        name = "Quick 10-Min Head Massage",
                        category = "Massage",
                        price = 70,
                        durationMinutes = 10,
                        description = "Almond oil acupressure relaxation massage"
                    ),
                    SalonService(
                        id = "srv_105",
                        name = "Charcoal Detan Face Pack",
                        category = "Facial",
                        price = 150,
                        durationMinutes = 20,
                        description = "Instant pollution removal, skin brightener, cooling toner"
                    )
                )
            ),
            Salon(
                id = "salon_urban_cuts",
                name = "Urban Cuts Barber Lounge",
                location = "14th Main, 5th Cross, HSR Sector 2",
                distance = "0.7 km",
                rating = 4.7,
                reviewCount = 145,
                partnerId = "partner_urban",
                isOpen = true,
                tagline = "Modern fades, textured crops & zero-queue appointments",
                nextSlotMinutes = 15,
                services = listOf(
                    SalonService(
                        id = "srv_201",
                        name = "Skin Fade / Taper Cut",
                        category = "Haircut",
                        price = 150,
                        durationMinutes = 25,
                        description = "Seamless zero-fade with textured fringe styling",
                        isPopular = true
                    ),
                    SalonService(
                        id = "srv_202",
                        name = "Clean Charcoal Razor Shave",
                        category = "Beard",
                        price = 60,
                        durationMinutes = 12,
                        description = "Smooth single-blade shave with aftershave balm",
                        isPopular = true
                    ),
                    SalonService(
                        id = "srv_203",
                        name = "Beard Color Touchup (Ammonia-Free)",
                        category = "Beard",
                        price = 100,
                        durationMinutes = 15,
                        description = "Natural dark brown / black root blend"
                    )
                )
            ),
            Salon(
                id = "salon_velvet_shears",
                name = "Velvet & Shears Luxury Unisex",
                location = "Outer Ring Road, Near Bellandur Flyover",
                distance = "1.2 km",
                rating = 4.8,
                reviewCount = 340,
                partnerId = "partner_velvet",
                isOpen = true,
                tagline = "Premium styling & spa with sanitization certified chairs",
                nextSlotMinutes = 25,
                services = listOf(
                    SalonService(
                        id = "srv_301",
                        name = "Senior Stylist Hair Makeover",
                        category = "Haircut",
                        price = 250,
                        durationMinutes = 35,
                        description = "Face shape consultation, wash, precision layer cut & blowdry",
                        isPopular = true
                    ),
                    SalonService(
                        id = "srv_302",
                        name = "Beard Styling & Line Architecture",
                        category = "Beard",
                        price = 120,
                        durationMinutes = 20,
                        description = "Cheek & neck laser-like contouring with beard wax"
                    ),
                    SalonService(
                        id = "srv_303",
                        name = "Deep Glow Face Cleanse",
                        category = "Facial",
                        price = 350,
                        durationMinutes = 30,
                        description = "Herbal fruit scrub, ultrasonic cleansing, glow mask",
                        isPopular = true
                    )
                )
            ),
            Salon(
                id = "salon_express_trim",
                name = "Express Groomers (Quick Slot)",
                location = "Near BDA Complex, HSR 6th Sector",
                distance = "0.5 km",
                rating = 4.6,
                reviewCount = 98,
                partnerId = "partner_express",
                isOpen = true,
                tagline = "Pocket-friendly fast cuts • In and out in 15 minutes",
                nextSlotMinutes = 10,
                services = listOf(
                    SalonService(
                        id = "srv_401",
                        name = "Fast Clipper Trim",
                        category = "Haircut",
                        price = 90,
                        durationMinutes = 15,
                        description = "Quick clean clipper cut and neck trim",
                        isPopular = true
                    ),
                    SalonService(
                        id = "srv_402",
                        name = "Express Shave",
                        category = "Beard",
                        price = 50,
                        durationMinutes = 10,
                        description = "Clean foam shave & menthol splash",
                        isPopular = true
                    ),
                    SalonService(
                        id = "srv_403",
                        name = "Moustache & Beard Shape",
                        category = "Beard",
                        price = 60,
                        durationMinutes = 10,
                        description = "Quick trim with trimmer number 1 to 4"
                    )
                )
            )
        )

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
