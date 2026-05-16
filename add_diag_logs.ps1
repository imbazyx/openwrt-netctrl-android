$path = 'D:\project\H3363T\openwrt-netctrl-android\app\src\main\assets\map.html'
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

$old = "console.log('Map HTML loaded, AndroidBridge=' + (window.AndroidBridge ? 'OK' : 'MISSING'));"
$new = @"
console.log('Map HTML loaded, AndroidBridge=' + (window.AndroidBridge ? 'OK' : 'MISSING'));
console.log('L (Leaflet) = ' + (typeof L !== 'undefined' ? 'OK' : 'UNDEFINED'));
console.log('map div = ' + (document.getElementById('map') ? 'OK' : 'NULL'));
"@

if ($c.Contains($old)) {
    $c = $c.Replace($old, $new)
    [System.IO.File]::WriteAllText($path, $c, [System.Text.Encoding]::UTF8)
    Write-Host 'OK - Added diagnostic logs'
} else {
    Write-Host 'NOT FOUND'
}
