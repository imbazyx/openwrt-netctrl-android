package com.netctrl.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

// ─── VOID Color Scheme ─────────────────────────────────────────────────────
val BgDark      = Color(0xFF050410)
val CardBg      = Color(0xFF0D0B1E)
val AccentVoid  = Color(0xFF7050C8)
val AccentGreen = Color(0xFF238636)
val TextPrimary = Color(0xFFE6EDF3)
val TextSecondary = Color(0xFF8B949E)
val OnlineGreen = Color(0xFF3FB950)
val OfflineRed  = Color(0xFFF85149)
val TrustGold   = Color(0xFFE3B341)
val H3363tPurple = Color(0xFF9E6AFF)
val DividerColor = Color(0xFF1E1A35)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NetCtrlApp() }
    }
}

@Composable
fun NetCtrlApp(vm: MainViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    MaterialTheme(colorScheme = darkColorScheme(
        background = BgDark, surface = CardBg,
        primary = AccentVoid, onBackground = TextPrimary
    )) {
        when (val screen = ui.screen) {
            is Screen.Login    -> LoginScreen(ui, vm::login)
            is Screen.Detail   -> DetailScreen(
                agent = screen.agent,
                agentDetail = ui.agentDetail,
                detailLoading = ui.detailLoading,
                metrics = ui.detailMetrics,
                h3363tNodes = ui.h3363tNodes,
                onBack = { vm.closeDetail() },
                onOpenTerminal = { type -> vm.openAgentWeb(screen.agent, type) },
                onMetrics = { vm.openMetrics(screen.agent.agent_id) },
                onDelete = { vm.deleteAgentOnServer(screen.agent.agent_id) }
            )
            is Screen.SshTerminal -> SshTerminalScreen(
                agent = screen.agent,
                serverUrl = ui.serverUrl,
                token = ui.token,
                onBack = { vm.navigateTo(Screen.Dashboard) }
            )
            is Screen.LuciView -> LuciViewScreen(
                agent = screen.agent,
                onBack = { vm.navigateTo(Screen.Dashboard) }
            )
            is Screen.H3363TNode -> H3363TScreen(
                nodes = ui.h3363tNodes, events = ui.h3363tEvents,
                loading = ui.h3363tLoading, error = ui.h3363tError,
                connected = ui.h3363tConnected, commandResult = ui.h3363tCommandResult,
                onRefresh = { vm.loadH3363tData() },
                onCommand = { cmd, port, rule -> vm.sendH3363tCommand(cmd, port, rule) },
                onClearResult = { vm.clearCommandResult() },
                onBack = { vm.navigateTo(Screen.Dashboard) }
            )
            is Screen.LocalNode -> LocalNodeScreen(
                connected = ui.localNodeConnected, peerCount = ui.localNodePeerCount,
                loading = ui.localNodeLoading,
                onRefresh = { vm.refreshLocalNode() },
                onBack = { vm.navigateTo(Screen.Dashboard) }
            )
            is Screen.Settings -> SettingsScreen(
                serverUrl = ui.serverUrl, username = ui.username,
                onSave = { url -> vm.updateServerUrl(url) },
                onLogout = { vm.logout() },
                onBack = { vm.navigateTo(Screen.Dashboard) }
            )
            is Screen.Web -> MainWebScreen(serverUrl = screen.url, token = ui.token, username = ui.username, onLogout = { vm.logout() })
            is Screen.Metrics -> MetricsFullScreen(ui = ui, vm = vm, onBack = { vm.navigateTo(Screen.Dashboard) })
            else -> MainTabScaffold(ui = ui, vm = vm)
        }
        // Router card overlay (shown above any screen)
        ui.routerCardAgent?.let { agent ->
            RouterCard(agent = agent, ui = ui, vm = vm, onDismiss = { vm.closeRouterCard() })
        }
    }
}

// ─── LOGIN ───────────────────────────────────────────────────────────────────

