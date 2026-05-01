package com.netctrl.app

data class UiState(
    val loading: Boolean = false,
    val error: String? = null,
    val token: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val isSuperAdmin: Boolean = false,
    val screen: Screen = Screen.Login,
    // Agents / routers
    val agents: List<AgentFull> = emptyList(),
    val selectedAgent: AgentFull? = null,
    val detailMetrics: List<Metric> = emptyList(),
    val serverHealthOk: Boolean = false,
    // Admin management
    val adminList: List<AdminInfo> = emptyList(),
    val adminLoading: Boolean = false,
    val adminError: String? = null,
    // Metrics tab
    val metricsLoading: Boolean = false,
    val selectedMetricsAgentId: String = "",
    // Map pick mode
    val pickedLat: Double? = null,
    val pickedLng: Double? = null,
    // H3363T via OWM server
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
)
