package com.netctrl.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class Screen { object Login : Screen(); object Dashboard : Screen(); data class Web(val url: String) : Screen() }

data class UiState(
    val screen: Screen = Screen.Login,
    val loading: Boolean = false,
    val error: String? = null,
    val agents: List<AgentFull> = emptyList(),
    val selectedAgent: AgentFull? = null,
    val detailMetrics: List<Metric> = emptyList(),
    val token: String = "",
    val serverUrl: String = "",
    val username: String = ""
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(prefs.token, prefs.serverUrl, prefs.username) { t, u, n ->
                Triple(t, u, n)
            }.collect { (token, url, name) ->
                if (!token.isNullOrBlank() && !url.isNullOrBlank()) {
                    _ui.update {
                        it.copy(token = token, serverUrl = url,
                            username = name ?: "", screen = Screen.Dashboard)
                    }
                }
            }
        }
    }

    fun login(url: String, username: String, password: String) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val api = buildApi(url)
                val resp = api.login(LoginRequest(username, password))
                prefs.save(resp.access_token, url, username)
                _ui.update {
                    it.copy(loading = false, screen = Screen.Dashboard,
                        token = resp.access_token, serverUrl = url, username = username)
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Ошибка подключения") }
            }
        }
    }

    fun refresh() {
        val s = _ui.value
        if (s.serverUrl.isNotBlank() && s.token.isNotBlank())
            loadAgents(s.serverUrl, s.token)
    }

    private fun loadAgents(url: String, token: String) {
        viewModelScope.launch {
            try {
                val api = buildApi(url)
                val bearer = "Bearer $token"

                // Параллельно грузим агентов и конфиги
                val agentsDeferred = async { api.agents(bearer).data ?: emptyList() }
                val configsDeferred = async { api.configs(bearer).data ?: emptyList() }
                val agents = agentsDeferred.await()
                val configs = configsDeferred.await()
                val configMap = configs.associateBy { it.agent_id }

                // Для онлайн агентов грузим последнюю метрику параллельно
                val metricsMap = agents
                    .filter { it.online }
                    .map { agent ->
                        async {
                            agent.agent_id to try {
                                api.metrics(agent.agent_id, bearer, 1).data?.firstOrNull()
                            } catch (e: Exception) { null }
                        }
                    }.awaitAll().toMap()

                val full = agents.map { a ->
                    val cfg = configMap[a.agent_id]
                    AgentFull(
                        agent_id = a.agent_id,
                        online = a.online,
                        last_seen_secs = a.last_seen_secs,
                        display_name = cfg?.display_name,
                        address = cfg?.address,
                        local_ip = cfg?.local_ip,
                        luci_url = cfg?.luci_url,
                        lat = cfg?.lat,
                        lng = cfg?.lng,
                        metric = metricsMap[a.agent_id]
                    )
                }
                _ui.update { it.copy(agents = full, error = null) }
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message) }
            }
        }
    }

    fun selectAgent(agent: AgentFull?) {
        _ui.update { it.copy(selectedAgent = agent, detailMetrics = emptyList()) }
        if (agent != null && agent.online) loadDetailMetrics(agent.agent_id)
    }

    private fun loadDetailMetrics(agentId: String) {
        viewModelScope.launch {
            val s = _ui.value
            try {
                val metrics = buildApi(s.serverUrl).metrics(agentId, "Bearer ${s.token}", 100)
                    .data ?: emptyList()
                _ui.update { it.copy(detailMetrics = metrics) }
            } catch (e: Exception) { /* ignore */ }
        }
    }

    fun openAgentWeb(agent: AgentFull, type: String) {
        val base = _ui.value.serverUrl
        val url = when (type) {
            "__terminal__" -> "$base/#terminal_${agent.agent_id}"
            "__luci__" -> "$base/luci-login/${agent.agent_id}"
            else -> type
        }
        _ui.update { it.copy(screen = Screen.Web(url)) }
    }

    fun openWeb(url: String) {
        _ui.update { it.copy(screen = Screen.Web(url)) }
    }

    fun closeWeb() {
        _ui.update { it.copy(screen = Screen.Dashboard) }
    }

    fun logout() {
        viewModelScope.launch {
            prefs.clear()
            _ui.update { UiState() }
        }
    }
}
