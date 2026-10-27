package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedAlert
import com.example.util.SecurityValidator

import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.components.TopSmsOtpBanner
import com.example.ui.components.VerifiedNumberBadge
import com.example.ui.components.VerifyMobileOtpDialog
import com.example.ui.components.triggerAndroidSmsNotification

/**
 * Upgraded Authentication & Phone Number Verification Screen:
 * 1. Top Mode Switcher: "Login" vs "Sign Up".
 * 2. "Login" Tab: Requires BOTH:
 *    - "Mobile Number OR Email Address"
 *    - "4-Digit Login PIN" (with a "Forgot PIN? Verify via OTP" option that sends the in-app OTP banner to reset their PIN).
 * 3. "Sign Up" Tab: Requires Role Selector ("Customer" / "Partner / Salon"), "Full Name", "Email Address",
 *    "10-Digit Mobile Number", and "4-Digit Secret Login PIN / Password".
 *    Clicking "Send Verification OTP" validates the Indian mobile number (^[6-9][0-9]{9}$), checks Firestore
 *    `users` uniqueness, triggers the realistic Top SMS-style Banner & Android Notification:
 *    "💬 TrimTime Verification OTP for +91 XXXXXXXXXX is: 4829", and opens the 4-box OTP verification dialog
 *    with a 30-second "Resend OTP" timer before creating the verified account (`isPhoneVerified = true`).
 */
