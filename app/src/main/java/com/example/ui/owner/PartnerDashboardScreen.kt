package com.example.ui.owner

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Booking
import com.example.model.Salon
import com.example.model.SalonService
import com.example.model.UserProfile
import com.example.ui.components.PartnerLocationPickerOsmMap
import com.example.ui.components.fetchDeviceGpsLocation
import com.example.ui.components.hasLocationPermission
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedAlert
import com.example.util.SalonImageUtils

enum class PartnerTab(val title: String) {
    LIVE_QUEUE("Live Queue"),
    SERVICES_PRICING("Services & Pricing"),
    SHOP_PROFILE("Shop Profile")
}

/**
 * Partner Mode (Salon Owner Dashboard) UI in Jetpack Compose:
 * Zero-clutter, bold, high-contrast interface designed for salon barbers & shop owners.
 * 100% connected to real-time Cloud Firestore (`salons` and `bookings` snapshot listeners).
 */
@Composable
fun PartnerDashboardScreen(
    salons: List<Salon>,
    partnerProfile: UserProfile?,
    selectedSalonId: String?,
    onSelectSalonId: (String) -> Unit,
    allBookings: List<Booking>,
    activeFilter: String,
    onFilterChange: (String) -> Unit,
    onOpenPinVerification: (Booking) -> Unit,
    onAcceptBooking: (String) -> Unit = {},
    onRejectBooking: (String) -> Unit = {},
    onToggleSalonOpen: (String) -> Unit,
    onSetSalonOpen: (String, Boolean) -> Unit = { id, _ -> onToggleSalonOpen(id) },
    onSetSalonBusyStatus: (String, String) -> Unit = { _, _ -> },
    onUpdateChairAvailability: (String, String, Int, Int) -> Unit = { _, _, _, _ -> },
    onSaveMenuPrices: (String, Int, Int, Int, Int) -> Unit = { _, _, _, _, _ -> },
    onAddService: (String, String, String, Int, Int, String) -> Unit = { _, _, _, _, _, _ -> },
    onEditService: (String, SalonService) -> Unit = { _, _ -> },
    onDeleteService: (String, String, String) -> Unit = { _, _, _ -> },
    onSaveShopLocation: (String, String, String, Double, Double) -> Unit = { _, _, _, _, _ -> },
    onSaveFullProfile: (String, String, String, String, String, String, Double, Double, Int) -> Unit = { salonId, _, _, _, addr, lm, lat, lng, _ ->
        onSaveShopLocation(salonId, addr, lm, lat, lng)
    },
    onSaveShopPhoto: (String, String) -> Unit = { _, _ -> },
    onExitPartnerMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Logged-in Partner manages only their own salon: match by ownerId / partnerId / profile.salonId first
    val currentSalon = salons.find {
        !partnerProfile?.id.isNullOrBlank() &&
            (it.ownerId == partnerProfile?.id || it.partnerId == partnerProfile?.id || it.id == "salon_${partnerProfile?.id}")
    }
        ?: salons.find {
            !partnerProfile?.salonId.isNullOrBlank() && it.id == partnerProfile?.salonId
        }
        ?: salons.find { it.id == selectedSalonId }
        ?: salons.firstOrNull()

    // Filter bookings in real time for this salon
    val salonBookings = if (currentSalon != null) {
        allBookings.filter { it.salonId == currentSalon.id }
    } else {
        allBookings
    }

    // Active Queue includes both "pending" (new incoming) and "accepted" (confirmed waiting for 4-digit PIN)
    val pendingBookings = salonBookings.filter {
        it.status.equals("pending", ignoreCase = true) || it.status.equals("accepted", ignoreCase = true)
    }
    val completedBookings = salonBookings.filter { it.status.equals("completed", ignoreCase = true) }

    // Today's Summary calculations
    val todayAppointmentsCount = salonBookings.count {
        !it.status.equals("rejected", ignoreCase = true) && !it.status.equals("cancelled", ignoreCase = true)
    }
    val todayEarnings = completedBookings.sumOf { it.servicePrice }
    val pendingCashToCollect = pendingBookings.sumOf { it.servicePrice }

    // Active Tab in Partner Dashboard
    var currentTab by remember { mutableStateOf(PartnerTab.LIVE_QUEUE) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Partner Top Bar (manages only the logged-in Partner's own salon)
        PartnerTopBar(
            partnerName = partnerProfile?.name ?: "Salon Partner",
            salonName = currentSalon?.name ?: "My Salon",
            onExitPartnerMode = onExitPartnerMode
        )

        // Navigation Tabs (Live Queue / Services & Pricing / Shop Profile)
        PartnerDashboardTabs(
            currentTab = currentTab,
            pendingCount = pendingBookings.size,
            onTabSelected = { currentTab = it }
        )

        when (currentTab) {
            PartnerTab.LIVE_QUEUE -> {
                PartnerLiveQueueTabContent(
                    salon = currentSalon,
                    onSetSalonOpen = onSetSalonOpen,
                    onSetSalonBusyStatus = onSetSalonBusyStatus,
                    onUpdateChairAvailability = onUpdateChairAvailability,
                    todayEarnings = todayEarnings,
                    todayAppointmentsCount = todayAppointmentsCount,
                    pendingCashToCollect = pendingCashToCollect,
                    pendingBookings = pendingBookings,
                    completedBookings = completedBookings,
                    onOpenPinVerification = onOpenPinVerification,
                    onAcceptBooking = onAcceptBooking,
                    onRejectBooking = onRejectBooking,
                    onNavigateToProfile = { currentTab = PartnerTab.SHOP_PROFILE }
                )
            }

            PartnerTab.SERVICES_PRICING -> {
                PartnerMenuManagerTabContent(
                    salon = currentSalon,
                    onSavePrices = onSaveMenuPrices,
                    onAddService = onAddService,
                    onEditService = onEditService,
                    onDeleteService = onDeleteService
                )
            }

            PartnerTab.SHOP_PROFILE -> {
                PartnerShopProfileTabContent(
                    salon = currentSalon,
                    partnerProfile = partnerProfile,
                    onSaveLocation = onSaveShopLocation,
                    onSaveFullProfile = onSaveFullProfile,
                    onSaveShopPhoto = onSaveShopPhoto
                )
            }
        }
    }
}

/**
 * Top Header bar for Partner Mode
 */
@Composable
fun PartnerTopBar(
    partnerName: String,
    salonName: String,
    onExitPartnerMode: () -> Unit
) {
    Surface(
        color = Color(0xFF111827),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2563EB)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Salon Partner Mode",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = salonName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Partner Mode • $partnerName",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 1-Tap Switch back to Customer Mode button
            OutlinedButton(
                onClick = onExitPartnerMode,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(AmberPrimary)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF2E2210)
                ),
                modifier = Modifier.testTag("exit_partner_mode_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Switch to Customer Mode",
                    tint = AmberPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Customer Mode",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberPrimary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 3 Tabs: Live Queue, Services & Pricing, Shop Profile
 */
@Composable
fun PartnerDashboardTabs(
    currentTab: PartnerTab,
    pendingCount: Int,
    onTabSelected: (PartnerTab) -> Unit
) {
    TabRow(
        selectedTabIndex = currentTab.ordinal,
        containerColor = Color(0xFF0F172A),
        contentColor = AmberPrimary,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(tabPositions[currentTab.ordinal]),
                color = AmberPrimary,
                height = 3.dp
            )
        }
    ) {
        PartnerTab.values().forEach { tab ->
            val isSelected = currentTab == tab
            Tab(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = tab.title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) AmberPrimary else Color(0xFF94A3B8)
                        )
                        if (tab == PartnerTab.LIVE_QUEUE && pendingCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(AmberPrimary)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "$pendingCount",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1E1402)
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("partner_tab_${tab.name}")
            )
        }
    }
}

