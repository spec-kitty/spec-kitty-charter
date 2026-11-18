# Tasks: ADR-0075 slice 5, the PACK and CLOSURE rulesets and one pipeline

**Mission**: `pack-closure-rulesets-01M7FA90` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)
**Branch**: `kitty/mission-pack-closure-rulesets-01M7FA90` → `slice-5-554-pack-closure-rulesets` → `main`, as one pull
request (R4).

Thirteen work packages in one lane, in dependency order (replanned on 2026-11-16 for the operator's S1 to S3 rulings,
and folded for S6, the domain port `PackReading`, [squad-shaping](research/squad-shaping.md); WP01 and WP02 are
done):

- Each package leaves `./gradlew verifyAll --max-workers=1` green.
- WP09 moves validate mode to the validation edge; WP12 moves registration and the compose read to the one read.
  Nothing else changes who judges a pack.
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
| T011 | Open-packs draft `open-packs#309`; update the golden snapshot for Q1 | WP02 | |
| T012 | `rules/GraphBinding`: the graph build (`registeredGraph`, `build`, `joinsItself`, `TENSION`, endpoint constants) | WP03 | |
| T013 | `rules/Paths`: `bodyPathEscape` and `assetPathEscape`, as plain functions | WP03 | [P] |
| T014 | `rules/DescriptorChecks`: descriptor well-formedness as one pure function, for the reader's precheck, the offering and the resolver | WP03 | [P] |
| T015 | `rules/Wording` and `rules/Precedence`: the detail vocabulary and `PRECEDENCE`, with their whole tests | WP03 | |
| T016 | Repoint the roughly 20 callers outside the rules, leaving `PackPolicy` delegating | WP03 | |
| T017 | `PackCodes` (domain tokens) and `StorageCodes` (storage tokens): each token built once | WP04 | |
| T018 | `PackFinding.code` and `PackElementViolation.kind` become `ReasonCode`; the `invalid` fallback goes | WP04 | |
| T019 | Apply the R2 table on the registration surface (catch-alls become specific codes) | WP04 | |
| T020 | Apply the R2 table on the validate surface (adopt registration's specific tokens) | WP04 | |
| T021 | Two `### Breaking` fragments, `CharterRefusalCode.yaml`'s `validation_failed` text, the golden diff list | WP04 | [P] |
| T022 | `planted/*/expected.json` to the new tokens and severities; `core_verdicts.py`; the token-grammar test | WP04 | [P] |
| T023 | `PackAssembly` (Spring-free, not a bean): builds the aggregate from the one read and judges nothing | WP05 | |
| T024 | `PackManifestDerivation`: the derived set, computed by the reader into the census and shared with the stamp; the membership rule reads the census only | WP05 | |
| T025 | The one reader (`readTree`, package-private): `PackContents` (storage-agnostic, the whole assembly input) and `TreeCensus` (one owner of "held"); the A-M1 facts; the domain port's types `PackReading`, `PackReadout`, `NotHeldFile`, `ClassifiedFile` (S6) | WP05 | |
| T026 | The registration path follows no symlinks; a root that is not a directory answers today's registration shape | WP05 | |
| T027 | One composition-facts builder over the one read; `DomainRepositoryService` delegates (P7) | WP05 | |
| T028 | The store's load consumes the one reader, and the compose read its store half (`StoreRead`, S7); `OneReadTest`; the A-M1 fixtures | WP05 | |
| T029 | `packvalidation.placement`: `LevelRules` and `OrgFragmentRules`, validate only (S2) | WP09 | |
| T030 | S2's format prechecks (descriptor version format, asset mime format) as read faults of the one reader; validate mode sees them, and registration from WP12 | WP05 | |
| T031 | `rules/StepRules` and `rules/ArtifactRules` (duplicate ids) | WP06 | [P] |
| T032 | `rules/AssetRules`: the escape, and the repeated asset id as a consistency rule (S2) | WP06 | [P] |
| T033 | `rules/MissionTypeRules` (slice F's step and substep rules) (#106's glossary checks stay edge checks, S2, WP06 D1) | WP06 | [P] |
| T034 | Per-rule tests, each with a failing and a passing case, from the planted packs | WP06 | |
| T035 | `content()`'s gates over the one read: the edges-read gate; no semantic rule on an unreadable file | WP07 | |
| T036 | `rules/GraphRules`: drg schema and endpoint, through slice F's bridge | WP07 | |
| T037 | `rules/FragmentRules` (step enhancements, orphans) and `rules/PromptSlotRules` | WP07 | |
| T038 | `infra.storage.rules`: `NotHeldRules`, `ReservedShardRules` and `ManifestRules` over `TreeRead` (S3), named by nothing outside storage (S6) | WP07 | |
| T039 | `PackRulesets.content()`, `StorageRulesets.holding()` and `registration()` (`holding().must(content().on(TreeRead::contents))`), the manifest gate as a plain function, `verdict()` in registration's wire order, and `FileSystemPackInspector` implementing `PackReading` (S6) | WP07 | |
| T040 | `PackRefusals`, the existing package-private class in `packs.application` (Q5), over the readout's `Rationale` in the order given, naming no storage code (S6) | WP07 | |
| T041 | Composition tests (M-7), `PackReadingTest` and `PackRefusals` tests, checked against the golden snapshot | WP07 | |
| T042 | Resolver checks collect every fault (`List<Refusal>`), instead of throwing the first | WP08 | |
| T043 | `rules/CompositionRules`: `CLOSURE` over `List<PackUnderResolution>` from the one builder | WP08 | |
| T044 | `OfferingReadService` takes the first refusal in today's interleaved order, with its pack list | WP08 | |
| T045 | Classify `refuseUnresolvedDelegations`, `checkStepEnhancementTargets` and `refuseCaseOnlyMatches` (#556) | WP08 | |
| T046 | `CLOSURE` tests, plus multi-fault fixtures against the offering's golden snapshot | WP08 | |
| T047 | `PackCheck` in `pack-validation` (moved from `PackValidation`): read through `PackReading`, then `rationale().and(placement().check(...))` (S6); `PackRulesets.closure()` delegate; the `PackInspector` port and its `Scope` deleted, their test callers repointed (N6, N7) | WP09 | |
| T048 | Registration on the one read: read once through `PackReading`, refuse through `PackRefusals` on the readout's `Rationale`, then assemble and stamp; no guard; no onion rule widened (S6) | WP12 | |
| T049 | `loadStaged(PackReadout)` assembles from `PackContents` and judges nothing; `removeNotHeld` takes the readout's `NotHeldFile`s; `ExcludedFile` goes; `PackSourceStagers` calls through registration | WP12 | |
| T050 | Compose read: `DomainRepositoryService` runs only the store half of the one reader (`StoreRead`, S7) and answers as today, with no S4 filter; a repeated artifact id answers `pack_unreadable` (S5, landed in WP04); the withholding stays | WP12 | |
| T051 | Delete `PackPolicy`, `PackFinding`, `PackFindingCode` and the converters; migrate their tests (the `PackInspector` port went in WP09, T047) | WP13 | |
| T052 | ArchUnit: rules A to C re-pointed; tokens built once; `PACK_RULES_READ_NO_FILE`; `STORAGE_RULES_ARE_FRAMEWORK_FREE`; bite tests; the onion and `PACK_VALIDATION_IS_FRAMEWORK_FREE` rules unchanged (S6) | WP13 | |
| T053 | The two subset tests, and the one-read registration test | WP12 | |
| T054 | Collapse the old test classes into per-rule, composition and mapper tests | WP10 | |
| T055 | The code-coverage test: every `PackCodes` and `StorageCodes` constant has a failing and a passing fixture | WP10 | |
| T056 | Narrowed PIT and the nightly measurement; margins re-set in the build files and ADR-0062 | WP10 | |
| T057 | ADR-0075 amendment (R3 as superseded by S1 to S3 and S6; `PackReading` replaces `PackInspector`, no rule widened), ADR-0065 annotation, docs | WP10 | [P] |
| T058 | The golden snapshot's final diff list, checked; the open-packs drafts complete | WP10 | |
| T059 | Open case 8: the clean control's missing `assets/doc.txt` | WP05 | [P] |
| T060 | `PackVerdicts` in `pack-validation`; `Precedence` retyped to `comparator(Set<ReasonCode>)` over `Reason` and `PackVerdict.findings` to `List<Reason>` (out-of-map on WP03 and WP05, N2) | WP09 | |
| T061 | Validate-mode tests and the validate golden move to `:pack-validation:test` (Pr5) | WP09 | |
| T062 | The `PackValidation` bean goes; ArchUnit unchanged: `PackCheck` names only `packs.domain` and the kernel (S6) | WP09 | |
| T063 | Issue texts: #554 amended and closed with evidence; #478 inherits the CLI entry, the pack set and the module merge | WP10 | [P] |
| T064 | Narrow `**/charter.yaml` and nested `**/*.graph.yaml`; classifier cases; packs `### Breaking` line; census gate checked | WP11 | |
| T065 | An explicit not-held kind in `ExcludedFile`; retired files off the not-held list (WP02 review L1, L2) | WP11 | |
| T066 | Validate mode warns `file_not_held` and judges nothing in a file that is not held | WP11 | |
| T067 | `HeldSetAgreementTest`: validate mode's and the store's not-held sets agree | WP11 | |
| T068 | Golden rows for open case 6; the open-packs draft text; the note's open case 6 and 8 pointers | WP11 | |
| T069 | S2's records: one `### Breaking` fragment, `CharterRefusalCode.yaml`, the registration scenarios, the golden | WP12 | |
| T070 | `VocabularyEqualityTest`, `WireTokenParityTest` and `ReservedRefusalCodesTest` onto `PackCodes`, `StorageCodes`, `PlacementCodes` and the mappers (which name no storage code, S6) | WP13 | |

## Phase 1: Freeze and Q1

### WP01: The golden snapshot and the R2 code table (done)

- **Prompt**: [tasks/WP01-golden-snapshot-and-code-table.md](tasks/WP01-golden-snapshot-and-code-table.md)
- **Goal**: record what every surface answers today, and decide each fault's one code (R2), before anything changes.
- **Independent test**: the golden comparison test passes on today's code, and fails when a detail string is altered.
- **Subtasks**: T001, T002, T003, T004, T005.
- **Dependencies**: none.
- **Review routing**: open case 8 (the clean control's missing `assets/doc.txt`) goes to WP05 (T059).

### WP02: Q1, an unmodelled file is not held (done)

- **Prompt**: [tasks/WP02-unmodelled-file-not-held.md](tasks/WP02-unmodelled-file-not-held.md)
- **Goal**: `notes.txt` is accepted with a `file_not_held` warning and is not stored, on today's pipeline.
- **Independent test**: the rewritten registration scenarios pass, and the golden diff lists only Q1 rows.
- **Subtasks**: T006, T007, T008, T009, T010, T011.
- **Dependencies**: WP01.
- **Review routing**: the LOW findings (the reason compared as text, retired files on the not-held list, the wording
  for mapped kinds) go to WP11.

### WP11: Open case 6, validate mode judges only what registration holds

- **Prompt**: [tasks/WP11-open-case-6-held-set.md](tasks/WP11-open-case-6-held-set.md)
- **Goal**: on today's pipeline, validate mode warns `file_not_held` for a nested shard or a `charter.yaml` and judges
  nothing in it; the packs contract narrows those globs; the not-held kind is explicit.
- **Independent test**: `HeldSetAgreementTest` passes over every planted and example pack and a synthetic every-class
  pack, and the golden diff lists only open case 6's validate rows, the two excluded-file validate warnings and the
  retired files' dropped duplicates.
- **Subtasks**: T064, T065, T066, T067, T068.
- **Dependencies**: WP02. It can run beside WP03.
- **Risks**: narrowing the table re-kinds the census gate (Pr6).

## Phase 2: Foundations

### WP03: PackPolicy's residue as packs-domain functions

- **Prompt**: [tasks/WP03-policy-residue-functions.md](tasks/WP03-policy-residue-functions.md)
- **Goal**: the members that roughly 20 classes use move into named functions, so that `PackPolicy` can later be
  deleted.
- **Subtasks**: T012, T013, T014, T015, T016.
- **Dependencies**: WP02. It is behaviour-preserving, and the golden snapshot is unchanged.

### WP04: PackCodes, StorageCodes and the unified codes (R2)

- **Prompt**: [tasks/WP04-pack-codes-unified.md](tasks/WP04-pack-codes-unified.md)
- **Goal**: one typed code table per owner, each token built once, which every surface speaks.
- **Subtasks**: T017, T018, T019, T020, T021, T022.
- **Dependencies**: WP03, WP11.
- **Risks**: this is a wire change. Every golden diff row must cite an R2 row.

### WP05: The one reader, the census, the Spring-free assembly and composition facts

- **Prompt**: [tasks/WP05-inspector-facts-and-assembly.md](tasks/WP05-inspector-facts-and-assembly.md)
- **Goal**: one read produces one `TreeRead` (`PackContents`, the whole assembly input; `TreeCensus`; read faults)
  inside storage (S1); the domain port `PackReading` and its value types are declared (S6); the aggregate and the
  composition facts are built from the read; S2's prechecks are read faults; the clean control is clean.
- **Subtasks**: T023, T024, T025, T026, T027, T028, T030, T059.
- **Dependencies**: WP04.
- **Risks**: the largest step of the mission; keep the commits ordered.

## Phase 3: The rules

### WP06: Content rules part 1 (ids, assets, mission types, glossary)

- **Prompt**: [tasks/WP06-rules-descriptor-ids-assets.md](tasks/WP06-rules-descriptor-ids-assets.md)
- **Subtasks**: T031, T032, T033, T034.
- **Dependencies**: WP05.

### WP07: Content rules part 2, the storage rules, the compositions and PackRefusals

- **Prompt**: [tasks/WP07-rules-graph-fragments-pack-mappers.md](tasks/WP07-rules-graph-fragments-pack-mappers.md)
- **Subtasks**: T035, T036, T037, T038, T039, T040, T041.
- **Dependencies**: WP06.

### WP08: CLOSURE

- **Prompt**: [tasks/WP08-closure.md](tasks/WP08-closure.md)
- **Subtasks**: T042, T043, T044, T045, T046.
- **Dependencies**: WP05. It can run beside WP06 and WP07, but stays in the same lane.

## Phase 4: The edges, the deletions and the closeout

### WP09: The validation edge, PackCheck in pack-validation

- **Prompt**: [tasks/WP09-one-pipeline-switch.md](tasks/WP09-one-pipeline-switch.md)
- **Goal**: validate mode is `PackCheck`: the readout of `PackReading` (the storage verdict, manifest gate included)
  joined by `Rationale.and` with the placement rules' (`PlacementCodes`), mapped by `PackVerdicts`, in
  `pack-validation`, which names only `packs.domain` and the kernel (S6). `Precedence` and `PackVerdict` move onto
  kernel types, and the `PackInspector` port and its `Scope` are deleted.
- **Subtasks**: T047, T029, T060, T061, T062.
- **Dependencies**: WP07, WP08.

### WP12: Registration and the compose read on the one read

- **Prompt**: [tasks/WP12-registration-and-compose-read-on-one-read.md](tasks/WP12-registration-and-compose-read-on-one-read.md)
- **Goal**: registration reads once through `PackReading`, refuses through `PackRefusals` on the readout's
  `Rationale` (`registration()` and the manifest gate), then assembles and stamps (S6); the compose read runs only the store half of the one reader (`StoreRead`, S7), with no
  filter (S4 holds by construction), and answers `pack_unreadable` for a repeated artifact id (S5, landed in WP04); S2's gains land under `### Breaking`.
- **Subtasks**: T048, T049, T050, T053, T069.
- **Dependencies**: WP09.

### WP13: The deletions and ArchUnit

- **Prompt**: [tasks/WP13-deletions-and-archunit.md](tasks/WP13-deletions-and-archunit.md)
- **Subtasks**: T051, T052, T070.
- **Dependencies**: WP12.

### WP10: Tests, floors and records

- **Prompt**: [tasks/WP10-tests-floors-records.md](tasks/WP10-tests-floors-records.md)
- **Subtasks**: T054, T055, T056, T057, T058, T063.
- **Dependencies**: WP13.

## Dependency graph

```text
WP01✓ ─ WP02✓ ─┬─ WP11 ─┐
               └─ WP03 ─┴─ WP04 ─ WP05 ─┬─ WP06 ─ WP07 ─┐
                                        └─ WP08 ────────┴─ WP09 ─ WP12 ─ WP13 ─ WP10
```

WP09 is the validation edge, WP12 is registration plus the compose read on the one read, and WP13 is the deletions
plus ArchUnit.

## MVP

WP01 and WP02 (done). They froze the wire and delivered the operator's Q1 ruling on today's pipeline. WP11 completes
Q1 on validate mode.
