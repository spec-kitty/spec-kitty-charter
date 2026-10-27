# Implementation Plan: Mission step contract round trip

**Branch**: `feat/mission-step-contract-round-trip` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)
**Decision record**: an amendment to [ADR-0037](../../docs/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam.md)
decision 4 (Proposed); no new ADR.

## Summary

Bind the `mission_step_contract` kind in the ADR-0037 registry: a domain entity in `packs.domain`,
a DSO, a binding and a YAML format in `packs.infra.storage`. First, let a binding name its schema,
since this kind's schema (`mission-step-contract`) and file token (`step-contract`) are not its
token. Then let the #187 gate model every bound kind from the registry, and drop the kind's
expected-red entry. The kernel, the HTTP contract and the controllers are untouched.

## Technical Context

**Language/Version**: Java 25 (CI); local builds may use `-Pcharter.javaVersion=21`
**Primary Dependencies**: Gradle; snakeyaml-engine (through `ParsedYaml`, `YamlDumpEmitter`); networknt JSON Schema (through `PackSchemas`); JUnit 5, AssertJ, ArchUnit. No dependency is added, upgraded or removed (DIRECTIVE_051: nothing to check).
**Storage**: pack files on disk (`*.step-contract.yaml`), validated by `contracts/packs/schemas/mission-step-contract.schema.json`
**Testing**: unit tests per class; a corpus test over the 28 pinned files; the whole-pack gate (`:packs:test`, `wholePackRoundTrip`) against open-packs at `openPacksRef`
**Target Platform**: the charter service (Spring Boot), Linux
**Project Type**: single (Gradle multi-module service; work stays in `service/packs`)
**Performance Goals**: none new; one more kind per pack load (28 small files in the pinned packs)
**Constraints**: no HTTP contract change; shared files get append-only edits except the schema-name seam; unknown fields refused; no workaround for pack inconsistencies
**Scale/Scope**: about 12 new source files, 6 edited shared files, about 8 test classes

## Charter Check

- **ADR-0006 slices.** The entity and its value types live in `packs.domain` and depend on the kernel
  and the JDK only. The binding, DSO and format live in `packs.infra.storage`. A missions slice is not
  created (ADR-0037 decision 6 names only profiles and mission types). ✅
- **ADR-0035.** The entity implements `CharterArtifact`. Its schema version is always present, so it
  returns one. ✅
- **ADR-0036.** Unknown fields are refused by the closed schema in `PackSchemas`, not by the reader.
  The DSO keeps what was written, so equality holds without schema defaults. No list is a set (the
  per-kind row says "Sets: none"). ✅
