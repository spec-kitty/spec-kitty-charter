# Tasks: Asset and template round trip

**Mission**: `asset-template-round-trip-01M5YY7G` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)
**Planning branch / merge target**: `feat/asset-template-round-trip` (PR to `main`, issue #193)

Six work packages. WP01 (vocabulary) and WP02 (domain) start in parallel. WP03 (the whole storage read and write
path) needs both: sidecars, asset files and templates must land together, or the new `Pack` invariant would breach on
packs that hold assets. WP04 (storage proofs) and WP05 (gate and open-packs ratchets) then run in parallel. WP06 (ADR
and living docs) needs WP03, so it describes the shipped storage. The plan's "Acceptance checks" 1–12 are binding and are each owned by
exactly one WP below.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `ContentForm` enum; `PackFileKind.Mapped(kind, Optional schema, contentForm)`; `PayloadRule` | WP01 | |
| T002 | Parse `content_form` on `files` rules and the new `payloads` section; payload-vs-payload overlap check | WP01 | |
| T003 | Edit `contracts/packs/file-kinds.yaml` (payload rules, README exclusion, retire `**/*.config.yaml`, header) and `contracts/packs/README.md` | WP01 | |
| T004 | Validate mode skips schema-less `Mapped`; built-in validate verdict unchanged | WP01 | [P] |
| T005 | `PackFileKindTest`/`VocabularyEqualityTest`: nested samples, payload classification, README, unbuilt form | WP01 | |
| T006 | `Payload` value object | WP02 | [P] |
| T007 | `AssetIdentifier`, `Asset` (`CharterArtifact`, no schema version) | WP02 | |
| T008 | `AssetFile` value object | WP02 | [P] |
| T009 | `TemplateIdentifier`, `Template` (`CharterArtifact`, bytes) | WP02 | [P] |
| T010 | `Pack`: asset files carried by `toBuilder`/`with`, `assets()`/`templates()` views, payload invariants, javadoc | WP02 | |
| T011 | `AssetDso`, `AssetYaml`, `AssetBinding`; append to `KindBindings` and `YamlFormats` | WP03 | |
| T012 | `PackFileSet`: walk every regular file, classify once, list bytes-kind files; digest asset files then templates last | WP03 | |
| T013 | `PackRevision` over bytes; `PackCommitStore` and `CommitStorePackWriteStore` read digested files as bytes | WP03 | |
| T014 | `TemplateBinding` (concrete) and `PackLayout` paths for templates | WP03 | |
| T015 | `FilesystemPackStore`: read asset files and templates as bytes; missing- and duplicate-payload refusals; bytes write; unbuilt content form refused | WP03 | |
| T016 | One shared assembly helper (`DomainRepositoryService.assemble`, `WholePackStore.load`); templates through `writeArtifact`; package-private `writeAssetFile` | WP03 | |
| T017 | `WholePackStore`: bytes `put`, asset files and templates written, modelled set, case collisions, 0-byte files | WP03 | |
| T018 | `GeneratedPackTree`/`PackFileSetExactnessTest` shape rows; `DECOY_ASSET` reclassified | WP04 | [P] |
| T019 | Literal digest list and fingerprint pinned for packs without assets/templates | WP04 | [P] |
| T020 | Registration into a pack holding an asset keeps its asset files (real registration service) | WP04 | [P] |
| T021 | `asset:` and `template:` URNs accepted as relationship endpoints; unknown URN still refused | WP04 | [P] |
| T022 | If-Match/ETag and commit over a pack with binary payloads | WP04 | [P] |
| T023 | `WholePackCensus` and `WholePackExpectation`: content form per file; optional exact `refused` reason and `pack` on file entries | WP05 | |
| T024 | `DomainRoundTrip`: assets, asset files, templates saved and paired; `MODELLED` gains exactly three kinds | WP05 | |
| T025 | `WholePackRoundTripTest`: bytes branch before any text read; lossy-byte and truncation controls through the save path; exact counts | WP05 | |
| T026 | `expected-red.yaml`: remove `asset`, `other:assets`, `other:templates`; add six built-in entries with exact reasons | WP05 | |
| T027 | `WholePackOpenPacksRoundTripTest`: pinned entries from `expected-red.yaml`, file-level comparison, per-pack counts, corrected built-in copy | WP05 | |
| T028 | ADR-0040 and the ADR index | WP06 | |
| T029 | ADR-0036 revised in place (Other files paragraph, per-kind rows) | WP06 | |
| T030 | `docs/architecture/logical-model.md` assets/templates section; glossary entries | WP06 | [P] |
| T031 | `docs/changelog/` entry; open-packs issue text; docs checks green | WP06 | |

## Phase 1 — Foundations

### WP01 — Content form in the pack vocabulary

**Prompt**: [tasks/WP01-content-form-in-the-pack-vocabulary.md](tasks/WP01-content-form-in-the-pack-vocabulary.md)
**Goal**: `file-kinds.yaml` declares each file's content form and classifies schema-less payload files; every
consumer of the table keeps working. **Priority**: P1. **Independent test**: `PackFileKindTest` classifies
`built-in/assets/audiences/line_manager.md` as `Mapped(asset-payload, no schema, BYTES)`, `built-in/assets/README.md`
as excluded, `built-in/templates/writing/meeting-minutes.md` as `template`, and a mission template as unclassified.

T001 `ContentForm`, `Mapped` shape, `PayloadRule` (WP01)
T002 Parse `content_form` and `payloads`; payload overlap check (WP01)
T003 Edit `file-kinds.yaml` and `contracts/packs/README.md` (WP01)
T004 Validate mode skips schema-less `Mapped` (WP01)
T005 Classification tests (WP01)

Dependencies: none. Risks: shared with #192 (markdown rows); no trailing `**` in the glob language; validate-mode
output for built-in must not change.

### WP02 — Asset and template domain model

**Prompt**: [tasks/WP02-asset-and-template-domain-model.md](tasks/WP02-asset-and-template-domain-model.md)
**Goal**: `Payload`, `AssetFile`, `Asset`, `Template` and the `Pack` additions exist with invariants and tests.
**Priority**: P1. **Independent test**: domain unit tests; `HexagonalArchitectureTest` green.

T006 `Payload` (WP02)
T007 `AssetIdentifier`, `Asset` (WP02)
T008 `AssetFile` (WP02)
T009 `TemplateIdentifier`, `Template` (WP02)
T010 `Pack` additions (WP02)

Dependencies: none. Risks: `Pack.with` must not drop asset files (post-plan P3).

## Phase 2 — Storage

### WP03 — Storage read and write path for assets and templates

**Prompt**: [tasks/WP03-storage-read-and-write-path.md](tasks/WP03-storage-read-and-write-path.md)
**Goal**: the per-pack and whole-pack stores load sidecars, asset files and templates into the `Pack`, refuse missing
payloads as violations, write every byte back, and fingerprint and revision them as bytes. **Priority**: P1.

T011 Asset binding (WP03)
T012 `PackFileSet` walk and digest (WP03)
T013 Revision over bytes (WP03)
T014 `TemplateBinding`, `PackLayout` (WP03)
T015 `FilesystemPackStore` bytes and refusals (WP03)
T016 Shared assembly; `writeAssetFile` (WP03)
T017 `WholePackStore` bytes write (WP03)

Dependencies: WP01, WP02. Risks: `KindBindings`/`YamlFormats` are append-only hotspots shared with #190/#191/#192;
packs without assets must keep their exact digest.

## Phase 3 — Proofs, gate and records

### WP04 — Storage proofs

**Prompt**: [tasks/WP04-storage-proofs.md](tasks/WP04-storage-proofs.md)
**Goal**: generated, literal and service-level tests that pin the fingerprint, registrations and relationship
endpoints with assets present. **Priority**: P1.

T018 Generated-tree oracle rows (WP04)
T019 Literal fingerprint pin (WP04)
T020 Registration keeps asset files (WP04)
T021 Relationship endpoints (WP04)
T022 ETag and commit over binaries (WP04)

Dependencies: WP03.

### WP05 — Gate and open-packs ratchets

**Prompt**: [tasks/WP05-gate-and-open-packs-ratchets.md](tasks/WP05-gate-and-open-packs-ratchets.md)
**Goal**: the whole-pack gate turns `asset`, `asset-payload` and `template` green with exactly six pinned reds; the
open-packs test proves file-level fidelity over every pack. **Priority**: P1.

T023 Census and expectation (WP05)
T024 `DomainRoundTrip` (WP05)
T025 Gate bytes branch and controls (WP05)
T026 `expected-red.yaml` (WP05)
T027 Open-packs fidelity and corrected built-in copy (WP05)

Dependencies: WP03. Risks: `REQUIRED_KINDS`, `ARTEFACT_KINDS` and `expected-red.yaml` are edited by every sibling:
rebase after any sibling merge and touch only our lines.

### WP06 — Decision record and living docs

**Prompt**: [tasks/WP06-decision-record-and-living-docs.md](tasks/WP06-decision-record-and-living-docs.md)
**Goal**: ADR-0040, ADR-0036 revised, logical model, glossary and changelog say what the code does. **Priority**: P2.

T028 ADR-0040 and index (WP06)
T029 ADR-0036 revised (WP06)
T030 Logical model and glossary (WP06)
T031 Changelog, open-packs issue text, docs checks (WP06)

Dependencies: WP03.

## Parallel opportunities

- WP01 ∥ WP02; WP04 ∥ WP05 ∥ WP06 after WP03.

## MVP

WP01–WP03 deliver the round trip; WP04 and WP05 prove it; WP06 records it.
