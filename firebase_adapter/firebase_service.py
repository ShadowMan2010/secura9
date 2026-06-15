"""
SECURA-9 Firebase Adapter
Replaces Telegram bot with Firebase Firestore + Cloud Messaging.

Setup:
  1. Go to https://console.firebase.google.com → Create Project
  2. Enable Authentication → Google provider
  3. Create Firestore Database (start in test mode)
  4. Project Settings → Service Accounts → Generate new private key
  5. Save as serviceAccountKey.json in this directory
  6. Set GOOGLE_APPLICATION_CREDENTIALS env var or copy file here

Architecture:
  Pi writes to Firestore collections:
    /devices/{device_id}/notifications/  — OTP, alerts, system events
    /devices/{device_id}/approvals/      — face approval requests
    /devices/{device_id}/status/         — live status
    
  Android app listens via onSnapshot for real-time updates.
  App writes decisions to /devices/{device_id}/decisions/
  Pi listens to /devices/{device_id}/decisions/ for approve/deny.
"""

import firebase_admin
from firebase_admin import credentials, firestore, auth, messaging
import os
import json
import logging
import threading
import time
from datetime import datetime

log = logging.getLogger('firebase_svc')

DEVICE_ID = 'secura9_pi_01'  # unique device identifier


class FirebaseService:
    def __init__(self, device_id: str = DEVICE_ID):
        self._device_id = device_id
        self._db = None
        self._initialized = False
        self._on_approve = None
        self._on_deny = None
        self._on_nobody_home = None
        self._on_command = None
        self._listener = None
        self._running = False
        self._device_uid = device_id
        self._device_token = ''

    def initialize(self, cred_path: str = ''):
        """Initialize Firebase Admin SDK."""
        try:
            if not cred_path:
                cred_path = os.path.join(
                    os.path.dirname(os.path.abspath(__file__)),
                    'serviceAccountKey.json'
                )
            if os.path.exists(cred_path):
                cred = credentials.Certificate(cred_path)
                firebase_admin.initialize_app(cred)
            else:
                # Fallback: use application default credentials
                firebase_admin.initialize_app()
            
            self._db = firestore.client()
            self._device_auth()
            self._initialized = True
            log.info('Firebase initialized')
            return True
        except Exception as e:
            log.error(f'Firebase init failed: {e}')
            return False

    # ── DEVICE AUTH (seamless IoT sign-in) ──────────────────────────────

    def _device_auth(self):
        """Register this device as an authenticated IoT user in Firebase Auth,
        enabling seamless identity without browser login."""
        try:
            try:
                user = auth.get_user(self._device_id)
                log.info(f'Device user exists: {user.uid}')
            except auth.UserNotFoundError:
                user = auth.create_user(
                    uid=self._device_id,
                    display_name='SECURA-9 Pi Door Controller',
                    email=f'{self._device_id}@secura9.local',
                    disabled=False,
                )
                log.info(f'Device user created: {user.uid}')

            custom_token = auth.create_custom_token(self._device_id).decode()
            log.info(f'Device auth token generated for {self._device_id}')

            self._device_uid = user.uid
            self._device_token = custom_token
        except Exception as e:
            log.warning(f'Device auth skipped (non-fatal): {e}')
            self._device_uid = self._device_id
            self._device_token = ''

    # ── NOTIFICATIONS (Pi → App) ───────────────────────────────────────

    def send_notification(self, notif_type: str, title: str, body: str, 
                          data: dict = None):
        """Write a notification document to Firestore and send FCM push."""
        if not self._initialized:
            return
        try:
            doc = {
                'type': notif_type,
                'title': title,
                'body': body,
                'data': data or {},
                'timestamp': firestore.SERVER_TIMESTAMP,
                'read': False,
            }
            self._db.collection('devices').document(self._device_id) \
                .collection('notifications').add(doc)
            log.info(f'Notification sent: {title}')
        except Exception as e:
            log.error(f'Send notification error: {e}')

        self._send_fcm_push(notif_type, title, body, data)

    def _send_fcm_push(self, notif_type: str, title: str, body: str,
                        data: dict = None):
        """Send FCM push notification to all registered app tokens."""
        if not self._initialized:
            return
        try:
            tokens = self._db.collection('devices').document(self._device_id) \
                .collection('fcm_tokens').get()
            if not tokens:
                return

            message_data = {
                'type': notif_type,
                'title': title,
                'body': body,
            }
            if data:
                for k, v in data.items():
                    message_data[k] = str(v)

            for token_doc in tokens:
                token = token_doc.to_dict().get('token', '')
                if not token:
                    continue
                try:
                    msg = messaging.Message(
                        notification=messaging.Notification(
                            title=title,
                            body=body,
                        ),
                        data=message_data,
                        token=token,
                    )
                    messaging.send(msg)
                    log.info(f'FCM push sent to {token_doc.id}')
                except messaging.UnregisteredError:
                    log.warning(f'FCM token unregistered, removing: {token_doc.id}')
                    token_doc.reference.delete()
                except Exception as e:
                    log.warning(f'FCM send to {token_doc.id} failed: {e}')
        except Exception as e:
            log.warning(f'FCM push error: {e}')

    def send_otp(self, otp: str, expires_in: int):
        """Send OTP to the app."""
        self.send_notification(
            notif_type='otp',
            title='🔐 OTP Generated',
            body=f'OTP: {otp} — valid for {expires_in}s',
            data={'otp': otp, 'expires_in': expires_in}
        )

    def send_approval_request(self, name: str, confidence: float, 
                               image_bytes: bytes = None):
        """Send face approval request to the app."""
        import base64
        b64 = base64.b64encode(image_bytes).decode() if image_bytes else ''
        
        # Write to approvals collection
        if self._initialized:
            try:
                doc_ref = self._db.collection('devices').document(self._device_id) \
                    .collection('approvals').document()
                doc_ref.set({
                    'name': name,
                    'confidence': confidence,
                    'imageB64': b64,
                    'status': 'pending',  # pending, approved, denied
                    'timestamp': firestore.SERVER_TIMESTAMP,
                })
                log.info(f'Approval request sent for: {name}')
            except Exception as e:
                log.error(f'Approval request error: {e}')

        self.send_notification(
            notif_type='approval',
            title='👤 Face Approval Required',
            body=f'{name} is at the door ({confidence:.0f}% match)',
            data={'name': name, 'confidence': confidence}
        )

    def send_motion_detected(self):
        self.send_notification(
            notif_type='motion',
            title='🚶 Motion Detected',
            body='Someone is at the door',
        )

    def send_system_on(self):
        self.send_notification(
            notif_type='system',
            title='🟢 System Online',
            body='SECURA-9 is active and monitoring',
        )

    def send_system_off(self):
        self.send_notification(
            notif_type='system',
            title='🔴 System Offline',
            body='SECURA-9 has shut down',
        )

    def send_nobody_home_on(self):
        self.send_notification(
            notif_type='nobody_home',
            title='🏠 Nobody Home: ON',
            body='Nobody home mode activated. Visitors will get OTP.',
        )

    def send_nobody_home_off(self):
        self.send_notification(
            notif_type='system',
            title='🟢 Nobody-Home Off',
            body='Normal mode restored',
        )

    def send_tamper_alarm(self):
        self.send_notification(
            notif_type='tamper',
            title='🚨 TAMPER ALARM',
            body='Intrusion detected at the door!',
            data={'alarm': 'tamper'}
        )

    def send_passage_on(self):
        self.send_notification(
            notif_type='passage',
            title='🚪 Passage Mode ON',
            body='Door will stay unlocked',
        )

    def send_passage_off(self):
        self.send_notification(
            notif_type='passage',
            title='🚪 Passage Mode OFF',
            body='Door locked',
        )

    def send_ota_update_available(self, version: str):
        self.send_notification(
            notif_type='ota',
            title='📦 OTA Update Available',
            body=f'Version {version} ready to install',
            data={'otaVersion': version}
        )

    # ── Timed codes listener ──────────────────────────────────────────────

    def listen_timed_codes(self, on_code_request=None):
        if not self._initialized:
            return
        try:
            codes_ref = self._db.collection('devices').document(self._device_id) \
                .collection('timedCodes')
            codes_ref.on_snapshot(lambda snaps, _: self._handle_timed_codes(snaps, on_code_request))
            log.info('Timed codes listener started')
        except Exception as e:
            log.warning(f'Timed codes listener failed: {e}')

    def _handle_timed_codes(self, snaps, callback):
        if not callback:
            return
        for snap in snaps:
            if snap.exists:
                data = snap.to_dict()
                if data.get('status') == 'pending':
                    callback(snap.id, data)

    # ── Config listener ──────────────────────────────────────────────────

    def listen_config(self, on_config_update=None):
        if not self._initialized:
            return
        try:
            config_ref = self._db.collection('devices').document(self._device_id) \
                .collection('config').document('settings')
            config_ref.on_snapshot(
                lambda snap, _: self._handle_config(snap, on_config_update)
            )
            log.info('Config listener started')
        except Exception as e:
            log.warning(f'Config listener failed: {e}')

    def _handle_config(self, snap, callback):
        if not callback or not snap.exists:
            return
        data = snap.to_dict()
        if data:
            callback(data)

    def send_otp_accepted(self, name: str):
        self.send_notification(
            notif_type='access',
            title='✅ Access Granted',
            body=f'{name} entered via OTP',
            data={'name': name}
        )

    def send_otp_failed(self, reason: str):
        self.send_notification(
            notif_type='access',
            title='❌ Access Denied',
            body=f'OTP failed: {reason}',
        )

    def send_access_granted(self, name: str):
        self.send_notification(
            notif_type='access',
            title='✅ Access Granted',
            body=f'{name} identified and entered',
            data={'name': name}
        )

    def send_lurker_alert(self):
        self.send_notification(
            notif_type='lurker',
            title='🚨 Lurker Alert',
            body='Prolonged motion detected at door — person lingering without identification',
        )

    # ── LIVE STATUS ────────────────────────────────────────────────────

    def update_status(self, status_data: dict):
        """Update device status document."""
        if not self._initialized:
            return
        try:
            status_data['lastSeen'] = firestore.SERVER_TIMESTAMP
            self._db.collection('devices').document(self._device_id) \
                .collection('status').document('live') \
                .set(status_data, merge=True)
        except Exception as e:
            log.error(f'Status update error: {e}')

    # ── LISTEN FOR DECISIONS (App → Pi) ───────────────────────────────

    def set_callbacks(self, on_approve=None, on_deny=None, 
                      on_nobody_home=None, on_command=None):
        self._on_approve = on_approve
        self._on_deny = on_deny
        self._on_nobody_home = on_nobody_home
        self._on_command = on_command

    def start_listening(self):
        """Start listening for decisions from the app in a background thread."""
        if not self._initialized:
            log.warning('Firebase not initialized — cannot listen')
            return
        self._running = True
        thread = threading.Thread(target=self._listen_loop, daemon=True,
                                  name='firebase-listen')
        thread.start()
        log.info('Listening for app decisions...')

    def stop_listening(self):
        self._running = False

    def _listen_loop(self):
        """Listen for new decisions in the decisions collection."""
        decisions_ref = self._db.collection('devices').document(self._device_id) \
            .collection('decisions')

        # Listen using on_snapshot
        self._listener = decisions_ref.on_snapshot(self._on_decision_snapshot)
        
        # Keep thread alive
        while self._running:
            time.sleep(1)

    def _on_decision_snapshot(self, docs, changes, read_time):
        for change in changes:
            if change.type.name == 'ADDED':
                data = change.document.to_dict()
                decision_type = data.get('decision', '')
                log.info(f'Decision received: {decision_type}')

                if decision_type in ('approve', 'deny'):
                    new_status = 'approved' if decision_type == 'approve' else 'denied'
                    approval_id = data.get('approvalId') or data.get('id')
                    if approval_id and self._db:
                        try:
                            self._db.collection('devices').document(self._device_id) \
                                .collection('approvals').document(approval_id) \
                                .set({'status': new_status}, merge=True)
                            log.info(f'Approval {approval_id} → {new_status}')
                        except Exception as e:
                            log.warning(f'Failed to update approval status: {e}')

                if decision_type == 'approve' and self._on_approve:
                    self._on_approve(data)
                elif decision_type == 'deny' and self._on_deny:
                    self._on_deny(data)
                elif decision_type == 'nobody_home' and self._on_nobody_home:
                    self._on_nobody_home(data.get('active', False))
                elif self._on_command:
                    self._on_command(decision_type, data)

                try:
                    change.document.reference.delete()
                except Exception:
                    pass

    # ── TIMED ACCESS CODES ──────────────────────────────────────────────

    def listen_timed_codes(self, on_codes_update=None):
        """Listen for changes to timed access codes."""
        if not self._initialized:
            return
        codes_ref = self._db.collection('devices').document(self._device_id) \
            .collection('codes')

        def _on_codes_snapshot(docs, changes, read_time):
            codes = {}
            for doc in docs:
                data = doc.to_dict()
                if data and data.get('active', True):
                    code_str = data.get('code', '')
                    if code_str:
                        codes[code_str] = {
                            'id': doc.id,
                            'label': data.get('label', ''),
                            'expiresAt': data.get('expiresAt'),
                            'maxUses': data.get('maxUses', 0),
                            'useCount': data.get('useCount', 0),
                        }
            if on_codes_update:
                on_codes_update(codes)

        codes_ref.on_snapshot(_on_codes_snapshot)
        log.info('Listening for timed access codes...')

    # ── CONFIG LISTENER ────────────────────────────────────────────────

    def listen_config(self, on_config_update=None):
        """Listen for remote config changes from the app/web."""
        if not self._initialized:
            return
        config_ref = self._db.collection('devices').document(self._device_id) \
            .collection('config')

        def _on_config_snapshot(query_snapshot, changes, read_time):
            if not on_config_update:
                return
            config = {}
            for doc in query_snapshot:
                config[doc.id] = doc.to_dict() or {}
            on_config_update(config)

        config_ref.on_snapshot(_on_config_snapshot)
        log.info('Listening for remote config changes...')

    # ── MEMBER CHECK ───────────────────────────────────────────────────

    def get_device_members(self) -> list:
        """Get list of member UIDs for this device."""
        if not self._initialized:
            return []
        try:
            members_snap = self._db.collection('devices').document(self._device_id) \
                .collection('members').get()
            return [m.id for m in members_snap]
        except Exception as e:
            log.warning(f'Failed to get members: {e}')
            return []

    # ── CLEANUP ────────────────────────────────────────────────────────


# ── Global singleton ──────────────────────────────────────────────────────
_fb = None


def init_firebase(cred_path: str = ''):
    global _fb
    _fb = FirebaseService()
    _fb.initialize(cred_path)
    return _fb


def get_firebase():
    return _fb