/**
 * TAB 1: Live Queue (Includes Live Switch, Live Chairs & Waiting Time, Today's Summary & Incoming Customer Bookings)
 */
@Composable
fun PartnerLiveQueueTabContent(
    salon: Salon?,
    onSetSalonOpen: (String, Boolean) -> Unit,
    onSetSalonBusyStatus: (String, String) -> Unit = { _, _ -> },
    onUpdateChairAvailability: (String, String, Int, Int) -> Unit = { _, _, _, _ -> },
    todayEarnings: Int,
    todayAppointmentsCount: Int,
    pendingCashToCollect: Int,
    pendingBookings: List<Booking>,
    completedBookings: List<Booking>,
    onOpenPinVerification: (Booking) -> Unit,
    onAcceptBooking: (String) -> Unit = {},
    onRejectBooking: (String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("partner_live_queue_feed"),
            contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. THE "LIVE SWITCH": Massive, highly visible Toggle Button
        if (salon != null) {
            item {
                TheLiveSwitch(
                    isOpen = salon.isOpen,
                    hasLocationSetup = salon.hasLocationSetup,
                    onNavigateToProfile = onNavigateToProfile,
                    onToggle = { newStatus ->
                        onSetSalonOpen(salon.id, newStatus)
                    }
                )
            }

            // Real-Time Busy/Free Status + Available Chairs & Waiting Time System:
            item {
                BarberAvailabilityToggleCard(
                    currentStatus = salon.currentStatus,
                    availableChairs = salon.availableChairs,
                    waitingTimeMinutes = salon.waitingTimeMinutes,
                    isShopOpen = salon.isOpen,
                    onStatusChange = { newStatus ->
                        onSetSalonBusyStatus(salon.id, newStatus)
                    },
                    onChairAndWaitChange = { status, chairs, waitMins ->
                        onUpdateChairAvailability(salon.id, status, chairs, waitMins)
                    }
                )
            }
        }

        // 2. TODAY'S SUMMARY: Stats cards for "Today's Earnings (₹)" & "Total Appointments Today"
        item {
            TodaysSummarySection(
                todayEarnings = todayEarnings,
                todayAppointmentsCount = todayAppointmentsCount,
                pendingCashToCollect = pendingCashToCollect
            )
        }

        // Section Title: Live Queue
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (pendingBookings.isNotEmpty()) AmberPrimary else GreenSuccess)
                    )
                    Text(
                        text = "LIVE QUEUE (${pendingBookings.size} WAITING)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "${completedBookings.size} Finished",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // 3. LIVE QUEUE (Pending & Accepted Bookings) Cards
        if (pendingBookings.isEmpty()) {
            item {
                EmptyQueueCard()
            }
        } else {
            items(pendingBookings, key = { it.id }) { booking ->
                LiveQueueBookingCard(
                    booking = booking,
                    onAcceptClick = { onAcceptBooking(booking.id) },
                    onRejectClick = { onRejectBooking(booking.id) },
                    onVerifyPinClick = { onOpenPinVerification(booking) }
                )
            }
        }

        // Completed Bookings section (if any)
        if (completedBookings.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "COMPLETED TODAY (${completedBookings.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            items(completedBookings.take(5), key = { it.id }) { booking ->
                CompletedBookingCard(booking = booking)
            }
        }
    }
}

