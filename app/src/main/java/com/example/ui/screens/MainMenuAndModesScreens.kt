package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AiDifficulty
import com.example.data.model.GameCatalog
import com.example.data.model.GameMode
import com.example.data.model.PlayerProfile
import com.example.ui.components.AdBannerFooter
import com.example.ui.components.CobraBackdrop
import com.example.ui.components.CobraPrimaryButton
import com.example.ui.components.CobraTopBar
import com.example.ui.components.SkinPreviewCanvas
import com.example.ui.navigation.ScreenRoute
import com.example.ui.theme.BorderEmerald
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldCore
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.SolarGold
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletMythic

private data class MenuTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val route: ScreenRoute,
    val tag: String
)

@Composable
fun MainMenuScreen(
    profile: PlayerProfile,
    selectedMode: GameMode,
    selectedDifficulty: AiDifficulty,
    isGuestSession: Boolean,
    adsRemoved: Boolean,
    onPlayNow: () -> Unit,
    onNavigate: (ScreenRoute) -> Unit,
    onSwitchToGoogleSignIn: () -> Unit
) {
    val activeSkin = GameCatalog.skinById(profile.selectedSkinId)
    val activeArena = GameCatalog.arenaById(profile.selectedArenaId)
    val levelProgress = (profile.xp % 500L) / 500f

    val tiles = listOf(
        MenuTile("Game Modes", "${GameMode.entries.size} Modes", Icons.Default.SportsEsports, EmeraldCore, ScreenRoute.MODE_SELECT, "menu_modes_tile"),
        MenuTile("Skins (50)", activeSkin.name, Icons.Default.Palette, CyberCyan, ScreenRoute.SKIN_COLLECTION, "menu_skins_tile"),
        MenuTile("Arenas (10)", activeArena.name, Icons.Default.Map, SolarGold, ScreenRoute.ARENA_SELECT, "menu_arenas_tile"),
        MenuTile("Multiplayer", "WebSocket Lobby", Icons.Default.Public, VioletMythic, ScreenRoute.MULTIPLAYER_LOBBY, "menu_multiplayer_tile"),
        MenuTile("Cosmetic Store", "Bundles & Gems", Icons.Default.ShoppingBag, SolarGold, ScreenRoute.STORE, "menu_store_tile"),
        MenuTile("Battle Pass", "Season 1 Track", Icons.Default.AutoAwesome, CyberCyan, ScreenRoute.BATTLE_PASS, "menu_battle_pass_tile"),
        MenuTile("VIP Club", "Royal Perks", Icons.Default.WorkspacePremium, SolarGold, ScreenRoute.VIP, "menu_vip_tile"),
        MenuTile("Daily Rewards", "Streak ${profile.dailyRewardStreak}d", Icons.Default.CalendarMonth, EmeraldCore, ScreenRoute.DAILY_REWARDS, "menu_daily_tile"),
        MenuTile("Missions", "Earn BP XP", Icons.Default.Flag, CyberCyan, ScreenRoute.MISSIONS, "menu_missions_tile"),
        MenuTile("Achievements", "${profile.unlockedAchievementIds.size}/${GameCatalog.achievements.size}", Icons.Default.EmojiEvents, SolarGold, ScreenRoute.ACHIEVEMENTS, "menu_achievements_tile"),
        MenuTile("Leaderboard", "Top Cobras", Icons.Default.Leaderboard, EmeraldCore, ScreenRoute.LEADERBOARD, "menu_leaderboard_tile"),
        MenuTile("Profile", profile.nickname, Icons.Default.AccountCircle, CyberCyan, ScreenRoute.PROFILE, "menu_profile_tile")
    )

    CobraBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 880.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "COBRADASH 3D",
                            style = MaterialTheme.typography.headlineLarge,
                            color = EmeraldCore
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${profile.nickname} • LVL ${profile.level}",
                                style = MaterialTheme.typography.titleMedium,
                                color = SolarGold
                            )
                            if (isGuestSession) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = CyberCyan.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { onSwitchToGoogleSignIn() }
                                ) {
                                    Text(
                                        text = "GUEST • TAP TO SYNC",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = { onNavigate(ScreenRoute.SETTINGS) },
                        modifier = Modifier.size(48.dp).testTag("menu_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = EmeraldCore
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { levelProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = EmeraldCore,
                    trackColor = CardSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Hero Arena Launch Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CardSurfaceElevated.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, EmeraldCore.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            SkinPreviewCanvas(skin = activeSkin, size = 78.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = selectedMode.title.uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = EmeraldCore
                                )
                                Text(
                                    text = "${activeArena.name} • ${selectedDifficulty.label} AI",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Best Score: ${profile.highScore}  •  Wins: ${profile.wins}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SolarGold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        CobraPrimaryButton(
                            text = "PLAY 3D",
                            onClick = onPlayNow,
                            testTag = "quick_play_button"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Command Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 152.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(tiles, key = { it.route.name }) { tile ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .testTag(tile.tag)
                                .clickable { onNavigate(tile.route) },
                            color = CardSurface.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, tile.accent.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = tile.icon,
                                        contentDescription = tile.title,
                                        tint = tile.accent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = TextSecondary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = tile.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tile.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tile.accent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AdBannerFooter(adsRemoved = adsRemoved)
        }
    }
}

@Composable
fun ModeSelectScreen(
    level: Long,
    selectedMode: GameMode,
    selectedDifficulty: AiDifficulty,
    onSelectMode: (GameMode) -> Unit,
    onSelectDifficulty: (AiDifficulty) -> Unit,
    onLaunchPlayableMode: () -> Unit,
    onOpenMultiplayerLobby: () -> Unit,
    onBack: () -> Unit
) {
    CobraBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CobraTopBar(title = "GAME MODES", level = level, onBack = onBack)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                item {
                    Text(
                        text = "AI DIFFICULTY",
                        style = MaterialTheme.typography.labelLarge,
                        color = SolarGold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiDifficulty.entries.forEach { diff ->
                            FilterChip(
                                selected = selectedDifficulty == diff,
                                onClick = { onSelectDifficulty(diff) },
                                label = { Text(diff.label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldCore,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.testTag("diff_chip_${diff.name.lowercase()}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(GameMode.entries, key = { it.id }) { mode ->
                    val isSelected = selectedMode == mode
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mode_card_${mode.id.lowercase()}")
                            .clickable { onSelectMode(mode) },
                        color = if (isSelected) EmeraldDeep.copy(alpha = 0.85f) else CardSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) EmeraldCore else BorderEmerald
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (mode.requiresOnlineBackend) Icons.Default.Groups else Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (mode.requiresOnlineBackend) VioletMythic else EmeraldCore,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (mode.requiresOnlineBackend) {
                                        "Authoritative WebSocket Server Mode"
                                    } else {
                                        "Instant Playable 3D Arena"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (mode.requiresOnlineBackend) VioletMythic else CyberCyan
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            if (isSelected) {
                                CobraPrimaryButton(
                                    text = if (mode.requiresOnlineBackend) "LOBBY" else "START",
                                    onClick = {
                                        if (mode.requiresOnlineBackend) onOpenMultiplayerLobby() else onLaunchPlayableMode()
                                    },
                                    testTag = "start_selected_mode_button"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
