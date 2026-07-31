package com.wheelchair.cockpit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelchair.cockpit.model.AppLanguage
import com.wheelchair.cockpit.model.AssistantState

@Composable
fun ManualInputBar(
    queryInput: String,
    assistantState: AssistantState,
    appLanguage: AppLanguage,
    primaryBlue: Color,
    surfaceContainer: Color,
    textMain: Color,
    textSecondary: Color,
    outlineVariant: Color,
    isDrivingRestricted: Boolean = false,
    onQueryInputChange: (String) -> Unit,
    onManualSend: (String) -> Unit
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Surface(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isDrivingRestricted) surfaceContainer.copy(alpha = 0.5f) else surfaceContainer,
        border = BorderStroke(1.dp, if (isDrivingRestricted) Color(0xFFEF4444).copy(alpha = 0.5f) else outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDrivingRestricted) {
                Row(
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.VIETNAMESE) 
                            "VOICE MODE ONLY (Đang lái xe)" else "VOICE MODE ONLY (Driving)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                }
            } else {
                TextField(
                    value = queryInput,
                    onValueChange = onQueryInputChange,
                    enabled = true,
                    placeholder = {
                        Text(
                            if (appLanguage == AppLanguage.VIETNAMESE) "Nhập câu hỏi..." else "Ask question...",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedTextColor = textMain,
                        unfocusedTextColor = textMain,
                        disabledTextColor = textSecondary,
                        focusedPlaceholderColor = textSecondary,
                        unfocusedPlaceholderColor = textSecondary,
                        disabledPlaceholderColor = textSecondary
                    ),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = {
                            if (!isDrivingRestricted && queryInput.isNotBlank() && assistantState != AssistantState.PROCESSING) {
                                focusManager.clearFocus()
                                onManualSend(queryInput)
                                onQueryInputChange("")
                            }
                        }
                    )
                )
            }
            
            IconButton(
                onClick = { 
                    if (!isDrivingRestricted && queryInput.isNotBlank() && assistantState != AssistantState.PROCESSING) { 
                        focusManager.clearFocus()
                        onManualSend(queryInput)
                        onQueryInputChange("") 
                    } 
                },
                enabled = !isDrivingRestricted && queryInput.isNotBlank() && assistantState != AssistantState.PROCESSING,
                modifier = Modifier
                    .size(36.dp)
                    .background(if (isDrivingRestricted) Color.Gray.copy(alpha = 0.3f) else primaryBlue, RoundedCornerShape(10.dp))
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
