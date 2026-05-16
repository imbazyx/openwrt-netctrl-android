$lines = [System.IO.File]::ReadAllLines('D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt', [System.Text.Encoding]::UTF8)

$newLines = @()
$i = 0
while ($i -lt $lines.Length) {
    $line = $lines[$i]
    
    # Replace refreshAgents evaluateJavascript with delayed version
    if ($line -match 'webViewRef\?\.evaluateJavascript\("refreshAgents\(\);", null\)') {
        $indent = $line -replace '\S.*', ''
        $newLines += "${indent}android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({"
        $newLines += "${indent}    webViewRef?.evaluateJavascript(\"try { refreshAgents(); } catch(e) { console.log('refreshAgents error: ' + e.message); }\", null)"
        $newLines += "${indent}}, 1500)"
        $i++
        continue
    }
    
    # Replace enterPickMode evaluateJavascript
    if ($line -match 'webViewRef\?\.evaluateJavascript\("enterPickMode\(\);", null\)') {
        $indent = $line -replace '\S.*', ''
        $newLines += "${indent}android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({"
        $newLines += "${indent}    webViewRef?.evaluateJavascript(\"try { enterPickMode(); } catch(e) { console.log('enterPickMode error: ' + e.message); }\", null)"
        $newLines += "${indent}}, 500)"
        $i++
        continue
    }
    
    # Replace exitPickMode evaluateJavascript
    if ($line -match 'webViewRef\?\.evaluateJavascript\("exitPickMode\(\);", null\)') {
        $indent = $line -replace '\S.*', ''
        $newLines += "${indent}android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({"
        $newLines += "${indent}    webViewRef?.evaluateJavascript(\"try { exitPickMode(); } catch(e) { console.log('exitPickMode error: ' + e.message); }\", null)"
        $newLines += "${indent}}, 500)"
        $i++
        continue
    }
    
    $newLines += $line
    $i++
}

[System.IO.File]::WriteAllLines('D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt', $newLines, [System.Text.Encoding]::UTF8)
Write-Host 'OK - Done'
