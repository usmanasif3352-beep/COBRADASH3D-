package com.example.game.engine

import com.example.data.model.AiDifficulty
import com.example.data.model.GameCatalog
import com.example.data.model.GameMode
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
}
