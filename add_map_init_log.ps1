$path = 'D:\project\H3363T\openwrt-netctrl-android\app\src\main\assets\map.html'
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

# Add log after map initialization
$old = "  tiles.addTo(map);"
$new = @"
  tiles.addTo(map);
  console.log('Map initialized, center=' + map.getCenter() + ' zoom=' + map.getZoom());
"@

if ($c.Contains($old)) {
    $c = $c.Replace($old, $new)
    [System.IO.File]::WriteAllText($path, $c, [System.Text.Encoding]::UTF8)
    Write-Host 'OK - Added map init log'
} else {
    Write-Host 'NOT FOUND'
}
