package com.example.util

/**
 * Centralized Cyber Security & Input Hardening Utility for TrimTime:
 * 1. Sanitizes all user/partner text inputs by stripping HTML/script tags (<, >, script, javascript:)
 *    and enforcing strict character length limits.
 * 2. Strictly validates Indian 10-digit mobile numbers using regex ^[6-9][0-9]{9}$ and blocks known
 *    fake/sequential/repeated numbers (e.g., 1234567890, 0000000000, 9999999999).
 * 3. Strictly validates salon service prices between ₹10 and ₹25,000.
 * 4. Masks customer phone numbers (+91 ••••••1234) once a booking is COMPLETED, CANCELLED, or REJECTED.
 */
object SecurityValidator {

    const val MAX_NAME_LENGTH = 60
    const val MAX_ADDRESS_LENGTH = 250
    const val MAX_TAGLINE_LENGTH = 140
    const val MAX_FEEDBACK_LENGTH = 500
    const val MIN_SERVICE_PRICE = 10
    const val MAX_SERVICE_PRICE = 25000
    const val MIN_SERVICE_DURATION = 5
    const val MAX_SERVICE_DURATION = 300

    const val MAX_PIN_ATTEMPTS = 5
    const val PIN_LOCKOUT_DURATION_MS = 60_000L // 60 seconds

    private val INDIAN_MOBILE_REGEX = Regex("^[6-9][0-9]{9}$")

    private val BLOCKED_FAKE_NUMBERS = setOf(
        "0000000000",
        "1111111111",
        "2222222222",
        "3333333333",
        "4444444444",
        "5555555555",
        "6666666666",
        "7777777777",
        "8888888888",
        "9999999999",
        "1234567890",
        "0123456789",
        "9000000000",
        "8000000000",
        "7000000000",
        "6000000000"
    )

    // Matches full HTML/script tags such as <script>...</script> or any <...> tag
    private val SCRIPT_BLOCK_REGEX = Regex("(?i)<\\s*script[^>]*>[\\s\\S]*?<\\s*/\\s*script\\s*>")
    private val HTML_TAG_REGEX = Regex("<[^>]*>")
    private val DANGEROUS_TOKENS_REGEX = Regex("(?i)(javascript\\s*:|onerror\\s*=|onload\\s*=|<\\s*/?\\s*script)")

    /**
     * Strips HTML/script tags, angle brackets (<, >), and dangerous script tokens while enforcing
     * a strict maximum character length. Preserves normal typing whitespace when `trimEdges` is false.
     */
    fun sanitizeInput(
        raw: String,
        maxLength: Int = MAX_NAME_LENGTH,
        trimEdges: Boolean = true
    ): String {
        if (raw.isEmpty()) return ""
        val withoutScriptBlocks = raw.replace(SCRIPT_BLOCK_REGEX, "")
        val withoutHtmlTags = withoutScriptBlocks.replace(HTML_TAG_REGEX, "")
        val withoutDangerousTokens = withoutHtmlTags.replace(DANGEROUS_TOKENS_REGEX, "")
        val strippedAngles = withoutDangerousTokens
            .replace("<", "")
            .replace(">", "")
            .replace("\u0000", "")
        val normalized = if (trimEdges) {
            strippedAngles.trim().replace(Regex("\\s{2,}"), " ")
        } else {
            strippedAngles
        }
        return normalized.take(maxLength)
    }

    fun sanitizeName(raw: String, trimEdges: Boolean = true): String =
        sanitizeInput(raw, maxLength = MAX_NAME_LENGTH, trimEdges = trimEdges)

    fun sanitizeAddress(raw: String, trimEdges: Boolean = true): String =
        sanitizeInput(raw, maxLength = MAX_ADDRESS_LENGTH, trimEdges = trimEdges)

    fun sanitizeTagline(raw: String, trimEdges: Boolean = true): String =
        sanitizeInput(raw, maxLength = MAX_TAGLINE_LENGTH, trimEdges = trimEdges)

    fun sanitizeFeedback(raw: String, trimEdges: Boolean = true): String =
        sanitizeInput(raw, maxLength = MAX_FEEDBACK_LENGTH, trimEdges = trimEdges)

