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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
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

    val activeError = localError ?: errorMessage

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

                    // Error Message Banner
                    AnimatedVisibility(
                        visible = !activeError.isNullOrBlank(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        activeError?.let { errorText ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2D1214))
                                    .border(1.dp, RedAlert.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("auth_error_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Authentication Error",
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

                    // Primary Email/Password Submit Button (Login / Sign Up)
                    Button(
                        onClick = {
                            val trimmedName = fullName.trim()
                            val trimmedEmail = email.trim()

                            if (isSignUpMode && trimmedName.length < 2) {
                                localError = "Please enter your full name (at least 2 characters)."
                                return@Button
                            }
                            if (trimmedEmail.isBlank() || !trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
                                localError = "Please enter a valid email address."
                                return@Button
                            }
                            if (isSignUpMode && password.length < 6) {
                                localError = "Password must be at least 6 characters long."
                                return@Button
                            }
                            if (!isSignUpMode && password.isBlank()) {
                                localError = "Please enter your password."
                                return@Button
                            }

                            localError = null
                            if (isSignUpMode) {
                                onSignUpWithEmail(trimmedName, trimmedEmail, password)
                            } else {
                                onLoginWithEmail(trimmedEmail, password)
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
                            localError = null
                            onContinueWithGoogle()
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
