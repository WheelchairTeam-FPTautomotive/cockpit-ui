package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsVoice
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wheelchair.cockpit.model.AppLanguage

@Composable
fun AutomotiveBottomDock(
    activeNavIndex: Int,
    appLanguage: AppLanguage,
    primaryBlue: Color,
    surfaceContainer: Color,
    textSecondary: Color,
    outlineVariant: Color,
    modifier: Modifier = Modifier,
    isDrivingRestricted: Boolean = false,
    onNavSelect: (Int) -> Unit,
    onMicTap: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        color = surfaceContainer,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutomotiveNavItem(
                label = "Home",
                icon = Icons.Rounded.Home,
                selected = activeNavIndex == 0,
                primaryColor = primaryBlue,
                unselectedColor = textSecondary
            ) { onNavSelect(0) }

            AutomotiveNavItem(
                label = if (appLanguage == AppLanguage.VIETNAMESE) "Trợ lý" else "Assistant",
                icon = Icons.Rounded.SettingsVoice,
                selected = activeNavIndex == 1,
                primaryColor = primaryBlue,
                unselectedColor = textSecondary
            ) { onNavSelect(1); onMicTap() }

            AutomotiveNavItem(
                label = "Maps",
                icon = Icons.Rounded.Map,
                selected = activeNavIndex == 2,
                primaryColor = if (isDrivingRestricted) primaryBlue.copy(alpha = 0.5f) else primaryBlue,
                unselectedColor = textSecondary.copy(alpha = if (isDrivingRestricted) 0.5f else 1.0f)
            ) { if (!isDrivingRestricted) onNavSelect(2) }

            AutomotiveNavItem(
                label = if (appLanguage == AppLanguage.VIETNAMESE) "Cài đặt" else "Settings",
                icon = Icons.Rounded.Settings,
                selected = activeNavIndex == 3,
                primaryColor = if (isDrivingRestricted) primaryBlue.copy(alpha = 0.5f) else primaryBlue,
                unselectedColor = textSecondary.copy(alpha = if (isDrivingRestricted) 0.5f else 1.0f)
            ) { if (!isDrivingRestricted) { onNavSelect(3); onOpenSettings() } }
        }
    }
}