@Composable
fun LoginScreen(ui: UiState, onLogin: (String, String, String) -> Unit) {
    var url by remember { mutableStateOf("http://95.174.102.25:9090") }
    var user by remember { mutableStateOf("admin") }
    var pass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(BgDark), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo / brand
            Text("H3363T", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = AccentVoid)
            Text("NetCtrl", color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(),
                colors = customOutlinedColors(),
                singleLine = true
            )
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                colors = customOutlinedColors(),
                singleLine = true
            )
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton({ passVisible = !passVisible }) {
                        Text(if (passVisible) "Скрыть" else "Показать",
                            color = TextSecondary, fontSize = 12.sp)
                    }
                },
                colors = customOutlinedColors(),
                singleLine = true
            )

            if (ui.error != null)
                Text(ui.error, color = OfflineRed, fontSize = 13.sp)

            Button(
                onClick = { onLogin(url, user, pass) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !ui.loading,
                colors = ButtonDefaults.buttonColors(containerColor = AccentVoid)
            ) {
                if (ui.loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                else Text("Подключиться", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RouterListTab(ui: UiState, vm: MainViewModel, pad: PaddingValues) {
    var contextMenuAgent by remember { mutableStateOf<AgentFull?>(null) }
    var renameAgent by remember { mutableStateOf<AgentFull?>(null) }
    var changeIpAgent by remember { mutableStateOf<AgentFull?>(null) }
    var reinstallAgent by remember { mutableStateOf<AgentFull?>(null) }
    var renameField by remember { mutableStateOf("") }
    var ipField by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    val filtered = remember(ui.agents, ui.searchQuery) {
        if (ui.searchQuery.isBlank()) ui.agents
        else ui.agents.filter { a ->
            (a.display_name ?: a.agent_id).contains(ui.searchQuery, ignoreCase = true)
                    || (a.local_ip ?: "").contains(ui.searchQuery)
                    || (a.address ?: "").contains(ui.searchQuery, ignoreCase = true)
        }
    }

    Box(Modifier.fillMaxSize().padding(pad)) {
        Column(Modifier.fillMaxSize()) {
            // Sub-header
            Row(
                Modifier.fillMaxWidth().background(CardBg).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "РОУТЕРЫ (${ui.agents.size})",
                    color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider(color = DividerColor)

            // Search bar (only when > 5 agents)
            if (ui.agents.size > 5) {
                OutlinedTextField(
                    value = ui.searchQuery,
                    onValueChange = { vm.setSearchQuery(it) },
                    placeholder = { Text("Поиск роутера...", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = customOutlinedColors(),
                    singleLine = true,
                    trailingIcon = {
                        if (ui.searchQuery.isNotEmpty())
                            IconButton(onClick = { vm.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                    }
                )
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { ServerStatusCard(ui) }
                if (filtered.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Router, null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (ui.agents.isEmpty()) "Нет зарегистрированных агентов" else "Ничего не найдено",
                                    color = TextSecondary, fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.agent_id }) { agent ->
                        Box(Modifier.combinedClickable(
                            onClick = { vm.openDetail(agent) },
                            onLongClick = { contextMenuAgent = agent }
                        )) {
                            AgentCard(
                                a = agent,
                                onMetrics = { vm.openMetrics(agent.agent_id) },
                                onSsh = { if (agent.online) vm.openSshTerminal(agent) },
                                onLuci = { vm.openLuci(agent) },
                                onSettings = { vm.openDetail(agent) },
                                onDelete = { vm.deleteAgentOnServer(agent.agent_id) }
                            )
                        }
                        // Context menu dropdown
                        DropdownMenu(
                            expanded = contextMenuAgent?.agent_id == agent.agent_id,
                            onDismissRequest = { contextMenuAgent = null },
                            modifier = Modifier.background(CardBg)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Переименовать", color = TextPrimary) },
                                onClick = {
                                    renameField = agent.display_name ?: agent.agent_id
                                    renameAgent = agent
                                    contextMenuAgent = null
                                },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null, tint = AccentVoid) }
                            )
                            DropdownMenuItem(
                                text = { Text("Изменить IP", color = TextPrimary) },
                                onClick = {
                                    ipField = agent.local_ip ?: ""
                                    changeIpAgent = agent
                                    contextMenuAgent = null
                                },
                                leadingIcon = { Icon(Icons.Outlined.Wifi, null, tint = AccentVoid) }
                            )
                            DropdownMenuItem(
                                text = { Text("Переустановить агент", color = TextPrimary) },
                                onClick = {
                                    reinstallAgent = agent
                                    contextMenuAgent = null
                                },
                                leadingIcon = { Icon(Icons.Outlined.Download, null, tint = AccentVoid) }
                            )
                        }
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { vm.openAddRouter() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = AccentVoid,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, "Добавить роутер")
        }
    }

    // Rename dialog
    if (renameAgent != null) {
        AlertDialog(
            onDismissRequest = { renameAgent = null },
            title = { Text("Переименовать", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameField, onValueChange = { renameField = it },
                    label = { Text("Новое имя") }, colors = customOutlinedColors(), singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.renameAgent(renameAgent!!.agent_id, renameField); renameAgent = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentVoid),
                    enabled = renameField.isNotBlank()
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton({ renameAgent = null }) { Text("Отмена", color = TextSecondary) } },
            containerColor = CardBg
        )
    }

    // Change IP dialog
    if (changeIpAgent != null) {
        AlertDialog(
            onDismissRequest = { changeIpAgent = null },
            title = { Text("Изменить IP", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = ipField, onValueChange = { ipField = it },
                    label = { Text("LAN IP роутера") }, colors = customOutlinedColors(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.updateAgentIp(changeIpAgent!!.agent_id, ipField); changeIpAgent = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentVoid),
                    enabled = ipField.isNotBlank()
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton({ changeIpAgent = null }) { Text("Отмена", color = TextSecondary) } },
            containerColor = CardBg
        )
    }

    // Reinstall agent dialog
    if (reinstallAgent != null) {
        val agent = reinstallAgent!!
        val s = ui
        val cmd = "curl -sL ${s.serverUrl}/api/v1/agents/install.sh | AGENT_ID=${agent.agent_id} sh"
        AlertDialog(
            onDismissRequest = { reinstallAgent = null },
            title = { Text("Переустановить агент", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Выполните на роутере ${agent.display_name ?: agent.agent_id}:", color = TextSecondary, fontSize = 12.sp)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(cmd, color = OnlineGreen, fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { clipboardManager.setText(AnnotatedString(cmd)); reinstallAgent = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentVoid)
                ) { Text("Копировать") }
            },
            dismissButton = { TextButton({ reinstallAgent = null }) { Text("Закрыть", color = TextSecondary) } },
            containerColor = CardBg
        )
    }
}

@Composable
fun AddRouterContent(ui: UiState, vm: MainViewModel, pad: PaddingValues) {
    var showInstallDialog by remember { mutableStateOf(false) }
    val installCmd = if (ui.installAgentStatus?.startsWith("CMD:") == true)
        ui.installAgentStatus.removePrefix("CMD:") else null
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(ui.installAgentStatus) {
        if (ui.installAgentStatus?.startsWith("CMD:") == true) showInstallDialog = true
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("+ Добавить роутер", color = AccentVoid,
                fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        item {
            OutlinedTextField(
                value = ui.addRouterId,
                onValueChange = { vm.setAddRouterField(id = it) },
                label = { Text("ID роутера / hostname") },
                placeholder = { Text("ax6000", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = customOutlinedColors(), singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = ui.addRouterName,
                onValueChange = { vm.setAddRouterField(name = it) },
                label = { Text("Название (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
                colors = customOutlinedColors(), singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = ui.addRouterDesc,
                onValueChange = { vm.setAddRouterField(desc = it) },
                label = { Text("Описание (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
                colors = customOutlinedColors(), singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = ui.addRouterIp,
                onValueChange = { vm.setAddRouterField(ip = it) },
                label = { Text("IP роутера (LAN)") },
                placeholder = { Text("192.168.1.1", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = customOutlinedColors(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        }
        item {
            OutlinedTextField(
                value = ui.addRouterSshPass,
                onValueChange = { vm.setAddRouterField(sshPass = it) },
                label = { Text("SSH пароль (root)") },
                modifier = Modifier.fillMaxWidth(),
                colors = customOutlinedColors(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ui.addRouterAddress,
                    onValueChange = { vm.setAddRouterField(address = it) },
                    label = { Text("Адрес / метка") },
                    modifier = Modifier.weight(1f),
                    colors = customOutlinedColors(), singleLine = true
                )
                OutlinedButton(
                    onClick = { vm.enterMapPickMode() },
                    modifier = Modifier.size(56.dp).align(Alignment.CenterVertically),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentVoid)
                ) { Text("📍", fontSize = 20.sp) }
            }
        }
        if (ui.addRouterLat != null && ui.addRouterLng != null) {
            item {
                Text(
                    "✓ %.4f, %.4f".format(ui.addRouterLat, ui.addRouterLng),
                    color = OnlineGreen, fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
        if (ui.installAgentStatus != null && !ui.installAgentStatus.startsWith("CMD:")) {
            item {
                Text(
                    ui.installAgentStatus,
                    color = when {
                        ui.installAgentStatus.startsWith("✓") -> OnlineGreen
                        ui.installAgentStatus.startsWith("✗") -> OfflineRed
                        else -> TextSecondary
                    },
                    fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.submitAddRouter() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentVoid),
                    enabled = ui.addRouterId.isNotBlank() && ui.addRouterIp.isNotBlank()
                            && ui.installAgentStatus != "⏳ Подключаемся..."
                ) {
                    if (ui.installAgentStatus == "⏳ Подключаемся...")
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White)
                    else
                        Text("Установить агент", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { vm.closeAddRouter() },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) { Text("Отмена") }
            }
        }
        item {
            TextButton(onClick = { vm.installAgent() }) {
                Text("Показать команду установки", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }

    if (showInstallDialog && installCmd != null) {
        AlertDialog(
            onDismissRequest = { showInstallDialog = false },
            title = { Text("Команда установки агента", color = TextPrimary) },
            text = {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        installCmd,
                        color = OnlineGreen, fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(installCmd ?: ""))
                            showInstallDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentVoid)
                    ) { Text("Копировать") }
                    TextButton(onClick = { showInstallDialog = false }) {
                        Text("Закрыть", color = TextSecondary)
                    }
                }
            },
            containerColor = CardBg
        )
    }
}

@Composable
fun ServerStatusCard(ui: UiState) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(10.dp).background(
                    if (ui.serverHealthOk) OnlineGreen else OfflineRed,
                    RoundedCornerShape(50)
                )
            )
            Column(Modifier.weight(1f)) {
                Text(
                    if (ui.serverHealthOk) "OWM Server Online" else "OWM Server Offline",
                    color = if (ui.serverHealthOk) OnlineGreen else OfflineRed,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                )
                Text(ui.serverUrl.ifBlank { "Не настроен" }, color = TextSecondary, fontSize = 11.sp)
            }
            Text("${ui.agents.size} агентов", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun AgentCard(
    a: AgentFull,
    onMetrics: () -> Unit = {},
    onSsh: () -> Unit = {},
    onLuci: () -> Unit = {},
    onSettings: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(
                    if (a.online) OnlineGreen else OfflineRed, RoundedCornerShape(50)))
                Spacer(Modifier.width(8.dp))
                Text(a.display_name ?: a.agent_id,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 16.sp,
                    modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (a.online) Color(0xFF0D2818) else Color(0xFF2D1215)
                ) {
                    Text(
                        if (a.online) "online" else "offline",
                        color = if (a.online) OnlineGreen else OfflineRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Address info
            if (a.address != null || a.local_ip != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    a.address?.let {
                        Text(it, color = TextSecondary, fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    a.local_ip?.let {
                        Text(it, color = TextSecondary, fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }

            // Metrics rows
            if (a.online && a.metric != null) {
                val m = a.metric
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MetricRow("Load", "%.2f".format(m.load1))
                    MetricRow("Mem", memPercent(m.mem_free, m.mem_total))
                    MetricRow("Uptime", formatUptime(m.uptime))
                    m.temperature?.let { MetricRow("Темп.", "%.1f°C".format(it)) }
                    m.wifi_clients?.let { MetricRow("WiFi клиенты", it.toString()) }
                    if (m.wan_rx != null || m.wan_tx != null)
                        MetricRow("WAN ↕", "${formatBytes(m.wan_rx)} / ${formatBytes(m.wan_tx)}")
                }
            } else if (!a.online && a.last_seen_secs != null) {
                Text("Был онлайн: ${formatAgo(a.last_seen_secs)} назад",
                    color = TextSecondary, fontSize = 11.sp)
            }

            HorizontalDivider(color = DividerColor)

            // Action buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AgentActionBtn(onClick = onMetrics) {
                    Icon(Icons.Outlined.ShowChart, null,
                        tint = AccentVoid, modifier = Modifier.size(16.dp))
                }
                AgentActionBtn(onClick = onSsh, enabled = a.online) {
                    Text("SSH", fontSize = 11.sp,
                        color = if (a.online) TextPrimary else TextSecondary)
                }
                AgentActionBtn(onClick = onLuci, enabled = a.online) {
                    Text("LuCI", fontSize = 11.sp,
                        color = if (a.online) TextPrimary else TextSecondary)
                }
                AgentActionBtn(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, null,
                        tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.weight(1f))
                AgentActionBtn(
                    onClick = { confirmDelete = true },
                    borderColor = OfflineRed.copy(alpha = 0.6f)
                ) {
                    Icon(Icons.Default.Close, null,
                        tint = OfflineRed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить агент?", color = TextPrimary) },
            text = { Text(a.display_name ?: a.agent_id, color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = OfflineRed)
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton({ confirmDelete = false }) { Text("Отмена", color = TextSecondary) }
            },
            containerColor = CardBg
        )
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Text(value, color = TextPrimary, fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

@Composable
fun AgentActionBtn(
    onClick: () -> Unit,
    enabled: Boolean = true,
    borderColor: Color = Color(0xFF2A2250),
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF16133A),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.height(30.dp)
    ) {
        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

// ─── MAIN TAB SCAFFOLD ───────────────────────────────────────────────────────

@Composable
fun RowScope.NavTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                color = if (selected) AccentVoid else TextSecondary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            if (selected) {
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(AccentVoid, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabScaffold(ui: UiState, vm: MainViewModel) {
    val screen = ui.screen
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ui.agentDeleteError) {
        if (ui.agentDeleteError != null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Ошибка: ${ui.agentDeleteError}",
                    duration = SnackbarDuration.Short
                )
            }
            vm.clearAgentDeleteError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("H3363T", fontWeight = FontWeight.Bold, color = AccentVoid, fontSize = 17.sp)
                        Text("NetCtrl", color = TextSecondary, fontSize = 14.sp)
                        if (ui.serverHealthOk) {
                            Box(Modifier.size(7.dp).background(OnlineGreen, RoundedCornerShape(50)))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg),
                actions = {
                    IconButton({ vm.refresh() }) { Icon(Icons.Default.Refresh, null, tint = TextSecondary) }
                    OutlinedButton(
                        onClick = { vm.logout() },
                        modifier = Modifier.padding(end = 8.dp).height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) { Text("Выйти", fontSize = 12.sp) }
                }
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = DividerColor)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(CardBg)
                        .height(52.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dashActive = screen is Screen.Dashboard || screen is Screen.AddRouter
                    NavTab("Роутеры", dashActive) { vm.navigateTo(Screen.Dashboard) }
                    NavTab("Карта", screen is Screen.Map) { vm.navigateTo(Screen.Map) }
                    if (ui.isSuperAdmin) {
                        NavTab("Админ", screen is Screen.Admin) { vm.navigateTo(Screen.Admin) }
                    }
                }
            }
        },
        containerColor = BgDark
    ) { pad ->
        when (screen) {
            is Screen.Map       -> MapTabContent(ui, vm, pad)
            is Screen.Admin     -> AdminTabContent(ui, vm, pad)
            is Screen.AddRouter -> AddRouterContent(ui, vm, pad)
            else                -> RouterListTab(ui, vm, pad)
        }
    }
}

// ─── MAP TAB ──────────────────────────────────────────────────────────────────

class MapBridge(private val vm: MainViewModel) {
    @android.webkit.JavascriptInterface
    fun getAgentsJson(): String {
        val agents = vm.ui.value.agents
        return try {
            org.json.JSONArray(agents.map { a ->
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
                        val ramTotalMb = if (m.mem_total != null && m.mem_total > 0) m.mem_total / 1024L / 1024L else 0L
                        val ramUsedMb = if (m.mem_total != null && m.mem_total > 0) (m.mem_total - (m.mem_free ?: 0L)) / 1024L / 1024L else 0L
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

    @android.webkit.JavascriptInterface
    fun log(msg: String) {
        android.util.Log.d("NetCtrl-MapJS", msg)
    }

    @android.webkit.JavascriptInterface
    fun onAgentClick(agentId: String) {
        val agent = vm.ui.value.agents.find { it.agent_id == agentId } ?: return
        vm.openDetail(agent)
    }

    @android.webkit.JavascriptInterface
    fun openLuci(agentId: String) {
        val agent = vm.ui.value.agents.find { it.agent_id == agentId } ?: return
        vm.openLuci(agent)
    }

    @android.webkit.JavascriptInterface
    fun openSsh(agentId: String) {
        val agent = vm.ui.value.agents.find { it.agent_id == agentId } ?: return
        if (agent.online) vm.openSshTerminal(agent)
    }

    @android.webkit.JavascriptInterface
    fun openMetrics(agentId: String) {
        vm.openMetrics(agentId)
    }

    @android.webkit.JavascriptInterface
    fun deleteAgent(agentId: String) {
        vm.deleteAgentOnServer(agentId)
    }

    @android.webkit.JavascriptInterface
    fun onLocationPicked(lat: Double, lng: Double) {
        vm.setPickedLocation(lat, lng)
    }

    @android.webkit.JavascriptInterface
    fun onAddressPicked(address: String) {
        if (address.isNotBlank()) vm.setAddRouterField(address = address)
    }
}

@Composable
fun MapTabContent(ui: UiState, vm: MainViewModel, pad: PaddingValues) {
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    val agentsSnapshot = ui.agents
    val pickMode = ui.mapPickMode
    BackHandler(enabled = pickMode) { vm.cancelMapPick() }

    LaunchedEffect(agentsSnapshot) {
        // Немедленное обновление при изменении списка агентов
        webViewRef?.evaluateJavascript("try { window.refreshAgents(); } catch(e) { console.log('err: ' + e.message); }", null)
    }
    LaunchedEffect(pickMode) {
        if (pickMode) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ webViewRef?.evaluateJavascript("try { enterPickMode(); } catch(e) { console.log('err: ' + e.message); }", null) }, 500)
        else android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ webViewRef?.evaluateJavascript("try { exitPickMode(); } catch(e) { console.log('err: ' + e.message); }", null) }, 500)
    }

    Box(Modifier.fillMaxSize().padding(pad)) {
        // ── Full-screen map WebView ──────────────────────────────────────────
        DisposableEffect(Unit) {
            onDispose { vm.mapWebViewCenterCallback = null }
        }
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

        // ── Pick mode top banner ─────────────────────────────────────────────
        if (pickMode) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .background(Color(0xFF2A1020).copy(alpha = 0.95f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📍 Тапните на карте для выбора места", color = AccentVoid, fontSize = 13.sp)
                    IconButton(
                        onClick = { vm.cancelMapPick() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}


// ─── SSH TAB ──────────────────────────────────────────────────────────────────

@Composable
fun SshTabContent(ui: UiState, vm: MainViewModel, pad: PaddingValues) {
    var selectedAgent by remember { mutableStateOf<AgentFull?>(null) }

    if (selectedAgent != null) {
        SshTerminalView(
            agent = selectedAgent!!,
            serverUrl = ui.serverUrl,
            token = ui.token,
            onClose = { selectedAgent = null }
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Выберите роутер для SSH", color = TextPrimary,
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        if (ui.agents.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Нет доступных агентов", color = TextSecondary)
                }
            }
        } else {
            items(ui.agents) { agent ->
                AgentCard(
                    a = agent,
                    onSsh = { if (agent.online) selectedAgent = agent },
                    onMetrics = { vm.loadMetrics(agent.agent_id, 1); vm.navigateTo(Screen.Metrics) },
                    onLuci = { vm.navigateTo(Screen.LuciView(agent)) },
                    onSettings = { vm.openDetail(agent) },
                    onDelete = { vm.deleteAgentOnServer(agent.agent_id) }
                )
            }
        }
    }
}

@Composable
fun SshTerminalView(
    agent: AgentFull,
    serverUrl: String,
    token: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wsBase = serverUrl.replace(Regex("^http://"), "ws://").replace(Regex("^https://"), "wss://")
    val url = "file:///android_asset/terminal.html" +
        "?server=${android.net.Uri.encode(wsBase)}" +
        "&agent_id=${android.net.Uri.encode(agent.agent_id)}" +
        "&token=${android.net.Uri.encode(token)}"

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    loadUrl(url)
                }
            }
        )
        Box(
            Modifier.align(Alignment.TopStart).padding(8.dp)
                .background(CardBg.copy(alpha = 0.85f), RoundedCornerShape(50))
        ) {
            IconButton(onClose) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshTerminalScreen(agent: AgentFull, serverUrl: String, token: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSH — ${agent.display_name ?: agent.agent_id}", color = TextPrimary) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextSecondary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        },
        containerColor = BgDark
    ) { pad ->
        SshTerminalView(agent = agent, serverUrl = serverUrl, token = token,
            onClose = onBack, modifier = Modifier.padding(pad))
    }
}

// ─── LUCI VIEW ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuciViewScreen(agent: AgentFull, onBack: () -> Unit) {
    val luciUrl = agent.luci_url ?: "http://${agent.local_ip ?: "192.168.1.1"}"
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
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    loadUrl(luciUrl)
                }
            }
        )
    }
}

// ─── METRICS FULL SCREEN (standalone, navigated from Detail) ─────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsFullScreen(ui: UiState, vm: MainViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val agentName = ui.agents.find { it.agent_id == ui.selectedMetricsAgentId }
                        ?.let { it.display_name ?: it.agent_id } ?: "Метрики"
                    Text(agentName, fontWeight = FontWeight.Bold, color = TextPrimary)
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextSecondary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        },
        containerColor = BgDark
    ) { pad ->
        MetricsTabContent(ui, vm, pad)
    }
}

// ─── METRICS TAB ──────────────────────────────────────────────────────────────

@Composable
fun MetricsTabContent(ui: UiState, vm: MainViewModel, pad: PaddingValues) {
    var selectedAgentId by remember(ui.selectedMetricsAgentId) { mutableStateOf(ui.selectedMetricsAgentId) }
    var timeRange by remember { mutableStateOf(1) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(ui.agents) {
        if (selectedAgentId.isEmpty() && ui.agents.isNotEmpty()) {
            selectedAgentId = ui.agents.first().agent_id
            vm.loadMetrics(selectedAgentId, timeRange)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box {
                OutlinedTextField(
                    value = ui.agents.find { it.agent_id == selectedAgentId }
                        ?.let { it.display_name ?: it.agent_id } ?: "Выберите роутер",
                    onValueChange = {},
                    label = { Text("Роутер") },
                    modifier = Modifier.fillMaxWidth().clickable { dropdownExpanded = true },
                    enabled = false,
                    colors = customOutlinedColors(),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary) }
                )
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.background(CardBg)
                ) {
                    ui.agents.forEach { agent ->
                        DropdownMenuItem(
                            text = { Text(agent.display_name ?: agent.agent_id, color = TextPrimary) },
                            onClick = {
                                selectedAgentId = agent.agent_id
                                dropdownExpanded = false
                                vm.loadMetrics(selectedAgentId, timeRange)
                            }
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "1ч", 6 to "6ч", 24 to "24ч").forEach { (h, label) ->
                    FilterChip(
                        selected = timeRange == h,
                        onClick = { timeRange = h; vm.loadMetrics(selectedAgentId, h) },
                        label = { Text(label, color = if (timeRange == h) Color.White else TextSecondary) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentVoid,
                            containerColor = CardBg
                        )
                    )
                }
            }
        }
        if (ui.metricsLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentVoid)
                }
            }
        } else if (ui.detailMetrics.isNotEmpty()) {
            item { ChartCard("Load Average", ui.detailMetrics, { it.load1.toFloat() }, AccentVoid, "") }
            item {
                ChartCard("RAM %", ui.detailMetrics, { m ->
                    val total = m.mem_total ?: 1L
                    if (total == 0L) 0f else ((total - m.mem_free).toFloat() / total * 100f)
                }, Color(0xFF7EE787), "%", 100f)
            }
            item { ChartCard("Температура °C", ui.detailMetrics, { it.temperature ?: 0f }, Color(0xFFFF7B72), "°") }
            item { ChartCard("WiFi клиенты", ui.detailMetrics, { it.wifi_clients?.toFloat() ?: 0f }, H3363tPurple, "") }
        } else if (selectedAgentId.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.ShowChart, null, tint = TextSecondary, modifier = Modifier.size(40.dp))
                            Text("Метрики недоступны", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Установите пакет h3363t-metrics на роутере", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─── ADMIN TAB ────────────────────────────────────────────────────────────────

@Composable
fun AdminTabContent(ui: UiState, vm: MainViewModel, pad: PaddingValues) {
    var formUsername by remember { mutableStateOf("") }
    var formPassword by remember { mutableStateOf("") }
    var formConfirm  by remember { mutableStateOf("") }
    var savedMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (ui.adminList.isEmpty()) vm.loadAdmins() }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── АДМИНИСТРАТОРЫ ────────────────────────────────────────────────────
        item {
            Text("АДМИНИСТРАТОРЫ",
                color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
        if (ui.adminLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentVoid, modifier = Modifier.size(28.dp))
                }
            }
        }
        if (ui.adminError != null) {
            item {
                Text(ui.adminError, color = OfflineRed, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        items(ui.adminList) { admin ->
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(CardBg)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (admin.role == "superadmin") Icons.Outlined.ManageAccounts
                        else Icons.Outlined.Person,
                        null,
                        tint = if (admin.role == "superadmin") TrustGold else AccentVoid,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(admin.username, color = TextPrimary, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f))
                    if (admin.username != ui.username) {
                        Surface(
                            onClick = { vm.deleteAdmin(admin.username) },
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, OfflineRed.copy(alpha = 0.7f))
                        ) {
                            Icon(Icons.Default.Close, null, tint = OfflineRed,
                                modifier = Modifier.padding(4.dp).size(18.dp))
                        }
                    }
                }
                HorizontalDivider(color = DividerColor)
            }
        }

        // ── ДОБАВИТЬ / ИЗМЕНИТЬ ПАРОЛЬ ────────────────────────────────────────
        item { Spacer(Modifier.height(16.dp)) }
        item {
            Text("ДОБАВИТЬ / ИЗМЕНИТЬ ПАРОЛЬ",
                color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        item {
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = formUsername, onValueChange = { formUsername = it },
                    label = { Text("Логин") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customOutlinedColors(), singleLine = true
                )
                OutlinedTextField(
                    value = formPassword, onValueChange = { formPassword = it },
                    label = { Text("Пароль") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customOutlinedColors(), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                OutlinedTextField(
                    value = formConfirm, onValueChange = { formConfirm = it },
                    label = { Text("Подтвердить пароль") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customOutlinedColors(), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (savedMsg != null) {
                    Text(savedMsg!!,
                        color = if (savedMsg!!.startsWith("✓")) OnlineGreen else OfflineRed,
                        fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        when {
                            formUsername.isBlank() || formPassword.isBlank() ->
                                savedMsg = "✗ Заполните поля"
                            formPassword != formConfirm ->
                                savedMsg = "✗ Пароли не совпадают"
                            formPassword.length < 4 ->
                                savedMsg = "✗ Пароль слишком короткий"
                            else -> {
                                vm.createAdmin(formUsername, formPassword)
                                savedMsg = "✓ Сохранено"
                                formUsername = ""; formPassword = ""; formConfirm = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentVoid),
                    enabled = formUsername.isNotBlank() && formPassword.isNotBlank()
                ) { Text("Сохранить", fontWeight = FontWeight.SemiBold) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─── LOCAL NODE MONITOR ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalNodeScreen(
    connected: Boolean,
    peerCount: Int,
    loading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PhoneAndroid, null, tint = AccentVoid)
                        Spacer(Modifier.width(8.dp))
                        Text("Локальная нода", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextSecondary) }
                },
                actions = {
                    IconButton(onRefresh) { Icon(Icons.Default.Refresh, null, tint = TextSecondary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        },
        containerColor = BgDark
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(12.dp).background(
                                if (connected) OnlineGreen else OfflineRed,
                                RoundedCornerShape(50)
                            )
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                if (connected) "Нода активна" else "Нода не запущена",
                                color = if (connected) OnlineGreen else OfflineRed,
                                fontWeight = FontWeight.Bold, fontSize = 16.sp
                            )
                            Text("ws://127.0.0.1:9001", color = TextSecondary, fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }

                    HorizontalDivider(color = DividerColor)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        StatItem("Пиры", if (connected) peerCount.toString() else "—")
                        StatItem("Порт WS", "9001")
                        StatItem("h3363t-core", "v0.6.0")
                    }
                }
            }

            // Info card
            if (!connected) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Запуск локальной ноды", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Нода h3363t-core должна быть запущена как Foreground Service " +
                            "через H3363TService на этом устройстве.",
                            color = TextSecondary, fontSize = 13.sp
                        )
                    }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentVoid)
                }
            }
        }
    }
}

// ─── SETTINGS ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    serverUrl: String,
    username: String,
    onSave: (String) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    var urlField by remember(serverUrl) { mutableStateOf(serverUrl) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextSecondary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        },
        containerColor = BgDark
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Подключение", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    InfoRow("Пользователь", username.ifBlank { "—" })
                    OutlinedTextField(
                        value = urlField,
                        onValueChange = { urlField = it; saved = false },
                        label = { Text("OWM Server URL") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = customOutlinedColors(),
                        singleLine = true
                    )
                    if (saved) {
                        Text("✓ Сохранено", color = OnlineGreen, fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            onSave(urlField)
                            saved = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentVoid),
                        enabled = urlField.isNotBlank() && urlField != serverUrl
                    ) {
                        Text("Сохранить и переподключиться")
                    }
                }
            }

            // About card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("О приложении", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    InfoRow("Версия", "1.0.0")
                    InfoRow("OWM Server", "v2.5.0")
                    InfoRow("h3363t-core", "v0.6.0-alpha")
                }
            }

            Spacer(Modifier.weight(1f))

            // Logout
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OfflineRed)
            ) {
                Icon(Icons.Default.ExitToApp, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Выйти")
            }
        }
    }
}

// ─── H3363T NODE SCREEN ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun H3363TScreen(
    nodes: List<H3363tNodeStatus>,
    events: List<H3363tEvent>,
    loading: Boolean,
    error: String?,
    connected: Boolean,
    commandResult: String?,
    onRefresh: () -> Unit,
    onCommand: (String, Int?, String?) -> Unit,
    onClearResult: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Security, null, tint = H3363tPurple)
                        Spacer(Modifier.width(8.dp))
                        Text("H3363T Нода", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextSecondary) }
                },
                actions = {
                    Box(Modifier.padding(end = 4.dp)) {
                        Box(
                            Modifier.size(10.dp).background(
                                if (connected) OnlineGreen else OfflineRed,
                                RoundedCornerShape(50)
                            )
                        )
                    }
                    IconButton(onRefresh) { Icon(Icons.Default.Refresh, null, tint = TextSecondary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        },
        containerColor = BgDark
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = H3363tPurple)
                    }
                    error != null -> Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(Modifier.padding(24.dp)) { Text(error, color = OfflineRed) }
                    }
                    nodes.isNotEmpty() -> NodeStatusCard(nodes[0])
                    else -> Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Devices, null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Нода не подключена к OWM серверу", color = TextSecondary)
                            }
                        }
                    }
                }
            }

            item {
                CommandPanel(onCommand = onCommand, commandResult = commandResult, onClearResult = onClearResult)
            }

            if (events.isNotEmpty()) {
                item {
                    Text("Журнал событий", fontWeight = FontWeight.SemiBold, color = TextPrimary,
                        fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
                items(events.take(50)) { event -> EventCard(event) }
            }
        }
    }
}

