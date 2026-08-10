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
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.ui.theme.CockpitTypography

/**
 * Self-contained climate control card for the dashboard left column.
 *
 * Shows thermostat icon, current temperature with +/- step buttons,
 * and Fan / A/C toggle buttons — all inside one GlassSurface card.
 */
@Composable
fun ClimateCard(
    isHvacOn: Boolean,
    hvacTemp: Float,
    onHvacToggle: () -> Unit,
    onTempUp: () -> Unit,
    onTempDown: () -> Unit,
    primaryColor: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    theme: DisplayTheme,
    appLanguage: AppLanguage,
    modifier: Modifier = Modifier
) {
    val vi = appLanguage == AppLanguage.VIETNAMESE
    val accent = if (isHvacOn) primaryColor else textSecondary.copy(alpha = 0.5f)
    val surfaceColor = if (isHvacOn)
        primaryColor.copy(alpha = 0.06f)
    else
        Color.Transparent

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        theme = theme,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: icon + title + temp + step buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Thermostat,
                        contentDescription = "Climate",
                        tint = accent,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "%.1f°C".format(hvacTemp),
                            style = CockpitTypography.data.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            ),
                            color = textMain
                        )
                    }

                    // +/- step buttons
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TempStep(
                            icon = Icons.Rounded.Add,
                            description = if (vi) "Tăng nhiệt độ" else "Increase temperature",
                            enabled = isHvacOn,
                            primaryColor = primaryColor,
                            textSecondary = textSecondary,
                            onClick = onTempUp
                        )
                        TempStep(
                            icon = Icons.Rounded.Remove,
                            description = if (vi) "Giảm nhiệt độ" else "Decrease temperature",
                            enabled = isHvacOn,
                            primaryColor = primaryColor,
                            textSecondary = textSecondary,
                            onClick = onTempDown
                        )
                    }
                }

                // Row 2: Fan + A/C quick toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClimateChip(
                        icon = Icons.Rounded.Air,
                        label = if (vi) "Quạt" else "Fan",
                        isActive = isHvacOn,
                        primaryColor = primaryColor,
                        textMain = textMain,
                        textSecondary = textSecondary,
                        onClick = onHvacToggle,
                        modifier = Modifier.weight(1f)
                    )
                    ClimateChip(
                        icon = Icons.Rounded.AcUnit,
                        label = if (vi) "Điều hòa" else "A/C",
                        isActive = isHvacOn,
                        primaryColor = primaryColor,
                        textMain = textMain,
                        textSecondary = textSecondary,
                        onClick = onHvacToggle,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}

@Composable
private fun TempStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    primaryColor: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    val tint = if (enabled) primaryColor else textSecondary.copy(alpha = 0.35f)
    val bg = if (enabled) primaryColor.copy(alpha = 0.12f) else Color.Transparent
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ClimateChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    primaryColor: Color,
    textMain: Color,
    textSecondary: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isActive) primaryColor.copy(alpha = 0.16f) else textSecondary.copy(alpha = 0.08f)
    val contentColor = if (isActive) primaryColor else textSecondary.copy(alpha = 0.6f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = CockpitTypography.label,
            color = contentColor
        )
    }
}
