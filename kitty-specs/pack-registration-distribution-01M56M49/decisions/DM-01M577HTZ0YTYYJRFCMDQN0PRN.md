# Decision Moment `01M577HTZ0YTYYJRFCMDQN0PRN`

- **Mission:** `pack-registration-distribution-01M56M49`
- **Origin flow:** `plan`
- **Slot key:** `plan.sourcing.trust-consent`
- **Input key:** `trust_consent`
- **Status:** `resolved`
- **Created:** `2026-10-18T10:05:09.984103+00:00`
- **Resolved:** `2026-10-18T10:11:13.571389+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Trust & consent model for consumer/private git sources (floor: pin-to-rev + content-hash verify + offline cache + fail-closed)?

## Options

- Explicit allowlist in config (v1); built-in/open = high-trust; reserve signing as a higher tier
- Trust-on-first-use interactive consent
- Signed-sources-only / provenance chain now

## Final answer

Explicit allowlist in config for v1 on the shared floor (pin-to-rev + content-hash verify + offline cache + fail-closed); built-in/open high-trust; signing reserved as a higher tier.

## Rationale

_(none)_

## Change log

- `2026-10-18T10:05:09.984103+00:00` — opened
- `2026-10-18T10:11:13.571389+00:00` — resolved (final_answer="Explicit allowlist in config for v1 on the shared floor (pin-to-rev + content-hash verify + offline cache + fail-closed); built-in/open high-trust; signing reserved as a higher tier.")