@Composable
fun NodeStatusCard(node: H3363tNodeStatus) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, null, tint = TrustGold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${node.pubkey.take(8)}...${node.pubkey.takeLast(8)}",
                    color = TextSecondary, fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Star, null, tint = TrustGold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Trust Level", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("${node.trust_level}/100", color = TrustGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            HorizontalDivider(color = DividerColor)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatItem("Peers", node.peer_count.toString())
                StatItem("Feed", formatFeedSize(node.feed_size))
                StatItem("Uptime", formatUptime(node.uptime_sec.toDouble()))
            }

            HorizontalDivider(color = DividerColor)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).background(
                        if (node.osiis_active) OnlineGreen else OfflineRed,
                        RoundedCornerShape(50)
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "OSIIS ${if (node.osiis_active) "Active" else "Inactive"}",
                    color = if (node.osiis_active) OnlineGreen else OfflineRed, fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun CommandPanel(
    onCommand: (String, Int?, String?) -> Unit,
    commandResult: String?,
    onClearResult: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Быстрые действия", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommandButton("Firewall 9004", Icons.Outlined.Shield) { onCommand("apply_firewall", 9004, "ACCEPT") }
                CommandButton("Restart", Icons.Outlined.RestartAlt) { onCommand("restart_node", null, null) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommandButton("Sync Now", Icons.Outlined.Sync) { onCommand("sync_now", null, null) }
                CommandButton("Port Fwd", Icons.Outlined.OpenInBrowser) { onCommand("set_port_forward", 9005, "TCP") }
            }
            if (commandResult != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (commandResult.startsWith("✓")) Color(0xFF0D2818) else Color(0xFF2D1215),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            commandResult,
                            color = if (commandResult.startsWith("✓")) OnlineGreen else OfflineRed,
                            fontSize = 13.sp, modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearResult, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.CommandButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = TextPrimary,
            containerColor = Color(0xFF16133A)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AccentVoid, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp)
        }
    }
}

