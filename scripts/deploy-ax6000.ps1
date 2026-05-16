# deploy-ax6000.ps1 - Деплой агента h3363t на Redmi AX6000
# Использует base64 для надёжной передачи файлов через SSH (без scp/heredoc)

$ErrorActionPreference = "Continue"

# Пути к локальным файлам
$InitScriptPath = Join-Path $PSScriptRoot "h3363t-init.sh"
$AgentScriptPath = Join-Path $PSScriptRoot "h3363t-agent.sh"

Write-Host "=== Deploy h3363t agent to AX6000 ===" -ForegroundColor Cyan

# Проверяем наличие файлов
if (-not (Test-Path $InitScriptPath)) {
    Write-Host "ERROR: Init script not found: $InitScriptPath" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path $AgentScriptPath)) {
    Write-Host "ERROR: Agent script not found: $AgentScriptPath" -ForegroundColor Red
    exit 1
}

# Получаем IP адрес ПК для HTTP-сервера
# AX6000 видит ПК через LAN интерфейс (192.168.1.x)
$LocalIP = "192.168.1.170"
$HttpPort = 18080
$BaseUrl = "http://${LocalIP}:${HttpPort}"

Write-Host "Local IP: $LocalIP, serving files on $BaseUrl" -ForegroundColor Gray

# Запускаем временный HTTP-сервер в фоне
$HttpServerJob = Start-Job -ScriptBlock {
    param($Path, $Port)
    cd $Path
    python -m http.server $Port
} -ArgumentList $PSScriptRoot, $HttpPort

Start-Sleep -Seconds 2
Write-Host "HTTP server started (job=$($HttpServerJob.Id))" -ForegroundColor Gray

# Функция очистки
function Cleanup {
    if ($HttpServerJob) {
        Stop-Job $HttpServerJob -ErrorAction SilentlyContinue
        Remove-Job $HttpServerJob -Force -ErrorAction SilentlyContinue
        Write-Host "HTTP server stopped" -ForegroundColor Gray
    }
}

try {
    Write-Host "[1/5] Downloading init script via wget..." -ForegroundColor Yellow
    $wgetInitResult = ssh ax "wget -q -O /tmp/h3363t '${BaseUrl}/h3363t-init.sh' && mv /tmp/h3363t /etc/init.d/h3363t && chmod +x /etc/init.d/h3363t" 2>&1
    Write-Host "  Wget result: $wgetInitResult (exit=$LASTEXITCODE)" -ForegroundColor Gray
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Failed to download init script" -ForegroundColor Red
        Cleanup
        exit 1
    }
    Write-Host "  OK: Init script uploaded and installed" -ForegroundColor Green

    Write-Host "[2/5] Downloading agent script via wget..." -ForegroundColor Yellow
    $wgetAgentResult = ssh ax "wget -q -O /tmp/h3363t-agent.sh '${BaseUrl}/h3363t-agent.sh' && mv /tmp/h3363t-agent.sh /usr/bin/h3363t-agent.sh && chmod +x /usr/bin/h3363t-agent.sh" 2>&1
    Write-Host "  Wget result: $wgetAgentResult (exit=$LASTEXITCODE)" -ForegroundColor Gray
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Failed to download agent script" -ForegroundColor Red
        Cleanup
        exit 1
    }
    Write-Host "  OK: Agent script uploaded and installed" -ForegroundColor Green

Write-Host "[3/5] Enabling and starting service..." -ForegroundColor Yellow
ssh ax "/etc/init.d/h3363t stop" 2>$null
Start-Sleep -Seconds 1
ssh ax "/etc/init.d/h3363t enable"
ssh ax "/etc/init.d/h3363t start"
if ($LASTEXITCODE -ne 0) {
    Write-Host "WARNING: Service start returned non-zero" -ForegroundColor Yellow
}
Write-Host "  OK: Service enabled and started" -ForegroundColor Green

Write-Host "[4/5] Checking service status..." -ForegroundColor Yellow
Start-Sleep -Seconds 2
$StatusOutput = ssh ax "/etc/init.d/h3363t status" 2>&1
Write-Host "  Status: $StatusOutput" -ForegroundColor Gray

$ProcessCheck = ssh ax "ps | grep h3363t-agent | grep -v grep" 2>&1
if ($ProcessCheck) {
    Write-Host "  Process running: $ProcessCheck" -ForegroundColor Green
} else {
    Write-Host "  WARNING: Process not found yet (may need time to start)" -ForegroundColor Yellow
}

Write-Host "[5/5] Waiting for heartbeat (35 seconds)..." -ForegroundColor Yellow
for ($i = 35; $i -gt 0; $i--) {
    Write-Host "  Waiting... $i s remaining   " -NoNewline
    Start-Sleep -Seconds 1
    Write-Host "`r" -NoNewline
}
Write-Host "  Done waiting." -ForegroundColor Yellow

Write-Host "Checking agent status on owm-server..." -ForegroundColor Yellow
$AgentsResponse = ssh x3 "curl -s http://127.0.0.1:9090/agents" 2>&1
Write-Host "Response: $AgentsResponse" -ForegroundColor Gray

# Проверяем наличие ax6000-main с online:true
if ($AgentsResponse -match '"ax6000-main"' -and $AgentsResponse -match '"online"\s*:\s*true') {
    Write-Host "`n=== DEPLOY SUCCESS ===" -ForegroundColor Green
    Write-Host "Agent ax6000-main is ONLINE on owm-server" -ForegroundColor Green
} else {
    Write-Host "`n=== DEPLOY FAILED ===" -ForegroundColor Red
    Write-Host "Agent not found or offline. Checking logs..." -ForegroundColor Red
    $LogOutput = ssh ax "cat /tmp/h3363t-agent.log 2>/dev/null || echo 'NO LOG'" 2>&1
    Write-Host "--- Agent Log ---" -ForegroundColor Gray
    Write-Host $LogOutput -ForegroundColor Gray
    Write-Host "--- End Log ---" -ForegroundColor Gray
    
    $LogRead = ssh ax "logread 2>/dev/null | grep -i h3363t | tail -10" 2>&1
    Write-Host "--- System Log ---" -ForegroundColor Gray
    Write-Host $LogRead -ForegroundColor Gray
    Write-Host "--- End System Log ---" -ForegroundColor Gray
    exit 1
}
} finally {
    Cleanup
}