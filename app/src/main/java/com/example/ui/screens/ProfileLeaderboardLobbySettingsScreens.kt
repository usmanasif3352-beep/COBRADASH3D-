package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.GameCatalog
import com.example.data.model.GraphicsQuality
import com.example.data.model.Handedness
import com.example.data.model.LeaderboardEntry
import com.example.data.model.MatchRecord
import com.example.data.model.PlayerProfile
import com.example.data.model.PlayerSettings
import com.example.data.network.MultiplayerConnectionState
import com.example.ui.components.CobraBackdrop
import com.example.ui.components.CobraPrimaryButton
import com.example.ui.components.CobraTopBar
import com.example.ui.components.SkinPreviewCanvas
import com.example.ui.theme.BorderEmerald
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldCore
import com.example.ui.theme.SolarGold
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletMythic
import com.example.ui.viewmodel.UiState

@Composable
fun ProfileScreen(
    profile: PlayerProfile,
    matchHistoryState: UiState<List<MatchRecord>>,
    isGuestSession: Boolean,
    onUpdateNickname: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSwitchToGoogleSignIn: () -> Unit,
    onBack: () -> Unit
) {
    var nickInput by remember(profile.nickname) { mutableStateOf(profile.nickname) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account Data?") },
            text = { Text("This permanently deletes your profile, match history, and leaderboard rank.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteAccount()
                }) { Text("DELETE", color = CrimsonDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("CANCEL") }
            }
        )
    }

    CobraBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CobraTopBar(title = "PLAYER PROFILE", level = profile.level, onBack = onBack)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Surface(
                        color = CardSurfaceElevated,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, EmeraldCore)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SkinPreviewCanvas(skin = GameCatalog.skinById(profile.selectedSkinId), size = 76.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(profile.nickname, style = MaterialTheme.typography.headlineMedium, color = EmeraldCore)
                                    Text("Level ${profile.level} • ${profile.xp} XP", style = MaterialTheme.typography.bodyLarge, color = SolarGold)
                                    Text(
                                        if (isGuestSession) "Offline Guest Session" else "Synced to Cloud Firestore",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberCyan
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                StatPill("High Score", "${profile.highScore}")
                                StatPill("Kills", "${profile.totalKills}")
                                StatPill("Matches", "${profile.matchesPlayed}")
                                StatPill("Wins", "${profile.wins}")
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = nickInput,
                                    onValueChange = { nickInput = it },
                                    label = { Text("Nickname") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                CobraPrimaryButton(
                                    text = "SAVE",
                                    onClick = { onUpdateNickname(nickInput) },
                                    testTag = "save_nickname_button"
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                if (isGuestSession) {
                                    CobraPrimaryButton(
                                        text = "SIGN IN GOOGLE",
                                        onClick = onSwitchToGoogleSignIn,
                                        testTag = "profile_google_signin",
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = onSignOut,
                                        modifier = Modifier.weight(1f).height(48.dp).testTag("sign_out_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sign Out")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showDeleteDialog = true },
                                    border = BorderStroke(1.dp, CrimsonDanger),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("delete_account_button")
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = CrimsonDanger)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Delete", color = CrimsonDanger)
                                }
                            }
                        }
                    }
                }

                item {
                    Text("RECENT MATCH HISTORY", style = MaterialTheme.typography.titleLarge, color = SolarGold)
                }

                when (matchHistoryState) {
                    is UiState.Loading -> item { Text("Loading match history...", color = TextSecondary) }
                    is UiState.Error -> item { Text(matchHistoryState.message, color = CrimsonDanger) }
                    is UiState.Success -> {
                        if (matchHistoryState.data.isEmpty()) {
                            item {
                                Text(
                                    if (isGuestSession) "Sign in with Google to persist cloud match history." else "No completed cloud matches yet. Jump into the 3D Arena!",
                                    color = TextSecondary
                                )
                            }
                        } else {
                            items(matchHistoryState.data) { rec ->
                                Surface(color = CardSurface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderEmerald)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("${rec.mode} • ${GameCatalog.arenaById(rec.arenaId).name}", style = MaterialTheme.typography.titleMedium)
                                            Text("Kills ${rec.kills} • Max Len ${rec.maxLength} • ${rec.durationSeconds}s", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("${rec.score}", style = MaterialTheme.typography.titleLarge, color = SolarGold)
                                            Text(if (rec.victory) "VICTORY" else "FINISHED", style = MaterialTheme.typography.labelSmall, color = if (rec.victory) EmeraldCore else TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = EmeraldCore)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
fun LeaderboardScreen(
    level: Long,
    isGuestSession: Boolean,
    leaderboardState: UiState<List<LeaderboardEntry>>,
    onSwitchToGoogleSignIn: () -> Unit,
    onBack: () -> Unit
) {
    CobraBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CobraTopBar(title = "GLOBAL LEADERBOARD", level = level, onBack = onBack)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (isGuestSession) {
                    item {
                        Surface(color = CardSurfaceElevated, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CyberCyan)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Google Sign-In Required for Cloud Leaderboard", style = MaterialTheme.typography.titleLarge, color = CyberCyan)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Offline Guest sessions never fake online players. Sign in with Google to compete on Firestore global rankings.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                Spacer(modifier = Modifier.height(10.dp))
                                CobraPrimaryButton(text = "SIGN IN WITH GOOGLE", onClick = onSwitchToGoogleSignIn, testTag = "leaderboard_signin_button")
                            }
                        }
                    }
                } else {
                    when (leaderboardState) {
                        is UiState.Loading -> item { Text("Loading rankings...", color = TextSecondary) }
                        is UiState.Error -> item { Text(leaderboardState.message, color = CrimsonDanger) }
                        is UiState.Success -> {
                            if (leaderboardState.data.isEmpty()) {
                                item { Text("No scores posted yet. Complete a match to claim #1!", color = TextSecondary) }
                            } else {
                                itemsIndexed(leaderboardState.data) { index, entry ->
                                    Surface(
                                        color = if (index == 0) CardSurfaceElevated else CardSurface,
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, if (index == 0) SolarGold else BorderEmerald)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Leaderboard, contentDescription = null, tint = if (index == 0) SolarGold else EmeraldCore)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text("#${index + 1} ${entry.nickname}", style = MaterialTheme.typography.titleMedium)
                                                    Text("Level ${entry.level} • Wins ${entry.wins}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                                }
                                            }
                                            Text("${entry.highScore}", style = MaterialTheme.typography.titleLarge, color = SolarGold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MultiplayerLobbyScreen(
    level: Long,
    settings: PlayerSettings,
    connectionState: MultiplayerConnectionState,
    onUpdateEndpoints: (String, String) -> Unit,
    onCheckServer: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onQueueMatchmaking: () -> Unit,
    onDisconnect: () -> Unit,
    onPlayAiBattleInstead: () -> Unit,
    onBack: () -> Unit
) {
    var restUrl by remember(settings.multiplayerServerUrl) { mutableStateOf(settings.multiplayerServerUrl) }
    var wsUrl by remember(settings.multiplayerWsUrl) { mutableStateOf(settings.multiplayerWsUrl) }
    var roomCode by remember { mutableStateOf("") }

    CobraBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CobraTopBar(title = "ONLINE MULTIPLAYER LOBBY", level = level, onBack = onBack)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Surface(
                        color = CardSurfaceElevated,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, VioletMythic)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (connectionState is MultiplayerConnectionState.Connected) Icons.Default.Public else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = if (connectionState is MultiplayerConnectionState.Connected) EmeraldCore else SolarGold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (connectionState) {
                                        MultiplayerConnectionState.Disconnected -> "Status: Ready to Connect"
                                        MultiplayerConnectionState.Connecting -> "Status: Connecting to WebSocket..."
                                        is MultiplayerConnectionState.Connected -> "Connected Room ${connectionState.roomCode} (${connectionState.playerCount} players, ${connectionState.pingMs}ms)"
                                        is MultiplayerConnectionState.ServerUnavailable -> "Server Unavailable"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (connectionState is MultiplayerConnectionState.Connected) EmeraldCore else SolarGold
                                )
                            }
                            if (connectionState is MultiplayerConnectionState.ServerUnavailable) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = connectionState.reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            CobraPrimaryButton(
                                text = "CHECK SERVER & MATCHMAKE",
                                onClick = onQueueMatchmaking,
                                testTag = "queue_matchmaking_button",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onPlayAiBattleInstead,
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("fallback_ai_battle_button")
                            ) {
                                Text("Play Offline AI Battle / Practice Mode Instead", color = EmeraldCore)
                            }
                        }
                    }
                }

                item {
                    Surface(color = CardSurface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderEmerald)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Private Friends Room", style = MaterialTheme.typography.titleLarge, color = CyberCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = roomCode,
                                    onValueChange = { roomCode = it.uppercase() },
                                    label = { Text("Room Code") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                CobraPrimaryButton(
                                    text = "JOIN",
                                    onClick = { onJoinRoom(roomCode) },
                                    testTag = "join_room_button"
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = onCreateRoom, modifier = Modifier.weight(1f).height(48.dp)) {
                                    Text("Create Private Room")
                                }
                                OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f).height(48.dp)) {
                                    Text("Disconnect")
                                }
                            }
                        }
                    }
                }

                item {
                    Surface(color = CardSurface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderEmerald)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Authoritative Server Endpoints", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = restUrl,
                                onValueChange = { restUrl = it },
                                label = { Text("REST Base URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = wsUrl,
                                onValueChange = { wsUrl = it },
                                label = { Text("WebSocket Endpoint") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CobraPrimaryButton(
                                    text = "SAVE & TEST",
                                    onClick = {
                                        onUpdateEndpoints(restUrl, wsUrl)
                                        onCheckServer()
                                    },
                                    testTag = "save_server_endpoints_button"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    level: Long,
    settings: PlayerSettings,
    onUpdateSettings: ((PlayerSettings) -> PlayerSettings) -> Unit,
    onBack: () -> Unit
) {
    CobraBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CobraTopBar(title = "SETTINGS & PERFORMANCE", level = level, onBack = onBack)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Surface(color = CardSurface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderEmerald)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Controls", style = MaterialTheme.typography.titleLarge, color = EmeraldCore)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Joystick Sensitivity: ${"%.2f".format(settings.joystickSensitivity)}x", style = MaterialTheme.typography.bodyLarge)
                            Slider(
                                value = settings.joystickSensitivity,
                                onValueChange = { v -> onUpdateSettings { it.copy(joystickSensitivity = v) } },
                                valueRange = 0.6f..1.8f,
                                colors = SliderDefaults.colors(thumbColor = EmeraldCore, activeTrackColor = EmeraldCore)
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Left-Handed Controls", style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = settings.handedness == Handedness.LEFT_HANDED,
                                    onCheckedChange = { left ->
                                        onUpdateSettings {
                                            it.copy(handedness = if (left) Handedness.LEFT_HANDED else Handedness.RIGHT_HANDED)
                                        }
                                    }
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Haptic Vibration Feedback", style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = settings.hapticsEnabled,
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(hapticsEnabled = v) } }
                                )
                            }
                        }
                    }
                }

                item {
                    Surface(color = CardSurface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderEmerald)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("3D Graphics & Battery", style = MaterialTheme.typography.titleLarge, color = CyberCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            ) {
                                GraphicsQuality.entries.forEach { q ->
                                    FilterChip(
                                        selected = settings.graphicsQuality == q,
                                        onClick = { onUpdateSettings { it.copy(graphicsQuality = q) } },
                                        label = { Text(q.name, style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyberCyan,
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Adaptive Resolution Scaling", style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = settings.adaptiveResolution,
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(adaptiveResolution = v) } }
                                )
                            }
                        }
                    }
                }

                item {
                    Surface(color = CardSurface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderEmerald)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Privacy & Release Notes", style = MaterialTheme.typography.titleLarge, color = SolarGold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Optional Gameplay Telemetry", style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = settings.analyticsOptIn,
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(analyticsOptIn = v) } }
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Build uses Google Play target SDK 36, OpenGL ES 3.0, Firebase Auth + Firestore, Play Billing 7.1.1, and Google test ad units.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
