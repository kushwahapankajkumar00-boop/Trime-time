package com.example.ui.customer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.Booking
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedAlert
import com.example.viewmodel.MyBookingsUiState
import com.example.viewmodel.MyBookingsViewModel

/**
 * Comprehensive 'My Bookings' Screen for the Customer Side (`MyBookingsScreen.kt`).
 *
 * Features:
 * 1. Clean `TopAppBar` titled "My Bookings" with back navigation and refresh action.
 * 2. `TabRow` to switch between "Upcoming" (`Pending`, `Confirmed`) and "Past" (`Completed`, `Cancelled`) lists.
 * 3. `LazyColumn` displaying `BookingCard` items with Salon Name, Date & Time, and a prominent
 *    Colored Status Marker (Yellow/Orange for Pending, Green for Confirmed/Completed, Red for Cancelled).
 * 4. "No bookings found" empty state illustration and text when the selected list is empty.
 * 5. Full Loading, Success, and Error state rendering via `MyBookingsViewModel`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsScreen(
    myBookingsViewModel: MyBookingsViewModel = viewModel(),
    localBookings: List<Booking> = emptyList(),
    onBackClick: (() -> Unit)? = null,
    onCancelBooking: ((String) -> Unit)? = null,
    onBrowseSalons: (() -> Unit)? = null,
    onRateSalon: ((salonId: String, stars: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (onBackClick != null) {
        BackHandler { onBackClick() }
    }

    androidx.compose.runtime.LaunchedEffect(localBookings) {
        myBookingsViewModel.syncLocalBookings(localBookings)
    }

    val uiState by myBookingsViewModel.uiState.collectAsState()
    val upcomingBookings by myBookingsViewModel.upcomingBookings.collectAsState()
    val pastBookings by myBookingsViewModel.pastBookings.collectAsState()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Upcoming", "Past")

    var bookingToRate by remember { mutableStateOf<Booking?>(null) }
    var isSubmittingRating by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("my_bookings_screen"),
        containerColor = DarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "My Bookings",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                        }
                    },
                    navigationIcon = {
                        if (onBackClick != null) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("my_bookings_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { myBookingsViewModel.fetchBookings() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("my_bookings_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Bookings",
                                tint = AmberPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkSurface,
                        titleContentColor = Color.White
                    )
                )

                // TabRow to switch between "Upcoming" and "Past" bookings
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = DarkSurface,
                    contentColor = AmberPrimary,
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                height = 3.dp,
                                color = AmberPrimary
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("my_bookings_tab_row")
                ) {
                    tabs.forEachIndexed { index, title ->
                        val count = if (index == 0) upcomingBookings.size else pastBookings.size
                        val isSelected = selectedTabIndex == index
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTabIndex = index },
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .testTag(if (index == 0) "tab_upcoming_bookings" else "tab_past_bookings"),
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = title,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = if (isSelected) AmberPrimary else Color(0xFF94A3B8)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) AmberPrimary.copy(alpha = 0.2f)
                                                else Color(0xFF1E293B)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) AmberPrimary else Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
        ) {
            when (val state = uiState) {
                is MyBookingsUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .testTag("my_bookings_loading_state"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = AmberPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Loading your bookings...",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                is MyBookingsUiState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .align(Alignment.Center)
                            .testTag("my_bookings_error_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF281114)),
                        border = BorderStroke(1.dp, RedAlert.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = RedAlert,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Unable to Load Bookings",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = state.message,
                                color = Color(0xFFFCA5A5),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { myBookingsViewModel.fetchBookings() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberPrimary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("my_bookings_retry_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Retry",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                is MyBookingsUiState.Success -> {
                    val displayedBookings = if (selectedTabIndex == 0) {
                        upcomingBookings
                    } else {
                        pastBookings
                    }

                    if (displayedBookings.isEmpty()) {
                        NoBookingsEmptyState(
                            isUpcomingTab = selectedTabIndex == 0,
                            onBrowseSalons = onBrowseSalons
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("my_bookings_lazy_column"),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(
                                items = displayedBookings,
                                key = { booking ->
                                    booking.resolvedBookingId.ifBlank {
                                        "${booking.salonName}_${booking.resolvedDate}_${booking.resolvedTime}_${booking.createdAt}"
                                    }
                                }
                            ) { booking ->
                                val isPastCompletedBooking = selectedTabIndex == 1 &&
                                    booking.normalizedStatus.equals("Completed", ignoreCase = true)

                                BookingCard(
                                    booking = booking,
                                    onCancelBooking = if (selectedTabIndex == 0 && onCancelBooking != null) {
                                        {
                                            onCancelBooking(booking.resolvedBookingId)
                                            myBookingsViewModel.fetchBookings()
                                        }
                                    } else null,
                                    onRateSalonClick = if (isPastCompletedBooking) {
                                        { bookingToRate = booking }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 1-to-5 Star "Rate Salon" Dialog for Completed Bookings in the Past Tab
    bookingToRate?.let { targetBooking ->
        RateSalonDialog(
            booking = targetBooking,
            isSubmitting = isSubmittingRating,
            onDismiss = {
                if (!isSubmittingRating) {
                    bookingToRate = null
                }
            },
            onSubmitRating = { selectedStars ->
                isSubmittingRating = true
                myBookingsViewModel.rateSalon(
                    booking = targetBooking,
                    stars = selectedStars,
                    onSuccess = {
                        val salonId = targetBooking.resolvedPartnerId
                        if (salonId.isNotBlank()) {
                            onRateSalon?.invoke(salonId, selectedStars)
                        }
                        isSubmittingRating = false
                        bookingToRate = null
                        Toast.makeText(
                            context,
                            "Thank you for your feedback!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onError = { errMsg ->
                        isSubmittingRating = false
                        Toast.makeText(
                            context,
                            errMsg,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        )
    }
}

/**
 * Simple Digital Ticket Booking Card for 'My Bookings':
 * Prominently displays: "Reach Salon exactly at [Final Allocated Time]"
 * along with Salon Name, Service & Price, Status Badge, and 4-Digit Verification PIN.
 */
