package com.wheelchair.cockpit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelchair.cockpit.data.HealthResult
import com.wheelchair.cockpit.dev.DevSettings
import com.wheelchair.cockpit.dev.HttpLogLevel
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.ui.components.SettingsSelectableChip
import com.wheelchair.cockpit.ui.dialogs.DeveloperModeSection
import com.wheelchair.cockpit.ui.theme.CockpitTypography

@Composable
fun SettingsScreen(
    appLanguage: AppLanguage,
    displayTheme: DisplayTheme,
    primaryBlue: Color,
    textMain: Color,
    outlineVariant: Color,
    modifier: Modifier = Modifier,
    showDeveloperControls: Boolean = false,
    devSettings: DevSettings = DevSettings(),
    healthResult: HealthResult? = null,
    healthChecking: Boolean = false,
    onLanguageChange: (AppLanguage) -> Unit = {},
    onThemeChange: (DisplayTheme) -> Unit = {},
    onDeveloperModeChange: (Boolean) -> Unit = {},
    onBaseUrlApply: (String) -> Unit = {},
    onMockRagChange: (Boolean) -> Unit = {},
    onBypassDrivingChange: (Boolean) -> Unit = {},
    onHttpLogLevelChange: (HttpLogLevel) -> Unit = {},
    onShowCitationCardsChange: (Boolean) -> Unit = {},
    onHealthCheck: () -> Unit = {},
    appVersionName: String = "",
    lastQueryLatencyMs: Long? = null,
    // MODIFIED: STM idle TTL controls
    sessionTtlMin: Int = 5,
    stmTurns: Int = 0,
    onSessionTtlChange: (Int) -> Unit = {},
    onSessionReset: () -> Unit = {}
) {
    val vi = appLanguage == AppLanguage.VIETNAMESE

    if (displayTheme == DisplayTheme.CENTRAL) {
        LaunchedEffect(Unit) { onThemeChange(DisplayTheme.DARK) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                tint = primaryBlue,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = if (vi) "Cài Đặt Hệ Thống" else "System Settings",
                style = CockpitTypography.title,
                fontWeight = FontWeight.Bold,
                color = textMain
            )
        }

        // Speech Language
        SettingsSection(
            title = if (vi) "Ngôn ngữ nhận diện & phát âm" else "Speech Language",
            titleColor = primaryBlue
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsSelectableChip(
                    selected = appLanguage == AppLanguage.VIETNAMESE,
                    label = "Tiếng Việt (vi-VN)",
                    icon = Icons.Rounded.Language,
                    primaryColor = primaryBlue,
                    onClick = { onLanguageChange(AppLanguage.VIETNAMESE) }
                )
                SettingsSelectableChip(
                    selected = appLanguage == AppLanguage.ENGLISH,
                    label = "English (en-US)",
                    icon = Icons.Rounded.Language,
                    primaryColor = primaryBlue,
                    onClick = { onLanguageChange(AppLanguage.ENGLISH) }
                )
            }
        }

        HorizontalDivider(color = outlineVariant)

        // Display Theme
        SettingsSection(
            title = if (vi) "Giao diện hiển thị" else "Display Theme",
            titleColor = primaryBlue
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsSelectableChip(
                    selected = displayTheme == DisplayTheme.LIGHT,
                    label = "Light",
                    icon = Icons.Rounded.LightMode,
                    primaryColor = primaryBlue,
                    onClick = { onThemeChange(DisplayTheme.LIGHT) }
                )
                SettingsSelectableChip(
                    selected = displayTheme == DisplayTheme.DARK,
                    label = "Dark",
                    icon = Icons.Rounded.DarkMode,
                    primaryColor = primaryBlue,
                    onClick = { onThemeChange(DisplayTheme.DARK) }
                )
            }
        }

        if (showDeveloperControls) {
            HorizontalDivider(color = outlineVariant)
            DeveloperModeSection(
                appLanguage = appLanguage,
                primaryBlue = primaryBlue,
                textMain = textMain,
                outlineVariant = outlineVariant,
                devSettings = devSettings,
                healthResult = healthResult,
                healthChecking = healthChecking,
                onDeveloperModeChange = onDeveloperModeChange,
                onBaseUrlApply = onBaseUrlApply,
                onMockRagChange = onMockRagChange,
                onBypassDrivingChange = onBypassDrivingChange,
                onHttpLogLevelChange = onHttpLogLevelChange,
                onShowCitationCardsChange = onShowCitationCardsChange,
                onHealthCheck = onHealthCheck,
                appVersionName = appVersionName,
                lastQueryLatencyMs = lastQueryLatencyMs,
                sessionTtlMin = sessionTtlMin,
                stmTurns = stmTurns,
                onSessionTtlChange = onSessionTtlChange,
                onSessionReset = onSessionReset
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    titleColor: Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = titleColor
        )
        content()
    }
}
