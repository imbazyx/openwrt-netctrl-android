$path = 'D:\project\H3363T\openwrt-netctrl-android\app\src\main\assets\map.html'
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

# Change CSS to use 100% instead of 100vh and add resize handler
$old = "#map { width: 100vw; height: 100vh; }"
$new = "#map { width: 100%; height: 100%; position: absolute; top: 0; left: 0; }"

if ($c.Contains($old)) {
    $c = $c.Replace($old, $new)
    
    # Also add resize handler after map initialization
    $old2 = "console.log('Map initialized, center=' + map.getCenter() + ' zoom=' + map.getZoom());"
    $new2 = @"
console.log('Map initialized, center=' + map.getCenter() + ' zoom=' + map.getZoom());
function resizeMap() {
  var w = window.innerWidth || document.documentElement.clientWidth;
  var h = window.innerHeight || document.documentElement.clientHeight;
  document.getElementById('map').style.width = w + 'px';
  document.getElementById('map').style.height = h + 'px';
  if (map) map.invalidateSize();
}
resizeMap();
window.addEventListener('resize', resizeMap);
setTimeout(resizeMap, 500);
"@
    if ($c.Contains($old2)) {
        $c = $c.Replace($old2, $new2)
    }
    
    [System.IO.File]::WriteAllText($path, $c, [System.Text.Encoding]::UTF8)
    Write-Host 'OK - Fixed map sizing'
} else {
    Write-Host 'NOT FOUND'
}
