$c = [System.IO.File]::ReadAllText('D:\project\H3363T\openwrt-netctrl-android\app\src\main\assets\map.html', [System.Text.Encoding]::UTF8)

# Add more diagnostic logs
$c = $c -replace "console.log\('Map HTML loaded, AndroidBridge=' \+ \(window.AndroidBridge ? 'OK' : 'MISSING'\)\);",
    "console.log('Map HTML loaded, AndroidBridge=' + (window.AndroidBridge ? 'OK' : 'MISSING'));" + "`n" +
    "console.log('L (Leaflet) = ' + (typeof L !== 'undefined' ? 'OK' : 'UNDEFINED'));" + "`n" +
    "console.log('document.getElementById(map) = ' + (document.getElementById('map') ? 'OK' : 'NULL'));"

[System.IO.File]::WriteAllText('D:\project\H3363T\openwrt-netctrl-android\app\src\main\assets\map.html', $c, [System.Text.Encoding]::UTF8)
Write-Host 'OK'
