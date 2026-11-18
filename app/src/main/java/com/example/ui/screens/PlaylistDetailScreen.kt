package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.SongKeyUtil
import com.example.ui.components.AlbumArtwork
import com.example.ui.components.DeletePlaylistDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PlaylistNameDialog
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceHighlight
import com.example.ui.viewmodel.PlaylistDetailViewModel
import com.example.ui.viewmodel.ResolvedPlaylistTrack

@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel,
    currentPlayingSongKey: String?,
    onBack: () -> Unit,
    bottomContentPadding: Dp
) {
    BackHandler(onBack = onBack)

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val persistedTracks by viewModel.tracks.collectAsStateWithLifecycle()

    var localTracks by remember { mutableStateOf(persistedTracks) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(persistedTracks, draggingIndex) {
        if (draggingIndex < 0) {
            localTracks = persistedTracks
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val rowHeightPx = with(LocalDensity.current) { 74.dp.toPx() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("playlist_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to library"
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = playlist?.name ?: "Playlist",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("playlist_detail_menu")
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist actions")
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
                            showRenameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_playlist)) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }

        // Playlist Hero Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp)),
            color = SurfaceElevated,
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(EmeraldPrimary.copy(alpha = 0.35f), CyanAccent.copy(alpha = 0.25f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist?.name ?: "Playlist",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${localTracks.size} songs • Drag handle to reorder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (localTracks.isNotEmpty()) {
                    Button(
                        onClick = { viewModel.playFromIndex(0) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.testTag("playlist_play_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play playlist",
                            tint = Color(0xFF051A0C)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play", color = Color(0xFF051A0C), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (localTracks.isEmpty()) {
            EmptyStateCard(
                title = "Playlist is empty",
                subtitle = "Go to the Songs tab and long-press any tracks to add them here."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = bottomContentPadding + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(localTracks, key = { _, item -> item.entity.id }) { index, item ->
                    val isDragging = draggingIndex == index
                    val isCurrent = item.song.songKey == currentPlayingSongKey
                    DraggablePlaylistTrackRow(
                        item = item,
                        positionNumber = index + 1,
                        isPlayingCurrent = isCurrent,
                        isDragging = isDragging,
                        translationY = if (isDragging) dragOffsetY else 0f,
                        onPlay = { viewModel.playFromIndex(index) },
                        onRemove = { viewModel.removeTrack(item.entity.id) },
                        onDragStart = {
                            draggingIndex = index
                            dragOffsetY = 0f
                        },
                        onDragDelta = { deltaY ->
                            if (draggingIndex < 0) return@DraggablePlaylistTrackRow
                            dragOffsetY += deltaY
                            val currentIdx = draggingIndex
                            val targetIdx = when {
                                dragOffsetY > rowHeightPx * 0.55f && currentIdx < localTracks.lastIndex -> currentIdx + 1
                                dragOffsetY < -rowHeightPx * 0.55f && currentIdx > 0 -> currentIdx - 1
                                else -> currentIdx
                            }
                            if (targetIdx != currentIdx) {
                                val mutable = localTracks.toMutableList()
                                val moved = mutable.removeAt(currentIdx)
                                mutable.add(targetIdx, moved)
                                localTracks = mutable
                                dragOffsetY -= (targetIdx - currentIdx) * rowHeightPx
                                draggingIndex = targetIdx
                            }
                        },
                        onDragEnd = {
                            draggingIndex = -1
                            dragOffsetY = 0f
                            viewModel.persistOrder(localTracks.map { it.entity })
                        }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.rename_playlist),
            initialName = playlist?.name.orEmpty(),
            confirmLabel = "Save",
            onConfirm = viewModel::renamePlaylist,
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog) {
        DeletePlaylistDialog(
            playlistName = playlist?.name.orEmpty(),
            onConfirmDelete = {
                viewModel.deletePlaylist(onDeleted = onBack)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun DraggablePlaylistTrackRow(
    item: ResolvedPlaylistTrack,
    positionNumber: Int,
    isPlayingCurrent: Boolean,
    isDragging: Boolean,
    translationY: Float,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                this.translationY = translationY
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onPlay),
        color = when {
            isDragging -> SurfaceHighlight
            isPlayingCurrent -> SurfaceElevated
            else -> SurfaceElevated.copy(alpha = 0.7f)
        },
        tonalElevation = if (isDragging) 8.dp else 1.dp,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .pointerInput(item.entity.id) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
                    .testTag("drag_handle_${item.entity.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = "Drag to reorder ${item.song.title}",
                    tint = if (isDragging) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "$positionNumber",
                style = MaterialTheme.typography.labelMedium,
                color = if (isPlayingCurrent) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )

            AlbumArtwork(
                albumArtUri = item.song.albumArtUri,
                contentDescription = "Cover art for ${item.song.title}",
                modifier = Modifier.size(44.dp),
                cornerRadius = 8.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.song.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isPlayingCurrent) EmeraldPrimary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.song.artist} • ${SongKeyUtil.formatDuration(item.song.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove ${item.song.title} from playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
