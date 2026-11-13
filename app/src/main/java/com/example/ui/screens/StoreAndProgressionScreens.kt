package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.GameCatalog
import com.example.data.model.PlayerProfile
import com.example.monetization.AdLoadStatus
import com.example.monetization.AdManager
import com.example.monetization.BillingManager
import com.example.monetization.BillingUiState
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
fun StoreScreen(
    profile: PlayerProfile,
    billingManager: BillingManager,
    billingState: BillingUiState,
    adManager: AdManager,
    rewardedStatus: AdLoadStatus,
    onRewardedXp: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var adFeedback by remember { mutableStateOf<String?>(null) }

    CobraBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CobraTopBar(title = "COSMETIC STORE", level = profile.level, onBack = onBack)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Surface(
                        color = EmeraldDeep.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, EmeraldCore)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = SolarGold, modifier = Modifier.size(34.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Rewarded Ad Bonus", style = MaterialTheme.typography.titleLarge, color = SolarGold)
                                    Text(
                                        text = when (rewardedStatus) {
                                            is AdLoadStatus.Ready -> "Watch a short video for +100 XP & Battle Pass XP"
                                            is AdLoadStatus.Loading -> "Loading test rewarded ad..."
                                            is AdLoadStatus.Failed -> "Ad unavailable: ${rewardedStatus.reason}"
                                            AdLoadStatus.Idle -> "Initializing AdMob test unit..."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            CobraPrimaryButton(
                                text = "WATCH",
                                onClick = {
                                    val act = context as? Activity ?: return@CobraPrimaryButton
                                    adManager.showRewardedAd(
                                        activity = act,
                                        onRewardGranted = { amt ->
                                            onRewardedXp(amt)
                                            adFeedback = "Granted +$amt XP from Rewarded Ad!"
                                        },
                                        onUnavailable = { reason -> adFeedback = reason }
                                    )
                                },
                                testTag = "watch_rewarded_ad_button"
                            )
                        }
                    }
                }

                if (adFeedback != null || billingState is BillingUiState.Message || billingState is BillingUiState.PurchasePendingVerification) {
                    item {
                        val msg = when {
                            billingState is BillingUiState.PurchasePendingVerification ->
                                "Purchase acknowledged (${billingState.productId}). Awaiting server-side Play verification before granting entitlements."
                            billingState is BillingUiState.Message -> billingState.text
                            else -> adFeedback.orEmpty()
                        }
                        Surface(
                            color = CardSurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CyberCyan)
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CyberCyan,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            )
                        }
                    }
                }

                items(GameCatalog.storeOffers, key = { it.id }) { offer ->
                    val skinPreview = offer.skinId?.let { GameCatalog.skinById(it) }
                    Surface(
                        color = CardSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderEmerald)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                if (skinPreview != null) {
                                    SkinPreviewCanvas(skin = skinPreview, size = 56.dp)
                                } else {
                                    Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = SolarGold, modifier = Modifier.size(36.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(offer.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                        if (offer.badge.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(color = SolarGold, shape = RoundedCornerShape(6.dp)) {
                                                Text(offer.badge, style = MaterialTheme.typography.labelSmall, color = Color.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                    Text(offer.category, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            CobraPrimaryButton(
                                text = offer.priceDisplay,
                                onClick = {
                                    val act = context as? Activity ?: return@CobraPrimaryButton
                                    billingManager.launchPurchase(act, offer.productId)
                                },
                                testTag = "buy_${offer.id}"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VipScreen(
    profile: PlayerProfile,
    billingManager: BillingManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val emperorSkin = GameCatalog.skinById("legendary_emperor_cobra")

    CobraBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CobraTopBar(title = "ROYAL COBRA VIP", level = profile.level, onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = CardSurfaceElevated,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, SolarGold)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = "VIP Crown", tint = SolarGold, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        SkinPreviewCanvas(skin = emperorSkin, size = 104.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("COSMETIC-ONLY VIP MEMBERSHIP", style = MaterialTheme.typography.titleLarge, color = SolarGold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "• Exclusive Legendary Emperor Cobra Skin\n• Golden Crown Nameplate & Profile Frame\n• Zero Banner/Interstitial Ads\n• No Pay-to-Win Gameplay Stat Advantages",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        CobraPrimaryButton(
                            text = "SUBSCRIBE $4.99 / MO",
                            onClick = {
                                val act = context as? Activity ?: return@CobraPrimaryButton
                                billingManager.launchPurchase(act, "cobradash_vip_monthly")
                            },
                            testTag = "vip_subscribe_button",
                            containerColor = SolarGold,
                            contentColor = Color.Black,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BattlePassScreen(
    profile: PlayerProfile,
    billingManager: BillingManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    CobraBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CobraTopBar(title = "SEASON 1 BATTLE PASS", level = profile.level, onBack = onBack)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Surface(
                        color = CardSurfaceElevated,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CyberCyan)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Battle Pass XP: ${profile.battlePassXp}", style = MaterialTheme.typography.titleLarge, color = CyberCyan)
                                Text("Earn XP by playing matches & completing missions.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                            CobraPrimaryButton(
                                text = "PREMIUM $3.99",
                                onClick = {
                                    val act = context as? Activity ?: return@CobraPrimaryButton
                                    billingManager.launchPurchase(act, "cobradash_battle_pass_s1")
                                },
                                testTag = "unlock_battle_pass_button"
                            )
                        }
                    }
                }

                items(GameCatalog.battlePassTiers, key = { it.tier }) { tier ->
                    val unlocked = profile.battlePassXp >= tier.requiredXp
                    Surface(
                        color = if (unlocked) EmeraldDeep.copy(alpha = 0.6f) else CardSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (unlocked) EmeraldCore else BorderEmerald)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (unlocked) EmeraldCore else TextSecondary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("TIER ${tier.tier} (${tier.requiredXp} XP)", style = MaterialTheme.typography.titleMedium, color = SolarGold)
                                    Text("Free: ${tier.freeRewardLabel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Premium: ${tier.premiumRewardLabel}", style = MaterialTheme.typography.bodyMedium, color = CyberCyan)
                                }
                            }
                            if (unlocked) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Tier Reached", tint = EmeraldCore)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyRewardsScreen(
    profile: PlayerProfile,
    onClaimReward: () -> Unit,
    onBack: () -> Unit
) {
    val today = System.currentTimeMillis() / 86_400_000L
    val canClaimToday = profile.lastDailyClaimDay < today

    CobraBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CobraTopBar(title = "DAILY REWARDS", level = profile.level, onBack = onBack)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Surface(
                        color = CardSurfaceElevated,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, EmeraldCore)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Current Streak: ${profile.dailyRewardStreak} Days", style = MaterialTheme.typography.titleLarge, color = SolarGold)
                                Text(
                                    if (canClaimToday) "Today's XP drop is ready!" else "Come back tomorrow for your next streak bonus.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            CobraPrimaryButton(
                                text = if (canClaimToday) "CLAIM" else "CLAIMED",
                                onClick = onClaimReward,
                                enabled = canClaimToday,
                                testTag = "claim_daily_reward_button"
                            )
                        }
                    }
                }

                items((1..7).toList()) { day ->
                    val claimed = profile.dailyRewardStreak >= day
                    Surface(
                        color = if (claimed) EmeraldDeep.copy(alpha = 0.65f) else CardSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (claimed) EmeraldCore else BorderEmerald)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = if (claimed) EmeraldCore else SolarGold)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Day $day Bonus", style = MaterialTheme.typography.titleMedium)
                                    val bonus = 150 + day * 50
                                    val extra = if (day == 3) " + Neon Snake Skin" else ""
                                    Text("+$bonus Arena & Battle Pass XP$extra", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                            }
                            if (claimed) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Claimed", tint = EmeraldCore)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MissionsScreen(profile: PlayerProfile, onBack: () -> Unit) {
    CobraBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CobraTopBar(title = "MISSIONS", level = profile.level, onBack = onBack)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(GameCatalog.missions, key = { it.id }) { mission ->
                    val currentVal = when (mission.metric) {
                        "matchesPlayed" -> profile.matchesPlayed.toInt()
                        "highScore" -> profile.highScore.toInt()
                        "kills" -> profile.totalKills.toInt()
                        "wins" -> profile.wins.toInt()
                        else -> 0
                    }
                    val progress = (currentVal.toFloat() / mission.targetValue.toFloat()).coerceIn(0f, 1f)
                    val done = currentVal >= mission.targetValue

                    Surface(
                        color = if (done) EmeraldDeep.copy(alpha = 0.6f) else CardSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (done) EmeraldCore else BorderEmerald)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Flag, contentDescription = null, tint = if (done) EmeraldCore else CyberCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(mission.title, style = MaterialTheme.typography.titleMedium)
                                }
                                Text("+${mission.rewardBpXp} BP XP", style = MaterialTheme.typography.labelSmall, color = SolarGold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = if (done) EmeraldCore else CyberCyan,
                                trackColor = Color.Black.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${currentVal.coerceAtMost(mission.targetValue)} / ${mission.targetValue}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AchievementsScreen(profile: PlayerProfile, onBack: () -> Unit) {
    CobraBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CobraTopBar(title = "ACHIEVEMENTS", level = profile.level, onBack = onBack)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(GameCatalog.achievements, key = { it.id }) { ach ->
                    val unlocked = profile.unlockedAchievementIds.contains(ach.id)
                    Surface(
                        color = if (unlocked) EmeraldDeep.copy(alpha = 0.65f) else CardSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (unlocked) SolarGold else BorderEmerald)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = if (unlocked) SolarGold else TextSecondary,
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(ach.title, style = MaterialTheme.typography.titleMedium, color = if (unlocked) SolarGold else MaterialTheme.colorScheme.onSurface)
                                    Text(ach.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                            }
                            Text(
                                text = if (unlocked) "UNLOCKED" else "+${ach.rewardXp} XP",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (unlocked) EmeraldCore else CyberCyan
                            )
                        }
                    }
                }
            }
        }
    }
}
