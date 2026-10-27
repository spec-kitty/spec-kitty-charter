# Research: Glossary pack and skill round trip

The evidence is in [research/grounding-squad.md](research/grounding-squad.md) (pre-spec squad,
findings G1 to G16 with dispositions). This file records the planning decisions it led to.

## R1 — Where the skill body lives

- **Decision**: the body is a value on the `Skill` entity. A binding-level companion hook reads it,
  writes it and digests it beside the descriptor (ADR-0040).
- **Rationale**: the issue keeps the pair on write, and the operator confirmed the body is in scope
  (decision `01M5YVEVMPMHPE0GDFQD0BZA6F`). The prompt is part of what a skill means. The fingerprint
  must cover every loaded file (`PackFileSet` contract).
- **Alternatives considered**:
  - The body stays unmodelled with a follow-up issue: rejected by the operator.
  - The body is a separate artifact kind: no such kind exists, and the CRG has no node for it.
  - The YAML strategy reads the body: the format would own paths, which the store owns.

## R2 — Schema sources

- **Decision**:
  - `skill.schema.json` is imported from core `581b5c46da`'s `skill.schema.yaml`, unchanged in
    meaning.
  - `glossary-pack.schema.json` is authored from core's `GlossaryPack`/`GlossaryTerm`. Both are
    closed.
- **Rationale**:
  - ADR-0015 makes this repository canonical for pack schemas.
  - Core's glossary model is `extra="forbid"`, so a closed schema matches it.
  - Core allows `None` on optional fields, so the schema allows `null` there too. ADR-0036 E2
    treats an explicit null as equal to absent.
- **Alternatives considered**:
  - Leave both kinds excluded: refusal of unknown fields would never start (ADR-0036 decision 6).
  - Wait for core to export a glossary schema: core consumes ours.

## R3 — Equality rows for ADR-0036's table

- **Decision**:
  - `glossary-pack`: YAML, sets none; `terms` is a sequence; unique `surface` is an invariant.
  - `skill`: YAML, sets none; the body compares as text, byte for byte.
- **Rationale**:
  - Only edges and nodes are sets (ADR-0036 decision 4). Core never relies on term order, but a
    reorder is still a change.
  - The bodies carry no frontmatter.
- **Alternatives considered**: `terms` as a set keyed by surface. That would hide a reorder, and no
  ruling asks for it.

## R4 — Normalizations not reproduced

- **Decision**: none of core's normalizations is applied: the `model_invocable` forcing, and the
  defaults for `tools`, `version`, `required` and `user_invocable`.
- **Rationale**: equality is with the file, not with core's in-memory view. All five pinned skills
  declare `side_effects` without `model_invocable`.

## R5 — id versus file stem

- **Decision**: keep the authored path through `PackLayout`, with no rule (open-packs#47 upstream).
- **Rationale**: ADR-0036 ruling 3. Core keys by `id` and has no stem rule, so nothing breaks.

## R6 — Base branch

- **Decision**: stack on #191's branch, and rebase onto `main` when it merges.
- **Rationale**: it carries the `schemaName()` seam and the registry-driven gate unchanged. #190
  carries the identical seam, so either merge order rebases cleanly.

## Supply chain

No dependency is added, upgraded or removed (DIRECTIVE_051: nothing to check).
