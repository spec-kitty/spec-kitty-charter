# Decision Moment `01M6ZS12SJAZYET6A3E06R0T74`

- **Mission:** `adr-0066-step-enhancement-service-alignment-01M6ZRYK`
- **Origin flow:** `specify`
- **Slot key:** `specify.compose.step-integrity-inheritance`
- **Input key:** `step_integrity_inheritance`
- **Status:** `resolved`
- **Created:** `2026-11-09T09:08:03.506716+00:00`
- **Resolved:** `2026-11-09T11:04:33.021051+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Does a step (action:<type>/<step>) inherit its mission type's mutability level, so a locked mission type refuses a step enhancement (ADR-0066 d7)? Policies cannot name an action today.

## Options

- inherit the mission type level
- steps are always enhanceable (d7 locked-target rule vacuous for steps until policies can name actions)
- Other

## Final answer

(A) a step inherits its mission type's mutability level; an enhancement onto a locked step is refused per the C21 precedent: 500 pack_unreadable on reads, 422 override_not_permitted (description broadened, no new enum value) at registration (operator, interview 2026-11-09)

## Rationale

_(none)_

## Change log

- `2026-11-09T09:08:03.506716+00:00` — opened
- `2026-11-09T11:04:33.021051+00:00` — resolved (final_answer="(A) a step inherits its mission type's mutability level; an enhancement onto a locked step is refused per the C21 precedent: 500 pack_unreadable on reads, 422 override_not_permitted (description broadened, no new enum value) at registration (operator, interview 2026-11-09)")
