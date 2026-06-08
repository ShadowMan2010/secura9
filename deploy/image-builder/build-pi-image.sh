#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# SECURA-9 Raspberry Pi OS Image Builder
# Produces a ready-to-flash .img.gz for Raspberry Pi.
# Usage:  sudo ./build-pi-image.sh [output-dir]
# ═══════════════════════════════════════════════════════════════════════════
set -euo pipefail

BOLD='\033[1m'
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'
info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()   { echo -e "${RED}[ERR]${NC}   $*" >&2; }

OUT_DIR="${1:-./build}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"

# ── Requirements ───────────────────────────────────────────────────────────

REQUIRED_TOOLS=(wget gunzip losetup mount rsync chroot qemu-arm-static)
for tool in "${REQUIRED_TOOLS[@]}"; do
    if ! command -v "$tool" &>/dev/null; then
        err "$tool is required but not installed"
        exit 1
    fi
done

# ── Config ─────────────────────────────────────────────────────────────────

PI_OS_URL="${PI_OS_URL:-https://downloads.raspberrypi.com/raspios_lite_arm64/images/raspios_lite_arm64-2024-11-19/2024-11-19-raspios-bookworm-arm64-lite.img.xz}"
IMAGE_NAME="secura9-pi.img"
IMAGE_SIZE_MB=4096  # 4 GB final image

mkdir -p "$OUT_DIR"
OUT_IMG="$OUT_DIR/$IMAGE_NAME"
OUT_GZ="$OUT_IMG.gz"

# ── Download Raspberry Pi OS ───────────────────────────────────────────────

header() {
    echo -e "\n${BOLD}── $* ──${NC}\n"
}

header "Downloading Raspberry Pi OS"

BASE_IMG="$OUT_DIR/raspios-base.img"
if [[ ! -f "$BASE_IMG" ]]; then
    info "Downloading Raspberry Pi OS Lite (arm64)..."
    wget -O "$OUT_DIR/raspios.img.xz" "$PI_OS_URL"
    xz -d "$OUT_DIR/raspios.img.xz"
    mv "$OUT_DIR/raspios.img" "$BASE_IMG"
    ok "Base image downloaded"
else
    ok "Base image already exists"
fi

# ── Resize image ───────────────────────────────────────────────────────────

header "Preparing image"

cp "$BASE_IMG" "$OUT_IMG"

# Get geometry
SECTOR_SIZE=512
LOOP_DEV=$(losetup -f --show -P "$OUT_IMG")
PART_START=$(fdisk -l "$OUT_IMG" | grep "${OUT_IMG}" | awk 'NR==2 {print $2}')
PART_SIZE=$((IMAGE_SIZE_MB * 1024 * 1024 / SECTOR_SIZE - PART_START))

losetup -d "$LOOP_DEV"

# Resize partition and filesystem
truncate -s ${IMAGE_SIZE_MB}M "$OUT_IMG"
LOOP_DEV=$(losetup -f --show -P "$OUT_IMG")
parted -s "$LOOP_DEV" resizepart 2 "${PART_SIZE}s"
e2fsck -f "${LOOP_DEV}p2"
resize2fs "${LOOP_DEV}p2"

# Mount
BOOT_MNT=$(mktemp -d)
ROOT_MNT=$(mktemp -d)
mount "${LOOP_DEV}p1" "$BOOT_MNT"
mount "${LOOP_DEV}p2" "$ROOT_MNT"
ok "Image mounted"

# ── Customize ──────────────────────────────────────────────────────────────

header "Customizing image"

# Enable SSH, set hostname
touch "$BOOT_MNT/ssh"
echo "secura9" > "$ROOT_MNT/etc/hostname"
sed -i 's/127.0.1.1.*/127.0.1.1\tsecura9/' "$ROOT_MNT/etc/hosts"

# Enable camera, serial, I2C, SPI
for cfg in camera_i2c=on enable_uart=1 dtparam=i2c_arm=on dtparam=spi=on; do
    if ! grep -q "^$cfg" "$BOOT_MNT/config.txt" 2>/dev/null; then
        echo "$cfg" >> "$BOOT_MNT/config.txt"
    fi
done

# Copy SECURA-9 code
mkdir -p "$ROOT_MNT/opt/secura9"
rsync -a --delete \
    --exclude='venv' --exclude='env' --exclude='facelock' \
    --exclude='__pycache__' --exclude='*.pyc' --exclude='.git' \
    --exclude='logs' --exclude='faces' --exclude='clips' --exclude='snapshots' \
    "$REPO_DIR/" "$ROOT_MNT/opt/secura9/"

# Create firstboot script
cat > "$ROOT_MNT/usr/lib/raspberrypi-sys-mods/firstboot.d/90-secura9" << 'FIEND'
#!/bin/bash
# SECURA-9 firstboot hook — runs installer on first real boot
set -e
cd /opt/secura9
bash deploy/install.sh 2>&1 | logger -t secura9-firstboot
FIEND
chmod +x "$ROOT_MNT/usr/lib/raspberrypi-sys-mods/firstboot.d/90-secura9"

# ── Cleanup ────────────────────────────────────────────────────────────────

header "Finalizing"

umount "$BOOT_MNT"
umount "$ROOT_MNT"
rm -rf "$BOOT_MNT" "$ROOT_MNT"
losetup -d "$LOOP_DEV"

# Compress
info "Compressing image..."
gzip -f "$OUT_IMG"

# Cleanup base image
rm -f "$BASE_IMG"

ok "Image built: $OUT_GZ"
echo ""
echo -e "  Flash with:  ${CYAN}balenaEtcher${NC} or ${CYAN}dd${NC}"
echo -e "  ${BOLD}dd if=$OUT_GZ of=/dev/sdX bs=4M status=progress${NC}"
echo ""
echo -e "  Size: $(du -h "$OUT_GZ" | cut -f1)"
echo ""
