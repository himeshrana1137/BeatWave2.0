package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.PlaylistWithCount
import com.example.data.model.RepeatMode
import com.example.data.model.SongKeyUtil
import com.example.player.PlaybackUiState
import com.example.ui.components.AlbumArtwork
import com.example.ui.components.PlaylistPickerSheet
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceHighlight

@Composable
fun PlayerScreen(
    playbackState: PlaybackUiState,
    playlists: List<PlaylistWithCount>,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onAddCurrentSongToPlaylist: (String) -> Unit,
    onCreatePlaylistAndAdd: (String) -> Unit
) {
    BackHandler(onBack = onCollapse)

    val song = playbackState.currentSong ?: return
    val durationMs = playbackState.durationMs.coerceAtLeast(1L)

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    val displayPositionMs = if (isScrubbing) {
        (scrubPosition * durationMs).toLong()
    } else {
        playbackState.positionMs.coerceIn(0L, durationMs)
    }
    val sliderFraction = if (isScrubbing) {
        scrubPosition
    } else {
        (displayPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    val artworkScale by animateFloatAsState(
        targetValue = if (playbackState.isPlaying) 1f else 0.92f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "artworkScale"
    )

    var showPlaylistSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(ObsidianBlack)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(EmeraldPrimary.copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.32f),
                        radius = size.minDimension * 0.72f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(CyanAccent.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.78f),
                        radius = size.minDimension * 0.5f
                    )
                )
            }
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("player_collapse_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Minimize player",
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { showPlaylistSheet = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("player_add_to_playlist_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = "Add current song to playlist"
                    )
                }
            }

            // Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtwork(
                    albumArtUri = song.albumArtUri,
                    contentDescription = "Album art for ${song.title}",
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .aspectRatio(1f)
                        .scale(artworkScale)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp)),
                    cornerRadius = 24.dp
                )
            }

            // Track Info + Seekbar + Controls
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(18.dp))

                Slider(
                    value = sliderFraction,
                    onValueChange = { value ->
                        isScrubbing = true
                        scrubPosition = value
                    },
                    onValueChangeFinished = {
                        onSeekTo((scrubPosition * durationMs).toLong())
                        isScrubbing = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = EmeraldPrimary,
                        activeTrackColor = EmeraldPrimary,
                        inactiveTrackColor = SurfaceHighlight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_seek_bar")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = SongKeyUtil.formatDuration(displayPositionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = SongKeyUtil.formatDuration(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Main transport row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = if (playbackState.shuffleEnabled) "Shuffle on" else "Shuffle off",
                            tint = if (playbackState.shuffleEnabled) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous track",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color(0xFF051A0C)
                        ),
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next track",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    IconButton(
                        onClick = onCycleRepeat,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("repeat_button")
                    ) {
                        val icon = when (playbackState.repeatMode) {
                            RepeatMode.ONE -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        }
                        val tint = if (playbackState.repeatMode == RepeatMode.OFF) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            EmeraldPrimary
                        }
                        val desc = when (playbackState.repeatMode) {
                            RepeatMode.OFF -> "Repeat off"
                            RepeatMode.ONE -> "Repeat one"
                            RepeatMode.ALL -> "Repeat all"
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = desc,
                            tint = tint,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                if (playbackState.queue.size > 1) {
                    Surface(
                        color = SurfaceHighlight.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Queue",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldPrimary
                            )
                            Text(
                                text = "Track ${(playbackState.currentIndex + 1).coerceAtLeast(1)} of ${playbackState.queue.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPlaylistSheet) {
        PlaylistPickerSheet(
            playlists = playlists,
            selectedCount = 1,
            onSelectPlaylist = onAddCurrentSongToPlaylist,
            onCreateNewPlaylist = onCreatePlaylistAndAdd,
            onDismiss = { showPlaylistSheet = false }
        )
    }
}
