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

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (copilotAnswer.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = parseMarkdownToAnnotatedString(copilotAnswer, textMain),
                                fontSize = 15.sp,
                                color = textMain,
                                lineHeight = 21.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        // --- START MODIFICATION ---
                        // Bibliography / evidence cards: developer mode only
                        if (showEvidence) {
                            items(citations) { citation ->
                                CitationCard(citation, primaryBlue, textMain, surfaceContainer, outlineVariant)
                            }
                        }
                        // --- END MODIFICATION ---
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StitchVoiceWaveform(
                            rmsLevel = rmsLevel,
                            primaryColor = primaryBlue,
                            modifier = Modifier.fillMaxWidth(0.6f).height(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // --- START MODIFICATION ---
                        val fallback = when (assistantState) {
                            AssistantState.PROCESSING -> if (vi) "Đang suy nghĩ..." else "Analyzing..."
                            else -> if (vi) "Đang lắng nghe..." else "Listening..."
                        }
                        val caption = partialTranscript.ifBlank { fallback }
                        val isPartial = partialTranscript.isNotBlank()
                        Text(
                            text = caption,
                            fontSize = if (isPartial) 15.sp else 14.sp,
                            fontStyle = if (isPartial) FontStyle.Italic else FontStyle.Normal,
                            fontWeight = if (isPartial) FontWeight.Medium else FontWeight.Normal,
                            color = if (isPartial) {
                                textSecondary.copy(alpha = if (isDrivingRestricted) 0.45f else 0.75f)
                            } else {
                                textSecondary
                            },
                            textAlign = TextAlign.Center,
                            maxLines = if (isDrivingRestricted) 1 else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        if (micDiagLabel.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = micDiagLabel,
                                fontSize = 11.sp,
                                color = textSecondary.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                        // --- END MODIFICATION ---
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
