"""
SECURA-9 WebRTC Broadcaster
Publishes Pi camera as WebRTC stream via Firestore signaling.
"""

import asyncio
from fractions import Fraction
import logging
import threading
import time
from typing import Optional

import cv2
import numpy as np
from aiortc import RTCPeerConnection, RTCConfiguration, RTCIceServer, RTCSessionDescription, VideoStreamTrack, AudioStreamTrack
from av import VideoFrame, AudioFrame
import pyaudio
import numpy as np
from firebase_admin import firestore

try:
    import config
except ImportError:
    import sys, os
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import config

log = logging.getLogger('webrtc')

STUN_SERVERS = [RTCIceServer(urls='stun:stun.l.google.com:19302')]

def _make_config():
    return RTCConfiguration(iceServers=STUN_SERVERS)


class CameraVideoTrack(VideoStreamTrack):
    def __init__(self, get_frame_fn):
        super().__init__()
        self._get_frame = get_frame_fn
        self._running = True

    async def recv(self):
        while self._running:
            frame = self._get_frame()
            if frame is not None:
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                video_frame = VideoFrame.from_ndarray(rgb, format='rgb24')
                video_frame.pts = int(time.time() * 1000000)
                video_frame.time_base = Fraction(1, 1000000)
                return video_frame
            await asyncio.sleep(0.03)

    def stop(self):
        self._running = False


# ─── AUDIO ─────────────────────────────────────────────────────────────────
class AudioPlayer:
    """Plays incoming WebRTC audio from Android through the speaker."""
    def __init__(self):
        self._pa = pyaudio.PyAudio()
        self._stream = None
        self._running = False

    def start(self):
        self._running = True
        try:
            self._stream = self._pa.open(
                format=pyaudio.paInt16,
                channels=1,
                rate=48000,
                output=True,
                frames_per_buffer=960,
            )
            log.info('AudioPlayer started')
        except Exception as e:
            log.warning(f'AudioPlayer start: {e}')

    def feed(self, frame):
        if self._stream and self._stream.is_active():
            try:
                arr = frame.to_ndarray()
                self._stream.write(arr.tobytes())
            except Exception as e:
                log.warning(f'AudioPlayer feed: {e}')

    def stop(self):
        self._running = False
        if self._stream:
            self._stream.stop_stream()
            self._stream.close()
            self._stream = None
        if self._pa:
            self._pa.terminate()
            self._pa = None
        log.info('AudioPlayer stopped')


class MicrophoneAudioTrack(AudioStreamTrack):
    """Captures Pi mic and sends it as a WebRTC audio track to Android."""
    def __init__(self):
        super().__init__()
        self._running = True
        self._pa = pyaudio.PyAudio()
        self._stream = None
        self._init_stream()

    def _init_stream(self):
        try:
            import config
            kwargs = dict(
                format=pyaudio.paInt16,
                channels=1,
                rate=48000,
                input=True,
                frames_per_buffer=960,
            )
            if config.MIC_DEVICE_INDEX is not None:
                kwargs['input_device_index'] = config.MIC_DEVICE_INDEX
            self._stream = self._pa.open(**kwargs)
            log.info('MicrophoneAudioTrack started index=%s', config.MIC_DEVICE_INDEX)
        except Exception as e:
            log.warning(f'MicAudioTrack init: {e}')

    async def recv(self):
        while self._running:
            if self._stream and self._stream.is_active():
                try:
                    data = self._stream.read(960, exception_on_overflow=False)
                    samples = np.frombuffer(data, dtype=np.int16).reshape(1, -1)
                    frame = AudioFrame.from_ndarray(samples, format='s16')
                    frame.sample_rate = 48000
                    frame.pts = int(time.time() * 1000000)
                    frame.time_base = Fraction(1, 1000000)
                    return frame
                except Exception as e:
                    log.warning(f'MicAudioTrack recv: {e}')
            await asyncio.sleep(0.02)

    def stop(self):
        self._running = False
        if self._stream:
            self._stream.stop_stream()
            self._stream.close()
            self._stream = None
        if self._pa:
            self._pa.terminate()
            self._pa = None
        log.info('MicrophoneAudioTrack stopped')


