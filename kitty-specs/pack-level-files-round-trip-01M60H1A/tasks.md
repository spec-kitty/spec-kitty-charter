# Tasks: Pack-level files round trip

**Mission**: pack-level-files-round-trip-01M60H1A
**Planning base / merge target**: `feat/195-pack-level-round-trip`
**Design sources**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/pack-level-shapes.md](./contracts/pack-level-shapes.md), [quickstart.md](./quickstart.md)

The work splits into eight packages. Each one leaves the build green:

- **WP01**, **WP02** and **WP03** are independent foundations: the schemas, the preset domain, and the descriptor closure.
- **WP04** puts presets through storage, validate mode and the gate.
- **WP05** builds the manifest model and its recipes.
- **WP06** wires the manifest into `WholePackStore` and the gate.
- **WP07** records ADR-0043 and the living docs.
- **WP08** brings in `pack.md` through #194's `markdown` form. It is blocked until #194's PR-A merges.

Code WPs use `java-jenny` on Java 25. The binding acceptance checks are in plan.md ("Acceptance checks").

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `preset.schema.json` (WP01) | WP01 | [P] |
| T002 | `pack-manifest.schema.json` (WP01) | WP01 | [P] |
| T003 | `PackLevelSchemasTest` with paired controls (WP01) | WP01 |  |
| T004 | `PresetItem` and `Preset` with invariants and derived required set (WP02) | WP02 | [P] |
| T005 | `Pack` presets slot and keyed-sort helper (WP02) | WP02 |  |
| T006 | Domain tests (WP02) | WP02 |  |
| T007 | Close `pack-descriptor.schema.json` (WP03) | WP03 |  |
| T008 | Remove undeclaredKeys, PACK_UNKNOWN_KEY and PackDescriptorWriter (WP03) | WP03 |  |
| T009 | Tests move to the refusal (WP03) | WP03 |  |
| T010 | Close DIV-005 and DIV-006 (WP03) | WP03 |  |
| T011 | `file-kinds.yaml` preset rule (WP04) | WP04 |  |
| T012 | `PresetDso`, `PresetYaml`, `PresetFiles`, `readValidated` (WP04) | WP04 |  |
| T013 | Store, assembly and write (WP04) | WP04 |  |
| T014 | Fingerprint tail (WP04) | WP04 |  |
| T015 | Validate mode (WP04) | WP04 |  |
| T016 | Gate: preset kind, known failures, placeholders (WP04) | WP04 |  |
| T017 | Fixture pack and seam test (WP04) | WP04 |  |
| T018 | Manifest DSO and YAML format (WP05) | WP05 | [P] |
| T019 | `PackManifests` recipes and `recompute` (WP05) | WP05 |  |
| T020 | Recipe vectors and pinned parity (WP05) | WP05 |  |
| T021 | Manifest rule and `WholePackStore` ownership (WP06) | WP06 |  |
| T022 | Fingerprint and validate mode (WP06) | WP06 |  |
| T023 | Gate: manifest kind, comparator rule, derived-field verification (WP06) | WP06 |  |
| T024 | Fixture and corpus proofs (WP06) | WP06 |  |
| T025 | Comparator test and timing note (WP06) | WP06 |  |
| T026 | ADR-0043 (WP07) | WP07 | [P] |
| T027 | Amendment notes and ADR index (WP07) | WP07 |  |
| T028 | Changelog and glossary (WP07) | WP07 |  |
| T029 | Architecture pages and conformance divergence (WP07) | WP07 |  |
| T030 | pack.md rule and domain slot via #194 (WP08) | WP08 |  |
| T031 | Gate: pack-document (WP08) | WP08 |  |
| T032 | pack.md tests and ADR note (WP08) | WP08 |  |

## WP01 — Closed schemas for the preset and the pack manifest

**Prompt**: [tasks/WP01-pack-level-schemas.md](tasks/WP01-pack-level-schemas.md)
**Depends on**: none
**Requirements**: FR-001, FR-006, C-003

