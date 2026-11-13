package com.example.data.network

import com.example.data.model.LeaderboardEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

sealed interface MultiplayerConnectionState {
    data object Disconnected : MultiplayerConnectionState
    data object Connecting : MultiplayerConnectionState
    data class Connected(val roomCode: String, val playerCount: Int, val pingMs: Int) : MultiplayerConnectionState
    data class ServerUnavailable(val endpoint: String, val reason: String) : MultiplayerConnectionState
}

data class PublicRoomSummary(
    val roomCode: String,
    val modeId: String,
    val arenaId: String,
    val playerCount: Int,
    val maxPlayers: Int
)

data class ServerWallet(
    val coins: Int = 0,
    val gems: Int = 0,
    val region: String = "GLOBAL",
    val eloRating: Int = 1000,
    val vipActive: Boolean = false,
    val battlePassPremium: Boolean = false,
    val adsRemoved: Boolean = false
)

data class RemotePowerUpState(
    val type: String,
    val remainingSec: Float
)

data class RemoteSnakeState(
    val uid: String,
    val nickname: String,
    val skinId: String,
    val teamId: Int,
    val isAlive: Boolean,
    val connected: Boolean,
    val headX: Float,
    val headZ: Float,
    val yawRad: Float,
    val isBoosting: Boolean,
    val score: Int,
    val kills: Int,
    val length: Int,
    val boostMeter: Float?,
    val abilityCooldownSec: Float?,
    val activePowerUps: List<RemotePowerUpState>,
    val segments: List<Pair<Float, Float>>
)

data class RemoteOrbState(
    val id: Int,
    val x: Float,
    val z: Float,
    val value: Int,
    val isDropped: Boolean
)

data class RemoteWorldPowerUp(
    val id: Int,
    val type: String,
    val x: Float,
    val z: Float
)

data class RemoteObstacle(
    val x: Float,
    val z: Float,
    val radius: Float
)

data class RemoteWorldSnapshot(
    val roomCode: String,
    val modeId: String,
    val arenaId: String,
    val localUid: String,
    val players: Int,
    val maxPlayers: Int,
    val elapsedSec: Float,
    val timeRemainingSec: Int?,
    val currentRingRadius: Float,
    val arenaRadius: Float,
    val matchFinished: Boolean,
    val isVictory: Boolean,
    val deathReason: String?,
    val canRespawn: Boolean,
    val killFeed: List<String>,
    val snakes: List<RemoteSnakeState>,
    val orbs: List<RemoteOrbState>,
    val powerUps: List<RemoteWorldPowerUp>,
    val obstacles: List<RemoteObstacle>
)

interface MultiplayerService {
    val connectionState: StateFlow<MultiplayerConnectionState>
    val worldSnapshot: StateFlow<RemoteWorldSnapshot?>
    fun configureEndpoints(restBaseUrl: String, webSocketUrl: String)
    fun isConfigured(): Boolean
    suspend fun checkServerHealth(): Result<String>
    suspend fun listPublicRooms(idToken: String): Result<List<PublicRoomSummary>>
    suspend fun fetchWallet(idToken: String): Result<ServerWallet>
    suspend fun updateRegion(idToken: String, region: String): Result<String>
    suspend fun fetchRegionalLeaderboard(idToken: String, region: String): Result<List<LeaderboardEntry>>
    suspend fun verifyPurchase(idToken: String, productId: String, purchaseToken: String): Result<ServerWallet>
    suspend fun unlockSkinWithCoins(idToken: String, skinId: String): Result<ServerWallet>
    suspend fun createPrivateRoom(idToken: String?, modeId: String, arenaId: String): Result<String>
    suspend fun joinRoom(idToken: String?, roomCode: String): Result<String>
    suspend fun queueMatchmaking(idToken: String?, modeId: String, arenaId: String)
    fun sendInputState(yaw: Float, boosting: Boolean)
    fun sendAbility()
    fun sendRespawn()
    fun disconnect()
}

