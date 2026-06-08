"""
SECURA-9 — Voice module
Adds stop_mixer() / restart_mixer() so the audio device is
freed before the microphone opens.
"""

import base64
import json
import os
import logging
import threading
import subprocess
import time

import config

log = logging.getLogger('voice')

PHRASES = {
    'welcome'      : 'স্বাগতম! প্রবেশ অনুমোদিত হয়েছে।',
    'welcome_name' : 'স্বাগতম। দরজা খুলছে।',
    'show_face'    : 'অনুগ্রহ করে ক্যামেরার দিকে মুখ করুন।',
    'say_name'     : 'আপনাকে চেনা যাচ্ছে না। অনুগ্রহ করে আপনার নাম বলুন।',
    'wait_approval': 'অনুগ্রহ করে অপেক্ষা করুন।',
    'denied'       : 'দুঃখিত। প্রবেশ অস্বীকৃত হয়েছে।',
    'nobody_home'  : 'এই বাড়িতে এখন কেউ নেই। পরে আসুন।',
    'nobody_long'  : 'নমস্কার। সবাই বাইরে আছেন। পরে আসুন।',
    'door_opening' : 'দরজা খুলছে। ভেতরে আসুন।',
    'door_closing' : 'দরজা বন্ধ হচ্ছে।',
    'goodbye'      : 'ধন্যবাদ। বিদায়।',
    'system_on'    : 'নিরাপত্তা সিস্টেম চালু হয়েছে।',
}

VOICES_JSON = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                            'secura9_voices_1774932694371.json')


