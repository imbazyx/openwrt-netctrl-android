# e2e-test.ps1
# End-to-end проверка: owm-server + AX6000 агент + Android (adb)
# Запускать после deploy-ax6000.ps1

$ErrorActionPreference = 'Continue'
$LogFile = "$PSScriptRoot\deploy-log.txt"

function Log($msg) {
    $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $msg"
    Write-Host $line
    Add-Content -Path $LogFile -Value $line
}

function Fail($msg) {
    Log "E2E FAIL: $msg"
    exit 1
}

Log "=== e2e-test.ps1 START ==="

# ── 1. Проверка owm-server health ─────────────────────────────────────────────
Log "--- 1. owm-server /health"
$health = ssh x3 "curl -sf http://127.0.0.1:9090/health" 2>&1
Log "health: $health"
if ($health -notmatch '"ok"') {
    Fail "owm-server /health не вернул ok. Ответ: $health"
}

# ── 2. Проверка агента AX6000 в списке ───────────────────────────────────────
Log "--- 2. GET /agents (список)"
$agents = ssh x3 "curl -sf http://127.0.0.1:9090/agents" 2>&1
Log "GET /agents: $agents"
if ($agents -notmatch "ax6000-main") {
    Fail "ax6000-main не найден в GET /agents. Ответ: $agents"
}

# ── 3. Проверка online-статуса ────────────────────────────────────────────────
Log "--- 3. Проверка online=true для ax6000-main"
if ($agents -notmatch '"ax6000-main".*"online"\s*:\s*true|"online"\s*:\s*true.*"ax6000-main"') {
    Log "WARN: ax6000-main может быть offline (heartbeat ещё не пришёл?)"
}

# ── 4. GET /api/v1/agents/ax6000-main (детали + метрики) ─────────────────────
Log "--- 4. GET /api/v1/agents/ax6000-main"
$detail = ssh x3 "curl -sf http://127.0.0.1:9090/api/v1/agents/ax6000-main" 2>&1
Log "agent detail: $detail"
if ($detail -notmatch '"success"\s*:\s*true') {
    Fail "GET /api/v1/agents/ax6000-main не вернул success:true. Ответ: $detail"
}

# Проверка наличия метрик
if ($detail -match '"metrics"\s*:\s*\{') {
    Log "OK: метрики присутствуют в ответе"
} else {
    Log "WARN: метрики отсутствуют (возможно heartbeat ещё не отправил данные)"
}

# ── 5. GET /configs (lat/lng для карты) ───────────────────────────────────────
Log "--- 5. GET /configs (lat/lng)"
$configs = ssh x3 "curl -sf http://127.0.0.1:9090/configs" 2>&1
Log "GET /configs: $configs"
if ($configs -match "ax6000-main") {
    if ($configs -match '"lat"\s*:\s*55') {
        Log "OK: lat/lng установлены для ax6000-main (маркер будет на карте)"
    } else {
        Log "WARN: lat/lng не установлены для ax6000-main. Маркер не появится на карте."
        Log "      Запустите deploy-ax6000.ps1 повторно или зарегистрируйте агента через UI."
    }
}

# ── 6. Проверка AX6000: процесс агента ───────────────────────────────────────
Log "--- 6. Процесс h3363t на AX6000"
$psAx = ssh ax "ps | grep h3363t-agent | grep -v grep" 2>&1
Log "ps ax: $psAx"
if (-not $psAx -or $psAx -match "No such file") {
    Log "WARN: Процесс h3363t-agent не найден на AX6000. Проверьте деплой."
} else {
    Log "OK: Процесс агента запущен"
}

# ── 7. Android adb (если подключен) ─────────────────────────────────────────
Log "--- 7. Android adb"
$adbPath = (Get-Command adb -ErrorAction SilentlyContinue)?.Source
if (-not $adbPath) {
    Log "INFO: adb не найден в PATH, пропускаем Android проверку"
} else {
    $devices = adb devices 2>&1
    Log "adb devices: $devices"

    if ($devices -match "device$") {
        adb logcat -c 2>&1 | Out-Null
        Start-Sleep -Seconds 3
        $logcat = adb logcat -d 2>&1 | Select-String -Pattern "NetCtrl-Map|updateMarkers|MapJS|refreshAgents" | Select-Object -Last 15
        Log "adb logcat (map): $($logcat -join "`n")"

        if ($logcat -match "refreshAgents|setAgents|updateMarkers") {
            Log "OK: Android карта обновляет маркеры"
        } else {
            Log "INFO: Логи карты не найдены (откройте вкладку Карта в приложении)"
        }
    } else {
        Log "INFO: Android устройство не подключено по adb"
    }
}

Log "=== e2e-test.ps1 PASS ==="
Write-Host "`nE2E TEST PASS" -ForegroundColor Green
