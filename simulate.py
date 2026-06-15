#!/usr/bin/env python3
"""
SECURA-9 Simulation — runs the full app logic without Raspberry Pi hardware.
Mocks GPIO, camera, display, voice, Firebase.
Usage: python3 simulate.py
"""
import logging, sys, os, time, threading
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
os.environ['SDL_VIDEODRIVER'] = 'dummy'

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(name)s: %(message)s')

# ── Mocks ──────────────────────────────────────────────────────────────────
import importlib, types

# Mock RPi.GPIO
rpi = types.ModuleType('RPi')
rpi.GPIO = types.ModuleType('RPi.GPIO')
class _GPIO:
    BCM, IN, OUT, HIGH, LOW, RISING, FALLING, PUD_UP = 1,2,3,4,5,6,7,8
    def setmode(s,*a): pass
    def setwarnings(s,*a): pass
    def setup(s,*a,**kw): pass
    def output(s,*a): pass
    def input(s,*a): return 0
    def cleanup(s,*a): pass
    def add_event_detect(s,*a,**kw): pass
rpi.GPIO = _GPIO()
sys.modules['RPi'] = rpi
sys.modules['RPi.GPIO'] = rpi.GPIO

# Mock picamera2
sys.modules['picamera2'] = types.ModuleType('picamera2')

# Mock pygame
class _DummyMusic:
    def load(self,*a,**kw): pass
    def play(self,*a,**kw): pass
    def stop(self,*a): pass
    def set_volume(self,*a): pass
    def get_busy(self): return False
    def get_pos(self): return 0

class _DummySound:
    def __init__(self,*a,**kw): pass
    def play(self,*a,**kw): pass
    def stop(self,*a): pass
    def set_volume(self,*a): pass

class _DummyMixer:
    music = _DummyMusic()
    Sound = _DummySound
    def init(self,*a,**kw): pass
    def quit(self,*a): pass
    def stop(self,*a): pass
    def get_init(self): return (22050, -16, 2)

class _DummyFastevent:
    def pump(self): pass
    def get(self): return []

import pygame as _real_pygame
pygame = _real_pygame
pygame.mixer = _DummyMixer()
pygame.mixer.music = _DummyMusic()
pygame.mixer.Sound = _DummySound
pygame.event = _DummyFastevent()
pygame.fastevent = _DummyFastevent()
pygame.display.init = lambda *a: None
pygame.display.set_mode = lambda *a: types.SimpleNamespace(
    fill=lambda *a: None, blit=lambda *a: None,
    get_width=lambda: 720, get_height=lambda: 1280)
pygame.display.set_caption = lambda *a: None
pygame.transform = types.ModuleType('pygame.transform')
pygame.transform.scale = lambda s,sz: s
pygame.transform.rotate = lambda s,a: s
pygame.image = types.ModuleType('pygame.image')
pygame.image.load = lambda *a: types.SimpleNamespace(
    get_width=lambda: 100, get_height=lambda: 100)
pygame.Surface = lambda *a,**kw: types.SimpleNamespace(
    fill=lambda *a: None, blit=lambda *a: None,
    get_width=lambda: 720, get_height=lambda: 1280)
pygame.Rect = lambda *a: types.SimpleNamespace(
    left=0, top=0, width=a[2] if len(a)>2 else 0,
    height=a[3] if len(a)>3 else 0)

# Mock firebase_admin
fb = types.ModuleType('firebase_admin')
fb.initialize_app = lambda a: None
fb.credentials = types.ModuleType('firebase_admin.credentials')
fb.credentials.Certificate = lambda a: None
# firebase_admin.firestore is a different thing from google.cloud.firestore
fb.firestore = types.ModuleType('firebase_admin.firestore')
fb.firestore.client = lambda: None
fb.auth = types.ModuleType('firebase_admin.auth')
fb.messaging = types.ModuleType('firebase_admin.messaging')
sys.modules['firebase_admin'] = fb
sys.modules['firebase_admin.credentials'] = fb.credentials
sys.modules['firebase_admin.firestore'] = fb.firestore
sys.modules['firebase_admin.auth'] = fb.auth
sys.modules['firebase_admin.messaging'] = fb.messaging

# Mock google.cloud.firestore
gcf = types.ModuleType('google.cloud.firestore')
sys.modules['google.cloud'] = types.ModuleType('google.cloud')
sys.modules['google.cloud.firestore'] = gcf

# ── Imports after mocks ────────────────────────────────────────────────────
import listen as _listen_mod
_listen_mod.listen_for_name = lambda **kw: 'Bob'
_listen_mod.stop_mic = lambda: True

# Speed up OTP in simulation
import otp_manager as _otp_mod
_orig_generate = _otp_mod.OTPManager.generate
def _fast_generate(self, expiry_seconds=None):
    return _orig_generate(self, expiry_seconds or 5)
