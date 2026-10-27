# Decision Moment `01M5Z18GX78VXESS14SH0Y3QZB`

- **Mission:** `asset-template-round-trip-01M5YY7G`
- **Origin flow:** `plan`
- **Slot key:** `plan.vocabulary.payload-classification`
- **Input key:** `payload_classification`
- **Status:** `resolved`
- **Created:** `2026-10-27T15:56:59.687544+00:00`
- **Resolved:** `2026-10-27T16:14:45.141061+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How does file-kinds.yaml classify an asset payload, given sidecars and the config file also live under assets/ and rules must not overlap?

## Options

- Fallback root rule: */assets/** is asset-payload (bytes) only for files no files/exclusions rule matches; retire the **/*.config.yaml exclusion
- By reference: payload kind comes from sidecar path only; config file needs an upstream sidecar
- Other

## Final answer

A: fallback root rule */assets/** = asset-payload (bytes) for files no files/exclusions rule matches; retire **/*.config.yaml exclusion; templates get */templates/** rule (no schema, bytes). Gate keeps DomainRoundTrip; storage save writes sidecar+payload and templates in place.

## Rationale

_(none)_

## Change log

- `2026-10-27T15:56:59.687544+00:00` — opened
- `2026-10-27T16:14:45.141061+00:00` — resolved (final_answer="A: fallback root rule */assets/** = asset-payload (bytes) for files no files/exclusions rule matches; retire **/*.config.yaml exclusion; templates get */templates/** rule (no schema, bytes). Gate keeps DomainRoundTrip; storage save writes sidecar+payload and templates in place.")
