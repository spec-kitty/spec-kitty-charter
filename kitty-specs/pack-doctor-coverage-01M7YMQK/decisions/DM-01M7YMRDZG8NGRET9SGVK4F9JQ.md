# Decision Moment `01M7YMRDZG8NGRET9SGVK4F9JQ`

- **Mission:** `pack-doctor-coverage-01M7YMQK`
- **Origin flow:** `specify`
- **Slot key:** `specify.manifest.stale`
- **Input key:** `manifest_stale`
- **Status:** `resolved`
- **Created:** `2026-11-21T08:49:53.136394+00:00`
- **Resolved:** `2026-11-21T08:49:55.154534+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

#630: what is the one answer for a stale manifest hash?

## Options

_(none)_

## Final answer

R-b Refuse everywhere: manifest_stale at pack-manifest.yaml in the manifest gate beside manifest_membership; validate, pack-doctor and whole-pack registration refuse; round trip drops its duplicate; amends ADR-0042

## Rationale

_(none)_

## Change log

- `2026-11-21T08:49:53.136394+00:00` - opened
- `2026-11-21T08:49:55.154534+00:00` - resolved (final_answer="R-b Refuse everywhere: manifest_stale at pack-manifest.yaml in the manifest gate beside manifest_membership; validate, pack-doctor and whole-pack registration refuse; round trip drops its duplicate; amends ADR-0042")