@Composable
fun BookingCard(
    booking: Booking,
    onCancelBooking: (() -> Unit)? = null,
    onRateSalonClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val canonicalStatus = booking.normalizedStatus
    val displayDate = booking.resolvedDate.ifBlank { "Today" }
    val finalAllocatedTime = booking.cleanAllocatedClockTime
    val isZeroWaitSlot = booking.isZeroWaitInternalSlot(nowMillis)
    val customerReachByTime = booking.customerReachByClockTime
    val isUpcoming = canonicalStatus.equals("Pending", ignoreCase = true) ||
        canonicalStatus.equals("Confirmed", ignoreCase = true)
    val isConfirmed = canonicalStatus.equals("Confirmed", ignoreCase = true)
    val isCompleted = canonicalStatus.equals("Completed", ignoreCase = true)
    val isLate = isUpcoming && booking.isLateArrival(nowMillis)
    val minsLate = if (isLate) booking.minutesLate(nowMillis) else 0
    val graceMinsLeft = if (isLate) booking.remainingGraceMinutes(nowMillis) else Booking.LATE_GRACE_PERIOD_MINUTES

    val statusColor = when {
        isLate -> RedAlert
        canonicalStatus.equals("pending", ignoreCase = true) -> Color(0xFFF59E0B) // Yellow / Orange for Pending
        canonicalStatus.equals("confirmed", ignoreCase = true) || canonicalStatus.equals("completed", ignoreCase = true) -> GreenSuccess
        canonicalStatus.equals("cancelled", ignoreCase = true) -> RedAlert
        else -> AmberPrimary
    }

    val statusBgColor = when {
        isLate -> Color(0xFF450A0A).copy(alpha = 0.9f)
        canonicalStatus.equals("pending", ignoreCase = true) -> Color(0xFF451A03).copy(alpha = 0.85f)
        canonicalStatus.equals("confirmed", ignoreCase = true) || canonicalStatus.equals("completed", ignoreCase = true) -> Color(0xFF064E3B).copy(alpha = 0.85f)
        canonicalStatus.equals("cancelled", ignoreCase = true) -> Color(0xFF450A0A).copy(alpha = 0.85f)
        else -> Color(0xFF1E293B)
    }

    val statusIcon = when {
        isLate -> Icons.Default.ErrorOutline
        canonicalStatus.equals("pending", ignoreCase = true) -> Icons.Default.Schedule
        canonicalStatus.equals("confirmed", ignoreCase = true) || canonicalStatus.equals("completed", ignoreCase = true) -> Icons.Default.CheckCircle
        canonicalStatus.equals("cancelled", ignoreCase = true) -> Icons.Default.Cancel
        else -> Icons.Default.Schedule
    }

    val displayStatusBadgeText = if (isLate) "Late (${minsLate}m)" else canonicalStatus

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("booking_card_${booking.resolvedBookingId}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(
            width = if (isConfirmed || isLate) 1.5.dp else 1.dp,
            color = statusColor.copy(alpha = if (isConfirmed || isLate) 0.75f else 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Digital Ticket Top Colored Accent Strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(statusColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Ticket Header: Salon Name, Service, and Status Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DIGITAL SALON PASS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberPrimary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = booking.salonName.ifBlank { "TrimTime Salon" },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (booking.service.isNotBlank()) {
                                Text(
                                    text = "${booking.service} • ₹${booking.servicePrice} ($displayDate)",
                                    fontSize = 12.sp,
                                    color = Color(0xFFCBD5E1),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Status Chip
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusBgColor)
                            .border(1.dp, statusColor, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("booking_status_marker_${booking.resolvedBookingId}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = displayStatusBadgeText,
                            tint = statusColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = displayStatusBadgeText,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Digital Ticket Hero Banner: "Reach Salon exactly at [Final Allocated Time]" + Late Warning
                if (isUpcoming) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when {
                                    isLate -> Color(0xFF450A0A).copy(alpha = 0.85f)
                                    isConfirmed -> Color(0xFF064E3B).copy(alpha = 0.75f)
                                    else -> Color(0xFF451A03).copy(alpha = 0.65f)
                                }
                            )
                            .border(
                                width = 1.5.dp,
                                color = when {
                                    isLate -> RedAlert
                                    isConfirmed -> GreenSuccess
                                    else -> AmberPrimary
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("booking_allocated_time_banner_${booking.resolvedBookingId}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLate) Icons.Default.ErrorOutline else Icons.Default.AccessTime,
                                    contentDescription = "Final Allocated Time",
                                    tint = when {
                                        isLate -> RedAlert
                                        isConfirmed -> GreenSuccess
                                        else -> AmberPrimary
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isZeroWaitSlot) {
                                        "Reach by $customerReachByTime"
                                    } else {
                                        "Reach Salon exactly at $finalAllocatedTime"
                                    },
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Text(
                                text = when {
                                    isLate -> "⚠️ LATE ARRIVAL ($minsLate min late) • Auto-expires in ${graceMinsLeft}m grace period"
                                    isConfirmed && isZeroWaitSlot -> "Confirmed by Salon (Slot: $finalAllocatedTime) • Zero waiting at the shop"
                                    isConfirmed -> "Confirmed by Salon • Zero waiting at the shop"
                                    isZeroWaitSlot -> "Booked at $finalAllocatedTime • Awaiting Salon Partner confirmation"
                                    else -> "Awaiting Salon Partner confirmation"
                                },
                                color = when {
                                    isLate -> Color(0xFFFCA5A5)
                                    isConfirmed -> Color(0xFFA7F3D0)
                                    else -> Color(0xFFFDE68A)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Date: $displayDate",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Slot: $finalAllocatedTime",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Perforated Ticket Stub Footer: 4-Digit PIN & Cancel Action
                if (isUpcoming && (booking.pin.isNotBlank() || onCancelBooking != null)) {
                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (booking.pin.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, AmberPrimary.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Service PIN",
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "PIN: ${booking.pin}",
                                    color = AmberPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        if (onCancelBooking != null) {
                            OutlinedButton(
                                onClick = onCancelBooking,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, RedAlert.copy(alpha = 0.7f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .testTag("cancel_booking_btn_${booking.resolvedBookingId}")
                            ) {
                                Text(
                                    text = "Cancel Booking",
                                    color = RedAlert,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Prominent "Rate Salon" Button for Completed Bookings in the Past Tab
                if (isCompleted && onRateSalonClick != null) {
                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                    Button(
                        onClick = onRateSalonClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("rate_salon_btn_${booking.resolvedBookingId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rate Salon",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Rate Salon",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog allowing the customer to submit a 1 to 5 star rating for a completed salon booking.
 */
@Composable
private fun RateSalonDialog(
    booking: Booking,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmitRating: (Int) -> Unit
) {
    var selectedStars by rememberSaveable { mutableIntStateOf(5) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rate_salon_dialog"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
            border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AmberPrimary.copy(alpha = 0.16f))
                        .border(1.dp, AmberPrimary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rate Salon",
                        tint = AmberPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Rate Salon",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "How was your experience at ${booking.salonName.ifBlank { "this salon" }}?",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 1 to 5 Star Rating Selector Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (star in 1..5) {
                        val isFilled = star <= selectedStars
                        IconButton(
                            onClick = { selectedStars = star },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("rate_salon_star_$star")
                        ) {
                            Icon(
                                imageVector = if (isFilled) Icons.Default.Star else Icons.Outlined.StarOutline,
                                contentDescription = "$star Star${if (star > 1) "s" else ""}",
                                tint = if (isFilled) AmberPrimary else Color(0xFF64748B),
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "$selectedStars out of 5 Stars",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberPrimary
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("cancel_rate_salon_btn")
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = { onSubmitRating(selectedStars) },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 48.dp)
                            .testTag("submit_salon_rating_btn")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                text = "Submit Rating",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clean Empty State UI when "Upcoming" or "Past" bookings list is empty.
 * Always displays "No bookings found" along with a helpful illustration and call-to-action.
 */
@Composable
private fun NoBookingsEmptyState(
    isUpcomingTab: Boolean,
    onBrowseSalons: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("no_bookings_empty_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B))
                .border(2.dp, AmberPrimary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.EventBusy,
                contentDescription = "No bookings found",
                tint = AmberPrimary,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "No bookings found",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isUpcomingTab) {
                "You don't have any upcoming salon appointments right now. Book a slot at a nearby salon to skip the waiting line!"
            } else {
                "Your completed and cancelled salon appointments will appear here."
            },
            fontSize = 13.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        if (isUpcomingTab && onBrowseSalons != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onBrowseSalons,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberPrimary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("empty_bookings_browse_salons_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Explore Nearby Salons",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
