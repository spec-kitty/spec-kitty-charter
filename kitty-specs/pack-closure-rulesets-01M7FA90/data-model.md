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
| Not held | `file_not_held` (excluded **or unmodelled**, Q1) | WARNING |
| Closure (CLOSURE) | the 8 `RefusalCode`s of the resolver's cycle, dependency, parent, level and conflict kinds | VIOLATION |

Gone: `unmodelled` (Q1) and the `invalid` fallback.

## PackRules (new port)

```java
public interface PackRules {
    Rationale pack(PackContents contents);   // PACK
    Rationale closure(ClosureFacts facts);   // CLOSURE
}
```

`pack-validation`'s `PackRulesets` implements it, and `deployable`'s `CharterWiring` wires it.

## ClosureFacts (new)

The descriptors of a set of packs, each with its root level. `CLOSURE` reads these and no graph.

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
| `PackRefusals.of` | `Rationale` | `List<RefusalViolation>`, plus the warnings as `Diagnostic` | graph faults by path, then the read and rule faults, the orphans by path, the reserved shard, then manifest membership |

The reserved shard comes out as `drg_schema_error` in a verdict and as `reserved_shard` in a refusal (P7). Both
mappers read `failures()` only.

## Invariants

- No rule reads a file. Rules read only `PackContents` and `ClosureFacts`.
- Manifest membership is judged only when no other `VIOLATION` was found.
- A fault the store's reader meets that `PACK` did not report refuses the registration (fails closed).
