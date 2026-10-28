package com.example.ui.components

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedAlert
import kotlinx.coroutines.delay

private const val OTP_NOTIFICATION_CHANNEL_ID = "trimtime_otp_verification_channel"
private const val OTP_NOTIFICATION_ID = 4829

/**
 * Fires a realistic local Android heads-up notification with the exact message:
 * "💬 TrimTime Verification OTP for +91 XXXXXXXXXX is: 4829"
 * Safe on all Android versions without requiring paid SMS gateways.
 */
fun triggerAndroidSmsNotification(
    context: Context,
    phoneTenDigits: String,
    otpCode: String
) {
    try {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                OTP_NOTIFICATION_CHANNEL_ID,
                "TrimTime OTP Verification",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instant in-app OTP verification alerts"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val messageText = "💬 TrimTime Verification OTP for +91 $phoneTenDigits is: $otpCode"
        val notification = NotificationCompat.Builder(context, OTP_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("TrimTime Verification OTP")
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(OTP_NOTIFICATION_ID, notification)
    } catch (_: Exception) {
        // Fallback relies on the prominent TopSmsOtpBanner rendered inside the app UI
    }
}

/**
 * Top SMS-Style Banner displayed at the top of the screen:
 * "💬 TrimTime Verification OTP for +91 XXXXXXXXXX is: 4829"
 */
@Composable
fun TopSmsOtpBanner(
    phoneTenDigits: String,
    otpCode: String,
    visible: Boolean,
    onAutoFillClick: ((String) -> Unit)? = null,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && otpCode.length == 4 && phoneTenDigits.isNotBlank(),
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .shadow(elevation = 14.dp, shape = RoundedCornerShape(16.dp), spotColor = GreenSuccess.copy(alpha = 0.4f))
                .testTag("top_sms_otp_banner"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF062C1E)),
            border = BorderStroke(1.5.dp, GreenSuccess)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(GreenSuccess.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sms,
                                contentDescription = "SMS OTP",
                                tint = GreenSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "MESSAGES • NOW",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF6EE7B7),
                            letterSpacing = 0.7.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss SMS Banner",
                            tint = Color(0xFFA7F3D0),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Text(
                    text = "💬 TrimTime Verification OTP for +91 $phoneTenDigits is: $otpCode",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    lineHeight = 18.sp,
                    modifier = Modifier.testTag("sms_otp_banner_text")
                )

                if (onAutoFillClick != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onAutoFillClick(otpCode) }
                                .testTag("sms_otp_autofill_chip"),
                            color = AmberPrimary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Tap to Auto-Fill $otpCode",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Standard Verified Blue Tick Icon (☑️) displayed right next to verified user names or phone numbers.
 */
@Composable
fun VerifiedBlueTickIcon(
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = Icons.Default.Verified,
        contentDescription = "Verified",
        tint = Color(0xFF1D9BF0),
        modifier = modifier
            .size(size)
            .testTag("verified_blue_tick_icon")
    )
}

@Composable
fun VerifiedNumberBadge(
    text: String = "",
    modifier: Modifier = Modifier
) {
    VerifiedBlueTickIcon(modifier = modifier)
}

/**
 * Clean "Verify Your Mobile Number" 4-Box OTP Input Screen / Modal with a 30-second "Resend OTP" timer.
 * Also supports "Forgot PIN? Verify via OTP" mode when `requireNewLoginPin = true`.
 */
