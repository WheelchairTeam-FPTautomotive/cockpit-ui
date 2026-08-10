package com.wheelchair.cockpit.model

/**
 * AAOS-safe UI state machine for the voice copilot HUD.
 *
 * This is a UI-only projection of [AssistantState] plus transient confirmation
 * events. It lives in Compose and must not replace the backend-facing
 * [AssistantState] used by wake/STT/VHAL paths.
 */
sealed class CopilotUiState {
    data object Idle : CopilotUiState()
    data object Listening : CopilotUiState()
    data object Thinking : CopilotUiState()
    data object Speaking : CopilotUiState()
    data class ControlSuccess(val kind: ControlKind) : CopilotUiState()
    data object ControlFail : CopilotUiState()
    data object RagAnswer : CopilotUiState()
}

enum class ControlKind {
    DOOR,
    HVAC,
    MUSIC,
    RAG
}
