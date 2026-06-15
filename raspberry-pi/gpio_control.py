"""
SECURA-9 — GPIO Control
Handles door lock relay, PIR sensor, tamper sensor, IR illuminator, light sensor.
Falls back gracefully if RPi.GPIO is not available (desktop testing).
"""

import logging
import time
from datetime import datetime
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
        self._pir_handler = None
        self._tamper_callback = None
        self._ir_on = False

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

        # Tamper sensor
        if config.TAMPER_ENABLED:
            try:
                GPIO.setup(config.TAMPER_PIN, GPIO.IN, pull_up_down=GPIO.PUD_UP)
                GPIO.add_event_detect(
                    config.TAMPER_PIN,
                    GPIO.FALLING,
                    callback=self._tamper_handler,
                    bouncetime=500
                )
                log.info(f'Tamper sensor active on GPIO {config.TAMPER_PIN}')
            except Exception as e:
                log.warning(f'Tamper sensor setup failed: {e}')

        # IR illuminator
        if config.IR_ILLUMINATOR_ENABLED:
            GPIO.setup(config.IR_ILLUMINATOR_PIN, GPIO.OUT)
            try:
                self._ir_pwm = GPIO.PWM(config.IR_ILLUMINATOR_PIN, 1000)  # 1 kHz
                self._ir_pwm.start(0)
                log.info(f'IR illuminator PWM on GPIO {config.IR_ILLUMINATOR_PIN}')
            except Exception:
                self._ir_pwm = None
                log.info(f'IR illuminator GPIO {config.IR_ILLUMINATOR_PIN} (digital)')

        # Light sensor
        if config.LIGHT_SENSOR_ENABLED:
            GPIO.setup(config.LIGHT_SENSOR_PIN, GPIO.IN, pull_up_down=GPIO.PUD_UP)
            log.info(f'Light sensor on GPIO {config.LIGHT_SENSOR_PIN}')

        log.info(f'GPIO ready — relay on GPIO {config.DOOR_RELAY_PIN}')

    # ── PIR ───────────────────────────────────────────────────────────────

    def set_pir_callback(self, callback):
        self._pir_handler = callback

    def _pir_callback(self, channel):
        log.info('PIR: motion detected')
        if self._pir_handler:
            self._pir_handler()

    # ── TAMPER ─────────────────────────────────────────────────────────────

    def set_tamper_callback(self, callback):
        self._tamper_callback = callback

    def _tamper_handler(self, channel):
        log.warning('TAMPER: sensor triggered!')
        if self._tamper_callback:
            self._tamper_callback()

    # ── IR ILLUMINATOR ─────────────────────────────────────────────────────

    def set_ir(self, on: bool):
        if self._ir_on == on:
            return
        self._ir_on = on
        if not GPIO_AVAILABLE:
            print(f'[GPIO SIM] IR: {"ON" if on else "OFF"}')
            return
        pin = config.IR_ILLUMINATOR_PIN
        if hasattr(self, '_ir_pwm') and self._ir_pwm is not None:
            duty = config.IR_ILLUMINATOR_BRIGHTNESS if on else 0
            self._ir_pwm.ChangeDutyCycle(duty)
        else:
            GPIO.output(pin, GPIO.HIGH if on else GPIO.LOW)
        log.info(f'IR illuminator: {"ON" if on else "OFF"}')

    @property
    def ir_active(self) -> bool:
        return self._ir_on

    # ── LIGHT SENSOR ──────────────────────────────────────────────────────

    @staticmethod
    def is_dark() -> bool:
        if not config.LIGHT_SENSOR_ENABLED or not GPIO_AVAILABLE:
            return GPIOControl._is_dark_fallback()
        try:
            pin = GPIO.input(config.LIGHT_SENSOR_PIN)
            return pin == GPIO.LOW  # LOW = dark (typical LM393 DO pin)
        except Exception:
            return GPIOControl._is_dark_fallback()

    @staticmethod
    def _is_dark_fallback() -> bool:
        h = datetime.now().hour
        return h < 6 or h >= 19

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
        self.set_ir(False)
        if hasattr(self, '_ir_pwm') and self._ir_pwm is not None:
            self._ir_pwm.stop()
        self._set_relay(False)
        GPIO.cleanup()
        log.info('GPIO cleaned up')
