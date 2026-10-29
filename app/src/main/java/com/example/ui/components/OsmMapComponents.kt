package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.Salon
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

const val DEFAULT_PATNA_LAT = 25.5941
const val DEFAULT_PATNA_LNG = 85.1376
const val DEFAULT_CUSTOMER_MAP_ZOOM = 15.5

/**
 * Initializes OSMDroid Configuration BEFORE any MapView is created or rendered:
 * - Sets `Configuration.getInstance().userAgentValue`
 * - Configures `osmdroidBasePath` and `osmdroidTileCache` inside `context.cacheDir` so map tiles
 *   always download and render reliably on Android 10–16 without external storage permissions.
 */
fun initOsmDroidConfig(context: Context) {
    try {
        val appCtx = context.applicationContext
        val osmConfig = Configuration.getInstance()
        val baseDir = File(appCtx.cacheDir, "osmdroid").apply { mkdirs() }
        val tileCacheDir = File( baseDir, "tiles").apply { mkdirs() }
        osmConfig.osmdroidBasePath = baseDir
        osmConfig.osmdroidTileCache = tileCacheDir
        osmConfig.load(appCtx, appCtx.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
        osmConfig.userAgentValue = appCtx.packageName.ifBlank { "com.trimtime.app" }
    } catch (_: Exception) {
        Configuration.getInstance().userAgentValue = context.packageName.ifBlank { "com.trimtime.app" }
    }
}

/**
 * Launches a 100% free turn-by-turn navigation intent using standard `geo:` URI
 * (`geo:lat,lng?q=lat,lng(Salon+Name)`), compatible with Google Maps, OsmAnd, Organic Maps, etc.
 */
fun launchGeoDirections(
    context: Context,
    latitude: Double?,
    longitude: Double?,
    salonName: String,
    fallbackAddress: String = ""
) {
    val validLat = if (latitude != null && latitude != 0.0) latitude else DEFAULT_PATNA_LAT
    val validLng = if (longitude != null && longitude != 0.0) longitude else DEFAULT_PATNA_LNG
    val encodedLabel = Uri.encode(salonName.ifBlank { fallbackAddress.ifBlank { "Salon" } })
    val geoUri = Uri.parse("geo:$validLat,$validLng?q=$validLat,$validLng($encodedLabel)")

    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(mapIntent)
    } catch (_: Exception) {
        // Fallback to browser OpenStreetMap directions if no map app is installed
        try {
            val browserUri = Uri.parse(
                "https://www.openstreetmap.org/?mlat=$validLat&mlon=$validLng#map=16/$validLat/$validLng"
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW, browserUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Coordinates: ${"%.4f".format(validLat)}, ${"%.4f".format(validLng)}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

/**
 * Checks if either FINE or COARSE location permission is currently granted.
 */
fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

/**
 * Fetches the device's real GPS / Network location using both Google Play Services
 * `FusedLocationProviderClient` and Android's native `LocationManager`.
 * Immediately returns the best lastKnownLocation if available AND requests a fresh live GPS update.
 */
@SuppressLint("MissingPermission")
fun fetchDeviceGpsLocation(
    context: Context,
    fallbackLat: Double = DEFAULT_PATNA_LAT,
    fallbackLng: Double = DEFAULT_PATNA_LNG,
    onLocationResult: (Double, Double, Boolean) -> Unit
) {
    if (!hasLocationPermission(context)) {
        onLocationResult(fallbackLat, fallbackLng, false)
        return
    }

    var emittedLiveFix = false

    // 1. Try FusedLocationProviderClient for fast & accurate real GPS
    try {
        val fusedClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null && loc.latitude != 0.0 && loc.longitude != 0.0) {
                emittedLiveFix = true
                onLocationResult(loc.latitude, loc.longitude, true)
            }
        }
        val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
        fusedClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { freshLoc ->
            if (freshLoc != null && freshLoc.latitude != 0.0 && freshLoc.longitude != 0.0) {
                emittedLiveFix = true
                onLocationResult(freshLoc.latitude, freshLoc.longitude, true)
            }
        }
    } catch (_: Exception) {
        // Fallback to LocationManager below
    }

    // 2. Also query native LocationManager for immediate cached & active GPS/Network fix
    try {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            if (!emittedLiveFix) onLocationResult(fallbackLat, fallbackLng, false)
            return
        }

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            val isEnabled = runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
            if (isEnabled || provider == LocationManager.PASSIVE_PROVIDER) {
                val loc = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
                if (loc != null && loc.latitude != 0.0 && loc.longitude != 0.0) {
                    if (bestLocation == null ||
                        loc.time > bestLocation.time + 120_000L ||
                        (kotlin.math.abs(loc.time - bestLocation.time) <= 120_000L && loc.accuracy < bestLocation.accuracy)
                    ) {
                        bestLocation = loc
                    }
                }
            }
        }

        val emittedCachedFix = if (bestLocation != null) {
            emittedLiveFix = true
            onLocationResult(bestLocation.latitude, bestLocation.longitude, true)
            true
        } else {
            false
        }

        // Also request a fresh live location fix from active GPS/Network provider
        val activeProviders = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
            runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false)
        }
        if (activeProviders.isNotEmpty()) {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { locationManager.removeUpdates(this) }
                    if (location.latitude != 0.0 && location.longitude != 0.0) {
                        emittedLiveFix = true
                        onLocationResult(location.latitude, location.longitude, true)
                    } else if (!emittedCachedFix && !emittedLiveFix) {
                        onLocationResult(fallbackLat, fallbackLng, false)
                    }
                }

                @Deprecated("Deprecated in Android API")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            for (prov in activeProviders) {
                runCatching {
                    locationManager.requestSingleUpdate(prov, listener, Looper.getMainLooper())
                }
            }
        } else if (!emittedCachedFix && !emittedLiveFix) {
            onLocationResult(fallbackLat, fallbackLng, false)
        }
    } catch (_: Exception) {
        if (!emittedLiveFix) {
            onLocationResult(fallbackLat, fallbackLng, false)
        }
    }
}

