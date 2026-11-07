package com.example.ui.customer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.Booking
import com.example.model.Salon
import com.example.model.SalonService
import com.example.ui.components.TRIMTIME_SUPPORT_PHONE
import com.example.ui.components.launchGeoDirections
import com.example.ui.components.launchPhoneDialer
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedAlert
import com.example.util.SalonImageUtils

@Composable
fun SalonDetailScreen(
    salon: Salon,
    activeLiveQueueCount: Int = 0,
    isGlobalRushModeEnabled: Boolean = false,
    currentUserId: String = "",
    hasActiveBooking: Boolean = false,
    isSuspended: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onBack: () -> Unit,
    onServiceSelected: (SalonService) -> Unit,
    onBookingBlocked: (String) -> Unit = {},
    onSwitchToPartnerMode: () -> Unit = {},
    onRateSalon: (Int, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var showRatingDialog by remember { mutableStateOf(false) }

    // Strict Real-Time Firestore check on the Salon document's `isOpen` & `currentStatus`
    var liveFirestoreIsOpen by remember(salon.id, salon.isOpen, salon.currentStatus) {
        mutableStateOf(salon.isAcceptingBookings)
    }

    DisposableEffect(salon.id, salon.ownerId, salon.partnerId) {
        val listeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
        try {
            val dbId = try {
                context.getString(com.example.R.string.firestore_database_id).trim()
            } catch (_: Exception) {
                ""
            }
            val namedDb = try {
                if (dbId.isNotEmpty() && dbId != "(default)") {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance(
                        com.google.firebase.FirebaseApp.getInstance(),
                        dbId
                    )
                } else {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                }
            } catch (_: Exception) {
                null
            }
            val defDb = try {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
            } catch (_: Exception) {
                null
            }
            val dbs = listOfNotNull(namedDb, defDb).distinct()
            val candidateDocIds = setOf(
                salon.id.trim(),
                salon.partnerId.trim(),
                salon.ownerId.trim(),
                if (salon.partnerId.isNotBlank()) "salon_${salon.partnerId.trim()}" else "",
                if (salon.ownerId.isNotBlank()) "salon_${salon.ownerId.trim()}" else ""
            ).filter { it.isNotBlank() }

            val docStates = mutableMapOf<String, Boolean>()
            dbs.forEach { db ->
                candidateDocIds.forEach { docId ->
                    val reg = db.collection("salons").document(docId)
                        .addSnapshotListener { snap, err ->
                            if (err == null && snap != null && snap.exists()) {
                                val docOpen = snap.getBoolean("isOpen")
                                    ?: snap.getString("isOpen")?.toBooleanStrictOrNull()
                                    ?: snap.getBoolean("isShopOpen")
                                    ?: true
                                val docStatus = snap.getString("currentStatus")?.trim().orEmpty()
                                val docSalonStatus = snap.getString("salonStatus")?.trim().orEmpty()
                                val docSuspended = snap.getBoolean("isSuspended") ?: false
                                val isDocAccepting = docOpen &&
                                    !docStatus.equals("CLOSED", ignoreCase = true) &&
                                    !docSalonStatus.equals("closed", ignoreCase = true) &&
                                    !docSalonStatus.equals("suspended", ignoreCase = true) &&
                                    !docSuspended
                                docStates["${db.hashCode()}_$docId"] = isDocAccepting
                                // If ANY matching document for this salon is marked closed in Firestore, enforce closed state immediately
                                liveFirestoreIsOpen = docStates.values.all { it }
                            }
                        }
                    listeners.add(reg)
                }
            }
        } catch (_: Exception) {
        }
        onDispose {
            listeners.forEach {
                try {
                    it.remove()
                } catch (_: Exception) {
                }
            }
        }
    }

    val isSalonOpen = liveFirestoreIsOpen && salon.isAcceptingBookings

    val defaultServiceDuration = remember(salon.services) {
        salon.services
            .map { if (it.duration > 0) it.duration else it.durationMinutes }
            .filter { it > 0 }
            .average()
            .takeIf { !it.isNaN() }
            ?.toInt() ?: 20
    }
    val isLocalRushMode = remember(activeLiveQueueCount) {
        Booking.isLocalRushMode(activeLiveQueueCount)
    }
    val isRushActive = remember(isGlobalRushModeEnabled, isLocalRushMode) {
        isGlobalRushModeEnabled || isLocalRushMode
    }
    val trafficLightStatus = remember(activeLiveQueueCount) {
        Booking.getTrafficLightStatus(activeLiveQueueCount)
    }
    val systemEtaClockTime = remember(activeLiveQueueCount, defaultServiceDuration) {
        Booking.calculateFormattedEtaClockTime(activeLiveQueueCount, defaultServiceDuration)
    }
    val isExceedingClosingTime = remember(salon.closingTime, activeLiveQueueCount, defaultServiceDuration) {
        Booking.isEtaExceedingClosingTime(
            closingTimeStr = salon.closingTime.ifBlank { "9:00 PM" },
            activeBookingsCount = activeLiveQueueCount,
            defaultServiceDurationMinutes = defaultServiceDuration
        )
    }

    val isOwnSalon = currentUserId.isNotBlank() &&
        (salon.ownerId == currentUserId || salon.partnerId == currentUserId)

    val isBookDisabled = !isSalonOpen || isOwnSalon || hasActiveBooking || isSuspended || isExceedingClosingTime
    val disabledReason = when {
        !isSalonOpen -> "Currently Closed - Not accepting new bookings"
        isOwnSalon -> "This is your own registered salon. Manage it in Partner Mode."
        isSuspended -> "Your account has been suspended for too many cancellations"
        isExceedingClosingTime -> "Booking Blocked: Newly calculated ETA ($systemEtaClockTime) exceeds official closing time (${salon.closingTime.ifBlank { "9:00 PM" }})."
        hasActiveBooking -> "You already have an active booking. Please complete or cancel it first."
        else -> ""
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            Surface(
                color = DarkSurface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 12.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("salon_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = salon.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (!isSalonOpen) {
                                "${salon.distance} away • 🔴 Currently Closed - Not accepting new bookings"
                            } else {
                                "${salon.distance} away • ${trafficLightStatus.badgeText}"
                            },
                            fontSize = 11.sp,
                            color = if (!isSalonOpen) {
                                RedAlert
                            } else {
                                when (trafficLightStatus) {
                                    Booking.QueueTrafficLightStatus.NO_WAIT -> GreenSuccess
                                    Booking.QueueTrafficLightStatus.SLIGHT_WAIT -> AmberPrimary
                                    Booking.QueueTrafficLightStatus.HIGH_RUSH -> RedAlert
                                }
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("salon_detail_favorite_btn")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                                tint = if (isFavorite) Color(0xFFF43F5E) else Color(0xFFCBD5E1)
                            )
                        }
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("salon_detail_close_btn")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF334155), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Salon Details",
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("salon_detail_services_list"),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Salon Header Card
            item {
                SalonHeaderBanner(
                    salon = salon,
                    isSalonOpen = isSalonOpen,
                    trafficLightStatus = trafficLightStatus,
                    onRateClick = { showRatingDialog = true }
                )
            }

            // Prominent Red Warning Overlay / Banner when Salon is Closed (`isOpen == false`)
            if (!isSalonOpen) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("salon_closed_warning_banner"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            width = 1.5.dp,
                            brush = androidx.compose.ui.graphics.SolidColor(RedAlert)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(RedAlert.copy(alpha = 0.2f))
                                    .border(1.dp, RedAlert, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Salon Closed",
                                    tint = RedAlert,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Currently Closed - Not accepting new bookings",
                                    color = Color(0xFFFECACA),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "This salon has manually switched its shop status to Closed. Online bookings are disabled until the partner re-opens.",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Pay-at-salon banner
            item {
                PayAtSalonBanner()
            }

            // Simple Traffic Light Live Status Banner (Only shown when shop is Open)
            if (isSalonOpen) {
                item {
                    val (badgeBg, badgeBorder, badgeTextColor) = when (trafficLightStatus) {
                        Booking.QueueTrafficLightStatus.NO_WAIT -> Triple(
                            Color(0xFF064E3B),
                            GreenSuccess,
                            Color(0xFFA7F3D0)
                        )
                        Booking.QueueTrafficLightStatus.SLIGHT_WAIT -> Triple(
                            Color(0xFF451A03),
                            AmberPrimary,
                            Color(0xFFFDE68A)
                        )
                        Booking.QueueTrafficLightStatus.HIGH_RUSH -> Triple(
                            Color(0xFF450A0A),
                            RedAlert,
                            Color(0xFFFECACA)
                        )
                    }
                    val friendlySubtitle = when (trafficLightStatus) {
                        Booking.QueueTrafficLightStatus.NO_WAIT ->
                            "Instant chair availability • Tap Book Now to reserve your slot."
                        Booking.QueueTrafficLightStatus.SLIGHT_WAIT ->
                            "Next available slot around $systemEtaClockTime • Book ahead to skip waiting at the shop."
                        Booking.QueueTrafficLightStatus.HIGH_RUSH ->
                            "High demand right now • Next slot around $systemEtaClockTime. Book ahead and reach directly at your confirmed time."
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("salon_live_eta_banner"),
                        colors = CardDefaults.cardColors(containerColor = badgeBg.copy(alpha = 0.7f)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(badgeBorder)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(badgeBg)
                                            .border(1.dp, badgeBorder, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                            .testTag("salon_traffic_light_badge")
                                    ) {
                                        Text(
                                            text = trafficLightStatus.badgeText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = badgeTextColor
                                        )
                                    }
                                    if (activeLiveQueueCount > 0 || isRushActive) {
                                        Text(
                                            text = "Slot around $systemEtaClockTime",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                Text(
                                    text = friendlySubtitle,
                                    fontSize = 11.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }
            }

            // Real-Time Busy Status Banner
            if (salon.isBusy) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("barber_busy_warning_banner"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF381403)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFEA580C))),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = "✂️", fontSize = 22.sp)
                            Column {
                                Text(
                                    text = "🔴 Barber is currently cutting hair!",
                                    color = Color(0xFFFED7AA),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Approximate 15–20 minutes wait time. You can join the live waitlist to hold your spot.",
                                    color = Color(0xFFFFEDD5),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Rule 3: Suspension Warning Banner
            if (isSuspended) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("salon_suspended_notice"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1214)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RedAlert)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = RedAlert, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Your account has been suspended for too many cancellations",
                                color = RedAlert,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Rule 2: Active Booking Warning Banner
            if (hasActiveBooking && !isSuspended) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("salon_active_booking_notice"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2210)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AmberPrimary)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(20.dp))
                            Text(
                                text = "You already have an active booking. Please complete or cancel it first.",
                                color = AmberPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Rule 4: Prevent Self-Booking Banner (Owner Viewing Their Own Salon in Customer Mode)
            if (isOwnSalon) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("own_salon_booking_blocked_banner"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(AmberPrimary)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "This is your own registered salon. Manage it in Partner Mode.",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Button(
                                onClick = onSwitchToPartnerMode,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberPrimary,
                                    contentColor = Color(0xFF1E1402)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manage_own_salon_partner_btn")
                            ) {
                                Text(
                                    text = "Manage in Partner Mode",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            // Services catalog title
            item {
                Text(
                    text = "CATALOG & INSTANT TIME SLOTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Service items
            items(salon.services, key = { it.id }) { service ->
                ServiceCatalogItem(
                    service = service,
                    isSalonOpen = isSalonOpen,
                    isBusy = salon.isBusy,
                    isOwnSalon = isOwnSalon,
                    isBookDisabled = isBookDisabled,
                    disabledReason = disabledReason,
                    onBookClick = {
                        if (!isSalonOpen || isBookDisabled) {
                            onBookingBlocked(disabledReason)
                        } else {
                            onServiceSelected(service)
                        }
                    }
                )
            }
        }
    }

    if (showRatingDialog) {
        CustomerRatingDialog(
            salon = salon,
            onDismiss = { showRatingDialog = false },
            onSubmit = { rating, review ->
                onRateSalon(rating, review)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SalonHeaderBanner(
    salon: Salon,
    isSalonOpen: Boolean = salon.isAcceptingBookings,
    trafficLightStatus: Booking.QueueTrafficLightStatus = Booking.QueueTrafficLightStatus.NO_WAIT,
    onRateClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val rawPhotoData = salon.imageUrl.trim().takeIf { it.isNotEmpty() } ?: salon.resolvedPhotoData.trim()
    val coilImageModel = remember(rawPhotoData) {
        SalonImageUtils.resolveCoilImageModel(rawPhotoData)
    }
    val salonCoverBitmap = remember(rawPhotoData) {
        SalonImageUtils.decodeBase64ToImageBitmap(rawPhotoData)
    }
    val hasCustomPhoto = coilImageModel != null || salonCoverBitmap != null || rawPhotoData.isNotBlank()
    val highContrastShadow = Shadow(
        color = Color.Black,
        offset = Offset(0f, 2f),
        blurRadius = 8f
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Tall 250.dp Salon Profile Cover Photo — unobstructed center with bottom gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1E1B4B), Color(0xFF451A03), Color(0xFF0F172A))
                        )
                    )
            ) {
                if (coilImageModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(coilImageModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${salon.name} Cover Photo",
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (salonCoverBitmap != null) {
                    Image(
                        bitmap = salonCoverBitmap,
                        contentDescription = "${salon.name} Cover Photo",
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom gradient overlay behind the salon name so text remains easily readable while keeping the photo unobstructed
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.08f),
                                    0.55f to Color.Transparent,
                                    0.80f to Color(0xB3090D16),
                                    1.0f to Color(0xF2090D16)
                                )
                            )
                        )
                )

                // Bottom Edge of Image: Salon Name, Verified Badge, Tagline & Scissors Emblem
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = salon.name,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    shadow = highContrastShadow
                                ),
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (salon.isPhoneVerified) {
                                com.example.ui.components.VerifiedBlueTickIcon(size = 18.dp)
                            }
                        }
                        if (salon.tagline.isNotBlank()) {
                            Text(
                                text = salon.tagline,
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xD90F172A))
                            .border(1.dp, AmberPrimary.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = "Salon",
                            tint = AmberPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 2. Repositioned Status Tags & Details cleanly below the image (unobstructed salon photo)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val ratingText = if (salon.totalRatings > 0) {
                        String.format(java.util.Locale.US, "★ %.1f (%d ratings)", salon.effectiveRating, salon.totalRatings)
                    } else if (salon.averageRating > 0.0) {
                        String.format(java.util.Locale.US, "★ %.1f", salon.effectiveRating)
                    } else if (salon.reviewCount > 0) {
                        String.format(java.util.Locale.US, "★ %.1f (%d reviews)", salon.rating, salon.reviewCount)
                    } else {
                        "★ New"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF064E3B))
                            .clickable { onRateClick() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ratingText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // AC / Non-AC Status Badge
                    if (salon.isAcSalon) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0C4A6E))
                                .border(0.8.dp, Color(0xFF38BDF8), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("detail_ac_badge")
                        ) {
                            Text(
                                text = "❄️ AC Salon",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7DD3FC),
                                maxLines = 1
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                                .border(0.8.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("detail_non_ac_badge")
                        ) {
                            Text(
                                text = "Non-AC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF94A3B8),
                                maxLines = 1
                            )
                        }
                    }

                    // Status Badge: Prominent Red Closed Badge when !isSalonOpen, else Traffic Light Badge
                    if (!isSalonOpen) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF450A0A))
                                .border(1.dp, RedAlert, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("salon_detail_status_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🔴",
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "Currently Closed - Not accepting new bookings",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFECACA),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else {
                        // Simple Traffic Light Status Badge:
                        // 🟢 "No Wait" (Queue 0-1), 🟡 "Slight Wait" (Queue 2-3), 🔴 "High Rush" (Queue 4+)
                        val (statusBgColor, statusBorderColor, statusLabelColor) = when (trafficLightStatus) {
                            Booking.QueueTrafficLightStatus.NO_WAIT -> Triple(
                                Color(0xFF064E3B),
                                GreenSuccess,
                                GreenSuccess
                            )
                            Booking.QueueTrafficLightStatus.SLIGHT_WAIT -> Triple(
                                Color(0xFF451A03),
                                AmberPrimary,
                                Color(0xFFFDE68A)
                            )
                            Booking.QueueTrafficLightStatus.HIGH_RUSH -> Triple(
                                Color(0xFF450A0A),
                                RedAlert,
                                Color(0xFFFCA5A5)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusBgColor)
                                .border(1.dp, statusBorderColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("salon_detail_status_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = trafficLightStatus.emoji,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = trafficLightStatus.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusLabelColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = salon.shopAddress.ifBlank { salon.location },
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val salonPhone = salon.phone.ifBlank { TRIMTIME_SUPPORT_PHONE }
                            launchPhoneDialer(context, salonPhone)
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GreenSuccess),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenSuccess),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .testTag("salon_detail_call_salon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call Salon Directly",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Call",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            launchGeoDirections(
                                context = context,
                                latitude = salon.latitude,
                                longitude = salon.longitude,
                                salonName = salon.name,
                                fallbackAddress = salon.location
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .heightIn(min = 42.dp)
                            .testTag("salon_detail_get_directions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = "Get Directions",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Directions",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onRateClick,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberPrimary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .heightIn(min = 42.dp)
                            .testTag("salon_detail_rate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rate Salon",
                            tint = AmberPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Rate",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // FlowRow for Amenity Pills so "Zero Queue" never breaks vertically letter-by-letter
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AmenityTag(text = "Air Conditioned")
                    AmenityTag(text = "Instant Walk-in Priority")
                    AmenityTag(text = "Zero Queue")
                }
            }
        }
    }
}

@Composable
fun AmenityTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurface)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "✓ $text",
            fontSize = 10.sp,
            color = Color(0xFF94A3B8),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun PayAtSalonBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F291E))
            .border(1.dp, Color(0xFF065F46), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Payments,
            contentDescription = "Pay at Salon",
            tint = GreenSuccess,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                text = "Pay at Salon Policy (₹0 Advance Required)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA7F3D0)
            )
            Text(
                text = "Select a service & choose a slot. Pay the barber directly in cash or UPI after grooming.",
                fontSize = 11.sp,
                color = Color(0xFFD1FAE5)
            )
        }
    }
}

