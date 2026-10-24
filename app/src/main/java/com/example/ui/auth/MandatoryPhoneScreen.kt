package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedAlert

/**
 * Rule 1: Mandatory Phone Collection Screen (No OTP)
 * Required after Google Login when user has no phone number saved in their Firestore profile.
 * Customers must enter a 10-digit contact number before accessing the Home Screen.
 * Saved directly to Firestore without SMS/OTP costs.
 */
@Composable
fun MandatoryPhoneScreen(
    userName: String,
    userEmail: String,
    isLoading: Boolean,
    errorMessage: String?,
    onSavePhone: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rawPhone by remember { mutableStateOf("") }
    var isSavingLocally by remember { mutableStateOf(false) }

    // Strip out all non-digits. If user pastes or types "+91" prefix, strip country code '91' if length > 10
    val allDigits = rawPhone.filter { it.isDigit() }
    val cleanDigits = if (allDigits.length > 10 && allDigits.startsWith("91")) {
        allDigits.drop(2).take(10)
    } else {
        allDigits.take(10)
    }

    val isValid = cleanDigits.length == 10
    val showLoading = isLoading || isSavingLocally

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        DarkBackground,
                        Color(0xFF090D16)
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Decorative ambient glow
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AmberPrimary.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp), spotColor = AmberPrimary.copy(alpha = 0.2f))
                .testTag("mandatory_phone_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF334155), AmberPrimary.copy(alpha = 0.3f))
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Mini Logo Badge
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2E2210), Color(0xFF18181B))
                            )
                        )
                        .border(1.5.dp, AmberPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = AmberSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Title & Subtitle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Contact Number Required",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Hello $userName! To confirm your appointments and verify your 4-digit Pay-at-Salon PIN, please provide your 10-digit mobile number.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }

                // Anti-spam assurance badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "No OTP verification required. Saved securely to your profile to prevent throwaway spam bookings.",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 15.sp
                    )
                }

                // 10-digit Phone Number Input
                OutlinedTextField(
                    value = rawPhone,
                    onValueChange = { input ->
                        // Automatically strip non-digits, country code 91 if full number pasted with prefix
                        val onlyDigits = input.filter { it.isDigit() }
                        val sanitized = if (onlyDigits.length > 10 && onlyDigits.startsWith("91")) {
                            onlyDigits.drop(2).take(10)
                        } else {
                            onlyDigits.take(10)
                        }
                        rawPhone = sanitized
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mandatory_phone_input"),
                    label = { Text("10-Digit Mobile Number") },
                    placeholder = { Text("9876543210", color = Color(0xFF64748B)) },
                    leadingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = if (isValid) GreenSuccess else AmberPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+91",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    },
                    trailingIcon = {
                        if (isValid) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Valid 10 digits",
                                tint = GreenSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = "${cleanDigits.length}/10",
                                fontSize = 12.sp,
                                color = if (cleanDigits.isNotEmpty()) AmberPrimary else Color(0xFF64748B),
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isValid) GreenSuccess else AmberPrimary,
                        unfocusedBorderColor = if (cleanDigits.isNotEmpty()) Color(0xFF475569) else Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Error message banner
                AnimatedVisibility(
                    visible = !errorMessage.isNullOrBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    errorMessage?.let { err ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2D1214))
                                .border(1.dp, RedAlert.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = RedAlert,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(text = err, color = RedAlert, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Save & Continue Button
                Button(
                    onClick = {
                        if (isValid && !showLoading) {
                            isSavingLocally = true
                            onSavePhone("+91 $cleanDigits")
                        }
                    },
                    enabled = isValid && !showLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_phone_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPrimary,
                        disabledContainerColor = Color(0xFF334155),
                        contentColor = Color(0xFF1E1402),
                        disabledContentColor = Color(0xFF94A3B8)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (showLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF1E1402)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Saving to Profile...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = if (isValid) "Save & Access Home Screen" else "Enter 10-digit number (${10 - cleanDigits.length} more)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Switch account / Sign out option
                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("phone_screen_sign_out_button")
                ) {
                    Text(
                        text = "Sign out ($userEmail)",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
