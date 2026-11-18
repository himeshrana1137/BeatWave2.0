package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthOutcome
import com.example.auth.GoogleAuthHelper
import com.example.data.local.SessionPreferences
import com.example.data.remote.CloudSyncRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthGateState {
    data object Checking : AuthGateState
    data object Unauthenticated : AuthGateState
    data class Authenticated(val user: FirebaseUser) : AuthGateState
    data object OfflineSkipped : AuthGateState
}

class MainViewModel(
    private val prefs: SessionPreferences,
    private val cloudSync: CloudSyncRepository
) : ViewModel() {

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _autoSignInChecked = MutableStateFlow(false)

    val currentUser: StateFlow<FirebaseUser?> = GoogleAuthHelper.authStateFlow(Firebase.auth)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), Firebase.auth.currentUser)

    val lastSyncMs: StateFlow<Long> = prefs.lastSyncMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val authGateState: StateFlow<AuthGateState> = combine(
        currentUser,
        prefs.skippedLogin,
        _autoSignInChecked
    ) { user, skipped, checked ->
        when {
            user != null -> AuthGateState.Authenticated(user)
            skipped -> AuthGateState.OfflineSkipped
            !checked -> AuthGateState.Checking
            else -> AuthGateState.Unauthenticated
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AuthGateState.Checking)

    private var lastSyncedUid: String? = null

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null && user.uid != lastSyncedUid) {
                    lastSyncedUid = user.uid
                    syncNow(silent = true)
                } else if (user == null) {
                    lastSyncedUid = null
                }
            }
        }
    }

    fun checkAutoSignIn(context: Context) {
        viewModelScope.launch {
            if (Firebase.auth.currentUser == null && !prefs.skippedLogin.first()) {
                GoogleAuthHelper.attemptSilentAutoSignIn(context)
            }
            _autoSignInChecked.value = true
        }
    }

    fun signInWithGoogle(context: Context) {
        if (_isSigningIn.value) return
        _authError.value = null
        _isSigningIn.value = true
        viewModelScope.launch {
            when (val outcome = GoogleAuthHelper.signInInteractive(context)) {
                is AuthOutcome.Success -> {
                    prefs.setSkippedLogin(false)
                    _isSigningIn.value = false
                }
                is AuthOutcome.Cancelled -> {
                    _isSigningIn.value = false
                }
                is AuthOutcome.Error -> {
                    _isSigningIn.value = false
                    _authError.value = outcome.message
                }
            }
        }
    }

    fun skipLoginForNow() {
        viewModelScope.launch {
            _authError.value = null
            prefs.setSkippedLogin(true)
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            _syncMessage.value = null
            prefs.setSkippedLogin(false)
            GoogleAuthHelper.signOut(context)
        }
    }

    fun syncNow(silent: Boolean = false) {
        if (_isSyncing.value || Firebase.auth.currentUser == null) return
        viewModelScope.launch {
            _isSyncing.value = true
            if (!silent) _syncMessage.value = null
            val result = cloudSync.syncAll()
            _isSyncing.value = false
            if (!silent) {
                _syncMessage.value = if (result.isSuccess) {
                    "Playlists & stats synced to cloud"
                } else {
                    "Sync saved offline — will retry when online"
                }
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    class Factory(
        private val prefs: SessionPreferences,
        private val cloudSync: CloudSyncRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(prefs, cloudSync) as T
        }
    }
}
