package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppAuthState
import com.example.model.UserRole
import com.example.service.TrimTimeNotificationHelper
import com.example.ui.auth.AuthDialog
import com.example.ui.auth.AuthFullScreen
import com.example.ui.auth.PhoneSetupScreen
import com.example.ui.components.AppBottomNav
import com.example.ui.components.AppTopBar
import com.example.ui.components.PinDisplayDialog
import com.example.ui.components.PinVerificationDialog
import com.example.ui.components.TrimTimeSplashScreen
import com.example.ui.customer.BookingBottomSheet
import com.example.ui.customer.CustomerBookingsContent
import com.example.ui.customer.CustomerHomeScreen
import com.example.ui.customer.MyBookingsScreen
import com.example.ui.customer.ProfileScreen
import com.example.ui.customer.SalonDetailScreen
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.owner.PartnerDashboardScreen
import com.example.ui.owner.PartnerOnboardingScreen
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.TrimTimeTheme
import com.example.viewmodel.CustomerNavTab
import com.example.viewmodel.TrimTimeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.ui.components.initOsmDroidConfig(this)
        TrimTimeNotificationHelper.ensureNotificationChannel(this)
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
    val appContext = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val isCloudSynced by viewModel.isCloudSynced.collectAsState()
    val allSalons by viewModel.allSalons.collectAsState()
    val filteredSalons by viewModel.filteredSalons.collectAsState()
    val allBookings by viewModel.allBookings.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val supportTickets by viewModel.supportTickets.collectAsState()
    val showAdminDashboard by viewModel.showAdminDashboard.collectAsState()
    val isSuperAdmin by viewModel.isSuperAdmin.collectAsState()
    val customerBookings by viewModel.customerBookings.collectAsState()
    val latestCompletedBooking by viewModel.latestCompletedBooking.collectAsState()
    val appPreferences by viewModel.appPreferences.collectAsState()
    val isGlobalRushModeEnabled by viewModel.isGlobalRushModeEnabled.collectAsState()
    val hasRegisteredPartnerSalon by viewModel.hasRegisteredPartnerSalon.collectAsState()
    val showPartnerOnboarding by viewModel.showPartnerOnboarding.collectAsState()

    val userLatitude by viewModel.userLatitude.collectAsState()
    val userLongitude by viewModel.userLongitude.collectAsState()
    val userLocationTitle by viewModel.userLocationTitle.collectAsState()
    val userLocationSubtitle by viewModel.userLocationSubtitle.collectAsState()
    val isLiveGpsActive by viewModel.isLiveGpsActive.collectAsState()

    val currentNavTab by viewModel.currentNavTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedSalon by viewModel.selectedSalon.collectAsState()

    val serviceToBook by viewModel.serviceToBook.collectAsState()
    val selectedTimeSlot by viewModel.selectedTimeSlot.collectAsState()
    val newlyConfirmedBooking by viewModel.newlyConfirmedBooking.collectAsState()
    val showConfirmationDialog by viewModel.showConfirmationDialog.collectAsState()
    val showSalonClosedDialog by viewModel.showSalonClosedDialog.collectAsState()

    val authMode by viewModel.authMode.collectAsState()
    val isAuthLoading by viewModel.isLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()

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

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            viewModel.clearUserMessage()
            val snackbarJob = launch {
                snackbarHostState.showSnackbar(msg)
            }
            delay(1300L)
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarJob.cancel()
        }
    }

    // Number of pending/accepted/in_progress customer bookings for badge (strictly for the logged-in customer)
    val currentCustomerUid = currentUserProfile?.id?.trim().orEmpty()
    val pendingBookingsCount = customerBookings.count {
        currentCustomerUid.isNotBlank() &&
            it.customerId == currentCustomerUid &&
            com.example.util.SecurityValidator.isBookingActiveStatus(it.status)
    }

    // Retrieve FCM device token upon successful login and persist `fcmToken` to the user's Firestore profile
    LaunchedEffect(currentCustomerUid, currentUserProfile?.salonId) {
        if (currentCustomerUid.isNotBlank()) {
            TrimTimeNotificationHelper.fetchAndSaveCurrentUserFcmToken(
                context = appContext,
                userId = currentCustomerUid,
                salonId = currentUserProfile?.salonId
            )
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        TrimTimeNotificationHelper.ensureNotificationChannel(appContext)
    }

    val favoriteSalonIds by viewModel.favoriteSalonIds.collectAsState()
    var showSplashScreen by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1500L)
        showSplashScreen = false
    }

    // Request POST_NOTIFICATIONS permission on Android 13+ when reaching the Auth/Home screen
    LaunchedEffect(showSplashScreen, authState) {
        if (!showSplashScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (showSplashScreen) {
        TrimTimeSplashScreen()
        return
    }

    AnimatedContent(
        targetState = authState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "mainAuthStateTransition"
    ) { state ->
        when (state) {
            is AppAuthState.Loading -> {
                TrimTimeSplashScreen()
            }

            is AppAuthState.Unauthenticated -> {
                val context = LocalContext.current
                AuthFullScreen(
                    isLoading = isAuthLoading,
                    errorMessage = authError,
                    onContinueWithGoogle = {
                        viewModel.signInWithGoogle(context)
                    },
                    onLoginWithEmail = { email, password ->
                        viewModel.signInWithEmail(email, password)
                    },
                    onSignUpWithEmail = { fullName, email, password ->
                        viewModel.signUpWithEmail(fullName, email, password)
                    },
                    onClearError = {
                        viewModel.clearAuthError()
                    }
                )
            }

            is AppAuthState.Authenticated -> {
                val currentProfile = currentUserProfile
                if (showAdminDashboard && (isSuperAdmin || currentUserProfile?.isAdmin == true)) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = DarkBackground,
                        snackbarHost = { SnackbarHost(snackbarHostState) }
                    ) { adminPadding ->
                        Box(modifier = Modifier.padding(adminPadding)) {
                            AdminDashboardScreen(
                                salons = allSalons,
                                users = allUsers,
                                supportTickets = supportTickets,
                                onBack = { viewModel.closeAdminDashboard() },
                                onUpdateSalonApproval = { salonId, status, suspended ->
                                    viewModel.updateSalonApproval(salonId, status, suspended)
                                },
                                onUpdateSalonCategory = { partnerId, tag ->
                                    viewModel.updateSalonCategory(partnerId, tag)
                                },
                                onUpdateSalonStatus = { partnerId, status ->
                                    viewModel.updateSalonStatus(partnerId, status)
                                },
                                onUpdateUserBlockStatus = { userId, blocked ->
                                    viewModel.updateUserBlockStatus(userId, blocked)
                                },
                                onResolveSupportTicket = { ticketId, resolved ->
                                    viewModel.resolveSupportTicket(ticketId, resolved)
                                },
                                onApproveLocationUnlock = { userId, salonId ->
                                    viewModel.approveLocationUnlock(userId, salonId)
                                },
                                isGlobalRushModeEnabled = isGlobalRushModeEnabled,
                                onToggleGlobalRushMode = { enabled ->
                                    viewModel.setGlobalRushModeEnabled(enabled)
                                }
                            )
                        }
                    }
                } else when (state.role) {
                    UserRole.CUSTOMER -> {
                            if (showPartnerOnboarding) {
                                // Mandatory "Partner Registration Form" (No 1-click bypass for unregistered users)
                                Scaffold(
                                    modifier = Modifier.fillMaxSize(),
                                    containerColor = DarkBackground,
                                    snackbarHost = { SnackbarHost(snackbarHostState) }
                                ) { onboardingPadding ->
                                    PartnerOnboardingScreen(
                                        userProfile = currentProfile,
                                        isLoading = isAuthLoading,
                                        onCancel = { viewModel.dismissPartnerOnboarding() },
                                        onSubmitRegistration = { salonName, ownerName, businessPhone, shopAddress, cityArea, pincode, landmark, shopCategory, availableChairs, openingTime, closingTime, initialServices, latitude, longitude, isAcSalon, imageUrl ->
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
                                                longitude = longitude,
                                                isPhoneVerified = true,
                                                isAcSalon = isAcSalon,
                                                imageUrl = imageUrl
                                            )
                                        },
                                        modifier = Modifier.padding(onboardingPadding)
                                    )
                                }
                            } else {
                                // Customer Mode App
                                Scaffold(
                                    modifier = Modifier.fillMaxSize(),
                                    containerColor = MaterialTheme.colorScheme.background,
                                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                    snackbarHost = { SnackbarHost(snackbarHostState) },
                                    topBar = {
                                        if (selectedSalon == null && currentNavTab != CustomerNavTab.MY_BOOKINGS) {
                                            AppTopBar(
                                                isCloudSynced = isCloudSynced,
                                                hasRegisteredPartnerSalon = hasRegisteredPartnerSalon,
                                                onSwitchToPartnerMode = {
                                                    if (hasRegisteredPartnerSalon) {
                                                        viewModel.switchToPartnerRole()
                                                    } else {
                                                        viewModel.openPartnerRegistrationForm()
                                                    }
                                                }
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
                                        val activeSalon = selectedSalon?.let { sel ->
                                            allSalons.find { it.id == sel.id } ?: filteredSalons.find { it.id == sel.id } ?: sel
                                        }
                                        if (activeSalon != null) {
                                            val isActiveSalonFav = activeSalon.id in favoriteSalonIds ||
                                                (activeSalon.partnerId.isNotBlank() && activeSalon.partnerId in favoriteSalonIds) ||
                                                (activeSalon.ownerId.isNotBlank() && activeSalon.ownerId in favoriteSalonIds)
                                            val activeSalonQueueCount = com.example.model.Booking.countActiveInQueueToday(
                                                bookings = allBookings,
                                                salonId = activeSalon.id,
                                                partnerId = activeSalon.partnerId,
                                                ownerId = activeSalon.ownerId
                                            )
                                            SalonDetailScreen(
                                                salon = activeSalon,
                                                currentUserId = if (isSuperAdmin || currentUserProfile?.isWhitelistedAdminOrDev == true) "" else currentCustomerUid,
                                                activeLiveQueueCount = activeSalonQueueCount,
                                                isGlobalRushModeEnabled = isGlobalRushModeEnabled,
                                                hasActiveBooking = hasActivePendingBooking,
                                                isSuspended = isUserSuspended,
                                                isFavorite = isActiveSalonFav,
                                                onToggleFavorite = {
                                                    viewModel.toggleFavoriteSalon(activeSalon)
                                                },
                                                onBack = { viewModel.selectSalon(null) },
                                                onServiceSelected = { service ->
                                                    viewModel.setServiceToBook(service)
                                                },
                                                onBookingBlocked = { _ ->
                                                    viewModel.clearUserMessage()
                                                },
                                                onSwitchToPartnerMode = {
                                                    viewModel.switchToPartnerRole()
                                                },
                                                onRateSalon = { rating, review ->
                                                    viewModel.submitSalonRating(activeSalon.id, rating, review)
                                                }
                                            )
                                        } else {
                                            when (currentNavTab) {
                                                CustomerNavTab.SALONS -> {
                                                    CustomerHomeScreen(
                                                        salons = filteredSalons,
                                                        currentUserId = currentCustomerUid,
                                                        favoriteSalonIds = favoriteSalonIds,
                                                        onToggleFavorite = { salon ->
                                                            viewModel.toggleFavoriteSalon(salon)
                                                        },
                                                        searchQuery = searchQuery,
                                                        onSearchChanged = { viewModel.setSearchQuery(it) },
                                                        selectedCategory = selectedCategory,
                                                        onCategorySelected = { viewModel.selectCategory(it) },
                                                        onSalonSelected = { viewModel.selectSalon(it) },
                                                        userLocationTitle = userLocationTitle,
                                                        userLocationSubtitle = userLocationSubtitle,
                                                        userLatitude = userLatitude,
                                                        userLongitude = userLongitude,
                                                        isLiveGpsActive = isLiveGpsActive,
                                                        onDetectGpsLocation = { lat, lng, showToast ->
                                                            viewModel.onLiveGpsDetected(lat, lng, showToast)
                                                        },
                                                        onManualLocationSelected = { cityText, pickedLat, pickedLng ->
                                                            viewModel.updateCustomerLocationManually(cityText, pickedLat, pickedLng)
                                                        },
                                                        pastBooking = latestCompletedBooking,
                                                        onRebook = { booking ->
                                                            val targetSalon = allSalons.find { it.id == booking.salonId || it.name.equals(booking.salonName, ignoreCase = true) }
                                                            viewModel.selectSalon(targetSalon)
                                                        },
                                                        onRateSalon = { salon, rating, review ->
                                                            viewModel.submitSalonRating(salon.id, rating, review)
                                                        },
                                                        onProfileClick = {
                                                            viewModel.setNavTab(CustomerNavTab.PROFILE)
                                                        },
                                                        onOpenMyBookings = {
                                                            viewModel.setNavTab(CustomerNavTab.MY_BOOKINGS)
                                                        }
                                                    )
                                                }

                                                CustomerNavTab.MY_BOOKINGS -> {
                                                    MyBookingsScreen(
                                                        localBookings = customerBookings,
                                                        onBackClick = {
                                                            viewModel.setNavTab(CustomerNavTab.SALONS)
                                                        },
                                                        onCancelBooking = { bookingId ->
                                                            viewModel.cancelBooking(bookingId)
                                                        },
                                                        onBrowseSalons = {
                                                            viewModel.setNavTab(CustomerNavTab.SALONS)
                                                        },
                                                        onRateSalon = { salonId, stars ->
                                                            viewModel.submitSalonRating(salonId, stars)
                                                            viewModel.clearUserMessage()
                                                        }
                                                    )
                                                }

                                                CustomerNavTab.PROFILE -> {
                                                    val ownedSalon = allSalons.find {
                                                        currentCustomerUid.isNotBlank() &&
                                                            (it.ownerId == currentCustomerUid || it.partnerId == currentCustomerUid) &&
                                                            it.isRegistrationCompleted &&
                                                            it.registrationFormSubmitted
                                                    }
                                                    ProfileScreen(
                                                        userProfile = currentUserProfile,
                                                        appPreferences = appPreferences,
                                                        hasRegisteredSalon = hasRegisteredPartnerSalon,
                                                        registeredSalonName = ownedSalon?.name,
                                                        onOpenAuth = { viewModel.openAuth(it) },
                                                        onBecomePartner = { viewModel.openPartnerRegistrationForm() },
                                                        onSwitchToPartner = { viewModel.switchToPartnerRole() },
                                                        onResetSalonRegistration = {
                                                            viewModel.resetPartnerSalonRegistration(ownedSalon?.id)
                                                        },
                                                        onUpdatePersonalProfile = { name, phone, city ->
                                                            viewModel.updatePersonalProfile(name, phone, city, isPhoneVerified = true)
                                                        },
                                                        onThemeModeChange = { viewModel.setThemeMode(it) },
                                                        onLanguageChange = { viewModel.setLanguage(it) },
                                                        onNotificationPreferencesChange = { bookingAlerts, queueReminders, offersDiscounts ->
                                                            viewModel.setNotificationPreferences(bookingAlerts, queueReminders, offersDiscounts)
                                                        },
                                                        onShowDemoSalonsChange = { showDemo ->
                                                            viewModel.setShowDemoSalons(showDemo)
                                                        },
                                                        onClearCache = { viewModel.clearAppCache() },
                                                        onSubmitFeedback = { rating, category, msg, onDone ->
                                                            viewModel.submitFeedback(rating, category, msg, onDone)
                                                        },
                                                        isAdmin = isSuperAdmin || currentUserProfile?.isAdmin == true,
                                                        onOpenAdminPanel = { viewModel.openAdminDashboard() },
                                                        onRequestLocationUnlock = {
                                                            viewModel.requestLocationUnlock(
                                                                userId = currentUserProfile?.id,
                                                                salonId = ownedSalon?.id ?: currentUserProfile?.salonId
                                                            )
                                                        },
                                                        onRefreshProfile = { viewModel.refreshUserProfile() },
                                                        onOpenMyBookings = {
                                                            viewModel.setNavTab(CustomerNavTab.MY_BOOKINGS)
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
                            if (!hasRegisteredPartnerSalon) {
                                // Strict Guard: Never allow access to PartnerDashboardScreen unless a salon with isRegistrationCompleted == true exists
                                Scaffold(
                                    modifier = Modifier.fillMaxSize(),
                                    containerColor = DarkBackground,
                                    snackbarHost = { SnackbarHost(snackbarHostState) }
                                ) { onboardingPadding ->
                                    PartnerOnboardingScreen(
                                        userProfile = currentUserProfile,
                                        isLoading = isAuthLoading,
                                        onCancel = { viewModel.switchToCustomerRole() },
                                        onSubmitRegistration = { salonName, ownerName, businessPhone, shopAddress, cityArea, pincode, landmark, shopCategory, availableChairs, openingTime, closingTime, initialServices, latitude, longitude, isAcSalon, imageUrl ->
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
                                                longitude = longitude,
                                                isPhoneVerified = true,
                                                isAcSalon = isAcSalon,
                                                imageUrl = imageUrl
                                            )
                                        },
                                        modifier = Modifier.padding(onboardingPadding)
                                    )
                                }
                            } else {
                                // Partner Mode App (Accessible ONLY after completing Partner Salon Registration)
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
                                        isGlobalRushModeEnabled = isGlobalRushModeEnabled,
                                        onAcceptBooking = { viewModel.acceptBooking(it) },
                                        onAcceptBookingWithTime = { bookingId, allocatedTime ->
                                            viewModel.acceptBooking(bookingId, allocatedTime)
                                        },
                                        onRejectBooking = { viewModel.rejectBooking(it) },
                                        onMarkNoShow = { viewModel.markBookingNoShow(it) },
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
                                        onSaveFullProfile = { id, name, phone, tagline, address, landmark, lat, lng, availableChairs, isAc ->
                                            viewModel.saveSalonFullProfile(id, name, phone, tagline, address, landmark, lat, lng, availableChairs, isAc)
                                        },
                                        onSaveShopPhoto = { id, photoDataUrl ->
                                            viewModel.saveSalonPhoto(id, photoDataUrl)
                                        },
                                        onSubmitFeedback = { rating, category, msg, onDone ->
                                            viewModel.submitFeedback(rating, category, msg, onDone)
                                        },
                                        onRequestLocationUnlock = { salonId ->
                                            viewModel.requestLocationUnlock(
                                                userId = currentUserProfile?.id,
                                                salonId = salonId ?: currentUserProfile?.salonId
                                            )
                                        },
                                        onResetSalonRegistration = { salonId ->
                                            viewModel.resetPartnerSalonRegistration(salonId)
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
    }

    // Customer Booking Bottom Sheet
    if (serviceToBook != null && selectedSalon != null) {
        val liveBookingSalon = allSalons.find { it.id == selectedSalon?.id } ?: selectedSalon!!
        val liveBookingSalonQueueCount = com.example.model.Booking.countActiveInQueueToday(
            bookings = allBookings,
            salonId = liveBookingSalon.id,
            partnerId = liveBookingSalon.partnerId,
            ownerId = liveBookingSalon.ownerId
        )
        BookingBottomSheet(
            salon = liveBookingSalon,
            service = serviceToBook!!,
            userProfile = currentUserProfile ?: com.example.model.UserProfile(name = "Customer", phone = "", phoneNumber = ""),
            selectedSlot = selectedTimeSlot,
            activeLiveQueueCount = liveBookingSalonQueueCount,
            isGlobalRushModeEnabled = isGlobalRushModeEnabled,
            hasActiveBooking = hasActivePendingBooking,
            isSuspended = isUserSuspended,
            onServiceChanged = { updatedService ->
                viewModel.setServiceToBook(updatedService)
            },
            onSlotSelected = { viewModel.setSelectedTimeSlot(it) },
            onConfirm = { name, phone ->
                viewModel.confirmBooking(name, phone)
            },
            onDismiss = { viewModel.setServiceToBook(null) }
        )
    }

    // Strict Shop Closed Error Dialog
    if (showSalonClosedDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSalonClosedDialog() },
            title = {
                Text(
                    text = "Salon Closed",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This salon is currently closed."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissSalonClosedDialog() }
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Customer Booking Confirmation Dialog (displays 4-Digit PIN)
    if (showConfirmationDialog) {
        newlyConfirmedBooking?.let { confirmedBooking ->
            PinDisplayDialog(
                booking = confirmedBooking,
                onDismiss = { viewModel.dismissConfirmationDialog() },
                onViewBookings = {
                    viewModel.dismissConfirmationDialog()
                    viewModel.selectSalon(null)
                    viewModel.setNavTab(CustomerNavTab.MY_BOOKINGS)
                }
            )
        }
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

    // Auth Dialog for in-app mode switching
    AuthDialog(
        authMode = authMode,
        isLoading = isAuthLoading,
        errorMessage = authError,
        onSwitchToPartner = {
            viewModel.switchToPartnerRole()
        },
        onDismiss = { viewModel.dismissAuth() }
    )
}

