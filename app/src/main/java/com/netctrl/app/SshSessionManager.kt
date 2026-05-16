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
    private val writeLock = Any()

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    fun connect(host: String, port: Int, username: String, password: String) {
        scope.launch(Dispatchers.IO) {
            try {
                if (host.isBlank()) {
                    appendOutput("\r\nОшибка: IP-адрес роутера не задан. Настройте в 'Настройки'.\r\n")
                    _connected.value = false
                    return@launch
                }
                if (password.isBlank()) {
                    appendOutput("\r\nОшибка: пароль SSH не задан. Настройте в 'Настройки'.\r\n")
                    _connected.value = false
                    return@launch
                }
                val jsch = JSch()
                val s = jsch.getSession(username, host, port).apply {
                    setPassword(password)
                    setConfig("StrictHostKeyChecking", "no")
                    setTimeout(15000)
                    connect()
                }
                session = s
                val ch = s.openChannel("shell") as ChannelShell
                ch.setPty(true)
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
                    val clean = stripNonColorEscapes(raw)
                    withContext(Dispatchers.Main) { appendOutput(clean) }
                }
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) { _connected.value = false }
        }
    }

    // Strip cursor movement / erase / OSC sequences but keep SGR color codes ([...m)
    private fun stripNonColorEscapes(text: String): String = text
        .replace(Regex("\\u001B\\[[0-9;]*[ABCDHJ]"), "")   // cursor movement, erase display
        .replace(Regex("\\u001B\\[[0-9;]*[Ks]"), "")        // erase line, save cursor
        .replace(Regex("\\u001B\\[[0-9;]*[fru]"), "")       // cursor position, restore
        .replace(Regex("\\u001B\\][^\\u0007]*\\u0007"), "") // OSC (window title etc.)
        .replace(Regex("\\u001B[()][A-Z0-9]"), "")          // charset selection
        .replace(Regex("\\u001B[>=78M]"), "")               // misc (keypad, scroll, reverse index)

    private fun appendOutput(text: String) {
        _output.value = (_output.value + text).takeLast(60000)
    }

    private fun normalizeCRLF(text: String): String {
        return text.replace("\r\n", "\n").replace("\n", "\r\n")
    }

    fun sendLine(line: String) {
        scope.launch(Dispatchers.IO) {
            synchronized(writeLock) {
                try {
                    val normalized = normalizeCRLF(line + "\n")
                    outputStream?.write(normalized.toByteArray(Charsets.UTF_8))
                    outputStream?.flush()
                } catch (_: Exception) {}
            }
        }
    }

    fun sendRaw(text: String) {
        scope.launch(Dispatchers.IO) {
            synchronized(writeLock) {
                try {
                    val normalized = normalizeCRLF(text)
                    outputStream?.write(normalized.toByteArray(Charsets.UTF_8))
                    outputStream?.flush()
                } catch (_: Exception) {}
            }
        }
    }

    fun sendByte(b: Byte) {
        scope.launch(Dispatchers.IO) {
            synchronized(writeLock) {
                try {
                    outputStream?.write(b.toInt() and 0xFF)
                    outputStream?.flush()
                } catch (_: Exception) {}
            }
        }
    }

    fun sendBytes(data: ByteArray) {
        scope.launch(Dispatchers.IO) {
            synchronized(writeLock) {
                try {
                    outputStream?.write(data)
                    outputStream?.flush()
                } catch (_: Exception) {}
            }
        }
    }

    fun disconnect() {
        _connected.value = false
        try { channel?.disconnect() } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
    }
}
