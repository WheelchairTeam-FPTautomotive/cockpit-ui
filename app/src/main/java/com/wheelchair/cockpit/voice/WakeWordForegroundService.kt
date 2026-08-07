package com.wheelchair.cockpit.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wheelchair.cockpit.MainActivity

/**
 * Microphone FGS for standby "Hey Car" while the Activity may be backgrounded.
 * Foreground UI: SINGLE_TOP wake intent, no trigger heads-up.
 * Background: BAL-safe FSI notification; SAW startActivity when granted.
 */
// --- START MODIFICATION ---
class WakeWordForegroundService : Service() {

    private var engine: WakeWordEngine? = null
    private var wakeArmed = true
    /** Single-flight guard: model-ready + ACTION_RESUME must not overlap engine.start(). */
    @Volatile
    private var startInFlight = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        engine = WakeWordEngine(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                startInFlight = false
                engine?.pause()
                wakeArmed = false
                return START_STICKY
            }
            ACTION_RESUME -> {
                wakeArmed = true
                ensureListening()
                return START_STICKY
            }
            ACTION_STOP -> {
                startInFlight = false
                stopSelfSafe()
                return START_NOT_STICKY
            }
            else -> {
                promoteForeground()
                isRunning = true
                engine?.prepareModel { ok ->
                    if (!ok) {
                        Log.e(TAG, "Wake FGS model unpack failed")
                        startFailed = true
                        stopSelfSafe()
                        return@prepareModel
                    }
                    startFailed = false
                    ensureListening()
                }
            }
        }
        return START_STICKY
    }

    // MODIFIED: Idempotent / single-flight ensureListening to prevent overlapping start()
    private fun ensureListening() {
        if (!wakeArmed) {
            startInFlight = false
            engine?.pause()
            return
        }
        val eng = engine ?: return
        if (startInFlight) {
            Log.d(TAG, "ensureListening skipped — start already in flight")
            return
        }
        startInFlight = true
        try {
            eng.start(object : WakeWordEngine.Callbacks {
                override fun onWakeDetected() {
                    if (!wakeArmed) return
                    wakeArmed = false
                    startInFlight = false
                    eng.pause()
                    bringActivityForward()
                }

                override fun onError(message: String) {
                    startInFlight = false
                    Log.e(TAG, "Wake FGS engine: $message")
                }
            })
            // start() is synchronous for capture setup; clear in-flight unless we paused mid-call.
            if (wakeArmed) {
                startInFlight = false
            }
        } catch (t: Throwable) {
            startInFlight = false
            throw t
        }
    }

    private fun bringActivityForward() {
        // --- START MODIFICATION ---
        // Already in Copilot UI: no "Tap to open" heads-up; deliver EXTRA_WAKE via SINGLE_TOP.
        if (MainActivity.isUiForeground) {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIF_TRIGGER_ID)
            try {
                startActivity(
                    wakeActivityIntent().addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                )
                Log.i(TAG, "Wake foreground short-circuit (no FSI notification)")
            } catch (e: Exception) {
                Log.w(TAG, "Foreground wake startActivity failed; falling back to FSI", e)
                postWakeFullScreenNotification()
            }
            return
        }

        // Background: high-importance FSI notification (BAL-privileged for FGS).
        postWakeFullScreenNotification()

        // Emulator fast path: SYSTEM_ALERT_WINDOW is a BAL exemption.
        if (Settings.canDrawOverlays(this)) {
            try {
                startActivity(
                    wakeActivityIntent().addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                )
                Log.i(TAG, "Wake bring-to-front via SAW startActivity")
            } catch (e: Exception) {
                Log.w(TAG, "SAW startActivity blocked; relying on FSI notification", e)
            }
        } else {
            Log.i(TAG, "SAW not granted; relying on full-screen intent notification")
        }
        // --- END MODIFICATION ---
    }

    private fun wakeActivityIntent(): Intent {
        return Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra(EXTRA_WAKE_DETECTED, true)
        }
    }

    private fun postWakeFullScreenNotification() {
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPi = PendingIntent.getActivity(this, REQ_WAKE_CONTENT, wakeActivityIntent(), piFlags)
        val fullScreenPi = PendingIntent.getActivity(this, REQ_WAKE_FSI, wakeActivityIntent(), piFlags)

        val notification = NotificationCompat.Builder(this, CHANNEL_TRIGGER)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Hey Car")
            .setContentText("Tap to open Copilot")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .setFullScreenIntent(fullScreenPi, true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_TRIGGER_ID, notification)
    }

    private fun promoteForeground() {
        val standby = NotificationCompat.Builder(this, CHANNEL_STANDBY)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Wheelchair Copilot")
            .setContentText("Listening for Hey Car")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    REQ_STANDBY,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIF_STANDBY_ID,
                standby,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_STANDBY_ID, standby, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_STANDBY_ID, standby)
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STANDBY,
                "Wake standby",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing listening for Hey Car"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRIGGER,
                "Wake trigger",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Heads-up when Hey Car is detected"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun stopSelfSafe() {
        isRunning = false
        try {
            engine?.shutdown()
        } catch (_: Exception) {
        }
        engine = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        try {
            engine?.shutdown()
        } catch (_: Exception) {
        }
        engine = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CockpitUI"
        const val ACTION_START = "com.wheelchair.cockpit.wake.START"
        const val ACTION_STOP = "com.wheelchair.cockpit.wake.STOP"
        const val ACTION_PAUSE = "com.wheelchair.cockpit.wake.PAUSE"
        const val ACTION_RESUME = "com.wheelchair.cockpit.wake.RESUME"
        const val EXTRA_WAKE_DETECTED = "com.wheelchair.cockpit.EXTRA_WAKE_DETECTED"

        const val CHANNEL_STANDBY = "wake_standby"
        const val CHANNEL_TRIGGER = "wake_trigger"
        const val NOTIF_STANDBY_ID = 1401
        const val NOTIF_TRIGGER_ID = 1402
        private const val REQ_STANDBY = 1410
        private const val REQ_WAKE_CONTENT = 1411
        private const val REQ_WAKE_FSI = 1412

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var startFailed: Boolean = false

        fun start(context: Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            if (!isRunning) return
            context.startService(
                Intent(context, WakeWordForegroundService::class.java).setAction(ACTION_PAUSE)
            )
        }

        fun resume(context: Context) {
            if (!isRunning) {
                start(context)
                return
            }
            context.startService(
                Intent(context, WakeWordForegroundService::class.java).setAction(ACTION_RESUME)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WakeWordForegroundService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
// --- END MODIFICATION ---
