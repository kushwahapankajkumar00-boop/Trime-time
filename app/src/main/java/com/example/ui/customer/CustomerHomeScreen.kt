package com.example.ui.customer

import android.Manifest
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.Booking
import com.example.model.Salon
import com.example.ui.components.CustomerSalonsOsmMapSection
import com.example.ui.components.PartnerLocationPickerOsmMap
import com.example.ui.components.calculateDistanceKm
import com.example.ui.components.fetchDeviceGpsLocation
import com.example.ui.components.hasLocationPermission
import com.example.ui.components.initOsmDroidConfig
import com.example.ui.components.launchGeoDirections
import com.example.ui.components.requestFreshHighAccuracyLocation
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.RedAlert
import com.example.util.SalonImageUtils
import org.osmdroid.config.Configuration

/**
 * Frictionless, Blinkit-style Customer Home Screen in Jetpack Compose:
 * 1. Interactive Live GPS Location Header: Auto-fetches real GPS coordinates and reverse-geocodes
 *    into actual City/Town & Road/Area name; tapping opens the "Change / Detect Location" bottom sheet.
 * 2. Quick Re-book Section: Top horizontal card showing user's past booking with 1-tap "Book Again".
 * 3. Smart Filter Chips: "Open Now", "Under ₹150", "Top Rated", "A/C Salons".
 * 4. Nearby Salons Feed (Geolocation Discovery): Vertical list of salon cards sorted by nearest distance first.
 * 5. Salon Card UI: Salon Name, Live Haversine Distance (e.g. "0.1 km"), Price starting from,
 *    and highly visible Live Wait Time badge.
 * 6. Booking Action: Clear "View & Book" button on each card.
 */
