package com.netctrl.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel

val BgDark = ComposeColor(0xFF0D1117)
val CardBg = ComposeColor(0xFF161B22)
val AccentBlue = ComposeColor(0xFF218BFD)
val TextPrimary = ComposeColor(0xFFE6EDF3)
val TextSecondary = ComposeColor(0xFF8B949E)
val OfflineRed = ComposeColor(0xFFF85149)

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
        background = BgDark, surface = CardBg, primary = AccentBlue
    )) {
        if (ui.screen == Screen.Login) {
            LoginScreen(loading = ui.loading, error = ui.error, onLogin = vm::login)
        } else {
            PanelScreen(
                serverUrl = ui.serverUrl,
                token = ui.token,
                onLogout = vm::logout,
                onTokenChanged = vm::onTokenChanged,
            )
        }
    }
}

// ─── LOGIN ────────────────────────────────────────────────────────────────────

@Composable
private fun LoginScreen(
    loading: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
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

            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("Адрес панели") },
                placeholder = { Text("192.168.1.100:3000") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
                singleLine = true,
                enabled = !loading,
            )
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text("Пароль") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation =
                    if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { passVisible = !passVisible }) {
                        Text(
                            if (passVisible) "Скрыть" else "Показать",
                            color = TextSecondary, fontSize = 12.sp
                        )
                    }
                },
                colors = fieldColors(),
                singleLine = true,
                enabled = !loading,
            )

            if (error != null) Text(error, color = OfflineRed, fontSize = 13.sp)

            Button(
                onClick = { onLogin(url, pass) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                if (loading) CircularProgressIndicator(Modifier.height(20.dp), color = ComposeColor.White)
                else Text("Войти", fontWeight = FontWeight.SemiBold)
            }

            Text("Пароль администратора панели NetCtrl", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = ComposeColor(0xFF30363D),
    focusedLabelColor = AccentBlue,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = AccentBlue,
)

// ─── PANEL (WebView) ──────────────────────────────────────────────────────────

/**
 * Панель отдаёт свой UI сама (server.js + client/index.html), поэтому нативный
 * экран тут только логин. Токен кладём в sessionStorage панели — этого хватает
 * и для её fetch() с заголовком x-auth-token, и для WebSocket SSH.
 *
 * Важно: токен пишется ТОЛЬКО на origin самой панели. Страницы роутеров,
 * открытые через /proxy/:id и /luci-login/:id, идут на другой origin
 * (IP роутера) и токен туда не попадает.
 */
@Composable
private fun PanelScreen(
    serverUrl: String,
    token: String,
    onLogout: () -> Unit,
    onTokenChanged: (String) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val serverHost = remember(serverUrl) { Uri.parse(serverUrl).host ?: "" }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onLogout()
    }

    Box(Modifier.fillMaxSize().background(BgDark)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.parseColor("#0D1117"))
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    webViewClient = PanelClient(serverHost, token, onTokenChanged)
                    webView = this
                    loadUrl(serverUrl)
                }
            },
        )
    }
}

private class PanelClient(
    private val serverHost: String,
    private var token: String,
    private val onTokenChanged: (String) -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
        val url = request.url
        // Панель и её прокси к LuCI живут на origin панели — остаёмся внутри
        if (url.host == serverHost || url.scheme == "about") return false
        // Внешние ссылки (OSM-тайлы, ссылки внутри LuCI) — в системный браузер
        return runCatching {
            view.context.startActivity(
                Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(true)
    }

    override fun onPageFinished(view: WebView, url: String) {
        val uri = Uri.parse(url)
        if ((uri.host ?: "") != serverHost) return          // страница роутера
        // Прокси к LuCI живёт на origin панели, но это чужой код —
        // токен туда не отдаём, иначе роутер сможет его вытащить.
        val path = uri.path.orEmpty()
        if (path.startsWith("/proxy/") || path.startsWith("/luci-login")) return

        // 1) дать панели токен (его же она использует для WebSocket SSH)
        view.evaluateJavascript(
            """
            (function(){
              try {
                if (sessionStorage.getItem('authToken') !== '$token') {
                  sessionStorage.setItem('authToken', '$token');
                  location.reload();
                }
              } catch(e) {}
            })();
            """.trimIndent(),
            null,
        )
        // 2) забрать токен обратно — в панели его можно сменить
        view.evaluateJavascript(
            "sessionStorage.getItem('authToken')",
            ValueCallback { value ->
                val v = value?.trim('"')
                if (!v.isNullOrBlank() && v != token) {
                    token = v
                    onTokenChanged(v)
                }
            },
        )
    }
}