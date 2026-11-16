# Data model: ADR-0075 slice 5

All types are in `ai.speckitty.charter.packs.domain` unless noted. Kernel types are in
`ai.speckitty.charter.kernel.validation`.

## PackCodes (new, replaces PackFindingCode and the string kinds)

Kernel `ReasonCode` constants. Each has a wire token and a severity (ruling 4). Today's tokens are unchanged.

| Group | Codes (wire token) | Severity |
| --- | --- | --- |
| Level and descriptor | `pack_level_missing` | WARNING |
| | `pack_level_mismatch`, `requires_core_invalid`, `pack_version_invalid`, `dependency_range_invalid`, `org_fragment_missing` | VIOLATION |
| Read | `pack_unreadable`, `unreadable`, `schema`, `retired`, `domain`, `reference`, `companion` | VIOLATION |
| Graph | `drg_schema_error`, `relationship_endpoint_not_allowed`, `overrides_source_not_activatable` | VIOLATION |
| Artifacts | `artifact_schema_error`, `duplicate_step_id`, `duplicate_artifact_id`, `asset_path_escape`, `asset_mime_invalid`, `asset` | VIOLATION |
| Mission type (F) | `mission_step_unlinked`, `substep_delegation_unresolved`, `substep_delegation_unscoped` | VIOLATION |
| Fragments and slots | `prompt_fragment_invalid`, `prompt_slot_malformed`, `prompt_slot_not_empty` | VIOLATION |
| | `prompt_slot_not_empty` (unversioned descriptor) | WARNING |
| Load-only | `reserved_shard`, `manifest_membership` | VIOLATION |
| Not held | `file_not_held` (excluded **or unmodelled**, Q1; an unmodelled file's reason is `no pack file kind models this file`) | WARNING |
| Closure (CLOSURE) | the 8 `RefusalCode`s of the resolver's cycle, dependency, parent, level and conflict kinds | VIOLATION |

Gone: `unmodelled` (Q1) and the `invalid` fallback.

## PackRulesets and the surface profile (packs.domain.rules)

```java
public enum Surface { VALIDATE, REGISTER, COMPOSE_READ }
public static ModularRuleset<PackContents> pack(Surface surface);   // PACK
public static ModularRuleset<List<PackUnderResolution>> closure();  // CLOSURE
```

There is no judging port (R3). Each surface in `:packs` calls `PackRulesets` directly.

**R1.** `REGISTER` and `COMPOSE_READ` compose exactly the rules behind the faults those surfaces report today.
`VALIDATE` composes every rule.

| Rule group | VALIDATE | REGISTER | COMPOSE_READ |
| --- | --- | --- | --- |
| Read faults (schema, parse, retired, domain, reference, companion) | yes | yes | yes |
| Graph, endpoint, mission type, step enhancement, prompt slot, glossary | yes | yes | yes |
| Level, org fragment, descriptor, asset rules | yes | no | no |
| Manifest membership, reserved shard, not held | yes | yes | no |

WP01's golden snapshot checks this table, and both subset tests pin it.

## Composition facts (F5)

A Spring-free builder in `packs.infra.storage` builds `PackUnderResolution` from `PackContents`. That covers the
descriptor, the level, the defined and changed artifacts, and the held terms. A parity test checks it against
`DomainRepositoryService`'s facts over the example packs. `CLOSURE` reads these, never the closure walk (#556).

## R2 code table (filled in WP01)

One row per fault: today's validate code and wording, today's registration code, pointer and wording, and the one code
and wording kept. The rule is that the specific code wins over a catch-all. The catch-alls are `domain`, `reference`,
`invalid`, the aggregate `validation_failed`, the refusal at pointer `pack`, and `schema` reported once per violation.
Where registration already answers a specific code, validate mode takes it. Each changed row names its `### Breaking`
fragment.

## PackContents (extended, P1)

It gains these facts, so that registration can judge the inspector's read:

- value-object breaches: file, kind and the breach's `Reason`;
- reference faults;
- the manifest: present, regular and listed;
- the reserved-shard stamp;
- the files that are not held: unmodelled or excluded, each with its reason.

## Mappers (new)

| Mapper | Input | Output | Order |
| --- | --- | --- | --- |
| `PackVerdicts.of` | `Rationale` | `PackVerdict` | `PRECEDENCE` (group, path, place) |
| `PackRefusals.of` | `Rationale` | `List<RefusalViolation>`, plus the diagnostics: the load's warnings in today's order, then one path-sorted list of files not held | graph faults by path, then the read and rule faults, the orphans by path, the reserved shard, then manifest membership |

Under R2 the mappers differ only in shape and order. Both read `failures()` only, and both switch on the code
constant, never on `value()`.

## Invariants

- No rule reads a file. Rules read only `PackContents` and `PackUnderResolution` (`PACK_RULES_READ_NO_FILE`).
- Manifest membership is judged only when no other `VIOLATION` was found.
- A fault the store's reader meets that `PACK` did not report refuses the registration (fails closed).
