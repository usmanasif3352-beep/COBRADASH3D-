package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.GraphicsQuality
import com.example.data.model.Handedness
import com.example.data.model.PlayerProfile
import com.example.data.model.PlayerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cobraDataStore: DataStore<Preferences> by preferencesDataStore(name = "cobradash3d_prefs")

class SettingsDataStore(private val context: Context) {

    val settingsFlow: Flow<PlayerSettings> = context.cobraDataStore.data.map { prefs ->
        PlayerSettings(
            joystickSensitivity = prefs[KEY_SENSITIVITY] ?: 1.0f,
            handedness = if (prefs[KEY_HANDEDNESS] == Handedness.LEFT_HANDED.name) {
                Handedness.LEFT_HANDED
            } else {
                Handedness.RIGHT_HANDED
            },
            hapticsEnabled = prefs[KEY_HAPTICS] ?: true,
            graphicsQuality = GraphicsQuality.entries.firstOrNull {
                it.name == prefs[KEY_GRAPHICS]
            } ?: GraphicsQuality.HIGH,
            adaptiveResolution = prefs[KEY_ADAPTIVE_RES] ?: true,
            targetFps = prefs[KEY_TARGET_FPS] ?: 60,
            multiplayerServerUrl = prefs[KEY_SERVER_URL] ?: "https://api.cobradash3d.example.com",
            multiplayerWsUrl = prefs[KEY_WS_URL] ?: "wss://ws.cobradash3d.example.com/arena",
            analyticsOptIn = prefs[KEY_ANALYTICS] ?: false
        )
    }

    val guestProfileFlow: Flow<PlayerProfile> = context.cobraDataStore.data.map { prefs ->
        val skinsCsv = prefs[KEY_GUEST_SKINS] ?: "royal_cobra,emerald_striker,cyber_fang"
        val achCsv = prefs[KEY_GUEST_ACH] ?: ""
        PlayerProfile(
            userId = "local_guest",
            nickname = prefs[KEY_GUEST_NICK] ?: "GuestCobra",
            xp = prefs[KEY_GUEST_XP] ?: 0L,
            level = prefs[KEY_GUEST_LEVEL] ?: 1L,
            highScore = prefs[KEY_GUEST_HIGH_SCORE] ?: 0L,
            totalKills = prefs[KEY_GUEST_KILLS] ?: 0L,
            matchesPlayed = prefs[KEY_GUEST_MATCHES] ?: 0L,
            wins = prefs[KEY_GUEST_WINS] ?: 0L,
            selectedSkinId = prefs[KEY_GUEST_SKIN] ?: "royal_cobra",
            selectedArenaId = prefs[KEY_GUEST_ARENA] ?: "jungle_arena",
            unlockedSkinIds = skinsCsv.split(",").filter { it.isNotBlank() },
            unlockedAchievementIds = achCsv.split(",").filter { it.isNotBlank() },
            battlePassXp = prefs[KEY_GUEST_BP_XP] ?: 0L,
            dailyRewardStreak = prefs[KEY_GUEST_STREAK] ?: 0L,
            lastDailyClaimDay = prefs[KEY_GUEST_CLAIM_DAY] ?: 0L
        )
    }

