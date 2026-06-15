#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# SECURA-9 x86_64 ISO Image Builder
# Produces a bootable .iso for x86_64 PCs.
# Usage:  sudo ./build-x86_64-iso.sh [output-dir]
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
REPO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

ISO_NAME="secura9-x86_64.iso"
CHROOT_DIR="/tmp/secura9-chroot"

# ── Requirement checks ───────────────────────────────────────────────────

REQUIRED_TOOLS=(debootstrap mkisofs grub-mkrescue mksquashfs rsync chroot)
MISSING=()
for tool in "${REQUIRED_TOOLS[@]}"; do
    if ! command -v "$tool" &>/dev/null; then
        MISSING+=("$tool")
    fi
done
if ! command -v xorriso &>/dev/null; then
    MISSING+=("xorriso")
fi
if [[ ${#MISSING[@]} -gt 0 ]]; then
    info "Installing missing tools: ${MISSING[*]}"
    apt-get update -qq && apt-get install -y -qq "${MISSING[@]}"
fi

if [[ $EUID -ne 0 ]]; then
    err "This script must be run as root."
    exit 1
fi

mkdir -p "$OUT_DIR"

# ── Stage 1: debootstrap base system ──────────────────────────────────────

header() { echo -e "\n${BOLD}── $* ──${NC}\n"; }

header "Stage 1: Creating base rootfs (Ubuntu Noble 24.04)"

if [[ -d "$CHROOT_DIR" ]]; then
    info "Removing existing chroot..."
    rm -rf "$CHROOT_DIR"
fi

debootstrap --arch=amd64 --variant=minbase \
    noble "$CHROOT_DIR" http://archive.ubuntu.com/ubuntu/

ok "Base rootfs created"

# ── Stage 2: System configuration ────────────────────────────────────────

header "Stage 2: Configuring base system"

# Set hostname
echo "secura9" > "$CHROOT_DIR/etc/hostname"
echo "127.0.0.1 localhost
127.0.1.1 secura9" > "$CHROOT_DIR/etc/hosts"

# Set up locales
echo "en_US.UTF-8 UTF-8" > "$CHROOT_DIR/etc/locale.gen"
echo "LANG=en_US.UTF-8" > "$CHROOT_DIR/etc/default/locale"

# Mount pseudo-fs for chroot
mount --bind /dev  "$CHROOT_DIR/dev"
mount --bind /proc "$CHROOT_DIR/proc"
mount --bind /sys  "$CHROOT_DIR/sys"
mount -t devpts devpts "$CHROOT_DIR/dev/pts"

# Network config (DHCP)
mkdir -p "$CHROOT_DIR/etc/netplan"
cat > "$CHROOT_DIR/etc/netplan/01-netcfg.yaml" << 'NETPLAN'
network:
  version: 2
  renderer: networkd
  ethernets:
    eth0:
      dhcp4: true
NETPLAN

# ── Stage 3: Install packages ────────────────────────────────────────────

header "Stage 3: Installing packages"

chroot "$CHROOT_DIR" /bin/bash << 'CHROOT'
set -e
export DEBIAN_FRONTEND=noninteractive

# Enable universe repository
sed -i '/^deb /s/main$/main universe/' /etc/apt/sources.list
apt-get update

apt-get install -y \
    python3 python3-pip python3-venv python3-dev \
    git build-essential cmake gfortran \
    libatlas-base-dev \
    libgstreamer1.0-dev libgstreamer-plugins-base1.0-dev \
    libavcodec-dev libavformat-dev libswscale-dev \
    libjpeg-dev libpng-dev libtiff-dev \
    libgtk-3-dev \
    libssl-dev libffi-dev \
    portaudio19-dev \
    libsdl2-dev libsdl2-image-dev libsdl2-mixer-dev libsdl2-ttf-dev \
    libfreetype-dev \
    fonts-noto-core fonts-noto-ui-core \
    openssh-server \
    networkd-dispatcher \
    linux-image-generic \
    grub-pc grub-common grub-efi-amd64-bin \
    shim-signed efibootmgr \
    sudo ca-certificates curl wget jq \
    plymouth plymouth-label plymouth-theme-spinner \
    initramfs-tools

apt-get clean
CHROOT

ok "Packages installed"

# ── Stage 4: Create secura9 user ─────────────────────────────────────────

header "Stage 4: Creating secura9 user"

chroot "$CHROOT_DIR" /bin/bash << 'CHROOT'
useradd -r -m -s /usr/sbin/nologin secura9
usermod -aG video,input,sudo secura9
echo "secura9:secura9" | chpasswd
CHROOT

# ── Stage 5: Copy Secura9 code ──────────────────────────────────────────

header "Stage 5: Copying SECURA-9 code"

mkdir -p "$CHROOT_DIR/opt/secura9"
rsync -a --delete \
    --exclude='venv' --exclude='env' --exclude='facelock' \
    --exclude='__pycache__' --exclude='*.pyc' --exclude='.git' \
    --exclude='logs' --exclude='faces' --exclude='clips' --exclude='snapshots' \
    --exclude='node_modules' --exclude='build' \
    "$REPO_DIR/" "$CHROOT_DIR/opt/secura9/"

# Create data dirs
for d in faces clips logs snapshots; do
    mkdir -p "$CHROOT_DIR/opt/secura9/$d"
done

chown -R 1000:1000 "$CHROOT_DIR/opt/secura9"

# ── Stage 6: Python virtualenv ──────────────────────────────────────────

header "Stage 6: Setting up Python virtualenv"

chroot "$CHROOT_DIR" /bin/bash << 'CHROOT'
set -e
cd /opt/secura9
python3 -m venv venv
source venv/bin/activate
pip install --upgrade pip setuptools wheel
pip install -r requirements.txt
deactivate
CHROOT

ok "Python virtualenv ready"

# ── Stage 7: Systemd services ───────────────────────────────────────────

header "Stage 7: Installing systemd services"

SERVICE_DIR="/opt/secura9/deploy/systemd"
if [[ -d "$CHROOT_DIR$SERVICE_DIR" ]]; then
    for svc in secura9.service secura9-firstboot.service; do
        if [[ -f "$CHROOT_DIR$SERVICE_DIR/$svc" ]]; then
            cp "$CHROOT_DIR$SERVICE_DIR/$svc" "$CHROOT_DIR/etc/systemd/system/$svc"
        fi
    done
    chroot "$CHROOT_DIR" systemctl enable secura9-firstboot.service
fi

# Enable SSH
chroot "$CHROOT_DIR" systemctl enable ssh

# ── Stage 8: Plymouth boot splash ────────────────────────────────────────

header "Stage 8: Installing SECURA-9 Plymouth theme"

PLYMOUTH_THEME_DIR="$CHROOT_DIR/usr/share/plymouth/themes/secura9"
mkdir -p "$PLYMOUTH_THEME_DIR"

# Create the Plymouth theme script
cat > "$PLYMOUTH_THEME_DIR/secura9.script" << 'SCRIPT'
# SECURA-9 Plymouth boot splash
# Cyberpunk-themed with rotating hex dots

Wallpaper.SetWallpaperFunction();

fun progress_callback (progress) {
    secura9_progress = progress;
}

fun refresh_callback () {
    # Draw background
    bg_image = Image.Empty();
    bg_image = bg_image.Autoscale(Window.GetWidth(), Window.GetHeight(), 1, 1);
    bg_image = bg_image.Fill(0.06, 0.06, 0.10);
    Wallpaper.SetImage(bg_image);

    # Draw "SECURA-9" title
    title = Text("SECURA-9");
    title.SetFontFace("Noto Sans");
    title.SetFontBold(true);
    title.SetFontSize(36);
    title.SetColor(0.0, 0.85, 0.90);
    title_x = (Window.GetWidth() - title.GetWidth()) / 2;
    title_y = Window.GetHeight() * 0.30;
    title.SetPosition(title_x, title_y);
    title.Draw();

    # Draw subtitle
    subtitle = Text("ACCESS CONTROL SYSTEM");
    subtitle.SetFontFace("Noto Sans");
    subtitle.SetFontSize(14);
    subtitle.SetColor(0.40, 0.40, 0.50);
    sub_x = (Window.GetWidth() - subtitle.GetWidth()) / 2;
    subtitle.SetPosition(sub_x, title_y + 48);
    subtitle.Draw();

    # Animated hex dots
    num_dots = 6;
    dot_radius = 6;
    spacing = 40;
    dots_width = num_dots * spacing;
    dots_x = (Window.GetWidth() - dots_width) / 2 + spacing / 2;
    dots_y = title_y + 100;

    idx = 0;
    while (idx < num_dots) {
        offset = Math.IntrinsicMod((Math.GetTime() * 100 + idx * 60), 600);
        brightness = 0.3 + 0.7 * (600 - offset) / 600;

        if (offset < 300) {
            brightness = 0.3 + 0.7 * offset / 300;
        } else {
            brightness = 0.3 + 0.7 * (600 - offset) / 300;
        }

        dot_color = Color(0.0, brightness * 0.85, brightness * 0.90);
        dot_x = dots_x + idx * spacing;
        dot = Image.Text("●");
        dot.SetFontSize(18);
        dot.SetColor(dot_color.r, dot_color.g, dot_color.b);
        dot_x_final = dot_x - dot.GetWidth() / 2;
        dot.SetPosition(dot_x_final, dots_y);
        dot.Draw();

        idx = idx + 1;
    }

    # Draw progress bar background
    bar_width = Window.GetWidth() * 0.5;
    bar_height = 3;
    bar_x = (Window.GetWidth() - bar_width) / 2;
    bar_y = dots_y + 60;

    bar_bg = Image.Rectangle(bar_width, bar_height, 1, 1);
    bar_bg.SetColor(0.15, 0.15, 0.20);
    bar_bg.SetPosition(bar_x, bar_y);
    bar_bg.Draw();

    # Draw progress bar fill
    fill_width = bar_width * secura9_progress;
    if (fill_width > 0) {
        bar_fill = Image.Rectangle(fill_width, bar_height, 1, 1);
        bar_fill.SetColor(0.0, 0.85, 0.90);
        bar_fill.SetPosition(bar_x, bar_y);
        bar_fill.Draw();
    }
}

# Register callbacks
Plymouth.SetRefreshFunction(refresh_callback);
Plymouth.SetProgressFunction(progress_callback);
SCRIPT

# Create theme metadata
cat > "$PLYMOUTH_THEME_DIR/secura9.plymouth" << 'META'
[Plymouth Theme]
Name=SECURA-9
Description=Cyberpunk boot splash for SECURA-9 access control
ModuleName=script
[script]
ImageDir=/usr/share/plymouth/themes/secura9
ScriptFile=/usr/share/plymouth/themes/secura9/secura9.script
META

# Set as default theme
chroot "$CHROOT_DIR" /bin/bash << 'CHROOT'
update-alternatives --install /usr/share/plymouth/themes/default.plymouth default.plymouth \
    /usr/share/plymouth/themes/secura9/secura9.plymouth 200
update-alternatives --set default.plymouth \
    /usr/share/plymouth/themes/secura9/secura9.plymouth
CHROOT

ok "Plymouth theme installed"

# ── Stage 9: Configure GRUB ──────────────────────────────────────────────

header "Stage 9: Configuring GRUB"

cat > "$CHROOT_DIR/etc/default/grub" << 'GRUB'
GRUB_DEFAULT=0
GRUB_TIMEOUT=2
GRUB_DISTRIBUTOR="SECURA-9"
GRUB_CMDLINE_LINUX_DEFAULT="quiet splash plymouth.ignore-serial-consoles"
GRUB_CMDLINE_LINUX=""
GRUB_TERMINAL=console
GRUB_ENABLE_BLSCFG=false
GRUB
# Update GRUB
chroot "$CHROOT_DIR" update-grub 2>/dev/null || true

# Regenerate initramfs with Plymouth
chroot "$CHROOT_DIR" update-initramfs -u -k all 2>/dev/null || true

ok "GRUB and initramfs configured"

# ── Stage 10: Create squashfs ────────────────────────────────────────────

header "Stage 10: Creating squashfs"

# Cleanup mounts before squashing
umount "$CHROOT_DIR/dev/pts" 2>/dev/null || true
umount "$CHROOT_DIR/dev" 2>/dev/null || true
umount "$CHROOT_DIR/proc" 2>/dev/null || true
umount "$CHROOT_DIR/sys" 2>/dev/null || true

rm -f "$OUT_DIR/filesystem.squashfs"
mksquashfs "$CHROOT_DIR" "$OUT_DIR/filesystem.squashfs" \
    -comp xz -noappend -no-exports -no-sparse \
    -wildcards -e 'var/cache/apt/archives/*' -e 'var/log/*' -e 'tmp/*' \
    -e 'home/*' -e 'root/*'

ok "Squashfs created ($(du -h "$OUT_DIR/filesystem.squashfs" | cut -f1))"

# ── Stage 11: Build ISO ─────────────────────────────────────────────────

header "Stage 11: Building ISO"

ISO_DIR=$(mktemp -d)

# Create ISO directory structure
mkdir -p "$ISO_DIR/boot/grub"
mkdir -p "$ISO_DIR/live"

# Copy squashfs
cp "$OUT_DIR/filesystem.squashfs" "$ISO_DIR/live/filesystem.squashfs"

# Copy kernel and initrd
KERNEL=$(ls "$CHROOT_DIR/boot/vmlinuz-"* 2>/dev/null | head -1)
INITRD=$(ls "$CHROOT_DIR/boot/initrd.img-"* 2>/dev/null | head -1)
if [[ -z "$KERNEL" || -z "$INITRD" ]]; then
    err "Kernel or initrd not found in $CHROOT_DIR/boot/"
    ls -la "$CHROOT_DIR/boot/" || true
    exit 1
fi
cp "$KERNEL" "$ISO_DIR/boot/vmlinuz"
cp "$INITRD" "$ISO_DIR/boot/initrd.img"

# Create GRUB config
cat > "$ISO_DIR/boot/grub/grub.cfg" << 'GRUB'
set default="0"
set timeout=3

# Enable framebuffer for Plymouth
insmod all_video

menuentry "SECURA-9 Access Control System" {
    linux /boot/vmlinuz boot=live live-media-path=/live quiet splash plymouth.ignore-serial-consoles vt.handoff=7
    initrd /boot/initrd.img
}

menuentry "SECURA-9 (Recovery Mode)" {
    linux /boot/vmlinuz boot=live live-media-path=/live nomodeset plymouth.ignore-serial-consoles
    initrd /boot/initrd.img
}
GRUB

# Build ISO
grub-mkrescue -o "$OUT_DIR/$ISO_NAME" "$ISO_DIR" 2>&1 || {
    err "grub-mkrescue failed"
    rm -rf "$ISO_DIR"
    exit 1
}

rm -rf "$ISO_DIR"

if [[ ! -f "$OUT_DIR/$ISO_NAME" ]]; then
    err "ISO file was not created at $OUT_DIR/$ISO_NAME"
    exit 1
fi

ok "ISO built: $OUT_DIR/$ISO_NAME"
echo ""
echo -e "  Size: $(du -h "$OUT_DIR/$ISO_NAME" | cut -f1)"
echo ""
echo -e "  ${BOLD}Write to USB:${NC}"
echo -e "    ${CYAN}dd if=$OUT_DIR/$ISO_NAME of=/dev/sdX bs=4M status=progress${NC}"
echo ""

# ── Cleanup ─────────────────────────────────────────────────────────────

header "Cleanup"

if [[ -n "${KEEP_CHROOT:-}" ]]; then
    info "KEEP_CHROOT is set — leaving $CHROOT_DIR in place"
else
    rm -rf "$CHROOT_DIR"
fi

ok "Done!"
