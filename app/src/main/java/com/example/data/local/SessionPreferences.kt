package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "beatwave_prefs")

class SessionPreferences(private val context: Context) {

    private object Keys {
        val SKIPPED_LOGIN = booleanPreferencesKey("skipped_login")
        val LAST_SYNC_MS = longPreferencesKey("last_sync_ms")
    }

    val skippedLogin: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SKIPPED_LOGIN] ?: false
    }

    val lastSyncMs: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_SYNC_MS] ?: 0L
    }

    suspend fun setSkippedLogin(skipped: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SKIPPED_LOGIN] = skipped
        }
    }

    suspend fun setLastSyncMs(timestampMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_MS] = timestampMs
        }
    }
}
