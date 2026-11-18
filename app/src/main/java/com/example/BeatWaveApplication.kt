package com.example

import android.app.Application
import com.example.data.PlaylistRepository
import com.example.data.local.BeatWaveDatabase
import com.example.data.local.LocalMusicRepository
import com.example.data.local.SessionPreferences
import com.example.data.remote.CloudSyncRepository
import com.example.player.PlaybackController

class AppContainer(application: Application) {
    val database: BeatWaveDatabase by lazy { BeatWaveDatabase.getInstance(application) }
    val sessionPreferences: SessionPreferences by lazy { SessionPreferences(application) }
    val localMusicRepository: LocalMusicRepository by lazy { LocalMusicRepository(application) }
    val cloudSyncRepository: CloudSyncRepository by lazy {
        CloudSyncRepository(
            context = application,
            localDb = database,
            prefs = sessionPreferences
        )
    }
    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(
            localDb = database,
            cloudSync = cloudSyncRepository
        )
    }
    val playbackController: PlaybackController by lazy {
        PlaybackController(
            context = application,
            localDb = database,
            cloudSync = cloudSyncRepository
        )
    }
}

class BeatWaveApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
