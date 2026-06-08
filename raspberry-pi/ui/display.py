"""
SECURA-9 — Display  (matches hand-drawn sketch)

Layout  720 × 1280  portrait:
┌─────────────────────────────┐  y=0
│  DATE          TIME    38px │  ← top HUD bar
├─────────────────────────────┤  y=38
│                             │
│       CAMERA FEED           │  ← top half  ~580px
│                             │
├─────────────────────────────┤  y=618
│                             │
│   STATUS PANEL              │  ← bottom half  ~652px
│   (changes with state)      │
└─────────────────────────────┘  y=1280

Status panel messages:
  IDLE          → "Waiting for face..."
  NEW_FACE      → "Unknown face — say your name"  + mic animation
  WAITING       → "Sending for approval..."  + spinner
  GRANTED       → "Access Granted  ✓"  green
  DENIED        → "Access Denied   ✗"  red
  NOBODY_HOME   → "Nobody home"  red
  MESSAGE       → custom text (mic open etc.)
"""

import pygame
import pygame.gfxdraw
import pygame.freetype
import threading
import time
import math
import random
import logging
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import config

log = logging.getLogger('display')

# ── Font cache ────────────────────────────────────────────────────────────
_FC = {}

def F(size, bold=False):
    k = (size, bold)
    if k not in _FC:
        paths = [
            '/usr/share/fonts/truetype/orbitron/Orbitron-Bold.ttf',
            '/usr/share/fonts/truetype/liberation/LiberationMono-Bold.ttf',
            '/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf',
            '/usr/share/fonts/truetype/freefont/FreeMono.ttf',
            '/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf',
            '/usr/share/fonts/truetype/noto/NotoSansMono-Regular.ttf',
        ]
        f = None
        for p in paths:
            if os.path.exists(p):
                try: f = pygame.font.Font(p, size); break
                except Exception: pass
        _FC[k] = f or pygame.font.SysFont('monospace', size, bold=bold)
    return _FC[k]

def _find_bengali_font():
    """Return path to best Bengali font, or None."""
    for p in [
        '/usr/share/fonts/truetype/noto/NotoSansBengaliUI-Regular.ttf',
        '/usr/share/fonts/truetype/noto/NotoSansBengali-Regular.ttf',
        '/usr/share/fonts/truetype/noto/NotoSansBengaliUI-Bold.ttf',
        '/usr/share/fonts/truetype/noto/NotoSansBengali-Bold.ttf',
        '/usr/share/fonts/truetype/noto/NotoSansBengaliUI-Medium.ttf',
        '/usr/share/fonts/truetype/noto/NotoSansBengaliUI-SemiBold.ttf',
        '/usr/share/fonts/truetype/freefont/FreeSans.ttf',
        '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
    ]:
        if os.path.exists(p):
            return p
    return None

_BN_FONT_PATH = _find_bengali_font()

def FB(size):
    """Bengali-capable font (pygame.font)."""
    k = ('bn', size)
    if k not in _FC:
        f = None
        if _BN_FONT_PATH:
            try:
                f = pygame.font.Font(_BN_FONT_PATH, size)
            except Exception as e:
                log.warning(f'FB: font.Font({_BN_FONT_PATH}) failed: {e}')
        _FC[k] = f or pygame.font.SysFont('sans', size)
        if f is None:
            log.warning('FB: no Bengali font — falling back to sans')
    return _FC[k]


# ── Particles (floating cyan dots) ───────────────────────────────────────
class Particle:
    def __init__(self, w, h):
        self._w = w
        self._h = h
        self.reset()

    def reset(self):
        self.x   = random.randint(0, self._w)
        self.y   = random.randint(0, self._h)
        self.vy  = random.uniform(-0.6, -0.2)
        self.vx  = random.uniform(-0.15, 0.15)
        self.life     = random.uniform(0.5, 1.0)
        self.max_life = self.life
        self.col = random.choice([(0,255,229),(0,200,180),(0,255,136)])

    def update(self, dt):
        self.x += self.vx
        self.y += self.vy
        self.life -= dt * 0.25
        if self.life <= 0 or self.y < 0:
            self.reset()
            self.y = self._h

    def draw(self, surf):
        a = max(0, int(200 * self.life / self.max_life))
        try:
            pygame.gfxdraw.pixel(surf, int(self.x), int(self.y),
                                  (*self.col, a))
        except Exception:
            pass


# ── Waveform helper ───────────────────────────────────────────────────────
def draw_waveform(scr, x, y, w, h, col, t, bars=24, active=False):
    bw = w // bars
    for i in range(bars):
        if active:
            bh = int(h * (0.2 + 0.8 * abs(math.sin(t * 9 + i * 0.65))))
        else:
            bh = int(h * (0.08 + 0.12 * abs(math.sin(t * 1.4 + i * 0.45))))
        bh = max(2, bh)
        by = y + h - bh
        a  = int(80 + 100 * abs(math.sin(t * 3 + i * 0.3))) if active else 70
        s = pygame.Surface((max(1, bw-2), bh), pygame.SRCALPHA)
        s.fill((*col, a))
        scr.blit(s, (x + i * bw + 1, by))


# ── Rotating arc ring ─────────────────────────────────────────────────────
def draw_ring(scr, cx, cy, r, col, t, speed=1.0, gap=70, thick=2, alpha=200):
    offset = t * speed * 180
    span   = math.radians(360 - gap)
    start  = math.radians(offset % 360)
    steps  = max(48, r * 2)
    pts = []
    for i in range(int(steps)+1):
        a = start + span * (i / steps)
        pts.append((int(cx + r*math.cos(a)), int(cy + r*math.sin(a))))
    if len(pts) > 1:
        s = pygame.Surface((scr.get_width(), scr.get_height()), pygame.SRCALPHA)
        c = (*col, alpha) if len(col) == 3 else col
        pygame.draw.lines(s, c, False, pts, thick)
        scr.blit(s, (0,0))


