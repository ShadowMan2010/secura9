"""
SECURA-9 First-Boot Provisioner.
Sets up WiFi AP, shows QR code, runs setup server, reboots on completion.
"""
import json
import logging
import os
import subprocess
import sys
import threading
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '../..'))

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(name)s: %(message)s')
log = logging.getLogger('provisioner')

AP_IFACE = 'wlan0'
AP_IP = '10.42.0.1'
AP_SSID = 'SECURA9-Setup'
SETUP_URL = f'http://{AP_IP}:8080/setup'

QR_AVAILABLE = False
try:
    import qrcode
    from qrcode.image.pil import PilImage
    QR_AVAILABLE = True
except ImportError:
    try:
        # Try pure Python QR
        import qrcode as qr
        QR_AVAILABLE = True
    except ImportError:
        pass


def _run(cmd, check=True, timeout=30):
    """Run a command, log output."""
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        if r.returncode != 0:
            log.warning(f'Command {" ".join(cmd)} exited {r.returncode}: {r.stderr.strip()}')
            if check:
                r.check_returncode()
        return r.stdout.strip()
    except subprocess.TimeoutExpired:
        log.warning(f'Command {" ".join(cmd)} timed out')
        if check:
            raise
        return ''
    except FileNotFoundError:
        log.warning(f'Command not found: {cmd[0]}')
        if check:
            raise
        return ''


def setup_ap():
    """Configure WiFi Access Point using hostapd + dnsmasq."""
    log.info('Setting up WiFi AP...')

    # Stop interfering services
    for svc in ['wpa_supplicant', 'dhcpcd']:
        _run(['systemctl', 'stop', svc], check=False)
        _run(['systemctl', 'disable', svc], check=False)

    # Bring up the interface
    _run(['ip', 'link', 'set', AP_IFACE, 'down'], check=False)
    _run(['ip', 'addr', 'flush', 'dev', AP_IFACE], check=False)
    _run(['ip', 'addr', 'add', f'{AP_IP}/24', 'dev', AP_IFACE], check=False)
    _run(['ip', 'link', 'set', AP_IFACE, 'up'], check=False)

    # Write hostapd config
    hostapd_conf = f"""interface={AP_IFACE}
driver=nl80211
ssid={AP_SSID}
hw_mode=g
channel=6
wmm_enabled=0
macaddr_acl=0
auth_algs=1
ignore_broadcast_ssid=0
"""
    with open('/tmp/hostapd.conf', 'w') as f:
        f.write(hostapd_conf)

    # Write dnsmasq config
    dnsmasq_conf = f"""interface={AP_IFACE}
dhcp-range=10.42.0.10,10.42.0.100,255.255.255.0,24h
dhcp-option=3,{AP_IP}
dhcp-option=6,{AP_IP}
address=/#/{AP_IP}
no-resolv
server=8.8.8.8
server=8.8.4.4
"""
    with open('/tmp/dnsmasq.conf', 'w') as f:
        f.write(dnsmasq_conf)

    # Start services
    _run(['hostapd', '/tmp/hostapd.conf', '-B'], check=False)
    _run(['dnsmasq', '-C', '/tmp/dnsmasq.conf', '--no-daemon', '&'], check=False)

    # Enable IP forwarding
    _run(['sysctl', '-w', 'net.ipv4.ip_forward=1'], check=False)

    log.info(f'WiFi AP "{AP_SSID}" ready at {AP_IP}')


def show_qr_terminal():
    """Show QR code in terminal using Unicode blocks."""
    if QR_AVAILABLE:
        try:
            qr_img = qrcode.make(SETUP_URL)
            if hasattr(qr_img, 'get_matrix'):
                matrix = qr_img.get_matrix()
            elif hasattr(qr_img, 'text'):
                # text fallback
                print(qr_img.text())
                return
            else:
                # PIL image — convert to matrix
                w = qr_img.size[0]
                matrix = []
                for y in range(w):
                    row = []
                    for x in range(w):
                        px = qr_img.getpixel((x, y))
                        row.append(px == 0)
                    matrix.append(row)

            # Render UTF-8 QR
            for row in matrix:
                line = ''
                for i in range(0, len(row), 2):
                    a = row[i]
                    b = row[i + 1] if i + 1 < len(row) else False
                    if a and b:
                        line += '\u2588'
                    elif a:
                        line += '\u2580'
                    elif b:
                        line += '\u2584'
                    else:
                        line += ' '
                print(line)
        except Exception as e:
            log.warning(f'QR render failed: {e}')

    print(f'\n  Scan this QR or visit: {SETUP_URL}')
    print(f'  Connect to WiFi SSID: {AP_SSID}\n')


