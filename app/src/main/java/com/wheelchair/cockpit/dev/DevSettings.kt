package com.wheelchair.cockpit.dev

import okhttp3.logging.HttpLoggingInterceptor

// --- START MODIFICATION ---
// Developer-mode preference model.
// Debug toggles (citations, mock RAG, bypass) require developerModeEnabled.
// Backend baseUrl always applies once set — turning developer mode off must not
// silently revert to DEFAULT_BASE_URL (was causing 10.0.2.2 mismatch).
data class DevSettings(
    val developerModeEnabled: Boolean = false,
    val baseUrl: String = DEFAULT_BASE_URL,
    val mockRagEnabled: Boolean = false,
    val bypassDrivingLock: Boolean = false,
    val httpLogLevel: HttpLogLevel = HttpLogLevel.BODY,
    // MODIFIED: citation cards gated separately under developer mode
    val showCitationCards: Boolean = true,
) {
    // MODIFIED: persist applied host for all requests (on or off developer mode)
    val effectiveBaseUrl: String
        get() = normalizeBaseUrl(baseUrl)

    val effectiveMockRag: Boolean
        get() = developerModeEnabled && mockRagEnabled

    val effectiveBypassDrivingLock: Boolean
        get() = developerModeEnabled && bypassDrivingLock

    val effectiveHttpLogLevel: HttpLogLevel
        get() = if (developerModeEnabled) httpLogLevel else HttpLogLevel.BODY

    val effectiveShowCitationCards: Boolean
        get() = developerModeEnabled && showCitationCards

    companion object {
        const val DEFAULT_BASE_URL = "http://52.64.18.95:8000/"

        fun normalizeBaseUrl(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return DEFAULT_BASE_URL
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }
}

enum class HttpLogLevel {
    NONE,
    BASIC,
    HEADERS,
    BODY;

    fun toOkHttpLevel(): HttpLoggingInterceptor.Level = when (this) {
        NONE -> HttpLoggingInterceptor.Level.NONE
        BASIC -> HttpLoggingInterceptor.Level.BASIC
        HEADERS -> HttpLoggingInterceptor.Level.HEADERS
        BODY -> HttpLoggingInterceptor.Level.BODY
    }

    companion object {
        fun fromName(name: String?): HttpLogLevel =
            entries.firstOrNull { it.name == name } ?: BODY
    }
}
// --- END MODIFICATION ---
