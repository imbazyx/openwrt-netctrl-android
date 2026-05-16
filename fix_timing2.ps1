$c = [System.IO.File]::ReadAllText('D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt', [System.Text.Encoding]::UTF8)

# Fix 1: refreshAgents with delay and try-catch
$c = $c -replace 'webViewRef\?\.evaluateJavascript\("refreshAgents\(\);", null\)', 
    "android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ webViewRef?.evaluateJavascript(`"try { refreshAgents(); } catch(e) { console.log('err: ' + e.message); }`", null) }, 1500)"

# Fix 2: enterPickMode with delay
$c = $c -replace 'webViewRef\?\.evaluateJavascript\("enterPickMode\(\);", null\)',
    "android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ webViewRef?.evaluateJavascript(`"try { enterPickMode(); } catch(e) { console.log('err: ' + e.message); }`", null) }, 500)"

# Fix 3: exitPickMode with delay
$c = $c -replace 'webViewRef\?\.evaluateJavascript\("exitPickMode\(\);", null\)',
    "android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ webViewRef?.evaluateJavascript(`"try { exitPickMode(); } catch(e) { console.log('err: ' + e.message); }`", null) }, 500)"

[System.IO.File]::WriteAllText('D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt', $c, [System.Text.Encoding]::UTF8)
Write-Host 'OK'
