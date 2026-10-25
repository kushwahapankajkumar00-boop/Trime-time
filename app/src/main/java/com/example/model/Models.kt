package com.example.model

enum class UserRole(val roleKey: String, val title: String) {
    CUSTOMER("customer", "Customer"),
    PARTNER("partner", "Partner")
}

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
    val role: String = "customer", // "customer" or "partner"
    val salonId: String? = null,
    val cancellationCount: Int = 0,
    val isSuspended: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "email" to email,
        "phone" to phone,
        "role" to role,
        "salonId" to salonId,
        "cancellationCount" to cancellationCount,
        "isSuspended" to isSuspended,
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
    val latitude: Double? = null,
    val longitude: Double? = null
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
        "latitude" to latitude,
        "longitude" to longitude,
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
