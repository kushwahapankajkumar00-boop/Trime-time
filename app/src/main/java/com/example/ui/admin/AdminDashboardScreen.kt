package com.example.ui.admin

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HolidayVillage
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FeedbackSubmission
import com.example.model.Salon
import com.example.model.UserProfile
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AdminCrimson = Color(0xFFDC2626)
private val AdminCrimsonDark = Color(0xFF991B1B)
private val TrimTimeGold = Color(0xFFF59E0B)
private val StatusGreen = Color(0xFF10B981)
private val AlertRed = Color(0xFFEF4444)
private val WarningAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardScreen(
    salons: List<Salon>,
    users: List<UserProfile>,
    supportTickets: List<FeedbackSubmission>,
    onBack: () -> Unit,
    onUpdateSalonApproval: (salonId: String, approvalStatus: String, isSuspended: Boolean) -> Unit,
    onUpdateSalonCategory: (salonId: String, categoryTag: String) -> Unit,
    onUpdateUserBlockStatus: (userId: String, isBlocked: Boolean) -> Unit,
    onResolveSupportTicket: (ticketId: String, isResolved: Boolean) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val openTicketsCount = remember(supportTickets) {
        supportTickets.count { !it.isResolved && !it.status.equals("RESOLVED", ignoreCase = true) }
    }
    val blockedUsersCount = remember(users) {
        users.count { it.isEffectivelyBlocked }
    }
    val suspendedSalonsCount = remember(salons) {
        salons.count { it.isSuspended || it.approvalStatus.equals("REJECTED", ignoreCase = true) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF18181B), Color(0xFF271518), Color(0xFF18181B))
                        )
                    )
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("admin_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Profile",
                                tint = Color.White
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = AdminCrimson.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AdminCrimson.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = "👑 SUPER ADMIN",
                                    color = Color(0xFFFCA5A5),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "Central Control",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )

                // Platform KPI Summary Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminMetricPill(
                        modifier = Modifier.weight(1f),
                        label = "Salons",
                        value = "${salons.size}",
                        subValue = if (suspendedSalonsCount > 0) "$suspendedSalonsCount flagged" else "All active",
                        accentColor = TrimTimeGold
                    )
                    AdminMetricPill(
                        modifier = Modifier.weight(1f),
                        label = "Users",
                        value = "${users.size}",
                        subValue = if (blockedUsersCount > 0) "$blockedUsersCount blocked" else "0 blocked",
                        accentColor = if (blockedUsersCount > 0) AlertRed else StatusGreen
                    )
                    AdminMetricPill(
                        modifier = Modifier.weight(1f),
                        label = "Help Desk",
                        value = "${supportTickets.size}",
                        subValue = "$openTicketsCount open",
                        accentColor = if (openTicketsCount > 0) WarningAmber else StatusGreen
                    )
                }

                // 3 Core Management Tabs
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    divider = {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        modifier = Modifier.testTag("admin_tab_salons"),
                        text = {
                            Text(
                                text = "Salons (${salons.size})",
                                fontWeight = if (selectedTabIndex == 0) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = "Salon Verification & Tagging",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        modifier = Modifier.testTag("admin_tab_users"),
                        text = {
                            Text(
                                text = "Users (${users.size})",
                                fontWeight = if (selectedTabIndex == 1) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "User & Spam Management",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        modifier = Modifier.testTag("admin_tab_support"),
                        text = {
                            Text(
                                text = "Support ($openTicketsCount)",
                                fontWeight = if (selectedTabIndex == 2) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = "Support Help Desk",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Filter Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    val hint = when (selectedTabIndex) {
                        0 -> "Search salons by name, owner, phone, or tag..."
                        1 -> "Search users by name, mobile, or email..."
                        else -> "Search tickets by user, phone, or message..."
                    }
                    Text(hint, fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TrimTimeGold
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("admin_search_input")
            )

            when (selectedTabIndex) {
                0 -> AdminSalonsVerificationTab(
                    salons = salons,
                    searchQuery = searchQuery,
                    onUpdateSalonApproval = onUpdateSalonApproval,
                    onUpdateSalonCategory = onUpdateSalonCategory,
                    onCallPhone = { phone ->
                        val clean = phone.filter { it.isDigit() || it == '+' }
                        if (clean.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
                        }
                    }
                )
                1 -> AdminUsersSpamTab(
                    users = users,
                    searchQuery = searchQuery,
                    onUpdateUserBlockStatus = onUpdateUserBlockStatus,
                    onCallPhone = { phone ->
                        val clean = phone.filter { it.isDigit() || it == '+' }
                        if (clean.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
                        }
                    }
                )
                2 -> AdminSupportHelpDeskTab(
                    tickets = supportTickets,
                    searchQuery = searchQuery,
                    onResolveSupportTicket = onResolveSupportTicket,
                    onCallUser = { phone ->
                        val clean = phone.filter { it.isDigit() || it == '+' }
                        if (clean.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AdminMetricPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subValue: String,
    accentColor: Color
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label.uppercase(),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = accentColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subValue,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminSalonsVerificationTab(
    salons: List<Salon>,
    searchQuery: String,
    onUpdateSalonApproval: (salonId: String, approvalStatus: String, isSuspended: Boolean) -> Unit,
    onUpdateSalonCategory: (salonId: String, categoryTag: String) -> Unit,
    onCallPhone: (String) -> Unit
) {
    val categoryTags = remember { listOf("Premium", "Standard", "Rural/Local") }
    val filteredSalons = remember(salons, searchQuery) {
        if (searchQuery.isBlank()) {
            salons
        } else {
            salons.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.ownerName.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery, ignoreCase = true) ||
                    it.shopAddress.contains(searchQuery, ignoreCase = true) ||
                    it.adminCategoryTag.contains(searchQuery, ignoreCase = true) ||
                    it.approvalStatus.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    if (filteredSalons.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = "No Salons",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No registered salons match your filter",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Newly registered partner salons will appear here in real time for approval & tagging.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(filteredSalons, key = { it.id }) { salon ->
            val isApproved = salon.approvalStatus.equals("APPROVED", ignoreCase = true) && !salon.isSuspended
            val isRejected = salon.approvalStatus.equals("REJECTED", ignoreCase = true)
            val isSuspended = salon.isSuspended || salon.approvalStatus.equals("SUSPENDED", ignoreCase = true)
            val statusColor = when {
                isSuspended -> AlertRed
                isRejected -> AlertRed
                isApproved -> StatusGreen
                else -> WarningAmber
            }
            val statusText = when {
                isSuspended -> "SUSPENDED"
                isRejected -> "REJECTED"
                isApproved -> "APPROVED"
                else -> "PENDING REVIEW"
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_salon_card_${salon.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isSuspended || isRejected) AlertRed.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Row: Salon Name, Rating & Status Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = salon.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Owner: ${salon.ownerName.ifBlank { "Partner" }} • ${salon.phone.ifBlank { "No phone" }}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                            Text(
                                text = salon.shopAddress.ifBlank { salon.location },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                color = statusColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = statusText,
                                    color = statusColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // Average Rating Pill
                            Surface(
                                color = if (salon.rating < 3.5) AlertRed.copy(alpha = 0.15f) else TrimTimeGold.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Average Rating",
                                        tint = if (salon.rating < 3.5) AlertRed else TrimTimeGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f (%d)", salon.rating, salon.reviewCount),
                                        color = if (salon.rating < 3.5) AlertRed else TrimTimeGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Category Tagging System: "Premium", "Standard", or "Rural/Local"
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Assign Salon Category Tag:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categoryTags.forEach { tag ->
                                val isSelected = salon.adminCategoryTag.equals(tag, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onUpdateSalonCategory(salon.id, tag) },
                                    modifier = Modifier.testTag("admin_salon_tag_${salon.id}_$tag"),
                                    label = {
                                        Text(
                                            text = tag,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (tag) {
                                                "Premium" -> Icons.Default.WorkspacePremium
                                                "Rural/Local" -> Icons.Default.HolidayVillage
                                                else -> Icons.Default.Verified
                                            },
                                            contentDescription = tag,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TrimTimeGold.copy(alpha = 0.22f),
                                        selectedLabelColor = TrimTimeGold,
                                        selectedLeadingIconColor = TrimTimeGold
                                    )
                                )
                            }
                        }
                    }

                    // Verification & Suspension Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Approve Button
                        Button(
                            onClick = {
                                onUpdateSalonApproval(salon.id, "APPROVED", false)
                            },
                            enabled = !isApproved || isSuspended,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("admin_approve_salon_${salon.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusGreen,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Approve Salon",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Approve",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                        // Reject Button
                        OutlinedButton(
                            onClick = {
                                onUpdateSalonApproval(salon.id, "REJECTED", false)
                            },
                            enabled = !isRejected,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("admin_reject_salon_${salon.id}"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, WarningAmber),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = WarningAmber
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Reject Salon",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Reject",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                        // Suspend / Reactivate Button
                        Button(
                            onClick = {
                                if (isSuspended) {
                                    onUpdateSalonApproval(salon.id, "APPROVED", false)
                                } else {
                                    onUpdateSalonApproval(salon.id, "SUSPENDED", true)
                                }
                            },
                            modifier = Modifier
                                .weight(1.1f)
                                .height(42.dp)
                                .testTag("admin_suspend_salon_${salon.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSuspended) TrimTimeGold else AlertRed,
                                contentColor = if (isSuspended) Color.Black else Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isSuspended) Icons.Default.Restore else Icons.Default.Block,
                                contentDescription = if (isSuspended) "Reactivate Salon" else "Suspend Salon",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSuspended) "Unsuspend" else "Suspend",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    if (salon.phone.isNotBlank()) {
                        TextButton(
                            onClick = { onCallPhone(salon.phone) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call Partner",
                                tint = TrimTimeGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Call Partner (${salon.phone})",
                                color = TrimTimeGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUsersSpamTab(
    users: List<UserProfile>,
    searchQuery: String,
    onUpdateUserBlockStatus: (userId: String, isBlocked: Boolean) -> Unit,
    onCallPhone: (String) -> Unit
) {
    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) {
            users
        } else {
            users.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery, ignoreCase = true) ||
                    it.email.contains(searchQuery, ignoreCase = true) ||
                    it.role.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    if (filteredUsers.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = "No Users",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No users found",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(filteredUsers, key = { it.id }) { user ->
            val isBlocked = user.isEffectivelyBlocked
            val strikes = user.effectiveCancellationStrikes

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_user_card_${user.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isBlocked) AlertRed.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isBlocked) AlertRed.copy(alpha = 0.18f)
                                        else TrimTimeGold.copy(alpha = 0.18f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.name.take(1).uppercase().ifBlank { "U" },
                                    color = if (isBlocked) AlertRed else TrimTimeGold,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = user.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (user.isPhoneVerified) {
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "Verified",
                                            tint = Color(0xFF3B82F6),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    if (user.isAdmin) {
                                        Surface(
                                            color = AdminCrimson.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "ADMIN",
                                                color = Color(0xFFFCA5A5),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = user.phone.ifBlank { user.email.ifBlank { "No contact info" } },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    modifier = Modifier.clickable(enabled = user.phone.isNotBlank()) {
                                        onCallPhone(user.phone)
                                    }
                                )
                                if (user.email.isNotBlank() && user.phone.isNotBlank()) {
                                    Text(
                                        text = user.email,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Direct Block / Unblock Switch
                        Column(horizontalAlignment = Alignment.End) {
                            Switch(
                                checked = isBlocked,
                                onCheckedChange = { shouldBlock ->
                                    onUpdateUserBlockStatus(user.id, shouldBlock)
                                },
                                modifier = Modifier.testTag("admin_user_block_switch_${user.id}"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AlertRed,
                                    uncheckedThumbColor = StatusGreen,
                                    uncheckedTrackColor = StatusGreen.copy(alpha = 0.2f)
                                )
                            )
                            Text(
                                text = if (isBlocked) "BLOCKED" else "ACTIVE",
                                color = if (isBlocked) AlertRed else StatusGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Strikes Badge + Block/Unblock Action Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = when {
                                strikes >= 3 -> AlertRed.copy(alpha = 0.15f)
                                strikes > 0 -> WarningAmber.copy(alpha = 0.15f)
                                else -> StatusGreen.copy(alpha = 0.12f)
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (strikes >= 3) Icons.Default.Warning else Icons.Default.Shield,
                                    contentDescription = "Cancellation Strikes",
                                    tint = when {
                                        strikes >= 3 -> AlertRed
                                        strikes > 0 -> WarningAmber
                                        else -> StatusGreen
                                    },
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Cancellation Strikes: $strikes / 3",
                                    color = when {
                                        strikes >= 3 -> AlertRed
                                        strikes > 0 -> WarningAmber
                                        else -> StatusGreen
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onUpdateUserBlockStatus(user.id, !isBlocked)
                            },
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("admin_user_block_button_${user.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBlocked) StatusGreen else AlertRed,
                                contentColor = if (isBlocked) Color.Black else Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Icon(
                                imageVector = if (isBlocked) Icons.Default.LockOpen else Icons.Default.Block,
                                contentDescription = if (isBlocked) "Unblock User" else "Block User",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBlocked) "Unblock User" else "Block User",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminSupportHelpDeskTab(
    tickets: List<FeedbackSubmission>,
    searchQuery: String,
    onResolveSupportTicket: (ticketId: String, isResolved: Boolean) -> Unit,
    onCallUser: (String) -> Unit
) {
    val dateFormatter = remember {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    }

    val filteredTickets = remember(tickets, searchQuery) {
        if (searchQuery.isBlank()) {
            tickets
        } else {
            tickets.filter {
                it.userName.contains(searchQuery, ignoreCase = true) ||
                    it.userPhone.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true) ||
                    it.message.contains(searchQuery, ignoreCase = true) ||
                    it.status.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    if (filteredTickets.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = "No Support Tickets",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No support or feedback tickets yet",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Customer & Partner help desk messages from the feedback collection will appear here live.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(filteredTickets, key = { it.id }) { ticket ->
            val isResolved = ticket.isResolved || ticket.status.equals("RESOLVED", ignoreCase = true)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_ticket_card_${ticket.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isResolved) StatusGreen.copy(alpha = 0.4f)
                    else WarningAmber.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Top Row: User Name, Phone, Role & Status Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = ticket.userName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Surface(
                                    color = TrimTimeGold.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = ticket.userRole.uppercase(),
                                        color = TrimTimeGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "📞 ${ticket.userPhone.ifBlank { "Phone not provided" }} • ${ticket.category}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (ticket.timestamp > 0L) {
                                Text(
                                    text = dateFormatter.format(Date(ticket.timestamp)),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                color = if (isResolved) StatusGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isResolved) "✓ RESOLVED" else "OPEN TICKET",
                                    color = if (isResolved) StatusGreen else WarningAmber,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // Star Rating Row
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                repeat(5) { idx ->
                                    Icon(
                                        imageVector = if (idx < ticket.rating) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Rating ${ticket.rating}",
                                        tint = if (idx < ticket.rating) TrimTimeGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Ticket Message Body
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = ticket.message,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    // Action Buttons Row: Call User & Mark Resolved
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onCallUser(ticket.userPhone) },
                            enabled = ticket.userPhone.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("admin_call_ticket_user_${ticket.id}"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (ticket.userPhone.isNotBlank()) TrimTimeGold else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call User",
                                tint = if (ticket.userPhone.isNotBlank()) TrimTimeGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Call User",
                                color = if (ticket.userPhone.isNotBlank()) TrimTimeGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                onResolveSupportTicket(ticket.id, !isResolved)
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(42.dp)
                                .testTag("admin_resolve_ticket_${ticket.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isResolved) MaterialTheme.colorScheme.surfaceVariant else StatusGreen,
                                contentColor = if (isResolved) MaterialTheme.colorScheme.onSurface else Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = if (isResolved) Icons.Default.Refresh else Icons.Default.CheckCircle,
                                contentDescription = if (isResolved) "Reopen Ticket" else "Mark Resolved",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isResolved) "Reopen Ticket" else "Mark Resolved",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
