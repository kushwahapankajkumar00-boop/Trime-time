package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppAuthState
import com.example.model.UserRole
import com.example.ui.auth.AuthDialog
import com.example.ui.auth.AuthFullScreen
import com.example.ui.auth.MandatoryPhoneScreen
import com.example.ui.components.AppBottomNav
import com.example.ui.components.AppTopBar
import com.example.ui.components.PinDisplayDialog
import com.example.ui.components.PinVerificationDialog
import com.example.ui.components.TestGuideDialog
import com.example.ui.customer.BookingBottomSheet
import com.example.ui.customer.CustomerBookingsContent
import com.example.ui.customer.CustomerHomeScreen
import com.example.ui.customer.ProfileScreen
import com.example.ui.customer.SalonDetailScreen
import com.example.ui.owner.PartnerDashboardScreen
import com.example.ui.owner.PartnerOnboardingScreen
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.TrimTimeTheme
import com.example.viewmodel.CustomerNavTab
import com.example.viewmodel.TrimTimeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: TrimTimeViewModel = viewModel()
            val appPreferences by mainViewModel.appPreferences.collectAsState()
            TrimTimeTheme(themeMode = appPreferences.themeMode) {
                TrimTimeApp(viewModel = mainViewModel)
            }
        }
    }
}

@Composable
fun TrimTimeApp(
    viewModel: TrimTimeViewModel = viewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val isCloudSynced by viewModel.isCloudSynced.collectAsState()
    val allSalons by viewModel.allSalons.collectAsState()
    val filteredSalons by viewModel.filteredSalons.collectAsState()
    val allBookings by viewModel.allBookings.collectAsState()
    val customerBookings by viewModel.customerBookings.collectAsState()
    val appPreferences by viewModel.appPreferences.collectAsState()
    val hasRegisteredPartnerSalon by viewModel.hasRegisteredPartnerSalon.collectAsState()
    val showPartnerOnboarding by viewModel.showPartnerOnboarding.collectAsState()

    val currentNavTab by viewModel.currentNavTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedSalon by viewModel.selectedSalon.collectAsState()

    val serviceToBook by viewModel.serviceToBook.collectAsState()
    val selectedTimeSlot by viewModel.selectedTimeSlot.collectAsState()
    val newlyConfirmedBooking by viewModel.newlyConfirmedBooking.collectAsState()

    val authMode by viewModel.authMode.collectAsState()
    val isAuthLoading by viewModel.isLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val showSha1FallbackDialog by viewModel.showSha1FallbackDialog.collectAsState()

    // Anti-Spam & Booking Rules State
    val hasActivePendingBooking by viewModel.hasActivePendingBooking.collectAsState()
    val isUserSuspended by viewModel.isUserSuspended.collectAsState()

    val partnerSelectedSalonId by viewModel.partnerSelectedSalonId.collectAsState()
    val partnerBookingFilter by viewModel.partnerBookingFilter.collectAsState()
    val bookingToVerify by viewModel.bookingToVerify.collectAsState()
    val enteredPin by viewModel.enteredPin.collectAsState()
    val pinErrorMessage by viewModel.pinErrorMessage.collectAsState()
    val pinLockoutSeconds by viewModel.pinLockoutSeconds.collectAsState()
    val verificationSuccess by viewModel.verificationSuccess.collectAsState()

    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showHelpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    // Number of pending/accepted/in_progress customer bookings for badge (strictly for the logged-in customer)
    val currentCustomerUid = currentUserProfile?.id?.trim().orEmpty()
    val pendingBookingsCount = customerBookings.count {
        currentCustomerUid.isNotBlank() &&
            it.customerId == currentCustomerUid &&
            com.example.util.SecurityValidator.isBookingActiveStatus(it.status)
    }

    AnimatedContent(
        targetState = authState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "mainAuthStateTransition"
    ) { state ->
        when (state) {
            is AppAuthState.Loading -> {
                // Splash Loading Screen while checking Firebase currentUser
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AmberPrimary,
                            modifier = Modifier.size(42.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Checking session...",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            is AppAuthState.Unauthenticated -> {
                val context = LocalContext.current
                AuthFullScreen(
                    isLoading = isAuthLoading,
                    errorMessage = authError,
                    showSha1FallbackDialog = showSha1FallbackDialog,
                    onDismissSha1FallbackDialog = {
                        viewModel.dismissSha1FallbackDialog()
                    },
                    onLoginExistingUser = { identifier, onNotFound ->
                        viewModel.loginExistingUser(
                            identifier = identifier,
                            onAccountNotFound = onNotFound
                        )
                    },
                    onSignInWithRealIdentity = { name, email, phone, asPartner ->
                        viewModel.signInWithRealIdentity(name, email, phone, asPartner)
                    },
                    onContinueWithGoogle = {
                        viewModel.signInWithGoogle(context)
                    },
                    onDeveloperTestLogin = {
                        viewModel.developerTestLogin()
                    }
                )
            }

            is AppAuthState.Authenticated -> {
                when (state.role) {
                    UserRole.CUSTOMER -> {
                        val currentProfile = currentUserProfile
                        val userPhoneDigits = currentProfile?.phone?.filter { it.isDigit() } ?: ""
                        // Strip country code if 12 digits starting with 91
                        val tenDigits = if (userPhoneDigits.length > 10 && userPhoneDigits.startsWith("91")) {
                            userPhoneDigits.drop(2).take(10)
                        } else {
                            userPhoneDigits.takeLast(10)
                        }
                        val needsPhone = currentProfile == null || tenDigits.length != 10

                        if (needsPhone) {
                            // Rule 1: Mandatory Phone Collection (No OTP)
                            // User must save a 10-digit number before accessing the Home Screen
                            MandatoryPhoneScreen(
                                userName = currentProfile?.name ?: "Customer",
                                userEmail = currentProfile?.email ?: "",
                                isLoading = isAuthLoading,
                                errorMessage = authError,
                                onSavePhone = { validPhone ->
                                    viewModel.savePhoneNumber(validPhone) {
                                        viewModel.setNavTab(CustomerNavTab.SALONS)
                                        viewModel.selectSalon(null)
                                    }
                                },
                                onSignOut = {
                                    viewModel.logout()
                                }
                            )
                        } else if (showPartnerOnboarding) {
                            // Dedicated "Become a Salon Partner" Registration Form (No instant bypass for unregistered users)
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = DarkBackground,
                                snackbarHost = { SnackbarHost(snackbarHostState) }
                            ) { onboardingPadding ->
                                PartnerOnboardingScreen(
                                    userProfile = currentProfile,
                                    isLoading = isAuthLoading,
                                    onCancel = { viewModel.dismissPartnerOnboarding() },
                                    onSubmitRegistration = { salonName, ownerName, businessPhone, shopAddress, cityArea, pincode, landmark, shopCategory, availableChairs, openingTime, closingTime, initialServices, latitude, longitude ->
                                        viewModel.registerPartnerSalon(
                                            salonName = salonName,
                                            ownerName = ownerName,
                                            businessPhone = businessPhone,
                                            shopAddress = shopAddress,
                                            cityArea = cityArea,
                                            pincode = pincode,
                                            landmark = landmark,
                                            shopCategory = shopCategory,
                                            availableChairs = availableChairs,
                                            openingTime = openingTime,
                                            closingTime = closingTime,
                                            initialServices = initialServices,
                                            latitude = latitude,
                                            longitude = longitude
                                        )
                                    },
                                    modifier = Modifier.padding(onboardingPadding)
                                )
                            }
                        } else {
                            // Customer Mode App (Accessible only when logged in as Customer with a verified phone)
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = MaterialTheme.colorScheme.background,
                                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                snackbarHost = { SnackbarHost(snackbarHostState) },
                                topBar = {
                                    if (selectedSalon == null) {
                                        AppTopBar(
                                            isCloudSynced = isCloudSynced,
                                            onHelpClicked = { showHelpDialog = true },
                                            onSwitchToPartnerMode = { viewModel.switchToPartnerRole() }
                                        )
                                    }
                                },
                                bottomBar = {
                                    if (selectedSalon == null) {
                                        AppBottomNav(
                                            currentTab = currentNavTab,
                                            pendingBookingsCount = pendingBookingsCount,
                                            onTabSelected = { viewModel.setNavTab(it) }
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                        .background(MaterialTheme.colorScheme.background)
                                ) {
                                    val activeSalon = selectedSalon
                                    if (activeSalon != null) {
                                        // Salon Detail & Catalog View
                                        SalonDetailScreen(
                                            salon = activeSalon,
                                            hasActiveBooking = hasActivePendingBooking,
                                            isSuspended = isUserSuspended,
                                            onBack = { viewModel.selectSalon(null) },
                                            onServiceSelected = { service ->
                                                viewModel.setServiceToBook(service)
                                            },
                                            onBookingBlocked = { reason ->
                                                viewModel.clearUserMessage()
                                            }
                                        )
                                    } else {
                                        // Bottom Nav Tabs
                                        when (currentNavTab) {
                                            CustomerNavTab.SALONS -> {
                                                val pastBooking = customerBookings.firstOrNull {
                                                    currentCustomerUid.isNotBlank() &&
                                                        it.customerId == currentCustomerUid &&
                                                        it.status.equals("completed", ignoreCase = true)
                                                } ?: customerBookings.firstOrNull {
                                                    currentCustomerUid.isNotBlank() &&
                                                        it.customerId == currentCustomerUid
                                                }
                                                CustomerHomeScreen(
                                                    salons = filteredSalons,
                                                    searchQuery = searchQuery,
                                                    onSearchChanged = { viewModel.setSearchQuery(it) },
                                                    selectedCategory = selectedCategory,
                                                    onCategorySelected = { viewModel.selectCategory(it) },
                                                    onSalonSelected = { viewModel.selectSalon(it) },
                                                    pastBooking = pastBooking,
                                                    onRebook = { booking ->
                                                        val targetSalon = allSalons.find { it.id == booking.salonId || it.name.equals(booking.salonName, ignoreCase = true) }
                                                        viewModel.selectSalon(targetSalon)
                                                    },
                                                    onProfileClick = {
                                                        viewModel.setNavTab(CustomerNavTab.PROFILE)
                                                    }
                                                )
                                            }

                                            CustomerNavTab.MY_BOOKINGS -> {
                                                CustomerBookingsContent(
                                                    bookings = customerBookings,
                                                    currentUserId = currentCustomerUid,
                                                    salons = allSalons,
                                                    cancellationCount = currentUserProfile?.effectiveCancellationStrikes ?: 0,
                                                    isSuspended = isUserSuspended,
                                                    onViewBookingDetails = { /* detail */ },
                                                    onCancelBooking = { viewModel.cancelBooking(it) }
                                                )
                                            }

                                            CustomerNavTab.PROFILE -> {
                                                val ownedSalon = allSalons.find {
                                                    currentCustomerUid.isNotBlank() &&
                                                        (it.ownerId == currentCustomerUid || it.partnerId == currentCustomerUid || it.id == currentUserProfile?.salonId)
                                                }
                                                ProfileScreen(
                                                    userProfile = currentUserProfile,
                                                    appPreferences = appPreferences,
                                                    hasRegisteredSalon = hasRegisteredPartnerSalon,
                                                    registeredSalonName = ownedSalon?.name,
                                                    onOpenAuth = { viewModel.openAuth(it) },
                                                    onSwitchToPartner = { viewModel.switchToPartnerRole() },
                                                    onUpdatePersonalProfile = { name, phone, city ->
                                                        viewModel.updatePersonalProfile(name, phone, city)
                                                    },
                                                    onUpdateAccountIdentity = { name, email, phone, asPartner ->
                                                        viewModel.signInWithRealIdentity(name, email, phone, asPartner)
                                                    },
                                                    onThemeModeChange = { viewModel.setThemeMode(it) },
                                                    onLanguageChange = { viewModel.setLanguage(it) },
                                                    onNotificationPreferencesChange = { bookingAlerts, queueReminders, offersDiscounts ->
                                                        viewModel.setNotificationPreferences(bookingAlerts, queueReminders, offersDiscounts)
                                                    },
                                                    onClearCache = { viewModel.clearAppCache() },
                                                    onOpenHelp = { showHelpDialog = true },
                                                    onSubmitFeedback = { rating, category, msg, onDone ->
                                                        viewModel.submitFeedback(rating, category, msg, onDone)
                                                    },
                                                    onLogout = { viewModel.logout() }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    UserRole.PARTNER -> {
                        // Partner Mode App (Accessible with 1-tap switcher between Customer & Partner)
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = DarkBackground,
                            snackbarHost = { SnackbarHost(snackbarHostState) }
                        ) { innerPadding ->
                            PartnerDashboardScreen(
                                salons = allSalons,
                                partnerProfile = currentUserProfile,
                                selectedSalonId = partnerSelectedSalonId,
                                onSelectSalonId = { viewModel.setPartnerSelectedSalonId(it) },
                                allBookings = allBookings,
                                activeFilter = partnerBookingFilter,
                                onFilterChange = { viewModel.setPartnerBookingFilter(it) },
                                onOpenPinVerification = { viewModel.openPinVerificationDialog(it) },
                                onAcceptBooking = { viewModel.acceptBooking(it) },
                                onRejectBooking = { viewModel.rejectBooking(it) },
                                onToggleSalonOpen = { viewModel.toggleSalonOpenStatus(it) },
                                onSetSalonOpen = { id, isOpen -> viewModel.setSalonOpenStatus(id, isOpen) },
                                onSetSalonBusyStatus = { id, status -> viewModel.setSalonBusyStatus(id, status) },
                                onUpdateChairAvailability = { id, currentStatus, chairs, waitMins ->
                                    viewModel.updateSalonChairAvailability(id, currentStatus, chairs, waitMins)
                                },
                                onSaveMenuPrices = { id, h, s, f, m -> viewModel.saveSalonMenuPrices(id, h, s, f, m) },
                                onAddService = { id, name, category, price, duration, description ->
                                    viewModel.addSalonService(id, name, category, price, duration, description)
                                },
                                onEditService = { id, updatedService ->
                                    viewModel.editSalonService(id, updatedService)
                                },
                                onDeleteService = { id, serviceId, serviceName ->
                                    viewModel.deleteSalonService(id, serviceId, serviceName)
                                },
                                onSaveShopLocation = { id, address, landmark, lat, lng ->
                                    viewModel.saveSalonLocation(id, address, landmark, lat, lng)
                                },
                                onSaveFullProfile = { id, name, phone, tagline, address, landmark, lat, lng, availableChairs ->
                                    viewModel.saveSalonFullProfile(id, name, phone, tagline, address, landmark, lat, lng, availableChairs)
                                },
                                onSaveShopPhoto = { id, photoDataUrl ->
                                    viewModel.saveSalonPhoto(id, photoDataUrl)
                                },
                                onSubmitFeedback = { rating, category, msg, onDone ->
                                    viewModel.submitFeedback(rating, category, msg, onDone)
                                },
                                onExitPartnerMode = { viewModel.switchToCustomerRole() },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }

    // Customer Booking Bottom Sheet
    if (serviceToBook != null && selectedSalon != null) {
        BookingBottomSheet(
            salon = selectedSalon!!,
            service = serviceToBook!!,
            userProfile = currentUserProfile ?: com.example.model.UserProfile(name = "Customer", phone = "+91 98765 43210"),
            selectedSlot = selectedTimeSlot,
            hasActiveBooking = hasActivePendingBooking,
            isSuspended = isUserSuspended,
            onSlotSelected = { viewModel.setSelectedTimeSlot(it) },
            onConfirm = { name, phone ->
                viewModel.confirmBooking(name, phone)
            },
            onDismiss = { viewModel.setServiceToBook(null) }
        )
    }

    // Customer Booking Confirmation Dialog (displays 4-Digit PIN)
    newlyConfirmedBooking?.let { confirmedBooking ->
        PinDisplayDialog(
            booking = confirmedBooking,
            onDismiss = { viewModel.dismissConfirmationDialog() },
            onViewBookings = {
                viewModel.selectSalon(null)
                viewModel.setNavTab(CustomerNavTab.MY_BOOKINGS)
            }
        )
    }

    // Partner Anti-Fake Service PIN Verification Dialog
    bookingToVerify?.let { booking ->
        PinVerificationDialog(
            booking = booking,
            enteredPin = enteredPin,
            onPinChanged = { viewModel.setEnteredPin(it) },
            errorMessage = pinErrorMessage,
            isSuccess = verificationSuccess,
            lockoutRemainingSeconds = pinLockoutSeconds,
            onVerifyClick = { viewModel.verifyAndCompleteService() },
            onDismiss = { viewModel.dismissPinVerificationDialog() }
        )
    }

    // Auth Dialog for in-app mode switching (e.g. discreet Partner Portal Login from Profile)
    AuthDialog(
        authMode = authMode,
        isLoading = isAuthLoading,
        errorMessage = authError,
        onSwitchToPartner = {
            viewModel.switchToPartnerRole()
        },
        onDismiss = { viewModel.dismissAuth() }
    )

    // How to Test Guide Dialog
    if (showHelpDialog) {
        TestGuideDialog(
            onDismiss = { showHelpDialog = false }
        )
    }
}

