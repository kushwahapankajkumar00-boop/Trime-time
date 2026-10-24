package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkSurface
import com.example.viewmodel.CustomerNavTab

@Composable
fun AppBottomNav(
    currentTab: CustomerNavTab,
    pendingBookingsCount: Int,
    onTabSelected: (CustomerNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.testTag("customer_bottom_navigation"),
        containerColor = DarkSurface,
        tonalElevation = 8.dp
    ) {
        // Tab 1: Salons
        NavigationBarItem(
            selected = currentTab == CustomerNavTab.SALONS,
            onClick = { onTabSelected(CustomerNavTab.SALONS) },
            icon = {
                Icon(
                    imageVector = if (currentTab == CustomerNavTab.SALONS) Icons.Filled.ContentCut else Icons.Outlined.ContentCut,
                    contentDescription = "Salons",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = "Salons",
                    fontSize = 12.sp,
                    fontWeight = if (currentTab == CustomerNavTab.SALONS) FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF1E1402),
                selectedTextColor = AmberPrimary,
                indicatorColor = AmberPrimary,
                unselectedIconColor = Color(0xFF94A3B8),
                unselectedTextColor = Color(0xFF94A3B8)
            ),
            modifier = Modifier.testTag("nav_tab_salons")
        )

        // Tab 2: My Bookings
        NavigationBarItem(
            selected = currentTab == CustomerNavTab.MY_BOOKINGS,
            onClick = { onTabSelected(CustomerNavTab.MY_BOOKINGS) },
            icon = {
                BadgedBox(
                    badge = {
                        if (pendingBookingsCount > 0) {
                            Badge(
                                containerColor = AmberPrimary,
                                contentColor = Color(0xFF1E1402)
                            ) {
                                Text(
                                    text = pendingBookingsCount.toString(),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (currentTab == CustomerNavTab.MY_BOOKINGS) Icons.Filled.CalendarToday else Icons.Outlined.CalendarToday,
                        contentDescription = "My Bookings",
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            label = {
                Text(
                    text = "My Bookings",
                    fontSize = 12.sp,
                    fontWeight = if (currentTab == CustomerNavTab.MY_BOOKINGS) FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF1E1402),
                selectedTextColor = AmberPrimary,
                indicatorColor = AmberPrimary,
                unselectedIconColor = Color(0xFF94A3B8),
                unselectedTextColor = Color(0xFF94A3B8)
            ),
            modifier = Modifier.testTag("nav_tab_my_bookings")
        )

        // Tab 3: Profile / Account
        NavigationBarItem(
            selected = currentTab == CustomerNavTab.PROFILE,
            onClick = { onTabSelected(CustomerNavTab.PROFILE) },
            icon = {
                Icon(
                    imageVector = if (currentTab == CustomerNavTab.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = "Profile",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = "Profile",
                    fontSize = 12.sp,
                    fontWeight = if (currentTab == CustomerNavTab.PROFILE) FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF1E1402),
                selectedTextColor = AmberPrimary,
                indicatorColor = AmberPrimary,
                unselectedIconColor = Color(0xFF94A3B8),
                unselectedTextColor = Color(0xFF94A3B8)
            ),
            modifier = Modifier.testTag("nav_tab_profile")
        )
    }
}
