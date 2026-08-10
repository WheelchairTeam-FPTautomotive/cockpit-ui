package com.wheelchair.cockpit

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wheelchair.cockpit.api.CitationInfo
import com.wheelchair.cockpit.data.HealthResult
import com.wheelchair.cockpit.dev.DevSettings
import com.wheelchair.cockpit.dev.HttpLogLevel
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.AssistantState
import com.wheelchair.cockpit.model.CopilotUiState
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.model.Screen
import com.wheelchair.cockpit.ui.components.ClimateBar
import com.wheelchair.cockpit.ui.components.SideRail
import com.wheelchair.cockpit.ui.components.VoicePlate
import com.wheelchair.cockpit.ui.screens.AssistantScreen
import com.wheelchair.cockpit.ui.screens.DashboardScreen
import com.wheelchair.cockpit.ui.screens.MapScreen
import com.wheelchair.cockpit.ui.screens.MediaScreen
import com.wheelchair.cockpit.ui.screens.SettingsScreen
import com.wheelchair.cockpit.ui.theme.CockpitColors

/**
 * Root app shell for the cockpit overhaul.
 *
 * Wires the new side-rail navigation, dashboard tab bar, and the existing
 * voice-copilot overlay into one scaffold. The bottom dock from the original
 * layout is intentionally replaced by the side rail + future climate bar.
 */
