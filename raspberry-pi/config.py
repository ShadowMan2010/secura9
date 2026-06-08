"""
SECURA-9 Configuration — all settings in one place.
"""
import os

# ── SERVER ────────────────────────────────────────────────────────────────
SERVER_IP       = '127.0.0.1'   # localhost for testing
SERVER_PORT     = 3000
SERVER_WS_URL   = f'ws://{SERVER_IP}:{SERVER_PORT}/ws?type=device'
SERVER_HTTP_URL = f'http://{SERVER_IP}:{SERVER_PORT}'

# ── CAMERA ────────────────────────────────────────────────────────────────
CAMERA_INDEX    = 1
CAMERA_WIDTH    = 640
CAMERA_HEIGHT   = 480
CAMERA_FPS      = 30
FLIP_HORIZONTAL = True    # mirror like a selfie cam
PROCESS_EVERY_N = 4       # run recognition every Nth frame (saves CPU)

# ── FACE RECOGNITION ──────────────────────────────────────────────────────
FACES_DIR             = 'faces'
RECOGNITION_TOLERANCE = 0.50   # 0.40 = very strict, 0.55 = relaxed
MIN_FACE_SIZE         = 55     # px — ignore tiny/far faces

# How many consecutive unrecognised frames before "unknown" triggers
UNKNOWN_HOLD_FRAMES   = 10

# How many consecutive un-encodable frames before "show face" triggers
# Higher = more tolerant of slight head movements (was causing false positives)
LOOKASIDE_HOLD_FRAMES = 18    # ~18 × PROCESS_EVERY_N frames ≈ 2–3 seconds

# Cooldown between "please face camera" announcements (seconds)
LOOKASIDE_COOLDOWN    = 8

# Cooldown between full recognition triggers (seconds)
TRIGGER_COOLDOWN      = 14

# ── DOOR ──────────────────────────────────────────────────────────────────
DOOR_RELAY_PIN    = 17
DOOR_OPEN_SECONDS = 5
RELAY_ACTIVE_LOW  = True

# ── PIR SENSOR (optional) ─────────────────────────────────────────────────
PIR_ENABLED = False
PIR_PIN     = 24

# ── SPEECH ────────────────────────────────────────────────────────────────
MIC_DEVICE_INDEX = None
MIC_ALSA_DEVICE  = ''     # e.g. 'plughw:1,0' — empty = auto-detect
LISTEN_TIMEOUT   = 12     # seconds — increased so person has time to speak
SPEECH_LANG      = 'bn-IN'

# ── AUDIO ─────────────────────────────────────────────────────────────────
SOUNDS_DIR   = 'sounds'
TTS_LANG     = 'bn'
AUDIO_VOLUME = 0.9

# ── CAMERA STREAMING ──────────────────────────────────────────────────────
STREAM_CAMERA_TO_DECK = True   # stream frames to Web Deck browser

# ── DISPLAY ───────────────────────────────────────────────────────────────
# Initial window size — orientation is auto-detected at runtime.
# On Pi portrait display use 720×1280; on laptop use 1280×720.
DISPLAY_WIDTH   = 720
DISPLAY_HEIGHT  = 1280
FULLSCREEN      = True         # True on Pi with monitor, False for testing
SHOW_FPS        = True

# ── COLOURS ───────────────────────────────────────────────────────────────
COL_BG     = (3,    8,   16)
COL_PANEL  = (7,   15,   28)
COL_PANEL2 = (10,  21,   40)
COL_CYAN   = (0,  255,  229)
COL_GREEN  = (0,  255,  136)
COL_RED    = (255,  0,   60)
COL_YELLOW = (255, 230,   0)
COL_TEXT   = (184, 221,  240)
COL_MUTED  = (58,   96,  128)
COL_DIM    = (10,   24,   40)
COL_WHITE  = (255, 255,  255)

# ── OTP ───────────────────────────────────────────────────────────────────
# OTP is triggered ONLY when Nobody Home mode is ON + unknown face detected
OTP_EXPIRY_SECONDS  = 90    # how long OTP stays valid
OTP_MAX_ATTEMPTS    = 3     # wrong attempts before lockout
OTP_LOCKOUT_SECONDS = 120   # lockout duration after max wrong attempts

# When Nobody Home is ON, known faces send an approval request via Firebase
# instead of auto-opening the door. Set False to fall back to OTP for known faces too.
KNOWN_FACE_BYPASS_NOBODY_HOME = True

# ── PATHS ─────────────────────────────────────────────────────────────────
BASE_DIR    = os.path.dirname(os.path.abspath(__file__))
FACES_PATH  = os.path.join(BASE_DIR, FACES_DIR)
SOUNDS_PATH = os.path.join(BASE_DIR, SOUNDS_DIR)
LOGS_PATH   = os.path.join(BASE_DIR, 'logs')

for _d in [FACES_PATH, SOUNDS_PATH, LOGS_PATH]:
    os.makedirs(_d, exist_ok=True)
