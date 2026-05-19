package com.netctrl.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LocalNodeManager {
    private var webSocket: WebSocket? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connected.value = true
            webSocket.send("""{"type":"get_known_peers"}""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = JSONObject(text)
                if (json.optString("type") == "known_peers_list") {
                    val peers = json.optJSONArray("peers")
                    _peerCount.value = peers?.length() ?: 0
                }
            } catch (_: Exception) {}
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connected.value = false
            android.util.Log.d("H3363T-LocalNode", "Local WS closed, reconnecting in 3s")
            scheduleReconnect(3000L)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connected.value = false
            android.util.Log.d("H3363T-LocalNode", "Local WS failure, reconnecting in 3s")
            scheduleReconnect(3000L)
        }
    }

    private fun scheduleReconnect(delayMs: Long) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            connect()
        }
    }

    fun connect() {
        connectTo("ws://127.0.0.1:9001")
    }

    fun connectTo(url: String) {
        disconnect()
        _peerCount.value = 0
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, listener)
    }

    fun refresh() {
        if (_connected.value) {
            webSocket?.send("""{"type":"get_known_peers"}""")
        } else {
            connect()
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        scope.cancel()
        webSocket?.close(1000, "Disconnecting")
        webSocket = null
        _connected.value = false
    }
}