class OkHttpWebSocketMultiplayerService(
    private val scope: CoroutineScope
) : MultiplayerService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val inputSeq = AtomicInteger(0)

    private var restBaseUrl: String = "https://api.cobradash3d.example.com"
    private var wsUrl: String = "wss://ws.cobradash3d.example.com/arena"
    private var activeWebSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var currentRoomCode: String = ""
    private var localUid: String = ""
    private var reconnectToken: String? = null
    private var lastAuthToken: String? = null
    private var lastAction: String = "matchmake"
    private var lastModeId: String = "RANKED_MULTIPLAYER"
    private var lastArenaId: String = "jungle_arena"
    private var cachedObstacles: List<RemoteObstacle> = emptyList()
    private var lastDeathReason: String? = null
    private var lastCanRespawn: Boolean = true
    private var manualDisconnect: Boolean = false
    private var lastPingMs: Int = 35
    private var lastPingSentAtMs: Long = 0L

    private val _connectionState = MutableStateFlow<MultiplayerConnectionState>(
        MultiplayerConnectionState.Disconnected
    )
    override val connectionState: StateFlow<MultiplayerConnectionState> = _connectionState.asStateFlow()

    private val _worldSnapshot = MutableStateFlow<RemoteWorldSnapshot?>(null)
    override val worldSnapshot: StateFlow<RemoteWorldSnapshot?> = _worldSnapshot.asStateFlow()

    override fun configureEndpoints(restBaseUrl: String, webSocketUrl: String) {
        this.restBaseUrl = restBaseUrl.trim().removeSuffix("/")
        this.wsUrl = webSocketUrl.trim()
    }

    override fun isConfigured(): Boolean {
        return restBaseUrl.isNotBlank() && !restBaseUrl.contains("example.com")
    }

    override suspend fun checkServerHealth(): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            val msg = "Configure your Cloud Run / local multiplayer backend URL (current: $restBaseUrl)."
            _connectionState.value = MultiplayerConnectionState.ServerUnavailable(restBaseUrl, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }
        try {
            val req = Request.Builder().url("$restBaseUrl/health").get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    val rooms = json?.optInt("activeRooms", 0) ?: 0
                    val players = json?.optInt("totalConnectedPlayers", 0) ?: 0
                    Result.success("Online • $rooms rooms • $players players")
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

    override suspend fun listPublicRooms(idToken: String): Result<List<PublicRoomSummary>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(emptyList())
        try {
            val req = Request.Builder()
                .url("$restBaseUrl/api/v1/rooms/public")
                .addHeader("Authorization", "Bearer $idToken")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}"))
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                val arr = json.optJSONArray("rooms") ?: JSONArray()
                val list = buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(
                            PublicRoomSummary(
                                roomCode = obj.optString("roomCode"),
                                modeId = obj.optString("modeId"),
                                arenaId = obj.optString("arenaId"),
                                playerCount = obj.optInt("playerCount"),
                                maxPlayers = obj.optInt("maxPlayers")
                            )
                        )
                    }
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchWallet(idToken: String): Result<ServerWallet> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Backend not configured"))
        try {
            val req = Request.Builder()
                .url("$restBaseUrl/api/v1/players/wallet")
                .addHeader("Authorization", "Bearer $idToken")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(extractErrorMessage(raw, resp.code)))
                }
                val w = JSONObject(raw).getJSONObject("wallet")
                Result.success(parseWallet(w))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateRegion(idToken: String, region: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Backend not configured"))
        try {
            val body = JSONObject().put("region", region).toString().toRequestBody(jsonMediaType)
            val req = Request.Builder()
                .url("$restBaseUrl/api/v1/players/region")
                .addHeader("Authorization", "Bearer $idToken")
                .put(body)
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(extractErrorMessage(raw, resp.code)))
                }
                Result.success(JSONObject(raw).optString("region", region))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchRegionalLeaderboard(
        idToken: String,
        region: String
    ): Result<List<LeaderboardEntry>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Backend not configured"))
        try {
            val req = Request.Builder()
                .url("$restBaseUrl/api/v1/leaderboard/regional/$region")
                .addHeader("Authorization", "Bearer $idToken")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(extractErrorMessage(raw, resp.code)))
                }
                val arr = JSONObject(raw).optJSONArray("entries") ?: JSONArray()
                val entries = buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(
                            LeaderboardEntry(
                                id = obj.optString("userId"),
                                userId = obj.optString("userId"),
                                nickname = obj.optString("nickname", "CobraPilot"),
                                highScore = obj.optLong("highScore", 0L),
                                level = obj.optLong("level", 1L),
                                wins = obj.optLong("wins", 0L),
                                skinId = obj.optString("skinId", "royal_cobra"),
                                isPublic = true
                            )
                        )
                    }
                }
                Result.success(entries)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyPurchase(
        idToken: String,
        productId: String,
        purchaseToken: String
    ): Result<ServerWallet> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Backend not configured"))
        try {
            val body = JSONObject()
                .put("productId", productId)
                .put("purchaseToken", purchaseToken)
                .toString()
                .toRequestBody(jsonMediaType)
            val req = Request.Builder()
                .url("$restBaseUrl/api/v1/purchases/verify")
                .addHeader("Authorization", "Bearer $idToken")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(extractErrorMessage(raw, resp.code)))
                }
                val obj = JSONObject(raw)
                Result.success(parseWallet(obj))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unlockSkinWithCoins(idToken: String, skinId: String): Result<ServerWallet> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Backend not configured"))
            try {
                val body = JSONObject().put("skinId", skinId).toString().toRequestBody(jsonMediaType)
                val req = Request.Builder()
                    .url("$restBaseUrl/api/v1/players/economy/unlock-skin")
                    .addHeader("Authorization", "Bearer $idToken")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException(extractErrorMessage(raw, resp.code)))
                    }
                    Result.success(parseWallet(JSONObject(raw)))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun createPrivateRoom(
        idToken: String?,
        modeId: String,
        arenaId: String
    ): Result<String> {
        if (idToken.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Google Sign-In is required for online multiplayer."))
        }
        val health = checkServerHealth()
        if (health.isFailure) return Result.failure(health.exceptionOrNull()!!)

        return withContext(Dispatchers.IO) {
            try {
                lastAuthToken = idToken
                lastAction = "create"
                lastModeId = modeId
                lastArenaId = arenaId
                reconnectToken = null
                val request = Request.Builder()
                    .url("$wsUrl?mode=$modeId&arena=$arenaId&action=create")
                    .addHeader("Authorization", "Bearer $idToken")
                    .build()
                openSocket(request, "CREATING")
                Result.success("Creating private room...")
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
        if (idToken.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Google Sign-In is required for online multiplayer."))
        }
        val cleanCode = roomCode.trim().uppercase()
        if (cleanCode.length < 4) {
            return Result.failure(IllegalArgumentException("Enter a valid room code (4+ characters)."))
        }
        val health = checkServerHealth()
        if (health.isFailure) return Result.failure(health.exceptionOrNull()!!)

        return withContext(Dispatchers.IO) {
            try {
                lastAuthToken = idToken
                lastAction = "join"
                reconnectToken = null
                val request = Request.Builder()
                    .url("$wsUrl?room=$cleanCode&action=join")
                    .addHeader("Authorization", "Bearer $idToken")
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
        if (idToken.isNullOrBlank()) {
            _connectionState.value = MultiplayerConnectionState.ServerUnavailable(
                wsUrl,
                "Google Sign-In is required to join online multiplayer."
            )
            return
        }
        val health = checkServerHealth()
        if (health.isFailure) return
        withContext(Dispatchers.IO) {
            lastAuthToken = idToken
            lastAction = "matchmake"
            lastModeId = modeId
            lastArenaId = arenaId
            reconnectToken = null
            val request = Request.Builder()
                .url("$wsUrl?mode=$modeId&arena=$arenaId&action=matchmake")
                .addHeader("Authorization", "Bearer $idToken")
                .build()
            openSocket(request, "MATCHMAKING")
        }
    }

    private fun openSocket(request: Request, initialRoomLabel: String) {
        manualDisconnect = false
        pingJob?.cancel()
        activeWebSocket?.close(1000, "Reconnecting")
        _connectionState.value = MultiplayerConnectionState.Connecting
        currentRoomCode = initialRoomLabel
        lastDeathReason = null
        lastCanRespawn = true

        activeWebSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connectionState.value = MultiplayerConnectionState.Connected(
                        roomCode = currentRoomCode,
                        playerCount = 1,
                        pingMs = lastPingMs
                    )
                    pingJob = scope.launch(Dispatchers.IO) {
                        while (isActive) {
                            lastPingSentAtMs = System.currentTimeMillis()
                            webSocket.send(
                                JSONObject()
                                    .put("type", "ping")
                                    .put("ts", lastPingSentAtMs)
                                    .toString()
                            )
                            delay(4000L)
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    scope.launch {
                        handleIncomingFrame(text)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    pingJob?.cancel()
                    val token = reconnectToken
                    val auth = lastAuthToken
                    if (!manualDisconnect && !token.isNullOrBlank() && !auth.isNullOrBlank() && currentRoomCode.isNotBlank()) {
                        scope.launch(Dispatchers.IO) {
                            delay(1200L)
                            if (!manualDisconnect) {
                                val reconnectReq = Request.Builder()
                                    .url("$wsUrl?action=reconnect&room=$currentRoomCode&reconnectToken=$token")
                                    .addHeader("Authorization", "Bearer $auth")
                                    .build()
                                openSocket(reconnectReq, currentRoomCode)
                            }
                        }
                    } else {
                        _connectionState.value = MultiplayerConnectionState.ServerUnavailable(
                            wsUrl,
                            t.localizedMessage ?: "WebSocket connection failed"
                        )
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    pingJob?.cancel()
                    if (_connectionState.value !is MultiplayerConnectionState.ServerUnavailable) {
                        _connectionState.value = MultiplayerConnectionState.Disconnected
                    }
                }
            }
        )
    }

    private fun handleIncomingFrame(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "welcome" -> {
                localUid = json.optString("uid", localUid)
                currentRoomCode = json.optString("roomCode", currentRoomCode)
                reconnectToken = json.optString("reconnectToken", reconnectToken ?: "")
                val count = json.optInt("players", 1)
                cachedObstacles = parseObstacles(json.optJSONArray("obstacles"))
                _connectionState.value = MultiplayerConnectionState.Connected(currentRoomCode, count, lastPingMs)
            }
            "pong" -> {
                val clientTs = json.optLong("clientTs", lastPingSentAtMs)
                if (clientTs > 0L) {
                    lastPingMs = (System.currentTimeMillis() - clientTs).toInt().coerceIn(1, 999)
                }
            }
            "eliminated" -> {
                lastDeathReason = json.optString("reason", "Eliminated in arena")
                lastCanRespawn = json.optBoolean("canRespawn", true)
            }
            "world_snapshot" -> {
                val room = json.optString("roomCode", currentRoomCode)
                val count = json.optInt("players", 1)
                currentRoomCode = room
                _connectionState.value = MultiplayerConnectionState.Connected(room, count, lastPingMs)
                _worldSnapshot.value = parseWorldSnapshot(json, victory = false)
            }
            "match_result" -> {
                val standings = json.optJSONArray("standings")
                var won = false
                if (standings != null) {
                    for (i in 0 until standings.length()) {
                        val row = standings.getJSONObject(i)
                        if (row.optString("uid") == localUid) {
                            won = row.optBoolean("victory", false)
                            break
                        }
                    }
                }
                val prev = _worldSnapshot.value
                if (prev != null) {
                    _worldSnapshot.value = prev.copy(matchFinished = true, isVictory = won)
                }
            }
            "error" -> {
                val message = json.optString("message", "Server rejected action")
                if (json.optString("code") == "ROOM_NOT_FOUND") {
                    reconnectToken = null
                    _connectionState.value = MultiplayerConnectionState.ServerUnavailable(wsUrl, message)
                }
            }
        }
    }

    private fun parseObstacles(arr: JSONArray?): List<RemoteObstacle> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(RemoteObstacle(o.optDouble("x").toFloat(), o.optDouble("z").toFloat(), o.optDouble("radius").toFloat()))
            }
        }
    }

    private fun parseWorldSnapshot(json: JSONObject, victory: Boolean): RemoteWorldSnapshot {
        val snakesArr = json.optJSONArray("snakes") ?: JSONArray()
        val snakes = buildList {
            for (i in 0 until snakesArr.length()) {
                val s = snakesArr.getJSONObject(i)
                val puArr = s.optJSONArray("activePowerUps") ?: JSONArray()
                val powerUps = buildList {
                    for (j in 0 until puArr.length()) {
                        val p = puArr.getJSONObject(j)
                        add(RemotePowerUpState(p.optString("type"), p.optDouble("remainingSec").toFloat()))
                    }
                }
                val segArr = s.optJSONArray("segments") ?: JSONArray()
                val segments = buildList {
                    for (j in 0 until segArr.length()) {
                        val pt = segArr.getJSONArray(j)
                        add(pt.getDouble(0).toFloat() to pt.getDouble(1).toFloat())
                    }
                }
                add(
                    RemoteSnakeState(
                        uid = s.optString("uid"),
                        nickname = s.optString("nickname"),
                        skinId = s.optString("skinId", "royal_cobra"),
                        teamId = s.optInt("teamId", 0),
                        isAlive = s.optBoolean("isAlive", true),
                        connected = s.optBoolean("connected", true),
                        headX = s.optDouble("headX").toFloat(),
                        headZ = s.optDouble("headZ").toFloat(),
                        yawRad = s.optDouble("yawRad").toFloat(),
                        isBoosting = s.optBoolean("isBoosting", false),
                        score = s.optInt("score", 0),
                        kills = s.optInt("kills", 0),
                        length = s.optInt("length", 14),
                        boostMeter = if (s.has("boostMeter")) s.optDouble("boostMeter").toFloat() else null,
                        abilityCooldownSec = if (s.has("abilityCooldownSec")) s.optDouble("abilityCooldownSec").toFloat() else null,
                        activePowerUps = powerUps,
                        segments = segments
                    )
                )
            }
        }

        val orbsArr = json.optJSONArray("orbs") ?: JSONArray()
        val orbs = buildList {
            for (i in 0 until orbsArr.length()) {
                val o = orbsArr.getJSONArray(i)
                add(
                    RemoteOrbState(
                        id = o.getInt(0),
                        x = o.getDouble(1).toFloat(),
                        z = o.getDouble(2).toFloat(),
                        value = o.getInt(3),
                        isDropped = o.getInt(4) == 1
                    )
                )
            }
        }

        val puArr = json.optJSONArray("powerUps") ?: JSONArray()
        val powerUps = buildList {
            for (i in 0 until puArr.length()) {
                val p = puArr.getJSONObject(i)
                add(
                    RemoteWorldPowerUp(
                        id = p.optInt("id"),
                        type = p.optString("type"),
                        x = p.optDouble("x").toFloat(),
                        z = p.optDouble("z").toFloat()
                    )
                )
            }
        }

        val feedArr = json.optJSONArray("killFeed") ?: JSONArray()
        val killFeed = buildList {
            for (i in 0 until feedArr.length()) add(feedArr.getString(i))
        }

        val timeRem = if (json.isNull("timeRemainingSec")) null else json.optInt("timeRemainingSec")
        return RemoteWorldSnapshot(
            roomCode = json.optString("roomCode"),
            modeId = json.optString("modeId"),
            arenaId = json.optString("arenaId"),
            localUid = localUid,
            players = json.optInt("players", snakes.size),
            maxPlayers = json.optInt("maxPlayers", 12),
            elapsedSec = json.optDouble("elapsedSec", 0.0).toFloat(),
            timeRemainingSec = timeRem,
            currentRingRadius = json.optDouble("currentRingRadius", 56.0).toFloat(),
            arenaRadius = json.optDouble("arenaRadius", 56.0).toFloat(),
            matchFinished = json.optBoolean("matchFinished", false),
            isVictory = victory,
            deathReason = lastDeathReason,
            canRespawn = lastCanRespawn,
            killFeed = killFeed,
            snakes = snakes,
            orbs = orbs,
            powerUps = powerUps,
            obstacles = cachedObstacles
        )
    }

    private fun parseWallet(json: JSONObject): ServerWallet {
        return ServerWallet(
            coins = json.optInt("coins", 0),
            gems = json.optInt("gems", 0),
            region = json.optString("region", "GLOBAL"),
            eloRating = json.optInt("eloRating", 1000),
            vipActive = json.optBoolean("vipActive", false),
            battlePassPremium = json.optBoolean("battlePassPremium", false),
            adsRemoved = json.optBoolean("adsRemoved", false)
        )
    }

    private fun extractErrorMessage(raw: String, code: Int): String {
        return runCatching {
            JSONObject(raw).getJSONObject("error").getString("message")
        }.getOrDefault("Server HTTP $code")
    }

    override fun sendInputState(yaw: Float, boosting: Boolean) {
        val payload = JSONObject().apply {
            put("type", "input")
            put("yaw", yaw.toDouble())
            put("boost", boosting)
            put("seq", inputSeq.incrementAndGet())
        }
        activeWebSocket?.send(payload.toString())
    }

    override fun sendAbility() {
        activeWebSocket?.send(JSONObject().put("type", "ability").toString())
    }

    override fun sendRespawn() {
        lastDeathReason = null
        activeWebSocket?.send(JSONObject().put("type", "respawn").toString())
    }

    override fun disconnect() {
        manualDisconnect = true
        reconnectToken = null
        pingJob?.cancel()
        activeWebSocket?.close(1000, "Client disconnected")
        activeWebSocket = null
        _worldSnapshot.value = null
        _connectionState.value = MultiplayerConnectionState.Disconnected
    }
}
