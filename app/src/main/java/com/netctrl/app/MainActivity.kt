package com.netctrl.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
                agent = screen.agent, metrics = ui.detailMetrics,
                onBack = { vm.closeDetail() },
                onOpenTerminal = { type -> vm.openAgentWeb(screen.agent, type) }
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
            else -> MainTabScaffold(ui = ui, vm = vm)
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


@Composable
fun RouterListTab(ui: UiState, vm: MainViewModel, pad: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(pad)) {
        // Sub-header row
        Row(
            Modifier
                .fillMaxWidth()
                .background(CardBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "РОУТЕРЫ (${ui.agents.size})",
                color = TextSecondary, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { vm.refresh() },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Обновить", fontSize = 11.sp)
            }
            Button(
                onClick = { vm.openAddRouter() },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentVoid)
            ) {
                Text("+ Добавить", fontSize = 11.sp)
            }
        }
        HorizontalDivider(color = DividerColor)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { ServerStatusCard(ui) }
            if (ui.agents.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Router, null,
                                tint = TextSecondary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Нет зарегистрированных агентов",
                                color = TextSecondary, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(ui.agents) { agent ->
                    AgentCard(
                        a = agent,
                        onMetrics = {
                            vm.loadMetrics(agent.agent_id, 1)
                            vm.navigateTo(Screen.Metrics)
                        },
                        onSsh = { vm.navigateTo(Screen.SshTerminal(agent)) },
                        onLuci = { vm.navigateTo(Screen.LuciView(agent)) },
                        onSettings = { vm.openDetail(agent) },
                        onDelete = { vm.deleteAgentOnServer(agent.agent_id) }
                    )
                }
            }
        }
    }
}

