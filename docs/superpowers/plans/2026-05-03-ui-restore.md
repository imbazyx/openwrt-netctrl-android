# H3363T Android UI Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the original OpenWRT NetCtrl UI structure — inline agent action buttons (📊 SSH LuCI ⚙ ✗), Add Router full-screen form with "Установить агент", split-pane map with native sidebar, inline Admin form — while keeping the VOID #7050C8 theme and 5-tab navigation.

**Architecture:** Variant B — rewrite all Compose UI composables in `MainActivity.kt`, add state fields to `UiState.kt`, add ViewModel methods to `MainViewModel.kt`. ViewModel/Api/Prefs/Screen extended additively. `map.html` extended with JS pick-mode API.

**Tech Stack:** Kotlin, Jetpack Compose Material3, OkHttp/Retrofit, WebView (Leaflet 1.9.4, xterm.js).

---

## File Map

| File | Change |
|---|---|
| `Screen.kt` | Add `AddRouter` sealed object |
| `UiState.kt` | Add 11 AddRouter + mapPickMode fields |
| `MainViewModel.kt` | Update `setPickedLocation`; add 6 new methods |
| `MainActivity.kt` | Rewrite: TopBar, AgentCard, RouterListTab, AddRouterContent, MapTabContent, AdminTabContent |
| `assets/map.html` | Add JS `enterPickMode()` / `exitPickMode()` + map click handler |

---

### Task 1: Data layer — Screen, UiState, ViewModel

**Files:**
- Modify: `app/src/main/java/com/netctrl/app/Screen.kt`
- Modify: `app/src/main/java/com/netctrl/app/UiState.kt`
- Modify: `app/src/main/java/com/netctrl/app/MainViewModel.kt`

- [ ] **Step 1.1: Add Screen.AddRouter**

Replace entire `Screen.kt`:

```kotlin
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
}
```

- [ ] **Step 1.2: Extend UiState with AddRouter + mapPickMode fields**

Replace entire `UiState.kt`:

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
```

- [ ] **Step 1.3: Update setPickedLocation + add AddRouter methods in MainViewModel.kt**

Find this block in `MainViewModel.kt`:

```kotlin
fun setPickedLocation(lat: Double, lng: Double) {
    _ui.update { it.copy(pickedLat = lat, pickedLng = lng) }
}
```

Replace with:

```kotlin
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
        addRouterId      = id      ?: s.addRouterId,
        addRouterName    = name    ?: s.addRouterName,
        addRouterDesc    = desc    ?: s.addRouterDesc,
        addRouterIp      = ip      ?: s.addRouterIp,
        addRouterSshPass = sshPass ?: s.addRouterSshPass,
        addRouterAddress = address ?: s.addRouterAddress
    ) }
}

fun enterMapPickMode() {
    _ui.update { it.copy(mapPickMode = true, screen = Screen.Map) }
}

fun installAgent() {
    val s = _ui.value
    val cmd = "curl -sL ${s.serverUrl}/api/v1/agents/install.sh | AGENT_ID=${s.addRouterId} sh"
    _ui.update { it.copy(installAgentStatus = "CMD:$cmd") }
}

