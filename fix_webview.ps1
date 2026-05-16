$c = [System.IO.File]::ReadAllText('D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt', [System.Text.Encoding]::UTF8)

$old = @'
                    addJavascriptInterface(MapBridge(vm), "AndroidBridge")
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    webViewRef = this
                    loadUrl("file:///android_asset/map.html")
'@

$new = @'
                    addJavascriptInterface(MapBridge(vm), "AndroidBridge")
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(cm: android.webkit.ConsoleMessage): Boolean {
                            android.util.Log.d("NetCtrl-Map", "${cm.message()} -- ${cm.lineNumber()}")
                            return true
                        }
                    }
                    webViewRef = this
                    val html = try {
                        ctx.assets.open("map.html").bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        "<html><body style='background:#050410;color:#C0B0F0;display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif'>Ошибка загрузки карты</body></html>"
                    }
                    loadDataWithBaseURL("https://h3363t.online/", html, "text/html", "UTF-8", null)
'@

if ($c.Contains($old)) {
    $c = $c.Replace($old, $new)
    [System.IO.File]::WriteAllText('D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt', $c, [System.Text.Encoding]::UTF8)
    Write-Host 'OK - Replacement successful'
} else {
    Write-Host 'NOT FOUND - Pattern not found in file'
    # Debug: show what we're looking for
    Write-Host 'Looking for:'
    Write-Host $old
}
