package com.wheelchair.cockpit.voice

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

// --- START MODIFICATION ---
/**
 * Throttles SpeechRecognizer partial hypotheses before Compose state updates.
 * Distinct-until-changed + min interval to avoid recomposition storms.
 */
class PartialTranscriptPublisher(
    private val minIntervalMs: Long = 200L,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val onPublish: (String) -> Unit
) {
    private var lastPublished: String = ""
    private var lastPublishAtMs: Long = 0L
    private var pendingText: String? = null
    private var pendingRunnable: Runnable? = null

    fun offer(raw: String?) {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == lastPublished) return

        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastPublishAtMs
        if (elapsed >= minIntervalMs) {
            publishNow(text)
            return
        }

        pendingText = text
        if (pendingRunnable == null) {
            val delay = (minIntervalMs - elapsed).coerceAtLeast(1L)
            val runnable = Runnable {
                pendingRunnable = null
                val pending = pendingText
                pendingText = null
                if (!pending.isNullOrEmpty() && pending != lastPublished) {
                    publishNow(pending)
                }
            }
            pendingRunnable = runnable
            mainHandler.postDelayed(runnable, delay)
        }
    }

    fun clear() {
        pendingRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRunnable = null
        pendingText = null
        lastPublished = ""
        lastPublishAtMs = 0L
        onPublish("")
    }

    private fun publishNow(text: String) {
        lastPublished = text
        lastPublishAtMs = SystemClock.elapsedRealtime()
        onPublish(text)
    }
}
// --- END MODIFICATION ---