    suspend fun updateSettings(transform: (PlayerSettings) -> PlayerSettings) {
        context.cobraDataStore.edit { prefs ->
            val current = PlayerSettings(
                joystickSensitivity = prefs[KEY_SENSITIVITY] ?: 1.0f,
                handedness = if (prefs[KEY_HANDEDNESS] == Handedness.LEFT_HANDED.name) Handedness.LEFT_HANDED else Handedness.RIGHT_HANDED,
                hapticsEnabled = prefs[KEY_HAPTICS] ?: true,
                graphicsQuality = GraphicsQuality.entries.firstOrNull { it.name == prefs[KEY_GRAPHICS] } ?: GraphicsQuality.HIGH,
                adaptiveResolution = prefs[KEY_ADAPTIVE_RES] ?: true,
                targetFps = prefs[KEY_TARGET_FPS] ?: 60,
                multiplayerServerUrl = prefs[KEY_SERVER_URL] ?: "https://api.cobradash3d.example.com",
                multiplayerWsUrl = prefs[KEY_WS_URL] ?: "wss://ws.cobradash3d.example.com/arena",
                analyticsOptIn = prefs[KEY_ANALYTICS] ?: false
            )
            val next = transform(current)
            prefs[KEY_SENSITIVITY] = next.joystickSensitivity
            prefs[KEY_HANDEDNESS] = next.handedness.name
            prefs[KEY_HAPTICS] = next.hapticsEnabled
            prefs[KEY_GRAPHICS] = next.graphicsQuality.name
            prefs[KEY_ADAPTIVE_RES] = next.adaptiveResolution
            prefs[KEY_TARGET_FPS] = next.targetFps
            prefs[KEY_SERVER_URL] = next.multiplayerServerUrl
            prefs[KEY_WS_URL] = next.multiplayerWsUrl
            prefs[KEY_ANALYTICS] = next.analyticsOptIn
        }
    }

    suspend fun saveGuestProfile(profile: PlayerProfile) {
        context.cobraDataStore.edit { prefs ->
            prefs[KEY_GUEST_NICK] = profile.nickname
            prefs[KEY_GUEST_XP] = profile.xp
            prefs[KEY_GUEST_LEVEL] = profile.level
            prefs[KEY_GUEST_HIGH_SCORE] = profile.highScore
            prefs[KEY_GUEST_KILLS] = profile.totalKills
            prefs[KEY_GUEST_MATCHES] = profile.matchesPlayed
            prefs[KEY_GUEST_WINS] = profile.wins
            prefs[KEY_GUEST_SKIN] = profile.selectedSkinId
            prefs[KEY_GUEST_ARENA] = profile.selectedArenaId
            prefs[KEY_GUEST_SKINS] = profile.unlockedSkinIds.joinToString(",")
            prefs[KEY_GUEST_ACH] = profile.unlockedAchievementIds.joinToString(",")
            prefs[KEY_GUEST_BP_XP] = profile.battlePassXp
            prefs[KEY_GUEST_STREAK] = profile.dailyRewardStreak
            prefs[KEY_GUEST_CLAIM_DAY] = profile.lastDailyClaimDay
        }
    }

    private companion object {
        val KEY_SENSITIVITY = floatPreferencesKey("sensitivity")
        val KEY_HANDEDNESS = stringPreferencesKey("handedness")
        val KEY_HAPTICS = booleanPreferencesKey("haptics")
        val KEY_GRAPHICS = stringPreferencesKey("graphics")
        val KEY_ADAPTIVE_RES = booleanPreferencesKey("adaptive_res")
        val KEY_TARGET_FPS = intPreferencesKey("target_fps")
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_WS_URL = stringPreferencesKey("ws_url")
        val KEY_ANALYTICS = booleanPreferencesKey("analytics_opt_in")

        val KEY_GUEST_NICK = stringPreferencesKey("guest_nick")
        val KEY_GUEST_XP = longPreferencesKey("guest_xp")
        val KEY_GUEST_LEVEL = longPreferencesKey("guest_level")
        val KEY_GUEST_HIGH_SCORE = longPreferencesKey("guest_high_score")
        val KEY_GUEST_KILLS = longPreferencesKey("guest_kills")
        val KEY_GUEST_MATCHES = longPreferencesKey("guest_matches")
        val KEY_GUEST_WINS = longPreferencesKey("guest_wins")
        val KEY_GUEST_SKIN = stringPreferencesKey("guest_skin")
        val KEY_GUEST_ARENA = stringPreferencesKey("guest_arena")
        val KEY_GUEST_SKINS = stringPreferencesKey("guest_skins")
        val KEY_GUEST_ACH = stringPreferencesKey("guest_ach")
        val KEY_GUEST_BP_XP = longPreferencesKey("guest_bp_xp")
        val KEY_GUEST_STREAK = longPreferencesKey("guest_streak")
        val KEY_GUEST_CLAIM_DAY = longPreferencesKey("guest_claim_day")
    }
}
