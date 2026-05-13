# deploy-ax6000.ps1
# Деплой агента h3363t на AX6000 (OpenWrt) через scp/ssh
# Все файлы создаются без BOM через [System.IO.File]::WriteAllText()

$ErrorActionPreference = 'Continue'
$LogFile = "$PSScriptRoot\deploy-log.txt"
$Server  = "http://95.174.102.25:9090"
$AgentId = "ax6000-main"

function Log($msg) {
    $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $msg"
    Write-Host $line
    Add-Content -Path $LogFile -Value $line
}

Log "=== deploy-ax6000.ps1 START ==="

# ── Шаг А1. Диагностика AX6000 ───────────────────────────────────────────────
Log ">>> A1: Диагностика AX6000"
$diag = ssh ax "which curl; cat /etc/openwrt_release | head -4; uname -m; df -h /tmp; ps | grep h3363t" 2>&1
Log "AX6000 diag:`n$diag"

if ($diag -notmatch "curl") {
    Log "WARN: curl не найден, добавляем установку в скрипт"
    $installCurl = "opkg update && opkg install curl; "
} else {
    $installCurl = ""
}

# ── Шаг А2. Создание файлов без BOM ─────────────────────────────────────────
Log ">>> A2: Создание файлов агента"
$TempAgent = "$env:TEMP\h3363t-agent.sh"
$TempInitd = "$env:TEMP\h3363t"

$agentScript = @'
#!/bin/sh
# h3363t-agent.sh - heartbeat loop for owm-server
SERVER="http://95.174.102.25:9090"
AGENT_ID="ax6000-main"
WAN_IFACE="${WAN_IFACE:-eth1}"

send_heartbeat() {
    CPU_LOAD=$(awk 'NR==1{print $1}' /proc/loadavg 2>/dev/null || echo 0)

    RAM_TOTAL_KB=$(awk '/MemTotal/{print $2}' /proc/meminfo 2>/dev/null || echo 0)
    RAM_AVAIL_KB=$(awk '/MemAvailable/{print $2}' /proc/meminfo 2>/dev/null || echo 0)
    RAM_TOTAL_MB=$((RAM_TOTAL_KB / 1024))
    RAM_USED_MB=$(( (RAM_TOTAL_KB - RAM_AVAIL_KB) / 1024 ))

    UPTIME=$(awk '{printf "%d", $1}' /proc/uptime 2>/dev/null || echo 0)

    LOCAL_IP=$(ip route get 1.1.1.1 2>/dev/null | sed -n 's/.*src \([0-9][0-9.]*\).*/\1/p' | head -1)

    WIFI_CLIENTS=0
    if command -v iw >/dev/null 2>&1; then
        WIFI_CLIENTS=$(iw dev 2>/dev/null | awk '/station/{c++} END{print c+0}')
    fi

    WAN_RX=0; WAN_TX=0
    if [ -r /proc/net/dev ]; then
        WAN_LINE=$(awk -v iface="$WAN_IFACE" '$0~iface":"{print}' /proc/net/dev 2>/dev/null | head -1)
        if [ -n "$WAN_LINE" ]; then
            WAN_RX=$(echo "$WAN_LINE" | awk -F'[: ]+' '{for(i=1;i<=NF;i++) if($i~/^[1-9][0-9]*$/){print $i; exit}}')
            WAN_TX=$(echo "$WAN_LINE" | awk -F'[: ]+' '{n=0; for(i=1;i<=NF;i++) if($i~/^[1-9][0-9]*$/){n++; if(n==9){print $i; exit}}}')
        fi
    fi
    WAN_RX=${WAN_RX:-0}; WAN_TX=${WAN_TX:-0}

    PAYLOAD="{\"agent_id\":\"${AGENT_ID}\",\"local_ip\":\"${LOCAL_IP}\",\"cpu_load\":${CPU_LOAD},\"ram_usage\":${RAM_USED_MB},\"ram_total\":${RAM_TOTAL_MB},\"uptime\":${UPTIME},\"wifi_clients\":${WIFI_CLIENTS},\"wan_rx_bytes\":${WAN_RX},\"wan_tx_bytes\":${WAN_TX}}"

    curl -sf -X POST "${SERVER}/api/v1/agents/heartbeat" \
        -H "Content-Type: application/json" \
        -d "$PAYLOAD" > /dev/null 2>&1 || true
}

# Начальный heartbeat (регистрирует агента в БД если его нет)
send_heartbeat
echo "[h3363t] Initial heartbeat sent for ${AGENT_ID}"

# Бесконечный цикл
while true; do
    sleep 30
    send_heartbeat
done
'@

$initdScript = @'
#!/bin/sh /etc/rc.common
START=99
USE_PROCD=1

