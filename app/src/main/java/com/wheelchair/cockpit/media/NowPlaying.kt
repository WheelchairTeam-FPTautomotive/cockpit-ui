package com.wheelchair.cockpit.media

import android.graphics.Bitmap

/**
 * Single source of truth for now-playing UI / voice.
 * Album art may be null — Compose must use a vector fallback / URI load.
 */
data class NowPlaying(
    val title: String = "No track playing",
    val artist: String = "—",
    val albumArt: Bitmap? = null,
    val artUri: String? = null,
    // MODIFIED: neighbors from MediaSession queue (YT Music / SoundCloud / Local)
    val prev2AlbumArt: Bitmap? = null,
    val prev2ArtUri: String? = null,
    val prevAlbumArt: Bitmap? = null,
    val prevArtUri: String? = null,
    val nextAlbumArt: Bitmap? = null,
    val nextArtUri: String? = null,
    val next2AlbumArt: Bitmap? = null,
    val next2ArtUri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val packageName: String? = null,
    val sourceLabel: String = "None"
) {
    val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    fun artForOffset(offset: Int): Pair<Bitmap?, String?> = when (offset) {
        -2 -> prev2AlbumArt to prev2ArtUri
        -1 -> prevAlbumArt to prevArtUri
        0 -> albumArt to artUri
        1 -> nextAlbumArt to nextArtUri
        2 -> next2AlbumArt to next2ArtUri
        else -> null to null
    }
}

enum class MediaSourcePreference {
    ACTIVE,
    YOUTUBE_MUSIC,
    SOUNDCLOUD,
    LOCAL
}

object MediaPackages {
    const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    const val SOUNDCLOUD = "com.soundcloud.android"
    const val LOCAL = "com.wheelchair.cockpit"
}
