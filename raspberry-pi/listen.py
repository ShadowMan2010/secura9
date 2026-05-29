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

# Default ALSA devices to try in order
_ALSA_DEVICES = [
    'default',
    'plughw:1,0',
    'plughw:2,0',
    'plughw:0,0',
    'sysdefault',
    'hw:1,0',
    'hw:2,0',
    'hw:0,0',
]


def _find_working_arecord():
    """Return the first ALSA device that can record, or 'default'."""
    try:
        subprocess.run(['arecord', '--version'], capture_output=True, timeout=3)
    except Exception:
        return None
    for dev in _ALSA_DEVICES:
        try:
            fd, wav = tempfile.mkstemp(suffix='.wav')
            os.close(fd)
            r = subprocess.run(
                ['arecord', '-q', '-D', dev, '-f', 'S16_LE', '-r', '16000',
                 '-c', '1', '-d', '1', wav],
                timeout=5, capture_output=True
            )
            os.unlink(wav)
            if r.returncode == 0:
                log.info(f'arecord device OK: {dev}')
                return dev
        except Exception:
            continue
    return 'default'


def listen_for_name(timeout: int = 10,
                    speech_lang: str = 'bn-IN',
                    device: str = '') -> str:
    """
    Record audio and return recognised text.
    Returns '' on any failure — caller handles the empty case.
    """
    log.info('=== MIC OPEN — speak name now ===')

    # Try arecord with auto-detected or specified device
    alsa_dev = device or _find_working_arecord()
    if alsa_dev:
        result = _arecord_stt(timeout, speech_lang, alsa_dev)
        if result:
            return result

    # Try arecord with default (might work even if probing failed)
    result = _arecord_stt(timeout, speech_lang, 'default')
    if result:
        return result

    # Fallback: SpeechRecognition + PyAudio
    result = _pyaudio_stt(timeout, speech_lang)
    if result:
        return result

    log.warning('Mic: nothing captured')
    return ''


# ─────────────────────────────────────────────────────────────────────────
# METHOD 1: arecord → WAV file → Google STT
# ─────────────────────────────────────────────────────────────────────────

def _arecord_stt(timeout: int, lang: str, device: str = 'default') -> str:
    wav = None
    try:
        fd, wav = tempfile.mkstemp(suffix='.wav')
        os.close(fd)

        duration = min(timeout, 8)
        log.info(f'arecord {duration}s (device={device})...')

        r = subprocess.run(
            ['arecord', '-q', '-D', device, '-f', 'S16_LE', '-r', '16000',
             '-c', '1', '-d', str(duration), wav],
            timeout=duration + 4,
            capture_output=True
        )

        if r.returncode != 0:
            log.warning('arecord failed: ' + r.stderr.decode().strip()[:120])
            return ''

        size = os.path.getsize(wav)
        log.info(f'Recorded {size} bytes')
        if size < 8000:          # < ~0.25 s — silence
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
        log.warning(f'arecord error: {e}')
        return ''
    finally:
        if wav and os.path.exists(wav):
            try: os.unlink(wav)
            except Exception: pass


def _stt_from_wav(wav_path: str, lang: str) -> str:
    try:
        import speech_recognition as sr
    except ImportError:
        log.warning('pip3 install SpeechRecognition --break-system-packages')
        return ''
    try:
        r = sr.Recognizer()
        with sr.AudioFile(wav_path) as src:
            audio = r.record(src)
        for l in ['en-IN', 'en-US', lang]:
            try:
                text = r.recognize_google(audio, language=l)
                if text:
                    log.info(f'STT ({l}): "{text}"')
                    return text.strip()
            except sr.UnknownValueError:
                continue
            except sr.RequestError as e:
                log.warning(f'STT request error: {e}')
                return ''
        return ''
    except Exception as e:
        log.warning(f'STT error: {e}')
        return ''


# ─────────────────────────────────────────────────────────────────────────
# METHOD 2: SpeechRecognition + PyAudio (direct)
# ─────────────────────────────────────────────────────────────────────────

def _pyaudio_stt(timeout: int, lang: str) -> str:
    try:
        import speech_recognition as sr
        r = sr.Recognizer()
        r.pause_threshold = 1.2
        r.dynamic_energy_threshold = True
        mic = sr.Microphone()
        log.info('PyAudio mic — speak now')
        with mic as src:
            r.adjust_for_ambient_noise(src, duration=0.6)
            audio = r.listen(src, timeout=timeout, phrase_time_limit=8)
        for l in ['en-IN', 'en-US', lang]:
            try:
                text = r.recognize_google(audio, language=l)
                if text:
                    log.info(f'PyAudio STT ({l}): "{text}"')
                    return text.strip()
            except sr.UnknownValueError:
                continue
            except sr.RequestError as e:
                log.warning(f'STT request: {e}')
                return ''
    except Exception as e:
        log.warning(f'PyAudio error: {e}')
    return ''


# ─────────────────────────────────────────────────────────────────────────
# Self-test
# ─────────────────────────────────────────────────────────────────────────

if __name__ == '__main__':
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s %(levelname)s: %(message)s'
    )
    print('\n──────────────────────────────────')
    print(' SECURA-9 Mic Test')
    print('──────────────────────────────────')

    # Test 1: can arecord record at all?
    print('\n[1] Testing arecord (3 second recording)...')
    fd, tmp = tempfile.mkstemp(suffix='.wav')
    os.close(fd)
    r = subprocess.run(
        ['arecord', '-q', '-f', 'S16_LE', '-r', '16000', '-c', '1', '-d', '3', tmp],
        capture_output=True
    )
    if r.returncode == 0:
        sz = os.path.getsize(tmp)
        print(f'    ✓ arecord works — {sz} bytes recorded')
        os.unlink(tmp)
    else:
        print(f'    ✗ arecord failed: {r.stderr.decode().strip()}')
        print('    Fix: sudo apt install alsa-utils')
        print('    Also try: arecord -l   to list devices')
        print('    If wrong device: arecord -D hw:1,0 ...')

    # Test 2: full STT test
    print('\n[2] Full speech-to-text test (say your name):')
    result = listen_for_name(timeout=8, speech_lang='en-IN')
    if result:
        print(f'\n    ✓ Heard: "{result}"')
    else:
        print('\n    ✗ Nothing captured or understood')
        print('\nDebug commands:')
        print('  arecord -l')
        print('  arecord -d 3 /tmp/test.wav && aplay /tmp/test.wav')
        print('  pip3 install SpeechRecognition --break-system-packages')
