# Tasks: Asset and template round trip

**Mission**: `asset-template-round-trip-01M5YY7G` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)
**Planning branch / merge target**: `feat/asset-template-round-trip` (PR to `main`, issue #193)

Eight work packages. WP01 (vocabulary), WP02 (domain), WP03 (revision over bytes) and WP04 (pinned refusals in the
gate expectation) start in parallel. WP05 (the whole storage read and write path, with the fingerprint oracle and the
open-packs round trip it would otherwise break) needs all four. WP06 (storage proofs) and WP07 (whole-pack gate) then
run in parallel; WP08 (ADR and living docs) needs both. Every WP leaves `:packs:test :packs:check` green using only
the files it owns. The whole-pack gate (not part of `check`) is red from WP01 until WP07; the PR opens after WP07.

Acceptance-check owners (plan "Acceptance checks"): 1, 2, 5, 10 → WP05; 3, 4, 8, 9 → WP07; 6, 11 → WP06;
7, 12 → WP01.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `ContentForm`; `PackFileKind.Mapped(kind, Optional schema, contentForm)`; `PayloadRule` | WP01 | |
| T002 | Parse `content_form` and `payloads`; unknown keys refused; unbuilt forms refused; payload overlap check | WP01 | |
| T003 | Edit `contracts/packs/file-kinds.yaml` and `contracts/packs/README.md` | WP01 | |
| T004 | Validate mode skips schema-less `Mapped`; findings over built-in unchanged | WP01 | [P] |
| T005 | Classification tests | WP01 | |
| T006 | `Payload` | WP02 | [P] |
| T007 | `AssetIdentifier`, `Asset` | WP02 | |
| T008 | `AssetFile` | WP02 | [P] |
| T009 | `TemplateIdentifier`, `Template` | WP02 | [P] |
| T010 | `Pack`: asset files via `toBuilder`/`with`, views, invariants, javadoc | WP02 | |
| T011 | `PackRevision.computeOverBytes` (same values for text) | WP03 | |
| T012 | Revision readers over bytes | WP03 | |
| T013 | If-Match over bytes | WP03 | |
| T014 | Expectation file entries with exact `refused` reason and `pack` | WP04 | |
| T015 | Pinned-refusals accessor | WP04 | |
| T016 | `expected-red.yaml`: −3 kinds, +6 built-in entries | WP04 | |
| T017 | Asset sidecar binding; title round trip | WP05 | |
| T018 | `PackFileSet` walk and digest | WP05 | |
| T019 | `TemplateBinding`, `PackLayout` | WP05 | |
| T020 | `FilesystemPackStore` bytes and refusals | WP05 | |
| T021 | Shared assembly; `saveArtifact`/`writeAssetFile` | WP05 | |
| T022 | `WholePackStore` bytes write | WP05 | |
| T023 | Oracle rows | WP05 | |
| T024 | Open-packs fidelity and corrected built-in copy | WP05 | |
| T025 | Literal fingerprint pin; payload/template controls | WP06 | [P] |
| T026 | Registration keeps asset files | WP06 | [P] |
| T027 | Relationship endpoints through the real store | WP06 | [P] |
| T028 | Revision and If-Match over binary payloads | WP06 | [P] |
| T029 | Census content form; mission templates stay `other:missions` | WP07 | |
| T030 | `DomainRoundTrip` | WP07 | |
| T031 | Bytes branch, lossy controls, exact counts | WP07 | |
| T032 | ADR-0040 and index | WP08 | |
| T033 | ADR-0036 revised | WP08 | |
| T034 | Logical model and glossary | WP08 | [P] |
| T035 | Changelog, open-packs issue text, docs checks | WP08 | |

## Phase 1 — Foundations

### WP01 — Content form in the pack vocabulary

**Prompt**: [tasks/WP01-content-form-in-the-pack-vocabulary.md](tasks/WP01-content-form-in-the-pack-vocabulary.md)
**Goal**: the table declares content form and classifies schema-less payload files. **Priority**: P1.

T001 `ContentForm`, `Mapped`, `PayloadRule` (WP01)
T002 Parsing, strictness, overlap (WP01)
T003 Table and README (WP01)
T004 Validate mode (WP01)
T005 Classification tests (WP01)

Dependencies: none.

### WP02 — Asset and template domain model

**Prompt**: [tasks/WP02-asset-and-template-domain-model.md](tasks/WP02-asset-and-template-domain-model.md)
**Goal**: domain types and `Pack` additions with invariants. **Priority**: P1.

T006 `Payload` (WP02)
T007 `Asset` (WP02)
T008 `AssetFile` (WP02)
T009 `Template` (WP02)
T010 `Pack` additions (WP02)

Dependencies: none.

### WP03 — Pack revision over bytes

**Prompt**: [tasks/WP03-revision-over-bytes.md](tasks/WP03-revision-over-bytes.md)
**Goal**: ETags and If-Match computable over binary files, values unchanged for text. **Priority**: P1.

T011 `computeOverBytes` (WP03)
T012 Revision readers (WP03)
T013 If-Match (WP03)

Dependencies: none.

### WP04 — Pinned refusals in the gate expectation

**Prompt**: [tasks/WP04-pinned-refusals-in-the-gate-expectation.md](tasks/WP04-pinned-refusals-in-the-gate-expectation.md)
**Goal**: one pinned list of the six built-in refusals, read by both ratchets. **Priority**: P1.

T014 Entry shape (WP04)
T015 Accessor (WP04)
T016 `expected-red.yaml` (WP04)

Dependencies: none.

## Phase 2 — Storage

### WP05 — Storage read and write path for assets and templates

**Prompt**: [tasks/WP05-storage-read-and-write-path.md](tasks/WP05-storage-read-and-write-path.md)
**Goal**: stores load and write sidecars, asset files and templates; fingerprint covers them; open-packs round trip
proves file-level fidelity. **Priority**: P1.

T017 Asset binding (WP05)
T018 Walk and digest (WP05)
T019 `TemplateBinding`, `PackLayout` (WP05)
T020 `FilesystemPackStore` (WP05)
T021 Shared assembly (WP05)
T022 `WholePackStore` (WP05)
T023 Oracle rows (WP05)
T024 Open-packs fidelity (WP05)

Dependencies: WP01, WP02, WP03, WP04. Risks: `KindBindings`/`YamlFormats` shared hotspots; largest WP (8 subtasks).

## Phase 3 — Proofs, gate and records

### WP06 — Storage proofs

**Prompt**: [tasks/WP06-storage-proofs.md](tasks/WP06-storage-proofs.md)

T025 Literal fingerprint pin (WP06)
T026 Registration keeps asset files (WP06)
T027 Relationship endpoints (WP06)
T028 Binary revision and If-Match (WP06)

Dependencies: WP05.

### WP07 — Whole-pack gate for assets and templates

**Prompt**: [tasks/WP07-whole-pack-gate.md](tasks/WP07-whole-pack-gate.md)

T029 Census content form (WP07)
T030 `DomainRoundTrip` (WP07)
T031 Bytes branch and controls (WP07)

Dependencies: WP05. Risks: `REQUIRED_KINDS`/`ARTEFACT_KINDS` shared with every sibling.

### WP08 — Decision record and living docs

**Prompt**: [tasks/WP08-decision-record-and-living-docs.md](tasks/WP08-decision-record-and-living-docs.md)

T032 ADR-0040 (WP08)
T033 ADR-0036 (WP08)
T034 Logical model, glossary (WP08)
T035 Changelog, issue text, docs checks (WP08)

Dependencies: WP06, WP07.

## Parallel opportunities

- WP01 ∥ WP02 ∥ WP03 ∥ WP04; then WP05; then WP06 ∥ WP07; then WP08.

## MVP

WP01–WP05 deliver the round trip; WP06 and WP07 prove it; WP08 records it.
