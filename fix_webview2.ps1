$lines = [System.IO.File]::ReadAllLines('D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt', [System.Text.Encoding]::UTF8)

$newLines = @()
$i = 0
while ($i -lt $lines.Length) {
    if ($i -lt ($lines.Length - 4) -and 
        $lines[$i].Trim() -eq 'addJavascriptInterface(MapBridge(vm), "AndroidBridge")' -and
        $lines[$i+1].Trim() -eq 'webViewClient = android.webkit.WebViewClient()' -and
        $lines[$i+2].Trim() -eq 'webChromeClient = android.webkit.WebChromeClient()' -and
        $lines[$i+3].Trim() -eq 'webViewRef = this' -and
        $lines[$i+4].Trim() -eq 'loadUrl("file:///android_asset/map.html")') {
        
        # Get indentation from current line
        $indent = $lines[$i] -replace '\S.*', ''
        
        $newLines += $lines[$i]  # addJavascriptInterface
        $newLines += $lines[$i+1]  # webViewClient
        $newLines += "$indent webChromeClient = object : android.webkit.WebChromeClient() {"
        $newLines += "$indent     override fun onConsoleMessage(cm: android.webkit.ConsoleMessage): Boolean {"
        $newLines += "$indent         android.util.Log.d(`"NetCtrl-Map`", `"`${cm.message()} -- ${cm.lineNumber()}`"`)"
        $newLines += "$indent         return true"
        $newLines += "$indent     }"
        $newLines += "$indent }"
        $newLines += $lines[$i+3]  # webViewRef
        $newLines += "$indent val html = try {"
        $newLines += "$indent     ctx.assets.open(`"map.html`").bufferedReader().use { it.readText() }"
        $newLines += "$indent } catch (e: Exception) {"
        $newLines += "$indent     `"<html><body style='background:#050410;color:#C0B0F0;display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif'>Ошибка загрузки карты</body></html>`""
        $newLines += "$indent }"
        $newLines += "$indent loadDataWithBaseURL(`"https://h3363t.online/`", html, `"text/html`", `"UTF-8`", null)"
        
        $i += 5  # Skip the 5 original lines
    } else {
        $newLines += $lines[$i]
        $i++
    }
}

[System.IO.File]::WriteAllLines('D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt', $newLines, [System.Text.Encoding]::UTF8)
Write-Host 'OK - Done'
