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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel

// --- Цвета ---
val BgDark = Color(0xFF0D1117)
val CardBg = Color(0xFF161B22)
val AccentBlue = Color(0xFF238636).copy(red = 0.13f, green = 0.52f, blue = 0.96f)
val AccentGreen = Color(0xFF238636)
val TextPrimary = Color(0xFFE6EDF3)
val TextSecondary = Color(0xFF8B949E)
val OnlineGreen = Color(0xFF3FB950)
val OfflineRed = Color(0xFFF85149)

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
        primary = AccentBlue, onBackground = TextPrimary
    )) {
        when {
            ui.screen is Screen.Login -> LoginScreen(ui, vm::login)
            else -> MainWebScreen(
                serverUrl = ui.serverUrl,
                token = ui.token,
                username = ui.username,
                onLogout = { vm.logout() }
            )
        }
    }
}

// ─── LOGIN ───────────────────────────────────────────────────────────────────

@Composable
fun LoginScreen(ui: UiState, onLogin: (String, String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("admin") }
    var pass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(BgDark), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("NetCtrl", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("OpenWRT Management", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(),
                singleLine = true
            )
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(),
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
                colors = outlinedTextFieldColors(),
                singleLine = true
            )

            if (ui.error != null)
                Text(ui.error, color = OfflineRed, fontSize = 13.sp)

            Button(
                onClick = { onLogin(url, user, pass) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !ui.loading,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                if (ui.loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                else Text("Войти", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── DASHBOARD ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(ui: UiState, onRefresh: () -> Unit, onLogout: () -> Unit, vm: MainViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NetCtrl", fontWeight = FontWeight.Bold, color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg),
                actions = {
                    IconButton(onRefresh) { Icon(Icons.Default.Refresh, null, tint = TextSecondary) }
                    IconButton(onLogout) { Icon(Icons.Default.ExitToApp, null, tint = TextSecondary) }
                }
            )
        },
        containerColor = BgDark
    ) { pad ->
        if (ui.agents.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Нет роутеров", color = TextSecondary)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ui.agents) { agent ->
                    AgentCard(agent, onClick = { vm.selectAgent(agent) })
                }
            }
        }
    }
}

@Composable
fun AgentCard(a: AgentFull, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).background(
                        if (a.online) OnlineGreen else OfflineRed,
                        RoundedCornerShape(50)
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    a.display_name ?: a.agent_id,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 16.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (a.online) "Online" else "Offline",
                    color = if (a.online) OnlineGreen else OfflineRed, fontSize = 12.sp
                )
            }
            if (a.address != null)
                Text(a.address, color = TextSecondary, fontSize = 12.sp)
            if (a.local_ip != null)
                Text(a.local_ip, color = TextSecondary, fontSize = 11.sp)

            if (a.online && a.metric != null) {
                Divider(color = Color(0xFF30363D))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    StatItem("Load", "%.2f".format(a.metric.load1))
                    StatItem("RAM", memPercent(a.metric.mem_free, a.metric.mem_total))
                    StatItem("Temp", a.metric.temperature?.let { "%.0f°".format(it) } ?: "—")
                    StatItem("WiFi", a.metric.wifi_clients?.toString() ?: "—")
                }
                Divider(color = Color(0xFF30363D))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    StatItem("Uptime", formatUptime(a.metric.uptime))
                    StatItem("WAN↓", formatBytes(a.metric.wan_rx))
                    StatItem("WAN↑", formatBytes(a.metric.wan_tx))
                }
            } else if (!a.online) {
                val secs = a.last_seen_secs
                if (secs != null)
                    Text("Был онлайн: ${formatAgo(secs)} назад",
                        color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

fun memPercent(free: Long?, total: Long?): String {
    if (free == null || total == null || total == 0L) return "—"
    val used = (total - free).toDouble() / total * 100
    return "%.0f%%".format(used)
}

fun formatUptime(secs: Double): String {
    val s = secs.toLong()
    return when {
        s < 3600 -> "${s/60}м"
        s < 86400 -> "${s/3600}ч"
        else -> "${s/86400}д"
    }
}

fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "—"
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1048576 -> "${bytes/1024}K"
        bytes < 1073741824 -> "${bytes/1048576}M"
        else -> "%.1fG".format(bytes/1073741824.0)
    }
}

fun formatAgo(secs: Long): String = when {
    secs < 60 -> "${secs}с"
    secs < 3600 -> "${secs/60}м"
    else -> "${secs/3600}ч"
}

@Composable
fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = Color(0xFF30363D),
    focusedLabelColor = AccentBlue,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = AccentBlue
)

