# Decision Moment `01M5EEV6ZVJPE8GD80S4DN14TX`

- **Mission:** `pack-repositories-validation-01M5EET9`
- **Origin flow:** `specify`
- **Slot key:** `specify.modeling.record-vs-builder-split`
- **Input key:** `record_builder_split`
- **Status:** `resolved`
- **Created:** `2026-10-21T05:27:18.267446+00:00`
- **Resolved:** `2026-10-21T05:29:17.791373+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How should the pack types be modelled, reconciling 'fluent builders + package-private full ctor' with 'use records for DTO/DAO'?

## Options

- Split: records for adapter DTO/DAO, fluent-builder final classes for domain entities
- Builders everywhere (final classes + pkg-private ctor), no separate record DTO layer
- Records everywhere (public canonical ctor + static factories), drop pkg-private-ctor goal

## Final answer

Option 1 (split), refined to a type-nature hard-and-fast rule: RECORDS for value objects / data structures / parameter & DTO classes (TacticReference, TacticStep, PackPaths, YAML-row DTOs), with a fluent builder added only where a record is still wide; FINAL CLASSES with a public fluent builder + package-private all-args constructor for ENTITIES (identity + invariants + lifecycle: Tactic, the pack aggregate). Readers parse YAML->DTO records, then assemble domain entities via builders. Aligns with the DDD entity/valueObject glossary entries this mission adds.

## Rationale

_(none)_

## Change log

- `2026-10-21T05:27:18.267446+00:00` — opened
- `2026-10-21T05:29:17.791373+00:00` — resolved (final_answer="Option 1 (split), refined to a type-nature hard-and-fast rule: RECORDS for value objects / data structures / parameter & DTO classes (TacticReference, TacticStep, PackPaths, YAML-row DTOs), with a fluent builder added only where a record is still wide; FINAL CLASSES with a public fluent builder + package-private all-args constructor for ENTITIES (identity + invariants + lifecycle: Tactic, the pack aggregate). Readers parse YAML->DTO records, then assemble domain entities via builders. Aligns with the DDD entity/valueObject glossary entries this mission adds.")
