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
    val defaultCity: String = "Patna, Bihar",
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
    val loginPin: String = "",
    val isPhoneVerified: Boolean = false,
    val role: String = "customer", // "customer" or "partner"
    val salonId: String? = null,
    val hasRegisteredSalon: Boolean = false,
    val defaultCity: String = "Patna, Bihar",
    val cancellationCount: Int = 0,
    val cancellationStrikes: Int = cancellationCount,
    val isSuspended: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val effectiveCancellationStrikes: Int
        get() = maxOf(cancellationCount, cancellationStrikes)

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "email" to email,
        "phone" to phone,
        "loginPin" to loginPin,
        "isPhoneVerified" to isPhoneVerified,
        "role" to role,
        "salonId" to salonId,
        "hasRegisteredSalon" to hasRegisteredSalon,
        "defaultCity" to defaultCity,
        "cancellationCount" to effectiveCancellationStrikes,
        "cancellationStrikes" to effectiveCancellationStrikes,
        "isSuspended" to (isSuspended || effectiveCancellationStrikes >= 3),
        "createdAt" to createdAt
    )
}

data class SalonService(
    val id: String = "",
    val name: String = "",
    val category: String = "Haircut",
    val price: Int = 100,
    val durationMinutes: Int = 20,
    val description: String = "",
    val isPopular: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "category" to category,
        "price" to price,
        "durationMinutes" to durationMinutes,
        "description" to description,
        "isPopular" to isPopular
    )
}

data class Salon(
    val id: String = "",
    val name: String = "",
    val location: String = "",
    val distance: String = "0.5 km",
    val rating: Double = 4.8,
    val reviewCount: Int = 0,
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
    val openingTime: String = "9:00 AM",
    val closingTime: String = "9:00 PM",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isRegistrationCompleted: Boolean = false,
    val isPhoneVerified: Boolean = true
) {
    val isBusy: Boolean
        get() = currentStatus.equals("BUSY", ignoreCase = true)

    val isFree: Boolean
        get() = !isBusy

    val resolvedPhotoData: String
        get() = photoBase64.ifBlank { imageUrl }

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
        "openingTime" to openingTime,
        "closingTime" to closingTime,
        "latitude" to latitude,
        "longitude" to longitude,
        "isRegistrationCompleted" to isRegistrationCompleted,
        "isPhoneVerified" to isPhoneVerified,
        "coordinates" to if (latitude != null && longitude != null) mapOf(
            "lat" to latitude,
            "lng" to longitude
        ) else null
    )
}

enum class BookingStatus(val displayName: String) {
    PENDING("Pending Arrival"),
    ACCEPTED("Accepted • Confirmed"),
    IN_PROGRESS("In Chair"),
    COMPLETED("Completed"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled")
}

data class Booking(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val salonId: String = "",
    val salonName: String = "",
    val service: String = "",
    val servicePrice: Int = 100,
    val timeSlot: String = "",
    val bookingDate: String = "Today",
    val status: String = "pending", // pending, accepted, in_progress, completed, rejected, cancelled
    val pin: String = "", // 4-digit Service PIN
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val servicePin: String
        get() = pin

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "customerId" to customerId,
        "customerName" to customerName,
        "customerPhone" to customerPhone,
        "salonId" to salonId,
        "salonName" to salonName,
        "service" to service,
        "servicePrice" to servicePrice,
        "timeSlot" to timeSlot,
        "bookingDate" to bookingDate,
        "status" to status,
        "pin" to pin,
        "servicePin" to pin,
        "createdAt" to createdAt,
        "completedAt" to completedAt
    )
}

data class FeedbackSubmission(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userRole: String = "customer",
    val rating: Int = 5,
    val category: String = "Bug Report",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "userId" to userId,
        "userName" to userName,
        "userRole" to userRole,
        "rating" to rating,
        "category" to category,
        "message" to message,
        "timestamp" to timestamp
    )
}

