# Decision Moment `01M60PKRCCMHZ2XJ2AMX4FRBBE`

- **Mission:** `procedure-antipattern-toolguide-round-trip-01M60H9B`
- **Origin flow:** `plan`
- **Slot key:** `plan.gate.pin-strategy`
- **Input key:** `pin_strategy`
- **Status:** `resolved`
- **Created:** `2026-10-28T07:29:22.316715+00:00`
- **Resolved:** `2026-10-28T07:29:24.600863+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

How does PR-A get green when the sanitized shape refuses the pinned toolguides?

## Options

_(none)_

## Final answer

Model first; rewrite the existing packs into the sanitized shape with our own writer code via a throwaway test-file harness; land the result in open-packs and re-pin openPacksRef.

## Rationale

_(none)_

## Change log

- `2026-10-28T07:29:22.316715+00:00` — opened
- `2026-10-28T07:29:24.600863+00:00` — resolved (final_answer="Model first; rewrite the existing packs into the sanitized shape with our own writer code via a throwaway test-file harness; land the result in open-packs and re-pin openPacksRef.")
