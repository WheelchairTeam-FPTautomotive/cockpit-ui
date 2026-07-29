package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.AssistantState

@Composable
fun SystemStatusCard(
    assistantState: AssistantState,
    statusText: String,
    pulseScale: Float,
    appLanguage: AppLanguage,
    primaryBlue: Color,
    primaryContainer: Color,
    surfaceContainer: Color,
    textMain: Color,
    indicatorColor: Color,
    outlineVariant: Color,
    onWakeSimulate: () -> Unit,
    onMicTap: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = surfaceContainer,
        border = BorderStroke(1.dp, outlineVariant),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                    Box(modifier = Modifier.size(40.dp).scale(pulseScale).background(indicatorColor.copy(alpha = 0.15f), CircleShape))
                    Box(modifier = Modifier.size(30.dp).background(primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Rounded.RadioButtonChecked, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (assistantState) {
                            AssistantState.IDLE -> if (appLanguage == AppLanguage.VIETNAMESE) "TRẠNG THÁI CHỜ" else "SYSTEM STANDBY"
                            AssistantState.WAKE_DETECTED -> if (appLanguage == AppLanguage.VIETNAMESE) "ĐANG LẮNG NGHE..." else "LISTENING..."
                            AssistantState.PROCESSING -> if (appLanguage == AppLanguage.VIETNAMESE) "ĐANG XỬ LÝ..." else "ANALYZING..."
                            AssistantState.SPEAKING -> if (appLanguage == AppLanguage.VIETNAMESE) "TRỢ LÝ ĐANG PHÁT" else "ASSISTANT SPEAKING"
                        },
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = primaryBlue, letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = statusText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textMain, maxLines = 2)
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onWakeSimulate,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, primaryBlue),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = primaryBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hey Car", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryBlue)
                }

                Button(
                    onClick = onMicTap,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (assistantState == AssistantState.SPEAKING) Color(0xFFEF4444) else primaryBlue,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(if (assistantState == AssistantState.SPEAKING) Icons.Rounded.Stop else Icons.Rounded.Mic, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (assistantState == AssistantState.SPEAKING) "Stop" else "Talk", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
