$c = [System.IO.File]::ReadAllText('D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt', [System.Text.Encoding]::UTF8)

$old = @'

    @android.webkit.JavascriptInterface
    fun onAgentClick(agentId: String) {
'@

$new = @'

    @android.webkit.JavascriptInterface
    fun log(msg: String) {
        android.util.Log.d("NetCtrl-MapJS", msg)
    }

    @android.webkit.JavascriptInterface
    fun onAgentClick(agentId: String) {
'@

if ($c.Contains($old)) {
    $c = $c.Replace($old, $new)
    [System.IO.File]::WriteAllText('D:\project\H3363T\openwrt-netctrl-android\app\src\main\java\com\netctrl\app\MainActivity.kt', $c, [System.Text.Encoding]::UTF8)
    Write-Host 'OK - Added log method to MapBridge'
} else {
    Write-Host 'NOT FOUND'
}