T001 `preset.schema.json` (WP01)
T002 `pack-manifest.schema.json` (WP01)
T003 `PackLevelSchemasTest` with paired controls (WP01)

## WP02 — Preset in the domain

**Prompt**: [tasks/WP02-preset-domain.md](tasks/WP02-preset-domain.md)
**Depends on**: none
**Requirements**: FR-002, FR-003

T004 `PresetItem` and `Preset` with invariants and derived required set (WP02)
T005 `Pack` presets slot and keyed-sort helper (WP02)
T006 Domain tests (WP02)

## WP03 — Close pack.yaml: refuse unknown keys, retire undeclaredKeys

**Prompt**: [tasks/WP03-descriptor-closure.md](tasks/WP03-descriptor-closure.md)
**Depends on**: none
**Requirements**: FR-011, FR-012, C-001

T007 Close `pack-descriptor.schema.json` (WP03)
T008 Remove undeclaredKeys, PACK_UNKNOWN_KEY and PackDescriptorWriter (WP03)
T009 Tests move to the refusal (WP03)
T010 Close DIV-005 and DIV-006 (WP03)

## WP04 — Presets read, written, fingerprinted, validated and gated

**Prompt**: [tasks/WP04-preset-storage-and-gate.md](tasks/WP04-preset-storage-and-gate.md)
**Depends on**: WP01, WP02
**Requirements**: FR-002, FR-003, FR-004, FR-013, FR-014, FR-018, NFR-002, C-002, C-005

T011 `file-kinds.yaml` preset rule (WP04)
T012 `PresetDso`, `PresetYaml`, `PresetFiles`, `readValidated` (WP04)
T013 Store, assembly and write (WP04)
T014 Fingerprint tail (WP04)
T015 Validate mode (WP04)
T016 Gate: preset kind, known failures, placeholders (WP04)
T017 Fixture pack and seam test (WP04)

## WP05 — Manifest storage object, YAML format and hash recipes

**Prompt**: [tasks/WP05-manifest-model-and-recipes.md](tasks/WP05-manifest-model-and-recipes.md)
**Depends on**: WP01
**Requirements**: FR-007, FR-008, NFR-001

T018 Manifest DSO and YAML format (WP05)
T019 `PackManifests` recipes and `recompute` (WP05)
T020 Recipe vectors and pinned parity (WP05)

## WP06 — Manifest owned by WholePackStore, regenerated on write, and gated

**Prompt**: [tasks/WP06-manifest-wiring-and-gate.md](tasks/WP06-manifest-wiring-and-gate.md)
**Depends on**: WP03, WP04, WP05
**Requirements**: FR-009, FR-010, FR-013, FR-014, FR-015, FR-018, NFR-004

T021 Manifest rule and `WholePackStore` ownership (WP06)
T022 Fingerprint and validate mode (WP06)
T023 Gate: manifest kind, comparator rule, derived-field verification (WP06)
T024 Fixture and corpus proofs (WP06)
T025 Comparator test and timing note (WP06)

## WP07 — ADR-0043, amendment notes, changelog, glossary and architecture docs

**Prompt**: [tasks/WP07-decision-records-and-docs.md](tasks/WP07-decision-records-and-docs.md)
**Depends on**: WP06
**Requirements**: FR-016, FR-017, NFR-003

T026 ADR-0043 (WP07)
T027 Amendment notes and ADR index (WP07)
T028 Changelog and glossary (WP07)
T029 Architecture pages and conformance divergence (WP07)

## WP08 — pack.md through #194's markdown content form

**Prompt**: [tasks/WP08-pack-document-markdown.md](tasks/WP08-pack-document-markdown.md)
**Depends on**: WP06
**Requirements**: FR-005

T030 pack.md rule and domain slot via #194 (WP08)
T031 Gate: pack-document (WP08)
T032 pack.md tests and ADR note (WP08)
