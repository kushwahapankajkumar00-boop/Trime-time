package com.example.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Booking
import com.example.model.Salon
import com.example.ui.components.launchGeoDirections
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedAlert

@Composable
fun CustomerBookingsContent(
    bookings: List<Booking>,
    currentUserId: String = "",
    salons: List<Salon> = emptyList(),
    cancellationCount: Int = 0,
    isSuspended: Boolean = false,
    onViewBookingDetails: (Booking) -> Unit,
    onCancelBooking: (String) -> Unit
) {
    val userBookings = if (currentUserId.isNotBlank()) {
        bookings.filter { it.customerId == currentUserId }
    } else {
        emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Strike / Anti-Spam Policy Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("bookings_anti_spam_banner"),
            colors = CardDefaults.cardColors(
                containerColor = if (isSuspended) Color(0xFF2D1214) else Color(0xFF131B2E)
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    if (isSuspended) RedAlert else Color(0xFF3B82F6)
                )
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isSuspended) Icons.Default.Cancel else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isSuspended) RedAlert else AmberPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = if (isSuspended) "Account Suspended: Cancellation Limit Exceeded" else "Anti-Spam & Booking Rules",
                        color = if (isSuspended) RedAlert else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isSuspended)
                            "Your account has been suspended for too many cancellations"
                        else
                            "Cancellation Strikes: $cancellationCount/3 • Max 1 Active Booking Allowed",
                        color = if (isSuspended) Color(0xFFFCA5A5) else Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (userBookings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No bookings yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = "Switch to 'Nearby Salons' tab to book your first slot!",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("customer_bookings_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(userBookings, key = { it.id }) { booking ->
                    val matchedSalon = salons.find {
                        it.id == booking.salonId || it.name.equals(booking.salonName, ignoreCase = true)
                    }
                    CustomerBookingCard(
                        booking = booking,
                        salon = matchedSalon,
                        onViewDetails = { onViewBookingDetails(booking) },
                        onCancel = { onCancelBooking(booking.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomerBookingCard(
    booking: Booking,
    salon: Salon? = null,
    onViewDetails: () -> Unit,
    onCancel: () -> Unit
) {
    val isPending = booking.status.equals("pending", ignoreCase = true)
    val isAccepted = booking.status.equals("accepted", ignoreCase = true)
    val isActiveSlot = isPending || isAccepted
    val isCompleted = booking.status.equals("completed", ignoreCase = true)
    val isRejected = booking.status.equals("rejected", ignoreCase = true)
    val isCancelled = booking.status.equals("cancelled", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("booking_card_${booking.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Salon Name & Status Badge
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
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = booking.salonName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                // Status chip
                val statusBg = when {
                    isCompleted || isAccepted -> Color(0xFF064E3B)
                    isPending -> Color(0xFF451A03)
                    else -> Color(0xFF450A0A)
                }
                val statusText = when {
                    isCompleted -> "COMPLETED"
                    isAccepted -> "ACCEPTED • CONFIRMED"
                    isPending -> "PENDING ARRIVAL"
                    isRejected -> "REJECTED BY SALON"
                    else -> "CANCELLED"
                }
                val statusColor = when {
                    isCompleted || isAccepted -> GreenSuccess
                    isPending -> AmberPrimary
                    else -> RedAlert
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            // Service & Time Slot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = booking.service,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "Slot: ${booking.timeSlot}",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Text(
                    text = "₹${booking.servicePrice}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AmberPrimary
                )
            }

            // Highlighted 4-Digit Service PIN Box (Visible whenever pending or accepted)
            val displayPin = booking.pin.ifBlank { booking.servicePin }
            if (isActiveSlot) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isAccepted) GreenSuccess else AmberPrimary
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isAccepted) GreenSuccess else AmberPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "SERVICE PIN (SHOW TO BARBER)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCBD5E1),
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                text = if (isAccepted) "Salon accepted! Show PIN after service" else "Provide at shop after service",
                                fontSize = 10.sp,
                                color = if (isAccepted) Color(0xFFA7F3D0) else Color(0xFF94A3B8)
                            )
                        }

                        // PIN Display Box
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurface)
                                .border(1.dp, if (isAccepted) GreenSuccess else AmberPrimary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = displayPin,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberPrimary,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp
                            )
                        }
                    }
                }
            } else if (isCompleted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF064E3B))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = GreenSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Verified with PIN $displayPin • Paid ₹${booking.servicePrice} at salon",
                        fontSize = 11.sp,
                        color = Color(0xFFA7F3D0)
                    )
                }
            }

            // Bottom Actions: Pay at Salon note, Get Directions & Cancel Button if Pending or Accepted
            val context = LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        tint = GreenSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Pay at Salon",
                        fontSize = 11.sp,
                        color = Color(0xFFA7F3D0)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            launchGeoDirections(
                                context = context,
                                latitude = salon?.latitude ?: 25.5941,
                                longitude = salon?.longitude ?: 85.1376,
                                salonName = booking.salonName,
                                fallbackAddress = salon?.location ?: ""
                            )
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("booking_get_directions_${booking.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = "Get Directions",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Get Directions", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (isActiveSlot) {
                        OutlinedButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAlert),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("cancel_booking_${booking.id}")
                        ) {
                            Text(text = "Cancel Slot", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
