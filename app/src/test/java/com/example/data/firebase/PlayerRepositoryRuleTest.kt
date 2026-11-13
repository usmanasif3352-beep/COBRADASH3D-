package com.example.data.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlayerRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureProfileInitialized_andRecordMatch_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = PlayerRepository(firestore, auth)

        val profile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureProfileInitialized("AliceViper")
        }
        assertEquals(uid, profile.userId)
        assertNotNull(profile.createdAt)

        val updated = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.recordMatch(
                currentProfile = profile,
                mode = "AI_BATTLE",
                arenaId = "jungle_arena",
                skinId = "royal_cobra",
                score = 2400L,
                kills = 5L,
                maxLength = 42L,
                durationSeconds = 120L,
                victory = true
            )
        }
        assertTrue(updated.highScore >= 2400L)

        val matches = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMatchHistory(uid).first { it.isNotEmpty() }
        }
        assertTrue(matches.any { it.score == 2400L && it.mode == "AI_BATTLE" })

        val leaderboard = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeLeaderboard().first { list -> list.any { it.userId == uid } }
        }
        assertTrue(leaderboard.any { it.userId == uid })
    }

    @Test
    fun observeProfile_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = PlayerRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureProfileInitialized("AliceViper")
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = PlayerRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeProfile(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's profile")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }
    }

    @Test
    fun observeLeaderboard_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = PlayerRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeLeaderboard().first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_cobra@test.com"
        const val BOB_EMAIL = "bob_cobra@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
