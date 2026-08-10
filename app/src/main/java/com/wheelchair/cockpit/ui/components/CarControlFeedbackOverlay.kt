package com.wheelchair.cockpit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.wheelchair.cockpit.R
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.ControlKind
import com.wheelchair.cockpit.model.CopilotUiState

/**
 * One-shot car-control confirmation overlay.
 *
 * Shows a Lottie animation when available, otherwise falls back to a Compose
 * animated icon so the demo never crashes on a missing/broken asset.
 */
@Composable
fun CarControlFeedbackOverlay(
    state: CopilotUiState,
    appLanguage: AppLanguage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val event = when (state) {
        is CopilotUiState.ControlSuccess -> state.kind
        is CopilotUiState.ControlFail -> null // handled below
        is CopilotUiState.RagAnswer -> ControlKind.RAG
        else -> null
    }
    val isFailure = state is CopilotUiState.ControlFail
    val visible = event != null || isFailure
    val vi = appLanguage == AppLanguage.VIETNAMESE

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) +
                slideInVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 4 }
                ) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(300)),
        exit = fadeOut(tween(220)) +
                slideOutVertically(
                    animationSpec = tween(240),
                    targetOffsetY = { it / 4 }
                ),
        modifier = modifier
    ) {
        // Auto-dismiss after 800–900ms so it never lingers while driving.
        LaunchedEffect(state) {
            if (visible) {
                kotlinx.coroutines.delay(850L)
                onDismiss()
            }
        }

        if (isFailure) {
            FeedbackChip(
                icon = Icons.Rounded.Close,
                title = if (vi) "Không thực hiện được" else "Could not complete",
                subtitle = if (vi) "Vui lòng thử lại" else "Please try again",
                accent = Color(0xFFEF4444),
                modifier = Modifier.fillMaxWidth()
            )
        } else if (event != null) {
            val (title, subtitle, icon, accent) = controlMeta(event, vi)
            FeedbackChip(
                icon = icon,
                title = title,
                subtitle = subtitle,
                accent = accent,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class ControlMeta(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color
)

private fun controlMeta(kind: ControlKind, vietnamese: Boolean): ControlMeta = when (kind) {
    ControlKind.DOOR -> ControlMeta(
        title = if (vietnamese) "Cửa xe" else "Doors",
        subtitle = if (vietnamese) "Đã khóa/mở khóa thành công" else "Lock/unlock successful",
        icon = Icons.Rounded.Lock,
        accent = Color(0xFF38BDF8)
    )
    ControlKind.HVAC -> ControlMeta(
        title = if (vietnamese) "Điều hòa" else "Climate",
        subtitle = if (vietnamese) "Đã điều chỉnh thành công" else "Climate adjusted",
        icon = Icons.Rounded.AcUnit,
        accent = Color(0xFF34D399)
    )
    ControlKind.MUSIC -> ControlMeta(
        title = if (vietnamese) "Âm nhạc" else "Music",
        subtitle = if (vietnamese) "Đã bật nhạc" else "Music started",
        icon = Icons.Rounded.MusicNote,
        accent = Color(0xFFA78BFA)
    )
    ControlKind.RAG -> ControlMeta(
        title = if (vietnamese) "Tài liệu OEM" else "OEM manual",
        subtitle = if (vietnamese) "Trả lời có trích dẫn" else "Cited answer ready",
        icon = Icons.Rounded.MenuBook,
        accent = Color(0xFFFBBF24)
    )
}

@Composable
private fun FeedbackChip(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val compositionResult = rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_control_success)
    )
    val composition = compositionResult.value
    val lottieProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        speed = 1.2f
    )
    val hasLottie = composition != null

    // Fallback scale animation
    val targetScale = if (hasLottie) 1f else 1.15f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "feedbackScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xE6111827),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(scale)
                    .background(accent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (hasLottie) {
                    LottieAnimation(
                        composition = composition,
                        progress = { lottieProgress },
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = subtitle,
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = if (hasLottie) Icons.Rounded.CheckCircle else icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
        }
    }
}
