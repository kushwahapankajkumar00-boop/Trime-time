package com.example.ui.owner

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import coil.compose.AsyncImage
import com.google.firebase.storage.FirebaseStorage
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PrivacyTip
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
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.example.ui.components.HelpAndSupportCenterDialog
import com.example.ui.components.LegalPolicyTab
import com.example.ui.components.PartnerLocationPickerOsmMap
import com.example.ui.components.PrivacyPolicyScreen
import com.example.ui.components.SendFeedbackDialog
import com.example.ui.components.SupportFeedbackLegalSectionCard
import com.example.ui.components.TermsAndConditionsScreen
import com.example.ui.components.fetchDeviceGpsLocation
import com.example.ui.components.hasLocationPermission
import com.example.ui.components.launchPhoneDialer
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedAlert
import com.example.util.SalonImageUtils
import com.example.util.SecurityValidator
import com.example.viewmodel.LiveQueueViewModel
import com.example.viewmodel.triggerNewBookingSoundAndVibration
import androidx.compose.material.icons.filled.SupportAgent

enum class PartnerTab(val title: String) {
    LIVE_QUEUE("Live Queue"),
    SERVICES_PRICING("Manage Menu"),
    EARNINGS_WALLET("Earnings/Wallet"),
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
    isGlobalRushModeEnabled: Boolean = false,
    onAcceptBooking: (String) -> Unit = {},
    onAcceptBookingWithTime: (String, String) -> Unit = { id, _ -> onAcceptBooking(id) },
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
    onSaveFullProfile: (String, String, String, String, String, String, Double, Double, Int, Boolean) -> Unit = { salonId, _, _, _, addr, lm, lat, lng, _, _ ->
        onSaveShopLocation(salonId, addr, lm, lat, lng)
    },
    onSaveShopPhoto: (String, String) -> Unit = { _, _ -> },
    onSubmitFeedback: (rating: Int, category: String, message: String, onDone: (Boolean, String) -> Unit) -> Unit = { _, _, _, onDone ->
        onDone(true, "Saved")
    },
    onRequestLocationUnlock: (String?) -> Unit = {},
    onResetSalonRegistration: (String?) -> Unit = {},
    onExitPartnerMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Logged-in Partner manages ONLY their own registered salon (strictly where isRegistrationCompleted == true)
    val currentSalon = salons.find {
        !partnerProfile?.id.isNullOrBlank() &&
            (it.ownerId == partnerProfile?.id || it.partnerId == partnerProfile?.id || it.id == "salon_${partnerProfile?.id}") &&
            it.isRegistrationCompleted
    }
        ?: salons.find {
            !partnerProfile?.salonId.isNullOrBlank() &&
                it.id == partnerProfile?.salonId &&
                it.isRegistrationCompleted
        }
        ?: salons.find { it.id == selectedSalonId && it.isRegistrationCompleted }

    // Filter bookings in real time strictly for this partner's salon
    val salonBookings = if (currentSalon != null) {
        val candidateSalonIds = setOf(
            currentSalon.id.trim(),
            currentSalon.partnerId.trim(),
            currentSalon.ownerId.trim(),
            if (currentSalon.partnerId.isNotBlank()) "salon_${currentSalon.partnerId.trim()}" else "",
            if (currentSalon.ownerId.isNotBlank()) "salon_${currentSalon.ownerId.trim()}" else "",
            currentSalon.id.trim().removePrefix("salon_")
        ).filter { it.isNotBlank() }
        allBookings.filter { b ->
            b.salonId.trim() in candidateSalonIds ||
                b.partnerId.trim() in candidateSalonIds ||
                (currentSalon.name.isNotBlank() && b.salonName.trim().equals(currentSalon.name.trim(), ignoreCase = true))
        }
    } else {
        emptyList()
    }

    // Active Queue includes "pending", "accepted", and "in_progress" bookings
    val pendingBookings = salonBookings.filter {
        SecurityValidator.isBookingActiveStatus(it.status)
    }
    val completedBookings = salonBookings.filter {
        it.status.equals("completed", ignoreCase = true) ||
            it.status.equals("cancelled", ignoreCase = true) ||
            it.status.equals("rejected", ignoreCase = true)
    }
    val strictlyCompletedBookings = salonBookings.filter { it.status.equals("completed", ignoreCase = true) }

    // Today's Summary calculations
    val todayAppointmentsCount = salonBookings.count {
        !it.status.equals("rejected", ignoreCase = true) && !it.status.equals("cancelled", ignoreCase = true)
    }
    val todayEarnings = strictlyCompletedBookings.sumOf { it.servicePrice }
    val pendingCashToCollect = pendingBookings.sumOf { it.servicePrice }

    val context = LocalContext.current
    val liveQueueViewModel: LiveQueueViewModel = viewModel()
    val newIncomingBooking by liveQueueViewModel.newIncomingBooking.collectAsState()

    // Active Tab in Partner Dashboard
    var currentTab by remember { mutableStateOf(PartnerTab.LIVE_QUEUE) }

    // Attach real-time Firestore listener for the Partner's salon (ignores initial load & old bookings)
    LaunchedEffect(currentSalon?.id, partnerProfile?.id) {
        liveQueueViewModel.observeSalonLiveQueue(
            salonId = currentSalon?.id,
            partnerUid = partnerProfile?.id
        )
    }

    // Also keep LiveQueueViewModel synced with the live salonBookings list (skips initial load)
    LaunchedEffect(currentSalon?.id, salonBookings) {
        liveQueueViewModel.syncLiveQueueBookings(
            salonId = currentSalon?.id,
            bookings = salonBookings
        )
    }

    // React to a new incoming "Pending" booking when on non-Live-Queue tabs or show Toast notification
    LaunchedEffect(newIncomingBooking?.id) {
        val incoming = newIncomingBooking ?: return@LaunchedEffect
        if (currentTab != PartnerTab.LIVE_QUEUE) {
            triggerNewBookingSoundAndVibration(context)
        }
        Toast.makeText(
            context,
            "🔔 New Booking: ${incoming.customerName} (${incoming.service})",
            Toast.LENGTH_SHORT
        ).show()
        liveQueueViewModel.consumeNewBookingAlert(incoming.id)
    }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var activeLegalTab by remember { mutableStateOf<LegalPolicyTab?>(null) }

