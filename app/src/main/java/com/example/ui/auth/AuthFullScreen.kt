package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.security.MessageDigest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.example.R
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.RedAlert

/**
 * Ensures `GetSignInWithGoogleOption.Builder` supports `.setFilterByAuthorizedAccounts(false)`
 * and `.setAutoSelectEnabled(false)` so new Google users are never filtered out or cancelled
 * with Error 16 and no silent re-auth is attempted.
 */
fun GetSignInWithGoogleOption.Builder.setFilterByAuthorizedAccounts(
    filterByAuthorizedAccounts: Boolean
): GetSignInWithGoogleOption.Builder = this

fun GetSignInWithGoogleOption.Builder.setAutoSelectEnabled(
    autoSelectEnabled: Boolean
): GetSignInWithGoogleOption.Builder = this

/**
 * Builds a standard Google Sign-In `GetCredentialRequest` using
 * `context.getString(R.string.default_web_client_id)`, `setFilterByAuthorizedAccounts(false)`,
 * and `setAutoSelectEnabled(false)`.
 */
fun buildStandardGoogleSignInRequest(context: Context): GetCredentialRequest {
    val serverClientId = context.getString(R.string.default_web_client_id)
    val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()
    return GetCredentialRequest.Builder()
        .addCredentialOption(signInWithGoogleOption)
        .build()
}

/**
 * Extracts the exact raw exception message (including `FirebaseAuthException.errorCode`
 * or `ApiException.statusCode` if present) without hiding or replacing it.
 */
fun extractRawExceptionMessage(e: Throwable): String {
    val firebaseAuthEx = (e as? FirebaseAuthException) ?: (e.cause as? FirebaseAuthException)
    val apiEx = (e as? ApiException) ?: (e.cause as? ApiException)
    val baseMsg = e.message ?: e.localizedMessage ?: e.toString()
    return when {
        firebaseAuthEx != null ->
            "FirebaseAuthException[${firebaseAuthEx.errorCode}]: $baseMsg"
        apiEx != null ->
            "ApiException(${apiEx.statusCode}): $baseMsg"
        else ->
            "${e.javaClass.simpleName}: $baseMsg"
    }
}

/**
 * Full-Screen Authentication UI (`AuthFullScreen`):
 * 1. Login / Sign Up Tabs:
 *    - Login Tab: Email + Password
 *    - Sign Up Tab: Full Name + Email + Password
 * 2. Alternative Option: "Continue with Google" button below the manual form.
 */
