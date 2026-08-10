package com.wheelchair.cockpit.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelchair.cockpit.BuildConfig
import com.wheelchair.cockpit.data.HealthResult
import com.wheelchair.cockpit.dev.DevSettings
import com.wheelchair.cockpit.dev.HttpLogLevel
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.ui.components.SettingsSelectableChip

@Composable
fun SystemSettingsDialog(
    show: Boolean,
    appLanguage: AppLanguage,
    displayTheme: DisplayTheme,
    primaryBlue: Color,
    textMain: Color,
    outlineVariant: Color,
    onClose: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (DisplayTheme) -> Unit,
    // --- START MODIFICATION ---
    showDeveloperControls: Boolean = false,
    devSettings: DevSettings = DevSettings(),
    healthResult: HealthResult? = null,
    healthChecking: Boolean = false,
    onDeveloperModeChange: (Boolean) -> Unit = {},
    onBaseUrlApply: (String) -> Unit = {},
    onMockRagChange: (Boolean) -> Unit = {},
    onBypassDrivingChange: (Boolean) -> Unit = {},
    onHttpLogLevelChange: (HttpLogLevel) -> Unit = {},
    onHealthCheck: () -> Unit = {},
    appVersionName: String = BuildConfig.VERSION_NAME,
    // --- START MODIFICATION ---
    lastQueryLatencyMs: Long? = null
    // --- END MODIFICATION ---
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        titleContentColor = textMain,
        textContentColor = textMain,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = primaryBlue,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (appLanguage == AppLanguage.VIETNAMESE) "Cài Đặt Hệ Thống" else "System Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textMain
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Speech Language Section
                Column {
                    Text(
                        text = if (appLanguage == AppLanguage.VIETNAMESE) "Ngôn ngữ nhận diện & phát âm" else "Speech Language",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryBlue
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

                // Display Theme Section
                Column {
                    Text(
                        text = if (appLanguage == AppLanguage.VIETNAMESE) "Giao diện hiển thị" else "Display Theme",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryBlue
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        SettingsSelectableChip(
                            selected = displayTheme == DisplayTheme.CENTRAL,
                            label = "Central",
                            icon = Icons.Rounded.Tune,
                            primaryColor = primaryBlue,
                            onClick = { onThemeChange(DisplayTheme.CENTRAL) }
                        )
                    }
                }

                // --- START MODIFICATION ---
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
                        onHealthCheck = onHealthCheck,
                        appVersionName = appVersionName,
                        lastQueryLatencyMs = lastQueryLatencyMs
                    )
                }
                // --- END MODIFICATION ---
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryBlue, contentColor = Color.White),
                modifier = Modifier.height(42.dp)
            ) {
                Text(if (appLanguage == AppLanguage.VIETNAMESE) "Đóng" else "Close", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// --- START MODIFICATION ---
@Composable
internal fun DeveloperModeSection(
    appLanguage: AppLanguage,
    primaryBlue: Color,
    textMain: Color,
    outlineVariant: Color,
    devSettings: DevSettings,
    healthResult: HealthResult?,
    healthChecking: Boolean,
    onDeveloperModeChange: (Boolean) -> Unit,
    onBaseUrlApply: (String) -> Unit,
    onMockRagChange: (Boolean) -> Unit,
    onBypassDrivingChange: (Boolean) -> Unit,
    onHttpLogLevelChange: (HttpLogLevel) -> Unit,
    onHealthCheck: () -> Unit,
    appVersionName: String,
    lastQueryLatencyMs: Long? = null
) {
    var urlDraft by remember(devSettings.baseUrl) { mutableStateOf(devSettings.baseUrl) }
    val vi = appLanguage == AppLanguage.VIETNAMESE

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.BugReport,
                contentDescription = null,
                tint = primaryBlue,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (vi) "Chế độ nhà phát triển" else "Developer mode",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = primaryBlue,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = devSettings.developerModeEnabled,
                onCheckedChange = onDeveloperModeChange,
                colors = SwitchDefaults.colors(checkedTrackColor = primaryBlue)
            )
        }

        if (!devSettings.developerModeEnabled) {
            Text(
                text = if (vi) {
                    "Bật để hiện công cụ debug (URL, mock RAG, bypass khóa lái)."
                } else {
                    "Enable to reveal debug tools (URL, mock RAG, driving bypass)."
                },
                fontSize = 12.sp,
                color = textMain.copy(alpha = 0.7f)
            )
            return
        }

        OutlinedTextField(
            value = urlDraft,
            onValueChange = { urlDraft = it },
            label = { Text(if (vi) "Backend base URL" else "Backend base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onBaseUrlApply(urlDraft) },
                colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
            ) {
                Text(if (vi) "Áp dụng" else "Apply")
            }
            OutlinedButton(
                onClick = onHealthCheck,
                enabled = !healthChecking
            ) {
                Text(
                    when {
                        healthChecking -> if (vi) "Đang kiểm tra…" else "Checking…"
                        else -> if (vi) "Kiểm tra health" else "Check health"
                    }
                )
            }
        }
        healthResult?.let { result ->
            val statusColor = if (result.ok) Color(0xFF059669) else Color(0xFFDC2626)
            Text(
                text = "${if (result.ok) "OK" else "FAIL"} · ${result.message} · ${result.latencyMs}ms",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (vi) "Mock RAG" else "Mock RAG",
                modifier = Modifier.weight(1f),
                color = textMain,
                fontSize = 13.sp
            )
            Switch(
                checked = devSettings.mockRagEnabled,
                onCheckedChange = onMockRagChange,
                colors = SwitchDefaults.colors(checkedTrackColor = primaryBlue)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (vi) "Bypass khóa lái" else "Bypass driving lock",
                modifier = Modifier.weight(1f),
                color = textMain,
                fontSize = 13.sp
            )
            Switch(
                checked = devSettings.bypassDrivingLock,
                onCheckedChange = onBypassDrivingChange,
                colors = SwitchDefaults.colors(checkedTrackColor = primaryBlue)
            )
        }

        Text(
            text = if (vi) "HTTP log level" else "HTTP log level",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = textMain
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(HttpLogLevel.NONE, HttpLogLevel.BASIC, HttpLogLevel.BODY).forEach { level ->
                SettingsSelectableChip(
                    selected = devSettings.httpLogLevel == level,
                    label = level.name,
                    icon = Icons.Rounded.BugReport,
                    primaryColor = primaryBlue,
                    onClick = { onHttpLogLevelChange(level) }
                )
            }
        }

        HorizontalDivider(color = outlineVariant)
        lastQueryLatencyMs?.let { ms ->
            Text(
                text = if (vi) "Last query: ${ms}ms" else "Last query: ${ms}ms",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textMain
            )
        }
        Text(
            text = "v$appVersionName · debug · ${devSettings.effectiveBaseUrl}",
            fontSize = 11.sp,
            color = textMain.copy(alpha = 0.6f)
        )
    }
}
// --- END MODIFICATION ---