    when (activeLegalTab) {
        LegalPolicyTab.PRIVACY -> {
            PrivacyPolicyScreen(
                onBack = { activeLegalTab = null },
                modifier = modifier
            )
            return
        }
        LegalPolicyTab.TERMS -> {
            TermsAndConditionsScreen(
                onBack = { activeLegalTab = null },
                modifier = modifier
            )
            return
        }
        null -> Unit
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Partner Top Bar (manages only the logged-in Partner's own salon)
        PartnerTopBar(
            partnerName = partnerProfile?.name ?: "Salon Partner",
            salonName = currentSalon?.name ?: "My Salon",
            onOpenSupportCenter = { showSupportDialog = true },
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
                    isGlobalRushModeEnabled = isGlobalRushModeEnabled,
                    onOpenPinVerification = onOpenPinVerification,
                    onAcceptBooking = onAcceptBooking,
                    onAcceptBookingWithTime = onAcceptBookingWithTime,
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

            PartnerTab.EARNINGS_WALLET -> {
                PartnerEarningsWalletTabContent(
                    salon = currentSalon,
                    completedBookings = strictlyCompletedBookings,
                    pendingBookings = pendingBookings,
                    allSalonBookings = salonBookings
                )
            }

            PartnerTab.SHOP_PROFILE -> {
                PartnerShopProfileTabContent(
                    salon = currentSalon,
                    partnerProfile = partnerProfile,
                    onSaveLocation = onSaveShopLocation,
                    onSaveFullProfile = onSaveFullProfile,
                    onSaveShopPhoto = onSaveShopPhoto,
                    onRequestLocationUnlock = { onRequestLocationUnlock(currentSalon?.id) },
                    onOpenSupportCenter = { showSupportDialog = true },
                    onOpenFeedbackForm = { showFeedbackDialog = true },
                    onOpenTerms = { activeLegalTab = LegalPolicyTab.TERMS },
                    onOpenPrivacyPolicy = { activeLegalTab = LegalPolicyTab.PRIVACY },
                    onResetSalonRegistration = { onResetSalonRegistration(currentSalon?.id) }
                )
            }
        }
    }

    if (showSupportDialog) {
        HelpAndSupportCenterDialog(
            isPartnerMode = true,
            onDismiss = { showSupportDialog = false },
            onOpenFeedbackForm = {
                showSupportDialog = false
                showFeedbackDialog = true
            },
            onOpenTerms = {
                showSupportDialog = false
                activeLegalTab = LegalPolicyTab.TERMS
            },
            onOpenPrivacy = {
                showSupportDialog = false
                activeLegalTab = LegalPolicyTab.PRIVACY
            }
        )
    }

    if (showFeedbackDialog) {
        SendFeedbackDialog(
            userName = partnerProfile?.name ?: (currentSalon?.name ?: "Salon Partner"),
            userRole = "partner",
            onDismiss = { showFeedbackDialog = false },
            onSubmitFeedback = onSubmitFeedback
        )
    }

    // Legal Policy screens are rendered via full-screen state above
}

/**
 * Top Header bar for Partner Mode
 */
@Composable
fun PartnerTopBar(
    partnerName: String,
    salonName: String,
    onOpenSupportCenter: () -> Unit = {},
    onExitPartnerMode: () -> Unit
) {
    Surface(
        color = Color(0xFF111827),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onOpenSupportCenter,
                    modifier = Modifier.testTag("partner_topbar_support_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = "Partner Help & Support",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
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
                        maxLines = 1,
                        softWrap = false
                    )
                }
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

private var activeLiveQueueMediaPlayer: MediaPlayer? = null

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
    isGlobalRushModeEnabled: Boolean = false,
    onOpenPinVerification: (Booking) -> Unit,
    onAcceptBooking: (String) -> Unit = {},
    onAcceptBookingWithTime: (String, String) -> Unit = { id, _ -> onAcceptBooking(id) },
    onRejectBooking: (String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    var bookingForSlotAllocation by remember { mutableStateOf<Booking?>(null) }
    val isLocalRushMode = Booking.isLocalRushMode(pendingBookings.size)
    val isRushActive = Booking.isRushModeActive(isGlobalRushModeEnabled, pendingBookings.size)

    // Detect the latest 'Pending' booking and count of 'Pending' bookings in the Partner's Live Queue
    val pendingOnlyList = remember(pendingBookings) {
        pendingBookings.filter { it.status.trim().equals("Pending", ignoreCase = true) }
    }
    val latestPendingBooking = remember(pendingOnlyList) {
        pendingOnlyList.maxByOrNull { it.createdAt } ?: pendingOnlyList.firstOrNull()
    }

    // Trigger real-time loud custom audio (MediaPlayer with R.raw.loud_alert) and strong waveform vibration whenever a new 'Pending' booking appears
    LaunchedEffect(latestPendingBooking?.id, pendingOnlyList.size) {
        if (latestPendingBooking != null) {
            try {
                // 1. Loud Custom Audio with MediaPlayer (R.raw.loud_alert)
                try {
                    activeLiveQueueMediaPlayer?.let { prev ->
                        try {
                            if (prev.isPlaying) prev.stop()
                            prev.release()
                        } catch (_: Exception) {
                        }
                    }
                    activeLiveQueueMediaPlayer = null
                    MediaPlayer.create(
                        context,
                        com.example.R.raw.loud_alert
                    )?.apply {
                        activeLiveQueueMediaPlayer = this
                        setVolume(1.0f, 1.0f)
                        setOnCompletionListener { mp ->
                            try {
                                mp.release()
                            } catch (_: Exception) {
                            }
                            if (activeLiveQueueMediaPlayer === mp) {
                                activeLiveQueueMediaPlayer = null
                            }
                        }
                        start()
                    }
                } catch (_: Exception) {
                }

                // 2. Enforce Vibration with strong distinct waveform pattern
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
                // Lifecycle-safe guard if device is muted or lacks audio/vibration hardware
            }
        }
    }

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
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
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
                        text = if (isRushActive) {
                            "LIVE QUEUE (${pendingBookings.size} WAITING • RUSH ETA ON)"
                        } else {
                            "LIVE QUEUE (${pendingBookings.size} WAITING)"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isRushActive) AmberPrimary else Color.White,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "${completedBookings.size} Finished",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End
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
                    onAcceptClick = {
                        if (isRushActive) {
                            bookingForSlotAllocation = booking
                        } else {
                            onAcceptBooking(booking.id)
                        }
                    },
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

    bookingForSlotAllocation?.let { targetBooking ->
        val evaluatedId = targetBooking.resolvedBookingId.ifBlank { targetBooking.id }
        val directQueueCount = pendingBookings.count { b ->
            val bId = b.resolvedBookingId.ifBlank { b.id }
            bId != evaluatedId &&
                b.id != targetBooking.id &&
                Booking.isStatusPendingOrAccepted(b.status)
        }
        val countedToday = Booking.countActiveInQueueToday(
            bookings = pendingBookings,
            salonId = salon?.id.orEmpty().ifBlank { targetBooking.salonId },
            partnerId = salon?.partnerId.orEmpty().ifBlank { targetBooking.partnerId },
            ownerId = salon?.ownerId.orEmpty(),
            salonName = salon?.name.orEmpty().ifBlank { targetBooking.salonName },
            excludeBookingId = evaluatedId
        )
        val activeInQueue = maxOf(directQueueCount, countedToday)
        val matchedService = salon?.services?.find {
            it.name.equals(targetBooking.service, ignoreCase = true) ||
                it.serviceName.equals(targetBooking.service, ignoreCase = true)
        }
        val defaultServiceMins = when {
            matchedService != null && matchedService.duration > 0 -> matchedService.duration
            matchedService != null && matchedService.durationMinutes > 0 -> matchedService.durationMinutes
            salon != null && salon.waitingTimeMinutes in 10..60 -> salon.waitingTimeMinutes
            else -> 20
        }
        PartnerEtaAllocationDialog(
            booking = targetBooking,
            activeBookingsAheadCount = activeInQueue,
            defaultServiceDurationMinutes = defaultServiceMins,
            onDismiss = { bookingForSlotAllocation = null },
            onConfirmAllocatedTime = { finalAllocatedTime ->
                val bookingId = targetBooking.id
                bookingForSlotAllocation = null
                onAcceptBookingWithTime(bookingId, finalAllocatedTime)
            }
        )
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
                    .heightIn(min = 64.dp)
                    .testTag("massive_shop_toggle_button"),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
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
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
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
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Current State Badge ("4 CHAIRS FREE" / "CUTTING HAIR") - single-line non-wrapping
                Box(
                    modifier = Modifier
                        .heightIn(min = 26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBg)
                        .border(1.dp, statusBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("current_status_badge"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isBusy) "🔴 CUTTING HAIR (${waitingTimeMinutes}m wait)" else "🟢 $availableChairs CHAIRS FREE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isBusy) RedAlert else GreenSuccess,
                        maxLines = 1,
                        softWrap = false
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
                        .heightIn(min = 50.dp)
                        .testTag("status_free_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isBusy) GreenSuccess else Color(0xFF1E293B),
                        contentColor = if (!isBusy) Color.White else Color(0xFF94A3B8)
                    ),
                    border = if (!isBusy) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "🟢",
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "Free (Available)",
                            fontSize = 12.sp,
                            fontWeight = if (!isBusy) FontWeight.Black else FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // "✂️ Busy (Cutting Hair)" Button
                Button(
                    onClick = { onStatusChange("BUSY") },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("status_busy_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBusy) RedAlert else Color(0xFF1E293B),
                        contentColor = if (isBusy) Color.White else Color(0xFF94A3B8)
                    ),
                    border = if (isBusy) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "✂️",
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "Busy (Cutting)",
                            fontSize = 12.sp,
                            fontWeight = if (isBusy) FontWeight.Black else FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
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
                        text = "Active Chairs: $availableChairs",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, AmberPrimary, RoundedCornerShape(8.dp))
                                .clickable {
                                    if (availableChairs > 1) {
                                        onChairAndWaitChange(currentStatus, availableChairs - 1, waitingTimeMinutes)
                                    }
                                }
                                .testTag("chair_count_dec"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "-",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberPrimary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AmberPrimary)
                                .padding(horizontal = 12.dp)
                                .testTag("chair_count_$availableChairs"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$availableChairs",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1E1402)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, AmberPrimary, RoundedCornerShape(8.dp))
                                .clickable {
                                    if (availableChairs < 99) {
                                        onChairAndWaitChange(currentStatus, availableChairs + 1, waitingTimeMinutes)
                                    }
                                }
                                .testTag("chair_count_inc"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberPrimary
                            )
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
    val context = LocalContext.current
    val isAccepted = booking.status.equals("accepted", ignoreCase = true) ||
        booking.status.equals("in_progress", ignoreCase = true)
    val isActiveBooking = SecurityValidator.isBookingActiveStatus(booking.status)
    val displayPhone = SecurityValidator.maskCustomerPhoneForStatus(booking.customerPhone, booking.status)

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
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isAccepted) Color(0xFF064E3B) else Color(0xFF3B2306))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isAccepted) "✓ ACCEPTED" else "NEW",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isAccepted) GreenSuccess else AmberPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (booking.customerPhone.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = displayPhone,
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // Masked 4-Digit PIN indicator (NEVER exposes plain-text servicePin on Partner UI)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "PIN: ••••",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA5B4FC),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Slot Badge & Call Customer Button (ONLY allowed while booking is actively pending/accepted/in_progress)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF451A03))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⏱ ${booking.allocatedTime.ifBlank { booking.timeSlot }}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (isActiveBooking && booking.customerPhone.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF064E3B))
                                .border(1.dp, GreenSuccess.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .clickable {
                                    launchPhoneDialer(context, booking.customerPhone)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("call_customer_btn_${booking.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Call Customer",
                                tint = GreenSuccess,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Call Customer",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA7F3D0),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
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
                        color = Color(0xFFF1F5F9),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "₹${booking.servicePrice}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = GreenSuccess,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Accept & Decline Action Row (Syncs directly to Firestore bookings/{bookingId})
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isAccepted) {
                    Button(
                        onClick = onAcceptClick,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("accept_booking_btn_${booking.id}"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GreenSuccess,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Accept Booking",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Accept",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRejectClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("reject_booking_btn_${booking.id}"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAlert),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RedAlert.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Decline Booking",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Decline",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // "Enter 4-digit PIN to Complete" Action Button (Requires exact customer 4-digit servicePin)
            Button(
                onClick = onVerifyPinClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("enter_pin_to_complete_btn_${booking.id}"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun CompletedBookingCard(booking: Booking) {
    val isCompleted = booking.status.equals("completed", ignoreCase = true)
    // Mask customer phone once booking is COMPLETED, CANCELLED, or REJECTED (+91 ••••••1234)
    val maskedPhone = SecurityValidator.maskCustomerPhoneForStatus(booking.customerPhone, booking.status)

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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Close,
                    contentDescription = booking.status,
                    tint = if (isCompleted) GreenSuccess else RedAlert,
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
                        text = if (isCompleted) {
                            "PIN [••••] Verified • $maskedPhone"
                        } else {
                            "${booking.status.uppercase()} • $maskedPhone (Phone Masked)"
                        },
                        fontSize = 11.sp,
                        color = if (isCompleted) Color(0xFFA7F3D0) else Color(0xFF94A3B8)
                    )
                }
            }

            Text(
                text = if (isCompleted) "+₹${booking.servicePrice}" else "₹0",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isCompleted) GreenSuccess else Color(0xFF64748B)
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
                            text = "Manage Menu — Services & Prices",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Add, Edit, or Delete custom services (Name, ₹ Price & Duration in mins).",
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
                        onValueChange = { newServiceName = SecurityValidator.sanitizeName(it, trimEdges = false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_service_name_input"),
                        label = { Text("Service Name * (Max 60 chars)", color = Color(0xFF94A3B8)) },
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
                            onValueChange = { newServicePrice = it.filter { c -> c.isDigit() }.take(5) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_service_price_input"),
                            label = { Text("Price (₹10–₹25k) *", color = Color(0xFF94A3B8)) },
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
                            onValueChange = { newServiceDuration = it.filter { c -> c.isDigit() }.take(3) },
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

                    val parsedNewPrice = newServicePrice.toIntOrNull()
                    val priceErrorText = if (newServicePrice.isNotBlank()) {
                        SecurityValidator.validateServicePriceError(parsedNewPrice)
                    } else null

                    if (priceErrorText != null) {
                        Text(
                            text = priceErrorText,
                            fontSize = 11.sp,
                            color = RedAlert,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            val cleanName = SecurityValidator.sanitizeName(newServiceName)
                            val priceVal = newServicePrice.toIntOrNull() ?: 0
                            val durationVal = (newServiceDuration.toIntOrNull() ?: 20).coerceIn(5, 300)
                            if (salon != null && cleanName.isNotBlank() && SecurityValidator.isValidServicePrice(priceVal)) {
                                onAddService(
                                    salon.id,
                                    cleanName,
                                    newServiceCategory,
                                    priceVal,
                                    durationVal,
                                    SecurityValidator.sanitizeTagline(newServiceDesc)
                                )
                                newServiceName = ""
                                newServiceDesc = ""
                            }
                        },
                        enabled = newServiceName.isNotBlank() && parsedNewPrice != null && SecurityValidator.isValidServicePrice(parsedNewPrice),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("add_service_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
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
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
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
                    text = "ACTIVE SALON SERVICES (${liveServices.size})",
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
                                onValueChange = { editName = SecurityValidator.sanitizeName(it, trimEdges = false) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Service Name (Max 60 chars)", color = Color(0xFF94A3B8)) },
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
                                    onValueChange = { editPrice = it.filter { c -> c.isDigit() }.take(5) },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Price (₹10–₹25k)", color = Color(0xFF94A3B8)) },
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
                                    onValueChange = { editDuration = it.filter { c -> c.isDigit() }.take(3) },
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
                            val editPriceInt = editPrice.toIntOrNull()
                            val editPriceError = SecurityValidator.validateServicePriceError(editPriceInt)
                            if (editPriceError != null) {
                                Text(
                                    text = editPriceError,
                                    fontSize = 11.sp,
                                    color = RedAlert,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Button(
                                onClick = {
                                    val validPrice = editPriceInt ?: service.price
                                    if (SecurityValidator.isValidServicePrice(validPrice)) {
                                        val updated = service.copy(
                                            name = SecurityValidator.sanitizeName(editName).ifBlank { service.name },
                                            price = validPrice,
                                            durationMinutes = (editDuration.toIntOrNull() ?: service.durationMinutes).coerceIn(5, 300)
                                        )
                                        onEditService(salon.id, updated)
                                        editingServiceId = null
                                    }
                                },
                                enabled = editPriceInt != null && SecurityValidator.isValidServicePrice(editPriceInt),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberPrimary,
                                    contentColor = Color(0xFF1E1402)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Save Service Changes", fontWeight = FontWeight.Bold)
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
                    .heightIn(min = 52.dp)
                    .testTag("save_menu_prices_button"),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
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
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
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
    onSaveFullProfile: (String, String, String, String, String, String, Double, Double, Int, Boolean) -> Unit = { salonId, _, _, _, addr, lm, lat, lng, _, _ ->
        onSaveLocation(salonId, addr, lm, lat, lng)
    },
    onSaveShopPhoto: (String, String) -> Unit = { _, _ -> },
    onRequestLocationUnlock: () -> Unit = {},
    onOpenSupportCenter: () -> Unit = {},
    onOpenFeedbackForm: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onResetSalonRegistration: () -> Unit = {}
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
    var isAcSalon by remember(salon?.id, salon?.isAcSalon) {
        mutableStateOf(salon?.isAcSalon ?: false)
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

    // Shop Cover Photo State (synced with Firestore `salon.imageUrl` / `salon.resolvedPhotoData`)
    var localPhotoDataUrl by remember(salon?.id, salon?.imageUrl, salon?.resolvedPhotoData) {
        mutableStateOf(salon?.imageUrl?.takeIf { it.isNotBlank() } ?: salon?.resolvedPhotoData ?: "")
    }
    var uploadStatusMessage by remember { mutableStateOf<String?>(null) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val decodedShopBitmap = remember(localPhotoDataUrl) {
        if (!localPhotoDataUrl.startsWith("http")) {
            SalonImageUtils.decodeBase64ToImageBitmap(localPhotoDataUrl)
        } else null
    }

    val coilShopModel = remember(localPhotoDataUrl) {
        SalonImageUtils.resolveCoilImageModel(localPhotoDataUrl)
    }

    // Real Gallery Image Picker (`ActivityResultContracts.PickVisualMedia()`) -> Firebase Storage / Base64 JPEG in Firestore
    val galleryImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && salon != null) {
            isUploadingPhoto = true
            uploadStatusMessage = "Uploading photo to cloud storage..."
            val base64Jpeg = SalonImageUtils.encodeUriToBase64Jpeg(context, uri, maxWidth = 800, quality = 70)
            if (!base64Jpeg.isNullOrBlank()) {
                localPhotoDataUrl = base64Jpeg
                onSaveShopPhoto(salon.id, base64Jpeg)
            }

            try {
                val storage = FirebaseStorage.getInstance().apply {
                    maxUploadRetryTimeMillis = 10_000L
                    maxOperationRetryTimeMillis = 10_000L
                }
                val storageRef = storage.reference
                    .child("salon_images/salon_${salon.id}_${System.currentTimeMillis()}.jpg")
                val compressedBytes = SalonImageUtils.compressUriToJpegBytes(context, uri, maxWidth = 960, quality = 78)
                val uploadTask = if (compressedBytes != null && compressedBytes.isNotEmpty()) {
                    storageRef.putBytes(compressedBytes)
                } else {
                    storageRef.putFile(uri)
                }

                uploadTask
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                            val downloadUrl = downloadUri.toString()
                            localPhotoDataUrl = downloadUrl
                            onSaveShopPhoto(salon.id, downloadUrl)
                            isUploadingPhoto = false
                            uploadStatusMessage = "Shop photo uploaded to Cloud Storage & live!"
                        }.addOnFailureListener {
                            isUploadingPhoto = false
                            if (!base64Jpeg.isNullOrBlank()) {
                                uploadStatusMessage = "Shop photo attached & saved!"
                            }
                        }
                    }
                    .addOnFailureListener {
                        isUploadingPhoto = false
                        if (!base64Jpeg.isNullOrBlank()) {
                            uploadStatusMessage = "Shop photo saved & live on Customer Card!"
                        } else {
                            uploadStatusMessage = "Could not process photo: ${it.message}"
                        }
                    }
            } catch (e: Exception) {
                isUploadingPhoto = false
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
                            text = if (localPhotoDataUrl.isNotBlank()) "Live on Customer Card" else "No Photo Yet",
                            fontSize = 10.sp,
                            color = if (localPhotoDataUrl.isNotBlank()) GreenSuccess else Color(0xFF94A3B8),
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
                                color = if (localPhotoDataUrl.isNotBlank()) AmberPrimary else Color(0xFF334155),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                galleryImageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("add_shop_image_box"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUploadingPhoto) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = AmberPrimary,
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = "Uploading to Cloud Storage...",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (localPhotoDataUrl.isNotBlank()) {
                            AsyncImage(
                                model = coilShopModel ?: localPhotoDataUrl,
                                contentDescription = "Uploaded Shop Cover Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Subtle gradient scrim so image is bright and text remains readable
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.35f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.72f)
                                            )
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = shopName.ifBlank { salon?.name ?: "Your Grooming Studio" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = shopTagline.ifBlank { "Instant grooming slots • Zero queue wait time" },
                                    fontSize = 11.sp,
                                    color = Color(0xFFE2E8F0),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
                                    text = "Pick a photo from your phone gallery to display on your salon card",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Remove Custom Photo button when a custom/preset photo is active
                    if (localPhotoDataUrl.isNotBlank() || decodedShopBitmap != null) {
                        OutlinedButton(
                            onClick = {
                                localPhotoDataUrl = ""
                                if (salon != null) {
                                    onSaveShopPhoto(salon.id, "")
                                    uploadStatusMessage = "Custom cover photo removed. Reverted to clean default dark header."
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .testTag("remove_custom_photo_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RedAlert.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5))
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Remove Custom Photo",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Remove Custom Photo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                                            uploadStatusMessage = "Applied '${preset.title}' cover photo!"
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

                        if (salon?.isPhoneVerified == true || partnerProfile?.isPhoneVerified == true) {
                            com.example.ui.components.VerifiedBlueTickIcon(size = 18.dp)
                        }
                    }

                    Text(
                        text = "Edit your Shop Name, Phone Number, Available Chairs, and Address. Changes appear immediately for nearby customers.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp
                    )

                    // Shop Name Input (Sanitized, max 60 chars)
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = {
                            shopName = SecurityValidator.sanitizeName(it, trimEdges = false)
                            locationSaveSuccess = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shop_name_input"),
                        label = { Text("Shop Name * (Max 60 chars)", color = Color(0xFF94A3B8)) },
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

                    // Phone Number Input (Validated against Indian 10-digit regex ^[6-9][0-9]{9}$)
                    OutlinedTextField(
                        value = shopPhone,
                        onValueChange = {
                            shopPhone = it.take(16)
                            locationSaveSuccess = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shop_phone_input"),
                        label = { Text("Phone Number * (10-Digit Indian Mobile)", color = Color(0xFF94A3B8)) },
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
                    val shopPhoneError = if (shopPhone.isNotBlank()) {
                        SecurityValidator.validateIndianMobileError(shopPhone)
                    } else null
                    if (shopPhoneError != null) {
                        Text(
                            text = shopPhoneError,
                            fontSize = 11.sp,
                            color = RedAlert,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Dynamic Active Chairs Stepper + Numeric Input
                    var profileChairText by remember(availableChairs) { mutableStateOf(availableChairs.toString()) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurface)
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Active Grooming Chairs: $availableChairs",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                                Text(
                                    text = "Adjust with [ - ] [ + ] or enter exact number of chairs",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (availableChairs > 1) {
                                            availableChairs--
                                            profileChairText = availableChairs.toString()
                                            locationSaveSuccess = false
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("profile_chair_dec_button"),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary)
                                ) {
                                    Text("-", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = AmberPrimary)
                                }

                                OutlinedTextField(
                                    value = profileChairText,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }.take(2)
                                        profileChairText = digits
                                        val parsed = digits.toIntOrNull()
                                        if (parsed != null && parsed >= 1) {
                                            availableChairs = parsed
                                            locationSaveSuccess = false
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AmberPrimary,
                                        unfocusedBorderColor = Color(0xFF475569),
                                        focusedContainerColor = Color(0xFF0F172A),
                                        unfocusedContainerColor = Color(0xFF0F172A),
                                        focusedTextColor = AmberPrimary,
                                        unfocusedTextColor = AmberPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .width(68.dp)
                                        .testTag("profile_chair_count_$availableChairs")
                                )

                                OutlinedButton(
                                    onClick = {
                                        if (availableChairs < 99) {
                                            availableChairs++
                                            profileChairText = availableChairs.toString()
                                            locationSaveSuccess = false
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("profile_chair_inc_button"),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary)
                                ) {
                                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = AmberPrimary)
                                }
                            }
                        }
                    }

                    // Shop Tagline Input (Sanitized, max 140 chars)
                    OutlinedTextField(
                        value = shopTagline,
                        onValueChange = {
                            shopTagline = SecurityValidator.sanitizeTagline(it, trimEdges = false)
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

                    // AC / Non-AC Salon Status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurface)
                            .border(1.dp, if (isAcSalon) Color(0xFF06B6D4) else Color(0xFF334155), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isAcSalon) Color(0xFF06B6D4).copy(alpha = 0.2f) else Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isAcSalon) "❄️" else "🌬️",
                                    fontSize = 18.sp
                                )
                            }
                            Column {
                                Text(
                                    text = if (isAcSalon) "AC Salon Enabled" else "Non-AC Salon",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isAcSalon) "Customer card displays cyan ❄️ AC badge" else "Customers see standard non-AC tag",
                                    fontSize = 11.sp,
                                    color = if (isAcSalon) Color(0xFF67E8F9) else Color(0xFF94A3B8)
                                )
                            }
                        }

                        Switch(
                            checked = isAcSalon,
                            onCheckedChange = {
                                isAcSalon = it
                                locationSaveSuccess = false
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF06B6D4),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.testTag("profile_ac_switch")
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // PARTNER LOCATION LOCK & UNLOCK REQUEST FLOW:
                    // Checks `isLocationLocked` and `unlockRequestStatus` ("none", "pending", "approved").
                    val resolvedUnlockStatus = when {
                        partnerProfile?.unlockRequestStatus.equals("approved", ignoreCase = true) ||
                            salon?.unlockRequestStatus.equals("approved", ignoreCase = true) -> "approved"
                        partnerProfile?.unlockRequestStatus.equals("pending", ignoreCase = true) ||
                            salon?.unlockRequestStatus.equals("pending", ignoreCase = true) -> "pending"
                        else -> (partnerProfile?.unlockRequestStatus ?: salon?.unlockRequestStatus ?: "none")
                    }
                    val isLocationLocked = if (
                        resolvedUnlockStatus.equals("approved", ignoreCase = true) ||
                        partnerProfile?.isLocationLocked == false ||
                        salon?.isLocationLocked == false
                    ) {
                        false
                    } else {
                        true
                    }
                    var localUnlockRequestSent by remember(resolvedUnlockStatus) {
                        mutableStateOf(resolvedUnlockStatus.equals("pending", ignoreCase = true))
                    }
                    val isRequestPending = localUnlockRequestSent || resolvedUnlockStatus.equals("pending", ignoreCase = true)

                    val gpsPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                        if (granted && !isLocationLocked) {
                            isFetchingGps = true
                            fetchDeviceGpsLocation(
                                context = context,
                                fallbackLat = currentLatitude,
                                fallbackLng = currentLongitude,
                                onLocationResult = { lat, lng, _ ->
                                    currentLatitude = lat
                                    currentLongitude = lng
                                    isGpsFetched = true
                                    isFetchingGps = false
                                    locationSaveSuccess = false
                                }
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("partner_locked_location_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (!isLocationLocked) GreenSuccess.copy(alpha = 0.6f) else Color(0xFF334155)
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = if (!isLocationLocked) GreenSuccess else AmberPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Registered Shop Location",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E293B))
                                        .border(
                                            1.dp,
                                            if (!isLocationLocked) GreenSuccess.copy(alpha = 0.7f) else AmberPrimary.copy(alpha = 0.6f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .testTag("location_locked_badge")
                                ) {
                                    Text(
                                        text = if (!isLocationLocked) "🔓 Location Unlocked" else "🔒 Location Locked",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (!isLocationLocked) GreenSuccess else AmberPrimary
                                    )
                                }
                            }

                            if (isLocationLocked) {
                                // Locked state: Location editing is disabled
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DarkSurface)
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = fullAddress.ifBlank { salon?.location ?: "Registered Shop Address" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (landmark.isNotBlank()) {
                                        Text(
                                            text = "Landmark: $landmark",
                                            fontSize = 11.sp,
                                            color = Color(0xFFCBD5E1)
                                        )
                                    }
                                    Text(
                                        text = "Verified GPS Coordinates: ${String.format(java.util.Locale.US, "%.4f, %.4f", currentLatitude, currentLongitude)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF38BDF8)
                                    )
                                }

                                OutlinedTextField(
                                    value = fullAddress.ifBlank { salon?.location ?: "" },
                                    onValueChange = {},
                                    enabled = false,
                                    readOnly = true,
                                    label = { Text("Shop Address (Locked)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = AmberPrimary
                                        )
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledBorderColor = Color(0xFF334155),
                                        disabledContainerColor = DarkSurface,
                                        disabledTextColor = Color(0xFF94A3B8),
                                        disabledLabelColor = Color(0xFF64748B)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("locked_shop_address_input")
                                )

                                Text(
                                    text = "For customer trust and accurate distance discovery, your physical salon coordinates are locked after onboarding. Request an Admin unlock if you need to update your location.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 15.sp
                                )

                                if (isRequestPending) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("location_unlock_pending_banner"),
                                        color = Color(0xFF064E3B).copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, GreenSuccess)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = GreenSuccess,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Request Sent to Admin",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = GreenSuccess
                                                )
                                                Text(
                                                    text = "Status: pending • Admin will approve & unlock your location shortly.",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFD1FAE5)
                                                )
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        localUnlockRequestSent = true
                                        onRequestLocationUnlock()
                                        android.widget.Toast.makeText(
                                            context,
                                            "Request Sent to Admin",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("request_location_unlock_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRequestPending) Color(0xFF1E293B) else AmberPrimary,
                                        contentColor = if (isRequestPending) AmberPrimary else Color.Black
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isRequestPending) Icons.Default.CheckCircle else Icons.Default.LockOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isRequestPending) "Request Sent to Admin" else "Request Location Unlock",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            } else {
                                // Unlocked state: Admin approved location unlock (`isLocationLocked == false`)
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF064E3B).copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.7f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockOpen,
                                            contentDescription = null,
                                            tint = GreenSuccess,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Admin approved your request! You can now edit your shop address and live GPS coordinates below.",
                                            fontSize = 11.sp,
                                            color = Color(0xFFD1FAE5),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = fullAddress,
                                    onValueChange = {
                                        fullAddress = SecurityValidator.sanitizeAddress(it, trimEdges = false)
                                        locationSaveSuccess = false
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("unlocked_shop_address_input"),
                                    label = { Text("Shop Address *", color = Color(0xFF94A3B8)) },
                                    placeholder = { Text("Enter updated shop address", color = Color(0xFF64748B)) },
                                    singleLine = false,
                                    maxLines = 2,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GreenSuccess,
                                        unfocusedBorderColor = Color(0xFF475569),
                                        focusedContainerColor = DarkSurface,
                                        unfocusedContainerColor = DarkSurface,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                OutlinedTextField(
                                    value = landmark,
                                    onValueChange = {
                                        landmark = SecurityValidator.sanitizeName(it, trimEdges = false)
                                        locationSaveSuccess = false
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("unlocked_shop_landmark_input"),
                                    label = { Text("Nearby Landmark (Optional)", color = Color(0xFF94A3B8)) },
                                    placeholder = { Text("e.g. Near Metro Gate 2", color = Color(0xFF64748B)) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GreenSuccess,
                                        unfocusedBorderColor = Color(0xFF475569),
                                        focusedContainerColor = DarkSurface,
                                        unfocusedContainerColor = DarkSurface,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                OutlinedButton(
                                    onClick = {
                                        if (hasLocationPermission(context)) {
                                            isFetchingGps = true
                                            fetchDeviceGpsLocation(
                                                context = context,
                                                fallbackLat = currentLatitude,
                                                fallbackLng = currentLongitude,
                                                onLocationResult = { lat, lng, _ ->
                                                    currentLatitude = lat
                                                    currentLongitude = lng
                                                    isGpsFetched = true
                                                    isFetchingGps = false
                                                    locationSaveSuccess = false
                                                }
                                            )
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
                                        .height(42.dp)
                                        .testTag("detect_unlocked_gps_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GreenSuccess),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenSuccess)
                                ) {
                                    if (isFetchingGps) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = GreenSuccess,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Detecting Live GPS...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.MyLocation,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Detect Live GPS (${String.format(java.util.Locale.US, "%.4f, %.4f", currentLatitude, currentLongitude)})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
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
                                    availableChairs,
                                    isAcSalon
                                )
                                locationSaveSuccess = true
                            }
                        },
                        enabled = fullAddress.isNotBlank() && shopName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("save_shop_location_button"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
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
                                color = DarkBackground,
                                maxLines = 1,
                                softWrap = false
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
                                text = "Shop Details & GPS location saved!",
                                fontSize = 11.sp,
                                color = Color(0xFFA7F3D0),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 3. Partner Legal & Play Store Compliance Buttons + Help & Support Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("partner_legal_settings_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Legal & Play Store Policies",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Review TrimTime's official Privacy Policy and Partner Terms & Conditions.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenPrivacyPolicy,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("partner_privacy_policy_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.65f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = "Privacy Policy",
                                tint = GreenSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Privacy Policy",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenTerms,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("partner_terms_conditions_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.65f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gavel,
                                contentDescription = "Terms & Conditions",
                                tint = AmberPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Terms & Conditions",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 4. Partner Help, Support, Feedback & Legal Policies Card
        item {
            SupportFeedbackLegalSectionCard(
                isHinglish = false,
                isPartnerMode = true,
                cardBackgroundColor = DarkCardSurface,
                primaryTextColor = Color.White,
                secondaryTextColor = Color(0xFF94A3B8),
                onOpenSupportCenter = onOpenSupportCenter,
                onOpenFeedbackForm = onOpenFeedbackForm,
                onOpenTerms = onOpenTerms,
                onOpenPrivacyPolicy = onOpenPrivacyPolicy
            )
        }
    }
}

/**
 * TAB 3: Partner Earnings / Wallet Tab
 * Calculates and displays total revenue generated from bookings marked as "Completed",
 * along with average ticket value, pending queue revenue, and completed transaction history.
 */
@Composable
fun PartnerEarningsWalletTabContent(
    salon: Salon?,
    completedBookings: List<Booking>,
    pendingBookings: List<Booking>,
    allSalonBookings: List<Booking>
) {
    val totalRevenue = completedBookings.sumOf { it.servicePrice }
    val completedCount = completedBookings.size
    val pendingQueueRevenue = pendingBookings.sumOf { it.servicePrice }
    val averageTicket = if (completedCount > 0) totalRevenue / completedCount else 0
    val declinedOrCancelledCount = allSalonBookings.count {
        it.status.equals("cancelled", ignoreCase = true) ||
            it.status.equals("rejected", ignoreCase = true) ||
            it.status.equals("declined", ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("partner_earnings_wallet_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("partner_total_revenue_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, GreenSuccess)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(GreenSuccess.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = "Total Revenue",
                                    tint = GreenSuccess,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "EARNINGS & WALLET",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFA7F3D0),
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = salon?.name ?: "My Salon",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF022C22))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "100% Pay at Salon",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenSuccess
                            )
                        }
                    }

                    HorizontalDivider(color = GreenSuccess.copy(alpha = 0.3f))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Total Completed Revenue",
                            fontSize = 12.sp,
                            color = Color(0xFFD1FAE5)
                        )
                        Text(
                            text = "₹$totalRevenue",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.testTag("partner_wallet_total_amount")
                        )
                        Text(
                            text = "Generated from $completedCount verified Completed booking${if (completedCount == 1) "" else "s"}",
                            fontSize = 11.sp,
                            color = Color(0xFFA7F3D0),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Key Metrics Breakdown Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "COMPLETED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "$completedCount",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = GreenSuccess
                        )
                        Text(
                            text = "Avg ₹$averageTicket / visit",
                            fontSize = 10.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "IN QUEUE (PENDING)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "₹$pendingQueueRevenue",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = AmberPrimary
                        )
                        Text(
                            text = "${pendingBookings.size} active request${if (pendingBookings.size == 1) "" else "s"}",
                            fontSize = 10.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "DECLINED / CANCEL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "$declinedOrCancelledCount",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = RedAlert
                        )
                        Text(
                            text = "₹0 charged",
                            fontSize = 10.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "COMPLETED BOOKINGS LEDGER (${completedBookings.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF94A3B8),
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (completedBookings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No Completed Earnings Yet",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Verify customer 4-digit PINs in the Live Queue tab to mark services Completed and record revenue here.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(completedBookings, key = { it.id }) { booking ->
                CompletedBookingCard(booking = booking)
            }
        }
    }
}

/**
 * Smart ETA & Dynamic Slot Allocation Confirmation Dialog for Salon Partners.
 * Triggered when the Partner taps "Accept" on an incoming 'Pending' booking.
 * Shows the system-calculated time based on active Live Queue and gives the Partner
 * quick options to adjust it ("Give exactly this time", "+10 Mins", "+20 Mins", "+30 Mins")
 * before confirming and writing `allocatedTime` to Firestore.
 */
@Composable
fun PartnerEtaAllocationDialog(
    booking: Booking,
    activeBookingsAheadCount: Int,
    defaultServiceDurationMinutes: Int = 20,
    onDismiss: () -> Unit,
    onConfirmAllocatedTime: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedOffsetMins by remember { mutableStateOf(0) }
    var firestoreActiveInQueue by remember(booking.id, activeBookingsAheadCount) {
        mutableStateOf(activeBookingsAheadCount)
    }

    // Live Firestore query on the active named database for today's bookings where status is IN ['Pending', 'Accepted']
    // (excluding the exact booking currently being evaluated)
    LaunchedEffect(booking.id, booking.salonId, activeBookingsAheadCount) {
        firestoreActiveInQueue = maxOf(firestoreActiveInQueue, activeBookingsAheadCount)
        try {
            val targetSalonId = booking.salonId.ifBlank { booking.partnerId }.trim()
            val targetPartnerId = booking.partnerId.ifBlank { booking.salonId }.trim()
            val targetSalonName = booking.salonName.trim()
            val evaluatedId = booking.resolvedBookingId.ifBlank { booking.id }.trim()
            val candidateIds = setOf(
                targetSalonId,
                targetPartnerId,
                if (targetPartnerId.isNotBlank()) "salon_$targetPartnerId" else "",
                targetSalonId.removePrefix("salon_")
            ).filter { it.isNotBlank() }

            val app = com.google.firebase.FirebaseApp.getInstance()
            val dbId = try {
                context.getString(com.example.R.string.firestore_database_id)
            } catch (_: Exception) {
                "ai-studio-260b673c-6e6b-45c7-b680-335d42dc251f"
            }
            val db = if (dbId.isNotBlank() && dbId != "(default)") {
                com.google.firebase.firestore.FirebaseFirestore.getInstance(app, dbId)
            } else {
                com.google.firebase.firestore.FirebaseFirestore.getInstance(app)
            }

            db.collection("bookings")
                .get()
                .addOnSuccessListener { snapshot ->
                    val nowMs = System.currentTimeMillis()
                    val count = snapshot.documents.count { doc ->
                        val docId = (doc.getString("bookingId") ?: doc.getString("id") ?: doc.id).trim()
                        val docSalonId = (doc.getString("salonId") ?: "").trim()
                        val docPartnerId = (doc.getString("partnerId") ?: "").trim()
                        val docSalonName = (doc.getString("salonName") ?: "").trim()
                        val rawStatus = doc.getString("status")?.trim().orEmpty()
                        val dateStr = (doc.getString("date") ?: doc.getString("bookingDate")).orEmpty().trim()
                        val createdAtMs = (doc.get("createdAt") as? Number)?.toLong() ?: nowMs
                        val isExcluded = docId == evaluatedId || doc.id == evaluatedId || doc.id == booking.id
                        val matchesSalon = docSalonId in candidateIds ||
                            docPartnerId in candidateIds ||
                            (targetSalonName.isNotBlank() && docSalonName.equals(targetSalonName, ignoreCase = true))
                        val isPendingOrAccepted = Booking.isStatusPendingOrAccepted(rawStatus)
                        val isToday = dateStr.isBlank() ||
                            dateStr.equals("Today", ignoreCase = true) ||
                            Booking.isSameCalendarDay(createdAtMs, nowMs)
                        !isExcluded && matchesSalon && isPendingOrAccepted && isToday
                    }
                    firestoreActiveInQueue = maxOf(activeBookingsAheadCount, count)
                }
        } catch (_: Exception) {
        }
    }

    val activeInQueue = maxOf(activeBookingsAheadCount, firestoreActiveInQueue)
    val systemWaitMins = Booking.calculateSystemWaitMinutes(
        activeBookingsCount = activeInQueue,
        defaultServiceDurationMinutes = defaultServiceDurationMinutes
    )
    val totalWaitMins = systemWaitMins + selectedOffsetMins
    val calculatedClockTime = Booking.calculateFormattedEtaClockTime(
        activeBookingsCount = activeInQueue,
        defaultServiceDurationMinutes = defaultServiceDurationMinutes,
        extraOffsetMinutes = selectedOffsetMins
    )
    val previewFormattedSlot = if (totalWaitMins > 0) {
        "$calculatedClockTime (~$totalWaitMins mins)"
    } else {
        calculatedClockTime
    }

    val quickOffsets = listOf(
        0 to "Exact Time",
        10 to "+10 Mins",
        20 to "+20 Mins",
        30 to "+30 Mins"
    )

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("partner_eta_allocation_dialog"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, AmberPrimary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AmberPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Smart ETA Allocation",
                                tint = AmberPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "ALLOCATE ARRIVAL TIME",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberPrimary,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = booking.customerName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                // System Calculated ETA Summary Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SYSTEM CALCULATED ETA ($activeInQueue active in queue)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = previewFormattedSlot,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = GreenSuccess,
                            modifier = Modifier.testTag("partner_eta_dialog_preview_time")
                        )
                        Text(
                            text = "Service: ${booking.service} (₹${booking.servicePrice}) • Requested: ${booking.cleanAllocatedClockTime}",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }

                Text(
                    text = "Adjust Customer Arrival Time (Festival Rush Control):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Quick Offset Option Buttons ("Exact Time", "+10 Mins", "+20 Mins", "+30 Mins")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickOffsets.forEach { (offsetMins, label) ->
                        val isSelected = selectedOffsetMins == offsetMins
                        val optionTimePreview = Booking.calculateFormattedEtaClockTime(
                            activeBookingsCount = activeInQueue,
                            defaultServiceDurationMinutes = defaultServiceDurationMinutes,
                            extraOffsetMinutes = offsetMins
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOffsetMins = offsetMins }
                                .testTag("partner_eta_option_$offsetMins"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF064E3B) else Color(0xFF1E293B)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) GreenSuccess else Color(0xFF334155)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = if (isSelected) GreenSuccess else AmberPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = optionTimePreview,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) Color(0xFFA7F3D0) else AmberPrimary
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("partner_eta_cancel_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color(0xFFCBD5E1),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { onConfirmAllocatedTime(calculatedClockTime) },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("partner_eta_confirm_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GreenSuccess,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Confirm & Send Time",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