@Composable
fun AuthFullScreen(
    isLoading: Boolean,
    errorMessage: String?,
    showSha1FallbackDialog: Boolean = false,
    onDismissSha1FallbackDialog: () -> Unit = {},
    onLoginExistingUser: (
        identifier: String,
        loginPin: String,
        onAccountNotFound: (prefillEmail: String, prefillPhone: String) -> Unit
    ) -> Unit = { _, _, _ -> },
    onSignInWithRealIdentity: (
        name: String,
        email: String,
        phone: String,
        asPartner: Boolean,
        loginPin: String,
        isPhoneVerified: Boolean
    ) -> Unit = { _, _, _, _, _, _ -> },
    onRequestPhoneOtp: (
        rawPhone: String,
        onOtpGenerated: (cleanTenDigits: String, otpCode: String) -> Unit,
        onError: (String) -> Unit
    ) -> Unit = { _, _, _ -> },
    onRequestForgotPinOtp: (
        rawIdentifier: String,
        onOtpGenerated: (cleanTenDigits: String, otpCode: String, userId: String) -> Unit,
        onError: (String) -> Unit
    ) -> Unit = { _, _, _ -> },
    onResetLoginPin: (
        rawIdentifier: String,
        newPin: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit = { _, _, _, _ -> },
    onGenerateNewOtpCode: () -> String = { (1000..9999).random().toString() },
    onContinueWithGoogle: () -> Unit,
    onDeveloperTestLogin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Tab state: true = "Login (Existing User)", false = "Sign Up (New Account)"
    var isLoginTab by remember { mutableStateOf(true) }

    // Login Tab state (Requires BOTH Identifier AND 4-Digit Login PIN)
    var loginIdentifier by remember { mutableStateOf("") }
    var loginPin by remember { mutableStateOf("") }
    var showLoginPinDigits by remember { mutableStateOf(false) }
    var infoBannerMessage by remember { mutableStateOf<String?>(null) }
    var localValidationError by remember { mutableStateOf<String?>(null) }

    // Sign Up Tab states
    var fullName by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf("") }
    var phoneDigits by remember { mutableStateOf("") }
    var signUpPin by remember { mutableStateOf("") }
    var showSignUpPinDigits by remember { mutableStateOf(false) }
    var loginAsPartner by remember { mutableStateOf(false) }

    // 2-Step OTP Verification & Top SMS Banner states
    var showTopSmsBanner by remember { mutableStateOf(false) }
    var activeOtpPhone by remember { mutableStateOf("") }
    var activeOtpCode by remember { mutableStateOf("") }
    var showSignUpOtpModal by remember { mutableStateOf(false) }
    var showForgotPinOtpModal by remember { mutableStateOf(false) }
    var isCheckingOtpRequest by remember { mutableStateOf(false) }

    val activeError = localValidationError ?: errorMessage

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
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top SMS-Style OTP Banner (Spark-Plan Friendly In-App SMS Notification)
            TopSmsOtpBanner(
                visible = showTopSmsBanner && activeOtpCode.length == 4,
                phoneTenDigits = activeOtpPhone,
                otpCode = activeOtpCode,
                onDismiss = { showTopSmsBanner = false }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Brand Header with generous top clearance so the Scissors circle is never cut off
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(elevation = 14.dp, shape = CircleShape, spotColor = AmberPrimary.copy(alpha = 0.4f))
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2E2210),
                                Color(0xFF1E1B15),
                                Color(0xFF12141A)
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(AmberPrimary, AmberSecondary, Color(0xFF78350F))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "Salon Scissors",
                        tint = AmberPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Instant Slots Clock",
                        tint = AmberSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "TrimTime",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                ),
                color = Color.White
            )

            Text(
                text = "Instant Salon & Barber Appointments",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Auth Card with Top Mode Switcher ("Login" vs "Sign Up" Tabs)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF334155))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Top Mode Switcher ("Login" vs "Sign Up")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .padding(4.dp)
                            .testTag("auth_mode_tabs_row"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isLoginTab) AmberPrimary else Color.Transparent)
                                .clickable {
                                    isLoginTab = true
                                    infoBannerMessage = null
                                    localValidationError = null
                                }
                                .padding(horizontal = 8.dp)
                                .testTag("tab_login_existing_user"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Login,
                                    contentDescription = null,
                                    tint = if (isLoginTab) Color.Black else Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Login",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isLoginTab) Color.Black else Color(0xFFCBD5E1),
                                    maxLines = 1
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (!isLoginTab) AmberPrimary else Color.Transparent)
                                .clickable {
                                    isLoginTab = false
                                    localValidationError = null
                                }
                                .padding(horizontal = 8.dp)
                                .testTag("tab_signup_new_account"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = if (!isLoginTab) Color.Black else Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Sign Up",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (!isLoginTab) Color.Black else Color(0xFFCBD5E1),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Helpful Notice Banner when switching from Login -> Sign Up automatically
                    AnimatedVisibility(visible = !infoBannerMessage.isNullOrBlank()) {
                        infoBannerMessage?.let { bannerMsg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, AmberPrimary.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                                    .testTag("account_not_found_switch_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = bannerMsg,
                                    color = AmberPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // Inline validation message inside the Auth Card
                    AnimatedVisibility(
                        visible = !activeError.isNullOrBlank(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        activeError?.let { errorText ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2D1214))
                                    .border(1.dp, RedAlert.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Validation Info",
                                    tint = RedAlert,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = errorText,
                                    color = RedAlert,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    if (isLoginTab) {
                        // ====================================================
                        // 2. Protected "Login" Tab (Mobile/Email + 4-Digit Login PIN)
                        // ====================================================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Welcome Back!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(GreenSuccess.copy(alpha = 0.14f))
                                    .border(1.dp, GreenSuccess.copy(alpha = 0.4f), RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = GreenSuccess,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "PIN Protected",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenSuccess,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Text(
                            text = "Enter your registered Mobile Number OR Email Address and your 4-Digit Secret Login PIN to access your account.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 15.sp
                        )

                        // Input 1: Mobile Number OR Email Address
                        OutlinedTextField(
                            value = loginIdentifier,
                            onValueChange = {
                                loginIdentifier = SecurityValidator.sanitizeInput(it, maxLength = 80, trimEdges = false)
                                infoBannerMessage = null
                                localValidationError = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_identifier_input"),
                            label = { Text("1. Mobile Number OR Email Address", fontSize = 12.sp) },
                            placeholder = {
                                Text(
                                    "e.g. 9876543210 or yourname@gmail.com",
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (loginIdentifier.contains("@")) Icons.Default.Email else Icons.Default.Phone,
                                    contentDescription = "Mobile or Email",
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
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

                        // Input 2: Mandatory 4-Digit Login PIN
                        OutlinedTextField(
                            value = loginPin,
                            onValueChange = { input ->
                                loginPin = input.filter { it.isDigit() }.take(4)
                                localValidationError = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_pin_input"),
                            label = { Text("2. 4-Digit Login PIN", fontSize = 12.sp) },
                            placeholder = {
                                Text(
                                    "Enter 4-digit secret PIN (e.g. 1234)",
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "4-Digit Login PIN",
                                    tint = if (loginPin.length == 4) GreenSuccess else AmberPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "${loginPin.length}/4",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (loginPin.length == 4) GreenSuccess else Color(0xFF94A3B8)
                                    )
                                    IconButton(
                                        onClick = { showLoginPinDigits = !showLoginPinDigits },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (showLoginPinDigits) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle PIN Visibility",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            },
                            visualTransformation = if (showLoginPinDigits) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (loginPin.length == 4) GreenSuccess else AmberPrimary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = AmberPrimary,
                                unfocusedLabelColor = Color(0xFF94A3B8)
                            )
                        )

                        // "Forgot PIN? Verify via OTP" row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    localValidationError = null
                                    if (loginIdentifier.trim().isBlank()) {
                                        localValidationError = "Enter your registered Mobile Number or Email above first, then tap 'Forgot PIN? Verify via OTP'."
                                    } else {
                                        isCheckingOtpRequest = true
                                        onRequestForgotPinOtp(
                                            loginIdentifier.trim(),
                                            { cleanPhone, otpCode, _ ->
                                                isCheckingOtpRequest = false
                                                activeOtpPhone = cleanPhone
                                                activeOtpCode = otpCode
                                                showTopSmsBanner = true
                                                triggerAndroidSmsNotification(context, cleanPhone, otpCode)
                                                showForgotPinOtpModal = true
                                            },
                                            { err ->
                                                isCheckingOtpRequest = false
                                                localValidationError = err
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("forgot_pin_otp_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sms,
                                    contentDescription = null,
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Forgot PIN? Verify via OTP",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberPrimary
                                )
                            }
                        }

                        Button(
                            onClick = {
                                infoBannerMessage = null
                                localValidationError = null
                                val cleanId = loginIdentifier.trim()
                                val cleanPin = loginPin.filter { it.isDigit() }
                                if (cleanId.isBlank()) {
                                    localValidationError = "Please enter your registered 10-Digit Mobile Number or Email Address."
                                    return@Button
                                }
                                if (cleanPin.length != 4) {
                                    localValidationError = "Please enter your 4-Digit Secret Login PIN (or tap 'Forgot PIN? Verify via OTP' to reset it)."
                                    return@Button
                                }
                                onLoginExistingUser(cleanId, cleanPin) { prefillEmail, prefillPhone ->
                                    if (prefillEmail.isNotBlank()) {
                                        emailAddress = prefillEmail
                                    }
                                    if (prefillPhone.isNotBlank()) {
                                        phoneDigits = prefillPhone
                                    }
                                    infoBannerMessage = "Account not found. Switching to Sign Up..."
                                    isLoginTab = false
                                }
                            },
                            enabled = !isLoading && !isCheckingOtpRequest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("login_existing_user_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberPrimary,
                                contentColor = Color.Black
                            )
                        ) {
                            if (isLoading || isCheckingOtpRequest) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Verifying Account & PIN...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Verify PIN & Login",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    } else {
                        // ====================================================
                        // 3. Full "Sign Up" Tab (With 2-Step Phone OTP + 4-Digit Login PIN)
                        // ====================================================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Create Verified Account",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            VerifiedNumberBadge(text = "2-Step OTP")
                        }
                        Text(
                            text = "Fill in your details, set a 4-Digit Login PIN, and verify your mobile number via instant 4-digit OTP.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 15.sp
                        )

                        // Role Toggle: Customer vs Partner / Salon Owner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!loginAsPartner) AmberPrimary else Color.Transparent)
                                    .clickable { loginAsPartner = false }
                                    .padding(vertical = 8.dp)
                                    .testTag("role_select_customer"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (!loginAsPartner) Color.Black else Color(0xFF94A3B8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Customer",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!loginAsPartner) Color.Black else Color(0xFF94A3B8)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (loginAsPartner) Color(0xFF2563EB) else Color.Transparent)
                                    .clickable { loginAsPartner = true }
                                    .padding(vertical = 8.dp)
                                    .testTag("role_select_partner"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = if (loginAsPartner) Color.White else Color(0xFF94A3B8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Partner / Salon",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (loginAsPartner) Color.White else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }

                        // Full Name Input (Sanitized, max 60 chars)
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = {
                                fullName = SecurityValidator.sanitizeName(it, trimEdges = false)
                                localValidationError = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_name_input"),
                            label = { Text("Full Name (Max 60 chars)", fontSize = 12.sp) },
                            placeholder = { Text("e.g. Pankaj Kumar", fontSize = 13.sp, color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Name",
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
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

                        // Email Address Input (Sanitized)
                        OutlinedTextField(
                            value = emailAddress,
                            onValueChange = {
                                emailAddress = SecurityValidator.sanitizeInput(it, maxLength = 80, trimEdges = false)
                                localValidationError = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input"),
                            label = { Text("Email Address", fontSize = 12.sp) },
                            placeholder = { Text("yourname@gmail.com", fontSize = 13.sp, color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Email",
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
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

                        // 10-Digit Phone Input (Strict Indian mobile regex ^[6-9][0-9]{9}$)
                        OutlinedTextField(
                            value = phoneDigits,
                            onValueChange = { input ->
                                phoneDigits = SecurityValidator.extractTenDigitMobile(input).take(10)
                                localValidationError = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_phone_input"),
                            label = { Text("10-Digit Mobile Number (Starts 6–9)", fontSize = 12.sp) },
                            placeholder = { Text("9876543210", fontSize = 13.sp, color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Phone",
                                    tint = GreenSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                Text(
                                    text = "${phoneDigits.length}/10",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (phoneDigits.length == 10) GreenSuccess else Color(0xFF94A3B8),
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
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

                        val phoneWarning = if (phoneDigits.length == 10) {
                            SecurityValidator.validateIndianMobileError(phoneDigits)
                        } else null
                        if (phoneWarning != null) {
                            Text(
                                text = phoneWarning,
                                fontSize = 11.sp,
                                color = RedAlert,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Mandatory 4-Digit Secret Login PIN / Password Input
                        OutlinedTextField(
                            value = signUpPin,
                            onValueChange = { input ->
                                signUpPin = input.filter { it.isDigit() }.take(4)
                                localValidationError = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_signup_pin_input"),
                            label = { Text("4-Digit Secret Login PIN / Password", fontSize = 12.sp) },
                            placeholder = { Text("Set a 4-digit PIN (e.g. 2580)", fontSize = 13.sp, color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Secret Login PIN",
                                    tint = if (signUpPin.length == 4) GreenSuccess else AmberPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "${signUpPin.length}/4",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (signUpPin.length == 4) GreenSuccess else Color(0xFF94A3B8)
                                    )
                                    IconButton(
                                        onClick = { showSignUpPinDigits = !showSignUpPinDigits },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (showSignUpPinDigits) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle PIN Visibility",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            },
                            visualTransformation = if (showSignUpPinDigits) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (signUpPin.length == 4) GreenSuccess else AmberPrimary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = AmberPrimary,
                                unfocusedLabelColor = Color(0xFF94A3B8)
                            )
                        )

                        Button(
                            onClick = {
                                infoBannerMessage = null
                                localValidationError = null

                                val cleanName = SecurityValidator.sanitizeName(fullName)
                                val cleanEmail = emailAddress.trim()
                                val phoneErr = SecurityValidator.validateIndianMobileError(phoneDigits)
                                val cleanPin = signUpPin.filter { it.isDigit() }

                                if (cleanName.length < 2) {
                                    localValidationError = "Please enter your full name (2–60 characters)."
                                    return@Button
                                }
                                if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
                                    localValidationError = "Please enter a valid email address."
                                    return@Button
                                }
                                if (phoneErr != null) {
                                    localValidationError = phoneErr
                                    return@Button
                                }
                                if (cleanPin.length != 4) {
                                    localValidationError = "Please set a 4-Digit Secret Login PIN (4 digits, e.g. 2580) to protect your account."
                                    return@Button
                                }

                                isCheckingOtpRequest = true
                                onRequestPhoneOtp(
                                    phoneDigits,
                                    { verifiedTenDigits, generatedOtp ->
                                        isCheckingOtpRequest = false
                                        activeOtpPhone = verifiedTenDigits
                                        activeOtpCode = generatedOtp
                                        showTopSmsBanner = true
                                        triggerAndroidSmsNotification(context, verifiedTenDigits, generatedOtp)
                                        showSignUpOtpModal = true
                                    },
                                    { err ->
                                        isCheckingOtpRequest = false
                                        localValidationError = err
                                    }
                                )
                            },
                            enabled = !isLoading && !isCheckingOtpRequest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("sign_in_email_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberPrimary,
                                contentColor = Color.Black
                            )
                        ) {
                            if (isLoading || isCheckingOtpRequest) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sending Verification OTP...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Send Verification OTP",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 2-Step Sign Up OTP Verification Modal
    if (showSignUpOtpModal && activeOtpCode.length == 4) {
        VerifyMobileOtpDialog(
            phoneTenDigits = activeOtpPhone,
            expectedOtp = activeOtpCode,
            title = "Verify Your Mobile Number",
            subtitle = "Enter the 4-digit OTP sent to +91 $activeOtpPhone to verify your number and create your account.",
            requireNewLoginPin = false,
            onResendOtp = {
                val refreshedOtp = onGenerateNewOtpCode()
                activeOtpCode = refreshedOtp
                showTopSmsBanner = true
                triggerAndroidSmsNotification(context, activeOtpPhone, refreshedOtp)
            },
            onVerified = { _ ->
                showSignUpOtpModal = false
                showTopSmsBanner = false
                onSignInWithRealIdentity(
                    fullName,
                    emailAddress,
                    activeOtpPhone,
                    loginAsPartner,
                    signUpPin.filter { it.isDigit() },
                    true
                )
            },
            onDismiss = {
                showSignUpOtpModal = false
            }
        )
    }

    // "Forgot PIN? Verify via OTP" Reset Modal
    if (showForgotPinOtpModal && activeOtpCode.length == 4) {
        VerifyMobileOtpDialog(
            phoneTenDigits = activeOtpPhone,
            expectedOtp = activeOtpCode,
            title = "Reset 4-Digit Login PIN via OTP",
            subtitle = "Enter the 4-digit OTP shown in the top SMS banner for +91 $activeOtpPhone and choose your new 4-Digit Secret Login PIN.",
            requireNewLoginPin = true,
            onResendOtp = {
                val refreshedOtp = onGenerateNewOtpCode()
                activeOtpCode = refreshedOtp
                showTopSmsBanner = true
                triggerAndroidSmsNotification(context, activeOtpPhone, refreshedOtp)
            },
            onVerified = { newLoginPin ->
                showForgotPinOtpModal = false
                showTopSmsBanner = false
                if (!newLoginPin.isNullOrBlank()) {
                    loginPin = newLoginPin
                    onResetLoginPin(
                        loginIdentifier.trim(),
                        newLoginPin,
                        {},
                        { err -> localValidationError = err }
                    )
                }
            },
            onDismiss = {
                showForgotPinOtpModal = false
            }
        )
    }

    // Helpful Dialog when Google Sign-In fails on physical APK due to SHA-1 fingerprint mismatch
    if (showSha1FallbackDialog) {
        Sha1RealAccountLoginDialog(
            initialName = fullName,
            initialEmail = emailAddress,
            initialPhone = phoneDigits,
            isLoading = isLoading,
            onDismiss = onDismissSha1FallbackDialog,
            onLoginExisting = { identifier, pin ->
                onLoginExistingUser(identifier, pin) { prefillEmail, prefillPhone ->
                    if (prefillEmail.isNotBlank()) emailAddress = prefillEmail
                    if (prefillPhone.isNotBlank()) phoneDigits = prefillPhone
                    infoBannerMessage = "Account not found. Switching to Sign Up..."
                    isLoginTab = false
                    onDismissSha1FallbackDialog()
                }
            },
            onConfirmSignIn = { name, email, phone, isPartner, pin ->
                onDismissSha1FallbackDialog()
                onRequestPhoneOtp(
                    phone,
                    { cleanPhone, otp ->
                        fullName = name
                        emailAddress = email
                        phoneDigits = cleanPhone
                        loginAsPartner = isPartner
                        signUpPin = pin
                        activeOtpPhone = cleanPhone
                        activeOtpCode = otp
                        showTopSmsBanner = true
                        triggerAndroidSmsNotification(context, cleanPhone, otp)
                        showSignUpOtpModal = true
                    },
                    { err ->
                        localValidationError = err
                    }
                )
            }
        )
    }
}

@Composable
fun Sha1RealAccountLoginDialog(
    initialName: String = "",
    initialEmail: String = "",
    initialPhone: String = "",
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onLoginExisting: (identifier: String, loginPin: String) -> Unit = { _, _ -> },
    onConfirmSignIn: (name: String, email: String, phone: String, asPartner: Boolean, loginPin: String) -> Unit
) {
    var dialogIsLoginTab by remember { mutableStateOf(true) }
    var dialogIdentifier by remember { mutableStateOf(initialEmail.ifBlank { initialPhone }) }
    var dialogLoginPin by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var phone by remember { mutableStateOf(initialPhone) }
    var newPin by remember { mutableStateOf("") }
    var asPartner by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sha1_fallback_login_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(AmberPrimary.copy(alpha = 0.6f))
            )
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Direct Cloud Account Access",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Dialog Login vs Sign Up Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (dialogIsLoginTab) AmberPrimary else Color.Transparent)
                            .clickable {
                                dialogIsLoginTab = true
                                localError = null
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Login (Existing)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (dialogIsLoginTab) Color.Black else Color(0xFFCBD5E1)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!dialogIsLoginTab) AmberPrimary else Color.Transparent)
                            .clickable {
                                dialogIsLoginTab = false
                                localError = null
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign Up (New)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!dialogIsLoginTab) Color.Black else Color(0xFFCBD5E1)
                        )
                    }
                }

                if (dialogIsLoginTab) {
                    OutlinedTextField(
                        value = dialogIdentifier,
                        onValueChange = {
                            dialogIdentifier = SecurityValidator.sanitizeInput(it, maxLength = 80, trimEdges = false)
                            localError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Mobile Number OR Email Address", fontSize = 12.sp) },
                        placeholder = { Text("9876543210 or yourname@gmail.com", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    OutlinedTextField(
                        value = dialogLoginPin,
                        onValueChange = {
                            dialogLoginPin = it.filter { ch -> ch.isDigit() }.take(4)
                            localError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("4-Digit Login PIN", fontSize = 12.sp) },
                        placeholder = { Text("Enter 4-digit PIN", fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    localError?.let { err ->
                        Text(
                            text = err,
                            fontSize = 11.sp,
                            color = RedAlert,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            if (dialogIdentifier.trim().isBlank()) {
                                localError = "Please enter your Mobile Number or Email."
                            } else if (dialogLoginPin.length != 4) {
                                localError = "Please enter your 4-Digit Login PIN."
                            } else {
                                onLoginExisting(dialogIdentifier.trim(), dialogLoginPin)
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = "Verify PIN & Login",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = SecurityValidator.sanitizeName(it, trimEdges = false)
                            localError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Your Full Name (Max 60 chars)", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Pankaj Kumar", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = SecurityValidator.sanitizeInput(it, maxLength = 80, trimEdges = false)
                            localError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Your Email Address", fontSize = 12.sp) },
                        placeholder = { Text("yourname@gmail.com", fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { input ->
                            phone = SecurityValidator.extractTenDigitMobile(input).take(10)
                            localError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("10-Digit Mobile Number", fontSize = 12.sp) },
                        placeholder = { Text("9876543210", fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { input ->
                            newPin = input.filter { it.isDigit() }.take(4)
                            localError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("4-Digit Secret Login PIN", fontSize = 12.sp) },
                        placeholder = { Text("e.g. 2580", fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Role toggle inside dialog
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { asPartner = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (!asPartner) Color(0xFF2E2210) else Color.Transparent,
                                contentColor = if (!asPartner) AmberPrimary else Color(0xFF94A3B8)
                            )
                        ) {
                            Text("Customer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { asPartner = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (asPartner) Color(0xFF1E3A8A) else Color.Transparent,
                                contentColor = if (asPartner) Color.White else Color(0xFF94A3B8)
                            )
                        ) {
                            Text("Salon Partner", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    localError?.let { err ->
                        Text(
                            text = err,
                            fontSize = 11.sp,
                            color = RedAlert,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            val cleanName = SecurityValidator.sanitizeName(name)
                            val phoneError = SecurityValidator.validateIndianMobileError(phone)
                            if (cleanName.length < 2) {
                                localError = "Please enter your full name (2–60 characters)."
                            } else if (!email.contains("@") || !email.contains(".")) {
                                localError = "Please enter a valid email address."
                            } else if (phoneError != null) {
                                localError = phoneError
                            } else if (newPin.length != 4) {
                                localError = "Please set a 4-Digit Secret Login PIN."
                            } else {
                                onConfirmSignIn(cleanName, email.trim(), phone.trim(), asPartner, newPin)
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = "Send Verification OTP",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom Canvas-rendered Google "G" 4-color logo.
 */
@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val strokeWidth = width * 0.22f
        val radius = (width - strokeWidth) / 2f
        val center = Offset(width / 2f, height / 2f)

        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth)
        )
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth)
        )
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth)
        )
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth)
        )
        val barPath = Path().apply {
            moveTo(center.x - 1f, center.y)
            lineTo(width - strokeWidth / 4f, center.y)
        }
        drawPath(
            path = barPath,
            color = Color(0xFF4285F4),
            style = Stroke(width = strokeWidth)
        )
    }
}
