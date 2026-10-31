# Decision Moment `01M68A2TYC1VMDPCJ8B76602YH`

- **Mission:** `drg-tension-and-node-fields-01M68A0T`
- **Origin flow:** `specify`
- **Slot key:** `specify.domain.tension-representation`
- **Input key:** `tension_representation`
- **Status:** `resolved`
- **Created:** `2026-10-31T06:24:20.428328+00:00`
- **Resolved:** `2026-10-31T06:24:33.002779+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How is an ADR-0028 tension relationship held in the domain so that a shard's in_tension_with edge reads and writes back as written?

## Options

- A kernel Tension value held beside the structural relationships in DoctrineGraph (no RelationType, no HTTP contract change)
- A new RelationType constant (grows Relation and the Relation.yaml enum; /relationships accepts and answers it: HTTP contract change, operator decision)
- Other

## Final answer

A kernel Tension value held beside the structural relationships in DoctrineGraph (no RelationType, no HTTP contract change). Rationale: ADR-0028 D3 counts tension apart from the one-structural-relationship-per-pair identity, so it is not a Relationship of another type; it keeps RelationType, Relation and the Relation.yaml enum unchanged and /relationships neither accepts nor answers a new token (the brief reserves an HTTP contract change for the operator). The pack spelling in_tension_with stays a storage concern (ADR-0036 D4). Recorded by the mission agent under the brief's 'stop only at genuine decisions' rule; logged as an open review item for operator confirmation in the PR.

## Rationale

_(none)_

## Change log

- `2026-10-31T06:24:20.428328+00:00` — opened
- `2026-10-31T06:24:33.002779+00:00` — resolved (final_answer="A kernel Tension value held beside the structural relationships in DoctrineGraph (no RelationType, no HTTP contract change). Rationale: ADR-0028 D3 counts tension apart from the one-structural-relationship-per-pair identity, so it is not a Relationship of another type; it keeps RelationType, Relation and the Relation.yaml enum unchanged and /relationships neither accepts nor answers a new token (the brief reserves an HTTP contract change for the operator). The pack spelling in_tension_with stays a storage concern (ADR-0036 D4). Recorded by the mission agent under the brief's 'stop only at genuine decisions' rule; logged as an open review item for operator confirmation in the PR.")
