# SECURA-9 — Raspberry Pi Door Device

## File Structure

```
raspberry-pi/
├── main.py                 ← Entry point — start here
├── config.py               ← ALL settings (edit this first!)
├── face_engine.py          ← Camera + face recognition loop
├── voice.py                ← Audio announcements
├── gpio_control.py         ← Door relay + PIR sensor
├── ws_client.py            ← WebSocket → Node.js bridge
├── convert_voice_pack.py   ← Import Voice Studio recordings
├── install.sh              ← One-shot dependency installer
├── ui/
│   ├── __init__.py
│   └── display.py          ← Fullscreen Pygame cyberpunk UI
├── faces/                  ← Put known face JPGs here (name.jpg)
├── sounds/                 ← Converted voice clips land here
└── logs/                   ← Auto-created log files
```

---

## Hardware Wiring

```
Raspberry Pi 4 GPIO (BCM numbering)
────────────────────────────────────
GPIO 17  →  Relay IN   (door lock)
GPIO 24  →  PIR OUT    (optional motion sensor)
5V       →  Relay VCC
GND      →  Relay GND

Camera   →  Pi Camera port (ribbon cable) OR USB webcam
Speaker  →  3.5mm audio jack or USB speaker
Mic      →  USB microphone
HDMI     →  Monitor / TV for UI display
```

---

## Quick Start

```bash
# 1. Clone / copy files to Pi
cd /home/pi/secura9/raspberry-pi

# 2. Run installer
chmod +x install.sh
./install.sh

# 3. Edit settings
nano config.py          # Set SERVER_IP to your PC's local IP

# 4. Import your voice pack from Voice Studio
#    (Export from browser, copy .json to Pi, then:)
python3 convert_voice_pack.py secura9_voices_XXXXX.json

# 5. Add known face photos
cp /path/to/your-photo.jpg faces/Dhruba.jpg
cp /path/to/moms-photo.jpg faces/Maa.jpg

# 6. Start bridge server on your PC first
#    (on PC)  node server.js

# 7. Run SECURA-9
python3 main.py
```

---

## Screens

| Screen | When shown |
|---|---|
| Boot | System starting up |
| Monitoring | Idle — shows live camera feed |
| New Face | Unknown face detected — mic icon appears |
| Waiting | Name spoken — waiting for dashboard/ESP32 |
| Access Granted | Green overlay with name + door unlocks |
| Access Denied | Red overlay |
| Nobody Home | Camera + red broadcast banner |

**Keyboard shortcuts (when display is focused):**
- `ESC` — quit
- `F11` — toggle fullscreen

---

## Adding Known Faces

Simply copy a clear photo into the `faces/` folder:
```bash
cp photo.jpg faces/Dhruba.jpg     # filename becomes the person's display name
cp mom.jpg   faces/Maa.jpg
cp dad.jpg   faces/Baba.jpg
```

Any `.jpg`, `.jpeg`, `.png`, or `.bmp` file works. The system loads them automatically on startup. If you add faces while running, call `reload_faces()` or restart.

---

## Voice Pack

1. Open `voice-studio.html` in Chrome on any PC
2. Record all phrases or drop MP3/WAV files from Audacity
3. Click **Export Voice Pack** → saves a `.json` file
4. Copy the `.json` to the Pi
5. Run: `python3 convert_voice_pack.py your_file.json`
6. WAV files appear in `sounds/` — Pi picks them up automatically

If no recording exists for a phrase, falls back to gTTS (online) → espeak (offline).

---

## Config Reference

```python
# config.py — most important settings

SERVER_IP       = '192.168.1.100'   # PC running server.js
CAMERA_INDEX    = 0                 # 0 = first camera
DOOR_RELAY_PIN  = 17                # BCM GPIO for relay
DOOR_OPEN_SECONDS = 5               # door unlock duration
RECOGNITION_TOLERANCE = 0.5        # 0.4 = strict, 0.6 = loose
FULLSCREEN      = True              # False for windowed (dev mode)
DISPLAY_WIDTH   = 1920              # match your monitor
DISPLAY_HEIGHT  = 1080
```

---

## Troubleshooting

**Camera not opening:**
```bash
ls /dev/video*          # check camera is detected
vcgencmd get_camera     # for Pi Camera module
```

**face_recognition slow:**
```bash
# Use PROCESS_EVERY_N = 5 in config.py
# Or install dlib with NEON optimizations:
pip3 install dlib --no-binary dlib
```

**No sound:**
```bash
aplay -l                # list audio devices
amixer sset Master 80%  # set volume
```

**pygame display error (no display):**
```bash
export DISPLAY=:0       # if running via SSH
# Or add to service file: Environment=DISPLAY=:0
```

**WebSocket connection refused:**
- Make sure `node server.js` is running on PC first
- Check PC firewall allows port 3000
- Verify `SERVER_IP` in config.py matches your PC's LAN IP