def show_qr_display():
    """Show QR code on the physical display using pygame."""
    try:
        import pygame
        pygame.init()
        # Try to detect actual screen size
        import config as cfg
        w, h = cfg.DISPLAY_WIDTH, cfg.DISPLAY_HEIGHT
        scr = pygame.display.set_mode((w, h), pygame.FULLSCREEN)
        pygame.display.set_caption('SECURA-9 Setup')

        # Generate QR code image
        qr_img = None
        if QR_AVAILABLE:
            try:
                qr_obj = qrcode.make(SETUP_URL)
                if hasattr(qr_obj, 'save'):
                    import io
                    buf = io.BytesIO()
                    qr_obj.save(buf, format='PNG')
                    buf.seek(0)
                    qr_img = pygame.image.load(buf)
        except Exception:
            pass

        clock = pygame.time.Clock()
        running = True
        setup_done = False
        font_large = pygame.font.Font(None, 28)
        font_small = pygame.font.Font(None, 18)

        while running:
            for ev in pygame.event.get():
                if ev.type == pygame.QUIT:
                    running = False

            # Check if provisioning is done
            if os.path.exists('/etc/secura9/config.json') and \
               os.path.exists('/etc/secura9/firebase.json') and \
               not setup_done:
                setup_done = True

            scr.fill((10, 2, 16))

            if setup_done:
                txt = font_large.render('CONFIGURED! REBOOTING...', True, (57, 255, 20))
                scr.blit(txt, (w // 2 - txt.get_width() // 2, h // 2))
                pygame.display.flip()
                time.sleep(2)
                return

            # Title
            title = font_large.render('SECURA-9 SETUP', True, (0, 255, 255))
            scr.blit(title, (w // 2 - title.get_width() // 2, 30))

            # QR code
            if qr_img:
                qr_size = min(w, h) - 120
                qr_scaled = pygame.transform.scale(qr_img, (qr_size, qr_size))
                qr_x = w // 2 - qr_scaled.get_width() // 2
                qr_y = h // 2 - qr_scaled.get_height() // 2 - 30
                scr.blit(qr_scaled, (qr_x, qr_y))

            # Instructions
            instr = [
                f'1. Connect to WiFi: {AP_SSID}',
                f'2. Open browser to: {SETUP_URL}',
                '3. Upload Firebase credentials',
                '4. Device auto-reboots',
            ]
            for i, line in enumerate(instr):
                txt = font_small.render(line, True, (90, 80, 120))
                scr.blit(txt, (w // 2 - txt.get_width() // 2, h - 120 + i * 22))

            pygame.display.flip()
            clock.tick(10)

        pygame.quit()
    except Exception as e:
        log.warning(f'Display QR failed: {e}')
        show_qr_terminal()


def wait_for_setup():
    """Wait until provisioning files are written, then reboot."""
    log.info('Waiting for setup to complete...')
    while True:
        if os.path.exists('/etc/secura9/config.json') and \
           os.path.exists('/etc/secura9/firebase.json'):
            log.info('Provisioning complete!')
            return
        time.sleep(2)


def cleanup_ap():
    """Tear down the WiFi AP."""
    log.info('Cleaning up WiFi AP...')
    _run(['killall', 'hostapd', 'dnsmasq'], check=False)
    _run(['systemctl', 'start', 'dhcpcd'], check=False)
    _run(['systemctl', 'enable', 'dhcpcd'], check=False)


def main():
    log.info('=== SECURA-9 First-Boot Provisioning ===')
    log.info(f'Setup URL: {SETUP_URL}')

    # Setup WiFi AP
    try:
        setup_ap()
    except Exception as e:
        log.warning(f'Failed to setup WiFi AP: {e}')
        log.warning('Falling back to wired-only setup')
        log.warning(f'Open http://0.0.0.0:8080/setup on a device on the same network')

    # Start setup server in background
    from deploy.provisioning.setup_server import SetupHandler
    server = SetupHandler()
    server_thread = threading.Thread(target=server.start, daemon=True, name='setup-http')
    server_thread.start()
    time.sleep(0.5)

    # Show QR code
    try:
        import config
        if not config.HEADLESS and config.DISPLAY_WIDTH > 0 and config.DISPLAY_HEIGHT > 0:
            show_qr_display()
        else:
            show_qr_terminal()
    except Exception:
        show_qr_terminal()

    # Wait for setup
    wait_for_setup()

    # Cleanup
    cleanup_ap()

    # Reboot
    log.info('Rebooting in 3 seconds...')
    time.sleep(3)
    _run(['reboot'], check=False)


if __name__ == '__main__':
    main()
