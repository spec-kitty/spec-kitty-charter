# Decision Moment `01M5Z18GX78VXESS14SH0Y3QZB`

- **Mission:** `asset-template-round-trip-01M5YY7G`
- **Origin flow:** `plan`
- **Slot key:** `plan.vocabulary.payload-classification`
- **Input key:** `payload_classification`
- **Status:** `open`
- **Created:** `2026-10-27T15:56:59.687544+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How does file-kinds.yaml classify an asset payload, given sidecars and the config file also live under assets/ and rules must not overlap?

## Options

- Fallback root rule: */assets/** is asset-payload (bytes) only for files no files/exclusions rule matches; retire the **/*.config.yaml exclusion
- By reference: payload kind comes from sidecar path only; config file needs an upstream sidecar
- Other

## Final answer

_(none)_

## Rationale

_(none)_

## Change log

- `2026-10-27T15:56:59.687544+00:00` — opened
