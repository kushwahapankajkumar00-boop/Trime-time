package com.example.ui.customer

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Booking
import com.example.model.Salon
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedAlert

/**
 * Frictionless, Blinkit-style Customer Home Screen in Jetpack Compose:
 * 1. Auto-Location Header: GPS location (e.g., "Patna, Bihar 📍") with profile avatar icon.
 * 2. Quick Re-book Section: Top horizontal card showing user's past booking with 1-tap "Book Again".
 * 3. Smart Filter Chips: "Open Now", "Under ₹150", "Top Rated", "A/C Salons".
 * 4. Nearby Salons Feed (Geolocation Discovery): Vertical list of salon cards.
 * 5. Salon Card UI: Salon Name, Distance (e.g. "1.2 km"), Price starting from (e.g. "Starts at ₹100"),
 *    and highly visible Live Wait Time badge (e.g. "🟢 No Wait" or "🔴 15 Min Wait").
 * 6. Booking Action: Clear "View & Book" button on each card.
 */
@Composable
fun CustomerHomeScreen(
    salons: List<Salon>,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onSalonSelected: (Salon) -> Unit,
    pastBooking: Booking? = null,
    onRebook: (Booking) -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Smart Filter state (Blinkit style)
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    // Filter salons according to selected Smart Filter
    val displayedSalons = remember(salons, selectedFilter) {
        when (selectedFilter) {
            "Open Now" -> salons.filter { it.isOpen }
            "Under ₹150" -> salons.filter { salon ->
                val minPrice = salon.services.minOfOrNull { it.price } ?: 100
                minPrice <= 150
            }
            "Top Rated" -> salons.filter { it.rating >= 4.7 }
            "A/C Salons" -> salons.filter { salon ->
                salon.tagline.contains("AC", ignoreCase = true) ||
                salon.tagline.contains("Luxury", ignoreCase = true) ||
                salon.name.contains("Unisex", ignoreCase = true) ||
                salon.rating >= 4.7
            }
            else -> salons
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("salons_list"),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. Auto-Location Header: "Patna, Bihar 📍" with Profile Icon
        item {
            BlinkitAutoLocationHeader(
                currentLocation = "Patna, Bihar",
                subLocation = "Kankarbagh Main Rd • 1.2 km radius",
                onProfileClick = onProfileClick
            )
        }

        // 2. Search Bar
        item {
            BlinkitSearchBar(
                searchQuery = searchQuery,
                onSearchChanged = onSearchChanged
            )
        }

        // 3. Quick Re-book Section (Past booking with 1-tap "Book Again")
        item {
            QuickRebookSection(
                pastBooking = pastBooking,
                salons = salons,
                onRebookClick = { booking ->
                    onRebook(booking)
                    val matchingSalon = salons.find { it.id == booking.salonId || it.name.equals(booking.salonName, ignoreCase = true) }
                    if (matchingSalon != null) {
                        onSalonSelected(matchingSalon)
                    }
                }
            )
        }

        // 4. Smart Filter Chips ("Open Now", "Under ₹150", "Top Rated", "A/C Salons")
        item {
            SmartFilterChipsRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { filter ->
                    selectedFilter = if (selectedFilter == filter) null else filter
                }
            )
        }

        // 5. Section Header with Live Counter
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GreenSuccess)
                    )
                    Text(
                        text = "NEARBY SALONS & INSTANT CHAIRS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "${displayedSalons.size} nearby",
                    fontSize = 12.sp,
                    color = AmberPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 6. Nearby Salons Feed
        if (displayedSalons.isEmpty()) {
            item {
                EmptySalonsPlaceholder(
                    onClearFilters = {
                        selectedFilter = null
                        onSearchChanged("")
                    }
                )
            }
        } else {
            items(displayedSalons, key = { it.id }) { salon ->
                BlinkitSalonCard(
                    salon = salon,
                    onBookClick = { onSalonSelected(salon) }
                )
            }
        }
    }
}

/**
 * 1. Auto-Location Header:
 * Displays dummy GPS location (e.g. "Patna, Bihar 📍") with delivery badge & profile icon
 */
