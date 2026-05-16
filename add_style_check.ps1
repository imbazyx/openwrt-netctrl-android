$path = 'D:\project\H3363T\openwrt-netctrl-android\app\src\main\assets\map.html'
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

# Add check for Leaflet CSS after map initialization
$old = "console.log('Map initialized, center=' + map.getCenter() + ' zoom=' + map.getZoom());"
$new = @"
console.log('Map initialized, center=' + map.getCenter() + ' zoom=' + map.getZoom());
var computedStyle = window.getComputedStyle(document.getElementById('map'));
console.log('Map bg=' + computedStyle.backgroundColor + ' width=' + computedStyle.width + ' height=' + computedStyle.height);
setTimeout(function() {
  var tiles = document.querySelectorAll('.leaflet-tile');
  console.log('Tiles loaded count=' + tiles.length);
}, 2000);
"@

if ($c.Contains($old)) {
    $c = $c.Replace($old, $new)
    [System.IO.File]::WriteAllText($path, $c, [System.Text.Encoding]::UTF8)
    Write-Host 'OK - Added style and tile checks'
} else {
    Write-Host 'NOT FOUND'
}
