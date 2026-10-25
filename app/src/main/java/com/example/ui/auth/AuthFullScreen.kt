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

/**
 * Physical-Device Compatible Authentication Screen with separated "Login" and "Sign Up" tabs:
 * 1. Top Mode Switcher: "Login (Existing User)" vs "Sign Up (New Account)".
 * 2. Simple "Login" Tab: Asks ONLY for "Mobile Number OR Email Address", looks up the account in
 *    Firestore `users` collection, and logs the user in with their saved Name, Role (Customer/Partner),
 *    and profile data. If not found, shows "Account not found. Switching to Sign Up..." and switches
 *    to the Sign Up tab with the phone/email pre-filled.
 * 3. Full "Sign Up" Tab: Shows Role Selector ("Customer" / "Partner / Salon"), "Full Name",
 *    "Email Address", and "10-Digit Mobile Number" with a "Create Account" button.
 */
@Composable
fun AuthFullScreen(
    isLoading: Boolean,
    errorMessage: String?,
    showSha1FallbackDialog: Boolean = false,
    onDismissSha1FallbackDialog: () -> Unit = {},
    onLoginExistingUser: (
        identifier: String,
        onAccountNotFound: (prefillEmail: String, prefillPhone: String) -> Unit
    ) -> Unit = { _, _ -> },
    onSignInWithRealIdentity: (name: String, email: String, phone: String, asPartner: Boolean) -> Unit = { _, _, _, _ -> },
    onContinueWithGoogle: () -> Unit,
    onDeveloperTestLogin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Tab state: true = "Login (Existing User)", false = "Sign Up (New Account)"
    var isLoginTab by remember { mutableStateOf(true) }

    // Simple Login Tab state
    var loginIdentifier by remember { mutableStateOf("") }
    var infoBannerMessage by remember { mutableStateOf<String?>(null) }

    // Full Sign Up Tab states
    var fullName by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf("") }
    var phoneDigits by remember { mutableStateOf("") }
    var loginAsPartner by remember { mutableStateOf(false) }

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
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(18.dp))

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

                    // Inline validation message inside the Auth Card (only for form validation)
                    AnimatedVisibility(
                        visible = !errorMessage.isNullOrBlank(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        errorMessage?.let { errorText ->
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
                        // 2. Simple "Login" Tab (For Existing Users)
                        // ====================================================
                        Text(
                            text = "Welcome Back!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Enter your registered 10-Digit Mobile Number OR Email Address. We'll restore your saved Name, Role (Customer/Partner), and bookings automatically.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 15.sp
                        )

                        OutlinedTextField(
                            value = loginIdentifier,
                            onValueChange = {
                                loginIdentifier = it
                                infoBannerMessage = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_identifier_input"),
                            label = { Text("Mobile Number OR Email Address", fontSize = 12.sp) },
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
                                imeAction = ImeAction.Done
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

                        Button(
                            onClick = {
                                infoBannerMessage = null
                                onLoginExistingUser(loginIdentifier) { prefillEmail, prefillPhone ->
                                    // Account not found -> prefill Sign Up fields and switch tab automatically
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
                            enabled = !isLoading,
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
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Checking Firestore Account...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            } else {
                                Text(
                                    text = "Login",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    } else {
                        // ====================================================
                        // 3. Full "Sign Up" Tab (For New Users)
                        // ====================================================
                        Text(
                            text = "Create New Account",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Choose your account type and fill in your details once to register in Cloud Firestore.",
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

                        // Full Name Input
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_name_input"),
                            label = { Text("Full Name", fontSize = 12.sp) },
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

                        // Email Address Input
                        OutlinedTextField(
                            value = emailAddress,
                            onValueChange = { emailAddress = it },
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

                        // 10-Digit Phone Input
                        OutlinedTextField(
                            value = phoneDigits,
                            onValueChange = { input ->
                                phoneDigits = input.filter { it.isDigit() }.take(10)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_phone_input"),
                            label = { Text("10-Digit Mobile Number", fontSize = 12.sp) },
                            placeholder = { Text("9876543210", fontSize = 13.sp, color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Phone",
                                    tint = GreenSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
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

                        Button(
                            onClick = {
                                infoBannerMessage = null
                                onSignInWithRealIdentity(fullName, emailAddress, phoneDigits, loginAsPartner)
                            },
                            enabled = !isLoading,
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
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Creating Account...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            } else {
                                Text(
                                    text = "Create Account",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Helpful Dialog when Google Sign-In fails on physical APK due to SHA-1 fingerprint mismatch
    if (showSha1FallbackDialog) {
        Sha1RealAccountLoginDialog(
            initialName = fullName,
            initialEmail = emailAddress,
            initialPhone = phoneDigits,
            isLoading = isLoading,
            onDismiss = onDismissSha1FallbackDialog,
            onLoginExisting = { identifier ->
                onLoginExistingUser(identifier) { prefillEmail, prefillPhone ->
                    if (prefillEmail.isNotBlank()) emailAddress = prefillEmail
                    if (prefillPhone.isNotBlank()) phoneDigits = prefillPhone
                    infoBannerMessage = "Account not found. Switching to Sign Up..."
                    isLoginTab = false
                    onDismissSha1FallbackDialog()
                }
            },
            onConfirmSignIn = { name, email, phone, isPartner ->
                onSignInWithRealIdentity(name, email, phone, isPartner)
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
    onLoginExisting: (identifier: String) -> Unit = {},
    onConfirmSignIn: (name: String, email: String, phone: String, asPartner: Boolean) -> Unit
) {
    var dialogIsLoginTab by remember { mutableStateOf(true) }
    var dialogIdentifier by remember { mutableStateOf(initialEmail.ifBlank { initialPhone }) }
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var phone by remember { mutableStateOf(initialPhone) }
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
                            dialogIdentifier = it
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
                            } else {
                                onLoginExisting(dialogIdentifier.trim())
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
                            text = "Login",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            localError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Your Full Name", fontSize = 12.sp) },
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
                            email = it
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
                            phone = input.filter { it.isDigit() }.take(10)
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
                            if (name.trim().length < 2) {
                                localError = "Please enter your full name."
                            } else if (!email.contains("@") || !email.contains(".")) {
                                localError = "Please enter a valid email address."
                            } else {
                                onConfirmSignIn(name.trim(), email.trim(), phone.trim(), asPartner)
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
                            text = "Create Account",
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
