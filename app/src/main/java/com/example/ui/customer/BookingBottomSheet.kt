package com.example.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    hasActiveBooking: Boolean = false,
    isSuspended: Boolean = false,
    onSlotSelected: (String) -> Unit,
    onConfirm: (name: String, phone: String) -> Unit,
    onDismiss: () -> Unit
) {
    var customerName by remember { mutableStateOf(userProfile.name) }
    var customerPhone by remember { mutableStateOf(userProfile.phone) }

    val isBusy = salon.isBusy
    val availableSlots = if (isBusy) {
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
                .padding(8.dp)
                .testTag("booking_sheet_modal"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Select Time Slot",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                        Text(
                            text = "${salon.name} • ${service.name}",
                            fontSize = 12.sp,
                            color = AmberPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_booking_sheet")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Time Slots Grid
                Text(
                    text = "AVAILABLE TODAY",
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
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = textColor
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
                    text = "YOUR CONTACT INFO",
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
                    placeholder = { Text("Your Name", color = Color(0xFF64748B)) },
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
                    onValueChange = { customerPhone = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_phone_input"),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = Color(0xFF94A3B8))
                    },
                    placeholder = { Text("Phone Number", color = Color(0xFF64748B)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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

                Spacer(modifier = Modifier.height(14.dp))

                // Price & Pay at Salon Guarantee
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F261D)),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF047857)))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Pay at Salon",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA7F3D0)
                                )
                                Text(
                                    text = "₹0 Due Online • Strictly Pay Counter",
                                    fontSize = 10.sp,
                                    color = Color(0xFFD1FAE5)
                                )
                            }
                        }

                        Text(
                            text = "₹${service.price}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = GreenSuccess
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

                // Confirm / Book Button
                val isButtonEnabled = !hasActiveBooking && !isSuspended && customerName.isNotBlank() && customerPhone.isNotBlank()

                Button(
                    onClick = {
                        onConfirm(customerName, customerPhone)
                    },
                    enabled = isButtonEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_booking_button"),
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
                            hasActiveBooking -> "Booking Limit Reached (1 Active)"
                            isBusy -> "Join Waitlist & Get 4-Digit PIN"
                            else -> "Confirm Slot & Get 4-Digit PIN"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