@Composable
fun EventCard(event: H3363tEvent) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = when (event.event_type) {
                    "peer_connected"       -> Color(0xFF1A1435)
                    "trust_level_changed"  -> Color(0xFF3D3308)
                    "feed_sync_complete"   -> Color(0xFF0D2818)
                    "ban_applied"          -> Color(0xFF2D1215)
                    else                   -> Color(0xFF16133A)
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (event.event_type) {
                            "peer_connected"      -> Icons.Outlined.People
                            "trust_level_changed" -> Icons.Outlined.Star
                            "feed_sync_complete"  -> Icons.Outlined.CheckCircle
                            "ban_applied"         -> Icons.Outlined.Block
                            else                  -> Icons.Outlined.Info
                        },
                        null,
                        tint = when (event.event_type) {
                            "peer_connected"      -> AccentVoid
                            "trust_level_changed" -> TrustGold
                            "feed_sync_complete"  -> OnlineGreen
                            "ban_applied"         -> OfflineRed
                            else                  -> TextSecondary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.event_type.replace("_", " ").capitalize(),
                    fontWeight = FontWeight.Medium, color = TextPrimary, fontSize = 13.sp)
                Text("${event.node_pubkey.take(6)}...${event.node_pubkey.takeLast(6)}",
                    color = TextSecondary, fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            Text(formatEventTime(event.timestamp), color = TextSecondary, fontSize = 11.sp)
        }
    }
}

