# Decision Moment `01M630W3RPN6HKJ5GEM25J7V0N`

- **Mission:** `procedure-antipattern-toolguide-round-trip-01M60H9B`
- **Origin flow:** `plan`
- **Slot key:** `plan.procedure.actor-role-mapping`
- **Input key:** `actor_role_mapping`
- **Status:** `resolved`
- **Created:** `2026-10-29T05:07:10.742671+00:00`
- **Resolved:** `2026-10-29T05:07:13.357502+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

How do pinned procedure actors map to role, and what about an incoming guide H1?

## Options

_(none)_

## Final answer

human -> operator; agent and system steps carry no role. An incoming guide may omit its H1; if present it must equal the toolguide title, else refused; the server always writes the H1.

## Rationale

_(none)_

## Change log

- `2026-10-29T05:07:10.742671+00:00` — opened
- `2026-10-29T05:07:13.357502+00:00` — resolved (final_answer="human -> operator; agent and system steps carry no role. An incoming guide may omit its H1; if present it must equal the toolguide title, else refused; the server always writes the H1.")
