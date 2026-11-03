package com.example.model

enum class BookingStatus(val displayName: String) {
    PENDING("Pending"),
    CONFIRMED("Confirmed"),
    ACCEPTED("Confirmed"),
    IN_PROGRESS("Confirmed"),
    COMPLETED("Completed"),
    REJECTED("Cancelled"),
    CANCELLED("Cancelled")
}

/**
 * Comprehensive Booking Data Model for Firestore `bookings` collection.
 * Includes standard fields: `bookingId`, `userId`, `partnerId`, `salonName`, `date`, `time`,
 * and `status` ("Pending", "Confirmed", "Completed", "Cancelled"), while maintaining full
 * compatibility with all salon queue & PIN verification fields.
 */
data class Booking(
    val bookingId: String = "",
    val userId: String = "",
    val partnerId: String = "",
    val salonName: String = "",
    val date: String = "Today",
    val time: String = "",
    val status: String = "Pending", // "Pending", "Confirmed", "Completed", "Cancelled"
    val id: String = bookingId,
    val customerId: String = userId,
    val customerName: String = "",
    val customerPhone: String = "",
    val salonId: String = partnerId,
    val service: String = "",
    val servicePrice: Int = 100,
    val timeSlot: String = time,
    val bookingDate: String = date,
    val pin: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val servicePin: String
        get() = pin

    val resolvedBookingId: String
        get() = bookingId.ifBlank { id }

    val resolvedUserId: String
        get() = userId.ifBlank { customerId }

    val resolvedPartnerId: String
        get() = partnerId.ifBlank { salonId }

    val resolvedDate: String
        get() = date.ifBlank { bookingDate }.ifBlank { "Today" }

    val resolvedTime: String
        get() = time.ifBlank { timeSlot }

    /**
     * Normalizes raw Firestore status strings into one of the four canonical display statuses:
     * "Pending", "Confirmed", "Completed", or "Cancelled".
     */
    val normalizedStatus: String
        get() = normalizeStatusValue(status)

    fun toMap(): Map<String, Any?> {
        val finalId = resolvedBookingId
        val finalUserId = resolvedUserId
        val finalPartnerId = resolvedPartnerId
        val finalDate = resolvedDate
        val finalTime = resolvedTime
        return mapOf(
            "bookingId" to finalId,
            "id" to finalId,
            "userId" to finalUserId,
            "customerId" to finalUserId,
            " customerName" to customerName,
            "customerName" to customerName,
            "customerPhone" to customerPhone,
            "partnerId" to finalPartnerId,
            "salonId" to finalPartnerId,
            "salonName" to salonName,
            "service" to service,
            "servicePrice" to servicePrice,
            "date" to finalDate,
            "bookingDate" to finalDate,
            "time" to finalTime,
            "timeSlot" to finalTime,
            "status" to status,
            "pin" to pin,
            "servicePin" to pin,
            "createdAt" to createdAt,
            "completedAt" to completedAt
        )
    }

    companion object {
        fun normalizeStatusValue(rawStatus: String?): String {
            return when (rawStatus?.trim()?.lowercase()) {
                "pending" -> "Pending"
                "confirmed", "accepted", "in_progress" -> "Confirmed"
                "completed" -> "Completed"
                "cancelled", "canceled", "rejected" -> "Cancelled"
                else -> "Pending"
            }
        }
    }
}
