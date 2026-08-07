package com.wheelchair.cockpit.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Wake-word capture path for AAOS emulators.
 *
 * Stock Vosk [org.vosk.android.SpeechService] hardcodes VOICE_RECOGNITION, which often
 * opens a silent input on Car AVDs even when host-mic is enabled. This recorder prefers
 * [MediaRecorder.AudioSource.MIC], pins a built-in/virtual mic when possible, and reports
 * buffer RMS so the HUD waveform can prove audio is arriving during standby.
 *
 * Does NOT own [Recognizer] lifecycle — [WakeWordEngine] is the sole caller of [Recognizer.close].
 */
// --- START MODIFICATION ---
// Refactored: no Recognizer.close(); AudioRecord.stop() before join to unblock read()
class VoskWakeRecorder(
    private val recognizer: Recognizer,
    private val sampleRate: Int = 16_000,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {
    interface RmsListener {
        fun onRms(normalized: Float)
    }

    private val bufferSize = (sampleRate * BUFFER_SIZE_SECONDS).toInt().coerceAtLeast(320)
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    fun start(context: Context, listener: RecognitionListener, rmsListener: RmsListener? = null) {
        if (running.get()) return
        stop()

        val audioRecord = buildAudioRecord(context)
            ?: throw IOException("Failed to initialize AudioRecord for Vosk wake capture.")
        recorder = audioRecord
        running.set(true)
        paused.set(false)

        worker = Thread({
            try {
                audioRecord.startRecording()
                if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    mainHandler.post {
                        listener.onError(
                            IOException("Failed to start recording. Microphone might be already in use.")
                        )
                    }
                    return@Thread
                }

                val buffer = ShortArray(bufferSize)
                while (running.get() && !Thread.currentThread().isInterrupted) {
                    val nread = audioRecord.read(buffer, 0, buffer.size)
                    if (!running.get()) break
                    if (nread <= 0) continue
                    if (paused.get()) continue

                    rmsListener?.let {
                        val level = rmsNormalized(buffer, nread)
                        mainHandler.post { it.onRms(level) }
                    }

                    if (!running.get()) break
                    if (recognizer.acceptWaveForm(buffer, nread)) {
                        if (!running.get()) break
                        val result = recognizer.getResult()
                        mainHandler.post { listener.onResult(result) }
                    } else {
                        if (!running.get()) break
                        val partial = recognizer.partialResult
                        mainHandler.post { listener.onPartialResult(partial) }
                    }
                }
            } catch (t: Throwable) {
                if (running.get()) {
                    Log.e(TAG, "Vosk wake capture failed", t)
                    mainHandler.post { listener.onError(Exception(t)) }
                }
            } finally {
                try {
                    if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop()
                    }
                } catch (_: Exception) {
                }
            }
        }, "vosk-wake-recorder").also { it.start() }
    }

    fun setPaused(value: Boolean) {
        paused.set(value)
    }

    /**
     * Stops the worker and releases AudioRecord only.
     * Order: running=false → AudioRecord.stop() (unblocks read) → join → release.
     */
    fun stop() {
        running.set(false)
        paused.set(false)

        val audioRecord = recorder
        // Unblock any blocking AudioRecord.read() before joining the worker.
        if (audioRecord != null) {
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
            } catch (_: Exception) {
            }
        }

        worker?.interrupt()
        try {
            worker?.join(JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        worker = null

        if (audioRecord != null) {
            try {
                audioRecord.release()
            } catch (_: Exception) {
            }
        }
        recorder = null
    }

    @SuppressLint("MissingPermission")
    private fun buildAudioRecord(context: Context): AudioRecord? {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.DEFAULT
        )
        for (source in sources) {
            val candidate = try {
                AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 2
                )
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord create failed for source=$source", e)
                null
            } ?: continue

            if (candidate.state != AudioRecord.STATE_INITIALIZED) {
                candidate.release()
                continue
            }

            preferBuiltinOrVirtualMic(context, candidate)
            Log.i(TAG, "Vosk wake AudioRecord ready source=$source session=${candidate.audioSessionId}")
            return candidate
        }
        return null
    }

    private fun preferBuiltinOrVirtualMic(context: Context, record: AudioRecord) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val preferred = am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
                device.type == AudioDeviceInfo.TYPE_TELEPHONY ||
                device.type == AudioDeviceInfo.TYPE_UNKNOWN
        } ?: return
        val ok = record.setPreferredDevice(preferred)
        Log.i(TAG, "Preferred input device type=${preferred.type} ok=$ok")
    }

    private fun rmsNormalized(buffer: ShortArray, nread: Int): Float {
        var sumSquares = 0.0
        var peak = 0
        for (i in 0 until nread) {
            val v = buffer[i].toInt()
            val a = abs(v)
            if (a > peak) peak = a
            sumSquares += (v * v).toDouble()
        }
        val rms = sqrt(sumSquares / nread.coerceAtLeast(1))
        // Map typical speech RMS into 0..1 for the Compose waveform.
        return (rms / 4000.0).toFloat().coerceIn(0f, 1f).let { level ->
            if (peak < 40) 0f else level
        }
    }

    companion object {
        private const val TAG = "CockpitUI"
        private const val BUFFER_SIZE_SECONDS = 0.2f
        private const val JOIN_TIMEOUT_MS = 1500L
    }
}
// --- END MODIFICATION ---
