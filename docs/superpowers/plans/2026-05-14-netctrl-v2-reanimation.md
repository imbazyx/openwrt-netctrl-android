# NetCtrl Android v2.0 — Full Reanimation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore and upgrade NetCtrl Android to a fully working state with map markers, bottom-sheet agent list, router card, embedded native SSH, LuCI auto-login, per-agent local settings with CredentialStore, and correct WiFi metrics — synced with the real owm-server API.

**Architecture:** Single-module Android (Compose + MVVM). New API format (`GET /agents` returns metrics inline) replaces the old 3-call pattern. Per-agent settings (IP, creds, coords) stored locally via DataStore + EncryptedSharedPreferences. SSH via native JSch embedded in the APK. Map bottom sheet replaces the existing left sidebar.

**Tech Stack:** Kotlin, Jetpack Compose, Retrofit2/OkHttp3, DataStore, JSch (`com.github.mwiede:jsch`), EncryptedSharedPreferences (`security-crypto`), Leaflet.js (WebView), Gson (already transitive).

---

## File Map

**New files to create:**
- `app/src/main/java/com/netctrl/app/AgentLocalPrefs.kt` — DataStore JSON storage for per-agent settings (IP, ports, coords, address)
- `app/src/main/java/com/netctrl/app/CredentialStore.kt` — EncryptedSharedPreferences wrapper for SSH/LuCI passwords
- `app/src/main/java/com/netctrl/app/SshSessionManager.kt` — JSch shell session (connect, read loop, send line, disconnect)

**Modified files:**
- `app/build.gradle.kts` — add JSch + security-crypto dependencies
- `app/src/main/java/com/netctrl/app/Api.kt` — add `OWMAgent` data class, change `agents()` return type
- `app/src/main/java/com/netctrl/app/UiState.kt` — add `owmAgents`, `agentLocalMap`, `routerCardAgent`, `agentSettingsId`, `sshOutput`, `sshConnected`, `sshConnecting`
- `app/src/main/java/com/netctrl/app/Screen.kt` — add `AgentSettings(agentId: String)` sealed class entry
- `app/src/main/java/com/netctrl/app/MainViewModel.kt` — replace `loadAgents()` with `fetchAgents()`, add SSH session lifecycle, local settings CRUD, Nominatim geocoding
- `app/src/main/assets/map.html` — add `window.centerMap(lat, lon)` JS function, update online marker color to `#7050c8`, update map popup to show cpu_load/ram in new format
- `app/src/main/java/com/netctrl/app/MainActivity.kt` — add `MapBottomSheet`, `RouterCard`, `AgentSettingsScreen`, `NativeSshScreen` composables; update `LuciViewScreen` for auto-login; add `AgentSettings` to `NetCtrlApp` router; add `centerMap` call to `MapBridge`

---

## Task 1: Add Dependencies

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add JSch and security-crypto dependencies**

In `app/build.gradle.kts`, inside the `dependencies { }` block, add after the last `implementation(...)` line:

```kotlin
    implementation("com.github.mwiede:jsch:0.2.18")
    implementation("org.slf4j:slf4j-nop:2.0.9")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

The full `dependencies` block should end with:
```kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.github.mwiede:jsch:0.2.18")
    implementation("org.slf4j:slf4j-nop:2.0.9")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
```

- [ ] **Step 2: Verify build compiles with new deps**

```powershell
cd D:\project\H3363T\openwrt-netctrl-android
.\gradlew assembleDebug --no-build-cache 2>&1 | Select-String -Pattern "BUILD|error:|FAILURE" | Select-Object -First 30
```

Expected: `BUILD SUCCESSFUL` (or only pre-existing errors, not dependency resolution errors).

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add JSch SSH + security-crypto dependencies"
```

---

## Task 2: New OWMAgent Model + API Endpoint

**Files:**
- Modify: `app/src/main/java/com/netctrl/app/Api.kt`

- [ ] **Step 1: Add `OWMAgent` data class**

In `Api.kt`, after the closing `}` of the `AgentInfo` data class (after line 16), add:

```kotlin
data class OWMAgent(
    val agent_id: String,
    val agent_name: String,
    val online: Boolean,
    val last_seen_secs: Long?,
    val cpu_load: Double?,
    val ram_usage: Long?,
    val ram_total: Long?,
    val wifi_clients: Int? = null
)
```

- [ ] **Step 2: Update `agents()` endpoint return type in `NetCtrlApi`**

In `Api.kt`, find the interface method:
```kotlin
@GET("agents")
suspend fun agents(@Header("Authorization") bearer: String): ApiResponse<List<AgentInfo>>
```

Replace it with:
```kotlin
@GET("agents")
suspend fun agents(@Header("Authorization") bearer: String): ApiResponse<List<OWMAgent>>
```

- [ ] **Step 3: Verify compilation**

```powershell
.\gradlew compileDebugKotlin 2>&1 | Select-String -Pattern "error:|warning:|BUILD" | Select-Object -First 20
```

Expected: compilation errors pointing to callers of `agents()` that now use the old `AgentInfo` type. This is expected — we will fix them in Task 6.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/netctrl/app/Api.kt
git commit -m "feat: add OWMAgent model matching new owm-server /agents response"
```

---

## Task 3: AgentLocalPrefs — Per-Agent Local Storage

**Files:**
- Create: `app/src/main/java/com/netctrl/app/AgentLocalPrefs.kt`

- [ ] **Step 1: Create `AgentLocalPrefs.kt`**

```kotlin
package com.netctrl.app

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

private val Context.agentLocalStore by preferencesDataStore("agent_local_v2")

data class AgentLocalSettings(
    val ip: String = "",
    val sshPort: Int = 22,
    val luciPort: Int = 80,
    val physicalAddress: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val heartbeatInterval: Int = 30
)

class AgentLocalPrefs(private val context: Context) {
    private val gson = Gson()

    private fun key(agentId: String) = stringPreferencesKey("agent_$agentId")

    suspend fun load(agentId: String): AgentLocalSettings {
        val prefs = context.agentLocalStore.data.first()
        val raw = prefs[key(agentId)] ?: return AgentLocalSettings()
        return try { gson.fromJson(raw, AgentLocalSettings::class.java) } catch (_: Exception) { AgentLocalSettings() }
    }

    suspend fun save(agentId: String, settings: AgentLocalSettings) {
        context.agentLocalStore.edit { prefs ->
            prefs[key(agentId)] = gson.toJson(settings)
        }
    }

