# Tasks: Slice B, real packs register as they are

**Mission**: `slice-b-real-packs-register-01M7649C` · **Branch**: `feat/466-slice-b-real-packs-register` (planning base and
merge target) · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Squad**: [research/squad-plan-point.md](research/squad-plan-point.md)

Gradle runs one at a time in this session (`--max-workers=1`, foreground), so work packages run in sequence even where
their files would allow parallel lanes. Each work package lands green on its own: its commits pass `./gradlew
--max-workers=1 :packs:test` and the gates it touches.

## Subtask Index

| ID | Description | WP | Parallel |
|---|---|---|---|
| T001 | Generate the contract with a two-media-type 200 sketch and inspect the generated interface | WP01 | |
| T002 | Prove a controller pattern for negotiation, 304, 406 under ADR-0004 | WP01 | |
| T003 | Record the chosen pattern in research/doc-read-spike.md | WP01 | |
| T004 | `files` rule `*/README.md`; `retired` rules `*/pack.md`, `*/PROVENANCE.md`; #432 comment | WP02 | |
| T005 | `PackDocumentMarkdown.FILE` = `README.md`; schema description; digest tail | WP02 | |
| T006 | Case-variant refusal of root rule files (FR-022) | WP02 | |
| T007 | Example packs: `README.md` with `## Provenance` | WP02 | [P] |
| T008 | Retired fixtures and tests for `pack.md`, `PROVENANCE.md` | WP02 | |
| T009 | Retarget the tests that name `pack.md`; recapture the digest pin | WP02 | |
| T010 | `ignored` section and classification; companion claims win | WP03 | |
| T011 | Load and registration: excluded files listed, not refused, stripped before commit | WP03 | |
| T012 | `file_not_held` warning diagnostic in registration answers | WP03 | |
| T013 | Gate census: checked EXCLUDED status | WP03 | |
| T014 | Validate mode and the agreement test across components | WP03 | |
| T015 | Registration scenarios: excluded, retired, unmodelled control | WP03 | |
| T016 | Example ignored note and table-excluded file; flipped tests | WP03 | |
| T017 | Retire `org-charter.yaml`: rule, schema, page, finding code, `codeFor` | WP04 | |
| T018 | Remove `active_charter` from the descriptor stack | WP04 | |
| T019 | Fixtures, example `project` pack, planted case, scenario Backgrounds | WP04 | |
| T020 | Agreement test: classification, validate, load, registration on `org-charter.yaml` | WP04 | |
| T021 | Retarget org-charter tests to the retired refusal | WP04 | |
| T022 | Contract: `/packs/{packId}/doc`, `PackDocumentRead`, code, relation, examples, fragment | WP05 | |
| T023 | Domain port and storage implementation | WP05 | |
| T024 | Application query with revision in one consistent read | WP05 | |
| T025 | Controller: negotiation, ETag, Vary, 304, 406 | WP05 | |
| T026 | `doc` link on the pack resource | WP05 | |
| T027 | Contract scenarios for the doc read | WP05 | |
| T028 | MOVE_ASIDE phase hook; `SlowSwapCommitStores` on the real swap | WP06 | |
| T029 | `PackDirectories` resolver and identity-checked re-read | WP06 | |
| T030 | Route every lock-free directory resolution through it | WP06 | |
| T031 | Listing with `tryLock`; create window reads as absent | WP06 | |
| T032 | Deterministic swap-window tests T1 to T7 | WP06 | |
| T033 | Concurrency test stable; rewrite the lock-wait test | WP06 | |
| T034 | `ConsistentRead` accepts an unchanged read in one odd generation | WP07 | |
| T035 | Tests for the accepted and refused cases | WP07 | |
| T036 | New ADR for the mission's calls; dated notes on ADR-0042, 0036, 0031, 0065, 0019, 0040 | WP08 | |
| T037 | How-to: enforce presets on an org's projects, with the field mapping | WP08 | [P] |
| T038 | Architecture, context and schema pages; diagrams | WP08 | [P] |
| T039 | Glossary sources, divergence register, epic-186 tracker | WP08 | [P] |
| T040 | Usability script and README | WP08 | [P] |
| T041 | Codec dry run over open-packs `main` READMEs; classification dry run | WP09 | |
| T042 | Finalize the two open-packs drafts | WP09 | |

## Phase 1: Foundation

### WP01 - Spike: the doc read's contract shape

**Prompt**: [tasks/WP01-doc-read-contract-spike.md](tasks/WP01-doc-read-contract-spike.md) · **Priority**: P1 · **Depends on**: none

Goal: decide how one operation with two 200 media types is generated and served under ADR-0004, with 304 and 406.
Independent test: the decision note names a pattern a throwaway build compiled and a test exercised.

T001 Generate the contract with a two-media-type 200 sketch and inspect the generated interface (WP01)
T002 Prove a controller pattern for negotiation, 304, 406 under ADR-0004 (WP01)
T003 Record the chosen pattern in research/doc-read-spike.md (WP01)

Risks: an unchecked cast or bypassing the generated interface needs the operator.

### WP02 - The pack document is README.md; pack.md and PROVENANCE.md are retired

**Prompt**: [tasks/WP02-readme-is-the-pack-document.md](tasks/WP02-readme-is-the-pack-document.md) · **Priority**: P1 · **Depends on**: none

Goal: FR-001 to FR-003, FR-016, FR-019 (document), FR-022. Independent test: `:packs:test` and `:packs:wholePackRoundTrip`
green with `pack-document` GREEN on `README.md`.

