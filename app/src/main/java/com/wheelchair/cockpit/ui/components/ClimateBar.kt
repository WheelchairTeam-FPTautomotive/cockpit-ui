package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.ui.theme.CockpitTypography

/**
 * Bottom climate control bar for the dashboard.
 *
 * Contains: thermostat icon, temperature readout with +/- step buttons,
 * Fan and A/C toggles, and a master power switch.
 */
@Composable
fun ClimateBar(
    isHvacOn: Boolean,
    hvacTemp: Float,
    onHvacToggle: () -> Unit,
    onTempUp: () -> Unit,
    onTempDown: () -> Unit,
    primaryColor: Color,
    surfaceColor: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    theme: DisplayTheme,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        theme = theme,
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // Temp section: icon + value + +/- step buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Thermostat,
                        contentDescription = "Temperature",
                        tint = if (isHvacOn) primaryColor else textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "Cabin",
                            style = CockpitTypography.label,
                            color = textSecondary
                        )
                        Text(
                            text = "%.1f°C".format(hvacTemp),
                            style = CockpitTypography.title,
                            color = if (isHvacOn) textMain else textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TempStepButton(
                            icon = Icons.Rounded.Remove,
                            description = "Decrease temperature",
                            enabled = isHvacOn,
                            primaryColor = primaryColor,
                            surfaceColor = surfaceColor,
                            textSecondary = textSecondary,
                            onClick = onTempDown
                        )
                        TempStepButton(
                            icon = Icons.Rounded.Add,
                            description = "Increase temperature",
                            enabled = isHvacOn,
                            primaryColor = primaryColor,
                            surfaceColor = surfaceColor,
                            textSecondary = textSecondary,
                            onClick = onTempUp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                // Visual divider
                androidx.compose.material3.VerticalDivider(
                    modifier = Modifier.height(32.dp),
                    color = outlineVariant.copy(alpha = 0.4f),
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.width(24.dp))

                // Fan + A/C toggles
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ClimateToggleButton(
                        icon = Icons.Rounded.Air,
                        label = "Fan",
                        isActive = isHvacOn,
                        primaryColor = primaryColor,
                        textMain = textMain,
                        surfaceColor = surfaceColor,
                        onClick = onHvacToggle
                    )
                    ClimateToggleButton(
                        icon = Icons.Rounded.AcUnit,
                        label = "A/C",
                        isActive = isHvacOn,
                        primaryColor = primaryColor,
                        textMain = textMain,
                        surfaceColor = surfaceColor,
                        onClick = onHvacToggle
                    )
                }
            }
        }
    )
}

@Composable
private fun TempStepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    primaryColor: Color,
    surfaceColor: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    val tint = if (enabled) primaryColor else textSecondary.copy(alpha = 0.4f)
    val bg = if (enabled) primaryColor.copy(alpha = 0.12f) else surfaceColor.copy(alpha = 0.3f)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ClimateToggleButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    primaryColor: Color,
    textMain: Color,
    surfaceColor: Color,
    onClick: () -> Unit
) {
    val background = if (isActive) primaryColor.copy(alpha = 0.18f) else surfaceColor.copy(alpha = 0.5f)
    val contentColor = if (isActive) primaryColor else textMain.copy(alpha = 0.6f)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(background)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = CockpitTypography.label,
            color = contentColor
        )
    }
}
