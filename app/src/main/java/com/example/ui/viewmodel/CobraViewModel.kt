package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.PlayerRepository
import com.example.data.local.SettingsDataStore
import com.example.data.model.AiDifficulty
import com.example.data.model.GameCatalog
import com.example.data.model.GameMode
import com.example.data.model.LeaderboardEntry
import com.example.data.model.MatchRecord
import com.example.data.model.PlayerProfile
import com.example.data.model.PlayerSettings
import com.example.data.network.MultiplayerConnectionState
import com.example.data.network.MultiplayerService
import com.example.game.engine.MatchConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class MatchResultSummary(
    val mode: GameMode,
    val arenaName: String,
    val skinName: String,
    val score: Int,
    val kills: Int,
    val maxLength: Int,
    val durationSec: Int,
    val isVictory: Boolean,
    val xpEarned: Int,
    val deathReason: String?
)

class CobraViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val playerRepository: PlayerRepository,
    private val multiplayerService: MultiplayerService,
    private val currentUserId: String?,
    private val isGuestSession: Boolean
) : ViewModel() {

    val settings: StateFlow<PlayerSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PlayerSettings())

    private val _selectedMode = MutableStateFlow(GameMode.AI_BATTLE)
    val selectedMode: StateFlow<GameMode> = _selectedMode.asStateFlow()

    private val _selectedDifficulty = MutableStateFlow(AiDifficulty.NORMAL)
    val selectedDifficulty: StateFlow<AiDifficulty> = _selectedDifficulty.asStateFlow()

    private val _lastMatchSummary = MutableStateFlow<MatchResultSummary?>(null)
    val lastMatchSummary: StateFlow<MatchResultSummary?> = _lastMatchSummary.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val connectionState: StateFlow<MultiplayerConnectionState> = multiplayerService.connectionState

    val profileState: StateFlow<UiState<PlayerProfile>> = if (currentUserId != null && !isGuestSession) {
        playerRepository.observeProfile(currentUserId)
            .map<PlayerProfile?, UiState<PlayerProfile>> { profile ->
                if (profile != null) {
                    UiState.Success(profile)
                } else {
                    UiState.Loading
                }
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Profile stream error", root)
                emit(UiState.Error(root.message ?: "Failed to load profile"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
    } else {
        settingsDataStore.guestProfileFlow
            .map<PlayerProfile, UiState<PlayerProfile>> { UiState.Success(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
    }

    val matchHistoryState: StateFlow<UiState<List<MatchRecord>>> = if (currentUserId != null && !isGuestSession) {
        playerRepository.observeMatchHistory(currentUserId)
            .map<List<MatchRecord>, UiState<List<MatchRecord>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                emit(UiState.Error((fsErr ?: error).message ?: "Failed to load matches"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
    } else {
        MutableStateFlow<UiState<List<MatchRecord>>>(UiState.Success(emptyList())).asStateFlow()
    }

    val leaderboardState: StateFlow<UiState<List<LeaderboardEntry>>> = if (currentUserId != null && !isGuestSession) {
        playerRepository.observeLeaderboard()
            .map<List<LeaderboardEntry>, UiState<List<LeaderboardEntry>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                emit(UiState.Error((fsErr ?: error).message ?: "Failed to load leaderboard"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
    } else {
        MutableStateFlow<UiState<List<LeaderboardEntry>>>(UiState.Success(emptyList())).asStateFlow()
    }

    init {
        if (currentUserId != null && !isGuestSession) {
            viewModelScope.launch {
                try {
                    val displayName = Firebase.auth.currentUser?.displayName ?: "CobraPilot"
                    playerRepository.ensureProfileInitialized(displayName)
                } catch (e: Exception) {
                    Log.w(TAG, "Initial profile check error: ${e.message}")
                }
            }
        }
        viewModelScope.launch {
            settings.collect { s ->
                multiplayerService.configureEndpoints(s.multiplayerServerUrl, s.multiplayerWsUrl)
            }
        }
    }

    fun activeProfile(): PlayerProfile {
        return (profileState.value as? UiState.Success)?.data ?: PlayerProfile()
    }

    fun selectMode(mode: GameMode) {
        _selectedMode.value = mode
    }

    fun selectDifficulty(difficulty: AiDifficulty) {
        _selectedDifficulty.value = difficulty
    }

    fun buildMatchConfig(): MatchConfig {
        val profile = activeProfile()
        return MatchConfig(
            mode = _selectedMode.value,
            arena = GameCatalog.arenaById(profile.selectedArenaId),
            playerSkin = GameCatalog.skinById(profile.selectedSkinId),
            playerName = profile.nickname,
            aiDifficulty = _selectedDifficulty.value
        )
    }

    fun selectSkin(skinId: String) {
        val profile = activeProfile()
        val skin = GameCatalog.skinById(skinId)
        val isUnlocked = profile.unlockedSkinIds.contains(skinId) || profile.level >= skin.unlockLevel
        if (!isUnlocked) {
            _statusBanner.value = "Reach Level ${skin.unlockLevel} to unlock ${skin.name}."
            return
        }
        val updatedSkins = (profile.unlockedSkinIds + skinId).distinct()
        saveProfileUpdate(profile.copy(selectedSkinId = skinId, unlockedSkinIds = updatedSkins))
        _statusBanner.value = "${skin.name} equipped!"
    }

    fun selectArena(arenaId: String) {
        val profile = activeProfile()
        val arena = GameCatalog.arenaById(arenaId)
        if (profile.level < arena.unlockLevel) {
            _statusBanner.value = "Reach Level ${arena.unlockLevel} to unlock ${arena.name}."
            return
        }
        saveProfileUpdate(profile.copy(selectedArenaId = arenaId))
        _statusBanner.value = "${arena.name} selected!"
    }

    fun updateNickname(newNickname: String) {
        val clean = newNickname.trim()
        if (clean.length < 2 || clean.length > 20) {
            _statusBanner.value = "Nickname must be between 2 and 20 characters."
            return
        }
        saveProfileUpdate(activeProfile().copy(nickname = clean))
        _statusBanner.value = "Nickname updated to $clean."
    }

    fun claimDailyReward() {
        val profile = activeProfile()
        val today = System.currentTimeMillis() / 86_400_000L
        if (profile.lastDailyClaimDay >= today) {
            _statusBanner.value = "Today's daily reward has already been claimed."
            return
        }
        val newStreak = if (profile.lastDailyClaimDay == today - 1L) profile.dailyRewardStreak + 1L else 1L
        val bonusXp = 150L + (newStreak.coerceAtMost(7L) * 50L)
        val newXp = profile.xp + bonusXp
        val newLevel = (1L + newXp / 500L).coerceAtMost(500L)
        val bonusSkins = if (newStreak >= 3L) {
            (profile.unlockedSkinIds + "neon_snake").distinct()
        } else {
            profile.unlockedSkinIds
        }
        saveProfileUpdate(
            profile.copy(
                xp = newXp,
                level = newLevel,
                battlePassXp = profile.battlePassXp + bonusXp,
                dailyRewardStreak = newStreak,
                lastDailyClaimDay = today,
                unlockedSkinIds = bonusSkins
            )
        )
        _statusBanner.value = "Day $newStreak Reward Claimed: +$bonusXp XP!"
    }

    fun completeMatch(
        config: MatchConfig,
        score: Int,
        kills: Int,
        maxLength: Int,
        durationSec: Int,
        isVictory: Boolean,
        deathReason: String?
    ) {
        val earnedXp = (score / 10) + (kills * 25) + if (isVictory) 120 else 30
        _lastMatchSummary.value = MatchResultSummary(
            mode = config.mode,
            arenaName = config.arena.name,
            skinName = config.playerSkin.name,
            score = score,
            kills = kills,
            maxLength = maxLength,
            durationSec = durationSec,
            isVictory = isVictory,
            xpEarned = earnedXp,
            deathReason = deathReason
        )

        val profile = activeProfile()
        val newKills = profile.totalKills + kills
        val newMatches = profile.matchesPlayed + 1L
        val newWins = profile.wins + if (isVictory) 1L else 0L
        val newHigh = maxOf(profile.highScore, score.toLong())
        val newXp = profile.xp + earnedXp.toLong()
        val newLevel = (1L + newXp / 500L).coerceAtMost(500L)

        // Auto-unlock skins unlocked by new level
        val autoSkins = GameCatalog.skins
            .filter { !it.isVipExclusive && it.unlockLevel <= newLevel }
            .map { it.id }
        val mergedSkins = (profile.unlockedSkinIds + autoSkins).distinct().take(60)

        val newAchievements = buildList {
            if (newKills >= 1L) add("first_blood")
            if (newKills >= 25L) add("apex_hunter")
            if (newHigh >= 2500L) add("high_roller")
            if (newMatches >= 10L) add("veteran_pilot")
            if (newWins >= 5L) add("crown_victor")
            if (mergedSkins.size >= 5) add("skin_collector")
        }

        if (currentUserId != null && !isGuestSession) {
            viewModelScope.launch {
                try {
                    playerRepository.recordMatch(
                        currentProfile = profile.copy(unlockedSkinIds = mergedSkins),
                        mode = config.mode.id,
                        arenaId = config.arena.id,
                        skinId = config.playerSkin.id,
                        score = score.toLong(),
                        kills = kills.toLong(),
                        maxLength = maxLength.toLong(),
                        durationSeconds = durationSec.toLong(),
                        victory = isVictory,
                        newlyUnlockedAchievements = newAchievements
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to record match to Firestore", e)
                }
            }
        } else {
            val updated = profile.copy(
                xp = newXp,
                level = newLevel,
                highScore = newHigh,
                totalKills = newKills,
                matchesPlayed = newMatches,
                wins = newWins,
                battlePassXp = profile.battlePassXp + earnedXp.toLong(),
                unlockedSkinIds = mergedSkins,
                unlockedAchievementIds = (profile.unlockedAchievementIds + newAchievements).distinct()
            )
            viewModelScope.launch { settingsDataStore.saveGuestProfile(updated) }
        }
    }

    fun grantRewardedAdBonus(xpBonus: Int) {
        val profile = activeProfile()
        val newXp = profile.xp + xpBonus.toLong()
        val newLevel = (1L + newXp / 500L).coerceAtMost(500L)
        saveProfileUpdate(
            profile.copy(
                xp = newXp,
                level = newLevel,
                battlePassXp = profile.battlePassXp + xpBonus.toLong()
            )
        )
        _statusBanner.value = "Reward granted: +$xpBonus XP & Battle Pass XP!"
    }

    fun updateSettings(transform: (PlayerSettings) -> PlayerSettings) {
        viewModelScope.launch { settingsDataStore.updateSettings(transform) }
    }

    fun testMultiplayerServer() {
        viewModelScope.launch {
            multiplayerService.checkServerHealth()
        }
    }

    fun createMultiplayerRoom() {
        viewModelScope.launch {
            val token = try {
                Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
            } catch (_: Exception) {
                null
            }
            multiplayerService.createPrivateRoom(token, _selectedMode.value.id, activeProfile().selectedArenaId)
        }
    }

    fun joinMultiplayerRoom(code: String) {
        viewModelScope.launch {
            val token = try {
                Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
            } catch (_: Exception) {
                null
            }
            val result = multiplayerService.joinRoom(token, code)
            if (result.isFailure) {
                _statusBanner.value = result.exceptionOrNull()?.message
            }
        }
    }

    fun queueMatchmaking() {
        viewModelScope.launch {
            val token = try {
                Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
            } catch (_: Exception) {
                null
            }
            multiplayerService.queueMatchmaking(token, _selectedMode.value.id, activeProfile().selectedArenaId)
        }
    }

    fun disconnectMultiplayer() {
        multiplayerService.disconnect()
    }

    fun deleteCloudAccount(onComplete: () -> Unit) {
        if (currentUserId == null || isGuestSession) {
            viewModelScope.launch {
                settingsDataStore.saveGuestProfile(PlayerProfile(userId = "local_guest", nickname = "GuestCobra"))
                onComplete()
            }
            return
        }
        viewModelScope.launch {
            try {
                playerRepository.deleteAccountData()
                Firebase.auth.currentUser?.delete()?.await()
                onComplete()
            } catch (e: Exception) {
                _statusBanner.value = "Account deletion requires recent sign-in: ${e.localizedMessage}"
            }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    private fun saveProfileUpdate(updated: PlayerProfile) {
        if (currentUserId != null && !isGuestSession) {
            viewModelScope.launch {
                try {
                    playerRepository.updateProfile(updated)
                } catch (e: Exception) {
                    _statusBanner.value = e.localizedMessage ?: "Failed to sync profile"
                }
            }
        } else {
            viewModelScope.launch { settingsDataStore.saveGuestProfile(updated) }
        }
    }

    private companion object {
        const val TAG = "CobraViewModel"
    }
}