T004 `files` rule `*/README.md`; `retired` rules `*/pack.md`, `*/PROVENANCE.md`; #432 comment (WP02)
T005 `PackDocumentMarkdown.FILE` = `README.md`; schema description; digest tail (WP02)
T006 Case-variant refusal of root rule files (FR-022) (WP02)
T007 Example packs: `README.md` with `## Provenance` (WP02)
T008 Retired fixtures and tests for `pack.md`, `PROVENANCE.md` (WP02)
T009 Retarget the tests that name `pack.md`; recapture the digest pin (WP02)

## Phase 2: Registration accepts real packs

### WP03 - Excluded files are reported, stripped and judged

**Prompt**: [tasks/WP03-excluded-files-reported-and-stripped.md](tasks/WP03-excluded-files-reported-and-stripped.md) · **Priority**: P1 · **Depends on**: WP02

Goal: FR-004, FR-005, FR-021. Independent test: a pack with `docs/notes.md` and `missions/x/templates/README.md`
registers through `POST /packs`, answers two `file_not_held` warnings, and the live directory holds neither.

T010 `ignored` section and classification; companion claims win (WP03)
T011 Load and registration: excluded files listed, not refused, stripped before commit (WP03)
T012 `file_not_held` warning diagnostic in registration answers (WP03)
T013 Gate census: checked EXCLUDED status (WP03)
T014 Validate mode and the agreement test across components (WP03)
T015 Registration scenarios: excluded, retired, unmodelled control (WP03)
T016 Example ignored note and table-excluded file; flipped tests (WP03)

### WP04 - org-charter.yaml and active_charter are retired

**Prompt**: [tasks/WP04-retire-org-charter.md](tasks/WP04-retire-org-charter.md) · **Priority**: P1 · **Depends on**: WP03

Goal: FR-009 to FR-011, FR-019 (descriptor). Independent test: the agreement test and the scenario suite green.

T017 Retire `org-charter.yaml`: rule, schema, page, finding code, `codeFor` (WP04)
T018 Remove `active_charter` from the descriptor stack (WP04)
T019 Fixtures, example `project` pack, planted case, scenario Backgrounds (WP04)
T020 Agreement test: classification, validate, load, registration on `org-charter.yaml` (WP04)
T021 Retarget org-charter tests to the retired refusal (WP04)

## Phase 3: The doc read

### WP05 - GET /packs/{packId}/doc and the doc link

**Prompt**: [tasks/WP05-pack-document-read.md](tasks/WP05-pack-document-read.md) · **Priority**: P1 · **Depends on**: WP01, WP04

Goal: FR-006 to FR-008, FR-018 (Added), FR-024. Independent test: contract scenarios for every acceptance scenario of
User Story 2.

T022 Contract: `/packs/{packId}/doc`, `PackDocumentRead`, code, relation, examples, fragment (WP05)
T023 Domain port and storage implementation (WP05)
T024 Application query with revision in one consistent read (WP05)
T025 Controller: negotiation, ETag, Vary, 304, 406 (WP05)
T026 `doc` link on the pack resource (WP05)
T027 Contract scenarios for the doc read (WP05)

## Phase 4: The swap window

### WP06 - Readers ride out the swap window

**Prompt**: [tasks/WP06-swap-window-readers.md](tasks/WP06-swap-window-readers.md) · **Priority**: P2 · **Depends on**: WP05

Goal: FR-013 to FR-015, NFR-001, NFR-002. Independent test: deterministic phase-hook tests; the concurrency test 20
times.

T028 MOVE_ASIDE phase hook; `SlowSwapCommitStores` on the real swap (WP06)
T029 `PackDirectories` resolver and identity-checked re-read (WP06)
T030 Route every lock-free directory resolution through it (WP06)
T031 Listing with `tryLock`; create window reads as absent (WP06)
T032 Deterministic swap-window tests T1 to T7 (WP06)
T033 Concurrency test stable; rewrite the lock-wait test (WP06)

### WP07 - Verified reads accept an unchanged read during a swap

**Prompt**: [tasks/WP07-consistent-read-during-swap.md](tasks/WP07-consistent-read-during-swap.md) · **Priority**: P2 · **Depends on**: WP06

Goal: FR-023 (a mission call, separable). Independent test: a verified read during a paused swap answers the old state.

T034 `ConsistentRead` accepts an unchanged read in one odd generation (WP07)
T035 Tests for the accepted and refused cases (WP07)

## Phase 5: The record

### WP08 - ADRs, how-to, architecture, glossary, usability script

**Prompt**: [tasks/WP08-record-and-docs.md](tasks/WP08-record-and-docs.md) · **Priority**: P1 · **Depends on**: WP07

Goal: FR-012, FR-017, FR-018 (record). Independent test: `check_docs.py all`, markdownlint-cli2, cspell, `verifyAll`.

T036 New ADR for the mission's calls; dated notes on ADR-0042, 0036, 0031, 0065, 0019, 0040 (WP08)
T037 How-to: enforce presets on an org's projects, with the field mapping (WP08)
T038 Architecture, context and schema pages; diagrams (WP08)
T039 Glossary sources, divergence register, epic-186 tracker (WP08)
T040 Usability script and README (WP08)

### WP09 - Open-packs drafts, proven against open-packs main

**Prompt**: [tasks/WP09-open-packs-drafts.md](tasks/WP09-open-packs-drafts.md) · **Priority**: P1 · **Depends on**: WP04

Goal: FR-020, SC-005 readiness. Independent test: the dry runs list no leftover refusal beyond what the drafts fix.

T041 Codec dry run over open-packs `main` READMEs; classification dry run (WP09)
T042 Finalize the two open-packs drafts (WP09)
