package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.AppContainer
import com.example.R
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.viewmodel.AuthGateState
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PlaylistDetailViewModel
import com.example.ui.viewmodel.StatsViewModel
import com.google.firebase.auth.FirebaseUser

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_STATS = "stats"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_PLAYLIST_DETAIL = "playlist/{playlistId}"

@Composable
fun BeatWaveRootApp(container: AppContainer) {
    val context = LocalContext.current
    val mainViewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(
            prefs = container.sessionPreferences,
            cloudSync = container.cloudSyncRepository
        )
    )

    LaunchedEffect(Unit) {
        mainViewModel.checkAutoSignIn(context)
    }

    val gateState by mainViewModel.authGateState.collectAsStateWithLifecycle()
    val isSigningIn by mainViewModel.isSigningIn.collectAsStateWithLifecycle()
    val authError by mainViewModel.authError.collectAsStateWithLifecycle()

    when (val state = gateState) {
        AuthGateState.Checking -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBlack),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.img_beatwave_logo),
                        contentDescription = "BeatWave 2 logo",
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    CircularProgressIndicator(color = EmeraldPrimary)
                }
            }
        }

        AuthGateState.Unauthenticated -> {
            AuthScreen(
                isSigningIn = isSigningIn,
                errorMessage = authError,
                onContinueWithGoogle = { mainViewModel.signInWithGoogle(context) },
                onSkipForNow = { mainViewModel.skipLoginForNow() }
            )
        }

        is AuthGateState.Authenticated -> {
            key(state.user.uid) {
                MainShell(
                    container = container,
                    mainViewModel = mainViewModel,
                    user = state.user
                )
            }
        }

        AuthGateState.OfflineSkipped -> {
            key("offline") {
                MainShell(
                    container = container,
                    mainViewModel = mainViewModel,
                    user = null
                )
            }
        }
    }
}

