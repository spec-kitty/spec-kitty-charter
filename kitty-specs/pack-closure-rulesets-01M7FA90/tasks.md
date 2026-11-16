# Tasks: ADR-0075 slice 5, the PACK and CLOSURE rulesets and one pipeline

**Mission**: `pack-closure-rulesets-01M7FA90` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)
**Branch**: `kitty/mission-pack-closure-rulesets-01M7FA90` → `slice-5-554-pack-closure-rulesets` → `main`, as one pull
request (R4).

Ten work packages in one lane, in dependency order:

- Each package leaves `./gradlew verifyAll --max-workers=1` green.
- Only WP09 changes who judges a pack.
- Implementation profile: `java-jenny`.
- Subtask completion is recorded with `spec-kitty agent tasks mark-status`, never by ticking.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Golden harness: validate, registration, compose-read and diagnostics answers, recorded on today's code | WP01 | |
| T002 | Move `planted/` into the `:packs` testFixtures; repoint its readers and `core_verdicts.py` | WP01 | [P] |
| T003 | Golden files, and a test that compares the current answers to them, with a regeneration switch | WP01 | |
| T004 | The R2 code table: one row per fault, filled from the golden snapshot | WP01 | |
| T005 | Golden snapshot of the offering's composition refusals, for multi-fault sets | WP01 | [P] |
| T006 | `WholePackStore`: an unmodelled file joins the files that are not held (`notHeld()` replaces `unmodelled()`) | WP02 | |
| T007 | The callers of `unmodelled()`: #578's harness, the drift test, the agreement test and the example-pack tests | WP02 | |
| T008 | Rewrite the `unmodelled` scenarios, and fix the titles and comments | WP02 | [P] |
| T009 | Contract records: the `### Breaking` fragment, the 2026-11-11 fragment, `DiagnosticCode.yaml`, `packs_packId.yaml` | WP02 | [P] |
| T010 | Packs contract wording (`file-kinds.yaml`, `README.md`, `classifier-cases.yaml`, the packs CHANGELOG) | WP02 | [P] |
| T011 | Open-packs draft `open-packs#TBD-unmodelled-not-held`; update the golden snapshot for Q1 | WP02 | |
| T012 | `rules/GraphBinding`: the graph build (`registeredGraph`, `build`, `joinsItself`, `TENSION`, endpoint constants) | WP03 | |
| T013 | `rules/Paths`: `bodyPathEscape` and `assetPathEscape`, as plain functions | WP03 | [P] |
| T014 | `rules/DescriptorRules`: descriptor well-formedness as one function, for the offering and the resolver | WP03 | [P] |
| T015 | `rules/Wording` and `rules/Precedence`: the detail vocabulary and `PRECEDENCE`, with their whole tests | WP03 | |
| T016 | Repoint the roughly 20 callers outside the rules, leaving `PackPolicy` delegating | WP03 | |
| T017 | `PackCodes`: one `ReasonCode` per fault, with token, severity and wording | WP04 | |
| T018 | `PackFinding.code` and `PackElementViolation.kind` become `ReasonCode`; the `invalid` fallback goes | WP04 | |
| T019 | Apply the R2 table on the registration surface (catch-alls become specific codes) | WP04 | |
| T020 | Apply the R2 table on the validate surface (adopt registration's specific tokens) | WP04 | |
| T021 | Two `### Breaking` fragments, `CharterRefusalCode.yaml`'s `validation_failed` text, the golden diff list | WP04 | [P] |
| T022 | `planted/*/expected.json` to the new tokens and severities; `core_verdicts.py`; the token-grammar test | WP04 | [P] |
| T023 | `PackAssembly` (Spring-free, not a bean) carved from `DomainRepositoryService.assembly` and `judged()` | WP05 | |
| T024 | `PackManifestDerivation`: the derived set, shared by the check and the stamp | WP05 | |
| T025 | `PackContents` gains the A-M1 facts; `FileSystemPackInspector` fills them | WP05 | |
| T026 | The registration path follows no symlinks; a root that is not a directory answers today's registration shape | WP05 | |
| T027 | Composition-facts builder (`PackUnderResolution` from `PackContents`) and its parity test | WP05 | |
| T028 | Agreement tests: the A-M1 checklist fixtures; `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` stays green | WP05 | |
| T029 | `rules/LevelRules` and `rules/OrgFragmentRules` | WP06 | |
| T030 | `rules/DescriptorRules` as a `Rule` over the descriptor | WP06 | |
| T031 | `rules/StepRules` and `rules/ArtifactRules` (duplicate ids) | WP06 | [P] |
| T032 | `rules/AssetRules` (escape, mime) over one sidecar fact | WP06 | [P] |
| T033 | `rules/MissionTypeRules` (slice F's step and substep rules) and `rules/GlossaryRules` (#106) | WP06 | [P] |
| T034 | Per-rule tests, each with a failing and a passing case, from the planted packs | WP06 | |
| T035 | `rules/ReadRules`: schema, parse, retired, domain, reference, companion, from `PackContents` facts | WP07 | |
| T036 | `rules/GraphRules`: drg schema and endpoint, through slice F's bridge | WP07 | |
| T037 | `rules/FragmentRules` (step enhancements, orphans) and `rules/PromptSlotRules` | WP07 | |
| T038 | `rules/ManifestRules`, `rules/ReservedShardRules` and `rules/NotHeldRules` | WP07 | |
| T039 | `PackRulesets.pack(Surface)`, with the R1 profile table and the manifest gate as a plain sequence | WP07 | |
| T040 | `PackVerdicts` and `PackRefusals`, with the order functions and the diagnostics order | WP07 | |
| T041 | `PACK` composition tests (M-7) and mapper tests, checked against the golden snapshot | WP07 | |
| T042 | Resolver checks collect every fault (`List<Refusal>`), instead of throwing the first | WP08 | |
| T043 | `rules/CompositionRules`: `CLOSURE` over `List<PackUnderResolution>` | WP08 | |
| T044 | `OfferingReadService` takes the first refusal in today's interleaved order, with its pack list | WP08 | |
| T045 | Classify `refuseUnresolvedDelegations`, `checkStepEnhancementTargets` and `refuseCaseOnlyMatches` (#556) | WP08 | |
| T046 | `CLOSURE` tests, plus multi-fault fixtures against the offering's golden snapshot | WP08 | |
| T047 | Validate mode: `PackValidation` runs `PACK(VALIDATE)` and `PackVerdicts` | WP09 | |
| T048 | Registration: `PackRegistrationService` inspects, judges and refuses, then calls `loadStaged`; the guard runs before the gate and the impact check | WP09 | |
| T049 | `loadStaged` reads only and returns the reader's faults; `PackSourceStagers` calls through registration | WP09 | |
| T050 | Compose read: `DomainRepositoryService` runs `PACK(COMPOSE_READ)` on its own read; the withholding stays | WP09 | |
| T051 | Delete `PackPolicy`, `PackFinding`, `PackFindingCode` and the converters; migrate their tests | WP09 | |
| T052 | ArchUnit: rules A to C re-pointed; `PACK_CODES_HAVE_ONE_OWNER`; `PACK_RULES_READ_NO_FILE`; bite tests | WP09 | |
| T053 | The two subset tests, and the fail-closed guard test | WP09 | |
| T054 | Collapse the old test classes into per-rule, composition and mapper tests | WP10 | |
| T055 | The code-coverage test: every `PackCodes` constant has a failing and a passing fixture | WP10 | |
| T056 | Narrowed PIT and the nightly measurement; margins re-set in the build files and ADR-0062 | WP10 | |
| T057 | ADR-0075 amendment (R3), ADR-0065 annotation, docs | WP10 | [P] |
| T058 | The golden snapshot's final diff list, checked; the open-packs drafts complete | WP10 | |

## Phase 1: Freeze and Q1

### WP01: The golden snapshot and the R2 code table

- **Prompt**: [tasks/WP01-golden-snapshot-and-code-table.md](tasks/WP01-golden-snapshot-and-code-table.md)
- **Goal**: record what every surface answers today, and decide each fault's one code (R2), before anything changes.
- **Independent test**: the golden comparison test passes on today's code, and fails when a detail string is altered.
- **Subtasks**: T001, T002, T003, T004, T005.
- **Dependencies**: none.
- **Risks**: it must be taken before any behaviour changes.

### WP02: Q1, an unmodelled file is not held

- **Prompt**: [tasks/WP02-unmodelled-file-not-held.md](tasks/WP02-unmodelled-file-not-held.md)
- **Goal**: `notes.txt` is accepted with a `file_not_held` warning and is not stored, on today's pipeline.
- **Independent test**: the rewritten registration scenarios pass, and the golden diff lists only Q1 rows.
- **Subtasks**: T006, T007, T008, T009, T010, T011.
- **Dependencies**: WP01.

## Phase 2: Foundations

### WP03: PackPolicy's residue as packs-domain functions

- **Prompt**: [tasks/WP03-policy-residue-functions.md](tasks/WP03-policy-residue-functions.md)
- **Goal**: the members that roughly 20 classes use move into named functions, so that `PackPolicy` can later be
  deleted.
- **Subtasks**: T012, T013, T014, T015, T016.
- **Dependencies**: WP01. It is behaviour-preserving, and the golden snapshot is unchanged.

### WP04: PackCodes and the unified codes (R2)

- **Prompt**: [tasks/WP04-pack-codes-unified.md](tasks/WP04-pack-codes-unified.md)
- **Goal**: one typed code table, which every surface speaks.
- **Subtasks**: T017, T018, T019, T020, T021, T022.
- **Dependencies**: WP02, WP03.
- **Risks**: this is a wire change. Every golden diff row must cite an R2 row.

### WP05: Inspector facts, the Spring-free assembly and composition facts

- **Prompt**: [tasks/WP05-inspector-facts-and-assembly.md](tasks/WP05-inspector-facts-and-assembly.md)
- **Goal**: `PackContents` holds every fact a rule needs (P1), and composition facts are built without Spring (F5).
- **Subtasks**: T023, T024, T025, T026, T027, T028.
- **Dependencies**: WP04.

## Phase 3: The rules

### WP06: Per-pack rules, part 1 (level, descriptor, ids, assets, mission types, glossary)

- **Prompt**: [tasks/WP06-rules-descriptor-ids-assets.md](tasks/WP06-rules-descriptor-ids-assets.md)
- **Subtasks**: T029, T030, T031, T032, T033, T034.
- **Dependencies**: WP05.

### WP07: Per-pack rules, part 2, `PACK` and the mappers

- **Prompt**: [tasks/WP07-rules-graph-fragments-pack-mappers.md](tasks/WP07-rules-graph-fragments-pack-mappers.md)
- **Subtasks**: T035, T036, T037, T038, T039, T040, T041.
- **Dependencies**: WP06.

### WP08: CLOSURE

- **Prompt**: [tasks/WP08-closure.md](tasks/WP08-closure.md)
- **Subtasks**: T042, T043, T044, T045, T046.
- **Dependencies**: WP05. It can run beside WP06 and WP07, but stays in the same lane.

## Phase 4: The switch and the closeout

### WP09: One pipeline: the switch, the deletions and ArchUnit (atomic)

- **Prompt**: [tasks/WP09-one-pipeline-switch.md](tasks/WP09-one-pipeline-switch.md)
- **Subtasks**: T047, T048, T049, T050, T051, T052, T053.
- **Dependencies**: WP07, WP08.

### WP10: Tests, floors and records

- **Prompt**: [tasks/WP10-tests-floors-records.md](tasks/WP10-tests-floors-records.md)
- **Subtasks**: T054, T055, T056, T057, T058.
- **Dependencies**: WP09.

## Dependency graph

```text
WP01 ─┬─ WP02 ─┐
      └─ WP03 ─┴─ WP04 ── WP05 ─┬─ WP06 ── WP07 ─┐
                                └─ WP08 ─────────┴─ WP09 ── WP10
```

## MVP

WP01 and WP02. They freeze the wire and deliver the operator's Q1 ruling on today's pipeline.
