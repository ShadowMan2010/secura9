# SECURA-9

> Biometric access control system for Raspberry Pi / x86.

Face recognition door lock with OTP fallback, Firebase push notifications, WebRTC live view, Android companion app, and web dashboard.

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Raspberry Pi   │────▶│    Firebase      │◀────│   Android App   │
│  (Python)       │     │  Firestore + FCM │     │   (Kotlin)      │
│                 │     │                  │     │                 │
│  - Face recog   │     │  - Approvals     │     │  - Google Auth  │
│  - OTP entry    │     │  - Decisions     │     │  - Live video   │
│  - Voice (bn)   │     │  - WebRTC sig.   │     │  - Two-way talk │
│  - WebRTC       │     │  - Notifications │     │  - Approvals    │
│  - GPIO relay   │     │  - Status        │     │                 │
└─────────────────┘     └──────────────────┘     └─────────────────┘
                                │
                        ┌───────┴───────┐
                        │  Web Dashboard │
                        │ (Firebase Host)│
                        └───────────────┘
```

## Hardware Wiring (Raspberry Pi)

```
GPIO 17  →  Relay IN   (door lock)
GPIO 24  →  PIR OUT    (motion sensor, optional)
5V       →  Relay VCC
GND      →  Relay GND
Camera   →  Pi Camera / USB webcam
Speaker  →  3.5mm / USB
Mic      →  USB microphone
```

## Quick Start

```bash
# Flash the OS image to SD card:
#   sudo bash deploy/image-builder/build-pi-image.sh
#   dd if=build/secura9-pi.img.gz of=/dev/sdX bs=4M status=progress

# Or install manually on Raspberry Pi OS / Ubuntu:
sudo bash deploy/install.sh
```

First boot: device starts WiFi AP `SECURA9-Setup`. Scan the QR code on screen, upload Firebase credentials, and the device auto-configures.

## Features

- **Face Recognition** — identify known faces, detect unknowns, nobody-home mode
- **OTP Access** — 6-digit OTP via on-screen numpad with lockout
- **Bengali Voice** — TTS announcements (gTTS + pre-recorded)
- **WebRTC Live View** — real-time camera + two-way audio via Firestore signaling
- **Push Notifications** — FCM alerts for approval requests, motion, system events
- **Android App** — live camera, talkback, approve/deny, door control
- **Web Dashboard** — same features in browser, Firebase Hosting
- **Full-Screen Boot Animation** — 5-phase cyberpunk boot sequence
- **QR Code Provisioning** — first-boot wizard for zero-config setup
- **OTA Updates** — git-based atomic updates with rollback

## Components

| Directory | Description |
|-----------|-------------|
| `raspberry-pi/` | Pi/x86 controller: face engine, display, voice, WebRTC, OTP, Firebase |
| `android-dashboard/` | Android app (Jetpack Compose) — live view, two-way audio, approvals |
| `deploy/` | OS tooling: systemd services, installer, provisioner, OTA updater, image builder |

## Screens (Pygame UI)

| Screen | Description |
|--------|-------------|
| Boot | 5-phase full-screen animation with POST lines |
| Monitoring | Live camera feed with cyberpunk HUD |
| New Face | Unknown detected — mic icon + name prompt |
| Waiting | Name recorded — pending approval |
| Access Granted | Green overlay + door unlock |
| Access Denied | Red overlay |
| OTP | On-screen numpad entry |

## License

MIT
