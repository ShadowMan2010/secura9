"""
SECURA-9 — Standalone mic listener

Uses arecord (ALSA, built into Linux) to capture audio — completely
separate from pygame so there is zero audio device conflict.

Quick self-test:
  python3 listen.py
"""

import os
import subprocess
import tempfile
import logging
import time

log = logging.getLogger('listen')

# Time tracking for budget-aware recording
_t0 = 0.0

# ─── Device cache — probed once, reused globally ────────────────────────
_CACHED_DEVICE = None
_DEVICE_PROBED = False

_PROBE_DEVICES = [
    'plughw:1,0',
    'plughw:0,0',
    'sysdefault',
    'hw:1,0',
    'hw:0,0',
]


def _probe_device(force: bool = False) -> str | None:
    """
    Probe once and cache the first working ALSA capture device.
    Returns None if arecord is not installed.

    First tries 'default' (quick win with PulseAudio), then falls back
    to specific HW devices.  Results are cached globally so subsequent
    calls are instant.
    """
    global _CACHED_DEVICE, _DEVICE_PROBED
    if _DEVICE_PROBED and not force:
        return _CACHED_DEVICE

    try:
        subprocess.run(['arecord', '--version'], capture_output=True, timeout=3)
    except Exception:
        _CACHED_DEVICE = None
        _DEVICE_PROBED = True
        return None

    # Quick check: 'default' almost always works (PulseAudio)
    for dev in ['default'] + _PROBE_DEVICES:
        try:
            fd, wav = tempfile.mkstemp(suffix='.wav')
            os.close(fd)
            r = subprocess.run(
                ['arecord', '-q', '-D', dev, '-f', 'S16_LE', '-r', '16000',
                 '-c', '1', '-d', '1', wav],
                timeout=3, capture_output=True
            )
            os.unlink(wav)
            if r.returncode == 0:
                log.info('arecord device OK: %s', dev)
                _CACHED_DEVICE = dev
                _DEVICE_PROBED = True
                return dev
        except Exception:
            continue

    _CACHED_DEVICE = 'default'   # last resort
    _DEVICE_PROBED = True
    return 'default'


# Eager probe on import so the device is cached before the first use.
# Runs ~1 s and saves 6+ s later.  If it fails, the live call will
# probe again.
if os.environ.get('SECURA9_SKIP_PROBE', '') != '1':
    _probe_device()


def listen_for_name(timeout: int = 10,
                    speech_lang: str = 'bn-IN',
                    device: str = '') -> str:
    """
    Record audio and return recognised text.
    Returns '' on any failure — caller handles the empty case.

    *device* overrides ALSA device detection; pass from config.py.
    """
    log.info('=== MIC OPEN — speak name now ===')
    global _t0
    _t0 = time.time()

    alsa_dev = device or _probe_device()
    if alsa_dev:
        result = _arecord_stt(timeout, speech_lang, alsa_dev)
        if result:
            return result

    # Try 'default' as final fallback if cached device isn't already 'default'
    if alsa_dev and alsa_dev != 'default':
        result = _arecord_stt(timeout, speech_lang, 'default')
        if result:
            return result

    log.warning('Mic: nothing captured (%.1fs)', time.time() - _t0)
    return ''


# ─── METHOD 1: arecord → WAV file → Google STT ─────────────────────────

def _arecord_stt(timeout: int, lang: str, device: str = 'default') -> str:
    wav = None
    try:
        fd, wav = tempfile.mkstemp(suffix='.wav')
        os.close(fd)

        # Use remaining timeout (minus 1s margin) for recording
        elapsed = time.time() - _t0 if _t0 else 0
        budget = max(timeout - elapsed - 1, 2)
        duration = min(budget, 8)
        log.info('arecord %.0fs (device=%s)', duration, device)

        r = subprocess.run(
            ['arecord', '-q', '-D', device, '-f', 'S16_LE', '-r', '16000',
             '-c', '1', '-d', str(int(duration)), wav],
            timeout=int(duration) + 4,
            capture_output=True
        )

        if r.returncode != 0:
            log.warning('arecord failed: %s', r.stderr.decode().strip()[:120])
            return ''

        size = os.path.getsize(wav)
        log.info('Recorded %d bytes', size)
        if size < 8000:
            log.info('Too short / silence')
            return ''

        return _stt_from_wav(wav, lang)

    except FileNotFoundError:
        log.warning('arecord missing — sudo apt install alsa-utils')
        return ''
    except subprocess.TimeoutExpired:
        log.warning('arecord timeout')
        return ''
    except Exception as e:
        log.warning('arecord error: %s', e)
        return ''
    finally:
        if wav and os.path.exists(wav):
            try:
                os.unlink(wav)
            except Exception:
                pass


def _stt_from_wav(wav_path: str, lang: str) -> str:
    try:
        import speech_recognition as sr
    except ImportError:
        log.warning('pip3 install SpeechRecognition')
        return ''
    try:
        r = sr.Recognizer()
        with sr.AudioFile(wav_path) as src:
            audio = r.record(src)
        # Try target language first, then fall back to English
        for l in [lang, 'en-US', 'en-IN']:
            try:
                text = r.recognize_google(audio, language=l)
                if text:
                    log.info('STT (%s): "%s"', l, text)
                    return text.strip()
            except sr.UnknownValueError:
                continue
            except sr.RequestError as e:
                log.warning('STT request error: %s', e)
                return ''
        return ''
    except Exception as e:
        log.warning('STT error: %s', e)
        return ''


