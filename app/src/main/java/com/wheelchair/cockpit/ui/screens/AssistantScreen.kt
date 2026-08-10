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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.AssistantState
import com.wheelchair.cockpit.ui.components.ChatMessage
import com.wheelchair.cockpit.ui.theme.CockpitTypography

@Composable
fun AssistantScreen(
    chatHistory: List<ChatMessage>,
    assistantState: AssistantState,
    appLanguage: AppLanguage,
    textMain: Color,
    textSecondary: Color,
    primaryBlue: Color,
    surfaceColor: Color,
    onMicTap: () -> Unit,
    onManualSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val vi = appLanguage == AppLanguage.VIETNAMESE
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

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
                    vi = vi
                )
            }
        }

        // Bottom Input Row
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            color = surfaceColor,
            shape = RoundedCornerShape(32.dp),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { 
                        Text(
                            text = if (vi) "Nhập lệnh của bạn..." else "Type your command...",
                            color = textSecondary,
                            style = CockpitTypography.body
                        ) 
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = textMain,
                        unfocusedTextColor = textMain
                    ),
                    textStyle = CockpitTypography.body,
                    singleLine = true
                )
                
                if (inputText.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onManualSend(inputText)
                            inputText = ""
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = if (vi) "Gửi" else "Send",
                            tint = primaryBlue
                        )
                    }
                }
                
                val isSpeakingOrThinking = assistantState == AssistantState.SPEAKING || assistantState == AssistantState.PROCESSING
                val buttonColor = if (isSpeakingOrThinking) Color(0xFFEF4444) else primaryBlue
                val buttonIcon = if (isSpeakingOrThinking) Icons.Rounded.Stop else Icons.Rounded.Mic
                val buttonDesc = if (isSpeakingOrThinking) (if (vi) "Dừng" else "Stop") else (if (vi) "Nói" else "Speak")

                FloatingActionButton(
                    onClick = onMicTap,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    containerColor = buttonColor,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        imageVector = buttonIcon,
                        contentDescription = buttonDesc,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
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
    vi: Boolean
) {
    val isUser = message.isUser

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Avatar for assistant messages
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
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 480.dp)
        ) {
            // Sender label
            Text(
                text = if (isUser) { if (vi) "Bạn" else "You" } else "Copilot",
                style = CockpitTypography.caption.copy(fontSize = 10.sp),
                color = textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            // Bubble
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
                Text(
                    text = message.text,
                    style = CockpitTypography.body,
                    color = textMain,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        // Spacer after user bubble (right side)
        if (isUser) {
            Spacer(modifier = Modifier.size(8.dp))
            // User avatar
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