@Composable
fun AddRouterContent(ui: UiState, vm: MainViewModel, pad: PaddingValues) {
    var showInstallDialog by remember { mutableStateOf(false) }
    val installCmd = if (ui.installAgentStatus?.startsWith("CMD:") == true)
        ui.installAgentStatus.removePrefix("CMD:") else null

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
                Button(
                    onClick = { showInstallDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentVoid)
                ) { Text("OK") }
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
fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = AccentVoid,
    selectedTextColor = AccentVoid,
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary,
    indicatorColor = Color(0xFF1E1A35)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabScaffold(ui: UiState, vm: MainViewModel) {
    val screen = ui.screen
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚡", fontSize = 18.sp)
                        Text("H3363T NetCtrl", fontWeight = FontWeight.Bold, color = AccentVoid, fontSize = 17.sp)
                        if (ui.serverHealthOk) {
                            Box(Modifier.size(7.dp).background(OnlineGreen, RoundedCornerShape(50)))
                            Text("онлайн", color = OnlineGreen, fontSize = 12.sp)
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
            NavigationBar(containerColor = CardBg, tonalElevation = 0.dp) {
                val dashActive = screen is Screen.Dashboard || screen is Screen.AddRouter
                NavigationBarItem(
                    selected = dashActive,
                    onClick = { vm.navigateTo(Screen.Dashboard) },
                    icon = { Icon(Icons.Outlined.Router, null) },
                    label = { Text("Роутеры", fontSize = 11.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = screen is Screen.Map,
                    onClick = { vm.navigateTo(Screen.Map) },
                    icon = { Icon(Icons.Outlined.Place, null) },
                    label = { Text("Карта", fontSize = 11.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = screen is Screen.Ssh,
                    onClick = { vm.navigateTo(Screen.Ssh) },
                    icon = { Icon(Icons.Outlined.Code, null) },
                    label = { Text("SSH", fontSize = 11.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = screen is Screen.Metrics,
                    onClick = { vm.navigateTo(Screen.Metrics) },
                    icon = { Icon(Icons.Outlined.ShowChart, null) },
                    label = { Text("Метрики", fontSize = 11.sp) },
                    colors = navItemColors()
                )
                if (ui.isSuperAdmin) {
                    NavigationBarItem(
                        selected = screen is Screen.Admin,
                        onClick = { vm.navigateTo(Screen.Admin) },
                        icon = { Icon(Icons.Outlined.ManageAccounts, null) },
                        label = { Text("Админ", fontSize = 11.sp) },
                        colors = navItemColors()
                    )
                }
            }
        },
        containerColor = BgDark
    ) { pad ->
        when (screen) {
            is Screen.Map       -> MapTabContent(ui, vm, pad)
            is Screen.Ssh       -> SshTabContent(ui, vm, pad)
            is Screen.Metrics   -> MetricsTabContent(ui, vm, pad)
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
                    a.lat?.let { put("lat", it) } ?: put("lat", org.json.JSONObject.NULL)
                    a.lng?.let { put("lng", it) } ?: put("lng", org.json.JSONObject.NULL)
                }
            }).toString()
        } catch (_: Exception) { "[]" }
    }

    @android.webkit.JavascriptInterface
    fun onAgentClick(agentId: String) {
        val agent = vm.ui.value.agents.find { it.agent_id == agentId } ?: return
        kotlinx.coroutines.MainScope().launch { vm.openDetail(agent) }
    }

    @android.webkit.JavascriptInterface
    fun onLocationPicked(lat: Double, lng: Double) {
        kotlinx.coroutines.MainScope().launch { vm.setPickedLocation(lat, lng) }
    }
}

@Composable
fun MapTabContent(ui: UiState, vm: MainViewModel, pad: PaddingValues) {
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    val agentsSnapshot = ui.agents
    val pickMode = ui.mapPickMode

    LaunchedEffect(agentsSnapshot) {
        webViewRef?.evaluateJavascript("refreshAgents();", null)
    }
    LaunchedEffect(pickMode) {
        if (pickMode) webViewRef?.evaluateJavascript("enterPickMode();", null)
        else webViewRef?.evaluateJavascript("exitPickMode();", null)
    }

    Row(Modifier.fillMaxSize().padding(pad)) {
        // ── Left sidebar ─────────────────────────────────────────────────────
        Column(
            Modifier
                .width(160.dp)
                .fillMaxHeight()
                .background(CardBg)
        ) {
            // Back button row
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { vm.navigateTo(Screen.Dashboard) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF16133A), RoundedCornerShape(6.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, null,
                        tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
            }

            if (pickMode) {
                // Pick mode panel
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .background(Color(0xFF2A1020), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📍", fontSize = 18.sp)
                    Text("Кликни на карту чтобы назначить место",
                        color = AccentVoid, fontSize = 12.sp)
                    OutlinedButton(
                        onClick = { vm.cancelMapPick() },
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) { Text("Отмена", fontSize = 12.sp) }
                }
            } else {
                // Router list
                Text("РОУТЕРЫ",
                    color = TextSecondary, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(ui.agents) { agent ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.openDetail(agent) }
                                .background(Color(0xFF16133A), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .offset(y = 4.dp)
                                    .background(
                                        if (agent.online) OnlineGreen else OfflineRed,
                                        RoundedCornerShape(50)
                                    )
                            )
                            Column {
                                Text(agent.display_name ?: agent.agent_id,
                                    color = TextPrimary, fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium)
                                agent.address?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("📍", fontSize = 10.sp)
                                        Spacer(Modifier.width(2.dp))
                                        Text(it, color = TextSecondary, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        // ── Map WebView ───────────────────────────────────────────────────────
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    addJavascriptInterface(MapBridge(vm), "AndroidBridge")
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    webViewRef = this
                    loadUrl("file:///android_asset/map.html?mode=view")
                }
            }
        )
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

// ─── METRICS TAB ──────────────────────────────────────────────────────────────

@Composable
fun MetricsTabContent(ui: UiState, vm: MainViewModel, pad: PaddingValues) {
    var selectedAgentId by remember { mutableStateOf("") }
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
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Нет данных метрик", color = TextSecondary)
                }
            }
        }
    }
}

// ─── ADMIN TAB ────────────────────────────────────────────────────────────────

@Composable
fun AdminTabContent(ui: UiState, vm: MainViewModel, pad: PaddingValues) {
    var showCreateDialog by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Администраторы", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = AccentVoid,
                    modifier = Modifier.size(40.dp)
                ) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
        if (ui.adminLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentVoid)
                }
            }
        }
        if (ui.adminError != null) {
            item { Text(ui.adminError, color = OfflineRed, fontSize = 13.sp) }
        }
        items(ui.adminList) { admin ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (admin.role == "superadmin") Icons.Outlined.ManageAccounts else Icons.Outlined.Person,
                        null, tint = if (admin.role == "superadmin") TrustGold else TextSecondary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(admin.username, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(admin.role, color = TextSecondary, fontSize = 12.sp)
                    }
                    if (admin.username != ui.username) {
                        IconButton(onClick = { vm.deleteAdmin(admin.username) }) {
                            Icon(Icons.Default.Delete, null, tint = OfflineRed)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var newUsername by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Новый администратор", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newUsername, onValueChange = { newUsername = it },
                        label = { Text("Username") }, colors = customOutlinedColors(), singleLine = true)
                    OutlinedTextField(value = newPassword, onValueChange = { newPassword = it },
                        label = { Text("Password") }, colors = customOutlinedColors(), singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.createAdmin(newUsername, newPassword); showCreateDialog = false },
                    enabled = newUsername.isNotBlank() && newPassword.length >= 4,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentVoid)
                ) { Text("Создать") }
            },
            dismissButton = { TextButton({ showCreateDialog = false }) { Text("Отмена", color = TextSecondary) } },
            containerColor = CardBg
        )
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
fun DetailScreen(agent: AgentFull, metrics: List<Metric>, onBack: () -> Unit, onOpenTerminal: (String) -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(agent.display_name ?: agent.agent_id, fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextSecondary) }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onOpenTerminal("__terminal__") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16133A)),
                        enabled = agent.online
                    ) { Text("SSH терминал", color = TextPrimary, fontSize = 13.sp) }
                    if (agent.luci_url != null) {
                        Button(
                            onClick = { onOpenTerminal("__luci__") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16133A)),
                            enabled = agent.online
                        ) { Text("LuCI", color = TextPrimary, fontSize = 13.sp) }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(
                                if (agent.online) OnlineGreen else OfflineRed, RoundedCornerShape(50)))
                            Spacer(Modifier.width(8.dp))
                            Text(if (agent.online) "Online" else "Offline",
                                color = if (agent.online) OnlineGreen else OfflineRed,
                                fontWeight = FontWeight.SemiBold)
                        }
                        if (agent.address != null) InfoRow("Адрес", agent.address)
                        if (agent.local_ip != null) InfoRow("IP", agent.local_ip)
                        agent.metric?.let { m ->
                            InfoRow("Uptime", formatUptime(m.uptime))
                            InfoRow("Температура", m.temperature?.let { "%.1f°C".format(it) } ?: "—")
                            InfoRow("WiFi клиенты", m.wifi_clients?.toString() ?: "—")
                            InfoRow("WAN ↓", formatBytes(m.wan_rx))
                            InfoRow("WAN ↑", formatBytes(m.wan_tx))
                        }
                    }
                }
            }

            if (metrics.isNotEmpty()) {
                item { ChartCard("Load Average", metrics, { it.load1.toFloat() }, AccentVoid, "") }
                item { ChartCard("RAM %", metrics, { m ->
                    val total = m.mem_total ?: 1L
                    if (total == 0L) 0f else ((total - m.mem_free).toFloat() / total * 100f)
                }, Color(0xFF7EE787), "%", 100f) }
                item { ChartCard("Температура °C", metrics, { it.temperature ?: 0f }, Color(0xFFFF7B72), "°") }
            } else if (agent.online) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentVoid)
                    }
                }
            }
        }
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
