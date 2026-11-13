package com.example.game.renderer

import android.content.Context
import android.opengl.GLSurfaceView
import com.example.data.model.GraphicsQuality
import com.example.game.engine.ArenaSimulation
import com.example.game.engine.HudSnapshot

class CobraGLSurfaceView(
    context: Context,
    simulation: ArenaSimulation,
    graphicsQuality: GraphicsQuality,
    onHudSnapshot: (HudSnapshot) -> Unit
) : GLSurfaceView(context) {

    val cobraRenderer: CobraGLRenderer

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        cobraRenderer = CobraGLRenderer(simulation, graphicsQuality, onHudSnapshot)
        setRenderer(cobraRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