/**
 * Reverse-geocodes live GPS coordinates `(latitude, longitude)` into:
 * - `first`: Primary Header Title (e.g. `"Bakhraur, Gopalganj"` or `"Kankarbagh, Patna"`)
 * - `second`: Subtitle / Road / Area details (e.g. `"Main Road, Bihar • Live GPS"`)
 * Uses Android's native `Geocoder` first, with an automatic fallback to OpenStreetMap Nominatim.
 */
suspend fun reverseGeocodeLocation(
    context: Context,
    latitude: Double,
    longitude: Double
): Pair<String, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val coordLabel = String.format(java.util.Locale.US, "%.4f, %.4f", latitude, longitude)

    // 1. Try Android native Geocoder first
    try {
        if (android.location.Geocoder.isPresent()) {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            val addr = addresses?.firstOrNull()
            if (addr != null) {
                val subLocality = addr.subLocality?.trim().orEmpty()
                val locality = addr.locality?.trim().orEmpty()
                val subAdmin = addr.subAdminArea?.trim().orEmpty()
                val admin = addr.adminArea?.trim().orEmpty()
                val thoroughfare = addr.thoroughfare?.trim().orEmpty()
                val feature = addr.featureName?.trim().orEmpty()

                val primaryParts = listOf(subLocality, locality, subAdmin, admin)
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }

                if (primaryParts.isNotEmpty()) {
                    val title = if (primaryParts.size >= 2) {
                        "${primaryParts[0]}, ${primaryParts[1]}"
                    } else {
                        primaryParts[0]
                    }
                    val roadOrArea = thoroughfare
                        .ifBlank { subLocality }
                        .ifBlank { feature.takeIf { it.length > 2 && !it.contains("+") } ?: "" }
                        .ifBlank { admin }
                    val subtitle = if (roadOrArea.isNotBlank()) {
                        "$roadOrArea • Live GPS ($coordLabel)"
                    } else {
                        "Live GPS ($coordLabel)"
                    }
                    return@withContext Pair(title, subtitle)
                }
            }
        }
    } catch (_: Exception) {
        // Proceed to OpenStreetMap Nominatim fallback
    }

    // 2. Fallback to OpenStreetMap Nominatim reverse geocoding
    try {
        val urlStr = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$latitude&lon=$longitude&zoom=16&addressdetails=1"
        val conn = (java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4500
            readTimeout = 4500
            setRequestProperty("User-Agent", "${context.packageName} TrimTime/1.0")
            setRequestProperty("Accept-Language", "en")
        }
        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(responseText)
            val addressObj = json.optJSONObject("address")
            if (addressObj != null) {
                val townOrVillage = listOf(
                    addressObj.optString("village"),
                    addressObj.optString("town"),
                    addressObj.optString("suburb"),
                    addressObj.optString("neighbourhood"),
                    addressObj.optString("city_district"),
                    addressObj.optString("hamlet"),
                    addressObj.optString("city")
                ).firstOrNull { it.isNotBlank() }.orEmpty()

                val districtOrState = listOf(
                    addressObj.optString("city"),
                    addressObj.optString("state_district"),
                    addressObj.optString("county"),
                    addressObj.optString("state")
                ).firstOrNull { it.isNotBlank() && !it.equals(townOrVillage, ignoreCase = true) }.orEmpty()

                val roadName = listOf(
                    addressObj.optString("road"),
                    addressObj.optString("neighbourhood"),
                    addressObj.optString("suburb"),
                    addressObj.optString("state")
                ).firstOrNull { it.isNotBlank() }.orEmpty()

                val title = when {
                    townOrVillage.isNotBlank() && districtOrState.isNotBlank() -> "$townOrVillage, $districtOrState"
                    townOrVillage.isNotBlank() -> townOrVillage
                    districtOrState.isNotBlank() -> districtOrState
                    else -> ""
                }
                if (title.isNotBlank()) {
                    val subtitle = if (roadName.isNotBlank()) {
                        "$roadName • Live GPS ($coordLabel)"
                    } else {
                        "Live GPS ($coordLabel)"
                    }
                    return@withContext Pair(title, subtitle)
                }
            }
        }
    } catch (_: Exception) {
        // Fallback below
    }

    Pair("GPS ($coordLabel)", "Live GPS Location • Nearby Salons")
}

