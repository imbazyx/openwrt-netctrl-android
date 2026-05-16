$c = [System.IO.File]::ReadAllLines('D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt')
for ($i = 0; $i -lt $c.Length; $i++) {
    if ($c[$i] -match 'loadUrl.*map\.html') {
        Write-Host "Line $($i+1): [$($c[$i])]"
        $start = [Math]::Max(0, $i - 5)
        $end = [Math]::Min($c.Length - 1, $i + 2)
        for ($j = $start; $j -le $end; $j++) {
            $lineNum = $j + 1
            Write-Host "  Line $lineNum : [$($c[$j])]"
        }
        break
    }
}
