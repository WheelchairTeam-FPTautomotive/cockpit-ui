package com.wheelchair.cockpit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mentor #17 — mock actuation / RAG-success motion (APNG not required; Compose motion is enough).
 */
enum class MockActuationKind {
    DOOR,
    HVAC,
    MUSIC,
    RAG
}

data class MockActuationEvent(
    val kind: MockActuationKind,
    val titleVi: String,
    val titleEn: String,
    val subtitleVi: String,
    val subtitleEn: String,
    val token: Long = System.currentTimeMillis()
)

fun mockActuationForCommandId(commandId: String?): MockActuationEvent? {
    val id = commandId?.trim()?.uppercase().orEmpty()
    if (id.isEmpty() || id == "GENERIC_CONTROL") return null
    return when {
        id.contains("DOOR") -> MockActuationEvent(
            kind = MockActuationKind.DOOR,
            titleVi = "Cửa xe",
            titleEn = "Doors",
            subtitleVi = "Mô phỏng điều khiển cửa thành công",
            subtitleEn = "Mock door actuation succeeded"
        )
        id.contains("HVAC") || id.contains("AC") || id.contains("CLIMATE") -> MockActuationEvent(
            kind = MockActuationKind.HVAC,
            titleVi = "Điều hòa",
            titleEn = "Climate",
            subtitleVi = "Mô phỏng HVAC thành công",
            subtitleEn = "Mock HVAC actuation succeeded"
        )
        id.contains("MUSIC") || id.contains("MEDIA") -> MockActuationEvent(
            kind = MockActuationKind.MUSIC,
            titleVi = "Âm nhạc",
            titleEn = "Music",
            subtitleVi = "Mô phỏng phát nhạc",
            subtitleEn = "Mock music playback started"
        )
        else -> MockActuationEvent(
            kind = MockActuationKind.DOOR,
            titleVi = "Điều khiển xe",
            titleEn = "Vehicle control",
            subtitleVi = "Mô phỏng lệnh $id",
            subtitleEn = "Mock command $id"
        )
    }
}

fun mockActuationForRagSuccess(vietnamese: Boolean): MockActuationEvent =
    MockActuationEvent(
        kind = MockActuationKind.RAG,
        titleVi = "Tài liệu OEM",
        titleEn = "OEM manual",
        subtitleVi = "Trả lời có trích dẫn — phản hồi thành công",
        subtitleEn = "Cited answer ready — RAG success"
    )

@Composable
fun MockActuationOverlay(
    event: MockActuationEvent?,
    vietnamese: Boolean,
    modifier: Modifier = Modifier
) {
    // --- START MODIFICATION ---
    AnimatedVisibility(
        visible = event != null,
        enter = fadeIn(tween(220)) + slideInVertically(
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 3 }
        ) + scaleIn(initialScale = 0.92f, animationSpec = tween(320)),
        exit = fadeOut(tween(280)) + slideOutVertically(
            animationSpec = tween(280),
            targetOffsetY = { it / 4 }
        ),
        modifier = modifier
    ) {
        val active = event ?: return@AnimatedVisibility
        val accent = when (active.kind) {
            MockActuationKind.DOOR -> Color(0xFF38BDF8)
            MockActuationKind.HVAC -> Color(0xFF34D399)
            MockActuationKind.MUSIC -> Color(0xFFA78BFA)
            MockActuationKind.RAG -> Color(0xFFFBBF24)
        }
        val icon: ImageVector = when (active.kind) {
            MockActuationKind.DOOR -> Icons.Rounded.Lock
            MockActuationKind.HVAC -> Icons.Rounded.AcUnit
            MockActuationKind.MUSIC -> Icons.Rounded.MusicNote
            MockActuationKind.RAG -> Icons.Rounded.MenuBook
        }
        val pulse = rememberInfiniteTransition(label = "actuationPulse")
        val scale by pulse.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "iconScale"
        )

        Surface(
            modifier = Modifier
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
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (vietnamese) active.titleVi else active.titleEn,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (vietnamese) active.subtitleVi else active.subtitleEn,
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                }
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
    }
    // --- END MODIFICATION ---
}
