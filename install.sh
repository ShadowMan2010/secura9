#!/bin/bash
# ══════════════════════════════════════════════════════════════
# SECURA-9 — Raspberry Pi Installer
# Run once after cloning the project:
#   chmod +x install.sh
#   ./install.sh
# ══════════════════════════════════════════════════════════════

set -e
echo ""
echo "══════════════════════════════════════════"
echo "  SECURA-9 Raspberry Pi Setup"
echo "══════════════════════════════════════════"
echo ""

# ── SYSTEM PACKAGES ──────────────────────────────────────────
echo "[1/5] Installing system packages..."
sudo apt update -qq
sudo apt install -y \
    python3-pip \
    python3-dev \
    python3-opencv \
    libatlas-base-dev \
    libhdf5-dev \
    libhdf5-serial-dev \
    cmake \
    libboost-all-dev \
    ffmpeg \
    espeak-ng \
    mpg123 \
    portaudio19-dev \
    fonts-freefont-ttf \
    fonts-noto-extra \
    libraqm0 \
    git

# ── PYTHON PACKAGES ───────────────────────────────────────────
echo ""
echo "[2/5] Installing Python packages..."

pip3 install --upgrade pip

# Core
pip3 install \
    face_recognition \
    opencv-python-headless \
    numpy \
    pillow

# UI
pip3 install pygame

# Audio
pip3 install \
    gTTS \
    pydub \
    SpeechRecognition \
    pyaudio

# Server communication
pip3 install websockets

# GPIO (Raspberry Pi only)
pip3 install RPi.GPIO || echo "RPi.GPIO skipped (not on Pi)"

# ── FONTS (Orbitron for cyberpunk look) ───────────────────────
echo ""
echo "[3/5] Installing Orbitron font..."
FONT_DIR="/usr/share/fonts/truetype/orbitron"
if [ ! -d "$FONT_DIR" ]; then
    sudo mkdir -p "$FONT_DIR"
    # Download from Google Fonts CDN
    wget -q -O /tmp/Orbitron.zip \
        "https://fonts.google.com/download?family=Orbitron" || true
    if [ -f /tmp/Orbitron.zip ]; then
        sudo unzip -q /tmp/Orbitron.zip -d "$FONT_DIR"
        sudo fc-cache -fv > /dev/null
        echo "  Orbitron installed"
    else
        echo "  Could not download Orbitron — using fallback font"
    fi
fi

# ── CREATE FOLDERS ────────────────────────────────────────────
echo ""
echo "[4/5] Creating project folders..."
mkdir -p faces sounds logs ui sounds/_tts_cache

# ── AUTOSTART (optional) ──────────────────────────────────────
echo ""
echo "[5/5] Setting up autostart (optional)..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

read -p "  Set SECURA-9 to start automatically on boot? [y/N] " yn
if [[ "$yn" =~ ^[Yy]$ ]]; then
    SERVICE_FILE="/etc/systemd/system/secura9.service"
    sudo tee "$SERVICE_FILE" > /dev/null <<EOF
[Unit]
Description=SECURA-9 Door Security System
After=network.target graphical.target

[Service]
Type=simple
User=$USER
WorkingDirectory=$SCRIPT_DIR
Environment=DISPLAY=:0
Environment=XAUTHORITY=/home/$USER/.Xauthority
ExecStart=/usr/bin/python3 $SCRIPT_DIR/main.py
Restart=always
RestartSec=5

[Install]
WantedBy=graphical.target
EOF
    sudo systemctl daemon-reload
    sudo systemctl enable secura9
    echo "  Autostart enabled. To start now: sudo systemctl start secura9"
    echo "  To view logs:  sudo journalctl -u secura9 -f"
fi

echo ""
echo "══════════════════════════════════════════"
echo "  Installation complete!"
echo ""
echo "  Next steps:"
echo "  1. Edit config.py — set SERVER_IP to your PC's IP"
echo "  2. Export voice pack from Voice Studio in browser"
echo "  3. Run: python3 convert_voice_pack.py your_voices.json"
echo "  4. Add known face photos to faces/ folder (name.jpg)"
echo "  5. Start bridge server on PC: node server.js"
  echo "  6. Run: python3 main.py"
  echo ""
  echo "  Bengali text:"
  echo "    - Bengali fonts & raqm installed automatically above"
  echo "    - Check logs for 'Bengali shaping' status on startup"
  echo "    - If missing, run: sudo apt install fonts-noto-extra libraqm0"
  echo "══════════════════════════════════════════"
  echo ""