- **ADR-0037.** The kind joins by one line in `KindBindings` and one in `YamlFormats`. Decision 4's
  "schema named by the token" is relaxed by an in-place amendment: a binding names its schema, as it
  names its file token. ✅ (amendment needs the operator's acceptance with the ADR)
- **ADR-0032.** Steps carry no role or profile in the pinned packs. None is added. ✅
- **Charter "how work lands".** Domain and architecture change: squads ran pre-spec
  ([research/grounding-squad.md](research/grounding-squad.md)); a post-plan squad and a pre-PR squad
  follow. Calls made without review are logged on #191 (A1 to A3). ✅
- **Charter gates.** `verifyAll` on Java 25, the docs checks, the style check. ✅ planned.

## Project Structure

### Documentation (this mission)

```
kitty-specs/mission-step-contract-round-trip-01M5YHYD/
├── spec.md
├── plan.md
├── research.md
├── research/grounding-squad.md
├── data-model.md
├── quickstart.md
├── checklists/requirements.md
└── tasks/
```

### Source Code (repository root)

```
service/packs/src/main/java/ai/speckitty/charter/packs/
├── domain/
│   ├── MissionStepContract.java            (new: the entity and its builder)
│   ├── MissionStepContractIdentifier.java  (new)
│   ├── ContractStep.java                   (new: a step)
│   ├── StepDelegation.java                 (new: delegates_to)
│   ├── StepInput.java                      (new: inputs item)
│   └── GateBinding.java                    (new: gates item, inert)
└── infra/storage/
    ├── KindBinding.java                    (edit: schema() hook)
    ├── KindBindings.java                   (edit: bySchema(); append the binding)
    ├── PackFileSet.java                    (edit: schema → kind through the registry)
    ├── YamlSerializationStrategy.java      (edit: validate against binding.schema())
    ├── YamlFormats.java                    (edit: append the format)
    ├── DomainRepositoryService.java        (edit: saveArtifact package-private, for the gate adapter)
    ├── MissionStepContractBinding.java     (new)
    ├── MissionStepContractDso.java         (new)
    └── MissionStepContractYaml.java        (new)
service/packs/src/test/...                  (new tests; DomainRoundTrip, KindBindingsTest and the
                                             generated pack tree adjusted)
service/packs/src/test/resources/whole-pack/expected-red.yaml   (edit: remove the kind entry)
docs/adr/0037-…md                           (edit: amendment block)
docs/changelog/2026-10-27-mission-step-contract-kind.md         (new) and docs/changelog/README.md (append)
```

## Design

### The schema-name seam (IC-01)

- `KindBinding.schema()`, defaulting to `kind().token()`, beside `fileToken()`. Its javadoc says it is
  the schema `file-kinds.yaml` maps `*.<fileToken>.yaml` to.
- `KindBindings.bySchema(String)` returns the binding whose `schema()` is the given name.
- `PackFileSet.loaded` resolves a mapped file's schema through `bySchema`, not `ArtifactKind.fromToken`.
- `YamlSerializationStrategy.read` validates against `kind.schema()`.
- `KindBindingsTest`:
  - `everyBoundKindHasASchema` uses `schema()`.
  - `entityTypesMatchTheKinds` compares the simple name with the token without underscores.
  - A new test checks that `PackFileKind` classifies `<pack>/x.<fileToken>.yaml` to `schema()` for
    every binding.
- The five bound kinds keep `schema() == token`. No behaviour changes, and their fingerprints are
  unchanged.

### Domain (IC-02)

`MissionStepContract implements CharterArtifact` holds:
- the identifier, the pack, the schema version, the action and the mission;
- `steps` (`List<ContractStep>`, at least one);
- `gates` (`List<GateBinding>`).

Invariants (with `ModularRuleset` messages):
- the schema version, action, mission and step ids are not blank;
- there is at least one step;
- a delegation's candidates are non-empty and not blank.

Duplicate step ids are **not** refused here: validate mode owns that rule (A4).

The CharterArtifact members:
- `label()` is the `action`, as the CRG node's label is.
- `description()` is empty.
- `relationships()` is `List.of()`: delegations are values, not edges (FR-008).
- Equality is entity identity, by identifier.

The value records:
- `ContractStep(id, description, Optional command, List<StepInput> inputs, Optional<StepDelegation> delegatesTo, Optional guidance)`.
- `StepDelegation(ArtifactKind kind, List<String> candidates)`. The kind is typed: the schema's enum
  is exactly the artifact kinds.
- `StepInput(flag, source, Optional<Boolean> optional)`.
- `GateBinding(onTransition, handler, Optional handlerKind, schemaVersion, Optional<Boolean> failOpen, Optional provenance)`.
  It is inert data; #155 owns its meaning. `handlerKind` stays text for the same reason.

### Storage (IC-03)

`MissionStepContractDso` is a flat mirror of the schema. Optional fields are `Optional`, so "written"
and "absent" survive.

`MissionStepContractYaml`:
- **Read.** It uses `YamlFields` for text, and its own small boolean reader for `fail_open` and
  `optional`: the schema guarantees a `Boolean`, which `YamlFields.text` would turn into a string.
- **Write.** The key order is `schema_version`, `id`, `action`, `mission`, `steps`, `gates`. A step
  writes `id`, `description`, `command`, `inputs`, `delegates_to` (`kind`, `candidates`) and
  `guidance`. Booleans are written as `Boolean`, empty lists and optionals are omitted, and strings go
  through the dump schema, so `"1.0"` stays quoted.

`MissionStepContractBinding`:
- `schema()` is `mission-step-contract`, `fileToken()` is `step-contract`, and `idOf` is the `id`.
- `toEntity` maps `delegates_to.kind` with `ArtifactKind.fromToken`; an unknown token is an
  `IllegalArgumentException`, as the binding contract allows.

Registration is one line appended to `KindBindings` and one to `YamlFormats`.

### Gate (IC-04)

`DomainRoundTrip` drives its artifact kinds from the registry instead of a hard-coded set:
- The modelled artifact kinds are `KindBindings.all()` mapped to `schema()`, plus the descriptor and
  the graph shard.
- `writeAll` saves every artifact of the aggregate through `DomainRepositoryService.saveArtifact`.
- The written-file match uses each binding's `fileToken()`.

A sibling kind then joins the gate by binding itself, so the three hard-coded spots stop colliding.
The `mission-step-contract` entry leaves `expected-red.yaml`. `DomainRoundTripTest`'s modelled-kind
list grows by one.

**Corpus test (FR-006).** For every `*.step-contract.yaml` under the pinned open-packs `packs/`, the
test reads the file through the strategy, maps it to the entity and back, writes it, and reads it
back. It then compares the source and the written text with `MeaningComparator` and requires equal
meaning. It runs where the open-packs checkout is available, as `WholePackOpenPacksRoundTripTest`
does, and covers `qa`, which has no `pack.yaml`.

### Docs (IC-05)

- ADR-0037: an amendment block under decision 4.
- A changelog entry and a line in its index.
- The glossary's `step-contract` entry: checked, and changed only if it contradicts.
- `docs/architecture/logical-model.md`: no change; the kind row already holds.

## Complexity Tracking

| Choice | Why | Simpler alternative rejected |
|---|---|---|
| `schema()` hook on `KindBinding` | Three spellings of one kind. A plain binding loads nothing, silently. | Renaming the schema changes its `$id`, the gate kind name and the kebab convention. A `_`→`-` convention is three names agreeing by coincidence, with nothing checking it. |
| Gate kinds from the registry | Removes three hard-coded spots every Wave 1 sibling would edit. | Appending this kind to each spot works, but makes four missions conflict three times each. |

## Implementation Concern Map

### IC-01 — Schema-name seam
`KindBinding`, `KindBindings`, `PackFileSet`, `YamlSerializationStrategy`, `KindBindingsTest`. One isolated
commit. The five kinds' behaviour and fingerprints are unchanged. FR-004.

### IC-02 — Domain entity and values
`MissionStepContract` and its value records, with invariant tests. FR-001, FR-008, NFR-001.

### IC-03 — Storage binding and YAML format
DSO, format, binding, registration, round-trip unit tests including planted `inputs`, unknown-key
refusal at each level, and booleans. FR-001, FR-002, FR-003, FR-007.

### IC-04 — Gate and corpus
`DomainRoundTrip` from the registry, `expected-red.yaml`, the corpus test over 28 files,
`GeneratedPackTree` decoy promotion, fingerprint tests. FR-005, FR-006, NFR-002.

### IC-05 — Docs
ADR-0037 amendment, changelog, glossary check. NFR-003.
