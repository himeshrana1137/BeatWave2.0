package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        DeletedPlaylistEntity::class,
        SongStatEntity::class,
        ListeningSummaryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BeatWaveDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun statsDao(): StatsDao

    companion object {
        @Volatile
        private var INSTANCE: BeatWaveDatabase? = null

        fun getInstance(context: Context): BeatWaveDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BeatWaveDatabase::class.java,
                    "beatwave_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
