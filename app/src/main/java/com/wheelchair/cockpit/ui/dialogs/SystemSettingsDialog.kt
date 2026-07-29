package com.wheelchair.cockpit.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onThemeChange: (DisplayTheme) -> Unit
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
                modifier = Modifier.width(480.dp),
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
