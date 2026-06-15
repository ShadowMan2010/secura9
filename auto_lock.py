import threading
import time
import logging
import config

log = logging.getLogger('auto_lock')


class AutoLockManager:
    def __init__(self, gpio, display, notif=None):
        self._gpio = gpio
        self._display = display
        self._notif = notif
        self._lock = threading.Lock()
        self._timer: threading.Timer = None
        self._passage_mode = config.PASSAGE_MODE_DEFAULT
        self._auto_lock_enabled = config.AUTO_LOCK_ENABLED
        self._delay = config.AUTO_LOCK_DELAY_SECONDS

    def on_unlock(self):
        with self._lock:
            self._cancel_timer()
            if self._passage_mode:
                log.info('Passage mode active — door stays unlocked')
                self._display.set_status(passage=True)
                return
            if not self._auto_lock_enabled:
                return
            log.info(f'Auto-lock timer: {self._delay}s')
            self._timer = threading.Timer(self._delay, self._do_lock)
            self._timer.daemon = True
            self._timer.start()

    def on_lock(self):
        with self._lock:
            self._cancel_timer()
            self._display.set_status(passage=False)

    def toggle_passage(self, active: bool = None):
        with self._lock:
            if active is None:
                self._passage_mode = not self._passage_mode
            else:
                self._passage_mode = active
            log.info(f'Passage mode: {"ON" if self._passage_mode else "OFF"}')
            self._display.set_status(passage=self._passage_mode)
            if self._passage_mode:
                self._cancel_timer()
                self._gpio.unlock_door()
                self._display.show_message('PASSAGE MODE — door unlocked')
            else:
                self._gpio.lock_door()
                if self._notif:
                    self._notif.send('Passage mode off', title='Door Locked')

    @property
    def passage_active(self):
        return self._passage_mode

    def set_delay(self, seconds: int):
        self._delay = max(3, seconds)

    def _do_lock(self):
        log.info('Auto-lock: locking door')
        self._gpio.lock_door()
        self._display.set_status(door_locked=True, passage=False)
        if self._notif:
            self._notif.send('Door auto-locked', title='Auto Lock')

    def _cancel_timer(self):
        if self._timer and self._timer.is_alive():
            self._timer.cancel()
            self._timer = None

    def cleanup(self):
        self._cancel_timer()
        self._passage_mode = False
