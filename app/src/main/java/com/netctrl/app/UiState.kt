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
    // Add Router form
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
    // H3363T
    val h3363tNodes: List<H3363tNodeStatus> = emptyList(),
    val h3363tEvents: List<H3363tEvent> = emptyList(),
    val h3363tLoading: Boolean = false,
    val h3363tError: String? = null,
    val h3363tConnected: Boolean = false,
    val h3363tCommandResult: String? = null,
    // Local node
    val localNodeConnected: Boolean = false,
    val localNodePeerCount: Int = 0,
    val localNodeLoading: Boolean = false,
    // Map sidebar (overlay toggle)
    val sidebarOpen: Boolean = false,
    // Router list search
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