/**
 * Forward-geocodes a manually typed City/Town/Area string into `(latitude, longitude, displaySubtitle)`
 * so manual location changes in Customer Mode or Profile also recalculate nearby salon distances.
 */
suspend fun forwardGeocodeLocation(
    context: Context,
    locationQuery: String
): Triple<Double, Double, String>? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val cleanQuery = locationQuery.trim()
    if (cleanQuery.length < 2) return@withContext null

    // 1. Try Android native Geocoder first
    try {
        if (android.location.Geocoder.isPresent()) {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocationName("$cleanQuery, India", 1)
                ?.takeIf { it.isNotEmpty() }
                ?: geocoder.getFromLocationName(cleanQuery, 1)
            val match = results?.firstOrNull()
            if (match != null && match.hasLatitude() && match.hasLongitude()) {
                val lat = match.latitude
                val lng = match.longitude
                val sub = listOfNotNull(match.subAdminArea, match.adminArea)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { "Custom Location" }
                return@withContext Triple(
                    lat,
                    lng,
                    "$sub • (${String.format(java.util.Locale.US, "%.4f, %.4f", lat, lng)})"
                )
            }
        }
    } catch (_: Exception) {
        // Fallback to Nominatim search
    }

    // 2. Fallback to OpenStreetMap Nominatim search
    try {
        val encoded = Uri.encode("$cleanQuery, India")
        val urlStr = "https://nominatim.openstreetmap.org/search?format=jsonv2&q=$encoded&limit=1&addressdetails=1"
        val conn = (java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4500
            readTimeout = 4500
            setRequestProperty("User-Agent", "${context.packageName} TrimTime/1.0")
            setRequestProperty("Accept-Language", "en")
        }
        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = org.json.JSONArray(responseText)
            if (arr.length() > 0) {
                val obj = arr.getJSONObject(0)
                val lat = obj.optString("lat").toDoubleOrNull()
                val lng = obj.optString("lon").toDoubleOrNull()
                val displayName = obj.optString("display_name").split(",").take(3).joinToString(",").trim()
                if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
                    return@withContext Triple(
                        lat,
                        lng,
                        "${displayName.ifBlank { cleanQuery }} • (${String.format(java.util.Locale.US, "%.4f, %.4f", lat, lng)})"
                    )
                }
            }
        }
    } catch (_: Exception) {
        // Ignore
    }

    null
}

