package com.example.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Backwards-compatible wrapper around [PhoneSetupScreen].
 */
@Composable
fun MandatoryPhoneScreen(
    userName: String,
    userEmail: String,
    isLoading: Boolean,
    errorMessage: String?,
    onSavePhone: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    PhoneSetupScreen(
        userName = userName,
        userEmail = userEmail,
        isLoading = isLoading,
        errorMessage = errorMessage,
        onSavePhone = onSavePhone,
        onSignOut = onSignOut,
        modifier = modifier
    )
}
