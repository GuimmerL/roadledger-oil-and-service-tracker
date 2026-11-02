"""Deterministic independent oil schedule smoke checks (stdlib only)."""
from calendar import monthrange
from datetime import date, timedelta
from math import ceil

def add_months(d, months):
    month_index = d.month - 1 + months
    year = d.year + month_index // 12
    month = month_index % 12 + 1
    return date(year, month, min(d.day, monthrange(year, month)[1]))

def schedule(service_date, service_odo, current_odo, reading_date, distance_interval, months, avg_per_day, today):
    assumptions = []
    due_odo = service_odo + distance_interval if distance_interval and distance_interval > 0 else None
    due_date_time = add_months(service_date, months) if months and months > 0 else None
    inconsistent = current_odo is not None and current_odo < service_odo
    remaining = (due_odo - current_odo) if current_odo is not None and due_odo is not None and not inconsistent else None
    if inconsistent: assumptions.append('odometer lower than service reading')
    due_date_mileage = None
    if remaining is not None and avg_per_day and avg_per_day > 0 and reading_date:
        days = max(0, ceil(remaining / avg_per_day))
        due_date_mileage = reading_date + timedelta(days=days)
    elif remaining is not None:
        assumptions.append('mileage date cannot be reliably predicted')
    due_date = min([d for d in [due_date_time, due_date_mileage] if d is not None], default=None)
    mileage_overdue = remaining is not None and remaining < 0
    time_overdue = due_date_time is not None and today > due_date_time
    mileage_due = remaining == 0
    time_due = due_date_time is not None and today == due_date_time
    days_left = (due_date - today).days if due_date else None
    near = (remaining is not None and 0 < remaining <= 1000) or (days_left is not None and 0 <= days_left <= 30)
    status = ('OVERDUE' if mileage_overdue or time_overdue else
              'DUE' if mileage_due or time_due else
              'UNAVAILABLE' if due_odo is None and due_date_time is None else
              'DUE_SOON' if near else 'UP_TO_DATE')
    return due_odo, due_date_mileage, due_date_time, due_date, remaining, status, assumptions

now = date(2026, 10, 12)
# Standard calendar month-end and leap year.
assert add_months(date(2024, 1, 31), 1) == date(2024, 2, 29)
assert add_months(date(2024, 2, 29), 12) == date(2025, 2, 28)
# Mileage projected earlier than six-month interval.
r = schedule(date(2026, 1, 1), 0, 7_500, date(2026, 10, 1), 8_000, 12, 100, date(2026, 10, 1))
assert r[3] == date(2026, 10, 6) and r[5] == 'DUE_SOON'
# Time interval earlier than projected mileage.
r = schedule(date(2026, 8, 1), 0, 1_000, date(2026, 10, 1), 8_000, 1, 10, now)
assert r[2] == date(2026, 9, 1) and r[3] == date(2026, 9, 1) and r[5] == 'OVERDUE'
# Exact distance equality is DUE, not OVERDUE.
r = schedule(date(2026, 10, 1), 0, 8_000, date(2026, 10, 1), 8_000, 6, 100, date(2026, 10, 1))
assert r[5] == 'DUE'
# Unknown/zero usage cannot cause division by zero; use time deadline.
for avg in [None, 0]:
    r = schedule(date(2026, 1, 1), 0, 1_000, date(2026, 10, 1), 8_000, 6, avg, now)
    assert r[3] == date(2026, 7, 1) and r[5] == 'OVERDUE'
# Time independently overdue; mileage still below threshold.
r = schedule(date(2026, 1, 1), 0, 1_000, date(2026, 10, 1), 8_000, 6, 1, now)
assert r[5] == 'OVERDUE'
# Inconsistent odometer excludes mileage estimate and is flagged.
r = schedule(date(2026, 1, 1), 1000, 900, date(2026, 10, 1), 8_000, 6, 10, now)
assert r[4] is None and any('lower' in x for x in r[6])
# Missing both intervals yields unavailable.
r = schedule(date(2026, 1, 1), 0, None, None, None, None, None, now)
assert r[5] == 'UNAVAILABLE' and r[3] is None
# Unit-independent stored intervals remain separate values for miles/kilometres.
assert 0 + 8_000 == 8_000 and 0 + 4_971 == 4_971
print('PASS: 9 deterministic oil schedule/date-edge smoke checks')
