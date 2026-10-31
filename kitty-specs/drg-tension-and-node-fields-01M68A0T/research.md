# Research: DRG tension edges and node fields round-trip

The pre-spec squad's findings are in [research/pre-spec-squad.md](research/pre-spec-squad.md). This file
records the plan-phase decisions and the dispositions of the plan-point adversarial squad's findings.

## Decisions

### D1 — Tension is a kernel value beside the relationships

- **Decision**: a kernel `Tension` record (`source`, `target`, `when`, `rationale`; no strength), held in
  `DoctrineGraph.tensions()` beside `relationships()`. It is not a `RelationType`. Recorded as
  DM-01M68A2TYC1VMDPCJ8B76602YH.
- **Rationale**:
  - ADR-0028 D3 counts tension apart from a pair's one structural relationship. `Relationship`'s
    identity is the unordered pair, so a tension cannot be a `Relationship` without breaking that
    identity or the coexistence rule. The built-in pack has exactly that case: `DIRECTIVE_024` /
    `DIRECTIVE_025` hold both `in_tension_with` and `requires`.
  - It changes no `RelationType`, no `Relation` value and no `Relation.yaml` enum, and `/relationships`
    neither accepts nor answers a new token (C-001).
- **Alternatives considered**:
  - A `RelationType.TENSION` constant: an HTTP contract change (the operator's decision), and it would
    still need a pair-identity exception.
  - Keep IMPACTS −1 and teach the writer to emit `in_tension_with` for strength −1: the retired
    reading stays, and an authored `impacts: -1` edge would be rewritten as a tension, a new loss.
  - Keep the raw relation token on `Relationship`: it leaks storage spelling into the kernel.

### D2 — `in_tension_with` is a storage spelling

- **Decision**: the word stays in `PackPolicy` and the writer as the written spelling of tension
  (ADR-0036 D4). The kernel has no token for it.
- **Rationale**: ADR-0028 names the relationship `tension`, while today's files spell it
  `in_tension_with`. When the files move to a new spelling, only storage changes.
- **Alternative considered**: a `Tension.TOKEN` constant in the kernel. Rejected: the kernel would carry
  a pack spelling that ADR-0028 already renames.

### D3 — Node fields on `DrgNode`

- **Decision**: `DrgNode(urn, label, provenance, tags, bodyPath)`, with `tags` an immutable ordered
  `List<String>`. The two-argument constructor is kept and means no extra fields.
- **Rationale**:
  - ADR-0036 L246-249 makes every declared field part of a node's value. E15 makes `tags` a sequence.
  - `body_path` belongs to a fragment node, and a shard node never has one. The writer of each shape
    writes the fields its schema allows: the shard writer never writes `body_path`, and the fragment
    writer never writes `provenance` or `tags`. The schemas (`additionalProperties: false`) keep a
    written document valid.
- **Alternative considered**: a shard-node type and a fragment-node type in the kernel. Rejected: the
  DRG has one node concept, and the document shape is a storage concern.

### D4 — A fragment write from the domain

- **Decision**: `GraphShardWriter.writeFragment(DoctrineGraph)`, written in the fragment's key order.
- **Rationale**: FR-007 needs `body_path` to survive a write, and the gate round-trips no fragment.
  Without a domain fragment write, "survives write" cannot be proven on the production path.
- **Alternative considered**: assert only that `body_path` reaches `DrgNode`. Rejected: that proves
  half the round trip.

### D5 — Records

- **Decision**:
  - A new ADR (number picked at writing time) records D1–D4 as the representation ADR-0028 D5 deferred.
  - DIV-018 and the `Relation`/`RelationType` javadoc drop the −1 claim.
  - The `Relation.yaml` description text and a CHANGELOG line under Unreleased say the service holds
    `in_tension_with` as tension and does not answer it as a relationship.
- **Rationale**: FR-010; the CHANGELOG previously promised the −1 reading. No enum value or path
  changes.

## Plan-point adversarial squad

Findings and dispositions are recorded below.
