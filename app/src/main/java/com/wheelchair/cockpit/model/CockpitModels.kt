package com.wheelchair.cockpit.model

enum class AssistantState {
    IDLE,           // STANDBY: Only keyword spotting, no backend streaming
    WAKE_DETECTED,  // ACTIVATED: Awake, waiting for user command
    PROCESSING,     // Querying AI RAG / Gemini API
    SPEAKING        // TTS speaking response
}

enum class AppLanguage {
    VIETNAMESE,
    ENGLISH
}

enum class DisplayTheme {
    LIGHT,
    DARK,
    CENTRAL
}