@Composable
fun BlinkitAutoLocationHeader(
    currentLocation: String,
    subLocation: String,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF0F172A)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Location Pin + Details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF451A03), Color(0xFF1E293B)))
                        )
                        .border(1.dp, AmberPrimary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "GPS Location",
                        tint = AmberPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentLocation,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "📍",
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Change Location",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = subLocation,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Profile Icon button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF334155), CircleShape)
                    .clickable { onProfileClick() }
                    .testTag("home_profile_icon_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User Profile",
                    tint = AmberPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Blinkit Search Bar for grooming services
 */
@Composable
fun BlinkitSearchBar(
    searchQuery: String,
    onSearchChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("search_salons_input"),
        placeholder = {
            Text(
                text = "Search 'Fade cut', 'Beard trim', 'A/C Parlour'...",
                color = Color(0xFF64748B),
                fontSize = 13.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = AmberPrimary,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchChanged("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AmberPrimary,
            unfocusedBorderColor = Color(0xFF334155),
            focusedContainerColor = DarkSurface,
            unfocusedContainerColor = DarkSurface,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        shape = RoundedCornerShape(14.dp)
    )
}

/**
 * 2. Quick Re-book Section:
 * A top horizontal banner/card showing the user's past booking with a 1-tap "Book Again" button.
 */
@Composable
fun QuickRebookSection(
    pastBooking: Booking?,
    salons: List<Salon>,
    onRebookClick: (Booking) -> Unit
) {
    // If user has a real past booking, show it; otherwise show a helpful quick re-book shortcut card
    val bookingToShow = pastBooking ?: Booking(
        id = "past_demo_1",
        salonName = "Express Groomers (Quick Slot)",
        salonId = "salon_express_trim",
        service = "Classic Haircut & Beard",
        servicePrice = 140,
        timeSlot = "Today, 11:30 AM",
        status = "completed"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("quick_rebook_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(AmberPrimary.copy(alpha = 0.5f), Color(0xFF1E293B))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Past Booking",
                        tint = AmberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "QUICK RE-BOOK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AmberPrimary,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF064E3B))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "⚡ 1-Tap Booking",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenSuccess
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bookingToShow.service,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "at ${bookingToShow.salonName}",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "₹${bookingToShow.servicePrice} • Pay at Salon",
                        fontSize = 11.sp,
                        color = GreenSuccess,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 1-Tap "Book Again" Button
                Button(
                    onClick = { onRebookClick(bookingToShow) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPrimary,
                        contentColor = Color(0xFF1E1402)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("book_again_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Book Again",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 3. Smart Filter Chips:
 * A row of clickable chips: "Open Now", "Under ₹150", "Top Rated", "A/C Salons".
 */
@Composable
fun SmartFilterChipsRow(
    selectedFilter: String?,
    onFilterSelected: (String) -> Unit
) {
    val filters = listOf(
        FilterChipItem("Open Now", Icons.Default.AccessTime),
        FilterChipItem("Under ₹150", Icons.Default.CurrencyRupee),
        FilterChipItem("Top Rated", Icons.Default.Star),
        FilterChipItem("A/C Salons", Icons.Default.AcUnit)
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { chip ->
            val isSelected = selectedFilter == chip.label
            val chipBg = if (isSelected) AmberPrimary else DarkSurface
            val contentColor = if (isSelected) Color(0xFF1E1402) else Color(0xFFE2E8F0)
            val borderColor = if (isSelected) AmberSecondary else Color(0xFF334155)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(chipBg)
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                    .clickable { onFilterSelected(chip.label) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("filter_chip_${chip.label.replace(" ", "_")}"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = chip.icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = chip.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

data class FilterChipItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/**
 * 4. Nearby Salons Feed & 5. Salon Card UI:
 * Displays Salon Name, Distance (e.g., "1.2 km"), Price starting from (e.g., "Starts at ₹100"),
 * and a highly visible "Live Wait Time" badge (e.g., "🟢 No Wait" or "🔴 15 Min Wait").
 * 6. Booking Action: A clear "View & Book" button.
 */
@Composable
fun BlinkitSalonCard(
    salon: Salon,
    onBookClick: () -> Unit
) {
    val minPrice = salon.services.minOfOrNull { it.price } ?: 80
    val isBusy = salon.isBusy
    val isNoWait = salon.isFree && salon.nextSlotMinutes <= 5

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .clickable { onBookClick() }
            .testTag("salon_card_${salon.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Salon Visual Banner with Live Wait Time Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF1E1B4B),
                                Color(0xFF31103F),
                                Color(0xFF0F172A)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Highly Visible Live Wait Time & Real-time Busy/Free Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isBusy) Color(0xFF451A03) else Color(0xFF064E3B)
                                    )
                                    .border(
                                        1.dp,
                                        if (isBusy) Color(0xFFF97316) else GreenSuccess,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("live_wait_badge_${salon.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (isBusy) "🔴" else "🟢",
                                        fontSize = 9.sp
                                    )
                                    Text(
                                        text = if (isBusy) "Barber is Busy (Approx 15-20 Min Wait)" else "Available Now",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isBusy) Color(0xFFFDBA74) else GreenSuccess
                                    )
                                }
                            }

                            if (salon.isOpen) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF0F2D1F))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "OPEN NOW",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF34D399)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = salon.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (salon.tagline.isNotBlank()) salon.tagline else "Verified Grooming Partner",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Salon Scissors Badge Icon
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0x33F59E0B))
                            .border(1.dp, AmberPrimary.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = "Salon",
                            tint = AmberPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Card Body: Distance, Rating, Price & View & Book Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rating + Distance Tags
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Rating
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF064E3B))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rating",
                                tint = AmberPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${salon.rating}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Distance (e.g., "1.2 km")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurface)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "Distance",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = salon.distance,
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Price starting from (e.g. "Starts at ₹100")
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Starts at ₹$minPrice",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AmberPrimary
                        )
                        Text(
                            text = "Pay at salon",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Text(
                    text = salon.location,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Top services preview chips
                if (salon.services.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        salon.services.take(3).forEach { service ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurface)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${service.name} • ₹${service.price}",
                                    fontSize = 10.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Bottom Action: Pay at Salon Guarantee + "View & Book" / "Join Waitlist"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = "Pay at Salon",
                            tint = GreenSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "₹0 Advance • Pay in Cash/UPI",
                            fontSize = 11.sp,
                            color = Color(0xFFA7F3D0),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // 6. Booking Action: "View & Book" or "Join Waitlist" if busy
                    Button(
                        onClick = onBookClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBusy) Color(0xFFEA580C) else AmberPrimary,
                            contentColor = if (isBusy) Color.White else Color(0xFF1E1402)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                        modifier = Modifier.testTag("view_and_book_button_${salon.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isBusy) "Join Waitlist" else "View & Book",
                                fontSize = 13.sp,
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
fun EmptySalonsPlaceholder(
    onClearFilters: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCut,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = "No salons match your selected filters.",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp
            )
            Button(
                onClick = onClearFilters,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Reset Filters", color = AmberPrimary, fontSize = 12.sp)
            }
        }
    }
}
