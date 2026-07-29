package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelchair.cockpit.api.CitationInfo
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.AssistantState

@Composable
fun CopilotResponsePanel(
    copilotAnswer: String,
    citations: List<CitationInfo>,
    assistantState: AssistantState,
    rmsLevel: Float,
    appLanguage: AppLanguage,
    primaryBlue: Color,
    surfaceContainer: Color,
    surfaceContainerLow: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(16.dp),
        color = surfaceContainerLow,
        border = BorderStroke(1.dp, outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SmartToy, null, tint = primaryBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.VIETNAMESE) "TRẢ LỜI TỪ AI COPILOT" else "COPILOT RESPONSE",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryBlue, letterSpacing = 1.sp
                    )
                }
                
                // Sleek Voice Waveform Badge Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = primaryBlue.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, primaryBlue.copy(alpha = 0.25f))
                ) {
                    StitchVoiceWaveform(
                        rmsLevel = rmsLevel,
                        primaryColor = primaryBlue,
                        modifier = Modifier
                            .width(70.dp)
                            .height(20.dp)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (copilotAnswer.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(text = copilotAnswer, fontSize = 15.sp, color = textMain, lineHeight = 21.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        items(citations) { citation ->
                            CitationCard(citation, primaryBlue, textMain, surfaceContainer, outlineVariant)
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StitchVoiceWaveform(
                            rmsLevel = rmsLevel,
                            primaryColor = primaryBlue,
                            modifier = Modifier.fillMaxWidth(0.6f).height(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (assistantState == AssistantState.PROCESSING) {
                                if (appLanguage == AppLanguage.VIETNAMESE) "Đang suy nghĩ..." else "Analyzing..."
                            } else {
                                if (appLanguage == AppLanguage.VIETNAMESE) "Đang lắng nghe..." else "Listening..."
                            },
                            fontSize = 14.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
