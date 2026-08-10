package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AccessibleForward
import androidx.compose.material.icons.rounded.Dashboard

import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.model.Screen
import com.wheelchair.cockpit.ui.theme.CockpitColors

/**
 * Vertical side rail for primary cockpit navigation.
 *
 * Mirrors the left-hand icon rail from the reference dashboard images:
 * compact, dark, and always visible.
 */
@Composable
fun SideRail(
    selectedScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    theme: DisplayTheme,
    appLanguage: AppLanguage,
    modifier: Modifier = Modifier
) {
    val vi = appLanguage == AppLanguage.VIETNAMESE
    val background = CockpitColors.getBackgroundBg(theme)
    val surface = CockpitColors.getSurfaceContainer(theme)
    val primary = CockpitColors.getPrimaryBlue(theme)
    val textSecondary = CockpitColors.getTextSecondary(theme)
    val outline = CockpitColors.getOutlineVariant(theme)

    Column(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(background)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top)
    ) {
        SideRailHeader(
            primaryColor = primary,
            textMain = textSecondary,
            surfaceColor = surface,
            outlineColor = outline
        )

        SideRailItem(
            screen = Screen.DASHBOARD,
            label = if (vi) "Trang chủ" else "Home",
            icon = Icons.Rounded.Dashboard,
            selected = selectedScreen == Screen.DASHBOARD,
            primaryColor = primary,
            textSecondary = textSecondary,
            surfaceColor = surface,
            outlineColor = outline,
            onClick = { onScreenSelected(Screen.DASHBOARD) }
        )

        SideRailItem(
            screen = Screen.ASSISTANT,
            label = if (vi) "Trợ lý" else "Assistant",
            icon = Icons.Rounded.Mic,
            selected = selectedScreen == Screen.ASSISTANT,
            primaryColor = primary,
            textSecondary = textSecondary,
            surfaceColor = surface,
            outlineColor = outline,
            onClick = { onScreenSelected(Screen.ASSISTANT) }
        )



        SideRailItem(
            screen = Screen.MEDIA,
            label = if (vi) "Giải trí" else "Media",
            icon = Icons.Rounded.PlayArrow,
            selected = selectedScreen == Screen.MEDIA,
            primaryColor = primary,
            textSecondary = textSecondary,
            surfaceColor = surface,
            outlineColor = outline,
            onClick = { onScreenSelected(Screen.MEDIA) }
        )

        SideRailItem(
            screen = Screen.MAP,
            label = if (vi) "Bản đồ" else "Map",
            icon = Icons.Rounded.Map,
            selected = selectedScreen == Screen.MAP,
            primaryColor = primary,
            textSecondary = textSecondary,
            surfaceColor = surface,
            outlineColor = outline,
            onClick = { onScreenSelected(Screen.MAP) }
        )

        Spacer(modifier = Modifier.weight(1f))

        SideRailItem(
            screen = Screen.SETTINGS,
            label = if (vi) "Cài đặt" else "Settings",
            icon = Icons.Rounded.Settings,
            selected = selectedScreen == Screen.SETTINGS,
            primaryColor = primary,
            textSecondary = textSecondary,
            surfaceColor = surface,
            outlineColor = outline,
            onClick = { onScreenSelected(Screen.SETTINGS) }
        )
    }
}

@Composable
private fun SideRailItem(
    screen: Screen,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    primaryColor: Color,
    textSecondary: Color,
    surfaceColor: Color,
    outlineColor: Color,
    onClick: () -> Unit
) {
    val contentColor = if (selected) primaryColor else textSecondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(primaryColor, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .align(Alignment.CenterStart)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = com.wheelchair.cockpit.ui.theme.CockpitTypography.label,
                color = contentColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SideRailHeader(
    primaryColor: Color,
    textMain: Color,
    surfaceColor: Color,
    outlineColor: Color
) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.AccessibleForward,
            contentDescription = "Wheelchair Copilot",
            tint = primaryColor,
            modifier = Modifier.size(32.dp)
        )
    }
}
