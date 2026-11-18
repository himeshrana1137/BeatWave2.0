package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.PlaylistWithCount
import com.example.data.model.AlbumGroup
import com.example.data.model.ArtistGroup
import com.example.data.model.Song
import com.example.data.model.SongKeyUtil
import com.example.ui.components.AlbumArtwork
import com.example.ui.components.DeletePlaylistDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PlaylistNameDialog
import com.example.ui.components.PlaylistPickerSheet
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceHighlight
import com.example.ui.viewmodel.LibraryTab
import com.example.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    currentPlayingSongKey: String?,
    onOpenPlaylist: (String) -> Unit,
    bottomContentPadding: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    val audioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    val hasAudioPermission by viewModel.hasAudioPermission.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val songs by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val albums by viewModel.filteredAlbums.collectAsStateWithLifecycle()
    val artists by viewModel.filteredArtists.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedSongKeys by viewModel.selectedSongKeys.collectAsStateWithLifecycle()

    val isMultiSelectMode = selectedSongKeys.isNotEmpty()
    BackHandler(enabled = isMultiSelectMode) {
        viewModel.clearSelection()
    }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var renameTargetPlaylist by remember { mutableStateOf<PlaylistWithCount?>(null) }
    var deleteTargetPlaylist by remember { mutableStateOf<PlaylistWithCount?>(null) }
    var showMultiAddSheet by remember { mutableStateOf(false) }
    var singleAddSong by remember { mutableStateOf<Song?>(null) }
    var activeAlbumGroup by remember { mutableStateOf<AlbumGroup?>(null) }
    var activeArtistGroup by remember { mutableStateOf<ArtistGroup?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionUpdated(granted)
        if (granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun checkAndSyncPermission() {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionUpdated(granted)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionUpdated(granted)
        if (!granted) {
            audioPermissionLauncher.launch(audioPermission)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        checkAndSyncPermission()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isMultiSelectMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("clear_selection_button")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Exit multi-select")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${selectedSongKeys.size} selected",
                            style = MaterialTheme.typography.titleLarge,
                            color = EmeraldPrimary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.selectAllFiltered() },
                            modifier = Modifier.testTag("select_all_button")
                        ) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = "Select all songs")
                        }
                        Button(
                            onClick = { showMultiAddSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            modifier = Modifier.testTag("add_selected_to_playlist_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = null,
                                tint = Color(0xFF051A0C)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add", color = Color(0xFF051A0C), fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${songs.size} local tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        if (hasAudioPermission) {
                            IconButton(
                                onClick = { viewModel.rescanLibrary() },
                                modifier = Modifier.testTag("rescan_library_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Rescan device audio",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceElevated,
                    unfocusedContainerColor = SurfaceElevated,
                    focusedBorderColor = EmeraldPrimary,
                    unfocusedBorderColor = BorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("library_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4 Library Tabs: Songs, Albums, Artists, Playlists
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    LibraryTab.SONGS to R.string.tab_songs,
                    LibraryTab.ALBUMS to R.string.tab_albums,
                    LibraryTab.ARTISTS to R.string.tab_artists,
                    LibraryTab.PLAYLISTS to R.string.tab_playlists
                )
                items(tabs) { (tab, labelRes) ->
                    val selected = selectedTab == tab
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectTab(tab) },
                        label = {
                            Text(
                                text = stringResource(labelRes),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary,
                            selectedLabelColor = Color(0xFF051A0C),
                            containerColor = SurfaceElevated,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = BorderSubtle,
                            selectedBorderColor = EmeraldPrimary
                        ),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }

            AnimatedVisibility(visible = isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    color = EmeraldPrimary,
                    trackColor = SurfaceHighlight
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tab Content
            when (selectedTab) {
                LibraryTab.SONGS -> {
                    if (!hasAudioPermission) {
                        AudioPermissionCard(
                            onGrantClick = { audioPermissionLauncher.launch(audioPermission) },
                            modifier = Modifier.padding(16.dp)
                        )
                    } else if (songs.isEmpty() && !isScanning) {
                        EmptyStateCard(
                            title = if (searchQuery.isBlank()) "No local songs found" else "No matching songs",
                            subtitle = if (searchQuery.isBlank()) {
                                "Add MP3, FLAC, or WAV files to your device Music folder and tap refresh."
                            } else {
                                "Try a different title or artist name."
                            },
                            actionLabel = if (searchQuery.isBlank()) "Rescan Audio" else null,
                            onAction = if (searchQuery.isBlank()) ({ viewModel.rescanLibrary() }) else null
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                bottom = bottomContentPadding + 16.dp
                            )
                        ) {
                            itemsIndexed(songs, key = { _, song -> song.mediaStoreId }) { index, song ->
                                val isSelected = song.songKey in selectedSongKeys
                                val isCurrent = song.songKey == currentPlayingSongKey
                                SongRowItem(
                                    song = song,
                                    isPlayingCurrent = isCurrent,
                                    isMultiSelectMode = isMultiSelectMode,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            viewModel.toggleSongSelection(song.songKey)
                                        } else {
                                            viewModel.playSongFromList(songs, index)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.enterMultiSelect(song.songKey)
                                    },
                                    onAddToPlaylist = { singleAddSong = song }
                                )
                            }
                        }
                    }
                }

                LibraryTab.ALBUMS -> {
                    if (!hasAudioPermission) {
                        AudioPermissionCard(
                            onGrantClick = { audioPermissionLauncher.launch(audioPermission) },
                            modifier = Modifier.padding(16.dp)
                        )
                    } else if (albums.isEmpty()) {
                        EmptyStateCard(
                            title = "No albums found",
                            subtitle = "Albums appear automatically from your local audio metadata."
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 156.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = bottomContentPadding + 16.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(albums, key = { "${it.albumId}_${it.title}" }) { album ->
                                AlbumGridCard(
                                    album = album,
                                    onClick = { activeAlbumGroup = album }
                                )
                            }
                        }
                    }
                }

                LibraryTab.ARTISTS -> {
                    if (!hasAudioPermission) {
                        AudioPermissionCard(
                            onGrantClick = { audioPermissionLauncher.launch(audioPermission) },
                            modifier = Modifier.padding(16.dp)
                        )
                    } else if (artists.isEmpty()) {
                        EmptyStateCard(
                            title = "No artists found",
                            subtitle = "Artists appear automatically from your local audio files."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = bottomContentPadding + 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(artists, key = { it.name }) { artist ->
                                ArtistRowCard(
                                    artist = artist,
                                    onClick = { activeArtistGroup = artist }
                                )
                            }
                        }
                    }
                }

                LibraryTab.PLAYLISTS -> {
                    if (playlists.isEmpty()) {
                        EmptyStateCard(
                            title = stringResource(R.string.no_playlists_title),
                            subtitle = stringResource(R.string.no_playlists_subtitle),
                            actionLabel = stringResource(R.string.create_playlist),
                            onAction = { showCreatePlaylistDialog = true }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 6.dp,
                                bottom = bottomContentPadding + 80.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(playlists, key = { it.id }) { playlist ->
                                PlaylistRowCard(
                                    playlist = playlist,
                                    onOpen = { onOpenPlaylist(playlist.id) },
                                    onRename = { renameTargetPlaylist = playlist },
                                    onDelete = { deleteTargetPlaylist = playlist }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Create playlist FAB when on Playlists tab
        if (selectedTab == LibraryTab.PLAYLISTS && playlists.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showCreatePlaylistDialog = true },
                containerColor = EmeraldPrimary,
                contentColor = Color(0xFF051A0C),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = bottomContentPadding + 16.dp)
                    .testTag("create_playlist_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create playlist")
            }
        }
    }

    if (showCreatePlaylistDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.create_playlist),
            confirmLabel = "Create",
            onConfirm = { name -> viewModel.createPlaylist(name) },
            onDismiss = { showCreatePlaylistDialog = false }
        )
    }

    renameTargetPlaylist?.let { target ->
        PlaylistNameDialog(
            title = stringResource(R.string.rename_playlist),
            initialName = target.name,
            confirmLabel = "Save",
            onConfirm = { newName -> viewModel.renamePlaylist(target.id, newName) },
            onDismiss = { renameTargetPlaylist = null }
        )
    }

    deleteTargetPlaylist?.let { target ->
        DeletePlaylistDialog(
            playlistName = target.name,
            onConfirmDelete = { viewModel.deletePlaylist(target.id) },
            onDismiss = { deleteTargetPlaylist = null }
        )
    }

    if (showMultiAddSheet) {
        PlaylistPickerSheet(
            playlists = playlists,
            selectedCount = selectedSongKeys.size,
            onSelectPlaylist = { playlistId -> viewModel.addSelectedSongsToPlaylist(playlistId) },
            onCreateNewPlaylist = { name -> viewModel.createPlaylist(name, alsoAddSelectedSongs = true) },
            onDismiss = { showMultiAddSheet = false }
        )
    }

    singleAddSong?.let { song ->
        PlaylistPickerSheet(
            playlists = playlists,
            selectedCount = 1,
            onSelectPlaylist = { playlistId -> viewModel.addSingleSongToPlaylist(playlistId, song) },
            onCreateNewPlaylist = { name ->
                viewModel.enterMultiSelect(song.songKey)
                viewModel.createPlaylist(name, alsoAddSelectedSongs = true)
            },
            onDismiss = { singleAddSong = null }
        )
    }

    activeAlbumGroup?.let { album ->
        ModalBottomSheet(
            onDismissRequest = { activeAlbumGroup = null },
            containerColor = SurfaceElevated
        ) {
            GroupSongsSheetContent(
                title = album.title,
                subtitle = "${album.artist} • ${album.songs.size} songs",
                songs = album.songs,
                currentPlayingSongKey = currentPlayingSongKey,
                onPlayAll = {
                    viewModel.playSongFromList(album.songs, 0)
                    activeAlbumGroup = null
                },
                onPlayIndex = { idx ->
                    viewModel.playSongFromList(album.songs, idx)
                    activeAlbumGroup = null
                },
                onAddToPlaylist = { song -> singleAddSong = song }
            )
        }
    }

    activeArtistGroup?.let { artist ->
        ModalBottomSheet(
            onDismissRequest = { activeArtistGroup = null },
            containerColor = SurfaceElevated
        ) {
            GroupSongsSheetContent(
                title = artist.name,
                subtitle = "${artist.albumCount} albums • ${artist.songs.size} songs",
                songs = artist.songs,
                currentPlayingSongKey = currentPlayingSongKey,
                onPlayAll = {
                    viewModel.playSongFromList(artist.songs, 0)
                    activeArtistGroup = null
                },
                onPlayIndex = { idx ->
                    viewModel.playSongFromList(artist.songs, idx)
                    activeArtistGroup = null
                },
                onAddToPlaylist = { song -> singleAddSong = song }
            )
        }
    }
}

@Composable
private fun AudioPermissionCard(
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, EmeraldPrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        color = SurfaceElevated,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(EmeraldPrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.permission_rationale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("grant_audio_permission_button")
            ) {
                Text(
                    text = stringResource(R.string.grant_permission),
                    color = Color(0xFF051A0C),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRowItem(
    song: Song,
    isPlayingCurrent: Boolean,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val bgColor = when {
        isSelected -> EmeraldPrimary.copy(alpha = 0.14f)
        isPlayingCurrent -> SurfaceElevated
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("song_row_${song.mediaStoreId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(24.dp)
            )
        }
        AlbumArtwork(
            albumArtUri = song.albumArtUri,
            contentDescription = "Cover art for ${song.title}",
            modifier = Modifier.size(50.dp),
            cornerRadius = 10.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlayingCurrent) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Currently playing",
                        tint = EmeraldPrimary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp)
                    )
                }
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPlayingCurrent) EmeraldPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${song.artist} • ${SongKeyUtil.formatDuration(song.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!isMultiSelectMode) {
            IconButton(
                onClick = onAddToPlaylist,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("add_song_button_${song.mediaStoreId}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = "Add ${song.title} to playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlbumGridCard(
    album: AlbumGroup,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = SurfaceElevated,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            AlbumArtwork(
                albumArtUri = album.albumArtUri,
                contentDescription = "Album art for ${album.title}",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                cornerRadius = 12.dp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.artist} • ${album.songs.size} songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistRowCard(
    artist: ArtistGroup,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = SurfaceElevated,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(EmeraldPrimary.copy(alpha = 0.35f), CyanAccent.copy(alpha = 0.25f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${artist.albumCount} albums • ${artist.songs.size} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlaylistRowCard(
    playlist: PlaylistWithCount,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .testTag("playlist_card_${playlist.id}"),
        color = SurfaceElevated,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(EmeraldPrimary.copy(alpha = 0.3f), CyanAccent.copy(alpha = 0.18f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.trackCount} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("playlist_menu_${playlist.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Playlist options"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename_playlist)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_playlist)) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupSongsSheetContent(
    title: String,
    subtitle: String,
    songs: List<Song>,
    currentPlayingSongKey: String?,
    onPlayAll: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onPlayAll,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFF051A0C))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play All", color = Color(0xFF051A0C), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.height(340.dp)) {
            itemsIndexed(songs, key = { _, s -> s.mediaStoreId }) { idx, song ->
                SongRowItem(
                    song = song,
                    isPlayingCurrent = song.songKey == currentPlayingSongKey,
                    isMultiSelectMode = false,
                    isSelected = false,
                    onClick = { onPlayIndex(idx) },
                    onLongClick = {},
                    onAddToPlaylist = { onAddToPlaylist(song) }
                )
            }
        }
    }
}
