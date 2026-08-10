package com.wheelchair.cockpit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.ui.components.StatusCard
import com.wheelchair.cockpit.ui.components.TripEfficiencyModule
import com.wheelchair.cockpit.ui.components.VehicleVisualization
import com.wheelchair.cockpit.ui.theme.CockpitColors

/**
 * Main dashboard screen for the AAOS cockpit overhaul.
 *
 * Layout: 45% Driving Hub | 55% Modular Widgets
 */
@Composable
fun DashboardScreen(
    vehicleSpeed: Float,
    isHvacOn: Boolean,
    hvacTemp: Float,
    onHvacToggle: () -> Unit,
    onTempUp: () -> Unit,
    onTempDown: () -> Unit,
    appLanguage: AppLanguage,
    displayTheme: DisplayTheme,
    modifier: Modifier = Modifier
) {
    val backgroundBg = CockpitColors.getBackgroundBg(displayTheme)
    val surfaceContainer = CockpitColors.getSurfaceContainer(displayTheme)
    val primaryBlue = CockpitColors.getPrimaryBlue(displayTheme)
    val textMain = CockpitColors.getTextMain(displayTheme)
    val textSecondary = CockpitColors.getTextSecondary(displayTheme)
    val outlineVariant = CockpitColors.getOutlineVariant(displayTheme)
    val accentGreen = CockpitColors.getAccentGreen(displayTheme)

    var doorStates by remember { mutableStateOf(DoorStates()) }
    val vi = appLanguage == AppLanguage.VIETNAMESE

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBg)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Main content row
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left: Driving Hub (45%)
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Center: vehicle visualization + PRND + Speed & Battery
                VehicleVisualization(
                    frontLeftLocked = doorStates.frontLeft,
                    frontRightLocked = doorStates.frontRight,
                    rearLeftLocked = doorStates.rearLeft,
                    rearRightLocked = doorStates.rearRight,
                    vehicleSpeed = vehicleSpeed,
                    vi = vi,
                    primaryColor = primaryBlue,
                    textMain = textMain,
                    textSecondary = textSecondary,
                    outlineVariant = outlineVariant,
                    accentGreen = accentGreen,
                    theme = displayTheme,
                    onFrontLeftToggle = { doorStates = doorStates.copy(frontLeft = !doorStates.frontLeft) },
                    onFrontRightToggle = { doorStates = doorStates.copy(frontRight = !doorStates.frontRight) },
                    onRearLeftToggle = { doorStates = doorStates.copy(rearLeft = !doorStates.rearLeft) },
                    onRearRightToggle = { doorStates = doorStates.copy(rearRight = !doorStates.rearRight) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Right: Modular Widgets (55%)
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TripEfficiencyModule(
                    theme = displayTheme,
                    primaryColor = primaryBlue,
                    textMain = textMain,
                    textSecondary = textSecondary,
                    modifier = Modifier.height(180.dp)
                )
                
                // Embedded Media Player Widget
                com.wheelchair.cockpit.ui.components.DashboardMediaWidget(
                    primaryColor = primaryBlue,
                    surfaceColor = surfaceContainer,
                    textMain = textMain,
                    textSecondary = textSecondary,
                    outlineVariant = outlineVariant,
                    theme = displayTheme,
                    appLanguage = appLanguage,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                
                // AC / Climate Bar shrunk to the right side
                com.wheelchair.cockpit.ui.components.ClimateBar(
                    isHvacOn = isHvacOn,
                    hvacTemp = hvacTemp,
                    onHvacToggle = onHvacToggle,
                    onTempUp = onTempUp,
                    onTempDown = onTempDown,
                    primaryColor = primaryBlue,
                    surfaceColor = surfaceContainer,
                    textMain = textMain,
                    textSecondary = textSecondary,
                    outlineVariant = outlineVariant,
                    theme = displayTheme,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private data class DoorStates(
    val frontLeft: Boolean = true,
    val frontRight: Boolean = true,
    val rearLeft: Boolean = true,
    val rearRight: Boolean = true
) {
    val allLocked: Boolean
        get() = frontLeft && frontRight && rearLeft && rearRight

    constructor(allLocked: Boolean) : this(
        frontLeft = allLocked,
        frontRight = allLocked,
        rearLeft = allLocked,
        rearRight = allLocked
    )
}
