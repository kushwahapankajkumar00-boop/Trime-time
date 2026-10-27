package com.example.ui.customer

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppLanguage
import com.example.model.AppPreferences
import com.example.model.AppThemeMode
import com.example.model.UserProfile
import androidx.compose.material.icons.filled.Fingerprint
import com.example.ui.components.HelpAndSupportCenterDialog
import com.example.ui.components.LegalPoliciesViewerDialog
import com.example.ui.components.LegalPolicyTab
import com.example.ui.components.SendFeedbackDialog
import com.example.ui.components.SupportFeedbackLegalSectionCard
import com.example.ui.components.VerifiedBlueTickIcon
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.GreenSuccess
import com.example.viewmodel.AuthMode

@Composable
fun ProfileScreen(
    userProfile: UserProfile?,
    appPreferences: AppPreferences = AppPreferences(),
    hasRegisteredSalon: Boolean = false,
    registeredSalonName: String? = null,
    onOpenAuth: (AuthMode) -> Unit,
    onBecomePartner: () -> Unit = {},
    onSwitchToPartner: () -> Unit = {},
    onResetSalonRegistration: () -> Unit = {},
    onOpenQuickAccessSetup: () -> Unit = {},
    onUpdatePersonalProfile: (name: String, phone: String, defaultCity: String) -> Unit = { _, _, _ -> },
    onUpdateAccountIdentity: (name: String, email: String, phone: String, asPartner: Boolean) -> Unit = { _, _, _, _ -> },
    onRequestPhoneOtp: (
        rawPhone: String,
        onOtpGenerated: (cleanTenDigits: String, otpCode: String) -> Unit,
        onError: (String) -> Unit
    ) -> Unit = { phone, onOtp, _ ->
        val clean = phone.filter { it.isDigit() }.takeLast(10)
        onOtp(clean, (1000..9999).random().toString())
    },
    onGenerateNewOtpCode: () -> String = { (1000..9999).random().toString() },
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    onLanguageChange: (AppLanguage) -> Unit = {},
    onNotificationPreferencesChange: (bookingAlerts: Boolean, queueReminders: Boolean, offersDiscounts: Boolean) -> Unit = { _, _, _ -> },
    onShowDemoSalonsChange: (Boolean) -> Unit = {},
    onClearCache: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onSubmitFeedback: (rating: Int, category: String, message: String, onDone: (Boolean, String) -> Unit) -> Unit = { _, _, _, onDone ->
        onDone(true, "Saved")
    },
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEditPersonalDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var activeLegalTab by remember { mutableStateOf<LegalPolicyTab?>(null) }

    // 2-Step Phone OTP state for Profile Phone Verification / Edit (Shown only during active verification)
    var showTopSmsBanner by remember { mutableStateOf(false) }
    var activeOtpPhone by remember { mutableStateOf("") }
    var activeOtpCode by remember { mutableStateOf("") }
    var showPhoneOtpModal by remember { mutableStateOf(false) }
    var pendingProfileSave by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    val isHinglish = appPreferences.language == AppLanguage.HINDI_HINGLISH
    val bgColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .testTag("customer_profile_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (showTopSmsBanner && showPhoneOtpModal && activeOtpCode.length == 4) {
            item {
                com.example.ui.components.TopSmsOtpBanner(
                    visible = true,
                    phoneTenDigits = activeOtpPhone,
                    otpCode = activeOtpCode,
                    onDismiss = { showTopSmsBanner = false }
                )
            }
        }

        // 1. User Account Info Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Avatar",
                                tint = AmberPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = userProfile?.name ?: "Customer",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = primaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (userProfile?.isPhoneVerified == true) {
                                    VerifiedBlueTickIcon(size = 17.dp)
                                }
                            }
                            Text(
                                text = userProfile?.email?.takeIf { it.isNotBlank() } ?: "Not signed in",
                                fontSize = 12.sp,
                                color = secondaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!userProfile?.phone.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                ) {
                                    Text(
                                        text = userProfile?.phone ?: "",
                                        fontSize = 12.sp,
                                        color = AmberPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    if (userProfile?.isPhoneVerified == true) {
                                        VerifiedBlueTickIcon(size = 15.dp)
                                    }
                                }
                            }
                            val displayCity = userProfile?.defaultCity?.ifBlank { appPreferences.defaultCity }
                                ?: appPreferences.defaultCity
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = displayCity,
                                    fontSize = 11.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F261D))
                                .border(1.dp, Color(0xFF065F46), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isHinglish) "ग्राहक (Customer)" else "Customer",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenSuccess,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showEditPersonalDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("edit_personal_profile_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.7f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = AmberPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHinglish) "प्रोफ़ाइल बदलें" else "Edit Profile",
                                color = AmberPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenQuickAccessSetup,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("quick_access_setup_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.7f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Quick Access",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (userProfile?.hasQuickAccessSetup == true) "Quick Access ✓" else "Set MPIN / Biometric",
                                color = primaryTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 2. Partner Salon Registration / Switcher Card (Strict Registration Wall)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("partner_portal_section"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(Color(0xFF3B82F6))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E3A8A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = Color(0xFF93C5FD),
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (hasRegisteredSalon) "REGISTERED SALON PARTNER" else "SALON OWNER PORTAL",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF93C5FD),
                                    letterSpacing = 0.8.sp
                                )
                                if (hasRegisteredSalon) {
                                    VerifiedBlueTickIcon(size = 14.dp)
                                }
                            }
                            Text(
                                text = if (hasRegisteredSalon) {
                                    registeredSalonName?.takeIf { it.isNotBlank() } ?: "Manage Your Salon Dashboard"
                                } else {
                                    if (isHinglish) "अपना सैलून रजिस्टर करें (Become a Salon Partner)" else "Become a Salon Partner"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                            Text(
                                text = if (hasRegisteredSalon) {
                                    "Manage your salon schedule, live chair availability, and customer bookings."
                                } else {
                                    "Register your shop name, address, timings, services & GPS pin to start receiving bookings."
                                },
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (hasRegisteredSalon) {
                                onSwitchToPartner()
                            } else {
                                onBecomePartner()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("login_as_partner_btn"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasRegisteredSalon) Color(0xFF2563EB) else AmberPrimary,
                            contentColor = if (hasRegisteredSalon) Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (hasRegisteredSalon) Icons.Default.SwapHoriz else Icons.Default.Storefront,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (hasRegisteredSalon) Color.White else Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasRegisteredSalon) {
                                if (isHinglish) "पार्टनर डैशबोर्ड खोलें" else "Open Partner Dashboard"
                            } else {
                                if (isHinglish) "सैलून पार्टनर बनें (Become a Salon Partner)" else "Become a Salon Partner"
                            },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = if (hasRegisteredSalon) Color.White else Color.Black,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 3. Full Customer Settings Section ("App Settings & Preferences")
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("customer_app_settings_section"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Section Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (isHinglish) "ऐप सेटिंग्स और पसंद (App Settings & Preferences)" else "App Settings & Preferences",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = primaryTextColor
                            )
                            Text(
                                text = if (isHinglish) "थीम, नोटिफिकेशन, भाषा और प्रोफ़ाइल सेटिंग्स" else "Theme, notifications, language & security controls",
                                fontSize = 11.sp,
                                color = secondaryTextColor
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))

                    // (a) Appearance / Theme Toggle
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isHinglish) "दिखावट / थीम (Appearance / Theme)" else "Appearance / Theme",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppThemeMode.entries.forEach { mode ->
                                val isSelected = appPreferences.themeMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) AmberPrimary else Color(0xFF0F172A).copy(alpha = 0.6f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) AmberPrimary else Color(0xFF334155),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onThemeModeChange(mode) }
                                        .padding(vertical = 9.dp, horizontal = 4.dp)
                                        .testTag("theme_mode_${mode.key}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) Color.Black else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))

                    // (b) Language Selector (English vs Hindi / Hinglish)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isHinglish) "ऐप की भाषा (App Language)" else "Language Selector",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppLanguage.entries.forEach { lang ->
                                val isSelected = appPreferences.language == lang
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) AmberPrimary else Color(0xFF0F172A).copy(alpha = 0.6f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) AmberPrimary else Color(0xFF334155),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onLanguageChange(lang) }
                                        .padding(vertical = 9.dp, horizontal = 8.dp)
                                        .testTag("language_option_${lang.key}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lang.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) Color.Black else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))

                    // (c) Notification Preferences
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isHinglish) "नोटिफिकेशन सेटिंग्स (Notification Preferences)" else "Notification Preferences",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor
                            )
                        }

                        NotificationToggleRow(
                            title = if (isHinglish) "बुकिंग स्टेटस अलर्ट (Booking Status Alerts)" else "Booking Status Alerts",
                            subtitle = "Instant updates when barber accepts or completes your slot",
                            checked = appPreferences.bookingStatusAlerts,
                            onCheckedChange = { checked ->
                                onNotificationPreferencesChange(
                                    checked,
                                    appPreferences.queueTurnReminders,
                                    appPreferences.salonOffersDiscounts
                                )
                            },
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            testTag = "notif_toggle_booking_alerts"
                        )

                        NotificationToggleRow(
                            title = if (isHinglish) "बारी आने का रिमाइंडर (Queue Turn Reminders)" else "Queue Turn Reminders",
                            subtitle = "Alert 10 minutes before your chair turn starts",
                            checked = appPreferences.queueTurnReminders,
                            onCheckedChange = { checked ->
                                onNotificationPreferencesChange(
                                    appPreferences.bookingStatusAlerts,
                                    checked,
                                    appPreferences.salonOffersDiscounts
                                )
                            },
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            testTag = "notif_toggle_queue_reminders"
                        )

                        NotificationToggleRow(
                            title = if (isHinglish) "सैलून ऑफर्स और डिस्काउंट (Salon Offers & Discounts)" else "Salon Offers & Discounts",
                            subtitle = "Nearby grooming combo deals & festive discounts",
                            checked = appPreferences.salonOffersDiscounts,
                            onCheckedChange = { checked ->
                                onNotificationPreferencesChange(
                                    appPreferences.bookingStatusAlerts,
                                    appPreferences.queueTurnReminders,
                                    checked
                                )
                            },
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            testTag = "notif_toggle_offers_discounts"
                        )
                    }

                    HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))

                    // (d) & (e) Clear Cache & Help & Support Quick Links
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClearCache,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("settings_clear_cache_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF475569))
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = "Clear Cache",
                                tint = AmberPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHinglish) "कैश साफ़ करें" else "Clear Cache",
                                color = primaryTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = { showSupportDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("settings_help_support_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF475569))
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "Help & Support",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHinglish) "मदद (Help)" else "Help & Support",
                                color = primaryTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 4. Help, Support, Feedback & Legal Policies Card
        item {
            SupportFeedbackLegalSectionCard(
                isHinglish = isHinglish,
                cardBackgroundColor = cardColor,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                onOpenSupportCenter = { showSupportDialog = true },
                onOpenFeedbackForm = { showFeedbackDialog = true },
                onOpenTerms = { activeLegalTab = LegalPolicyTab.TERMS },
                onOpenPrivacyPolicy = { activeLegalTab = LegalPolicyTab.PRIVACY }
            )
        }

        // 5. Log Out Button
        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("customer_logout_btn"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Logout",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isHinglish) "लॉग आउट (Log Out)" else "Log Out",
                    color = Color(0xFFEF4444),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // Dialog 1: Edit Personal Profile (Name, Phone Number, Default City/Location) with 2-Step Phone OTP
    if (showEditPersonalDialog) {
        val rawDigits = userProfile?.phone?.filter { it.isDigit() }.orEmpty()
        val initialPhone = if (rawDigits.length > 10 && rawDigits.startsWith("91")) {
            rawDigits.drop(2).take(10)
        } else {
            rawDigits.takeLast(10)
        }
        EditPersonalProfileDialog(
            initialName = userProfile?.name ?: "",
            initialPhone = initialPhone,
            initialCity = userProfile?.defaultCity?.ifBlank { appPreferences.defaultCity } ?: appPreferences.defaultCity,
            onDismiss = { showEditPersonalDialog = false },
            onSave = { updatedName, updatedPhone, updatedCity ->
                val cleanNewDigits = updatedPhone.filter { it.isDigit() }.takeLast(10)
                val phoneChanged = cleanNewDigits != initialPhone || userProfile?.isPhoneVerified != true
                if (phoneChanged) {
                    onRequestPhoneOtp(
                        cleanNewDigits,
                        { verifiedDigits, otp ->
                            showEditPersonalDialog = false
                            pendingProfileSave = Triple(updatedName, verifiedDigits, updatedCity)
                            activeOtpPhone = verifiedDigits
                            activeOtpCode = otp
                            showTopSmsBanner = true
                            com.example.ui.components.triggerAndroidSmsNotification(context, verifiedDigits, otp)
                            showPhoneOtpModal = true
                        },
                        { err ->
                            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                } else {
                    showEditPersonalDialog = false
                    onUpdatePersonalProfile(updatedName, updatedPhone, updatedCity)
                }
            }
        )
    }

    if (showPhoneOtpModal && activeOtpCode.length == 4) {
        com.example.ui.components.VerifyMobileOtpDialog(
            phoneTenDigits = activeOtpPhone,
            expectedOtp = activeOtpCode,
            title = "Verify Updated Mobile Number",
            subtitle = "Enter the 4-digit OTP sent to +91 $activeOtpPhone to verify and save your updated mobile number.",
            requireNewLoginPin = false,
            onResendOtp = {
                val refreshed = onGenerateNewOtpCode()
                activeOtpCode = refreshed
                showTopSmsBanner = true
                com.example.ui.components.triggerAndroidSmsNotification(context, activeOtpPhone, refreshed)
            },
            onVerified = { _ ->
                showPhoneOtpModal = false
                showTopSmsBanner = false
                activeOtpCode = ""
                pendingProfileSave?.let { (uName, uPhone, uCity) ->
                    onUpdatePersonalProfile(uName, uPhone, uCity)
                }
                pendingProfileSave = null
            },
            onDismiss = {
                showPhoneOtpModal = false
                showTopSmsBanner = false
            }
        )
    }

    // Dialog 3: Help & Support Center Sheet / Dialog (Call, WhatsApp, Email & FAQs)
    if (showSupportDialog) {
        HelpAndSupportCenterDialog(
            onDismiss = { showSupportDialog = false },
            onOpenFeedbackForm = {
                showSupportDialog = false
                showFeedbackDialog = true
            },
            onOpenTerms = {
                showSupportDialog = false
                activeLegalTab = LegalPolicyTab.TERMS
            },
            onOpenPrivacy = {
                showSupportDialog = false
                activeLegalTab = LegalPolicyTab.PRIVACY
            }
        )
    }

    // Dialog 4: Send Feedback / Report an Issue Form
    if (showFeedbackDialog) {
        SendFeedbackDialog(
            userName = userProfile?.name ?: "Customer",
            userRole = userProfile?.role ?: "customer",
            onDismiss = { showFeedbackDialog = false },
            onSubmitFeedback = onSubmitFeedback
        )
    }

    // Dialog 5: Dedicated Terms & Conditions and Privacy Policy Viewer
    activeLegalTab?.let { tab ->
        LegalPoliciesViewerDialog(
            initialTab = tab,
            onDismiss = { activeLegalTab = null }
        )
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = secondaryTextColor
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = AmberPrimary,
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF0F172A)
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun EditPersonalProfileDialog(
    initialName: String,
    initialPhone: String,
    initialCity: String,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, city: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var city by remember { mutableStateOf(initialCity) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AmberPrimary,
        unfocusedBorderColor = Color(0xFF475569),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = AmberPrimary,
        unfocusedLabelColor = Color(0xFF94A3B8),
        cursorColor = AmberPrimary
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Text(
                text = "Edit Personal Profile",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorText = null
                    },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = AmberPrimary) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_name_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { input ->
                        phone = input.filter { it.isDigit() }.take(10)
                        errorText = null
                    },
                    label = { Text("10-Digit Mobile Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = AmberPrimary) },
                    prefix = { Text("+91 ", color = AmberPrimary, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_phone_input")
                )

                OutlinedTextField(
                    value = city,
                    onValueChange = {
                        city = it
                        errorText = null
                    },
                    label = { Text("Default City / Location") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = AmberPrimary) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_city_input")
                )

                if (!errorText.isNullOrBlank()) {
                    Text(
                        text = errorText ?: "",
                        color = Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    val cleanPhone = phone.filter { it.isDigit() }
                    val cleanCity = city.trim().ifBlank { "Current Location" }
                    when {
                        cleanName.length < 2 -> errorText = "Please enter your full name."
                        cleanPhone.length != 10 -> errorText = "Please enter a valid 10-digit phone number."
                        else -> onSave(cleanName, cleanPhone, cleanCity)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberPrimary,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("save_personal_profile_btn")
            ) {
                Text("Save Changes", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}