/**
 * 1. THE "LIVE SWITCH":
 * A massive, highly visible Toggle Button at the top of the Home Screen for "Shop Open" (Green) / "Shop Closed" (Red).
 * Updates 'isOpen' in Firestore instantly.
 */
@Composable
fun TheLiveSwitch(
    isOpen: Boolean,
    hasLocationSetup: Boolean = true,
    onNavigateToProfile: () -> Unit = {},
    onToggle: (Boolean) -> Unit
) {
    var showLocationWarning by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("the_live_switch_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOpen) Color(0xFF063F28) else Color(0xFF450A0A)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isOpen) GreenSuccess else RedAlert
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isOpen) GreenSuccess else RedAlert)
                    )
                    Text(
                        text = if (isOpen) "SHOP IS OPEN" else "SHOP IS CLOSED",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isOpen) GreenSuccess else RedAlert,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = if (isOpen) "Accepting 15m Slots" else "Bookings Paused",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            // Massive Tap Action Button for Barber
            Button(
                onClick = {
                    if (!isOpen && !hasLocationSetup) {
                        // Salon owner CANNOT toggle their shop to Open unless they have provided their location!
                        showLocationWarning = true
                    } else {
                        showLocationWarning = false
                        onToggle(!isOpen)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("massive_shop_toggle_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOpen) GreenSuccess else RedAlert
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Toggle Shop Status",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = if (isOpen) "TAP TO CLOSE SHOP" else "TAP TO OPEN SHOP",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (isOpen) {
                                "Currently: Taking customers online"
                            } else if (!hasLocationSetup) {
                                "⚠️ Location setup required to open"
                            } else {
                                "Currently: Marked offline on customer app"
                            },
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Mandatory Location Enforcement Banner if owner tries to open without location
            if (showLocationWarning || (!isOpen && !hasLocationSetup)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2D1600))
                        .border(1.dp, AmberPrimary, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "MANDATORY LOCATION REQUIRED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberPrimary
                            )
                            Text(
                                text = "Please set your shop GPS location & address in Shop Profile before going Live.",
                                fontSize = 10.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier.testTag("go_to_profile_location_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AmberPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Setup 📍", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Real-Time Busy/Free Status + Available Chairs & Waiting Time System:
 * Syncs `currentStatus`, `availableChairs`, and `waitingTimeMinutes` directly to `salons/{salonId}` in Firestore.
 */
@Composable
fun BarberAvailabilityToggleCard(
    currentStatus: String,
    availableChairs: Int = 2,
    waitingTimeMinutes: Int = 0,
    isShopOpen: Boolean,
    onStatusChange: (String) -> Unit,
    onChairAndWaitChange: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    val isBusy = currentStatus.equals("BUSY", ignoreCase = true)
    val statusBg = if (isBusy) Color(0xFF3B151E) else Color(0xFF063522)
    val statusBorder = if (isBusy) RedAlert else GreenSuccess

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("barber_availability_toggle_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(statusBorder.copy(alpha = 0.6f))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isBusy) RedAlert else GreenSuccess)
                    )
                    Text(
                        text = "CHAIR AVAILABILITY & WAIT TIME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.sp
                    )
                }

                // Current State Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBg)
                        .border(1.dp, statusBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("current_status_badge")
                ) {
                    Text(
                        text = if (isBusy) "🔴 CUTTING HAIR (${waitingTimeMinutes}m wait)" else "🟢 $availableChairs CHAIRS FREE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isBusy) RedAlert else GreenSuccess
                    )
                }
            }

            Text(
                text = if (isBusy) {
                    "Your status is BUSY ($availableChairs chairs, ~${waitingTimeMinutes}m wait). Customers see live wait time on their Salons list & Map."
                } else {
                    "Your status is FREE ($availableChairs chairs available, 0m wait). Customers see '🟢 Available Now' with instant booking."
                },
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                lineHeight = 16.sp
            )

            // 1-Click Toggle Action: Dual Segmented / Big Toggle Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // "🟢 Free (Available)" Button
                Button(
                    onClick = { onStatusChange("FREE") },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("status_free_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isBusy) GreenSuccess else Color(0xFF1E293B),
                        contentColor = if (!isBusy) Color.White else Color(0xFF94A3B8)
                    ),
                    border = if (!isBusy) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🟢",
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Free (Available)",
                            fontSize = 13.sp,
                            fontWeight = if (!isBusy) FontWeight.Black else FontWeight.SemiBold
                        )
                    }
                }

                // "✂️ Busy (Cutting Hair)" Button
                Button(
                    onClick = { onStatusChange("BUSY") },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("status_busy_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBusy) RedAlert else Color(0xFF1E293B),
                        contentColor = if (isBusy) Color.White else Color(0xFF94A3B8)
                    ),
                    border = if (isBusy) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "✂️",
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Busy (Cutting)",
                            fontSize = 13.sp,
                            fontWeight = if (isBusy) FontWeight.Black else FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))

            // Live Available Chairs & Waiting Time Selector (Syncs immediately to Firestore)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Available Chairs: $availableChairs",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 3, 4).forEach { count ->
                            val selected = availableChairs == count
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) AmberPrimary else Color(0xFF1E293B))
                                    .border(
                                        1.dp,
                                        if (selected) AmberPrimary else Color(0xFF334155),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        onChairAndWaitChange(currentStatus, count, waitingTimeMinutes)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("chair_count_$count")
                            ) {
                                Text(
                                    text = "$count",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selected) Color(0xFF1E1402) else Color.White
                                )
                            }
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Wait Time: ${waitingTimeMinutes}m",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(0, 10, 15, 20, 30).forEach { mins ->
                            val selected = waitingTimeMinutes == mins
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Color(0xFF2563EB) else Color(0xFF1E293B))
                                    .border(
                                        1.dp,
                                        if (selected) Color(0xFF60A5FA) else Color(0xFF334155),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        val nextStatus = if (mins > 0) "BUSY" else "FREE"
                                        onChairAndWaitChange(nextStatus, availableChairs, mins)
                                    }
                                    .padding(horizontal = 7.dp, vertical = 6.dp)
                                    .testTag("wait_mins_$mins")
                            ) {
                                Text(
                                    text = "${mins}m",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. TODAY'S SUMMARY:
 * Quick stats cards showing "Today's Earnings (₹)" and "Total Appointments Today".
 */
@Composable
fun TodaysSummarySection(
    todayEarnings: Int,
    todayAppointmentsCount: Int,
    pendingCashToCollect: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "TODAY'S SUMMARY",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8),
            letterSpacing = 1.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Today's Earnings (₹)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_todays_earnings"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E3A8A))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = GreenSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Today's Earnings",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₹$todayEarnings",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = GreenSuccess
                    )
                    Text(
                        text = "Pay at Salon cash collected",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Total Appointments Today
            Card(
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_total_appointments"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF334155))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Appointments",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$todayAppointmentsCount",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = AmberPrimary
                    )
                    Text(
                        text = "₹$pendingCashToCollect cash to collect",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

/**
 * 3. LIVE QUEUE (Incoming Bookings Card):
 * Displays Customer Name, Phone, Service Booked, Accept/Reject actions, and
 * "Enter 4-digit PIN to Complete" action button (verifying the Customer's exact 4-digit servicePin).
 */
@Composable
fun LiveQueueBookingCard(
    booking: Booking,
    onAcceptClick: () -> Unit = {},
    onRejectClick: () -> Unit = {},
    onVerifyPinClick: () -> Unit
) {
    val isAccepted = booking.status.equals("accepted", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("live_queue_card_${booking.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isAccepted) GreenSuccess.copy(alpha = 0.6f) else AmberPrimary.copy(alpha = 0.4f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Customer Name & Arrival time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = booking.customerName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isAccepted) Color(0xFF064E3B) else Color(0xFF3B2306))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isAccepted) "✓ ACCEPTED" else "NEW",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isAccepted) GreenSuccess else AmberPrimary
                            )
                        }
                    }
                    if (booking.customerPhone.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = booking.customerPhone,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Slot Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF451A03))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "⏱ ${booking.timeSlot}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberPrimary
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))

            // Service Booked & Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = booking.service,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF1F5F9)
                    )
                }

                Text(
                    text = "₹${booking.servicePrice}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = GreenSuccess
                )
            }

            // Accept & Reject Action Row (Syncs directly to Firestore bookings/{bookingId})
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isAccepted) {
                    Button(
                        onClick = onAcceptClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("accept_booking_btn_${booking.id}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GreenSuccess,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Accept Booking",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Accept Booking",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRejectClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("reject_booking_btn_${booking.id}"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAlert),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RedAlert.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Reject Booking",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reject Slot",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // "Enter 4-digit PIN to Complete" Action Button (Requires exact customer 4-digit servicePin)
            Button(
                onClick = onVerifyPinClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("enter_pin_to_complete_btn_${booking.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "PIN",
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Enter 4-digit PIN to Complete",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CompletedBookingCard(booking: Booking) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Completed",
                    tint = GreenSuccess,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "${booking.customerName} • ${booking.service}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "PIN [${booking.pin}] Verified",
                        fontSize = 11.sp,
                        color = Color(0xFFA7F3D0)
                    )
                }
            }

            Text(
                text = "+₹${booking.servicePrice}",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GreenSuccess
            )
        }
    }
}

