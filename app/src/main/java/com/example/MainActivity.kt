package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.PlayerRepository
import com.example.data.local.SettingsDataStore
import com.example.data.model.GameMode
import com.example.data.model.PlayerProfile
import com.example.data.network.MultiplayerConnectionState
import com.example.data.network.OkHttpWebSocketMultiplayerService
import com.example.monetization.AdManager
import com.example.monetization.BillingManager
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutFromGoogle
import com.example.ui.navigation.ScreenRoute
import com.example.ui.screens.AchievementsScreen
import com.example.ui.screens.ArenaSelectScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BattlePassScreen
import com.example.ui.screens.DailyRewardsScreen
import com.example.ui.screens.GameplayScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.MainMenuScreen
import com.example.ui.screens.MatchResultScreen
import com.example.ui.screens.MissionsScreen
import com.example.ui.screens.ModeSelectScreen
import com.example.ui.screens.MultiplayerLobbyScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SkinCollectionScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StoreScreen
import com.example.ui.screens.VipScreen
import com.example.ui.theme.CobraDash3DTheme
import com.example.ui.viewmodel.CobraViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {

    private lateinit var adManager: AdManager
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        adManager = AdManager(applicationContext).apply { initialize() }
        billingManager = BillingManager(applicationContext).apply { startConnection() }

        setContent {
            CobraDash3DTheme {
                CobraDashRootApp(
                    adManager = adManager,
                    billingManager = billingManager
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::billingManager.isInitialized) {
            billingManager.queryProductsAndRestore()
        }
    }

    override fun onDestroy() {
        if (::billingManager.isInitialized) {
            billingManager.endConnection()
        }
        super.onDestroy()
    }
}

@Composable
fun CobraDashRootApp(
    adManager: AdManager,
    billingManager: BillingManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    var splashDone by remember { mutableStateOf(false) }
    var guestModeActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
    }

    if (!splashDone) {
        SplashScreen(onFinished = { splashDone = true })
        return
    }

    val user = currentUser
    if (user == null && !guestModeActive) {
        AuthScreen(
            onGoogleAuthSuccess = { guestModeActive = false },
            onContinueGuest = { guestModeActive = true }
        )
    } else {
        val sessionKey = user?.uid ?: "guest_local_session"
        androidx.compose.runtime.key(sessionKey) {
            AuthenticatedOrGuestNavHost(
                currentUserId = user?.uid,
                isGuestSession = user == null,
                adManager = adManager,
                billingManager = billingManager,
                onSwitchToGoogleSignIn = { guestModeActive = false },
                onSignOut = {
                    signOutFromGoogle(credentialManager, scope) {
                        guestModeActive = false
                    }
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedOrGuestNavHost(
    currentUserId: String?,
    isGuestSession: Boolean,
    adManager: AdManager,
    billingManager: BillingManager,
    onSwitchToGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: CobraViewModel = viewModel(
        key = currentUserId ?: "guest_vm",
        factory = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val dataStore = SettingsDataStore(app)
                val repo = PlayerRepository(app)
                val mpService = OkHttpWebSocketMultiplayerService(scope)
                CobraViewModel(
                    settingsDataStore = dataStore,
                    playerRepository = repo,
                    multiplayerService = mpService,
                    currentUserId = currentUserId,
                    isGuestSession = isGuestSession
                )
            }
        }
    )

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profileUiState by viewModel.profileState.collectAsStateWithLifecycle()
    val matchHistoryState by viewModel.matchHistoryState.collectAsStateWithLifecycle()
    val leaderboardState by viewModel.leaderboardState.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val selectedDifficulty by viewModel.selectedDifficulty.collectAsStateWithLifecycle()
    val lastMatchSummary by viewModel.lastMatchSummary.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val billingState by billingManager.billingState.collectAsStateWithLifecycle()
    val rewardedStatus by adManager.rewardedStatus.collectAsStateWithLifecycle()

    val profile = (profileUiState as? UiState.Success)?.data ?: PlayerProfile()

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.disconnectMultiplayer() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = ScreenRoute.MAIN_MENU.route
            ) {
                composable(ScreenRoute.MAIN_MENU.route) {
                    MainMenuScreen(
                        profile = profile,
                        selectedMode = selectedMode,
                        selectedDifficulty = selectedDifficulty,
                        isGuestSession = isGuestSession,
                        adsRemoved = false,
                        onPlayNow = {
                            if (selectedMode.requiresOnlineBackend) {
                                navController.navigate(ScreenRoute.MULTIPLAYER_LOBBY.route)
                            } else {
                                navController.navigate(ScreenRoute.GAMEPLAY.route)
                            }
                        },
                        onNavigate = { route -> navController.navigate(route.route) },
                        onSwitchToGoogleSignIn = onSwitchToGoogleSignIn
                    )
                }

                composable(ScreenRoute.MODE_SELECT.route) {
                    ModeSelectScreen(
                        level = profile.level,
                        selectedMode = selectedMode,
                        selectedDifficulty = selectedDifficulty,
                        onSelectMode = viewModel::selectMode,
                        onSelectDifficulty = viewModel::selectDifficulty,
                        onLaunchPlayableMode = { navController.navigate(ScreenRoute.GAMEPLAY.route) },
                        onOpenMultiplayerLobby = { navController.navigate(ScreenRoute.MULTIPLAYER_LOBBY.route) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ScreenRoute.SKIN_COLLECTION.route) {
                    SkinCollectionScreen(
                        profile = profile,
                        onSelectSkin = viewModel::selectSkin,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ScreenRoute.ARENA_SELECT.route) {
                    ArenaSelectScreen(
                        profile = profile,
                        onSelectArena = viewModel::selectArena,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ScreenRoute.STORE.route) {
                    StoreScreen(
                        profile = profile,
                        billingManager = billingManager,
                        billingState = billingState,
                        adManager = adManager,
                        rewardedStatus = rewardedStatus,
                        onRewardedXp = viewModel::grantRewardedAdBonus,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ScreenRoute.VIP.route) {
                    VipScreen(
                        profile = profile,
                        billingManager = billingManager,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ScreenRoute.BATTLE_PASS.route) {
                    BattlePassScreen(
                        profile = profile,
                        billingManager = billingManager,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ScreenRoute.DAILY_REWARDS.route) {
                    DailyRewardsScreen(
                        profile = profile,
                        onClaimReward = viewModel::claimDailyReward,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ScreenRoute.MISSIONS.route) {
                    MissionsScreen(
                        profile = profile,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ScreenRoute.ACHIEVEMENTS.route) {
                    AchievementsScreen(
                        profile = profile,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ScreenRoute.PROFILE.route) {
                    ProfileScreen(
                        profile = profile,
                        matchHistoryState = matchHistoryState,
                        isGuestSession = isGuestSession,
                        onUpdateNickname = viewModel::updateNickname,
                        onSignOut = onSignOut,
                        onDeleteAccount = { viewModel.deleteCloudAccount(onSignOut) },
                        onSwitchToGoogleSignIn = onSwitchToGoogleSignIn,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ScreenRoute.LEADERBOARD.route) {
                    LeaderboardScreen(
                        level = profile.level,
                        isGuestSession = isGuestSession,
                        leaderboardState = leaderboardState,
                        onSwitchToGoogleSignIn = onSwitchToGoogleSignIn,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ScreenRoute.MULTIPLAYER_LOBBY.route) {
                    MultiplayerLobbyScreen(
                        level = profile.level,
                        settings = settings,
                        connectionState = connectionState,
                        onUpdateEndpoints = { rest, ws ->
                            viewModel.updateSettings {
                                it.copy(multiplayerServerUrl = rest, multiplayerWsUrl = ws)
                            }
                        },
                        onCheckServer = viewModel::testMultiplayerServer,
                        onCreateRoom = viewModel::createMultiplayerRoom,
                        onJoinRoom = viewModel::joinMultiplayerRoom,
                        onQueueMatchmaking = viewModel::queueMatchmaking,
                        onDisconnect = viewModel::disconnectMultiplayer,
                        onPlayAiBattleInstead = {
                            viewModel.selectMode(GameMode.AI_BATTLE)
                            navController.navigate(ScreenRoute.GAMEPLAY.route)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ScreenRoute.SETTINGS.route) {
                    SettingsScreen(
                        level = profile.level,
                        settings = settings,
                        onUpdateSettings = viewModel::updateSettings,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ScreenRoute.GAMEPLAY.route) {
                    val matchConfig = remember(selectedMode, profile.selectedArenaId, profile.selectedSkinId, selectedDifficulty) {
                        viewModel.buildMatchConfig()
                    }
                    GameplayScreen(
                        matchConfig = matchConfig,
                        playerLevel = profile.level,
                        settings = settings,
                        isOnlineConnected = connectionState is MultiplayerConnectionState.Connected,
                        onMatchFinished = { score, kills, maxLen, dur, victory, reason ->
                            viewModel.completeMatch(matchConfig, score, kills, maxLen, dur, victory, reason)
                            val dest = if (victory) ScreenRoute.VICTORY.route else ScreenRoute.DEFEAT.route
                            navController.navigate(dest) {
                                popUpTo(ScreenRoute.GAMEPLAY.route) { inclusive = true }
                            }
                        },
                        onExitToMenu = {
                            navController.popBackStack(ScreenRoute.MAIN_MENU.route, inclusive = false)
                        }
                    )
                }

                composable(ScreenRoute.VICTORY.route) {
                    MatchResultScreen(
                        isVictoryScreen = true,
                        summary = lastMatchSummary,
                        adManager = adManager,
                        onPlayAgain = {
                            navController.navigate(ScreenRoute.GAMEPLAY.route) {
                                popUpTo(ScreenRoute.VICTORY.route) { inclusive = true }
                            }
                        },
                        onBackToMenu = {
                            navController.popBackStack(ScreenRoute.MAIN_MENU.route, inclusive = false)
                        }
                    )
                }

                composable(ScreenRoute.DEFEAT.route) {
                    MatchResultScreen(
                        isVictoryScreen = false,
                        summary = lastMatchSummary,
                        adManager = adManager,
                        onPlayAgain = {
                            navController.navigate(ScreenRoute.GAMEPLAY.route) {
                                popUpTo(ScreenRoute.DEFEAT.route) { inclusive = true }
                            }
                        },
                        onBackToMenu = {
                            navController.popBackStack(ScreenRoute.MAIN_MENU.route, inclusive = false)
                        }
                    )
                }
            }
        }
    }
}
