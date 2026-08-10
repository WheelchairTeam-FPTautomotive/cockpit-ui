package com.wheelchair.cockpit.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * System-wide media hub: listens to active MediaSessions and routes transport.
 *
 * Access paths (first that works):
 * 1. Privileged MEDIA_CONTENT_CONTROL (Carsky priv-app)
 * 2. Enabled [MediaNotificationListener] ComponentName (Studio emulator)
 * 3. [LocalSessionHub] same-process token (Local tracks only)
 */
class MediaControllerRepository(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listenerComponent =
        ComponentName(context, MediaNotificationListener::class.java)

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private val _preference = MutableStateFlow(MediaSourcePreference.ACTIVE)
    val preference: StateFlow<MediaSourcePreference> = _preference.asStateFlow()

    private var activeController: MediaController? = null
    private var started = false
    private var sessionQueryComponent: ComponentName? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishFrom(activeController)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishFrom(activeController)
        }

        // --- START MODIFICATION ---
        // Queue updates so prev/next carousel covers refresh for YT Music / SoundCloud
        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
            publishFrom(activeController)
        }

        override fun onQueueTitleChanged(title: CharSequence?) {
            publishFrom(activeController)
        }
        // --- END MODIFICATION ---

        override fun onSessionDestroyed() {
            Log.i(TAG, "Active session destroyed")
            bindController(null)
            refreshSessions()
        }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            mainHandler.post { onSessionsChanged(controllers) }
        }

    private val localHubListener: () -> Unit = {
        mainHandler.post { refreshSessions() }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            val c = activeController
            if (c != null) {
                publishFrom(c)
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    mainHandler.postDelayed(this, 500L)
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        activeInstance = WeakReference(this)
        LocalMediaService.start(context)
        LocalSessionHub.addListener(localHubListener)
        ensureAudibleMediaVolume()
        attachSessionListener()
        refreshSessions()
        mainHandler.postDelayed({ attachSessionListener(); refreshSessions() }, 500L)
        mainHandler.postDelayed({ refreshSessions() }, 1500L)
    }

    fun stop() {
        if (!started) return
        started = false
        if (activeInstance?.get() === this) activeInstance = null
        mainHandler.removeCallbacks(progressTicker)
        LocalSessionHub.removeListener(localHubListener)
        try {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        bindController(null)
    }

    fun setSourcePreference(pref: MediaSourcePreference) {
        _preference.value = pref
        when (pref) {
            MediaSourcePreference.LOCAL -> {
                // Pause external players so Local owns the bus
                transportForPackage(MediaPackages.YOUTUBE_MUSIC)?.pause()
                transportForPackage(MediaPackages.SOUNDCLOUD)?.pause()
                LocalMediaService.start(context)
                refreshSessions()
                play()
            }
            MediaSourcePreference.YOUTUBE_MUSIC -> {
                // MODIFIED: stop local so YT Music owns audio focus / active session
                pauseLocalOnly()
                if (!launchPackage(MediaPackages.YOUTUBE_MUSIC)) {
                    Log.w(TAG, "YouTube Music not installed — run install-media-stack.ps1")
                }
                mainHandler.postDelayed({ refreshSessions(); play() }, 800L)
            }
            MediaSourcePreference.SOUNDCLOUD -> {
                pauseLocalOnly()
                if (!launchPackage(MediaPackages.SOUNDCLOUD)) {
                    Log.w(TAG, "SoundCloud not installed — run install-media-stack.ps1")
                }
                mainHandler.postDelayed({ refreshSessions(); play() }, 800L)
            }
            MediaSourcePreference.ACTIVE -> refreshSessions()
        }
    }

    fun play() {
        ensureBound()
        val controls = transport()
        if (controls != null) {
            controls.play()
        } else if (_preference.value == MediaSourcePreference.LOCAL ||
            _preference.value == MediaSourcePreference.ACTIVE
        ) {
            startLocalAndPlay()
        }
    }

    fun pause() {
        ensureBound()
        transport()?.pause()
    }

    fun playPause() {
        ensureBound()
        val state = activeController?.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) pause() else play()
    }

    fun skipNext() {
        ensureBound()
        transport()?.skipToNext()
    }

    fun skipPrevious() {
        ensureBound()
        transport()?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        ensureBound()
        transport()?.seekTo(positionMs)
    }

    fun getMusicVolumeFraction(): Float {
        reflectCarMusicVolumeFraction()?.let { return it }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / max.toFloat()
    }

    fun setMusicVolumeFraction(fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        if (!reflectSetCarMusicVolumeFraction(f)) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val target = (f * max).toInt().coerceIn(0, max)
            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
            if (f > 0.01f) {
                try {
                    am.adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.ADJUST_UNMUTE,
                        0
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Raise / unmute MEDIA so Studio isn't stuck silent after UI volume glitches. */
    fun ensureAudibleMediaVolume() {
        try {
            val cur = getMusicVolumeFraction()
            if (cur < 0.35f) {
                setMusicVolumeFraction(0.8f)
                Log.i(TAG, "Restored media volume from ${"%.2f".format(cur)} → 0.80")
            } else {
                setMusicVolumeFraction(cur.coerceAtLeast(0.5f))
            }
        } catch (e: Exception) {
            Log.w(TAG, "ensureAudibleMediaVolume failed", e)
        }
    }

    /**
     * CarAudioManager stubs are incomplete in compile SDK — drive AAOS MUSIC volume
     * group (bus0_media_out) via reflection.
     */
    private fun withCarAudio(block: (Any) -> Unit): Boolean {
        return try {
            val car = android.car.Car.createCar(context) ?: return false
            val carAudio = car.getCarManager(android.car.Car.AUDIO_SERVICE) ?: return false
            block(carAudio)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Car AUDIO_SERVICE unavailable", e)
            false
        }
    }

    private fun reflectCarMusicVolumeFraction(): Float? {
        var result: Float? = null
        withCarAudio { carAudio ->
            val group = invokeInt(carAudio, "getVolumeGroupIdForUsage", android.media.AudioAttributes.USAGE_MEDIA)
            val max = runCatching { invokeInt(carAudio, "getGroupMaxVolume", PRIMARY_ZONE, group) }
                .getOrElse { invokeInt(carAudio, "getGroupMaxVolume", group) }
            val min = runCatching { invokeInt(carAudio, "getGroupMinVolume", PRIMARY_ZONE, group) }
                .getOrElse { runCatching { invokeInt(carAudio, "getGroupMinVolume", group) }.getOrDefault(0) }
            val cur = runCatching { invokeInt(carAudio, "getGroupVolume", PRIMARY_ZONE, group) }
                .getOrElse { invokeInt(carAudio, "getGroupVolume", group) }
            val span = (max - min).coerceAtLeast(1)
            result = ((cur - min).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }
        return result
    }

    private fun reflectSetCarMusicVolumeFraction(fraction: Float): Boolean {
        return withCarAudio { carAudio ->
            val group = invokeInt(carAudio, "getVolumeGroupIdForUsage", android.media.AudioAttributes.USAGE_MEDIA)
            val max = runCatching { invokeInt(carAudio, "getGroupMaxVolume", PRIMARY_ZONE, group) }
                .getOrElse { invokeInt(carAudio, "getGroupMaxVolume", group) }
            val min = runCatching { invokeInt(carAudio, "getGroupMinVolume", PRIMARY_ZONE, group) }
                .getOrElse { runCatching { invokeInt(carAudio, "getGroupMinVolume", group) }.getOrDefault(0) }
            val target = (min + fraction * (max - min)).toInt().coerceIn(min, max)
            val setOk = runCatching {
                carAudio.javaClass.getMethod(
                    "setGroupVolume",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).invoke(carAudio, PRIMARY_ZONE, group, target, 0)
                true
            }.getOrElse {
                runCatching {
                    carAudio.javaClass.getMethod(
                        "setGroupVolume",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    ).invoke(carAudio, group, target, 0)
                    true
                }.getOrDefault(false)
            }
            if (setOk && fraction > 0.01f) {
                runCatching {
                    carAudio.javaClass.getMethod(
                        "setVolumeGroupMute",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    ).invoke(carAudio, PRIMARY_ZONE, group, false, 0)
                }
            }
        }
    }

    private fun invokeInt(target: Any, name: String, vararg args: Int): Int {
        val types = Array(args.size) { Int::class.javaPrimitiveType }
        val method = target.javaClass.getMethod(name, *types)
        return method.invoke(target, *args.toTypedArray()) as Int
    }

    fun openSourceApp() {
        val pkg = _nowPlaying.value.packageName ?: return
        launchPackage(pkg)
    }

    fun isNotificationAccessGranted(): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.isNotificationListenerAccessGranted(listenerComponent)
    }

    private fun transportForPackage(pkg: String): MediaController.TransportControls? {
        return try {
            sessionManager.getActiveSessions(sessionQueryComponent ?: resolveSessionComponent())
                .firstOrNull { it.packageName == pkg }
                ?.transportControls
        } catch (_: Exception) {
            null
        }
    }

    private fun transport(): MediaController.TransportControls? =
        activeController?.transportControls

    private fun ensureBound() {
        if (activeController == null) refreshSessions()
    }

    private fun pauseLocalOnly() {
        try {
            context.startService(
                Intent(context, LocalMediaService::class.java).setAction(LocalMediaService.ACTION_PAUSE)
            )
        } catch (e: Exception) {
            Log.w(TAG, "pauseLocalOnly failed", e)
        }
    }

    private fun startLocalAndPlay() {
        _preference.value = MediaSourcePreference.LOCAL
        LocalMediaService.start(context)
        context.startForegroundService(
            Intent(context, LocalMediaService::class.java).setAction(LocalMediaService.ACTION_PLAY)
        )
        mainHandler.postDelayed({
            refreshSessions()
            transport()?.play()
        }, 400L)
    }

    private fun attachSessionListener() {
        try {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        sessionQueryComponent = resolveSessionComponent()
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                sessionsListener,
                sessionQueryComponent,
                mainHandler
            )
            Log.i(TAG, "Session listener attached component=$sessionQueryComponent")
        } catch (e: SecurityException) {
            sessionQueryComponent = null
            Log.w(TAG, "Cannot attach session listener — Local only until NLS enabled", e)
        }
    }

    private fun resolveSessionComponent(): ComponentName? {
        // 1) Privileged: null component with MEDIA_CONTENT_CONTROL
        try {
            sessionManager.getActiveSessions(null)
            return null
        } catch (_: SecurityException) {
        }
        // 2) Studio: enabled notification listener
        if (isNotificationAccessGranted()) {
            return listenerComponent
        }
        Log.w(
            TAG,
            "Enable notification access for YT Music control: " +
                "adb shell cmd notification allow_listener ${listenerComponent.flattenToString()}"
        )
        return listenerComponent // still pass; may work after user enables
    }

    private fun localControllerOrNull(): MediaController? {
        val token = LocalSessionHub.token() ?: return null
        return try {
            MediaController(context, token)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create local MediaController", e)
            null
        }
    }

    private fun refreshSessions() {
        val system = try {
            sessionManager.getActiveSessions(sessionQueryComponent ?: resolveSessionComponent().also {
                sessionQueryComponent = it
            })
        } catch (e: SecurityException) {
            Log.w(TAG, "getActiveSessions denied — Local hub only", e)
            emptyList()
        }
        onSessionsChanged(system)
    }

    private fun onSessionsChanged(controllers: List<MediaController>?) {
        val merged = controllers.orEmpty().toMutableList()
        val local = localControllerOrNull()
        if (local != null && merged.none { it.sessionToken == local.sessionToken }) {
            merged.add(local)
        }

        val preferred = when {
            merged.isEmpty() -> null
            else -> pickController(merged, _preference.value) ?: local
        }
        bindController(preferred)
        publishFrom(preferred ?: activeController)
    }

    private fun pickController(
        controllers: List<MediaController>,
        pref: MediaSourcePreference
    ): MediaController? {
        if (controllers.isEmpty()) return null
        fun byPkg(pkg: String) = controllers.firstOrNull { it.packageName == pkg }

        return when (pref) {
            MediaSourcePreference.YOUTUBE_MUSIC ->
                byPkg(MediaPackages.YOUTUBE_MUSIC) ?: playingExternalOrFirst(controllers)
            MediaSourcePreference.SOUNDCLOUD ->
                byPkg(MediaPackages.SOUNDCLOUD) ?: playingExternalOrFirst(controllers)
            MediaSourcePreference.LOCAL ->
                byPkg(MediaPackages.LOCAL) ?: localControllerOrNull()
            MediaSourcePreference.ACTIVE -> playingExternalOrFirst(controllers)
        }
    }

    private fun playingExternalOrFirst(controllers: List<MediaController>): MediaController? {
        val playingExt = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING &&
                it.packageName != MediaPackages.LOCAL
        }
        if (playingExt != null) return playingExt
        val playingAny = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        if (playingAny != null) return playingAny
        val yt = controllers.firstOrNull { it.packageName == MediaPackages.YOUTUBE_MUSIC }
        if (yt != null) return yt
        val sc = controllers.firstOrNull { it.packageName == MediaPackages.SOUNDCLOUD }
        if (sc != null) return sc
        return controllers.firstOrNull { it.packageName != MediaPackages.LOCAL }
            ?: controllers.firstOrNull()
    }

    private fun bindController(next: MediaController?) {
        val prev = activeController
        if (prev != null && next != null && prev.sessionToken == next.sessionToken) {
            mainHandler.removeCallbacks(progressTicker)
            mainHandler.post(progressTicker)
            return
        }
        if (prev != null) {
            try {
                prev.unregisterCallback(controllerCallback)
            } catch (e: Exception) {
                Log.w(TAG, "unregisterCallback failed", e)
            }
        }
        activeController = next
        if (next != null) {
            next.registerCallback(controllerCallback, mainHandler)
            mainHandler.removeCallbacks(progressTicker)
            mainHandler.post(progressTicker)
        } else {
            mainHandler.removeCallbacks(progressTicker)
        }
        Log.i(TAG, "Bound session package=${next?.packageName} title=${next?.metadata?.description?.title}")
    }

    private fun publishFrom(controller: MediaController?) {
        if (controller == null) {
            _nowPlaying.value = NowPlaying()
            return
        }
        val meta = controller.metadata
        val state = controller.playbackState
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: meta?.description?.title?.toString()
            ?: "Unknown title"
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: meta?.description?.subtitle?.toString()
            ?: "—"
        val art: Bitmap? = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val artUri = meta?.description?.iconUri?.toString()
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = state?.position ?: 0L
        val playing = state?.state == PlaybackState.STATE_PLAYING

        // --- START MODIFICATION ---
        // Pull adjacent queue item covers for carousel neighbors
        val neighbors = resolveQueueNeighbors(controller)
        // --- END MODIFICATION ---

        _nowPlaying.value = NowPlaying(
            title = title,
            artist = artist,
            albumArt = art,
            artUri = artUri,
            prev2AlbumArt = neighbors[-2]?.first,
            prev2ArtUri = neighbors[-2]?.second,
            prevAlbumArt = neighbors[-1]?.first,
            prevArtUri = neighbors[-1]?.second,
            nextAlbumArt = neighbors[1]?.first,
            nextArtUri = neighbors[1]?.second,
            next2AlbumArt = neighbors[2]?.first,
            next2ArtUri = neighbors[2]?.second,
            isPlaying = playing,
            positionMs = position,
            durationMs = duration,
            packageName = controller.packageName,
            sourceLabel = sourceLabelFor(controller.packageName)
        )
    }

    /**
     * Resolve cover bitmap/URI for queue offsets relative to the active item.
     * YT Music usually publishes a queue; SoundCloud often does not — then map stays empty.
     */
    private fun resolveQueueNeighbors(
        controller: MediaController
    ): Map<Int, Pair<Bitmap?, String?>> {
        val queue = controller.queue ?: return emptyMap()
        if (queue.isEmpty()) return emptyMap()

        val activeId = controller.playbackState?.activeQueueItemId
            ?: MediaSession.QueueItem.UNKNOWN_ID
        var idx = if (activeId != MediaSession.QueueItem.UNKNOWN_ID) {
            queue.indexOfFirst { it.queueId == activeId }
        } else {
            -1
        }
        // Fallback: match current metadata title against queue descriptions
        if (idx < 0) {
            val currentTitle = controller.metadata?.description?.title?.toString()
                ?: controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            if (!currentTitle.isNullOrBlank()) {
                idx = queue.indexOfFirst {
                    it.description?.title?.toString().equals(currentTitle, ignoreCase = true)
                }
            }
        }
        if (idx < 0) idx = 0

        val out = mutableMapOf<Int, Pair<Bitmap?, String?>>()
        for (offset in listOf(-2, -1, 1, 2)) {
            val at = idx + offset
            if (at !in queue.indices) continue
            out[offset] = artFromDescription(queue[at].description)
        }
        return out
    }

    private fun artFromDescription(desc: MediaDescription?): Pair<Bitmap?, String?> {
        if (desc == null) return null to null
        val bmp = desc.iconBitmap
        val uri = desc.iconUri?.toString()
        return bmp to uri
    }

    private fun sourceLabelFor(pkg: String?): String = when (pkg) {
        MediaPackages.YOUTUBE_MUSIC -> "YouTube Music"
        MediaPackages.SOUNDCLOUD -> "SoundCloud"
        MediaPackages.LOCAL -> "Local"
        null -> "None"
        else -> pkg.substringAfterLast('.')
    }

    private fun launchPackage(pkg: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            return true
        }
        Log.w(TAG, "No launch intent for $pkg")
        return false
    }

    companion object {
        private const val TAG = "MediaControllerRepo"
        private const val PRIMARY_ZONE = 0 // CarAudioManager.PRIMARY_AUDIO_ZONE
        private var activeInstance: WeakReference<MediaControllerRepository>? = null

        fun requestRefreshFromListener() {
            activeInstance?.get()?.let { repo ->
                repo.mainHandler.post {
                    repo.attachSessionListener()
                    repo.refreshSessions()
                }
            }
        }
    }
}
