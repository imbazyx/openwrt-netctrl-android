package com.netctrl.app

sealed class Screen(val route: String) {
    object Dashboard  : Screen("dashboard")
    object Login      : Screen("login")
    object Map        : Screen("map")
    object Ssh        : Screen("ssh")
    object Metrics    : Screen("metrics")
    object Admin      : Screen("admin")
    object AddRouter  : Screen("add_router")
    object H3363TNode : Screen("h3363t_node")
    object LocalNode  : Screen("local_node")
    object Settings   : Screen("settings")
    data class Detail(val agent: AgentFull)      : Screen("detail/${agent.agent_id}")
    data class SshTerminal(val agent: AgentFull) : Screen("ssh_terminal/${agent.agent_id}")
    data class LuciView(val agent: AgentFull)    : Screen("luci/${agent.agent_id}")
    data class Web(val url: String)              : Screen("web")
    data class AgentSettings(val agentId: String) : Screen("agent_settings/$agentId")
    data class NativeSsh(val agent: AgentFull)    : Screen("native_ssh/${agent.agent_id}")
}
