package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess

const val TRIMTIME_SUPPORT_PHONE = "+916200092115"
const val TRIMTIME_WHATSAPP_URL =
    "https://wa.me/916200092115?text=Hello%20TrimTime%20Support,%20I%20need%20help%20with..."
const val TRIMTIME_SUPPORT_EMAIL = "support@trimtime.app"
const val TRIMTIME_SUPPORT_EMAIL_SUBJECT = "TrimTime App Support"

enum class LegalPolicyTab(val title: String) {
    TERMS("Terms & Conditions"),
    PRIVACY("Privacy Policy")
}

/**
 * Launches Android's phone dialer with 1 tap without needing runtime CALL_PHONE permissions.
 */
fun launchPhoneDialer(context: Context, phoneNumber: String = TRIMTIME_SUPPORT_PHONE) {
    val cleanPhone = phoneNumber.trim().ifBlank { TRIMTIME_SUPPORT_PHONE }
    try {
        val dialUri = if (cleanPhone.startsWith("tel:")) {
            Uri.parse(cleanPhone)
        } else {
            Uri.parse("tel:$cleanPhone")
        }
        val intent = Intent(Intent.ACTION_DIAL, dialUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Unable to open phone dialer for $cleanPhone",
            Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Opens WhatsApp Live Support directly using Intent.ACTION_VIEW.
 */
fun launchWhatsAppSupport(context: Context, url: String = TRIMTIME_WHATSAPP_URL) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Unable to open WhatsApp Support link.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Launches Email Support client using Intent.ACTION_SENDTO with mailto:support@trimtime.app
 * and subject "TrimTime App Support".
 */
fun launchEmailSupport(
    context: Context,
    email: String = TRIMTIME_SUPPORT_EMAIL,
    subject: String = TRIMTIME_SUPPORT_EMAIL_SUBJECT
) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallbackUri = Uri.parse(
                "mailto:$email?subject=${Uri.encode(subject)}"
            )
            val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } catch (ex: Exception) {
            Toast.makeText(
                context,
                "Email Support: $email",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

private data class SupportFaqItem(
    val id: String,
    val question: String,
    val answer: String,
    val badge: String
)

private val defaultSupportFaqs = listOf(
    SupportFaqItem(
        id = "instant_booking",
        question = "How does Instant Chair Booking work on TrimTime?",
        answer = "Select any nearby salon from the List or Live OpenStreetMap view, pick your grooming service (Haircut, Beard, Combo, or Facial), and choose an available slot such as 'In 15 mins (⚡ Instant)'. Your booking is sent in real time to the barber's Partner Queue via Cloud Firestore.",
        badge = "Instant Slot"
    ),
    SupportFaqItem(
        id = "service_pin",
        question = "What is the 4-Digit Anti-Fake Service PIN and when do I share it?",
        answer = "Every confirmed booking generates a unique 4-digit Service PIN shown on your booking card. Share this PIN with the barber ONLY when you arrive at the salon and sit in the chair. This prevents fake completions and guarantees your turn.",
        badge = "4-Digit PIN"
    ),
    SupportFaqItem(
        id = "pay_at_salon",
        question = "Do I have to pay any advance booking fee online?",
        answer = "No! TrimTime follows a strict ₹0 Advance / 100% Pay-at-Salon policy. You never pay online to book a slot. Pay the exact listed service price directly to the barber by Cash or UPI after your haircut is completed.",
        badge = "₹0 Advance"
    ),
    SupportFaqItem(
        id = "cancellation_policy",
        question = "What is the 3-Strike Cancellation & No-Show Policy?",
        answer = "Because salons reserve a real physical chair for you with ₹0 advance, frequent last-minute cancellations or no-shows hurt local barbers. Accounts that accumulate 3 cancellations/no-shows are automatically restricted from making new instant bookings.",
        badge = "Anti-Spam"
    ),
    SupportFaqItem(
        id = "partner_onboarding",
        question = "How can a salon owner register their shop on TrimTime?",
        answer = "Go to Profile → 'Become a Salon Partner' and fill out your Shop Name, Business Phone, Street Address, Timings, Chair Count, Services Menu, and GPS Map Pin. Once submitted, your shop goes live immediately for nearby customers.",
        badge = "Partner"
    )
)

/**
 * Reusable Card for Customer Profile and Partner Dashboard providing 1-tap access to:
 * 1. Help & Support Center (Call, WhatsApp, Email & Quick FAQs)
 * 2. Send Feedback / Report an Issue (1–5 Stars saved to Firestore `feedback` collection)
 * 3. Terms & Conditions Viewer
 * 4. Privacy Policy Viewer
 */
@Composable
fun SupportFeedbackLegalSectionCard(
    isHinglish: Boolean = false,
    cardBackgroundColor: Color = Color(0xFF1E293B),
    primaryTextColor: Color = Color.White,
    secondaryTextColor: Color = Color(0xFF94A3B8),
    onOpenSupportCenter: () -> Unit,
    onOpenFeedbackForm: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("help_support_legal_section_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AmberPrimary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHinglish) {
                            "मदद, फ़ीडबैक और कानूनी नीतियां (Help, Support & Legal)"
                        } else {
                            "Help, Support, Feedback & Legal Policies"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = primaryTextColor
                    )
                    Text(
                        text = if (isHinglish) {
                            "24×7 कस्टमर सपोर्ट कॉल, व्हाट्सएप, ईमेल, फ़ीडबैक और नियम"
                        } else {
                            "1-tap Call, WhatsApp, Email support, live feedback & policies"
                        },
                        fontSize = 11.sp,
                        color = secondaryTextColor
                    )
                }
            }

            // Quick 1-Tap Direct Contact Strip (Call, WhatsApp, Email)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Call Support
                OutlinedButton(
                    onClick = { launchPhoneDialer(context, TRIMTIME_SUPPORT_PHONE) },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("quick_call_support_btn"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call Customer Support",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Call",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryTextColor,
                        maxLines = 1
                    )
                }

                // WhatsApp Support
                OutlinedButton(
                    onClick = { launchWhatsAppSupport(context, TRIMTIME_WHATSAPP_URL) },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("quick_whatsapp_support_btn"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.6f)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "WhatsApp Live Support",
                        tint = GreenSuccess,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "WhatsApp",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryTextColor,
                        maxLines = 1
                    )
                }

                // Email Support
                OutlinedButton(
                    onClick = { launchEmailSupport(context) },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("quick_email_support_btn"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.6f)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email Support",
                        tint = AmberPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Email",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryTextColor,
                        maxLines = 1
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))

            // 1. Help & Support Center Row (opens full sheet with Call, WhatsApp, Email & FAQs)
            SupportNavigationRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                iconTint = Color(0xFF38BDF8),
                title = if (isHinglish) "हेल्प और सपोर्ट सेंटर (Help & Support Center)" else "Help & Support Center",
                subtitle = "Call +91 62000 92115, WhatsApp chat, Email & Quick FAQs",
                badgeText = "24×7",
                badgeColor = Color(0xFF0284C7),
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                onClick = onOpenSupportCenter,
                testTag = "open_help_support_center_row"
            )

            // 2. Send Feedback / Report an Issue Row (opens Firestore Feedback Form)
            SupportNavigationRow(
                icon = Icons.Default.RateReview,
                iconTint = AmberPrimary,
                title = if (isHinglish) "फ़ीडबैक भेजें / शिकायत करें (Send Feedback)" else "Send Feedback / Report an Issue",
                subtitle = "Rate 1–5 stars, report a bug, salon complaint or feature request",
                badgeText = "LIVE",
                badgeColor = Color(0xFFD97706),
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                onClick = onOpenFeedbackForm,
                testTag = "open_send_feedback_row"
            )

            HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))

            // 3. Terms & Conditions Row
            SupportNavigationRow(
                icon = Icons.Default.Gavel,
                iconTint = Color(0xFFA78BFA),
                title = if (isHinglish) "नियम और शर्तें (Terms & Conditions)" else "Terms & Conditions",
                subtitle = "₹0 Advance Pay-at-Salon, 4-Digit PIN & 3-Strike cancellation rules",
                badgeText = null,
                badgeColor = Color.Transparent,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                onClick = onOpenTerms,
                testTag = "open_terms_conditions_row"
            )

            // 4. Privacy Policy Row
            SupportNavigationRow(
                icon = Icons.Default.PrivacyTip,
                iconTint = GreenSuccess,
                title = if (isHinglish) "प्राइवेसी पॉलिसी (Privacy Policy)" else "Privacy Policy",
                subtitle = "OpenStreetMap GPS usage, Firestore security & user data privacy",
                badgeText = null,
                badgeColor = Color.Transparent,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                onClick = onOpenPrivacyPolicy,
                testTag = "open_privacy_policy_row"
            )
        }
    }
}