class WebRTCBroadcaster:
    def __init__(self, db, get_frame_fn, device_id='secura9_pi_01'):
        self._db = db
        self._get_frame = get_frame_fn
        self._device_id = device_id
        self._pc: Optional[RTCPeerConnection] = None
        self._track: Optional[CameraVideoTrack] = None
        self._audio_track: Optional[MicrophoneAudioTrack] = None
        self._audio_player: Optional[AudioPlayer] = None
        self._session_id: Optional[str] = None
        self._sessions_ref = None
        self._listener = None
        self._viewer_ice_listener = None
        self._viewer_ice_unsub = None
        self._loop: Optional[asyncio.AbstractEventLoop] = None

    def _get_signaling_ref(self, session_id=None):
        sid = session_id or self._session_id
        if not sid:
            return None
        return self._db.collection('devices').document(self._device_id) \
            .collection('webrtc').document(sid)

    async def _on_ice_candidate(self, candidate):
        if not self._pc:
            return
        try:
            await self._pc.addIceCandidate(candidate)
        except Exception as e:
            log.warning(f'addIceCandidate error: {e}')

    def _push_ice_candidate(self, candidate):
        ref = self._get_signaling_ref()
        if not ref:
            return
        try:
            ref.collection('piIce').add({
                'candidate': {
                    'candidate': candidate['candidate'],
                    'sdpMid': candidate['sdpMid'],
                    'sdpMLineIndex': candidate['sdpMLineIndex'],
                },
                'timestamp': firestore.SERVER_TIMESTAMP,
            })
        except Exception:
            pass

    async def _handle_session(self, doc_id, data):
        if self._pc:
            await self._cleanup()

        self._session_id = doc_id
        offer = data.get('offer')
        if not offer:
            return

        log.info(f'Handling new WebRTC session: {doc_id}')

        self._pc = RTCPeerConnection(configuration=_make_config())

        @self._pc.on('iceconnectionstatechange')
        async def on_ice():
            if not self._pc:
                return
            s = self._pc.iceConnectionState
            log.info(f'ICE: {s}')
            if s in ('failed', 'disconnected', 'closed'):
                await self._cleanup()

        @self._pc.on('connectionstatechange')
        async def on_conn():
            if not self._pc:
                return
            s = self._pc.connectionState
            log.info(f'Connection: {s}')
            self._update_webrtc_status(s)

        @self._pc.on('icecandidate')
        async def on_candidate(candidate):
            if candidate:
                self._push_ice_candidate({
                    'candidate': candidate.candidate,
                    'sdpMid': candidate.sdpMid,
                    'sdpMLineIndex': candidate.sdpMLineIndex,
                })

        # Incoming audio from Android (two-way talk)
        self._audio_player = AudioPlayer()

        @self._pc.on('track')
        async def on_track(track):
            if track.kind == 'audio':
                log.info('Incoming audio track from viewer')
                self._audio_player.start()
                try:
                    while True:
                        frame = await track.recv()
                        self._audio_player.feed(frame)
                except Exception:
                    pass
                self._audio_player.stop()

        # Add camera track
        self._track = CameraVideoTrack(self._get_frame)
        self._pc.addTrack(self._track)

        # Add mic audio track only if the offer includes audio
        offer_sdp = offer.get('sdp', '')
        has_audio = 'm=audio' in offer_sdp
        if has_audio:
            self._audio_track = MicrophoneAudioTrack()
            self._pc.addTrack(self._audio_track)
            log.info('Audio track added (offer includes audio)')
        else:
            log.info('Offer has no audio — mic track skipped')

        try:
            log.info(f'WebRTC step: setRemoteDescription for {doc_id}')
            try:
                await asyncio.wait_for(
                    self._pc.setRemoteDescription(RTCSessionDescription(
                        type=offer['type'], sdp=offer['sdp'])),
                    timeout=10)
            except asyncio.TimeoutError:
                log.error(f'WebRTC: setRemoteDescription timed out for {doc_id}')
                await self._cleanup()
                return
            log.info(f'WebRTC step: remoteDescription set for {doc_id}')

            answer = await self._pc.createAnswer()
            log.info(f'WebRTC step: answer created for {doc_id}')

            await self._pc.setLocalDescription(answer)
            log.info(f'WebRTC step: localDescription set for {doc_id}')

            ref = self._get_signaling_ref()
            if ref:
                loop = asyncio.get_event_loop()
                await loop.run_in_executor(
                    None, lambda: ref.set({'answer': {
                        'type': self._pc.localDescription.type,
                        'sdp': self._pc.localDescription.sdp,
                    }}, merge=True))
                log.info(f'WebRTC step: answer written to Firestore for {doc_id}')

                self._viewer_ice_unsub = ref.collection('viewerIce').on_snapshot(
                    self._on_viewer_ice_snapshot)
                log.info(f'WebRTC step: viewerIce listener set for {doc_id}')

            self._update_webrtc_status('answering')
            log.info(f'WebRTC answer sent for {doc_id}')
        except Exception as e:
            log.error(f'WebRTC session {doc_id} failed: {e}', exc_info=True)
            await self._cleanup()

    def _on_viewer_ice_snapshot(self, docs, changes, read_time):
        """Add viewer ICE candidates as they arrive."""
        for change in changes:
            if change.type.name != 'ADDED':
                continue
            data = change.document.to_dict()
            c = data.get('candidate')
            if not c or not self._pc:
                continue
            try:
                if self._loop and self._loop.is_running():
                    asyncio.run_coroutine_threadsafe(
                        self._pc.addIceCandidate(c), self._loop)
            except Exception as e:
                log.warning(f'Viewer ICE add error: {e}')

    async def _cleanup(self):
        if self._viewer_ice_unsub:
            self._viewer_ice_unsub.unsubscribe()
            self._viewer_ice_unsub = None
        if self._track:
            self._track.stop()
            self._track = None
        if self._audio_track:
            self._audio_track.stop()
            self._audio_track = None
        if self._audio_player:
            self._audio_player.stop()
            self._audio_player = None
        if self._pc:
            await self._pc.close()
            self._pc = None
        self._session_id = None
        self._update_webrtc_status('idle')

    def _update_webrtc_status(self, status):
        try:
            self._db.collection('devices').document(self._device_id) \
                .collection('status').document('live') \
                .set({'webrtc': status}, merge=True)
        except Exception:
            pass

    def _on_session_snapshot(self, docs, changes, read_time):
        """Firestore on_snapshot callback — runs in SDK thread."""
        for change in changes:
            if change.type.name != 'ADDED':
                continue
            data = change.document.to_dict()
            doc_id = change.document.id
            if not data.get('offer') or data.get('answer'):
                continue
            if self._loop and self._loop.is_running():
                asyncio.run_coroutine_threadsafe(
                    self._handle_session(doc_id, data), self._loop)

    async def _poll_sessions(self):
        """Poll for new WebRTC sessions every 2 seconds (Firestore on_snapshot unreliable)."""
        seen = set()
        while True:
            try:
                docs = self._sessions_ref.limit(10).get()
                for doc in docs:
                    doc_id = doc.id
                    if doc_id in seen:
                        continue
                    data = doc.to_dict()
                    if data.get('offer') and not data.get('answer'):
                        seen.add(doc_id)
                        log.info(f'Poll found session: {doc_id}')
                        asyncio.create_task(
                            self._handle_session(doc_id, data))
                await asyncio.sleep(2)
            except Exception as e:
                log.warning(f'Poll error: {e}')
                await asyncio.sleep(5)

    def start(self, loop):
        self._loop = loop
        self._sessions_ref = self._db.collection('devices').document(self._device_id) \
            .collection('webrtc')
        asyncio.run_coroutine_threadsafe(self._poll_sessions(), self._loop)
        log.info('WebRTC broadcaster polling for viewer offers')

    def stop(self):
        if self._listener:
            self._listener.unsubscribe()
        if self._loop and self._loop.is_running():
            asyncio.run_coroutine_threadsafe(self._cleanup(), self._loop)


class WebRTCThread:
    def __init__(self, db, get_frame_fn):
        self._broadcaster = WebRTCBroadcaster(db, get_frame_fn)
        self._thread = None
        self._loop = None

    def start(self):
        def run():
            self._loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self._loop)
            self._broadcaster.start(self._loop)
            self._loop.run_forever()

        self._thread = threading.Thread(target=run, daemon=True, name='webrtc')
        self._thread.start()
        log.info('WebRTC thread started')

    def stop(self):
        self._broadcaster.stop()
        if self._loop and self._loop.is_running():
            self._loop.call_soon_threadsafe(self._loop.stop)