/**
 * Calculates Haversine distance in kilometers between two GPS coordinates.
 */
fun calculateDistanceKm(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusKm * c
}

/**
 * Generates a high-contrast map pin Drawable for OpenStreetMap markers.
 */
fun createMapPinDrawable(
    context: Context,
    fillColorInt: Int,
    strokeColorInt: Int,
    innerDotColorInt: Int = android.graphics.Color.WHITE,
    isUserDot: Boolean = false
): Drawable {
    val density = context.resources.displayMetrics.density
    if (isUserDot) {
        val sizePx = (26 * density).toInt().coerceAtLeast(28)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = sizePx / 2f
        val cy = sizePx / 2f

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColorInt
            alpha = 75
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, sizePx / 2f, haloPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, sizePx * 0.34f, borderPaint)

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColorInt
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, sizePx * 0.25f, corePaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    val widthPx = (34 * density).toInt().coerceAtLeast(36)
    val heightPx = (44 * density).toInt().coerceAtLeast(46)
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = widthPx / 2f
    val radius = widthPx * 0.40f
    val cy = radius + (4 * density)

    val pinPath = Path().apply {
        addCircle(cx, cy, radius, Path.Direction.CW)
        moveTo(cx - radius * 0.65f, cy + radius * 0.55f)
        lineTo(cx, heightPx - (2 * density))
        lineTo(cx + radius * 0.65f, cy + radius * 0.55f)
        close()
    }

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColorInt
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColorInt
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
    }
    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = innerDotColorInt
        style = Paint.Style.FILL
    }

    canvas.drawPath(pinPath, fillPaint)
    canvas.drawCircle(cx, cy, radius, strokePaint)
    canvas.drawCircle(cx, cy, radius * 0.42f, innerPaint)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Customer Real Interactive OpenStreetMap View:
 * - Configures `Configuration.getInstance().userAgentValue = context.packageName`
 * - Plots all salons from Firestore using their `latitude` and `longitude`
 * - Centers on the user's live GPS location (with fallback to Patna, Bihar `25.5941, 85.1376`)
 * - Floating "My Location" button to request GPS & re-center
 * - Tapping any salon marker shows a bottom popup card with Salon Name, distance in km,
 *   waiting/instant chair status, "Get Directions", and "View & Book" button.
 */
