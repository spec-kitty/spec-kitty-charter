# Implementation Plan: Closure symmetry: one closure rule on every path

**Branch**: `kitty/mission-closure-symmetry-01M7PNFT` → `feat/355-closure-in-pack-ruleset` → `main` | **Date**: 2026-11-18 |
**Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/closure-symmetry-01M7PNFT/spec.md`

## Summary

The closure (asset entry, guard asset, guard `config_schema`, lifecycle guard entries and `config`, mission-type output
templates, with the cascade) moves from `packs.infra.storage` (`GuardClosure`, `MissionTemplates`) into one pure domain
class, `ReferenceClosure`, in `packs.domain.rules`. `PackRulesets.content()` gains `ReferenceRules.RULES`, which runs
`ReferenceClosure` over a new fact of the one read, `ReferenceFacts`. The four paths then call the same rule:

| Path | Calls | On a finding |
|------|-------|--------------|
| Load (`PackAssembly.collect`) | `ReferenceClosure.firstUnresolved` | leaves the artifact out, `artifact_schema_error`, cascading (unchanged) |
| Whole-pack write (`WholePackStore.write`) | `ReferenceClosure.findings(pack)` | `validation_failed`, today's wording (unchanged) |
| Validate mode (`PackCheck` → `StorageRulesets.verdict` → `content()`) | `ReferenceRules.RULES` | error finding at the file, the load's reason |
| Per-file write (`CommitStorePackWriteStore.commit` and `.remove`) | `ReferenceClosure.findings(after)` | `409 registration_conflict`, the load's reason |

`EmbeddedSchemas` stays in infra and implements a new domain port, `ConfigSchemas`, which the rule is given. The
`Executable` and `Lifecycle` invariants already reach validate mode through #554's shared assembly (store-only
value-object breaches). The symmetry table pins them on every path; on the two write paths the domain value cannot be
built, so the table asserts the constructor's refusal there.

#593: the fair-lock case of `MixedReadConcurrencyTest` measures each write's wait per round against a 4 s bound (under the rig's 5 s registry lock) and
fails naming the write and the round; the class-level `@Timeout` becomes a loose 120 s hang guard.

## Technical Context

**Language/Version**: Java 25 (Gradle 9, `./gradlew verifyAll`)
**Primary Dependencies**: none added. The kernel validation vocabulary (`ModularRuleset`, `Rule`, `Reason`,
`ReasonCode`), the existing JSON Schema library behind `EmbeddedSchemas`, JUnit 5, AssertJ
**Storage**: pack trees on the file system (`FilesystemPackStore`, `PackCommitStore`); unchanged
**Testing**: JUnit 5 + AssertJ; red-first per concern; one table-driven symmetry test (`ClosureSymmetryTest`) over
the `guard-lifecycle` and a mission-type fixture, each row with a repaired control; ArchUnit gate; whole-pack round trip
**Target Platform**: the charter service (Linux container, JVM 25)
**Project Type**: single (Gradle multi-module service: `:packs`, `:pack-validation`, `:kernel`)
**Performance Goals**: the per-file closure check is linear in the pack's guards, lifecycles, assets and mission types;
it adds no file read on the write path (it judges the in-memory aggregate)
**Constraints**: ADR-0006 onion (domain names no infra; the application ring no infra); C-001 no legacy reader; C-002
canonical shapes in tests
**Scale/Scope**: about 10 main files and 30 test files in `:packs` (most of the tests touch only the moved constant),
2 in `:pack-validation`, 2 docs

**Supply chain**: no dependency is added, upgraded or removed; DIRECTIVE_051 has nothing to check.

## Charter Check

| Rule | Result |
|------|--------|
| The charter's model is canonical; no legacy reader | Pass. `GuardClosure` and `MissionTemplates` are deleted, not wrapped. `artifact_schema_error` gets one home (`PackCodes`), not an alias in `StorageCodes`. |
| Tests use only the canonical shape | Pass. Broken fixtures appear only as error-handling rows of the symmetry table, each with a repaired control. |
| Contract, architecture and domain changes get an adversarial squad at plan and before merge | Pass by one independent reviewer at plan (research.md, "Plan review"), and one before merge. Logged as an open review item for the operator, because a full squad was not run. |
| Gates before merge | `verifyAll` on Java 25, whole-pack round trip, docs checks. |
| Issues stay small | Pass. Nothing is filed. `Closes #355`, `Closes #593`. Tick epic #142 (and the orchestrator's #549). |
| Pack content goes to open-packs | Pass. No pack content changes; the drift test is run and reported. |

## Project Structure

### Documentation (this mission)

```
kitty-specs/closure-symmetry-01M7PNFT/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── occurrence_map.yaml     # bulk edit: StorageCodes.ARTIFACT_SCHEMA_ERROR -> PackCodes.ARTIFACT_SCHEMA_ERROR
└── tasks/
```

No `contracts/`: `409 registration_conflict` is already declared on every per-file write and delete path
(`contracts/charter/paths/packs_packId_artifacts_*.yaml`, `packs_packId_relationships.yaml`); `meta.json` records
`"contracts": "none"`.

