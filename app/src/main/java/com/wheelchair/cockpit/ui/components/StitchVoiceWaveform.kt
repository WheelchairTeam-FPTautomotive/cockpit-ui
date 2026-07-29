package com.wheelchair.cockpit.ui.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StitchVoiceWaveform(
    rmsLevel: Float,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 8
    val animatedHeights = (0 until barCount).map { i ->
        val offset = i * 0.12f
        val rawLevel = ((rmsLevel + offset) % 1.0f)
        val targetHeight = if (rmsLevel > 0.02f) {
            0.25f + rawLevel * 0.75f
        } else {
            0.18f + (kotlin.math.sin(System.nanoTime() / 400_000_000.0 + i).toFloat() * 0.12f)
        }
        animateFloatAsState(
            targetValue = targetHeight.coerceIn(0.15f, 1f),
            animationSpec = tween(durationMillis = 100, easing = EaseInOutSine),
            label = "stitchBar$i"
        ).value
    }

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val barWidth = (totalWidth / (barCount * 1.8f)).coerceIn(3.dp.toPx(), 10.dp.toPx())
        val spacing = (barWidth * 0.7f)
        val contentWidth = barCount * barWidth + (barCount - 1) * spacing
        val startX = (totalWidth - contentWidth) / 2f

        animatedHeights.forEachIndexed { i, heightFactor ->
            val barHeight = size.height * heightFactor
            val x = startX + i * (barWidth + spacing)
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = primaryColor.copy(alpha = 0.4f + heightFactor * 0.6f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}
