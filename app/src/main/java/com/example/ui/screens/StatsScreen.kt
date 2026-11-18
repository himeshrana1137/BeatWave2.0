package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SongKeyUtil
import com.example.ui.components.AlbumArtwork
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceHighlight
import com.example.ui.viewmodel.StatsViewModel
import java.util.Locale

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    bottomContentPadding: Dp
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val totalHoursDecimal = state.totalListeningMs / 3_600_000.0
    val formattedHours = String.format(Locale.ROOT, "%.1f hrs", totalHoursDecimal)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 14.dp,
            bottom = bottomContentPadding + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Listening Stats",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Your local playback history & top tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Hero total listening time card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                    .testTag("stats_total_time_card"),
                color = SurfaceElevated,
                shape = RoundedCornerShape(22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    EmeraldPrimary.copy(alpha = 0.18f),
                                    CyanAccent.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AccessTime,
                                contentDescription = null,
                                tint = EmeraldPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Total Listening Time",
                                style = MaterialTheme.typography.titleSmall,
                                color = EmeraldPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = formattedHours,
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Exact duration: ${SongKeyUtil.formatListeningHours(state.totalListeningMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Secondary stat tiles
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatTile(
                    icon = Icons.Rounded.PlayCircleOutline,
                    label = "Total Plays",
                    value = state.totalPlays.toString(),
                    accent = CyanAccent,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    icon = Icons.Rounded.MusicNote,
                    label = "Unique Tracks",
                    value = state.uniqueSongsPlayed.toString(),
                    accent = AmberWarm,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                text = "Most Played Songs",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (state.topSongs.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No playback stats yet",
                    subtitle = "Play songs from your Library to see your most played tracks and listening hours."
                )
            }
        } else {
            itemsIndexed(state.topSongs, key = { _, item -> item.stat.songKey }) { index, item ->
                val rankColor = when (index) {
                    0 -> EmeraldPrimary
                    1 -> CyanAccent
                    2 -> AmberWarm
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = item.localSong != null) { viewModel.playStatSong(item) }
                        .testTag("stat_song_row_$index"),
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SurfaceHighlight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = rankColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        AlbumArtwork(
                            albumArtUri = item.localSong?.albumArtUri,
                            contentDescription = "Cover art for ${item.stat.title}",
                            modifier = Modifier.size(46.dp),
                            cornerRadius = 10.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.stat.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.stat.artist} • ${SongKeyUtil.formatListeningHours(item.stat.listenedMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            color = EmeraldPrimary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "${item.stat.playCount} plays",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.border(1.dp, BorderSubtle, RoundedCornerShape(18.dp)),
        color = SurfaceElevated,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
