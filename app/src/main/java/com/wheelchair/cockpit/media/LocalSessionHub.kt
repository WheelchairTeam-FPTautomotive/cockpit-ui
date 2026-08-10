package com.wheelchair.cockpit.media

import android.media.session.MediaSession
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Same-process bridge to [LocalMediaService]'s MediaSession token.
 * Studio / non-privapp installs lack MEDIA_CONTENT_CONTROL, so
 * MediaSessionManager.getActiveSessions is empty even while local audio plays.
 */
object LocalSessionHub {
    private val tokenRef = AtomicReference<MediaSession.Token?>(null)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun publish(token: MediaSession.Token) {
        tokenRef.set(token)
        listeners.forEach { it.invoke() }
    }

    fun clear() {
        tokenRef.set(null)
        listeners.forEach { it.invoke() }
    }

    fun token(): MediaSession.Token? = tokenRef.get()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
}