    /**
     * Extracts the 10-digit core mobile number from raw input (stripping +91 or 0 prefix if present).
     */
    fun extractTenDigitMobile(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        return when {
            digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
            digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
            digits.length > 10 && digits.startsWith("91") -> digits.drop(2).take(10)
            else -> digits.takeLast(10)
        }
    }

    /**
     * Strictly validates an Indian 10-digit mobile number:
     * - Must match `^[6-9][0-9]{9}$`
     * - Must NOT be a fake sequential or single-digit repeated number (e.g., 1234567890, 0000000000, 9999999999)
     */
    fun isValidIndianMobile(rawPhone: String): Boolean {
        val tenDigits = extractTenDigitMobile(rawPhone)
        if (tenDigits.length != 10) return false
        if (!INDIAN_MOBILE_REGEX.matches(tenDigits)) return false
        if (tenDigits in BLOCKED_FAKE_NUMBERS) return false
        // Reject numbers where all 10 digits are identical
        if (tenDigits.toSet().size == 1) return false
        return true
    }

    /**
     * Returns a user-friendly error message if the mobile number is invalid, or null if valid.
     */
    fun validateIndianMobileError(rawPhone: String): String? {
        val tenDigits = extractTenDigitMobile(rawPhone)
        if (tenDigits.length != 10) {
            return "Please enter a valid 10-digit Indian mobile number."
        }
        if (tenDigits in BLOCKED_FAKE_NUMBERS || tenDigits.toSet().size == 1) {
            return "Fake or sequential mobile numbers ($tenDigits) are blocked for security. Please enter a real mobile number."
        }
        if (!INDIAN_MOBILE_REGEX.matches(tenDigits)) {
            return "Mobile number must start with 6, 7, 8, or 9 and contain 10 digits."
        }
        return null
    }

    fun formatIndianMobile(rawPhone: String): String {
        val tenDigits = extractTenDigitMobile(rawPhone)
        return if (tenDigits.length == 10) "+91 $tenDigits" else rawPhone.trim()
    }

    /**
     * Validates that a salon service price is within realistic bounds: ₹10 to ₹25,000.
     */
    fun isValidServicePrice(price: Int): Boolean {
        return price in MIN_SERVICE_PRICE..MAX_SERVICE_PRICE
    }

    fun validateServicePriceError(price: Int?): String? {
        if (price == null) return "Please enter a valid numeric price in ₹."
        if (price < MIN_SERVICE_PRICE || price > MAX_SERVICE_PRICE) {
            return "Service price must be between ₹$MIN_SERVICE_PRICE and ₹25,000."
        }
        return null
    }

    /**
     * Checks whether a booking status is considered active (PENDING, ACCEPTED, or IN_PROGRESS).
     */
    fun isBookingActiveStatus(status: String): Boolean {
        val normalized = status.trim().uppercase()
        return normalized == "PENDING" || normalized == "ACCEPTED" || normalized == "IN_PROGRESS"
    }

    /**
     * Checks whether customer phone number should be masked (when booking is COMPLETED, CANCELLED, or REJECTED).
     */
    fun shouldMaskCustomerPhone(status: String): Boolean {
        val normalized = status.trim().uppercase()
        return normalized == "COMPLETED" || normalized == "CANCELLED" || normalized == "REJECTED" ||
            !isBookingActiveStatus(status)
    }

    /**
     * Masks a customer phone number as `+91 ••••••1234` once a booking is completed, cancelled, or rejected.
     * While the booking is actively pending/accepted/in_progress, returns the formatted phone number.
     */
    fun maskCustomerPhoneForStatus(rawPhone: String, status: String): String {
        if (rawPhone.isBlank()) return "+91 ••••••0000"
        val tenDigits = extractTenDigitMobile(rawPhone)
        val lastFour = if (tenDigits.length >= 4) tenDigits.takeLast(4) else "0000"
        return if (shouldMaskCustomerPhone(status)) {
            "+91 ••••••$lastFour"
        } else {
            if (tenDigits.length == 10) "+91 $tenDigits" else rawPhone
        }
    }

    /**
     * Always masks a phone number as `+91 ••••••1234` regardless of status.
     */
    fun maskPhoneAlways(rawPhone: String): String {
        val tenDigits = extractTenDigitMobile(rawPhone)
        val lastFour = if (tenDigits.length >= 4) tenDigits.takeLast(4) else "0000"
        return "+91 ••••••$lastFour"
    }
}
