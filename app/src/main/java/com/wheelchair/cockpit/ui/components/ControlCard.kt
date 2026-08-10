package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.ui.theme.CockpitTypography

/**
 * Reusable control card for the dashboard grid.
 *
 * Shows a label, icon, and active-state value. Tapping toggles the action.
 */
@Composable
fun ControlCard(
    title: String,
    value: String,
    icon: ImageVector,
    isActive: Boolean,
    primaryColor: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    theme: DisplayTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isActive) primaryColor else textSecondary.copy(alpha = 0.5f)

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        theme = theme,
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = CockpitTypography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = textMain
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = value,
                        style = CockpitTypography.caption,
                        color = accent
                    )
                }
            }
        }
    )
}
