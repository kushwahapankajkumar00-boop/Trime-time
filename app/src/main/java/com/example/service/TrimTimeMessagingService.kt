package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Firebase Cloud Messaging (FCM) Service for TrimTime.
 * Handles incoming high-priority data and notification messages in the background, lock screen,
 * and foreground for both:
 * 1. Salon Partners (new incoming booking alerts using the loud custom `R.raw.loud_alert` sound + strong vibration).
 * 2. Customers (booking accepted, ETA adjusted, or booking declined notifications).
 */
class TrimTimeMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")
        TrimTimeNotificationHelper.cacheFcmTokenLocally(applicationContext, token)
        TrimTimeNotificationHelper.saveFcmTokenToFirestore(
            context = applicationContext,
            token = token,
            explicitUserId = null,
            explicitSalonId = null
        )
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Received FCM message from: ${remoteMessage.from}")

        val wakeLock = TrimTimeNotificationHelper.acquireTemporaryWakeLock(applicationContext, 10_000L)
        try {
            val data = remoteMessage.data
            val messageType = data["type"]?.trim()?.uppercase()
                ?: data["eventType"]?.trim()?.uppercase()
                ?: "NEW_BOOKING"
            val bookingId = data["bookingId"]
                ?: data["id"]
                ?: "booking_${System.currentTimeMillis()}"
            val customerName = data["customerName"] ?: "Customer"
            val serviceName = data["serviceName"] ?: data["service"] ?: "Salon Service"
            val servicePrice = data["servicePrice"] ?: ""
            val timeSlot = data["timeSlot"] ?: data["allocatedTime"] ?: "Instant Slot"
            val salonName = data["salonName"] ?: "TrimTime Salon"

            if (messageType in setOf("BOOKING_ACCEPTED", "ETA_ADJUSTED", "BOOKING_DECLINED") ||
                messageType.startsWith("CUSTOMER_BOOKING_")
            ) {
                val defaultTitle = when (messageType) {
                    "BOOKING_DECLINED" -> "❌ Booking Declined by $salonName"
                    "ETA_ADJUSTED" -> "⏱️ Booking Accepted & ETA Updated at $salonName"
                    else -> "✅ Booking Confirmed at $salonName!"
                }
                val defaultBody = when (messageType) {
                    "BOOKING_DECLINED" -> "Your booking for $serviceName at $salonName was declined by the partner."
                    "ETA_ADJUSTED" -> "Your booking for $serviceName is confirmed! Updated arrival slot: $timeSlot."
                    else -> "Your booking for $serviceName is confirmed for $timeSlot."
                }
                val title = data["title"] ?: remoteMessage.notification?.title ?: defaultTitle
                val body = data["body"] ?: remoteMessage.notification?.body ?: defaultBody

                TrimTimeNotificationHelper.showCustomerBookingStatusNotification(
                    context = applicationContext,
                    bookingId = bookingId,
                    eventKey = "${bookingId}_${messageType}_${timeSlot}",
                    title = title,
                    body = body
                )
            } else {
                val notificationTitle = data["title"]
                    ?: remoteMessage.notification?.title
                    ?: "🔔 New Booking at $salonName!"
                val priceSuffix = if (servicePrice.isNotBlank()) " • ₹$servicePrice" else ""
                val notificationBody = data["body"]
                    ?: remoteMessage.notification?.body
                    ?: "$customerName booked $serviceName$priceSuffix ($timeSlot). Tap to accept in Live Queue."

                TrimTimeNotificationHelper.showIncomingBookingNotification(
                    context = applicationContext,
                    bookingId = bookingId,
                    title = notificationTitle,
                    body = notificationBody,
                    customerName = customerName,
                    serviceName = serviceName,
                    timeSlot = timeSlot
                )
            }
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val TAG = "TrimTimeFCM"
    }
}

/**
 * Foreground DataSync Service (`PartnerLiveQueueService`) that keeps the Salon Partner's
 * real-time Firestore booking & FCM notification listeners active when the Partner minimizes
 * the app, goes to the home screen, or locks the device screen.
 */
class PartnerLiveQueueService : Service() {

