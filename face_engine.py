"""
SECURA-9 — Face Engine + Object Detection
"""
from __future__ import annotations
import cv2
import numpy as np
import os
import base64
import logging
import time
import threading
from typing import Optional, Callable

import config

log = logging.getLogger('face_engine')

try:
    import face_recognition
    FR = True
    log.info('face_recognition loaded')
except ImportError:
    FR = False
    log.warning('face_recognition not installed -> motion fallback active')

class FaceEngine:

    def __init__(
        self,
        on_known: Callable,
        on_unknown: Callable,
        on_nobody: Callable,
        on_lookaside: Callable,
        on_motion: Optional[Callable] = None,
        display=None,
    ) -> None:
        self.on_known     = on_known
        self.on_unknown   = on_unknown
        self.on_nobody    = on_nobody
        self.on_lookaside = on_lookaside
        self.on_motion    = on_motion
        self._display     = display

        self._running     = False
        self._cap: Optional[cv2.VideoCapture] = None

        self._frame_lock    = threading.Lock()
        self._current_frame: Optional[np.ndarray] = None

        self._known_encodings: list = []
        self._known_names: list[str] = []
        self._faces_lock      = threading.Lock()

        self._state           = 'idle'
        self._frame_count     = 0
        self._unknown_frames  = 0
        self._last_trigger_t  = 0.0
        self._lookaside_frames  = 0
        self._last_lookaside_t  = 0.0

        self._save_next_as    = None
        self._best_reg_frame  = None
        self._reg_window_end  = 0.0

        self._prev_gray: Optional[np.ndarray] = None
        self._last_motion_t = 0.0
        self._camera_retry_count = 0
        self._max_camera_retries = 3

    # ── KNOWN FACES ───────────────────────────────────────────────────────

    def _load_known_faces(self) -> None:
        encs, names = [], []
        if not FR:
            with self._faces_lock:
                self._known_encodings, self._known_names = encs, names
            return
        if not os.path.isdir(config.FACES_PATH):
            log.warning(f'faces/ folder not found: {config.FACES_PATH}')
            with self._faces_lock:
                self._known_encodings, self._known_names = encs, names
            return
        files = [f for f in os.listdir(config.FACES_PATH)
                 if f.lower().endswith(('.jpg', '.jpeg', '.png', '.bmp'))]
        if not files:
            log.info('faces/ is empty')
            with self._faces_lock:
                self._known_encodings, self._known_names = encs, names
            return
        for fname in sorted(files):
            name = os.path.splitext(fname)[0]
            path = os.path.join(config.FACES_PATH, fname)
            try:
                img = face_recognition.load_image_file(path)
                enc = face_recognition.face_encodings(img)
                if enc:
                    encs.append(enc[0])
                    names.append(name)
                    log.info(f'  {name}')
                else:
                    log.warning(f'  {fname} — no face found')
            except Exception as e:
                log.error(f'  {fname}: {e}')
        with self._faces_lock:
            self._known_encodings = encs
            self._known_names     = names
        log.info(f'Known faces: {len(names)} -> {names}')

    def reload_faces(self, _=None) -> None:
        log.info('Reloading face database...')
        self._load_known_faces()

    # ── REGISTRATION ──────────────────────────────────────────────────────

    def save_current_face(self, name: str) -> None:
        log.info(f'Registration started for: {name}')
        self._save_next_as   = name
        self._best_reg_frame = None
        self._reg_window_end = time.time() + 2.5

    def _score_reg_frame(self, frame: np.ndarray) -> None:
        if not FR:
            if self._best_reg_frame is None:
                self._best_reg_frame = (0, frame.copy())
            return
        small = cv2.resize(frame, (0, 0), fx=0.5, fy=0.5)
        rgb   = cv2.cvtColor(small, cv2.COLOR_BGR2RGB)
        locs  = face_recognition.face_locations(rgb, model='hog')
        if not locs:
            return
        h, w  = frame.shape[:2]
        cx, cy = w // 2, h // 2
        for (t, r, b, l) in locs:
            t2, r2, b2, l2 = t * 2, r * 2, b * 2, l * 2
            face_h = b2 - t2
            fcx = (l2 + r2) // 2
            fcy = (t2 + b2) // 2
            dist = ((fcx - cx) ** 2 + (fcy - cy) ** 2) ** 0.5
            score = face_h - dist * 0.3
            if self._best_reg_frame is None or score > self._best_reg_frame[0]:
                self._best_reg_frame = (score, frame.copy())

    def _finalise_registration(self, fallback_frame: np.ndarray) -> None:
        name = self._save_next_as
        self._save_next_as = None
        frame_to_save = self._best_reg_frame[1] if self._best_reg_frame else fallback_frame
        self._best_reg_frame = None
        safe = ''.join(c for c in name if c.isalnum() or c in ' -_').strip()
        if not safe:
            safe = 'person_' + str(int(time.time()))
        path = os.path.join(config.FACES_PATH, safe + '.jpg')
        cv2.imwrite(path, frame_to_save)
        log.info(f'Registration saved: {path}')
        self._load_known_faces()

    # ── CAMERA LOOP ───────────────────────────────────────────────────────

    def run(self) -> None:
        self._running = True

        source = config.CAMERA_SOURCE
        if isinstance(source, str):
            cap = cv2.VideoCapture(source)
            log.info(f'Opening IP camera: {source}')
        else:
            cap = cv2.VideoCapture(source)
            cap.set(cv2.CAP_PROP_FRAME_WIDTH,  config.CAMERA_WIDTH)
            cap.set(cv2.CAP_PROP_FRAME_HEIGHT, config.CAMERA_HEIGHT)
            cap.set(cv2.CAP_PROP_FPS,          config.CAMERA_FPS)
            cap.set(cv2.CAP_PROP_BUFFERSIZE,   1)
            log.info(f'Opening camera index: {source}')
        self._cap = cap

        if not cap.isOpened():
            log.error(f'Cannot open camera source: {config.CAMERA_SOURCE}')
            log.error('  - For USB camera: set CAMERA_SOURCE = 0 in config.py')
            log.error('  - For DroidCam:   set CAMERA_SOURCE = "http://phone-ip:4747/video"')
            log.error('  - List devices:   ls /dev/video*')
            log.error('  - Test camera:    python3 -c "import cv2; cap=cv2.VideoCapture(0); print(cap.isOpened())"')
            while self._running:
                time.sleep(1)
            return

        w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        log.info(f'Camera {w}x{h} opened')

        camera_failures = 0

        while self._running:
            ret, frame = cap.read()
            if not ret:
                camera_failures += 1
                log.warning(f'Camera read error ({camera_failures})')
                if camera_failures > 50:
                    log.error('Camera disconnected — attempting reopen')
                    cap.release()
                    time.sleep(2)
                    source = config.CAMERA_SOURCE
                    cap = cv2.VideoCapture(source)
                    if not isinstance(source, str):
                        cap.set(cv2.CAP_PROP_FRAME_WIDTH,  config.CAMERA_WIDTH)
                        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, config.CAMERA_HEIGHT)
                        cap.set(cv2.CAP_PROP_FPS,          config.CAMERA_FPS)
                        cap.set(cv2.CAP_PROP_BUFFERSIZE,   1)
                    self._cap = cap
                    if not cap.isOpened():
                        log.error('Camera reopen failed — stopping')
                        break
                    camera_failures = 0
                time.sleep(0.04)
                continue

            camera_failures = 0

            if config.FLIP_HORIZONTAL:
                frame = cv2.flip(frame, 1)

            with self._frame_lock:
                self._current_frame = frame.copy()

            if self._display is not None:
                try:
                    self._display.update_camera(frame)
                except Exception:
                    pass

            now = time.time()
            if self._save_next_as:
                if now < self._reg_window_end:
                    self._score_reg_frame(frame)
                else:
                    self._finalise_registration(frame)
                    continue

            self._frame_count += 1
            if self._frame_count % config.PROCESS_EVERY_N != 0:
                continue
            if self._state == 'waiting':
                continue

            if FR:
                self._process_fr(frame)
            else:
                self._process_motion(frame)

        cap.release()
        log.info('Camera stopped')

    # ── FACE RECOGNITION ──────────────────────────────────────────────────

    def _process_fr(self, frame: np.ndarray) -> None:
        now = time.time()
        small = cv2.resize(frame, (0, 0), fx=0.5, fy=0.5)
        rgb   = cv2.cvtColor(small, cv2.COLOR_BGR2RGB)
        locs  = face_recognition.face_locations(rgb, model='hog')

        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        gray = cv2.GaussianBlur(gray, (21, 21), 0)
        motion = 0
        if self._prev_gray is not None:
            diff = cv2.absdiff(self._prev_gray, gray)
            motion = np.sum(cv2.threshold(diff, 25, 255, cv2.THRESH_BINARY)[1]) / 255
        self._prev_gray = gray

        if not locs and motion > 2000:
            self._lookaside_frames += 1
            if (self._lookaside_frames >= config.LOOKASIDE_HOLD_FRAMES and
                    now - self._last_lookaside_t >= config.LOOKASIDE_COOLDOWN):
                self._last_lookaside_t = now
                self._lookaside_frames = 0
                self.on_lookaside()
            self._unknown_frames = max(0, self._unknown_frames - 2)
            return

        if not locs:
            self._lookaside_frames = 0
            self._unknown_frames   = max(0, self._unknown_frames - 2)
            if self._unknown_frames == 0 and self._state not in ('idle', 'waiting'):
                self._state = 'idle'
                self.on_nobody()
            return

        if self._state == 'idle' and self.on_motion and now - self._last_motion_t > config.TRIGGER_COOLDOWN:
            self._last_motion_t = now
            self.on_motion(frame)

        locs_full = [(t * 2, r * 2, b * 2, l * 2) for t, r, b, l in locs]
        valid = [loc for loc in locs_full if (loc[2] - loc[0]) >= config.MIN_FACE_SIZE]
        if not valid:
            self._lookaside_frames = 0
            return

        encs = face_recognition.face_encodings(
            cv2.cvtColor(frame, cv2.COLOR_BGR2RGB), valid,
        )

        if not encs:
            self._lookaside_frames += 1
            if (self._lookaside_frames >= config.LOOKASIDE_HOLD_FRAMES and
                    now - self._last_lookaside_t >= config.LOOKASIDE_COOLDOWN):
                self._last_lookaside_t = now
                self._lookaside_frames = 0
                self.on_lookaside()
            return

        self._lookaside_frames = 0
        cooldown_ok = (time.time() - self._last_trigger_t) >= config.TRIGGER_COOLDOWN

        with self._faces_lock:
            kenc  = list(self._known_encodings)
            kname = list(self._known_names)

        for enc in encs:
            if not kenc:
                self._unknown_frames += 1
                if (self._unknown_frames >= config.UNKNOWN_HOLD_FRAMES
                        and cooldown_ok and self._state != 'waiting'):
                    self._last_trigger_t = time.time()
                    self._unknown_frames = 0
                    self._trigger_unknown(frame)
                return

            distances = face_recognition.face_distance(kenc, enc)
            idx  = int(np.argmin(distances))
            dist = distances[idx]

            if dist <= config.RECOGNITION_TOLERANCE:
                if cooldown_ok:
                    name = kname[idx]
                    conf = (1.0 - dist) * 100.0
                    log.info(f'Match: {name}  dist={dist:.3f}  conf={conf:.1f}%')
                    self._last_trigger_t = time.time()
                    self._unknown_frames = 0
                    self._state          = 'known'
                    self.on_known(name, conf, frame)
            else:
                self._unknown_frames += 1
                if (self._unknown_frames >= config.UNKNOWN_HOLD_FRAMES
                        and cooldown_ok and self._state != 'waiting'):
                    self._last_trigger_t = time.time()
                    self._unknown_frames = 0
                    self._trigger_unknown(frame)

    # ── MOTION FALLBACK ───────────────────────────────────────────────────

    def _process_motion(self, frame: np.ndarray) -> None:
        gray = cv2.GaussianBlur(cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY), (21, 21), 0)
        if self._prev_gray is None:
            self._prev_gray = gray
            return

        diff   = cv2.absdiff(self._prev_gray, gray)
        motion = np.sum(cv2.threshold(diff, 25, 255, cv2.THRESH_BINARY)[1]) / 255
        self._prev_gray = gray

        now = time.time()
        cooldown_ok = (now - self._last_trigger_t) >= config.TRIGGER_COOLDOWN

        if motion > 2500:
            self._unknown_frames += 1
            if self._state == 'idle':
                self._state = 'person'
                if self.on_motion and now - self._last_motion_t > config.TRIGGER_COOLDOWN:
                    self._last_motion_t = now
                    self.on_motion(frame)
            if self._unknown_frames >= 8 and cooldown_ok and self._state != 'waiting':
                self._trigger_unknown(frame)
        else:
            self._unknown_frames = max(0, self._unknown_frames - 1)
            if self._unknown_frames == 0 and self._state not in ('idle', 'waiting'):
                self._state = 'idle'
                self.on_nobody()

    # ── TRIGGER UNKNOWN ───────────────────────────────────────────────────

    def _trigger_unknown(self, frame: np.ndarray) -> None:
        self._state = 'waiting'
        _, buf = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 65])
        b64 = base64.b64encode(buf).decode()
        threading.Thread(
            target=self.on_unknown, args=(frame, b64), daemon=True,
        ).start()

    # ── PUBLIC ────────────────────────────────────────────────────────────

    def get_current_frame(self) -> Optional[np.ndarray]:
        with self._frame_lock:
            return self._current_frame.copy() if self._current_frame is not None else None

    def reset_state(self) -> None:
        self._state           = 'idle'
        self._unknown_frames  = 0
        self._lookaside_frames = 0
        self._last_trigger_t  = 0.0

    def stop(self) -> None:
        self._running = False

    def listen_for_name(self, timeout: float = 12) -> str:
        try:
            import speech_recognition as sr
        except ImportError:
            log.warning('pip3 install SpeechRecognition pyaudio')
            return ''

        r = sr.Recognizer()
        r.pause_threshold = 1.0
        r.non_speaking_duration = 0.6
        r.energy_threshold = 300
        mic = sr.Microphone(device_index=config.MIC_DEVICE_INDEX)
        log.info('=== MIC OPEN — speak name now ===')
        try:
            with mic as source:
                r.adjust_for_ambient_noise(source, duration=0.5)
                log.info(f'Ambient noise calibrated — threshold: {r.energy_threshold:.0f}')
                audio = r.listen(source, timeout=timeout, phrase_time_limit=8)
            log.info('Audio captured — sending to Google STT...')
            result = r.recognize_google(audio, language=config.SPEECH_LANG)
            log.info(f'Heard: "{result}"')
            return result.strip()
        except sr.WaitTimeoutError:
            log.info('Mic timeout')
            return ''
        except sr.UnknownValueError:
            log.info('Could not understand')
            return ''
        except sr.RequestError as e:
            log.warning(f'Google STT request failed: {e}')
            return ''
        except Exception as e:
            log.info(f'Listen error: {e}')
            return ''
