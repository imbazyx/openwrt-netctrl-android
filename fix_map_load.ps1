$path = 'D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt'
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

$old = @"
                     val html = try {
                         ctx.assets.open("map.html").bufferedReader().use { it.readText() }
                     } catch (e: Exception) {
                         "<html><body style='background:#050410;color:#C0B0F0;display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif'>Ошибка загрузки карты</body></html>"
                     }
                     loadDataWithBaseURL("https://h3363t.online/", html, "text/html", "UTF-8", null)
"@

$new = '                     loadUrl("file:///android_asset/map.html")'

if ($c.Contains($old)) {
    $c = $c.Replace($old, $new)
    [System.IO.File]::WriteAllText($path, $c, [System.Text.Encoding]::UTF8)
    Write-Host 'OK - Changed to loadUrl'
} else {
    Write-Host 'NOT FOUND'
}
