# SECURA-9

> **Status: In Development** — actively being built and tested on Raspberry Pi hardware.

Face recognition door access system with OTP fallback, Firebase push notifications, WebRTC live view, Android companion app, and web dashboard.

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

## Components

| Directory | Description |
|-----------|-------------|
| `raspberry-pi/` | Pi controller: face engine, display (pygame), voice (Bengali TTS), WebRTC broadcaster, OTP, Firebase notifier |
| `dashboard/` | Web dashboard (Firebase Hosting) — approve/deny faces, live camera, talkback, door control |
| `secura9-android/` | Android app (Jetpack Compose) — live view, two-way audio, approval management, FCM push |

## Features

- **Face Recognition** — identify known faces, detect unknowns, nobody-home mode
- **OTP Access** — 6-digit OTP via on-screen numpad with lockout protection
- **Bengali Voice** — TTS announcements for all states (gTTS + pre-recorded)
- **WebRTC Live View** — real-time camera + two-way audio via Firestore signaling
- **Push Notifications** — FCM alerts for approval requests, motion, system events
- **Android App** — live camera, talkback, approve/deny, door control
- **Web Dashboard** — same features in browser, deployed to Firebase Hosting
- **Nobody Home Mode** — redirects known faces to approval queue, unknowns to OTP

## Setup

```bash
# Raspberry Pi
cd raspberry-pi
cp firebase_adapter/serviceAccountKey.json.example firebase_adapter/serviceAccountKey.json
pip install -r requirements.txt
python3 main.py

# Dashboard
cd dashboard
firebase login
firebase deploy --only hosting

# Android
open secura9-android/ in Android Studio
Update google-services.json from Firebase Console
Build and run
```

## License

MIT
