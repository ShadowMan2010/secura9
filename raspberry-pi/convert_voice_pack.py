#!/usr/bin/env python3
"""
SECURA-9 Voice Pack Converter
══════════════════════════════════════════════════════════════
Converts the .json voice pack exported from Voice Studio
into individual .wav files that the Pi can play directly.

Usage:
  python3 convert_voice_pack.py secura9_voices_123456.json

Output:
  sounds/welcome.wav
  sounds/say_name.wav
  sounds/denied.wav
  ... (one file per recorded phrase)

Requirements:
  pip3 install pydub
  sudo apt install ffmpeg  (for mp3/webm conversion)
══════════════════════════════════════════════════════════════
"""

import json
import base64
import os
import sys
import tempfile

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), 'sounds')
os.makedirs(OUTPUT_DIR, exist_ok=True)


def convert(json_path: str):
    print(f'Loading voice pack: {json_path}')

    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    count = 0
    for phrase_id, val in data.items():
        # Support both old format (string) and new format (object with audio key)
        audio_data = val if isinstance(val, str) else val.get('audio', '')
        if not audio_data:
            print(f'  [SKIP] {phrase_id}: no audio data')
            continue

        try:
            # Parse data URI:  data:audio/wav;base64,XXXX
            header, b64 = audio_data.split(',', 1)
            mime = header.split(':')[1].split(';')[0]   # e.g. audio/wav or audio/webm

            raw = base64.b64decode(b64)

            # Determine extension from MIME
            ext_map = {
                'audio/wav'  : '.wav',
                'audio/webm' : '.webm',
                'audio/ogg'  : '.ogg',
                'audio/mp4'  : '.m4a',
                'audio/mpeg' : '.mp3',
            }
            src_ext = ext_map.get(mime, '.webm')

            out_wav = os.path.join(OUTPUT_DIR, phrase_id + '.wav')

            if src_ext == '.wav':
                # Already WAV — write directly
                with open(out_wav, 'wb') as f:
                    f.write(raw)
                print(f'  [OK]   {phrase_id}.wav  ({len(raw)//1024} KB)')
            else:
                # Need to convert — try pydub then ffmpeg
                with tempfile.NamedTemporaryFile(suffix=src_ext, delete=False) as tmp:
                    tmp.write(raw)
                    tmp_path = tmp.name

                converted = False
                # Try pydub
                try:
                    from pydub import AudioSegment
                    seg = AudioSegment.from_file(tmp_path)
                    seg = seg.set_frame_rate(44100).set_channels(1)
                    seg.export(out_wav, format='wav')
                    converted = True
                    print(f'  [OK]   {phrase_id}.wav  (converted from {src_ext} via pydub)')
                except ImportError:
                    pass
                except Exception as e:
                    print(f'  pydub failed: {e}')

                # Try ffmpeg directly
                if not converted:
                    ret = os.system(
                        f'ffmpeg -y -i "{tmp_path}" -ar 44100 -ac 1 '
                        f'-acodec pcm_s16le "{out_wav}" -loglevel quiet'
                    )
                    if ret == 0:
                        converted = True
                        print(f'  [OK]   {phrase_id}.wav  (converted from {src_ext} via ffmpeg)')
                    else:
                        # Last resort: save as original format, voice.py handles it
                        out_raw = os.path.join(OUTPUT_DIR, phrase_id + src_ext)
                        with open(out_raw, 'wb') as f:
                            f.write(raw)
                        print(f'  [WARN] {phrase_id}{src_ext}  (kept as {src_ext} — install ffmpeg to convert)')

                os.unlink(tmp_path)

            count += 1

        except Exception as e:
            print(f'  [ERR]  {phrase_id}: {e}')

    print(f'\nDone! {count} voices saved to: {OUTPUT_DIR}')
    print('\nNext step:  python3 main.py')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python3 convert_voice_pack.py path/to/secura9_voices_XXXXX.json')
        sys.exit(1)
    convert(sys.argv[1])
