package com.wheelchair.cockpit.dev

import okhttp3.logging.HttpLoggingInterceptor

// --- START MODIFICATION ---
// Developer-mode preference model. Overrides apply only when developerModeEnabled is true.
data class DevSettings(
    val developerModeEnabled: Boolean = false,
    val baseUrl: String = DEFAULT_BASE_URL,
    val mockRagEnabled: Boolean = false,
    val bypassDrivingLock: Boolean = false,
    val httpLogLevel: HttpLogLevel = HttpLogLevel.BODY
) {
    val effectiveBaseUrl: String
        get() = if (developerModeEnabled) normalizeBaseUrl(baseUrl) else DEFAULT_BASE_URL

    val effectiveMockRag: Boolean
        get() = developerModeEnabled && mockRagEnabled

    val effectiveBypassDrivingLock: Boolean
        get() = developerModeEnabled && bypassDrivingLock

    val effectiveHttpLogLevel: HttpLogLevel
        get() = if (developerModeEnabled) httpLogLevel else HttpLogLevel.BODY

    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

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
