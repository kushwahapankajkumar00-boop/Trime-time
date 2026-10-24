package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PinVerificationResult
import com.example.data.TrimTimeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TrimTime", appName)
    }

    @Test
    fun `anti-fake pin verification succeeds with valid pin`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = TrimTimeRepository.getInstance(context)

        // Create booking with auto-generated 4-digit PIN
        val booking = repository.createBooking(
            customerId = "test_cust",
            customerName = "Test User",
            customerPhone = "+91 9999999999",
            salonId = "salon_blade_comb",
            salonName = "The Blade & Comb Studio",
            service = "Classic Executive Haircut",
            servicePrice = 120,
            timeSlot = "11:30 AM",
            bookingDate = "Today"
        )

        val pin = booking.pin
        assertEquals(4, pin.length)

        // Try wrong PIN first (Anti-fake check)
        val wrongPinResult = repository.verifyAndCompleteBooking(booking.id, "0000_wrong")
        assertTrue(wrongPinResult is PinVerificationResult.IncorrectPin)

        // Try exact PIN
        val correctResult = repository.verifyAndCompleteBooking(booking.id, pin)
        assertTrue(correctResult is PinVerificationResult.Success)

        // Verify status changed
        val updatedBooking = repository.bookings.value.find { it.id == booking.id }
        assertEquals("completed", updatedBooking?.status)
    }

    @Test
    fun `mandatory phone number update saves valid 10-digit phone`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = TrimTimeRepository.getInstance(context)

        repository.signInWithGoogle(context, onSuccess = {}, onError = {})

        var phoneSaved = false
        repository.updateUserPhone(
            phone = "9876543210",
            onSuccess = { phoneSaved = true },
            onError = {}
        )

        assertTrue(phoneSaved)
        assertEquals("9876543210", repository.currentUserProfile.value?.phone)
    }

    @Test
    fun `one active booking rule prevents multiple pending bookings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = TrimTimeRepository.getInstance(context)

        // Clear existing bookings or use unique ID
        val uniqueCustId = "cust_anti_spam_${System.currentTimeMillis()}"
        val phone = "+91 9123456780"

        val firstBooking = repository.createBooking(
            customerId = uniqueCustId,
            customerName = "Spam Tester",
            customerPhone = phone,
            salonId = "salon_blade_comb",
            salonName = "The Blade & Comb Studio",
            service = "Executive Haircut",
            servicePrice = 120,
            timeSlot = "11:30 AM",
            bookingDate = "Today"
        )
        org.junit.Assert.assertNotNull(firstBooking)

        // Attempting second pending booking with same customer should throw
        var exceptionThrown = false
        try {
            repository.createBooking(
                customerId = uniqueCustId,
                customerName = "Spam Tester",
                customerPhone = phone,
                salonId = "salon_blade_comb",
                salonName = "The Blade & Comb Studio",
                service = "Beard Trim",
                servicePrice = 60,
                timeSlot = "12:00 PM",
                bookingDate = "Today"
            )
        } catch (e: IllegalStateException) {
            exceptionThrown = true
            assertTrue(e.message?.contains("active booking") == true)
        }

        assertTrue(exceptionThrown)
    }

    @Test
    fun `three cancellations increments strikes and auto blocks user`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = TrimTimeRepository.getInstance(context)

        repository.signInWithGoogle(context, onSuccess = {}, onError = {})

        // Cancel 3 bookings to accumulate 3 strikes
        for (i in 1..3) {
            val booking = repository.createBooking(
                customerId = "strike_cust_${System.currentTimeMillis()}_$i",
                customerName = "Strike User",
                customerPhone = "+91 98888${i}0000",
                salonId = "salon_blade_comb",
                salonName = "The Blade & Comb Studio",
                service = "Haircut",
                servicePrice = 100,
                timeSlot = "10:00 AM",
                bookingDate = "Today"
            )
            repository.cancelBooking(booking.id)
        }

        val profile = repository.currentUserProfile.value
        assertEquals(3, profile?.cancellationCount)
        assertTrue(profile?.isSuspended == true)

        // Attempting to book when suspended should be blocked
        var blocked = false
        try {
            repository.createBooking(
                customerId = profile?.id ?: "strike_cust",
                customerName = "Strike User",
                customerPhone = "+91 9888899999",
                salonId = "salon_blade_comb",
                salonName = "The Blade & Comb Studio",
                service = "Haircut",
                servicePrice = 100,
                timeSlot = "10:00 AM",
                bookingDate = "Today"
            )
        } catch (e: IllegalStateException) {
            blocked = true
            assertTrue(e.message?.contains("suspended") == true)
        }

        assertTrue(blocked)
    }
}