// ─── DETAIL SCREEN ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    agent: AgentFull,
    agentDetail: AgentDetailData? = null,
    detailLoading: Boolean = false,
    metrics: List<Metric>,
    h3363tNodes: List<H3363tNodeStatus> = emptyList(),
    onBack: () -> Unit,
    onOpenTerminal: (String) -> Unit = {},
    onMetrics: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(agentDetail?.display_name ?: agent.display_name ?: agent.agent_id, fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextSecondary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        },
        containerColor = BgDark
    ) { pad ->
        val isOnline = agentDetail?.online ?: agent.online

        if (detailLoading && agentDetail == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentVoid)
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Action buttons
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onOpenTerminal("__terminal__") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16133A)),
                        enabled = isOnline
                    ) {
                        Icon(Icons.Outlined.Code, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SSH", color = TextPrimary, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { onOpenTerminal("__luci__") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16133A)),
                        enabled = isOnline
                    ) {
                        Icon(Icons.Outlined.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("LuCI", color = TextPrimary, fontSize = 13.sp)
                    }
                    Button(
                        onClick = onMetrics,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16133A)),
                        enabled = isOnline
                    ) {
                        Icon(Icons.Outlined.ShowChart, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Метрики", color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }

            // Status card
            item {
                Card(shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(
                                if (isOnline) OnlineGreen else OfflineRed, RoundedCornerShape(50)))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isOnline) "Online" else "Offline",
                                color = if (isOnline) OnlineGreen else OfflineRed,
                                fontWeight = FontWeight.SemiBold)
                        }
                        val displayIp = agentDetail?.local_ip ?: agent.local_ip
                        val displayAddr = agentDetail?.address ?: agent.address
                        if (displayAddr != null) InfoRow("Адрес", displayAddr)
                        if (displayIp != null) InfoRow("IP (LAN)", displayIp)

                        agentDetail?.metrics?.let { m ->
                            m.uptime?.let { InfoRow("Uptime", formatUptime(it.toDouble())) }
                            m.cpu_load?.let { InfoRow("CPU Load", "%.2f".format(it)) }
                            if (m.ram_usage != null && m.ram_total != null && m.ram_total > 0) {
                                val pct = m.ram_usage * 100 / m.ram_total
                                InfoRow("RAM", "${m.ram_usage}/${m.ram_total} MB ($pct%)")
                            }
                            m.wifi_clients?.let { InfoRow("WiFi клиенты", it.toString()) }
                            if (m.wan_rx_bytes != null || m.wan_tx_bytes != null)
                                InfoRow("WAN", "↓${formatBytes(m.wan_rx_bytes)}  ↑${formatBytes(m.wan_tx_bytes)}")
                            m.node_status?.let { InfoRow("H3363T нода", it) }
                            m.node_peer_count?.let { InfoRow("Пиры", it.toString()) }
                        } ?: agent.metric?.let { m ->
                            InfoRow("Uptime", formatUptime(m.uptime))
                            InfoRow("Load", "%.2f".format(m.load1))
                            InfoRow("RAM %", memPercent(m.mem_free, m.mem_total))
                            m.temperature?.let { InfoRow("Температура", "%.1f°C".format(it)) }
                            m.wifi_clients?.let { InfoRow("WiFi клиенты", it.toString()) }
                            if (m.wan_rx != null || m.wan_tx != null)
                                InfoRow("WAN", "↓${formatBytes(m.wan_rx)}  ↑${formatBytes(m.wan_tx)}")
                        }
                    }
                }
            }

            // Metrics preview (last 1h)
            if (metrics.isNotEmpty()) {
                item { ChartCard("Load Average", metrics, { it.load1.toFloat() }, AccentVoid, "") }
                item {
                    ChartCard("RAM %", metrics, { m ->
                        val total = m.mem_total ?: 1L
                        if (total == 0L) 0f else ((total - m.mem_free).toFloat() / total * 100f)
                    }, Color(0xFF7EE787), "%", 100f)
                }
                item { ChartCard("Температура °C", metrics, { it.temperature ?: 0f }, Color(0xFFFF7B72), "°") }
            } else if (agent.online) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.ShowChart, null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Метрики недоступны", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Установите пакет h3363t-metrics на роутере", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // H3363T node status
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Security, null, tint = H3363tPurple, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("H3363T Нода", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        HorizontalDivider(color = DividerColor)
                        if (h3363tNodes.isNotEmpty()) {
                            val node = h3363tNodes[0]
                            InfoRow("Пиры", node.peer_count.toString())
                            InfoRow("Feed", formatFeedSize(node.feed_size))
                            InfoRow("Uptime", formatUptime(node.uptime_sec.toDouble()))
                            InfoRow("OSIIS", if (node.osiis_active) "Active" else "Inactive")
                            InfoRow("Trust", "${node.trust_level}/100")
                        } else {
                            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text("Агент H3363T не запущен", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Delete button
            item {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OfflineRed)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Удалить роутер")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить роутер?", color = TextPrimary) },
            text = { Text(agent.display_name ?: agent.agent_id, color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = OfflineRed)
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton({ confirmDelete = false }) { Text("Отмена", color = TextSecondary) }
            },
            containerColor = CardBg
        )
    }
}

