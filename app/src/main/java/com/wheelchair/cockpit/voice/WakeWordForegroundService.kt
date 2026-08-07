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
 * BAL-safe bring-to-front: full-screen intent notification first; SAW startActivity when granted.
 */
// --- START MODIFICATION ---
class WakeWordForegroundService : Service() {

    private var engine: WakeWordEngine? = null
    private var wakeArmed = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        engine = WakeWordEngine(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
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

    private fun ensureListening() {
        if (!wakeArmed) {
            engine?.pause()
            return
        }
        val eng = engine ?: return
        eng.start(object : WakeWordEngine.Callbacks {
            override fun onWakeDetected() {
                if (!wakeArmed) return
                wakeArmed = false
                eng.pause()
                bringActivityForward()
            }

            override fun onError(message: String) {
                Log.e(TAG, "Wake FGS engine: $message")
            }
        })
    }

    private fun bringActivityForward() {
        // 1) Primary: high-importance FSI notification (BAL-privileged for FGS).
        postWakeFullScreenNotification()

        // 2) Emulator fast path: SYSTEM_ALERT_WINDOW is a BAL exemption.
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
