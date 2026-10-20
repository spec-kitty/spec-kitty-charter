# Vendored pack schemas

The 14 JSON Schemas in `schemas/` are core's pack and artifact models, exported as Draft 2020-12
JSON Schema and changed on import. They are produced by one script,
[`scripts/packs/sync_schemas.py`](../../scripts/packs/sync_schemas.py), and are exactly
`transform(export(core))` (ADR-0015, [ADR-0017](../../docs/adr/0017-pack-schemas-imported-as-canonical-json-schema.md)).

## Origin

- Repository: [spec-kitty/spec-kitty](https://github.com/spec-kitty/spec-kitty) (core)
- core commit: `9554e7b26afe7a61519f7744d0147346ab36f197`
- pydantic: `2.14.1`
- Export call: `Model.model_json_schema(mode="validation")` for each of the 14 models
- Command: `~/.venv-sk/bin/python -I scripts/packs/sync_schemas.py`

This repository's copy is canonical (ADR-0015). A change is made here, kept close to the origin
unless there is good reason, and consumers, core included, adapt. The data authority for node
kinds, fragment plurals, file classification and reviewed exclusions is
[`file-kinds.yaml`](file-kinds.yaml); the script reads its kind lists from it.

## The rule (ADR-0015 D4)

A refresh from core never overwrites a change listed below. `sync_schemas.py` refuses to run
unless the installed core is the commit and pydantic version named above, and every change is a
function in the script named after its id. `sync_schemas.py --check` regenerates into a temporary
directory and fails on any difference from `schemas/`, so the committed files are proven
reproducible. It also fails unless the C2 ledger below is exactly the one the script prints
with `--ledger`.

## Changed from the origin

Each change cites its ruling (spec rulings R1-R16, plan calls P1-P12). Where the premise of a
change was checked against the raw export and did not hold in whole or in part, that is stated.

### C1: identity (R1, P5)

All 14 files get `"$schema": "https://json-schema.org/draft/2020-12/schema"` and
`"$id": "https://schemas.spec-kitty.ai/charter/packs/<kind>.schema.json"`. Every `$ref` is
local (`#/$defs/...`) and every IRI ends in `.json`; the script fails otherwise. The `$id` is an
identifier and is never fetched.

### C2: nested sections closed (spec open item 1)

Every object schema with `properties` gets `additionalProperties: false`. Free-form maps that
core types as free-form (`additionalProperties` a schema or `true`, no `properties`) stay as
exported. Premise: the export already closes every nested section of 12 of the 14 models, because
core declares `extra="forbid"` on them. The list below is every schema the transform closed, per
file. Only `agent-profile` and `charter` have any.

- `pack-descriptor`: none
- `graph-shard`: none
- `org-fragment`: none
- `org-charter`: none
- `directive`: none
- `tactic`: none
- `styleguide`: none
- `toolguide`: none
- `paradigm`: none
- `procedure`: none
- `agent-profile`: `#/$defs/ArtifactRef`, `#/$defs/CollaborationContract`, `#/$defs/DirectiveRef`, `#/$defs/ModeDefault`, `#/$defs/SelfReviewProtocol`, `#/$defs/SelfReviewStep`, `#/$defs/Specialization`, `#/$defs/SpecializationContext`
- `mission-step-contract`: none
- `asset`: none
- `charter`: `#/$defs/BranchStrategyConfig`, `#/$defs/CharterCatalog`, `#/$defs/CharterCatalogReference`, `#/$defs/CharterTestingConfig`, `#/$defs/CommitConfig`, `#/$defs/Directive`, `#/$defs/DirectivesConfig`, `#/$defs/GovernanceCharterConfig`, `#/$defs/GovernanceConfig`, `#/$defs/PerformanceConfig`, `#/$defs/QualityConfig`, `#/$defs/RetrospectiveGovernance`, `#/$defs/RetrospectiveGovernancePermissions`

### C3: URN grammar (R6, R15)

`graph-shard` and `org-fragment`: the pattern
`^(<node_kinds joined by |>):[A-Za-z0-9_/.-]+$` is set on every edge `source` and `target`, and
on the shard node `urn`. It is generated from `node_kinds` in `file-kinds.yaml`. Core's extra
`glossary` and `glossary_scope` kinds are refused (R15). Fragment endpoints must be URNs; a bare
local id is refused (R6).

### C4: per-kind URN prefix (research R4)

`graph-shard`: node `kind` is an enum of `node_kinds` (the export had core's 17). `DRGNode` gets an
`allOf` of one `if kind == K then urn matches ^K:` branch per kind, 15 in all, generated from
`file-kinds.yaml`. The `NodeKind` `$def` is no longer referenced and is removed.

### C5: fragment node vocabulary (R14, P9)

`org-fragment`: node `kind` is an enum of the `fragment_kinds` keys (15 plurals, one name per
concept, no aliases). Node `id` gets `minLength: 1` and the pattern `^[A-Za-z0-9_/.-]+$`.
Core's `mission_steps` and `mission_type` are refused; `actions` and `anti_patterns` are
accepted, which core refuses.

### C6: fragment header fields removed (R7)

`org-fragment`: `pack_name`, `source_kind`, `source_ref`, `layer_index` and `provenance_marker` are
removed from `properties`, and with them `required` (which held only those four). The top level
stays closed, so a fragment that carries one is refused as an unknown key.

### C7: relation vocabulary (P2, ADR-0009 D1)

`graph-shard`: the `Relation` enum is core's (`offering/drg/models.py:83-148`, including the legacy
`in_tension_with`) plus `impacts`, appended last, 16 values. Core has no `impacts` member yet:
ADR-0009 D1 and ADR-0014 D-2 rule that the contract leads and core aligns at the cutover. The
`$def` description gains one line saying so. `org-fragment`: the edge `relation`, a free-form
string in core, becomes a `$ref` to a copy of the same `Relation` `$def`. There is no `extends`
alias. The fragment edge description's stale phrase "free-form string label" is replaced by "a
`Relation` value". The service's `Relation.java` has 15 values: the same set without
`in_tension_with`. See "Known differences". Divergence register entry: DIV-018.

### C8: shard `generated_by` optional (R8)

`graph-shard`: `generated_by` is removed from `required`. `generated_at` stays required.

### C9: pack descriptor (R3, R4, R5, R13)

`pack-descriptor`: `pack_version` is added to `required` (every level) and `pack_level` is
removed from it (still an enum when present). The top-level `additionalProperties` goes from
`false` to `true`: unknown and retired keys are reported by the adapter, not refused. The
`description` of `pack_version` and `pack_level` are rewritten to match; the model docstring is
kept as exported. Premise held in part: `requires_core` is already an optional string in the
export (`anyOf` string or null, default null), so nothing changes there. `pack_version` is
changed (R4, orchestrator ruling): it becomes a non-null string with `minLength: 1`. The
export's nullable `anyOf` and its `null` default are removed, so `pack_version: null` and
`pack_version: ""` are both refused. This is an exception to C13. The script asserts the
exported shape first and fails if core's export changes.

### C10: coercions dropped (R9)

- `agent-profile`: `roles` items get the pattern `^[a-z][a-z0-9_-]*$` (changed).
- Integer fields stay `type: integer`, so a quoted number is refused (held in the export).
- A scalar `role` is refused by the closed top-level object (held in the export).
- `org-charter` `schema_version` stays `integer`; `"1"` is refused (held in the export).

The script asserts the three held premises, so a core change that loosens them fails the build.

### C11: lost validators (FR-005f)

- `directive`: lenient adherence is an `if/then` on `enforcement == lenient-adherence`, requiring
  `explicit_allowances`. Core also rejects an empty list (`directives/models.py:135`), so the
  `then` adds `minItems: 1`.
- Premise did not hold for `agent-profile`: the export already has `routing-priority` `minimum 0`
  and `maximum 100`, and `max-concurrent-tasks` `exclusiveMinimum 0`, which for an integer is
  `minimum 1`. Nothing is changed; the script asserts both.

### C12: edge strength (ADR-0009 D1)

`graph-shard` and `org-fragment`: edges get an optional `impacts` of `type: number`. There is no
range in the schema; the strength rule, NaN included, stays in the domain. Divergence register
entry: DIV-018.

### C13: nullable fields (plan)

`anyOf [T, null]` is kept as exported. The domain collapses it (WP05). The one exception is
`pack_version` (C9), which is a non-null string with `minLength: 1`.

### C14: portable patterns (research R2)

No pattern is rewritten: the export's patterns already use anchors, character classes and
alternation only. The script fails if one does not. The optional regex engines are absent from
the validator, so JDK regex applies.

### C15: charter

`charter` takes C1 and C2 only. Whether this repository's own `.kittify/charter/charter.yaml`
validates is measured in WP08 (P7).

### C16: edge provenance not authored (P10)

`graph-shard`: `provenance` is removed from the edge. It is a merge-time marker that core stamps
(`offering/drg/models.py:409-412`), not authored data. Premise held in part: the fragment edge in the
export already has no `provenance`. No open-packs DRG edge carries one. Divergence register entry:
DIV-019.

## Known differences

- C7: `in_tension_with` is an intentional, accepted difference. The schema's `Relation` enum keeps
  core's legacy `in_tension_with` on purpose (16 values), so existing graph documents still
  validate. The service's `Relation.java` has 15 values. The service reads `in_tension_with` as
  `impacts` with strength -1 (see `GraphDocumentReaderTest` and the generated OpenAPI relation
  description). The WP05 equality test must allow exactly this one difference: schema =
  `Relation.java` + {`in_tension_with`}.

## Cross-links

- Divergence register: [`docs/conformance/divergence-register.md`](../../docs/conformance/divergence-register.md)
  (created by WP09).
- [ADR-0015](../../docs/adr/0015-charter-repository-is-canonical-for-charter-structure.md),
  [ADR-0017](../../docs/adr/0017-pack-schemas-imported-as-canonical-json-schema.md),
  [ADR-0009](../../docs/adr/0009-contract-1-0-reservations-and-additive-contract-sets.md) (D1).
