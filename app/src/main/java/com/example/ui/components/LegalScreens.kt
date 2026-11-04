package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess

private data class LegalDocumentSection(
    val number: String,
    val title: String,
    val body: String,
    val icon: ImageVector,
    val accentColor: Color
)

private val privacyPolicySections = listOf(
    LegalDocumentSection(
        number = "01",
        title = "Information We Collect",
        body = "When you create an account or book a salon appointment on TrimTime, we collect basic account information including your Full Name, Email Address (via Google Sign-In or Email Authentication), 10-Digit Mobile Number, and booking preferences. Salon Partners additionally provide business details including Shop Name, Street Address, Working Hours, Services Menu, and Shop Photos.",
        icon = Icons.Default.VerifiedUser,
        accentColor = AmberPrimary
    ),
    LegalDocumentSection(
        number = "02",
        title = "Location & GPS Data Usage",
        body = "TrimTime requests foreground device location permissions (ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION) strictly to display nearby salons on our interactive map, calculate accurate travel distances in kilometers, and verify physical salon pin locations during Partner registration. We do NOT track your location in the background when the app is closed, and we never sell location data to third parties.",
        icon = Icons.Default.LocationOn,
        accentColor = Color(0xFF38BDF8)
    ),
    LegalDocumentSection(
        number = "03",
        title = "Bookings & 4-Digit Service PIN Privacy",
        body = "Each salon booking is linked to your unique user ID and protected with a system-generated 4-Digit Service PIN. Your name, selected service, appointment time, and contact phone number are shared only with the specific Salon Partner you book with so they can reserve your chair and coordinate your appointment.",
        icon = Icons.Default.Lock,
        accentColor = GreenSuccess
    ),
    LegalDocumentSection(
        number = "04",
        title = "Data Storage & Security (Google Firebase)",
        body = "All user profiles, salon listings, real-time queue statuses, ratings, and bookings are securely stored using Google Firebase Authentication, Cloud Firestore, and Firebase Cloud Storage with encrypted HTTPS transport and strict document-level security rules.",
        icon = Icons.Default.Shield,
        accentColor = Color(0xFFA78BFA)
    ),
    LegalDocumentSection(
        number = "05",
        title = "User Rights, Data Retention & Account Deletion",
        body = "You have the right to view, edit, or update your personal profile information at any time from the Profile tab. You may also clear local app cache or request complete deletion of your account and associated booking history by contacting our support team.",
        icon = Icons.Default.PrivacyTip,
        accentColor = AmberPrimary
    ),
    LegalDocumentSection(
        number = "06",
        title = "Privacy Grievance & Contact Information",
        body = "If you have any questions regarding this Privacy Policy, data handling practices, or account deletion requests, please contact the TrimTime Privacy & Support Desk:\n• Email: support@trimtime.app\n• Helpline / WhatsApp: +91 62000 92115",
        icon = Icons.Default.SupportAgent,
        accentColor = GreenSuccess
    )
)

