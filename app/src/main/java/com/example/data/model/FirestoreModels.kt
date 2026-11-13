package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class PlayerProfile(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val nickname: String = "CobraPlayer",
    val xp: Long = 0L,
    val level: Long = 1L,
    val highScore: Long = 0L,
    val totalKills: Long = 0L,
    val matchesPlayed: Long = 0L,
    val wins: Long = 0L,
    val selectedSkinId: String = "royal_cobra",
    val selectedArenaId: String = "jungle_arena",
    val unlockedSkinIds: List<String> = listOf("royal_cobra", "emerald_striker", "cyber_fang"),
    val unlockedAchievementIds: List<String> = emptyList(),
    val battlePassXp: Long = 0L,
    val dailyRewardStreak: Long = 0L,
    val lastDailyClaimDay: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MatchRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mode: String = "PRACTICE",
    val arenaId: String = "jungle_arena",
    val skinId: String = "royal_cobra",
    val score: Long = 0L,
    val kills: Long = 0L,
    val maxLength: Long = 10L,
    val durationSeconds: Long = 0L,
    val victory: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val nickname: String = "CobraPlayer",
    val highScore: Long = 0L,
    val level: Long = 1L,
    val wins: Long = 0L,
    val skinId: String = "royal_cobra",
    @get:PropertyName("isPublic") val isPublic: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
