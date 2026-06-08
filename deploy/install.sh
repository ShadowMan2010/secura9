#!/usr/bin/env bash
set -euo pipefail

# ═══════════════════════════════════════════════════════════════════════════
# SECURA-9 Installer
# Installs all dependencies and configures the device as a SECURA-9 node.
# Target: Raspberry Pi OS Lite (Bookworm) or Ubuntu Server 22.04+
# ═══════════════════════════════════════════════════════════════════════════

BOLD='\033[1m'
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()      { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()     { echo -e "${RED}[ERR]${NC}   $*" >&2; }
header()  { echo -e "\n${BOLD}── $* ──${NC}\n"; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
INSTALL_DIR="/opt/secura9"
VENV_DIR="$INSTALL_DIR/venv"
SYS_USER="secura9"
SYS_GROUP="secura9"
SERVICE_DIR="$SCRIPT_DIR/systemd"

# ── Pre-flight checks ──────────────────────────────────────────────────────

header "Pre-flight checks"

if [[ $EUID -ne 0 ]]; then
    err "This script must be run as root (sudo)."
    exit 1
fi

if [[ -d "$INSTALL_DIR" ]]; then
    warn "$INSTALL_DIR already exists — will overwrite files"
fi

# Detect platform
ARCH=$(uname -m)
case "$ARCH" in
    aarch64|armv7l|armv6l)
        PLATFORM="raspberry-pi"
        ;;
    x86_64)
        PLATFORM="x86_64"
        ;;
    *)
        warn "Unknown architecture: $ARCH — attempting generic install"
        PLATFORM="generic"
        ;;
esac
info "Platform: $PLATFORM ($ARCH)"

OS_ID=$(grep -oP '(?<=^ID=).+' /etc/os-release 2>/dev/null || echo "unknown")
info "OS: $OS_ID"

# ── System dependencies ────────────────────────────────────────────────────

header "Installing system dependencies"

APT_PKGS=(
    python3
    python3-pip
    python3-venv
    python3-dev
    git
    build-essential
    cmake
    libatlas-base-dev
    libhdf5-dev
    libhdf5-serial-dev
    libilm-base-dev
    libopenexr-dev
    libgstreamer1.0-dev
    libgstreamer-plugins-base1.0-dev
    libavcodec-dev
    libavformat-dev
    libswscale-dev
    libv4l-dev
    libxvidcore-dev
    libx264-dev
    libjpeg-dev
    libpng-dev
    libtiff-dev
    gfortran
    openexr
    libgtk-3-dev
    libcanberra-gtk3-module
    libatlas-base-dev
    libssl-dev
    libffi-dev
    portaudio19-dev
    python3-pyaudio
    libsdl2-dev
    libsdl2-image-dev
    libsdl2-mixer-dev
    libsdl2-ttf-dev
    libfreetype6-dev
    libraqm-dev
    fonts-noto
    fonts-noto-extra
    hostapd
    dnsmasq
    iptables
)

if [[ "$PLATFORM" == "raspberry-pi" ]]; then
    APT_PKGS+=(raspberrypi-kernel-headers)
fi

apt-get update
apt-get install -y "${APT_PKGS[@]}"
ok "System dependencies installed"

# ── Create user ────────────────────────────────────────────────────────────

header "Creating secura9 user"

if ! id "$SYS_USER" &>/dev/null; then
    useradd -r -m -s /usr/sbin/nologin "$SYS_USER"
    usermod -a -G video,input,spi,i2c,gpio "$SYS_USER" 2>/dev/null || true
    ok "User $SYS_USER created"
else
    ok "User $SYS_USER already exists"
fi

# ── Copy code ──────────────────────────────────────────────────────────────

header "Installing SECURA-9 code"

mkdir -p "$INSTALL_DIR"

rsync -a --delete \
    --exclude='venv' \
    --exclude='env' \
    --exclude='facelock' \
    --exclude='__pycache__' \
    --exclude='*.pyc' \
    --exclude='.git' \
    --exclude='venv' \
    --exclude='env' \
    --exclude='logs' \
    --exclude='faces' \
    --exclude='clips' \
    --exclude='snapshots' \
    --exclude='node_modules' \
    "$REPO_DIR/" "$INSTALL_DIR/"

# Keep data dirs
for d in faces clips logs snapshots; do
    mkdir -p "$INSTALL_DIR/$d"
done

chown -R "$SYS_USER:$SYS_GROUP" "$INSTALL_DIR"
ok "Code installed to $INSTALL_DIR"

# ── Python virtualenv ──────────────────────────────────────────────────────

header "Setting up Python virtualenv"

if [[ ! -d "$VENV_DIR" ]]; then
    python3 -m venv "$VENV_DIR"
    ok "Virtualenv created"
fi

source "$VENV_DIR/bin/activate"

# Upgrade pip
pip install --upgrade pip setuptools wheel

# Install requirements (may take a while on Pi)
if [[ -f "$INSTALL_DIR/requirements.txt" ]]; then
    info "Installing Python packages (this may take 10+ minutes on a Pi)..."
    pip install -r "$INSTALL_DIR/requirements.txt"
    ok "Python packages installed"
fi

# dlib / face_recognition sometimes needs special handling on ARM
if [[ "$PLATFORM" == "raspberry-pi" ]]; then
    python3 -c "import face_recognition" 2>/dev/null || {
        warn "face_recognition import failed — trying to build dlib from source"
        pip install --no-cache-dir dlib
        pip install face-recognition
    }
fi

chown -R "$SYS_USER:$SYS_GROUP" "$VENV_DIR"
ok "Virtualenv ready"

# ── Systemd services ───────────────────────────────────────────────────────

header "Installing systemd services"

for svc in secura9.service secura9-firstboot.service secura9-updater.service secura9-updater.timer; do
    src="$SERVICE_DIR/$svc"
    if [[ -f "$src" ]]; then
        cp "$src" "/etc/systemd/system/$svc"
        ok "Installed $svc"
    fi
done

systemctl daemon-reload

# Enable firstboot if not provisioned
if [[ ! -f /etc/secura9/config.json ]]; then
    systemctl enable secura9-firstboot.service
    info "First-boot provisioning service enabled"
else
    systemctl enable secura9.service
    systemctl enable secura9-updater.timer
    info "Main app service enabled (already provisioned)"
fi

# ── Finalize ───────────────────────────────────────────────────────────────

header "Installation complete"

echo ""
echo -e "  ${CYAN}SECURA-9${NC} installed to ${BOLD}$INSTALL_DIR${NC}"
echo ""
echo -e "  ${YELLOW}What's next:${NC}"
echo -e "  - ${BOLD}First boot:${NC}  Power on with display connected"
echo -e "    The device will start a WiFi AP named ${CYAN}SECURA9-Setup${NC}"
echo -e "    Scan the QR code on screen to complete setup"
echo ""
echo -e "  - ${BOLD}Headless:${NC}    Set HEADLESS=true in /etc/secura9/config.json"
echo -e "    or connect via Ethernet at http://<device-ip>:8080/setup"
echo ""
echo -e "  - ${BOLD}Updates:${NC}     Set SECURA9_REPO_URL in /etc/secura9/ota.conf"
echo -e "    or run: systemctl start secura9-updater"
echo ""
echo -e "  ${GREEN}Reboot to start!${NC}"
echo ""
