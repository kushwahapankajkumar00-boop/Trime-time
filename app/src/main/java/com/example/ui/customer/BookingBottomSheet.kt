package com.example.ui.customer

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

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

    var customerName by remember(userProfile.name) {
        mutableStateOf(userProfile.name.ifBlank { "Customer" })
    }
    var customerPhone by remember(initialTenDigitPhone) { mutableStateOf(initialTenDigitPhone) }
    val cleanPhoneDigits = customerPhone.filter { it.isDigit() }
    val isValidTenDigitPhone = cleanPhoneDigits.length == 10 &&
        cleanPhoneDigits.firstOrNull() in listOf('6', '7', '8', '9')

    var isEditingContact by remember {
        mutableStateOf(!isValidTenDigitPhone || customerName.trim().length < 2)
    }

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
    val hasQueueOrRush = activeLiveQueueCount > 0 || isRushActive
    val calculatedEta = remember(activeLiveQueueCount, serviceDurationMins) {
        if (activeLiveQueueCount > 0) {
            Booking.calculateFormattedEtaClockTime(activeLiveQueueCount, serviceDurationMins)
        } else {
            Booking.calculateFormattedEtaClockTime(1, serviceDurationMins)
        }
    }
    val instantClockTime = remember {
        Booking.calculateFormattedEtaClockTime(0, 15)
    }
    val effectiveSlotTime = if (hasQueueOrRush) calculatedEta else instantClockTime

    LaunchedEffect(effectiveSlotTime) {
        if (selectedSlot != effectiveSlotTime) {
            onSlotSelected(effectiveSlotTime)
        }
    }

    val isExceedingClosingTime = remember(salon.closingTime, activeLiveQueueCount, serviceDurationMins) {
        Booking.isEtaExceedingClosingTime(
            closingTimeStr = salon.closingTime.ifBlank { "9:00 PM" },
            activeBookingsCount = activeLiveQueueCount,
            defaultServiceDurationMinutes = serviceDurationMins
        )
    }

    val displayServiceName = service.serviceName.ifBlank { service.name }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 16.dp)
                .testTag("booking_sheet_modal"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(
                width = 1.5.dp,
                color = if (hasQueueOrRush) AmberPrimary else GreenSuccess.copy(alpha = 0.65f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dialog Top Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasQueueOrRush) AmberPrimary.copy(alpha = 0.18f)
                                    else GreenSuccess.copy(alpha = 0.18f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (hasQueueOrRush) Icons.Default.AccessTime else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (hasQueueOrRush) AmberPrimary else GreenSuccess,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasQueueOrRush) "High Demand Alert" else "Confirm Booking",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${salon.name} • $displayServiceName (₹${service.price})",
                                fontSize = 12.sp,
                                color = AmberPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("close_booking_sheet")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                // Core Prompt Message Card (Rush Mode / Queue vs Instant)
                if (hasQueueOrRush) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("booking_sheet_system_eta_card"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AmberPrimary)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "High Demand: You will get a slot around $calculatedEta. Confirm Booking?",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFDE68A),
                                textAlign = TextAlign.Center,
                                lineHeight = 21.sp,
                                modifier = Modifier.testTag("high_demand_eta_alert_text")
                            )
                            Text(
                                text = "We will notify you with your exact arrival time in My Bookings so you don't wait at the shop.",
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("booking_sheet_instant_card"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B).copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "🟢 No Wait • Instant Slot Ready",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFA7F3D0),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Confirm your booking for $displayServiceName (₹${service.price}) around $instantClockTime?",
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Pay at Salon Policy Summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F261D)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF047857))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Pay at Salon (₹0 Advance)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                        Text(
                            text = "₹${service.price}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = GreenSuccess
                        )
                    }
                }

                // Customer Contact Details (Compact if already verified, editable on tap or if missing)
                if (!isEditingContact && isValidTenDigitPhone && customerName.trim().length >= 2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkCardSurface)
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                            .clickable { isEditingContact = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${customerName.trim()} • +91 $cleanPhoneDigits",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Contact Info",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "YOUR CONTACT DETAILS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 0.8.sp
                        )

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
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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
                    }
                }

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
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Salon Closed Real-Time Warning Banner
                if (!isSalonOpen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF450A0A))
                            .border(1.dp, RedAlert, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .testTag("sheet_salon_closed_banner"),
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
                            text = "Currently Closed - Not accepting new bookings",
                            color = Color(0xFFFECACA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
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
                            text = "Booking Blocked: Newly calculated ETA ($calculatedEta) exceeds official closing time (${salon.closingTime.ifBlank { "9:00 PM" }}).",
                            color = Color(0xFFFECACA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Yes / No Confirmation Buttons
                val isButtonEnabled = isSalonOpen &&
                    !hasActiveBooking &&
                    !isSuspended &&
                    !isExceedingClosingTime &&
                    customerName.trim().length >= 2 &&
                    isValidTenDigitPhone

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("cancel_booking_dialog_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569))
                    ) {
                        Text(
                            text = if (hasQueueOrRush) "No" else "Cancel",
                            color = Color(0xFFCBD5E1),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Button(
                        onClick = {
                            if (!isSalonOpen) return@Button
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onSlotSelected(effectiveSlotTime)
                            onConfirm(customerName.trim(), cleanPhoneDigits)
                        },
                        enabled = isButtonEnabled,
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 50.dp)
                            .testTag("confirm_booking_button"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasQueueOrRush) AmberPrimary else GreenSuccess,
                            disabledContainerColor = Color(0xFF334155),
                            contentColor = Color(0xFF1E1402),
                            disabledContentColor = Color(0xFF94A3B8)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when {
                                !isSalonOpen -> "Currently Closed"
                                isSuspended -> "Suspended"
                                isExceedingClosingTime -> "Shop Closing"
                                hasQueueOrRush -> "Yes, Confirm"
                                else -> "Confirm Booking"
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
}
