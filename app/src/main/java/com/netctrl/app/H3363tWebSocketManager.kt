package com.netctrl.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WsEvent(
    val eventType: String,
    val nodePubkey: String,
    val payload: Map<String, Any?>,
    val timestamp: Long
)

class H3363tWebSocketManager {
    private var webSocket: WebSocket? = null
    private val _events = MutableSharedFlow<H3363tEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<H3363tEvent> = _events.asSharedFlow()

    private val _outgoingMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val outgoingMessages: SharedFlow<String> = _outgoingMessages.asSharedFlow()

    private val _connectionState = MutableSharedFlow<Boolean>(replay = 1)
    val connectionState: SharedFlow<Boolean> = _connectionState.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 12

    // Экспоненциальный backoff: 1→2→4→8→16→30→30→... секунд
    private fun calculateBackoffDelay(attempt: Int): Long {
        val delays = listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L)
        return delays.getOrNull(attempt.coerceAtMost(delays.lastIndex)) ?: 30000L
    }

    // Состояние для автопереподключения
    private var serverUrl: String? = null
    private var token: String? = null
    private var isManualDisconnect = false

    // Список подписок на feed (room_id или pubkey)
    private val subscriptions = mutableSetOf<String>()

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempts = 0
            isManualDisconnect = false
            _connectionState.tryEmit(true)
            android.util.Log.d("H3363T-WS", "WebSocket connected")

            // Отправляем запрос истории для всех активных подписок
            for (feedId in subscriptions) {
                val historyRequest = org.json.JSONObject()
                    .put("type", "get_history")
                    .put("feed_id", feedId)
                    .put("limit", 50)
                webSocket.send(historyRequest.toString())
                android.util.Log.d("H3363T-WS", "Requested history for feed: $feedId")
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = JSONObject(text)

                // Handle recent events snapshot
                if (json.has("event") && json.getString("event") == "recent_events") {
                    val eventsArray = json.optJSONArray("events")
                    if (eventsArray != null) {
                        for (i in 0 until eventsArray.length()) {
                            val eventJson = eventsArray.getJSONObject(i)
                            val event = parseEvent(eventJson)
                            _events.tryEmit(event)
                        }
                    }
                    return
                }

                // Handle individual events
                if (json.has("event_type")) {
                    val event = H3363tEvent(
                        event_type = json.optString("event_type", "unknown"),
                        node_pubkey = json.optString("node_pubkey", ""),
                        payload = parsePayload(json.optJSONObject("payload")),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis() / 1000)
                    )
                    _events.tryEmit(event)
                }
            } catch (e: Exception) {
                android.util.Log.e("H3363T-WS", "Parse error: ${e.message}")
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connectionState.tryEmit(false)
            android.util.Log.d("H3363T-WS", "WebSocket closed: code=$code, reason=$reason")
            if (!isManualDisconnect) {
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connectionState.tryEmit(false)
            android.util.Log.d("H3363T-WS", "WebSocket failure: ${t.message}, scheduling reconnect")
            if (!isManualDisconnect) {
                scheduleReconnect()
            }
        }
    }

    fun connect(url: String, authToken: String) {
        disconnect()
        isManualDisconnect = false
        serverUrl = url
        token = authToken

        val wsUrl = url
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/') + "/api/ws/events"

        val client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        webSocket = client.newWebSocket(request, listener)
    }

    fun disconnect() {
        isManualDisconnect = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        reconnectAttempts = 0
        serverUrl = null
        token = null
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            android.util.Log.w("H3363T-WS", "Max reconnect attempts reached")
            return
        }

        val savedUrl = serverUrl
        val savedToken = token
        if (savedUrl == null || savedToken == null) {
            android.util.Log.w("H3363T-WS", "Cannot reconnect: URL or token not saved")
            return
        }

        reconnectJob?.cancel()
        val delayMs = calculateBackoffDelay(reconnectAttempts)
        val currentAttempt = reconnectAttempts

        reconnectJob = scope.launch {
            android.util.Log.d("H3363T-WS", "Reconnect attempt ${currentAttempt + 1}/$maxReconnectAttempts in ${delayMs}ms")
            delay(delayMs)
            reconnectAttempts++

            // Переподключаемся с сохранёнными параметрами
            val wsUrl = savedUrl
                .replace("http://", "ws://")
                .replace("https://", "wss://")
                .trimEnd('/') + "/api/ws/events"

            val client = OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()

            val request = Request.Builder()
                .url(wsUrl)
                .addHeader("Authorization", "Bearer $savedToken")
                .build()

            webSocket = client.newWebSocket(request, listener)
        }
    }

    fun sendMessage(message: String) {
        val sent = webSocket?.send(message)
        if (sent == true) {
            scope.launch {
                _outgoingMessages.tryEmit(message)
            }
        } else {
            android.util.Log.w("H3363T-WS", "Cannot send: WebSocket not connected")
        }
    }

    fun subscribeToFeed(feedId: String) {
        subscriptions.add(feedId)
        val subscribeRequest = org.json.JSONObject()
            .put("type", "subscribe")
            .put("feed_id", feedId)
        webSocket?.send(subscribeRequest.toString())
        android.util.Log.d("H3363T-WS", "Subscribed to feed: $feedId")
    }

    fun unsubscribeFromFeed(feedId: String) {
        subscriptions.remove(feedId)
        val unsubscribeRequest = org.json.JSONObject()
            .put("type", "unsubscribe")
            .put("feed_id", feedId)
        webSocket?.send(unsubscribeRequest.toString())
        android.util.Log.d("H3363T-WS", "Unsubscribed from feed: $feedId")
    }

    private fun parseEvent(json: JSONObject): H3363tEvent {
        return H3363tEvent(
            event_type = json.optString("event_type", "unknown"),
            node_pubkey = json.optString("node_pubkey", ""),
            payload = parsePayload(json.optJSONObject("payload")),
            timestamp = json.optLong("timestamp", System.currentTimeMillis() / 1000)
        )
    }

    private fun parsePayload(obj: JSONObject?): Map<String, Any?> {
        if (obj == null) return emptyMap()
        val map = mutableMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = obj.opt(key)
        }
        return map
    }
}
