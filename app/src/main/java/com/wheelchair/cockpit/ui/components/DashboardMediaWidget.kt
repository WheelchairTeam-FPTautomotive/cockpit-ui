package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wheelchair.cockpit.media.NowPlaying
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.DisplayTheme
import com.wheelchair.cockpit.ui.theme.CockpitTypography

@Composable
fun DashboardMediaWidget(
    primaryColor: Color,
    surfaceColor: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    theme: DisplayTheme,
    appLanguage: AppLanguage,
    // MODIFIED: bound to MediaControllerRepository
    nowPlaying: NowPlaying = NowPlaying(),
    onPlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onOpenSource: () -> Unit = {},
    isDrivingRestricted: Boolean = false,
    onLockedInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val guard: (() -> Unit) -> () -> Unit = { action ->
        {
            if (isDrivingRestricted) onLockedInteraction() else action()
        }
    }

    GlassSurface(
        modifier = modifier
            .fillMaxSize()
            .then(if (isDrivingRestricted) Modifier.alpha(0.55f) else Modifier),
        theme = theme,
        content = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .clickable(onClick = guard { onOpenSource() }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art with vector fallback when metadata art is null
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(primaryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    val art = nowPlaying.albumArt
                    if (art != null && !art.isRecycled) {
                        Image(
                            bitmap = art.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = nowPlaying.title,
                        style = CockpitTypography.title,
                        color = textMain,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${nowPlaying.artist} · ${nowPlaying.sourceLabel}",
                        style = CockpitTypography.body,
                        color = textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = textSecondary,
                        modifier = Modifier.size(32.dp).clickable(onClick = guard { onSkipPrevious() })
                    )
                    Icon(
                        imageVector = if (nowPlaying.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
                        tint = textMain,
                        modifier = Modifier.size(36.dp).clickable(onClick = guard { onPlayPause() })
                    )
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = textSecondary,
                        modifier = Modifier.size(32.dp).clickable(onClick = guard { onSkipNext() })
                    )
                }
            }
        }
    )
}
