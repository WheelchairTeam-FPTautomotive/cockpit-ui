package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.ui.theme.CockpitColors

/**
 * Reusable glassmorphism surface for the dark-tech cockpit.
 *
 * Dark/Central themes keep a translucent frosted look. LIGHT theme uses an
 * opaque fill and a stronger border so cards remain clearly visible against
 * the off-white dashboard background.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    theme: DisplayTheme = DisplayTheme.DARK,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit
) {
    val glassBase = CockpitColors.getSurfaceGlass(theme)
    val outline = CockpitColors.getOutlineVariant(theme)

    val isLight = theme == DisplayTheme.LIGHT
    val fillAlpha = if (isLight) 1.0f else 0.55f
    val borderWidth = if (isLight) 1.dp else 0.5.dp
    val borderAlpha = if (isLight) 0.6f else 0.35f
    val shadow = if (isLight) 2.dp else 0.dp

    Surface(
        modifier = modifier,
        shape = shape,
        color = glassBase.copy(alpha = fillAlpha),
        border = BorderStroke(borderWidth, outline.copy(alpha = borderAlpha)),
        tonalElevation = 0.dp,
        shadowElevation = shadow,
        content = content
    )
}