@Composable
fun ServiceCatalogItem(
    service: SalonService,
    isSalonOpen: Boolean = true,
    isBusy: Boolean = false,
    isOwnSalon: Boolean = false,
    isBookDisabled: Boolean = false,
    disabledReason: String = "",
    onBookClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("service_item_${service.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (service.isPopular) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF451A03))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "★ MOST BOOKED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = service.serviceName.ifBlank { service.name },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (service.description.isNotBlank()) {
                    Text(
                        text = service.description,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Duration",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        val displayDuration = if (service.duration > 0) service.duration else service.durationMinutes
                        Text(
                            text = "$displayDuration mins",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Text(
                        text = "•",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        softWrap = false
                    )

                    Text(
                        text = "₹${service.price}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AmberPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (!isOwnSalon) {
                Spacer(modifier = Modifier.width(12.dp))

                if (!isSalonOpen) {
                    // When isOpen is false, completely disable the 'Book Now' button and show 'Shop Closed'
                    Button(
                        onClick = {},
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF450A0A),
                            disabledContainerColor = Color(0xFF450A0A),
                            contentColor = Color(0xFFFCA5A5),
                            disabledContentColor = Color(0xFFFCA5A5)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("book_service_btn_${service.id}")
                    ) {
                        Text(
                            text = "Closed",
                            color = Color(0xFFFCA5A5),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                } else {
                    // Booking Button: "Book Now"
                    Button(
                        onClick = onBookClick,
                        enabled = !isBookDisabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            disabledContainerColor = Color(0xFF334155),
                            contentColor = Color(0xFF1E1402),
                            disabledContentColor = Color(0xFF94A3B8)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("book_service_btn_${service.id}")
                    ) {
                        Text(
                            text = if (isBookDisabled) "Unavailable" else "Book Now",
                            color = if (isBookDisabled) Color(0xFF94A3B8) else Color(0xFF1E1402),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
