package com.example.viewmodel

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.R
import com.example.model.Booking
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the Salon Partner's Live Queue flow.
 * Listens in real time to the Firestore `bookings` collection and detects specifically when
 * a NEW booking document with status "Pending" is added after the initial data load completes.
 */
class LiveQueueViewModel : ViewModel() {

    private val tag = "LiveQueueViewModel"

    private var bookingsListener: ListenerRegistration? = null
    private var defaultBookingsListener: ListenerRegistration? = null
    private var observedSalonId: String? = null
    private var observedPartnerUid: String? = null

    // Tracks whether the initial Firestore snapshot (and initial UI list) has been processed
    // so we NEVER trigger sound/vibration for initial data loads or historical bookings.
    private var hasCompletedInitialSnapshot = false
    private var hasCompletedInitialListSync = false
    private val knownBookingIds = mutableSetOf<String>()
    private val alertedBookingIds = mutableSetOf<String>()

    // State holding the most recent newly arrived "Pending" booking for the Partner's Live Queue
    private val _newIncomingBooking = MutableStateFlow<Booking?>(null)
    val newIncomingBooking: StateFlow<Booking?> = _newIncomingBooking.asStateFlow()

    /**
     * Attaches a real-time Firestore snapshot listener for the Partner's salon.
     * Skips the initial snapshot load and only triggers [_newIncomingBooking] when a
     * genuinely new document (`DocumentChange.Type.ADDED`) with `status == "Pending"` arrives.
     */
    fun observeSalonLiveQueue(salonId: String?, partnerUid: String? = null) {
        val cleanSalonId = salonId?.trim().orEmpty()
        val resolvedPartnerUid = partnerUid?.trim()?.takeIf { it.isNotEmpty() }
            ?: runCatching { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }.getOrDefault("")

        if (cleanSalonId.isEmpty() && resolvedPartnerUid.isEmpty()) {
            return
        }

        if (cleanSalonId == observedSalonId && resolvedPartnerUid == observedPartnerUid && bookingsListener != null) {
            return
        }

        bookingsListener?.remove()
        defaultBookingsListener?.remove()
        observedSalonId = cleanSalonId
        observedPartnerUid = resolvedPartnerUid
        hasCompletedInitialSnapshot = false

        try {
            val app = FirebaseApp.getInstance()
            val primaryDb = try {
                FirebaseFirestore.getInstance(app, "ai-studio-260b673c-6e6b-45c7-b680-335d42dc251f")
            } catch (_: Exception) {
                FirebaseFirestore.getInstance()
            }

            bookingsListener = attachFirestoreBookingListener(
                db = primaryDb,
                salonId = cleanSalonId,
                partnerUid = resolvedPartnerUid
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to attach LiveQueue Firestore listener: ${e.message}")
        }
    }

    private fun attachFirestoreBookingListener(
        db: FirebaseFirestore,
        salonId: String,
        partnerUid: String
    ): ListenerRegistration {
        return db.collection("bookings").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(tag, "LiveQueue snapshot error: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            // 1. On initial snapshot load, record all existing booking IDs without alerting
            if (!hasCompletedInitialSnapshot) {
                for (doc in snapshot.documents) {
                    knownBookingIds.add(doc.id)
                    doc.getString("id")?.takeIf { it.isNotBlank() }?.let { knownBookingIds.add(it) }
                    doc.getString("bookingId")?.takeIf { it.isNotBlank() }?.let { knownBookingIds.add(it) }
                }
                hasCompletedInitialSnapshot = true
                return@addSnapshotListener
            }

            // 2. For subsequent real-time updates, inspect only newly ADDED documents
            for (change in snapshot.documentChanges) {
                if (change.type == DocumentChange.Type.ADDED) {
                    val doc = change.document
                    val docBookingId = doc.getString("bookingId")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("id")?.takeIf { it.isNotBlank() }
                        ?: doc.id

                    val alreadyKnown = !knownBookingIds.add(docBookingId)
                    knownBookingIds.add(doc.id)

                    if (!alreadyKnown && isNewPendingBookingForPartner(doc, salonId, partnerUid)) {
                        val parsedBooking = parseIncomingBookingDoc(doc, docBookingId)
                        if (alertedBookingIds.add(parsedBooking.id)) {
                            _newIncomingBooking.value = parsedBooking
                        }
                    }
                }
            }
        }
    }

    /**
     * Synchronizes with the live bookings list already observed by the Partner Dashboard.
     * Ensures initial data loads are ignored and any newly added "Pending" booking triggers the alert once.
     */
    fun syncLiveQueueBookings(salonId: String?, bookings: List<Booking>) {
        if (salonId.isNullOrBlank()) return

        if (!hasCompletedInitialListSync) {
            bookings.forEach { booking ->
                knownBookingIds.add(booking.id)
                if (booking.bookingId.isNotBlank()) {
                    knownBookingIds.add(booking.bookingId)
                }
            }
            hasCompletedInitialListSync = true
            return
        }

        for (booking in bookings) {
            val isNewId = knownBookingIds.add(booking.id)
            if (booking.bookingId.isNotBlank()) {
                knownBookingIds.add(booking.bookingId)
            }
            if (isNewId &&
                booking.salonId == salonId &&
                booking.status.trim().equals("Pending", ignoreCase = true) &&
                alertedBookingIds.add(booking.id)
            ) {
                _newIncomingBooking.value = booking
            }
        }
    }

    /**
     * Clears the active incoming booking alert state after the UI has played the sound and vibration.
     */
    fun consumeNewBookingAlert(bookingId: String? = null) {
        if (bookingId != null) {
            alertedBookingIds.add(bookingId)
        }
        _newIncomingBooking.value = null
    }

    private fun isNewPendingBookingForPartner(
        doc: DocumentSnapshot,
        salonId: String,
        partnerUid: String
    ): Boolean {
        val status = (doc.getString("status") ?: "").trim()
        if (!status.equals("Pending", ignoreCase = true)) {
            return false
        }

        val docSalonId = (doc.getString("salonId") ?: "").trim()
        val docPartnerId = (doc.getString("partnerId") ?: doc.getString("ownerId") ?: "").trim()

        val matchesSalon = salonId.isNotBlank() && docSalonId == salonId
        val matchesPartner = partnerUid.isNotBlank() &&
            (docPartnerId == partnerUid || docSalonId == "salon_$partnerUid")

        return matchesSalon || matchesPartner
    }

    private fun parseIncomingBookingDoc(doc: DocumentSnapshot, resolvedId: String): Booking {
        val customerId = doc.getString("customerId") ?: doc.getString("userId") ?: ""
        val salonId = doc.getString("salonId") ?: ""
        val partnerId = doc.getString("partnerId") ?: doc.getString("ownerId") ?: ""
        val salonName = doc.getString("salonName") ?: "Salon"
        val customerName = doc.getString("customerName") ?: "Customer"
        val customerPhone = doc.getString("customerPhone") ?: ""
        val serviceName = doc.getString("service") ?: doc.getString("serviceName") ?: "Grooming Service"
        val servicePrice = (doc.getLong("servicePrice") ?: doc.getLong("totalAmount") ?: 0L).toInt()
        val timeSlot = doc.getString("timeSlot") ?: doc.getString("time") ?: "Instant Slot"
        val date = doc.getString("date") ?: "Today"
        val status = doc.getString("status") ?: "Pending"
        val pin = doc.getString("pin") ?: doc.getString("verificationPin") ?: ""
        val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

        return Booking(
            id = resolvedId,
            bookingId = resolvedId,
            userId = customerId,
            customerId = customerId,
            partnerId = partnerId,
            salonId = salonId,
            salonName = salonName,
            customerName = customerName,
            customerPhone = customerPhone,
            service = serviceName,
            servicePrice = servicePrice,
            timeSlot = timeSlot,
            date = date,
            time = timeSlot,
            pin = pin,
            status = status,
            createdAt = createdAt
        )
    }

    override fun onCleared() {
        super.onCleared()
        bookingsListener?.remove()
        defaultBookingsListener?.remove()
    }
}

private var activeAlertMediaPlayer: MediaPlayer? = null

/**
 * Triggers a distinct vibration pattern (0ms delay, 500ms ON, 200ms OFF, 500ms ON, 200ms OFF, 500ms ON)
 * and plays the default notification sound via MediaPlayer so the Salon Partner immediately notices
 * a new incoming booking.
 */
fun triggerNewBookingSoundAndVibration(context: Context) {
    try {
        // 1. Loud Custom Audio with MediaPlayer (R.raw.loud_alert)
        try {
            MediaPlayer.create(context, R.raw.loud_alert)?.apply {
                activeAlertMediaPlayer = this
                setOnCompletionListener { mp ->
                    try {
                        mp.release()
                    } catch (_: Exception) {
                    }
                    if (activeAlertMediaPlayer === mp) {
                        activeAlertMediaPlayer = null
                    }
                }
                start()
            }
        } catch (e: Exception) {
            Log.w("LiveQueueAlert", "Failed to play MediaPlayer loud_alert sound: ${e.message}")
        }

        // 2. Enforce Vibration with strong distinct waveform pattern
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
                    ?: (@Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator))
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 500), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
            }
        } catch (e: Exception) {
            Log.w("LiveQueueAlert", "Failed to vibrate for new booking: ${e.message}")
        }
    } catch (e: Exception) {
        Log.w("LiveQueueAlert", "Alert error: ${e.message}")
    }
}
