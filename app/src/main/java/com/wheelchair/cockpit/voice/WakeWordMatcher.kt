package com.wheelchair.cockpit.voice

import java.util.Locale

/**
 * Shared Hey Car / Xe ơi matching used by Activity fallback and FGS wake path.
 */
// --- START MODIFICATION ---
object WakeWordMatcher {
    fun containsWakeWord(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.contains("hey car") ||
            lower.contains("hay car") ||
            lower.contains("hây ca") ||
            lower.contains("hây car") ||
            lower.contains("he ca") ||
            lower.contains("hey call") ||
            lower.contains("hay call") ||
            lower.contains("he call") ||
            lower.contains("take care") ||
            lower.contains("xe ơi") ||
            lower.contains("chào xe") ||
            lower.matches(Regex(".*\\bhey\\b.*")) ||
            lower.matches(Regex(".*\\bhi\\b.*")) ||
            lower.matches(Regex(".*\\bhello\\b.*")) ||
            lower.matches(Regex(".*\\bokay\\b.*")) ||
            lower.matches(Regex(".*\\bok\\b.*"))
    }
}
// --- END MODIFICATION ---
