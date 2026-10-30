package com.example.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard Verified Blue Tick Icon displayed next to verified user names or phone numbers.
 */
@Composable
fun VerifiedBlueTickIcon(
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = Icons.Default.Verified,
        contentDescription = "Verified",
        tint = Color(0xFF1D9BF0),
        modifier = modifier
            .size(size)
            .testTag("verified_blue_tick_icon")
    )
}

@Composable
fun VerifiedNumberBadge(
    text: String = "",
    modifier: Modifier = Modifier
) {
    VerifiedBlueTickIcon(modifier = modifier)
}
