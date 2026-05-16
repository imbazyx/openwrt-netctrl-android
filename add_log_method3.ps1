$lines = [System.IO.File]::ReadAllLines('D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt', [System.Text.Encoding]::UTF8)

$newLines = @()
$i = 0
$inserted = $false
while ($i -lt $lines.Length) {
    $newLines += $lines[$i]
    
    # Look for the closing brace of getAgentsJson function (line with just "    }" after put statements)
    if (-not $inserted -and $i -gt 860 -and $i -lt 880 -and $lines[$i].Trim() -eq '}' -and $lines[$i-1] -match 'JSONObject\.NULL') {
        # Insert log method after this closing brace
        $newLines += ''
        $newLines += '    @android.webkit.JavascriptInterface'
        $newLines += '    fun log(msg: String) {'
        $newLines += '        android.util.Log.d("NetCtrl-MapJS", msg)'
        $newLines += '    }'
        $inserted = $true
    }
    
    $i++
}

[System.IO.File]::WriteAllLines('D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt', $newLines, [System.Text.Encoding]::UTF8)
if ($inserted) { Write-Host 'OK - Added log method' } else { Write-Host 'NOT FOUND' }
