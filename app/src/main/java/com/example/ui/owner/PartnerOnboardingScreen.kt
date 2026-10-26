package com.example.ui.owner

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SalonService
import com.example.model.UserProfile
import com.example.ui.components.DEFAULT_PATNA_LAT
import com.example.ui.components.DEFAULT_PATNA_LNG
import com.example.ui.components.LegalPoliciesViewerDialog
import com.example.ui.components.LegalPolicyTab
import com.example.ui.components.PartnerLocationPickerOsmMap
import com.example.ui.components.fetchDeviceGpsLocation
import com.example.ui.components.hasLocationPermission
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenSuccess

@Composable
fun PartnerOnboardingScreen(
    userProfile: UserProfile?,
    isLoading: Boolean,
    onCancel: () -> Unit,
    onSubmitRegistration: (
        salonName: String,
        ownerName: String,
        businessPhone: String,
        shopAddress: String,
        cityArea: String,
        pincode: String,
        landmark: String,
        shopCategory: String,
        availableChairs: Int,
        openingTime: String,
        closingTime: String,
        initialServices: List<SalonService>,
        latitude: Double,
        longitude: Double
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onCancel()
    }

    val context = LocalContext.current

    // 1. Salon & Owner Details
    var salonName by remember { mutableStateOf("") }
    var ownerName by remember(userProfile?.name) {
        mutableStateOf(
            userProfile?.name?.takeIf { it.isNotBlank() && it != "Customer" } ?: ""
        )
    }
    var businessPhone by remember(userProfile?.phone) {
        val digits = userProfile?.phone?.filter { it.isDigit() }.orEmpty()
        val ten = if (digits.length > 10 && digits.startsWith("91")) digits.drop(2).take(10) else digits.takeLast(10)
        mutableStateOf(ten)
    }

    // 2. Full Shop Address, City/Area, Pincode & Landmark
    var shopAddress by remember { mutableStateOf("") }
    var cityArea by remember(userProfile?.defaultCity) {
        mutableStateOf(userProfile?.defaultCity?.ifBlank { "Patna, Bihar" } ?: "Patna, Bihar")
    }
    var pincode by remember { mutableStateOf("") }
    var landmark by remember { mutableStateOf("") }

    // 3. Shop Category & Total Available Chairs
    val categories = listOf("Men's Salon", "Unisex Studio", "Beauty Parlour")
    var selectedCategory by remember { mutableStateOf(categories.first()) }
    var availableChairs by remember { mutableIntStateOf(2) }

    // 4. Opening & Closing Timings
    val openingOptions = listOf("8:00 AM", "9:00 AM", "10:00 AM")
    val closingOptions = listOf("8:00 PM", "9:00 PM", "10:00 PM")
    var openingTime by remember { mutableStateOf("9:00 AM") }
    var closingTime by remember { mutableStateOf("9:00 PM") }

    // 5. Initial Services (At least 1 required)
    val initialServices = remember {
        mutableStateListOf(
            SalonService(
                id = "init_srv_1",
                name = "Haircut",
                category = "Haircut",
                price = 100,
                durationMinutes = 20,
                description = "Standard haircut & styling",
                isPopular = true
            ),
            SalonService(
                id = "init_srv_2",
                name = "Beard Trim & Shave",
                category = "Beard",
                price = 60,
                durationMinutes = 15,
                description = "Clean shave & beard lineup",
                isPopular = true
            )
        )
    }
    var newServiceName by remember { mutableStateOf("") }
    var newServicePrice by remember { mutableStateOf("") }
    var newServiceDuration by remember { mutableStateOf("20") }
    var newServiceCategory by remember { mutableStateOf("Haircut") }

    // 6. GPS / Map Location Pin & Terms Confirmation
    var latitude by remember { mutableDoubleStateOf(DEFAULT_PATNA_LAT) }
    var longitude by remember { mutableDoubleStateOf(DEFAULT_PATNA_LNG) }
    var gpsLocationConfirmed by remember { mutableStateOf(false) }
    var termsAccepted by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchDeviceGpsLocation(context) { lat, lng, _ ->
                latitude = lat
                longitude = lng
                gpsLocationConfirmed = true
                Toast.makeText(context, "Shop GPS pin updated to current location!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "GPS permission denied. Tap on the map to place your shop pin.", Toast.LENGTH_SHORT).show()
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AmberPrimary,
        unfocusedBorderColor = Color(0xFF334155),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = AmberPrimary,
        unfocusedLabelColor = Color(0xFF94A3B8),
        cursorColor = AmberPrimary
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("partner_onboarding_screen")
    ) {
        // Top Header Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF0B1120),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(DarkSurface)
                            .testTag("partner_onboarding_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Customer Mode",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Become a Salon Partner",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Complete registration to list your shop live on TrimTime",
                            fontSize = 11.sp,
                            color = AmberPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Banner Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                    border = BorderStroke(1.dp, Color(0xFF3B82F6))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(AmberPrimary, Color(0xFFD97706))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PARTNER SALON REGISTRATION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF93C5FD),
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Register your salon once to unlock the Partner Dashboard, Live Chair Queue & Anti-Fake PIN verification.",
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // STEP 1: Salon Name, Owner Name & 10-Digit Business Phone
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionStepHeader(
                            stepNumber = "1",
                            title = "Salon & Owner Identity",
                            subtitle = "Official shop name and business contact number"
                        )

                        OutlinedTextField(
                            value = salonName,
                            onValueChange = {
                                salonName = it
                                validationError = null
                            },
                            label = { Text("Salon / Shop Name *") },
                            placeholder = { Text("e.g., Royal Cuts Grooming Studio", color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = AmberPrimary)
                            },
                            singleLine = true,
                            colors = textFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("partner_reg_salon_name_input")
                        )

                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = {
                                ownerName = it
                                validationError = null
                            },
                            label = { Text("Owner Full Name *") },
                            placeholder = { Text("e.g., Rajesh Sharma", color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = AmberPrimary)
                            },
                            singleLine = true,
                            colors = textFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("partner_reg_owner_name_input")
                        )

                        OutlinedTextField(
                            value = businessPhone,
                            onValueChange = { input ->
                                val digits = input.filter { it.isDigit() }.take(10)
                                businessPhone = digits
                                validationError = null
                            },
                            label = { Text("10-Digit Business Phone Number *") },
                            placeholder = { Text("9876543210", color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = AmberPrimary)
                            },
                            prefix = { Text("+91 ", color = AmberPrimary, fontWeight = FontWeight.Bold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            colors = textFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("partner_reg_phone_input")
                        )
                    }
                }
            }

            // STEP 2: Full Shop Address, City/Area & Pincode
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionStepHeader(
                            stepNumber = "2",
                            title = "Shop Address & Location Details",
                            subtitle = "Helps nearby customers find your salon easily"
                        )

                        OutlinedTextField(
                            value = shopAddress,
                            onValueChange = {
                                shopAddress = it
                                validationError = null
                            },
                            label = { Text("Full Shop Street Address *") },
                            placeholder = { Text("Shop No. 12, Boring Road Crossing", color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = AmberPrimary)
                            },
                            singleLine = false,
                            maxLines = 2,
                            colors = textFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("partner_reg_address_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = cityArea,
                                onValueChange = {
                                    cityArea = it
                                    validationError = null
                                },
                                label = { Text("City / Area *") },
                                placeholder = { Text("Patna", color = Color(0xFF64748B)) },
                                singleLine = true,
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("partner_reg_city_input")
                            )

                            OutlinedTextField(
                                value = pincode,
                                onValueChange = { input ->
                                    pincode = input.filter { it.isDigit() }.take(6)
                                    validationError = null
                                },
                                label = { Text("Pincode *") },
                                placeholder = { Text("800001", color = Color(0xFF64748B)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("partner_reg_pincode_input")
                            )
                        }

                        OutlinedTextField(
                            value = landmark,
                            onValueChange = { landmark = it },
                            label = { Text("Nearby Landmark (Optional)") },
                            placeholder = { Text("Opposite Metro Pillar 42 / Near Central Mall", color = Color(0xFF64748B)) },
                            singleLine = true,
                            colors = textFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("partner_reg_landmark_input")
                        )
                    }
                }
            }

            // STEP 3: Shop Category, Total Available Chairs & Timings
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SectionStepHeader(
                            stepNumber = "3",
                            title = "Shop Category, Chairs & Timings",
                            subtitle = "Configure salon type, seating capacity & working hours"
                        )

                        Text(
                            text = "Select Shop Category *",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E8F0)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { category ->
                                val isSelected = selectedCategory == category
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
                                        .clickable { selectedCategory = category }
                                        .padding(vertical = 10.dp, horizontal = 6.dp)
                                        .testTag("partner_reg_category_${category.lowercase().replace(" ", "_")}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = category,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) Color.Black else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Total Available Chairs Counter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurface)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventSeat,
                                    contentDescription = null,
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Total Available Chairs *",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Active grooming chairs in your shop",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { if (availableChairs > 1) availableChairs-- },
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, AmberPrimary)
                                ) {
                                    Text("-", color = AmberPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                }
                                Text(
                                    text = "$availableChairs",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AmberPrimary,
                                    modifier = Modifier.testTag("partner_reg_chairs_value")
                                )
                                OutlinedButton(
                                    onClick = { if (availableChairs < 15) availableChairs++ },
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, AmberPrimary)
                                ) {
                                    Text("+", color = AmberPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                }
                            }
                        }

                        // Opening & Closing Timings
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Opening & Closing Timings *",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = openingTime,
                                onValueChange = { openingTime = it },
                                label = { Text("Opening Time *") },
                                singleLine = true,
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("partner_reg_opening_time_input")
                            )

                            OutlinedTextField(
                                value = closingTime,
                                onValueChange = { closingTime = it },
                                label = { Text("Closing Time *") },
                                singleLine = true,
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("partner_reg_closing_time_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            openingOptions.forEach { preset ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (openingTime == preset) AmberPrimary.copy(alpha = 0.2f) else DarkSurface)
                                        .border(
                                            1.dp,
                                            if (openingTime == preset) AmberPrimary else Color(0xFF334155),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { openingTime = preset }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Open $preset",
                                        fontSize = 10.sp,
                                        color = if (openingTime == preset) AmberPrimary else Color(0xFF94A3B8),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            closingOptions.take(2).forEach { preset ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (closingTime == preset) AmberPrimary.copy(alpha = 0.2f) else DarkSurface)
                                        .border(
                                            1.dp,
                                            if (closingTime == preset) AmberPrimary else Color(0xFF334155),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { closingTime = preset }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Close $preset",
                                        fontSize = 10.sp,
                                        color = if (closingTime == preset) AmberPrimary else Color(0xFF94A3B8),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // STEP 4: Initial Services & Pricing (At least 1 required)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionStepHeader(
                            stepNumber = "4",
                            title = "Initial Services & Price Menu",
                            subtitle = "Add at least 1 service with price in ₹ (editable anytime later)"
                        )

                        initialServices.forEachIndexed { index, srv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCut,
                                        contentDescription = null,
                                        tint = AmberPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = srv.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${srv.category} • ${srv.durationMinutes} mins",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "₹${srv.price}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = GreenSuccess
                                    )
                                    if (initialServices.size > 1) {
                                        IconButton(
                                            onClick = { initialServices.removeAt(index) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Remove service",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFF334155))

                        Text(
                            text = "Add Another Service to Menu",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberPrimary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newServiceName,
                                onValueChange = { newServiceName = it },
                                label = { Text("Service Name") },
                                placeholder = { Text("e.g., Facial", color = Color(0xFF64748B)) },
                                singleLine = true,
                                colors = textFieldColors,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .testTag("partner_reg_new_service_name")
                            )

                            OutlinedTextField(
                                value = newServicePrice,
                                onValueChange = { newServicePrice = it.filter { ch -> ch.isDigit() }.take(4) },
                                label = { Text("Price (₹)") },
                                placeholder = { Text("150", color = Color(0xFF64748B)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = textFieldColors,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("partner_reg_new_service_price")
                            )

                            OutlinedTextField(
                                value = newServiceDuration,
                                onValueChange = { newServiceDuration = it.filter { ch -> ch.isDigit() }.take(3) },
                                label = { Text("Mins") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = textFieldColors,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(0.75f)
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val cleanSrvName = newServiceName.trim()
                                val priceVal = newServicePrice.toIntOrNull() ?: 0
                                val durVal = (newServiceDuration.toIntOrNull() ?: 20).coerceIn(5, 180)
                                if (cleanSrvName.isBlank() || priceVal < 10) {
                                    validationError = "Enter a valid service name and price (at least ₹10)."
                                } else {
                                    initialServices.add(
                                        SalonService(
                                            id = "init_srv_${System.currentTimeMillis()}",
                                            name = cleanSrvName,
                                            category = newServiceCategory,
                                            price = priceVal,
                                            durationMinutes = durVal,
                                            description = "$cleanSrvName ($durVal mins)",
                                            isPopular = true
                                        )
                                    )
                                    newServiceName = ""
                                    newServicePrice = ""
                                    validationError = null
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("partner_reg_add_service_btn"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AmberPrimary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add Service to Initial Menu",
                                color = AmberPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // STEP 5: GPS / OpenStreetMap Location Pin & Terms Confirmation
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionStepHeader(
                            stepNumber = "5",
                            title = "GPS Map Pin & Partner Agreement",
                            subtitle = "Pin your exact shop location on OpenStreetMap"
                        )

                        Button(
                            onClick = {
                                if (hasLocationPermission(context)) {
                                    fetchDeviceGpsLocation(context) { lat, lng, _ ->
                                        latitude = lat
                                        longitude = lng
                                        gpsLocationConfirmed = true
                                        Toast.makeText(
                                            context,
                                            "GPS coordinates locked: ${"%.4f".format(lat)}, ${"%.4f".format(lng)}",
                                            Toast.LENGTH_SHORT
                                        ).show()
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
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("partner_reg_use_gps_btn"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E3A8A),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = Color(0xFF93C5FD),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Use Current GPS Location for Shop Pin",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        PartnerLocationPickerOsmMap(
                            latitude = latitude,
                            longitude = longitude,
                            salonName = salonName.ifBlank { "New Partner Salon" },
                            onLocationPicked = { lat, lng ->
                                latitude = lat
                                longitude = lng
                                gpsLocationConfirmed = true
                            }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurface)
                                .clickable { gpsLocationConfirmed = !gpsLocationConfirmed }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("partner_reg_gps_confirm_checkbox"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = gpsLocationConfirmed,
                                onCheckedChange = {
                                    gpsLocationConfirmed = it
                                    validationError = null
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = GreenSuccess,
                                    uncheckedColor = Color(0xFF94A3B8)
                                )
                            )
                            Text(
                                text = "I confirm the map pin (${String.format(java.util.Locale.US, "%.4f, %.4f", latitude, longitude)}) marks my salon location *",
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        var activeLegalTab by remember { mutableStateOf<LegalPolicyTab?>(null) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurface)
                                .clickable { termsAccepted = !termsAccepted }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("partner_reg_terms_checkbox"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = termsAccepted,
                                onCheckedChange = {
                                    termsAccepted = it
                                    validationError = null
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AmberPrimary,
                                    uncheckedColor = Color(0xFF94A3B8)
                                )
                            )
                            Text(
                                text = "I agree to TrimTime Partner Terms (₹0 Advance for customers, Pay at Salon & 4-Digit Anti-Fake PIN verification) *",
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { activeLegalTab = LegalPolicyTab.TERMS },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("partner_reg_view_terms_btn"),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF475569))
                            ) {
                                Text(
                                    text = "View Terms & Conditions",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberPrimary
                                )
                            }
                            OutlinedButton(
                                onClick = { activeLegalTab = LegalPolicyTab.PRIVACY },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("partner_reg_view_privacy_btn"),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF475569))
                            ) {
                                Text(
                                    text = "View Privacy Policy",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF93C5FD)
                                )
                            }
                        }

                        activeLegalTab?.let { tab ->
                            LegalPoliciesViewerDialog(
                                initialTab = tab,
                                onDismiss = { activeLegalTab = null }
                            )
                        }
                    }
                }
            }

            // Validation Error Banner
            if (!validationError.isNullOrBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3F151B)),
                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Text(
                            text = validationError ?: "",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Submit Partner Salon Registration Button
            item {
                Button(
                    onClick = {
                        val cleanShop = salonName.trim()
                        val cleanOwner = ownerName.trim()
                        val phoneDigits = businessPhone.filter { it.isDigit() }
                        val cleanAddr = shopAddress.trim()
                        val cleanCity = cityArea.trim()
                        val cleanPin = pincode.filter { it.isDigit() }

                        when {
                            cleanShop.length < 3 -> {
                                validationError = "Please enter your Salon / Shop Name (at least 3 characters)."
                            }
                            cleanOwner.length < 2 -> {
                                validationError = "Please enter the Owner Full Name."
                            }
                            phoneDigits.length != 10 -> {
                                validationError = "Please enter a valid 10-digit Business Phone Number."
                            }
                            cleanAddr.length < 5 -> {
                                validationError = "Please enter the full Shop Street Address."
                            }
                            cleanCity.length < 2 -> {
                                validationError = "Please enter your City / Area."
                            }
                            cleanPin.length != 6 -> {
                                validationError = "Please enter a valid 6-digit Pincode."
                            }
                            openingTime.isBlank() || closingTime.isBlank() -> {
                                validationError = "Please specify Opening and Closing timings."
                            }
                            initialServices.isEmpty() -> {
                                validationError = "Please add at least 1 initial service with price."
                            }
                            !gpsLocationConfirmed -> {
                                validationError = "Please confirm your salon's GPS location pin on the map."
                            }
                            !termsAccepted -> {
                                validationError = "Please accept the TrimTime Partner Terms & Conditions."
                            }
                            else -> {
                                validationError = null
                                onSubmitRegistration(
                                    cleanShop,
                                    cleanOwner,
                                    phoneDigits,
                                    cleanAddr,
                                    cleanCity,
                                    cleanPin,
                                    landmark.trim(),
                                    selectedCategory,
                                    availableChairs,
                                    openingTime.trim(),
                                    closingTime.trim(),
                                    initialServices.toList(),
                                    latitude,
                                    longitude
                                )
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("partner_reg_submit_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPrimary,
                        contentColor = Color.Black
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Registering Salon in Cloud Firestore...",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Register Salon & Launch Partner Dashboard",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SectionStepHeader(
    stepNumber: String,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(AmberPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}