@Composable
fun EmptyQueueCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "No Customers Currently in Queue",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Keep 'Shop Open' enabled to receive instant 15-min bookings from nearby clients.",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 4. REAL-TIME SERVICES & PRICING MANAGER:
 * Allows the Partner to Add, Edit, and Delete salon services (Name, Category, Price in ₹, Duration in mins)
 * and persist them directly inside their Firestore salon document (`salons/{salonId}`),
 * plus quick rate updates for core services.
 */
@Composable
fun PartnerMenuManagerTabContent(
    salon: Salon?,
    onSavePrices: (String, Int, Int, Int, Int) -> Unit,
    onAddService: (String, String, String, Int, Int, String) -> Unit = { _, _, _, _, _, _ -> },
    onEditService: (String, SalonService) -> Unit = { _, _ -> },
    onDeleteService: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val existingHaircut = salon?.services?.find { it.category.equals("Haircut", ignoreCase = true) }?.price ?: 120
    val existingShaving = salon?.services?.find { it.category.equals("Beard", ignoreCase = true) }?.price ?: 80
    val existingFacial = salon?.services?.find { it.category.equals("Facial", ignoreCase = true) }?.price ?: 250
    val existingMassage = salon?.services?.find { it.category.equals("Massage", ignoreCase = true) }?.price ?: 100

    var haircutPrice by remember(salon) { mutableStateOf(existingHaircut.toString()) }
    var shavingPrice by remember(salon) { mutableStateOf(existingShaving.toString()) }
    var facialPrice by remember(salon) { mutableStateOf(existingFacial.toString()) }
    var massagePrice by remember(salon) { mutableStateOf(existingMassage.toString()) }
    var isSaved by remember { mutableStateOf(false) }

    // Add New Service States
    var newServiceName by remember { mutableStateOf("") }
    var newServiceCategory by remember { mutableStateOf("Haircut") }
    var newServicePrice by remember { mutableStateOf("150") }
    var newServiceDuration by remember { mutableStateOf("20") }
    var newServiceDesc by remember { mutableStateOf("") }

    // Edit Existing Service State
    var editingServiceId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editPrice by remember { mutableStateOf("") }
    var editDuration by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("partner_menu_manager_content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Real-Time Services & Pricing Manager",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Add, Edit, or Delete services (₹ & mins) directly in your live Firestore salon catalog.",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }

        // ==========================================
        // ADD NEW SERVICE TO FIRESTORE CARD
        // ==========================================
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_new_service_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(AmberPrimary.copy(alpha = 0.5f))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "ADD NEW SALON SERVICE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AmberPrimary,
                            letterSpacing = 0.5.sp
                        )
                    }

                    OutlinedTextField(
                        value = newServiceName,
                        onValueChange = { newServiceName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_service_name_input"),
                        label = { Text("Service Name *", color = Color(0xFF94A3B8)) },
                        placeholder = { Text("e.g. Fade Haircut, Detan Facial, Royal Beard", color = Color(0xFF64748B)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Category chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Haircut", "Beard", "Facial", "Massage", "Combo")) { cat ->
                            val selected = newServiceCategory.equals(cat, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) AmberPrimary else Color(0xFF1E293B))
                                    .border(
                                        1.dp,
                                        if (selected) AmberPrimary else Color(0xFF334155),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { newServiceCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color(0xFF1E1402) else Color.White
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newServicePrice,
                            onValueChange = { newServicePrice = it.filter { c -> c.isDigit() } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_service_price_input"),
                            label = { Text("Price (₹) *", color = Color(0xFF94A3B8)) },
                            leadingIcon = {
                                Text("₹", color = AmberPrimary, fontWeight = FontWeight.Bold)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberPrimary,
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = newServiceDuration,
                            onValueChange = { newServiceDuration = it.filter { c -> c.isDigit() } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_service_duration_input"),
                            label = { Text("Duration (mins) *", color = Color(0xFF94A3B8)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberPrimary,
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedContainerColor = DarkSurface,
                                unfocusedContainerColor = DarkSurface,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Button(
                        onClick = {
                            if (salon != null && newServiceName.isNotBlank()) {
                                val priceVal = newServicePrice.toIntOrNull() ?: 100
                                val durationVal = newServiceDuration.toIntOrNull() ?: 20
                                onAddService(
                                    salon.id,
                                    newServiceName.trim(),
                                    newServiceCategory,
                                    priceVal,
                                    durationVal,
                                    newServiceDesc.trim()
                                )
                                newServiceName = ""
                                newServiceDesc = ""
                            }
                        },
                        enabled = newServiceName.isNotBlank() && newServicePrice.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("add_service_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GreenSuccess,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Service",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+ Add Service to Live Catalog",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // ==========================================
        // LIVE FIRESTORE SERVICES LIST (EDIT / DELETE)
        // ==========================================
        val liveServices = salon?.services ?: emptyList()
        if (liveServices.isNotEmpty()) {
            item {
                Text(
                    text = "LIVE SALON SERVICES IN FIRESTORE (${liveServices.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )
            }

            items(liveServices, key = { it.id }) { service ->
                val isEditing = editingServiceId == service.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("partner_service_item_${service.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isEditing) AmberPrimary else Color(0xFF334155)
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = service.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF1E293B))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = service.category,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AmberPrimary
                                        )
                                    }
                                }
                                Text(
                                    text = "₹${service.price} • ${service.durationMinutes} mins",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenSuccess
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        if (isEditing) {
                                            editingServiceId = null
                                        } else {
                                            editingServiceId = service.id
                                            editName = service.name
                                            editPrice = service.price.toString()
                                            editDuration = service.durationMinutes.toString()
                                        }
                                    },
                                    modifier = Modifier.testTag("edit_service_btn_${service.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Service",
                                        tint = AmberPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (salon != null) {
                                            onDeleteService(salon.id, service.id, service.name)
                                        }
                                    },
                                    modifier = Modifier.testTag("delete_service_btn_${service.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Service",
                                        tint = RedAlert,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (isEditing && salon != null) {
                            HorizontalDivider(color = Color(0xFF334155))
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Service Name", color = Color(0xFF94A3B8)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AmberPrimary,
                                    unfocusedBorderColor = Color(0xFF475569),
                                    focusedContainerColor = DarkSurface,
                                    unfocusedContainerColor = DarkSurface,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = editPrice,
                                    onValueChange = { editPrice = it.filter { c -> c.isDigit() } },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Price (₹)", color = Color(0xFF94A3B8)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AmberPrimary,
                                        unfocusedBorderColor = Color(0xFF475569),
                                        focusedContainerColor = DarkSurface,
                                        unfocusedContainerColor = DarkSurface,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                OutlinedTextField(
                                    value = editDuration,
                                    onValueChange = { editDuration = it.filter { c -> c.isDigit() } },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Duration (mins)", color = Color(0xFF94A3B8)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AmberPrimary,
                                        unfocusedBorderColor = Color(0xFF475569),
                                        focusedContainerColor = DarkSurface,
                                        unfocusedContainerColor = DarkSurface,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }
                            Button(
                                onClick = {
                                    val updated = service.copy(
                                        name = editName.trim().ifBlank { service.name },
                                        price = editPrice.toIntOrNull() ?: service.price,
                                        durationMinutes = editDuration.toIntOrNull() ?: service.durationMinutes
                                    )
                                    onEditService(salon.id, updated)
                                    editingServiceId = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberPrimary,
                                    contentColor = Color(0xFF1E1402)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Save Service Changes to Firestore", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // QUICK STANDARD RATES UPDATER
        // ==========================================
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "QUICK STANDARD RATES (₹)",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF94A3B8),
                letterSpacing = 1.sp
            )
        }

        // Service 1: Haircut
        item {
            PredefinedServicePriceField(
                serviceName = "Haircut",
                serviceDesc = "Standard precision cut & styling",
                icon = Icons.Default.ContentCut,
                priceValue = haircutPrice,
                onPriceChange = {
                    haircutPrice = it.filter { char -> char.isDigit() }
                    isSaved = false
                },
                testTag = "input_price_haircut"
            )
        }

        // Service 2: Shaving
        item {
            PredefinedServicePriceField(
                serviceName = "Shaving / Beard",
                serviceDesc = "Clean foam shave, beard shape & trim",
                icon = Icons.Default.Face,
                priceValue = shavingPrice,
                onPriceChange = {
                    shavingPrice = it.filter { char -> char.isDigit() }
                    isSaved = false
                },
                testTag = "input_price_shaving"
            )
        }

        // Service 3: Facial
        item {
            PredefinedServicePriceField(
                serviceName = "Facial",
                serviceDesc = "Fruit scrub & deep facial clean",
                icon = Icons.Default.SelfImprovement,
                priceValue = facialPrice,
                onPriceChange = {
                    facialPrice = it.filter { char -> char.isDigit() }
                    isSaved = false
                },
                testTag = "input_price_facial"
            )
        }

        // Service 4: Head Massage
        item {
            PredefinedServicePriceField(
                serviceName = "Head Massage",
                serviceDesc = "Relaxing herbal oil head massage",
                icon = Icons.Default.SelfImprovement,
                priceValue = massagePrice,
                onPriceChange = {
                    massagePrice = it.filter { char -> char.isDigit() }
                    isSaved = false
                },
                testTag = "input_price_massage"
            )
        }

        // Save Button
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = {
                    if (salon != null) {
                        val h = haircutPrice.toIntOrNull() ?: 120
                        val s = shavingPrice.toIntOrNull() ?: 80
                        val f = facialPrice.toIntOrNull() ?: 250
                        val m = massagePrice.toIntOrNull() ?: 100
                        onSavePrices(salon.id, h, s, f, m)
                        isSaved = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_menu_prices_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberPrimary,
                    contentColor = Color(0xFF1E1402)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isSaved) "Saved to Profile & Cloud!" else "Save to Profile",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PredefinedServicePriceField(
    serviceName: String,
    serviceDesc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    priceValue: String,
    onPriceChange: (String) -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = serviceName,
                        tint = AmberPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = serviceName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = serviceDesc,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Input field for Price (₹)
            OutlinedTextField(
                value = priceValue,
                onValueChange = onPriceChange,
                modifier = Modifier
                    .width(100.dp)
                    .testTag(testTag),
                leadingIcon = {
                    Text(
                        text = "₹",
                        color = AmberPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AmberPrimary,
                    unfocusedBorderColor = Color(0xFF475569),
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

/**
 * 5. CLEAN, USER-FRIENDLY SHOP DETAILS, PHOTO UPLOAD & GPS MANAGER:
 * - Allows the Partner to edit Shop Name, Address/Location, Available Chairs, Phone Number, and Tagline
 *   with a clear "Save Changes" button.
 * - Real "+ Add Shop Image" gallery picker using `ActivityResultContracts.GetContent()` that compresses
 *   and encodes the selected photo to a Base64 JPEG data URL stored directly in Firestore `salons/{salonId}`
 *   (plus 4 preset 1-tap salon cover photos), immediately visible in Shop Profile and on Customer Salon Cards.
 * - Fixed OpenStreetMap container (`height(260.dp)`) with no vertical attribution bug.
 */
@Composable
fun PartnerShopProfileTabContent(
    salon: Salon?,
    partnerProfile: UserProfile?,
    onSaveLocation: (String, String, String, Double, Double) -> Unit = { _, _, _, _, _ -> },
    onSaveFullProfile: (String, String, String, String, String, String, Double, Double, Int) -> Unit = { salonId, _, _, _, addr, lm, lat, lng, _ ->
        onSaveLocation(salonId, addr, lm, lat, lng)
    },
    onSaveShopPhoto: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current

    // Editable Shop Details States
    var shopName by remember(salon?.id, salon?.name) {
        mutableStateOf(salon?.name ?: "My Grooming Studio")
    }
    var shopPhone by remember(salon?.id, salon?.phone, partnerProfile?.phone) {
        mutableStateOf(
            salon?.phone?.takeIf { it.isNotBlank() }
                ?: partnerProfile?.phone?.takeIf { it.isNotBlank() }
                ?: "+91 98765 43210"
        )
    }
    var availableChairs by remember(salon?.id, salon?.availableChairs) {
        mutableStateOf(salon?.availableChairs ?: 2)
    }
    var shopTagline by remember(salon?.id, salon?.tagline) {
        mutableStateOf(salon?.tagline ?: "Instant grooming slots • Zero queue wait time")
    }
    var fullAddress by remember(salon?.id, salon?.shopAddress, salon?.location) {
        mutableStateOf(salon?.shopAddress?.takeIf { it.isNotBlank() } ?: salon?.location ?: "")
    }
    var landmark by remember(salon?.id, salon?.landmark) {
        mutableStateOf(salon?.landmark ?: "")
    }
    var currentLatitude by remember(salon?.id, salon?.latitude) {
        mutableStateOf(salon?.latitude?.takeIf { it != 0.0 } ?: 25.5941)
    }
    var currentLongitude by remember(salon?.id, salon?.longitude) {
        mutableStateOf(salon?.longitude?.takeIf { it != 0.0 } ?: 85.1376)
    }
    var isGpsFetched by remember(salon?.latitude, salon?.longitude) {
        mutableStateOf((salon?.latitude ?: 0.0) != 0.0 && (salon?.longitude ?: 0.0) != 0.0)
    }
    var locationSaveSuccess by remember { mutableStateOf(false) }
    var isFetchingGps by remember { mutableStateOf(false) }

    // Shop Cover Photo State (synced with Firestore `salon.resolvedPhotoData`)
    var localPhotoDataUrl by remember(salon?.id, salon?.resolvedPhotoData) {
        mutableStateOf(salon?.resolvedPhotoData ?: "")
    }
    var uploadStatusMessage by remember { mutableStateOf<String?>(null) }

    val decodedShopBitmap = remember(localPhotoDataUrl) {
        SalonImageUtils.decodeBase64ToImageBitmap(localPhotoDataUrl)
    }

    // Real Gallery Image Picker (`ActivityResultContracts.GetContent()`) -> Base64 JPEG in Firestore
    val galleryImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && salon != null) {
            val base64Jpeg = SalonImageUtils.encodeUriToBase64Jpeg(
                context = context,
                uri = uri,
                maxWidth = 600,
                quality = 60
            )
            if (!base64Jpeg.isNullOrBlank()) {
                localPhotoDataUrl = base64Jpeg
                onSaveShopPhoto(salon.id, base64Jpeg)
                uploadStatusMessage = "Shop photo uploaded & live on Customer Salon Card!"
            } else {
                uploadStatusMessage = "Could not process selected photo. Try another image."
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("partner_shop_profile_content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. SHOP COVER PHOTO CARD ("+ Add Shop Image" + 1-Tap Preset Covers)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(AmberPrimary.copy(alpha = 0.4f))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "SHOP COVER PHOTO",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = if (decodedShopBitmap != null) "Live on Customer Card" else "No Photo Yet",
                            fontSize = 10.sp,
                            color = if (decodedShopBitmap != null) GreenSuccess else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Clickable Image Preview / Upload Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(165.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0F172A))
                            .border(
                                width = 1.dp,
                                color = if (decodedShopBitmap != null) AmberPrimary else Color(0xFF334155),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                galleryImageLauncher.launch("image/*")
                            }
                            .testTag("add_shop_image_box"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (decodedShopBitmap != null) {
                            Image(
                                bitmap = decodedShopBitmap,
                                contentDescription = "Uploaded Shop Cover Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0x33000000), Color(0xCC090D16))
                                        )
                                    )
                            )
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xE60F172A))
                                    .border(1.dp, AmberPrimary, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Change Photo",
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Change Gallery Photo",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x33F59E0B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Add Photo",
                                        tint = AmberPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Text(
                                    text = "+ Add Shop Image",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Pick a photo from your phone gallery (syncs directly to Firestore)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // 1-Tap Preset Salon Cover Photos
                    Text(
                        text = "Or choose a preset studio cover in 1 tap:",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(SalonImageUtils.PRESET_COVERS, key = { it.id }) { preset ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(preset.primaryColorHex),
                                                Color(preset.secondaryColorHex)
                                            )
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        Color(preset.accentColorHex).copy(alpha = 0.7f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        if (salon != null) {
                                            val generatedDataUrl = SalonImageUtils.generatePresetCoverBase64(preset)
                                            localPhotoDataUrl = generatedDataUrl
                                            onSaveShopPhoto(salon.id, generatedDataUrl)
                                            uploadStatusMessage = "Applied '${preset.title}' cover & synced to Firestore!"
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("preset_cover_${preset.id}")
                            ) {
                                Column {
                                    Text(
                                        text = preset.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = preset.subtitle,
                                        fontSize = 9.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }
                    }

                    if (!uploadStatusMessage.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF064E3B))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = uploadStatusMessage ?: "",
                                fontSize = 11.sp,
                                color = Color(0xFFA7F3D0),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 2. CLEAN "SHOP DETAILS" FORM (Shop Name, Address/Location, Available Chairs, Phone Number, Save Changes)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shop_location_registration_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (salon?.hasLocationSetup == true) GreenSuccess.copy(alpha = 0.5f) else AmberPrimary
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
                                text = "SHOP DETAILS",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF064E3B))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "LIVE SYNC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                    }

                    Text(
                        text = "Edit your Shop Name, Phone Number, Available Chairs, and Address. Changes appear immediately for nearby customers.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp
                    )

                    // Shop Name Input
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = {
                            shopName = it
                            locationSaveSuccess = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shop_name_input"),
                        label = { Text("Shop Name *", color = Color(0xFF94A3B8)) },
                        placeholder = { Text("e.g. Royal Cuts Grooming Studio", color = Color(0xFF64748B)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Phone Number Input
                    OutlinedTextField(
                        value = shopPhone,
                        onValueChange = {
                            shopPhone = it
                            locationSaveSuccess = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shop_phone_input"),
                        label = { Text("Phone Number *", color = Color(0xFF94A3B8)) },
                        placeholder = { Text("e.g. +91 98765 43210", color = Color(0xFF64748B)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Available Chairs Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Available Chairs: $availableChairs",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E8F0)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1, 2, 3, 4, 5, 6).forEach { count ->
                                val isSelected = availableChairs == count
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) AmberPrimary else DarkSurface)
                                        .border(
                                            1.dp,
                                            if (isSelected) AmberPrimary else Color(0xFF334155),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            availableChairs = count
                                            locationSaveSuccess = false
                                        }
                                        .testTag("profile_chair_count_$count"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$count",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) DarkBackground else Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Shop Tagline Input
                    OutlinedTextField(
                        value = shopTagline,
                        onValueChange = {
                            shopTagline = it
                            locationSaveSuccess = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shop_tagline_input"),
                        label = { Text("Shop Tagline / Specialty", color = Color(0xFF94A3B8)) },
                        placeholder = { Text("e.g. Instant grooming slots • Zero queue wait time", color = Color(0xFF64748B)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Address / Location Input
                    OutlinedTextField(
                        value = fullAddress,
                        onValueChange = {
                            fullAddress = it
                            locationSaveSuccess = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shop_full_address_input"),
                        label = { Text("Address / Location *", color = Color(0xFF94A3B8)) },
                        placeholder = { Text("e.g. Shop #12, Boring Road, Near Pantaloons", color = Color(0xFF64748B)) },
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Nearby Landmark Input
                    OutlinedTextField(
                        value = landmark,
                        onValueChange = {
                            landmark = it
                            locationSaveSuccess = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shop_landmark_input"),
                        label = { Text("Nearby Landmark", color = Color(0xFF94A3B8)) },
                        placeholder = { Text("e.g. Opposite Shiv Temple / Metro Pillar 42", color = Color(0xFF64748B)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // GPS Auto-Fetch + OpenStreetMap Pin Picker
                    val gpsPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                        if (granted) {
                            fetchDeviceGpsLocation(context) { lat, lng, _ ->
                                currentLatitude = lat
                                currentLongitude = lng
                                isGpsFetched = true
                                isFetchingGps = false
                                locationSaveSuccess = false
                                if (fullAddress.isBlank()) {
                                    fullAddress = "Shop #4, Kankarbagh Main Road, Near Central Park"
                                }
                                if (landmark.isBlank()) {
                                    landmark = "Opposite Metro Pillar #42"
                                }
                            }
                        } else {
                            isFetchingGps = false
                        }
                    }

                    Button(
                        onClick = {
                            isFetchingGps = true
                            if (hasLocationPermission(context)) {
                                fetchDeviceGpsLocation(context) { lat, lng, _ ->
                                    currentLatitude = lat
                                    currentLongitude = lng
                                    isGpsFetched = true
                                    isFetchingGps = false
                                    locationSaveSuccess = false
                                    if (fullAddress.isBlank()) {
                                        fullAddress = "Shop #4, Kankarbagh Main Road, Near Central Park"
                                    }
                                    if (landmark.isBlank()) {
                                        landmark = "Opposite Metro Pillar #42"
                                    }
                                }
                            } else {
                                gpsPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("gps_auto_fetch_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E3A8A)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Fetch GPS",
                                tint = Color(0xFF93C5FD),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isFetchingGps) "Acquiring Satellites..." else "📍 Use Current GPS Location",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Interactive OpenStreetMap Shop Pin Picker (inside clean 260.dp Box)
                    PartnerLocationPickerOsmMap(
                        latitude = currentLatitude,
                        longitude = currentLongitude,
                        salonName = shopName.ifBlank { salon?.name ?: "My Salon" },
                        onLocationPicked = { pickedLat, pickedLng ->
                            currentLatitude = pickedLat
                            currentLongitude = pickedLng
                            isGpsFetched = true
                            locationSaveSuccess = false
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lat: ${"%.4f".format(currentLatitude)}, Lng: ${"%.4f".format(currentLongitude)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF67E8F9)
                        )
                        Text(
                            text = if (isGpsFetched) "Pin Locked • Ready to Save" else "Tap map to adjust pin",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // Save Changes Action Button
                    Button(
                        onClick = {
                            if (salon != null && fullAddress.isNotBlank()) {
                                onSaveFullProfile(
                                    salon.id,
                                    shopName.trim().ifBlank { salon.name },
                                    shopPhone.trim(),
                                    shopTagline.trim(),
                                    fullAddress.trim(),
                                    landmark.trim(),
                                    currentLatitude,
                                    currentLongitude,
                                    availableChairs
                                )
                                locationSaveSuccess = true
                            }
                        },
                        enabled = fullAddress.isNotBlank() && shopName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_shop_location_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            disabledContainerColor = Color(0xFF475569)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save Changes",
                                tint = DarkBackground,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Save Changes",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DarkBackground
                            )
                        }
                    }

                    if (locationSaveSuccess) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF064E3B))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Shop Details & GPS saved to Cloud Firestore!",
                                fontSize = 11.sp,
                                color = Color(0xFFA7F3D0),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