@Composable
fun CustomerSalonsOsmMapSection(
    salons: List<Salon>,
    userLatitude: Double = DEFAULT_PATNA_LAT,
    userLongitude: Double = DEFAULT_PATNA_LNG,
    onGpsLocationDetected: (Double, Double) -> Unit = { _, _ -> },
    onSalonSelected: (Salon) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Configure OSMDroid User-Agent and internal tile cache BEFORE creating MapView
    remember(context) {
        initOsmDroidConfig(context)
        true
    }

    var userLat by remember(userLatitude) {
        mutableDoubleStateOf(if (userLatitude != 0.0) userLatitude else DEFAULT_PATNA_LAT)
    }
    var userLng by remember(userLongitude) {
        mutableDoubleStateOf(if (userLongitude != 0.0) userLongitude else DEFAULT_PATNA_LNG)
    }
    var hasLiveGps by remember { mutableStateOf(hasLocationPermission(context)) }
    var selectedMapSalon by remember(salons) { mutableStateOf(salons.firstOrNull()) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchDeviceGpsLocation(
                context = context,
                fallbackLat = userLat,
                fallbackLng = userLng
            ) { lat, lng, isLive ->
                userLat = lat
                userLng = lng
                hasLiveGps = isLive
                if (isLive) {
                    onGpsLocationDetected(lat, lng)
                }
                mapViewRef?.controller?.setZoom(DEFAULT_CUSTOMER_MAP_ZOOM)
                mapViewRef?.controller?.animateTo(GeoPoint(lat, lng))
                mapViewRef?.invalidate()
            }
        } else {
            Toast.makeText(
                context,
                "GPS permission denied. Tap top location bar to set your location manually.",
                Toast.LENGTH_SHORT
            ).show()
            mapViewRef?.controller?.setZoom(DEFAULT_CUSTOMER_MAP_ZOOM)
            mapViewRef?.controller?.animateTo(GeoPoint(userLat, userLng))
            mapViewRef?.invalidate()
        }
    }

    LaunchedEffect(Unit) {
        initOsmDroidConfig(context)
        if (hasLocationPermission(context)) {
            fetchDeviceGpsLocation(
                context = context,
                fallbackLat = userLat,
                fallbackLng = userLng
            ) { lat, lng, isLive ->
                userLat = lat
                userLng = lng
                hasLiveGps = isLive
                if (isLive) {
                    onGpsLocationDetected(lat, lng)
                }
                mapViewRef?.controller?.setZoom(DEFAULT_CUSTOMER_MAP_ZOOM)
                mapViewRef?.controller?.setCenter(GeoPoint(lat, lng))
                mapViewRef?.invalidate()
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

    LaunchedEffect(userLatitude, userLongitude) {
        if (userLatitude != 0.0 && userLongitude != 0.0) {
            userLat = userLatitude
            userLng = userLongitude
            mapViewRef?.controller?.setZoom(DEFAULT_CUSTOMER_MAP_ZOOM)
            mapViewRef?.controller?.animateTo(GeoPoint(userLatitude, userLongitude))
            mapViewRef?.invalidate()
        }
    }

    DisposableEffect(lifecycleOwner, mapViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onPause()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Map Container Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .testTag("customer_osm_map_container"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(AmberPrimary.copy(alpha = 0.45f))
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("customer_osm_map_view"),
                    factory = { ctx ->
                        initOsmDroidConfig(ctx)
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setUseDataConnection(true)
                            setMultiTouchControls(true)
                            minZoomLevel = 4.0
                            maxZoomLevel = 17.5
                            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
                            overlays.removeAll { it is org.osmdroid.views.overlay.CopyrightOverlay }
                            setOnTouchListener { v, _ ->
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                                false
                            }
                            onResume()
                            controller.setZoom(DEFAULT_CUSTOMER_MAP_ZOOM)
                            controller.setCenter(GeoPoint(userLat, userLng))
                            mapViewRef = this
                        }
                    },
                    update = { mapView ->
                        initOsmDroidConfig(mapView.context)
                        mapView.onResume()
                        if (mapView.zoomLevelDouble > 17.5 || mapView.zoomLevelDouble < 4.0) {
                            mapView.controller.setZoom(DEFAULT_CUSTOMER_MAP_ZOOM)
                        }
                        mapView.overlays.clear()

                        // 1. User's current GPS Location Marker
                        val userMarker = Marker(mapView).apply {
                            position = GeoPoint(userLat, userLng)
                            title = if (hasLiveGps) "Your Live GPS Location" else "Your Selected Location"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = createMapPinDrawable(
                                context = mapView.context,
                                fillColorInt = android.graphics.Color.parseColor("#3B82F6"),
                                strokeColorInt = android.graphics.Color.WHITE,
                                isUserDot = true
                            )
                            setOnMarkerClickListener { marker, _ ->
                                marker.showInfoWindow()
                                true
                            }
                        }
                        mapView.overlays.add(userMarker)

                        // 2. Plot Salon Markers from Firestore
                        salons.forEachIndexed { index, salon ->
                            // Offset slightly if multiple salons share default coordinates so all markers are clickable
                            val sLat = salon.latitude
                            val sLng = salon.longitude
                            val rawLat = if (sLat != null && sLat != 0.0) sLat else userLat + (index * 0.0022)
                            val rawLng = if (sLng != null && sLng != 0.0) sLng else userLng + (index * 0.0022)
                            val isSelected = selectedMapSalon?.id == salon.id

                            val fillColor = when {
                                isSelected -> android.graphics.Color.parseColor("#F59E0B") // Amber highlight
                                salon.isBusy -> android.graphics.Color.parseColor("#EA580C") // Busy Orange/Red
                                else -> android.graphics.Color.parseColor("#10B981") // Available Green
                            }

                            val salonMarker = Marker(mapView).apply {
                                position = GeoPoint(rawLat, rawLng)
                                title = salon.name
                                snippet = if (salon.isBusy) {
                                    "🔴 Barber Busy (15-20m wait)"
                                } else {
                                    "🟢 Available Now • Instant Chair"
                                }
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                icon = createMapPinDrawable(
                                    context = mapView.context,
                                    fillColorInt = fillColor,
                                    strokeColorInt = android.graphics.Color.WHITE
                                )
                                setOnMarkerClickListener { _, _ ->
                                    selectedMapSalon = salon
                                    mapView.controller.animateTo(GeoPoint(rawLat, rawLng))
                                    true
                                }
                            }
                            mapView.overlays.add(salonMarker)
                        }

                        mapView.invalidate()
                    }
                )

                // Top Map Legend Pill
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xD90F172A)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🟢 Free Chair",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                        Text(
                            text = "🔴 Busy",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFDBA74)
                        )
                        Text(
                            text = "🔵 You",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF93C5FD)
                        )
                    }
                }

                // Floating "My Location" Button on the Map
                FloatingActionButton(
                    onClick = {
                        if (hasLocationPermission(context)) {
                            fetchDeviceGpsLocation(
                                context = context,
                                fallbackLat = userLat,
                                fallbackLng = userLng
                            ) { lat, lng, isLive ->
                                userLat = lat
                                userLng = lng
                                hasLiveGps = isLive
                                if (isLive) {
                                    onGpsLocationDetected(lat, lng)
                                }
                                mapViewRef?.controller?.animateTo(GeoPoint(lat, lng))
                                mapViewRef?.controller?.setZoom(15.5)
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
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(48.dp)
                        .testTag("map_my_location_fab"),
                    containerColor = Color(0xFF0F172A),
                    contentColor = AmberPrimary,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "My Location",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Bottom Selected Salon Popup Card when a marker is tapped
        selectedMapSalon?.let { salon ->
            val formattedDistance = remember(userLat, userLng, salon.latitude, salon.longitude, salon.distance) {
                val sLat = salon.latitude
                val sLng = salon.longitude
                if (sLat != null && sLng != null && sLat != 0.0 && sLng != 0.0) {
                    val km = calculateDistanceKm(userLat, userLng, sLat, sLng).coerceAtLeast(0.1)
                    "${"%.1f".format(java.util.Locale.US, km)} km"
                } else {
                    salon.distance.ifBlank { "0.5 km" }
                }
            }
            val isBusy = salon.isBusy
            val minPrice = salon.services.minOfOrNull { it.price } ?: 80

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("map_selected_salon_popup_${salon.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(AmberPrimary)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                                // Live Chair Status Badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isBusy) Color(0xFF451A03) else Color(0xFF064E3B)
                                        )
                                        .border(
                                            1.dp,
                                            if (isBusy) Color(0xFFF97316) else GreenSuccess,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (isBusy) "🔴 Busy (15-20m wait)" else "🟢 Instant Chair Free",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isBusy) Color(0xFFFDBA74) else GreenSuccess
                                    )
                                }

                                // Distance Badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurface)
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Navigation,
                                            contentDescription = null,
                                            tint = AmberPrimary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = formattedDistance,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = salon.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = salon.shopAddress.ifBlank { salon.location },
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${salon.rating}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                IconButton(
                                    onClick = { selectedMapSalon = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close popup",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Starts ₹$minPrice",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberPrimary
                            )
                        }
                    }

                    // Bottom Action Buttons: "Get Directions" & "View & Book"
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
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("map_popup_directions_btn_${salon.id}"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
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

                        Button(
                            onClick = { onSalonSelected(salon) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("map_popup_book_btn_${salon.id}"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberPrimary,
                                contentColor = DarkBackground
                            )
                        ) {
                            Text(
                                text = "View & Book",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
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

/**
 * Partner (Salon Owner) Interactive OpenStreetMap Location Picker:
 * - Wraps MapView inside a clean Box(modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)))
 * - Disables osmdroid CopyrightOverlay so it never renders vertically
 * - Prevents parent vertical scroll from intercepting map touch gestures (requestDisallowInterceptTouchEvent(true))
 */
@Composable
fun PartnerLocationPickerOsmMap(
    latitude: Double,
    longitude: Double,
    salonName: String,
    onLocationPicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember(context) {
        initOsmDroidConfig(context)
        true
    }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner, mapViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onPause()
        }
    }

    // Automatically re-center and set zoom to 15.0 whenever GPS lock or parent updates latitude/longitude
    LaunchedEffect(latitude, longitude) {
        if (latitude != 0.0 && longitude != 0.0) {
            mapViewRef?.controller?.setZoom(DEFAULT_CUSTOMER_MAP_ZOOM)
            mapViewRef?.controller?.animateTo(GeoPoint(latitude, longitude))
            mapViewRef?.invalidate()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                tint = AmberPrimary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Tap map, pinch to zoom, or drag pin to set shop location",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE2E8F0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                .testTag("partner_osm_location_picker_map")
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    initOsmDroidConfig(ctx)
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setUseDataConnection(true)
                        setMultiTouchControls(true)
                        minZoomLevel = 4.0
                        maxZoomLevel = 17.5
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
                        overlays.removeAll { it is org.osmdroid.views.overlay.CopyrightOverlay }
                        setOnTouchListener { v, _ ->
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            false
                        }
                        onResume()
                        val startPoint = GeoPoint(
                            if (latitude != 0.0) latitude else DEFAULT_PATNA_LAT,
                            if (longitude != 0.0) longitude else DEFAULT_PATNA_LNG
                        )
                        controller.setZoom(DEFAULT_CUSTOMER_MAP_ZOOM)
                        controller.setCenter(startPoint)
                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    initOsmDroidConfig(mapView.context)
                    mapView.onResume()
                    mapView.overlays.clear()

                    val currentGeoPoint = GeoPoint(
                        if (latitude != 0.0) latitude else DEFAULT_PATNA_LAT,
                        if (longitude != 0.0) longitude else DEFAULT_PATNA_LNG
                    )

                    // 1. Tap anywhere on map to move shop pin
                    val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                onLocationPicked(p.latitude, p.longitude)
                                mapView.controller.animateTo(p)
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                onLocationPicked(p.latitude, p.longitude)
                                mapView.controller.animateTo(p)
                            }
                            return true
                        }
                    })
                    mapView.overlays.add(mapEventsOverlay)

                    // 2. Draggable Shop Pin Marker
                    val shopMarker = Marker(mapView).apply {
                        position = currentGeoPoint
                        title = salonName.ifBlank { "My Salon Pin" }
                        snippet = "Lat: ${"%.4f".format(currentGeoPoint.latitude)}, Lng: ${"%.4f".format(currentGeoPoint.longitude)}"
                        isDraggable = true
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = createMapPinDrawable(
                            context = mapView.context,
                            fillColorInt = android.graphics.Color.parseColor("#F59E0B"),
                            strokeColorInt = android.graphics.Color.WHITE
                        )
                        setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(marker: Marker?) {}
                            override fun onMarkerDragStart(marker: Marker?) {}
                            override fun onMarkerDragEnd(marker: Marker?) {
                                marker?.position?.let { newPos ->
                                    onLocationPicked(newPos.latitude, newPos.longitude)
                                }
                            }
                        })
                    }
                    mapView.overlays.add(shopMarker)
                    mapView.invalidate()
                }
            )

            // Re-center on current pin button
            FloatingActionButton(
                onClick = {
                    val target = GeoPoint(
                        if (latitude != 0.0) latitude else DEFAULT_PATNA_LAT,
                        if (longitude != 0.0) longitude else DEFAULT_PATNA_LNG
                    )
                    mapViewRef?.controller?.setZoom(15.5)
                    mapViewRef?.controller?.animateTo(target)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(44.dp)
                    .testTag("partner_map_recenter_button"),
                containerColor = Color(0xFF0F172A),
                contentColor = AmberPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Center on Shop Pin",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
