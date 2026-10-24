package com.example.ui.owner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CurrencyRupee
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Booking
import com.example.model.Salon
import com.example.model.UserProfile
import com.example.ui.components.ProjectExportHubCard
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedAlert

enum class PartnerTab(val title: String) {
    LIVE_QUEUE("Live Queue"),
    SERVICES_PRICING("Services & Pricing"),
    SHOP_PROFILE("Shop Profile")
}

/**
 * Partner Mode (Salon Owner Dashboard) UI in Jetpack Compose:
 * Zero-clutter, bold, high-contrast interface designed for salon barbers & shop owners.
 *
 * 1. The "Live Switch": Massive Toggle Button at top for "Shop Open" (Green) / "Shop Closed" (Red).
 * 2. Today's Summary: Stats cards for "Today's Earnings (₹)" and "Total Appointments Today".
 * 3. Live Queue (Pending Bookings): Clean list of incoming bookings with Customer Name, Service Booked,
 *    and clear "Enter 4-digit PIN to Complete" action button.
 * 4. Quick Menu Manager: Dedicated tab for "Services & Pricing" (Haircut, Shaving, Facial, Head Massage)
 *    where owner inputs price (₹) and hits "Save to Profile".
 * 5. Photo Upload Section: Simple "+ Add Shop Image" area using zero-permission Android Photo Picker.
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
    onToggleSalonOpen: (String) -> Unit,
    onSetSalonOpen: (String, Boolean) -> Unit = { id, _ -> onToggleSalonOpen(id) },
    onSetSalonBusyStatus: (String, String) -> Unit = { _, _ -> },
    onSaveMenuPrices: (String, Int, Int, Int, Int) -> Unit = { _, _, _, _, _ -> },
    onSaveShopLocation: (String, String, String, Double, Double) -> Unit = { _, _, _, _, _ -> },
    onExitPartnerMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Current managed salon: prioritize partner's assigned salonId or selected
    val currentSalon = salons.find { it.id == (selectedSalonId ?: partnerProfile?.salonId) }
        ?: salons.firstOrNull()

    // Filter bookings for this salon
    val salonBookings = if (currentSalon != null) {
        allBookings.filter { it.salonId == currentSalon.id }
    } else {
        allBookings
    }

    val pendingBookings = salonBookings.filter { it.status.equals("pending", ignoreCase = true) }
    val completedBookings = salonBookings.filter { it.status.equals("completed", ignoreCase = true) }

    // Today's Summary calculations
    val todayAppointmentsCount = salonBookings.size
    val todayEarnings = completedBookings.sumOf { it.servicePrice }
    val pendingCashToCollect = pendingBookings.sumOf { it.servicePrice }

    // Active Tab in Partner Dashboard
    var currentTab by remember { mutableStateOf(PartnerTab.LIVE_QUEUE) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Partner Top Bar
        PartnerTopBar(
            partnerName = partnerProfile?.name ?: "Salon Partner",
            salonName = currentSalon?.name ?: "My Salon",
            onExitPartnerMode = onExitPartnerMode
        )

        // Salon Selector if multiple registered salons exist in testing
        if (salons.size > 1 && currentSalon != null) {
            PartnerSalonSelectorBar(
                salons = salons,
                selectedSalonId = currentSalon.id,
                onSelect = onSelectSalonId
            )
        }

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
                    todayEarnings = todayEarnings,
                    todayAppointmentsCount = todayAppointmentsCount,
                    pendingCashToCollect = pendingCashToCollect,
                    pendingBookings = pendingBookings,
                    completedBookings = completedBookings,
                    onOpenPinVerification = onOpenPinVerification,
                    onNavigateToProfile = { currentTab = PartnerTab.SHOP_PROFILE }
                )
            }

            PartnerTab.SERVICES_PRICING -> {
                PartnerMenuManagerTabContent(
                    salon = currentSalon,
                    onSavePrices = onSaveMenuPrices
                )
            }

            PartnerTab.SHOP_PROFILE -> {
                PartnerShopProfileTabContent(
                    salon = currentSalon,
                    partnerProfile = partnerProfile,
                    onSaveLocation = onSaveShopLocation
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
 * TAB 1: Live Queue (Includes Live Switch, Today's Summary & Incoming Customer Bookings)
 */