fun submitAddRouter() {
    val s = _ui.value
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
```

- [ ] **Step 1.4: Verify compilation**

```bash
cd "D:/project/H3363T/openwrt-netctrl-android"
./gradlew compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` or only UI-related errors (MainActivity not yet updated).

---

### Task 2: TopBar + bottom nav + MainTabScaffold routing

**Files:**
- Modify: `app/src/main/java/com/netctrl/app/MainActivity.kt`

Find the `MainTabScaffold` composable (~line 292). Replace the entire function:

- [ ] **Step 2.1: Rewrite MainTabScaffold**

```kotlin
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
            is Screen.Map      -> MapTabContent(ui, vm, pad)
            is Screen.Ssh      -> SshTabContent(ui, vm, pad)
            is Screen.Metrics  -> MetricsTabContent(ui, vm, pad)
            is Screen.Admin    -> AdminTabContent(ui, vm, pad)
            is Screen.AddRouter -> AddRouterContent(ui, vm, pad)
            else               -> RouterListTab(ui, vm, pad)
        }
    }
}
```

- [ ] **Step 2.2: Verify the topBar compiles**

```bash
cd "D:/project/H3363T/openwrt-netctrl-android"
./gradlew compileDebugKotlin 2>&1 | grep -i "error" | head -20
```

Expected: errors only about missing `AddRouterContent` (added in Task 4).

---

### Task 3: AgentCard with inline action buttons

**Files:**
- Modify: `app/src/main/java/com/netctrl/app/MainActivity.kt`

Find the `AgentCard` composable (~line 228). Replace the entire function:

- [ ] **Step 3.1: Rewrite AgentCard with action button row**

```kotlin
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
                        fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Stats (monospace)
            if (a.address != null || a.local_ip != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    a.address?.let { Text(it, color = TextSecondary, fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
                    a.local_ip?.let { Text(it, color = TextSecondary, fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
                }
            }

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

            // Action buttons row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Metrics
                AgentActionBtn(onClick = onMetrics) {
                    Icon(Icons.Outlined.ShowChart, null,
                        tint = AccentVoid, modifier = Modifier.size(16.dp))
                }
                // SSH
                AgentActionBtn(onClick = onSsh, enabled = a.online) { Text("SSH", fontSize = 11.sp, color = if (a.online) TextPrimary else TextSecondary) }
                // LuCI
                AgentActionBtn(onClick = onLuci, enabled = a.online) { Text("LuCI", fontSize = 11.sp, color = if (a.online) TextPrimary else TextSecondary) }
                // Settings
                AgentActionBtn(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, null,
                        tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.weight(1f))
                // Delete
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
                Button(onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = OfflineRed)
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Отмена", color = TextSecondary) } },
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
        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { content() }
    }
}
```

- [ ] **Step 3.2: Update RouterListTab to wire AgentCard callbacks**

Find the `RouterListTab` composable. In its `items(ui.agents)` block, find:

```kotlin
items(ui.agents) { agent ->
    AgentCard(agent, onClick = { vm.openDetail(agent) })
}
```

Replace with:

```kotlin
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
```

- [ ] **Step 3.3: Fix SshTabContent to not use old AgentCard signature**

In `SshTabContent`, find:

```kotlin
items(ui.agents) { agent ->
    AgentCard(agent, onClick = { if (agent.online) selectedAgent = agent })
}
```

Replace with:

```kotlin
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
```

---

### Task 4: RouterListTab sub-header + AddRouterContent

**Files:**
- Modify: `app/src/main/java/com/netctrl/app/MainActivity.kt`

- [ ] **Step 4.1: Rewrite RouterListTab with РОУТЕРЫ header**

Find and replace the entire `RouterListTab` function:

```kotlin
@Composable
fun RouterListTab(ui: UiState, vm: MainViewModel, pad: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(pad)) {
        // Sub-header: "РОУТЕРЫ (N)" + buttons
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
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Router, null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Нет зарегистрированных агентов", color = TextSecondary, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(ui.agents) { agent ->
                    AgentCard(
                        a = agent,
                        onMetrics = { vm.loadMetrics(agent.agent_id, 1); vm.navigateTo(Screen.Metrics) },
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
```

- [ ] **Step 4.2: Add AddRouterContent composable**

Add this new composable anywhere in `MainActivity.kt` (e.g., after `RouterListTab`):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRouterContent(ui: UiState, vm: MainViewModel, pad: PaddingValues) {
    var showInstallDialog by remember { mutableStateOf(false) }
    val installCmd = remember(ui.installAgentStatus) {
        if (ui.installAgentStatus?.startsWith("CMD:") == true)
            ui.installAgentStatus.removePrefix("CMD:")
        else null
    }

    LaunchedEffect(installCmd) { if (installCmd != null) showInstallDialog = true }

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
            // Agent ID (hostname)
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
            // Address + map pin
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ui.addRouterAddress,
                    onValueChange = { vm.setAddRouterField(address = it) },
                    label = { Text("Адрес / метка") },
                    modifier = Modifier.weight(1f),
                    colors = customOutlinedColors(), singleLine = true
                )
                // Map pin picker button
                OutlinedButton(
                    onClick = { vm.enterMapPickMode() },
                    modifier = Modifier.size(56.dp).align(Alignment.CenterVertically),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentVoid)
                ) {
                    Text("📍", fontSize = 20.sp)
                }
            }
        }

        // Coordinate status
        if (ui.addRouterLat != null && ui.addRouterLng != null) {
            item {
                Text(
                    "✓ %.4f, %.4f".format(ui.addRouterLat, ui.addRouterLng),
                    color = OnlineGreen, fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        // Install status
        if (ui.installAgentStatus != null && !ui.installAgentStatus.startsWith("CMD:")) {
            item {
                Text(
                    ui.installAgentStatus,
                    color = if (ui.installAgentStatus.startsWith("✓")) OnlineGreen
                            else if (ui.installAgentStatus.startsWith("✗")) OfflineRed
                            else TextSecondary,
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
                ) {
                    if (ui.installAgentStatus == "⏳ Подключаемся...")
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White)
                    else Text("Установить агент", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { vm.closeAddRouter() },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) { Text("Отмена") }
            }
        }

        // "Show command" alternative
        item {
            TextButton(onClick = { vm.installAgent() }) {
                Text("Показать команду установки", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }

    // Command dialog
    if (showInstallDialog && installCmd != null) {
        AlertDialog(
            onDismissRequest = { showInstallDialog = false },
            title = { Text("Команда установки агента", color = TextPrimary) },
            text = {
                SelectionContainer {
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
```

Note: add `import androidx.compose.foundation.text.selection.SelectionContainer` at the top of `MainActivity.kt`.

---

### Task 5: Map tab — split-pane sidebar + JS pick mode

**Files:**
- Modify: `app/src/main/java/com/netctrl/app/MainActivity.kt`
- Modify: `app/src/main/assets/map.html`

- [ ] **Step 5.1: Rewrite MapTabContent as split-pane**

Find and replace the entire `MapTabContent` function:

```kotlin
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
        // ─── Left sidebar ──────────────────────────────────────────────
        Column(
            Modifier
                .width(160.dp)
                .fillMaxHeight()
                .background(CardBg)
        ) {
            // Back / collapse row
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { vm.navigateTo(Screen.Dashboard) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF16133A), RoundedCornerShape(6.dp))
                ) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary, modifier = Modifier.size(18.dp)) }
            }

            if (pickMode) {
                // Pick mode panel
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A1020), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📍", fontSize = 18.sp)
                    Text("Кликни на карту чтобы назначить место",
                        color = AccentVoid, fontSize = 12.sp)
                    OutlinedButton(
                        onClick = {
                            _ui_noop@ vm.setPickedLocation(0.0, 0.0) // cancel: reset pick mode
                            // Actually just exit pick mode without saving
                            vm.navigateTo(Screen.AddRouter)
                        },
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) { Text("Отмена", fontSize = 12.sp) }
                }
            } else {
                // Router list
                Text("РОУТЕРЫ", color = TextSecondary, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(ui.agents) { agent ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.openDetail(agent) }
                                .background(
                                    Color(0xFF16133A),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(Modifier.size(8.dp).offset(y = 4.dp).background(
                                if (agent.online) OnlineGreen else OfflineRed,
                                RoundedCornerShape(50)
                            ))
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

        // ─── Map WebView ───────────────────────────────────────────────
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
```

**Fix the cancel pick mode**: the "Отмена" button above calls `vm.setPickedLocation(0.0, 0.0)` which will write bad data. Instead, add a dedicated `cancelMapPick()` method to ViewModel:

In `MainViewModel.kt`, add after `enterMapPickMode()`:

```kotlin
fun cancelMapPick() {
    _ui.update { it.copy(
        mapPickMode = false,
        screen = if (it.addRouterOpen) Screen.AddRouter else Screen.Map
    ) }
}
```

Then in `MapTabContent`'s "Отмена" button, replace the `onClick` with:

```kotlin
onClick = { vm.cancelMapPick() }
```

- [ ] **Step 5.2: Add pick mode JS to map.html**

Open `app/src/main/assets/map.html`. Find the closing `</script>` tag. Before it, insert:

```javascript
// ─── Pick Mode ───────────────────────────────────────────────────────────────
var pickMode = false;
var tempPickMarker = null;

window.enterPickMode = function() {
  pickMode = true;
};

window.exitPickMode = function() {
  pickMode = false;
  if (tempPickMarker) { map.removeLayer(tempPickMarker); tempPickMarker = null; }
};

map.on('click', function(e) {
  if (!pickMode) return;
  var lat = e.latlng.lat, lng = e.latlng.lng;
  if (tempPickMarker) map.removeLayer(tempPickMarker);
  tempPickMarker = L.marker([lat, lng], {
    icon: L.divIcon({
      html: '<div style="font-size:20px">📍</div>',
      iconSize: [24, 24], iconAnchor: [12, 24], className: ''
    })
  }).addTo(map);
  pickMode = false;
  if (window.AndroidBridge) AndroidBridge.onLocationPicked(lat, lng);
});
```

- [ ] **Step 5.3: Update MapBridge.onLocationPicked to call MainScope**

The existing `MapBridge.onLocationPicked` already calls `vm.setPickedLocation(lat, lng)` — which now handles navigation back to AddRouter. Verify the existing `@JavascriptInterface` is:

```kotlin
@android.webkit.JavascriptInterface
fun onLocationPicked(lat: Double, lng: Double) {
    vm.setPickedLocation(lat, lng)
}
```

If the body only calls `vm.setPickedLocation(lat, lng)` without `MainScope().launch`, add it:

```kotlin
@android.webkit.JavascriptInterface
fun onLocationPicked(lat: Double, lng: Double) {
    kotlinx.coroutines.MainScope().launch { vm.setPickedLocation(lat, lng) }
}
```

---

### Task 6: AdminTabContent — inline form (no dialog)

**Files:**
- Modify: `app/src/main/java/com/netctrl/app/MainActivity.kt`

Find and replace the entire `AdminTabContent` function:

- [ ] **Step 6.1: Rewrite AdminTabContent with inline sections**

```kotlin
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
        // ── SECTION: Administrators list ──────────────────────────────
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
            item { Text(ui.adminError, color = OfflineRed, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp)) }
        }

        items(ui.adminList) { admin ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(CardBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (admin.role == "superadmin") Icons.Outlined.ManageAccounts else Icons.Outlined.Person,
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, OfflineRed.copy(alpha = 0.7f))
                    ) {
                        Icon(Icons.Default.Close, null, tint = OfflineRed,
                            modifier = Modifier.padding(4.dp).size(18.dp))
                    }
                }
            }
            HorizontalDivider(color = DividerColor)
        }

        // ── SECTION: Add / change password ────────────────────────────
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
                    Text(savedMsg!!, color = if (savedMsg!!.startsWith("✓")) OnlineGreen else OfflineRed,
                        fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        if (formPassword != formConfirm) { savedMsg = "✗ Пароли не совпадают"; return@Button }
                        if (formUsername.isBlank() || formPassword.length < 4) { savedMsg = "✗ Заполните поля"; return@Button }
                        vm.createAdmin(formUsername, formPassword)
                        savedMsg = "✓ Сохранено"
                        formUsername = ""; formPassword = ""; formConfirm = ""
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
```

---

### Task 7: Build APK and install

**Files:** None (build only)

- [ ] **Step 7.1: Full build**

```bash
cd "D:/project/H3363T/openwrt-netctrl-android"
./gradlew assembleDebug 2>&1 | tail -30
```

Expected:
```
BUILD SUCCESSFUL in Xs
1 actionable task: 1 executed
```

If there are errors, fix them (likely import issues).

- [ ] **Step 7.2: Check device connected**

```bash
adb devices
```

Expected: device listed as `device` (not `unauthorized`).

- [ ] **Step 7.3: Install APK**

```bash
adb install -r "D:/project/H3363T/openwrt-netctrl-android/app/build/outputs/apk/debug/app-debug.apk"
```

Expected: `Success`

- [ ] **Step 7.4: Manual verification checklist**

After launching the app and logging in as `imbazyx`:

1. **TopBar**: shows "⚡ H3363T NetCtrl • онлайн" and "Выйти" button.
2. **RouterListTab**: shows "РОУТЕРЫ (N)" + "Обновить" + "+ Добавить" sub-header.
3. **AgentCard**: shows inline 📊 / SSH / LuCI / ⚙ / ✗ buttons.
4. **Click 📊** → navigates to Metrics tab with that agent selected.
5. **Click SSH** → opens fullscreen SSH terminal for that agent.
6. **Click LuCI** → opens WebView with router LuCI interface.
7. **Click ✗** → shows confirmation dialog → deletes agent.
8. **Click "+ Добавить"** → opens AddRouter form with all fields.
9. **Click 📍 in AddRouter** → navigates to Map tab, left sidebar shows "Кликни на карту...".
10. **Click on map** → pin appears, navigates back to AddRouter with coordinates filled.
11. **Click "Показать команду установки"** → shows install command in dialog.
12. **Click "Установить агент"** → calls API, shows ✓ or ✗ status.
13. **Карта tab**: shows split-pane (sidebar left, map right).
14. **Админ tab**: shows inline ДОБАВИТЬ / ИЗМЕНИТЬ ПАРОЛЬ form (no popup dialog).
15. **Выйти** button → logs out and returns to Login screen.

---

## Self-Review

**Spec coverage check:**
- ✅ 2.1 Экран входа — LoginScreen unchanged, already matches spec
- ✅ 2.2 TopBar "Выйти" + "онлайн" — Task 2
- ✅ 2.2 Bottom nav 5 tabs text+icons — Task 2
- ✅ 2.3 Router list with agent status/metrics — existing AgentCard
- ✅ 2.3 Inline action buttons (SSH, LuCI, Metrics, Delete) — Task 3
- ✅ 2.3 "Добавить роутер" FAB/button — Task 4
- ✅ 2.3 Add Router form with all fields — Task 4
- ✅ 2.3 "Установить агент" button — Task 4
- ✅ 2.4 Map with Leaflet WebView — existing
- ✅ 2.4 Map "+" pick mode — Task 5
- ✅ 2.4 Split-pane sidebar — Task 5
- ✅ 2.5 Metrics tab with time ranges — existing MetricsTabContent (unchanged)
- ✅ 2.6 SSH terminal tab — existing SshTabContent (unchanged)
- ✅ 2.7 Admin tab inline form — Task 6
- ✅ 2.8 LuCI button in agent card — Task 3 (onLuci callback)
- ✅ 2.9 "Установить агент" command — Task 4 (installAgent + dialog)

**Placeholder scan:** No TBD or TODO found.

**Type consistency:**
- `AgentFull` used consistently throughout
- `vm.setAddRouterField(id=, name=, ...)` — named parameters, consistent
- `vm.cancelMapPick()` added in Task 5 — verify it's added to ViewModel in Step 5.1
- `AgentActionBtn` defined in Task 3, used in Task 3 only ✅
- `MetricRow` defined in Task 3 ✅