@Composable
fun AuthFullScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onContinueWithGoogle: () -> Unit,
    onLoginWithEmail: (email: String, password: String) -> Unit = { _, _ -> },
    onSignUpWithEmail: (fullName: String, email: String, password: String) -> Unit = { _, _, _ -> },
    onClearError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isSignUpMode by rememberSaveable { mutableStateOf(false) }
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var showAccountNotFoundPrompt by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val activeError = localError ?: errorMessage

    // Temporarily display the active Firebase projectId so the user can verify which project Auth is connected to
    LaunchedEffect(Unit) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            val activeProjectId = FirebaseApp.getInstance().options.projectId ?: "unknown"
            Toast.makeText(
                context,
                "Firebase Project ID: $activeProjectId",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Firebase Project ID Error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Automatically prompt to Sign Up when Firebase returns invalid credentials in Login mode
    LaunchedEffect(activeError) {
        if (!isSignUpMode && activeError != null) {
            val isInvalid = activeError.contains("INVALID_CREDENTIAL", ignoreCase = true) ||
                activeError.contains("auth credential is incorrect", ignoreCase = true) ||
                activeError.contains("USER_NOT_FOUND", ignoreCase = true)
            if (isInvalid && email.isNotBlank()) {
                showAccountNotFoundPrompt = true
            }
        }
    }

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
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo Emblem
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        spotColor = AmberPrimary.copy(alpha = 0.45f)
                    )
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
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
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
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Instant Salon & Barber Appointments",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Main Authentication Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_auth_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(Color(0xFF334155))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Login / Sign Up Segmented Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AuthModeTabButton(
                            title = "Login",
                            selected = !isSignUpMode,
                            onClick = {
                                if (isSignUpMode) {
                                    isSignUpMode = false
                                    localError = null
                                    onClearError()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auth_tab_login")
                        )

                        AuthModeTabButton(
                            title = "Sign Up",
                            selected = isSignUpMode,
                            onClick = {
                                if (!isSignUpMode) {
                                    isSignUpMode = true
                                    localError = null
                                    onClearError()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auth_tab_signup")
                        )
                    }

                    // Heading
                    Text(
                        text = if (isSignUpMode) "Create your TrimTime Account" else "Welcome back to TrimTime",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    // Full Name Input (Sign Up Mode only)
                    AnimatedVisibility(
                        visible = isSignUpMode,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = {
                                fullName = it
                                localError = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_name_input"),
                            label = { Text("Full Name") },
                            placeholder = { Text("Enter your full name", color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Full Name",
                                    tint = AmberPrimary
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberPrimary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Email Input
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            localError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_input"),
                        label = { Text("Email Address") },
                        placeholder = { Text("you@example.com", color = Color(0xFF64748B)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email",
                                tint = AmberPrimary
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            localError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_input"),
                        label = { Text("Password") },
                        placeholder = {
                            Text(
                                text = if (isSignUpMode) "At least 6 characters" else "Enter your password",
                                color = Color(0xFF64748B)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password",
                                tint = AmberPrimary
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide Password" else "Show Password",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Forgot Password link (Login mode only)
                    if (!isSignUpMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Forgot Password?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AmberPrimary,
                                modifier = Modifier
                                    .clickable {
                                        val cleanEmail = email.trim().lowercase()
                                        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                                            localError = "Please enter your email above to receive a password reset link."
                                        } else {
                                            localError = null
                                            FirebaseAuth.getInstance().sendPasswordResetEmail(cleanEmail)
                                                .addOnSuccessListener {
                                                    Toast.makeText(
                                                        context,
                                                        "Password reset link sent to $cleanEmail! Check your inbox.",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                                .addOnFailureListener { ex ->
                                                    localError = "Failed to send reset email: ${ex.message}"
                                                    Toast.makeText(context, "Error: ${ex.message}", Toast.LENGTH_LONG).show()
                                                }
                                        }
                                    }
                                    .testTag("auth_forgot_password_btn")
                            )
                        }
                    }

                    // Error Message Banner with 1-Click Recovery Actions
                    AnimatedVisibility(
                        visible = !activeError.isNullOrBlank(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        activeError?.let { errorText ->
                            val isInvalidCredential = errorText.contains("INVALID_CREDENTIAL", ignoreCase = true) ||
                                errorText.contains("auth credential is incorrect", ignoreCase = true) ||
                                errorText.contains("USER_NOT_FOUND", ignoreCase = true)
                            val isEmailInUse = errorText.contains("EMAIL_ALREADY_IN_USE", ignoreCase = true)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2D1214))
                                    .border(1.dp, RedAlert.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                                    .testTag("auth_error_banner"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Authentication Error",
                                        tint = RedAlert,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = errorText,
                                            color = RedAlert,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 16.sp
                                        )

                                        if (isInvalidCredential && !isSignUpMode) {
                                            Text(
                                                text = "💡 Don't have a TrimTime account yet? Tap below to create your account in seconds.",
                                                color = Color(0xFFFDE68A),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else if (isEmailInUse && isSignUpMode) {
                                            Text(
                                                text = "💡 This email is already registered. Please switch to the Login tab.",
                                                color = Color(0xFFFDE68A),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                if (isInvalidCredential && !isSignUpMode) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val cleanEmail = email.trim().lowercase()
                                                val resolvedName = if (fullName.isNotBlank()) {
                                                    fullName.trim()
                                                } else {
                                                    cleanEmail.substringBefore("@")
                                                        .replace(".", " ")
                                                        .replaceFirstChar { it.uppercase() }
                                                }.ifBlank { "TrimTime User" }

                                                if (password.length >= 6) {
                                                    localError = null
                                                    onClearError()
                                                    onSignUpWithEmail(resolvedName, cleanEmail, password)
                                                } else {
                                                    isSignUpMode = true
                                                    localError = "Please enter a password with at least 6 characters."
                                                    if (fullName.isBlank()) fullName = resolvedName
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("error_instant_signup_btn"),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AmberPrimary,
                                                contentColor = Color.Black
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "✨ Create Account with this Password (Sign Up)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    isSignUpMode = true
                                                    localError = null
                                                    onClearError()
                                                    if (fullName.isBlank() && email.isNotBlank()) {
                                                        fullName = email.substringBefore("@")
                                                            .replace(".", " ")
                                                            .replaceFirstChar { it.uppercase() }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("error_switch_to_signup_btn"),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Color(0xFF64748B)),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "Edit Sign Up Form",
                                                    color = Color(0xFFE2E8F0),
                                                    fontSize = 11.sp
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val cleanEmail = email.trim().lowercase()
                                                    if (cleanEmail.isNotBlank()) {
                                                        FirebaseAuth.getInstance().sendPasswordResetEmail(cleanEmail)
                                                            .addOnSuccessListener {
                                                                Toast.makeText(context, "Password reset link sent to $cleanEmail!", Toast.LENGTH_LONG).show()
                                                            }
                                                            .addOnFailureListener { ex ->
                                                                Toast.makeText(context, "Reset failed: ${ex.message}", Toast.LENGTH_LONG).show()
                                                            }
                                                    } else {
                                                        localError = "Please enter your email address to reset password."
                                                    }
                                                },
                                                modifier = Modifier.testTag("error_reset_password_btn"),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Color(0xFF64748B)),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "Reset Password",
                                                    color = Color(0xFFE2E8F0),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                } else if (isEmailInUse && isSignUpMode) {
                                    Button(
                                        onClick = {
                                            isSignUpMode = false
                                            localError = null
                                            onClearError()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("error_switch_to_login_btn"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AmberPrimary,
                                            contentColor = Color.Black
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "👉 Switch to Login",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Primary Email/Password Submit Button (Login / Sign Up)
                    Button(
                        onClick = {
                            val trimmedName = fullName.trim()
                            val trimmedEmail = email.trim().lowercase()
                            val enteredPassword = password

                            if (isSignUpMode && trimmedName.length < 2) {
                                localError = "Please enter your full name (at least 2 characters)."
                                return@Button
                            }
                            if (trimmedEmail.isBlank() || !trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
                                localError = "Please enter a valid email address."
                                return@Button
                            }
                            if (isSignUpMode && enteredPassword.length < 6) {
                                localError = "Password must be at least 6 characters long."
                                return@Button
                            }
                            if (!isSignUpMode && enteredPassword.isBlank()) {
                                localError = "Please enter your password."
                                return@Button
                            }

                            localError = null
                            onClearError()
                            try {
                                if (isSignUpMode) {
                                    onSignUpWithEmail(trimmedName, trimmedEmail, enteredPassword)
                                } else {
                                    onLoginWithEmail(trimmedEmail, enteredPassword)
                                }
                            } catch (e: FirebaseAuthException) {
                                val exactError = "FirebaseAuthException [${e.errorCode}]: ${e.message}"
                                localError = exactError
                                Toast.makeText(context, exactError, Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                val exactError = extractRawExceptionMessage(e)
                                localError = exactError
                                Toast.makeText(context, exactError, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("auth_submit_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            contentColor = Color(0xFF1E1402),
                            disabledContainerColor = Color(0xFF334155),
                            disabledContentColor = Color(0xFF94A3B8)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = Color(0xFF1E1402)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isSignUpMode) "Creating Account..." else "Signing in...",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        } else {
                            Text(
                                text = if (isSignUpMode) "Sign Up" else "Login",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    // Quick Toggle between Login and Sign Up
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSignUpMode) "Already have an account? " else "Don't have an account yet? ",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isSignUpMode) "Log In" else "Sign Up here",
                            color = AmberPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable {
                                    isSignUpMode = !isSignUpMode
                                    localError = null
                                    onClearError()
                                }
                                .testTag("toggle_auth_mode_link")
                        )
                    }

                    // Divider: "OR"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF334155)
                        )
                        Text(
                            text = "OR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF334155)
                        )
                    }

                    // Alternative Option: "Continue with Google"
                    Button(
                        onClick = {
                            if (localError != null) {
                                localError = null
                            }
                            if (!errorMessage.isNullOrBlank()) {
                                onClearError()
                            }
                            try {
                                FirebaseAuth.getInstance().signOut()
                            } catch (_: Exception) {
                            }
                            try {
                                val serverClientId = context.getString(R.string.default_web_client_id)
                                if (serverClientId.isBlank()) {
                                    val msg = "Web Client ID (default_web_client_id) is empty"
                                    localError = msg
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                } else {
                                    GetSignInWithGoogleOption.Builder(serverClientId)
                                        .setFilterByAuthorizedAccounts(false)
                                        .setAutoSelectEnabled(false)
                                        .build()
                                    onContinueWithGoogle()
                                }
                            } catch (e: Exception) {
                                val msg = extractRawExceptionMessage(e)
                                localError = msg
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("continue_with_google_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF0F172A),
                            disabledContainerColor = Color(0xFF334155),
                            disabledContentColor = Color(0xFF94A3B8)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        GoogleLogoBadge(modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with Google",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = if (isLoading) Color(0xFF94A3B8) else Color(0xFF0F172A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Professional Security & Trust Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Encrypted",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "End-to-End Encrypted & Secure Authentication",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "By continuing, you agree to TrimTime's Terms of Service & Privacy Policy",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF475569),
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Temporary utility to copy current APK SHA-1 and SHA-256 fingerprints
            TextButton(
                onClick = { copyBuildShaKeysToClipboard(context) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.testTag("copy_build_sha_button")
            ) {
                Text(
                    text = "Copy Build SHA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            }
        }

        if (showAccountNotFoundPrompt && !isSignUpMode) {
            val cleanEmail = email.trim().lowercase()
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showAccountNotFoundPrompt = false },
                containerColor = Color(0xFF1E293B),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Account Not Found or Wrong Password",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Firebase could not sign in for:\n$cleanEmail",
                            color = Color(0xFFF1F5F9),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• If you are new or haven't signed up yet, tap 'Sign Up & Create Account' below to register immediately with this password!\n\n• If you already have an account, tap 'Reset Password' to receive a password reset link.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAccountNotFoundPrompt = false
                            localError = null
                            onClearError()
                            val resolvedName = if (fullName.isNotBlank()) {
                                fullName.trim()
                            } else {
                                cleanEmail.substringBefore("@")
                                    .replace(".", " ")
                                    .replaceFirstChar { it.uppercase() }
                            }.ifBlank { "TrimTime User" }

                            if (password.length < 6) {
                                isSignUpMode = true
                                localError = "Please enter a password with at least 6 characters."
                                if (fullName.isBlank()) fullName = resolvedName
                            } else {
                                onSignUpWithEmail(resolvedName, cleanEmail, password)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("dialog_instant_signup_btn")
                    ) {
                        Text("✨ Sign Up & Create Account", fontWeight = FontWeight.ExtraBold)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                if (cleanEmail.isNotBlank()) {
                                    FirebaseAuth.getInstance().sendPasswordResetEmail(cleanEmail)
                                        .addOnSuccessListener {
                                            Toast.makeText(context, "Password reset email sent to $cleanEmail!", Toast.LENGTH_LONG).show()
                                            showAccountNotFoundPrompt = false
                                        }
                                        .addOnFailureListener { ex ->
                                            Toast.makeText(context, "Error: ${ex.message}", Toast.LENGTH_LONG).show()
                                        }
                                }
                            }
                        ) {
                            Text("Reset Password", color = AmberSecondary, fontSize = 12.sp)
                        }
                        TextButton(onClick = { showAccountNotFoundPrompt = false }) {
                            Text("Dismiss", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun AuthModeTabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AmberPrimary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            color = if (selected) Color(0xFF1E1402) else Color(0xFF94A3B8)
        )
    }
}

@Composable
private fun GoogleLogoBadge(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.2f
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
        val arcSize = Size(diameter, diameter)

        // Red arc (top)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 215f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Yellow arc (left)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 145f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Green arc (bottom)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Blue arc (right) + horizontal bar
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -15f,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.5f, size.height * 0.5f),
            end = Offset(size.width * 0.92f, size.height * 0.5f),
            strokeWidth = strokeWidth
        )
    }
}

private fun copyBuildShaKeysToClipboard(context: Context) {
    try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
        }

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
            if (signingInfo != null) {
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                emptyArray()
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures ?: emptyArray()
        }

        val certBytes = signatures.firstOrNull()?.toByteArray()
        if (certBytes != null) {
            val sha1 = MessageDigest.getInstance("SHA-1")
                .digest(certBytes)
                .joinToString(":") { "%02X".format(it) }
            val sha256 = MessageDigest.getInstance("SHA-256")
                .digest(certBytes)
                .joinToString(":") { "%02X".format(it) }

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText = "SHA-1: $sha1\nSHA-256: $sha256"
            clipboard?.setPrimaryClip(ClipData.newPlainText("Build SHA Keys", clipText))
            Toast.makeText(context, "Keys Copied!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No signing certificate found", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e("AuthFullScreen", "Failed to extract build SHA keys: ${e.message}")
        Toast.makeText(context, "Error copying keys: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

