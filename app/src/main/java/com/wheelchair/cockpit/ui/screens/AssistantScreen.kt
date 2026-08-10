package com.wheelchair.cockpit.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AccessibleForward
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.AssistantState
import com.wheelchair.cockpit.ui.components.ChatMessage
import com.wheelchair.cockpit.ui.components.CitationCard
import com.wheelchair.cockpit.ui.components.ManualInputBar
import com.wheelchair.cockpit.ui.components.formatTimingLine
import com.wheelchair.cockpit.ui.components.parseMarkdownToAnnotatedString
import com.wheelchair.cockpit.ui.theme.CockpitTypography

@Composable
fun AssistantScreen(
    chatHistory: List<ChatMessage>,
    appLanguage: AppLanguage,
    textMain: Color,
    textSecondary: Color,
    primaryBlue: Color,
    surfaceColor: Color,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
    // MODIFIED: restore citation cards + LM Studio-style timing footer
    showCitationCards: Boolean = true,
    showLatency: Boolean = false,
    outlineVariant: Color = textSecondary.copy(alpha = 0.35f),
    // MODIFIED: restore text chat bar next to voice mic
    assistantState: AssistantState = AssistantState.IDLE,
    isDrivingRestricted: Boolean = false,
    onManualSend: (String) -> Unit = {},
) {
    val vi = appLanguage == AppLanguage.VIETNAMESE
    val listState = rememberLazyListState()
    var queryInput by remember { mutableStateOf("") }

    // Welcome message shown when no history yet
    val welcomeMessage = ChatMessage(
        text = if (vi)
            "Xin chào! Tôi là Wheelchair Copilot - trợ lý ảo trên xe của bạn. Hãy hỏi tôi bất cứ điều gì hoặc ra lệnh điều khiển xe."
        else
            "Hi! I'm Wheelchair Copilot - your virtual assistant for car. Ask me anything or give a voice command to control the car.",
        isUser = false
    )
    val displayMessages = if (chatHistory.isEmpty()) listOf(welcomeMessage) else chatHistory

    // Scroll to bottom whenever messages change
    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Message list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            items(displayMessages, key = { it.hashCode() }) { message ->
                ChatBubble(
                    message = message,
                    modifier = Modifier.animateContentSize(),
                    primaryBlue = primaryBlue,
                    surfaceColor = surfaceColor,
                    textMain = textMain,
                    textSecondary = textSecondary,
                    outlineVariant = outlineVariant,
                    showCitationCards = showCitationCards,
                    showLatency = showLatency,
                    vi = vi
                )
            }
        }

        // --- START MODIFICATION ---
        // Text field + send + mic (restored; overhaul left mic-only FAB)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ManualInputBar(
                queryInput = queryInput,
                assistantState = assistantState,
                appLanguage = appLanguage,
                primaryBlue = primaryBlue,
                surfaceContainer = surfaceColor,
                textMain = textMain,
                textSecondary = textSecondary,
                outlineVariant = outlineVariant,
                isDrivingRestricted = isDrivingRestricted,
                onQueryInputChange = { queryInput = it },
                onManualSend = { text ->
                    onManualSend(text)
                    queryInput = ""
                },
                modifier = Modifier.weight(1f)
            )
            FloatingActionButton(
                onClick = onMicTap,
                shape = CircleShape,
                containerColor = primaryBlue,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = if (vi) "Nói" else "Speak",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        // --- END MODIFICATION ---
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    primaryBlue: Color,
    surfaceColor: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    showCitationCards: Boolean,
    showLatency: Boolean,
    vi: Boolean
) {
    val isUser = message.isUser

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(primaryBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.AccessibleForward,
                    contentDescription = "Copilot",
                    tint = primaryBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Text(
                text = if (isUser) { if (vi) "Bạn" else "You" } else "Copilot",
                style = CockpitTypography.caption.copy(fontSize = 10.sp),
                color = textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) primaryBlue.copy(alpha = 0.25f) else surfaceColor.copy(alpha = 0.7f),
                tonalElevation = if (isUser) 0.dp else 2.dp,
                shadowElevation = if (isUser) 0.dp else 4.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (isUser) {
                        Text(
                            text = message.text,
                            style = CockpitTypography.body,
                            color = textMain
                        )
                    } else {
                        Text(
                            text = parseMarkdownToAnnotatedString(message.text, primaryBlue),
                            style = CockpitTypography.body,
                            color = textMain
                        )
                        // --- START MODIFICATION ---
                        if (showCitationCards && message.citations.isNotEmpty()) {
                            Spacer(modifier = Modifier.size(8.dp))
                            message.citations.take(3).forEach { citation ->
                                CitationCard(
                                    citation = citation,
                                    primaryColor = primaryBlue,
                                    textColor = textMain,
                                    surfaceColor = surfaceColor,
                                    borderColor = outlineVariant
                                )
                            }
                        }
                        if (showLatency && message.timing != null) {
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = formatTimingLine(message.timing),
                                fontSize = 10.sp,
                                color = textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // --- END MODIFICATION ---
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(primaryBlue.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "U",
                    style = CockpitTypography.caption.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = Color.White
                )
            }
        }
    }
}
