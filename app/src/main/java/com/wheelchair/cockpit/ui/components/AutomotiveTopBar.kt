package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AccessibleForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal top bar with app branding only.
 *
 * Speed, climate, and settings live in the dashboard cards / side rail
 * to avoid duplicate controls.
 */
@Composable
fun AutomotiveTopBar(
    primaryBlue: Color,
    backgroundBg: Color,
    textMain: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        color = backgroundBg,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.AccessibleForward,
                contentDescription = null,
                tint = primaryBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Wheelchair Copilot",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = textMain
            )
        }
    }
}
