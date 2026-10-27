# Tasks: Mission step contract round trip

**Mission**: mission-step-contract-round-trip-01M5YHYD
**Planning base / merge target**: `feat/mission-step-contract-round-trip`
**Design sources**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md)

The work splits into five packages:

- **WP01** lands the schema-name seam as one isolated commit. Sibling #190 reuses it.
- **WP02** builds the domain entity. It is independent of WP01.
- **WP03** binds the kind in storage.
- **WP04** turns the gate and the corpus green.
- **WP05** records the ADR amendment and the changelog.

All code lives in `service/packs`. Unit tests are written before the code they cover (charter: test
first, no fake green). Code WPs use `execution_mode: code_change` and `agent_profile: java-jenny`, on
Java 25. The HTTP contract (`contracts/charter/`) is never edited (C-001).

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `KindBinding.schema()` hook and `KindBindings.bySchema()` (WP01) | WP01 | |
| T002 | `PackFileSet.loaded` and `YamlSerializationStrategy.read` resolve schemas through the registry (WP01) | WP01 | |
| T003 | `KindBindingsTest`: schema by `schema()`, entity name without underscores, file-token/schema tie to `file-kinds.yaml` (WP01) | WP01 | |
| T004 | Regression: the five kinds load and fingerprint unchanged (WP01) | WP01 | |
| T005 | Value records `ContractStep`, `StepDelegation`, `StepInput`, `GateBinding` with invariants (WP02) | WP02 | [P] |
| T006 | `MissionStepContractIdentifier` and `MissionStepContract` entity, builder, `CharterArtifact` members (WP02) | WP02 | |
| T007 | Domain tests: invariants, identity equality, no relationships, label/description, order kept (WP02) | WP02 | |
| T008 | `MissionStepContractDso` mirroring the schema with written-or-absent optionals (WP03) | WP03 | [P] |
| T009 | `MissionStepContractYaml` read and write, booleans kept, key order (WP03) | WP03 | |
| T010 | `MissionStepContractBinding` (schema `mission-step-contract`, file token `step-contract`) and registration in `KindBindings`/`YamlFormats` (WP03) | WP03 | |
| T011 | Storage tests: round trip incl. planted `inputs`, gate defaults written/omitted, unknown key at each level refused, folded strings, pack load from a nested directory (WP03) | WP03 | |
| T012 | `DomainRoundTrip` takes modelled kinds, writes and file tokens from the registry; `DomainRepositoryService.saveArtifact` package-private (WP04) | WP04 | |
| T013 | Remove the `mission-step-contract` expected-red entry; adjust `DomainRoundTripTest` (WP04) | WP04 | |
| T014 | Corpus test over every pinned `*.step-contract.yaml` (28) with a lossy-format positive control (WP04) | WP04 | |
| T015 | Generated pack tree and fingerprint/exactness tests: the step-contract decoy becomes a loaded kind (WP04) | WP04 | |
| T016 | Run the gate and `verifyAll`; record the gate report counts (WP04) | WP04 | |
| T017 | ADR-0037 amendment: a binding names its schema (WP05) | WP05 | [P] |
| T018 | Changelog entry and index line (WP05) | WP05 | [P] |
| T019 | Glossary and logical-model consistency check; docs checks (WP05) | WP05 | |

## WP01 — Schema-name seam (foundation)

**Goal**: a binding names its schema, and storage resolves schemas through the registry. No behaviour
change for the five bound kinds. **Priority**: P1. **Prompt**: [tasks/WP01-schema-name-seam.md](tasks/WP01-schema-name-seam.md)

T001 `KindBinding.schema()` hook and `KindBindings.bySchema()` (WP01)
T002 `PackFileSet.loaded` and `YamlSerializationStrategy.read` resolve schemas through the registry (WP01)
T003 `KindBindingsTest` generalised and tied to `file-kinds.yaml` (WP01)
T004 Regression: the five kinds load and fingerprint unchanged (WP01)

- **Independent test**: `./gradlew :packs:test` is green with no binding added.
- **Dependencies**: none. **Parallel**: with WP02.
- **Risk**: #190 lands the same seam differently. Mitigation: the seam is announced on #191, and
  whoever merges second rebases.

## WP02 — Domain entity (foundation)

**Goal**: `MissionStepContract` and its values in `packs.domain`. **Priority**: P1.
**Prompt**: [tasks/WP02-domain-entity.md](tasks/WP02-domain-entity.md)

T005 Value records with invariants (WP02)
T006 Identifier and entity (WP02)
T007 Domain tests (WP02)

- **Independent test**: the domain unit tests pass. ArchUnit holds.
- **Dependencies**: none. **Parallel**: with WP01.

## WP03 — Storage binding and YAML format (User Story 2)

**Goal**: the kind is bound and round-trips through the strategy and the store. **Priority**: P1.
**Prompt**: [tasks/WP03-storage-binding.md](tasks/WP03-storage-binding.md)

T008 DSO (WP03)
T009 YAML format (WP03)
T010 Binding and registration (WP03)
T011 Storage tests (WP03)

- **Dependencies**: WP01, WP02.
- **Risk**: booleans read as text. Mitigation: a dedicated boolean reader, with a test that the written
  `fail_open` is a YAML boolean.

## WP04 — Gate and corpus (User Stories 1 and 3)

**Goal**: the #187 gate models the kind from the registry, and all 28 pinned files round-trip.
**Priority**: P1. **Prompt**: [tasks/WP04-gate-and-corpus.md](tasks/WP04-gate-and-corpus.md)

T012 `DomainRoundTrip` from the registry (WP04)
T013 Expected-red entry removed (WP04)
T014 Corpus test with a positive control (WP04)
T015 Generated pack tree and fingerprint tests (WP04)
T016 Gate run and `verifyAll` (WP04)

- **Dependencies**: WP03.

## WP05 — Decision record and changelog (polish)

**Goal**: the amendment and the changelog are recorded. **Priority**: P2.
**Prompt**: [tasks/WP05-adr-amendment-and-changelog.md](tasks/WP05-adr-amendment-and-changelog.md)

T017 ADR-0037 amendment (WP05)
T018 Changelog (WP05)
T019 Consistency check and docs checks (WP05)

- **Dependencies**: WP04 (the changelog states measured counts).

## MVP

WP01 to WP04 make the gate green. WP05 records the change.
