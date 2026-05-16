$c = [System.IO.File]::ReadAllText('D:\project\H3363T\openwrt-netctrl-android\app\src\main\assets\map.html', [System.Text.Encoding]::UTF8)

$old = @'
  };
}
function showMapError(msg) {
'@

$new = @'
  };
}
console.log('Map HTML loaded, AndroidBridge=' + (window.AndroidBridge ? 'OK' : 'MISSING'));
function showMapError(msg) {
'@

if ($c.Contains($old)) {
    $c = $c.Replace($old, $new)
    [System.IO.File]::WriteAllText('D:\project\H3363T\openwrt-netctrl-android\app\src\main\assets\map.html', $c, [System.Text.Encoding]::UTF8)
    Write-Host 'OK - Added console.log test'
} else {
    Write-Host 'NOT FOUND'
}
