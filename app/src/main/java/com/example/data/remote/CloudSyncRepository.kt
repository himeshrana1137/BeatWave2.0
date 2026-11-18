package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.BeatWaveDatabase
import com.example.data.local.ListeningSummaryEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackEntity
import com.example.data.local.SessionPreferences
import com.example.data.local.SongStatEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val localDb: BeatWaveDatabase,
    private val prefs: SessionPreferences,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context, localDb: BeatWaveDatabase, prefs: SessionPreferences) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        localDb = localDb,
        prefs = prefs
    )

    private val playlistDao = localDb.playlistDao()
    private val statsDao = localDb.statsDao()

    private fun currentUserIdOrNull(): String? {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
    }

    suspend fun syncAll(): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = currentUserIdOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Cloud sync requires login"))
        try {
            pushPendingDeletions(uid)
            syncPlaylistsBidirectional(uid)
            syncStatsBidirectional(uid)
            prefs.setLastSyncMs(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun pushPlaylistDeletion(playlistId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = currentUserIdOrNull() ?: return@withContext Result.success(Unit)
        val playlistPath = "users/$uid/playlists/$playlistId"
        return@withContext try {
            deletePlaylistInFirestore(uid, playlistId)
            playlistDao.clearDeletedTombstone(playlistId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, playlistPath)
            Result.failure(e)
        }
    }

    suspend fun pushPlaylistNow(playlistId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = currentUserIdOrNull() ?: return@withContext Result.success(Unit)
        val playlist = playlistDao.getPlaylistOnce(playlistId) ?: return@withContext Result.success(Unit)
        val tracks = playlistDao.getPlaylistTracksOnce(playlistId)
        val path = "users/$uid/playlists/$playlistId"
        return@withContext try {
            upsertPlaylistInFirestore(uid, playlist, tracks)
            playlistDao.markPlaylistSynced(playlistId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun pushPendingDeletions(uid: String) {
        val tombstones = playlistDao.getDeletedTombstones()
        for (tombstone in tombstones) {
            val path = "users/$uid/playlists/${tombstone.playlistId}"
            try {
                deletePlaylistInFirestore(uid, tombstone.playlistId)
                playlistDao.clearDeletedTombstone(tombstone.playlistId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, path)
            }
        }
    }

    private suspend fun deletePlaylistInFirestore(uid: String, playlistId: String) {
        val playlistRef = db.collection("users").document(uid)
            .collection("playlists").document(playlistId)
        val tracksSnapshot = playlistRef.collection("tracks").get().await()
        if (!tracksSnapshot.isEmpty) {
            val batch = db.batch()
            for (doc in tracksSnapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.delete(playlistRef)
            batch.commit().await()
        } else {
            playlistRef.delete().await()
        }
    }

    private suspend fun upsertPlaylistInFirestore(
        uid: String,
        playlist: PlaylistEntity,
        localTracks: List<PlaylistTrackEntity>
    ) {
        val playlistRef = db.collection("users").document(uid)
            .collection("playlists").document(playlist.id)
        val existingDoc = playlistRef.get().await()
        if (existingDoc.exists()) {
            playlistRef.update(
                mapOf(
                    "name" to playlist.name.take(100),
                    "updatedAtMs" to playlist.updatedAtMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            playlistRef.set(
                mapOf(
                    "userId" to uid,
                    "name" to playlist.name.take(100),
                    "createdAtMs" to playlist.createdAtMs,
                    "updatedAtMs" to playlist.updatedAtMs,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val remoteTracksSnap = playlistRef.collection("tracks").get().await()
        val remoteTrackIds = remoteTracksSnap.documents.map { it.id }.toSet()
        val localTrackIds = localTracks.map { it.id }.toSet()

        // Delete tracks removed locally
        for (removedId in (remoteTrackIds - localTrackIds)) {
            playlistRef.collection("tracks").document(removedId).delete().await()
        }

        // Upsert local tracks
        for (track in localTracks) {
            val trackRef = playlistRef.collection("tracks").document(track.id)
            if (removedOrNew(track.id, remoteTrackIds)) {
                trackRef.set(
                    mapOf(
                        "userId" to uid,
                        "playlistId" to playlist.id,
                        "songKey" to track.songKey,
                        "mediaStoreId" to track.mediaStoreId,
                        "title" to track.title.take(300),
                        "artist" to track.artist.take(300),
                        "album" to track.album.take(300),
                        "durationMs" to track.durationMs,
                        "position" to track.position.coerceIn(0, 10000),
                        "addedAtMs" to track.addedAtMs,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                trackRef.update(
                    mapOf(
                        "position" to track.position.coerceIn(0, 10000),
                        "title" to track.title.take(300),
                        "artist" to track.artist.take(300),
                        "album" to track.album.take(300),
                        "durationMs" to track.durationMs,
                        "mediaStoreId" to track.mediaStoreId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
    }

    private fun removedOrNew(id: String, existingIds: Set<String>): Boolean = id !in existingIds

    private suspend fun syncPlaylistsBidirectional(uid: String) {
        val playlistsPath = "users/$uid/playlists"
        val remotePlaylistsSnap = try {
            db.collection("users").document(uid).collection("playlists").get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, playlistsPath)
            throw e
        }

        val remoteMap = remotePlaylistsSnap.documents.associateBy { it.id }
        val localPlaylists = playlistDao.getAllPlaylistsOnce()
        val localMap = localPlaylists.associateBy { it.id }

        // 1. Push local playlists that are missing remotely or newer locally
        for (localPlaylist in localPlaylists) {
            val remoteDoc = remoteMap[localPlaylist.id]
            val remoteUpdatedMs = remoteDoc?.getLong("updatedAtMs") ?: -1L
            if (remoteDoc == null || !localPlaylist.syncedToCloud || localPlaylist.updatedAtMs >= remoteUpdatedMs) {
                val tracks = playlistDao.getPlaylistTracksOnce(localPlaylist.id)
                upsertPlaylistInFirestore(uid, localPlaylist, tracks)
                playlistDao.markPlaylistSynced(localPlaylist.id)
            }
        }

        // 2. Pull remote playlists that are not locally deleted and are missing or newer remotely
        for ((remoteId, remoteDoc) in remoteMap) {
            if (playlistDao.isPlaylistDeleted(remoteId)) {
                // Respect local deletion tombstone: delete remotely instead of resurrecting
                deletePlaylistInFirestore(uid, remoteId)
                playlistDao.clearDeletedTombstone(remoteId)
                continue
            }

            val remoteName = remoteDoc.getString("name") ?: continue
            val remoteCreatedMs = remoteDoc.getLong("createdAtMs") ?: 0L
            val remoteUpdatedMs = remoteDoc.getLong("updatedAtMs") ?: 0L
            val localPlaylist = localMap[remoteId]

            if (localPlaylist == null || remoteUpdatedMs > localPlaylist.updatedAtMs) {
                val tracksSnap = db.collection("users").document(uid)
                    .collection("playlists").document(remoteId)
                    .collection("tracks").get().await()

                val pulledTracks = tracksSnap.documents.mapNotNull { doc ->
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val songKey = doc.getString("songKey") ?: return@mapNotNull null
                    PlaylistTrackEntity(
                        id = doc.id,
                        playlistId = remoteId,
                        songKey = songKey,
                        mediaStoreId = doc.getLong("mediaStoreId") ?: 0L,
                        title = title,
                        artist = doc.getString("artist") ?: "Unknown Artist",
                        album = doc.getString("album") ?: "Unknown Album",
                        durationMs = doc.getLong("durationMs") ?: 0L,
                        position = (doc.getLong("position") ?: 0L).toInt(),
                        addedAtMs = doc.getLong("addedAtMs") ?: 0L
                    )
                }.sortedBy { it.position }

                playlistDao.replacePlaylistAndTracksFromCloud(
                    playlist = PlaylistEntity(
                        id = remoteId,
                        name = remoteName,
                        createdAtMs = remoteCreatedMs,
                        updatedAtMs = remoteUpdatedMs,
                        syncedToCloud = true
                    ),
                    tracks = pulledTracks
                )
            }
        }
    }

    private suspend fun syncStatsBidirectional(uid: String) {
        val songStatsCol = db.collection("users").document(uid).collection("songStats")
        val remoteStatsSnap = try {
            songStatsCol.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$uid/songStats")
            throw e
        }

        val remoteStats = remoteStatsSnap.documents.associateBy { it.id }
        val localStats = statsDao.getAllSongStatsOnce().associateBy { it.songKey }

        val allKeys = (remoteStats.keys + localStats.keys).distinct()
        for (songKey in allKeys) {
            val local = localStats[songKey]
            val remoteDoc = remoteStats[songKey]

            val remotePlayCount = remoteDoc?.getLong("playCount") ?: 0L
            val remoteListenedMs = remoteDoc?.getLong("listenedMs") ?: 0L
            val remoteLastPlayed = remoteDoc?.getLong("lastPlayedAtMs") ?: 0L

            val mergedPlayCount = maxOf(local?.playCount ?: 0L, remotePlayCount)
            val mergedListenedMs = maxOf(local?.listenedMs ?: 0L, remoteListenedMs)
            val mergedLastPlayed = maxOf(local?.lastPlayedAtMs ?: 0L, remoteLastPlayed)

            val title = local?.title ?: remoteDoc?.getString("title") ?: "Unknown Track"
            val artist = local?.artist ?: remoteDoc?.getString("artist") ?: "Unknown Artist"
            val durationMs = local?.durationMs ?: (remoteDoc?.getLong("durationMs") ?: 0L)

            // Update local Room if remote had higher values or missing locally
            if (local == null || mergedPlayCount > local.playCount || mergedListenedMs > local.listenedMs) {
                statsDao.upsertSongStat(
                    SongStatEntity(
                        songKey = songKey,
                        title = title,
                        artist = artist,
                        durationMs = durationMs,
                        playCount = mergedPlayCount,
                        listenedMs = mergedListenedMs,
                        lastPlayedAtMs = mergedLastPlayed
                    )
                )
            }

            // Push to Firestore if remote is missing or smaller
            val statRef = songStatsCol.document(songKey)
            if (remoteDoc == null) {
                statRef.set(
                    mapOf(
                        "userId" to uid,
                        "songKey" to songKey,
                        "title" to title.take(300),
                        "artist" to artist.take(300),
                        "durationMs" to durationMs,
                        "playCount" to mergedPlayCount,
                        "listenedMs" to mergedListenedMs,
                        "lastPlayedAtMs" to mergedLastPlayed,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else if (mergedPlayCount > remotePlayCount || mergedListenedMs > remoteListenedMs) {
                statRef.update(
                    mapOf(
                        "title" to title.take(300),
                        "artist" to artist.take(300),
                        "durationMs" to durationMs,
                        "playCount" to mergedPlayCount,
                        "listenedMs" to mergedListenedMs,
                        "lastPlayedAtMs" to mergedLastPlayed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }

        // Sync aggregate summary
        val summaryRef = db.collection("users").document(uid)
            .collection("meta").document("listening_summary")
        val remoteSummary = summaryRef.get().await()
        val localSummary = statsDao.getListeningSummaryOnce()

        val remoteTotalMs = if (remoteSummary.exists()) remoteSummary.getLong("totalListeningMs") ?: 0L else 0L
        val remoteTotalPlays = if (remoteSummary.exists()) remoteSummary.getLong("totalPlays") ?: 0L else 0L

        val mergedTotalMs = maxOf(localSummary?.totalListeningMs ?: 0L, remoteTotalMs)
        val mergedTotalPlays = maxOf(localSummary?.totalPlays ?: 0L, remoteTotalPlays)

        if (localSummary == null || mergedTotalMs > localSummary.totalListeningMs || mergedTotalPlays > localSummary.totalPlays) {
            statsDao.upsertListeningSummary(
                ListeningSummaryEntity(
                    id = 1,
                    totalListeningMs = mergedTotalMs,
                    totalPlays = mergedTotalPlays,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }

        if (!remoteSummary.exists()) {
            summaryRef.set(
                mapOf(
                    "userId" to uid,
                    "totalListeningMs" to mergedTotalMs,
                    "totalPlays" to mergedTotalPlays,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else if (mergedTotalMs > remoteTotalMs || mergedTotalPlays > remoteTotalPlays) {
            summaryRef.update(
                mapOf(
                    "totalListeningMs" to mergedTotalMs,
                    "totalPlays" to mergedTotalPlays,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }
}
