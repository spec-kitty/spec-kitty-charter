# Decision Moment `01M6ZS10GDRY7NAA4PA5BK5EB9`

- **Mission:** `adr-0066-step-enhancement-service-alignment-01M6ZRYK`
- **Origin flow:** `specify`
- **Slot key:** `specify.compose.inactive-enhancement-slot-refusal`
- **Input key:** `inactive_enhancement_slot_refusal`
- **Status:** `resolved`
- **Created:** `2026-11-09T09:08:01.165507+00:00`
- **Resolved:** `2026-11-09T11:04:30.613714+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

ADR-0066 d8 says compose checks run 'whatever the activation': does an INACTIVE step enhancement whose slot a prompt override dropped refuse every composing read (500), or only an active one (warn otherwise)?

## Options

- confirm d8: refuse whatever the activation
- amend ADR-0066: refuse only when active, warn otherwise
- Other

## Final answer

(B) refuse only when active: amend ADR-0066 d8 with a dated annotation; an inactive enhancement whose slot the prompt in force dropped does not refuse (operator, interview 2026-11-09)

## Rationale

_(none)_

## Change log

- `2026-11-09T09:08:01.165507+00:00` — opened
- `2026-11-09T11:04:30.613714+00:00` — resolved (final_answer="(B) refuse only when active: amend ADR-0066 d8 with a dated annotation; an inactive enhancement whose slot the prompt in force dropped does not refuse (operator, interview 2026-11-09)")
