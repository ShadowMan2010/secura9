"""
SECURA-9 — Notifier (Firebase only)
Pushes all notifications to Firestore for Android app + web-deck.
"""

import logging

from firebase_adapter.firebase_service import FirebaseService, init_firebase, get_firebase

log = logging.getLogger('notifier')


class Notifier:

    def __init__(self):
        self._fb = None
        self._fb_ok = False

    def start(self, firebase_cred_path: str = ''):
        try:
            self._fb = init_firebase(firebase_cred_path)
            if self._fb and self._fb._initialized:
                self._fb_ok = True
                log.info('Firebase notifier ready')
        except Exception as e:
            log.warning(f'Firebase init failed: {e}')

    # ── Notification methods ───────────────────────────────────────────

    def send(self, body: str, title: str = 'SECURA-9', notif_type: str = 'alert'):
        if self._fb_ok:
            self._fb.send_notification(notif_type, title, body)

    def send_otp(self, otp: str, expires_in: int):
        if self._fb_ok:
            self._fb.send_otp(otp, expires_in)

    def send_approval_request(self, name: str, confidence: float,
                               image_bytes: bytes = None):
        if self._fb_ok:
            self._fb.send_approval_request(name, confidence, image_bytes)

    def send_motion_detected(self):
        if self._fb_ok:
            self._fb.send_motion_detected()

    def send_system_on(self):
        if self._fb_ok:
            self._fb.send_system_on()

    def send_system_off(self):
        if self._fb_ok:
            self._fb.send_system_off()

    def send_nobody_home_on(self):
        if self._fb_ok:
            self._fb.send_nobody_home_on()

    def send_nobody_home_off(self):
        if self._fb_ok:
            self._fb.send_nobody_home_off()

    def send_otp_accepted(self, name: str):
        if self._fb_ok:
            self._fb.send_otp_accepted(name)

    def send_otp_failed(self, reason: str):
        if self._fb_ok:
            self._fb.send_otp_failed(reason)

    def send_access_granted(self, name: str):
        if self._fb_ok:
            self._fb.send_access_granted(name)

    # ── Listen for Firebase decisions ──────────────────────────────────

    def set_firebase_callbacks(self, on_approve=None, on_deny=None,
                                on_nobody_home=None, on_command=None):
        if self._fb_ok:
            self._fb.set_callbacks(on_approve, on_deny, on_nobody_home, on_command)
            self._fb.start_listening()

    def update_status(self, status_data: dict):
        if self._fb_ok:
            self._fb.update_status(status_data)

    def cleanup(self):
        if self._fb_ok:
            self._fb.cleanup()
