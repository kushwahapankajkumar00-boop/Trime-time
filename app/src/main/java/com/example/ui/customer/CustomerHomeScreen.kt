package com.example.ui.customer

import android.Manifest
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Directions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import coil.compose.AsyncImage
import com.example.model.Booking
import com.example.model.Salon
import com.example.ui.components.CustomerSalonsOsmMapSection
import com.example.ui.components.PartnerLocationPickerOsmMap
import com.example.ui.components.calculateDistanceKm
import com.example.ui.components.fetchDeviceGpsLocation
import com.example.ui.components.hasLocationPermission
import com.example.ui.components.initOsmDroidConfig
import com.example.ui.components.launchGeoDirections
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Initialize OpenStreetMap Configuration (userAgentValue + internal tile cache) before MapView renders
    remember(context) {
        Configuration.getInstance().userAgentValue = context.packageName
        initOsmDroidConfig(context)
        true
    }

    // Smart Filter state (Blinkit style)
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    // Toggle between "List View" and "Live Map View" (OpenStreetMap)
    var isMapView by remember { mutableStateOf(false) }
    // Interactive "Change / Detect Location" Bottom Sheet state
    var showLocationSheet by remember { mutableStateOf(false) }
    var isDetectingGps by remember { mutableStateOf(false) }

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
                fallbackLng = userLongitude
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
                fallbackLng = userLongitude
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

    // Filter salons according to selected Smart Filter, dynamically recalculate live Haversine distance
    // from (userLatitude, userLongitude), and sort by shortest distance first!
    val displayedSalons = remember(salons, selectedFilter, userLatitude, userLongitude) {
        val withLiveDistances = salons.map { salon ->
            val sLat = salon.latitude
            val sLng = salon.longitude
            if (sLat != null && sLng != null && sLat != 0.0 && sLng != 0.0) {
                val km = calculateDistanceKm(userLatitude, userLongitude, sLat, sLng).coerceAtLeast(0.1)
                salon.copy(distance = String.format(java.util.Locale.US, "%.1f km", km))
            } else {
                salon
            }
        }.sortedBy { salon ->
            val sLat = salon.latitude
            val sLng = salon.longitude
            if (sLat != null && sLng != null && sLat != 0.0 && sLng != 0.0) {
                calculateDistanceKm(userLatitude, userLongitude, sLat, sLng).coerceAtLeast(0.1)
            } else {
                salon.distance.replace("km", "", ignoreCase = true).trim().toDoubleOrNull() ?: 999.0
            }
        }

        val baseList = when (selectedFilter) {
            "Open Now" -> withLiveDistances.filter { it.isOpen }
            "Under ₹150" -> withLiveDistances.filter { salon ->
                val minPrice = salon.services.minOfOrNull { it.price } ?: 100
                minPrice <= 150
            }
            "Top Rated" -> withLiveDistances.filter { it.rating >= 4.7 }
            "A/C Salons" -> withLiveDistances.filter { salon ->
                salon.tagline.contains("AC", ignoreCase = true) ||
                salon.tagline.contains("Luxury", ignoreCase = true) ||
                salon.name.contains("Unisex", ignoreCase = true) ||
                salon.rating >= 4.7
            }
            else -> withLiveDistances
        }
        baseList.distinctBy { it.id.ifBlank { it.name } }
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
                        fallbackLng = userLongitude
                    ) { lat, lng, isLive ->
                        isDetectingGps = false
                        if (lat != 0.0 && lng != 0.0) {
                            onDetectGpsLocation(lat, lng, true)
                            if (isLive) {
                                showLocationSheet = false
                            }
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
                            fallbackLng = userLongitude
                        ) { lat, lng, _ ->
                            isDetectingGps = false
                            if (lat != 0.0 && lng != 0.0) {
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

        // 4. Smart Filter Chips ("Open Now", "Under ₹150", "Top Rated", "A/C Salons")
        item {
            SmartFilterChipsRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { filter ->
                    selectedFilter = if (selectedFilter == filter) null else filter
                }
            )
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
            items(
                items = displayedSalons,
                key = { salon -> salon.id.ifBlank { salon.name } }
            ) { salon ->
                BlinkitSalonCard(
                    salon = salon,
                    currentUserId = currentUserId,
                    onBookClick = { onSalonSelected(salon) }
                )
            }
        }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
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
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF94A3B8)
                    )
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
    currentUserId: String = "",
    onBookClick: () -> Unit
) {
    val isOwnSalon = currentUserId.isNotBlank() &&
        (salon.ownerId == currentUserId || salon.partnerId == currentUserId)
    val minPrice = salon.services.minOfOrNull { it.price } ?: 80
    val isBusy = salon.isBusy
    val rawPhotoData = salon.resolvedPhotoData.trim()
    val salonCoverBitmap = remember(rawPhotoData) {
        SalonImageUtils.decodeBase64ToImageBitmap(rawPhotoData)
    }
    val hasCustomPhoto = salonCoverBitmap != null || rawPhotoData.isNotBlank()
    val highContrastTextShadow = Shadow(
        color = Color.Black,
        offset = Offset(0f, 2f),
        blurRadius = 8f
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 6.dp)
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
            // Top Salon Visual Banner with Custom Cover Photo + Strong Dark Vertical Gradient Scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 122.dp)
                    .wrapContentHeight()
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
                if (salonCoverBitmap != null) {
                    Image(
                        bitmap = salonCoverBitmap,
                        contentDescription = "${salon.name} Cover Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else if (rawPhotoData.isNotBlank()) {
                    AsyncImage(
                        model = rawPhotoData,
                        contentDescription = "${salon.name} Cover Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }

                // Always apply a strong dark vertical gradient scrim over custom cover photos so white text is 100% readable
                if (hasCustomPhoto) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(alpha = 0.65f),
                                        0.50f to Color(0xD9090D16),
                                        1.0f to Color(0xFA090D16)
                                    )
                                )
                            )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentHeight()
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Highly Visible Live Wait Time & Real-time Busy/Free Badge
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
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
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                    val waitLabel = if (salon.waitingTimeMinutes > 0) {
                                        "~${salon.waitingTimeMinutes}m Wait"
                                    } else {
                                        "No Wait"
                                    }
                                    Text(
                                        text = if (isBusy) {
                                            "Busy (${salon.availableChairs} Chairs • $waitLabel)"
                                        } else {
                                            "Available (${salon.availableChairs} Chairs • $waitLabel)"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isBusy) Color(0xFFFDBA74) else GreenSuccess,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                                        color = Color(0xFF34D399),
                                        maxLines = 1
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF450A0A))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
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

                        // Dedicated high-contrast text container so Salon Name & Tagline are 100% legible over any uploaded image
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (hasCustomPhoto) {
                                        Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xB3090D16))
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    } else {
                                        Modifier
                                    }
                                ),
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
                                        fontSize = 16.sp,
                                        lineHeight = 20.sp,
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
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFE2E8F0),
                                    shadow = highContrastTextShadow
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Salon Scissors Badge Icon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xD90F172A))
                            .border(1.dp, AmberPrimary.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = "Salon",
                            tint = AmberPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Card Body: Distance, Rating, Price, Service Pills & View & Book Button (Compact & Tight)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
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
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
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

                // Row 2 = Balanced Full-Width Action Buttons ("Get Directions" + "View & Book" / "Join Waitlist")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
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
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Get Directions",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!isOwnSalon) {
                        // 6. Booking Action: "View & Book" or "Join Waitlist" if busy
                        Button(
                            onClick = onBookClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBusy) Color(0xFFEA580C) else AmberPrimary,
                                contentColor = if (isBusy) Color.White else Color(0xFF1E1402)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("view_and_book_button_${salon.id}")
                        ) {
                            Text(
                                text = if (isBusy) "Join Waitlist" else "View & Book",
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
