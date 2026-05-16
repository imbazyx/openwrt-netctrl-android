$path = 'D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt'
$lines = [System.IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8)

# Find and replace the loadDataWithBaseURL line
for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match 'loadDataWithBaseURL.*h3363t') {
        # Replace this line and remove the previous val html block
        # First, find the start of the val html block
        $startIdx = -1
        for ($j = $i - 1; $j -ge 0; $j--) {
            if ($lines[$j] -match 'val html = try') {
                $startIdx = $j
                break
            }
        }
        
        if ($startIdx -ge 0) {
            # Remove lines from startIdx to i (inclusive) and replace with loadUrl
            $newLines = @()
            for ($k = 0; $k -lt $startIdx; $k++) {
                $newLines += $lines[$k]
            }
            $newLines += '                     loadUrl("file:///android_asset/map.html")'
            for ($k = $i + 1; $k -lt $lines.Length; $k++) {
                $newLines += $lines[$k]
            }
            [System.IO.File]::WriteAllLines($path, $newLines, [System.Text.Encoding]::UTF8)
            Write-Host "OK - Replaced lines $startIdx to $i with loadUrl"
        } else {
            Write-Host 'Could not find val html start'
        }
        break
    }
}
