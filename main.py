#!/usr/bin/env python3
"""
SECURA-9 — Main with OTP + Firebase + WebRTC
"""
from __future__ import annotations
import threading
import time
import logging
import signal
import sys
import os
from typing import Optional

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
    ],
)
log = logging.getLogger('main')

shutdown = threading.Event()
secura9_instance: 'Optional[Secura9]' = None

def _signal_handler(s, f):
    shutdown.set()
    if secura9_instance is not None and hasattr(secura9_instance, 'display'):
        secura9_instance.display.stop()

signal.signal(signal.SIGINT,  _signal_handler)
signal.signal(signal.SIGTERM, _signal_handler)


class Secura9:

    def __init__(self) -> None:
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

        self.notif = Notifier()

        self.webrtc: Optional[WebRTCThread] = None

        self._nobody_home   = False
        self._nobody_ann_t  = 0.0
        self._NOBODY_CD     = 15

        self._otp_attempts  = 0
        self._otp_lockout_t = 0.0
        self._otp_done = threading.Event()

        self._processing    = False
        self._processing_t  = 0.0
        self._PROCESS_CD    = 8

        # ── Lurker Alarm ────────────────────────────────────────────────────
        self._motion_start_t = 0.0
        self._last_lurker_t  = 0.0
        self._lurker_active  = False
        self._face_seen_since_motion = False
        self._lurker_enabled = config.LURKER_ALARM_ENABLED
        self._lurker_threshold = config.LURKER_ALARM_SECONDS
        self._lurker_cooldown = config.LURKER_POST_ALARM_CD

    # ── FACE CALLBACKS ────────────────────────────────────────────────────

    def _on_known(self, name: str, confidence: float, frame) -> None:
        self._on_face_detected()
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

    def _on_unknown(self, frame, frame_b64: str) -> None:
        self._on_face_detected()
        if self._processing:
            return
        self._processing = True
        if self._nobody_home:
            log.info('Unknown face + nobody home → starting OTP flow')
            self._start_otp_flow(frame, frame_b64)
        else:
            log.info('Unknown face → name collection flow')
            self._start_name_approval_flow(frame, frame_b64)

    def _on_nobody(self) -> None:
        if self._processing:
            return
        if self._nobody_home:
            self.display.show_nobody_home()
        else:
            self.display.show_idle()

    def _on_lookaside(self) -> None:
        if self._processing:
            return
        self.voice.play('show_face')
        self.display.show_message('Please face the camera')

    # ── MOTION ─────────────────────────────────────────────────────────

    def _on_motion(self, frame) -> None:
        log.info('Motion detected')
        self.notif.send_motion_detected()

        # Lurker alarm tracking
        if self._motion_start_t == 0.0:
            self._motion_start_t = time.time()
            self._face_seen_since_motion = False
            self._lurker_active = False
            log.info(f'Lurker timer started (threshold: {config.LURKER_ALARM_SECONDS}s)')

    def _on_face_detected(self) -> None:
        """Called when any face is detected (marks motion as non-lurker)."""
        self._face_seen_since_motion = True
        self._motion_start_t = 0.0
        self._lurker_active = False

    def _check_lurker(self) -> None:
        """Background thread to check for prolonged motion without face."""
        while not shutdown.is_set():
            now = time.time()
            if (self._lurker_enabled
                    and self._motion_start_t > 0
                    and not self._face_seen_since_motion
                    and not self._lurker_active
                    and (now - self._motion_start_t) >= self._lurker_threshold
                    and (now - self._last_lurker_t) >= self._lurker_cooldown):
                self._lurker_active = True
                self._last_lurker_t = now
                log.warning('LURKER ALARM — prolonged motion without face')
                self.notif.send_lurker_alert()
            time.sleep(2)

    def _on_config_update(self, config_data: dict) -> None:
        """Handle remote config updates from Firestore."""
        lurker = config_data.get('lurker', {})
        if lurker:
            self._lurker_enabled = lurker.get('enabled', self._lurker_enabled)
            self._lurker_threshold = lurker.get('thresholdSeconds', self._lurker_threshold)
            self._lurker_cooldown = lurker.get('cooldownSeconds', self._lurker_cooldown)
            log.info(f'Lurker config updated: enabled={self._lurker_enabled}, '
                     f'threshold={self._lurker_threshold}s, cooldown={self._lurker_cooldown}s')

    # ── OTP FLOW ──────────────────────────────────────────────────────────

    def _start_otp_flow(self, frame, frame_b64: str, visitor_name: str = '') -> None:
        if time.time() - self._otp_lockout_t < config.OTP_LOCKOUT_SECONDS:
            remaining = int(config.OTP_LOCKOUT_SECONDS - (time.time() - self._otp_lockout_t))
            log.warning(f'OTP lockout active — {remaining}s remaining')
            self.display.show_message(f'LOCKED OUT — wait {remaining}s')
            self.voice.play('denied')
            return

        otp = self.otp.generate()
        self._otp_attempts = 0
        self._otp_done.clear()

        log.info(f'OTP generated: {otp}')
        self.display.show_otp_waiting(otp, config.OTP_EXPIRY_SECONDS)
        self.voice.play_sync('nobody_home')
        self.voice.play_sync('otp_sent')
        self.notif.send_otp(otp, config.OTP_EXPIRY_SECONDS)
        self._wait_for_otp(frame_b64, visitor_name)

    def _wait_for_otp(self, frame_b64: str, visitor_name: str = '') -> None:
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

            log.info(f'OTP attempt {attempt}/{config.OTP_MAX_ATTEMPTS} ({remaining}s remaining)')
            self.display.reset_otp_input()
            self.display.show_otp_enter(attempt, config.OTP_MAX_ATTEMPTS, self.otp.seconds_remaining())
            self.voice.play_sync('otp_sent')

            digits = ''
            while self.otp.seconds_remaining() > 0 and not self._otp_done.is_set():
                self.display.show_otp_enter(attempt, config.OTP_MAX_ATTEMPTS, self.otp.seconds_remaining())
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
                    self.notif.send(f'Wrong OTP attempt {attempt}/{config.OTP_MAX_ATTEMPTS}', title='OTP Failed')
                    time.sleep(1.5)

        log.warning('OTP max attempts reached — lockout')
        self._otp_lockout_t = time.time()
        self.otp.invalidate()
        self.display.show_denied()
        self.voice.play('denied')
        self.notif.send_otp_failed('wrong')
        self.notif.send(f'Lockout active for {config.OTP_LOCKOUT_SECONDS}s', title='Lockout')
        time.sleep(3)
        self.engine.reset_state()
        self._nobody_home_idle()

    def _nobody_home_idle(self) -> None:
        self._processing = False
        if self._nobody_home:
            self.display.show_nobody_home()
        else:
            self.display.show_idle()

    # ── NORMAL APPROVAL FLOW ──────────────────────────────────────────────

    def _start_name_approval_flow(self, frame, frame_b64: str) -> None:
        self.display.show_new_face()
        self.voice.play_sync('say_name')
        self.voice.stop_mixer()
        time.sleep(0.5)
        self.display.show_mic_open()

        spoken = listen.listen_for_name(
            timeout=config.LISTEN_TIMEOUT,
            speech_lang=config.SPEECH_LANG,
            device=config.MIC_ALSA_DEVICE,
        )

        if not spoken:
            self.voice.play_sync('say_name')
            time.sleep(0.4)
            self.display.show_mic_retry()
            spoken = listen.listen_for_name(timeout=8, speech_lang='en-IN', device=config.MIC_ALSA_DEVICE)

        self.voice.restart_mixer()
        spoken = spoken.strip() if spoken else 'Unknown'
        log.info(f'Name: "{spoken}"')
        self.display.show_waiting(spoken)
        self.voice.play('wait_approval')
        _, buf = cv2.imencode('.jpg', frame)
        self.notif.send_approval_request(spoken, 0.0, buf.tobytes())

    # ── FIREBASE APPROVAL CALLBACKS ──────────────────────────────────────

    def _on_approve(self, data: dict) -> None:
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

    def _on_deny(self, data: dict) -> None:
        log.info('Denied by dashboard')
        self._processing = False
        self.voice.stop_loop()
        self.display.show_denied()
        self.voice.play('denied')
        time.sleep(3)
        self.engine.reset_state()
        self.display.show_idle()

    def _on_command(self, command: str, data: dict) -> None:
        log.info(f'Command from dashboard: {command}')
        if command == 'unlock':
            self.gpio.unlock_door()
            self.display.set_status(door_locked=False)
            self.notif.update_status({'doorLocked': False})
            self.notif.send('Door unlocked remotely', title='Remote Unlock')
        elif command == 'lock':
            self.gpio.lock_door()
            self.display.set_status(door_locked=True)
            self.notif.update_status({'doorLocked': True})
            self.notif.send('Door locked remotely', title='Remote Lock')
        elif command == 'generate_otp':
            otp = self.otp.generate()
            self.notif.send_otp(otp, config.OTP_EXPIRY_SECONDS)
            log.info(f'Manual OTP generated from dashboard: {otp}')

    def _on_nobody_home_cmd(self, active: bool) -> None:
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

    def _on_display_state_change(self, state: str) -> None:
        self.notif.update_status({'displayState': state})

    # ── OTP ACCESS GRANTED ────────────────────────────────────────────────

    def _grant_otp_access(self, name: str) -> None:
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

    # ── BUILD STATUS ──────────────────────────────────────────────────────

    def _build_status(self) -> dict:
        return {
            'door_locked': self.gpio._door_locked if hasattr(self.gpio, '_door_locked') else True,
            'nobody_home': self._nobody_home,
            'state': self.display._state if hasattr(self.display, '_state') else 'unknown',
            'online': True,
        }

    # ── START ─────────────────────────────────────────────────────────────

    def start(self) -> None:
        self.notif.start()
        self.notif.set_firebase_callbacks(
            on_approve=self._on_approve,
            on_deny=self._on_deny,
            on_nobody_home=self._on_nobody_home_cmd,
            on_command=self._on_command,
        )

        # Init WebRTC
        if self.notif._fb_ok and self.notif._fb._db:
            self.webrtc = WebRTCThread(self.notif._fb._db, self.engine.get_current_frame)
            self.webrtc.start()
            log.info('WebRTC broadcaster started')

        self.display.set_on_state_change(self._on_display_state_change)

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

        # Start timed codes and config listeners
        if self.notif._fb_ok:
            self.notif._fb.listen_timed_codes()
            self.notif._fb.listen_config(on_config_update=self._on_config_update)

        threads = [
            threading.Thread(target=self.engine.run, daemon=True, name='face-engine'),
            threading.Thread(target=self._check_lurker, daemon=True, name='lurker-checker'),
        ]
        for t in threads:
            t.start()
            log.info(f'Thread: {t.name}')

        self.display.show_boot()
        self.display.run()
        self.stop()

    def stop(self) -> None:
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
    secura9_instance = Secura9()
    secura9_instance.start()
