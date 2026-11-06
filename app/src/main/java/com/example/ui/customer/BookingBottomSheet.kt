package com.example.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.Booking
import com.example.model.Salon
import com.example.model.SalonService
import com.example.model.UserProfile
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedAlert

@Composable
fun BookingBottomSheet(
    salon: Salon,
    service: SalonService,
    userProfile: UserProfile,
    selectedSlot: String,
    activeLiveQueueCount: Int = 0,
    isGlobalRushModeEnabled: Boolean = false,
    hasActiveBooking: Boolean = false,
    isSuspended: Boolean = false,
    onServiceChanged: (SalonService) -> Unit = {},
    onSlotSelected: (String) -> Unit,
    onConfirm: (name: String, phone: String) -> Unit,
    onDismiss: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val initialTenDigitPhone = remember(userProfile.phone, userProfile.phoneNumber) {
        val raw = userProfile.phoneNumber.ifBlank { userProfile.phone }
        val digits = raw.filter { it.isDigit() }
        val ten = if (digits.length > 10 && digits.startsWith("91")) {
            digits.drop(2).take(10)
        } else {
            digits.takeLast(10)
        }
        if (ten.length == 10 && ten != "9876543210") ten else ""
    }

    var customerName by remember(userProfile.name) { mutableStateOf(userProfile.name) }
    var customerPhone by remember(initialTenDigitPhone) { mutableStateOf(initialTenDigitPhone) }
    val cleanPhoneDigits = customerPhone.filter { it.isDigit() }
    val isValidTenDigitPhone = cleanPhoneDigits.length == 10 &&
        cleanPhoneDigits.firstOrNull() in listOf('6', '7', '8', '9')

    val serviceDurationMins = if (service.duration > 0) {
        service.duration
    } else if (service.durationMinutes > 0) {
        service.durationMinutes
    } else {
        20
    }
    val isLocalRushMode = remember(activeLiveQueueCount) {
        Booking.isLocalRushMode(activeLiveQueueCount)
    }
    val isRushActive = remember(isGlobalRushModeEnabled, isLocalRushMode) {
        isGlobalRushModeEnabled || isLocalRushMode
    }
    val systemWaitMins = remember(activeLiveQueueCount, serviceDurationMins) {
        Booking.calculateSystemWaitMinutes(activeLiveQueueCount, serviceDurationMins)
    }
    val systemEtaTime = remember(activeLiveQueueCount, serviceDurationMins) {
        Booking.calculateFormattedEtaClockTime(activeLiveQueueCount, serviceDurationMins)
    }
    val isExceedingClosingTime = remember(salon.closingTime, activeLiveQueueCount, serviceDurationMins) {
        Booking.isEtaExceedingClosingTime(
            closingTimeStr = salon.closingTime.ifBlank { "9:00 PM" },
            activeBookingsCount = activeLiveQueueCount,
            defaultServiceDurationMinutes = serviceDurationMins
        )
    }
    val dynamicEtaSlot = if (activeLiveQueueCount > 0) {
        "Live ETA: $systemEtaTime (~${systemWaitMins}m)"
    } else {
        "Live ETA: $systemEtaTime (⚡ Instant)"
    }

    val isBusy = salon.isBusy || isRushActive
    val availableSlots = if (isRushActive) {
        listOf(
            dynamicEtaSlot,
            "Waitlist: Next in Line (~${systemWaitMins.coerceAtLeast(20)}m)",
            "11:30 AM",
            "12:15 PM",
            "02:00 PM",
            "04:30 PM"
        )
    } else if (salon.isBusy) {
        listOf(
            "Waitlist: Next in Line (~20m)",
            "11:30 AM",
            "12:15 PM",
            "02:00 PM",
            "04:30 PM",
            "06:00 PM"
        )
    } else {
        listOf(
            "In 15 mins (⚡ Instant)",
            "11:30 AM",
            "12:15 PM",
            "02:00 PM",
            "04:30 PM",
            "06:00 PM"
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 24.dp, bottom = 16.dp, start = 8.dp, end = 8.dp)
                .testTag("booking_sheet_modal"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header with Close (safe top padding & status-bar-aware container)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Select Service & Time Slot",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val displayServiceName = service.serviceName.ifBlank { service.name }
                        val displayDuration = if (service.duration > 0) service.duration else service.durationMinutes
                        Text(
                            text = "${salon.name} • $displayServiceName (₹${service.price} • ${displayDuration}m)",
                            fontSize = 12.sp,
                            color = AmberPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("close_booking_sheet")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF334155), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Dynamic Salon Custom Services Selector (fetched from salon's Firestore services list)
                if (salon.services.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SALON MENU SERVICES (${salon.services.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        salon.services.forEach { itemService ->
                            val isSelectedService = itemService.id == service.id ||
                                (itemService.id.isBlank() && itemService.name == service.name)
                            val srvLabel = itemService.serviceName.ifBlank { itemService.name }
                            val srvDuration = if (itemService.duration > 0) itemService.duration else itemService.durationMinutes
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelectedService) Color(0xFF2E2210) else DarkCardSurface)
                                    .border(
                                        width = if (isSelectedService) 1.5.dp else 1.dp,
                                        color = if (isSelectedService) AmberPrimary else Color(0xFF334155),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onServiceChanged(itemService) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("booking_sheet_service_${itemService.id}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = srvLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelectedService) AmberPrimary else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${srvDuration} mins • ${itemService.category}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Text(
                                    text = "₹${itemService.price}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelectedService) AmberPrimary else GreenSuccess
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dynamic Rush Mode (Festival or Local Crowd >= 3) System ETA Banner inside Booking Sheet
                if (isRushActive) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("booking_sheet_system_eta_card"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(AmberPrimary)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "⏱ LIVE SYSTEM ETA: $systemEtaTime",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF7DD3FC)
                        )
                        Text(
                            text = if (activeLiveQueueCount > 0) {
                                "Based on $activeLiveQueueCount active booking(s) × ${serviceDurationMins}m (~${systemWaitMins} mins). Partner will confirm your exact arrival time in My Bookings."
                            } else {
                                "0 active bookings in queue. Partner will confirm your exact arrival time ($systemEtaTime) so you don't wait at the shop."
                            },
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                }

                // Time Slots Grid
                Text(
                    text = if (isRushActive) {
                        "AVAILABLE TODAY (ADVANCE RUSH ETA SLOTS)"
                    } else {
                        "AVAILABLE TODAY (INSTANT BOOKING SLOTS)"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableSlots.chunked(2).forEach { rowSlots ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSlots.forEach { slot ->
                                val isSelected = selectedSlot == slot
                                val isInstant = slot.contains("Instant")

                                val bgColor = if (isSelected) AmberPrimary else DarkCardSurface
                                val textColor = if (isSelected) Color(0xFF1E1402) else Color.White
                                val borderColor = if (isSelected) AmberPrimary else if (isInstant) Color(0xFFD97706) else Color(0xFF334155)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bgColor)
                                        .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                                        .clickable { onSlotSelected(slot) }
                                        .padding(vertical = 10.dp, horizontal = 8.dp)
                                        .testTag("slot_option_$slot"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isInstant) {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = null,
                                                tint = if (isSelected) Color(0xFF1E1402) else AmberPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = slot,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = textColor,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Customer Info inputs
                Text(
                    text = "YOUR CONTACT INFO (10-DIGIT MOBILE MANDATORY *)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_name_input"),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color(0xFF94A3B8))
                    },
                    placeholder = { Text("Your Name *", color = Color(0xFF64748B)) },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = DarkCardSurface,
                        unfocusedContainerColor = DarkCardSurface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        val normalized = if (digits.length > 10 && digits.startsWith("91")) {
                            digits.drop(2).take(10)
                        } else {
                            digits.take(10)
                        }
                        val wasUnder10 = customerPhone.filter { it.isDigit() }.length < 10
                        customerPhone = normalized
                        if (normalized.length == 10 && wasUnder10) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_phone_input"),
                    leadingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+91", color = AmberPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    },
                    trailingIcon = {
                        if (isValidTenDigitPhone) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Valid 10-digit mobile",
                                tint = GreenSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    placeholder = { Text("10-Digit Mobile Number *", color = Color(0xFF64748B)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    ),
                    isError = cleanPhoneDigits.isNotEmpty() && !isValidTenDigitPhone,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isValidTenDigitPhone) GreenSuccess else AmberPrimary,
                        unfocusedBorderColor = if (isValidTenDigitPhone) GreenSuccess.copy(alpha = 0.6f) else Color(0xFF334155),
                        errorBorderColor = RedAlert,
                        focusedContainerColor = DarkCardSurface,
                        unfocusedContainerColor = DarkCardSurface,
                        errorContainerColor = DarkCardSurface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        errorTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                val phoneHelperText = when {
                    cleanPhoneDigits.isEmpty() -> "* Mandatory: Enter your 10-digit Indian mobile number"
                    cleanPhoneDigits.first() !in listOf('6', '7', '8', '9') -> "Invalid number: Indian mobile numbers must start with 6, 7, 8, or 9"
                    cleanPhoneDigits.length < 10 -> "Please enter all 10 digits (${cleanPhoneDigits.length}/10)"
                    else -> "✓ 10-digit mobile verified & will be saved for future bookings"
                }
                val phoneHelperColor = when {
                    isValidTenDigitPhone -> GreenSuccess
                    cleanPhoneDigits.isEmpty() -> Color(0xFF94A3B8)
                    else -> RedAlert
                }
                Text(
                    text = phoneHelperText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = phoneHelperColor,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Price & Pay at Salon Guarantee
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F261D)),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF047857)))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pay at Salon",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA7F3D0),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "₹0 Due Online • Strictly Pay Counter",
                                    fontSize = 10.sp,
                                    color = Color(0xFFD1FAE5),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            text = "₹${service.price}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = GreenSuccess,
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Rule 3: Account Suspended Warning Banner
                if (isSuspended) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2D1214))
                            .border(1.dp, RedAlert, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .testTag("account_suspended_booking_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = RedAlert,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Your account has been suspended for too many cancellations",
                            color = RedAlert,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Rule 2: One Active Booking Error Banner
                if (hasActiveBooking && !isSuspended) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2E2210))
                            .border(1.dp, AmberPrimary, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .testTag("active_booking_exists_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "You already have an active booking. Please complete or cancel it first.",
                            color = AmberPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Edge Case Protection — Closing Time Exceeded Banner
                if (isExceedingClosingTime) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF450A0A))
                            .border(1.dp, RedAlert, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .testTag("closing_time_exceeded_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = RedAlert,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Booking Blocked: Newly calculated ETA ($systemEtaTime) exceeds official closing time (${salon.closingTime.ifBlank { "9:00 PM" }}).",
                            color = Color(0xFFFECACA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Confirm / Book Button (Flexible minHeight = 52.dp so second line is never clipped)
                val isButtonEnabled = !hasActiveBooking &&
                    !isSuspended &&
                    !isExceedingClosingTime &&
                    customerName.trim().length >= 2 &&
                    isValidTenDigitPhone

                Button(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onConfirm(customerName.trim(), cleanPhoneDigits)
                    },
                    enabled = isButtonEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("confirm_booking_button"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBusy) Color(0xFFEA580C) else AmberPrimary,
                        disabledContainerColor = Color(0xFF334155),
                        contentColor = if (isBusy) Color.White else Color(0xFF1E1402),
                        disabledContentColor = Color(0xFF94A3B8)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when {
                            isSuspended -> "Account Suspended"
                            isExceedingClosingTime -> "Closed • ETA Exceeds ${salon.closingTime.ifBlank { "9:00 PM" }}"
                            hasActiveBooking -> "Booking Limit Reached (1 Active)"
                            isBusy -> "Join Waitlist & Get 4-Digit PIN"
                            else -> "Confirm Slot & Get 4-Digit PIN"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
