package com.example.model

enum class UserRole(val roleKey: String, val title: String) {
    CUSTOMER("customer", "Customer"),
    PARTNER("partner", "Partner")
}

enum class AppThemeMode(val key: String, val label: String) {
    DARK("dark", "Dark Mode"),
    LIGHT("light", "Light Mode"),
    SYSTEM("system", "System Default");

    companion object {
        fun fromKey(key: String?): AppThemeMode =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: DARK
    }
}

enum class AppLanguage(val key: String, val label: String) {
    ENGLISH("en", "English"),
    HINDI_HINGLISH("hi", "Hindi / Hinglish");

    companion object {
        fun fromKey(key: String?): AppLanguage =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: ENGLISH
    }
}

data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val bookingStatusAlerts: Boolean = true,
    val queueTurnReminders: Boolean = true,
    val salonOffersDiscounts: Boolean = false,
    val defaultCity: String = "Current Location",
    val showDemoSalons: Boolean = false
)

sealed class AppAuthState {
    data object Loading : AppAuthState()
    data object Unauthenticated : AppAuthState()
    data class Authenticated(val role: UserRole) : AppAuthState()
}

data class UserProfile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val phoneNumber: String = phone,
    val isPhoneVerified: Boolean = false,
    val role: String = "customer", // "customer", "partner", or "admin"
    val isAdmin: Boolean = false,
    val salonId: String? = null,
    val salonCategory: String = "Standard", // "Premium", "Standard", or "Rural/Local"
    val salonStatus: String = "approved", // "approved", "rejected", "suspended"
    val averageRating: Double = 0.0,
    val totalRatings: Int = 0,
    val isAcSalon: Boolean = false,
    val imageUrl: String = "",
    val hasRegisteredSalon: Boolean = false,
    val defaultCity: String = "Current Location",
    val cancellationCount: Int = 0,
    val cancellationStrikes: Int = cancellationCount,
    val isSuspended: Boolean = false,
    val isBlocked: Boolean = false,
    val isLocationLocked: Boolean = true,
    val unlockRequestStatus: String = "none", // "none", "pending", "approved"
    val favoriteSalons: List<String> = emptyList(),
    val services: List<SalonService> = emptyList(),
    val fcmToken: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val isWhitelistedAdminOrDev: Boolean
        get() = isAdmin ||
            role.trim().equals("admin", ignoreCase = true) ||
            role.trim().equals("superadmin", ignoreCase = true) ||
            email.trim().equals("kushwahapankajkumar00@gmail.com", ignoreCase = true)

    val effectiveCancellationStrikes: Int
        get() = if (isWhitelistedAdminOrDev) 0 else maxOf(cancellationCount, cancellationStrikes)

    /**
     * Admin (`role: "admin"` / `"superadmin"` / `isAdmin == true`) and Developer accounts are completely immune
     * to suspension. Automatic 3-strike suspension is also disabled globally so queue testing is never blocked.
     */
    val isEffectivelyBlocked: Boolean
        get() = if (isWhitelistedAdminOrDev) false else isBlocked

    fun toMap(): Map<String, Any?> = buildMap {
        val resolvedPhone = phoneNumber.ifBlank { phone }
        put("id", id)
        put("name", name)
        put("email", email)
        put("phone", resolvedPhone)
        put("phoneNumber", resolvedPhone)
        put("isPhoneVerified", isPhoneVerified)
        put("role", role)
        if (isAdmin || isWhitelistedAdminOrDev) {
            put("isAdmin", true)
        }
        put("salonId", salonId)
        put("salonCategory", salonCategory)
        put("salonStatus", salonStatus)
        put("averageRating", averageRating)
        put("totalRatings", totalRatings)
        put("isAcSalon", isAcSalon)
        put("imageUrl", imageUrl)
        put("hasRegisteredSalon", hasRegisteredSalon)
        put("defaultCity", defaultCity)
        put("cancellationCount", effectiveCancellationStrikes)
        put("cancellationStrikes", effectiveCancellationStrikes)
        put("isSuspended", isEffectivelyBlocked)
        put("isBlocked", if (isWhitelistedAdminOrDev) false else isBlocked)
        put("isLocationLocked", isLocationLocked)
        put("unlockRequestStatus", unlockRequestStatus)
        put("favoriteSalons", favoriteSalons)
        if (services.isNotEmpty()) {
            put("services", services.map { it.toMap() })
        }
        if (fcmToken.isNotBlank()) {
            put("fcmToken", fcmToken)
        }
        put("createdAt", createdAt)
    }
}

data class SalonService(
    val id: String = "",
    val name: String = "",
    val serviceName: String = name,
    val category: String = "Haircut",
    val price: Int = 100,
    val durationMinutes: Int = 20,
    val duration: Int = durationMinutes,
    val description: String = "",
    val isPopular: Boolean = false
) {
    fun toMap(): Map<String, Any> {
        val resolvedName = name.ifBlank { serviceName }
        val resolvedDuration = if (durationMinutes > 0) durationMinutes else duration
        return mapOf(
            "id" to id,
            "name" to resolvedName,
            "serviceName" to serviceName.ifBlank { resolvedName },
            "category" to category,
            "price" to price,
            "duration" to resolvedDuration,
            "durationMinutes" to resolvedDuration,
            "description" to description,
            "isPopular" to isPopular
        )
    }
}