@Composable
private fun MainShell(
    container: AppContainer,
    mainViewModel: MainViewModel,
    user: FirebaseUser?
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val sessionKey = user?.uid ?: "offline"

    val libraryViewModel: LibraryViewModel = viewModel(
        key = "library_$sessionKey",
        factory = LibraryViewModel.Factory(
            localMusicRepository = container.localMusicRepository,
            playlistRepository = container.playlistRepository,
            playbackController = container.playbackController
        )
    )
    val statsViewModel: StatsViewModel = viewModel(
        key = "stats_$sessionKey",
        factory = StatsViewModel.Factory(
            playlistRepository = container.playlistRepository,
            localMusicRepository = container.localMusicRepository,
            playbackController = container.playbackController
        )
    )

    val playbackState by container.playbackController.uiState.collectAsStateWithLifecycle()
    val playlists by libraryViewModel.playlists.collectAsStateWithLifecycle()
    val isSigningIn by mainViewModel.isSigningIn.collectAsStateWithLifecycle()
    val isSyncing by mainViewModel.isSyncing.collectAsStateWithLifecycle()
    val lastSyncMs by mainViewModel.lastSyncMs.collectAsStateWithLifecycle()
    val syncMessage by mainViewModel.syncMessage.collectAsStateWithLifecycle()
    val authError by mainViewModel.authError.collectAsStateWithLifecycle()

    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val hasMiniPlayer = playbackState.currentSong != null
    val miniPlayerHeightDp = if (hasMiniPlayer) 78.dp else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = ObsidianBlack,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark)
                ) {
                    AnimatedVisibility(
                        visible = hasMiniPlayer,
                        enter = slideInVertically { it / 2 } + fadeIn(),
                        exit = slideOutVertically { it / 2 } + fadeOut()
                    ) {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            onTogglePlayPause = { container.playbackController.togglePlayPause() },
                            onNext = { container.playbackController.skipToNext() },
                            onOpenPlayer = { isPlayerExpanded = true }
                        )
                    }

                    NavigationBar(
                        containerColor = SurfaceDark,
                        windowInsets = WindowInsets.navigationBars,
                        tonalElevation = 0.dp
                    ) {
                        val navItems = listOf(
                            Triple(
                                ROUTE_LIBRARY,
                                stringResource(R.string.nav_library),
                                Icons.Rounded.LibraryMusic to Icons.Outlined.LibraryMusic
                            ),
                            Triple(
                                ROUTE_STATS,
                                stringResource(R.string.nav_stats),
                                Icons.Rounded.BarChart to Icons.Outlined.BarChart
                            ),
                            Triple(
                                ROUTE_PROFILE,
                                stringResource(R.string.nav_profile),
                                Icons.Rounded.Person to Icons.Outlined.Person
                            )
                        )
                        navItems.forEach { (route, label, icons) ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == route || (route == ROUTE_LIBRARY && it.route?.startsWith("playlist/") == true)
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = EmeraldPrimary,
                                    selectedTextColor = EmeraldPrimary,
                                    indicatorColor = EmeraldPrimary.copy(alpha = 0.16f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("bottom_nav_$route")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_LIBRARY,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(ROUTE_LIBRARY) {
                        LibraryScreen(
                            viewModel = libraryViewModel,
                            currentPlayingSongKey = playbackState.currentSong?.songKey,
                            onOpenPlaylist = { playlistId ->
                                navController.navigate("playlist/$playlistId")
                            },
                            bottomContentPadding = miniPlayerHeightDp
                        )
                    }

                    composable(
                        route = ROUTE_PLAYLIST_DETAIL,
                        arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val playlistId = backStackEntry.arguments?.getString("playlistId").orEmpty()
                        val detailVm: PlaylistDetailViewModel = viewModel(
                            key = "playlist_${playlistId}_$sessionKey",
                            factory = PlaylistDetailViewModel.Factory(
                                playlistId = playlistId,
                                playlistRepository = container.playlistRepository,
                                localMusicRepository = container.localMusicRepository,
                                playbackController = container.playbackController
                            )
                        )
                        PlaylistDetailScreen(
                            viewModel = detailVm,
                            currentPlayingSongKey = playbackState.currentSong?.songKey,
                            onBack = { navController.popBackStack() },
                            bottomContentPadding = miniPlayerHeightDp
                        )
                    }

                    composable(ROUTE_STATS) {
                        StatsScreen(
                            viewModel = statsViewModel,
                            bottomContentPadding = miniPlayerHeightDp
                        )
                    }

                    composable(ROUTE_PROFILE) {
                        ProfileScreen(
                            user = user,
                            isSigningIn = isSigningIn,
                            isSyncing = isSyncing,
                            lastSyncMs = lastSyncMs,
                            syncMessage = syncMessage,
                            authError = authError,
                            onGoogleSignIn = { mainViewModel.signInWithGoogle(context) },
                            onSyncNow = { mainViewModel.syncNow(silent = false) },
                            onLogout = { mainViewModel.signOut(context) },
                            bottomContentPadding = miniPlayerHeightDp
                        )
                    }
                }
            }
        }

        // Full-Screen Player Overlay
        AnimatedVisibility(
            visible = isPlayerExpanded && playbackState.currentSong != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            PlayerScreen(
                playbackState = playbackState,
                playlists = playlists,
                onCollapse = { isPlayerExpanded = false },
                onTogglePlayPause = { container.playbackController.togglePlayPause() },
                onPrevious = { container.playbackController.skipToPrevious() },
                onNext = { container.playbackController.skipToNext() },
                onSeekTo = { pos -> container.playbackController.seekTo(pos) },
                onToggleShuffle = { container.playbackController.toggleShuffle() },
                onCycleRepeat = { container.playbackController.cycleRepeatMode() },
                onAddCurrentSongToPlaylist = { playlistId ->
                    playbackState.currentSong?.let { song ->
                        libraryViewModel.addSingleSongToPlaylist(playlistId, song)
                    }
                },
                onCreatePlaylistAndAdd = { name ->
                    playbackState.currentSong?.let { song ->
                        libraryViewModel.enterMultiSelect(song.songKey)
                        libraryViewModel.createPlaylist(name, alsoAddSelectedSongs = true)
                    }
                }
            )
        }
    }
}
