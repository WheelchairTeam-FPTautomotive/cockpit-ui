package com.wheelchair.cockpit.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.DisplayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.zIndex

@Composable
fun MediaScreen(
    appLanguage: AppLanguage,
    displayTheme: DisplayTheme,
    primaryBlue: Color,
    surfaceContainer: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    modifier: Modifier = Modifier
) {
    val cyanAccent = primaryBlue
    
    var isQueueVisible by remember { mutableStateOf(false) }
    var isVolumeVisible by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.6f) }
    
    // Auto-hide volume
    LaunchedEffect(isVolumeVisible) {
        if (isVolumeVisible) {
            delay(3000)
            isVolumeVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- 1. Album Artwork Area (Swipeable Carousel) ---
            val scope = rememberCoroutineScope()
            val dragAnimatable = remember { Animatable(0f) }
            val drag = dragAnimatable.value
            
            // Queue temporarily appears if the user is actively swiping/browsing
            val isInteracting = dragAnimatable.isRunning || drag != 0f
            val effectiveQueueVisible = isQueueVisible || isInteracting
            
            val queueAnim by animateFloatAsState(
                targetValue = if (effectiveQueueVisible) 1f else 0f, 
                label = "queue",
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )

            Box(
                modifier = Modifier
                    .weight(1f) // Increased visual prominence
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { 
                                scope.launch {
                                    val threshold = 0.3f
                                    if (dragAnimatable.value > threshold) {
                                        // Swipe Right (Previous): complete to 1f, then snap to 0f
                                        dragAnimatable.animateTo(1f, animationSpec = tween(300))
                                        dragAnimatable.snapTo(0f)
                                    } else if (dragAnimatable.value < -threshold) {
                                        // Swipe Left (Next): complete to -1f, then snap to 0f
                                        dragAnimatable.animateTo(-1f, animationSpec = tween(300))
                                        dragAnimatable.snapTo(0f)
                                    } else {
                                        // Cancel: snap back to 0f
                                        dragAnimatable.animateTo(0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                    }
                                }
                            },
                            onDragCancel = { 
                                scope.launch {
                                    dragAnimatable.animateTo(0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val newVal = dragAnimatable.value + (dragAmount / 400f)
                                dragAnimatable.snapTo(newVal.coerceIn(-1f, 1f))
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                
                // Render covers from -2 to 2 to support exactly 5 visible items
                for (i in -2..2) {
                    val pos = i + drag // Continuous position based on drag
                    
                    // Alpha logic
                    // Queue OFF: 1.5x multiplier means alpha reaches 0 exactly at pos = ±0.66
                    // This ensures only the absolute center cover is visible when resting.
                    val baseAlpha = (1f - (abs(pos) * 1.5f)).coerceIn(0f, 1f) 
                    // Queue ON: 0.4x multiplier ensures 5 covers stay comfortably visible
                    val queueAlpha = (1f - (abs(pos) * 0.4f)).coerceIn(0f, 1f) 
                    val alpha = baseAlpha * (1f - queueAnim) + queueAlpha * queueAnim
                    
                    if (alpha > 0.01f) {
                        // Offset logic: 200dp spread when OFF, tighter 160dp spread when ON
                        val xOffset = (200.dp - (40.dp * queueAnim)) * pos
                        
                        // Scale logic
                        val scale = (1f - (abs(pos) * 0.2f)).coerceIn(0f, 1f)
                        
                        // Z-Index: covers closer to center (pos 0) are always on top
                        val zIndex = -abs(pos)
                        
                        // Visual emphasis for the card closest to center
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
                                    if (!isCenterCard) {
                                        scope.launch {
                                            // Smoothly pull tapped cover to center, then visually 'commit' track change
                                            dragAnimatable.animateTo(-i.toFloat(), animationSpec = tween(400, easing = FastOutSlowInEasing))
                                            dragAnimatable.snapTo(0f)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote, 
                                contentDescription = "Album Art", 
                                modifier = Modifier.size(120.dp), 
                                tint = iconTint
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // --- 2. Compact Playback Controls & Info ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp), // More compact lower area
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Compact Song Info
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Very Long Song Title That Does Not Fit...",
                        color = textMain,
                        fontSize = 24.sp, // Reduced font size
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { /* action */ }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The Rolling Stones • Hackney Diamonds",
                        color = textSecondary,
                        fontSize = 16.sp, // Reduced font size
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { /* action */ }
                    )
                }
                
                // Center: Controls & Progress
                Column(
                    modifier = Modifier.weight(2f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Controls (Shuffle -> Prev -> Play -> Next -> Repeat)
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(onClick = { /* Shuffle */ }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = cyanAccent, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(onClick = { /* Prev */ }, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = textMain, modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(cyanAccent)
                                .clickable { /* Play/Pause */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Pause, contentDescription = "Play/Pause", tint = surfaceContainer, modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        IconButton(onClick = { /* Next */ }, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = textMain, modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(onClick = { /* Repeat */ }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Repeat, contentDescription = "Repeat", tint = textSecondary, modifier = Modifier.size(28.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Progress Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text("1:42", color = textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.width(16.dp))
                        // Progress Track
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(outlineVariant)
                                .clickable { /* seek */ }
                        ) {
                            Box(modifier = Modifier.fillMaxWidth(0.43f).fillMaxHeight().background(cyanAccent))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("3:58", color = textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                
                // Right: Queue & Volume triggers
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Queue Button
                        IconButton(onClick = { isQueueVisible = !isQueueVisible }, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = if (isQueueVisible) cyanAccent else textSecondary, modifier = Modifier.size(36.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        // Volume Trigger Button
                        IconButton(onClick = { isVolumeVisible = !isVolumeVisible }, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Rounded.VolumeUp, contentDescription = "Volume", tint = textSecondary, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }
        }
        
        // --- 3. Refined Volume Popup Overlay ---
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
                    // Dynamic Speaker Icon
                    val volIcon = when {
                        volumeLevel == 0f -> Icons.Rounded.VolumeMute
                        volumeLevel <= 0.5f -> Icons.Rounded.VolumeDown
                        else -> Icons.Rounded.VolumeUp
                    }
                    Icon(volIcon, contentDescription = null, tint = textMain, modifier = Modifier.size(32.dp))
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Volume Track
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .width(16.dp)
                            .clip(CircleShape)
                            .background(outlineVariant)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { _, _ -> /* Update volume */ }
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(volumeLevel).background(cyanAccent))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Numeric Volume Value (replacing the max-volume icon)
                    Text(
                        text = "${(volumeLevel * 100).toInt()}", 
                        color = textMain, 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
