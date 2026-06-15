import time
import logging
from datetime import datetime, time as dt_time
import config

log = logging.getLogger('scheduler')


class TimeSlot:
    def __init__(self, day_bits: int, start_hour: int, start_min: int,
                 end_hour: int, end_min: int):
        self.day_bits = day_bits
        self.start = dt_time(start_hour, start_min)
        self.end = dt_time(end_hour, end_min)

    def in_slot(self, now: datetime = None) -> bool:
        if now is None:
            now = datetime.now()
        if not (self.day_bits & (1 << now.weekday())):
            return False
        t = now.time()
        if self.start <= self.end:
            return self.start <= t <= self.end
        return t >= self.start or t <= self.end


class AccessSchedule:
    def __init__(self, rules: list[dict] = None):
        self._slots: list[TimeSlot] = []
        self._denied_names: set = set()
        if rules:
            self.load(rules)

    def load(self, rules: list[dict]):
        self._slots = []
        for r in rules:
            self._slots.append(TimeSlot(
                day_bits=r.get('days', 127),
                start_hour=r.get('startHour', 0),
                start_min=r.get('startMin', 0),
                end_hour=r.get('endHour', 23),
                end_min=r.get('endMin', 59),
            ))
            denied = r.get('denyNames', [])
            self._denied_names.update(denied)
        log.info(f'Schedule loaded: {len(self._slots)} slots, '
                 f'{len(self._denied_names)} denied names')

    def is_allowed(self, name: str = '') -> bool:
        if not config.SCHEDULE_ENABLED or not self._slots:
            return True
        if name and name.lower() in self._denied_names:
            log.warning(f'Schedule denied: {name}')
            return False
        for slot in self._slots:
            if slot.in_slot():
                return True
        return False

    def to_dict(self) -> list:
        return [
            {
                'days': s.day_bits,
                'startHour': s.start.hour,
                'startMin': s.start.minute,
                'endHour': s.end.hour,
                'endMin': s.end.minute,
            }
            for s in self._slots
        ]


_DEFAULT = AccessSchedule()
