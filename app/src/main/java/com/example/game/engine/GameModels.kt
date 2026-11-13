package com.example.game.engine

import com.example.data.model.AiDifficulty
import com.example.data.model.ArenaMap
import com.example.data.model.GameMode
import com.example.data.model.PowerUpType
import com.example.data.model.SnakeSkin

data class Vec3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
)

data class ActivePowerUp(
    val type: PowerUpType,
    var remainingSec: Float,
    val maxSec: Float
)

data class ArenaObstacle(
    val x: Float,
    val z: Float,
    val radius: Float,
    val height: Float
)

class EnergyOrb(
    var active: Boolean = false,
    var x: Float = 0f,
    var z: Float = 0f,
    var value: Int = 10,
    var color: Int = 0xFF00E676.toInt(),
    var radius: Float = 0.42f,
    var isDropped: Boolean = false,
    var pulsePhase: Float = 0f
)

class PowerUpPickup(
    val id: Int,
    var active: Boolean = false,
    var type: PowerUpType = PowerUpType.SPEED_BOOST,
    var x: Float = 0f,
    var z: Float = 0f,
    var bobPhase: Float = 0f
)

class FloatingIndicator(
    var active: Boolean = false,
    var worldX: Float = 0f,
    var worldY: Float = 0f,
    var worldZ: Float = 0f,
    var text: String = "",
    var color: Int = 0xFF69F0AE.toInt(),
    var lifeSec: Float = 0f,
    var maxLifeSec: Float = 1.1f
)

class SnakeEntity(
    val id: String,
    val name: String,
    val isPlayer: Boolean,
    val isAi: Boolean,
    val aiDifficulty: AiDifficulty? = null,
    val teamId: Int = 0,
    val skin: SnakeSkin,
    var headX: Float = 0f,
    var headZ: Float = 0f,
    var yawRad: Float = 0f,
    var targetYawRad: Float = 0f,
    var baseSpeed: Float = 10.5f,
    var boostMeter: Float = 100f,
    var isBoosting: Boolean = false,
    var score: Int = 0,
    var kills: Int = 0,
    var targetSegments: Int = 14,
    var maxLengthReached: Int = 14,
    var isAlive: Boolean = true,
    var respawnTimerSec: Float = 0f,
    var abilityCooldownSec: Float = 0f,
    var slowTimerSec: Float = 0f,
    var aiDecisionTimerSec: Float = 0f,
    var aiDesiredYaw: Float = 0f,
    var dropTrailAccum: Float = 0f
) {
    val segments: MutableList<Vec3> = ArrayList(64)
    val pathHistory: ArrayDeque<Vec3> = ArrayDeque(256)
    val activePowerUps: MutableList<ActivePowerUp> = ArrayList(4)

    fun hasPowerUp(type: PowerUpType): Boolean = activePowerUps.any { it.type == type }

    fun headRadius(): Float = (0.72f + (targetSegments.coerceAtMost(80) * 0.0045f))
}

data class MinimapDot(
    val normX: Float,
    val normZ: Float,
    val isPlayer: Boolean,
    val teamId: Int,
    val color: Int
)

data class ProjectedFloatingText(
    val text: String,
    val normX: Float,
    val normY: Float,
    val color: Int,
    val alpha: Float
)

data class SnakeRankEntry(
    val name: String,
    val score: Int,
    val isPlayer: Boolean,
    val isAi: Boolean
)

data class MatchConfig(
    val mode: GameMode,
    val arena: ArenaMap,
    val playerSkin: SnakeSkin,
    val playerName: String,
    val aiDifficulty: AiDifficulty = AiDifficulty.NORMAL,
    val botCount: Int = 7
)

data class HudSnapshot(
    val playerScore: Int = 0,
    val playerLength: Int = 14,
    val playerKills: Int = 0,
    val boostPercent: Float = 1f,
    val abilityCooldownSec: Float = 0f,
    val activePowerUps: List<ActivePowerUp> = emptyList(),
    val playerRank: Int = 1,
    val totalAlive: Int = 1,
    val leaderboard: List<SnakeRankEntry> = emptyList(),
    val minimapDots: List<MinimapDot> = emptyList(),
    val floatingTexts: List<ProjectedFloatingText> = emptyList(),
    val killFeed: List<String> = emptyList(),
    val fps: Int = 60,
    val timeRemainingSec: Int? = null,
    val elapsedSec: Int = 0,
    val currentRingRadius: Float = 56f,
    val arenaRadius: Float = 56f,
    val playerAlive: Boolean = true,
    val matchFinished: Boolean = false,
    val isVictory: Boolean = false,
    val deathReason: String? = null,
    val canRespawn: Boolean = true,
    val emeraldTeamScore: Int = 0,
    val crimsonTeamScore: Int = 0
)