    private val serviceListeners = mutableListOf<ListenerRegistration>()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TrimTimeNotificationHelper.ensureNotificationChannel(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action.orEmpty()
        if (action == ACTION_STOP_SERVICE) {
            cleanupListeners()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val partnerUid = intent?.getStringExtra(EXTRA_PARTNER_UID)?.trim().orEmpty()
            .ifBlank { TrimTimeNotificationHelper.getCachedPartnerUid(applicationContext) }
            .ifBlank { runCatching { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }.getOrDefault("") }

        val salonId = intent?.getStringExtra(EXTRA_SALON_ID)?.trim().orEmpty()
            .ifBlank { TrimTimeNotificationHelper.getCachedPartnerSalonId(applicationContext) }
            .ifBlank { if (partnerUid.isNotBlank()) "salon_$partnerUid" else "" }

        val salonName = intent?.getStringExtra(EXTRA_SALON_NAME)?.trim().orEmpty()
            .ifBlank { TrimTimeNotificationHelper.getCachedPartnerSalonName(applicationContext) }
            .ifBlank { "TrimTime Partner Salon" }

        try {
            val foregroundNotification = buildForegroundSyncNotification(salonName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    SYNC_NOTIFICATION_ID,
                    foregroundNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(SYNC_NOTIFICATION_ID, foregroundNotification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to promote PartnerLiveQueueService to foreground: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        if (partnerUid.isNotBlank() || salonId.isNotBlank()) {
            attachBackgroundPartnerListeners(partnerUid, salonId, salonName)
        }

        return START_STICKY
    }

    private fun buildForegroundSyncNotification(salonName: String): Notification {
        TrimTimeNotificationHelper.ensureNotificationChannel(applicationContext)
        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_partner_live_queue", true)
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            SYNC_NOTIFICATION_ID,
            launchIntent,
            pendingFlags
        )

        return NotificationCompat.Builder(applicationContext, TrimTimeNotificationHelper.SYNC_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$salonName • Live Queue Active")
            .setContentText("Listening for instant customer bookings in background")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun attachBackgroundPartnerListeners(
        partnerUid: String,
        salonId: String,
        salonName: String
    ) {
        cleanupListeners()
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TrimTime:PartnerQueueSyncLock")?.apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L) // Renewed on service start, capped at 1 hour per session
            }
        } catch (_: Exception) {
        }

        val dbs = TrimTimeNotificationHelper.resolveAllFirestoreDbs(applicationContext)
        val candidateIds = setOf(
            partnerUid.trim(),
            salonId.trim(),
            if (partnerUid.isNotBlank()) "salon_${partnerUid.trim()}" else "",
            salonId.trim().removePrefix("salon_")
        ).filter { it.isNotBlank() }

        dbs.forEach { db ->
            // 1. Real-time background listener on `bookings` collection
            val bookingsReg = db.collection("bookings")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val nowMillis = System.currentTimeMillis()
                    for (change in snapshot.documentChanges) {
                        if (change.type == DocumentChange.Type.ADDED || change.type == DocumentChange.Type.MODIFIED) {
                            val doc = change.document
                            val status = doc.getString("status")?.trim().orEmpty()
                            if (!status.equals("Pending", ignoreCase = true)) continue

                            val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: nowMillis
                            // Only alert for recent pending bookings within the 5-minute window
                            if (nowMillis - createdAt > 5 * 60_000L) continue

                            val docBookingId = doc.getString("bookingId")?.takeIf { it.isNotBlank() }
                                ?: doc.getString("id")?.takeIf { it.isNotBlank() }
                                ?: doc.id
                            val docSalonId = doc.getString("salonId")?.trim().orEmpty()
                            val docPartnerId = doc.getString("partnerId")?.trim().orEmpty()
                            val docSalonName = doc.getString("salonName")?.trim().orEmpty()

                            val matchesPartner = docSalonId in candidateIds ||
                                docPartnerId in candidateIds ||
                                docSalonId.removePrefix("salon_") in candidateIds ||
                                docPartnerId.removePrefix("salon_") in candidateIds ||
                                (salonName.isNotBlank() && docSalonName.equals(salonName, ignoreCase = true))

                            if (matchesPartner && !TrimTimeNotificationHelper.hasBookingBeenNotified(applicationContext, docBookingId)) {
                                val customerName = doc.getString("customerName")?.trim().orEmpty().ifBlank { "Customer" }
                                val serviceName = doc.getString("service")?.trim().orEmpty().ifBlank { "Salon Service" }
                                val servicePrice = (doc.get("servicePrice") as? Number)?.toInt() ?: 100
                                val timeSlot = doc.getString("allocatedTime")?.takeIf { it.isNotBlank() }
                                    ?: doc.getString("timeSlot")?.takeIf { it.isNotBlank() }
                                    ?: doc.getString("time")?.takeIf { it.isNotBlank() }
                                    ?: "Instant Slot"
                                val displaySalon = docSalonName.ifBlank { salonName.ifBlank { "Your Salon" } }

                                TrimTimeNotificationHelper.showIncomingBookingNotification(
                                    context = applicationContext,
                                    bookingId = docBookingId,
                                    title = "🔔 New Booking at $displaySalon!",
                                    body = "$customerName booked $serviceName (₹$servicePrice • $timeSlot). Tap to accept.",
                                    customerName = customerName,
                                    serviceName = serviceName,
                                    timeSlot = timeSlot
                                )
                            }
                        }
                    }
                }
            serviceListeners.add(bookingsReg)

            // 2. Real-time background listener on `fcm_notifications` collection for Partner
            val fcmReg = db.collection("fcm_notifications")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val nowMillis = System.currentTimeMillis()
                    for (change in snapshot.documentChanges) {
                        if (change.type == DocumentChange.Type.ADDED) {
                            val doc = change.document
                            val role = doc.getString("recipientRole")?.trim().orEmpty()
                            if (role.isNotBlank() && !role.equals("partner", ignoreCase = true)) continue

                            val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: nowMillis
                            if (nowMillis - createdAt > 5 * 60_000L) continue

                            val docPartnerId = doc.getString("partnerId")?.trim().orEmpty()
                            val docSalonId = doc.getString("salonId")?.trim().orEmpty()
                            val docBookingId = doc.getString("bookingId")?.trim().orEmpty().ifBlank { doc.id }

                            val matchesPartner = docPartnerId in candidateIds ||
                                docSalonId in candidateIds ||
                                docPartnerId.removePrefix("salon_") in candidateIds ||
                                docSalonId.removePrefix("salon_") in candidateIds

                            if (matchesPartner && !TrimTimeNotificationHelper.hasBookingBeenNotified(applicationContext, docBookingId)) {
                                val title = doc.getString("title")?.takeIf { it.isNotBlank() }
                                    ?: "🔔 New Booking Received!"
                                val body = doc.getString("body")?.takeIf { it.isNotBlank() }
                                    ?: "A customer just booked a slot at your salon. Tap to view in Live Queue."
                                TrimTimeNotificationHelper.showIncomingBookingNotification(
                                    context = applicationContext,
                                    bookingId = docBookingId,
                                    title = title,
                                    body = body
                                )
                            }
                        }
                    }
                }
            serviceListeners.add(fcmReg)
        }
    }

    private fun cleanupListeners() {
        serviceListeners.forEach {
            try {
                it.remove()
            } catch (_: Exception) {
            }
        }
        serviceListeners.clear()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    override fun onDestroy() {
        cleanupListeners()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PartnerLiveQueueSvc"
        private const val SYNC_NOTIFICATION_ID = 994102
        const val ACTION_START_SERVICE = "com.example.service.START_PARTNER_QUEUE_SYNC"
        const val ACTION_STOP_SERVICE = "com.example.service.STOP_PARTNER_QUEUE_SYNC"
        const val EXTRA_PARTNER_UID = "extra_partner_uid"
        const val EXTRA_SALON_ID = "extra_salon_id"
        const val EXTRA_SALON_NAME = "extra_salon_name"
    }
}

/**
 * Helper managing FCM token retrieval/persistence for both Customers and Partners,
 * high-priority notification channel setup, custom `R.raw.loud_alert` playback + waveform vibration,
 * and high-priority FCM push dispatch for both Partner new-booking alerts and Customer status/ETA updates.
 */
object TrimTimeNotificationHelper {

    private const val TAG = "TrimTimeNotification"
    const val CHANNEL_ID = "trimtime_partner_booking_alerts_v2"
    const val LEGACY_CHANNEL_ID = "trimtime_partner_booking_alerts"
    const val CUSTOMER_CHANNEL_ID = "trimtime_customer_booking_updates_v2"
    const val SYNC_CHANNEL_ID = "trimtime_partner_live_sync"
    private const val CHANNEL_NAME = "Partner Incoming Booking Alerts"
    private const val CHANNEL_DESC = "Loud custom alarm and vibration alerts for new customer bookings"
    private const val CUSTOMER_CHANNEL_NAME = "Customer Booking & ETA Updates"
    private const val CUSTOMER_CHANNEL_DESC = "High-priority alerts when a Partner accepts, adjusts ETA, or declines your booking"
    private const val SYNC_CHANNEL_NAME = "Partner Live Queue Background Sync"
    private const val SYNC_CHANNEL_DESC = "Keeps Partner Live Queue connected for instant booking alerts when minimized"
    private const val PREFS_NAME = "trimtime_fcm_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_PARTNER_UID = "partner_uid"
    private const val KEY_PARTNER_SALON_ID = "partner_salon_id"
    private const val KEY_PARTNER_SALON_NAME = "partner_salon_name"
    private const val KEY_NOTIFIED_BOOKING_IDS = "notified_booking_ids_set"

    private val notifiedBookingIds: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val notifiedCustomerEvents: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private var activeMediaPlayer: MediaPlayer? = null

    /**
     * Acquires a temporary CPU WakeLock so background/lock-screen notifications and audio
     * finish executing without being suspended by Doze mode.
     */
    fun acquireTemporaryWakeLock(context: Context, timeoutMs: Long = 10_000L): PowerManager.WakeLock? {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TrimTime:BookingAlertWakeLock").apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks both in-memory set and persistent SharedPreferences to see if a booking ID
     * has already triggered an incoming Partner alert on this device.
     */
    fun hasBookingBeenNotified(context: Context, bookingId: String): Boolean {
        val cleanId = bookingId.trim()
        if (cleanId.isBlank()) return false
        if (cleanId in notifiedBookingIds) return true
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedSet = prefs.getStringSet(KEY_NOTIFIED_BOOKING_IDS, emptySet()).orEmpty()
            if (cleanId in savedSet) {
                notifiedBookingIds.add(cleanId)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun markBookingNotified(context: Context, bookingId: String): Boolean {
        val cleanId = bookingId.trim()
        if (cleanId.isBlank()) return true
        if (hasBookingBeenNotified(context, cleanId)) return false
        notifiedBookingIds.add(cleanId)
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = prefs.getStringSet(KEY_NOTIFIED_BOOKING_IDS, emptySet()).orEmpty().toMutableSet()
            existing.add(cleanId)
            val trimmed = if (existing.size > 200) existing.toList().takeLast(150).toSet() else existing
            prefs.edit().putStringSet(KEY_NOTIFIED_BOOKING_IDS, trimmed).apply()
        } catch (_: Exception) {
        }
        return true
    }

    /**
     * Creates the high-importance notification channels on Android 8.0+ with the custom `R.raw.loud_alert`
     * sound for Partners (both current and legacy channel IDs) and high-priority alerts for Customers.
     */
    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        ?: return
                val loudAlertUri = Uri.parse("android.resource://${context.packageName}/${R.raw.loud_alert}")
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()

                listOf(CHANNEL_ID, LEGACY_CHANNEL_ID).forEach { channelId ->
                    val partnerChannel = NotificationChannel(
                        channelId,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = CHANNEL_DESC
                        enableVibration(true)
                        enableLights(true)
                        vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                        setSound(loudAlertUri, audioAttributes)
                        setBypassDnd(true)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                    notificationManager.createNotificationChannel(partnerChannel)
                }

                val customerChannel = NotificationChannel(
                    CUSTOMER_CHANNEL_ID,
                    CUSTOMER_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CUSTOMER_CHANNEL_DESC
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 350, 150, 350)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(customerChannel)

                val syncChannel = NotificationChannel(
                    SYNC_CHANNEL_ID,
                    SYNC_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = SYNC_CHANNEL_DESC
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
                notificationManager.createNotificationChannel(syncChannel)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create notification channels: ${e.message}")
            }
        }
    }

    /**
     * Starts or updates the background `PartnerLiveQueueService` so a Partner with a registered
     * salon receives instant booking notifications with sound even when the app is minimized,
     * on the home screen, or the device screen is locked.
     */
    fun startPartnerBackgroundQueueMonitor(
        context: Context,
        partnerUid: String,
        salonId: String,
        salonName: String = ""
    ) {
        val cleanUid = partnerUid.trim()
        val cleanSalonId = salonId.trim().ifBlank { if (cleanUid.isNotBlank()) "salon_$cleanUid" else "" }
        if (cleanUid.isBlank() && cleanSalonId.isBlank()) return

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            if (cleanUid.isNotBlank()) prefs.putString(KEY_PARTNER_UID, cleanUid)
            if (cleanSalonId.isNotBlank()) prefs.putString(KEY_PARTNER_SALON_ID, cleanSalonId)
            if (salonName.isNotBlank()) prefs.putString(KEY_PARTNER_SALON_NAME, salonName.trim())
            prefs.apply()
        } catch (_: Exception) {
        }

        subscribePartnerTopics(cleanUid, cleanSalonId)

        try {
            val serviceIntent = Intent(context.applicationContext, PartnerLiveQueueService::class.java).apply {
                action = PartnerLiveQueueService.ACTION_START_SERVICE
                putExtra(PartnerLiveQueueService.EXTRA_PARTNER_UID, cleanUid)
                putExtra(PartnerLiveQueueService.EXTRA_SALON_ID, cleanSalonId)
                putExtra(PartnerLiveQueueService.EXTRA_SALON_NAME, salonName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context.applicationContext, serviceIntent)
            } else {
                context.applicationContext.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not start PartnerLiveQueueService (background restriction): ${e.message}")
        }
    }

    fun stopPartnerBackgroundQueueMonitor(context: Context) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PARTNER_UID)
                .remove(KEY_PARTNER_SALON_ID)
                .remove(KEY_PARTNER_SALON_NAME)
                .apply()
        } catch (_: Exception) {
        }
        try {
            val stopIntent = Intent(context.applicationContext, PartnerLiveQueueService::class.java).apply {
                action = PartnerLiveQueueService.ACTION_STOP_SERVICE
            }
            context.applicationContext.stopService(stopIntent)
        } catch (_: Exception) {
        }
    }

