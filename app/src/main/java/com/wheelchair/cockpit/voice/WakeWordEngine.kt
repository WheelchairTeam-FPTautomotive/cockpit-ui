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
 */
// --- START MODIFICATION ---
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
        synchronized(lock) {
            this.callbacks = callbacks
            val m = model ?: run {
                callbacks.onError("Vosk model not ready")
                return
            }
            if (running.get()) {
                resume()
                return
            }
            paused.set(false)
            stopCaptureLocked(releaseRecognizer = true)

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
                recognizer = rec
                val wakeRecorder = VoskWakeRecorder(rec, SAMPLE_RATE, mainHandler)
                wakeRecorder.start(appContext, listener, rmsListener)
                recorder = wakeRecorder
                running.set(true)
                Log.i(TAG, "Wake engine started via VoskWakeRecorder")
            } catch (e: Exception) {
                Log.w(TAG, "VoskWakeRecorder failed; falling back to SpeechService", e)
                try {
                    val rec = Recognizer(m, SAMPLE_RATE.toFloat())
                    recognizer = rec
                    val service = SpeechService(rec, SAMPLE_RATE.toFloat())
                    service.startListening(listener)
                    speechService = service
                    running.set(true)
                    Log.i(TAG, "Wake engine started via SpeechService fallback")
                } catch (fallback: Exception) {
                    Log.e(TAG, "Failed to start wake engine", fallback)
                    callbacks.onError(fallback.message ?: "start failed")
                    running.set(false)
                }
            }
        }
    }

    fun pause() {
        paused.set(true)
        synchronized(lock) {
            // Release the mic so post-wake STT / TTS can open AudioRecord.
            try {
                recorder?.stop()
            } catch (_: Exception) {
            }
            recorder = null
            speechService?.let {
                try {
                    it.cancel()
                } catch (_: Exception) {
                }
            }
            running.set(false)
        }
    }

    fun resume() {
        synchronized(lock) {
            paused.set(false)
            val cb = callbacks ?: return
            if (model == null) return
            // Restart capture after pause released the mic.
            running.set(false)
            start(cb)
        }
    }

    fun stop() {
        synchronized(lock) {
            paused.set(true)
            stopCaptureLocked(releaseRecognizer = true)
            running.set(false)
        }
    }

    fun shutdown() {
        synchronized(lock) {
            stopCaptureLocked(releaseRecognizer = true)
            running.set(false)
            callbacks = null
            try {
                model?.close()
            } catch (_: Exception) {
            }
            model = null
            modelReady = false
        }
    }

    private fun stopCaptureLocked(releaseRecognizer: Boolean) {
        try {
            recorder?.shutdown()
        } catch (_: Exception) {
        }
        recorder = null
        try {
            speechService?.cancel()
            speechService?.shutdown()
        } catch (_: Exception) {
        }
        speechService = null
        if (releaseRecognizer) {
            try {
                recognizer?.close()
            } catch (_: Exception) {
            }
            recognizer = null
        }
    }

    companion object {
        private const val TAG = "CockpitUI"
        private const val SAMPLE_RATE = 16_000
    }
}
// --- END MODIFICATION ---
