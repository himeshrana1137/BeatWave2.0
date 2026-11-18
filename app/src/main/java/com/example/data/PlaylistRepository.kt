package com.example.data

import com.example.data.local.BeatWaveDatabase
import com.example.data.local.ListeningSummaryEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackEntity
import com.example.data.local.PlaylistWithCount
import com.example.data.local.SongStatEntity
import com.example.data.model.Song
import com.example.data.remote.CloudSyncRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

class PlaylistRepository(
    localDb: BeatWaveDatabase,
    private val cloudSync: CloudSyncRepository
) {
    private val playlistDao = localDb.playlistDao()
    private val statsDao = localDb.statsDao()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observePlaylists(): Flow<List<PlaylistWithCount>> =
        playlistDao.observePlaylistsWithCount()

    fun observePlaylist(playlistId: String): Flow<PlaylistEntity?> =
        playlistDao.observePlaylist(playlistId)

    fun observePlaylistTracks(playlistId: String): Flow<List<PlaylistTrackEntity>> =
        playlistDao.observePlaylistTracks(playlistId)

    fun observeTopSongStats(): Flow<List<SongStatEntity>> =
        statsDao.observeTopSongStats()

    fun observeListeningSummary(): Flow<ListeningSummaryEntity?> =
        statsDao.observeListeningSummary()

    suspend fun createPlaylist(name: String): String {
        val trimmed = name.trim().take(100)
        require(trimmed.isNotEmpty()) { "Playlist name cannot be empty" }
        val now = System.currentTimeMillis()
        val id = "pl_" + UUID.randomUUID().toString().replace("-", "")
        playlistDao.insertPlaylist(
            PlaylistEntity(
                id = id,
                name = trimmed,
                createdAtMs = now,
                updatedAtMs = now,
                syncedToCloud = false
            )
        )
        triggerPlaylistPushIfSignedIn(id)
        return id
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        val trimmed = newName.trim().take(100)
        require(trimmed.isNotEmpty()) { "Playlist name cannot be empty" }
        playlistDao.renamePlaylist(playlistId, trimmed, System.currentTimeMillis())
        triggerPlaylistPushIfSignedIn(playlistId)
    }

    suspend fun deletePlaylist(playlistId: String) {
        playlistDao.deletePlaylistWithTombstone(playlistId, System.currentTimeMillis())
        if (Firebase.auth.currentUser != null) {
            backgroundScope.launch {
                cloudSync.pushPlaylistDeletion(playlistId)
            }
        }
    }

    suspend fun addSongsToPlaylist(playlistId: String, songs: List<Song>) {
        if (songs.isEmpty()) return
        val existingTracks = playlistDao.getPlaylistTracksOnce(playlistId)
        val existingKeys = existingTracks.map { it.songKey }.toHashSet()
        var nextPos = (existingTracks.maxOfOrNull { it.position } ?: -1) + 1
        val now = System.currentTimeMillis()

        val newTracks = songs.filter { it.songKey !in existingKeys }.map { song ->
            PlaylistTrackEntity(
                id = "tr_" + UUID.randomUUID().toString().replace("-", ""),
                playlistId = playlistId,
                songKey = song.songKey,
                mediaStoreId = song.mediaStoreId,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                position = nextPos++,
                addedAtMs = now
            )
        }
        if (newTracks.isNotEmpty()) {
            playlistDao.insertTracks(newTracks)
            playlistDao.touchPlaylist(playlistId, now)
            triggerPlaylistPushIfSignedIn(playlistId)
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistDao.deleteTrackById(trackId)
        val remaining = playlistDao.getPlaylistTracksOnce(playlistId)
        val reindexed = remaining.mapIndexed { idx, t -> t.copy(position = idx) }
        playlistDao.reorderTracks(playlistId, reindexed, System.currentTimeMillis())
        triggerPlaylistPushIfSignedIn(playlistId)
    }

    suspend fun reorderPlaylistTracks(playlistId: String, orderedTracks: List<PlaylistTrackEntity>) {
        val reindexed = orderedTracks.mapIndexed { idx, track -> track.copy(position = idx) }
        playlistDao.reorderTracks(playlistId, reindexed, System.currentTimeMillis())
        triggerPlaylistPushIfSignedIn(playlistId)
    }

    private fun triggerPlaylistPushIfSignedIn(playlistId: String) {
        if (Firebase.auth.currentUser != null) {
            backgroundScope.launch {
                cloudSync.pushPlaylistNow(playlistId)
            }
        }
    }

    fun triggerFullSyncIfSignedIn() {
        if (Firebase.auth.currentUser != null) {
            backgroundScope.launch {
                cloudSync.syncAll()
            }
        }
    }
}
