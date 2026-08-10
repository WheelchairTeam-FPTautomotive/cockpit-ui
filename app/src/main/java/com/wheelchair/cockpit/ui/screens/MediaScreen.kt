package com.wheelchair.cockpit.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.wheelchair.cockpit.media.NowPlaying
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.DisplayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = (ms / 1000L).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

@Composable
fun MediaScreen(
    appLanguage: AppLanguage,
    displayTheme: DisplayTheme,
    primaryBlue: Color,
    surfaceContainer: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    // MODIFIED: bound to MediaControllerRepository
    nowPlaying: NowPlaying = NowPlaying(),
    onPlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onOpenSource: () -> Unit = {},
    onSelectLocal: () -> Unit = {},
    onSelectYouTube: () -> Unit = {},
    onSelectSoundCloud: () -> Unit = {},
    // MODIFIED: STREAM_MUSIC volume (0f..1f)
    volumeLevel: Float = 0.6f,
    onVolumeChange: (Float) -> Unit = {},
    isDrivingRestricted: Boolean = false,
    onLockedInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cyanAccent = primaryBlue
    val guard: (() -> Unit) -> () -> Unit = { action ->
        {
            if (isDrivingRestricted) onLockedInteraction() else action()
        }
    }

    var isQueueVisible by remember { mutableStateOf(false) }
    var isVolumeVisible by remember { mutableStateOf(false) }
    var localVolume by remember { mutableFloatStateOf(volumeLevel) }

    LaunchedEffect(volumeLevel) {
        localVolume = volumeLevel
    }

    // Auto-hide volume
    LaunchedEffect(isVolumeVisible) {
        if (isVolumeVisible) {
            delay(3500)
            isVolumeVisible = false
        }
    }
    LaunchedEffect(isDrivingRestricted) {
        if (isDrivingRestricted) {
            isQueueVisible = false
            isVolumeVisible = false
        }
    }

    val scope = rememberCoroutineScope()
    val dragAnimatable = remember { Animatable(0f) }
    val drag = dragAnimatable.value

    val onSkipNextState = rememberUpdatedState(onSkipNext)
    val onSkipPreviousState = rememberUpdatedState(onSkipPrevious)

    // MODIFIED: cover swipe / button skip share the same animated commit
    suspend fun commitSkip(direction: Int) {
        // direction: +1 = previous (swipe right), -1 = next (swipe left)
        dragAnimatable.animateTo(direction.toFloat(), animationSpec = tween(280, easing = FastOutSlowInEasing))
        dragAnimatable.snapTo(0f)
        if (direction > 0) onSkipPreviousState.value() else onSkipNextState.value()
    }

    fun skipNextAnimated() {
        if (isDrivingRestricted) {
            onLockedInteraction()
            return
        }
        scope.launch { commitSkip(-1) }
    }

    fun skipPreviousAnimated() {
        if (isDrivingRestricted) {
            onLockedInteraction()
            return
        }
        scope.launch { commitSkip(1) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
            .then(if (isDrivingRestricted) Modifier.alpha(0.72f) else Modifier)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Source chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    "Local" to onSelectLocal,
                    "YT Music" to onSelectYouTube,
                    "SoundCloud" to onSelectSoundCloud
                ).forEach { (label, action) ->
                    val selected = when (label) {
                        "Local" -> nowPlaying.packageName == "com.wheelchair.cockpit" ||
                            nowPlaying.sourceLabel.equals("Local", ignoreCase = true)
                        "YT Music" -> nowPlaying.packageName == "com.google.android.apps.youtube.music" ||
                            nowPlaying.sourceLabel.contains("YouTube", ignoreCase = true)
                        "SoundCloud" -> nowPlaying.packageName == "com.soundcloud.android" ||
                            nowPlaying.sourceLabel.contains("SoundCloud", ignoreCase = true)
                        else -> false
                    }
                    Text(
                        text = label,
                        color = if (selected) surfaceContainer else textMain,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) cyanAccent else outlineVariant.copy(alpha = 0.35f))
                            .clickable(onClick = guard { action() })
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            // --- 1. Album Artwork Area (Swipeable Carousel) ---
            val isInteracting = dragAnimatable.isRunning || drag != 0f
            val effectiveQueueVisible = isQueueVisible || isInteracting

            val queueAnim by animateFloatAsState(
                targetValue = if (effectiveQueueVisible) 1f else 0f,
                label = "queue",
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(isDrivingRestricted) {
                        if (isDrivingRestricted) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.changedToDown() }) {
                                        onLockedInteraction()
                                    }
                                }
                            }
                        } else {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        val threshold = 0.28f
                                        when {
                                            dragAnimatable.value > threshold -> commitSkip(1)
                                            dragAnimatable.value < -threshold -> commitSkip(-1)
                                            else -> dragAnimatable.animateTo(
                                                0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                )
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        dragAnimatable.animateTo(
                                            0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                    }
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    val newVal = dragAnimatable.value + (dragAmount / 400f)
                                    dragAnimatable.snapTo(newVal.coerceIn(-1f, 1f))
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                for (i in -2..2) {
                    val pos = i + drag
                    val baseAlpha = (1f - (abs(pos) * 1.5f)).coerceIn(0f, 1f)
                    val queueAlpha = (1f - (abs(pos) * 0.4f)).coerceIn(0f, 1f)
                    val alpha = baseAlpha * (1f - queueAnim) + queueAlpha * queueAnim

                    if (alpha > 0.01f) {
                        val xOffset = (200.dp - (40.dp * queueAnim)) * pos
                        val scale = (1f - (abs(pos) * 0.2f)).coerceIn(0f, 1f)
                        val zIndex = -abs(pos)
                        val isCenterCard = abs(pos) < 0.5f
                        val bgColor = if (isCenterCard) cyanAccent.copy(alpha = 0.2f) else outlineVariant
                        val borderWidth = if (isCenterCard) 2.dp else 0.dp
                        val bColor = if (isCenterCard) cyanAccent else Color.Transparent
                        val iconTint = if (isCenterCard) cyanAccent else textSecondary

                        Box(
                            modifier = Modifier
                                .fillMaxHeight(scale)
                                .aspectRatio(1f)
                                .offset(x = xOffset)
                                .alpha(alpha)
                                .zIndex(zIndex)
                                .clip(RoundedCornerShape(40.dp))
                                .background(bgColor)
                                .border(borderWidth, bColor, RoundedCornerShape(40.dp))
                                .clickable {
                                    if (isDrivingRestricted) {
                                        onLockedInteraction()
                                    } else if (!isCenterCard) {
                                        // Tap neighbor cover → same as skip prev/next
                                        if (i < 0) skipPreviousAnimated() else skipNextAnimated()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // MODIFIED: show queue neighbor covers (bitmap or URI), not only center
                            val (art, artUri) = nowPlaying.artForOffset(i)
                            when {
                                art != null && !art.isRecycled -> {
                                    Image(
                                        bitmap = art.asImageBitmap(),
                                        contentDescription = nowPlaying.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                !artUri.isNullOrBlank() -> {
                                    AsyncImage(
                                        model = artUri,
                                        contentDescription = nowPlaying.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                else -> {
                                    Icon(
                                        Icons.Rounded.MusicNote,
                                        contentDescription = "Album Art",
                                        modifier = Modifier.size(if (isCenterCard) 120.dp else 72.dp),
                                        tint = iconTint
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 2. Compact Playback Controls & Info ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = nowPlaying.title,
                        color = textMain,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = guard { onOpenSource() })
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${nowPlaying.artist} · ${nowPlaying.sourceLabel}",
                        color = textSecondary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = guard { onOpenSource() })
                    )
                }

                Column(
                    modifier = Modifier.weight(2f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(onClick = guard { onSelectLocal() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Local queue", tint = cyanAccent, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(onClick = { skipPreviousAnimated() }, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = textMain, modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(cyanAccent)
                                .clickable(onClick = guard { onPlayPause() }),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (nowPlaying.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = surfaceContainer,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        IconButton(onClick = { skipNextAnimated() }, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = textMain, modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(onClick = guard { onOpenSource() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Repeat, contentDescription = "Open source app", tint = textSecondary, modifier = Modifier.size(28.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text(formatMs(nowPlaying.positionMs), color = textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.width(16.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(outlineVariant)
                                .clickable(onClick = guard { onPlayPause() })
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(nowPlaying.progress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(cyanAccent)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(formatMs(nowPlaying.durationMs), color = textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = guard { isQueueVisible = !isQueueVisible }, modifier = Modifier.size(64.dp)) {
                            Icon(
                                Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "Queue",
                                tint = if (isQueueVisible) cyanAccent else textSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(
                            onClick = guard {
                                isVolumeVisible = !isVolumeVisible
                                if (isVolumeVisible) localVolume = volumeLevel
                            },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = "Volume",
                                tint = if (isVolumeVisible) cyanAccent else textSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- 3. Volume popup (vertical drag → STREAM_MUSIC) ---
        AnimatedVisibility(
            visible = isVolumeVisible,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(280.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(surfaceContainer.copy(alpha = 0.95f))
                    .border(1.dp, outlineVariant, RoundedCornerShape(40.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize().padding(vertical = 24.dp)
                ) {
                    val volIcon = when {
                        localVolume <= 0.01f -> Icons.AutoMirrored.Rounded.VolumeMute
                        localVolume <= 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    }
                    Icon(volIcon, contentDescription = null, tint = textMain, modifier = Modifier.size(32.dp))

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .width(28.dp)
                            .clip(CircleShape)
                            .background(outlineVariant)
                            .pointerInput(isDrivingRestricted) {
                                if (isDrivingRestricted) {
                                    awaitEachGesture {
                                        awaitFirstDown()
                                        onLockedInteraction()
                                    }
                                    return@pointerInput
                                }
                                val latestVolumeCb = onVolumeChange
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val h = size.height.coerceAtLeast(1).toFloat()
                                    fun yToVol(y: Float) = (1f - (y / h)).coerceIn(0f, 1f)
                                    var vol = yToVol(down.position.y)
                                    localVolume = vol
                                    latestVolumeCb(vol)
                                    drag(down.id) { change ->
                                        vol = yToVol(change.position.y)
                                        localVolume = vol
                                        latestVolumeCb(vol)
                                        if (change.positionChange() != Offset.Zero) {
                                            change.consume()
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(localVolume.coerceIn(0f, 1f))
                                .background(cyanAccent)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "${(localVolume * 100).toInt()}",
                        color = textMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
