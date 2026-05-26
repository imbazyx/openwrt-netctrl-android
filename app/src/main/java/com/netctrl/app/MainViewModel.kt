package com.netctrl.app

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.*
import java.util.concurrent.TimeUnit

import com.netctrl.app.NearbyNode

private fun decodeJwtRole(token: String): String {
    return try {
        val payload = token.split(".").getOrNull(1) ?: return "admin"
        val decoded = android.util.Base64.decode(
            payload.replace("-", "+").replace("_", "/"),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        org.json.JSONObject(String(decoded)).optString("role", "admin")
    } catch (_: Exception) { "admin" }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var wsManager: H3363tWebSocketManager? = null
    private var wsCollectorJob: kotlinx.coroutines.Job? = null
    private val localNodeManager = LocalNodeManager()
    val agentLocalPrefs = AgentLocalPrefs(app)
    val credentialStore = CredentialStore(app)
    private var sshSession: SshSessionManager? = null
    var mapWebViewCenterCallback: ((Double, Double) -> Unit)? = null

    // WakeLock и WifiLock для поддержания WS-соединения
    private val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wifiManager = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // mDNS / NsdManager
    private val nsdManager = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null

    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "H3363T::WsWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 минут
            }
        }
        if (wifiLock?.isHeld != true) {
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "H3363T::WsWifiLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
    }

    init {
        viewModelScope.launch {
            combine(prefs.token, prefs.serverUrl, prefs.username) { t, u, n ->
                Triple(t, u, n)
            }.collect { (token, url, name) ->
                if (!token.isNullOrBlank() && !url.isNullOrBlank()) {
                    _ui.update {
                        it.copy(
                            token = token, serverUrl = url,
                            username = name ?: "", screen = Screen.Dashboard,
                            isSuperAdmin = decodeJwtRole(token) == "superadmin"
                        )
                    }
                    connectH3363tWs(url, token)
                    fetchAgents(url, token)
                }
            }
        }

        // Collect local node state
        viewModelScope.launch {
            localNodeManager.connected.collect { connected ->
                _ui.update { it.copy(localNodeConnected = connected, localNodeLoading = false) }
            }
        }
        viewModelScope.launch {
            localNodeManager.peerCount.collect { count ->
                _ui.update { it.copy(localNodePeerCount = count) }
            }
        }
    }

    // ─── Auth ───

    fun login(url: String, username: String, password: String) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val api = buildApi(url)
                val resp = api.login(LoginRequest(username, password))
                prefs.save(resp.access_token, url, username)
                val role = decodeJwtRole(resp.access_token)
                _ui.update {
                    it.copy(
                        loading = false, screen = Screen.Dashboard,
                        token = resp.access_token, serverUrl = url, username = username,
                        isSuperAdmin = role == "superadmin"
                    )
                }
                connectH3363tWs(url, resp.access_token)
                fetchAgents(url, resp.access_token)
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Ошибка подключения") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            wsManager?.disconnect()
            wsManager = null
            releaseLocks()
            localNodeManager.disconnect()
            prefs.clear()
            _ui.update { UiState() }
        }
    }

    // ─── Navigation ───

    fun navigateTo(screen: Screen) {
        _ui.update { it.copy(screen = screen) }
        when (screen) {
            is Screen.Dashboard -> refresh()
            is Screen.H3363TNode -> loadH3363tData()
            is Screen.Admin -> loadAdmins()
            is Screen.LocalNode -> {
                _ui.update { it.copy(localNodeLoading = true) }
                localNodeManager.connect()
            }
            else -> {}
        }
    }

    fun closeWeb() {
        _ui.update { it.copy(screen = Screen.Dashboard) }
    }

    fun openDetail(agent: AgentFull) {
        _ui.update { it.copy(
            selectedAgent = agent,
            detailMetrics = emptyList(),
            agentDetail = null,
            detailLoading = true,
            screen = Screen.Detail(agent)
        ) }
        loadAgentDetail(agent.agent_id)
        if (agent.online) loadDetailMetrics(agent.agent_id)
        loadH3363tData()
    }

    fun loadAgentDetail(agentId: String) {
        val s = _ui.value
        viewModelScope.launch {
            try {
                val api = buildApi(s.serverUrl)
                val response = api.getAgent(agentId)
                _ui.update { it.copy(agentDetail = response.data, detailLoading = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(detailLoading = false) }
            }
        }
    }

    fun closeDetail() {
        _ui.update { it.copy(selectedAgent = null, detailMetrics = emptyList(), agentDetail = null, screen = Screen.Dashboard) }
    }

    // ─── OpenWRT Agents ───

    fun refresh() {
        val s = _ui.value
        if (s.serverUrl.isNotBlank() && s.token.isNotBlank())
            fetchAgents(s.serverUrl, s.token)
    }

    private fun fetchAgents(url: String, token: String) {
        viewModelScope.launch {
            try {
                val api = buildApi(url)
                val bearer = "Bearer $token"

                try { api.health(); _ui.update { it.copy(serverHealthOk = true) } }
                catch (_: Exception) { _ui.update { it.copy(serverHealthOk = false) } }

                val owmList = api.agents(bearer).data ?: emptyList()
                val localMap = agentLocalPrefs.loadAll(owmList.map { it.agent_id })

                // Запрашиваем полные данные для каждого агента через /api/v1/agents/{id}
                val full = owmList.map { a ->
                    val settings = localMap[a.agent_id] ?: AgentLocalSettings()
                    
                    // Пытаемся получить полные данные агента с метриками
                    val detailData = try {
                        val resp = api.getAgent(a.agent_id)
                        android.util.Log.d("H3363T-Agent", "Response for ${a.agent_id}: ${resp.data?.metrics}")
                        resp.data
                    } catch (e: Exception) {
                        android.util.Log.e("H3363T-Agent", "Error fetching ${a.agent_id}: ${e.message}")
                        null
                    }
                    
                    // IP: приоритет — из ответа сервера (local_ip), затем fallback для Vontar X3
                    val serverIp = detailData?.local_ip
                    val fallbackIp = if (a.agent_id.contains("x3", ignoreCase = true) || a.agent_id.contains("vontar", ignoreCase = true)) {
                        "95.174.102.25" // Fallback только для Vontar X3
                    } else {
                        null
                    }
                    val effectiveIp = serverIp?.ifBlank { null } ?: settings.ip.ifBlank { null } ?: fallbackIp
                    
                    // displayName из настроек имеет приоритет
                    val displayName = settings.displayName.ifBlank { detailData?.display_name ?: a.agent_id }
                    
                    // Метрики из detailData
                    val metric = if (a.online && detailData?.metrics != null) {
                        val m = detailData.metrics
                        android.util.Log.d("H3363T-Metrics", "Raw metrics for ${a.agent_id}: temp=${m.temperature}, wifi=${m.wifi_clients}, cpu=${m.cpu_load}, ram=${m.ram_usage}/${m.ram_total}")
                        val ramTotalBytes = (m.ram_total ?: 0L) * 1024L * 1024L
                        val ramUsedBytes = (m.ram_usage ?: 0L) * 1024L * 1024L
                        val ramFreeBytes = ramTotalBytes - ramUsedBytes
                        Metric(
                            timestamp = m.timestamp ?: (a.last_seen_secs ?: 0L),
                            uptime = (m.uptime ?: 0L).toDouble(),
                            load1 = m.cpu_load ?: 0.0,
                            load5 = m.cpu_load ?: 0.0,
                            load15 = m.cpu_load ?: 0.0,
                            mem_free = ramFreeBytes,
                            mem_total = ramTotalBytes,
                            temperature = m.temperature?.toFloat(),
                            wifi_clients = m.wifi_clients,
                            wan_rx = m.wan_rx_bytes,
                            wan_tx = m.wan_tx_bytes
                        )
                    } else null
                    
                    // Порог online: 4 часа (14400 секунд)
                    val diff = a.last_seen_secs?.let { 
                        if (it > 1_000_000_000L) (System.currentTimeMillis() / 1000 - it) else it 
                    } ?: Long.MAX_VALUE
                    val isOnline = diff < 14400
                    android.util.Log.d("H3363T-Status", "agent=${a.agent_id} diff=${diff}s threshold=14400 online=$isOnline")

                    AgentFull(
                        agent_id = a.agent_id,
                        online = isOnline,
                        last_seen_secs = a.last_seen_secs,
                        display_name = displayName,
                        address = settings.physicalAddress.ifBlank { detailData?.address },
                        local_ip = effectiveIp,
                        internal_ip = detailData?.internal_ip,
                        luci_url = if (effectiveIp != null) "http://${effectiveIp}:${settings.luciPort}" else (detailData?.luci_url),
                        lat = settings.lat ?: detailData?.lat,
                        lng = settings.lon ?: detailData?.lng,
                        metric = metric
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

    private fun loadDetailMetrics(agentId: String) {
        viewModelScope.launch {
            val s = _ui.value
            try {
                val bearer = if (s.token.isNotBlank()) "Bearer ${s.token}" else ""
                val metrics = buildApi(s.serverUrl).metrics(agentId, bearer, 100)
                    .data ?: emptyList()
                _ui.update { it.copy(detailMetrics = metrics) }
            } catch (_: Exception) {}
        }
    }

    fun openAgentWeb(agent: AgentFull, type: String) {
        when (type) {
            "__terminal__" -> openNativeSsh(agent)
            "__luci__" -> _ui.update { it.copy(screen = Screen.LuciView(agent)) }
            else -> _ui.update { it.copy(screen = Screen.Web(type)) }
        }
    }

    // ─── Settings ───

    fun updateServerUrl(newUrl: String) {
        val s = _ui.value
        if (newUrl.isBlank()) return
        viewModelScope.launch {
            prefs.save(s.token, newUrl, s.username)
            _ui.update { it.copy(serverUrl = newUrl) }
            if (s.token.isNotBlank()) {
                connectH3363tWs(newUrl, s.token)
                fetchAgents(newUrl, s.token)
            }
        }
    }

    // ─── Local node (ws://127.0.0.1:9001) ───

    fun refreshLocalNode() {
        localNodeManager.refresh()
    }

    // ─── H3363T via OWM Server ───

    private fun connectH3363tWs(url: String, token: String) {
        wsCollectorJob?.cancel()
        wsManager?.disconnect()
        acquireLocks()
        wsManager = H3363tWebSocketManager().also { manager ->
            manager.connect(url, token)
            wsCollectorJob = viewModelScope.launch {
                launch {
                    manager.connectionState.collect { connected ->
                        _ui.update { it.copy(h3363tConnected = connected) }
                        if (connected) {
                            android.util.Log.d("H3363T-VM", "WS connected, locks acquired")
                        } else {
                            android.util.Log.d("H3363T-VM", "WS disconnected, reconnect handled by manager")
                        }
                    }
                }
                launch {
                    manager.events.collect { event ->
                        val currentEvents = _ui.value.h3363tEvents.toMutableList()
                        currentEvents.add(0, event)
                        if (currentEvents.size > 50) currentEvents.removeAt(currentEvents.size - 1)
                        _ui.update { it.copy(h3363tEvents = currentEvents) }
                    }
                }
            }
        }
    }

    fun loadH3363tData() {
        val s = _ui.value
        if (s.serverUrl.isBlank() || s.token.isBlank()) return

        viewModelScope.launch {
            _ui.update { it.copy(h3363tLoading = true, h3363tError = null) }
            try {
                val api = buildApi(s.serverUrl)
                val bearer = "Bearer ${s.token}"

                val statusDeferred = async {
                    try { api.h3363tStatus(bearer).data ?: emptyList() }
                    catch (_: Exception) { emptyList<H3363tNodeStatus>() }
                }
                val eventsDeferred = async {
                    try { api.h3363tEvents(bearer).data ?: emptyList() }
                    catch (_: Exception) { emptyList<H3363tEvent>() }
                }

                val nodes = statusDeferred.await()
                val events = eventsDeferred.await()
                _ui.update { it.copy(h3363tNodes = nodes, h3363tEvents = events, h3363tLoading = false) }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(h3363tLoading = false, h3363tError = e.message ?: "Ошибка загрузки H3363T")
                }
            }
        }
    }

    fun sendH3363tCommand(command: String, port: Int? = null, rule: String? = null) {
        val s = _ui.value
        if (s.serverUrl.isBlank() || s.token.isBlank()) return

        viewModelScope.launch {
            _ui.update { it.copy(h3363tCommandResult = null) }
            try {
                val api = buildApi(s.serverUrl)
                val bearer = "Bearer ${s.token}"
                val req = H3363tCommandRequest(command = command, port = port, rule = rule)
                val resp = api.h3363tCommand(bearer, req)
                _ui.update {
                    it.copy(
                        h3363tCommandResult = if (resp.success)
                            "✓ Команда '$command' выполнена"
                        else
                            "✗ Ошибка: ${resp.message}"
                    )
                }
                loadH3363tData()
            } catch (e: Exception) {
                _ui.update { it.copy(h3363tCommandResult = "✗ Ошибка выполнения: ${e.message}") }
            }
        }
    }

    fun clearCommandResult() {
        _ui.update { it.copy(h3363tCommandResult = null) }
    }

    fun loadAdmins() {
        val s = _ui.value
        if (!s.isSuperAdmin || s.serverUrl.isBlank() || s.token.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(adminLoading = true, adminError = null) }
            try {
                val result = buildApi(s.serverUrl).listAdmins("Bearer ${s.token}")
                _ui.update { it.copy(adminList = result.data ?: emptyList(), adminLoading = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(adminLoading = false, adminError = e.message) }
            }
        }
    }

    fun createAdmin(username: String, password: String) {
        val s = _ui.value
        viewModelScope.launch {
            try {
                buildApi(s.serverUrl).createAdmin(
                    "Bearer ${s.token}",
                    CreateAdminRequest(username, password)
                )
                loadAdmins()
            } catch (e: Exception) {
                _ui.update { it.copy(adminError = e.message) }
            }
        }
    }

    fun deleteAdmin(username: String) {
        val s = _ui.value
        viewModelScope.launch {
            try {
                buildApi(s.serverUrl).deleteAdmin("Bearer ${s.token}", username)
                loadAdmins()
            } catch (e: Exception) {
                _ui.update { it.copy(adminError = e.message) }
            }
        }
    }

    fun setPickedLocation(lat: Double, lng: Double) {
        _ui.update { it.copy(
            pickedLat = lat, pickedLng = lng,
            addRouterLat = if (it.mapPickMode) lat else it.addRouterLat,
            addRouterLng = if (it.mapPickMode) lng else it.addRouterLng,
            mapPickMode = false,
            screen = if (it.mapPickMode && it.addRouterOpen) Screen.AddRouter else it.screen
        ) }
    }

    // ─── AddRouter ───────────────────────────────────────────────────────────────

    fun openAddRouter() {
        _ui.update { it.copy(
            addRouterOpen = true, screen = Screen.AddRouter,
            addRouterId = "", addRouterName = "", addRouterDesc = "",
            addRouterIp = "", addRouterSshPass = "", addRouterAddress = "",
            addRouterLat = null, addRouterLng = null, installAgentStatus = null
        ) }
    }

    fun closeAddRouter() {
        _ui.update { it.copy(addRouterOpen = false, screen = Screen.Dashboard, installAgentStatus = null) }
    }

    fun setAddRouterField(
        id: String? = null, name: String? = null, desc: String? = null,
        ip: String? = null, sshPass: String? = null, address: String? = null
    ) {
        _ui.update { s -> s.copy(
            addRouterId      = if (id      != null) id      else s.addRouterId,
            addRouterName    = if (name    != null) name    else s.addRouterName,
            addRouterDesc    = if (desc    != null) desc    else s.addRouterDesc,
            addRouterIp      = if (ip      != null) ip      else s.addRouterIp,
            addRouterSshPass = if (sshPass != null) sshPass else s.addRouterSshPass,
            addRouterAddress = if (address != null) address else s.addRouterAddress
        ) }
    }

    fun enterMapPickMode() {
        _ui.update { it.copy(mapPickMode = true, screen = Screen.Map) }
    }

    fun cancelMapPick() {
        _ui.update { it.copy(
            mapPickMode = false,
            screen = if (it.addRouterOpen) Screen.AddRouter else Screen.Map
        ) }
    }

    fun installAgent() {
        val s = _ui.value
        val cmd = "curl -sL ${s.serverUrl}/api/v1/agents/install.sh | AGENT_ID=${s.addRouterId} sh"
        _ui.update { it.copy(installAgentStatus = "CMD:$cmd") }
    }

    fun submitAddRouter() {
        val s = _ui.value
        if (s.serverUrl.isBlank() || s.token.isBlank()) {
            _ui.update { it.copy(installAgentStatus = "✗ Нет подключения к серверу") }
            return
        }
        if (s.addRouterId.isBlank() || s.addRouterIp.isBlank()) {
            _ui.update { it.copy(installAgentStatus = "✗ Введите ID и IP роутера") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(installAgentStatus = "⏳ Подключаемся...") }
            try {
                val req = CreateAgentRequest(
                    agent_id     = s.addRouterId,
                    display_name = s.addRouterName.ifBlank { null },
                    local_ip     = s.addRouterIp,
                    lat          = s.addRouterLat,
                    lng          = s.addRouterLng,
                    description  = s.addRouterDesc.ifBlank { null },
                    address      = s.addRouterAddress.ifBlank { null },
                    ssh_host     = s.addRouterIp,
                    ssh_port     = 22,
                    ssh_user     = "root",
                    ssh_password = s.addRouterSshPass.ifBlank { null }
                )
                buildApi(s.serverUrl).createAgent("Bearer ${s.token}", req)
                _ui.update { it.copy(installAgentStatus = "✓ Агент ${s.addRouterId} зарегистрирован") }
                refresh()
            } catch (e: Exception) {
                _ui.update { it.copy(installAgentStatus = "✗ ${e.message}") }
            }
        }
    }

    fun loadMetrics(agentId: String, hours: Int = 1) {
        val s = _ui.value
        if (s.serverUrl.isBlank() || agentId.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(metricsLoading = true, selectedMetricsAgentId = agentId) }
            try {
                val limit = hours * 60
                val bearer = if (s.token.isNotBlank()) "Bearer ${s.token}" else ""
                val metrics = buildApi(s.serverUrl)
                    .metrics(agentId, bearer, limit)
                    .data ?: emptyList()
                _ui.update { it.copy(detailMetrics = metrics, metricsLoading = false) }
            } catch (_: Exception) {
                _ui.update { it.copy(metricsLoading = false) }
            }
        }
    }

    fun createAgentOnServer(req: CreateAgentRequest) {
        val s = _ui.value
        viewModelScope.launch {
            try {
                buildApi(s.serverUrl).createAgent("Bearer ${s.token}", req)
                fetchAgents(s.serverUrl, s.token)
            } catch (_: Exception) {}
        }
    }

    fun deleteAgentOnServer(agentId: String) {
        val s = _ui.value
        if (s.token.isBlank()) {
            _ui.update { it.copy(agentDeleteError = "Не авторизован") }
            return
        }
        viewModelScope.launch {
            try {
                val resp = buildApi(s.serverUrl).deleteAgent("Bearer ${s.token}", agentId)
                if (resp.success) {
                    fetchAgents(s.serverUrl, s.token)
                    val cur = _ui.value.screen
                    if (cur is Screen.Detail && cur.agent.agent_id == agentId)
                        _ui.update { it.copy(screen = Screen.Dashboard, selectedAgent = null, agentDetail = null) }
                } else {
                    _ui.update { it.copy(agentDeleteError = resp.message ?: "Ошибка удаления") }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(agentDeleteError = e.message ?: "Ошибка сети") }
            }
        }
    }

    fun clearAgentDeleteError() {
        _ui.update { it.copy(agentDeleteError = null) }
    }

    fun renameAgent(agentId: String, newName: String) {
        val s = _ui.value
        viewModelScope.launch {
            try {
                buildApi(s.serverUrl).createAgent(
                    "Bearer ${s.token}",
                    CreateAgentRequest(agent_id = agentId, display_name = newName)
                )
                fetchAgents(s.serverUrl, s.token)
            } catch (_: Exception) {}
        }
    }

    fun setSidebarOpen(open: Boolean) {
        _ui.update { it.copy(sidebarOpen = open) }
    }

    fun setSearchQuery(q: String) {
        _ui.update { it.copy(searchQuery = q) }
    }

    fun openSshTerminal(agent: AgentFull) {
        _ui.update { it.copy(screen = Screen.SshTerminal(agent)) }
    }

    fun openLuci(agent: AgentFull) {
        val s = _ui.value
        val localSettings = s.agentLocalMap[agent.agent_id] ?: AgentLocalSettings()

        val host = agent.local_ip?.takeIf { it.isNotBlank() }
            ?: localSettings.ip.takeIf { it.isNotBlank() }
            ?: run {
                android.util.Log.e("H3363T-LuCI", "No IP for ${agent.agent_id}")
                _ui.update { it.copy(error = "IP не получен. Укажите в Настройках агента.") }
                return
            }

        val url = "http://$host:80"
        android.util.Log.d("H3363T-LuCI", "Opening $url")

        _ui.update { it.copy(screen = Screen.LuciView(agent)) }
    }

    fun openMetrics(agentId: String) {
        _ui.update { it.copy(selectedMetricsAgentId = agentId) }
        loadMetrics(agentId, 1)
        navigateTo(Screen.Metrics)
    }

    // ─── Native SSH Relay ───

    private var sshRelay: SshRelayManager? = null

    fun openNativeSsh(agent: AgentFull) {
        sshRelay?.disconnect()

        val s = _ui.value
        val token = s.token ?: run {
            _ui.update { it.copy(error = "Необходима авторизация") }
            return
        }

        // Всегда через relay — работает из любой точки мира
        val wsUrl = "${s.serverUrl.replace("http", "ws")}/api/v1/ssh/ws/${agent.agent_id}?token=$token"
        android.util.Log.d("H3363T-SSH", "Opening SSH relay to ${agent.agent_id} via $wsUrl")

        _ui.update { it.copy(
            screen = Screen.NativeSsh(agent),
            sshOutput = "Connecting to ${agent.agent_id} via H3363T relay...\n",
            sshConnecting = true
        )}

        sshRelay = SshRelayManager().also { mgr ->
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                mgr.connect(
                    url = wsUrl,
                    onOutput = { text ->
                        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main) {
                            _ui.update { state ->
                                // First strip all ANSI escape sequences
                                var cleaned = text.replace(Regex("\u001B\\[[0-9;]*[A-Za-z]"), "")
                                // Then process BS and DEL
                                var out = state.sshOutput
                                for (ch in cleaned) {
                                    when (ch) {
                                        '\b', '\u007F' -> if (out.isNotEmpty()) out = out.dropLast(1)
                                        else -> out += ch
                                    }
                                }
                                state.copy(sshOutput = out)
                            }
                        }
                    },
                    onConnected = {
                        Log.d("H3363T-SSH", "SSH relay connected")
                        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main) {
                            _ui.update { it.copy(sshOutput = it.sshOutput + "Connected.\r\n", sshConnecting = false, sshConnected = true) }
                        }
                    },
                    onError = { err ->
                        Log.e("H3363T-SSH", "SSH relay error: $err")
                        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main) {
                            _ui.update { it.copy(sshOutput = it.sshOutput + "\r\n[ERROR] $err\r\n", sshConnecting = false) }
                        }
                    },
                    onDisconnected = {
                        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main) {
                            _ui.update { it.copy(sshOutput = it.sshOutput + "\r\n[Disconnected]\r\n", sshConnected = false, sshConnecting = false) }
                        }
                    }
                )
            }
        }
    }

    fun sendSshChar(ch: Char) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            when (ch) {
                '\b', '\u007F' -> sshRelay?.send("__BS__") // backspace marker
                else -> sshRelay?.send(ch.toString())
            }
        }
    }

    fun sendSshCommand(cmd: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            sshRelay?.send(cmd)
        }
        _ui.update { it.copy(sshInputBuffer = "") }
    }

    fun updateSshInput(text: String) {
        _ui.update { it.copy(sshInputBuffer = text) }
    }

    fun closeSsh() {
        sshRelay?.disconnect()
        sshRelay = null
        _ui.update { it.copy(sshOutput = "", sshConnected = false, sshConnecting = false, screen = Screen.Dashboard) }
    }

    // ─── Channel / DM Creation ───

    fun openCreateChannelSheet() {
        _ui.update { it.copy(showCreateChannelSheet = true, createChannelError = null) }
    }

    fun closeCreateChannelSheet() {
        _ui.update { it.copy(showCreateChannelSheet = false, createChannelError = null) }
    }

    fun setCreateChannelField(name: String? = null, id: String? = null, pubkey: String? = null) {
        _ui.update { s -> s.copy(
            createChannelName = name ?: s.createChannelName,
            createChannelId = id ?: s.createChannelId,
            createDmPubkey = pubkey ?: s.createDmPubkey
        ) }
    }

    fun createRoom(roomName: String) {
        val s = _ui.value
        if (s.serverUrl.isBlank() || s.token.isBlank()) {
            _ui.update { it.copy(createChannelError = "Нет подключения к серверу") }
            return
        }
        viewModelScope.launch {
            try {
                val api = buildApi(s.serverUrl)
                val bearer = "Bearer ${s.token}"
                // Предпроверка trust >= 1 через API (если есть эндпоинт)
                // Пока отправляем команду напрямую
                val req = H3363tCommandRequest(command = "create_room", rule = roomName)
                val resp = api.h3363tCommand(bearer, req)
                if (resp.success) {
                    _ui.update { it.copy(showCreateChannelSheet = false, createChannelError = null) }
                    loadH3363tData()
                } else {
                    _ui.update { it.copy(createChannelError = "Ошибка: ${resp.message}") }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(createChannelError = "Ошибка сети: ${e.message}") }
            }
        }
    }

    fun createDm(pubkey: String) {
        val s = _ui.value
        if (s.serverUrl.isBlank() || s.token.isBlank()) {
            _ui.update { it.copy(createChannelError = "Нет подключения к серверу") }
            return
        }
        // Валидация pubkey: hex 64 символа
        if (!pubkey.matches(Regex("^[0-9a-fA-F]{64}$"))) {
            _ui.update { it.copy(createChannelError = "Неверный формат pubkey (должен быть 64 hex символа)") }
            return
        }
        viewModelScope.launch {
            try {
                val api = buildApi(s.serverUrl)
                val bearer = "Bearer ${s.token}"
                val req = H3363tCommandRequest(command = "create_dm", rule = pubkey)
                val resp = api.h3363tCommand(bearer, req)
                if (resp.success) {
                    _ui.update { it.copy(showCreateChannelSheet = false, createChannelError = null) }
                    loadH3363tData()
                } else {
                    _ui.update { it.copy(createChannelError = "Ошибка: ${resp.message}") }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(createChannelError = "Ошибка сети: ${e.message}") }
            }
        }
    }

    // ─── Local Settings CRUD ───

    fun openAgentSettings(agentId: String) {
        _ui.update { it.copy(agentSettingsId = agentId, screen = Screen.AgentSettings(agentId)) }
    }

    fun closeAgentSettings() {
        _ui.update { it.copy(agentSettingsId = null, screen = Screen.Dashboard) }
    }

    fun updateAgentDisplayName(agentId: String, displayName: String?) {
        val s = _ui.value
        if (s.token == null) return
        viewModelScope.launch {
            try {
                buildApi(s.serverUrl).updateAgent("Bearer ${s.token}", agentId, UpdateAgentRequest(displayName = displayName))
                fetchAgents(s.serverUrl, s.token)
            } catch (e: Exception) {
                android.util.Log.e("H3363T-VM", "updateAgent failed", e)
                _ui.update { it.copy(error = "Ошибка обновления имени: ${e.message}") }
            }
        }
    }

    fun saveAgentLocalSettings(agentId: String, settings: AgentLocalSettings) {
        val s = _ui.value
        viewModelScope.launch {
            agentLocalPrefs.save(agentId, settings)
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

    // ─── mDNS / Nearby Functions ────────────────────────────────────────────────

    fun startNearbyScan() {
        if (_ui.value.nearbyScanning) return
        _ui.update { it.copy(nearbyScanning = true, nearbyNodes = emptyList()) }

        val serviceType = "_h3363t._tcp"
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("H3363T-mDNS", "Discovery started: $regType")
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d("H3363T-mDNS", "Service found: ${serviceInfo.serviceName}")
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e("H3363T-mDNS", "Resolve failed: $errorCode")
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host?.hostAddress ?: return
                        val port = serviceInfo.port
                        val name = serviceInfo.serviceName
                        Log.d("H3363T-mDNS", "Resolved: $name at $host:$port")
                        _ui.update { currentState ->
                            val existing = currentState.nearbyNodes.find { it.ip == host && it.port == port }
                            if (existing == null) {
                                currentState.copy(nearbyNodes = currentState.nearbyNodes + NearbyNode(name, host, port))
                            } else currentState
                        }
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d("H3363T-mDNS", "Service lost: ${serviceInfo.serviceName}")
                _ui.update { currentState ->
                    currentState.copy(
                        nearbyNodes = currentState.nearbyNodes.filterNot {
                            it.name == serviceInfo.serviceName
                        }
                    )
                }
            }
            override fun onDiscoveryStopped(regType: String) {
                Log.d("H3363T-mDNS", "Discovery stopped: $regType")
                _ui.update { it.copy(nearbyScanning = false) }
            }
            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {
                Log.e("H3363T-mDNS", "Start discovery failed: $errorCode")
                _ui.update { it.copy(nearbyScanning = false) }
            }
            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {
                Log.e("H3363T-mDNS", "Stop discovery failed: $errorCode")
                _ui.update { it.copy(nearbyScanning = false) }
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("H3363T-mDNS", "Discover services error: ${e.message}")
            _ui.update { it.copy(nearbyScanning = false) }
        }
    }

    fun stopNearbyScan() {
        try {
            discoveryListener?.let {
                nsdManager.stopServiceDiscovery(it)
            }
        } catch (e: Exception) {
            Log.e("H3363T-mDNS", "Stop scan error: ${e.message}")
        }
        _ui.update { it.copy(nearbyScanning = false) }
    }

    fun connectToNearbyNode(node: NearbyNode) {
        val wsUrl = "ws://${node.ip}:${node.port}"
        Log.d("H3363T-mDNS", "Connecting to nearby node: $wsUrl")
        localNodeManager.connectTo(wsUrl)
        navigateTo(Screen.LocalNode)
    }

    override fun onCleared() {
        super.onCleared()
        wsManager?.disconnect()
        releaseLocks()
        localNodeManager.disconnect()
        sshRelay?.disconnect()
        sshRelay = null
    }
}

class SshRelayManager {
    private var ws: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect(
        url: String,
        onOutput: (String) -> Unit,
        onConnected: () -> Unit,
        onError: (String) -> Unit,
        onDisconnected: () -> Unit
    ) {
        disconnect()
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("H3363T-SSH", "Relay WS opened")
                onConnected()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                onOutput(text)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("H3363T-SSH", "Relay WS failure: ${t.message}")
                onError(t.message ?: "Unknown error")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onDisconnected()
            }
        })
    }

    fun send(text: String): Boolean = ws?.send(text) ?: false

    fun sendBinary(data: ByteArray): Boolean {
        val bs = okio.ByteString.of(*data)
        return ws?.send(bs) ?: false
    }

    fun disconnect() {
        ws?.close(1000, "User closed")
        ws = null
    }

    fun isConnected(): Boolean = ws != null
}