private val termsAndConditionsSections = listOf(
    LegalDocumentSection(
        number = "01",
        title = "Acceptance of Terms & Platform Role",
        body = "By accessing or using the TrimTime Salon Booking application, you agree to be bound by these Terms & Conditions. TrimTime operates as a real-time discovery and appointment scheduling platform connecting customers with independent local barbershops, salons, and grooming studios.",
        icon = Icons.Default.Gavel,
        accentColor = AmberPrimary
    ),
    LegalDocumentSection(
        number = "02",
        title = "₹0 Advance & 100% Pay-at-Salon Policy",
        body = "TrimTime does not charge customers any online advance booking fee. Customers pay 100% of the listed service price directly to the Salon Partner (via Cash or UPI) after the grooming service is completed at the salon. Salon Partners must honor the exact service prices listed on their live TrimTime menu.",
        icon = Icons.Default.Payments,
        accentColor = GreenSuccess
    ),
    LegalDocumentSection(
        number = "03",
        title = "4-Digit Service PIN Verification",
        body = "Every confirmed appointment generates a unique 4-Digit Service PIN displayed in the customer's 'My Bookings' screen. Customers must share this PIN with the barber only after arriving at the salon and taking their seat. Salon Partners may only mark a booking as 'Completed' using this authentic PIN.",
        icon = Icons.Default.Lock,
        accentColor = Color(0xFF38BDF8)
    ),
    LegalDocumentSection(
        number = "04",
        title = "Cancellations, Punctuality & 3-Strike No-Show Policy",
        body = "Because Salon Partners reserve physical chairs with ₹0 advance payment, customers are expected to arrive at the salon on time for their booked slot. Repeated last-minute cancellations or no-shows harm local barbers. Accounts accumulating three (3) no-shows or abusive cancellations may be temporarily or permanently restricted from placing instant bookings.",
        icon = Icons.Default.Warning,
        accentColor = Color(0xFFF87171)
    ),
    LegalDocumentSection(
        number = "05",
        title = "Salon Partner Obligations & Hygiene Standards",
        body = "Registered Salon Partners agree to maintain accurate shop hours, real-time chair availability, verified GPS coordinates, and transparent service pricing. Partners are solely responsible for maintaining clean, hygienic tools, sanitized blades, and safe grooming services for all customers.",
        icon = Icons.Default.Storefront,
        accentColor = Color(0xFFA78BFA)
    ),
    LegalDocumentSection(
        number = "06",
        title = "Reviews, Conduct & Account Termination",
        body = "Customers may submit honest 1-to-5 star ratings after completing a verified booking. Fraudulent bookings, abusive behavior toward salon staff or customers, or manipulation of ratings/queues will result in immediate account suspension by TrimTime Admin.",
        icon = Icons.Default.Description,
        accentColor = AmberPrimary
    )
)

/**
 * Mandatory Full-Screen Privacy Policy Screen for Google Play Store Compliance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("privacy_policy_screen"),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkCardSurface,
                    titleContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("privacy_policy_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(GreenSuccess.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Privacy Policy",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "TrimTime Salon Booking App • Play Store Compliance",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                LegalHeaderBanner(
                    badgeText = "OFFICIAL PRIVACY POLICY",
                    headline = "Your Data & Location Privacy at TrimTime",
                    summary = "This Privacy Policy explains how TrimTime collects, uses, and safeguards your personal data, GPS coordinates, and salon appointment history when you use our mobile application.",
                    accentColor = GreenSuccess
                )
            }

            items(privacyPolicySections) { section ->
                LegalSectionDetailCard(section = section)
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("privacy_policy_done_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPrimary,
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
                        text = "Back",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Mandatory Full-Screen Terms & Conditions Screen for Google Play Store Compliance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsAndConditionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("terms_conditions_screen"),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkCardSurface,
                    titleContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("terms_conditions_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(AmberPrimary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gavel,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Terms & Conditions",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "TrimTime Salon Booking App • User & Partner Agreement",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                LegalHeaderBanner(
                    badgeText = "TERMS OF SERVICE",
                    headline = "TrimTime Salon Booking Rules & Agreement",
                    summary = "Please read these Terms & Conditions carefully before booking a grooming slot or managing a partner salon on TrimTime.",
                    accentColor = AmberPrimary
                )
            }

            items(termsAndConditionsSections) { section ->
                LegalSectionDetailCard(section = section)
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("terms_conditions_done_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPrimary,
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
                        text = "Back",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun LegalHeaderBanner(
    badgeText: String,
    headline: String,
    summary: String,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = badgeText,
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White
            )
            Text(
                text = summary,
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun LegalSectionDetailCard(
    section: LegalDocumentSection
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(section.accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        tint = section.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SECTION ${section.number}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = section.accentColor,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = section.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
            Text(
                text = section.body,
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                lineHeight = 18.sp
            )
        }
    }
}