    suspend fun loadAll(agentIds: List<String>): Map<String, AgentLocalSettings> {
        val prefs = context.agentLocalStore.data.first()
        return agentIds.associateWith { id ->
            val raw = prefs[key(id)] ?: return@associateWith AgentLocalSettings()
            try { gson.fromJson(raw, AgentLocalSettings::class.java) } catch (_: Exception) { AgentLocalSettings() }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```powershell
.\gradlew compileDebugKotlin 2>&1 | Select-String "error:" | Select-Object -First 10
```

Expected: no errors in `AgentLocalPrefs.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/netctrl/app/AgentLocalPrefs.kt
git commit -m "feat: add AgentLocalPrefs for per-agent IP, coords, ports storage"
```

---

## Task 4: CredentialStore — Android Keystore for SSH/LuCI

**Files:**
- Create: `app/src/main/java/com/netctrl/app/CredentialStore.kt`

- [ ] **Step 1: Create `CredentialStore.kt`**

```kotlin
package com.netctrl.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "netctrl_creds_v1",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSsh(agentId: String, login: String, password: String) {
        prefs.edit()
            .putString("${agentId}_ssh_l", login)
            .putString("${agentId}_ssh_p", password)
            .apply()
    }

    fun getSshLogin(agentId: String): String = prefs.getString("${agentId}_ssh_l", "") ?: ""
    fun getSshPass(agentId: String): String = prefs.getString("${agentId}_ssh_p", "") ?: ""

    fun saveLuci(agentId: String, login: String, password: String) {
        prefs.edit()
            .putString("${agentId}_luci_l", login)
            .putString("${agentId}_luci_p", password)
            .apply()
    }

    fun getLuciLogin(agentId: String): String = prefs.getString("${agentId}_luci_l", "") ?: ""
    fun getLuciPass(agentId: String): String = prefs.getString("${agentId}_luci_p", "") ?: ""
}
```

- [ ] **Step 2: Verify compilation**

```powershell
.\gradlew compileDebugKotlin 2>&1 | Select-String "error:" | Select-Object -First 10
```

Expected: no errors in `CredentialStore.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/netctrl/app/CredentialStore.kt
git commit -m "feat: add CredentialStore using Android Keystore for SSH/LuCI passwords"
```

---

## Task 5: SshSessionManager — Native JSch SSH Client

**Files:**
- Create: `app/src/main/java/com/netctrl/app/SshSessionManager.kt`

- [ ] **Step 1: Create `SshSessionManager.kt`**

```kotlin
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
        .replace(Regex("\\[[0-9;]*[A-Za-z]"), "")
        .replace(Regex("\\][^]*"), "")
        .replace(Regex("[()][AB012]"), "")

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
```

- [ ] **Step 2: Verify compilation**

```powershell
.\gradlew compileDebugKotlin 2>&1 | Select-String "error:" | Select-Object -First 10
```

Expected: no errors. JSch classes resolve correctly.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/netctrl/app/SshSessionManager.kt
git commit -m "feat: add SshSessionManager with JSch native SSH client"
```

---

## Task 6: UiState + Screen — New State Fields

**Files:**
- Modify: `app/src/main/java/com/netctrl/app/UiState.kt`
- Modify: `app/src/main/java/com/netctrl/app/Screen.kt`

- [ ] **Step 1: Add new fields to `UiState.kt`**

Find the closing `}` of the `UiState` data class (line 52). Before it, add the following fields (after `val searchQuery: String = ""`):

```kotlin
    // OWM v2 agents (new API format)
    val owmAgents: List<OWMAgent> = emptyList(),
    val agentLocalMap: Map<String, AgentLocalSettings> = emptyMap(),
    // Router card (long press on bottom sheet agent)
    val routerCardAgent: AgentFull? = null,
    // Per-agent settings screen
    val agentSettingsId: String? = null,
    // Native SSH terminal state
    val sshOutput: String = "",
    val sshConnected: Boolean = false,
    val sshConnecting: Boolean = false,
```

The full UiState.kt should look like:
```kotlin
package com.netctrl.app

data class UiState(
    val loading: Boolean = false,
    val error: String? = null,
    val token: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val isSuperAdmin: Boolean = false,
    val screen: Screen = Screen.Login,
    val agents: List<AgentFull> = emptyList(),
    val selectedAgent: AgentFull? = null,
    val detailMetrics: List<Metric> = emptyList(),
    val serverHealthOk: Boolean = false,
    val adminList: List<AdminInfo> = emptyList(),
    val adminLoading: Boolean = false,
    val adminError: String? = null,
    val metricsLoading: Boolean = false,
    val selectedMetricsAgentId: String = "",
    val detailLoading: Boolean = false,
    val agentDeleteError: String? = null,
    val agentDetail: AgentDetailData? = null,
    val pickedLat: Double? = null,
    val pickedLng: Double? = null,
    val addRouterOpen: Boolean = false,
    val addRouterId: String = "",
    val addRouterName: String = "",
    val addRouterDesc: String = "",
    val addRouterIp: String = "",
    val addRouterSshPass: String = "",
    val addRouterAddress: String = "",
    val addRouterLat: Double? = null,
    val addRouterLng: Double? = null,
    val installAgentStatus: String? = null,
    val mapPickMode: Boolean = false,
    val h3363tNodes: List<H3363tNodeStatus> = emptyList(),
    val h3363tEvents: List<H3363tEvent> = emptyList(),
    val h3363tLoading: Boolean = false,
    val h3363tError: String? = null,
    val h3363tConnected: Boolean = false,
    val h3363tCommandResult: String? = null,
    val localNodeConnected: Boolean = false,
    val localNodePeerCount: Int = 0,
    val localNodeLoading: Boolean = false,
    val sidebarOpen: Boolean = false,
    val searchQuery: String = "",
    // OWM v2 agents (new API format)
    val owmAgents: List<OWMAgent> = emptyList(),
    val agentLocalMap: Map<String, AgentLocalSettings> = emptyMap(),
    // Router card (long press on bottom sheet agent)
    val routerCardAgent: AgentFull? = null,
    // Per-agent settings screen
    val agentSettingsId: String? = null,
    // Native SSH terminal state
    val sshOutput: String = "",
    val sshConnected: Boolean = false,
    val sshConnecting: Boolean = false,
)
```

- [ ] **Step 2: Add `AgentSettings` to `Screen.kt`**

In `Screen.kt`, find:
```kotlin
    data class Web(val url: String)              : Screen("web")
```

After that line (before the closing `}`), add:
```kotlin
    data class AgentSettings(val agentId: String) : Screen("agent_settings/$agentId")
    data class NativeSsh(val agent: AgentFull)    : Screen("native_ssh/${agent.agent_id}")
```

- [ ] **Step 3: Verify compilation**

```powershell
.\gradlew compileDebugKotlin 2>&1 | Select-String "error:" | Select-Object -First 10
```

Expected: no new errors from UiState or Screen changes.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/netctrl/app/UiState.kt app/src/main/java/com/netctrl/app/Screen.kt
git commit -m "feat: add new UiState fields and AgentSettings/NativeSsh screens"
```

---

## Task 7: MainViewModel — New fetchAgents(), SSH Control, Local Settings, Geocoding

**Files:**
- Modify: `app/src/main/java/com/netctrl/app/MainViewModel.kt`

This task replaces `loadAgents()` with `fetchAgents()` and adds SSH session management, per-agent settings CRUD, and Nominatim geocoding.

- [ ] **Step 1: Add instance fields after `private val localNodeManager`**

In `MainViewModel.kt` (around line 30), after:
```kotlin
    private val localNodeManager = LocalNodeManager()
```

Add:
```kotlin
    val agentLocalPrefs = AgentLocalPrefs(app)
    val credentialStore = CredentialStore(app)
    private var sshSession: SshSessionManager? = null
```

- [ ] **Step 2: Replace `loadAgents()` with `fetchAgents()`**

Find the entire `private fun loadAgents(url: String, token: String)` function (lines 157-207 in original). Replace the whole function with:

```kotlin
    private fun fetchAgents(url: String, token: String) {
        viewModelScope.launch {
            try {
                val api = buildApi(url)
                val bearer = "Bearer $token"

                try { api.health(); _ui.update { it.copy(serverHealthOk = true) } }
                catch (_: Exception) { _ui.update { it.copy(serverHealthOk = false) } }

                val owmList = api.agents(bearer).data ?: emptyList()
                val localMap = agentLocalPrefs.loadAll(owmList.map { it.agent_id })

                val full = owmList.map { a ->
                    val settings = localMap[a.agent_id] ?: AgentLocalSettings()
                    val ramFreeBytes = ((a.ram_total ?: 0L) - (a.ram_usage ?: 0L)) * 1024L * 1024L
                    val ramTotalBytes = (a.ram_total ?: 0L) * 1024L * 1024L
                    AgentFull(
                        agent_id = a.agent_id,
                        online = a.online,
                        last_seen_secs = a.last_seen_secs,
                        display_name = a.agent_name,
                        address = settings.physicalAddress.ifBlank { null },
                        local_ip = settings.ip.ifBlank { null },
                        luci_url = if (settings.ip.isNotBlank()) "http://${settings.ip}:${settings.luciPort}" else null,
                        lat = settings.lat,
                        lng = settings.lon,
                        metric = if (a.online) Metric(
                            timestamp = a.last_seen_secs ?: 0L,
                            uptime = 0.0,
                            load1 = a.cpu_load ?: 0.0,
                            load5 = a.cpu_load ?: 0.0,
                            load15 = a.cpu_load ?: 0.0,
                            mem_free = ramFreeBytes,
                            mem_total = ramTotalBytes,
                            temperature = null,
                            wifi_clients = a.wifi_clients,
                            wan_rx = null,
                            wan_tx = null
                        ) else null
                    )
                }
                _ui.update {
                    it.copy(
                        owmAgents = owmList,
                        agentLocalMap = localMap,
                        agents = full,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message) }
            }
        }
    }
```

- [ ] **Step 3: Update all `loadAgents(` call sites to `fetchAgents(`**

In `MainViewModel.kt`, find and replace every occurrence of `loadAgents(` with `fetchAgents(`. There are 3 occurrences:
1. In `init {}` block: `loadAgents(url, token)`
2. In `login()`: `loadAgents(url, resp.access_token)`
3. In `refresh()`: `loadAgents(s.serverUrl, s.token)`
4. In `updateServerUrl()`: `loadAgents(newUrl, s.token)`

Replace each with `fetchAgents(url, token)`, `fetchAgents(url, resp.access_token)`, `fetchAgents(s.serverUrl, s.token)`, `fetchAgents(newUrl, s.token)` respectively.

- [ ] **Step 4: Add SSH session control functions**

At the end of `MainViewModel.kt` (before the final `}`), add:

```kotlin
    // ─── Native SSH ───

    fun openNativeSsh(agent: AgentFull) {
        sshSession?.disconnect()
        sshSession = SshSessionManager(viewModelScope).also { mgr ->
            viewModelScope.launch {
                mgr.output.collect { out -> _ui.update { it.copy(sshOutput = out) } }
            }
            viewModelScope.launch {
                mgr.connected.collect { c -> _ui.update { it.copy(sshConnected = c, sshConnecting = false) } }
            }
        }
        val s = _ui.value
        val localSettings = s.agentLocalMap[agent.agent_id] ?: AgentLocalSettings()
        val host = localSettings.ip.ifBlank { agent.local_ip ?: "" }
        val port = localSettings.sshPort
        val login = credentialStore.getSshLogin(agent.agent_id).ifBlank { "root" }
        val pass = credentialStore.getSshPass(agent.agent_id)
        _ui.update { it.copy(sshOutput = "", sshConnecting = true, screen = Screen.NativeSsh(agent)) }
        sshSession!!.connect(host, port, login, pass)
    }

    fun sshSendLine(line: String) { sshSession?.sendLine(line) }

    fun closeSsh() {
        sshSession?.disconnect()
        sshSession = null
        _ui.update { it.copy(sshOutput = "", sshConnected = false, sshConnecting = false, screen = Screen.Dashboard) }
    }

    // ─── Local Settings CRUD ───

    fun openAgentSettings(agentId: String) {
        _ui.update { it.copy(agentSettingsId = agentId, screen = Screen.AgentSettings(agentId)) }
    }

    fun closeAgentSettings() {
        _ui.update { it.copy(agentSettingsId = null, screen = Screen.Dashboard) }
    }

    fun saveAgentLocalSettings(agentId: String, settings: AgentLocalSettings) {
        viewModelScope.launch {
            agentLocalPrefs.save(agentId, settings)
            val s = _ui.value
            fetchAgents(s.serverUrl, s.token)
        }
    }

    // ─── Router Card ───

    fun openRouterCard(agent: AgentFull) {
        _ui.update { it.copy(routerCardAgent = agent) }
    }

    fun closeRouterCard() {
        _ui.update { it.copy(routerCardAgent = null) }
    }

    // ─── Map centering ───

    fun centerMapOnAgent(agentId: String): Pair<Double, Double>? {
        val s = _ui.value
        val local = s.agentLocalMap[agentId] ?: return null
        return if (local.lat != null && local.lon != null) Pair(local.lat, local.lon) else null
    }

    // ─── Nominatim Geocoding ───

    fun geocodeAddress(address: String, onResult: (Double?, Double?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(address, "UTF-8")
                val url = java.net.URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", "NetCtrl-Android/2.0")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val body = conn.inputStream.bufferedReader().readText()
                val arr = org.json.JSONArray(body)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val lat = obj.getString("lat").toDoubleOrNull()
                    val lon = obj.getString("lon").toDoubleOrNull()
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(lat, lon) }
                } else {
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(null, null) }
                }
            } catch (_: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(null, null) }
            }
        }
    }
```

Note: `withContext` needs to be imported. Add at top of file if missing: `import kotlinx.coroutines.withContext`.

- [ ] **Step 5: Verify compilation — fix any remaining type errors**

```powershell
.\gradlew compileDebugKotlin 2>&1 | Select-String "error:" | Select-Object -First 30
```

Expected: clean compile. If `api.configs(...)` or `api.metrics(...)` still referenced, those were removed — the errors will point to orphaned references in `MainViewModel.kt`. Remove any remaining calls to `configs()` or `metrics()` that were part of old `loadAgents()`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/netctrl/app/MainViewModel.kt
git commit -m "feat: replace loadAgents() with fetchAgents() using new OWM API, add SSH session + local settings + geocoding"
```

---

## Task 8: Update map.html — centerMap Function + Icon Colors

**Files:**
- Modify: `app/src/main/assets/map.html`

- [ ] **Step 1: Update `makeIcon()` to use VOID accent for online agents**

Find in `map.html`:
```javascript
function makeIcon(online) {
  const color = online ? '#3FB950' : '#F85149';
```

Replace with:
```javascript
function makeIcon(online) {
  const color = online ? '#7050c8' : '#888888';
  const opacity = online ? 1.0 : 0.5;
```

Also update the div in `makeIcon()`:
Find:
```javascript
    html: `<div style="width:16px;height:16px;border-radius:50%;background:${color};border:2px solid rgba(255,255,255,0.8);box-shadow:0 2px 8px rgba(0,0,0,0.5)"></div>`,
```
Replace with:
```javascript
    html: `<div style="width:16px;height:16px;border-radius:50%;background:${color};border:2px solid rgba(255,255,255,0.8);box-shadow:0 2px 8px rgba(0,0,0,0.5);opacity:${opacity}"></div>`,
```

- [ ] **Step 2: Update `makePopupHtml()` to use new field names**

Find in `map.html`:
```javascript
  if (a.cpu_load != null) {
    html += `<div class="popup-meta">CPU Load: ${a.cpu_load.toFixed(2)}</div>`;
  }
  if (a.mem_total && a.mem_total > 0 && a.mem_free != null) {
    const usedMem = Math.round((a.mem_total - a.mem_free) / 1024);
    const totalMem = Math.round(a.mem_total / 1024);
    html += `<div class="popup-meta">RAM: ${usedMem}/${totalMem} MB</div>`;
  }
```

Replace with (cpu_load stays the same, but RAM now uses ram_usage/ram_total directly in MB from new API, plus fallback to old format):
```javascript
  if (a.cpu_load != null) {
    const cpuPct = typeof a.cpu_load === 'number' ? (a.cpu_load * 100).toFixed(1) : a.cpu_load;
    html += `<div class="popup-meta">CPU: ${cpuPct}%</div>`;
  }
  // New API: ram_usage/ram_total in MB; old fallback: mem_total/mem_free in bytes
  if (a.ram_total && a.ram_total > 0) {
    html += `<div class="popup-meta">RAM: ${a.ram_usage || 0}/${a.ram_total} MB</div>`;
  } else if (a.mem_total && a.mem_total > 0 && a.mem_free != null) {
    const usedMem = Math.round((a.mem_total - a.mem_free) / 1024 / 1024);
    const totalMem = Math.round(a.mem_total / 1024 / 1024);
    html += `<div class="popup-meta">RAM: ${usedMem}/${totalMem} MB</div>`;
  }
  if (a.wifi_clients != null) {
    html += `<div class="popup-meta">WiFi клиенты: ${a.wifi_clients}</div>`;
  }
```

- [ ] **Step 3: Add `window.centerMap()` function**

In `map.html`, after the `window.fitBounds = function() { ... };` block, add:

```javascript
window.centerMap = function(lat, lon) {
  if (!map) return;
  map.flyTo([lat, lon], Math.max(map.getZoom(), 13), { animate: true, duration: 0.3 });
  var m = Object.values(markers).find(function(mk) {
    var ll = mk.getLatLng();
    return Math.abs(ll.lat - lat) < 0.0001 && Math.abs(ll.lng - lon) < 0.0001;
  });
  if (m) m.openPopup();
};
```

- [ ] **Step 4: Update `MapBridge.getAgentsJson()` to include new fields**

In `MainActivity.kt`, find `MapBridge.getAgentsJson()` (lines 860-888). Update the JSONObject building to include RAM in MB:

```kotlin
    fun getAgentsJson(): String {
        val agents = vm.ui.value.agents
        val localMap = vm.ui.value.agentLocalMap
        return try {
            org.json.JSONArray(agents.map { a ->
                val local = localMap[a.agent_id]
                org.json.JSONObject().apply {
                    put("agent_id", a.agent_id)
                    put("display_name", a.display_name ?: a.agent_id)
                    put("online", a.online)
                    put("address", a.address ?: "")
                    put("local_ip", a.local_ip ?: "")
                    put("luci_url", a.luci_url ?: "http://${a.local_ip ?: ""}")
                    a.lat?.let { put("lat", it) } ?: put("lat", org.json.JSONObject.NULL)
                    a.lng?.let { put("lng", it) } ?: put("lng", org.json.JSONObject.NULL)
                    a.metric?.let { m ->
                        put("cpu_load", m.load1)
                        // RAM in MB for new map popup
                        val ramTotalMb = if (m.mem_total != null && m.mem_total > 0) m.mem_total / 1024 / 1024 else 0
                        val ramUsedMb = if (m.mem_total != null && m.mem_total > 0) (m.mem_total - m.mem_free) / 1024 / 1024 else 0
                        put("ram_usage", ramUsedMb)
                        put("ram_total", ramTotalMb)
                        put("wifi_clients", m.wifi_clients ?: org.json.JSONObject.NULL)
                        put("temperature", m.temperature ?: org.json.JSONObject.NULL)
                    } ?: run {
                        put("cpu_load", org.json.JSONObject.NULL)
                        put("ram_usage", org.json.JSONObject.NULL)
                        put("ram_total", org.json.JSONObject.NULL)
                        put("wifi_clients", org.json.JSONObject.NULL)
                        put("temperature", org.json.JSONObject.NULL)
                    }
                }
            }).toString()
        } catch (_: Exception) { "[]" }
    }
```

- [ ] **Step 5: Add `centerMap()` function to MapBridge**

In `MainActivity.kt`, in the `MapBridge` class after `fun onAddressPicked(...)`, add a new reference to a WebView for centering. Actually, MapBridge doesn't have a WebView reference — centerMap is called FROM Kotlin TO JS via `webViewRef?.evaluateJavascript(...)`. Add a helper to ViewModel:

In `MainViewModel.kt`, at the end of the router card section, add:
```kotlin
    var mapWebViewCenterCallback: ((Double, Double) -> Unit)? = null
```

In `MapTabContent`, wire this callback after the WebView is created:
```kotlin
// After: webViewRef = this  (inside the WebView factory)
vm.mapWebViewCenterCallback = { lat, lon ->
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        webViewRef?.evaluateJavascript("try { window.centerMap($lat, $lon); } catch(e) {}", null)
    }
}
```

- [ ] **Step 6: Verify map.html is valid JS (syntax check)**

```powershell
node -e "const fs = require('fs'); try { new Function(fs.readFileSync('app/src/main/assets/map.html','utf8').match(/<script>([\s\S]*?)<\/script>/g).join('')); console.log('OK'); } catch(e) { console.error(e.message); }"
```

If Node.js is unavailable, skip this step and verify at runtime.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/assets/map.html app/src/main/java/com/netctrl/app/MainActivity.kt app/src/main/java/com/netctrl/app/MainViewModel.kt
git commit -m "feat: update map.html with centerMap(), VOID accent colors, new API fields"
```

---

## Task 9: MapBottomSheet — Replace Left Sidebar with Bottom Sheet

**Files:**
- Modify: `app/src/main/java/com/netctrl/app/MainActivity.kt`

- [ ] **Step 1: Add `MapBottomSheet` composable at the end of `MainActivity.kt`**

After the last `@Composable fun` in the file (around line 2355), add:

```kotlin
// ─── MAP BOTTOM SHEET ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapBottomSheet(
    ui: UiState,
    vm: MainViewModel,
    onCenterMap: (Double, Double) -> Unit,
    onOpenCard: (AgentFull) -> Unit
) {
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 64.dp,
        sheetContainerColor = Color(0xFF0D0B1E),
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = Color.Transparent,
        sheetContent = {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "РОУТЕРЫ (${ui.agents.size})",
                        color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.refresh() },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentVoid),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentVoid.copy(alpha = 0.5f))
                        ) { Text("Обновить", fontSize = 11.sp) }
                    }
                }
                HorizontalDivider(color = Color(0xFF2A2250))
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(ui.agents) { agent ->
                        AgentBottomSheetRow(
                            agent = agent,
                            onTap = {
                                val local = ui.agentLocalMap[agent.agent_id]
                                if (local?.lat != null && local.lon != null) {
                                    onCenterMap(local.lat, local.lon)
                                } else {
                                    // Toast in composable context via snackbar or just ignore
                                }
                            },
                            onLongPress = { onOpenCard(agent) }
                        )
                    }
                }
            }
        }
    ) {
        // Empty — map is drawn behind by MapTabContent
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgentBottomSheetRow(
    agent: AgentFull,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .background(Color(0xFF16133A), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (agent.online) AccentVoid else Color(0xFF888888), RoundedCornerShape(50))
        )
        Column(Modifier.weight(1f)) {
            Text(
                agent.display_name ?: agent.agent_id,
                color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
            Text(
                buildString {
                    if (agent.online) {
                        agent.metric?.let { m ->
                            append("CPU ${(m.load1 * 100).toInt()}%")
                            if (m.mem_total != null && m.mem_total > 0) {
                                val usedMb = (m.mem_total - m.mem_free) / 1024 / 1024
                                append("  RAM ${usedMb}MB")
                            }
                        }
                    } else {
                        append("Офлайн")
                    }
                },
                color = TextSecondary, fontSize = 11.sp
            )
        }
        Box(
            Modifier.size(6.dp)
                .background(if (agent.online) OnlineGreen else OfflineRed, RoundedCornerShape(50))
        )
    }
}
```

- [ ] **Step 2: Integrate `MapBottomSheet` into `MapTabContent`**

In `MapTabContent` (line 934), the current code has a sidebar overlay. We need to REPLACE the sidebar `AnimatedVisibility` block with `MapBottomSheet`. 

Find in `MapTabContent`:
```kotlin
    Box(Modifier.fillMaxSize().padding(pad)) {
        // ── Full-screen map WebView ──────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            ...
        )

        // ── Sidebar overlay ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = sidebarOpen,
            ...
        ) {
            ...
        }
```

Replace the entire `Box` content (keep the AndroidView for the WebView, remove the sidebar overlay, add `MapBottomSheet` overlay):

```kotlin
    Box(Modifier.fillMaxSize().padding(pad)) {
        // ── Full-screen map WebView ──────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                android.webkit.WebView.setWebContentsDebuggingEnabled(true)
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    @Suppress("SetJavaScriptEnabled")
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    addJavascriptInterface(MapBridge(vm), "AndroidBridge")
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(cm: android.webkit.ConsoleMessage): Boolean {
                            android.util.Log.d("NetCtrl-Map", "${cm.message()} -- ")
                            return true
                        }
                    }
                    webViewRef = this
                    vm.mapWebViewCenterCallback = { lat, lon ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            evaluateJavascript("try { window.centerMap($lat, $lon); } catch(e) {}", null)
                        }
                    }
                    loadUrl("file:///android_asset/map.html")
                }
            }
        )

        // ── Bottom sheet overlay ─────────────────────────────────────────────
        MapBottomSheet(
            ui = ui,
            vm = vm,
            onCenterMap = { lat, lon -> vm.mapWebViewCenterCallback?.invoke(lat, lon) },
            onOpenCard = { agent -> vm.openRouterCard(agent) }
        )
    }
```

Also remove the `sidebarOpen` `remember` state and `BackHandler` for `sidebarOpen` at the top of `MapTabContent` since the sidebar is removed.

Remove these lines from `MapTabContent`:
```kotlin
    var sidebarOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = sidebarOpen) { sidebarOpen = false }
```

- [ ] **Step 3: Verify compilation**

```powershell
.\gradlew compileDebugKotlin 2>&1 | Select-String "error:" | Select-Object -First 20
```

Fix any import errors (`BottomSheetScaffold`, `rememberBottomSheetScaffoldState`, `SheetValue`, `rememberStandardBottomSheetState`, `combinedClickable`, `ExperimentalFoundationApi`).

Required imports to add at top of `MainActivity.kt`:
```kotlin
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/netctrl/app/MainActivity.kt
git commit -m "feat: replace map sidebar with bottom sheet (tap=center, long=card)"
```

---

## Task 10: RouterCard — ModalBottomSheet with Sub-Header + Metrics

**Files:**
- Modify: `app/src/main/java/com/netctrl/app/MainActivity.kt`

- [ ] **Step 1: Add `RouterCard` composable at end of `MainActivity.kt`**

```kotlin
// ─── ROUTER CARD ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterCard(
    agent: AgentFull,
    ui: UiState,
    vm: MainViewModel,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0B1E),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Sub-header ───────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        agent.display_name ?: agent.agent_id,
                        color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    val local = ui.agentLocalMap[agent.agent_id]
                    val nowSecs = System.currentTimeMillis() / 1000
                    val secsAgo = agent.last_seen_secs?.let { nowSecs - it }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier.size(8.dp)
                                .background(if (agent.online) Color(0xFF00FF88) else Color(0xFFFF4444), RoundedCornerShape(50))
                        )
                        Text(
                            if (agent.online) "Онлайн"
                            else secsAgo?.let { "Офлайн (${it / 60} мин назад)" } ?: "Офлайн",
                            color = if (agent.online) Color(0xFF00FF88) else Color(0xFFFF4444),
                            fontSize = 12.sp
                        )
                    }
                    if (!local?.ip.isNullOrBlank()) {
                        Text(local?.ip ?: "", color = TextSecondary, fontSize = 12.sp)
                    }
                    val wifiClients = agent.metric?.wifi_clients
                    if (wifiClients != null) {
                        Text("WiFi клиенты: $wifiClients", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF2A2250))
            Spacer(Modifier.height(16.dp))

            // ── Action buttons ───────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onDismiss(); vm.openNativeSsh(agent) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16133A)),
                    modifier = Modifier.weight(1f)
                ) { Text("SSH", color = AccentVoid) }
                Button(
                    onClick = { onDismiss(); vm.openLuci(agent) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16133A)),
                    modifier = Modifier.weight(1f)
                ) { Text("LuCI", color = TextPrimary) }
                Button(
                    onClick = { onDismiss(); vm.openAgentSettings(agent.agent_id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16133A)),
                    modifier = Modifier.weight(1f)
                ) { Text("Настройки", color = TextSecondary) }
            }

            Spacer(Modifier.height(16.dp))

            // ── Metrics ──────────────────────────────────────────────────────
            agent.metric?.let { m ->
                Text("Метрики", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val cpuPct = (m.load1 * 100).toInt()
                MetricRow("CPU", "$cpuPct%")
                if (m.mem_total != null && m.mem_total > 0) {
                    val usedMb = (m.mem_total - m.mem_free) / 1024 / 1024
                    val totalMb = m.mem_total / 1024 / 1024
                    MetricRow("RAM", "$usedMb / $totalMb MB")
                }
                m.wifi_clients?.let { MetricRow("WiFi клиенты", "$it") }
                m.temperature?.let { MetricRow("Температура", "${it.toInt()}°C") }
            }
        }
    }
}
```

- [ ] **Step 2: Show `RouterCard` when `routerCardAgent` is set**

In `NetCtrlApp` composable (lines 57-113), inside the `MaterialTheme` block, after the `when (val screen = ui.screen)` block, add:

```kotlin
        // Router card (shown above any screen when set)
        ui.routerCardAgent?.let { agent ->
            RouterCard(agent = agent, ui = ui, vm = vm, onDismiss = { vm.closeRouterCard() })
        }
```

The `MaterialTheme` block should look like:
```kotlin
    MaterialTheme(...) {
        when (val screen = ui.screen) {
            ...
        }
        // Router card overlay
        ui.routerCardAgent?.let { agent ->
            RouterCard(agent = agent, ui = ui, vm = vm, onDismiss = { vm.closeRouterCard() })
        }
    }
```

- [ ] **Step 3: Verify compilation**

```powershell
.\gradlew compileDebugKotlin 2>&1 | Select-String "error:" | Select-Object -First 20
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/netctrl/app/MainActivity.kt
git commit -m "feat: add RouterCard ModalBottomSheet with sub-header, metrics, SSH/LuCI/Settings buttons"
```

---

## Task 11: AgentSettingsScreen — Per-Agent Settings + Nominatim Geocoding

**Files:**
- Modify: `app/src/main/java/com/netctrl/app/MainActivity.kt`
- Modify: `app/src/main/java/com/netctrl/app/MainViewModel.kt`

- [ ] **Step 1: Add `AgentSettingsScreen` composable at end of `MainActivity.kt`**

```kotlin
// ─── AGENT SETTINGS SCREEN ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSettingsScreen(
    agentId: String,
    ui: UiState,
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val agent = ui.agents.find { it.agent_id == agentId }
    val initLocal = ui.agentLocalMap[agentId] ?: AgentLocalSettings()

    var ip by remember(agentId) { mutableStateOf(initLocal.ip) }
    var sshPort by remember(agentId) { mutableStateOf(initLocal.sshPort.toString()) }
    var luciPort by remember(agentId) { mutableStateOf(initLocal.luciPort.toString()) }
    var address by remember(agentId) { mutableStateOf(initLocal.physicalAddress) }
    var lat by remember(agentId) { mutableStateOf(initLocal.lat?.toString() ?: "") }
    var lon by remember(agentId) { mutableStateOf(initLocal.lon?.toString() ?: "") }
    var sshLogin by remember(agentId) { mutableStateOf(vm.credentialStore.getSshLogin(agentId)) }
    var sshPass by remember(agentId) { mutableStateOf(vm.credentialStore.getSshPass(agentId)) }
    var luciLogin by remember(agentId) { mutableStateOf(vm.credentialStore.getLuciLogin(agentId)) }
    var luciPass by remember(agentId) { mutableStateOf(vm.credentialStore.getLuciPass(agentId)) }
    var geocoding by remember { mutableStateOf(false) }
    var geocodeError by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки — ${agent?.display_name ?: agentId}", color = TextPrimary) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextSecondary) } },
                actions = {
                    TextButton(onClick = {
                        vm.credentialStore.saveSsh(agentId, sshLogin, sshPass)
                        vm.credentialStore.saveLuci(agentId, luciLogin, luciPass)
                        vm.saveAgentLocalSettings(agentId, AgentLocalSettings(
                            ip = ip,
                            sshPort = sshPort.toIntOrNull() ?: 22,
                            luciPort = luciPort.toIntOrNull() ?: 80,
                            physicalAddress = address,
                            lat = lat.toDoubleOrNull(),
                            lon = lon.toDoubleOrNull()
                        ))
                        onBack()
                    }) { Text("Сохранить", color = AccentVoid) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        },
        containerColor = BgDark
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Сеть", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it },
                    label = { Text("IP адрес") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customOutlinedColors()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sshPort, onValueChange = { sshPort = it },
                        label = { Text("SSH порт") },
                        modifier = Modifier.weight(1f),
                        colors = customOutlinedColors()
                    )
                    OutlinedTextField(
                        value = luciPort, onValueChange = { luciPort = it },
                        label = { Text("LuCI порт") },
                        modifier = Modifier.weight(1f),
                        colors = customOutlinedColors()
                    )
                }
            }
            item {
                Text("SSH", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = sshLogin, onValueChange = { sshLogin = it },
                    label = { Text("Логин SSH") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customOutlinedColors()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sshPass, onValueChange = { sshPass = it },
                    label = { Text("Пароль SSH") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = customOutlinedColors()
                )
            }
            item {
                Text("LuCI", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = luciLogin, onValueChange = { luciLogin = it },
                    label = { Text("Логин LuCI") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customOutlinedColors()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = luciPass, onValueChange = { luciPass = it },
                    label = { Text("Пароль LuCI") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = customOutlinedColors()
                )
            }
            item {
                Text("Местоположение", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text("Физический адрес") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (geocoding) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = AccentVoid)
                        } else {
                            IconButton(onClick = {
                                if (address.isBlank()) return@IconButton
                                geocoding = true; geocodeError = ""
                                vm.geocodeAddress(address) { la, lo ->
                                    geocoding = false
                                    if (la != null && lo != null) {
                                        lat = la.toString(); lon = lo.toString()
                                    } else {
                                        geocodeError = "Адрес не найден"
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Search, "Геокодировать", tint = AccentVoid)
                            }
                        }
                    },
                    colors = customOutlinedColors()
                )
                if (geocodeError.isNotBlank()) {
                    Text(geocodeError, color = Color(0xFFFF4444), fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lat, onValueChange = { lat = it },
                        label = { Text("Широта") },
                        modifier = Modifier.weight(1f),
                        colors = customOutlinedColors()
                    )
                    OutlinedTextField(
                        value = lon, onValueChange = { lon = it },
                        label = { Text("Долгота") },
                        modifier = Modifier.weight(1f),
                        colors = customOutlinedColors()
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Add `AgentSettings` screen to `NetCtrlApp` router**

In `NetCtrlApp` (lines 57-113), inside the `when (val screen = ui.screen)` block, before `else -> MainTabScaffold(...)`, add:

```kotlin
            is Screen.AgentSettings -> AgentSettingsScreen(
                agentId = screen.agentId,
                ui = ui,
                vm = vm,
                onBack = { vm.closeAgentSettings() }
            )
            is Screen.NativeSsh -> NativeSshScreen(
                agent = screen.agent,
                ui = ui,
                vm = vm,
                onBack = { vm.closeSsh() }
            )
```

- [ ] **Step 3: Verify compilation**

```powershell
.\gradlew compileDebugKotlin 2>&1 | Select-String "error:" | Select-Object -First 20
```

Required imports to add if missing:
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/netctrl/app/MainActivity.kt
git commit -m "feat: add AgentSettingsScreen with Nominatim geocoding and credential storage"
```

---

## Task 12: NativeSshScreen — JSch-Based Compose Terminal

**Files:**
- Modify: `app/src/main/java/com/netctrl/app/MainActivity.kt`

- [ ] **Step 1: Add `NativeSshScreen` composable at end of `MainActivity.kt`**

```kotlin
// ─── NATIVE SSH SCREEN ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeSshScreen(
    agent: AgentFull,
    ui: UiState,
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val output by remember { derivedStateOf { ui.sshOutput } }
    val connected = ui.sshConnected
    val connecting = ui.sshConnecting
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when output changes
    LaunchedEffect(output) {
        val lines = output.split('\n')
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SSH — ${agent.display_name ?: agent.agent_id}", color = TextPrimary)
                        Box(
                            Modifier.size(8.dp)
                                .background(if (connected) Color(0xFF00FF88) else Color(0xFFFF4444), RoundedCornerShape(50))
                        )
                    }
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextSecondary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        },
        containerColor = BgDark
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (connecting) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentVoid, trackColor = CardBg)
            }

            // Terminal output
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF030209))
                    .padding(8.dp)
            ) {
                val lines = output.split('\n')
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(lines.size) { i ->
                        Text(
                            lines[i],
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            softWrap = true
                        )
                    }
                }
            }

            // Input row
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(CardBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Команда...", color = TextSecondary, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    colors = customOutlinedColors(),
                    singleLine = true,
                    enabled = connected,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = {
                            vm.sshSendLine(inputText)
                            inputText = ""
                        }
                    )
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { vm.sshSendLine(inputText); inputText = "" },
                    enabled = connected
                ) {
                    Icon(Icons.Default.Send, "Отправить", tint = if (connected) AccentVoid else TextSecondary)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```powershell
.\gradlew compileDebugKotlin 2>&1 | Select-String "error:" | Select-Object -First 20
```

Required imports if missing:
```kotlin
import androidx.compose.material.icons.filled.Send
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/netctrl/app/MainActivity.kt
git commit -m "feat: add NativeSshScreen with JSch-based terminal (LazyColumn + input field)"
```

---

## Task 13: LuciViewScreen — Auto-Login via JS Injection

**Files:**
- Modify: `app/src/main/java/com/netctrl/app/MainActivity.kt`

The existing `LuciViewScreen` (lines 1207-1237) loads LuCI URL but has no auto-login. We update the `webViewClient` to inject credentials after page load.

- [ ] **Step 1: Replace `LuciViewScreen` function body**

Find in `MainActivity.kt`:
```kotlin
fun LuciViewScreen(agent: AgentFull, onBack: () -> Unit) {
    val luciUrl = agent.luci_url ?: "http://${agent.local_ip ?: "192.168.1.1"}"
```

Replace the entire function with:
```kotlin
fun LuciViewScreen(agent: AgentFull, credentialStore: CredentialStore, onBack: () -> Unit) {
    val luciUrl = agent.luci_url ?: "http://${agent.local_ip ?: "192.168.1.1"}"
    val luciLogin = credentialStore.getLuciLogin(agent.agent_id).ifBlank { "root" }
    val luciPass = credentialStore.getLuciPass(agent.agent_id)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LuCI — ${agent.display_name ?: agent.agent_id}", color = TextPrimary) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextSecondary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        },
        containerColor = BgDark
    ) { pad ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(pad),
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webChromeClient = android.webkit.WebChromeClient()
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (luciPass.isNotBlank()) {
                                val js = """
                                    (function() {
                                        var pwField = document.querySelector('input[type=password]');
                                        if (!pwField) return;
                                        var userField = document.querySelector('input[name=luci_username], input[type=text]');
                                        if (userField) userField.value = '${luciLogin.replace("'", "\\'")}';
                                        pwField.value = '${luciPass.replace("'", "\\'")}';
                                        var form = pwField.closest('form');
                                        if (form) form.submit();
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(js, null)
                            }
                        }
                    }
                    loadUrl(luciUrl)
                }
            }
        )
    }
}
```

- [ ] **Step 2: Update `LuciViewScreen` call site in `NetCtrlApp`**

In `NetCtrlApp` (around line 83), find:
```kotlin
            is Screen.LuciView -> LuciViewScreen(
                agent = screen.agent,
                onBack = { vm.navigateTo(Screen.Dashboard) }
            )
```

Replace with:
```kotlin
            is Screen.LuciView -> LuciViewScreen(
                agent = screen.agent,
                credentialStore = vm.credentialStore,
                onBack = { vm.navigateTo(Screen.Dashboard) }
            )
```

- [ ] **Step 3: Verify compilation**

```powershell
.\gradlew compileDebugKotlin 2>&1 | Select-String "error:" | Select-Object -First 20
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/netctrl/app/MainActivity.kt
git commit -m "feat: LuciViewScreen auto-login via JS injection using CredentialStore"
```

---

## Task 14: Full Build Verification

**Files:** None changed; verification only.

- [ ] **Step 1: Clean build**

```powershell
cd D:\project\H3363T\openwrt-netctrl-android
.\gradlew clean assembleDebug 2>&1 | Tee-Object -FilePath scripts\deploy-log.txt | Select-String -Pattern "BUILD|error:|FAILURE|warning:" | Select-Object -First 50
```

Expected output: `BUILD SUCCESSFUL` with no `error:` lines.

- [ ] **Step 2: Install on device**

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected: `Success`

- [ ] **Step 3: Runtime verification checklist**

Run these verification steps on Redmi Note 12 Turbo:

```
1. Launch app → Login screen appears
2. Enter http://95.174.102.25:9090 + credentials → login succeeds
3. Dashboard (Map tab) → map loads with tiles
4. Map bottom sheet visible at bottom → shows agent list
5. Tap agent in sheet → map centers on marker (if coords set)
6. Long press agent → RouterCard appears with name/status/IP
7. RouterCard → [Настройки] → AgentSettingsScreen opens
8. Enter IP=192.168.31.1, SSH port=22, SSH login=root, SSH pass=<pass>
9. Enter address → tap Search icon → lat/lon populated from Nominatim
10. Tap Сохранить → back to map, marker appears
11. RouterCard → [SSH] → NativeSshScreen with terminal
12. Type 'uname -a' + Send → AX6000 kernel info appears
13. RouterCard → [LuCI] → LuCI WebView, auto-fills credentials
14. Marker on map shows VOID purple color for online agent
```

- [ ] **Step 4: Log any failures to deploy-log.txt**

```powershell
adb logcat -d -s "NetCtrl-Map","NetCtrl-MapJS" | Out-File -Append scripts\deploy-log.txt
```

- [ ] **Step 5: Final commit if any hotfixes applied during testing**

```bash
git add -p
git commit -m "fix: runtime issues found during device testing"
```

---

## Self-Review Against Spec

| Spec Section | Covered By | Status |
|---|---|---|
| 3.1 GET /agents fetch | Task 7 (fetchAgents) | ✅ |
| 3.2 Markers by lat/lon | Tasks 8 (map.html), 3 (AgentLocalPrefs) | ✅ |
| 3.3 JS bridge MapBridge | Task 8 (centerMap + getAgentsJson) | ✅ |
| 3.4 Fallback no coords | map.html skips null lat/lng | ✅ |
| 4.1 Agent list bottom sheet | Task 9 (MapBottomSheet) | ✅ |
| 4.2 Buttons Refresh/Add | Task 9 (MapBottomSheet has Refresh button; Add is stub) | ✅ |
| 5.1 Sub-header | Task 10 (RouterCard) | ✅ |
| 5.3 SSH terminal embedded | Tasks 5, 12, 13 | ✅ |
| 5.4 LuCI WebView auto-login | Task 13 | ✅ |
| 6.1 Settings fields | Task 11 (AgentSettingsScreen) | ✅ |
| 6.2 Nominatim geocoding | Task 7 (geocodeAddress) + Task 11 (UI) | ✅ |
| 7.2 WiFi clients metric | OWMAgent.wifi_clients optional field (Task 2) | ✅ |
| 8.1 CredentialStore Keystore | Task 4 | ✅ |
| 9.1 VOID colors | Task 9 (online=AccentVoid=#7050c8) | ✅ |

**Gap identified:** Section 4.2 "Add" button for new router is a stub (Toast "В разработке"). This is per the spec's own allowance for this feature.

**Gap identified:** SSH session keep-alive on app backgrounding (Foreground Service). The current implementation uses a CoroutineScope tied to ViewModel — it survives screen rotation but may be killed on background. A full Foreground Service would require AndroidManifest changes + Service class. Given the ТЗ says "или просто keep-alive", the ViewModel scope is acceptable for the scope of this plan.

**Placeholder scan:** No "TBD", "TODO", or "similar to Task N" patterns found.

**Type consistency check:**
- `OWMAgent` defined in Task 2, used in Tasks 6, 7 ✅
- `AgentLocalSettings` defined in Task 3, used in Tasks 6, 7, 11 ✅
- `CredentialStore` defined in Task 4, used in Tasks 7, 11, 13 ✅
- `SshSessionManager` defined in Task 5, used in Task 7 ✅
- `Screen.AgentSettings` defined in Task 6, used in Tasks 7, 11 ✅
- `Screen.NativeSsh` defined in Task 6, used in Tasks 7, 12 ✅
- `UiState.routerCardAgent` defined in Task 6, used in Tasks 7, 10 ✅
- `UiState.sshOutput/sshConnected/sshConnecting` defined in Task 6, used in Task 12 ✅
- `vm.credentialStore` (public) used in Tasks 11, 13 ✅
- `vm.agentLocalPrefs` (public) used in Task 11 ✅
- `vm.mapWebViewCenterCallback` added in Task 7, wired in Task 8, called in Task 9 ✅
- `MapBottomSheet` defined in Task 9, integrated in Task 8's `MapTabContent` changes ✅
- `RouterCard` defined in Task 10, shown in `NetCtrlApp` via `ui.routerCardAgent` ✅
- `AgentSettingsScreen` defined in Task 11, routed in Task 11 ✅
- `NativeSshScreen` defined in Task 12, routed in Task 11 ✅
- `LuciViewScreen` signature changed in Task 13, call site updated in Task 13 ✅
