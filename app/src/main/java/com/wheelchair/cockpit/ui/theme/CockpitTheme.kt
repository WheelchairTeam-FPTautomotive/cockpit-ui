package com.wheelchair.cockpit.ui.theme

import androidx.compose.ui.graphics.Color
import com.wheelchair.cockpit.model.DisplayTheme

object CockpitColors {
    fun getPrimaryBlue(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFF00458E)
        DisplayTheme.DARK -> Color(0xFF38BDF8)
        DisplayTheme.CENTRAL -> Color(0xFF60A5FA)
    }

    fun getPrimaryContainer(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFF00458E)
        DisplayTheme.DARK -> Color(0xFF0284C7)
        DisplayTheme.CENTRAL -> Color(0xFF2563EB)
    }

    fun getBackgroundBg(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFFAF8FF)
        DisplayTheme.DARK -> Color(0xFF090D16)
        DisplayTheme.CENTRAL -> Color(0xFF0B1329)
    }

    fun getSurfaceContainer(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFFFFFFF)
        DisplayTheme.DARK -> Color(0xFF131C2E)
        DisplayTheme.CENTRAL -> Color(0xFF172554)
    }

    fun getSurfaceContainerLow(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFF2F3FF)
        DisplayTheme.DARK -> Color(0xFF162032)
        DisplayTheme.CENTRAL -> Color(0xFF1E293B)
    }

    fun getSurfaceContainerHighest(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFEAEDFF)
        DisplayTheme.DARK -> Color(0xFF1E2D4A)
        DisplayTheme.CENTRAL -> Color(0xFF1E40AF)
    }

    fun getTextMain(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFF131B2E)
        DisplayTheme.DARK -> Color(0xFFF8FAFC)
        DisplayTheme.CENTRAL -> Color(0xFFFFFFFF)
    }

    fun getTextSecondary(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFF5A5C6E)
        DisplayTheme.DARK -> Color(0xFF94A3B8)
        DisplayTheme.CENTRAL -> Color(0xFF93C5FD)
    }

    fun getOutlineVariant(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFC4C5D5)
        DisplayTheme.DARK -> Color(0xFF2E3D59)
        DisplayTheme.CENTRAL -> Color(0xFF1E40AF)
    }
}
