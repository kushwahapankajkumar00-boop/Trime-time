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
import com.example.ui.components.VerifiedBlueTickIcon
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedAlert
import com.example.util.SecurityValidator

/**
 * Post-Login One-Time Phone Number Collection Screen:
 * Displayed after a successful Google Sign-In for a NEW user (when no 10-digit mobile number
 * is saved on their `users/{uid}` Firestore document).
 * Saves the 10-digit mobile number directly to `users/{uid}` in Firestore without simulated OTP.
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
    var localError by remember { mutableStateOf<String?>(null) }

    val cleanDigits = SecurityValidator.extractTenDigitMobile(rawPhone).take(10)
    val isValid = SecurityValidator.isValidIndianMobile(cleanDigits)
    val localValidationWarning = if (cleanDigits.length == 10 && !isValid) {
        SecurityValidator.validateIndianMobileError(cleanDigits)
    } else null
    val activeErrorMessage = localError ?: errorMessage ?: localValidationWarning
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
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = AmberPrimary.copy(alpha = 0.2f)
                )
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

                // Title & Google Account Info
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = userName.ifBlank { "Welcome" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberPrimary
                        )
                        VerifiedBlueTickIcon(size = 16.dp)
                    }
                    if (userEmail.isNotBlank()) {
                        Text(
                            text = userEmail,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Enter your 10-Digit Mobile Number",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Your account is secured via Google Sign-In. Please provide your 10-digit mobile number once for salon booking updates and contact.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }

                // 10-digit Phone Number Input
                OutlinedTextField(
                    value = rawPhone,
                    onValueChange = { input ->
                        val onlyDigits = input.filter { it.isDigit() }
                        val sanitized = if (onlyDigits.length > 10 && onlyDigits.startsWith("91")) {
                            onlyDigits.drop(2).take(10)
                        } else {
                            onlyDigits.take(10)
                        }
                        rawPhone = sanitized
                        localError = null
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
                    visible = !activeErrorMessage.isNullOrBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    activeErrorMessage?.let { err ->
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

                // Save Mobile Number Button (Direct Firestore save, no OTP required)
                Button(
                    onClick = {
                        if (isValid && !showLoading) {
                            localError = null
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
                            text = "Saving Mobile Number...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isValid) "Save & Continue" else "Enter 10-Digit Mobile Number",
                            fontWeight = FontWeight.ExtraBold,
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
                        text = if (userEmail.isNotBlank()) "Sign out ($userEmail)" else "Sign out",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