    fun subscribePartnerTopics(partnerUid: String, salonId: String?) {
        val cleanUid = partnerUid.trim()
        val cleanSalon = salonId?.trim().orEmpty()
        try {
            val messaging = FirebaseMessaging.getInstance()
            if (cleanUid.isNotBlank()) {
                val safeUidTopic = cleanUid.replace(Regex("[^a-zA-Z0-9-_.~%]"), "_")
                messaging.subscribeToTopic("partner_$safeUidTopic")
            }
            if (cleanSalon.isNotBlank()) {
                val safeSalonTopic = cleanSalon.replace(Regex("[^a-zA-Z0-9-_.~%]"), "_")
                messaging.subscribeToTopic("salon_$safeSalonTopic")
            }
        } catch (_: Exception) {
        }
    }

    fun getCachedPartnerUid(context: Context): String {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PARTNER_UID, "")
                .orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun getCachedPartnerSalonId(context: Context): String {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PARTNER_SALON_ID, "")
                .orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun getCachedPartnerSalonName(context: Context): String {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PARTNER_SALON_NAME, "")
                .orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun cacheFcmTokenLocally(context: Context, token: String) {
        if (token.isBlank()) return
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FCM_TOKEN, token)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun getCachedFcmToken(context: Context): String {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_FCM_TOKEN, "")
                .orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun resolveFirestoreDb(context: Context): FirebaseFirestore? {
        return try {
            val app = FirebaseApp.getInstance()
            val dbId = try {
                context.getString(R.string.firestore_database_id)
            } catch (_: Exception) {
                "ai-studio-260b673c-6e6b-45c7-b680-335d42dc251f"
            }
            if (dbId.isNotBlank() && dbId != "(default)") {
                FirebaseFirestore.getInstance(app, dbId)
            } else {
                FirebaseFirestore.getInstance(app)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore instance unavailable for FCM sync: ${e.message}")
            null
        }
    }

    fun resolveAllFirestoreDbs(context: Context): List<FirebaseFirestore> {
        val primary = resolveFirestoreDb(context)
        val defaultDb = runCatching { FirebaseFirestore.getInstance(FirebaseApp.getInstance()) }.getOrNull()
        return listOfNotNull(primary, defaultDb).distinct()
    }

    /**
     * Retrieves the device's current FCM registration token via FirebaseMessaging
     * and saves it to the user's Firestore profile document (`fcmToken` field)
     * as well as the Partner's salon document if applicable.
     */
    fun fetchAndSaveCurrentUserFcmToken(
        context: Context,
        userId: String?,
        salonId: String? = null,
        onTokenReady: (String) -> Unit = {}
    ) {
        val cleanUid = userId?.trim().orEmpty().ifBlank {
            runCatching { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }.getOrDefault("")
        }
        if (!salonId.isNullOrBlank()) {
            subscribePartnerTopics(cleanUid, salonId)
        }
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        cacheFcmTokenLocally(context, token)
                        if (cleanUid.isNotBlank()) {
                            saveFcmTokenToFirestore(
                                context = context,
                                token = token,
                                explicitUserId = cleanUid,
                                explicitSalonId = salonId
                            )
                        }
                        onTokenReady(token)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Unable to fetch FCM token: ${e.message}")
                    val cached = getCachedFcmToken(context)
                    if (cached.isNotBlank() && cleanUid.isNotBlank()) {
                        saveFcmTokenToFirestore(
                            context = context,
                            token = cached,
                            explicitUserId = cleanUid,
                            explicitSalonId = salonId
                        )
                        onTokenReady(cached)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseMessaging token request failed: ${e.message}")
        }
    }

    /**
     * Persists the FCM token (`fcmToken`) to `users/{uid}` and any owned `salons/{salonId}` document.
     */
    fun saveFcmTokenToFirestore(
        context: Context,
        token: String,
        explicitUserId: String? = null,
        explicitSalonId: String? = null
    ) {
        if (token.isBlank()) return
        val uid = explicitUserId?.trim().orEmpty().ifBlank {
            runCatching { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }.getOrDefault("")
        }
        if (uid.isBlank()) return

        val dbs = resolveAllFirestoreDbs(context)
        val tokenPayload = mapOf(
            "fcmToken" to token,
            "fcmUpdatedAt" to System.currentTimeMillis()
        )

        dbs.forEach { db ->
            db.collection("users").document(uid)
                .set(tokenPayload, SetOptions.merge())
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to save fcmToken to users/$uid: ${e.message}")
                }

            val targetSalonId = explicitSalonId?.trim().orEmpty().ifBlank { "salon_$uid" }
            db.collection("salons").document(targetSalonId).get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        db.collection("salons").document(targetSalonId)
                            .set(mapOf("fcmToken" to token), SetOptions.merge())
                    }
                }
        }
    }

    /**
     * Triggered when a Customer creates a new booking:
     * Fetches the Partner's saved `fcmToken` from Firestore and dispatches a high-priority
     * FCM push notification payload + writes to `fcm_notifications` in all active DB instances.
     */
    fun sendHighPriorityBookingFcmToPartner(
        context: Context,
        partnerId: String,
        salonId: String,
        salonName: String,
        bookingId: String,
        customerName: String,
        customerPhone: String,
        serviceName: String,
        servicePrice: Int,
        timeSlot: String,
        knownPartnerFcmToken: String = ""
    ) {
        val db = resolveFirestoreDb(context) ?: return
        val allDbs = resolveAllFirestoreDbs(context)

        val dispatchWithToken: (String) -> Unit = { partnerFcmToken ->
            val notificationId = "fcm_partner_${bookingId}_${System.currentTimeMillis()}"
            val title = "🔔 New Booking at $salonName!"
            val body = "$customerName booked $serviceName (₹$servicePrice • $timeSlot). Tap to accept."
            val dataPayload = mapOf(
                "type" to "NEW_BOOKING",
                "bookingId" to bookingId,
                "salonId" to salonId,
                "partnerId" to partnerId,
                "salonName" to salonName,
                "customerName" to customerName,
                "customerPhone" to customerPhone,
                "serviceName" to serviceName,
                "servicePrice" to servicePrice.toString(),
                "timeSlot" to timeSlot,
                "title" to title,
                "body" to body
            )
            val fcmQueueDoc = mapOf(
                "id" to notificationId,
                "to" to partnerFcmToken,
                "fcmToken" to partnerFcmToken,
                "recipientRole" to "partner",
                "partnerId" to partnerId,
                "salonId" to salonId,
                "salonName" to salonName,
                "bookingId" to bookingId,
                "priority" to "high",
                "title" to title,
                "body" to body,
                "data" to dataPayload,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )

            allDbs.forEach { targetDb ->
                targetDb.collection("fcm_notifications").document(notificationId)
                    .set(fcmQueueDoc)
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to queue Partner FCM notification doc: ${e.message}")
                    }
            }

            if (partnerFcmToken.isNotBlank()) {
                dispatchDirectFcmHttpIfConfigured(
                    targetToken = partnerFcmToken,
                    title = title,
                    body = body,
                    channelId = CHANNEL_ID,
                    dataPayload = dataPayload
                )
            }
            if (salonId.isNotBlank()) {
                val safeSalonTopic = salonId.trim().replace(Regex("[^a-zA-Z0-9-_.~%]"), "_")
                dispatchDirectFcmHttpIfConfigured(
                    targetToken = "/topics/salon_$safeSalonTopic",
                    title = title,
                    body = body,
                    channelId = CHANNEL_ID,
                    dataPayload = dataPayload
                )
            }
        }

        if (knownPartnerFcmToken.isNotBlank()) {
            dispatchWithToken(knownPartnerFcmToken)
            return
        }

        if (partnerId.isNotBlank()) {
            db.collection("users").document(partnerId).get()
                .addOnSuccessListener { userDoc ->
                    val tokenFromUser = userDoc?.getString("fcmToken")?.trim().orEmpty()
                    if (tokenFromUser.isNotBlank()) {
                        dispatchWithToken(tokenFromUser)
                    } else if (salonId.isNotBlank()) {
                        db.collection("salons").document(salonId).get()
                            .addOnSuccessListener { salonDoc ->
                                val tokenFromSalon = salonDoc?.getString("fcmToken")?.trim().orEmpty()
                                dispatchWithToken(tokenFromSalon)
                            }
                            .addOnFailureListener {
                                dispatchWithToken("")
                            }
                    } else {
                        dispatchWithToken("")
                    }
                }
                .addOnFailureListener {
                    dispatchWithToken("")
                }
        } else if (salonId.isNotBlank()) {
            db.collection("salons").document(salonId).get()
                .addOnSuccessListener { salonDoc ->
                    val tokenFromSalon = salonDoc?.getString("fcmToken")?.trim().orEmpty()
                    dispatchWithToken(tokenFromSalon)
                }
                .addOnFailureListener {
                    dispatchWithToken("")
                }
        }
    }

    /**
     * Triggered when a Partner accepts, adjusts the ETA, or declines a booking:
     * Resolves the Customer's saved `fcmToken` from their Firestore profile (`users/{customerId}`)
     * and dispatches a high-priority FCM push notification to the Customer.
     */
    fun sendHighPriorityBookingStatusFcmToCustomer(
        context: Context,
        customerId: String,
        salonId: String,
        salonName: String,
        bookingId: String,
        serviceName: String,
        allocatedTime: String,
        eventType: String, // "BOOKING_ACCEPTED", "ETA_ADJUSTED", or "BOOKING_DECLINED"
        knownCustomerFcmToken: String = ""
    ) {
        if (customerId.isBlank()) return
        val db = resolveFirestoreDb(context) ?: return
        val allDbs = resolveAllFirestoreDbs(context)

        val dispatchToCustomer: (String) -> Unit = { customerFcmToken ->
            val notificationId = "fcm_customer_${bookingId}_${eventType.lowercase()}_${System.currentTimeMillis()}"
            val cleanSalon = salonName.ifBlank { "TrimTime Salon" }
            val cleanTime = allocatedTime.ifBlank { "Instant Slot" }
            val title = when (eventType) {
                "BOOKING_DECLINED" -> "❌ Booking Declined by $cleanSalon"
                "ETA_ADJUSTED" -> "⏱️ Booking Accepted & ETA Updated at $cleanSalon"
                else -> "✅ Booking Confirmed at $cleanSalon!"
            }
            val body = when (eventType) {
                "BOOKING_DECLINED" -> "Your booking for $serviceName at $cleanSalon was declined by the partner."
                "ETA_ADJUSTED" -> "Reach Salon at $cleanTime ($serviceName at $cleanSalon)."
                else -> "Reach Salon at $cleanTime ($serviceName at $cleanSalon)."
            }
            val dataPayload = mapOf(
                "type" to eventType,
                "bookingId" to bookingId,
                "customerId" to customerId,
                "salonId" to salonId,
                "salonName" to cleanSalon,
                "serviceName" to serviceName,
                "allocatedTime" to cleanTime,
                "timeSlot" to cleanTime,
                "title" to title,
                "body" to body
            )
            val fcmQueueDoc = mapOf(
                "id" to notificationId,
                "to" to customerFcmToken,
                "fcmToken" to customerFcmToken,
                "recipientRole" to "customer",
                "customerId" to customerId,
                "salonId" to salonId,
                "bookingId" to bookingId,
                "eventType" to eventType,
                "priority" to "high",
                "title" to title,
                "body" to body,
                "data" to dataPayload,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )

            allDbs.forEach { targetDb ->
                targetDb.collection("fcm_notifications").document(notificationId)
                    .set(fcmQueueDoc)
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to queue Customer FCM notification doc: ${e.message}")
                    }
            }

            val localDeviceToken = getCachedFcmToken(context)
            val currentAuthUid = runCatching { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }.getOrDefault("")
            if ((customerFcmToken.isNotBlank() && customerFcmToken == localDeviceToken) ||
                (currentAuthUid.isNotBlank() && currentAuthUid == customerId)
            ) {
                showCustomerBookingStatusNotification(
                    context = context,
                    bookingId = bookingId,
                    eventKey = "${bookingId}_${eventType}_${cleanTime}",
                    title = title,
                    body = body
                )
            }

            if (customerFcmToken.isNotBlank()) {
                dispatchDirectFcmHttpIfConfigured(
                    targetToken = customerFcmToken,
                    title = title,
                    body = body,
                    channelId = CUSTOMER_CHANNEL_ID,
                    dataPayload = dataPayload
                )
            }
        }

        if (knownCustomerFcmToken.isNotBlank()) {
            dispatchToCustomer(knownCustomerFcmToken)
            return
        }

        db.collection("users").document(customerId).get()
            .addOnSuccessListener { userDoc ->
                val customerToken = userDoc?.getString("fcmToken")?.trim().orEmpty()
                dispatchToCustomer(customerToken)
            }
            .addOnFailureListener {
                dispatchToCustomer("")
            }
    }

    private fun dispatchDirectFcmHttpIfConfigured(
        targetToken: String,
        title: String,
        body: String,
        channelId: String,
        dataPayload: Map<String, String>
    ) {
        networkExecutor.execute {
            try {
                val serverKey = runCatching {
                    val clazz = Class.forName("com.example.BuildConfig")
                    val field = clazz.fields.firstOrNull { it.name.equals("FCM_SERVER_KEY", ignoreCase = true) }
                    (field?.get(null) as? String).orEmpty()
                }.getOrDefault("")
                if (serverKey.isBlank()) return@execute

                val url = URL("https://fcm.googleapis.com/fcm/send")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "key=$serverKey")
                    connectTimeout = 5000
                    readTimeout = 5000
                    doOutput = true
                }

                val json = JSONObject().apply {
                    put("to", targetToken)
                    put("priority", "high")
                    put("content_available", true)
                    put("notification", JSONObject().apply {
                        put("title", title)
                        put("body", body)
                        put("sound", "loud_alert")
                        put("android_channel_id", channelId)
                        put("channel_id", channelId)
                        put("priority", "high")
                        put("default_vibrate_timings", false)
                    })
                    val dataJson = JSONObject()
                    dataPayload.forEach { (k, v) -> dataJson.put(k, v) }
                    put("data", dataJson)
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(json.toString())
                    writer.flush()
                }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Direct FCM HTTP dispatch skipped: ${e.message}")
            }
        }
    }

    /**
     * Triggers a high-priority Android System Notification + custom `R.raw.loud_alert` audio
     * + strong waveform vibration + temporary WakeLock so the Partner is alerted immediately
     * even if the app is minimized, on the home screen, or the screen is locked.
     */
    fun showIncomingBookingNotification(
        context: Context,
        bookingId: String,
        title: String,
        body: String,
        customerName: String = "Customer",
        serviceName: String = "Grooming Service",
        timeSlot: String = "Instant Slot"
    ) {
        if (bookingId.isNotBlank() && !markBookingNotified(context, bookingId)) {
            return
        }

        val wakeLock = acquireTemporaryWakeLock(context, 10_000L)
        ensureNotificationChannel(context)

        // 1. Trigger loud custom `R.raw.loud_alert` audio (via USAGE_ALARM so it plays in background/lock screen)
        // and strong distinct waveform vibration
        try {
            try {
                activeMediaPlayer?.let { prev ->
                    runCatching {
                        if (prev.isPlaying) prev.stop()
                        prev.release()
                    }
                }
                activeMediaPlayer = null

                val afd = context.resources.openRawResourceFd(R.raw.loud_alert)
                if (afd != null) {
                    val mp = MediaPlayer()
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    mp.setOnCompletionListener { player ->
                        try {
                            player.release()
                        } catch (_: Exception) {
                        }
                        if (activeMediaPlayer === player) {
                            activeMediaPlayer = null
                        }
                        try {
                            if (wakeLock?.isHeld == true) {
                                wakeLock.release()
                            }
                        } catch (_: Exception) {
                        }
                    }
                    mp.prepare()
                    activeMediaPlayer = mp
                    mp.start()
                }
            } catch (audioErr: Exception) {
                Log.w(TAG, "Fallback MediaPlayer for loud_alert: ${audioErr.message}")
                runCatching {
                    MediaPlayer.create(context, R.raw.loud_alert)?.apply {
                        activeMediaPlayer = this
                        setOnCompletionListener { mp ->
                            runCatching { mp.release() }
                            if (activeMediaPlayer === mp) activeMediaPlayer = null
                        }
                        start()
                    }
                }
            }

            try {
                val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager =
                        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                        ?: (@Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator))
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 500, 200, 500, 200, 500),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
                }
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }

        // 2. Build and post the high-priority Android System Notification with full-screen & lock-screen support
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted; loud_alert sound/vibration still played.")
                    return
                }
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_partner_live_queue", true)
                putExtra("booking_id", bookingId)
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                bookingId.hashCode(),
                launchIntent,
                pendingIntentFlags
            )

            val loudAlertUri = Uri.parse("android.resource://${context.packageName}/${R.raw.loud_alert}")
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(loudAlertUri)
                .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .build()

            NotificationManagerCompat.from(context).notify(
                bookingId.hashCode(),
                notification
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post partner system notification: ${e.message}")
        }
    }

    /**
     * Posts a high-priority system notification for the Customer when the Partner accepts,
     * adjusts the ETA, or declines their booking.
     */
    fun showCustomerBookingStatusNotification(
        context: Context,
        bookingId: String,
        eventKey: String,
        title: String,
        body: String
    ) {
        if (eventKey.isNotBlank() && !notifiedCustomerEvents.add(eventKey)) {
            return
        }

        ensureNotificationChannel(context)

        // Trigger audible notification & vibration safely so the Customer hears it immediately
        try {
            try {
                MediaPlayer.create(context, Settings.System.DEFAULT_NOTIFICATION_URI)?.apply {
                    setOnCompletionListener { mp ->
                        try {
                            mp.release()
                        } catch (_: Exception) {
                        }
                    }
                    start()
                }
            } catch (_: Exception) {
            }

            try {
                val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager =
                        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                        ?: (@Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator))
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 400, 150, 400),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 400, 150, 400), -1)
                }
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) return
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_customer_bookings", true)
                putExtra("booking_id", bookingId)
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                eventKey.hashCode(),
                launchIntent,
                pendingIntentFlags
            )

            val notification = NotificationCompat.Builder(context, CUSTOMER_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVibrate(longArrayOf(0, 400, 150, 400))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(
                eventKey.hashCode(),
                notification
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post customer status notification: ${e.message}")
        }
    }
}
