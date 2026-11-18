package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.id, p.name, p.createdAtMs, p.updatedAtMs, COUNT(t.id) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks t ON p.id = t.playlistId
        GROUP BY p.id
        ORDER BY p.updatedAtMs DESC
        """
    )
    fun observePlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists ORDER BY updatedAtMs DESC")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun observePlaylist(playlistId: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistOnce(playlistId: String): PlaylistEntity?

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observePlaylistTracks(playlistId: String): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistTracksOnce(playlistId: String): List<PlaylistTrackEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>)

    @Query("UPDATE playlists SET name = :name, updatedAtMs = :updatedAtMs, syncedToCloud = 0 WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: String, name: String, updatedAtMs: Long)

    @Query("UPDATE playlists SET updatedAtMs = :updatedAtMs, syncedToCloud = 0 WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: String, updatedAtMs: Long)

    @Query("UPDATE playlists SET syncedToCloud = 1 WHERE id = :playlistId")
    suspend fun markPlaylistSynced(playlistId: String)

    @Query("DELETE FROM playlist_tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteTracksForPlaylist(playlistId: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistRow(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedTombstone(tombstone: DeletedPlaylistEntity)

    @Query("SELECT * FROM deleted_playlists")
    suspend fun getDeletedTombstones(): List<DeletedPlaylistEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_playlists WHERE playlistId = :playlistId)")
    suspend fun isPlaylistDeleted(playlistId: String): Boolean

    @Query("DELETE FROM deleted_playlists WHERE playlistId = :playlistId")
    suspend fun clearDeletedTombstone(playlistId: String)

    @Transaction
    suspend fun deletePlaylistWithTombstone(playlistId: String, deletedAtMs: Long) {
        insertDeletedTombstone(DeletedPlaylistEntity(playlistId = playlistId, deletedAtMs = deletedAtMs))
        deletePlaylistRow(playlistId)
    }

    @Transaction
    suspend fun reorderTracks(playlistId: String, orderedTracks: List<PlaylistTrackEntity>, updatedAtMs: Long) {
        orderedTracks.forEachIndexed { index, track ->
            insertTracks(listOf(track.copy(position = index)))
        }
        touchPlaylist(playlistId, updatedAtMs)
    }

    @Transaction
    suspend fun replacePlaylistAndTracksFromCloud(
        playlist: PlaylistEntity,
        tracks: List<PlaylistTrackEntity>
    ) {
        insertPlaylist(playlist)
        deleteTracksForPlaylist(playlist.id)
        if (tracks.isNotEmpty()) {
            insertTracks(tracks)
        }
    }
}

@Dao
interface StatsDao {

    @Query("SELECT * FROM song_stats ORDER BY playCount DESC, listenedMs DESC")
    fun observeTopSongStats(): Flow<List<SongStatEntity>>

    @Query("SELECT * FROM song_stats")
    suspend fun getAllSongStatsOnce(): List<SongStatEntity>

    @Query("SELECT * FROM song_stats WHERE songKey = :songKey LIMIT 1")
    suspend fun getSongStatOnce(songKey: String): SongStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongStat(stat: SongStatEntity)

    @Query("SELECT * FROM listening_summary WHERE id = 1 LIMIT 1")
    fun observeListeningSummary(): Flow<ListeningSummaryEntity?>

    @Query("SELECT * FROM listening_summary WHERE id = 1 LIMIT 1")
    suspend fun getListeningSummaryOnce(): ListeningSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertListeningSummary(summary: ListeningSummaryEntity)

    @Transaction
    suspend fun incrementPlayCount(song: Song, nowMs: Long) {
        val existing = getSongStatOnce(song.songKey)
        val updatedStat = if (existing == null) {
            SongStatEntity(
                songKey = song.songKey,
                title = song.title,
                artist = song.artist,
                durationMs = song.durationMs,
                playCount = 1L,
                listenedMs = 0L,
                lastPlayedAtMs = nowMs
            )
        } else {
            existing.copy(
                title = song.title,
                artist = song.artist,
                durationMs = song.durationMs,
                playCount = existing.playCount + 1L,
                lastPlayedAtMs = nowMs
            )
        }
        upsertSongStat(updatedStat)

        val summary = getListeningSummaryOnce()
        val updatedSummary = if (summary == null) {
            ListeningSummaryEntity(
                id = 1,
                totalListeningMs = 0L,
                totalPlays = 1L,
                updatedAtMs = nowMs
            )
        } else {
            summary.copy(
                totalPlays = summary.totalPlays + 1L,
                updatedAtMs = nowMs
            )
        }
        upsertListeningSummary(updatedSummary)
    }

    @Transaction
    suspend fun addListeningDuration(song: Song, deltaMs: Long, nowMs: Long) {
        if (deltaMs <= 0L) return
        val existing = getSongStatOnce(song.songKey)
        val updatedStat = if (existing == null) {
            SongStatEntity(
                songKey = song.songKey,
                title = song.title,
                artist = song.artist,
                durationMs = song.durationMs,
                playCount = 1L,
                listenedMs = deltaMs,
                lastPlayedAtMs = nowMs
            )
        } else {
            existing.copy(
                listenedMs = existing.listenedMs + deltaMs,
                lastPlayedAtMs = nowMs
            )
        }
        upsertSongStat(updatedStat)

        val summary = getListeningSummaryOnce()
        val updatedSummary = if (summary == null) {
            ListeningSummaryEntity(
                id = 1,
                totalListeningMs = deltaMs,
                totalPlays = 1L,
                updatedAtMs = nowMs
            )
        } else {
            summary.copy(
                totalListeningMs = summary.totalListeningMs + deltaMs,
                updatedAtMs = nowMs
            )
        }
        upsertListeningSummary(updatedSummary)
    }
}
