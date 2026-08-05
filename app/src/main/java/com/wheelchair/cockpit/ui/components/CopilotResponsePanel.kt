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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelchair.cockpit.api.CitationInfo
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.AssistantState

data class ChatMessage(
    val isUser: Boolean,
    val text: String,
    val citations: List<CitationInfo> = emptyList()
)

@Composable
fun CopilotResponsePanel(
    chatHistory: List<ChatMessage>,
    assistantState: AssistantState,
    rmsLevel: Float,
    appLanguage: AppLanguage,
    primaryBlue: Color,
    surfaceContainer: Color,
    surfaceContainerLow: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    modifier: Modifier = Modifier,
    // --- START MODIFICATION ---
    partialTranscript: String = "",
    micDiagLabel: String = "",
    isDrivingRestricted: Boolean = false,
    showLatency: Boolean = false,
    showEvidence: Boolean = false,
    lastQueryLatencyMs: Long? = null,
    lastHealthLatencyMs: Long? = null
    // --- END MODIFICATION ---
) {
    val vi = appLanguage == AppLanguage.VIETNAMESE

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
                        text = if (vi) "TRẢ LỜI TỪ AI COPILOT" else "COPILOT RESPONSE",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryBlue, letterSpacing = 1.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // --- START MODIFICATION ---
                    if (showLatency && (lastQueryLatencyMs != null || lastHealthLatencyMs != null)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = primaryBlue.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, primaryBlue.copy(alpha = 0.22f))
                        ) {
                            Text(
                                text = buildString {
                                    lastQueryLatencyMs?.let { append("Query ${it}ms") }
                                    if (lastQueryLatencyMs != null && lastHealthLatencyMs != null) append(" · ")
                                    lastHealthLatencyMs?.let { append("Health ${it}ms") }
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryBlue
                            )
                        }
                    }
                    // --- END MODIFICATION ---

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
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (chatHistory.isNotEmpty() || partialTranscript.isNotEmpty() || assistantState != AssistantState.IDLE) {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    androidx.compose.runtime.LaunchedEffect(chatHistory.size, partialTranscript, assistantState) {
                        listState.animateScrollToItem(kotlin.math.max(0, chatHistory.size * 2))
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(chatHistory) { msg ->
                            if (msg.isUser) {
                                // User Bubble
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Surface(
                                        color = primaryBlue,
                                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp),
                                        modifier = Modifier.widthIn(max = 280.dp)
                                    ) {
                                        Text(
                                            text = msg.text,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            } else {
                                // AI Bubble
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                    Surface(
                                        color = surfaceContainer,
                                        border = BorderStroke(1.dp, outlineVariant),
                                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp),
                                        modifier = Modifier.widthIn(max = 320.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = parseMarkdownToAnnotatedString(msg.text, primaryBlue),
                                                color = textMain,
                                                fontSize = 15.sp,
                                                lineHeight = 21.sp
                                            )
                                            if (showEvidence && msg.citations.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                msg.citations.forEach { citation ->
                                                    CitationCard(citation, primaryBlue, textMain, surfaceContainerLow, outlineVariant)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Active listening/processing state
                        if (assistantState != AssistantState.IDLE) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                    Surface(
                                        color = surfaceContainer,
                                        border = BorderStroke(1.dp, outlineVariant),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.widthIn(max = 320.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            StitchVoiceWaveform(
                                                rmsLevel = rmsLevel,
                                                primaryColor = primaryBlue,
                                                modifier = Modifier.fillMaxWidth().height(40.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            val fallback = when (assistantState) {
                                                AssistantState.PROCESSING -> if (vi) "Đang suy nghĩ..." else "Analyzing..."
                                                else -> if (vi) "Đang lắng nghe..." else "Listening..."
                                            }
                                            val caption = partialTranscript.ifBlank { fallback }
                                            Text(
                                                text = caption,
                                                fontSize = 14.sp,
                                                fontStyle = FontStyle.Italic,
                                                color = textSecondary.copy(alpha = 0.8f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Empty state
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (vi) "Hãy nói 'Hey Car' để bắt đầu" else "Say 'Hey Car' to start",
                            color = textSecondary.copy(alpha = 0.5f),
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

fun parseMarkdownToAnnotatedString(text: String, mainColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        val cleanedText = text.replace(Regex("^\\s*\\* ", RegexOption.MULTILINE), "• ")
        boldRegex.findAll(cleanedText).forEach { match ->
            append(cleanedText.substring(currentIndex, match.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = mainColor)) {
                append(match.groupValues[1])
            }
            currentIndex = match.range.last + 1
        }
        if (currentIndex < cleanedText.length) {
            append(cleanedText.substring(currentIndex))
        }
    }
}