@Composable
fun VerifyMobileOtpDialog(
    phoneTenDigits: String,
    expectedOtp: String,
    title: String = "Verify Your Mobile Number",
    subtitle: String = "Enter the 4-digit OTP sent to +91 $phoneTenDigits",
    requireNewLoginPin: Boolean = false,
    isLoading: Boolean = false,
    onResendOtp: () -> Unit,
    onVerified: (newLoginPinIfRequested: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentOtpTarget = expectedOtp
    var enteredOtp by remember { mutableStateOf("") }
    var newLoginPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resendCountdown by remember { mutableIntStateOf(30) }
    var showBanner by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    // Trigger Android system notification + start 30-second countdown whenever OTP is sent/resent
    LaunchedEffect(currentOtpTarget, phoneTenDigits) {
        if (currentOtpTarget.length == 4 && phoneTenDigits.length == 10) {
            showBanner = true
            triggerAndroidSmsNotification(context, phoneTenDigits, currentOtpTarget)
        }
    }

    LaunchedEffect(resendCountdown) {
        if (resendCountdown > 0) {
            delay(1000L)
            resendCountdown -= 1
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top SMS-style Banner right above the OTP card so it is impossible to miss
            TopSmsOtpBanner(
                phoneTenDigits = phoneTenDigits,
                otpCode = currentOtpTarget,
                visible = showBanner,
                onAutoFillClick = { code ->
                    enteredOtp = code
                    errorMessage = null
                },
                onDismiss = { showBanner = false }
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verify_mobile_otp_dialog"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF064E3B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = GreenSuccess,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = subtitle,
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 2
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close OTP Verification",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 4-Box OTP Input Row
                    BasicTextField(
                        value = enteredOtp,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() }.take(4)
                            enteredOtp = clean
                            errorMessage = null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        cursorBrush = SolidColor(AmberPrimary),
                        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .testTag("otp_four_box_input"),
                        decorationBox = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (i in 0 until 4) {
                                    val charStr = enteredOtp.getOrNull(i)?.toString() ?: ""
                                    val isFocusedBox = enteredOtp.length == i || (enteredOtp.length == 4 && i == 3)
                                    Box(
                                        modifier = Modifier
                                            .size(width = 58.dp, height = 62.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (charStr.isNotEmpty()) Color(0xFF172554) else DarkSurface
                                            )
                                            .border(
                                                width = if (isFocusedBox) 2.dp else 1.dp,
                                                color = when {
                                                    errorMessage != null -> RedAlert
                                                    charStr.isNotEmpty() -> GreenSuccess
                                                    isFocusedBox -> AmberPrimary
                                                    else -> Color(0xFF334155)
                                                },
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .clickable {
                                                try {
                                                    focusRequester.requestFocus()
                                                } catch (_: Exception) {
                                                }
                                            }
                                            .testTag("otp_digit_box_$i"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = charStr.ifEmpty { "•" },
                                            fontSize = if (charStr.isNotEmpty()) 24.sp else 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (charStr.isNotEmpty()) Color.White else Color(0xFF475569),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    )

                    // Optional New Password Input when resetting forgotten password via OTP
                    if (requireNewLoginPin) {
                        OutlinedTextField(
                            value = newLoginPin,
                            onValueChange = { raw ->
                                newLoginPin = raw.take(32)
                                errorMessage = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_new_login_pin_input"),
                            label = { Text("Set New Account Password", fontSize = 12.sp) },
                            placeholder = { Text("Min 4 characters", color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "New Password",
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberPrimary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = AmberPrimary,
                                unfocusedLabelColor = Color(0xFF94A3B8)
                            )
                        )
                    }

                    // 30-Second Resend OTP Timer Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (resendCountdown > 0) {
                            Text(
                                text = "Resend OTP in ${resendCountdown}s",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.testTag("otp_resend_timer_text")
                            )
                        } else {
                            OutlinedButton(
                                onClick = {
                                    onResendOtp()
                                    enteredOtp = ""
                                    errorMessage = null
                                    resendCountdown = 30
                                    showBanner = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AmberPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .testTag("otp_resend_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Resend OTP",
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Resend OTP",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Quick Auto-Fill helper button
                        OutlinedButton(
                            onClick = {
                                enteredOtp = currentOtpTarget
                                errorMessage = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.7f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .testTag("otp_quick_fill_button")
                        ) {
                            Text(
                                text = "Fill OTP ($currentOtpTarget)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenSuccess,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Error Notice
                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage ?: "",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedAlert,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("otp_error_message")
                        )
                    }

                    // Confirm / Verify Button
                    Button(
                        onClick = {
                            if (enteredOtp.trim() != currentOtpTarget.trim()) {
                                errorMessage = "Invalid 4-digit OTP. Please check the top SMS banner ($currentOtpTarget) and try again."
                                return@Button
                            }
                            if (requireNewLoginPin && newLoginPin.trim().length < 4) {
                                errorMessage = "Please enter a new password (at least 4 characters)."
                                return@Button
                            }
                            onVerified(newLoginPin.trim().ifBlank { null })
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("verify_otp_confirm_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GreenSuccess,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (requireNewLoginPin) {
                                "Verify OTP & Save Password"
                            } else {
                                "Verify Mobile Number"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * Checks if the Android device supports Biometric / Fingerprint authentication.
 */
fun isDeviceBiometricAvailable(context: Context): Boolean {
    return try {
        val biometricManager = BiometricManager.from(context)
        val result = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.BIOMETRIC_STRONG
        )
        result == BiometricManager.BIOMETRIC_SUCCESS
    } catch (_: Exception) {
        false
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/**
 * Launches the native Android BiometricPrompt (Fingerprint / Face Unlock).
 */
fun launchBiometricPrompt(
    context: Context,
    title: String = "TrimTime Quick Access",
    subtitle: String = "Use your Fingerprint or Biometrics to unlock",
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val activity = (context as? FragmentActivity) ?: context.findFragmentActivity()
    if (activity == null) {
        onError("Use your 4-digit MPIN below to unlock.")
        return
    }
    try {
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use 4-Digit MPIN")
            .build()
        prompt.authenticate(promptInfo)
    } catch (e: Exception) {
        onError("Biometric sensor unavailable. Please enter your 4-digit MPIN.")
    }
}

/**
 * Post-Login / Sign-Up "Set Up Quick Access" Dialog:
 * Lets the user set a 4-Digit MPIN and/or enable Fingerprint/Biometrics so future app launches
 * only ask for their Fingerprint or 4-Digit MPIN instead of the full password.
 */
@Composable
fun QuickAccessSetupDialog(
    userName: String,
    initialMpin: String = "",
    initialBiometricEnabled: Boolean = true,
    onSaveQuickAccess: (mpin: String, enableBiometrics: Boolean) -> Unit,
    onSkipForNow: () -> Unit
) {
    val context = LocalContext.current
    val canUseBiometrics = remember { isDeviceBiometricAvailable(context) }
    var mpinInput by remember { mutableStateOf(initialMpin.take(4)) }
    var biometricChecked by remember { mutableStateOf(initialBiometricEnabled) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    Dialog(
        onDismissRequest = onSkipForNow,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .testTag("quick_access_setup_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
            border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.65f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(AmberPrimary.copy(alpha = 0.16f))
                        .border(1.5.dp, AmberPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Quick Access",
                        tint = AmberPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "Enable Quick Access",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Welcome${if (userName.isNotBlank()) ", ${userName.substringBefore(" ")}" else ""}! Set a 4-Digit MPIN or enable Fingerprint so you can unlock TrimTime in 1 second on future launches without typing your password.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                Text(
                    text = "CREATE 4-DIGIT MPIN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AmberPrimary,
                    letterSpacing = 0.8.sp
                )

                // 4-Box MPIN Input
                BasicTextField(
                    value = mpinInput,
                    onValueChange = { raw ->
                        mpinInput = raw.filter { it.isDigit() }.take(4)
                        errorMessage = null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    cursorBrush = SolidColor(AmberPrimary),
                    textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .testTag("quick_access_mpin_setup_input"),
                    decorationBox = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until 4) {
                                val charStr = mpinInput.getOrNull(i)?.toString() ?: ""
                                val isFocused = mpinInput.length == i || (mpinInput.length == 4 && i == 3)
                                Box(
                                    modifier = Modifier
                                        .size(width = 56.dp, height = 60.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (charStr.isNotEmpty()) Color(0xFF172554) else DarkSurface)
                                        .border(
                                            width = if (isFocused) 2.dp else 1.dp,
                                            color = when {
                                                errorMessage != null -> RedAlert
                                                charStr.isNotEmpty() -> AmberPrimary
                                                isFocused -> AmberPrimary
                                                else -> Color(0xFF334155)
                                            },
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            try {
                                                focusRequester.requestFocus()
                                            } catch (_: Exception) {
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (charStr.isNotEmpty()) "●" else "•",
                                        fontSize = if (charStr.isNotEmpty()) 20.sp else 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (charStr.isNotEmpty()) AmberPrimary else Color(0xFF475569)
                                    )
                                }
                            }
                        }
                    }
                )

                // Biometric / Fingerprint Toggle Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Fingerprint Unlock",
                                tint = GreenSuccess,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Fingerprint / Biometric Unlock",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (canUseBiometrics) {
                                        "Unlock instantly with device biometrics"
                                    } else {
                                        "Biometric prompt + 4-digit MPIN fallback"
                                    },
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                        Switch(
                            checked = biometricChecked,
                            onCheckedChange = { biometricChecked = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = GreenSuccess
                            ),
                            modifier = Modifier.testTag("quick_access_biometric_switch")
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage ?: "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RedAlert,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        if (mpinInput.length != 4) {
                            errorMessage = "Please enter a 4-digit MPIN to enable Quick Access."
                            return@Button
                        }
                        onSaveQuickAccess(mpinInput, biometricChecked)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_quick_access_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPrimary,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "Save MPIN & Enable Quick Access",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                TextButton(
                    onClick = onSkipForNow,
                    modifier = Modifier.testTag("skip_quick_access_button")
                ) {
                    Text(
                        text = "Skip for now",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

/**
 * Full-Screen "Quick Access" Unlock Screen shown on future app launches when a user has
 * enabled a 4-Digit MPIN or Fingerprint/Biometrics.
 */
@Composable
fun QuickAccessUnlockScreen(
    userName: String,
    userPhone: String,
    isPhoneVerified: Boolean,
    expectedMpin: String,
    biometricEnabled: Boolean,
    onUnlocked: () -> Unit,
    onUsePasswordOrSwitchAccount: () -> Unit
) {
    val context = LocalContext.current
    var enteredMpin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (biometricEnabled) {
            launchBiometricPrompt(
                context = context,
                title = "Welcome back, ${userName.substringBefore(" ").ifBlank { "TrimTime User" }}",
                subtitle = "Touch the fingerprint sensor or enter your 4-digit MPIN",
                onSuccess = onUnlocked,
                onError = { /* User can enter 4-digit MPIN below */ }
            )
        }
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0F1D), DarkBackground, Color(0xFF0B1325))
                )
            )
            .padding(horizontal = 24.dp, vertical = 36.dp)
            .testTag("quick_access_unlock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(AmberPrimary.copy(alpha = 0.16f))
                        .border(1.5.dp, AmberPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Quick Access",
                        tint = AmberPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Welcome back, ${userName.ifBlank { "Member" }}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isPhoneVerified) {
                            VerifiedBlueTickIcon(size = 18.dp)
                        }
                    }
                    if (userPhone.isNotBlank()) {
                        Text(
                            text = "+91 $userPhone",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Text(
                    text = "Enter your 4-Digit Quick Access MPIN",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberPrimary
                )

                // 4-Box MPIN Entry with instant auto-unlock on 4th digit
                BasicTextField(
                    value = enteredMpin,
                    onValueChange = { raw ->
                        val clean = raw.filter { it.isDigit() }.take(4)
                        enteredMpin = clean
                        errorMessage = null
                        if (clean.length == 4) {
                            if (expectedMpin.isBlank() || clean == expectedMpin) {
                                onUnlocked()
                            } else {
                                errorMessage = "Incorrect 4-Digit MPIN. Please try again."
                                enteredMpin = ""
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    cursorBrush = SolidColor(AmberPrimary),
                    textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .testTag("quick_access_mpin_unlock_input"),
                    decorationBox = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until 4) {
                                val charStr = enteredMpin.getOrNull(i)?.toString() ?: ""
                                val isFocused = enteredMpin.length == i
                                Box(
                                    modifier = Modifier
                                        .size(width = 58.dp, height = 62.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (charStr.isNotEmpty()) Color(0xFF172554) else DarkSurface)
                                        .border(
                                            width = if (isFocused) 2.dp else 1.dp,
                                            color = when {
                                                errorMessage != null -> RedAlert
                                                charStr.isNotEmpty() -> GreenSuccess
                                                isFocused -> AmberPrimary
                                                else -> Color(0xFF334155)
                                            },
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            try {
                                                focusRequester.requestFocus()
                                            } catch (_: Exception) {
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (charStr.isNotEmpty()) "●" else "•",
                                        fontSize = if (charStr.isNotEmpty()) 22.sp else 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (charStr.isNotEmpty()) Color.White else Color(0xFF475569)
                                    )
                                }
                            }
                        }
                    }
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage ?: "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RedAlert,
                        textAlign = TextAlign.Center
                    )
                }

                // Fingerprint Button
                if (biometricEnabled) {
                    OutlinedButton(
                        onClick = {
                            launchBiometricPrompt(
                                context = context,
                                title = "TrimTime Biometric Unlock",
                                subtitle = "Authenticate with Fingerprint or Face Unlock",
                                onSuccess = onUnlocked,
                                onError = { msg -> errorMessage = msg }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("quick_access_fingerprint_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenSuccess)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Unlock with Fingerprint",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Unlock with Fingerprint / Biometrics",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                TextButton(
                    onClick = onUsePasswordOrSwitchAccount,
                    modifier = Modifier.testTag("quick_access_switch_account_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Use Password or Switch Account",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