// ─── DETAIL SCREEN ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(agent: AgentFull, metrics: List<Metric>, onBack: () -> Unit, onOpenTerminal: (String) -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(agent.display_name ?: agent.agent_id,
                        fontWeight = FontWeight.Bold, color = TextPrimary)
                },
                navigationIcon = {
                    IconButton(onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = TextSecondary)
                    }
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
            // Кнопки действий
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onOpenTerminal("__terminal__") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                        enabled = agent.online
                    ) {
                        Text("SSH терминал", color = TextPrimary, fontSize = 13.sp)
                    }
                    if (agent.luci_url != null) {
                        Button(
                            onClick = { onOpenTerminal("__luci__") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                            enabled = agent.online
                        ) {
                            Text("LuCI", color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Статус карточка
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
                        if (agent.address != null)
                            InfoRow("Адрес", agent.address)
                        if (agent.local_ip != null)
                            InfoRow("IP", agent.local_ip)
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
                // График Load
                item {
                    ChartCard(
                        title = "Load Average",
                        metrics = metrics,
                        valueSelector = { it.load1.toFloat() },
                        color = AccentBlue,
                        unit = ""
                    )
                }
                // График RAM
                item {
                    ChartCard(
                        title = "RAM использование %",
                        metrics = metrics,
                        valueSelector = { m ->
                            val total = m.mem_total ?: 1L
                            if (total == 0L) 0f
                            else ((total - m.mem_free).toFloat() / total * 100f)
                        },
                        color = Color(0xFF7EE787),
                        unit = "%",
                        maxValue = 100f
                    )
                }
                // График температуры
                item {
                    ChartCard(
                        title = "Температура °C",
                        metrics = metrics,
                        valueSelector = { it.temperature ?: 0f },
                        color = Color(0xFFFF7B72),
                        unit = "°"
                    )
                }
            } else if (agent.online) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                }
            }
        }
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
    val min = 0f
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
                val w = size.width
                val h = size.height
                val step = w / (values.size - 1)
                val range = (max - min).coerceAtLeast(0.001f)

                // Линия
                for (i in 0 until values.size - 1) {
                    val x1 = i * step
                    val y1 = h - ((values[i] - min) / range * h)
                    val x2 = (i + 1) * step
                    val y2 = h - ((values[i + 1] - min) / range * h)
                    drawLine(
                        color = color,
                        start = androidx.compose.ui.geometry.Offset(x1, y1),
                        end = androidx.compose.ui.geometry.Offset(x2, y2),
                        strokeWidth = 2.5f
                    )
                }

                // Последняя точка
                val lx = (values.size - 1) * step
                val ly = h - ((values.last() - min) / range * h)
                drawCircle(color = color, radius = 4f,
                    center = androidx.compose.ui.geometry.Offset(lx, ly))
            }
        }
    }
}

// ─── WEB SCREEN (SSH терминал / LuCI) ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(url: String, token: String, username: String, onBack: () -> Unit) {
    val uiUrl = url.substringBefore("/#").substringBefore("/luci-login")
    val isLuci = url.contains("luci-login")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isLuci) "LuCI" else "SSH Терминал",
                        fontWeight = FontWeight.Bold, color = TextPrimary)
                },
                navigationIcon = {
                    IconButton(onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = TextSecondary)
                    }
                },
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
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.setSupportZoom(true)
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView, pageUrl: String) {
                            // Инжектируем токен — если его нет, ставим и перезагружаем
                            val js = """
                                (function() {
                                    if (!localStorage.getItem('owm_token')) {
                                        localStorage.setItem('owm_token', '$token');
                                        localStorage.setItem('owm_user', '$username');
                                        location.reload();
                                    }
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(js, null)
                        }
                    }
                    // Загружаем owm-ui
                    val baseUiUrl = uiUrl.replace(":9000", ":1420")
                    loadUrl(baseUiUrl)
                }
            }
        )
    }
}

// ─── MAIN WEB SCREEN ─────────────────────────────────────────────────────────

@Composable
fun MainWebScreen(serverUrl: String, token: String, username: String, onLogout: () -> Unit) {
    val uiUrl = serverUrl.replace(":9000", ":1420")
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
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.textZoom = 100
                    settings.setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
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
                            val js = """
                                (function() {
                                    if (!localStorage.getItem('owm_token')) {
                                        localStorage.setItem('owm_token', '$token');
                                        localStorage.setItem('owm_user', '$username');
                                        location.reload();
                                    }
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(js, null)
                        }
                    }
                    webChromeClient = android.webkit.WebChromeClient()
                    clearCache(true)
                    clearHistory()
                    webViewRef = this
                    loadUrl(uiUrl)
                }
            }
        )
    }
}
