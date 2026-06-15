import re
import time
import random
import logging
import config

log = logging.getLogger('timer_codes')


class TimedCode:
    def __init__(self, code: str, expires_at: float, label: str = '',
                 single_use: bool = True):
        self.code = code
        self.expires_at = expires_at
        self.label = label
        self.single_use = single_use
        self._used = False

    @property
    def expired(self) -> bool:
        return time.time() >= self.expires_at

    @property
    def remaining(self) -> int:
        return max(0, int(self.expires_at - time.time()))

    def use(self) -> bool:
        if self.expired or (self.single_use and self._used):
            return False
        self._used = True
        return True


class TimerCodeManager:
    def __init__(self):
        self._codes: dict[str, TimedCode] = {}

    def generate(self, duration_seconds: int = None,
                 label: str = '', single_use: bool = True) -> str:
        if duration_seconds is None:
            duration_seconds = config.TIMER_CODE_DEFAULT_SECONDS
        code = f'{random.randint(0, 999999):06d}'
        expires = time.time() + duration_seconds
        self._codes[code] = TimedCode(code, expires, label, single_use)
        log.info(f'Timed code {code} generated for {duration_seconds}s'
                 f'{" (single-use)" if single_use else ""}'
                 f'{f" — {label}" if label else ""}')
        return code

    def generate_from_firebase(self, data: dict) -> str:
        duration = data.get('durationSeconds', config.TIMER_CODE_DEFAULT_SECONDS)
        label = data.get('label', '')
        single_use = data.get('singleUse', True)
        return self.generate(duration, label, single_use)

    def validate(self, code: str) -> tuple[bool, str]:
        entry = self._codes.get(code)
        if not entry:
            return False, 'not_found'
        if entry.expired:
            del self._codes[code]
            return False, 'expired'
        if entry.single_use and entry._used:
            return False, 'already_used'
        return True, ''

    def redeem(self, code: str) -> tuple[bool, str, str]:
        valid, reason = self.validate(code)
        if not valid:
            return False, reason, ''
        entry = self._codes[code]
        entry._used = True
        if entry.single_use:
            del self._codes[code]
        log.info(f'Timed code {code} redeemed{f" — {entry.label}" if entry.label else ""}')
        return True, '', entry.label

    def cleanup_expired(self):
        now = time.time()
        expired = [c for c, e in self._codes.items() if e.expires_at <= now]
        for c in expired:
            del self._codes[c]
        if expired:
            log.info(f'Cleaned {len(expired)} expired timed codes')
