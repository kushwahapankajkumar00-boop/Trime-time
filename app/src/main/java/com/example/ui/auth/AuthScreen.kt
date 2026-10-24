package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.RedAlert

@Composable
fun AuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLoginCustomer: (email: String, pass: String) -> Unit,
    onSignupCustomer: (name: String, email: String, phone: String, pass: String) -> Unit,
    onLoginPartner: (email: String, pass: String) -> Unit,
    onSignupPartner: (salonName: String, ownerName: String, email: String, phone: String, location: String, pass: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 0 = Customer, 1 = Partner
    var selectedPortal by remember { mutableIntStateOf(0) }
    // false = Sign In, true = Sign Up
    var isSignUp by remember { mutableStateOf(false) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var salonName by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }

    val isPartner = selectedPortal == 1

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Brand Logo & Title
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AmberPrimary, Color(0xFFD97706))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = "TrimTime Logo",
                    tint = Color.Black,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "TrimTime",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = Color.White
            )

            Text(
                text = "Hyper-local salon slots • Pay at Salon • Anti-Fake PIN",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Top Segmented Bar: Customer vs Partner Portal
            TabRow(
                selectedTabIndex = selectedPortal,
                containerColor = DarkSurface,
                contentColor = if (isPartner) Color(0xFF60A5FA) else AmberPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedPortal]),
                        color = if (isPartner) Color(0xFF3B82F6) else AmberPrimary,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("auth_portal_tab_row")
            ) {
                Tab(
                    selected = selectedPortal == 0,
                    onClick = {
                        selectedPortal = 0
                        isSignUp = false
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedPortal == 0) AmberPrimary else Color(0xFF94A3B8)
                            )
                            Text(
                                text = "Customer",
                                fontWeight = if (selectedPortal == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (selectedPortal == 0) AmberPrimary else Color(0xFF94A3B8)
                            )
                        }
                    },
                    modifier = Modifier.testTag("tab_customer_auth")
                )

                Tab(
                    selected = selectedPortal == 1,
                    onClick = {
                        selectedPortal = 1
                        isSignUp = false
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedPortal == 1) Color(0xFF93C5FD) else Color(0xFF94A3B8)
                            )
                            Text(
                                text = "Partner Portal",
                                fontWeight = if (selectedPortal == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (selectedPortal == 1) Color(0xFF93C5FD) else Color(0xFF94A3B8)
                            )
                        }
                    },
                    modifier = Modifier.testTag("tab_partner_auth")
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Card Form Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_form_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = if (isPartner) {
                    CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF2563EB))
                    )
                } else null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Form Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = when {
                                    isPartner && !isSignUp -> "Partner Login"
                                    isPartner && isSignUp -> "Register Salon"
                                    !isPartner && !isSignUp -> "Customer Sign In"
                                    else -> "Create Account"
                                },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = if (isPartner) "Manage live salon queue & PIN verification" else "Book instant 10-15m haircut slots",
                                fontSize = 11.sp,
                                color = if (isPartner) Color(0xFF93C5FD) else AmberPrimary
                            )
                        }

                        // Toggle Sign In / Sign Up
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .clickable { isSignUp = !isSignUp }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("toggle_signup_signin")
                        ) {
                            Text(
                                text = if (isSignUp) "Switch to Login" else "New? Sign Up",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPartner) Color(0xFF60A5FA) else AmberPrimary
                            )
                        }
                    }

                    // Security Info for Partner Mode
                    if (isPartner) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF1E3A8A), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Protected: Partner Mode verifies role == 'partner' in Firestore. Customer credentials will be denied access.",
                                fontSize = 10.sp,
                                color = Color(0xFFCBD5E1),
                                lineHeight = 14.sp
                            )
                        }
                    }

                    // Error Banner
                    AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                        errorMessage?.let { msg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF450A0A))
                                    .border(1.dp, Color(0xFF991B1B), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = RedAlert,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = msg,
                                    fontSize = 11.sp,
                                    color = Color(0xFFFECACA),
                                    modifier = Modifier.testTag("auth_error_message")
                                )
                            }
                        }
                    }

                    // Extra fields for Partner Registration
                    if (isPartner && isSignUp) {
                        OutlinedTextField(
                            value = salonName,
                            onValueChange = { salonName = it },
                            label = { Text("Salon / Barber Shop Name") },
                            placeholder = { Text("e.g. The Blade & Comb Studio") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_partner_salon_name"),
                            colors = authTextFieldColors(isPartner),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("Shop Location / Address") },
                            placeholder = { Text("e.g. 27th Main, Sector 1, HSR") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_partner_location"),
                            colors = authTextFieldColors(isPartner),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Name & Phone for Signups
                    if (isSignUp) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(if (isPartner) "Owner Full Name" else "Your Full Name") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF94A3B8))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_auth_name"),
                            colors = authTextFieldColors(isPartner),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            leadingIcon = {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF94A3B8))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_auth_phone"),
                            colors = authTextFieldColors(isPartner),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(if (isPartner) "Partner Email" else "Customer Email") },
                        placeholder = { Text(if (isPartner) "partner@trimtime.app" else "you@example.com") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF94A3B8))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_auth_email"),
                        colors = authTextFieldColors(isPartner),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8))
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_auth_password"),
                        colors = authTextFieldColors(isPartner),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Submit Action Button
                    Button(
                        onClick = {
                            if (isPartner) {
                                if (isSignUp) {
                                    onSignupPartner(salonName, name, email, phone, location, password)
                                } else {
                                    onLoginPartner(email, password)
                                }
                            } else {
                                if (isSignUp) {
                                    onSignupCustomer(name, email, phone, password)
                                } else {
                                    onLoginCustomer(email, password)
                                }
                            }
                        },
                        enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_action_submit_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPartner) Color(0xFF2563EB) else AmberPrimary,
                            disabledContainerColor = Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = when {
                                    isPartner && !isSignUp -> "Login to Partner Mode"
                                    isPartner && isSignUp -> "Register & Enter Partner Mode"
                                    !isPartner && !isSignUp -> "Sign In to Book Slots"
                                    else -> "Create Account & Continue"
                                },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = if (isPartner) Color.White else Color(0xFF1E1402)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fast Testing hint pill
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Tip: Create a new account with your email, or use registered credentials. Authentication is verified via Firebase.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun authTextFieldColors(isPartner: Boolean) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = if (isPartner) Color(0xFF3B82F6) else AmberPrimary,
    unfocusedBorderColor = Color(0xFF334155),
    focusedContainerColor = DarkCardSurface,
    unfocusedContainerColor = DarkCardSurface,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = if (isPartner) Color(0xFF93C5FD) else AmberPrimary,
    unfocusedLabelColor = Color(0xFF94A3B8)
)