start_service() {
    procd_open_instance
    procd_set_param command /usr/bin/h3363t-agent.sh
    procd_set_param respawn
    procd_set_param stdout 1
    procd_set_param stderr 1
    procd_close_instance
}

stop_service() {
    killall h3363t-agent.sh 2>/dev/null || true
}
'@

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($TempAgent, $agentScript, $utf8NoBom)
[System.IO.File]::WriteAllText($TempInitd, $initdScript, $utf8NoBom)
Log "Файлы созданы: $TempAgent, $TempInitd"

# ── Шаг А3. SCP + SSH деплой ─────────────────────────────────────────────────
Log ">>> A3: Деплой на AX6000"
$scpAgent = scp "$TempAgent" "ax:/tmp/h3363t-agent.sh" 2>&1
Log "scp agent: $scpAgent"

$scpInitd = scp "$TempInitd" "ax:/tmp/h3363t" 2>&1
Log "scp initd: $scpInitd"

$installCmd = "${installCurl}mv /tmp/h3363t-agent.sh /usr/bin/h3363t-agent.sh && chmod +x /usr/bin/h3363t-agent.sh && mv /tmp/h3363t /etc/init.d/h3363t && chmod +x /etc/init.d/h3363t && /etc/init.d/h3363t enable && /etc/init.d/h3363t restart && echo 'DEPLOY_OK'"
$deployResult = ssh ax $installCmd 2>&1
Log "deploy: $deployResult"

if ($deployResult -notmatch "DEPLOY_OK") {
    Log "ERROR: Деплой не прошёл. Вывод: $deployResult"
    Log "=== deploy-ax6000.ps1 FAIL (deploy) ==="
    exit 1
}

# ── JWT-регистрация агента с координатами ────────────────────────────────────
Log ">>> JWT: Регистрация ax6000-main с lat/lng через owm-server API"

$loginJson = '{"username":"imbazyx","password":"Damianidi1986God!"}'
$loginResp = ssh x3 "curl -sf -X POST http://127.0.0.1:9090/auth/login -H 'Content-Type: application/json' -d '$loginJson'" 2>&1
Log "login resp: $loginResp"

# Извлечь токен из {"access_token":"..."}
if ($loginResp -match '"access_token"\s*:\s*"([^"]+)"') {
    $token = $Matches[1]
    Log "JWT token получен (${token.Length} chars)"

    $regJson = '{"agent_id":"ax6000-main","display_name":"Redmi AX6000","lat":55.7558,"lng":37.6173,"description":"OpenWrt AX6000 router","address":"Moscow, Russia"}'
    $regResp = ssh x3 "curl -sf -X POST http://127.0.0.1:9090/api/v1/agents -H 'Content-Type: application/json' -H 'Authorization: Bearer $token' -d '$regJson'" 2>&1
    Log "agent register: $regResp"
} else {
    Log "WARN: Не удалось получить JWT токен. lat/lng не будут установлены. Ответ: $loginResp"
    Log "INFO: Агент всё равно появится в /agents после heartbeat, но без координат (нет маркера на карте)"
}

# ── Шаг А4. Верификация через 35 секунд ──────────────────────────────────────
Log ">>> A4: Ожидание 35 сек для первого heartbeat..."
Start-Sleep -Seconds 35

Log ">>> A4: Проверка процесса на AX6000"
$psCheck = ssh ax "ps | grep h3363t | grep -v grep" 2>&1
Log "ps h3363t: $psCheck"

Log ">>> A4: Проверка агента в owm-server"
$agentsResp = ssh x3 "curl -s http://127.0.0.1:9090/agents" 2>&1
Log "GET /agents: $agentsResp"

if ($agentsResp -notmatch "ax6000-main") {
    Log "ERROR: ax6000-main не найден в /agents. Проверяем логи..."
    $logRead = ssh ax "logread 2>/dev/null | tail -10 || cat /tmp/owm-agent.pid 2>/dev/null" 2>&1
    Log "AX6000 logs: $logRead"
    # Проверяем owm-server логи
    $owmLog = ssh x3 "tail -20 /mnt/hdd/logs/owm.log 2>/dev/null || journalctl -u owm-server --no-pager -n 20 2>/dev/null || echo 'log not found'" 2>&1
    Log "owm-server logs: $owmLog"
    Log "=== deploy-ax6000.ps1 FAIL (agent not visible) ==="
    exit 1
}

Log ">>> A4: Проверка GET /api/v1/agents/ax6000-main"
$agentDetail = ssh x3 "curl -s http://127.0.0.1:9090/api/v1/agents/ax6000-main" 2>&1
Log "GET /api/v1/agents/ax6000-main: $agentDetail"

if ($agentDetail -notmatch '"success"\s*:\s*true') {
    Log "WARN: Детальный запрос агента не вернул success:true"
}

Log "=== deploy-ax6000.ps1 SUCCESS ==="
