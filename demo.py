"""
SECURA-9 Demo — shows boot animation + cycles through all UI states.
Press ESC to quit anytime. No hardware required.
"""
import os, sys, time
os.environ.setdefault('SDL_VIDEO_WINDOW_POS', '100,50')

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config

config.FULLSCREEN = False
config.HEADLESS = False
config.DISPLAY_WIDTH = 540
config.DISPLAY_HEIGHT = 960
config.SHOW_FPS = False

from ui.display import Display

d = Display()
d.show_boot()

states = [
    (3,  d.show_idle,        'IDLE — waiting for face'),
    (3,  d.show_new_face,    'NEW_FACE — unknown detected'),
    (3,  lambda: d.show_waiting('Alice'), 'WAITING — pending approval'),
    (4,  lambda: d.show_granted('Alice', 95), 'GRANTED — access granted 🟢'),
    (3,  d.show_denied,      'DENIED — access denied 🔴'),
    (3,  d.show_nobody_home, 'NOBODY_HOME — redirect to approval'),
    (3,  lambda: d.show_message('SAY YOUR NAME'), 'MESSAGE — mic prompt'),
    (4,  lambda: d.show_otp_waiting('291847', 45), 'OTP_WAITING — 6-digit code shown'),
    (8,  lambda: d.show_otp_enter(1, 3, 60), 'OTP_ENTER — numpad entry (click digits!)'),
    (3,  lambda: d.show_otp_wrong(2), 'OTP_WRONG — incorrect attempt'),
    (3,  d.show_otp_expired, 'OTP_EXPIRED — code expired'),
]

def cycle():
    time.sleep(5.5)  # let boot animation finish
    for delay, fn, label in states:
        fn()
        print(f'  ▸ {label}')
        time.sleep(delay)
    print('\n  ✓ Cycle complete — closing...')
    d.stop()

import threading
threading.Thread(target=cycle, daemon=True).start()

print('\n  ╔══════════════════════════╗')
print('  ║   SECURA-9  Demo         ║')
print('  ║  540×960 windowed mode   ║')
print('  ║  ESC to quit             ║')
print('  ╚══════════════════════════╝\n')
print('  Boot animation starting...\n')

d.run()
