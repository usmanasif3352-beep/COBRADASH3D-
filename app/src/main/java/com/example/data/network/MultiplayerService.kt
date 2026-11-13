package com.example.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface MultiplayerConnectionState {
    data object Disconnected : MultiplayerConnectionState
    data object Connecting : MultiplayerConnectionState
    data class Connected(val roomCode: String, val playerCount: Int, val pingMs: Int) : MultiplayerConnectionState
    data class ServerUnavailable(val endpoint: String, val reason: String) : MultiplayerConnectionState
}

interface MultiplayerService {
    val connectionState: StateFlow<MultiplayerConnectionState>
    fun configureEndpoints(restBaseUrl: String, webSocketUrl: String)
    suspend fun checkServerHealth(): Result<String>
    suspend fun createPrivateRoom(idToken: String?, modeId: String, arenaId: String): Result<String>
    suspend fun joinRoom(idToken: String?, roomCode: String): Result<String>
    suspend fun queueMatchmaking(idToken: String?, modeId: String, arenaId: String)
    fun sendInputState(yaw: Float, boosting: Boolean)
    fun disconnect()
}

class OkHttpWebSocketMultiplayerService(
    private val scope: CoroutineScope
) : MultiplayerService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var restBaseUrl: String = "https://api.cobradash3d.example.com"
    private var wsUrl: String = "wss://ws.cobradash3d.example.com/arena"
    private var activeWebSocket: WebSocket? = null
    private var currentRoomCode: String = ""

    private val _connectionState = MutableStateFlow<MultiplayerConnectionState>(
        MultiplayerConnectionState.Disconnected
    )
    override val connectionState: StateFlow<MultiplayerConnectionState> = _connectionState.asStateFlow()

    override fun configureEndpoints(restBaseUrl: String, webSocketUrl: String) {
        this.restBaseUrl = restBaseUrl.trim().removeSuffix("/")
        this.wsUrl = webSocketUrl.trim()
    }

    override suspend fun checkServerHealth(): Result<String> = withContext(Dispatchers.IO) {
        if (restBaseUrl.contains("example.com") || restBaseUrl.isBlank()) {
            val msg = "Authoritative multiplayer backend not yet deployed ($restBaseUrl). Practice & AI modes remain fully playable."
            _connectionState.value = MultiplayerConnectionState.ServerUnavailable(restBaseUrl, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }
        try {
            val req = Request.Builder().url("$restBaseUrl/health").get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success("Online (${resp.code})")
                } else {
                    val msg = "Server returned HTTP ${resp.code}"
                    _connectionState.value = MultiplayerConnectionState.ServerUnavailable(restBaseUrl, msg)
                    Result.failure(IllegalStateException(msg))
                }
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Multiplayer server unreachable"
            _connectionState.value = MultiplayerConnectionState.ServerUnavailable(restBaseUrl, msg)
            Result.failure(e)
        }
    }

    override suspend fun createPrivateRoom(
        idToken: String?,
        modeId: String,
        arenaId: String
    ): Result<String> {
        val health = checkServerHealth()
        if (health.isFailure) return Result.failure(health.exceptionOrNull()!!)

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$wsUrl?mode=$modeId&arena=$arenaId&action=create")
                    .apply {
                        if (!idToken.isNullOrBlank()) {
                            addHeader("Authorization", "Bearer $idToken")
                        }
                    }
                    .build()
                openSocket(request, "CREATING")
                Result.success("Connecting to room...")
            } catch (e: Exception) {
                _connectionState.value = MultiplayerConnectionState.ServerUnavailable(
                    wsUrl,
                    e.localizedMessage ?: "WebSocket connection failed"
                )
                Result.failure(e)
            }
        }
    }

    override suspend fun joinRoom(idToken: String?, roomCode: String): Result<String> {
        val cleanCode = roomCode.trim().uppercase()
        if (cleanCode.length < 4) {
            return Result.failure(IllegalArgumentException("Enter a valid room code (4+ characters)."))
        }
        val health = checkServerHealth()
        if (health.isFailure) return Result.failure(health.exceptionOrNull()!!)

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$wsUrl?room=$cleanCode&action=join")
                    .apply {
                        if (!idToken.isNullOrBlank()) {
                            addHeader("Authorization", "Bearer $idToken")
                        }
                    }
                    .build()
                openSocket(request, cleanCode)
                Result.success(cleanCode)
            } catch (e: Exception) {
                _connectionState.value = MultiplayerConnectionState.ServerUnavailable(
                    wsUrl,
                    e.localizedMessage ?: "Failed to join room"
                )
                Result.failure(e)
            }
        }
    }

    override suspend fun queueMatchmaking(idToken: String?, modeId: String, arenaId: String) {
        val health = checkServerHealth()
        if (health.isFailure) return
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$wsUrl?mode=$modeId&arena=$arenaId&action=matchmake")
                .apply {
                    if (!idToken.isNullOrBlank()) {
                        addHeader("Authorization", "Bearer $idToken")
                    }
                }
                .build()
            openSocket(request, "MATCHMAKING")
        }
    }

    private fun openSocket(request: Request, initialRoomLabel: String) {
        activeWebSocket?.close(1000, "Reconnecting")
        _connectionState.value = MultiplayerConnectionState.Connecting
        currentRoomCode = initialRoomLabel
        activeWebSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connectionState.value = MultiplayerConnectionState.Connected(
                        roomCode = currentRoomCode,
                        playerCount = 1,
                        pingMs = 38
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    scope.launch {
                        try {
                            val json = JSONObject(text)
                            val room = json.optString("roomCode", currentRoomCode)
                            val count = json.optInt("players", 1)
                            val ping = json.optInt("pingMs", 38)
                            currentRoomCode = room
                            _connectionState.value = MultiplayerConnectionState.Connected(room, count, ping)
                        } catch (_: Exception) {
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _connectionState.value = MultiplayerConnectionState.ServerUnavailable(
                        wsUrl,
                        t.localizedMessage ?: "WebSocket connection failed"
                    )
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (_connectionState.value !is MultiplayerConnectionState.ServerUnavailable) {
                        _connectionState.value = MultiplayerConnectionState.Disconnected
                    }
                }
            }
        )
    }

    override fun sendInputState(yaw: Float, boosting: Boolean) {
        val payload = JSONObject().apply {
            put("type", "input")
            put("yaw", yaw.toDouble())
            put("boost", boosting)
        }
        activeWebSocket?.send(payload.toString())
    }

    override fun disconnect() {
        activeWebSocket?.close(1000, "Client disconnected")
        activeWebSocket = null
        _connectionState.value = MultiplayerConnectionState.Disconnected
    }
}