class Voice:
    def __init__(self):
        self._lock      = threading.Lock()
        self._mixer_ok  = False
        self._cache_dir = os.path.join(config.SOUNDS_PATH, '_tts_cache')
        os.makedirs(self._cache_dir, exist_ok=True)
        self._extract_json_voices()
        self._init_mixer()

    def _extract_json_voices(self):
        """Extract base64 audio from JSON and save as WAV files."""
        if not os.path.isfile(VOICES_JSON):
            log.info(f'Voices JSON not found: {VOICES_JSON}')
            return
        try:
            with open(VOICES_JSON) as f:
                data = json.load(f)
            count = 0
            for phrase_id, entry in data.items():
                audio = entry.get('audio', '')
                if not audio:
                    continue
                # data:audio/wav;base64,...
                if ',' in audio:
                    audio = audio.split(',', 1)[1]
                out_path = os.path.join(config.SOUNDS_PATH, f'{phrase_id}.wav')
                if os.path.isfile(out_path):
                    continue
                with open(out_path, 'wb') as f:
                    f.write(base64.b64decode(audio))
                count += 1
            if count:
                log.info(f'Extracted {count} voice files from JSON')
        except Exception as e:
            log.error(f'Failed to extract voices: {e}')

    def _init_mixer(self):
        try:
            import pygame
            if not pygame.get_init():
                pygame.init()
            pygame.mixer.init(frequency=44100, size=-16, channels=2, buffer=1024)
            self._mixer_ok = True
            log.info('pygame mixer ready')
        except Exception as e:
            log.warning(f'pygame mixer: {e}')

    # ── PUBLIC ────────────────────────────────────────────────────────────

    def play(self, phrase_id: str, name: str = ''):
        """Non-blocking."""
        threading.Thread(target=self._play, args=(phrase_id, name),
                         daemon=True).start()

    def play_sync(self, phrase_id: str, name: str = ''):
        """Blocking — returns only after audio fully finishes."""
        self._play(phrase_id, name)

    def play_loop(self, phrase_id: str = 'wait_approval'):
        """
        Play a sound in a continuous loop (like Google Meet ring).
        Call stop_loop() to end it.
        """
        try:
            import pygame
            path = self._find_audio(phrase_id)
            if not path:
                log.warning(f'No audio for loop: {phrase_id}')
                return
            if not self._mixer_ok:
                self.restart_mixer()
            pygame.mixer.music.load(path)
            pygame.mixer.music.set_volume(config.AUDIO_VOLUME)
            pygame.mixer.music.play(loops=-1)
            log.info(f'Looping: {phrase_id}')
        except Exception as e:
            log.warning(f'play_loop error: {e}')

    def stop_loop(self):
        """Stop the looping approval sound."""
        try:
            import pygame
            if pygame.mixer.get_init():
                pygame.mixer.music.stop()
                log.info('Loop stopped')
        except Exception as e:
            log.warning(f'stop_loop error: {e}')

    def stop_mixer(self):
        """
        Quit pygame mixer so the ALSA/PulseAudio device is released.
        Call this BEFORE opening the microphone.
        On many Linux systems the speaker and mic share one device and
        pygame keeps it locked exclusively.
        """
        try:
            import pygame
            if pygame.mixer.get_init():
                pygame.mixer.music.stop()
                pygame.mixer.quit()
                log.info('Mixer stopped — audio device released')
            time.sleep(0.3)   # give OS time to release the device
        except Exception as e:
            log.warning(f'stop_mixer: {e}')

    def restart_mixer(self):
        """Reinitialise mixer after listening is done."""
        try:
            import pygame
            if not pygame.mixer.get_init():
                pygame.mixer.init(frequency=44100, size=-16, channels=2, buffer=1024)
                self._mixer_ok = True
                log.info('Mixer restarted')
        except Exception as e:
            log.warning(f'restart_mixer: {e}')
            self._mixer_ok = False

    # ── INTERNAL ──────────────────────────────────────────────────────────

    def _play(self, phrase_id: str, name: str):
        with self._lock:
            try:
                path = self._find_audio(phrase_id)
                if path:
                    self._play_file(path)
                    return
                text = PHRASES.get(phrase_id, '')
                if not text:
                    return
                if name:
                    text = text.replace('[NAME]', name)
                self._tts(text, phrase_id)
            except Exception as e:
                log.error(f'Voice error ({phrase_id}): {e}')

    def _find_audio(self, phrase_id: str):
        for ext in ('.wav', '.mp3', '.ogg', '.flac', '.m4a'):
            p = os.path.join(config.SOUNDS_PATH, phrase_id + ext)
            if os.path.isfile(p):
                return p
        return None

    def _play_file(self, path: str):
        log.info(f'Playing: {os.path.basename(path)}')

        # Ensure mixer is up
        if not self._mixer_ok:
            self.restart_mixer()

        if self._mixer_ok:
            try:
                import pygame
                if not pygame.mixer.get_init():
                    self.restart_mixer()
                pygame.mixer.music.load(path)
                pygame.mixer.music.set_volume(config.AUDIO_VOLUME)
                pygame.mixer.music.play()
                while pygame.mixer.music.get_busy():
                    time.sleep(0.05)
                return
            except Exception as e:
                log.warning(f'pygame play failed: {e}')

        # Fallback system commands
        ext = os.path.splitext(path)[1].lower()
        cmds = {'.wav': ['aplay', '-q', path], '.mp3': ['mpg123', '-q', path]}
        cmd = cmds.get(ext, ['ffplay', '-nodisp', '-autoexit', '-loglevel', 'quiet', path])
        try:
            subprocess.run(cmd, check=False, timeout=30)
        except FileNotFoundError:
            log.warning(f'No player for {ext}')
        except Exception as e:
            log.warning(f'Playback: {e}')

    def _tts(self, text: str, phrase_id: str):
        cache = os.path.join(self._cache_dir, phrase_id + '.mp3')
        if os.path.isfile(cache):
            self._play_file(cache)
            return
        if self._gtts(text, cache):
            self._play_file(cache)
            return
        self._espeak(text)

    def _gtts(self, text: str, out: str) -> bool:
        try:
            from gtts import gTTS
            gTTS(text=text, lang='bn', slow=False).save(out)
            return True
        except Exception:
            return False

    def _espeak(self, text: str) -> bool:
        try:
            r = subprocess.run(
                ['espeak-ng', '-v', 'bn', '-s', '130', '-a', '180', text],
                check=False, capture_output=True, timeout=10
            )
            return r.returncode == 0
        except Exception:
            return False
