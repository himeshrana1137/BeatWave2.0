package com.example.ui.viewmodel

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PlaylistRepository
import com.example.data.local.LocalMusicRepository
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackEntity
import com.example.data.model.Song
import com.example.player.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ResolvedPlaylistTrack(
    val entity: PlaylistTrackEntity,
    val song: Song
)

class PlaylistDetailViewModel(
    private val playlistId: String,
    private val playlistRepository: PlaylistRepository,
    localMusicRepository: LocalMusicRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    val playlist: StateFlow<PlaylistEntity?> = playlistRepository.observePlaylist(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val tracks: StateFlow<List<ResolvedPlaylistTrack>> = combine(
        playlistRepository.observePlaylistTracks(playlistId),
        localMusicRepository.songs
    ) { entities, localSongs ->
        val byKey = localSongs.associateBy { it.songKey }
        val byId = localSongs.associateBy { it.mediaStoreId }
        entities.map { entity ->
            val matched = byKey[entity.songKey] ?: byId[entity.mediaStoreId]
            val song = matched ?: Song(
                mediaStoreId = entity.mediaStoreId,
                songKey = entity.songKey,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                albumId = 0L,
                durationMs = entity.durationMs,
                contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    entity.mediaStoreId
                ),
                albumArtUri = null
            )
            ResolvedPlaylistTrack(entity = entity, song = song)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun playFromIndex(index: Int) {
        val songs = tracks.value.map { it.song }
        if (songs.isNotEmpty()) {
            playbackController.playQueue(songs, index)
        }
    }

    fun persistOrder(reorderedEntities: List<PlaylistTrackEntity>) {
        viewModelScope.launch {
            playlistRepository.reorderPlaylistTracks(playlistId, reorderedEntities)
            // Keep active queue synced with the new custom order if playing from this playlist
            val currentKey = playbackController.uiState.value.currentSong?.songKey
            if (currentKey != null && reorderedEntities.any { it.songKey == currentKey }) {
                val orderedSongs = reorderedEntities.mapNotNull { entity ->
                    tracks.value.firstOrNull { it.entity.id == entity.id }?.song
                }
                if (orderedSongs.isNotEmpty()) {
                    playbackController.updateActiveQueueOrder(orderedSongs)
                }
            }
        }
    }

    fun removeTrack(trackId: String) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun renamePlaylist(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, trimmed)
        }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
            onDeleted()
        }
    }

    class Factory(
        private val playlistId: String,
        private val playlistRepository: PlaylistRepository,
        private val localMusicRepository: LocalMusicRepository,
        private val playbackController: PlaybackController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlaylistDetailViewModel(
                playlistId,
                playlistRepository,
                localMusicRepository,
                playbackController
            ) as T
        }
    }
}
