package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.ui.theme.CockpitTypography

/**
 * Compact music player bar for the bottom of the dashboard.
 *
 * Shows album icon, track name, artist, play/pause and skip controls,
 * and a thin progress indicator.
 */
@Composable
fun MusicPlayerBar(
    primaryColor: Color,
    surfaceColor: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    theme: DisplayTheme,
    appLanguage: AppLanguage,
    modifier: Modifier = Modifier
) {
    val vi = appLanguage == AppLanguage.VIETNAMESE
    var isPlaying by remember { mutableStateOf(false) }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        theme = theme,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Progress bar at very top of the bar
                LinearProgressIndicator(
                    progress = { if (isPlaying) 0.42f else 0.42f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = primaryColor,
                    trackColor = outlineVariant.copy(alpha = 0.3f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Album art placeholder
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(primaryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Track info
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (vi) "Không có bài hát nào" else "No track playing",
                            style = CockpitTypography.body.copy(fontWeight = FontWeight.SemiBold),
                            color = textMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "—",
                            style = CockpitTypography.caption,
                            color = textSecondary,
                            maxLines = 1
                        )
                    }

                    // Playback controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaButton(
                            icon = Icons.Rounded.SkipPrevious,
                            description = "Previous",
                            tint = textSecondary,
                            size = 28,
                            onClick = {}
                        )
                        // Play/Pause — larger, accented
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(primaryColor.copy(alpha = 0.15f))
                                .clickable { isPlaying = !isPlaying },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = primaryColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        MediaButton(
                            icon = Icons.Rounded.SkipNext,
                            description = "Next",
                            tint = textSecondary,
                            size = 28,
                            onClick = {}
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun MediaButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    size: Int,
    onClick: () -> Unit
) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier
            .size(size.dp)
            .clickable(onClick = onClick)
    )
}
