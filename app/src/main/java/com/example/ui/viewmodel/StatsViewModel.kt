package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PlaylistRepository
import com.example.data.local.ListeningSummaryEntity
import com.example.data.local.LocalMusicRepository
import com.example.data.local.SongStatEntity
import com.example.data.model.Song
import com.example.player.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TopSongUiItem(
    val stat: SongStatEntity,
    val localSong: Song?
)

data class StatsUiState(
    val totalListeningMs: Long = 0L,
    val totalPlays: Long = 0L,
    val uniqueSongsPlayed: Int = 0,
    val topSongs: List<TopSongUiItem> = emptyList()
)

class StatsViewModel(
    playlistRepository: PlaylistRepository,
    localMusicRepository: LocalMusicRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = combine(
        playlistRepository.observeListeningSummary(),
        playlistRepository.observeTopSongStats(),
        localMusicRepository.songs
    ) { summary: ListeningSummaryEntity?, stats: List<SongStatEntity>, songs: List<Song> ->
        val songsByKey = songs.associateBy { it.songKey }
        val topItems = stats.map { stat ->
            TopSongUiItem(stat = stat, localSong = songsByKey[stat.songKey])
        }
        StatsUiState(
            totalListeningMs = summary?.totalListeningMs ?: stats.sumOf { it.listenedMs },
            totalPlays = summary?.totalPlays ?: stats.sumOf { it.playCount },
            uniqueSongsPlayed = stats.size,
            topSongs = topItems
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StatsUiState())

    fun playStatSong(item: TopSongUiItem) {
        val song = item.localSong ?: return
        val playableList = uiState.value.topSongs.mapNotNull { it.localSong }
        val idx = playableList.indexOfFirst { it.songKey == song.songKey }.coerceAtLeast(0)
        if (playableList.isNotEmpty()) {
            playbackController.playQueue(playableList, idx)
        }
    }

    class Factory(
        private val playlistRepository: PlaylistRepository,
        private val localMusicRepository: LocalMusicRepository,
        private val playbackController: PlaybackController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatsViewModel(playlistRepository, localMusicRepository, playbackController) as T
        }
    }
}
