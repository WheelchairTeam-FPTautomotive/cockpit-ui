package com.wheelchair.cockpit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.sp
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.R

/**
 * Premium dark-tech color system for the AAOS cockpit overhaul.
 *
 * All tokens are theme-aware so the existing LIGHT/CENTRAL modes keep working,
 * but the overhaul is optimized for the DARK palette.
 */
object CockpitColors {
    fun getPrimaryBlue(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFF0F62FE)
        DisplayTheme.DARK -> Color(0xFF337CF8)
        DisplayTheme.CENTRAL -> Color(0xFF60A5FA)
    }

    fun getPrimaryContainer(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFE0E8FF)
        DisplayTheme.DARK -> Color(0xFF1C3B70)
        DisplayTheme.CENTRAL -> Color(0xFF2563EB)
    }

    fun getBackgroundBg(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFECEFF3) // Slightly deeper off-white for card contrast
        DisplayTheme.DARK -> Color(0xFF0B0D10)  // Deep charcoal / OLED-black
        DisplayTheme.CENTRAL -> Color(0xFF0B1329)
    }

    fun getSurfaceContainer(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFFFFFFF)
        DisplayTheme.DARK -> Color(0xFF14171C) // Semi-transparent dark panels basis
        DisplayTheme.CENTRAL -> Color(0xFF172554)
    }

    fun getSurfaceContainerLow(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFF4F5F7)
        DisplayTheme.DARK -> Color(0xFF0B0D10)
        DisplayTheme.CENTRAL -> Color(0xFF1E293B)
    }

    fun getSurfaceContainerHighest(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFE0E0E0)
        DisplayTheme.DARK -> Color(0xFF152642)
        DisplayTheme.CENTRAL -> Color(0xFF1E40AF)
    }

    /**
     * Translucent surface used for glassmorphism cards and panels.
     * Consumers should blend with their own alpha or use [GlassSurface].
     */
    fun getSurfaceGlass(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFFFFFFF)
        DisplayTheme.DARK -> Color(0xFF1B1E24) // Charcoal frosted glass basis
        DisplayTheme.CENTRAL -> Color(0xFF1E40AF)
    }

    fun getTextMain(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFF161616)
        DisplayTheme.DARK -> Color(0xFFFFFFFF)
        DisplayTheme.CENTRAL -> Color(0xFFFFFFFF)
    }

    fun getTextSecondary(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFF525252)
        DisplayTheme.DARK -> Color(0xFFCFCFD0)
        DisplayTheme.CENTRAL -> Color(0xFF93C5FD)
    }

    fun getOutlineVariant(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFD0D5DD) // Stronger border for light cards
        DisplayTheme.DARK -> Color(0xFF1C3B70)
        DisplayTheme.CENTRAL -> Color(0xFF1E40AF)
    }

    fun getAccentGreen(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFF24A148)
        DisplayTheme.DARK -> Color(0xFF16C78C)
        DisplayTheme.CENTRAL -> Color(0xFF4ADE80)
    }

    fun getWarningAmber(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFF1C21B)
        DisplayTheme.DARK -> Color(0xFFF59E0B)
        DisplayTheme.CENTRAL -> Color(0xFFFBBF24)
    }

    fun getDangerRed(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFDA1E28)
        DisplayTheme.DARK -> Color(0xFFEF4444)
        DisplayTheme.CENTRAL -> Color(0xFFF87171)
    }

    fun getDangerRedContainer(theme: DisplayTheme): Color = when (theme) {
        DisplayTheme.LIGHT -> Color(0xFFFF8389)
        DisplayTheme.DARK -> Color(0xFF7F1D1D)
        DisplayTheme.CENTRAL -> Color(0xFF991B1B)
    }
}

/**
 * Calibrated typography scale for in-car readability.
 *
 * Display/Data sizes are oversized for glanceability; captions are restrained
 * for dense status rows.
 */
object CockpitTypography {
    val InterRegularFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal)
    )

    val display: TextStyle = TextStyle(
        fontFamily = InterRegularFamily,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.5).sp
    )

    val title: TextStyle = TextStyle(
        fontFamily = InterRegularFamily,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    )

    val body: TextStyle = TextStyle(
        fontFamily = InterRegularFamily,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    )

    val caption: TextStyle = TextStyle(
        fontFamily = InterRegularFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.25.sp
    )

    val data: TextStyle = TextStyle(
        fontFamily = InterRegularFamily,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.5).sp
    )

    val label: TextStyle = TextStyle(
        fontFamily = InterRegularFamily,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.5.sp
    )
}
