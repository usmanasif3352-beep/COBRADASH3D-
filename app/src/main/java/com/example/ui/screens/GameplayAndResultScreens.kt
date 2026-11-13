package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.Handedness
import com.example.data.model.PlayerSettings
import com.example.game.engine.ArenaSimulation
import com.example.game.engine.HudSnapshot
import com.example.game.engine.MatchConfig
import com.example.game.renderer.CobraGLSurfaceView
import com.example.monetization.AdManager
import com.example.ui.components.CobraBackdrop
import com.example.ui.components.CobraPrimaryButton
import com.example.ui.theme.BorderEmerald
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldCore
import com.example.ui.theme.SolarGold
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian
import com.example.ui.viewmodel.MatchResultSummary
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun GameplayScreen(
    matchConfig: MatchConfig,
    playerLevel: Long,
    settings: PlayerSettings,
    isOnlineConnected: Boolean,
    onMatchFinished: (
        score: Int,
        kills: Int,
        maxLength: Int,
        durationSec: Int,
        isVictory: Boolean,
        deathReason: String?
    ) -> Unit,
    onExitToMenu: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val haptics = LocalHapticFeedback.current

    // Enforce landscape & fullscreen immersive mode during active 3D gameplay
    DisposableEffect(activity) {
        val prevOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = prevOrientation
        }
    }

    val simulation = remember(matchConfig) {
        ArenaSimulation(
            config = matchConfig,
            maxOrbs = settings.graphicsQuality.maxOrbs
        ).apply {
            sensitivity = settings.joystickSensitivity
        }
    }

    LaunchedEffect(settings.joystickSensitivity) {
        simulation.sensitivity = settings.joystickSensitivity
    }

    var hud by remember { mutableStateOf(HudSnapshot()) }
    var isPaused by remember { mutableStateOf(false) }
    var glViewRef by remember { mutableStateOf<CobraGLSurfaceView?>(null) }
    var resultDispatched by remember { mutableStateOf(false) }

    LaunchedEffect(isPaused) {
        glViewRef?.cobraRenderer?.isPaused = isPaused
    }

    LaunchedEffect(hud.matchFinished) {
        if (hud.matchFinished && !resultDispatched) {
            resultDispatched = true
            val p = synchronized(simulation) { simulation.playerSnake() }
            onMatchFinished(
                p.score,
                p.kills,
                p.maxLengthReached,
                hud.elapsedSec,
                hud.isVictory,
                hud.deathReason
            )
        }
    }

    BackHandler {
        isPaused = !isPaused
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, glViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> glViewRef?.onPause()
                Lifecycle.Event.ON_RESUME -> glViewRef?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glViewRef?.onPause()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        // Real OpenGL ES 3.0 3D Surface
        AndroidView(
            factory = { ctx ->
                var viewRef: CobraGLSurfaceView? = null
                CobraGLSurfaceView(
                    context = ctx,
                    simulation = simulation,
                    graphicsQuality = settings.graphicsQuality,
                    onHudSnapshot = { snap -> viewRef?.post { hud = snap } }
                ).also {
                    viewRef = it
                    glViewRef = it
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating 3D-projected Score & Ability Indicators
        hud.floatingTexts.forEach { ft ->
            val px = (ft.normX * screenW).roundToInt()
            val py = (ft.normY * screenH).roundToInt()
            Text(
                text = ft.text,
                style = MaterialTheme.typography.titleMedium,
                color = Color(ft.color).copy(alpha = ft.alpha),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset { IntOffset(px - 60, py - 24) }
            )
        }

        // Top HUD Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left: Score, Level, Length, Rank, FPS, Connection
            Surface(
                color = VoidObsidian.copy(alpha = 0.72f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderEmerald)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SCORE ${hud.playerScore}", style = MaterialTheme.typography.titleLarge, color = SolarGold)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("LEN ${hud.playerLength}", style = MaterialTheme.typography.labelLarge, color = EmeraldCore)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("RANK #${hud.playerRank}/${hud.totalAlive}", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("LVL $playerLevel • KILLS ${hud.playerKills}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("${hud.fps} FPS", style = MaterialTheme.typography.labelSmall, color = EmeraldCore)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isOnlineConnected) "WS ONLINE" else "LOCAL AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOnlineConnected) CyberCyan else SolarGold
                        )
                        if (hud.timeRemainingSec != null) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("${hud.timeRemainingSec}s", style = MaterialTheme.typography.labelLarge, color = CrimsonDanger)
                        }
                    }
                    if (matchConfig.mode.isTeamMode) {
                        Text(
                            text = "EMERALD ${hud.emeraldTeamScore} vs CRIMSON ${hud.crimsonTeamScore}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldCore
                        )
                    }
                }
            }

            // Active Power-Ups & Kill Feed
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    hud.activePowerUps.forEach { pu ->
                        Surface(
                            color = Color(pu.type.color).copy(alpha = 0.22f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(pu.type.color))
                        ) {
                            Text(
                                text = "${pu.type.shortCode} ${"%.1f".format(pu.remainingSec)}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                hud.killFeed.lastOrNull()?.let { event ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(color = VoidObsidian.copy(alpha = 0.65f), shape = RoundedCornerShape(8.dp)) {
                        Text(event, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }

            // Right: Top Leaderboard + Mini-Map + Pause Button
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = VoidObsidian.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderEmerald)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).widthIn(min = 128.dp)) {
                        hud.leaderboard.forEachIndexed { idx, row ->
                            Text(
                                text = "${idx + 1}. ${row.name.take(12)} ${row.score}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (row.isPlayer) EmeraldCore else TextSecondary
                            )
                        }
                    }
                }

                // Mini-Map Radar
                Surface(
                    color = VoidObsidian.copy(alpha = 0.75f),
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, EmeraldCore),
                    modifier = Modifier.size(72.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        val r = size.minDimension / 2f
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val ringRatio = (hud.currentRingRadius / hud.arenaRadius).coerceIn(0.1f, 1f)
                        drawCircle(color = CrimsonDanger.copy(alpha = 0.65f), radius = r * ringRatio, center = c, style = Stroke(width = 1.5.dp.toPx()))
                        hud.minimapDots.forEach { dot ->
                            val dx = c.x + dot.normX * (r * 0.9f)
                            val dz = c.y + dot.normZ * (r * 0.9f)
                            drawCircle(
                                color = if (dot.isPlayer) Color.White else Color(dot.color),
                                radius = if (dot.isPlayer) 4.dp.toPx() else 2.5.dp.toPx(),
                                center = Offset(dx, dz)
                            )
                        }
                    }
                }

                Surface(
                    color = VoidObsidian.copy(alpha = 0.78f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, EmeraldCore)
                ) {
                    IconButton(
                        onClick = { isPaused = true },
                        modifier = Modifier.size(48.dp).testTag("hud_pause_button")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause Game", tint = EmeraldCore)
                    }
                }
            }
        }

        // Bottom Controls (Virtual Joystick + Boost + Special Ability, supporting Left/Right Handedness)
        val isLeftHanded = settings.handedness == Handedness.LEFT_HANDED
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            val joystickBlock: @Composable () -> Unit = {
                VirtualJoystick(
                    onSteer = { active, angleRad ->
                        simulation.inputSteerActive = active
                        simulation.inputSteerAngleRad = angleRad
                    }
                )
            }
            val actionButtonsBlock: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Boost Meter Bar
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BOOST", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        LinearProgressIndicator(
                            progress = { hud.boostPercent },
                            modifier = Modifier.width(96.dp).height(7.dp),
                            color = CyberCyan,
                            trackColor = CardSurface
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Special Ability Button (Cobra Surge)
                        val abilityReady = hud.abilityCooldownSec <= 0.05f
                        Button(
                            onClick = {
                                if (settings.hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                synchronized(simulation) { simulation.triggerPlayerAbility() }
                            },
                            enabled = abilityReady,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (abilityReady) SolarGold else CardSurface,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.size(66.dp).testTag("hud_ability_button")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Bolt, contentDescription = "Special Ability", modifier = Modifier.size(22.dp))
                                Text(
                                    text = if (abilityReady) "SURGE" else "${hud.abilityCooldownSec.roundToInt()}s",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        // Hold-to-Boost Button
                        Surface(
                            shape = CircleShape,
                            color = EmeraldCore.copy(alpha = 0.88f),
                            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .size(78.dp)
                                .testTag("hud_boost_button")
                                .pointerInput(settings.hapticsEnabled) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        simulation.inputBoost = true
                                        if (settings.hapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        do {
                                            val event = awaitPointerEvent()
                                        } while (event.changes.any { it.pressed })
                                        simulation.inputBoost = false
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.FlashOn, contentDescription = "Boost", tint = VoidObsidian, modifier = Modifier.size(26.dp))
                                Text("BOOST", style = MaterialTheme.typography.labelSmall, color = VoidObsidian)
                            }
                        }
                    }
                }
            }

            if (isLeftHanded) {
                actionButtonsBlock()
                joystickBlock()
            } else {
                joystickBlock()
                actionButtonsBlock()
            }
        }

        // Respawn Overlay (when eliminated in respawn-enabled modes)
        if (!hud.playerAlive && hud.canRespawn && !isPaused) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = CardSurfaceElevated,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, CrimsonDanger)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).widthIn(max = 360.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("COBRA ELIMINATED!", style = MaterialTheme.typography.headlineMedium, color = CrimsonDanger)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(hud.deathReason ?: "Collision in the arena", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CobraPrimaryButton(
                                text = "RESPAWN",
                                onClick = {
                                    synchronized(simulation) { simulation.triggerPlayerRespawn() }
                                },
                                testTag = "respawn_button"
                            )
                            OutlinedButton(
                                onClick = {
                                    val p = synchronized(simulation) { simulation.playerSnake() }
                                    onMatchFinished(p.score, p.kills, p.maxLengthReached, hud.elapsedSec, false, hud.deathReason)
                                },
                                modifier = Modifier.height(52.dp).testTag("finish_match_button")
                            ) {
                                Text("END MATCH", color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // Pause Menu Overlay
        if (isPaused) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = CardSurfaceElevated,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.5.dp, EmeraldCore)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).widthIn(min = 280.dp, max = 340.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("MATCH PAUSED", style = MaterialTheme.typography.headlineMedium, color = EmeraldCore)
                        Text("${matchConfig.mode.title} • ${matchConfig.arena.name}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        CobraPrimaryButton(
                            text = "RESUME",
                            onClick = { isPaused = false },
                            testTag = "pause_resume_button",
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                isPaused = false
                                val p = synchronized(simulation) { simulation.playerSnake() }
                                onMatchFinished(p.score, p.kills, p.maxLengthReached, hud.elapsedSec, false, "Match ended early")
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_save_exit_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SolarGold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Finish & Record Score", color = SolarGold)
                        }
                        OutlinedButton(
                            onClick = onExitToMenu,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_quit_button")
                        ) {
                            Text("Exit to Main Menu", color = CrimsonDanger)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualJoystick(
    onSteer: (active: Boolean, angleRad: Float) -> Unit
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val outerRadiusDp = 64.dp

    Box(
        modifier = Modifier
            .size(outerRadiusDp * 2)
            .testTag("virtual_joystick")
            .pointerInput(Unit) {
                val maxDist = size.width * 0.42f
                detectDragGestures(
                    onDragStart = { start ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val d = start - center
                        val len = hypot(d.x, d.y)
                        val clamped = if (len > maxDist && len > 0f) d * (maxDist / len) else d
                        knobOffset = clamped
                        if (len > 4f) onSteer(true, atan2(clamped.y, clamped.x))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val d = change.position - center
                        val len = hypot(d.x, d.y)
                        val clamped = if (len > maxDist && len > 0f) d * (maxDist / len) else d
                        knobOffset = clamped
                        if (len > 4f) onSteer(true, atan2(clamped.y, clamped.x))
                    },
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onSteer(false, 0f)
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onSteer(false, 0f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseR = size.minDimension * 0.46f
            drawCircle(
                color = VoidObsidian.copy(alpha = 0.55f),
                radius = baseR,
                center = center
            )
            drawCircle(
                color = EmeraldCore.copy(alpha = 0.65f),
                radius = baseR,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = EmeraldCore.copy(alpha = 0.9f),
                radius = baseR * 0.38f,
                center = center + knobOffset
            )
        }
    }
}

@Composable
fun MatchResultScreen(
    isVictoryScreen: Boolean,
    summary: MatchResultSummary?,
    adManager: AdManager,
    onPlayAgain: () -> Unit,
    onBackToMenu: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val act = context as? Activity
        if (act != null) {
            adManager.showPostMatchInterstitialIfReady(act) {}
        }
    }

    val accent = if (isVictoryScreen) EmeraldCore else CrimsonDanger

    CobraBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                color = CardSurfaceElevated.copy(alpha = 0.95f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(2.dp, accent)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isVictoryScreen) Icons.Default.EmojiEvents else Icons.Default.SentimentVeryDissatisfied,
                        contentDescription = null,
                        tint = if (isVictoryScreen) SolarGold else CrimsonDanger,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isVictoryScreen) "ARENA VICTORY!" else "DEFEAT",
                        style = MaterialTheme.typography.displayLarge,
                        color = accent
                    )
                    if (summary != null) {
                        Text(
                            text = "${summary.mode.title} • ${summary.arenaName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyberCyan
                        )
                        if (!isVictoryScreen && !summary.deathReason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(summary.deathReason, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ResultStat("SCORE", "${summary.score}", SolarGold)
                            ResultStat("KILLS", "${summary.kills}", EmeraldCore)
                            ResultStat("MAX LEN", "${summary.maxLength}", CyberCyan)
                            ResultStat("XP GAINED", "+${summary.xpEarned}", SolarGold)
                        }
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CobraPrimaryButton(
                            text = "PLAY AGAIN",
                            onClick = onPlayAgain,
                            testTag = "result_play_again_button",
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onBackToMenu,
                            modifier = Modifier.weight(1f).height(52.dp).testTag("result_menu_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = EmeraldCore)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("MAIN MENU", color = EmeraldCore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
