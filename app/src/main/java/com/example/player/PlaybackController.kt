package com.example.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.local.BeatWaveDatabase
import com.example.data.model.RepeatMode
import com.example.data.model.Song
import com.example.data.remote.CloudSyncRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class PlaybackUiState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
)

class PlaybackController(
    private val context: Context,
    localDb: BeatWaveDatabase,
    private val cloudSync: CloudSyncRepository
) {
    private val statsDao = localDb.statsDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private var songCatalogByKey: Map<String, Song> = emptyMap()
    private var currentSessionSongKey: String? = null
    private var currentSessionPlayedMs: Long = 0L
    private var currentSessionCounted: Boolean = false
    private var lastPlaybackRealtimeMs: Long = 0L
    private var accumulatedListenMs: Long = 0L
    private var activeTrackedSong: Song? = null
    private var tickerJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val nowRealtime = SystemClock.elapsedRealtime()
            if (isPlaying) {
                val song = resolveCurrentSong()
                if (song != null && song.songKey != currentSessionSongKey) {
                    resetPlaySession(song.songKey)
                }
                activeTrackedSong = song
                lastPlaybackRealtimeMs = nowRealtime
                startTicker()
            } else {
                val song = activeTrackedSong ?: resolveCurrentSong()
                if (lastPlaybackRealtimeMs > 0L && song != null) {
                    val delta = (nowRealtime - lastPlaybackRealtimeMs).coerceIn(0L, 2000L)
                    currentSessionPlayedMs += delta
                    controller?.let { maybeIncrementPlayCount(song, it) }
                }
                flushListeningAccumulator(nowRealtime)
                lastPlaybackRealtimeMs = 0L
                stopTicker()
            }
            syncStateFromPlayer()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val nowRealtime = SystemClock.elapsedRealtime()
            val previousSong = activeTrackedSong
            if (lastPlaybackRealtimeMs > 0L && previousSong != null && controller?.isPlaying == true) {
                val delta = (nowRealtime - lastPlaybackRealtimeMs).coerceIn(0L, 2000L)
                currentSessionPlayedMs += delta
                controller?.let { maybeIncrementPlayCount(previousSong, it) }
            }
            flushListeningAccumulator(nowRealtime)
            val newSong = resolveCurrentSong()
            activeTrackedSong = newSong
            resetPlaySession(newSong?.songKey)
            lastPlaybackRealtimeMs = if (controller?.isPlaying == true) nowRealtime else 0L
            syncStateFromPlayer()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
                val looped = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                val restartedFromBeginning =
                    reason == Player.DISCONTINUITY_REASON_SEEK &&
                        newPosition.positionMs < 1500L &&
                        oldPosition.positionMs >= 1500L
                if (looped || restartedFromBeginning) {
                    resetPlaySession(resolveCurrentSong()?.songKey)
                    if (controller?.isPlaying == true) {
                        lastPlaybackRealtimeMs = SystemClock.elapsedRealtime()
                    }
                }
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            syncStateFromPlayer()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            syncStateFromPlayer()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncStateFromPlayer()
        }
    }

    fun initializeConnection() {
        if (controller != null || controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, BeatWavePlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val mc = future.get()
                controller = mc
                mc.addListener(playerListener)
                syncStateFromPlayer()
                val song = resolveCurrentSong()
                if (song != null && song.songKey != currentSessionSongKey) {
                    resetPlaySession(song.songKey)
                }
                if (mc.isPlaying) {
                    lastPlaybackRealtimeMs = SystemClock.elapsedRealtime()
                    activeTrackedSong = song
                    startTicker()
                }
            } catch (_: Exception) {
                controllerFuture = null
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private suspend fun awaitController(): MediaController? {
        controller?.let { return it }
        initializeConnection()
        val future = controllerFuture ?: return null
        return suspendCancellableCoroutine { cont ->
            future.addListener({
                val mc = try {
                    future.get()
                } catch (_: Exception) {
                    null
                }
                if (cont.isActive) cont.resume(mc)
            }, ContextCompat.getMainExecutor(context))
        }
    }

    fun updateCatalog(songs: List<Song>) {
        songCatalogByKey = songs.associateBy { it.songKey }
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        resetPlaySession(songs[safeIndex].songKey)
        scope.launch {
            val mc = awaitController() ?: return@launch
            updateCatalog(songs)
            val mediaItems = songs.map { it.toMediaItem() }
            mc.setMediaItems(mediaItems, safeIndex, 0L)
            mc.prepare()
            mc.play()
            _uiState.update {
                it.copy(
                    queue = songs,
                    currentIndex = safeIndex,
                    currentSong = songs[safeIndex],
                    durationMs = songs[safeIndex].durationMs,
                    positionMs = 0L
                )
            }
        }
    }

    fun updateActiveQueueOrder(orderedSongs: List<Song>) {
        val mc = controller ?: return
        if (orderedSongs.isEmpty()) return
        val currentSongKey = _uiState.value.currentSong?.songKey ?: return
        val currentPos = mc.currentPosition
        val wasPlaying = mc.isPlaying
        val newIndex = orderedSongs.indexOfFirst { it.songKey == currentSongKey }
        if (newIndex < 0) return

        // Update items around currently playing index smoothly without interrupting playback
        val currentMediaIndex = mc.currentMediaItemIndex
        val before = orderedSongs.subList(0, newIndex).map { it.toMediaItem() }
        val after = orderedSongs.subList(newIndex + 1, orderedSongs.size).map { it.toMediaItem() }

        if (currentMediaIndex >= 0 && currentMediaIndex < mc.mediaItemCount &&
            mc.getMediaItemAt(currentMediaIndex).mediaId == currentSongKey
        ) {
            if (currentMediaIndex > 0) {
                mc.removeMediaItems(0, currentMediaIndex)
            }
            if (mc.mediaItemCount > 1) {
                mc.removeMediaItems(1, mc.mediaItemCount)
            }
            if (before.isNotEmpty()) {
                mc.addMediaItems(0, before)
            }
            if (after.isNotEmpty()) {
                mc.addMediaItems(after)
            }
        } else {
            mc.setMediaItems(orderedSongs.map { it.toMediaItem() }, newIndex, currentPos)
            mc.prepare()
            if (wasPlaying) mc.play()
        }
        _uiState.update { it.copy(queue = orderedSongs, currentIndex = newIndex) }
    }

    fun togglePlayPause() {
        val mc = controller ?: return
        if (mc.isPlaying) {
            mc.pause()
        } else {
            if (mc.playbackState == Player.STATE_IDLE && mc.mediaItemCount > 0) {
                mc.prepare()
            }
            mc.play()
        }
    }

    fun skipToNext() {
        val mc = controller ?: return
        if (mc.hasNextMediaItem()) {
            mc.seekToNextMediaItem()
        } else if (mc.mediaItemCount > 0) {
            mc.seekTo(0, 0L)
        }
        mc.play()
    }

    fun skipToPrevious() {
        val mc = controller ?: return
        if (mc.currentPosition > 3000L) {
            mc.seekTo(0L)
            resetPlaySession(resolveCurrentSong()?.songKey)
        } else if (mc.hasPreviousMediaItem()) {
            mc.seekToPreviousMediaItem()
        } else {
            mc.seekTo(0L)
            resetPlaySession(resolveCurrentSong()?.songKey)
        }
    }

    fun seekTo(positionMs: Long) {
        val mc = controller ?: return
        val clamped = positionMs.coerceIn(0L, (mc.duration.takeIf { it > 0 } ?: _uiState.value.durationMs).coerceAtLeast(0L))
        mc.seekTo(clamped)
        _uiState.update { it.copy(positionMs = clamped) }
    }

    fun toggleShuffle() {
        val mc = controller ?: return
        mc.shuffleModeEnabled = !mc.shuffleModeEnabled
        syncStateFromPlayer()
    }

    fun cycleRepeatMode() {
        val mc = controller ?: return
        mc.repeatMode = when (mc.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        syncStateFromPlayer()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val mc = controller ?: break
                if (mc.isPlaying) {
                    val nowRealtime = SystemClock.elapsedRealtime()
                    if (lastPlaybackRealtimeMs > 0L) {
                        val delta = (nowRealtime - lastPlaybackRealtimeMs).coerceIn(0L, 2000L)
                        accumulatedListenMs += delta
                        currentSessionPlayedMs += delta
                    }
                    lastPlaybackRealtimeMs = nowRealtime

                    val song = activeTrackedSong ?: resolveCurrentSong()
                    if (song != null) {
                        maybeIncrementPlayCount(song, mc)
                    }

                    if (accumulatedListenMs >= 5000L) {
                        flushListeningAccumulator(nowRealtime)
                    }

                    val pos = mc.currentPosition.coerceAtLeast(0L)
                    val dur = mc.duration.takeIf { it > 0L } ?: _uiState.value.currentSong?.durationMs ?: 0L
                    _uiState.update { it.copy(positionMs = pos, durationMs = dur) }
                }
                delay(500L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun resetPlaySession(songKey: String?) {
        currentSessionSongKey = songKey
        currentSessionPlayedMs = 0L
        currentSessionCounted = false
    }

    private fun maybeIncrementPlayCount(song: Song, player: Player) {
        if (currentSessionCounted) return
        val durationMs = player.duration.takeIf { it > 0L } ?: song.durationMs
        if (durationMs <= 0L) return
        if (currentSessionPlayedMs >= durationMs / 2L) {
            currentSessionCounted = true
            recordPlayIncrement(song)
        }
    }

    private fun recordPlayIncrement(song: Song) {
        val now = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            statsDao.incrementPlayCount(song, now)
            if (Firebase.auth.currentUser != null) {
                cloudSync.syncAll()
            }
        }
    }

    private fun flushListeningAccumulator(nowRealtime: Long) {
        if (lastPlaybackRealtimeMs > 0L && controller?.isPlaying == true) {
            accumulatedListenMs += (nowRealtime - lastPlaybackRealtimeMs).coerceIn(0L, 5000L)
        }
        lastPlaybackRealtimeMs = nowRealtime
        val toSave = accumulatedListenMs
        val song = activeTrackedSong ?: resolveCurrentSong()
        if (toSave >= 1000L && song != null) {
            accumulatedListenMs = 0L
            val nowMs = System.currentTimeMillis()
            scope.launch(Dispatchers.IO) {
                statsDao.addListeningDuration(song, toSave, nowMs)
                if (Firebase.auth.currentUser != null) {
                    cloudSync.syncAll()
                }
            }
        }
    }

    private fun syncStateFromPlayer() {
        val mc = controller ?: return
        val current = resolveCurrentSong()
        val queue = buildQueueFromPlayer(mc)
        val idx = mc.currentMediaItemIndex
        val repeat = when (mc.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }
        val dur = mc.duration.takeIf { it > 0L } ?: current?.durationMs ?: 0L
        _uiState.update {
            it.copy(
                currentSong = current,
                queue = queue,
                currentIndex = idx,
                isPlaying = mc.isPlaying,
                positionMs = mc.currentPosition.coerceAtLeast(0L),
                durationMs = dur,
                shuffleEnabled = mc.shuffleModeEnabled,
                repeatMode = repeat
            )
        }
    }

    private fun resolveCurrentSong(): Song? {
        val mc = controller ?: return null
        val item = mc.currentMediaItem ?: return null
        return item.toSongOrNull()
    }

    private fun buildQueueFromPlayer(mc: MediaController): List<Song> {
        val list = mutableListOf<Song>()
        for (i in 0 until mc.mediaItemCount) {
            mc.getMediaItemAt(i).toSongOrNull()?.let { list.add(it) }
        }
        return list
    }

    private fun MediaItem.toSongOrNull(): Song? {
        songCatalogByKey[mediaId]?.let { return it }
        val meta = mediaMetadata
        val extras = meta.extras
        val mediaStoreId = extras?.getLong(EXTRA_MEDIA_STORE_ID, 0L) ?: 0L
        val durationMs = extras?.getLong(EXTRA_DURATION_MS, 0L) ?: 0L
        val albumId = extras?.getLong(EXTRA_ALBUM_ID, 0L) ?: 0L
        val contentUriStr = extras?.getString(EXTRA_CONTENT_URI) ?: localConfiguration?.uri?.toString()
        val contentUri = contentUriStr?.let { Uri.parse(it) } ?: return null
        return Song(
            mediaStoreId = mediaStoreId,
            songKey = mediaId,
            title = meta.title?.toString() ?: "Unknown Title",
            artist = meta.artist?.toString() ?: "Unknown Artist",
            album = meta.albumTitle?.toString() ?: "Unknown Album",
            albumId = albumId,
            durationMs = durationMs,
            contentUri = contentUri,
            albumArtUri = meta.artworkUri
        )
    }

    private fun Song.toMediaItem(): MediaItem {
        val extras = Bundle().apply {
            putLong(EXTRA_MEDIA_STORE_ID, mediaStoreId)
            putLong(EXTRA_DURATION_MS, durationMs)
            putLong(EXTRA_ALBUM_ID, albumId)
            putString(EXTRA_CONTENT_URI, contentUri.toString())
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(albumArtUri)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(songKey)
            .setUri(contentUri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun release() {
        flushListeningAccumulator(SystemClock.elapsedRealtime())
        stopTicker()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
    }

    private companion object {
        const val EXTRA_MEDIA_STORE_ID = "bw_media_store_id"
        const val EXTRA_DURATION_MS = "bw_duration_ms"
        const val EXTRA_ALBUM_ID = "bw_album_id"
        const val EXTRA_CONTENT_URI = "bw_content_uri"
    }
}
