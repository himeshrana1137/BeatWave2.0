package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PlaylistRepository
import com.example.data.local.LocalMusicRepository
import com.example.data.local.PlaylistWithCount
import com.example.data.model.AlbumGroup
import com.example.data.model.ArtistGroup
import com.example.data.model.Song
import com.example.player.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab {
    SONGS, ALBUMS, ARTISTS, PLAYLISTS
}

class LibraryViewModel(
    private val localMusicRepository: LocalMusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(LibraryTab.SONGS)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSongKeys = MutableStateFlow<Set<String>>(emptySet())
    val selectedSongKeys: StateFlow<Set<String>> = _selectedSongKeys.asStateFlow()

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    val isScanning: StateFlow<Boolean> = localMusicRepository.isScanning

    val allSongs: StateFlow<List<Song>> = localMusicRepository.songs

    val filteredSongs: StateFlow<List<Song>> = combine(
        localMusicRepository.songs,
        _searchQuery
    ) { songs, query ->
        playbackController.updateCatalog(songs)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            songs
        } else {
            songs.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.artist.contains(trimmed, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val filteredAlbums: StateFlow<List<AlbumGroup>> = combine(
        localMusicRepository.songs,
        _searchQuery
    ) { songs, query ->
        val trimmed = query.trim()
        val albums = localMusicRepository.groupByAlbums(songs)
        if (trimmed.isEmpty()) albums
        else albums.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                it.artist.contains(trimmed, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val filteredArtists: StateFlow<List<ArtistGroup>> = combine(
        localMusicRepository.songs,
        _searchQuery
    ) { songs, query ->
        val trimmed = query.trim()
        val artists = localMusicRepository.groupByArtists(songs)
        if (trimmed.isEmpty()) artists
        else artists.filter { it.name.contains(trimmed, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val playlists: StateFlow<List<PlaylistWithCount>> = combine(
        playlistRepository.observePlaylists(),
        _searchQuery
    ) { list, query ->
        val trimmed = query.trim()
        if (trimmed.isEmpty()) list
        else list.filter { it.name.contains(trimmed, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun onPermissionUpdated(granted: Boolean) {
        _hasAudioPermission.value = granted
        if (granted) {
            rescanLibrary()
        }
    }

    fun rescanLibrary() {
        viewModelScope.launch {
            localMusicRepository.scanDeviceAudio()
        }
    }

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun enterMultiSelect(songKey: String) {
        _selectedSongKeys.update { it + songKey }
    }

    fun toggleSongSelection(songKey: String) {
        _selectedSongKeys.update { current ->
            if (songKey in current) current - songKey else current + songKey
        }
    }

    fun selectAllFiltered() {
        val visibleKeys = filteredSongs.value.map { it.songKey }.toSet()
        _selectedSongKeys.value = visibleKeys
    }

    fun clearSelection() {
        _selectedSongKeys.value = emptySet()
    }

    fun playSongFromList(songs: List<Song>, index: Int) {
        playbackController.playQueue(songs, index)
    }

    fun createPlaylist(name: String, alsoAddSelectedSongs: Boolean = false) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val newId = playlistRepository.createPlaylist(trimmed)
            if (alsoAddSelectedSongs) {
                val keys = _selectedSongKeys.value
                val selectedSongs = allSongs.value.filter { it.songKey in keys }
                playlistRepository.addSongsToPlaylist(newId, selectedSongs)
                clearSelection()
            }
        }
    }

    fun addSelectedSongsToPlaylist(playlistId: String) {
        val keys = _selectedSongKeys.value
        val selectedSongs = allSongs.value.filter { it.songKey in keys }
        if (selectedSongs.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.addSongsToPlaylist(playlistId, selectedSongs)
            clearSelection()
        }
    }

    fun addSingleSongToPlaylist(playlistId: String, song: Song) {
        viewModelScope.launch {
            playlistRepository.addSongsToPlaylist(playlistId, listOf(song))
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, trimmed)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    class Factory(
        private val localMusicRepository: LocalMusicRepository,
        private val playlistRepository: PlaylistRepository,
        private val playbackController: PlaybackController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(localMusicRepository, playlistRepository, playbackController) as T
        }
    }
}