data class Salon(
    val id: String = "",
    val name: String = "",
    val location: String = "",
    val distance: String = "0.5 km",
    val rating: Double = 4.8,
    val reviewCount: Int = 0,
    val averageRating: Double = 0.0,
    val totalRatings: Int = 0,
    val isAcSalon: Boolean = false,
    val partnerId: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val imageUrl: String = "",
    val photoBase64: String = "",
    val services: List<SalonService> = emptyList(),
    val isOpen: Boolean = true,
    val currentStatus: String = "FREE", // "FREE" or "BUSY"
    val availableChairs: Int = 2,
    val waitingTimeMinutes: Int = 0,
    val tagline: String = "",
    val nextSlotMinutes: Int = 15,
    val shopAddress: String = "",
    val landmark: String = "",
    val pincode: String = "",
    val shopCategory: String = "Men's Salon",
    val adminCategoryTag: String = "Standard", // "Premium", "Standard", or "Rural/Local"
    val salonCategory: String = adminCategoryTag,
    val approvalStatus: String = "APPROVED", // "PENDING", "APPROVED", "REJECTED", "SUSPENDED"
    val isSuspended: Boolean = false,
    val salonStatus: String = when {
        isSuspended -> "suspended"
        approvalStatus.equals("REJECTED", ignoreCase = true) -> "rejected"
        else -> "approved"
    },
    val openingTime: String = "9:00 AM",
    val closingTime: String = "9:00 PM",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isRegistrationCompleted: Boolean = false,
    val registrationFormSubmitted: Boolean = false,
    val isPhoneVerified: Boolean = true,
    val isLocationLocked: Boolean = true,
    val unlockRequestStatus: String = "none", // "none", "pending", "approved"
    val fcmToken: String = ""
) {
    val isAcceptingBookings: Boolean
        get() = isOpen &&
            !currentStatus.equals("CLOSED", ignoreCase = true) &&
            !salonStatus.equals("closed", ignoreCase = true) &&
            !isSuspended &&
            !approvalStatus.equals("REJECTED", ignoreCase = true)

    val isBusy: Boolean
        get() = isOpen && currentStatus.equals("BUSY", ignoreCase = true)

    val isFree: Boolean
        get() = isOpen && !isBusy

    val resolvedPhotoData: String
        get() = imageUrl.ifBlank { photoBase64 }

    val hasLocationSetup: Boolean
        get() = (shopAddress.isNotBlank() || location.isNotBlank()) &&
            (latitude != null && longitude != null && latitude != 0.0 && longitude != 0.0)

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "location" to location,
        "distance" to distance,
        "rating" to rating,
        "reviewCount" to reviewCount,
        "averageRating" to averageRating,
        "totalRatings" to totalRatings,
        "isAcSalon" to isAcSalon,
        "partnerId" to partnerId.ifBlank { ownerId },
        "ownerId" to ownerId.ifBlank { partnerId },
        "ownerName" to ownerName,
        "phone" to phone,
        "imageUrl" to imageUrl.ifBlank { photoBase64 },
        "photoBase64" to photoBase64.ifBlank { imageUrl },
        "services" to services.map { it.toMap() },
        "isOpen" to isOpen,
        "currentStatus" to currentStatus,
        "availableChairs" to availableChairs,
        "waitingTimeMinutes" to waitingTimeMinutes,
        "tagline" to tagline,
        "nextSlotMinutes" to nextSlotMinutes,
        "shopAddress" to shopAddress,
        "landmark" to landmark,
        "pincode" to pincode,
        "shopCategory" to shopCategory,
        "adminCategoryTag" to adminCategoryTag,
        "salonCategory" to salonCategory.ifBlank { adminCategoryTag },
        "approvalStatus" to approvalStatus,
        "salonStatus" to salonStatus,
        "isSuspended" to isSuspended,
        "openingTime" to openingTime,
        "closingTime" to closingTime,
        "latitude" to latitude,
        "longitude" to longitude,
        "isRegistrationCompleted" to isRegistrationCompleted,
        "registrationFormSubmitted" to registrationFormSubmitted,
        "isPhoneVerified" to isPhoneVerified,
        "isLocationLocked" to isLocationLocked,
        "unlockRequestStatus" to unlockRequestStatus,
        "fcmToken" to fcmToken,
        "coordinates" to if (latitude != null && longitude != null) mapOf(
            "lat" to latitude,
            "lng" to longitude
        ) else null
    )

    val effectiveRating: Double
        get() = if (averageRating > 0.0) averageRating else rating
}

data class FeedbackSubmission(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val userRole: String = "customer",
    val rating: Int = 5,
    val category: String = "Bug Report",
    val message: String = "",
    val status: String = "OPEN", // "OPEN" or "RESOLVED"
    val isResolved: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "userId" to userId,
        "userName" to userName,
        "userPhone" to userPhone,
        "userRole" to userRole,
        "rating" to rating,
        "category" to category,
        "message" to message,
        "status" to status,
        "isResolved" to isResolved,
        "timestamp" to timestamp,
        "resolvedAt" to resolvedAt
    )
}

