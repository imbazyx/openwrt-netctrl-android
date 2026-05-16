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

    private val _connectionState = MutableSharedFlow<Boolean>(replay = 1)
    val connectionState: SharedFlow<Boolean> = _connectionState.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10
    private val baseReconnectDelayMs = 2000L

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempts = 0
            _connectionState.tryEmit(true)
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
                // Ignore parse errors
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connectionState.tryEmit(false)
            android.util.Log.d("H3363T-WS", "WebSocket closed, scheduling reconnect")
            scheduleReconnect(5000L)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connectionState.tryEmit(false)
            android.util.Log.d("H3363T-WS", "WebSocket failure, scheduling reconnect")
            scheduleReconnect(5000L)
        }
    }

    fun connect(serverUrl: String, token: String) {
        disconnect()

        val wsUrl = serverUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/') + "/api/ws/events"

        val client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WS
            .build()

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = client.newWebSocket(request, listener)
    }

    fun disconnect() {
        reconnectJob?.cancel()
        scope.cancel()
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        reconnectAttempts = 0
    }

    private fun scheduleReconnect(delayMs: Long) {
        if (reconnectAttempts >= maxReconnectAttempts) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            android.util.Log.d("H3363T-WS", "Attempting reconnect after ${delayMs}ms")
            reconnectAttempts++
            // connect() should be called by the owner of this manager to re-establish
        }
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
