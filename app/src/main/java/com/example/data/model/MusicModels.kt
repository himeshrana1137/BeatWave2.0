package com.example.data.model

import android.net.Uri
import java.security.MessageDigest
import java.util.Locale

data class Song(
    val mediaStoreId: Long,
    val songKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val contentUri: Uri,
    val albumArtUri: Uri?,
    val dateAdded: Long = 0L
)

data class AlbumGroup(
    val albumId: Long,
    val title: String,
    val artist: String,
    val albumArtUri: Uri?,
    val songs: List<Song>
)

data class ArtistGroup(
    val name: String,
    val albumCount: Int,
    val songs: List<Song>
)

enum class RepeatMode {
    OFF,
    ONE,
    ALL
}

object SongKeyUtil {
    fun computeSongKey(title: String, artist: String, durationMs: Long): String {
        val normalized = "${title.trim().lowercase(Locale.ROOT)}|${artist.trim().lowercase(Locale.ROOT)}|$durationMs"
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
        return "song_$hex"
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    fun formatListeningHours(totalMs: Long): String {
        val safeMs = totalMs.coerceAtLeast(0L)
        val totalMinutes = safeMs / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0L -> String.format(Locale.ROOT, "%dh %dm", hours, minutes)
            totalMinutes > 0L -> String.format(Locale.ROOT, "%dm", totalMinutes)
            else -> String.format(Locale.ROOT, "%ds", (safeMs / 1000L))
        }
    }
}
