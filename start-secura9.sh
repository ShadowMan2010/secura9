#!/usr/bin/env bash
set -e
echo "=== SECURA-9 ==="

# Kill old
pkill -f "python3.*main.py" 2>/dev/null || true
sleep 1

# Start Pi app
python3 /home/shadowman/raspberry-pi/main.py > /tmp/pi.log 2>&1 &
echo "[1/1] Python app started"
echo
echo "Logs: tail -f /tmp/pi.log"
echo "Deck: https://secura9-pi-security-system.web.app"
