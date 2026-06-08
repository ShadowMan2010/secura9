"""
SECURA-9 — GPIO Control
Handles door lock relay, PIR sensor, status LEDs.
Falls back gracefully if RPi.GPIO is not available (desktop testing).
"""

import logging
import time
import config

log = logging.getLogger('gpio')

try:
    import RPi.GPIO as GPIO
    GPIO_AVAILABLE = True
except (ImportError, RuntimeError):
    GPIO_AVAILABLE = False
    log.warning('RPi.GPIO not available — running in simulation mode')


class GPIOControl:
    def __init__(self):
        if not GPIO_AVAILABLE:
            log.info('GPIO simulation mode — door commands will be printed only')
            return

        GPIO.setmode(GPIO.BCM)
        GPIO.setwarnings(False)

        # Door relay
        GPIO.setup(config.DOOR_RELAY_PIN, GPIO.OUT)
        self._set_relay(False)   # ensure locked on start

        # PIR sensor
        if config.PIR_ENABLED:
            GPIO.setup(config.PIR_PIN, GPIO.IN)
            GPIO.add_event_detect(
                config.PIR_PIN,
                GPIO.RISING,
                callback=self._pir_callback,
                bouncetime=2000
            )
            log.info(f'PIR sensor active on GPIO {config.PIR_PIN}')

        log.info(f'GPIO ready — relay on GPIO {config.DOOR_RELAY_PIN}')

    # ── PIR ───────────────────────────────────────────────────────────────

    _pir_handler = None  # set by main to trigger camera wake-up

    def set_pir_callback(self, callback):
        self._pir_handler = callback

    def _pir_callback(self, channel):
        log.info('PIR: motion detected')
        if self._pir_handler:
            self._pir_handler()

    # ── DOOR RELAY ────────────────────────────────────────────────────────

    def unlock_door(self):
        log.info('DOOR UNLOCK')
        if not GPIO_AVAILABLE:
            print('[GPIO SIM] Door: UNLOCKED')
            return
        self._set_relay(True)

    def lock_door(self):
        log.info('DOOR LOCK')
        if not GPIO_AVAILABLE:
            print('[GPIO SIM] Door: LOCKED')
            return
        self._set_relay(False)

    def _set_relay(self, active: bool):
        if not GPIO_AVAILABLE:
            return
        # Most relay modules are active-LOW: HIGH = off, LOW = on
        if config.RELAY_ACTIVE_LOW:
            GPIO.output(config.DOOR_RELAY_PIN, GPIO.LOW if active else GPIO.HIGH)
        else:
            GPIO.output(config.DOOR_RELAY_PIN, GPIO.HIGH if active else GPIO.LOW)

    def is_door_unlocked(self) -> bool:
        if not GPIO_AVAILABLE:
            return False
        pin_val = GPIO.input(config.DOOR_RELAY_PIN)
        if config.RELAY_ACTIVE_LOW:
            return pin_val == GPIO.LOW
        return pin_val == GPIO.HIGH

    # ── CLEANUP ───────────────────────────────────────────────────────────

    def cleanup(self):
        if not GPIO_AVAILABLE:
            return
        self._set_relay(False)    # lock door on shutdown
        GPIO.cleanup()
        log.info('GPIO cleaned up')
