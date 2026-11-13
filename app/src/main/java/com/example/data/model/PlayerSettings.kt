package com.example.data.model

enum class GraphicsQuality(val label: String, val resolutionScale: Float, val maxOrbs: Int, val sphereSlices: Int) {
    LOW("Low (Battery)", 0.65f, 80, 10),
    MEDIUM("Medium", 0.80f, 120, 14),
    HIGH("High", 0.95f, 160, 18),
    ULTRA("Ultra (60 FPS)", 1.0f, 220, 22)
}

enum class Handedness {
    RIGHT_HANDED,
    LEFT_HANDED
}

data class PlayerSettings(
    val joystickSensitivity: Float = 1.0f,
    val handedness: Handedness = Handedness.RIGHT_HANDED,
    val hapticsEnabled: Boolean = true,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
    val adaptiveResolution: Boolean = true,
    val targetFps: Int = 60,
    val multiplayerServerUrl: String = "https://api.cobradash3d.example.com",
    val multiplayerWsUrl: String = "wss://ws.cobradash3d.example.com/arena",
    val analyticsOptIn: Boolean = false
)
