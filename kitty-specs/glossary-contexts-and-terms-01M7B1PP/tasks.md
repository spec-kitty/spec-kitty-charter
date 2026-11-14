# Tasks: R12 glossary contexts and terms

**Mission**: `glossary-contexts-and-terms-01M7B1PP` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)
**Planning branch and merge target**: `feat/106-glossary-contexts` (then `main` by pull request). Mission branch:
`kitty/mission-glossary-contexts-and-terms-01M7B1PP`.

Each work package's prompt is in `tasks/`. Subtask rows below are references. Completion is recorded with
`spec-kitty agent tasks mark-status <Txxx> --status done`, never by editing this file.

## Subtask Index

| ID | Description | WP | Parallel |
| --- | --- | --- | --- |
| T001 | `NodeKind` declares token, artifact kind, plural and graph flags per constant | WP01 | |
| T002 | Split `authoredInGraphs()` into node-declarable and edge-target; update the parity tests | WP01 | |
| T003 | Map a URN's kind to an artifact kind through `NodeKind.artifactKind()` in profiles-edge and `EffectiveBodies` | WP01 | [P] |
| T004 | Pin the table: a `NodeKindTest` row per constant, no behaviour change | WP01 | |
| T005 | `glossary.schema.json` replaces `glossary-pack.schema.json`; every packs schema and `file-kinds.yaml` follow | WP02 | |
| T006 | Kernel: `ArtifactKind.GLOSSARY`, `NodeKind.GLOSSARY_CONTEXT` and `TERM`, `Relation.VOCABULARY` targets | WP02 | |
| T007 | Domain: `GlossaryContext`, `GlossaryTerm`, identifiers, `TermStatus`, `AppliesTo`; `GlossaryPack*` deleted | WP02 | |
| T008 | Storage: binding, DSO, YAML codec, `KindBindings`, `YamlFormats`, presets (`activated_glossaries`) | WP02 | |
| T009 | Charter and offering contracts: kind enums, selection objects, `Preset`, `ArtifactKindToken`, `Artifact` member, `ActivationConfig` and corpus | WP02 | |
| T010 | Move the example-pack, digest-baseline and preset fixtures to the new shape, with minted term ids | WP02 | |
| T011 | Update every test that names the old kind; narrow `ProjectCharterYamlSchemaTest` | WP02 | |
| T012 | `scripts/usability/charter-to-activation.py` key mapping | WP02 | [P] |
| T013 | Change fragment, `### Breaking` | WP02 | |
| T014 | NFR-002 sweep against the occurrence map | WP02 | |
| T015 | Planted refusals: old shape, bad context id, bad term id, duplicate term id, bad `see_also` | WP03 | |
| T016 | `IdentifierBoundsTest` rows for `GlossaryContextIdentifier` and `TermIdentifier` | WP03 | [P] |
| T017 | Stored activation and write-body refusals of `glossary_pack`; the invalid corpus case | WP03 | [P] |
| T018 | Whole-pack round trip models `glossaries/*.glossary.yaml` (#547 check); broken `see_also` and `term:` targets | WP03 | |
| T019 | Contract: three pack paths, `ContextId`/`TermId` parameters, `GlossaryContextList`, `GlossaryContextSummary`, `GlossaryContext`, `GlossaryTerm`, examples | WP04 | |
| T020 | `ArtifactReadService` sets and `ForeignArtifactRead` for `glossary`; `?kind=glossary` | WP04 | |
| T021 | `:artifacts-edge`: `GlossaryReads`, `PacksGlossaries`, `GlossaryBodies`, `GlossariesController` | WP04 | |
| T022 | Refusals in order, with segment-naming details | WP04 | |
| T023 | `glossary.feature`, registry rows (owned here), routing test | WP04 | |
| T024 | Every example term readable (SC-001): a test iterates the 139 ids | WP04 | |
| T025 | Contract: three twin paths, `EffectiveGlossaryContextRead`, `EffectiveGlossaryTermRead`, examples | WP05 | |
| T026 | `EffectiveReadService` route and held winners; `EffectiveTag.Read` values and guards; term query | WP05 | |
| T027 | `:artifacts-edge`: `EffectiveGlossaryReads`, `EffectiveGlossariesController`, bodies | WP05 | |
| T028 | Activation link `glossaries`; context item `term` templated link; relation table | WP05 | |
| T029 | `effective-glossary.feature`: winner, states, 304, refusals, sibling conflict | WP05 | |
| T030 | Port `ActivationArtifacts.contextsInForce` and its `ActivationsArtifacts`/`EffectiveReadService` implementation | WP06 | |
| T031 | `PacksGlossaries` reads each winning pack once against the closure fingerprint | WP06 | |
| T032 | `VocabularyReads`: the active terms, ordered, with whole-context override | WP06 | |
| T033 | Pure projections: Contextive document and taboo list | WP06 | [P] |
| T034 | `EffectiveTag.Read` values for the three reads; the pack-read count test (NFR-005) | WP06 | |
| T035 | Contract: tag `Vocabulary`, three paths, `ActiveTermList`, `ActiveTerm`, `TabooList`, the Contextive example | WP07 | |
| T036 | `VocabularyController`: JSON, `application/yaml` via `snakeyaml-engine`, 406, ETag, 304 | WP07 | |
| T037 | Activation links `vocabulary-terms`, `vocabulary-contextive`, `vocabulary-taboos`; relation table | WP07 | |
| T038 | `vocabulary.feature`: whole override with positive control, shared name, empty, 304, 406, field set, cross-read consistency | WP07 | |
| T039 | REST conventions: the media-type exception line | WP07 | [P] |
| T040 | `docs/api/service-contract.md`: #106's decision, shape, scenarios and corpus cases | WP08 | |
| T041 | Divergence register (page and YAML): DIV-012, DIV-106, DIV-211, the lenient-conversions entry | WP08 | [P] |
| T042 | Plans: work breakdown item 4, next-slices, roadmap | WP08 | [P] |
| T043 | Research: `term-id-map.md` and the open-packs draft issue | WP08 | [P] |
| T044 | Closeout notes: issues to close and tick, the #518 comment text, the PR body notes | WP08 | |

## Phase 1: The model

### WP01: The explicit `NodeKind` table (behaviour-preserving)

**Prompt**: [tasks/WP01-nodekind-explicit-table.md](tasks/WP01-nodekind-explicit-table.md)

- **Goal**: `NodeKind` declares its facts per constant, and every URN-to-kind use maps through it. The behaviour does
  not change, and the build stays green (research R1).
- **Priority**: P1, the foundation.
- **Independent test**: `:kernel:test`, `:packs:test`, `:profiles-edge:test` and `:artifacts-edge:test` are green,
  and the table test pins every constant.
- **Subtasks**:
  - T001 `NodeKind` declares token, artifact kind, plural and graph flags per constant (WP01)
  - T002 Split `authoredInGraphs()` into node-declarable and edge-target; update the parity tests (WP01)
  - T003 Map a URN's kind to an artifact kind through `NodeKind.artifactKind()` (WP01)
  - T004 Pin the table (WP01)
- **Dependencies**: none.
- **Risks**: slice F (#537) touches `NodeKind`, so rebase first. Keep the constant order.

### WP02: The glossary kind replaces `glossary_pack` (atomic)

**Prompt**: [tasks/WP02-glossary-kind-replaces-glossary-pack.md](tasks/WP02-glossary-kind-replaces-glossary-pack.md)

- **Goal**: replace the kind, its shape, its file family and every contract spelling in one change that compiles and
  passes (FR-001 to FR-006, FR-020, NFR-002).
- **Priority**: P1.
- **Independent test**: `./gradlew verifyAll` is green. A `git grep` for the old spellings returns only the
  occurrence-map exceptions.
- **Subtasks**: T005 to T014 (WP02).
- **Dependencies**: WP01.
- **Risks**:
  - The largest WP. Its parts do not compile apart (squad, Java lens H4).
  - Digest re-records must be named.
  - PR #568 touches `file-kinds.yaml`.

### WP03: Model refusals and round-trip proofs

**Prompt**: [tasks/WP03-model-refusals-and-round-trip.md](tasks/WP03-model-refusals-and-round-trip.md)

- **Goal**: prove the invariants and the "no legacy" rule with refusal cases (FR-003, FR-005, edge cases), and that
  the round trip models the new file.
- **Subtasks**: T015 to T018 (WP03).
- **Dependencies**: WP02.

## Phase 2: The reads

### WP04: Glossary reads in a pack

**Prompt**: [tasks/WP04-glossary-reads-in-a-pack.md](tasks/WP04-glossary-reads-in-a-pack.md)

- **Goal**: the three pack reads and `?kind=glossary` (FR-007 to FR-010, FR-012, FR-019, SC-001). This WP owns the
  shared contract registries.
- **Subtasks**: T019 to T024 (WP04).
- **Dependencies**: WP02.

### WP05: Glossary reads under an activation

**Prompt**: [tasks/WP05-glossary-activation-twins.md](tasks/WP05-glossary-activation-twins.md)

- **Goal**: the three twins, the `glossaries` link and the `term` link (FR-011 to FR-013, FR-019).
- **Subtasks**: T025 to T029 (WP05).
- **Dependencies**: WP04.

### WP06: The activation's vocabulary: read path and projections

**Prompt**: [tasks/WP06-vocabulary-read-path-and-projections.md](tasks/WP06-vocabulary-read-path-and-projections.md)

- **Goal**: the service side of ADR-0076: the contexts in force, the active terms, and the two projections as pure
  functions, with the pack-read count (FR-014 to FR-017, NFR-005).
- **Subtasks**: T030 to T034 (WP06).
- **Dependencies**: WP05.

### WP07: The activation's vocabulary: contract and endpoints

**Prompt**: [tasks/WP07-vocabulary-contract-and-endpoints.md](tasks/WP07-vocabulary-contract-and-endpoints.md)

- **Goal**: the three operations, the YAML answer and the links, proven by `vocabulary.feature` (FR-013, FR-014,
  FR-016, FR-017, FR-019, SC-004).
- **Subtasks**: T035 to T039 (WP07).
- **Dependencies**: WP06.

## Phase 3: Records

### WP08: Records, docs and closeout material

**Prompt**: [tasks/WP08-records-docs-and-closeout.md](tasks/WP08-records-docs-and-closeout.md)

- **Goal**: the records say what the service does, and the closeout material is ready (FR-021, FR-022, SC-005).
- **Subtasks**: T040 to T044 (WP08).
- **Dependencies**: WP03, WP07.

## Dependency graph

```text
WP01 → WP02 → WP03 ─────────────────────────┐
              └→ WP04 → WP05 → WP06 → WP07 ─┴→ WP08
```

WP03 and WP04 can run in parallel after WP02.

## MVP

WP01 to WP04 deliver #106's core: the canonical model and the pack reads. WP05 closes #536's activation clause.
WP06 and WP07 deliver ADR-0076.
