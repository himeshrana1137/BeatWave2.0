package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val syncedToCloud: Boolean = false
)

@Entity(
    tableName = "playlist_tracks",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId", "position"])]
)
data class PlaylistTrackEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val songKey: String,
    val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val position: Int,
    val addedAtMs: Long
)

@Entity(tableName = "deleted_playlists")
data class DeletedPlaylistEntity(
    @PrimaryKey val playlistId: String,
    val deletedAtMs: Long
)

@Entity(tableName = "song_stats")
data class SongStatEntity(
    @PrimaryKey val songKey: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val playCount: Long,
    val listenedMs: Long,
    val lastPlayedAtMs: Long
)

@Entity(tableName = "listening_summary")
data class ListeningSummaryEntity(
    @PrimaryKey val id: Int = 1,
    val totalListeningMs: Long,
    val totalPlays: Long,
    val updatedAtMs: Long
)

data class PlaylistWithCount(
    val id: String,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val trackCount: Int
)
