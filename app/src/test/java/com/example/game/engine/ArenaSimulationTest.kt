package com.example.game.engine

import com.example.data.model.AiDifficulty
import com.example.data.model.GameCatalog
import com.example.data.model.GameMode
import com.example.data.network.RemoteObstacle
import com.example.data.network.RemoteOrbState
import com.example.data.network.RemotePowerUpState
import com.example.data.network.RemoteSnakeState
import com.example.data.network.RemoteWorldPowerUp
import com.example.data.network.RemoteWorldSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

class ArenaSimulationTest {

    private fun createConfig(
        mode: GameMode = GameMode.AI_BATTLE,
        difficulty: AiDifficulty = AiDifficulty.NORMAL
    ): MatchConfig {
        return MatchConfig(
            mode = mode,
            arena = GameCatalog.arenas.first(),
            playerSkin = GameCatalog.skins.first(),
            playerName = "Tester",
            aiDifficulty = difficulty,
            botCount = 5
        )
    }

    @Test
    fun initialization_spawnsPlayerBotsOrbsAndObstacles() {
        val sim = ArenaSimulation(createConfig())
        assertEquals(6, sim.snakes.size)
        assertTrue(sim.playerSnake().isPlayer)
        assertTrue(sim.playerSnake().isAlive)
        assertTrue(sim.orbs.count { it.active } >= 90)
        assertTrue(sim.powerUps.count { it.active } >= 1)
        assertEquals(GameCatalog.arenas.first().obstacleCount, sim.obstacles.size)
    }

    @Test
    fun playerSteeringAndBoost_movesHeadSegmentsAndConsumesBoost() {
        val sim = ArenaSimulation(createConfig())
        val player = sim.playerSnake()
        val initialX = player.headX
        val initialZ = player.headZ
        val initialBoost = player.boostMeter

        sim.inputSteerActive = true
        sim.inputSteerAngleRad = (PI / 4.0).toFloat()
        sim.inputBoost = true

        repeat(25) { sim.step(0.03f) }

        val moved = hypot(player.headX - initialX, player.headZ - initialZ)
        assertTrue("Expected player to move forward, moved=$moved", moved > 5f)
        assertTrue("Expected boost meter to decrease while boosting", player.boostMeter < initialBoost)
        assertTrue("Expected segments to follow head", player.segments.size >= 14)
    }

    @Test
    fun collectingOrbs_increasesScoreAndLength() {
        val sim = ArenaSimulation(createConfig())
        val player = sim.playerSnake()
        // Place high-value orbs directly in front of player
        val forwardX = player.headX + 0.5f
        val forwardZ = player.headZ
        sim.orbs[0].apply {
            active = true
            x = forwardX
            z = forwardZ
            value = 120
            radius = 0.6f
        }

        sim.inputSteerActive = true
        sim.inputSteerAngleRad = 0f
        repeat(10) { sim.step(0.02f) }

        assertTrue("Score should grow after eating orb, score=${player.score}", player.score >= 120)
        assertTrue("Length should grow after score increase, segments=${player.targetSegments}", player.targetSegments > 14)
    }

    @Test
    fun cobraSurgeAbility_triggersShieldAndSpeedAndCooldown() {
        val sim = ArenaSimulation(createConfig())
        val player = sim.playerSnake()
        assertEquals(0f, player.abilityCooldownSec, 0.001f)

        sim.triggerPlayerAbility()

        assertTrue(player.abilityCooldownSec > 7f)
        assertTrue(player.activePowerUps.any { it.type.name == "SHIELD" })
        assertTrue(player.activePowerUps.any { it.type.name == "SPEED_BOOST" })
    }

    @Test
    fun battleRoyale_shrinksEnergyRingOverTime() {
        val sim = ArenaSimulation(createConfig(mode = GameMode.BATTLE_ROYALE))
        val initialRing = sim.currentRingRadius
        repeat(60) { sim.step(0.05f) }
        assertTrue("Expected ring radius to shrink in Battle Royale", sim.currentRingRadius < initialRing)
        assertFalse(sim.config.mode.allowRespawn)
    }

    @Test
    fun onlineAuthoritativeMode_appliesRemoteSnapshotWithoutLocalBots() {
        val sim = ArenaSimulation(
            config = createConfig(mode = GameMode.RANKED_MULTIPLAYER),
            isOnlineAuthoritative = true
        )
        assertEquals(1, sim.snakes.size)
        assertEquals(0, sim.orbs.count { it.active })
        assertEquals(0, sim.powerUps.count { it.active })

        val snapshot = RemoteWorldSnapshot(
            roomCode = "ROOM42",
            modeId = "RANKED_MULTIPLAYER",
            arenaId = "jungle_arena",
            localUid = "uid_local",
            players = 2,
            maxPlayers = 12,
            elapsedSec = 14.5f,
            timeRemainingSec = 165,
            currentRingRadius = 56f,
            arenaRadius = 56f,
            matchFinished = false,
            isVictory = false,
            deathReason = null,
            canRespawn = false,
            killFeed = listOf("LocalCobra eliminated Rival"),
            snakes = listOf(
                RemoteSnakeState(
                    uid = "uid_rival",
                    nickname = "Rival",
                    skinId = "fire_dragon",
                    teamId = 1,
                    isAlive = true,
                    connected = true,
                    headX = -10f,
                    headZ = 4f,
                    yawRad = 1.2f,
                    isBoosting = false,
                    score = 90,
                    kills = 0,
                    length = 16,
                    boostMeter = null,
                    abilityCooldownSec = null,
                    activePowerUps = emptyList(),
                    segments = listOf(-10f to 4f, -11f to 4f)
                ),
                RemoteSnakeState(
                    uid = "uid_local",
                    nickname = "LocalCobra",
                    skinId = "royal_cobra",
                    teamId = 0,
                    isAlive = true,
                    connected = true,
                    headX = 6f,
                    headZ = -3f,
                    yawRad = 0.5f,
                    isBoosting = true,
                    score = 340,
                    kills = 2,
                    length = 21,
                    boostMeter = 72f,
                    abilityCooldownSec = 3.5f,
                    activePowerUps = listOf(RemotePowerUpState("SHIELD", 4.2f)),
                    segments = listOf(6f to -3f, 5f to -3f, 4f to -3f)
                )
            ),
            orbs = listOf(RemoteOrbState(1, 8f, -2f, 25, false)),
            powerUps = listOf(RemoteWorldPowerUp(1, "SPEED_BOOST", -4f, 6f)),
            obstacles = listOf(RemoteObstacle(12f, 12f, 2.5f))
        )

        sim.applyRemoteSnapshot(snapshot)
        sim.step(0.05f)

        assertEquals(2, sim.snakes.size)
        assertEquals("uid_local", sim.playerSnake().id)
        assertEquals(340, sim.playerSnake().score)
        assertEquals(2, sim.playerSnake().kills)
        assertEquals(72f, sim.playerSnake().boostMeter, 0.01f)
        assertEquals(1, sim.orbs.count { it.active })
        assertEquals(1, sim.powerUps.count { it.active })
        assertEquals(1, sim.obstacles.size)
        assertFalse(sim.buildHudSnapshot(60, emptyList()).canRespawn)
    }
}
