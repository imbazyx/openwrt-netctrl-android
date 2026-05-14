package com.netctrl.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    private fun loadAgentDetail(agentId: String) {
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

    private fun loadDetailMetrics(agentId: String) {
        viewModelScope.launch {
            val s = _ui.value
            try {
                val metrics = buildApi(s.serverUrl).metrics(agentId, "Bearer ${s.token}", 100)
                    .data ?: emptyList()
                _ui.update { it.copy(detailMetrics = metrics) }
            } catch (_: Exception) {}
        }
    }

    fun openAgentWeb(agent: AgentFull, type: String) {
        when (type) {
            "__terminal__" -> _ui.update { it.copy(screen = Screen.SshTerminal(agent)) }
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
        wsManager = H3363tWebSocketManager().also { manager ->
            manager.connect(url, token)
            wsCollectorJob = viewModelScope.launch {
                launch {
                    manager.connectionState.collect { connected ->
                        _ui.update { it.copy(h3363tConnected = connected) }
                        if (!connected) {
                            delay(5000)
                            val s = _ui.value
                            if (s.token.isNotBlank() && s.serverUrl.isNotBlank()) {
                                manager.connect(s.serverUrl, s.token)
                            }
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
        if (s.serverUrl.isBlank() || s.token.isBlank() || agentId.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(metricsLoading = true, selectedMetricsAgentId = agentId) }
            try {
                val limit = hours * 60
                val metrics = buildApi(s.serverUrl)
                    .metrics(agentId, "Bearer ${s.token}", limit)
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

    fun updateAgentIp(agentId: String, newIp: String) {
        val s = _ui.value
        viewModelScope.launch {
            try {
                buildApi(s.serverUrl).createAgent(
                    "Bearer ${s.token}",
                    CreateAgentRequest(agent_id = agentId, local_ip = newIp, ssh_host = newIp)
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
        _ui.update { it.copy(screen = Screen.LuciView(agent)) }
    }

    fun openMetrics(agentId: String) {
        _ui.update { it.copy(selectedMetricsAgentId = agentId) }
        loadMetrics(agentId, 1)
        navigateTo(Screen.Metrics)
    }

    // ─── Native SSH ───

    fun openNativeSsh(agent: AgentFull) {
        sshSession?.disconnect()
        val s = _ui.value
        val localSettings = s.agentLocalMap[agent.agent_id] ?: AgentLocalSettings()
        val host = localSettings.ip.ifBlank { agent.local_ip ?: "" }
        val port = localSettings.sshPort
        val login = credentialStore.getSshLogin(agent.agent_id).ifBlank { "root" }
        val pass = credentialStore.getSshPass(agent.agent_id)
        sshSession = SshSessionManager(viewModelScope).also { mgr ->
            viewModelScope.launch {
                mgr.output.collect { out -> _ui.update { it.copy(sshOutput = out) } }
            }
            viewModelScope.launch {
                mgr.connected.collect { c -> _ui.update { it.copy(sshConnected = c, sshConnecting = false) } }
            }
            _ui.update { it.copy(sshOutput = "", sshConnecting = true, screen = Screen.NativeSsh(agent)) }
            mgr.connect(host, port, login, pass)
        }
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

    override fun onCleared() {
        super.onCleared()
        wsManager?.disconnect()
        localNodeManager.disconnect()
        sshSession?.disconnect()
        sshSession = null
    }
}
