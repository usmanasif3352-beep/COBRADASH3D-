package com.example.game.renderer

import android.graphics.Color
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.data.model.GraphicsQuality
import com.example.data.model.PowerUpType
import com.example.game.engine.ArenaSimulation
import com.example.game.engine.HudSnapshot
import com.example.game.engine.ProjectedFloatingText
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CobraGLRenderer(
    private val simulation: ArenaSimulation,
    private val graphicsQuality: GraphicsQuality,
    private val onHudSnapshot: (HudSnapshot) -> Unit
) : GLSurfaceView.Renderer {

    private val shader = ShaderProgram()
    private lateinit var sphereMesh: GpuMesh
    private lateinit var cobraHeadMesh: GpuMesh
    private lateinit var cylinderMesh: GpuMesh
    private lateinit var torusMesh: GpuMesh
    private lateinit var crystalMesh: GpuMesh
    private lateinit var floorDiscMesh: GpuMesh
    private lateinit var gridMesh: GpuMesh
    private lateinit var crownMesh: GpuMesh

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tmpVec = FloatArray(4)
    private val outVec = FloatArray(4)

    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var lastFrameNs = System.nanoTime()
    private var fpsAccumSec = 0f
    private var fpsFrames = 0
    private var currentFps = 60
    private var hudEmitAccum = 0f

    private var camX = 0f
    private var camY = 18f
    private var camZ = 18f
    private var lookX = 0f
    private var lookZ = 0f

    @Volatile var isPaused: Boolean = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val arena = simulation.config.arena
        val skyR = Color.red(arena.skyBottomColor) / 255f
        val skyG = Color.green(arena.skyBottomColor) / 255f
        val skyB = Color.blue(arena.skyBottomColor) / 255f
        GLES30.glClearColor(skyR, skyG, skyB, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        shader.compile()
        val slices = graphicsQuality.sphereSlices
        val stacks = (slices * 3 / 4).coerceAtLeast(8)
        sphereMesh = MeshFactory.createSphere(slices, stacks)
        cobraHeadMesh = MeshFactory.createCobraHead(slices, stacks)
        cylinderMesh = MeshFactory.createCylinder(16)
        torusMesh = MeshFactory.createTorusRing(1f, 0.032f, 56, 8)
        crystalMesh = MeshFactory.createOctahedron()
        floorDiscMesh = MeshFactory.createArenaDisc(64)
        gridMesh = MeshFactory.createArenaGridLines(simulation.arenaRadius, 6f)
        crownMesh = MeshFactory.createCrown()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        val aspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 52f, aspect, 0.5f, 220f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        val dt = ((nowNs - lastFrameNs) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
        lastFrameNs = nowNs

        fpsAccumSec += dt
        fpsFrames++
        if (fpsAccumSec >= 0.5f) {
            currentFps = (fpsFrames / fpsAccumSec).toInt().coerceIn(1, 120)
            fpsAccumSec = 0f
            fpsFrames = 0
        }

        synchronized(simulation) {
            if (!isPaused) {
                simulation.step(dt)
            }

            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            shader.use()

            val arena = simulation.config.arena
            val fogR = Color.red(arena.skyBottomColor) / 255f
            val fogG = Color.green(arena.skyBottomColor) / 255f
            val fogB = Color.blue(arena.skyBottomColor) / 255f
            GLES30.glUniform3f(shader.uFogColorLoc, fogR, fogG, fogB)

            // Smooth camera chase behind player snake
            val player = simulation.playerSnake()
            val targetLookX = player.headX + cos(player.yawRad) * 3.5f
            val targetLookZ = player.headZ + sin(player.yawRad) * 3.5f
            val zoomScale = 1f + (player.targetSegments.coerceAtMost(80) / 140f)
            val targetCamX = player.headX - cos(player.yawRad) * (13.5f * zoomScale)
            val targetCamY = 16.5f * zoomScale
            val targetCamZ = player.headZ - sin(player.yawRad) * (13.5f * zoomScale) + 6.5f

            val camLerp = 1f - kotlin.math.exp(-dt * 6.5f)
            camX += (targetCamX - camX) * camLerp
            camY += (targetCamY - camY) * camLerp
            camZ += (targetCamZ - camZ) * camLerp
            lookX += (targetLookX - lookX) * camLerp
            lookZ += (targetLookZ - lookZ) * camLerp

            Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, lookX, 0.5f, lookZ, 0f, 1f, 0f)
            Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)
            GLES30.glUniform3f(shader.uCameraPosLoc, camX, camY, camZ)

            drawArenaEnvironment()
            drawOrbs()
            drawPowerUps()
            drawSnakes()

            hudEmitAccum += dt
            if (hudEmitAccum >= 0.05f) {
                hudEmitAccum = 0f
                val projected = buildProjectedFloatingTexts()
                val snap = simulation.buildHudSnapshot(currentFps, projected)
                onHudSnapshot(snap)
            }
        }
    }

    private fun drawArenaEnvironment() {
        val arena = simulation.config.arena
        // Floor disc
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.scaleM(modelMatrix, 0, simulation.arenaRadius, 1f, simulation.arenaRadius)
        drawMesh(floorDiscMesh, arena.floorColor, arena.gridColor, 8f, 0.04f, 1f)

        // Grid lines
        Matrix.setIdentityM(modelMatrix, 0)
        drawMesh(gridMesh, arena.gridColor, arena.gridColor, 16f, 0.55f, 0.45f)

        // Outer boundary energy ring
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, 0.45f, 0f)
        Matrix.scaleM(modelMatrix, 0, simulation.currentRingRadius, 2.0f, simulation.currentRingRadius)
        drawMesh(torusMesh, arena.rimColor, arena.rimColor, 64f, 0.95f, 1f)

        // Arena obstacles (glowing 3D pillars)
        for (obs in simulation.obstacles) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, obs.x, 0f, obs.z)
            Matrix.scaleM(modelMatrix, 0, obs.radius, obs.height, obs.radius)
            drawMesh(cylinderMesh, arena.obstacleColor, arena.rimColor, 36f, 0.25f, 1f)

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, obs.x, obs.height + 0.1f, obs.z)
            Matrix.scaleM(modelMatrix, 0, obs.radius * 0.95f, 1.5f, obs.radius * 0.95f)
            drawMesh(torusMesh, arena.rimColor, arena.rimColor, 48f, 0.85f, 1f)
        }
    }

    private fun drawOrbs() {
        for (orb in simulation.orbs) {
            if (!orb.active) continue
            val pulse = 1f + sin(orb.pulsePhase) * 0.16f
            val r = orb.radius * pulse
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, orb.x, r + 0.18f, orb.z)
            Matrix.scaleM(modelMatrix, 0, r, r, r)
            drawMesh(sphereMesh, orb.color, orb.color, 48f, 0.85f, 1f)
        }
    }

    private fun drawPowerUps() {
        for (pu in simulation.powerUps) {
            if (!pu.active) continue
            val bobY = 1.25f + sin(pu.bobPhase) * 0.35f
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, pu.x, bobY, pu.z)
            Matrix.rotateM(modelMatrix, 0, pu.bobPhase * 55f, 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, 0.85f, 0.85f, 0.85f)
            drawMesh(crystalMesh, pu.type.color, pu.type.color, 72f, 0.85f, 0.95f)

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, pu.x, 0.2f, pu.z)
            Matrix.scaleM(modelMatrix, 0, 1.15f, 1f, 1.15f)
            drawMesh(torusMesh, pu.type.color, pu.type.color, 48f, 0.9f, 0.85f)
        }
    }

    private fun drawSnakes() {
        for (snake in simulation.snakes) {
            if (!snake.isAlive) continue
            val skin = snake.skin
            val isInvis = snake.hasPowerUp(PowerUpType.INVISIBILITY)
            val alpha = if (isInvis) {
                if (snake.isPlayer) 0.42f else 0.18f
            } else {
                1f
            }
            val headR = snake.headRadius()
            val yawDeg = -snake.yawRad * (180f / PI.toFloat())

            // Body segments (tail to neck)
            for (i in snake.segments.indices.reversed()) {
                val seg = snake.segments[i]
                val taper = 1f - (i.toFloat() / (snake.segments.size + 4)) * 0.45f
                val r = headR * 0.88f * taper
                val segColor = if (i % 2 == 0) skin.primaryColor else skin.secondaryColor
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, seg.x, seg.y, seg.z)
                Matrix.scaleM(modelMatrix, 0, r, r * 0.92f, r)
                drawMesh(
                    sphereMesh,
                    segColor,
                    skin.glowColor,
                    skin.material.shininess,
                    if (snake.isBoosting) skin.material.emissiveBoost + 0.35f else skin.material.emissiveBoost,
                    alpha
                )
            }

            // Sculpted 3D Cobra Head
            val headY = headR * 0.82f
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, snake.headX, headY, snake.headZ)
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, headR, headR, headR)
            drawMesh(
                cobraHeadMesh,
                skin.primaryColor,
                skin.glowColor,
                skin.material.shininess,
                skin.material.emissiveBoost + 0.15f,
                alpha
            )

            // Glowing Eyes
            val eyeOffsets = floatArrayOf(0.45f, -0.45f)
            for (side in eyeOffsets) {
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, snake.headX, headY + headR * 0.25f, snake.headZ)
                Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
                Matrix.translateM(modelMatrix, 0, headR * 0.72f, 0f, headR * side)
                Matrix.scaleM(modelMatrix, 0, headR * 0.22f, headR * 0.22f, headR * 0.22f)
                drawMesh(sphereMesh, 0xFFFFF59D.toInt(), 0xFFFFEA00.toInt(), 96f, 0.95f, alpha)
            }

            // Royal Crown for crowned skins (e.g., Royal Cobra, Golden Cobra, Emperor Cobra)
            if (skin.hasCrown) {
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, snake.headX, headY + headR * 0.72f, snake.headZ)
                Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
                Matrix.scaleM(modelMatrix, 0, headR * 0.7f, headR * 0.7f, headR * 0.7f)
                drawMesh(crownMesh, 0xFFFFD54F.toInt(), 0xFFFFAB00.toInt(), 90f, 0.55f, alpha)
            }

            // Shield / Temp Protection Aura Ring
            if (snake.hasPowerUp(PowerUpType.SHIELD) || snake.hasPowerUp(PowerUpType.TEMP_PROTECTION)) {
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, snake.headX, headY, snake.headZ)
                Matrix.scaleM(modelMatrix, 0, headR * 2.1f, 1.5f, headR * 2.1f)
                drawMesh(torusMesh, 0xFF00E5FF.toInt(), 0xFF69F0AE.toInt(), 64f, 0.9f, 0.8f)
            }
        }
    }

    private fun drawMesh(
        mesh: GpuMesh,
        baseColor: Int,
        glowColor: Int,
        shininess: Float,
        emissive: Float,
        alpha: Float
    ) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(shader.uMvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(shader.uModelMatrixLoc, 1, false, modelMatrix, 0)
        GLES30.glUniform3f(
            shader.uBaseColorLoc,
            Color.red(baseColor) / 255f,
            Color.green(baseColor) / 255f,
            Color.blue(baseColor) / 255f
        )
        GLES30.glUniform3f(
            shader.uGlowColorLoc,
            Color.red(glowColor) / 255f,
            Color.green(glowColor) / 255f,
            Color.blue(glowColor) / 255f
        )
        GLES30.glUniform1f(shader.uShininessLoc, shininess)
        GLES30.glUniform1f(shader.uEmissiveLoc, emissive)
        GLES30.glUniform1f(shader.uAlphaLoc, alpha)
        mesh.draw()
    }

    private fun buildProjectedFloatingTexts(): List<ProjectedFloatingText> {
        val list = ArrayList<ProjectedFloatingText>()
        for (ind in simulation.floatingIndicators) {
            if (!ind.active) continue
            tmpVec[0] = ind.worldX
            tmpVec[1] = ind.worldY
            tmpVec[2] = ind.worldZ
            tmpVec[3] = 1f
            Matrix.multiplyMV(outVec, 0, vpMatrix, 0, tmpVec, 0)
            if (outVec[3] > 0.1f) {
                val ndcX = outVec[0] / outVec[3]
                val ndcY = outVec[1] / outVec[3]
                val normX = (ndcX * 0.5f + 0.5f)
                val normY = (1f - (ndcY * 0.5f + 0.5f))
                if (normX in 0.05f..0.95f && normY in 0.05f..0.95f) {
                    list.add(
                        ProjectedFloatingText(
                            text = ind.text,
                            normX = normX,
                            normY = normY,
                            color = ind.color,
                            alpha = (ind.lifeSec / ind.maxLifeSec).coerceIn(0f, 1f)
                        )
                    )
                }
            }
        }
        return list
    }
}
