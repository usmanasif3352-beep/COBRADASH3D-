package com.example.game.engine

import com.example.data.model.AiDifficulty
import com.example.data.model.GameCatalog
import com.example.data.model.GameMode
import com.example.data.model.PowerUpType
import com.example.data.model.SnakeSkin
import com.example.data.network.RemoteWorldSnapshot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class ArenaSimulation(
    val config: MatchConfig,
    private val maxOrbs: Int = 140,
    val isOnlineAuthoritative: Boolean = false
) {
    private val rng = Random(System.currentTimeMillis())
    val snakes = mutableListOf<SnakeEntity>()
    val orbs = Array(maxOrbs) { EnergyOrb() }
    val powerUps = Array(6) { PowerUpPickup(id = it) }
    val floatingIndicators = Array(28) { FloatingIndicator() }
    val obstacles = mutableListOf<ArenaObstacle>()
    val killFeed = ArrayDeque<String>(4)

    var arenaRadius: Float = config.arena.arenaRadius
    var currentRingRadius: Float = config.arena.arenaRadius
    var elapsedSec: Float = 0f
    var timeRemainingSec: Float? = config.mode.defaultDurationSec?.toFloat()
    var matchFinished: Boolean = false
    var isVictory: Boolean = false
    var deathReason: String? = null
    var sensitivity: Float = 1.0f

    // Player input state (thread-safe volatile updates from Compose touch controls)
    @Volatile var inputSteerActive: Boolean = false
    @Volatile var inputSteerAngleRad: Float = 0f
    @Volatile var inputBoost: Boolean = false

    private var powerUpSpawnTimer = 4f
    private var remoteCanRespawn: Boolean = config.mode.allowRespawn
    private var lastRemotePlayerScore: Int = 0
    private var lastRemotePlayerKills: Int = 0

    init {
        generateObstacles()
        spawnPlayer()
        if (!isOnlineAuthoritative) {
            spawnBots()
            repeat(maxOrbs * 3 / 4) { spawnRandomOrb() }
            repeat(3) { spawnPowerUp() }
        }
    }

    private fun generateObstacles() {
        obstacles.clear()
        val count = config.arena.obstacleCount
        val ringDist = arenaRadius * 0.48f
        for (i in 0 until count) {
            val angle = (i.toFloat() / count) * (2f * PI.toFloat()) + 0.35f
            val r = 2.2f + (i % 2) * 0.8f
            val h = 4.2f + (i % 3) * 1.4f
            obstacles.add(
                ArenaObstacle(
                    x = cos(angle) * ringDist,
                    z = sin(angle) * ringDist,
                    radius = r,
                    height = h
                )
            )
        }
    }

    private fun spawnPlayer() {
        val player = createSnake(
            id = "player",
            name = config.playerName.ifBlank { "You" },
            isPlayer = true,
            isAi = false,
            aiDifficulty = null,
            teamId = 0,
            skin = config.playerSkin,
            spawnX = 0f,
            spawnZ = 0f
        )
        player.activePowerUps.add(ActivePowerUp(PowerUpType.TEMP_PROTECTION, 3.5f, 3.5f))
        snakes.add(player)
    }

    private fun spawnBots() {
        val botCount = when (config.mode) {
            GameMode.PRACTICE -> 4
            GameMode.TEAM_BATTLE -> 7
            else -> config.botCount
        }
        val botDiff = if (config.mode == GameMode.PRACTICE) AiDifficulty.EASY else config.aiDifficulty
        val botNames = listOf(
            "[BOT] ApexFang", "[BOT] ViperX", "[BOT] Cobalt", "[BOT] Blaze",
            "[BOT] FrostByte", "[BOT] Nova", "[BOT] Shadow", "[BOT] Hydra"
        )
        for (i in 0 until botCount) {
            val angle = (i.toFloat() / botCount) * (2f * PI.toFloat())
            val dist = arenaRadius * 0.58f
            val team = if (config.mode.isTeamMode) {
                if (i % 2 == 0) 1 else 0
            } else {
                i + 1
            }
            val skin = GameCatalog.skins[(i * 3 + 1) % GameCatalog.skins.size]
            val bot = createSnake(
                id = "bot_$i",
                name = botNames[i % botNames.size],
                isPlayer = false,
                isAi = true,
                aiDifficulty = botDiff,
                teamId = team,
                skin = skin,
                spawnX = cos(angle) * dist,
                spawnZ = sin(angle) * dist
            )
            bot.yawRad = angle + PI.toFloat()
            bot.targetYawRad = bot.yawRad
            bot.aiDesiredYaw = bot.yawRad
            snakes.add(bot)
        }
    }

    private fun createSnake(
        id: String,
        name: String,
        isPlayer: Boolean,
        isAi: Boolean,
        aiDifficulty: AiDifficulty?,
        teamId: Int,
        skin: SnakeSkin,
        spawnX: Float,
        spawnZ: Float
    ): SnakeEntity {
        val snake = SnakeEntity(
            id = id,
            name = name,
            isPlayer = isPlayer,
            isAi = isAi,
            aiDifficulty = aiDifficulty,
            teamId = teamId,
            skin = skin,
            headX = spawnX,
            headZ = spawnZ
        )
        resetSnakeBody(snake, spawnX, spawnZ, snake.yawRad)
        return snake
    }

    private fun resetSnakeBody(snake: SnakeEntity, x: Float, z: Float, yaw: Float) {
        snake.headX = x
        snake.headZ = z
        snake.yawRad = yaw
        snake.targetYawRad = yaw
        snake.targetSegments = 14
        snake.boostMeter = 100f
        snake.isBoosting = false
        snake.isAlive = true
        snake.slowTimerSec = 0f
        snake.segments.clear()
        snake.pathHistory.clear()
        val spacing = 0.72f
        val backX = -cos(yaw)
        val backZ = -sin(yaw)
        for (i in 0 until 80) {
            val px = x + backX * (i * 0.25f)
            val pz = z + backZ * (i * 0.25f)
            snake.pathHistory.addLast(Vec3(px, 0.55f, pz))
        }
        for (i in 0 until snake.targetSegments) {
            snake.segments.add(Vec3(x + backX * i * spacing, 0.55f, z + backZ * i * spacing))
        }
    }

    fun playerSnake(): SnakeEntity = snakes.first()

    fun triggerPlayerRespawn() {
        if (isOnlineAuthoritative) return
        val player = playerSnake()
        if (!config.mode.allowRespawn || matchFinished) return
        val angle = rng.nextFloat() * 2f * PI.toFloat()
        val r = (currentRingRadius * 0.35f).coerceAtLeast(6f)
        resetSnakeBody(player, cos(angle) * r, sin(angle) * r, angle + PI.toFloat())
        player.score = (player.score * 0.75f).toInt()
        player.activePowerUps.clear()
        player.activePowerUps.add(ActivePowerUp(PowerUpType.TEMP_PROTECTION, 4.0f, 4.0f))
        deathReason = null
    }

    fun triggerPlayerAbility() {
        if (isOnlineAuthoritative) return
        val player = playerSnake()
        if (!player.isAlive || player.abilityCooldownSec > 0f || matchFinished) return
        player.abilityCooldownSec = 8.0f
        player.activePowerUps.add(ActivePowerUp(PowerUpType.SPEED_BOOST, 2.5f, 2.5f))
        player.activePowerUps.add(ActivePowerUp(PowerUpType.SHIELD, 3.0f, 3.0f))
        // Venom shockwave slows nearby rivals
        for (other in snakes) {
            if (other.id == player.id || !other.isAlive) continue
            if (config.mode.isTeamMode && other.teamId == player.teamId) continue
            val d = hypot(other.headX - player.headX, other.headZ - player.headZ)
            if (d < 13f) {
                other.slowTimerSec = 2.6f
                spawnFloatingText(other.headX, 2.4f, other.headZ, "VENOM SHOCK!", 0xFF00E5FF.toInt())
            }
        }
        spawnFloatingText(player.headX, 2.6f, player.headZ, "COBRA SURGE!", 0xFF69F0AE.toInt())
    }

    fun applyRemoteSnapshot(snapshot: RemoteWorldSnapshot) {
        if (!isOnlineAuthoritative) return

        arenaRadius = snapshot.arenaRadius
        currentRingRadius = snapshot.currentRingRadius
        elapsedSec = snapshot.elapsedSec
        timeRemainingSec = snapshot.timeRemainingSec?.toFloat()
        matchFinished = snapshot.matchFinished
        isVictory = snapshot.isVictory
        deathReason = snapshot.deathReason
        remoteCanRespawn = snapshot.canRespawn

        if (snapshot.obstacles.isNotEmpty()) {
            obstacles.clear()
            snapshot.obstacles.forEachIndexed { idx, obs ->
                obstacles.add(
                    ArenaObstacle(
                        x = obs.x,
                        z = obs.z,
                        radius = obs.radius,
                        height = 4.2f + (idx % 3) * 1.4f
                    )
                )
            }
        }

        killFeed.clear()
        snapshot.killFeed.takeLast(3).forEach { killFeed.addLast(it) }

        val existingById = snakes.associateBy { it.id }
        val localSnakes = snapshot.snakes.filter { it.uid == snapshot.localUid }
        val otherSnakes = snapshot.snakes.filter { it.uid != snapshot.localUid }
        val ordered = localSnakes + otherSnakes

        if (ordered.isNotEmpty()) {
            val rebuilt = ArrayList<SnakeEntity>(ordered.size)
            for (remote in ordered) {
                val isLocal = remote.uid == snapshot.localUid
                val prev = existingById[remote.uid]
                val skin = GameCatalog.skinById(remote.skinId)
                val entity = prev ?: SnakeEntity(
                    id = remote.uid,
                    name = remote.nickname,
                    isPlayer = isLocal,
                    isAi = false,
                    aiDifficulty = null,
                    teamId = remote.teamId,
                    skin = skin
                )
                entity.headX = remote.headX
                entity.headZ = remote.headZ
                entity.yawRad = remote.yawRad
                entity.targetYawRad = remote.yawRad
                entity.isBoosting = remote.isBoosting
                entity.score = remote.score
                entity.kills = remote.kills
                entity.targetSegments = remote.length.coerceAtLeast(10)
                entity.maxLengthReached = maxOf(entity.maxLengthReached, entity.targetSegments)
                entity.isAlive = remote.isAlive
                if (remote.boostMeter != null) {
                    entity.boostMeter = remote.boostMeter
                }
                if (remote.abilityCooldownSec != null) {
                    entity.abilityCooldownSec = remote.abilityCooldownSec
                }
                entity.activePowerUps.clear()
                remote.activePowerUps.forEach { rp ->
                    val pType = PowerUpType.entries.firstOrNull { it.name == rp.type }
                    if (pType != null) {
                        entity.activePowerUps.add(
                            ActivePowerUp(pType, rp.remainingSec, pType.durationSec)
                        )
                    }
                }

                val headY = entity.headRadius() * 0.78f
                entity.segments.clear()
                val segCount = remote.segments.size.coerceAtLeast(1)
                remote.segments.forEachIndexed { idx, (sx, sz) ->
                    val segY = headY * (1f - (idx.toFloat() / (segCount + 4)) * 0.28f)
                    entity.segments.add(Vec3(sx, segY, sz))
                }

                if (isLocal) {
                    val deltaScore = remote.score - lastRemotePlayerScore
                    val deltaKills = remote.kills - lastRemotePlayerKills
                    if (deltaKills > 0 && lastRemotePlayerKills >= 0) {
                        spawnFloatingText(remote.headX, 2.7f, remote.headZ, "ELIMINATED! +150", 0xFFFFD54F.toInt())
                    } else if (deltaScore >= 20 && lastRemotePlayerScore > 0) {
                        spawnFloatingText(remote.headX, 2.2f, remote.headZ, "+$deltaScore", 0xFF69F0AE.toInt())
                    }
                    lastRemotePlayerScore = remote.score
                    lastRemotePlayerKills = remote.kills
                }

                rebuilt.add(entity)
            }
            snakes.clear()
            snakes.addAll(rebuilt)
        }

        // Apply authoritative orbs
        for (i in orbs.indices) {
            if (i < snapshot.orbs.size) {
                val ro = snapshot.orbs[i]
                val orb = orbs[i]
                orb.active = true
                orb.x = ro.x
                orb.z = ro.z
                orb.value = ro.value
                orb.isDropped = ro.isDropped
                orb.radius = if (ro.isDropped || ro.value >= 25) 0.56f else 0.40f
                orb.color = when {
                    ro.isDropped -> 0xFFFFD54F.toInt()
                    ro.value >= 25 -> 0xFF00E5FF.toInt()
                    else -> 0xFF00E676.toInt()
                }
            } else {
                orbs[i].active = false
            }
        }

        // Apply authoritative world power-ups
        for (i in powerUps.indices) {
            if (i < snapshot.powerUps.size) {
                val rpu = snapshot.powerUps[i]
                val pType = PowerUpType.entries.firstOrNull { it.name == rpu.type } ?: PowerUpType.SPEED_BOOST
                powerUps[i].active = true
                powerUps[i].type = pType
                powerUps[i].x = rpu.x
                powerUps[i].z = rpu.z
            } else {
                powerUps[i].active = false
            }
        }
    }

    fun step(rawDt: Float) {
        if (matchFinished) return
        val dt = rawDt.coerceIn(0.001f, 0.05f)
        if (isOnlineAuthoritative) {
            // Only advance purely visual animations & floating text fade in online mode;
            // authoritative server snapshots drive positions, collisions, scores, and eliminations.
            for (ind in floatingIndicators) {
                if (!ind.active) continue
                ind.lifeSec -= dt
                ind.worldY += dt * 2.1f
                if (ind.lifeSec <= 0f) ind.active = false
            }
            for (orb in orbs) {
                if (orb.active) orb.pulsePhase += dt * 3.5f
            }
            for (pu in powerUps) {
                if (pu.active) pu.bobPhase += dt * 2.8f
            }
            return
        }
        elapsedSec += dt

        if (timeRemainingSec != null) {
            timeRemainingSec = (timeRemainingSec!! - dt).coerceAtLeast(0f)
            if (timeRemainingSec!! <= 0f) {
                finishTimedMatch()
                return
            }
        }

        if (config.mode.hasShrinkingRing) {
            currentRingRadius = (arenaRadius - elapsedSec * 0.32f).coerceAtLeast(16f)
        }

        powerUpSpawnTimer -= dt
        if (powerUpSpawnTimer <= 0f) {
            powerUpSpawnTimer = 6.5f
            spawnPowerUp()
        }

        // Update floating indicators
        for (ind in floatingIndicators) {
            if (!ind.active) continue
            ind.lifeSec -= dt
            ind.worldY += dt * 2.1f
            if (ind.lifeSec <= 0f) ind.active = false
        }

        // Pulse orbs
        for (orb in orbs) {
            if (orb.active) orb.pulsePhase += dt * 3.5f
        }
        for (pu in powerUps) {
            if (pu.active) pu.bobPhase += dt * 2.8f
        }

        // Step each snake
        for (snake in snakes) {
            if (!snake.isAlive) {
                if (snake.isAi && config.mode != GameMode.CLASSIC_SURVIVAL && config.mode != GameMode.BATTLE_ROYALE) {
                    snake.respawnTimerSec -= dt
                    if (snake.respawnTimerSec <= 0f) {
                        val a = rng.nextFloat() * 2f * PI.toFloat()
                        val dist = currentRingRadius * 0.55f
                        resetSnakeBody(snake, cos(a) * dist, sin(a) * dist, a + PI.toFloat())
                        snake.activePowerUps.clear()
                        snake.activePowerUps.add(ActivePowerUp(PowerUpType.TEMP_PROTECTION, 3.0f, 3.0f))
                    }
                }
                continue
            }

            if (snake.abilityCooldownSec > 0f) {
                snake.abilityCooldownSec = (snake.abilityCooldownSec - dt).coerceAtLeast(0f)
            }
            if (snake.slowTimerSec > 0f) {
                snake.slowTimerSec = (snake.slowTimerSec - dt).coerceAtLeast(0f)
            }

            updatePowerUpTimers(snake, dt)

            if (snake.isPlayer) {
                if (inputSteerActive) {
                    snake.targetYawRad = inputSteerAngleRad
                } else {
                    snake.targetYawRad = snake.yawRad
                }
                snake.isBoosting = inputBoost && snake.boostMeter > 4f
            } else if (snake.isAi) {
                updateAiSteering(snake, dt)
            }

            moveSnake(snake, dt)
            collectNearbyOrbs(snake, dt)
            collectNearbyPowerUps(snake)
        }

        resolveCollisions()
        maintainOrbPopulation()
        checkMatchEndConditions()
    }

    private fun updatePowerUpTimers(snake: SnakeEntity, dt: Float) {
        val iter = snake.activePowerUps.iterator()
        while (iter.hasNext()) {
            val pu = iter.next()
            pu.remainingSec -= dt
            if (pu.remainingSec <= 0f) iter.remove()
        }
        if (snake.hasPowerUp(PowerUpType.LIGHTNING) || snake.hasPowerUp(PowerUpType.FREEZE)) {
            val isFreeze = snake.hasPowerUp(PowerUpType.FREEZE)
            for (other in snakes) {
                if (other.id == snake.id || !other.isAlive) continue
                if (config.mode.isTeamMode && other.teamId == snake.teamId) continue
                val dist = hypot(other.headX - snake.headX, other.headZ - snake.headZ)
                if (dist < 13f && other.slowTimerSec <= 0.1f) {
                    other.slowTimerSec = if (isFreeze) 1.5f else 1.2f
                }
            }
        }
    }

    private fun moveSnake(snake: SnakeEntity, dt: Float) {
        val turnSpeed = (if (snake.isPlayer) 4.4f * sensitivity.coerceIn(0.6f, 1.8f) else 4.0f)
        val diff = normalizeAngle(snake.targetYawRad - snake.yawRad)
        val maxTurn = turnSpeed * dt
        snake.yawRad = if (abs(diff) <= maxTurn) {
            snake.targetYawRad
        } else {
            normalizeAngle(snake.yawRad + if (diff > 0f) maxTurn else -maxTurn)
        }

        var speed = snake.baseSpeed
        if (snake.hasPowerUp(PowerUpType.SPEED_BOOST)) speed *= 1.42f
        if (snake.slowTimerSec > 0f) speed *= 0.58f

        if (snake.isBoosting && snake.boostMeter > 1f) {
            speed *= 1.62f
            if (!snake.hasPowerUp(PowerUpType.SPEED_BOOST)) {
                snake.boostMeter = (snake.boostMeter - 26f * dt).coerceAtLeast(0f)
                snake.dropTrailAccum += dt
                if (snake.dropTrailAccum >= 0.35f && snake.targetSegments > 10) {
                    snake.dropTrailAccum = 0f
                    val tail = snake.segments.lastOrNull()
                    if (tail != null) {
                        spawnOrbAt(tail.x, tail.z, 8, snake.skin.glowColor, 0.36f, isDropped = true)
                    }
                }
            }
        } else {
            snake.isBoosting = false
            snake.boostMeter = (snake.boostMeter + 12f * dt).coerceAtMost(100f)
        }

        snake.headX += cos(snake.yawRad) * speed * dt
        snake.headZ += sin(snake.yawRad) * speed * dt

        // Record fine head path
        val first = snake.pathHistory.firstOrNull()
        val headY = snake.headRadius() * 0.78f
        if (first == null || hypot(snake.headX - first.x, snake.headZ - first.z) >= 0.22f) {
            snake.pathHistory.addFirst(Vec3(snake.headX, headY, snake.headZ))
            val maxHistory = (snake.targetSegments * 5).coerceAtLeast(80)
            while (snake.pathHistory.size > maxHistory) {
                snake.pathHistory.removeLast()
            }
        }

        // Rebuild segment positions smoothly along pathHistory
        while (snake.segments.size < snake.targetSegments) {
            val last = snake.segments.lastOrNull() ?: Vec3(snake.headX, headY, snake.headZ)
            snake.segments.add(Vec3(last.x, headY, last.z))
        }
        while (snake.segments.size > snake.targetSegments && snake.segments.size > 6) {
            snake.segments.removeAt(snake.segments.lastIndex)
        }

        val spacing = snake.headRadius() * 1.18f
        var histIdx = 0
        var accumDist = 0f
        var prevPt = Vec3(snake.headX, headY, snake.headZ)
        for (segIdx in snake.segments.indices) {
            val targetDist = (segIdx + 1) * spacing
            while (histIdx < snake.pathHistory.size) {
                val pt = snake.pathHistory.elementAt(histIdx)
                val stepD = hypot(pt.x - prevPt.x, pt.z - prevPt.z)
                if (accumDist + stepD >= targetDist) {
                    val t = if (stepD > 0.0001f) (targetDist - accumDist) / stepD else 0f
                    snake.segments[segIdx].x = prevPt.x + (pt.x - prevPt.x) * t
                    snake.segments[segIdx].y = headY * (1f - (segIdx.toFloat() / (snake.segments.size + 4)) * 0.28f)
                    snake.segments[segIdx].z = prevPt.z + (pt.z - prevPt.z) * t
                    break
                } else {
                    accumDist += stepD
                    prevPt = pt
                    histIdx++
                }
            }
        }
    }

    private fun collectNearbyOrbs(snake: SnakeEntity, dt: Float) {
        val magnetRange = if (snake.hasPowerUp(PowerUpType.MAGNET)) 11.5f else 2.2f
        val eatRange = snake.headRadius() + 0.72f
        val mult = if (snake.hasPowerUp(PowerUpType.DOUBLE_ENERGY)) 2 else 1

        for (orb in orbs) {
            if (!orb.active) continue
            val dx = snake.headX - orb.x
            val dz = snake.headZ - orb.z
            val dist = hypot(dx, dz)
            if (dist < magnetRange && dist > 0.01f) {
                val pullSpeed = if (snake.hasPowerUp(PowerUpType.MAGNET)) 22f else 9f
                orb.x += (dx / dist) * pullSpeed * dt
                orb.z += (dz / dist) * pullSpeed * dt
            }
            if (dist <= eatRange) {
                orb.active = false
                val pts = orb.value * mult
                snake.score += pts
                snake.boostMeter = (snake.boostMeter + 3.5f).coerceAtMost(100f)
                val newTarget = (14 + snake.score / 45).coerceAtMost(95)
                snake.targetSegments = newTarget
                if (newTarget > snake.maxLengthReached) snake.maxLengthReached = newTarget
                if (snake.isPlayer && (orb.isDropped || pts >= 20)) {
                    spawnFloatingText(snake.headX, 2.1f, snake.headZ, "+$pts", orb.color)
                }
            }
        }
    }

    private fun collectNearbyPowerUps(snake: SnakeEntity) {
        val reach = snake.headRadius() + 1.35f
        for (pu in powerUps) {
            if (!pu.active) continue
            val dist = hypot(snake.headX - pu.x, snake.headZ - pu.z)
            if (dist <= reach) {
                pu.active = false
                applyPowerUp(snake, pu.type)
            }
        }
    }

    private fun applyPowerUp(snake: SnakeEntity, type: PowerUpType) {
        if (type == PowerUpType.TELEPORT) {
            val destX = (snake.headX + cos(snake.yawRad) * 12f)
                .coerceIn(-currentRingRadius * 0.85f, currentRingRadius * 0.85f)
            val destZ = (snake.headZ + sin(snake.yawRad) * 12f)
                .coerceIn(-currentRingRadius * 0.85f, currentRingRadius * 0.85f)
            snake.headX = destX
            snake.headZ = destZ
            snake.activePowerUps.add(ActivePowerUp(PowerUpType.TEMP_PROTECTION, 2.5f, 2.5f))
        } else {
            snake.activePowerUps.removeAll { it.type == type }
            snake.activePowerUps.add(ActivePowerUp(type, type.durationSec, type.durationSec))
        }
        if (snake.isPlayer) {
            spawnFloatingText(snake.headX, 2.5f, snake.headZ, type.label.uppercase(), type.color)
        }
    }

    private fun resolveCollisions() {
        for (snake in snakes) {
            if (!snake.isAlive) continue
            val headR = snake.headRadius()
            val protected = snake.hasPowerUp(PowerUpType.SHIELD) || snake.hasPowerUp(PowerUpType.TEMP_PROTECTION)

            // 1. Arena boundary / Battle Royale ring
            val distFromCenter = hypot(snake.headX, snake.headZ)
            if (distFromCenter + headR >= currentRingRadius) {
                if (config.mode == GameMode.PRACTICE || protected) {
                    // Bounce safely inward
                    val inward = atan2(-snake.headZ, -snake.headX)
                    snake.yawRad = inward
                    snake.targetYawRad = inward
                    val clampR = currentRingRadius - headR - 0.6f
                    if (distFromCenter > 0.01f) {
                        snake.headX = (snake.headX / distFromCenter) * clampR
                        snake.headZ = (snake.headZ / distFromCenter) * clampR
                    }
                } else {
                    eliminateSnake(snake, null, "Struck the Arena Energy Barrier")
                    continue
                }
            }

            // 2. Arena obstacles
            for (obs in obstacles) {
                val dx = snake.headX - obs.x
                val dz = snake.headZ - obs.z
                val d = hypot(dx, dz)
                if (d < obs.radius + headR) {
                    if (protected || config.mode == GameMode.PRACTICE) {
                        val pushAngle = atan2(dz, dx)
                        snake.headX = obs.x + cos(pushAngle) * (obs.radius + headR + 0.3f)
                        snake.headZ = obs.z + sin(pushAngle) * (obs.radius + headR + 0.3f)
                        snake.yawRad = pushAngle
                    } else {
                        eliminateSnake(snake, null, "Crashed into an Arena Pillar")
                        break
                    }
                }
            }
            if (!snake.isAlive) continue

            // 3. Snake-to-snake collisions
            for (other in snakes) {
                if (other.id == snake.id || !other.isAlive) continue
                if (config.mode.isTeamMode && other.teamId == snake.teamId) continue

                // Head-to-head
                val hhDist = hypot(snake.headX - other.headX, snake.headZ - other.headZ)
                if (hhDist < headR + other.headRadius() * 0.9f) {
                    if (!protected && snake.targetSegments <= other.targetSegments) {
                        eliminateSnake(snake, other, "Head-on clash with ${other.name}")
                        break
                    }
                }

                // Head-to-body segments
                var hitBody = false
                for (i in other.segments.indices) {
                    val seg = other.segments[i]
                    val segR = other.headRadius() * 0.85f
                    if (hypot(snake.headX - seg.x, snake.headZ - seg.z) < headR + segR) {
                        hitBody = true
                        break
                    }
                }
                if (hitBody) {
                    if (snake.hasPowerUp(PowerUpType.SHIELD)) {
                        snake.activePowerUps.removeAll { it.type == PowerUpType.SHIELD }
                        snake.activePowerUps.add(ActivePowerUp(PowerUpType.TEMP_PROTECTION, 1.5f, 1.5f))
                        if (snake.isPlayer) {
                            spawnFloatingText(snake.headX, 2.4f, snake.headZ, "SHIELD SAVED!", 0xFF69F0AE.toInt())
                        }
                    } else if (!snake.hasPowerUp(PowerUpType.TEMP_PROTECTION)) {
                        eliminateSnake(snake, other, "Eliminated by ${other.name}")
                        break
                    }
                }
            }
        }
    }

    private fun eliminateSnake(victim: SnakeEntity, killer: SnakeEntity?, reason: String) {
        victim.isAlive = false
        victim.respawnTimerSec = 4.5f
        victim.isBoosting = false

        // Drop vibrant high-value energy orbs along victim's body
        val step = (victim.segments.size / 14).coerceAtLeast(1)
        for (i in victim.segments.indices step step) {
            val s = victim.segments[i]
            spawnOrbAt(s.x, s.z, 35, victim.skin.primaryColor, 0.62f, isDropped = true)
        }

        if (killer != null) {
            killer.kills += 1
            killer.score += 150
            addKillFeed("${killer.name} eliminated ${victim.name}")
            if (killer.isPlayer) {
                spawnFloatingText(victim.headX, 2.6f, victim.headZ, "ELIMINATED! +150", 0xFFFFD54F.toInt())
            }
        } else {
            addKillFeed("${victim.name} hit a hazard")
        }

        if (victim.isPlayer) {
            deathReason = reason
            if (!config.mode.allowRespawn) {
                matchFinished = true
                isVictory = false
            }
        }
    }

    private fun updateAiSteering(bot: SnakeEntity, dt: Float) {
        val diff = bot.aiDifficulty ?: AiDifficulty.NORMAL
        bot.aiDecisionTimerSec -= dt
        if (bot.aiDecisionTimerSec > 0f) {
            bot.targetYawRad = bot.aiDesiredYaw
            return
        }
        bot.aiDecisionTimerSec = (0.36f - diff.reactionRate * 0.24f).coerceAtLeast(0.08f)

        // 1. Hazard avoidance (boundary + obstacles + enemy bodies)
        val distCenter = hypot(bot.headX, bot.headZ)
        if (distCenter > currentRingRadius * 0.78f) {
            bot.aiDesiredYaw = atan2(-bot.headZ, -bot.headX)
            bot.targetYawRad = bot.aiDesiredYaw
            bot.isBoosting = false
            return
        }

        var avoidX = 0f
        var avoidZ = 0f
        val lookDist = 6.5f + diff.reactionRate * 5.5f

        for (obs in obstacles) {
            val dx = bot.headX - obs.x
            val dz = bot.headZ - obs.z
            val d = hypot(dx, dz)
            if (d < obs.radius + lookDist && d > 0.01f) {
                val weight = (obs.radius + lookDist - d) / lookDist
                avoidX += (dx / d) * weight * 3f
                avoidZ += (dz / d) * weight * 3f
            }
        }

        for (other in snakes) {
            if (other.id == bot.id || !other.isAlive) continue
            for (i in other.segments.indices step 2) {
                val seg = other.segments[i]
                val dx = bot.headX - seg.x
                val dz = bot.headZ - seg.z
                val d = hypot(dx, dz)
                if (d < lookDist && d > 0.01f) {
                    val weight = (lookDist - d) / lookDist
                    avoidX += (dx / d) * weight * 3.5f
                    avoidZ += (dz / d) * weight * 3.5f
                }
            }
        }

        if (hypot(avoidX, avoidZ) > 0.4f) {
            bot.aiDesiredYaw = atan2(avoidZ, avoidX)
            bot.targetYawRad = bot.aiDesiredYaw
            bot.isBoosting = bot.boostMeter > 45f && rng.nextFloat() < diff.boostTendency
            return
        }

        // 2. Aggressive trap/chase behavior on higher difficulties
        val player = playerSnake()
        if (player.isAlive && rng.nextFloat() < diff.aggression) {
            val dPlayer = hypot(player.headX - bot.headX, player.headZ - bot.headZ)
            if (dPlayer in 6f..22f && bot.targetSegments >= player.targetSegments * 0.8f) {
                // Aim ahead of player's head to cut off / trap!
                val interceptX = player.headX + cos(player.yawRad) * 6.5f
                val interceptZ = player.headZ + sin(player.yawRad) * 6.5f
                bot.aiDesiredYaw = atan2(interceptZ - bot.headZ, interceptX - bot.headX)
                bot.targetYawRad = bot.aiDesiredYaw
                bot.isBoosting = bot.boostMeter > 35f && dPlayer < 14f && rng.nextFloat() < diff.boostTendency
                return
            }
        }

        // 3. Otherwise seek highest-value nearby orb
        var bestOrb: EnergyOrb? = null
        var bestScore = -1f
        for (orb in orbs) {
            if (!orb.active) continue
            val d = hypot(orb.x - bot.headX, orb.z - bot.headZ)
            val s = orb.value / (d + 2f)
            if (s > bestScore) {
                bestScore = s
                bestOrb = orb
            }
        }
        bot.isBoosting = false
        if (bestOrb != null) {
            bot.aiDesiredYaw = atan2(bestOrb.z - bot.headZ, bestOrb.x - bot.headX)
            bot.targetYawRad = bot.aiDesiredYaw
        }
    }

    private fun checkMatchEndConditions() {
        val player = playerSnake()
        if (config.mode == GameMode.CLASSIC_SURVIVAL || config.mode == GameMode.BATTLE_ROYALE) {
            val aliveCount = snakes.count { it.isAlive }
            if (player.isAlive && aliveCount <= 1) {
                matchFinished = true
                isVictory = true
            }
        } else if (config.mode == GameMode.AI_BATTLE && player.score >= 3000) {
            matchFinished = true
            isVictory = true
        }
    }

    private fun finishTimedMatch() {
        matchFinished = true
        val player = playerSnake()
        if (config.mode.isTeamMode) {
            val emerald = snakes.filter { it.teamId == 0 }.sumOf { it.score }
            val crimson = snakes.filter { it.teamId == 1 }.sumOf { it.score }
            isVictory = emerald >= crimson
        } else {
            val topScore = snakes.maxOfOrNull { it.score } ?: 0
            isVictory = player.isAlive && player.score >= topScore * 0.8f
        }
    }

    private fun maintainOrbPopulation() {
        val activeCount = orbs.count { it.active }
        if (activeCount < maxOrbs * 3 / 4) {
            spawnRandomOrb()
        }
    }

    private fun spawnRandomOrb() {
        val a = rng.nextFloat() * 2f * PI.toFloat()
        val r = rng.nextFloat() * (currentRingRadius * 0.88f)
        val colors = intArrayOf(
            0xFF00E676.toInt(),
            0xFF00E5FF.toInt(),
            0xFFFFD54F.toInt(),
            0xFFFF4081.toInt(),
            0xFFBB86FC.toInt()
        )
        val c = colors[rng.nextInt(colors.size)]
        val value = if (rng.nextFloat() < 0.12f) 25 else 10
        val rad = if (value >= 25) 0.56f else 0.40f
        spawnOrbAt(cos(a) * r, sin(a) * r, value, c, rad, isDropped = false)
    }

    private fun spawnOrbAt(x: Float, z: Float, value: Int, color: Int, radius: Float, isDropped: Boolean) {
        val slot = orbs.firstOrNull { !it.active } ?: return
        slot.active = true
        slot.x = x
        slot.z = z
        slot.value = value
        slot.color = color
        slot.radius = radius
        slot.isDropped = isDropped
        slot.pulsePhase = rng.nextFloat() * 6.28f
    }

    private fun spawnPowerUp() {
        val slot = powerUps.firstOrNull { !it.active } ?: return
        val a = rng.nextFloat() * 2f * PI.toFloat()
        val r = rng.nextFloat() * (currentRingRadius * 0.72f)
        slot.active = true
        slot.type = PowerUpType.entries[rng.nextInt(PowerUpType.entries.size)]
        slot.x = cos(a) * r
        slot.z = sin(a) * r
        slot.bobPhase = 0f
    }

    private fun spawnFloatingText(x: Float, y: Float, z: Float, text: String, color: Int) {
        val slot = floatingIndicators.firstOrNull { !it.active } ?: return
        slot.active = true
        slot.worldX = x
        slot.worldY = y
        slot.worldZ = z
        slot.text = text
        slot.color = color
        slot.lifeSec = 1.15f
        slot.maxLifeSec = 1.15f
    }

    private fun addKillFeed(msg: String) {
        if (killFeed.size >= 3) killFeed.removeFirst()
        killFeed.addLast(msg)
    }

    fun buildHudSnapshot(
        fps: Int,
        projectedTexts: List<ProjectedFloatingText>
    ): HudSnapshot {
        val player = playerSnake()
        val sorted = snakes.sortedByDescending { it.score }
        val rank = (sorted.indexOfFirst { it.id == player.id } + 1).coerceAtLeast(1)
        val emeraldScore = snakes.filter { it.teamId == 0 }.sumOf { it.score }
        val crimsonScore = snakes.filter { it.teamId == 1 }.sumOf { it.score }

        return HudSnapshot(
            playerScore = player.score,
            playerLength = player.targetSegments,
            playerKills = player.kills,
            boostPercent = (player.boostMeter / 100f).coerceIn(0f, 1f),
            abilityCooldownSec = player.abilityCooldownSec,
            activePowerUps = player.activePowerUps.toList(),
            playerRank = rank,
            totalAlive = snakes.count { it.isAlive },
            leaderboard = sorted.take(5).map { SnakeRankEntry(it.name, it.score, it.isPlayer, it.isAi) },
            minimapDots = snakes.filter { it.isAlive }.map { s ->
                MinimapDot(
                    normX = (s.headX / arenaRadius).coerceIn(-1f, 1f),
                    normZ = (s.headZ / arenaRadius).coerceIn(-1f, 1f),
                    isPlayer = s.isPlayer,
                    teamId = s.teamId,
                    color = s.skin.primaryColor
                )
            },
            floatingTexts = projectedTexts,
            killFeed = killFeed.toList(),
            fps = fps,
            timeRemainingSec = timeRemainingSec?.toInt(),
            elapsedSec = elapsedSec.toInt(),
            currentRingRadius = currentRingRadius,
            arenaRadius = arenaRadius,
            playerAlive = player.isAlive,
            matchFinished = matchFinished,
            isVictory = isVictory,
            deathReason = deathReason,
            canRespawn = if (isOnlineAuthoritative) remoteCanRespawn && !matchFinished else config.mode.allowRespawn && !matchFinished,
            emeraldTeamScore = emeraldScore,
            crimsonTeamScore = crimsonScore
        )
    }

    private fun normalizeAngle(a: Float): Float {
        var r = a
        val twoPi = (2.0 * PI).toFloat()
        while (r > PI) r -= twoPi
        while (r < -PI) r += twoPi
        return r
    }
}