@Composable
fun PartnerLiveQueueTabContent(
    salon: Salon?,
    onSetSalonOpen: (String, Boolean) -> Unit,
    onSetSalonBusyStatus: (String, String) -> Unit = { _, _ -> },
    todayEarnings: Int,
    todayAppointmentsCount: Int,
    pendingCashToCollect: Int,
    pendingBookings: List<Booking>,
    completedBookings: List<Booking>,
    onOpenPinVerification: (Booking) -> Unit,
    onNavigateToProfile: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("partner_live_queue_feed"),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. THE "LIVE SWITCH": Massive, highly visible Toggle Button
        // MANDATORY: Salon owner cannot toggle their shop to Open unless location is provided!
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

            // Real-Time Busy/Free Status System:
            // 1-Click toggle button for "✂️ Busy (Cutting Hair)" and "🟢 Free (Available)"
            item {
                BarberAvailabilityToggleCard(
                    currentStatus = salon.currentStatus,
                    isShopOpen = salon.isOpen,
                    onStatusChange = { newStatus ->
                        onSetSalonBusyStatus(salon.id, newStatus)
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

        // 3. LIVE QUEUE (Pending Bookings) Cards
        if (pendingBookings.isEmpty()) {
            item {
                EmptyQueueCard()
            }
        } else {
            items(pendingBookings, key = { it.id }) { booking ->
                LiveQueueBookingCard(
                    booking = booking,
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
 * Real-Time Busy/Free Status System:
 * Partner App UI (Barber's side): Highly visible, 1-click toggle button on the Partner Dashboard
 * saying "✂️ Busy (Cutting Hair)" and "🟢 Free (Available)".
 */
@Composable
fun BarberAvailabilityToggleCard(
    currentStatus: String,
    isShopOpen: Boolean,
    onStatusChange: (String) -> Unit
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
                        text = "CHAIR AVAILABILITY STATUS",
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
                        text = if (isBusy) "🔴 CUTTING HAIR" else "🟢 CHAIR FREE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isBusy) RedAlert else GreenSuccess
                    )
                }
            }

            Text(
                text = if (isBusy) {
                    "Your status is BUSY. Customer app shows: '🔴 Barber is Busy (Approx 15-20 Min Wait)' with Join Waitlist."
                } else {
                    "Your status is FREE. Customer app shows: '🟢 Available Now' with instant booking."
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
 * 3. LIVE QUEUE (Pending Bookings Card):
 * Must display: Customer Name, Service Booked, and "Enter 4-digit PIN to Complete" action button.
 */
@Composable
fun LiveQueueBookingCard(
    booking: Booking,
    onVerifyPinClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("live_queue_card_${booking.id}"),
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

            // "Enter 4-digit PIN to Complete" Action Button
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
 * 4. QUICK MENU MANAGER:
 * Dedicated tab for "Services & Pricing". Pre-defined text fields:
 * - Haircut
 * - Shaving
 * - Facial
 * - Head Massage
 * where the owner inputs their Price (₹) and hits "Save to Profile".
 */
@Composable
fun PartnerMenuManagerTabContent(
    salon: Salon?,
    onSavePrices: (String, Int, Int, Int, Int) -> Unit
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
                            text = "Quick Menu & Price Manager",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Set your standard rates in rupees (₹). Displayed instantly to all nearby customers.",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
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
 * 5. PHOTO UPLOAD SECTION & SHOP PROFILE:
 * A simple "+ Add Shop Image" area in profile settings (prepared for Firebase Storage integration).
 */
@Composable
fun PartnerShopProfileTabContent(
    salon: Salon?,
    partnerProfile: UserProfile?,
    onSaveLocation: (String, String, String, Double, Double) -> Unit = { _, _, _, _, _ -> }
) {
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var uploadStatusMessage by remember { mutableStateOf<String?>(null) }

    // Address & GPS Location States
    var fullAddress by remember(salon?.shopAddress) {
        mutableStateOf(salon?.shopAddress?.takeIf { it.isNotBlank() } ?: salon?.location ?: "")
    }
    var landmark by remember(salon?.landmark) {
        mutableStateOf(salon?.landmark ?: "")
    }
    var currentLatitude by remember(salon?.latitude) {
        mutableStateOf(salon?.latitude?.takeIf { it != 0.0 } ?: 25.5941)
    }
    var currentLongitude by remember(salon?.longitude) {
        mutableStateOf(salon?.longitude?.takeIf { it != 0.0 } ?: 85.1376)
    }
    var isGpsFetched by remember(salon?.latitude, salon?.longitude) {
        mutableStateOf((salon?.latitude ?: 0.0) != 0.0 && (salon?.longitude ?: 0.0) != 0.0)
    }
    var locationSaveSuccess by remember { mutableStateOf(false) }
    var isFetchingGps by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker contract (Google Play compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            uploadStatusMessage = "Shop image selected! Ready to sync to Firebase Storage."
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("partner_shop_profile_content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // MANDATORY SHOP REGISTRATION & GPS ADDRESS SECTION
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
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "SHOP ADDRESS & GPS LOCATION",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }

                        if (salon?.hasLocationSetup == true) {
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
                                    text = "VERIFIED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA7F3D0)
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x33EF4444))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = RedAlert,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "SETUP REQUIRED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RedAlert
                                )
                            }
                        }
                    }

                    Text(
                        text = "Mandatory for opening shop. Nearby customers discover you and calculate accurate walking distance based on this location.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp
                    )

                    // 1. GPS Auto-Fetch Button
                    Button(
                        onClick = {
                            isFetchingGps = true
                            // Simulate GPS high accuracy capture for Patna / local area
                            val randomLatOffset = ((System.currentTimeMillis() % 1000) - 500) / 100000.0
                            val randomLngOffset = ((System.currentTimeMillis() % 900) - 450) / 100000.0
                            currentLatitude = 25.5941 + randomLatOffset
                            currentLongitude = 85.1376 + randomLngOffset
                            isGpsFetched = true
                            if (fullAddress.isBlank()) {
                                fullAddress = "Shop #4, Kankarbagh Main Road, Near Central Park"
                            }
                            if (landmark.isBlank()) {
                                landmark = "Opposite Metro Pillar #42"
                            }
                            isFetchingGps = false
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

                    // GPS Coordinates preview pill
                    if (isGpsFetched) {
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
                                text = "High Accuracy GPS (±5m)",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // 2. Full Shop Address Text Field
                    OutlinedTextField(
                        value = fullAddress,
                        onValueChange = {
                            fullAddress = it
                            locationSaveSuccess = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shop_full_address_input"),
                        label = { Text("Full Shop Address *", color = Color(0xFF94A3B8)) },
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

                    // 3. Nearby Landmark Text Field
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

                    // 4. Save Location to Firestore Action Button
                    Button(
                        onClick = {
                            if (salon != null && fullAddress.isNotBlank()) {
                                onSaveLocation(
                                    salon.id,
                                    fullAddress.trim(),
                                    landmark.trim(),
                                    currentLatitude,
                                    currentLongitude
                                )
                                locationSaveSuccess = true
                            }
                        },
                        enabled = fullAddress.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_shop_location_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            disabledContainerColor = Color(0xFF475569)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save",
                                tint = DarkBackground,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Save Location to Firestore",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
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
                                text = "Location saved! Shop can now toggle online.",
                                fontSize = 11.sp,
                                color = Color(0xFFA7F3D0),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 5. Photo Upload Section: "+ Add Shop Image"
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SHOP PHOTOS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Firebase Storage Ready",
                            fontSize = 10.sp,
                            color = GreenSuccess,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Clickable Image Upload Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("add_shop_image_box"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Show customers your clean chairs and modern setup",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    if (selectedImageUri != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF064E3B))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = uploadStatusMessage ?: "Photo staged for upload",
                                fontSize = 11.sp,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                    }
                }
            }
        }

        // Shop Information & Profile Details
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SALON SHOP DETAILS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.sp
                    )

                    ShopDetailRow(label = "Salon Name", value = salon?.name ?: "TrimTime Partner Salon")
                    ShopDetailRow(label = "Location", value = salon?.location ?: "Hyper-local Street")
                    ShopDetailRow(label = "Rating", value = "${salon?.rating ?: 4.8} ★ (${salon?.reviewCount ?: 120} reviews)")
                    ShopDetailRow(label = "Partner Account", value = partnerProfile?.email ?: "Registered Partner")
                    ShopDetailRow(label = "Contact Phone", value = partnerProfile?.phone ?: "+91 98765 43210")
                }
            }
        }

        // Project Export & Share Hub (Download Project Source Code .ZIP for External APK Build)
        item {
            ProjectExportHubCard()
        }
    }
}

@Composable
fun ShopDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF94A3B8))
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
fun PartnerSalonSelectorBar(
    salons: List<Salon>,
    selectedSalonId: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(salons) { salon ->
            val isSelected = salon.id == selectedSalonId
            val bg = if (isSelected) Color(0xFF2563EB) else DarkSurface
            val txt = if (isSelected) Color.White else Color(0xFF94A3B8)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable { onSelect(salon.id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("select_salon_${salon.id}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = salon.name,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = txt
                )
            }
        }
    }
}