// ─── WEB SCREEN ──────────────────────────────────────────────────────────────

@Composable
fun MainWebScreen(serverUrl: String, token: String, username: String, onLogout: () -> Unit) {
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) wv.goBack()
    }

    Box(Modifier.fillMaxSize().background(BgDark)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: android.webkit.WebView,
                            request: android.webkit.WebResourceRequest
                        ): Boolean {
                            view.loadUrl(request.url.toString())
                            return true
                        }
                        override fun onPageFinished(view: android.webkit.WebView, pageUrl: String) {
                            val safeToken = org.json.JSONObject.quote(token)
                            val safeUser = org.json.JSONObject.quote(username)
                            val js = """
                                (function() {
                                    if (!localStorage.getItem('owm_token')) {
                                        localStorage.setItem('owm_token', $safeToken);
                                        localStorage.setItem('owm_user', $safeUser);
                                        location.reload();
                                    }
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(js, null)
                        }
                    }
                    webChromeClient = android.webkit.WebChromeClient()
                    webViewRef = this
                    loadUrl(serverUrl)
                }
            }
        )
    }
}

// ─── Helper Components ───────────────────────────────────────────────────────

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ChartCard(
    title: String,
    metrics: List<Metric>,
    valueSelector: (Metric) -> Float,
    color: Color,
    unit: String,
    maxValue: Float? = null
) {
    val values = metrics.map(valueSelector)
    val max = maxValue ?: (values.maxOrNull()?.times(1.2f) ?: 1f).coerceAtLeast(1f)
    val lastVal = values.lastOrNull()

    Card(shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(lastVal?.let { "%.1f".format(it) + unit } ?: "—",
                    color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                if (values.size < 2) return@Canvas
                val w = size.width; val h = size.height
                val step = w / (values.size - 1)
                val range = (max - 0f).coerceAtLeast(0.001f)
                for (i in 0 until values.size - 1) {
                    val x1 = i * step; val y1 = h - (values[i] / range * h)
                    val x2 = (i + 1) * step; val y2 = h - (values[i + 1] / range * h)
                    drawLine(color = color,
                        start = androidx.compose.ui.geometry.Offset(x1, y1),
                        end = androidx.compose.ui.geometry.Offset(x2, y2),
                        strokeWidth = 2.5f)
                }
                val lx = (values.size - 1) * step
                val ly = h - (values.last() / range * h)
                drawCircle(color = color, radius = 4f, center = androidx.compose.ui.geometry.Offset(lx, ly))
            }
        }
    }
}

@Composable
fun customOutlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentVoid,
    unfocusedBorderColor = Color(0xFF2A2250),
    focusedLabelColor = AccentVoid,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = AccentVoid
)

// ─── Format Helpers ──────────────────────────────────────────────────────────

fun memPercent(free: Long?, total: Long?): String {
    if (free == null || total == null || total == 0L) return "—"
    return "%.0f%%".format((total - free).toDouble() / total * 100)
}

fun formatUptime(secs: Double): String {
    val s = secs.toLong()
    return when {
        s < 3600 -> "${s / 60}м"
        s < 86400 -> "${s / 3600}ч"
        else -> "${s / 86400}д"
    }
}

fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "—"
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1048576 -> "${bytes / 1024}K"
        bytes < 1073741824 -> "${bytes / 1048576}M"
        else -> "%.1fG".format(bytes / 1073741824.0)
    }
}

fun formatAgo(secs: Long): String = when {
    secs < 60 -> "${secs}с"
    secs < 3600 -> "${secs / 60}м"
    else -> "${secs / 3600}ч"
}

fun formatFeedSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} B"
    bytes < 1048576 -> "${bytes / 1024} KB"
    bytes < 1073741824 -> "${bytes / 1048576} MB"
    else -> "%.1f GB".format(bytes / 1073741824.0)
}

fun formatEventTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() / 1000 - timestamp
    return when {
        diff < 60 -> "${diff}с"
        diff < 3600 -> "${diff / 60}м"
        diff < 86400 -> "${diff / 3600}ч"
        else -> "${diff / 86400}д"
    }
}

fun String.capitalize(): String =
    if (isNotEmpty()) replaceFirstChar { it.uppercase() } else this

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
                    OutlinedButton(
                        onClick = { vm.refresh() },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentVoid),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentVoid.copy(alpha = 0.5f))
                    ) { Text("Обновить", fontSize = 11.sp) }
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
                            localSettings = ui.agentLocalMap[agent.agent_id],
                            onTap = {
                                val local = ui.agentLocalMap[agent.agent_id]
                                if (local?.lat != null && local.lon != null) {
                                    onCenterMap(local.lat, local.lon)
                                }
                            },
                            onLongPress = { onOpenCard(agent) }
                        )
                    }
                }
            }
        }
    ) { }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgentBottomSheetRow(
    agent: AgentFull,
    localSettings: AgentLocalSettings?,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF16133A), RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
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
                            append("Load ${"%.2f".format(m.load1)}")
                            if (m.mem_total != null && m.mem_total > 0L) {
                                val usedMb = (m.mem_total - (m.mem_free ?: 0L)) / 1024L / 1024L
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

// ─── ROUTER CARD ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterCard(
    agent: AgentFull,
    ui: UiState,
    vm: MainViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0D0B1E),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Sub-header
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
                    val secsAgo = agent.last_seen_secs
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

            // Action buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { scope.launch { sheetState.hide(); onDismiss(); vm.openNativeSsh(agent) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16133A)),
                    modifier = Modifier.weight(1f)
                ) { Text("SSH", color = AccentVoid) }
                Button(
                    onClick = { scope.launch { sheetState.hide(); onDismiss(); vm.openLuci(agent) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16133A)),
                    modifier = Modifier.weight(1f)
                ) { Text("LuCI", color = TextPrimary) }
                Button(
                    onClick = { scope.launch { sheetState.hide(); onDismiss(); vm.openAgentSettings(agent.agent_id) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16133A)),
                    modifier = Modifier.weight(1f)
                ) { Text("Настройки", color = TextSecondary) }
            }

            Spacer(Modifier.height(16.dp))

            // Metrics
            agent.metric?.let { m ->
                Text("Метрики", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                MetricRow("Load", "%.2f".format(m.load1))
                if (m.mem_total != null && m.mem_total > 0) {
                    val usedMb = (m.mem_total - (m.mem_free ?: 0L)) / 1024L / 1024L
                    val totalMb = m.mem_total / 1024L / 1024L
                    MetricRow("RAM", "$usedMb / $totalMb MB")
                }
                m.wifi_clients?.let { MetricRow("WiFi клиенты", "$it") }
                m.temperature?.let { MetricRow("Температура", "${it.toInt()}°C") }
            }
        }
    }
}
