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
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.runtime.remember
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
    currentUserId: String = "",
    hasActiveBooking: Boolean = false,
    isSuspended: Boolean = false,
    onBack: () -> Unit,
    onServiceSelected: (SalonService) -> Unit,
    onBookingBlocked: (String) -> Unit = {},
    onSwitchToPartnerMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val isOwnSalon = currentUserId.isNotBlank() &&
        (salon.ownerId == currentUserId || salon.partnerId == currentUserId)

    val isBookDisabled = isOwnSalon || hasActiveBooking || isSuspended
    val disabledReason = when {
        isOwnSalon -> "This is your own registered salon. Manage it in Partner Mode."
        isSuspended -> "Your account has been suspended for too many cancellations"
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
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("salon_detail_back_button")
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
                            text = "${salon.distance} away • Next slot in ${salon.nextSlotMinutes}m",
                            fontSize = 11.sp,
                            color = AmberPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                SalonHeaderBanner(salon = salon)
            }

            // Pay-at-salon banner
            item {
                PayAtSalonBanner()
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
                    isBusy = salon.isBusy,
                    isOwnSalon = isOwnSalon,
                    isBookDisabled = isBookDisabled,
                    disabledReason = disabledReason,
                    onBookClick = {
                        if (isBookDisabled) {
                            onBookingBlocked(disabledReason)
                        } else {
                            onServiceSelected(service)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SalonHeaderBanner(salon: Salon) {
    val rawPhotoData = salon.resolvedPhotoData.trim()
    val salonCoverBitmap = remember(rawPhotoData) {
        SalonImageUtils.decodeBase64ToImageBitmap(rawPhotoData)
    }
    val hasCustomPhoto = salonCoverBitmap != null || rawPhotoData.isNotBlank()
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 124.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1E1B4B), Color(0xFF451A03), Color(0xFF0F172A))
                        )
                    )
            ) {
                if (salonCoverBitmap != null) {
                    Image(
                        bitmap = salonCoverBitmap,
                        contentDescription = "${salon.name} Cover Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else if (rawPhotoData.isNotBlank()) {
                    AsyncImage(
                        model = rawPhotoData,
                        contentDescription = "${salon.name} Cover Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }

                if (hasCustomPhoto) {
                    // Strong multi-stop dark vertical gradient scrim so text & badges always have crisp contrast
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(alpha = 0.55f),
                                        0.45f to Color(0xCC090D16),
                                        1.0f to Color(0xF5090D16)
                                    )
                                )
                            )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF064E3B))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = AmberPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${salon.rating} (${salon.reviewCount} reviews)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Real-Time Barber Busy/Free Status Badge
                            val isBusy = salon.isBusy
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isBusy) Color(0xFF451A03) else Color(0xFF064E3B))
                                    .border(1.dp, if (isBusy) Color(0xFFF97316) else GreenSuccess, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("salon_detail_status_badge")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = if (isBusy) "🔴" else "🟢",
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = if (isBusy) "Barber Busy (15-20m wait)" else "Available Now",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBusy) Color(0xFFFDBA74) else GreenSuccess,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier.then(
                                if (hasCustomPhoto) {
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xB3090D16))
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                } else {
                                    Modifier
                                }
                            )
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
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xD90F172A))
                            .border(1.dp, AmberPrimary.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = "Salon",
                            tint = AmberPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            val context = LocalContext.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .testTag("salon_detail_call_salon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call Salon Directly",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Call Salon Directly",
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
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .testTag("salon_detail_get_directions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = "Get Directions",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Get Directions",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
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
                    text = service.name,
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
                        Text(
                            text = "${service.durationMinutes} mins",
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

                // Booking Button: changes to "Join Waitlist" (Orange) if status is BUSY
                Button(
                    onClick = onBookClick,
                    enabled = !isBookDisabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBusy) Color(0xFFEA580C) else AmberPrimary,
                        disabledContainerColor = Color(0xFF334155),
                        contentColor = if (isBusy) Color.White else Color(0xFF1E1402),
                        disabledContentColor = Color(0xFF94A3B8)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("book_service_btn_${service.id}")
                ) {
                    Text(
                        text = when {
                            isBookDisabled -> "Disabled"
                            isBusy -> "Join Waitlist"
                            else -> "Pick Slot"
                        },
                        color = when {
                            isBookDisabled -> Color(0xFF94A3B8)
                            isBusy -> Color.White
                            else -> Color(0xFF1E1402)
                        },
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
