package com.example.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.data.model.AlbumGroup
import com.example.data.model.ArtistGroup
import com.example.data.model.Song
import com.example.data.model.SongKeyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LocalMusicRepository(private val context: Context) {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    suspend fun scanDeviceAudio() {
        _isScanning.value = true
        try {
            val scanned = withContext(Dispatchers.IO) {
                queryMediaStoreAudio()
            }
            _songs.value = scanned
        } finally {
            _isScanning.value = false
        }
    }

    private fun queryMediaStoreAudio(): List<Song> {
        val result = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 1000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown Title"
                val rawArtist = cursor.getString(artistCol)
                val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") "Unknown Artist" else rawArtist
                val album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "Unknown Album"
                val albumId = cursor.getLong(albumIdCol)
                val durationMs = cursor.getLong(durationCol).coerceAtLeast(0L)
                val dateAdded = cursor.getLong(dateAddedCol)

                val contentUri = ContentUris.withAppendedId(collection, id)
                val albumArtUri = if (albumId > 0L) ContentUris.withAppendedId(albumArtBaseUri, albumId) else null
                val songKey = SongKeyUtil.computeSongKey(title, artist, durationMs)

                result.add(
                    Song(
                        mediaStoreId = id,
                        songKey = songKey,
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        durationMs = durationMs,
                        contentUri = contentUri,
                        albumArtUri = albumArtUri,
                        dateAdded = dateAdded
                    )
                )
            }
        }
        return result
    }

    fun groupByAlbums(songs: List<Song>): List<AlbumGroup> {
        return songs.groupBy { it.albumId to it.album }
            .map { (key, albumSongs) ->
                AlbumGroup(
                    albumId = key.first,
                    title = key.second,
                    artist = albumSongs.firstOrNull()?.artist ?: "Unknown Artist",
                    albumArtUri = albumSongs.firstOrNull()?.albumArtUri,
                    songs = albumSongs
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    fun groupByArtists(songs: List<Song>): List<ArtistGroup> {
        return songs.groupBy { it.artist }
            .map { (artistName, artistSongs) ->
                ArtistGroup(
                    name = artistName,
                    albumCount = artistSongs.map { it.albumId }.distinct().size,
                    songs = artistSongs
                )
            }
            .sortedBy { it.name.lowercase() }
    }
}
