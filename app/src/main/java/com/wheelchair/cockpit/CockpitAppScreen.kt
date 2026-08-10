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
import androidx.compose.runtime.LaunchedEffect
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
import com.wheelchair.cockpit.media.NowPlaying
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.AssistantState
import com.wheelchair.cockpit.model.CopilotUiState
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.model.Screen
import com.wheelchair.cockpit.ui.components.DrivingLockBanner
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
    isHvacOn: Boolean,
    hvacTemp: Float,
    rmsLevel: Float,
    appLanguage: AppLanguage,
    displayTheme: DisplayTheme,
    isDrivingRestricted: Boolean = false,
    // MODIFIED: toast + local TTS when user taps a locked control
    onLockedInteraction: () -> Unit = {},
    // MODIFIED: multi-app media hub bindings
    nowPlaying: NowPlaying = NowPlaying(),
    onMediaPlayPause: () -> Unit = {},
    onMediaSkipNext: () -> Unit = {},
    onMediaSkipPrevious: () -> Unit = {},
    onMediaOpenSource: () -> Unit = {},
    onMediaSelectLocal: () -> Unit = {},
    onMediaSelectYouTube: () -> Unit = {},
    onMediaSelectSoundCloud: () -> Unit = {},
    mediaVolume: Float = 0.6f,
    onMediaVolumeChange: (Float) -> Unit = {},
    onHvacToggle: () -> Unit,
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
    lastQueryLatencyMs: Long? = null,
    // MODIFIED: STM idle TTL (0=Off, 3/5/10 min) + turn counter
    sessionTtlMin: Int = 5,
    stmTurns: Int = 0,
    onSessionTtlChange: (Int) -> Unit = {},
    onSessionReset: () -> Unit = {}
) {
    var selectedScreen by remember { mutableStateOf(Screen.DASHBOARD) }
    var localTemp by remember { androidx.compose.runtime.mutableFloatStateOf(hvacTemp) }

    // MODIFIED: force exit Settings while driving lock is active
    LaunchedEffect(isDrivingRestricted, selectedScreen) {
        if (isDrivingRestricted && selectedScreen == Screen.SETTINGS) {
            selectedScreen = Screen.DASHBOARD
        }
    }

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
                isDrivingRestricted = isDrivingRestricted,
                onLockedInteraction = onLockedInteraction,
                modifier = Modifier.fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // MODIFIED: persistent driving-lock banner above content
                if (isDrivingRestricted) {
                    DrivingLockBanner(
                        appLanguage = appLanguage,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    )
                }
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
                                isHvacOn = isHvacOn,
                                hvacTemp = hvacTemp,
                                onHvacToggle = onHvacToggle,
                                onTempUp = { localTemp = (localTemp + 0.5f).coerceAtMost(32f) },
                                onTempDown = { localTemp = (localTemp - 0.5f).coerceAtLeast(16f) },
                                appLanguage = appLanguage,
                                displayTheme = displayTheme,
                                isDrivingRestricted = isDrivingRestricted,
                                onLockedInteraction = onLockedInteraction,
                                nowPlaying = nowPlaying,
                                onMediaPlayPause = onMediaPlayPause,
                                onMediaSkipNext = onMediaSkipNext,
                                onMediaSkipPrevious = onMediaSkipPrevious,
                                onMediaOpenSource = onMediaOpenSource
                            )

                            Screen.ASSISTANT -> AssistantScreen(
                                chatHistory = chatHistory,
                                appLanguage = appLanguage,
                                textMain = textMain,
                                textSecondary = textSecondary,
                                primaryBlue = primaryBlue,
                                surfaceColor = surfaceContainer,
                                onMicTap = onMicTap,
                                // MODIFIED: citations default ON; Dev toggle can hide. Timing only in Dev mode.
                                showCitationCards = !devSettings.developerModeEnabled ||
                                    devSettings.showCitationCards,
                                showLatency = showDeveloperControls &&
                                    devSettings.developerModeEnabled,
                                outlineVariant = outlineVariant,
                                // MODIFIED: restore text input beside mic
                                assistantState = assistantState,
                                isDrivingRestricted = isDrivingRestricted,
                                onManualSend = onManualSend
                            )

                            Screen.MEDIA -> MediaScreen(
                                appLanguage = appLanguage,
                                displayTheme = displayTheme,
                                primaryBlue = primaryBlue,
                                surfaceContainer = surfaceContainer,
                                textMain = textMain,
                                textSecondary = textSecondary,
                                outlineVariant = outlineVariant,
                                nowPlaying = nowPlaying,
                                onPlayPause = onMediaPlayPause,
                                onSkipNext = onMediaSkipNext,
                                onSkipPrevious = onMediaSkipPrevious,
                                onOpenSource = onMediaOpenSource,
                                onSelectLocal = onMediaSelectLocal,
                                onSelectYouTube = onMediaSelectYouTube,
                                onSelectSoundCloud = onMediaSelectSoundCloud,
                                volumeLevel = mediaVolume,
                                onVolumeChange = onMediaVolumeChange,
                                isDrivingRestricted = isDrivingRestricted,
                                onLockedInteraction = onLockedInteraction
                            )

                            Screen.MAP -> MapScreen(
                                appLanguage = appLanguage,
                                displayTheme = displayTheme,
                                primaryColor = primaryBlue,
                                surfaceColor = surfaceContainer,
                                textMain = textMain,
                                textSecondary = textSecondary,
                                outlineVariant = outlineVariant,
                                isDrivingRestricted = isDrivingRestricted,
                                onLockedInteraction = onLockedInteraction
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
                                lastQueryLatencyMs = lastQueryLatencyMs,
                                sessionTtlMin = sessionTtlMin,
                                stmTurns = stmTurns,
                                onSessionTtlChange = onSessionTtlChange,
                                onSessionReset = onSessionReset
                            )
                        }
                    }

                    // MODIFIED: plate lives in content column (not over SideRail Settings)
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
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                    )
                }
                
                // No global Climate Dock, it only appears on the Dashboard screen now.
            }
        }
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
