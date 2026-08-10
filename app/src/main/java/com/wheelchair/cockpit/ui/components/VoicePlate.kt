package com.wheelchair.cockpit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.CopilotUiState

/**
 * AAOS bottom voice plate.
 *
 * Enters/exits from the bottom, shows listening/thinking/speaking states, and
 * anchors above the system dock so it never covers climate or nav affordances.
 */
@Composable
fun VoicePlate(
    state: CopilotUiState,
    rmsLevel: Float,
    partialTranscript: String,
    appLanguage: AppLanguage,
    primaryColor: Color,
    surfaceColor: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    modifier: Modifier = Modifier
) {
    val active = state is CopilotUiState.Listening ||
            state is CopilotUiState.Thinking ||
            state is CopilotUiState.Speaking
    val vi = appLanguage == AppLanguage.VIETNAMESE

    AnimatedVisibility(
        visible = active,
        enter = slideInVertically(
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            initialOffsetY = { it }
        ) + fadeIn(tween(240)),
        exit = slideOutVertically(
            animationSpec = tween(260),
            targetOffsetY = { it }
        ) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        val (label, showWaveform) = when (state) {
            is CopilotUiState.Listening ->
                (if (vi) "Đang lắng nghe…" else "Listening…") to true
            is CopilotUiState.Thinking ->
                (if (vi) "Đang suy nghĩ…" else "Thinking…") to true
            is CopilotUiState.Speaking ->
                (if (vi) "Đang trả lởi…" else "Speaking…") to false
            else -> "" to false
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
            color = surfaceColor,
            border = BorderStroke(1.dp, outlineVariant),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Status mic/wave icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(primaryColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (showWaveform) {
                        StitchVoiceWaveform(
                            rmsLevel = rmsLevel,
                            primaryColor = primaryColor,
                            modifier = Modifier.size(28.dp, 20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        color = textMain,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (partialTranscript.isNotBlank() && state is CopilotUiState.Listening) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = partialTranscript,
                            color = textSecondary,
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
