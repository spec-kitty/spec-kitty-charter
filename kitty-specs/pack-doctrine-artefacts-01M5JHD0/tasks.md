# Tasks: Pack doctrine artefacts — directive, styleguide, paradigm

**Mission**: pack-doctrine-artefacts-01M5JHD0
**Planning base / merge target**: `feat/pack-doctrine-artefacts`
**Design sources**: [spec.md](./spec.md), [plan.md](./plan.md), [data-model.md](./data-model.md), [research.md](./research.md), [quickstart.md](./quickstart.md)

Decomposition is **layered** because the storage adapter (`SerializationStrategy`,
`YamlSerializationStrategy`, `FilesystemPackStore`, `DomainRepositoryService`) and the
`Pack` aggregate / `Tactic` are **shared files** across the three kinds — WP
`owned_files` may not overlap, so each shared seam is a single WP and the per-kind
leaf files are kind WPs. Subtask completion is event-sourced via
`spec-kitty agent tasks mark-status`.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `CharterArtifact` interface (spine + default `urn()`) | WP01 | |
| T002 | `Enforcement` typed value (explicit token field, round-trip) | WP01 | [P] |
| T003 | `ArtifactEdges` helper (refs → `DrgEdge`, target-kind + interim relations) | WP01 | |
| T004 | `DirectiveIdentifier` + `DirectiveReference` value objects | WP02 | [P] |
| T005 | `Directive` entity + `ModularRuleset` (conditional + non-blank procedures) | WP02 | |
| T006 | `DirectiveDso` record (nested `Reference`) | WP02 | |
| T007 | `StyleguideIdentifier` + `StyleguideScope` + `Pattern`/`AntiPattern` VOs | WP03 | [P] |
| T008 | `Styleguide` entity + `ModularRuleset` (scope enum, cardinality) | WP03 | |
| T009 | `ConfigValue` sealed raw-tree (raw-scalar typing, structural equality) | WP03 | |
| T010 | `StyleguideDso` record (patterns, tooling map, `Optional<ConfigValue>`) | WP03 | |
| T011 | `ParadigmIdentifier` + `ParadigmReference` value objects | WP04 | [P] |
| T012 | `Paradigm` entity + `ModularRuleset` (non-blank directive_refs) | WP04 | |
| T013 | `ParadigmDso` record | WP04 | |
| T014 | Extend `SerializationStrategy` with per-kind read/write | WP05 | |
| T015 | `YamlSerializationStrategy` read/write for the three kinds (closed-world via existing schema validate) | WP05 | |
| T016 | Raw-tree emit primitive (present-empty + nested nulls, bypassing omit rule) | WP05 | |
| T017 | Per-kind DSO round-trip tests (fidelity, incl. ConfigValue) | WP05 | |
| T018 | `Directive`/`Styleguide`/`Paradigm` repository ports (domain) | WP06 | [P] |
| T019 | `FilesystemPackStore` per-kind locate/new-file/upsert + `PackRead` extension | WP06 | |
| T020 | `DomainRepositoryService` per-kind load/save + aggregate assembly | WP06 | |
| T021 | Repository load/save contract tests | WP06 | |
| T022 | `Pack` aggregate: child collections + belongs-to/no-dup invariants + builder adders | WP07 | |
| T023 | `Tactic implements CharterArtifact` (label/description/edges) retrofit | WP07 | |
| T024 | Pack aggregate + Tactic-retrofit tests | WP07 | |
| T025 | Family test: all four kinds are `CharterArtifact` with kind-correct spine | WP08 | |
| T026 | Open-packs corpus round-trip test | WP08 | |
| T027 | Validation-parity audit + `deployable` wiring-round-trip analog; confirm ArchUnit gates | WP08 | |

## Work Packages

### WP01 — Family spine (`CharterArtifact`, `Enforcement`, edges helper)
Goal: the `packs.domain` family supertype and the shared edge-derivation helper every
kind builds on. Priority: P1 (foundational). Independent test: interface `urn()`
default, `Enforcement` token round-trip, `ArtifactEdges` target-kind + relation mapping.
Subtasks: T001, T002, T003. Dependencies: none. Prompt: `tasks/WP01-family-spine.md`.

### WP02 — Directive domain + DSO
Goal: the `Directive` kind's domain entity, identity/reference VOs, ruleset, and flat
DSO. Priority: P1. Independent test: build/validate a directive incl. the
`lenient-adherence ⇒ explicit_allowances` conditional; `DirectiveDso` equality.
Subtasks: T004, T005, T006. Dependencies: WP01. Prompt: `tasks/WP02-directive-domain.md`.

### WP03 — Styleguide domain + DSO + ConfigValue
Goal: the `Styleguide` kind incl. structured patterns and the nested-config value tree.
Priority: P1 (largest — carries FR-018). Independent test: scope-enum/cardinality
invariants; `ConfigValue` structural equality over a nested tree.
Subtasks: T007, T008, T009, T010. Dependencies: WP01. Prompt: `tasks/WP03-styleguide-domain.md`.

### WP04 — Paradigm domain + DSO
Goal: the lean `Paradigm` kind. Priority: P1. Independent test: required-field invariants
+ non-blank directive_refs. Subtasks: T011, T012, T013. Dependencies: WP01.
Prompt: `tasks/WP04-paradigm-domain.md`.

### WP05 — Serialization seam + round-trip
Goal: extend the shared YAML seam so all three kinds read (closed-world) and write with
field-wise fidelity, including the raw-tree emission rules. Priority: P1. Independent
test: per-kind `read(write(dso)).equals(dso)` across fixtures. Subtasks: T014, T015,
T016, T017. Dependencies: WP02, WP03, WP04. Prompt: `tasks/WP05-serialization-seam.md`.

### WP06 — Repository ports + storage adapter
Goal: per-type domain ports and their `FilesystemPackStore`/`DomainRepositoryService`
implementations (load/save one by id; aggregate assembly). Priority: P1. Independent
test: save-then-load returns an equal entity per kind. Subtasks: T018, T019, T020, T021.
Dependencies: WP02, WP03, WP04, WP05, WP07. Prompt: `tasks/WP06-repository-adapter.md`.

### WP07 — Pack aggregate + Tactic retrofit
Goal: extend `Pack` to hold the three kinds with the belongs-to-pack/no-duplicate-id
invariants, and retrofit `Tactic` onto `CharterArtifact`. Priority: P1. Independent
test: aggregate invariants; `Tactic` as `CharterArtifact` with unchanged behaviour.
Subtasks: T022, T023, T024. Dependencies: WP01, WP02, WP03, WP04.
Prompt: `tasks/WP07-aggregate-retrofit.md`.

### WP08 — Family + corpus + wiring verification
Goal: prove the family uniformity, corpus round-trip, validation parity, Spring wiring,
and that the hexagonal gates stay green. Priority: P1 (closeout). Independent test: this
WP *is* the verification. Subtasks: T025, T026, T027. Dependencies: WP05, WP06, WP07.
Prompt: `tasks/WP08-verification.md`.

## Sequencing & parallelism

- **Lane A (foundational):** WP01.
- **Lane B (parallel after WP01):** WP02 ∥ WP03 ∥ WP04 (disjoint new files).
- **Lane C:** WP05 (after WP02/03/04) ∥ WP07 (after WP02/03/04).
- **Lane D:** WP06 (after WP05 + WP07).
- **Lane E:** WP08 (after WP05/06/07).

MVP: WP01 + WP02 + WP05 + WP06 (+WP07 for aggregate) delivers a fully round-tripping,
repository-backed `Directive` — the highest-value slice — before styleguide/paradigm land.
