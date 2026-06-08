"""
SECURA-9 — WebSocket Client v2
Adds:
  • Pi status heartbeat every 10s → visible in Web Deck
  • Optional camera frame streaming to dashboard (every 500ms)
  • Handles 'request_status' and 'faces_updated' from server
"""

import asyncio
import base64
import json
import logging
import socket
import time

import config

log = logging.getLogger('ws_client')


class WSClient:
    def __init__(self, url, on_approve, on_deny, on_nobody_home,
                 on_faces_updated=None, on_otp_input=None):
        self._url              = url
        self._on_approve       = on_approve
        self._on_deny          = on_deny
        self._on_nobody_home   = on_nobody_home
        self._on_faces_updated = on_faces_updated
        self._on_otp_input     = on_otp_input

        self._loop    = None
        self._ws      = None
        self._queue   = None
        self._running = False

        # Set True in config.py to stream frames to Web Deck
        self._stream_frames = getattr(config, 'STREAM_CAMERA_TO_DECK', False)
        self._get_frame     = None   # set via set_frame_source()

        # Live status (updated by main.py)
        self.camera_ok   = False
        self.engine_ok   = False
        self.door_locked = True

    def set_frame_source(self, fn):
        """Pass face_engine.get_current_frame to enable camera streaming."""
        self._get_frame = fn

    # ── PUBLIC ────────────────────────────────────────────────────────────

    def send_event(self, data: dict):
        """Thread-safe: queue a message for the async send loop."""
        if self._loop and self._loop.is_running() and self._queue:
            asyncio.run_coroutine_threadsafe(
                self._queue.put(json.dumps(data)), self._loop
            )

    def send_new_face(self, name: str, image_b64: str = ''):
        self.send_event({
            'type'    : 'new_face_request',
            'name'    : name,
            'imageB64': image_b64,
        })

    def send_status(self):
        try:
            ip = socket.gethostbyname(socket.gethostname())
        except Exception:
            ip = '0.0.0.0'
        self.send_event({
            'type'      : 'pi_status',
            'camera'    : self.camera_ok,
            'engine'    : self.engine_ok,
            'doorLocked': self.door_locked,
            'ip'        : ip,
        })

    def run(self):
        """Blocking — call in a thread."""
        self._running = True
        self._loop    = asyncio.new_event_loop()
        asyncio.set_event_loop(self._loop)
        self._queue   = asyncio.Queue()
        self._loop.run_until_complete(self._connect_loop())

    def stop(self):
        self._running = False
        if self._loop:
            self._loop.call_soon_threadsafe(self._loop.stop)

    # ── ASYNC INTERNALS ───────────────────────────────────────────────────

    async def _connect_loop(self):
        try:
            import websockets
        except ImportError:
            log.error('websockets not installed — pip3 install websockets')
            return

        while self._running:
            try:
                log.info(f'Connecting to {self._url}')
                async with websockets.connect(
                    self._url,
                    ping_interval=20,
                    ping_timeout=10,
                ) as ws:
                    self._ws = ws
                    log.info('WebSocket connected to bridge server')
                    # Small delay then send initial status
                    await asyncio.sleep(0.8)
                    self.send_status()

                    await asyncio.gather(
                        self._recv_loop(ws),
                        self._send_loop(ws),
                        self._heartbeat_loop(),
                        self._frame_loop(ws),
                    )
            except Exception as e:
                log.warning(f'WebSocket error: {e} — reconnecting in 5s')
                self._ws = None
                await asyncio.sleep(5)

    async def _recv_loop(self, ws):
        async for raw in ws:
            try:
                self._dispatch(json.loads(raw))
            except Exception as e:
                log.error(f'Recv error: {e}')

    async def _send_loop(self, ws):
        while True:
            msg = await self._queue.get()
            try:
                await ws.send(msg)
            except Exception as e:
                log.error(f'Send error: {e}')
                await self._queue.put(msg)
                break   # reconnect

    async def _heartbeat_loop(self):
        """Send Pi status heartbeat every 10 seconds."""
        while True:
            await asyncio.sleep(10)
            self.send_status()

    async def _frame_loop(self, ws):
        """Stream camera frames to Web Deck every 500ms (if enabled)."""
        if not self._stream_frames or not self._get_frame:
            return
        try:
            import cv2
        except ImportError:
            log.warning('opencv not available — camera streaming disabled')
            return

        log.info('Camera frame streaming enabled')
        while True:
            await asyncio.sleep(0.5)
            try:
                frame = self._get_frame()
                if frame is None:
                    continue
                small = cv2.resize(frame, (320, 240))
                _, buf = cv2.imencode('.jpg', small, [cv2.IMWRITE_JPEG_QUALITY, 50])
                b64 = base64.b64encode(buf).decode('utf-8')
                await ws.send(json.dumps({'type': 'pi_frame', 'frameB64': b64}))
            except Exception:
                pass

    def _dispatch(self, msg: dict):
        t = msg.get('type')
        log.info(f'Server → {t}')

        if   t == 'approve':         self._on_approve(msg)
        elif t == 'deny':            self._on_deny(msg)
        elif t == 'nobody_home':     self._on_nobody_home(msg.get('active', False))
        elif t == 'request_status':  self.send_status()
        elif t == 'faces_updated':
            if self._on_faces_updated:
                self._on_faces_updated(msg.get('knownFaces', []))
        elif t == 'state_sync':
            log.info(f"State sync: {len(msg.get('knownFaces',[]))} faces, {len(msg.get('pending',[]))} pending")
        elif t == 'otp_input':
            if self._on_otp_input:
                self._on_otp_input(msg.get('digits', ''))
