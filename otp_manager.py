"""
OTP Manager — generates, validates, and manages OTP lifecycle.
"""

import re
import time
import random
import logging

import config

log = logging.getLogger('otp')


class OTPManager:
    def __init__(self):
        self._otp: str = ''
        self._generated_at: float = 0.0
        self._valid = False
        self._custom_expiry: int = None

    def generate(self, expiry_seconds: int = None) -> str:
        """Generate a new 6-digit OTP with optional custom expiry."""
        self._otp = f'{random.randint(0, 999999):06d}'
        self._generated_at = time.time()
        self._valid = True
        self._custom_expiry = expiry_seconds
        log.info(f'OTP generated: {self._otp}'
                 f'{" (" + str(expiry_seconds) + "s)" if expiry_seconds else ""}')
        return self._otp

    def validate(self, spoken: str) -> tuple[bool, str]:
        """
        Validate spoken input against current OTP.
        Returns (valid: bool, reason: str).
        """
        if not self._valid:
            return False, 'no_otp'

        if self.seconds_remaining() <= 0:
            self._valid = False
            return False, 'expired'

        digits = re.sub(r'[^0-9]', '', spoken)

        if digits == self._otp:
            self._valid = False
            return True, ''

        if len(digits) < 4:
            return False, 'too_short'

        return False, 'wrong'

    def invalidate(self):
        """Force-invalidate current OTP."""
        self._valid = False
        log.info('OTP invalidated')

    def seconds_remaining(self) -> int:
        """Seconds left before OTP expires."""
        if not self._valid or not self._generated_at:
            return 0
        elapsed = time.time() - self._generated_at
        expiry = self._custom_expiry if self._custom_expiry else config.OTP_EXPIRY_SECONDS
        remaining = expiry - elapsed
        return max(0, int(remaining))
