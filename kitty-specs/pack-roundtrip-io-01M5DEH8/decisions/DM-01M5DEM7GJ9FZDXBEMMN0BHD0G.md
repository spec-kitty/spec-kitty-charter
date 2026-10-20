# Decision Moment `01M5DEM7GJ9FZDXBEMMN0BHD0G`

- **Mission:** `pack-roundtrip-io-01M5DEH8`
- **Origin flow:** `specify`
- **Slot key:** `specify.roundtrip.content-scope`
- **Input key:** `content_scope`
- **Status:** `resolved`
- **Created:** `2026-10-20T20:04:14.994747+00:00`
- **Resolved:** `2026-10-20T20:08:28.920951+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

Which pack content does the round-trip cover?

## Options

- Descriptor + graph documents (the existing reader/writer pair)
- Whole pack: descriptor plus every artifact kind the schemas define
- Descriptor only

## Final answer

Descriptor + one low-complexity leaf artifact kind (tactic). Round-trip proven via a repository port in the domain with a filesystem adapter in infra, exercised for both fetch and write of existing elements. Graph shards, relationships, and the domain graph shape are DEFERRED (future creed/values epic; careful cross-onion-layer modeling). Excluded as needing modeling work: glossary, mission-step-contract, agent-profile, asset. Tactic 'references' carried as an opaque ordered id list, not modeled as domain relationships.

## Rationale

_(none)_

## Change log

- `2026-10-20T20:04:14.994747+00:00` — opened
- `2026-10-20T20:08:28.920951+00:00` — resolved (final_answer="Descriptor + one low-complexity leaf artifact kind (tactic). Round-trip proven via a repository port in the domain with a filesystem adapter in infra, exercised for both fetch and write of existing elements. Graph shards, relationships, and the domain graph shape are DEFERRED (future creed/values epic; careful cross-onion-layer modeling). Excluded as needing modeling work: glossary, mission-step-contract, agent-profile, asset. Tactic 'references' carried as an opaque ordered id list, not modeled as domain relationships.")
