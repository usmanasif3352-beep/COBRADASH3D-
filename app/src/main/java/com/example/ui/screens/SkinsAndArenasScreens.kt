package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GameCatalog
import com.example.data.model.PlayerProfile
import com.example.data.model.SkinRarity
import com.example.ui.components.CobraBackdrop
import com.example.ui.components.CobraPrimaryButton
import com.example.ui.components.CobraTopBar
import com.example.ui.components.SkinPreviewCanvas
import com.example.ui.theme.BorderEmerald
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldCore
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.SolarGold
import com.example.ui.theme.TextSecondary

@Composable
fun SkinCollectionScreen(
    profile: PlayerProfile,
    onSelectSkin: (String) -> Unit,
    onBack: () -> Unit
) {
    var filterRarity by remember { mutableStateOf<SkinRarity?>(null) }
    val filteredSkins = remember(filterRarity) {
        if (filterRarity == null) GameCatalog.skins
        else GameCatalog.skins.filter { it.rarity == filterRarity }
    }
    val previewSkin = GameCatalog.skinById(profile.selectedSkinId)

    CobraBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CobraTopBar(title = "50 COBRA SKINS", level = profile.level, onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 880.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Live Equipped Preview Banner
                Surface(
                    color = CardSurfaceElevated,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, Color(previewSkin.rarity.colorHex)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SkinPreviewCanvas(skin = previewSkin, size = 86.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = previewSkin.name.uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(previewSkin.rarity.colorHex)
                            )
                            Text(
                                text = "${previewSkin.rarity.label} • ${previewSkin.material.label} • ${previewSkin.effect.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = previewSkin.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Rarity Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterRarity == null,
                        onClick = { filterRarity = null },
                        label = { Text("All (${GameCatalog.skins.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldCore,
                            selectedLabelColor = Color.Black
                        )
                    )
                    SkinRarity.entries.forEach { rarity ->
                        val count = GameCatalog.skins.count { it.rarity == rarity }
                        FilterChip(
                            selected = filterRarity == rarity,
                            onClick = { filterRarity = rarity },
                            label = { Text("${rarity.label} ($count)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(rarity.colorHex),
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredSkins, key = { it.id }) { skin ->
                        val unlocked = profile.unlockedSkinIds.contains(skin.id) ||
                            (!skin.isVipExclusive && profile.level >= skin.unlockLevel)
                        val equipped = profile.selectedSkinId == skin.id
                        val rarityColor = Color(skin.rarity.colorHex)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("skin_card_${skin.id}")
                                .clickable { onSelectSkin(skin.id) },
                            color = if (equipped) EmeraldDeep.copy(alpha = 0.8f) else CardSurface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = if (equipped) 2.dp else 1.dp,
                                color = if (equipped) EmeraldCore else rarityColor.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    SkinPreviewCanvas(
                                        skin = skin,
                                        size = 72.dp,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                    Icon(
                                        imageVector = when {
                                            equipped -> Icons.Default.CheckCircle
                                            unlocked -> Icons.Default.CheckCircle
                                            else -> Icons.Default.Lock
                                        },
                                        contentDescription = if (unlocked) "Unlocked" else "Locked",
                                        tint = when {
                                            equipped -> EmeraldCore
                                            unlocked -> CyberCyan.copy(alpha = 0.7f)
                                            else -> TextSecondary
                                        },
                                        modifier = Modifier.size(18.dp).align(Alignment.TopEnd)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = skin.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${skin.rarity.label} • ${skin.material.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = rarityColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = when {
                                        equipped -> "EQUIPPED"
                                        unlocked -> "TAP TO EQUIP"
                                        skin.isVipExclusive -> "VIP EXCLUSIVE"
                                        else -> "LVL ${skin.unlockLevel} UNLOCK"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (equipped) EmeraldCore else SolarGold
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
fun ArenaSelectScreen(
    profile: PlayerProfile,
    onSelectArena: (String) -> Unit,
    onBack: () -> Unit
) {
    CobraBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CobraTopBar(title = "10 ARENA WORLDS", level = profile.level, onBack = onBack)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(GameCatalog.arenas, key = { it.id }) { arena ->
                    val unlocked = profile.level >= arena.unlockLevel
                    val selected = profile.selectedArenaId == arena.id

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("arena_card_${arena.id}")
                            .clickable { onSelectArena(arena.id) },
                        color = if (selected) EmeraldDeep.copy(alpha = 0.85f) else CardSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) EmeraldCore else Color(arena.rimColor).copy(alpha = 0.55f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(Color(arena.rimColor), Color(arena.floorColor))
                                            ),
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = arena.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = arena.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "Radius ${arena.arenaRadius.toInt()}m • ${arena.obstacleCount} Pillars • Unlock Lvl ${arena.unlockLevel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(arena.gridColor)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            if (unlocked) {
                                CobraPrimaryButton(
                                    text = if (selected) "ACTIVE" else "SELECT",
                                    onClick = { onSelectArena(arena.id) },
                                    testTag = "select_arena_${arena.id}",
                                    containerColor = if (selected) EmeraldCore else CardSurfaceElevated,
                                    contentColor = if (selected) Color.Black else EmeraldCore
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