# ── Main Display class ────────────────────────────────────────────────────
class Display:

    # States
    BOOT        = 'boot'
    IDLE        = 'idle'
    NEW_FACE    = 'new_face'
    WAITING     = 'waiting'
    GRANTED     = 'granted'
    DENIED      = 'denied'
    NOBODY_HOME = 'nobody_home'
    MESSAGE     = 'message'
    OTP_WAITING = 'otp_waiting'
    OTP_ENTER   = 'otp_enter'
    OTP_WRONG   = 'otp_wrong'
    OTP_EXPIRED = 'otp_expired'

    def __init__(self):
        self._state    = self.BOOT
        self._lock     = threading.Lock()
        self._running  = False
        self._on_state_change = None

        self._person_name  = ''
        self._confidence   = 0.0
        self._message_text = ''
        self._cam_surf     = None
        self._face_boxes   = []   # list of (x,y,w,h) or (x,y,w,h,conf)

        self._t   = 0.0
        self._s_t = time.time()

        self.known_face_count = 0

        # Computed at runtime from actual window size
        self._w = self._h = 0
        self._hud_h = 38
        self._is_portrait = True
        self._cam_x = self._cam_y = self._cam_w = self._cam_h = 0
        self._panel_x = self._panel_y = self._panel_w = self._panel_h = 0
        self._particles = []

        # Scanning line y position (only inside camera area)
        self._scan_y = 0

        # OTP numpad state
        self._otp_display     = ''
        self._otp_expiry      = 0
        self._otp_digits      = ''
        self._otp_attempt     = 0
        self._otp_max_att     = 0
        self._otp_submitted   = False
        self._otp_retries     = 0
        self._otp_wrong_shown = 0.0  # timestamp

        # System status
        self._start_time    = time.time()
        self._fb_ok         = True
        self._cam_ok        = True
        self._webrtc_ok     = False
        self._door_locked   = True

    # ── Public API ────────────────────────────────────────────────────────

    def show_boot(self):        self._set(self.BOOT)
    def show_idle(self):        self._set(self.IDLE)
    def show_nobody_home(self): self._set(self.NOBODY_HOME)
    def show_new_face(self):    self._set(self.NEW_FACE)
    def show_denied(self):      self._set(self.DENIED)

    def show_waiting(self, name=''):
        self._person_name = name
        self._set(self.WAITING)

    def show_granted(self, name, confidence=100.0):
        self._person_name = name
        self._confidence  = confidence
        self._set(self.GRANTED)

    def show_mic_open(self):
        self._message_text = 'SAY YOUR NAME NOW'
        self._set(self.MESSAGE)

    def show_mic_retry(self):
        self._message_text = 'PLEASE SAY NAME AGAIN'
        self._set(self.MESSAGE)

    def show_message(self, text):
        self._message_text = text
        self._set(self.MESSAGE)

    def show_otp_waiting(self, otp, expiry):
        self._otp_display = otp
        self._otp_expiry  = expiry
        self._otp_digits  = ''
        self._otp_submitted = False
        self._otp_retries = 0
        self._set(self.OTP_WAITING)

    def show_otp_enter(self, attempt, max_attempts, remaining):
        self._otp_attempt   = attempt
        self._otp_max_att   = max_attempts
        self._otp_expiry    = remaining
        self._otp_submitted = False
        self._set(self.OTP_ENTER)

    def show_otp_wrong(self, retries_left):
        self._otp_retries     = retries_left
        self._otp_wrong_shown = time.time()
        self._set(self.OTP_WRONG)

    def show_otp_expired(self):
        self._set(self.OTP_EXPIRED)

    def get_otp_input(self):
        return self._otp_digits

    def is_otp_submitted(self):
        return self._otp_submitted

    def reset_otp_input(self):
        self._otp_digits = ''
        self._otp_submitted = False

    def set_status(self, fb=None, cam=None, webrtc=None, door_locked=None):
        if fb is not None:      self._fb_ok = fb
        if cam is not None:     self._cam_ok = cam
        if webrtc is not None:  self._webrtc_ok = webrtc
        if door_locked is not None: self._door_locked = door_locked

    def update_camera(self, frame_bgr, face_boxes=None):
        if frame_bgr is None:
            return
        try:
            import cv2
            rgb  = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
            surf = pygame.surfarray.make_surface(rgb.swapaxes(0, 1))
            with self._lock:
                self._cam_surf  = surf
                self._face_boxes = [
                    (b[0], b[1], b[2], b[3],
                     b[4] if len(b) > 4 else 90)
                    for b in (face_boxes or [])
                ]
        except Exception as e:
            log.debug(f'update_camera: {e}')

    def stop(self):
        self._running = False

    # ── Layout computation ───────────────────────────────────────────────

    def _compute_layout(self):
        self._w, self._h = self._scr.get_size()
        self._is_portrait = self._h > self._w
        self._hud_h = max(28, min(42, int(self._h * 0.03)))

        PAD = 8

        if self._is_portrait:
            # Camera on top half, panel below
            self._cam_x = PAD
            self._cam_y = self._hud_h + PAD
            self._cam_w = self._w - PAD * 2
            self._cam_h = int(self._h * 0.46) - PAD * 2

            self._panel_x = PAD
            self._panel_y = self._cam_y + self._cam_h + 4
            self._panel_w = self._w - PAD * 2
            self._panel_h = self._h - self._panel_y - PAD
        else:
            # Camera on left, panel on right (side-by-side)
            self._cam_x = PAD
            self._cam_y = self._hud_h + PAD
            self._cam_w = int(self._w * 0.52) - PAD * 2
            self._cam_h = self._h - self._hud_h - PAD * 2

            self._panel_x = self._cam_x + self._cam_w + PAD
            self._panel_y = self._hud_h + PAD
            self._panel_w = self._w - self._panel_x - PAD
            self._panel_h = self._cam_h

        self._scan_y = self._hud_h
        self._particles = [Particle(self._w, self._h) for _ in range(50)]

        log.info(f'Display {self._w}×{self._h} '
                 f'{"portrait" if self._is_portrait else "landscape"}'
                 f'  cam={self._cam_w}×{self._cam_h}  '
                 f'panel={self._panel_w}×{self._panel_h}')

    # ── Main loop ─────────────────────────────────────────────────────────

    def run(self):
        pygame.init()
        pygame.mouse.set_visible(True)

        flags = (pygame.FULLSCREEN | pygame.HWSURFACE | pygame.DOUBLEBUF
                 if config.FULLSCREEN else 0)
        self._scr = pygame.display.set_mode(
            (config.DISPLAY_WIDTH, config.DISPLAY_HEIGHT), flags
        )
        pygame.display.set_caption('SECURA-9')
        self._compute_layout()
        clk = pygame.time.Clock()
        self._running = True

        # Pre-bake hex-grid background
        self._bg = self._make_bg()

        while self._running:
            for ev in pygame.event.get():
                if ev.type == pygame.QUIT:
                    self._running = False
                if ev.type == pygame.KEYDOWN:
                    if ev.key == pygame.K_ESCAPE: self._running = False
                    if ev.key == pygame.K_F11:    pygame.display.toggle_fullscreen()
                if ev.type == pygame.MOUSEBUTTONDOWN and ev.button == 1:
                    self._handle_numpad_click(*ev.pos)

            dt = clk.tick(30) / 1000.0
            self._t += dt

            # Scan line sweeps only inside the camera area
            self._scan_y += 3
            if self._scan_y > self._cam_y + self._cam_h:
                self._scan_y = self._hud_h

            # ── Draw ──────────────────────────────────────────────────────
            self._scr.fill(config.COL_BG)
            self._scr.blit(self._bg, (0, 0))

            # Particles
            ps = pygame.Surface(
                (self._w, self._h),
                pygame.SRCALPHA
            )
            for p in self._particles:
                p.update(dt)
                p.draw(ps)
            self._scr.blit(ps, (0, 0))

            # Always draw these fixed sections first
            self._draw_camera_section()

            # Then the state-driven bottom panel
            s = self._state
            if   s == self.BOOT:        self._panel_boot()
            elif s == self.IDLE:        self._panel_idle()
            elif s == self.NEW_FACE:    self._panel_new_face()
            elif s == self.WAITING:     self._panel_waiting()
            elif s == self.GRANTED:     self._panel_granted()
            elif s == self.DENIED:      self._panel_denied()
            elif s == self.NOBODY_HOME: self._panel_nobody_home()
            elif s == self.MESSAGE:     self._panel_message()
            elif s == self.OTP_WAITING: self._panel_otp_waiting()
            elif s == self.OTP_ENTER:   self._panel_otp_enter()
            elif s == self.OTP_WRONG:   self._panel_otp_wrong()
            elif s == self.OTP_EXPIRED: self._panel_otp_expired()

            # HUD bar always on top
            self._draw_hud()

            if config.SHOW_FPS:
                fs = F(10).render(f'{clk.get_fps():.0f}fps', True, config.COL_MUTED)
                self._scr.blit(fs, (self._w - 44, self._h - 14))

            pygame.display.flip()

        pygame.quit()
        log.info('Display closed')

    # ── Internal ──────────────────────────────────────────────────────────

    def set_on_state_change(self, callback):
        self._on_state_change = callback

    def _set(self, s):
        with self._lock:
            self._state = s
            self._s_t   = time.time()
        if self._on_state_change:
            self._on_state_change(s)

    def _elapsed(self): return time.time() - self._s_t
    def _pulse(self, sp=3.0): return int(128 + 127 * math.sin(self._t * sp))
    def _pulse01(self, sp=3.0): return 0.5 + 0.5 * math.sin(self._t * sp)

    # shorthand draw helpers
    def _tc(self, txt, sz, col, cx, cy, bold=False):
        try:
            s = F(sz, bold).render(str(txt), True, col)
            self._scr.blit(s, (cx - s.get_width()//2, cy - s.get_height()//2))
        except Exception: pass

    def _bnc(self, txt, sz, col, cx, cy):
        try:
            from PIL import Image, ImageDraw, ImageFont
            fp = _BN_FONT_PATH or '/usr/share/fonts/truetype/noto/NotoSansBengaliUI-Regular.ttf'
            if os.path.exists(fp):
                font = ImageFont.truetype(fp, sz)
                bbox = font.getbbox(str(txt))
                tw = bbox[2] - bbox[0]
                th = bbox[3] - bbox[1]
                img = Image.new('RGBA', (tw or 1, th or 1), (0, 0, 0, 0))
                draw = ImageDraw.Draw(img)
                draw.text((-bbox[0], -bbox[1]), str(txt), font=font, fill=(*col, 255))
                raw = img.tobytes()
                surf = pygame.image.fromstring(raw, img.size, 'RGBA')
                self._scr.blit(surf, (cx - tw // 2, cy - th // 2))
                return
        except Exception as e:
            log.warning(f'_bnc PIL fallback: {e}')
        try:
            if _BN_FONT_PATH:
                ft = pygame.freetype.Font(_BN_FONT_PATH, sz)
                s, _ = ft.render(str(txt), col)
                self._scr.blit(s, (cx - s.get_width()//2, cy - s.get_height()//2))
                return
        except Exception as e:
            log.warning(f'_bnc freetype fallback: {e}')
        try:
            s = FB(sz).render(str(txt), True, col)
            self._scr.blit(s, (cx - s.get_width()//2, cy - s.get_height()//2))
        except Exception as e:
            log.warning(f'_bnc FB fallback: {e}')

    def _tl(self, txt, sz, col, x, cy):
        try:
            s = F(sz).render(str(txt), True, col)
            self._scr.blit(s, (x, cy - s.get_height()//2))
        except Exception: pass

    def _tr(self, txt, sz, col, rx, cy):
        try:
            s = F(sz).render(str(txt), True, col)
            self._scr.blit(s, (rx - s.get_width(), cy - s.get_height()//2))
        except Exception: pass

    # ── Hex background ────────────────────────────────────────────────────
    def _make_bg(self):
        s  = pygame.Surface((self._w, self._h), pygame.SRCALPHA)
        r  = 20
        hr = r * math.sqrt(3)
        c1 = (0, 255, 229, 7)
        c2 = (0, 255, 136, 4)
        row = 0; y = 0
        while y < self._h + r:
            x = (r * 1.5) if row % 2 else 0
            while x < self._w + r:
                col = c2 if (row + int(x/r)) % 8 == 0 else c1
                pts = []
                for i in range(6):
                    a = math.radians(60*i - 30)
                    pts.append((int(x + r*math.cos(a)), int(y + r*math.sin(a))))
                try: pygame.draw.polygon(s, col, pts, 1)
                except Exception: pass
                x += r * 3
            y  += hr / 2
            row += 1
        return s

    # ── HUD top bar ───────────────────────────────────────────────────────
    def _draw_hud(self):
        """
        HUD bar:
        [DATE dd/mm/yy] [● ● ●] [LOCKED] [HH:MM:SS] [UP 2h14m]
        """
        H_BAR = self._hud_h
        W_SCR = self._w
        cy = H_BAR // 2
        fs = 11
        bold = True

        pygame.draw.rect(self._scr, config.COL_PANEL, (0, 0, W_SCR, H_BAR))
        pygame.draw.line(self._scr, config.COL_CYAN,  (0, H_BAR), (W_SCR, H_BAR), 1)
        pygame.draw.line(self._scr, (*config.COL_CYAN, 40),
                          (0, H_BAR-1), (W_SCR, H_BAR-1), 1)

        # DATE on the left
        date_str = time.strftime('%d/%m/%y')
        ds = F(fs, bold).render(date_str, True, config.COL_CYAN)
        self._scr.blit(ds, (10, cy - ds.get_height() // 2))
        x = 10 + ds.get_width() + 8

        # Status dots: Firebase ● Camera ● WebRTC
        colors = [
            config.COL_GREEN if self._fb_ok else config.COL_RED,
            config.COL_GREEN if self._cam_ok else config.COL_RED,
            config.COL_GREEN if self._webrtc_ok else config.COL_YELLOW,
        ]
        for c in colors:
            s = pygame.Surface((8, 8), pygame.SRCALPHA)
            pygame.draw.circle(s, (*c, 220), (4, 4), 3)
            pygame.draw.circle(s, (*c, 60), (4, 4), 4)
            self._scr.blit(s, (x, cy - 4))
            x += 12

        x += 4

        # Lock indicator
        lock_label = 'LOCKED' if self._door_locked else 'OPEN'
        lock_col = config.COL_GREEN if self._door_locked else config.COL_RED
        ls = F(9, bold).render(lock_label, True, lock_col)
        self._scr.blit(ls, (x, cy - ls.get_height() // 2))
        x += ls.get_width() + 8

        # TIME on the right
        time_str = time.strftime('%H:%M:%S')
        ts = F(fs, bold).render(time_str, True, config.COL_CYAN)

        # Uptime between lock and time
        up = int(time.time() - self._start_time)
        h, m = up // 3600, (up % 3600) // 60
        if h:
            up_str = f'UP {h}h{m:02d}m'
        else:
            up_str = f'UP {m}m'
        us = F(8, False).render(up_str, True, config.COL_MUTED)
        ux = W_SCR - ts.get_width() - us.get_width() - 18
        self._scr.blit(us, (ux, cy - us.get_height() // 2))

        self._scr.blit(ts, (W_SCR - ts.get_width() - 10,
                              cy - ts.get_height() // 2))

    # ── Camera section (always shown) ─────────────────────────────────────
    def _draw_camera_section(self):
        cam_x = self._cam_x
        cam_y = self._cam_y
        cam_w = self._cam_w
        cam_h = self._cam_h

        with self._lock:
            surf  = self._cam_surf
            boxes = list(self._face_boxes)

        if surf:
            sw, sh = surf.get_size()
            scale = min(cam_w / max(sw, 1), cam_h / max(sh, 1))
            nw, nh = int(sw * scale), int(sh * scale)
            scaled = pygame.transform.scale(surf, (nw, nh))
            ox = cam_x + (cam_w - nw) // 2
            oy = cam_y + (cam_h - nh) // 2
            self._scr.blit(scaled, (ox, oy))
        else:
            pygame.draw.rect(self._scr, config.COL_DIM, (cam_x, cam_y, cam_w, cam_h))
            dots = '.' * (int(self._t * 2) % 4)
            m = F(14).render(f'CAMERA INITIALISING{dots}', True, config.COL_MUTED)
            self._scr.blit(m, (cam_x + cam_w//2 - m.get_width()//2,
                                cam_y + cam_h//2))

        # Scan line inside cam area only
        sl = pygame.Surface((cam_w, 2), pygame.SRCALPHA)
        a  = int(60 * self._pulse01(0.8))
        sl.fill((0, 255, 229, a))
        self._scr.blit(sl, (cam_x, self._scan_y))

        # Animated cyan border
        bc = config.COL_CYAN
        pygame.draw.rect(self._scr, bc, (cam_x, cam_y, cam_w, cam_h), 1)
        # Corner brackets
        L = 18; T = 2
        for (cx2, cy2, sx, sy) in [
            (cam_x,          cam_y,           1,  1),
            (cam_x+cam_w-L,  cam_y,          -1,  1),
            (cam_x,          cam_y+cam_h-L,   1, -1),
            (cam_x+cam_w-L,  cam_y+cam_h-L,  -1, -1),
        ]:
            pygame.draw.line(self._scr, bc, (cx2, cy2), (cx2+L*sx, cy2), T)
            pygame.draw.line(self._scr, bc, (cx2, cy2), (cx2, cy2+L*sy), T)

        # REC dot
        rec_a = 255 if int(self._t*2) % 2 == 0 else 50
        pygame.draw.circle(self._scr, (*config.COL_RED, rec_a),
                            (cam_x+10, cam_y+10), 5)
        r = F(9).render('REC', True, config.COL_RED)
        self._scr.blit(r, (cam_x+18, cam_y+6))

        # ── Glowing face boxes ────────────────────────────────────────────
        if surf and boxes:
            sw, sh = surf.get_size()
            scale = min(cam_w / max(sw, 1), cam_h / max(sh, 1))
            nw, nh = int(sw * scale), int(sh * scale)
            ox = cam_x + (cam_w - nw) // 2
            oy = cam_y + (cam_h - nh) // 2

            # Box colour by state
            state = self._state
            if state == self.GRANTED:
                fc = config.COL_GREEN
            elif state == self.DENIED:
                fc = config.COL_RED
            elif state in (self.NEW_FACE, self.WAITING, self.MESSAGE):
                fc = config.COL_YELLOW
            else:
                fc = config.COL_CYAN

            ga = self._pulse(4)

            for box in boxes:
                bx = int(ox + box[0] * scale)
                by = int(oy + box[1] * scale)
                bw = int(box[2] * scale)
                bh = int(box[3] * scale)
                conf = box[4] if len(box) > 4 else 0

                # Multi-layer glow
                for spread, base_alpha in [(10,15),(7,25),(4,50),(2,90),(1,150)]:
                    gs = pygame.Surface((bw+spread*2, bh+spread*2), pygame.SRCALPHA)
                    alpha = int(base_alpha * ga / 255)
                    pygame.draw.rect(gs, (*fc, alpha),
                                     (0, 0, bw+spread*2, bh+spread*2), 1)
                    self._scr.blit(gs, (bx-spread, by-spread))

                # Solid box
                pygame.draw.rect(self._scr, fc, (bx, by, bw, bh), 2)

                # Corner ticks
                TL = 14; TT = 3
                for (tx,ty,tsx,tsy) in [
                    (bx,      by,       1,  1),
                    (bx+bw-TL,by,      -1,  1),
                    (bx,      by+bh-TL, 1, -1),
                    (bx+bw-TL,by+bh-TL,-1, -1),
                ]:
                    pygame.draw.line(self._scr, fc,(tx,ty),(tx+TL*tsx,ty),TT)
                    pygame.draw.line(self._scr, fc,(tx,ty),(tx,ty+TL*tsy),TT)

                # Confidence label above box
                if conf > 0:
                    cl = F(10).render(f'{conf:.0f}%', True, fc)
                    self._scr.blit(cl, (bx, by-14))

    # ── Panel helpers ─────────────────────────────────────────────────────

    def _panel_rect(self):
        """Returns (x, y, w, h) of the status panel."""
        return (self._panel_x, self._panel_y,
                self._panel_w, self._panel_h)

    def _panel_base(self, border_col, bg_alpha=12):
        """Draw base background for bottom panel."""
        px, py, pw, ph = self._panel_rect()
        bg = pygame.Surface((pw, ph), pygame.SRCALPHA)
        bg.fill((*border_col, bg_alpha))
        self._scr.blit(bg, (px, py))
        pygame.draw.rect(self._scr, border_col, (px, py, pw, ph), 1)
        # Corner brackets on panel too
        L = 14; T = 2
        for (cx2,cy2,sx,sy) in [
            (px,    py,    1, 1),(px+pw-L,py,   -1, 1),
            (px,    py+ph-L,1,-1),(px+pw-L,py+ph-L,-1,-1),
        ]:
            pygame.draw.line(self._scr, border_col,(cx2,cy2),(cx2+L*sx,cy2),T)
            pygame.draw.line(self._scr, border_col,(cx2,cy2),(cx2,cy2+L*sy),T)
        # Branding watermark
        bm = F(8, True).render('SECURA-9', True, (*border_col, 30))
        self._scr.blit(bm, (px + pw - bm.get_width() - 8, py + 4))
        return px, py, pw, ph

    def _cx(self):
        """Centre X of screen."""
        return self._w // 2

    def _panel_cx(self):
        """Centre X of the status panel (may differ from screen centre in landscape)."""
        return self._panel_x + self._panel_w // 2

    # ── Numpad helpers ────────────────────────────────────────────────────

    def _numpad_buttons(self):
        """Return list of (label, rect, action) for numpad keys.
        Button size adapts to available panel space."""
        px, py, pw, ph = self._panel_rect()
        cx = self._panel_cx()
        # Reserve space for digit boxes (~56px) and header text (~50px)
        avail_h = ph - 110
        gap = max(6, int(avail_h * 0.025))
        bh = max(40, (avail_h - 3 * gap) // 4)
        bw = bh
        grid_w = 3 * bw + 2 * gap
        gx = cx - grid_w // 2
        gy = py + 60
        keys = [
            '1', '2', '3',
            '4', '5', '6',
            '7', '8', '9',
            '◀', '0', '✓',
        ]
        buttons = []
        for i, label in enumerate(keys):
            row, col = divmod(i, 3)
            bx = gx + col * (bw + gap)
            by = gy + row * (bh + gap)
            rect = pygame.Rect(bx, by, bw, bh)
            action = 'digit' if label.isdigit() else ('back' if label == '◀' else 'submit')
            buttons.append((label, rect, action))
        return buttons

    def _handle_numpad_click(self, mx, my):
        """Handle mouse click on numpad when in OTP_ENTER state."""
        if self._state != self.OTP_ENTER:
            return
        for label, rect, action in self._numpad_buttons():
            if rect.collidepoint(mx, my):
                if action == 'digit' and len(self._otp_digits) < 6:
                    self._otp_digits += label
                elif action == 'back':
                    self._otp_digits = self._otp_digits[:-1]
                elif action == 'submit' and len(self._otp_digits) >= 4:
                    self._otp_submitted = True
                break

    def _draw_numpad(self, px, py, pw, ph):
        """Draw the numpad grid on the panel."""
        for label, rect, action in self._numpad_buttons():
            col = config.COL_CYAN
            if action == 'submit':
                col = config.COL_GREEN if len(self._otp_digits) >= 4 else config.COL_MUTED
            elif action == 'back':
                col = config.COL_YELLOW

            pygame.draw.rect(self._scr, config.COL_PANEL2, rect, 0, 6)
            pygame.draw.rect(self._scr, col, rect, 1, 6)
            if action == 'submit' and len(self._otp_digits) >= 4:
                pygame.draw.rect(self._scr, (*config.COL_GREEN, 30), rect, 0, 6)

            f = F(28, bold=True)
            ts = f.render(label, True, col)
            self._scr.blit(ts, (rect.centerx - ts.get_width() // 2,
                                 rect.centery - ts.get_height() // 2))

    # ═══════════════════════════════════════════════════════════════════════
    # PANELS
    # ═══════════════════════════════════════════════════════════════════════

    def _panel_boot(self):
        px, py, pw, ph = self._panel_base(config.COL_CYAN, 8)
        cx = self._panel_cx()

        # Rings inside panel
        ring_cy = py + ph//2
        draw_ring(self._scr, cx, ring_cy, 55, config.COL_CYAN,
                  self._t, speed=1.0, gap=80, thick=1, alpha=120)
        draw_ring(self._scr, cx, ring_cy, 38, config.COL_GREEN,
                  self._t, speed=-1.6, gap=60, thick=1, alpha=90)

        t = self._elapsed()
        lines = [
            ('▸ LOADING FACE DATABASE', config.COL_MUTED,  0.0),
            ('▸ CONNECTING TO SERVER',  config.COL_MUTED,  0.5),
            ('▸ CAMERA ONLINE',         config.COL_MUTED,  1.0),
            ('▸ SYSTEM READY',          config.COL_GREEN,  1.5),
        ]
        start_y = py + 20
        for ln, col, delay in lines:
            if t > delay:
                self._tl(ln, 11, col, px+16, start_y)
            start_y += 20

    # ──────────────────────────────────────────────────────────────────────

    def _panel_idle(self):
        """
        'Finding / Waiting for face...'  — matches sketch exactly.
        Shows when no face is detected.
        """
        px, py, pw, ph = self._panel_base(config.COL_CYAN, 6)
        cx = self._panel_cx()
        mid_y = py + ph//2

        # Subtle rotating ring in background
        draw_ring(self._scr, cx, mid_y, pw//2 - 20, config.COL_CYAN,
                  self._t, speed=0.3, gap=200, thick=1, alpha=25)

        # Main status text — pulsing
        p = self._pulse01(1.5)
        col = tuple(int(c * (0.6 + 0.4*p)) for c in config.COL_CYAN)

        self._tc('WAITING FOR FACE...',     20, col,              cx, mid_y-28, bold=True)
        self._tc('Scanning entrance...',    13, config.COL_MUTED, cx, mid_y+4)

        # Bengali
        self._bnc('মুখের জন্য অপেক্ষা করছি', 16, config.COL_MUTED, cx, mid_y+30)

        # Waveform at bottom of panel
        draw_waveform(self._scr, px+8, py+ph-28, pw-16, 22,
                      config.COL_CYAN, self._t, bars=36, active=False)

        # Known faces count bottom-right
        self._tr(f'DB: {self.known_face_count} FACES', 9,
                  config.COL_MUTED, px+pw-8, py+ph-34)

    # ──────────────────────────────────────────────────────────────────────

    def _panel_new_face(self):
        """Unknown face — ask for name."""
        px, py, pw, ph = self._panel_base(config.COL_RED, 14)
        cx = self._panel_cx()
        mid_y = py + ph//2

        # Blink border
        if int(self._t * 3) % 2 == 0:
            pygame.draw.rect(self._scr, config.COL_RED,
                              (px, py, pw, ph), 2)

        self._tc('UNKNOWN FACE',          18, config.COL_RED,    cx, py+20, bold=True)
        self._tc('NOT IN DATABASE',       10, config.COL_MUTED,  cx, py+42)

        # Animated mic icon
        mic_y = mid_y - 10
        mr = int(26 + 4*self._pulse01(6))
        draw_ring(self._scr, cx, mic_y, mr, config.COL_RED,
                  self._t, speed=4, gap=40, thick=2, alpha=int(180*self._pulse01(3)))
        pygame.draw.circle(self._scr, config.COL_DIM, (cx, mic_y), 20)
        pygame.draw.rect(self._scr, config.COL_RED, (cx-7, mic_y-16, 14, 22), 0, 4)
        pygame.draw.arc(self._scr, config.COL_RED,
                        (cx-12, mic_y+5, 24, 14), math.pi, 0, 2)
        pygame.draw.line(self._scr, config.COL_RED,
                          (cx, mic_y+19), (cx, mic_y+28), 2)

        self._tc('PLEASE SAY YOUR NAME',  13, config.COL_YELLOW, cx, mid_y+46)
        self._bnc('আপনার নাম বলুন',       16, config.COL_TEXT,   cx, mid_y+70)

        draw_waveform(self._scr, px+8, py+ph-28, pw-16, 22,
                      config.COL_RED, self._t, bars=36, active=True)

    # ──────────────────────────────────────────────────────────────────────

    def _panel_waiting(self):
        """Sending for approval — spinner."""
        px, py, pw, ph = self._panel_base(config.COL_YELLOW, 10)
        cx = self._panel_cx()
        mid_y = py + ph//2

        self._tc('SENDING FOR APPROVAL', 17, config.COL_YELLOW, cx, py+18, bold=True)
        self._tc('DASHBOARD REVIEWING',  10, config.COL_MUTED,  cx, py+40)

        # Name
        self._tc(f'"{self._person_name}"', 22, config.COL_TEXT, cx, py+68, bold=True)

        # Spinner rings
        draw_ring(self._scr, cx, mid_y+14, 34, config.COL_YELLOW,
                  self._t, speed=2.5, gap=60, thick=2, alpha=200)
        draw_ring(self._scr, cx, mid_y+14, 22, config.COL_CYAN,
                  self._t, speed=-3.5, gap=90, thick=1, alpha=150)
        pygame.draw.circle(self._scr, config.COL_YELLOW, (cx, mid_y+14), 5)

        self._bnc('অনুগ্রহ করে অপেক্ষা করুন', 15, config.COL_MUTED, cx, py+ph-44)

        draw_waveform(self._scr, px+8, py+ph-28, pw-16, 22,
                      config.COL_YELLOW, self._t, bars=36, active=False)

    # ──────────────────────────────────────────────────────────────────────

    def _panel_granted(self):
        """ACCESS GRANTED — green, animated."""
        px, py, pw, ph = self._panel_base(config.COL_GREEN, 18)
        cx = self._panel_cx()
        mid_y = py + ph//2

        # Full overlay pulse
        p = self._pulse01(2)
        ov = pygame.Surface((pw, ph), pygame.SRCALPHA)
        ov.fill((*config.COL_GREEN, int(10 + 8*p)))
        self._scr.blit(ov, (px, py))

        # Checkmark circle
        check_cy = py + 48
        r_ch = 32
        draw_ring(self._scr, cx, check_cy, r_ch+8, config.COL_GREEN,
                  self._t, speed=1.5, gap=30, thick=1, alpha=100)
        pygame.draw.circle(self._scr, config.COL_DIM, (cx, check_cy), r_ch)
        pygame.draw.circle(self._scr, config.COL_GREEN, (cx, check_cy), r_ch, 2)
        pts = [(cx-16, check_cy+2), (cx-4, check_cy+14), (cx+18, check_cy-12)]
        pygame.draw.lines(self._scr, config.COL_GREEN, False, pts, 4)

        self._tc('ACCESS GRANTED',    22, config.COL_GREEN, cx, py+100, bold=True)
        self._tc('WELCOME',           14, config.COL_TEXT,  cx, py+126)
        self._tc(self._person_name,   24, config.COL_GREEN, cx, py+152, bold=True)

        if self._confidence > 0:
            self._tc(f'{self._confidence:.0f}% match', 11,
                      config.COL_MUTED, cx, py+176)

        self._tc('DOOR UNLOCKING...', 13, config.COL_GREEN, cx, py+200)
        self._bnc('স্বাগতম — প্রবেশ অনুমোদিত', 16, config.COL_TEXT, cx, py+222)

        draw_waveform(self._scr, px+8, py+ph-28, pw-16, 22,
                      config.COL_GREEN, self._t, bars=36, active=True)

    # ──────────────────────────────────────────────────────────────────────

    def _panel_denied(self):
        """ACCESS DENIED — red."""
        px, py, pw, ph = self._panel_base(config.COL_RED, 18)
        cx = self._panel_cx()

        p = self._pulse01(3)
        ov = pygame.Surface((pw, ph), pygame.SRCALPHA)
        ov.fill((*config.COL_RED, int(8 + 8*p)))
        self._scr.blit(ov, (px, py))

        # X circle
        xc_cy = py + 50
        r_x = 30
        draw_ring(self._scr, cx, xc_cy, r_x+8, config.COL_RED,
                  self._t, speed=-2.0, gap=30, thick=1, alpha=100)
        pygame.draw.circle(self._scr, config.COL_DIM, (cx, xc_cy), r_x)
        pygame.draw.circle(self._scr, config.COL_RED, (cx, xc_cy), r_x, 2)
        pygame.draw.line(self._scr, config.COL_RED,
                          (cx-16, xc_cy-16), (cx+16, xc_cy+16), 4)
        pygame.draw.line(self._scr, config.COL_RED,
                          (cx+16, xc_cy-16), (cx-16, xc_cy+16), 4)

        self._tc('ACCESS DENIED',       22, config.COL_RED,   cx, py+98, bold=True)
        self._tc('UNAUTHORIZED ENTRY',  13, config.COL_TEXT,  cx, py+124)
        self._tc('DOOR STAYS LOCKED',   13, config.COL_RED,   cx, py+148)
        self._bnc('প্রবেশ অস্বীকৃত হয়েছে', 16, config.COL_TEXT, cx, py+174)

        draw_waveform(self._scr, px+8, py+ph-28, pw-16, 22,
                      config.COL_RED, self._t, bars=36, active=True)

    # ──────────────────────────────────────────────────────────────────────

    def _panel_nobody_home(self):
        """Nobody home — red warning."""
        px, py, pw, ph = self._panel_base(config.COL_RED, 14)
        cx = self._panel_cx()
        mid_y = py + ph//2

        p = self._pulse01(2)
        rc = (min(255, int(80 + 175*p)), 0, 20)

        self._tc('⚠  NOBODY HOME  ⚠',      20, rc,                cx, py+22, bold=True)
        self._bnc('কেউ বাড়িতে নেই',          18, config.COL_YELLOW, cx, mid_y-10)
        self._bnc('পরে আসুন',               16, config.COL_TEXT,   cx, mid_y+18)

        draw_ring(self._scr, cx, mid_y+44, 28, config.COL_RED,
                  self._t, speed=2, gap=50, thick=1, alpha=120)

        draw_waveform(self._scr, px+8, py+ph-28, pw-16, 22,
                      config.COL_RED, self._t, bars=36, active=True)

    # ──────────────────────────────────────────────────────────────────────

    def _panel_message(self):
        """Generic message — used for mic-open etc."""
        px, py, pw, ph = self._panel_base(config.COL_YELLOW, 10)
        cx = self._panel_cx()
        mid_y = py + ph//2

        # Mic icon with rings
        mic_cy = mid_y - 20
        draw_ring(self._scr, cx, mic_cy, 36, config.COL_YELLOW,
                  self._t, speed=3, gap=40, thick=2,
                  alpha=int(200*self._pulse01(4)))
        pygame.draw.circle(self._scr, config.COL_DIM, (cx, mic_cy), 24)
        pygame.draw.rect(self._scr, config.COL_YELLOW,
                          (cx-9, mic_cy-20, 18, 30), 0, 5)
        pygame.draw.arc(self._scr, config.COL_YELLOW,
                        (cx-16, mic_cy+8, 32, 18), math.pi, 0, 3)
        pygame.draw.line(self._scr, config.COL_YELLOW,
                          (cx, mic_cy+26), (cx, mic_cy+36), 2)

        # Message text
        words = self._message_text.split()
        line1 = ' '.join(words[:4])
        line2 = ' '.join(words[4:]) if len(words) > 4 else ''
        self._tc(line1, 15, config.COL_YELLOW, cx, mid_y+24, bold=True)
        if line2:
            self._tc(line2, 15, config.COL_YELLOW, cx, mid_y+46)

        self._tc('LISTENING...', 11, config.COL_MUTED, cx, mid_y+68)

        draw_waveform(self._scr, px+8, py+ph-28, pw-16, 22,
                      config.COL_YELLOW, self._t, bars=36, active=True)

    # ──────────────────────────────────────────────────────────────────────

    def _panel_otp_waiting(self):
        """OTP generated — show on screen + awaiting entry."""
        px, py, pw, ph = self._panel_base(config.COL_CYAN, 10)
        cx = self._panel_cx()
        mid_y = py + ph // 2

        self._tc('🔐  OTP GENERATED', 18, config.COL_CYAN, cx, py+18, bold=True)
        self._tc('CHECK YOUR PHONE',  11, config.COL_MUTED, cx, py+42)

        # Show OTP big
        self._tc(self._otp_display, 42, config.COL_GREEN, cx, mid_y-18, bold=True)

        remaining = max(0, int(self._otp_expiry - self._elapsed()))
        self._tc(f'Expires in {remaining}s', 13, config.COL_YELLOW, cx, mid_y+18)

        # Dots animation
        dots = '.' * (int(self._t * 2) % 4)
        self._tc(f'Waiting for entry{dots}', 12, config.COL_MUTED, cx, mid_y+46)

        draw_waveform(self._scr, px+8, py+ph-28, pw-16, 22,
                      config.COL_CYAN, self._t, bars=36, active=False)

    # ──────────────────────────────────────────────────────────────────────

    def _panel_otp_enter(self):
        """OTP entry screen with numpad."""
        px, py, pw, ph = self._panel_base(config.COL_CYAN, 10)
        cx = self._panel_cx()

        # Header
        self._tc('ENTER OTP',                 16, config.COL_CYAN,   cx, py+14, bold=True)
        self._tc(f'Attempt {self._otp_attempt}/{self._otp_max_att}',
                 11, config.COL_MUTED,          cx, py+32)
        self._tc(f'{self._otp_expiry}s remaining',
                 11, config.COL_YELLOW,         cx, py+44)

        # Adaptive digit boxes
        box_h = max(32, int(ph * 0.07))
        box_w = max(28, int(box_h * 0.8))
        gap = max(4, int(box_w * 0.2))
        box_y = py + 54
        total_w = 6 * box_w + 5 * gap
        box_x0 = cx - total_w // 2
        font_sz = max(16, box_h - 10)
        for i in range(6):
            bx = box_x0 + i * (box_w + gap)
            filled = i < len(self._otp_digits)
            col = config.COL_GREEN if filled else config.COL_MUTED
            pygame.draw.rect(self._scr, config.COL_PANEL2, (bx, box_y, box_w, box_h), 0, 4)
            pygame.draw.rect(self._scr, col, (bx, box_y, box_w, box_h), 1, 4)
            if filled:
                self._tc(self._otp_digits[i], font_sz, config.COL_GREEN,
                         bx + box_w // 2, box_y + box_h // 2, bold=True)
            elif int(self._t * 3) % 2 == 0:
                pygame.draw.line(self._scr, config.COL_CYAN,
                                 (bx + 4, box_y + box_h - 4),
                                 (bx + box_w - 4, box_y + box_h - 4), 2)

        # Numpad grid
        self._draw_numpad(px, py, pw, ph)

        draw_waveform(self._scr, px+8, py+ph-28, pw-16, 22,
                      config.COL_CYAN, self._t, bars=36, active=True)

    # ──────────────────────────────────────────────────────────────────────

    def _panel_otp_wrong(self):
        """Wrong OTP feedback."""
        px, py, pw, ph = self._panel_base(config.COL_RED, 18)
        cx = self._panel_cx()
        mid_y = py + ph // 2

        p = self._pulse01(3)
        ov = pygame.Surface((pw, ph), pygame.SRCALPHA)
        ov.fill((*config.COL_RED, int(8 + 8 * p)))
        self._scr.blit(ov, (px, py))

        self._tc('✗  WRONG OTP', 22, config.COL_RED, cx, mid_y-20, bold=True)
        self._tc(f'{self._otp_retries} attempt{"s" if self._otp_retries != 1 else ""} remaining',
                 14, config.COL_YELLOW, cx, mid_y+12)

        draw_waveform(self._scr, px+8, py+ph-28, pw-16, 22,
                      config.COL_RED, self._t, bars=36, active=True)

    # ──────────────────────────────────────────────────────────────────────

    def _panel_otp_expired(self):
        """OTP expired."""
        px, py, pw, ph = self._panel_base(config.COL_RED, 14)
        cx = self._panel_cx()
        mid_y = py + ph // 2

        self._tc('⏱  OTP EXPIRED', 20, config.COL_RED, cx, mid_y-14, bold=True)
        self._tc('Please request a new code', 13, config.COL_TEXT, cx, mid_y+16)

        draw_waveform(self._scr, px+8, py+ph-28, pw-16, 22,
                      config.COL_RED, self._t, bars=36, active=True)
