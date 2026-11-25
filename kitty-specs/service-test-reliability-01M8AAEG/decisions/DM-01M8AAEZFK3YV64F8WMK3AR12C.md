# Decision Moment `01M8AAEZFK3YV64F8WMK3AR12C`

- **Mission:** `service-test-reliability-01M8AAEG`
- **Origin flow:** `specify`
- **Slot key:** `workmeter`
- **Input key:** `workmeter-disposition`
- **Status:** `open`
- **Created:** `2026-11-25T21:40:50.803708+00:00`
- **Opened by:** `claude::java-jenny`
- **Other answer:** `false`

## Question

Q5: The six static counters are replaced by WorkMeter, a ScopedValue-bound meter only tests call. Keep it on purpose (ADR-0075 AA5 amendment) and move it out of packs.domain, give it a production consumer, or something else?

## Options

- keep + ADR-0075 AA5 amendment + move out of packs.domain (recommended)
- give it a production consumer (per-request read-work metrics)
- keep as is in packs.domain

## Final answer

_(none)_

## Rationale

_(none)_

## Change log

- `2026-11-25T21:40:50.803708+00:00` - opened
