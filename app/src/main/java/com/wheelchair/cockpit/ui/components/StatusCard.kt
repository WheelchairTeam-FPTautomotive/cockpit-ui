package com.wheelchair.cockpit.ui.components

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
 * Reusable status card for the dashboard grid.
 *
 * Displays a title with an optional icon and a list of label/value rows.
 */
@Composable
fun StatusCard(
    title: String,
    icon: ImageVector?,
    items: List<Pair<String, String>>,
    primaryColor: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    theme: DisplayTheme,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        theme = theme,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = title,
                            tint = primaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = CockpitTypography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = textMain
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                items.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = CockpitTypography.caption,
                            color = textSecondary
                        )
                        Text(
                            text = value,
                            style = CockpitTypography.body.copy(fontWeight = FontWeight.SemiBold),
                            color = textMain,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    )
}
