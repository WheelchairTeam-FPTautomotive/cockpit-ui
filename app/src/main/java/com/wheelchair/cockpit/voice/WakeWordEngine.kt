package com.wheelchair.cockpit.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Standby wake-word capture: prefers [VoskWakeRecorder] (MIC), falls back to stock [SpeechService].
 *
 * Sole owner of [Recognizer.close] for the VoskWakeRecorder path. After [SpeechService.shutdown],
 * the recognizer is treated as already freed (no second close).
 */
// --- START MODIFICATION ---
// Refactored: single Recognizer ownership; join/close outside lock; idempotent start/resume
class WakeWordEngine(
    private val appContext: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {
    interface Callbacks {
        fun onWakeDetected()
        fun onRms(normalized: Float) {}
        fun onError(message: String) {}
    }

    private val lock = Any()
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var recorder: VoskWakeRecorder? = null
    private var speechService: SpeechService? = null
    /** True when SpeechService owns/frees the current recognizer. */
    private var speechServiceOwnsRecognizer = false
    private var callbacks: Callbacks? = null
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private var modelReady = false

    fun prepareModel(onReady: (Boolean) -> Unit) {
        if (modelReady && model != null) {
            onReady(true)
            return
        }
        StorageService.unpack(
            appContext,
            "model-en",
            "model",
            { unpacked ->
                synchronized(lock) {
                    model = unpacked
                    modelReady = true
                }
                onReady(true)
            },
            { e ->
                Log.e(TAG, "Failed to unpack Vosk model", e)
                onReady(false)
            }
        )
    }

    fun start(callbacks: Callbacks) {
        val m: Model
        synchronized(lock) {
            this.callbacks = callbacks
            m = model ?: run {
                callbacks.onError("Vosk model not ready")
                return
            }
            if (running.get() && !paused.get()) {
                // Already listening — refresh callbacks only; avoid start→resume→start recursion.
                return
            }
            paused.set(false)
        }

        stopCapture(releaseRecognizer = true)

        val listener = object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                if (paused.get()) return
                if (hypothesis != null && WakeWordMatcher.containsWakeWord(hypothesis)) {
                    Log.d(TAG, "Wake partial: $hypothesis")
                    callbacks.onWakeDetected()
                }
            }

            override fun onResult(hypothesis: String?) {
                if (paused.get()) return
                if (hypothesis != null && WakeWordMatcher.containsWakeWord(hypothesis)) {
                    Log.d(TAG, "Wake result: $hypothesis")
                    callbacks.onWakeDetected()
                }
            }

            override fun onFinalResult(hypothesis: String?) {}
            override fun onError(e: Exception?) {
                Log.e(TAG, "Wake engine error", e)
                callbacks.onError(e?.message ?: "wake error")
            }

            override fun onTimeout() {}
        }

        val rmsListener = object : VoskWakeRecorder.RmsListener {
            override fun onRms(normalized: Float) {
                if (!paused.get()) callbacks.onRms(normalized)
            }
        }

        try {
            val rec = Recognizer(m, SAMPLE_RATE.toFloat())
            val wakeRecorder = VoskWakeRecorder(rec, SAMPLE_RATE, mainHandler)
            wakeRecorder.start(appContext, listener, rmsListener)
            synchronized(lock) {
                recognizer = rec
                recorder = wakeRecorder
                speechService = null
                speechServiceOwnsRecognizer = false
                running.set(true)
                paused.set(false)
            }
            Log.i(TAG, "Wake engine started via VoskWakeRecorder")
        } catch (e: Exception) {
            Log.w(TAG, "VoskWakeRecorder failed; falling back to SpeechService", e)
            try {
                val rec = Recognizer(m, SAMPLE_RATE.toFloat())
                val service = SpeechService(rec, SAMPLE_RATE.toFloat())
                service.startListening(listener)
                synchronized(lock) {
                    recognizer = rec
                    recorder = null
                    speechService = service
                    speechServiceOwnsRecognizer = true
                    running.set(true)
                    paused.set(false)
                }
                Log.i(TAG, "Wake engine started via SpeechService fallback")
            } catch (fallback: Exception) {
                Log.e(TAG, "Failed to start wake engine", fallback)
                callbacks.onError(fallback.message ?: "start failed")
                running.set(false)
            }
        }
    }

    fun pause() {
        paused.set(true)
        // Release the mic so post-wake STT / TTS can open AudioRecord.
        // Keep Recognizer alive only if we will reuse it; current design restarts capture on resume,
        // so release the native handle fully to avoid stale state.
        stopCapture(releaseRecognizer = true)
        running.set(false)
    }

    fun resume() {
        val cb: Callbacks
        synchronized(lock) {
            paused.set(false)
            cb = callbacks ?: return
            if (model == null) return
            if (running.get()) return
        }
        start(cb)
    }

    fun stop() {
        paused.set(true)
        stopCapture(releaseRecognizer = true)
        running.set(false)
    }

    fun shutdown() {
        stopCapture(releaseRecognizer = true)
        running.set(false)
        synchronized(lock) {
            callbacks = null
            try {
                model?.close()
            } catch (_: Exception) {
            }
            model = null
            modelReady = false
        }
    }

    /**
     * Tear down capture safely:
     * under lock — snapshot refs and clear fields;
     * outside lock — stop audio / join / close Recognizer once (engine-owned path only).
     */
    private fun stopCapture(releaseRecognizer: Boolean) {
        val recToStop: VoskWakeRecorder?
        val serviceToStop: SpeechService?
        val recognizerToClose: Recognizer?
        val speechOwned: Boolean

        synchronized(lock) {
            recToStop = recorder
            serviceToStop = speechService
            speechOwned = speechServiceOwnsRecognizer
            recognizerToClose = if (releaseRecognizer && !speechServiceOwnsRecognizer) {
                recognizer
            } else {
                null
            }
            recorder = null
            speechService = null
            if (releaseRecognizer) {
                recognizer = null
                speechServiceOwnsRecognizer = false
            }
        }

        // Outside lock: join / native free must not hold WakeWordEngine monitor.
        try {
            recToStop?.stop()
        } catch (_: Exception) {
        }

        if (serviceToStop != null) {
            try {
                serviceToStop.cancel()
            } catch (_: Exception) {
            }
            try {
                serviceToStop.shutdown()
            } catch (_: Exception) {
            }
            // SpeechService.shutdown() already frees its Recognizer — do not close again.
        }

        if (recognizerToClose != null && !speechOwned) {
            try {
                recognizerToClose.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val TAG = "CockpitUI"
        private const val SAMPLE_RATE = 16_000
    }
}
// --- END MODIFICATION ---
