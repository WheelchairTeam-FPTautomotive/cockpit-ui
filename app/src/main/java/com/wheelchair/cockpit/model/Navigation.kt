package com.wheelchair.cockpit.model

/**
 * Primary navigation destinations shown in the side rail.
 */
enum class Screen {
    DASHBOARD,
    ASSISTANT,
    MEDIA,
    MAP,
    SETTINGS
}

/**
 * Sub-tabs inside the Dashboard screen.
 */
enum class DashboardTab {
    CONTROL,
    CLIMATE,
    NAVIGATION,
    AUDIO
}