@Composable
private fun SupportNavigationRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String?,
    badgeColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.55f))
            .border(1.dp, Color(0xFF334155).copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!badgeText.isNullOrBlank()) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(0.5.dp, badgeColor)
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = secondaryTextColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open",
            tint = secondaryTextColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 1. "Help & Support Center" Full Sheet / Dialog with Working Call, WhatsApp, Email Intents
 * and Expandable Quick FAQs Accordion.
 */
@Composable
fun HelpAndSupportCenterDialog(
    onDismiss: () -> Unit,
    onOpenFeedbackForm: () -> Unit,
    onOpenTerms: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {}
) {
    val context = LocalContext.current
    var expandedFaqId by remember { mutableStateOf<String?>(defaultSupportFaqs.first().id) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(22.dp))
                .testTag("help_support_center_dialog"),
            color = DarkBackground,
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkCardSurface)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AmberPrimary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "TrimTime Help & Support Center",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Instant Call, WhatsApp, Email & Grooming FAQs",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_help_support_dialog_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Support Center",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Direct Support Channels Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "DIRECT CUSTOMER & PARTNER SUPPORT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberPrimary,
                                letterSpacing = 0.8.sp
                            )

                            // (a) Call Customer Support Button
                            Button(
                                onClick = { launchPhoneDialer(context, TRIMTIME_SUPPORT_PHONE) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("support_call_customer_support_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0284C7),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call Customer Support",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Call Customer Support (+91 62000 92115)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }

                            // (b) WhatsApp Live Support Button
                            Button(
                                onClick = { launchWhatsAppSupport(context, TRIMTIME_WHATSAPP_URL) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("support_whatsapp_live_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF16A34A),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = "WhatsApp Live Support",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "WhatsApp Live Support (Instant Chat)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }

                            // (c) Email Support Button
                            OutlinedButton(
                                onClick = {
                                    launchEmailSupport(
                                        context = context,
                                        email = TRIMTIME_SUPPORT_EMAIL,
                                        subject = TRIMTIME_SUPPORT_EMAIL_SUBJECT
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("support_email_support_btn"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AmberPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Email Support",
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Email Support (support@trimtime.app)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = AmberPrimary
                                )
                            }
                        }
                    }

                    // Send Feedback / Report Issue Banner inside Support Center
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF172554)),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 10.dp)
                            ) {
                                Text(
                                    text = "Have a bug report or salon complaint?",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Submit a rated feedback ticket directly to our Cloud Firestore team.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFBFDBFE)
                                )
                            }
                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenFeedbackForm()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberPrimary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("support_sheet_open_feedback_btn")
                            ) {
                                Text(
                                    text = "Send Feedback",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // (e) Quick FAQs Section (Accordion / Expandable Cards)
                    Text(
                        text = "FREQUENTLY ASKED QUESTIONS (FAQs)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AmberPrimary,
                        letterSpacing = 0.8.sp
                    )

                    defaultSupportFaqs.forEach { faq ->
                        val isExpanded = expandedFaqId == faq.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedFaqId = if (isExpanded) null else faq.id
                                }
                                .testTag("faq_accordion_${faq.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isExpanded) Color(0xFF1E293B) else DarkCardSurface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isExpanded) AmberPrimary.copy(alpha = 0.7f) else Color(0xFF334155)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = AmberPrimary.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = faq.badge,
                                            color = AmberPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = faq.question,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse FAQ" else "Expand FAQ",
                                        tint = AmberPrimary
                                    )
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = Color(0xFF334155))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = faq.answer,
                                            fontSize = 12.sp,
                                            color = Color(0xFFCBD5E1),
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Quick links to Legal Policies from inside Support Sheet
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenTerms()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF475569))
                        ) {
                            Text(
                                text = "Terms & Conditions",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenPrivacy()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF475569))
                        ) {
                            Text(
                                text = "Privacy Policy",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. Real-Time "Send Feedback / Report an Issue" Form (Saved to Firestore `feedback` collection)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SendFeedbackDialog(
    userName: String,
    userRole: String,
    onDismiss: () -> Unit,
    onSubmitFeedback: (rating: Int, category: String, message: String, onDone: (Boolean, String) -> Unit) -> Unit
) {
    var selectedRating by remember { mutableIntStateOf(5) }
    val categories = remember {
        listOf("Bug Report", "Feature Request", "Salon Complaint", "Appreciation")
    }
    var selectedCategory by remember { mutableStateOf(categories.first()) }
    var message by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submissionSuccess by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AmberPrimary,
        unfocusedBorderColor = Color(0xFF475569),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = AmberPrimary,
        unfocusedLabelColor = Color(0xFF94A3B8),
        cursorColor = AmberPrimary
    )

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(22.dp))
                .testTag("send_feedback_dialog"),
            color = DarkCardSurface,
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AmberPrimary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Feedback,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Send Feedback / Report Issue",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Submitting as ${userName.ifBlank { "TrimTime User" }} (${userRole.uppercase()})",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(
                        onClick = { if (!isSubmitting) onDismiss() },
                        modifier = Modifier.testTag("close_feedback_dialog_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Feedback Dialog",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF334155))

                if (submissionSuccess) {
                    // Confirmation View
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(GreenSuccess.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = GreenSuccess,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text(
                            text = "Thank You for Your Feedback!",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Your $selectedRating-star '$selectedCategory' submission has been saved to Cloud Firestore. Our team reviews every report promptly.",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("feedback_done_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberPrimary,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Done", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                } else {
                    // 1. Star Rating Selector (1 to 5 stars)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "1. Select Star Rating ($selectedRating / 5 Stars)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E8F0)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurface)
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (star in 1..5) {
                                val isFilled = star <= selectedRating
                                IconButton(
                                    onClick = { selectedRating = star },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("feedback_star_$star")
                                ) {
                                    Icon(
                                        imageVector = if (isFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "$star Stars",
                                        tint = if (isFilled) AmberPrimary else Color(0xFF64748B),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Category Selector (Bug Report, Feature Request, Salon Complaint, Appreciation)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "2. Choose Feedback Category",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E8F0)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { category ->
                                val isSelected = selectedCategory == category
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) AmberPrimary else DarkSurface)
                                        .border(
                                            1.dp,
                                            if (isSelected) AmberPrimary else Color(0xFF475569),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedCategory = category }
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                        .testTag(
                                            "feedback_category_${category.lowercase().replace(" ", "_")}"
                                        )
                                ) {
                                    Text(
                                        text = category,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    // 3. Feedback Message Input
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "3. Your Message / Details *",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E8F0)
                        )
                        OutlinedTextField(
                            value = message,
                            onValueChange = {
                                message = it
                                errorMessage = null
                            },
                            placeholder = {
                                Text(
                                    text = "Describe your experience, bug details, salon name, or feature idea...",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            },
                            minLines = 4,
                            maxLines = 6,
                            colors = fieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("feedback_message_input")
                        )
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 4. Submit Feedback Button
                    Button(
                        onClick = {
                            val trimmed = message.trim()
                            if (trimmed.length < 5) {
                                errorMessage = "Please write at least a brief message (5+ characters) before submitting."
                            } else {
                                isSubmitting = true
                                errorMessage = null
                                onSubmitFeedback(selectedRating, selectedCategory, trimmed) { success, msg ->
                                    isSubmitting = false
                                    if (success) {
                                        submissionSuccess = true
                                    } else {
                                        errorMessage = msg
                                    }
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("submit_feedback_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Saving to Cloud Firestore...",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Submit Feedback",
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

/**
 * 3. Dedicated "Privacy Policy" & "Terms and Conditions" Full-Screen Dialog Viewer
 */
@Composable
fun LegalPoliciesViewerDialog(
    initialTab: LegalPolicyTab = LegalPolicyTab.TERMS,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf(initialTab) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(22.dp))
                .testTag("legal_policies_viewer_dialog"),
            color = DarkBackground,
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkCardSurface)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AmberPrimary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (activeTab == LegalPolicyTab.TERMS) Icons.Default.Gavel else Icons.Default.PrivacyTip,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = activeTab.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Official TrimTime Grooming Platform Policies",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_legal_policies_dialog_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Legal Viewer",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                // Tab Switcher (Terms & Conditions vs Privacy Policy)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkCardSurface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LegalPolicyTab.entries.forEach { tab ->
                        val isSelected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AmberPrimary else DarkSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) AmberPrimary else Color(0xFF334155),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { activeTab = tab }
                                .padding(vertical = 10.dp)
                                .testTag("legal_tab_${tab.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Policy Content Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (activeTab == LegalPolicyTab.TERMS) {
                        LegalSectionCard(
                            icon = Icons.Default.Payments,
                            iconTint = GreenSuccess,
                            title = "1. Pay-at-Salon (₹0 Advance) Guarantee",
                            body = "• TrimTime charges ₹0 advance fee when booking a grooming chair.\n" +
                                "• Customers pay 100% of the listed service amount directly to the salon partner (via Cash or UPI) only after the haircut or grooming service is completed.\n" +
                                "• Salons must honor the exact service prices published on their live TrimTime menu."
                        )

                        LegalSectionCard(
                            icon = Icons.Default.Lock,
                            iconTint = AmberPrimary,
                            title = "2. Anti-Fake 4-Digit Service PIN Verification Rules",
                            body = "• Every booking generates a unique 4-digit Service PIN visible on the Customer's 'My Bookings' screen.\n" +
                                "• Customers must share this 4-digit PIN with the barber ONLY after physically arriving at the shop and taking their seat.\n" +
                                "• Salon Partners cannot mark a booking as 'Completed' without entering the customer's authentic 4-digit PIN, preventing fake bookings and queue manipulation."
                        )

                        LegalSectionCard(
                            icon = Icons.Default.Warning,
                            iconTint = Color(0xFFF87171),
                            title = "3. Customer 3-Strike Cancellation & No-Show Policy",
                            body = "• Because salons hold real physical chairs with ₹0 advance payment, customers are expected to arrive on time for their reserved slot.\n" +
                                "• Cancelling confirmed bookings repeatedly or failing to show up counts as a cancellation strike.\n" +
                                "• Accumulating 3 cancellation strikes automatically suspends the account from placing new instant bookings to protect partner barbers from spam."
                        )

                        LegalSectionCard(
                            icon = Icons.Default.Storefront,
                            iconTint = Color(0xFF38BDF8),
                            title = "4. Partner Salon Listing Accuracy & Hygiene Standards",
                            body = "• Registered Salon Partners must maintain accurate shop names, street addresses, working hours, live chair counts, and OpenStreetMap GPS coordinates.\n" +
                                "• Partners must use sanitized razors, fresh blades, clean towels, and hygienic grooming tools for every customer.\n" +
                                "• Misleading listings or repeated customer complaints may result in removal from the TrimTime directory."
                        )
                    } else {
                        LegalSectionCard(
                            icon = Icons.Default.LocationOn,
                            iconTint = Color(0xFF38BDF8),
                            title = "1. GPS Location & OpenStreetMap Privacy",
                            body = "• TrimTime requests device location permission (ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION) strictly to display nearby salons on our free OpenStreetMap view and calculate distance in kilometers.\n" +
                                "• We do NOT track your background location when the app is closed, and we never sell location data to third-party advertisers."
                        )

                        LegalSectionCard(
                            icon = Icons.Default.Shield,
                            iconTint = GreenSuccess,
                            title = "2. Cloud Firestore Data Storage & Isolation",
                            body = "• Account profiles, salon menus, live chair queues, and feedback reports are stored securely in Google Cloud Firestore.\n" +
                                "• Customer bookings are strictly isolated by unique account UID (`customerId`) so no other customer can view your personal appointment history or 4-digit Service PIN."
                        )

                        LegalSectionCard(
                            icon = Icons.Default.VerifiedUser,
                            iconTint = AmberPrimary,
                            title = "3. Contact Information & Communication",
                            body = "• Your name and 10-digit mobile number are shared only with the specific salon where you book an appointment so the barber can coordinate your chair turn.\n" +
                                "• You can update your profile details, notification toggles, or clear cached data anytime from the Customer Profile settings section."
                        )

                        LegalSectionCard(
                            icon = Icons.Default.SupportAgent,
                            iconTint = Color(0xFFA78BFA),
                            title = "4. Grievance Officer & Support Contact",
                            body = "• For privacy questions, account data deletion requests, or support inquiries, contact us anytime:\n" +
                                "  - Helpline / WhatsApp: +91 62000 92115\n" +
                                "  - Official Support Email: support@trimtime.app"
                        )
                    }
                }

                // Bottom Close Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkCardSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("acknowledge_legal_policy_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = "I Understand & Close",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalSectionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = body,
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                lineHeight = 18.sp
            )
        }
    }
}
