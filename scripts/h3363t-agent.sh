#!/bin/sh
# h3363t-agent.sh - heartbeat loop for owm-server
SERVER="http://95.174.102.25:9090"
AGENT_ID="ax6000-main"
LOG="/tmp/h3363t-agent.log"

echo "$(date): agent starting (PID $$)" >> "$LOG"

send_heartbeat() {
    CPU_LOAD=$(awk 'NR==1{print $1}' /proc/loadavg 2>/dev/null || echo "0.0")
    RAM_TOTAL_KB=$(awk '/MemTotal/{print $2}' /proc/meminfo 2>/dev/null || echo "0")
    RAM_AVAIL_KB=$(awk '/MemAvailable/{print $2}' /proc/meminfo 2>/dev/null || echo "0")
    RAM_TOTAL_MB=$((RAM_TOTAL_KB / 1024))
    RAM_USED_MB=$(( (RAM_TOTAL_KB - RAM_AVAIL_KB) / 1024 ))
    UPTIME=$(awk '{printf "%d", $1}' /proc/uptime 2>/dev/null || echo "0")

    PAYLOAD="{\"agent_id\":\"${AGENT_ID}\",\"agent_name\":\"Redmi AX6000\",\"cpu_load\":${CPU_LOAD},\"ram_usage\":${RAM_USED_MB},\"ram_total\":${RAM_TOTAL_MB},\"uptime\":${UPTIME}}"

    HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" -X POST "${SERVER}/api/v1/agents/heartbeat" \
        -H "Content-Type: application/json" \
        -d "$PAYLOAD" 2>/dev/null || echo "000")

    echo "$(date): heartbeat HTTP ${HTTP_CODE} payload=${PAYLOAD}" >> "$LOG"
}

# Начальный heartbeat
send_heartbeat

# Бесконечный цикл
while true; do
    sleep 30
    send_heartbeat
done
