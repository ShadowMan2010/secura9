"""
SECURA-9 Configuration — all settings in one place.
Override priority: /etc/secura9/config.json > env var SECURA9_* > defaults below
"""
from __future__ import annotations
import json
import os

# ── Config file paths (checked at import time) ──────────────────────────
CONFIG_DIR      = '/etc/secura9'
CONFIG_PATH     = os.path.join(CONFIG_DIR, 'config.json')
FIREBASE_PATH   = os.path.join(CONFIG_DIR, 'firebase.json')
DEVICE_ID_PATH  = os.path.join(CONFIG_DIR, 'device_id')

# PROVISIONED = both config.json and firebase.json exist
PROVISIONED = os.path.exists(CONFIG_PATH) and os.path.exists(FIREBASE_PATH)

# ── SERVER ────────────────────────────────────────────────────────────────
SERVER_IP       = '127.0.0.1'
SERVER_PORT     = 3000
SERVER_WS_URL   = f'ws://{SERVER_IP}:{SERVER_PORT}/ws?type=device'
SERVER_HTTP_URL = f'http://{SERVER_IP}:{SERVER_PORT}'

DEVICE_ID       = 'secura9_pi_01'

# ── CAMERA ────────────────────────────────────────────────────────────────
CAMERA_SOURCE    = 0
CAMERA_WIDTH     = 640
CAMERA_HEIGHT    = 480
CAMERA_FPS       = 30
FLIP_HORIZONTAL  = True
PROCESS_EVERY_N  = 4

# ── FACE RECOGNITION ──────────────────────────────────────────────────────
FACES_DIR             = 'faces'
RECOGNITION_TOLERANCE = 0.50
MIN_FACE_SIZE         = 55
UNKNOWN_HOLD_FRAMES   = 10
LOOKASIDE_HOLD_FRAMES = 18
LOOKASIDE_COOLDOWN    = 8
TRIGGER_COOLDOWN      = 14

# ── DOOR ──────────────────────────────────────────────────────────────────
DOOR_RELAY_PIN  = 17
DOOR_OPEN_SECONDS = 5
RELAY_ACTIVE_LOW = True

# ── PIR SENSOR ────────────────────────────────────────────────────────────
PIR_ENABLED = False
PIR_PIN     = 24

# ── SPEECH ────────────────────────────────────────────────────────────────
MIC_DEVICE_INDEX = None
MIC_ALSA_DEVICE  = ''
LISTEN_TIMEOUT   = 12
SPEECH_LANG      = 'bn-IN'

# ── AUDIO ─────────────────────────────────────────────────────────────────
SOUNDS_DIR   = 'sounds'
TTS_LANG     = 'bn'
AUDIO_VOLUME = 0.9

# ── CAMERA STREAMING ──────────────────────────────────────────────────────
STREAM_CAMERA_TO_DECK = True

# ── DISPLAY ───────────────────────────────────────────────────────────────
DISPLAY_WIDTH  = 720
DISPLAY_HEIGHT = 1280
FULLSCREEN     = True
SHOW_FPS       = True
HEADLESS       = False    # True = no display, run as daemon

# ── COLOURS ───────────────────────────────────────────────────────────────
COL_BG     = (10,   2,   16)
COL_PANEL  = (20,   5,   35)
COL_PANEL2 = (30,  10,   50)
COL_CYAN   = (0,  255,  255)
COL_GREEN  = (57, 255,   20)
COL_RED    = (255,  0,  127)
COL_YELLOW = (255, 255,   0)
COL_PURPLE = (188, 19,  254)
COL_TEXT   = (200, 230,  255)
COL_MUTED  = (90,   80,  120)
COL_DIM    = (15,    5,   25)
COL_WHITE  = (255, 255, 255)

# ── OTP ───────────────────────────────────────────────────────────────────
OTP_EXPIRY_SECONDS         = 90
OTP_MAX_ATTEMPTS           = 3
OTP_LOCKOUT_SECONDS        = 120
KNOWN_FACE_BYPASS_NOBODY_HOME = True

