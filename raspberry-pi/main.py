#!/usr/bin/env python3
"""
SECURA-9 — Main with OTP + Firebase + WebRTC

Flow when Nobody Home = ON:
  Known face  → Approval request via Firebase → Android app / web-deck
  Unknown face → OTP generated → sent via Firebase → visitor taps OTP on numpad → validated

Flow when Nobody Home = OFF:
  Known face  → door opens immediately
  Unknown face → name collected → sent to dashboard for approval
"""

import threading
import time
import logging
import signal
import sys
import os

import cv2

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import config
from face_engine  import FaceEngine
from gpio_control import GPIOControl
from voice        import Voice
from ui.display   import Display
from otp_manager  import OTPManager
from firebase_adapter.notifier import Notifier
from webrtc_broadcaster import WebRTCThread
import listen

os.makedirs('logs', exist_ok=True)
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler('logs/secura9.log'),
    ]
)
log = logging.getLogger('main')

shutdown = threading.Event()
signal.signal(signal.SIGINT,  lambda s, f: shutdown.set())
signal.signal(signal.SIGTERM, lambda s, f: shutdown.set())


class Secura9:

    def __init__(self):
        log.info('SECURA-9 starting...')

        self.gpio    = GPIOControl()
        self.voice   = Voice()
        self.display = Display()
        self.otp     = OTPManager()

        self.engine = FaceEngine(
            on_known     = self._on_known,
            on_unknown   = self._on_unknown,
            on_nobody    = self._on_nobody,
            on_lookaside = self._on_lookaside,
            on_motion    = self._on_motion,
            display      = self.display,
        )

        # Unified notifier (Firebase only)
        self.notif = Notifier()

        self.webrtc = None

        # Nobody Home state
        self._nobody_home   = False
        self._nobody_ann_t  = 0.0
        self._NOBODY_CD     = 15    # seconds between nobody-home announcements

        # OTP lockout
        self._otp_attempts  = 0
        self._otp_lockout_t = 0.0
        self._otp_done = threading.Event()

        # Processing lock — prevents restarting the same flow repeatedly
        self._processing    = False
        self._processing_t  = 0.0
        self._PROCESS_CD    = 8      # seconds before another flow can start

    # ── FACE CALLBACKS ────────────────────────────────────────────────────

    def _on_known(self, name: str, confidence: float, frame):
        """Known face from database."""

        if self._processing:
            return

        if self._nobody_home:
            self._processing = True
            log.info(f'Known face {name} — nobody home → waiting for Firebase approval')
            self.display.show_message(f'Waiting approval for {name}')
            self.voice.play('wait_approval')
            _, buf = cv2.imencode('.jpg', frame)
            self.notif.send_approval_request(name, confidence, buf.tobytes())
            return

        log.info(f'Known: {name}  {confidence:.1f}%')
        self.display.show_granted(name, confidence)
        self.voice.play('welcome')
        self.gpio.unlock_door()
        self.display.set_status(door_locked=False)
        time.sleep(config.DOOR_OPEN_SECONDS)
        self.gpio.lock_door()
        self.display.set_status(door_locked=True)
        self.engine.reset_state()
        self.display.show_idle()

    def _on_unknown(self, frame, frame_b64: str):
        """Unknown face detected."""
        if self._processing:
            return
        self._processing = True

        if self._nobody_home:
            log.info('Unknown face + nobody home → starting OTP flow')
            self._start_otp_flow(frame, frame_b64)
        else:
            log.info('Unknown face → name collection flow')
            self._start_name_approval_flow(frame, frame_b64)

    def _on_nobody(self):
        if self._processing:
            return
        if self._nobody_home:
            self.display.show_nobody_home()
        else:
            self.display.show_idle()

    def _on_lookaside(self):
        if self._processing:
            return
        self.voice.play('show_face')
        self.display.show_message('Please face the camera')

    # ── MOTION DETECTED ─────────────────────────────────────────────────

    def _on_motion(self, frame):
        log.info('Motion detected')
        self.notif.send_motion_detected()

    # ── OTP FLOW ──────────────────────────────────────────────────────────

    def _start_otp_flow(self, frame, frame_b64: str, visitor_name: str = ''):

        # Check lockout
        if time.time() - self._otp_lockout_t < config.OTP_LOCKOUT_SECONDS:
            remaining = int(config.OTP_LOCKOUT_SECONDS - (time.time() - self._otp_lockout_t))
            log.warning(f'OTP lockout active — {remaining}s remaining')
            self.display.show_message(f'LOCKED OUT — wait {remaining}s')
            self.voice.play('denied')
            return

        # Generate OTP
        otp = self.otp.generate()
        self._otp_attempts = 0
        self._otp_done.clear()

        log.info(f'OTP generated: {otp}')

        # Show OTP screen on device
        self.display.show_otp_waiting(otp, config.OTP_EXPIRY_SECONDS)

        # Announce in Bengali
        self.voice.play_sync('nobody_home')
        self.voice.play_sync('otp_sent')

        # Send OTP via Firebase
        self.notif.send_otp(otp, config.OTP_EXPIRY_SECONDS)

        # Wait for visitor to enter OTP on numpad
        self._wait_for_otp(frame_b64, visitor_name)

    def _wait_for_otp(self, frame_b64: str, visitor_name: str = ''):
        """Wait for OTP via on-screen numpad — retry up to OTP_MAX_ATTEMPTS times."""

        for attempt in range(1, config.OTP_MAX_ATTEMPTS + 1):
            remaining = self.otp.seconds_remaining()
            if remaining <= 0:
                log.info('OTP expired before entry')
                self.display.show_otp_expired()
                self.voice.play_sync('otp_expired')
                self.notif.send_otp_failed('expired')
                self.engine.reset_state()
                self._nobody_home_idle()
                return

            log.info(f'OTP attempt {attempt}/{config.OTP_MAX_ATTEMPTS} '
                     f'({remaining}s remaining)')

            self.display.reset_otp_input()
            self.display.show_otp_enter(attempt, config.OTP_MAX_ATTEMPTS,
                                         self.otp.seconds_remaining())

            self.voice.play_sync('otp_sent')

            # Poll for numpad input
            digits = ''
            while self.otp.seconds_remaining() > 0 and not self._otp_done.is_set():
                self.display.show_otp_enter(attempt, config.OTP_MAX_ATTEMPTS,
                                             self.otp.seconds_remaining())
                if self.display.is_otp_submitted():
                    digits = self.display.get_otp_input()
                    break
                time.sleep(0.08)

            if self._otp_done.is_set():
                return

            if not digits:
                log.info('No OTP entered before expiry')
                self.display.show_otp_expired()
                self.voice.play_sync('otp_expired')
                self.notif.send_otp_failed('expired')
                self.engine.reset_state()
                self._nobody_home_idle()
                return

            log.info(f'OTP entered: "{digits}"')

            valid, reason = self.otp.validate(digits)

            if valid:
                log.info(f'OTP accepted → granting access to {visitor_name or "Visitor"}')
                self._grant_otp_access(visitor_name or 'Visitor')
                return
            else:
                log.warning(f'OTP wrong ({reason}): "{digits}"')
                self._otp_attempts += 1

                if reason == 'expired':
                    self.display.show_otp_expired()
                    self.voice.play_sync('otp_expired')
                    self.notif.send_otp_failed('expired')
                    self.engine.reset_state()
                    self._nobody_home_idle()
                    return

                if attempt < config.OTP_MAX_ATTEMPTS:
                    self.display.show_otp_wrong(config.OTP_MAX_ATTEMPTS - attempt)
                    self.voice.play_sync('otp_wrong')
                    self.notif.send(f'Wrong OTP attempt {attempt}/{config.OTP_MAX_ATTEMPTS}',
                                    title='⚠️ OTP Failed')
                    time.sleep(1.5)

        log.warning('OTP max attempts reached — lockout')
        self._otp_lockout_t = time.time()
        self.otp.invalidate()
        self.display.show_denied()
        self.voice.play('denied')
        self.notif.send_otp_failed('wrong')
        self.notif.send(f'Lockout active for {config.OTP_LOCKOUT_SECONDS}s',
                        title='🔐 Lockout')
        time.sleep(3)
        self.engine.reset_state()
        self._nobody_home_idle()

    def _nobody_home_idle(self):
        """Return to idle while keeping nobody-home active."""
        self._processing = False
        if self._nobody_home:
            self.display.show_nobody_home()
        else:
            self.display.show_idle()

    # ── NORMAL APPROVAL FLOW ──────────────────────────────────────────────

    def _start_name_approval_flow(self, frame, frame_b64: str):
        self.display.show_new_face()
        self.voice.play_sync('say_name')
        self.voice.stop_mixer()
        time.sleep(0.5)
        self.display.show_mic_open()

        spoken = listen.listen_for_name(
            timeout=config.LISTEN_TIMEOUT,
            speech_lang=config.SPEECH_LANG,
            device=config.MIC_ALSA_DEVICE
        )

        if not spoken:
            self.voice.play_sync('say_name')
            time.sleep(0.4)
            self.display.show_mic_retry()
            spoken = listen.listen_for_name(timeout=8, speech_lang='en-IN',
                                             device=config.MIC_ALSA_DEVICE)

        self.voice.restart_mixer()
        spoken = spoken.strip() if spoken else 'Unknown'
        log.info(f'Name: "{spoken}"')

        self.display.show_waiting(spoken)
        self.voice.play('wait_approval')
        _, buf = cv2.imencode('.jpg', frame)
        self.notif.send_approval_request(spoken, 0.0, buf.tobytes())

    # ── FIREBASE APPROVAL CALLBACKS ──────────────────────────────────────

    def _on_approve(self, data: dict):
        name = data.get('name', 'Visitor')
        log.info(f'Dashboard approved: {name}')
        self._processing = False
        self.voice.stop_loop()
        self.display.show_granted(name, 100.0)
        self.voice.play('welcome')
        self.gpio.unlock_door()
        self.display.set_status(door_locked=False)
        self.engine.save_current_face(name)
        time.sleep(config.DOOR_OPEN_SECONDS)
        self.gpio.lock_door()
        self.display.set_status(door_locked=True)
        self.engine.reset_state()
        self.display.show_idle()

    def _on_deny(self, data: dict):
        log.info('Denied by dashboard')
        self._processing = False
        self.voice.stop_loop()
        self.display.show_denied()
        self.voice.play('denied')
        time.sleep(3)
        self.engine.reset_state()
        self.display.show_idle()

    def _on_command(self, command: str, data: dict):
        """Handle commands from Pi dashboard."""
        log.info(f'Command from dashboard: {command}')
        if command == 'unlock':
            self.gpio.unlock_door()
            self.display.set_status(door_locked=False)
            self.notif.update_status({'doorLocked': False})
            self.notif.send('Door unlocked remotely', title='🔓 Remote Unlock')
        elif command == 'lock':
            self.gpio.lock_door()
            self.display.set_status(door_locked=True)
            self.notif.update_status({'doorLocked': True})
            self.notif.send('Door locked remotely', title='🔒 Remote Lock')
        elif command == 'generate_otp':
            otp = self.otp.generate()
            self.notif.send_otp(otp, config.OTP_EXPIRY_SECONDS)
            log.info(f'Manual OTP generated from dashboard: {otp}')

    def _on_nobody_home_cmd(self, active: bool):
        self._nobody_home  = active
        self._nobody_ann_t = 0.0
        log.info(f'Nobody home: {"ON" if active else "OFF"}')

        if active:
            self.voice.play('nobody_home')
            self.display.show_nobody_home()
            self.notif.send_nobody_home_on()
        else:
            self.otp.invalidate()
            self.engine.reset_state()
            self.display.show_idle()
            self.notif.send_nobody_home_off()

    # ── DISPLAY STATE → DASHBOARD ──────────────────────────────────────

    def _on_display_state_change(self, state: str):
        self.notif.update_status({'displayState': state})

    # ── OTP ACCESS GRANTED ────────────────────────────────────────────────

    def _grant_otp_access(self, name: str):
        """Unlock door after valid OTP."""
        self.display.show_granted(name, 0)
        self.voice.play('welcome')
        self.gpio.unlock_door()
        self.display.set_status(door_locked=False)
        self.notif.send_otp_accepted(name)
        time.sleep(config.DOOR_OPEN_SECONDS)
        self.gpio.lock_door()
        self.display.set_status(door_locked=True)
        self.engine.reset_state()
        self._nobody_home_idle()

    # ── START ─────────────────────────────────────────────────────────────

    def start(self):
        # Init Firebase notifier
        self.notif.start()

        self.notif.set_firebase_callbacks(
            on_approve=self._on_approve,
            on_deny=self._on_deny,
            on_nobody_home=self._on_nobody_home_cmd,
            on_command=self._on_command,
        )

        # Init WebRTC broadcaster once Firebase db is available
        if self.notif._fb_ok and self.notif._fb._db:
            self.webrtc = WebRTCThread(self.notif._fb._db,
                                        self.engine.get_current_frame)
            self.webrtc.start()
            log.info('WebRTC broadcaster started')

        # Display state → dashboard
        self.display.set_on_state_change(self._on_display_state_change)

        # Load faces
        log.info('Loading face database...')
        self.engine._load_known_faces()
        self.display.known_face_count = len(self.engine._known_names)
        log.info(f'Faces: {self.engine._known_names}')

        self.voice.play_sync('system_on')
        self.notif.send_system_on()
        self.notif.update_status({
            'camera': True,
            'engine': True,
            'doorLocked': True,
            'displayState': 'IDLE',
        })
        self.display.set_status(fb=self.notif._fb_ok, cam=True, door_locked=True)

        threads = [
            threading.Thread(target=self.engine.run, daemon=True, name='face-engine'),
        ]
        for t in threads:
            t.start()
            log.info(f'Thread: {t.name}')

        self.display.show_boot()
        threading.Timer(1.5, self.display.show_idle).start()
        self.display.run()
        self.stop()

    def stop(self):
        log.info('Stopping...')
        self.notif.send_system_off()
        self.notif.update_status({'camera': False, 'engine': False})
        self.notif.cleanup()
        self.engine.stop()
        if self.webrtc:
            self.webrtc.stop()
        self.gpio.cleanup()
        log.info('Done.')


if __name__ == '__main__':
    Secura9().start()
