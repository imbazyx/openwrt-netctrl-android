$path = 'D:\project\H3363T\openwrt-netctrl-android\app\src\main\assets\map.html'
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

# Add error handling for Leaflet loading
$old = '<script src="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js"></script>'
$new = @"
<script src="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js" onerror="console.log('Leaflet JS load ERROR')"></script>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css" onerror="console.log('Leaflet CSS load ERROR')" />
"@

if ($c.Contains($old)) {
    $c = $c.Replace($old, $new)
    [System.IO.File]::WriteAllText($path, $c, [System.Text.Encoding]::UTF8)
    Write-Host 'OK - Added error handlers for Leaflet'
} else {
    Write-Host 'NOT FOUND'
}