@Composable
fun CustomerHomeScreen(
    salons: List<Salon>,
    currentUserId: String = "",
    favoriteSalonIds: Set<String> = emptySet(),
    onToggleFavorite: (Salon) -> Unit = {},
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onSalonSelected: (Salon) -> Unit,
    userLocationTitle: String = "Current Location",
    userLocationSubtitle: String = "Tap to detect live GPS location",
    userLatitude: Double = 25.5941,
    userLongitude: Double = 85.1376,
    isLiveGpsActive: Boolean = false,
    onDetectGpsLocation: (Double, Double, Boolean) -> Unit = { _, _, _ -> },
    onManualLocationSelected: (String, Double?, Double?) -> Unit = { _, _, _ -> },
    pastBooking: Booking? = null,
    onRebook: (Booking) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onOpenMyBookings: () -> Unit = {},
    onRateSalon: (Salon, Int, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Rating dialog state
    var salonToRate by remember { mutableStateOf<Salon?>(null) }

    // Initialize OpenStreetMap Configuration (userAgentValue + internal tile cache) before MapView renders
    remember(context) {
        Configuration.getInstance().userAgentValue = context.packageName
        initOsmDroidConfig(context)
        true
    }

    // Smart Filter state (Blinkit style)
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    // My Favorites Tab state ("All Salons" vs "My Favorites")
    var showFavoritesTab by remember { mutableStateOf(false) }
    // Toggle between "List View" and "Live Map View" (OpenStreetMap)
    var isMapView by remember { mutableStateOf(false) }
    // Interactive "Change / Detect Location" Bottom Sheet state
    var showLocationSheet by remember { mutableStateOf(false) }
    var isDetectingGps by remember { mutableStateOf(false) }

    val gpsSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            isDetectingGps = true
            requestFreshHighAccuracyLocation(
                context = context,
                fallbackLat = userLatitude,
                fallbackLng = userLongitude
            ) { lat, lng, isLive ->
                isDetectingGps = false
                if (isLive && lat != 0.0 && lng != 0.0) {
                    onDetectGpsLocation(lat, lng, true)
                    showLocationSheet = false
                }
            }
        } else {
            isDetectingGps = false
            Toast.makeText(
                context,
                "Please turn on GPS Location to detect your live location.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            isDetectingGps = true
            fetchDeviceGpsLocation(
                context = context,
                fallbackLat = userLatitude,
                fallbackLng = userLongitude,
                onResolutionRequired = { intentSenderRequest ->
                    gpsSettingsLauncher.launch(intentSenderRequest)
                }
            ) { lat, lng, isLive ->
                isDetectingGps = false
                if (isLive && lat != 0.0 && lng != 0.0) {
                    onDetectGpsLocation(lat, lng, true)
                }
            }
        } else {
            isDetectingGps = false
            Toast.makeText(
                context,
                "Location permission denied. Tap the top location bar to set your City/Area manually.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 1. Auto-Fetch Real GPS Location as soon as CustomerHomeScreen opens
    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) {
            isDetectingGps = true
            fetchDeviceGpsLocation(
                context = context,
                fallbackLat = userLatitude,
                fallbackLng = userLongitude,
                onResolutionRequired = { intentSenderRequest ->
                    gpsSettingsLauncher.launch(intentSenderRequest)
                }
            ) { lat, lng, isLive ->
                isDetectingGps = false
                if (isLive && lat != 0.0 && lng != 0.0) {
                    onDetectGpsLocation(lat, lng, false)
                }
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val isSalonFavorited: (Salon) -> Boolean = remember(favoriteSalonIds) {
        { salon ->
            salon.id in favoriteSalonIds ||
                (salon.partnerId.isNotBlank() && salon.partnerId in favoriteSalonIds) ||
                (salon.ownerId.isNotBlank() && salon.ownerId in favoriteSalonIds)
        }
    }

    // Filter salons according to selected Smart Filter / My Favorites tab, dynamically recalculate live Haversine distance
    // from (userLatitude, userLongitude), and sort by shortest geographic distance first (nearest salons at the very top)!
    val displayedSalons = remember(salons, selectedFilter, showFavoritesTab, favoriteSalonIds, userLatitude, userLongitude) {
        val withLiveDistances = salons.map { salon ->
            val sLat = salon.latitude
            val sLng = salon.longitude
            if (sLat != null && sLng != null && sLat != 0.0 && sLng != 0.0) {
                val km = calculateDistanceKm(userLatitude, userLongitude, sLat, sLng).coerceAtLeast(0.1)
                salon.copy(distance = String.format(java.util.Locale.US, "%.1f km", km))
            } else {
                salon
            }
        }.sortedWith(
            compareBy<Salon> { salon ->
                val sLat = salon.latitude
                val sLng = salon.longitude
                if (sLat != null && sLng != null && sLat != 0.0 && sLng != 0.0) {
                    com.example.data.TrimTimeRepository.calculateRawDistanceKm(userLatitude, userLongitude, sLat, sLng)
                } else {
                    val parsed = salon.distance.replace("km", "", ignoreCase = true).trim().toDoubleOrNull()
                    if (parsed != null) parsed + 5000.0 else 99999.0
                }
            }.thenBy { it.name.lowercase(java.util.Locale.US) }
        )

        val filteredByChip = when (selectedFilter) {
            "My Favorites" -> withLiveDistances.filter { isSalonFavorited(it) }
            "Open Now" -> withLiveDistances.filter { it.isOpen }
            "Under ₹150" -> withLiveDistances.filter { salon ->
                val minPrice = salon.services.minOfOrNull { it.price } ?: 100
                minPrice <= 150
            }
            "Top Rated" -> withLiveDistances.filter { it.rating >= 4.7 }
            "A/C Salons" -> withLiveDistances.filter { salon ->
                salon.isAcSalon ||
                salon.tagline.contains("AC", ignoreCase = true) ||
                salon.tagline.contains("Luxury", ignoreCase = true) ||
                salon.name.contains("Unisex", ignoreCase = true) ||
                salon.rating >= 4.7
            }
            else -> withLiveDistances
        }
        val baseList = if (showFavoritesTab) {
            filteredByChip.filter { isSalonFavorited(it) }
        } else {
            filteredByChip
        }
        baseList.distinctBy { it.id.ifBlank { it.name } }
    }

    val trimmedSearchQuery = searchQuery.trim()
    val isSearching = trimmedSearchQuery.isNotBlank()

    // When searching: Partition into Exact/Query Matches (with exact salon name matches at the very top)
    // and Recommended Salons (other available/nearby salons sorted by nearest distance)
    val (searchExactMatches, searchRecommendedSalons) = remember(displayedSalons, trimmedSearchQuery, userLatitude, userLongitude) {
        if (trimmedSearchQuery.isBlank()) {
            Pair(displayedSalons, emptyList<Salon>())
        } else {
            val matches = displayedSalons.filter { salon ->
                salon.name.contains(trimmedSearchQuery, ignoreCase = true) ||
                    salon.location.contains(trimmedSearchQuery, ignoreCase = true) ||
                    salon.shopAddress.contains(trimmedSearchQuery, ignoreCase = true) ||
                    salon.landmark.contains(trimmedSearchQuery, ignoreCase = true) ||
                    salon.pincode.contains(trimmedSearchQuery, ignoreCase = true) ||
                    salon.adminCategoryTag.contains(trimmedSearchQuery, ignoreCase = true) ||
                    salon.salonCategory.contains(trimmedSearchQuery, ignoreCase = true) ||
                    salon.shopCategory.contains(trimmedSearchQuery, ignoreCase = true) ||
                    salon.tagline.contains(trimmedSearchQuery, ignoreCase = true) ||
                    ((trimmedSearchQuery.equals("ac", ignoreCase = true) || trimmedSearchQuery.equals("a/c", ignoreCase = true)) && salon.isAcSalon) ||
                    salon.services.any {
                        it.name.contains(trimmedSearchQuery, ignoreCase = true) ||
                            it.serviceName.contains(trimmedSearchQuery, ignoreCase = true) ||
                            it.category.contains(trimmedSearchQuery, ignoreCase = true)
                    }
            }.sortedWith(
                compareBy<Salon> { salon ->
                    val cleanName = salon.name.trim()
                    when {
                        cleanName.equals(trimmedSearchQuery, ignoreCase = true) -> 0 // Exact salon name match first
                        cleanName.startsWith(trimmedSearchQuery, ignoreCase = true) -> 1
                        cleanName.contains(trimmedSearchQuery, ignoreCase = true) -> 2
                        else -> 3
                    }
                }.thenBy { salon ->
                    val sLat = salon.latitude
                    val sLng = salon.longitude
                    if (sLat != null && sLng != null && sLat != 0.0 && sLng != 0.0) {
                        com.example.data.TrimTimeRepository.calculateRawDistanceKm(userLatitude, userLongitude, sLat, sLng)
                    } else {
                        99999.0
                    }
                }
            )
            val matchedIds = matches.map { it.id.ifBlank { it.name } }.toSet()
            val recommended = displayedSalons.filterNot { (it.id.ifBlank { it.name }) in matchedIds }
            Pair(matches, recommended)
        }
    }

    if (showLocationSheet) {
        CustomerLocationBottomSheet(
            currentLocationTitle = userLocationTitle,
            currentLocationSubtitle = userLocationSubtitle,
            currentLatitude = userLatitude,
            currentLongitude = userLongitude,
            isLiveGpsActive = isLiveGpsActive,
            isDetectingGps = isDetectingGps,
            onDismiss = { showLocationSheet = false },
            onUseCurrentGpsClick = {
                if (hasLocationPermission(context)) {
                    isDetectingGps = true
                    fetchDeviceGpsLocation(
                        context = context,
                        fallbackLat = userLatitude,
                        fallbackLng = userLongitude,
                        onResolutionRequired = { intentSenderRequest ->
                            gpsSettingsLauncher.launch(intentSenderRequest)
                        }
                    ) { lat, lng, isLive ->
                        isDetectingGps = false
                        if (isLive && lat != 0.0 && lng != 0.0) {
                            onDetectGpsLocation(lat, lng, true)
                            showLocationSheet = false
                        }
                    }
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            onApplyManualOrPinLocation = { cityText, pickedLat, pickedLng ->
                onManualLocationSelected(cityText, pickedLat, pickedLng)
                showLocationSheet = false
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("salons_list"),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // 1. Interactive Live GPS Location Header with Profile Icon
        item {
            BlinkitAutoLocationHeader(
                currentLocation = userLocationTitle,
                subLocation = userLocationSubtitle,
                isLiveGpsActive = isLiveGpsActive,
                isDetectingGps = isDetectingGps,
                onLocationClick = { showLocationSheet = true },
                onQuickGpsClick = {
                    if (hasLocationPermission(context)) {
                        isDetectingGps = true
                        fetchDeviceGpsLocation(
                            context = context,
                            fallbackLat = userLatitude,
                            fallbackLng = userLongitude,
                            onResolutionRequired = { intentSenderRequest ->
                                gpsSettingsLauncher.launch(intentSenderRequest)
                            }
                        ) { lat, lng, isLive ->
                            isDetectingGps = false
                            if (isLive && lat != 0.0 && lng != 0.0) {
                                onDetectGpsLocation(lat, lng, true)
                            }
                        }
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
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

        // 2b. Quick Access 'My Bookings' Button on Home Screen
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onOpenMyBookings,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.75f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DarkCardSurface,
                        contentColor = AmberPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("home_my_bookings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "My Bookings",
                        tint = AmberPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "My Bookings • View Upcoming & Past Appointments",
                        color = AmberPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 3. Quick Re-book Section (Strictly shown ONLY when the logged-in customer has a COMPLETED past booking)
        if (pastBooking != null && pastBooking.status.equals("COMPLETED", ignoreCase = true)) {
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
        }

        // 4. Smart Filter Chips ("My Favorites", "Open Now", "Under ₹150", "Top Rated", "A/C Salons")
        item {
            SmartFilterChipsRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { filter ->
                    selectedFilter = if (selectedFilter == filter) null else filter
                }
            )
        }

        // 4b. "All Salons" vs "❤️ My Favorites" Tab Switcher
        item {
            val favCount = salons.count { isSalonFavorited(it) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCardSurface)
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                    .padding(4.dp)
                    .testTag("customer_favorites_tab_row"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (!showFavoritesTab) AmberPrimary else Color.Transparent)
                        .clickable { showFavoritesTab = false }
                        .testTag("tab_all_salons"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All Salons (${salons.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (!showFavoritesTab) DarkBackground else Color(0xFFCBD5E1)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (showFavoritesTab) Color(0xFFF43F5E) else Color.Transparent)
                        .clickable { showFavoritesTab = true }
                        .testTag("tab_my_favorites"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "My Favorites",
                            tint = if (showFavoritesTab) Color.White else Color(0xFFF43F5E),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "My Favorites ($favCount)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (showFavoritesTab) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }

        // 5. Section Header with Live Counter & List / Live Map View Switcher
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

                // List View vs Live Map View Toggle Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkCardSurface)
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                        .padding(4.dp)
                        .testTag("customer_view_mode_toggle_row"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val listSelected = !isMapView
                    val mapSelected = isMapView

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (listSelected) AmberPrimary else Color.Transparent)
                            .clickable { isMapView = false }
                            .testTag("toggle_list_view_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatListBulleted,
                                contentDescription = "List View",
                                tint = if (listSelected) DarkBackground else Color(0xFFCBD5E1),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "List View",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (listSelected) DarkBackground else Color(0xFFCBD5E1)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (mapSelected) AmberPrimary else Color.Transparent)
                            .clickable { isMapView = true }
                            .testTag("toggle_live_map_view_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Live Map View",
                                tint = if (mapSelected) DarkBackground else AmberPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Live Map View",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (mapSelected) DarkBackground else Color.White
                            )
                        }
                    }
                }
            }
        }

        // 5b. Real Interactive OpenStreetMap Section (when Live Map View is active)
        if (isMapView) {
            item {
                CustomerSalonsOsmMapSection(
                    salons = displayedSalons,
                    userLatitude = userLatitude,
                    userLongitude = userLongitude,
                    onGpsLocationDetected = { lat, lng ->
                        onDetectGpsLocation(lat, lng, true)
                    },
                    onSalonSelected = onSalonSelected
                )
            }
        }

        // 6. Nearby Salons Feed (Sorted by Nearest Location; when searching: Exact Matches first -> "Recommended" Divider -> Other Available/Nearby Salons)
        if (displayedSalons.isEmpty()) {
            item {
                EmptySalonsPlaceholder(
                    onClearFilters = {
                        selectedFilter = null
                        showFavoritesTab = false
                        onSearchChanged("")
                    }
                )
            }
        } else if (isSearching) {
            if (searchExactMatches.isNotEmpty()) {
                item(key = "exact_matches_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "EXACT & MATCHING SALONS (${searchExactMatches.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = GreenSuccess,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Top Match First",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                items(
                    items = searchExactMatches,
                    key = { salon -> "exact_${salon.id.ifBlank { salon.name }}" }
                ) { salon ->
                    BlinkitSalonCard(
                        salon = salon,
                        currentUserId = currentUserId,
                        isFavorite = isSalonFavorited(salon),
                        onToggleFavorite = { onToggleFavorite(salon) },
                        onRateClick = { salonToRate = salon },
                        onBookClick = { onSalonSelected(salon) }
                    )
                }
            } else {
                item(key = "no_exact_match_banner") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "No exact match for \"$trimmedSearchQuery\". Showing recommended salons near you below.",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // "Recommended" Divider below Exact Matches, followed by other available or nearby salons
            item(key = "recommended_salons_divider_item") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("recommended_salons_divider"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = AmberPrimary.copy(alpha = 0.4f)
                    )
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "✨ Recommended • Available & Nearby Salons",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AmberPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = AmberPrimary.copy(alpha = 0.4f)
                    )
                }
            }

            if (searchRecommendedSalons.isNotEmpty()) {
                items(
                    items = searchRecommendedSalons,
                    key = { salon -> "rec_${salon.id.ifBlank { salon.name }}" }
                ) { salon ->
                    BlinkitSalonCard(
                        salon = salon,
                        currentUserId = currentUserId,
                        isFavorite = isSalonFavorited(salon),
                        onToggleFavorite = { onToggleFavorite(salon) },
                        onRateClick = { salonToRate = salon },
                        onBookClick = { onSalonSelected(salon) }
                    )
                }
            } else {
                item(key = "all_matched_notice") {
                    Text(
                        text = "All available nearby salons matched your search above.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        } else {
            items(
                items = displayedSalons,
                key = { salon -> salon.id.ifBlank { salon.name } }
            ) { salon ->
                BlinkitSalonCard(
                    salon = salon,
                    currentUserId = currentUserId,
                    isFavorite = isSalonFavorited(salon),
                    onToggleFavorite = { onToggleFavorite(salon) },
                    onRateClick = { salonToRate = salon },
                    onBookClick = { onSalonSelected(salon) }
                )
            }
        }
    }

    // Customer Rating Dialog
    salonToRate?.let { salon ->
        CustomerRatingDialog(
            salon = salon,
            onDismiss = { salonToRate = null },
            onSubmit = { rating, review ->
                onRateSalon(salon, rating, review)
            }
        )
    }
}

/**
 * 1. Interactive Live GPS Location Header:
 * Displays live reverse-geocoded GPS location (e.g., "Bakhraur, Gopalganj 📍") with 1-tap location sheet trigger,
 * live GPS detect button, and profile icon.
 */
@Composable
fun BlinkitAutoLocationHeader(
    currentLocation: String,
    subLocation: String,
    isLiveGpsActive: Boolean = false,
    isDetectingGps: Boolean = false,
    onLocationClick: () -> Unit = {},
    onQuickGpsClick: () -> Unit = {},
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
            // Clickable Location Pin + Details (Opens Change / Detect Location Bottom Sheet)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onLocationClick() }
                    .padding(vertical = 4.dp, horizontal = 4.dp)
                    .testTag("customer_location_header_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF451A03), Color(0xFF1E293B)))
                        )
                        .border(
                            1.dp,
                            if (isLiveGpsActive) GreenSuccess.copy(alpha = 0.7f) else AmberPrimary.copy(alpha = 0.5f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDetectingGps) {
                        CircularProgressIndicator(
                            color = AmberPrimary,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "GPS Location",
                            tint = if (isLiveGpsActive) GreenSuccess else AmberPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentLocation,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "📍",
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Change Location",
                            tint = AmberPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = subLocation,
                        fontSize = 11.sp,
                        color = if (isLiveGpsActive) Color(0xFFA7F3D0) else Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 1-Tap Quick GPS Detect Icon Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .border(
                        1.dp,
                        if (isLiveGpsActive) GreenSuccess.copy(alpha = 0.5f) else Color(0xFF334155),
                        CircleShape
                    )
                    .clickable { onQuickGpsClick() }
                    .testTag("home_quick_gps_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Use Current GPS Location",
                    tint = if (isLiveGpsActive) GreenSuccess else AmberPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

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
 * Interactive "Change / Detect Location" Modal Bottom Sheet for Customer Mode:
 * - "🎯 Use My Current GPS Location" button (requests location permission if needed, fetches live GPS, reverse-geocodes, recalculates distances)
 * - Manual City / Town / Area text input with "Apply Location"
 * - Interactive OpenStreetMap Pin Picker so the customer can tap/drag on the map to set their exact coordinates
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerLocationBottomSheet(
    currentLocationTitle: String,
    currentLocationSubtitle: String,
    currentLatitude: Double,
    currentLongitude: Double,
    isLiveGpsActive: Boolean,
    isDetectingGps: Boolean,
    onDismiss: () -> Unit,
    onUseCurrentGpsClick: () -> Unit,
    onApplyManualOrPinLocation: (String, Double?, Double?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var manualCityInput by remember(currentLocationTitle) { mutableStateOf(currentLocationTitle) }
    var pickedLat by remember(currentLatitude) { mutableDoubleStateOf(currentLatitude) }
    var pickedLng by remember(currentLongitude) { mutableDoubleStateOf(currentLongitude) }
    var hasMovedMapPin by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Change / Detect Your Location",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Salon distances (km) are calculated from this location",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF334155), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Current Active Location Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = BorderStroke(
                    1.dp,
                    if (isLiveGpsActive) GreenSuccess.copy(alpha = 0.5f) else Color(0xFF334155)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLiveGpsActive) "🟢 ACTIVE LIVE GPS LOCATION" else "📍 CURRENT SELECTED LOCATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isLiveGpsActive) GreenSuccess else AmberPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentLocationTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = currentLocationSubtitle,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Text(
                        text = String.format(java.util.Locale.US, "%.4f,\n%.4f", currentLatitude, currentLongitude),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }

            // 1. Primary Action: "🎯 Use My Current GPS Location"
            Button(
                onClick = onUseCurrentGpsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("use_current_gps_location_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberPrimary,
                    contentColor = Color(0xFF1E1402)
                )
            ) {
                if (isDetectingGps) {
                    CircularProgressIndicator(
                        color = Color(0xFF1E1402),
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Detecting Live GPS...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🎯 Use My Current GPS Location",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            // 2. Manual City / Town / Area Input
            Text(
                text = "OR ENTER CITY / TOWN / AREA MANUALLY",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF94A3B8),
                letterSpacing = 0.5.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualCityInput,
                    onValueChange = { manualCityInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_location_city_input"),
                    placeholder = {
                        Text(
                            text = "e.g. Bakhraur, Gopalganj",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
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
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        if (manualCityInput.isNotBlank()) {
                            onApplyManualOrPinLocation(manualCityInput.trim(), null, null)
                        }
                    },
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("apply_manual_location_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = AmberPrimary
                    ),
                    border = BorderStroke(1.dp, AmberPrimary)
                ) {
                    Text(
                        text = "Apply",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            // 3. Interactive OpenStreetMap Pin Picker
            Text(
                text = "OR PICK EXACT LOCATION PIN ON MAP",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF94A3B8),
                letterSpacing = 0.5.sp
            )

            PartnerLocationPickerOsmMap(
                latitude = pickedLat,
                longitude = pickedLng,
                salonName = "My Location Pin",
                onLocationPicked = { newLat, newLng ->
                    pickedLat = newLat
                    pickedLng = newLng
                    hasMovedMapPin = true
                }
            )

            OutlinedButton(
                onClick = {
                    onApplyManualOrPinLocation(
                        if (hasMovedMapPin) "" else manualCityInput,
                        pickedLat,
                        pickedLng
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("confirm_customer_map_pin_btn"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GreenSuccess),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenSuccess)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Confirm Selected Map Pin (${String.format(java.util.Locale.US, "%.4f, %.4f", pickedLat, pickedLng)})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("search_salons_input"),
        placeholder = {
            Text(
                text = "Search by salon name, address, or tags ('AC', 'Standard', 'Fade')...",
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
                IconButton(
                    onClick = {
                        onSearchChanged("")
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboardController?.hide()
                focusManager.clearFocus()
            },
            onDone = {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        ),
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
    val bookingToShow = pastBooking?.takeIf { it.status.equals("COMPLETED", ignoreCase = true) } ?: return

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
        FilterChipItem("My Favorites", Icons.Default.Favorite),
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
                        tint = if (!isSelected && chip.label == "My Favorites") Color(0xFFF43F5E) else contentColor,
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
 * 6. Booking Action: A clear "View & Book" button + Favorite (Heart) toggle icon.
 */
@Composable
fun BlinkitSalonCard(
    salon: Salon,
    currentUserId: String = "",
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onRateClick: () -> Unit = {},
    onBookClick: () -> Unit
) {
    val context = LocalContext.current
    val isOwnSalon = currentUserId.isNotBlank() &&
        (salon.ownerId == currentUserId || salon.partnerId == currentUserId)
    val minPrice = salon.services.minOfOrNull { it.price } ?: 80
    val isBusy = salon.isBusy
    val rawPhotoData = salon.imageUrl.trim().ifBlank { salon.resolvedPhotoData.trim() }
    val coilImageModel = remember(rawPhotoData) {
        SalonImageUtils.resolveCoilImageModel(rawPhotoData)
    }
    val salonCoverBitmap = remember(rawPhotoData) {
        SalonImageUtils.decodeBase64ToImageBitmap(rawPhotoData)
    }
    val hasCustomPhoto = coilImageModel != null || salonCoverBitmap != null
    val highContrastTextShadow = Shadow(
        color = Color.Black,
        offset = Offset(0f, 2f),
        blurRadius = 8f
    )
    val isShopAccepting = salon.isAcceptingBookings

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onBookClick() }
            .testTag("salon_card_${salon.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            // 1. Tall 230.dp Salon Cover Photo Container — unobstructed center with bottom readability gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
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
                if (coilImageModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(coilImageModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${salon.name} Cover Photo",
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (salonCoverBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = salonCoverBitmap,
                        contentDescription = "${salon.name} Cover Photo",
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom gradient overlay behind the salon name & tagline so text remains crisp while leaving the photo unobstructed
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.10f),
                                    0.55f to Color.Transparent,
                                    0.80f to Color(0xAD090D16),
                                    1.0f to Color(0xF2090D16)
                                )
                            )
                        )
                )

                // Top-Right Corner: Favorite (Heart) Toggle Button only (keeps image unobstructed)
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xBF0F172A))
                        .border(
                            1.dp,
                            if (isFavorite) Color(0xFFF43F5E) else Color(0xFF475569),
                            CircleShape
                        )
                        .testTag("favorite_salon_btn_${salon.id}")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove ${salon.name} from Favorites" else "Add ${salon.name} to Favorites",
                        tint = if (isFavorite) Color(0xFFF43F5E) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Bottom Edge: Salon Name & Tagline over the bottom gradient scrim
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = salon.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                lineHeight = 22.sp,
                                shadow = highContrastTextShadow
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (salon.isPhoneVerified) {
                            com.example.ui.components.VerifiedBlueTickIcon(size = 16.dp)
                        }
                    }

                    Text(
                        text = if (salon.tagline.isNotBlank()) salon.tagline else "Verified Grooming Partner",
                        style = TextStyle(
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0),
                            shadow = highContrastTextShadow
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Card Body: Status Tags positioned cleanly below the image, followed by Distance, Rating, Price & Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // Repositioned Status Tags Row (Just below the image so the photo is 100% unobstructed)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    !isShopAccepting -> Color(0xFF450A0A)
                                    isBusy -> Color(0xFF451A03)
                                    else -> Color(0xFF064E3B)
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    !isShopAccepting -> RedAlert
                                    isBusy -> Color(0xFFF97316)
                                    else -> GreenSuccess
                                },
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
                                text = if (!isShopAccepting || isBusy) "🔴" else "🟢",
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                            val waitLabel = if (salon.waitingTimeMinutes > 0) {
                                "~${salon.waitingTimeMinutes}m Wait"
                            } else {
                                "No Wait"
                            }
                            Text(
                                text = when {
                                    !isShopAccepting -> "Currently Closed - Not accepting new bookings"
                                    isBusy -> "Busy (${salon.availableChairs} Chairs • $waitLabel)"
                                    else -> "Available (${salon.availableChairs} Chairs • $waitLabel)"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when {
                                    !isShopAccepting -> Color(0xFFFECACA)
                                    isBusy -> Color(0xFFFDBA74)
                                    else -> GreenSuccess
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isShopAccepting) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F2D1F))
                                .border(1.dp, GreenSuccess.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "OPEN NOW",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399),
                                maxLines = 1
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF450A0A))
                                .border(1.dp, RedAlert, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "CLOSED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = RedAlert,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rating + Distance + AC Tags
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Rating (Clickable to trigger rating dialog)
                        val ratingText = if (salon.totalRatings > 0) {
                            String.format(java.util.Locale.US, "★ %.1f (%d)", salon.effectiveRating, salon.totalRatings)
                        } else if (salon.averageRating > 0.0) {
                            String.format(java.util.Locale.US, "★ %.1f", salon.effectiveRating)
                        } else if (salon.reviewCount > 0) {
                            String.format(java.util.Locale.US, "★ %.1f (%d)", salon.rating, salon.reviewCount)
                        } else {
                            "★ New"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF064E3B))
                                .clickable { onRateClick() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("salon_rating_badge_${salon.id}")
                        ) {
                            Text(
                                text = ratingText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
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
                                fontSize = 11.sp,
                                color = Color(0xFFE2E8F0),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }

                        // AC / Non-AC Status Tag
                        if (salon.isAcSalon) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0C4A6E))
                                    .border(0.8.dp, Color(0xFF38BDF8), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                    .testTag("ac_badge_${salon.id}")
                            ) {
                                Text(
                                    text = "❄️ AC",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7DD3FC),
                                    maxLines = 1
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(0.8.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                    .testTag("non_ac_badge_${salon.id}")
                            ) {
                                Text(
                                    text = "Non-AC",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Price starting from (e.g. "Starts at ₹100")
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Starts at ₹$minPrice",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AmberPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "Pay at salon",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = salon.location,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Top services preview chips — single-line horizontal row with wrapContentHeight()
                // so a 2nd/3rd chip NEVER squeezes into a tall vertical column of characters!
                if (salon.services.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        salon.services.take(3).forEach { service ->
                            Box(
                                modifier = Modifier
                                    .wrapContentHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${service.name} • ₹${service.price}",
                                    fontSize = 10.sp,
                                    color = Color(0xFFE2E8F0),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Strictly 10.dp spacing between service chips and bottom divider / "View & Book" row
                Spacer(modifier = Modifier.height(10.dp))

                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Section: Row 1 = Pay at Salon Guarantee Badge (No overlap with buttons)
                val context = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF064E3B).copy(alpha = 0.28f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = "Pay at Salon",
                        tint = GreenSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "₹0 Advance • Pay at Salon after your service",
                        fontSize = 11.sp,
                        color = Color(0xFFA7F3D0),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 2 = Balanced Full-Width Action Buttons ("Get Directions" + "Rate" + "View & Book" / "Join Waitlist")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            launchGeoDirections(
                                context = context,
                                latitude = salon.latitude,
                                longitude = salon.longitude,
                                salonName = salon.name,
                                fallbackAddress = salon.location
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("salon_card_directions_${salon.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = "Get Directions",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Directions",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Quick Rate Button
                    OutlinedButton(
                        onClick = onRateClick,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberPrimary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("salon_card_rate_btn_${salon.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rate Salon",
                            tint = AmberPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Rate",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    if (!isOwnSalon) {
                        val isShopAccepting = salon.isAcceptingBookings
                        // 6. Booking Action: "View & Book" or "Join Waitlist" if busy, or "Currently Closed" when closed
                        Button(
                            onClick = onBookClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    !isShopAccepting -> Color(0xFF450A0A)
                                    isBusy -> Color(0xFFEA580C)
                                    else -> AmberPrimary
                                },
                                contentColor = when {
                                    !isShopAccepting -> Color(0xFFFECACA)
                                    isBusy -> Color.White
                                    else -> Color(0xFF1E1402)
                                }
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("view_and_book_button_${salon.id}")
                        ) {
                            Text(
                                text = when {
                                    !isShopAccepting -> "Currently Closed"
                                    isBusy -> "Join Waitlist"
                                    else -> "View & Book"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, AmberPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Your Salon",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberPrimary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
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
                text = "No nearby registered salons match your filters.",
                color = Color(0xFFE2E8F0),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = "Register your salon in Partner Mode or reset filters to discover nearby grooming studios.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

/**
 * Interactive Customer Rating & Review Dialog (1 to 5 Stars)
 */
@Composable
fun CustomerRatingDialog(
    salon: Salon,
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, review: String) -> Unit
) {
    var selectedStars by remember { mutableIntStateOf(5) }
    var reviewComment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE2E8F0),
        shape = RoundedCornerShape(18.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "⭐", fontSize = 20.sp)
                Text(
                    text = "Rate ${salon.name}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "How was your experience at this salon?",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )

                // 5 Stars Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..5).forEach { star ->
                        val isSelected = star <= selectedStars
                        IconButton(
                            onClick = { selectedStars = star },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("star_rating_$star")
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$star Stars",
                                tint = if (isSelected) AmberPrimary else Color(0xFF64748B),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                val starLabel = when (selectedStars) {
                    1 -> "1 Star • Poor"
                    2 -> "2 Stars • Fair"
                    3 -> "3 Stars • Good"
                    4 -> "4 Stars • Very Good"
                    5 -> "5 Stars • Excellent! 🌟"
                    else -> ""
                }
                Text(
                    text = starLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberPrimary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                OutlinedTextField(
                    value = reviewComment,
                    onValueChange = { reviewComment = it },
                    label = { Text("Write a review (optional)", color = Color(0xFF94A3B8)) },
                    placeholder = { Text("e.g. Great haircut, clean shop!", color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberPrimary,
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp)
                        .testTag("rating_review_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(selectedStars, reviewComment)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberPrimary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("submit_rating_button")
            ) {
                Text("Submit Rating", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
            ) {
                Text("Cancel")
            }
        }
    )
}
