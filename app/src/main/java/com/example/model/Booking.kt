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
    val allocatedTime: String = "",
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
        get() = allocatedTime.ifBlank { time }.ifBlank { timeSlot }

    /**
     * Normalizes raw Firestore status strings into one of the four canonical display statuses:
     * "Pending", "Confirmed", "Completed", or "Cancelled".
     */
    val normalizedStatus: String
        get() = normalizeStatusValue(status)

    /**
     * Returns true if this booking is still 'Pending' and more than 5 minutes have elapsed
     * without the Partner responding.
     */
    fun isPendingTimedOut(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return status.trim().equals("pending", ignoreCase = true) &&
            createdAt > 0L &&
            (nowMillis - createdAt) >= PENDING_TIMEOUT_MS
    }

    /**
     * Remaining seconds before this 'Pending' booking is auto-cancelled (5-minute window).
     */
    fun remainingPendingSeconds(nowMillis: Long = System.currentTimeMillis()): Int {
        if (!status.trim().equals("pending", ignoreCase = true) || createdAt <= 0L) return 0
        val elapsed = (nowMillis - createdAt).coerceAtLeast(0L)
        val remainingMs = (PENDING_TIMEOUT_MS - elapsed).coerceAtLeast(0L)
        return ((remainingMs + 999L) / 1000L).toInt()
    }

    fun toMap(): Map<String, Any?> {
        val finalId = resolvedBookingId
        val finalUserId = resolvedUserId
        val finalPartnerId = resolvedPartnerId
        val finalDate = resolvedDate
        val finalTime = resolvedTime
        val finalAllocated = allocatedTime.ifBlank { finalTime }
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
            "allocatedTime" to finalAllocated,
            "status" to status,
            "pin" to pin,
            "servicePin" to pin,
            "createdAt" to createdAt,
            "completedAt" to completedAt
        )
    }

    companion object {
        /**
         * Automatic Local Rush Detection threshold:
         * `isLocalRushMode = true` IF a salon's current active/pending 'Live Queue' count is >= 3.
         */
        const val LOCAL_RUSH_QUEUE_THRESHOLD = 3

        /**
         * 5-minute timeout (300,000 ms) for 'Pending' bookings if the Partner does not respond.
         */
        const val PENDING_TIMEOUT_MS = 5 * 60 * 1000L

        fun isLocalRushMode(activeBookingsCount: Int): Boolean {
            return activeBookingsCount >= LOCAL_RUSH_QUEUE_THRESHOLD
        }

        fun isRushModeActive(
            isGlobalRushModeEnabled: Boolean,
            activeBookingsCount: Int
        ): Boolean {
            return isGlobalRushModeEnabled || isLocalRushMode(activeBookingsCount)
        }

        fun normalizeStatusValue(rawStatus: String?): String {
            return when (rawStatus?.trim()?.lowercase()) {
                "pending" -> "Pending"
                "confirmed", "accepted", "in_progress" -> "Confirmed"
                "completed" -> "Completed"
                "cancelled", "canceled", "rejected" -> "Cancelled"
                else -> "Pending"
            }
        }

        /**
         * Calculates the estimated wait time in minutes based on the Partner's current active Live Queue
         * (`activeBookingsCount * defaultServiceDurationMinutes`).
         */
        fun calculateSystemWaitMinutes(
            activeBookingsCount: Int,
            defaultServiceDurationMinutes: Int = 20
        ): Int {
            val safeDuration = defaultServiceDurationMinutes.coerceIn(10, 120)
            val safeQueue = activeBookingsCount.coerceAtLeast(0)
            return safeDuration * safeQueue
        }

        /**
         * Calculates the estimated arrival epoch timestamp in milliseconds.
         */
        fun calculateEtaArrivalMillis(
            activeBookingsCount: Int,
            defaultServiceDurationMinutes: Int = 20,
            extraOffsetMinutes: Int = 0,
            baseTimeMillis: Long = System.currentTimeMillis()
        ): Long {
            val queueWaitMins = calculateSystemWaitMinutes(activeBookingsCount, defaultServiceDurationMinutes)
            val totalMinutesFromNow = (queueWaitMins + extraOffsetMinutes).coerceAtLeast(0)
            return baseTimeMillis + (totalMinutesFromNow * 60_000L)
        }

        /**
         * Calculates the formatted clock arrival time (e.g., "04:35 PM") by adding the system queue wait
         * (`activeBookingsCount * defaultServiceDurationMinutes`) plus any Partner manual override (`extraOffsetMinutes`).
         */
        fun calculateFormattedEtaClockTime(
            activeBookingsCount: Int,
            defaultServiceDurationMinutes: Int = 20,
            extraOffsetMinutes: Int = 0,
            baseTimeMillis: Long = System.currentTimeMillis()
        ): String {
            val arrivalMillis = calculateEtaArrivalMillis(
                activeBookingsCount = activeBookingsCount,
                defaultServiceDurationMinutes = defaultServiceDurationMinutes,
                extraOffsetMinutes = extraOffsetMinutes,
                baseTimeMillis = baseTimeMillis
            )
            val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
            return formatter.format(java.util.Date(arrivalMillis))
        }

        /**
         * Parses a salon's official closing time string (e.g. "9:00 PM", "09:30 PM", "21:00")
         * into today's epoch timestamp in milliseconds.
         */
        fun parseSalonClosingTimeMillisToday(
            closingTimeStr: String?,
            referenceMillis: Long = System.currentTimeMillis()
        ): Long? {
            val clean = closingTimeStr?.trim()?.uppercase(java.util.Locale.US).orEmpty()
            if (clean.isBlank()) return null

            val patterns = listOf("hh:mm a", "h:mm a", "hh a", "h a", "HH:mm", "H:mm")
            var parsedTime: java.util.Date? = null
            for (pattern in patterns) {
                try {
                    val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                    sdf.isLenient = false
                    parsedTime = sdf.parse(clean)
                    if (parsedTime != null) break
                } catch (_: Exception) {
                }
            }
            if (parsedTime == null) return null

            val parsedCal = java.util.Calendar.getInstance().apply { time = parsedTime }
            val todayCal = java.util.Calendar.getInstance().apply {
                timeInMillis = referenceMillis
                set(java.util.Calendar.HOUR_OF_DAY, parsedCal.get(java.util.Calendar.HOUR_OF_DAY))
                set(java.util.Calendar.MINUTE, parsedCal.get(java.util.Calendar.MINUTE))
                set(java.util.Calendar.SECOND, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }
            return todayCal.timeInMillis
        }

        /**
         * Returns true if the newly calculated ETA exceeds the salon's official closing time.
         */
        fun isEtaExceedingClosingTime(
            closingTimeStr: String?,
            activeBookingsCount: Int,
            defaultServiceDurationMinutes: Int = 20,
            extraOffsetMinutes: Int = 0,
            baseTimeMillis: Long = System.currentTimeMillis()
        ): Boolean {
            val closingMillis = parseSalonClosingTimeMillisToday(closingTimeStr, baseTimeMillis) ?: return false
            val etaArrivalMillis = calculateEtaArrivalMillis(
                activeBookingsCount = activeBookingsCount,
                defaultServiceDurationMinutes = defaultServiceDurationMinutes,
                extraOffsetMinutes = extraOffsetMinutes,
                baseTimeMillis = baseTimeMillis
            )
            return etaArrivalMillis > closingMillis
        }
    }
}
