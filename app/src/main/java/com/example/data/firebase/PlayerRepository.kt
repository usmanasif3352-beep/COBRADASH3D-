package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.LeaderboardEntry
import com.example.data.model.MatchRecord
import com.example.data.model.PlayerProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PlayerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeProfile(userId: String): Flow<PlayerProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            PlayerProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureProfileInitialized(defaultNickname: String): PlayerProfile {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                existing.toObject(
                    PlayerProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: createInitialProfile(uid, defaultNickname)
            } else {
                createInitialProfile(uid, defaultNickname)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    private suspend fun createInitialProfile(uid: String, rawNickname: String): PlayerProfile {
        val cleanNick = rawNickname.trim().ifBlank { "CobraPilot" }.take(20).padEnd(3, 'X')
        val docRef = db.collection("users").document(uid)
        val starterSkins = listOf("royal_cobra", "emerald_striker", "cyber_fang")
        val payload = mapOf(
            "userId" to uid,
            "nickname" to cleanNick,
            "xp" to 0L,
            "level" to 1L,
            "highScore" to 0L,
            "totalKills" to 0L,
            "matchesPlayed" to 0L,
            "wins" to 0L,
            "selectedSkinId" to "royal_cobra",
            "selectedArenaId" to "jungle_arena",
            "unlockedSkinIds" to starterSkins,
            "unlockedAchievementIds" to emptyList<String>(),
            "battlePassXp" to 0L,
            "dailyRewardStreak" to 0L,
            "lastDailyClaimDay" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            syncLeaderboardEntry(
                uid = uid,
                nickname = cleanNick,
                highScore = 0L,
                level = 1L,
                wins = 0L,
                skinId = "royal_cobra"
            )
            return docRef.get().await().toObject(
                PlayerProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: PlayerProfile(userId = uid, nickname = cleanNick)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateProfile(profile: PlayerProfile) {
        val uid = requireUserId()
        requireNotNull(profile.createdAt) { "Profile createdAt must not be null for update" }
        val docRef = db.collection("users").document(uid)
        try {
            val sanitized = profile.copy(
                userId = uid,
                nickname = profile.nickname.trim().take(24).ifBlank { "CobraPilot" }.padEnd(2, 'X'),
                updatedAt = null
            )
            docRef.set(sanitized).await()
            syncLeaderboardEntry(
                uid = uid,
                nickname = sanitized.nickname,
                highScore = sanitized.highScore,
                level = sanitized.level,
                wins = sanitized.wins,
                skinId = sanitized.selectedSkinId
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun recordMatch(
        currentProfile: PlayerProfile,
        mode: String,
        arenaId: String,
        skinId: String,
        score: Long,
        kills: Long,
        maxLength: Long,
        durationSeconds: Long,
        victory: Boolean,
        newlyUnlockedAchievements: List<String> = emptyList()
    ): PlayerProfile {
        val uid = requireUserId()
        val matchRef = db.collection("users").document(uid).collection("matches").document()
        val matchPayload = mapOf(
            "userId" to uid,
            "mode" to mode.take(40),
            "arenaId" to arenaId.take(64),
            "skinId" to skinId.take(64),
            "score" to score.coerceAtLeast(0L),
            "kills" to kills.coerceAtLeast(0L),
            "maxLength" to maxLength.coerceAtLeast(1L),
            "durationSeconds" to durationSeconds.coerceAtLeast(0L),
            "victory" to victory,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            matchRef.set(matchPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, matchRef.path)
            throw e
        }

        val earnedXp = (score / 10L) + (kills * 25L) + if (victory) 120L else 30L
        val newXp = currentProfile.xp + earnedXp
        val newLevel = (1L + newXp / 500L).coerceAtMost(500L)
        val mergedAchievements = (currentProfile.unlockedAchievementIds + newlyUnlockedAchievements)
            .distinct()
            .take(60)
        val updatedProfile = currentProfile.copy(
            xp = newXp,
            level = newLevel,
            highScore = maxOf(currentProfile.highScore, score),
            totalKills = currentProfile.totalKills + kills,
            matchesPlayed = currentProfile.matchesPlayed + 1L,
            wins = currentProfile.wins + if (victory) 1L else 0L,
            battlePassXp = currentProfile.battlePassXp + earnedXp,
            unlockedAchievementIds = mergedAchievements
        )
        updateProfile(updatedProfile)
        return updatedProfile
    }

    fun observeMatchHistory(userId: String): Flow<List<MatchRecord>> = flow {
        val colRef = db.collection("users").document(userId).collection("matches")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(25)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MatchRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeLeaderboard(): Flow<List<LeaderboardEntry>> = flow {
        val colRef = db.collection("leaderboard_entries")
        emitAll(
            colRef.whereEqualTo("isPublic", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LeaderboardEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.highScore }
                        .take(50)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    private suspend fun syncLeaderboardEntry(
        uid: String,
        nickname: String,
        highScore: Long,
        level: Long,
        wins: Long,
        skinId: String
    ) {
        val entryRef = db.collection("leaderboard_entries").document(uid)
        try {
            val snap = entryRef.get().await()
            if (snap.exists()) {
                entryRef.update(
                    mapOf(
                        "nickname" to nickname,
                        "highScore" to highScore,
                        "level" to level,
                        "wins" to wins,
                        "skinId" to skinId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                entryRef.set(
                    mapOf(
                        "userId" to uid,
                        "nickname" to nickname,
                        "highScore" to highScore,
                        "level" to level,
                        "wins" to wins,
                        "skinId" to skinId,
                        "isPublic" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, entryRef.path)
            throw e
        }
    }

    suspend fun deleteAccountData() {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        val leaderboardDoc = db.collection("leaderboard_entries").document(uid)
        try {
            val matches = userDoc.collection("matches").get().await()
            for (doc in matches.documents) {
                doc.reference.delete().await()
            }
            leaderboardDoc.delete().await()
            userDoc.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userDoc.path)
            throw e
        }
    }
}