@Composable
fun CockpitAppScreen(
    assistantState: AssistantState,
    copilotUiState: CopilotUiState,
    statusText: String,
    chatHistory: List<com.wheelchair.cockpit.ui.components.ChatMessage>,
    citations: List<CitationInfo>,
    vehicleSpeed: Float,
    currentGear: String,
    batteryLevel: Int,
    isHvacOn: Boolean,
    hvacTemp: Float,
    doorLockFL: Boolean,
    doorLockFR: Boolean,
    doorLockRL: Boolean,
    doorLockRR: Boolean,
    tirePressureFL: Int,
    tirePressureFR: Int,
    tirePressureRL: Int,
    tirePressureRR: Int,
    rmsLevel: Float,
    appLanguage: AppLanguage,
    displayTheme: DisplayTheme,
    isDrivingRestricted: Boolean = false,
    onHvacToggle: () -> Unit,
    onTempChange: (Float) -> Unit,
    onDoorLockToggle: (Int, Boolean) -> Unit,
    onManualSend: (String) -> Unit,
    onMicTap: () -> Unit,
    onWakeSimulate: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (DisplayTheme) -> Unit,
    showDeveloperControls: Boolean = false,
    devSettings: DevSettings = DevSettings(),
    healthResult: HealthResult? = null,
    healthChecking: Boolean = false,
    onDeveloperModeChange: (Boolean) -> Unit = {},
    onBaseUrlApply: (String) -> Unit = {},
    onMockRagChange: (Boolean) -> Unit = {},
    onBypassDrivingChange: (Boolean) -> Unit = {},
    onHttpLogLevelChange: (HttpLogLevel) -> Unit = {},
    onShowCitationCardsChange: (Boolean) -> Unit = {},
    onHealthCheck: () -> Unit = {},
    partialTranscript: String = "",
    micDiagLabel: String = "",
    lastQueryLatencyMs: Long? = null
) {
    var selectedScreen by remember { mutableStateOf(Screen.DASHBOARD) }

    val primaryBlue = CockpitColors.getPrimaryBlue(displayTheme)
    val backgroundBg = CockpitColors.getBackgroundBg(displayTheme)
    val surfaceContainer = CockpitColors.getSurfaceContainer(displayTheme)
    val textMain = CockpitColors.getTextMain(displayTheme)
    val textSecondary = CockpitColors.getTextSecondary(displayTheme)
    val outlineVariant = CockpitColors.getOutlineVariant(displayTheme)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBg)
    ) {
        // Side-by-side root layout: fixed-width sidebar + flexible content area.
        // MapScreen renders inside the weighted Column and must not draw under the rail.
        Row(modifier = Modifier.fillMaxSize()) {
            SideRail(
                selectedScreen = selectedScreen,
                onScreenSelected = { selectedScreen = it },
                theme = displayTheme,
                appLanguage = appLanguage,
                modifier = Modifier.fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Crossfade(
                        targetState = selectedScreen,
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            Screen.DASHBOARD -> DashboardScreen(
                                vehicleSpeed = vehicleSpeed,
                                currentGear = currentGear,
                                batteryLevel = batteryLevel,
                                isHvacOn = isHvacOn,
                                hvacTemp = hvacTemp,
                                doorLockFL = doorLockFL,
                                doorLockFR = doorLockFR,
                                doorLockRL = doorLockRL,
                                doorLockRR = doorLockRR,
                                tirePressureFL = tirePressureFL,
                                tirePressureFR = tirePressureFR,
                                tirePressureRL = tirePressureRL,
                                tirePressureRR = tirePressureRR,
                                onHvacToggle = onHvacToggle,
                                onDoorLockToggle = onDoorLockToggle,
                                onTempUp = { onTempChange((hvacTemp + 0.5f).coerceAtMost(32f)) },
                                onTempDown = { onTempChange((hvacTemp - 0.5f).coerceAtLeast(16f)) },
                                appLanguage = appLanguage,
                                displayTheme = displayTheme
                            )

                            Screen.ASSISTANT -> AssistantScreen(
                                chatHistory = chatHistory,
                                appLanguage = appLanguage,
                                textMain = textMain,
                                textSecondary = textSecondary,
                                primaryBlue = primaryBlue,
                                surfaceColor = surfaceContainer,
                                onMicTap = onMicTap
                            )

                            Screen.MEDIA -> MediaScreen(
                                appLanguage = appLanguage,
                                displayTheme = displayTheme,
                                primaryBlue = primaryBlue,
                                surfaceContainer = surfaceContainer,
                                textMain = textMain,
                                textSecondary = textSecondary,
                                outlineVariant = outlineVariant
                            )

                            Screen.MAP -> MapScreen(
                                appLanguage = appLanguage,
                                displayTheme = displayTheme,
                                primaryColor = primaryBlue,
                                surfaceColor = surfaceContainer,
                                textMain = textMain,
                                textSecondary = textSecondary,
                                outlineVariant = outlineVariant
                            )

                            Screen.SETTINGS -> SettingsScreen(
                                appLanguage = appLanguage,
                                displayTheme = displayTheme,
                                primaryBlue = primaryBlue,
                                textMain = textMain,
                                outlineVariant = outlineVariant,
                                showDeveloperControls = showDeveloperControls,
                                devSettings = devSettings,
                                healthResult = healthResult,
                                healthChecking = healthChecking,
                                onLanguageChange = onLanguageChange,
                                onThemeChange = onThemeChange,
                                onDeveloperModeChange = onDeveloperModeChange,
                                onBaseUrlApply = onBaseUrlApply,
                                onMockRagChange = onMockRagChange,
                                onBypassDrivingChange = onBypassDrivingChange,
                                onHttpLogLevelChange = onHttpLogLevelChange,
                                onShowCitationCardsChange = onShowCitationCardsChange,
                                onHealthCheck = onHealthCheck,
                                appVersionName = BuildConfig.VERSION_NAME,
                                lastQueryLatencyMs = lastQueryLatencyMs
                            )
                        }
                    }
                }
                
                // No global Climate Dock, it only appears on the Dashboard screen now.
            }
        }

        // Voice plate floats above the main content, just above the bottom edge.
        VoicePlate(
            state = copilotUiState,
            rmsLevel = rmsLevel,
            partialTranscript = partialTranscript,
            appLanguage = appLanguage,
            primaryColor = primaryBlue,
            surfaceColor = surfaceContainer,
            textMain = textMain,
            textSecondary = textSecondary,
            outlineVariant = outlineVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )


    }
}


@Composable
private fun PlaceholderTab(title: String, textMain: Color, textSecondary: Color) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = com.wheelchair.cockpit.ui.theme.CockpitTypography.title,
            color = textMain
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$title screen coming in the next work package.",
            style = com.wheelchair.cockpit.ui.theme.CockpitTypography.body,
            color = textSecondary,
            textAlign = TextAlign.Center
        )
    }
}
