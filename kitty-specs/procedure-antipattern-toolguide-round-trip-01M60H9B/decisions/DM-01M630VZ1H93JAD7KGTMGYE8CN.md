# Decision Moment `01M630VZ1H93JAD7KGTMGYE8CN`

- **Mission:** `procedure-antipattern-toolguide-round-trip-01M60H9B`
- **Origin flow:** `plan`
- **Slot key:** `plan.api.fetch-network-posture`
- **Input key:** `fetch_network_posture`
- **Status:** `resolved`
- **Created:** `2026-10-29T05:07:05.905525+00:00`
- **Resolved:** `2026-10-29T05:07:08.337933+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

Guide-fetch network posture?

## Options

_(none)_

## Final answer

Direct: the fetch never uses a proxy; a JEP 418 resolver provider refuses non-global addresses for allowlisted hosts (closes DNS rebinding). Proxy-only deployments cannot use guideUri.

## Rationale

_(none)_

## Change log

- `2026-10-29T05:07:05.905525+00:00` — opened
- `2026-10-29T05:07:08.337933+00:00` — resolved (final_answer="Direct: the fetch never uses a proxy; a JEP 418 resolver provider refuses non-global addresses for allowlisted hosts (closes DNS rebinding). Proxy-only deployments cannot use guideUri.")
