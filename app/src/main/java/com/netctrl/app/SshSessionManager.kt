package com.netctrl.app

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream

class SshSessionManager(private val scope: CoroutineScope) {
    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var outputStream: OutputStream? = null

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    fun connect(host: String, port: Int, username: String, password: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val jsch = JSch()
                val s = jsch.getSession(username, host, port).apply {
                    setPassword(password)
                    setConfig("StrictHostKeyChecking", "no")
                    connect(15000)
                }
                session = s
                val ch = s.openChannel("shell") as ChannelShell
                ch.setPtyType("vt100")
                ch.connect()
                channel = ch
                outputStream = ch.outputStream
                _connected.value = true
                appendOutput("Connected to $host:$port\r\n")
                readLoop(ch.inputStream)
            } catch (e: Exception) {
                appendOutput("\r\nConnection failed: ${e.message}\r\n")
                _connected.value = false
            }
        }
    }

    private suspend fun readLoop(input: InputStream) {
        val buf = ByteArray(4096)
        withContext(Dispatchers.IO) {
            try {
                while (_connected.value) {
                    if (input.available() == 0) {
                        delay(50)
                        continue
                    }
                    val n = input.read(buf)
                    if (n <= 0) break
                    val raw = String(buf, 0, n, Charsets.UTF_8)
                    val clean = stripAnsi(raw)
                    withContext(Dispatchers.Main) { appendOutput(clean) }
                }
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) { _connected.value = false }
        }
    }

    private fun stripAnsi(text: String): String = text
        .replace(Regex("\\u001B\\[[0-9;]*[A-Za-z]"), "")
        .replace(Regex("\\u001B\\][^]*"), "")
        .replace(Regex("\\u001B[()][AB012]"), "")

    private fun appendOutput(text: String) {
        _output.value = (_output.value + text).takeLast(60000)
    }

    fun sendLine(line: String) {
        scope.launch(Dispatchers.IO) {
            try {
                outputStream?.write("$line\n".toByteArray(Charsets.UTF_8))
                outputStream?.flush()
            } catch (_: Exception) {}
        }
    }

    fun disconnect() {
        _connected.value = false
        channel?.disconnect()
        session?.disconnect()
    }
}
