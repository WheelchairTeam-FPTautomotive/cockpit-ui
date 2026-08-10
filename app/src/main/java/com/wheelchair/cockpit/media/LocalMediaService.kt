package com.wheelchair.cockpit.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.wheelchair.cockpit.MainActivity
import java.io.File

/**
 * Onboard MediaSession over bundled assets (+ optional /sdcard/Music/Wheelchair).
 * Guarantees a controllable session even when YT Music / SoundCloud fail to start.
 */
class LocalMediaService : Service() {

    private var mediaSession: MediaSession? = null
    private var player: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tracks: List<LocalTrack> = emptyList()
    private var index: Int = 0
    private var prepared = false

    private val progressTicker = object : Runnable {
        override fun run() {
            publishPlaybackState()
            if (player?.isPlaying == true) {
                mainHandler.postDelayed(this, 500L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        tracks = discoverTracks()
        mediaSession = MediaSession(this, "WheelchairLocalMedia").apply {
            setCallback(sessionCallback)
            isActive = true
        }
        // MODIFIED: publish token so UI can bind without MEDIA_CONTENT_CONTROL (Studio emulator)
        LocalSessionHub.publish(mediaSession!!.sessionToken)
        if (tracks.isNotEmpty()) {
            // MODIFIED: publish queue so carousel can show prev/next neighbors
            publishQueue()
            prepareTrack(index, autoPlay = false)
        } else {
            Log.w(TAG, "No local tracks found (assets/music or $REMOTE_MUSIC_DIR)")
            publishEmptyMetadata()
        }
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> mediaSession?.controller?.transportControls?.play()
            ACTION_PAUSE -> mediaSession?.controller?.transportControls?.pause()
            ACTION_NEXT -> mediaSession?.controller?.transportControls?.skipToNext()
            ACTION_PREV -> mediaSession?.controller?.transportControls?.skipToPrevious()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mainHandler.removeCallbacks(progressTicker)
        releasePlayer()
        mediaSession?.apply {
            isActive = false
            setCallback(null)
            release()
        }
        mediaSession = null
        LocalSessionHub.clear()
        super.onDestroy()
    }

    private val sessionCallback = object : MediaSession.Callback() {
        override fun onPlay() {
            if (tracks.isEmpty()) return
            if (!prepared) prepareTrack(index, autoPlay = true)
            else {
                player?.start()
                publishPlaybackState()
                mainHandler.removeCallbacks(progressTicker)
                mainHandler.post(progressTicker)
            }
        }

        override fun onPause() {
            player?.pause()
            publishPlaybackState()
            mainHandler.removeCallbacks(progressTicker)
        }

        override fun onSkipToNext() {
            if (tracks.isEmpty()) return
            index = (index + 1) % tracks.size
            prepareTrack(index, autoPlay = true)
        }

        override fun onSkipToPrevious() {
            if (tracks.isEmpty()) return
            index = if (index - 1 < 0) tracks.lastIndex else index - 1
            prepareTrack(index, autoPlay = true)
        }

        override fun onSkipToQueueItem(id: Long) {
            if (tracks.isEmpty()) return
            val nextIndex = id.toInt()
            if (nextIndex !in tracks.indices) return
            index = nextIndex
            prepareTrack(index, autoPlay = true)
        }

        override fun onSeekTo(pos: Long) {
            player?.seekTo(pos.toInt())
            publishPlaybackState()
        }

        override fun onStop() {
            player?.pause()
            player?.seekTo(0)
            publishPlaybackState()
            mainHandler.removeCallbacks(progressTicker)
        }
    }

    private fun discoverTracks(): List<LocalTrack> {
        val found = linkedMapOf<String, LocalTrack>()

        // 1) Bundled assets (always available after APK install)
        try {
            assets.list(ASSET_MUSIC_DIR)?.forEach { name ->
                if (name.endsWith(".mp3", ignoreCase = true)) {
                    found[name] = LocalTrack(
                        id = name,
                        title = friendlyTitle(name),
                        artist = "Local",
                        assetPath = "$ASSET_MUSIC_DIR/$name",
                        filePath = null
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Asset music list failed", e)
        }

        // 2) Device Music folder from install-media-stack.ps1
        val remoteDir = File(REMOTE_MUSIC_DIR)
        if (remoteDir.isDirectory) {
            remoteDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp3", ignoreCase = true) }
                ?.forEach { file ->
                    found[file.name] = LocalTrack(
                        id = file.name,
                        title = friendlyTitle(file.name),
                        artist = "Local",
                        assetPath = null,
                        filePath = file.absolutePath
                    )
                }
        }

        return found.values.toList().sortedBy { it.title }
    }

    private fun prepareTrack(trackIndex: Int, autoPlay: Boolean) {
        if (tracks.isEmpty()) return
        index = trackIndex.coerceIn(0, tracks.lastIndex)
        val track = tracks[index]
        releasePlayer()
        prepared = false

        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            if (track.filePath != null) {
                mp.setDataSource(track.filePath)
            } else {
                val afd = assets.openFd(track.assetPath!!)
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }
            mp.setOnPreparedListener {
                prepared = true
                publishMetadata(track, it.duration.toLong())
                if (autoPlay) {
                    it.start()
                    mainHandler.removeCallbacks(progressTicker)
                    mainHandler.post(progressTicker)
                }
                publishPlaybackState()
            }
            mp.setOnCompletionListener {
                sessionCallback.onSkipToNext()
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                true
            }
            mp.prepareAsync()
            publishMetadata(track, 0L)
            publishPlaybackState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare ${track.id}", e)
            releasePlayer()
        }
    }

    private fun publishQueue() {
        val items = tracks.mapIndexed { i, track ->
            val description = MediaDescription.Builder()
                .setMediaId(track.id)
                .setTitle(track.title)
                .setSubtitle(track.artist)
                .build()
            MediaSession.QueueItem(description, i.toLong())
        }
        mediaSession?.setQueue(items)
        mediaSession?.setQueueTitle("Local")
    }

    private fun publishMetadata(track: LocalTrack, durationMs: Long) {
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, track.title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, track.artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            .build()
        mediaSession?.setMetadata(metadata)
    }

    private fun publishEmptyMetadata() {
        mediaSession?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "No local tracks")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Add MP3s via install-media-stack")
                .build()
        )
        mediaSession?.setQueue(emptyList())
        publishPlaybackState()
    }

    private fun publishPlaybackState() {
        val playing = player?.isPlaying == true
        val position = try {
            player?.currentPosition?.toLong() ?: 0L
        } catch (_: Exception) {
            0L
        }
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM or
            PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_STOP
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setActiveQueueItemId(index.toLong())
                .setState(state, position, if (playing) 1f else 0f)
                .build()
        )
    }

    private fun releasePlayer() {
        try {
            player?.reset()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        prepared = false
    }

    private fun startAsForeground() {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Wheelchair Media")
            .setContentText("Local music session ready")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Local media",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    private data class LocalTrack(
        val id: String,
        val title: String,
        val artist: String,
        val assetPath: String?,
        val filePath: String?
    )

    companion object {
        private const val TAG = "LocalMediaService"
        private const val CHANNEL_ID = "wheelchair_local_media"
        private const val NOTIF_ID = 42
        private const val ASSET_MUSIC_DIR = "music"
        const val REMOTE_MUSIC_DIR = "/sdcard/Music/Wheelchair"

        const val ACTION_PLAY = "com.wheelchair.cockpit.media.PLAY"
        const val ACTION_PAUSE = "com.wheelchair.cockpit.media.PAUSE"
        const val ACTION_NEXT = "com.wheelchair.cockpit.media.NEXT"
        const val ACTION_PREV = "com.wheelchair.cockpit.media.PREV"
        const val ACTION_STOP = "com.wheelchair.cockpit.media.STOP"

        fun friendlyTitle(fileName: String): String {
            var name = fileName.removeSuffix(".mp3").removeSuffix(".MP3")
            name = name.removePrefix("YTDown.com_YouTube_")
            name = name.replace(Regex("_Media_-[A-Za-z0-9_-]+_\\d+_\\d+k$"), "")
            name = name.replace(Regex("_Media_[A-Za-z0-9_-]+_\\d+_\\d+k$"), "")
            name = name.replace('_', ' ').replace('-', ' ').trim()
            return name.ifBlank { fileName }
        }

        fun start(context: android.content.Context) {
            val intent = Intent(context, LocalMediaService::class.java)
            context.startForegroundService(intent)
        }
    }
}
