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
    val customerFcmToken: String = "",
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
     * Returns a clean, human-readable arrival time (e.g., "04:35 PM") without technical prefixes
     * or raw queue suffixes like "Live ETA:", "(~40m)", "(Reach Now)", etc.
     */
    val cleanAllocatedClockTime: String
        get() = formatCleanArrivalTime(allocatedTime.ifBlank { resolvedTime })

    /**
     * Returns true if this booking's internal slot time is at the booking creation time
     * (Wait Time = 0m, within 2 minutes of `createdAt`), meaning no queue wait or extra offset
     * was added to the salon's internal queue schedule.
     */
    fun isZeroWaitInternalSlot(referenceMillis: Long = System.currentTimeMillis()): Boolean {
        val slotMillis = getAssignedArrivalMillis(referenceMillis) ?: return false
        if (createdAt <= 0L) return false
        return kotlin.math.abs(slotMillis - createdAt) <= 2 * 60_000L
    }

    /**
     * Customer-facing arrival target time:
     * - When Wait Time is 0m (internal booking slot is logged at `createdAt`, e.g. 06:39 PM),
     *   returns `Current + 10m` (e.g. "06:49 PM") so the customer UI can say "Reach by 06:49 PM"
     *   while the backend and Partner Live Queue log the actual current time ("06:39 PM").
     * - When Wait Time > 0m or Partner allocated a custom future slot, returns `cleanAllocatedClockTime`.
     */
    val customerReachByClockTime: String
        get() {
            val now = System.currentTimeMillis()
            val slotMillis = getAssignedArrivalMillis(now)
            if (slotMillis != null && createdAt > 0L && kotlin.math.abs(slotMillis - createdAt) <= 2 * 60_000L) {
                val baseMillis = maxOf(slotMillis, createdAt)
                val bufferedMillis = baseMillis + (DEFAULT_MIN_TRAVEL_BUFFER_MINUTES * 60_000L)
                val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
                return formatter.format(java.util.Date(bufferedMillis))
            }
            return cleanAllocatedClockTime
        }

    /**
     * Normalizes raw Firestore status strings into one of the four canonical display statuses:
     * "Pending", "Confirmed", "Completed", or "Cancelled".
     */
    val normalizedStatus: String
        get() = normalizeStatusValue(status)

    /**
     * Returns the assigned internal arrival slot time as an epoch timestamp in milliseconds on the booking's day,
     * or null if no clock time can be parsed.
     */
    fun getAssignedArrivalMillis(referenceMillis: Long = System.currentTimeMillis()): Long? {
        val dayAnchor = if (createdAt > 0L && isSameCalendarDay(createdAt, referenceMillis)) {
            createdAt
        } else {
            referenceMillis
        }
        return parseClockTimeMillisOnDay(cleanAllocatedClockTime, dayAnchor)
    }

    /**
     * Returns the effective arrival deadline in milliseconds for late-arrival detection:
     * - For a 0m-wait instant booking (logged at `createdAt`, e.g. 6:39 PM), includes the
     *   10-minute customer travel buffer (`6:49 PM`) before marking the customer as late.
     * - For a queued or Partner-offset booking, uses the assigned slot time directly.
     */
    fun getEffectiveArrivalDeadlineMillis(referenceMillis: Long = System.currentTimeMillis()): Long? {
        val slotMillis = getAssignedArrivalMillis(referenceMillis) ?: return null
        return if (createdAt > 0L && kotlin.math.abs(slotMillis - createdAt) <= 2 * 60_000L) {
            maxOf(slotMillis, createdAt) + (DEFAULT_MIN_TRAVEL_BUFFER_MINUTES * 60_000L)
        } else {
            slotMillis
        }
    }

    /**
     * Returns true if this booking is active today and the current device time has surpassed
     * the effective arrival deadline (`nowMillis > effectiveArrivalDeadlineMillis`).
     */
    fun isLateArrival(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!isStatusPendingOrAccepted(status)) return false
        val dateStr = resolvedDate.trim()
        val isToday = dateStr.isBlank() ||
            dateStr.equals("Today", ignoreCase = true) ||
            isSameCalendarDay(createdAt, nowMillis)
        if (!isToday) return false
        val arrivalMillis = getEffectiveArrivalDeadlineMillis(nowMillis) ?: return false
        return nowMillis > arrivalMillis
    }

    /**
     * Returns how many minutes late the customer currently is past their arrival deadline.
     */
    fun minutesLate(nowMillis: Long = System.currentTimeMillis()): Int {
        val arrivalMillis = getEffectiveArrivalDeadlineMillis(nowMillis) ?: return 0
        if (nowMillis <= arrivalMillis) return 0
        return (((nowMillis - arrivalMillis) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    }

    /**
     * Returns remaining minutes in the 10-minute grace period after the arrival deadline.
     */
    fun remainingGraceMinutes(nowMillis: Long = System.currentTimeMillis()): Int {
        val arrivalMillis = getEffectiveArrivalDeadlineMillis(nowMillis) ?: return LATE_GRACE_PERIOD_MINUTES
        val graceDeadline = arrivalMillis + LATE_GRACE_PERIOD_MS
        if (nowMillis >= graceDeadline) return 0
        return (((graceDeadline - nowMillis) + 59_999L) / 60_000L).toInt().coerceIn(0, LATE_GRACE_PERIOD_MINUTES)
    }

    /**
     * Returns true if this active booking is late beyond the 10-minute grace period
     * (`nowMillis >= effectiveArrivalDeadlineMillis + LATE_GRACE_PERIOD_MS`), triggering auto-expiry.
     */
    fun isGracePeriodExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!isStatusPendingOrAccepted(status)) return false
        val dateStr = resolvedDate.trim()
        val isToday = dateStr.isBlank() ||
            dateStr.equals("Today", ignoreCase = true) ||
            isSameCalendarDay(createdAt, nowMillis)
        if (!isToday) return false
        val arrivalMillis = getEffectiveArrivalDeadlineMillis(nowMillis) ?: return false
        return nowMillis >= (arrivalMillis + LATE_GRACE_PERIOD_MS)
    }

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
        val finalPartnerId = partnerId.ifBlank { salonId }
        val finalSalonId = salonId.ifBlank { partnerId }
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
            "customerFcmToken" to customerFcmToken,
            "partnerId" to finalPartnerId,
            "salonId" to finalSalonId,
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

    enum class QueueTrafficLightStatus(
        val emoji: String,
        val label: String,
        val badgeText: String
    ) {
        NO_WAIT("🟢", "No Wait", "🟢 No Wait"),
        SLIGHT_WAIT("🟡", "Slight Wait", "🟡 Slight Wait"),
        HIGH_RUSH("🔴", "High Rush", "🔴 High Rush")
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

        /**
         * Default minimum travel buffer (10 minutes) when queue wait time is 0 so the customer
         * always receives a future arrival time with enough travel time to reach the salon.
         */
        const val DEFAULT_MIN_TRAVEL_BUFFER_MINUTES = 10

        /**
         * 10-minute grace period after the assigned arrival time before a late booking
         * can be auto-expired / cancelled as a No-Show.
         */
        const val LATE_GRACE_PERIOD_MINUTES = 10
        const val LATE_GRACE_PERIOD_MS = 10 * 60 * 1000L

        private val CLOCK_TIME_REGEX = Regex("(\\d{1,2}:\\d{2}\\s*(?:AM|PM|am|pm))")

        /**
         * Traffic Light status badge mapping for Customer Salon Profile Screen:
         * - 🟢 "No Wait" (Queue 0-1)
         * - 🟡 "Slight Wait" (Queue 2-3)
         * - 🔴 "High Rush" (Queue 4+)
         */
        fun getTrafficLightStatus(activeInQueue: Int): QueueTrafficLightStatus {
            return when {
                activeInQueue <= 1 -> QueueTrafficLightStatus.NO_WAIT
                activeInQueue in 2..3 -> QueueTrafficLightStatus.SLIGHT_WAIT
                else -> QueueTrafficLightStatus.HIGH_RUSH
            }
        }

        /**
         * Extracts a clean clock time (e.g. "04:35 PM") from any slot string,
         * removing technical jargon like "Live ETA:", "(~40 mins)", "(Reach Now)", etc.
         */
        fun formatCleanArrivalTime(rawTime: String?): String {
            val trimmed = rawTime?.trim().orEmpty()
            if (trimmed.isBlank()) {
                return calculateFormattedEtaClockTime(0, 15)
            }
            val match = CLOCK_TIME_REGEX.find(trimmed)
            if (match != null) {
                return match.groupValues[1].uppercase(java.util.Locale.US)
            }
            return trimmed
                .removePrefix("Live ETA:")
                .removePrefix("Waitlist:")
                .replace("(Reach Now)", "", ignoreCase = true)
                .replace("(⚡ Instant)", "", ignoreCase = true)
                .replace(Regex("\\(~?\\d+\\s*(?:m|mins|minutes)\\)", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { trimmed }
        }

        /**
         * Parses a formatted clock time string (e.g. "03:47 PM") into an epoch timestamp in milliseconds
         * on the calendar day of `dayAnchorMillis`.
         */
        fun parseClockTimeMillisOnDay(
            clockTimeStr: String?,
            dayAnchorMillis: Long = System.currentTimeMillis()
        ): Long? {
            val raw = clockTimeStr?.trim().orEmpty()
            if (raw.isBlank()) return null
            val match = CLOCK_TIME_REGEX.find(raw)
            val clean = (match?.groupValues?.get(1) ?: raw).trim().uppercase(java.util.Locale.US)
            val patterns = listOf("hh:mm a", "h:mm a", "HH:mm", "H:mm")
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
            val dayCal = java.util.Calendar.getInstance().apply {
                timeInMillis = dayAnchorMillis
                set(java.util.Calendar.HOUR_OF_DAY, parsedCal.get(java.util.Calendar.HOUR_OF_DAY))
                set(java.util.Calendar.MINUTE, parsedCal.get(java.util.Calendar.MINUTE))
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            return dayCal.timeInMillis
        }

        /**
         * Guarantees that a candidate internal arrival clock time is never in the past relative to `nowMillis`.
         * - If `candidateTimeStr` is at the current clock minute or in the future (`>= nowMillis - 60_000L`),
         *   preserves it (so a 0m-wait booking at 6:39 PM logs at 06:39 PM in the backend).
         * - If `candidateTimeStr` is blank, unparseable, or in the past (`< nowMillis - 60_000L`),
         *   recalculates `Current Time + Queue Wait` (`06:39 PM` when Wait Time is 0m).
         */
        fun ensureFutureArrivalClockTime(
            candidateTimeStr: String?,
            activeBookingsCount: Int = 0,
            defaultServiceDurationMinutes: Int = 20,
            nowMillis: Long = System.currentTimeMillis()
        ): String {
            val cleanCandidate = formatCleanArrivalTime(candidateTimeStr)
            val parsedMillis = parseClockTimeMillisOnDay(cleanCandidate, nowMillis)
            val currentMinuteFloorMillis = nowMillis - 60_000L
            return if (parsedMillis != null && parsedMillis >= currentMinuteFloorMillis) {
                val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
                formatter.format(java.util.Date(parsedMillis))
            } else {
                calculateFormattedEtaClockTime(
                    activeBookingsCount = activeBookingsCount,
                    defaultServiceDurationMinutes = defaultServiceDurationMinutes,
                    extraOffsetMinutes = 0,
                    baseTimeMillis = nowMillis
                )
            }
        }

        /**
         * Returns true if the booking's status is in ['Pending', 'Accepted'] (case-insensitive,
         * also matching 'Confirmed' and 'in_progress' which represent accepted bookings).
         */
        fun isStatusPendingOrAccepted(rawStatus: String?): Boolean {
            val normalized = rawStatus?.trim()?.lowercase().orEmpty()
            return normalized == "pending" ||
                normalized == "accepted" ||
                normalized == "confirmed" ||
                normalized == "in_progress"
        }

        /**
         * Returns true if two timestamps fall on the same local calendar day.
         */
        fun isSameCalendarDay(timestampMillis: Long, referenceMillis: Long = System.currentTimeMillis()): Boolean {
            if (timestampMillis <= 0L) return true
            val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = timestampMillis }
            val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = referenceMillis }
            return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
        }

        /**
         * Returns true if a booking is for Today and has status IN ['Pending', 'Accepted'].
         */
        fun isActiveInQueueForToday(
            booking: Booking,
            excludeBookingId: String? = null,
            referenceMillis: Long = System.currentTimeMillis()
        ): Boolean {
            if (!excludeBookingId.isNullOrBlank()) {
                val target = excludeBookingId.trim()
                if (booking.id.trim() == target ||
                    booking.bookingId.trim() == target ||
                    booking.resolvedBookingId.trim() == target
                ) {
                    return false
                }
            }
            if (!isStatusPendingOrAccepted(booking.status)) return false
            val dateStr = booking.resolvedDate.trim()
            val isTodayLabel = dateStr.isBlank() || dateStr.equals("Today", ignoreCase = true)
            val isCreatedToday = isSameCalendarDay(booking.createdAt, referenceMillis)
            return isTodayLabel || isCreatedToday
        }

        /**
         * Counts ALL bookings for today for a given salon where `status` is IN `['Pending', 'Accepted']`
         * (excluding the exact booking currently being evaluated when `excludeBookingId` is provided).
         */
        fun countActiveInQueueToday(
            bookings: List<Booking>,
            salonId: String,
            partnerId: String = "",
            ownerId: String = "",
            salonName: String = "",
            excludeBookingId: String? = null,
            referenceMillis: Long = System.currentTimeMillis()
        ): Int {
            val cleanSalonId = salonId.trim()
            val cleanPartnerId = partnerId.trim()
            val cleanOwnerId = ownerId.trim()
            val cleanSalonName = salonName.trim()
            val candidateIds = setOf(
                cleanSalonId,
                cleanPartnerId,
                cleanOwnerId,
                if (cleanPartnerId.isNotBlank()) "salon_$cleanPartnerId" else "",
                if (cleanOwnerId.isNotBlank()) "salon_$cleanOwnerId" else "",
                cleanSalonId.removePrefix("salon_")
            ).filter { it.isNotBlank() }

            return bookings
                .filter { b ->
                    val bSalonId = b.salonId.trim()
                    val bPartnerId = b.partnerId.trim()
                    val matchesId = candidateIds.isEmpty() ||
                        bSalonId in candidateIds ||
                        bPartnerId in candidateIds ||
                        bSalonId.removePrefix("salon_") in candidateIds ||
                        bPartnerId.removePrefix("salon_") in candidateIds
                    val matchesName = cleanSalonName.isNotBlank() &&
                        b.salonName.trim().equals(cleanSalonName, ignoreCase = true)
                    (matchesId || matchesName) &&
                        isActiveInQueueForToday(b, excludeBookingId = excludeBookingId, referenceMillis = referenceMillis)
                }
                .distinctBy { it.resolvedBookingId.ifBlank { "${it.customerId}_${it.createdAt}" } }
                .size
        }

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
                "cancelled", "canceled", "rejected", "no_show", "expired" -> "Cancelled"
                else -> "Pending"
            }
        }

        /**
         * Calculates the salon's internal queue wait time in minutes based on the Partner's current active Live Queue
         * (`activeInQueue * defaultServiceDurationMinutes`).
         * When `activeBookingsCount == 0`, returns `0` so the internal booking slot is logged at the CURRENT time
         * without pushing the salon's internal queue schedule forward.
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
         * Calculates the internal booking slot epoch timestamp in milliseconds (`Current Time + Queue Wait Time`).
         * When `activeBookingsCount == 0` and `extraOffsetMinutes == 0`, returns `baseTimeMillis` (Current Time).
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
         * Calculates the formatted internal booking slot clock time (e.g., "06:39 PM" when Wait Time is 0m,
         * or `Current Time + Queue Wait + Offset` when there is a queue or Partner offset).
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
         * Calculates the customer-facing "Reach by" clock time:
         * Adds a 10-minute travel buffer when Wait Time is 0m (`Current + 10m`, e.g. "06:49 PM" when current time is "06:39 PM"),
         * while keeping the backend internal slot at `Current Time`.
         */
        fun calculateCustomerReachByClockTime(
            activeBookingsCount: Int = 0,
            defaultServiceDurationMinutes: Int = 20,
            extraOffsetMinutes: Int = 0,
            baseTimeMillis: Long = System.currentTimeMillis()
        ): String {
            val queueWaitMins = calculateSystemWaitMinutes(activeBookingsCount, defaultServiceDurationMinutes)
            val customerBufferMins = (queueWaitMins + extraOffsetMinutes).coerceAtLeast(DEFAULT_MIN_TRAVEL_BUFFER_MINUTES)
            val reachByMillis = baseTimeMillis + (customerBufferMins * 60_000L)
            val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
            return formatter.format(java.util.Date(reachByMillis))
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