# ── LURKER ALARM ──────────────────────────────────────────────────────────
LURKER_ALARM_SECONDS   = 30
LURKER_ALARM_ENABLED   = True
LURKER_POST_ALARM_CD   = 60

# ── PATHS (computed from defaults, may be overridden below) ──────────────
BASE_DIR     = os.path.dirname(os.path.abspath(__file__))
FACES_PATH   = os.path.join(BASE_DIR, FACES_DIR)
SOUNDS_PATH  = os.path.join(BASE_DIR, SOUNDS_DIR)
LOGS_PATH    = os.path.join(BASE_DIR, 'logs')


# ════════════════════════════════════════════════════════════════════════
# OVERRIDE LOADER — reads config.json + env vars
# ════════════════════════════════════════════════════════════════════════

_FILE_CFG = {}
if os.path.exists(CONFIG_PATH):
    try:
        with open(CONFIG_PATH) as _f:
            _FILE_CFG = json.load(_f)
    except (json.JSONDecodeError, PermissionError):
        pass

# If firebase.json exists, point Firebase adapter to it
if PROVISIONED:
    os.environ.setdefault('GOOGLE_APPLICATION_CREDENTIALS', FIREBASE_PATH)

# Read device_id
if os.path.exists(DEVICE_ID_PATH):
    try:
        with open(DEVICE_ID_PATH) as _f:
            _id = _f.read().strip()
            if _id:
                DEVICE_ID = _id
    except PermissionError:
        pass

# Device ID from config.json
if not PROVISIONED:
    _did = _FILE_CFG.get('device_id', '')
    if _did:
        DEVICE_ID = _did


def _apply_overrides(src, prefix=''):
    """Apply flat or nested overrides to module globals."""
    _g = globals()
    for _key, _val in src.items():
        _py_key = prefix + _key.upper()
        if _py_key in _g:
            _existing = _g[_py_key]
            if isinstance(_existing, bool):
                _g[_py_key] = str(_val).lower() in ('true', '1', 'yes')
            elif isinstance(_existing, int):
                _g[_py_key] = int(_val)
            elif isinstance(_existing, float):
                _g[_py_key] = float(_val)
            elif isinstance(_existing, tuple):
                _g[_py_key] = tuple(int(x.strip()) for x in str(_val).strip('()').split(','))
            else:
                _g[_py_key] = _val


# Apply flat config.json overrides (top-level keys like "camera_source")
_apply_overrides(_FILE_CFG)

# Apply nested config.json overrides (e.g. {"camera": {"source": 0}} )
for _section, _values in _FILE_CFG.items():
    if isinstance(_values, dict):
        _apply_overrides(_values, prefix=_section.upper() + '_')

# Apply env var overrides (SECURA9_DISPLAY_WIDTH=1024)
for _env_key, _env_val in os.environ.items():
    if _env_key.startswith('SECURA9_'):
        _py_key = _env_key[8:]
        if _py_key in globals():
            _existing = globals()[_py_key]
            if isinstance(_existing, bool):
                globals()[_py_key] = _env_val.lower() in ('true', '1', 'yes')
            elif isinstance(_existing, int):
                globals()[_py_key] = int(_env_val)
            elif isinstance(_existing, float):
                globals()[_py_key] = float(_env_val)
            elif isinstance(_existing, tuple):
                globals()[_py_key] = tuple(int(x.strip()) for x in _env_val.strip('()').split(','))
            else:
                globals()[_py_key] = _env_val

# Recompute derived values after overrides
SERVER_WS_URL   = f'ws://{SERVER_IP}:{SERVER_PORT}/ws?type=device'
SERVER_HTTP_URL = f'http://{SERVER_IP}:{SERVER_PORT}'
FACES_PATH      = os.path.join(BASE_DIR, FACES_DIR)
SOUNDS_PATH     = os.path.join(BASE_DIR, SOUNDS_DIR)
LOGS_PATH       = os.path.join(BASE_DIR, 'logs')

# ── Ensure data dirs exist ────────────────────────────────────────────────
for _d in [FACES_PATH, SOUNDS_PATH, LOGS_PATH]:
    os.makedirs(_d, exist_ok=True)

# Cleanup internal helpers
del _FILE_CFG, _apply_overrides