### Source Code (repository root)

```
service/packs/src/main/java/ai/speckitty/charter/packs/
├── domain/ConfigSchemas.java                 # new port
├── domain/PackContents.java                  # + closure fact
├── domain/PackCodes.java                     # + ARTIFACT_SCHEMA_ERROR
├── domain/Pack.java                          # + without(urns)
├── domain/rules/ReferenceClosure.java             # new: the rule
├── domain/rules/ReferenceRules.java            # new: Rule<PackContents>
├── domain/rules/PackRulesets.java            # content() gains ReferenceRules
├── application/PackRemovalStore.java         # Removal carries the pack after
├── application/ArtifactRemovalService.java   # passes it
└── infra/storage/
    ├── EmbeddedSchemas.java                  # implements ConfigSchemas
    ├── PackAssembly.java, StoreRead.java     # call ReferenceClosure; build ReferenceFacts
    ├── WholePackStore.java                   # calls ReferenceClosure
    ├── CommitStorePackWriteStore.java        # per-file judge, 409
    ├── GuardClosure.java, MissionTemplates.java   # deleted
    └── rules/StorageCodes.java               # loses ARTIFACT_SCHEMA_ERROR
service/packs/src/test/.../ClosureSymmetryTest.java, MixedReadConcurrencyTest.java
docs/adr/0042-*.md, docs/plans/pack-ecosystem-work-breakdown.md
```

**Structure Decision**: the existing Gradle module layout; no new module.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|------|------------|--------------------------------------|
| `ReferenceFacts` on `PackContents` | the rule must see the candidates the load judges, before the load leaves any out | judging the assembled pack misses exactly the dropped artifacts |
| Moving `artifact_schema_error` to `PackCodes` (bulk edit) | a domain rule cannot name an infra code, and the storage and domain vocabularies are disjoint (`StorageCodesTest`) | a new code would change the load's code and the registration wire |

## Implementation Concern Map

### IC-01 — The closure rule in the domain

- **Purpose**: one pure rule decides every closure case with the cascade; the load and the whole-pack write call it.
- **Relevant requirements**: FR-001, FR-002
- **Affected surfaces**: `domain/rules/ReferenceClosure.java`, `domain/ConfigSchemas.java`, `infra/storage/EmbeddedSchemas.java`, `PackAssembly.java`, `WholePackStore.java`; delete `GuardClosure.java`, `MissionTemplates.java`; move `ARTIFACT_SCHEMA_ERROR`
- **Sequencing/depends-on**: none
- **Risks**: the wording and order the load and the write state today must not move; the existing load and round-trip tests are the guard.

### IC-02 — The rule in the PACK ruleset (validate mode)

- **Purpose**: `content()` runs the closure over the read's candidates, so validate mode and registration state it from the ruleset, not as a store-only fault.
- **Relevant requirements**: FR-003, FR-005
- **Affected surfaces**: `PackContents` (closure fact), `ReferenceRules`, `PackRulesets`, `StoreRead`, `AssemblyRules` (closure faults leave the store-only list)
- **Sequencing/depends-on**: IC-01
- **Risks**: the verdict's order and the golden snapshots (`golden/validate.json`, `surfaces.json`) may move; any diff is listed.

### IC-03 — The per-file write judge

- **Purpose**: the commit store's choke point refuses a write or a delete whose result does not close, with `409 registration_conflict`, under `PackWriteLocks`.
- **Relevant requirements**: FR-004
- **Affected surfaces**: `CommitStorePackWriteStore`, `PackRemovalStore.Removal`, `ArtifactRemovalService`, `Pack.without`
- **Sequencing/depends-on**: IC-01
- **Risks**: the judge must run before any staging, and must not refuse a write on a pack the load already closed.

### IC-04 — The symmetry table

- **Purpose**: one table, every case on all four paths, each row with a repaired control; per-case tests folded in.
- **Relevant requirements**: FR-006, SC-001
- **Affected surfaces**: new `ClosureSymmetryTest` (`:pack-validation` test, which reaches `PackCheck` and the packs store); `GuardAndLifecycleLoadTest` rows folded
- **Sequencing/depends-on**: IC-01, IC-02, IC-03 (written red first, before them)

### IC-05 — #593: the per-write wait bound

- **Purpose**: the starvation case fails naming the write and round that waited too long; the outer timeout catches only a hang.
- **Relevant requirements**: FR-008, NFR-002
- **Affected surfaces**: `MixedReadConcurrencyTest`
- **Sequencing/depends-on**: none

### IC-06 — Docs and closeout

- **Purpose**: ADR-0042 decision 7's known-gap paragraph amended; work breakdown item 8 note cleared; drift test run and reported.
- **Relevant requirements**: FR-007, NFR-003
- **Affected surfaces**: `docs/adr/0042-*.md`, `docs/plans/pack-ecosystem-work-breakdown.md`
- **Sequencing/depends-on**: IC-01 to IC-04
