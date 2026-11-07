package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
 * Handles incoming high-priority data messages in the background and foreground for both:
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

        val data = remoteMessage.data
        val messageType = data["type"]?.trim()?.uppercase() ?: "NEW_BOOKING"
        val bookingId = data["bookingId"]
            ?: data["id"]
            ?: "booking_${System.currentTimeMillis()}"
        val customerName = data["customerName"] ?: "Customer"
        val serviceName = data["serviceName"] ?: data["service"] ?: "Salon Service"
        val servicePrice = data["servicePrice"] ?: ""
        val timeSlot = data["timeSlot"] ?: data["allocatedTime"] ?: "Instant Slot"
        val salonName = data["salonName"] ?: "TrimTime Salon"

        if (messageType in setOf("BOOKING_ACCEPTED", "ETA_ADJUSTED", "BOOKING_DECLINED")) {
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
    }

    companion object {
        private const val TAG = "TrimTimeFCM"
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
    const val CUSTOMER_CHANNEL_ID = "trimtime_customer_booking_updates"
    private const val CHANNEL_NAME = "Partner Incoming Booking Alerts"
    private const val CHANNEL_DESC = "Loud custom alarm and vibration alerts for new customer bookings"
    private const val CUSTOMER_CHANNEL_NAME = "Customer Booking & ETA Updates"
    private const val CUSTOMER_CHANNEL_DESC = "High-priority alerts when a Partner accepts, adjusts ETA, or declines your booking"
    private const val PREFS_NAME = "trimtime_fcm_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"

    private val notifiedBookingIds: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val notifiedCustomerEvents: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private var activeMediaPlayer: MediaPlayer? = null

    /**
     * Creates the high-importance notification channels on Android 8.0+ with the custom `R.raw.loud_alert`
     * sound for Partners and high-priority alerts for Customers.
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

                val partnerChannel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                    setSound(loudAlertUri, audioAttributes)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(partnerChannel)

                val customerChannel = NotificationChannel(
                    CUSTOMER_CHANNEL_ID,
                    CUSTOMER_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CUSTOMER_CHANNEL_DESC
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 350, 150, 350)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(customerChannel)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create notification channels: ${e.message}")
            }
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

        val db = resolveFirestoreDb(context) ?: return
        val tokenPayload = mapOf(
            "fcmToken" to token,
            "fcmUpdatedAt" to System.currentTimeMillis()
        )

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

    /**
     * Triggered when a Customer creates a new booking:
     * Fetches the Partner's saved `fcmToken` from Firestore and dispatches a high-priority
     * FCM push notification payload.
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
                "bookingId" to bookingId,
                "priority" to "high",
                "title" to title,
                "body" to body,
                "data" to dataPayload,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )

            db.collection("fcm_notifications").document(notificationId)
                .set(fcmQueueDoc)
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to queue Partner FCM notification doc: ${e.message}")
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
                "ETA_ADJUSTED" -> "Your booking for $serviceName is confirmed! Adjusted ETA / Slot: $cleanTime."
                else -> "Your booking for $serviceName at $cleanSalon is confirmed ($cleanTime)."
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

            db.collection("fcm_notifications").document(notificationId)
                .set(fcmQueueDoc)
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to queue Customer FCM notification doc: ${e.message}")
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
                    put("notification", JSONObject().apply {
                        put("title", title)
                        put("body", body)
                        put("sound", "loud_alert")
                        put("android_channel_id", channelId)
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
     * + strong waveform vibration so the Partner is alerted even if the app is minimized or closed.
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
        if (bookingId.isNotBlank() && !notifiedBookingIds.add(bookingId)) {
            return
        }

        ensureNotificationChannel(context)

        // 1. Trigger loud custom `R.raw.loud_alert` audio & strong distinct vibration (lifecycle-safe)
        try {
            try {
                MediaPlayer.create(context, R.raw.loud_alert)?.apply {
                    activeMediaPlayer = this
                    setOnCompletionListener { mp ->
                        try {
                            mp.release()
                        } catch (_: Exception) {
                        }
                        if (activeMediaPlayer === mp) {
                            activeMediaPlayer = null
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

        // 2. Build and post the high-priority Android System Notification
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
                .setSound(loudAlertUri)
                .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
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
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVibrate(longArrayOf(0, 350, 150, 350))
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