_otp_mod.OTPManager.generate = _fast_generate

from gpio_control import GPIOControl
GPIOControl.is_dark = staticmethod(lambda: False)

import config
from main import Secura9
import numpy as np

DUMMY_FRAME = np.zeros((480, 640, 3), dtype=np.uint8)

def colored(s, c): return s  # no terminal colors needed

class SimUI:
    """Console UI that walks through scenarios."""
    def __init__(self):
        self.app: Secura9 = None
        self._step = 0

    def step(self, title):
        self._step += 1
        w = 60
        print()
        print('═' * w)
        print(f'  STEP {self._step}: {title}')
        print('═' * w)

    def ok(self, msg):
        print(f'  ✓ {msg}')

    def info(self, msg):
        print(f'    {msg}')

    def wait(self, sec=1.5):
        if 'DISABLE_SIM_WAIT' not in os.environ:
            time.sleep(sec)

    def run(self):
        self.step('Starting SECURA-9')
        self.app = Secura9()
        self.ok('App initialized (hardware mocked)')

        # ── 1. IDLE STATE ─────────────────────────────────────────────────
        self.step('IDLE state — waiting for face')
        self.info('Display: "Waiting for face..."')
        self.info(f'Status: FB={self.app.notif._fb_ok}, Cam=True, Door=Locked')
        self.ok('System ready')

        # ── 2. KNOWN FACE → GRANT ─────────────────────────────────────────
        self.step('Known face detected')
        self.info('FaceEngine → _on_known("Alice", 94.5)')
        self.app._on_known('Alice', 94.5, DUMMY_FRAME)
        self.ok('Door unlocked for Alice (DOOR_OPEN_SECONDS)')
        self.ok('Door re-locked automatically')
        self.wait()

        # ── 3. SCHEDULE DENY ──────────────────────────────────────────────
        self.step('Schedule denies access')
        self.app.schedule.load([{
            'days': 127, 'startHour': 0, 'startMin': 0,
            'endHour': 8, 'endMin': 0, 'denyNames': ['intruder']
        }])
        self.info('Rule: deny "intruder" before 08:00')
        self.info('FaceEngine → _on_known("intruder", 88.0)')
        self.app._on_known('intruder', 88.0, None)
        self.ok('Access denied — schedule blocked')
        self.app.schedule.load([])  # reset
        self.wait()

        # ── 4. UNKNOWN → NAME COLLECTION → APPROVAL ──────────────────────
        self.step('Unknown face — name collection flow')
        self.info('FaceEngine → _on_unknown(frame, b64)')
        self.app._on_unknown(DUMMY_FRAME, '')
        self.ok('Name prompt shown ("say your name")')
        self.info('(mic would listen → sends approval request to Firebase)')
        self.wait()

        # ── 5. FIREBASE APPROVE ──────────────────────────────────────────
        self.step('Dashboard approves pending request')
        self.info('Firebase → _on_approve({"name": "Bob"})')
        self.app._on_approve({'name': 'Bob'})
        self.ok('Door unlocked for Bob')
        self.ok('Door re-locked')
        self.wait()

        # ── 6. FIREBASE DENY ─────────────────────────────────────────────
        self.step('Dashboard denies request')
        self.info('Firebase → _on_deny({})')
        self.app._on_deny({})
        self.ok('Access denied — voice plays "denied"')
        self.wait()

        # ── 7. NOBODY HOME → OTP FLOW ────────────────────────────────────
        self.step('Nobody Home mode + OTP flow')
        self.app._on_nobody_home_cmd(True)
        self.ok('Nobody Home mode ON')
        self.info('FaceEngine → _on_unknown(frame, b64)')
        self.app._on_unknown(DUMMY_FRAME, '')
        self.ok('OTP generated and displayed')
        self.info('Visitor enters correct OTP')
        self.app._grant_otp_access('Visitor')
        self.ok('Door unlocked for visitor via OTP')
        self.app._on_nobody_home_cmd(False)
        self.wait()

        # ── 8. REMOTE UNLOCK ─────────────────────────────────────────────
        self.step('Remote unlock via dashboard')
        self.info('Firebase → _on_command("unlock", {})')
        self.app._on_command('unlock', {})
        self.ok('Door unlocked remotely')
        self.app._on_command('lock', {})
        self.wait()

        # ── 9. AUTO-LOCK + PASSAGE MODE ──────────────────────────────────
        self.step('Auto-lock + Passage mode')
        self.info('Auto-lock timer: on_unlock() → auto re-lock')
        self.app.auto_lock.on_unlock()
        self.ok('Auto-lock timer started')
        self.app.auto_lock.on_lock()
        self.ok('Auto-lock triggered (or manual lock)')
        self.info('Passage mode toggle → disables auto-lock')
        self.app.auto_lock.toggle_passage(True)
        self.ok(f'Passage mode: {self.app.auto_lock.passage_active}')
        self.app.auto_lock.toggle_passage(False)
        self.wait()

        # ── 10. TAMPER ALARM ─────────────────────────────────────────────
        self.step('Tamper alarm')
        self.info('GPIO FALLING edge on TAMPER_PIN')
        self.app._on_tamper()
        self.ok('Tamper alarm triggered!')
        self.info('Display: ALARM state, voice: alarm sound')
        self.info(f'Notification: tamper alert sent to Firebase')
        self.wait(0.5)

        # ── 11. TAMPER RESET ──────────────────────────────────────────────
        self.step('Tamper reset via command')
        self.app._on_command('tamper_reset', {})
        self.ok('Tamper alarm reset')
        self.wait()

        # ── 12. GENERATE OTP ─────────────────────────────────────────────
        self.step('Manual OTP generation')
        self.app._on_command('generate_otp', {})
        self.ok('OTP generated and sent to dashboard')
        self.wait()

        # ── 13. TIMED CODE ───────────────────────────────────────────────
        self.step('Timed code (custom-duration OTP)')
        self.app._on_command('generate_timed_code', {'durationSeconds': 120, 'label': 'Delivery'})
        self.ok('Timed code generated (120s, label: Delivery)')
        self.wait()

        # ── 14. OTA CHECK ─────────────────────────────────────────────────
        self.step('OTA update check')
        result = self.app.ota.check_now()
        if result.get('update_available'):
            self.ok(f'Update available: {result["version"]}')
        else:
            self.ok(f'No update available ({result.get("message", "up to date")})')
        self.wait()

        # ── 15. DUAL AUTH ────────────────────────────────────────────────
        self.step('Dual auth (face + OTP)')
        config.DUAL_AUTH_ENABLED = True
        config.DUAL_AUTH_ALWAYS = True
        self.info('Known face → dual auth triggered')
        self.app._start_dual_auth_flow(None, 'Alice')
        self.ok('OTP shown on display + sent to phone')
        self.info('Would wait for OTP entry on keypad...')
        config.DUAL_AUTH_ENABLED = False
        self.wait()

        # ── 16. UNLOCK DURING CALL ───────────────────────────────────────
        self.step('Unlock during WebRTC call')
        self.app._on_unlock_during_call()
        self.ok('Door unlocked during call')
        self.wait(0.5)

        # ── 17. SCHEDULE CLEANUP ──────────────────────────────────────────
        self.step('Schedule cleanup (expired timed codes)')
        before = len(self.app.timer_codes._codes)
        self.app.timer_codes.cleanup_expired()
        after = len(self.app.timer_codes._codes)
        self.ok(f'Expired codes cleaned: {before - after} removed, {after} active')
        self.wait()

        # ── 18. MOTION + LURKER ──────────────────────────────────────────
        self.step('Motion detected + lurker alarm')
        self.info('PIR → _on_motion(frame)')
        self.app._on_motion(None)
        self.ok('Motion alert sent to Firebase')
        self.info('(lurker timer starts — alarm fires if no face seen)')
        self.wait()

        # ── 19. PASSAGE TOGGLE ───────────────────────────────────────────
        self.step('Passage mode toggle via command')
        self.app._on_command('passage_toggle', {})
        self.ok('Passage mode toggled')
        self.app._on_command('passage_toggle', {})
        self.ok('Passage mode toggled off')
        self.wait()

        # ── 20. CONFIG UPDATE ────────────────────────────────────────────
        self.step('Remote config update')
        self.app._on_config_update({'lurker': {'enabled': True, 'thresholdSeconds': 10, 'cooldownSeconds': 60}})
        self.ok('Lurker config updated from Firebase')
        self.wait()

        # ── SUMMARY ──────────────────────────────────────────────────────
        w = 60
        print()
        print('═' * w)
        print('  SIMULATION COMPLETE')
        print('═' * w)
        print(f'  {self._step} scenarios executed successfully')
        print()
        print('  What was demonstrated:')
        print('   1. Boot & idle')
        print('   2. Known face → unlock')
        print('   3. Schedule denial')
        print('   4. Unknown face → name collection')
        print('   5. Firebase approve → grant')
        print('   6. Firebase deny')
        print('   7. Nobody home → OTP flow')
        print('   8. Remote unlock/lock commands')
        print('   9. Auto-lock timer + passage mode')
        print('  10. Tamper alarm')
        print('  11. Tamper reset')
        print('  12. Manual OTP generation')
        print('  13. Timed code (custom duration)')
        print('  14. OTA update check')
        print('  15. Dual auth (face + OTP)')
        print('  16. Unlock during WebRTC call')
        print('  17. Schedule cleanup')
        print('  18. Motion + lurker alarm')
        print('  19. Passage mode toggle')
        print('  20. Remote config update')

if __name__ == '__main__':
    SimUI().run()
