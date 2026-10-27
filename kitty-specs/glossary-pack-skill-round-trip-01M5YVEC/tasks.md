# Tasks: Glossary pack and skill round trip

**Mission**: `glossary-pack-skill-round-trip-01M5YVEC` · **Issue**: #192 · **Planning base / merge target**:
`feat/192-glossary-pack-skill-round-trip` · **Plan**: [plan.md](plan.md) · **Spec**: [spec.md](spec.md)

Subtask completion is event-sourced: record it with `spec-kitty agent tasks mark-status Txxx --status done`. The
rows below are references, not checkboxes.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `CompanionContent` (sealed, `Text`) and the `Companion` record; `KindBinding` hooks and javadoc | WP01 | |
| T002 | Companion read in `FilesystemPackStore` (`readKind`, `readOne`): path rule, containment, text, duplicates | WP01 | |
| T003 | Companion upsert in `FilesystemPackStore.write`: new/replace, orphan removal, refusals | WP01 | |
| T004 | Fixture-binding tests of read, upsert and every refusal | WP01 | |
| T005 | `glossary-pack.schema.json` (authored, closed) | WP02 | [P] |
| T006 | `skill.schema.json` (imported from core, closed) | WP02 | [P] |
| T007 | `file-kinds.yaml` rules; pinned counts 14 → 16; `PackFileKindTest` samples; README; `PackSchemas` javadoc | WP02 | |
| T008 | `SchemaKeywordCoverageTest` fallback for `not`, `maxLength`; schema tests (refusal at each level, form rule) | WP02 | |
| T009 | `GlossaryPack`, `GlossaryPackIdentifier`, `GlossaryTerm` with invariants | WP03 | |
| T010 | `GlossaryPackDso`, `GlossaryPackYaml` (number kept a number) | WP03 | |
| T011 | `GlossaryPackBinding` (`glossary-pack` schema and file token); append to `KindBindings`, `YamlFormats` | WP03 | |
| T012 | Glossary entity and format tests | WP03 | |
| T013 | `Skill`, `SkillIdentifier`, `SkillForm`, `SkillParameter`, `SkillInvocation`, `SkillExpansion` | WP04 | |
| T014 | `SkillDso`, `SkillYaml` (booleans kept, nothing forced) | WP04 | |
| T015 | `SkillBinding` with companions; append to `KindBindings`, `YamlFormats` | WP04 | |
| T016 | Skill entity and format tests (prompt and wrapper forms, store read of a pair) | WP04 | |
| T017 | `WholePackStore`: companions written and modelled | WP05 | |
| T018 | `PackFileSet.digested`: companions digested (text, deduped, path rule) | WP05 | |
| T019 | Tests: whole-pack pair round trip, unmodelled list, fingerprint moves with a body, unchanged digests | WP05 | |
| T020 | `empty_kinds` in the gate expectation, ratcheted, with positive controls | WP06 | |
| T021 | `expected-red.yaml`: drop `other:glossary_packs`, add `empty_kinds: skill` | WP06 | |
| T022 | Corpus test over every pinned glossary pack and skill pair, with a lossy-writer control | WP06 | |
| T023 | Run the gate and the seam test; record evidence | WP06 | |
| T024 | ADR-0040 and its index row | WP07 | [P] |
| T025 | Changelog entry and index line | WP07 | [P] |
| T026 | Cross-check docs against the shipped code | WP07 | |

## Phase 1 — Foundations

### WP01 — Companion-file seam ([tasks/WP01-companion-file-seam.md](tasks/WP01-companion-file-seam.md))

**Goal**: a binding can declare text files read and written with its artifact; the store reads, contains and
upserts them. Priority P1. **Independent test**: a fixture binding over a stub strategy round-trips an artifact
and its companion through `FilesystemPackStore`, and every refusal fires.

T001 `CompanionContent` and `Companion`; `KindBinding` hooks (WP01)
T002 Companion read in `FilesystemPackStore` (WP01)
T003 Companion upsert in `FilesystemPackStore.write` (WP01)
T004 Fixture-binding tests (WP01)

Dependencies: none. Risks: the commit path's directory swap is what makes body+YAML atomic; a live direct save is
not supported for companion kinds (documented, not built).

### WP02 — Vocabulary and schemas ([tasks/WP02-vocabulary-and-schemas.md](tasks/WP02-vocabulary-and-schemas.md))

**Goal**: both kinds' files map to closed schemas; the vocabulary tests count 16. Priority P1. **Independent
test**: every pinned glossary pack and skill validates; a planted unknown key at each level is refused.

T005 `glossary-pack.schema.json` (WP02)
T006 `skill.schema.json` (WP02)
T007 `file-kinds.yaml`, counts, samples, README, javadoc (WP02)
T008 Keyword coverage and schema tests (WP02)

Dependencies: none. Parallel with WP01.

## Phase 2 — The kinds

### WP03 — Glossary pack kind ([tasks/WP03-glossary-pack-kind.md](tasks/WP03-glossary-pack-kind.md))

**Goal**: glossary packs load into and write from the aggregate. Priority P1 (US1, US3).

T009 Entity and values (WP03)
T010 DSO and YAML format (WP03)
T011 Binding and registration (WP03)
T012 Tests (WP03)

Dependencies: WP02.

### WP04 — Skill kind ([tasks/WP04-skill-kind.md](tasks/WP04-skill-kind.md))

**Goal**: skills load with their body and write as a pair through the store. Priority P1 (US2).

T013 Entity and values (WP04)
T014 DSO and YAML format (WP04)
T015 Binding with companions and registration (WP04)
T016 Tests (WP04)

Dependencies: WP01, WP02, WP03 (WP03 owns the two registry files; WP04 appends one line to each, recorded).

### WP05 — Whole pack and fingerprint ([tasks/WP05-whole-pack-and-fingerprint.md](tasks/WP05-whole-pack-and-fingerprint.md))

**Goal**: the whole-pack write and the fingerprint carry companions. Priority P1 (US2 scenarios 3-4).

T017 `WholePackStore` (WP05)
T018 `PackFileSet.digested` (WP05)
T019 Tests (WP05)

Dependencies: WP04.

## Phase 3 — Evidence and record

### WP06 — Gate and corpus ([tasks/WP06-gate-and-corpus.md](tasks/WP06-gate-and-corpus.md))

**Goal**: the gate shows the built-in glossary pack green and accounts for the empty skill kind; every pinned
file of both kinds round-trips. Priority P1 (US1, US4).

T020 `empty_kinds` ratchet (WP06)
T021 `expected-red.yaml` (WP06)
T022 Corpus test (WP06)
T023 Gate and seam runs (WP06)

Dependencies: WP03, WP05.

### WP07 — ADR-0040 and changelog ([tasks/WP07-adr-and-changelog.md](tasks/WP07-adr-and-changelog.md))

**Goal**: the companion seam and the two kinds' equality rows are recorded. Priority P2.

T024 ADR-0040 and index (WP07)
T025 Changelog (WP07)
T026 Docs cross-check (WP07)

Dependencies: WP01 (the seam it records). Parallel with WP02 to WP06; T026 runs last.
