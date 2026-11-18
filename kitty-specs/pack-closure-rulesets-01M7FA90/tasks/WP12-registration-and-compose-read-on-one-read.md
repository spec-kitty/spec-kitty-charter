---
work_package_id: WP12
title: Registration and the compose read on the one read
dependencies:
- WP09
requirement_refs:
- FR-002
- FR-006
- FR-016
- FR-017
- SC-001
- NFR-002
- C-005
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts for this mission were generated on slice-5-554-pack-closure-rulesets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-5-554-pack-closure-rulesets unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-closure-rulesets-01M7FA90
base_commit: ba073f8bd5eb1d5e228657145ba1baadf8dd22bc
created_at: '2026-11-17T12:32:53.495309+00:00'
subtasks:
- T048
- T049
- T050
- T053
- T069
history:
- at: '2026-11-16T12:00:00Z'
  actor: planner-priti
  action: Prompt generated for the S1 to S3 replan (squad-shaping)
- at: '2026-11-16T18:00:00Z'
  actor: planner-priti
  action: Folded S6 (registration reads through PackReading; no onion rule widened)
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRegistrationService.java
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/application/SurfaceSubsetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/OneReadRegistrationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackStoreReadTest.java
- contracts/charter/changes/2026-11-16-registration-judges-content-and-format.md
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackSourceStagers.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/AssetPackRegistrationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/SurfaceSubsetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/OneReadRegistrationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackStoreReadTest.java
- contracts/charter/changes/2026-11-16-registration-judges-content-and-format.md
- contracts/charter/scenarios/assets.feature
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP12 - Registration and the compose read on the one read

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else.

Then read, in `kitty-specs/pack-closure-rulesets-01M7FA90/`:

- `spec.md` (the Intent Summary's rulings table, S1 to S3 included);
- `plan.md` (Decisions, and the IC named below);
- `data-model.md` (placement, the one reader, the compositions);
- `research/squad-shaping.md` (the S1 to S3 rulings, their recorded readings and the target shape);
- `research/design-r3.md` (§1 to §7, read with §8, which lists what S1 to S3 and S6 superseded) and `research/squad-plan.md`;
- `research/squad-similar-surfaces.md` (file:line map, as of before slice F; re-check each reference).

Charter rules that bind every subtask:

- The charter's model is canonical. There is no legacy reader and no shim.
- Tests carry only the canonical shape, apart from error-path fixtures.

## Branch Strategy

- Planning base and final merge target: `slice-5-554-pack-closure-rulesets`. The mission branch is `kitty/mission-pack-closure-rulesets-01M7FA90`.
- The execution worktree is allocated per computed lane from `lanes.json`; this mission has one lane.
- Start with `spec-kitty agent action implement WP12 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.
- An edit to a file another work package owns is an **out-of-map edit**: one purpose, recorded in the review log.

## Objective

Registration and the compose read judge the **one read** (S1). Registration reads the staged tree once through the
domain port `PackReading` (S6), refuses through `PackRefusals` when the readout's `Rationale` (the read faults,
`StorageRulesets.registration()` and the manifest gate) fails, then the store's `Loader` has `PackAssembly` build the
aggregate from that readout's `PackContents`, and the store stamps and commits it. The compose read runs only the store half of the one reader (`StoreRead`, S7) and answers as today, with
`pack_unreadable` for a repeated artifact id (S5, landed in WP04). There is no
second read and no fail-closed guard. Registration gains S2's checks under one `### Breaking` fragment. Spec FR-002,
FR-016, FR-017, SC-001 and NFR-002; plan IC-11; squad findings A2/P2, A-M3, F6 to F10, Pr7.

## Ownership

This work package owns `WholePackStore.java` (its main rewrite, T049) and `DomainRepositoryService.java` (T050).
WP04, WP05, WP11 and WP13 edit them out-of-map, each with a coordination note in its body.

This work package edits two files of done or earlier work packages out-of-map, each recorded: `CharterWiring.java`
(WP09's; T048: one constructor argument) and `WholePackWriteStore.java` (WP02's; T049: the `Loader` signatures,
`Contents.notHeld` as `NotHeldFile`s, and `ExcludedFile` deleted).

## Subtasks

### T048: Registration on the one read

- **`PackRegistrationService`** takes the one reader as the domain port **`PackReading`** (S6, operator 2026-11-16,
  "Port, no rule change"). Wire it in `CharterWiring` (an out-of-map edit of WP09's file: one constructor argument),
  with the one `FileSystemPackInspector` bean as the implementation; the compose read (`DomainRepositoryService`, in
  the reader's package) takes the same bean and runs only its store half (S7). `packs.application` names only `PackReading`, `PackReadout`,
  `NotHeldFile` and the kernel, never `packs.infra.storage.rules`, so `ONION_APPLICATION_DEPENDS_ON_DOMAIN_ONLY` and
  `ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS` hold unchanged. Widen no rule.
- **Order of work (`register`, `:265`):**
  1. Stage, as today.
  2. Read the staged tree once: `PackReadout read = reading.read(new PackRoot(packId, Optional.empty(),
     staged.directory().toString()))` (C-11). Its `Rationale` is WP07's `StorageRulesets.verdict`: the read faults,
     `registration()` (`holding().must(PackRulesets.content().on(TreeRead::contents))`), and the manifest gate when
     those pass, in registration's wire order. Registration does not compose rules itself.
  3. If `read.rationale().isFailing()`, throw `CharterRefusal.withViolations(VALIDATION_FAILED,
     PackRefusals.of(read.rationale())...)`, with today's problem detail. A root that cannot be read answers today's
     shape (F4, WP05).
  4. Otherwise `loader.loadStaged(read)` builds the aggregate with `PackAssembly` from `read.contents()` (T049); run
     the whole-pack gate and the registration impact check on it, `loader.removeNotHeld(staged, read.notHeld())`,
     stamp and commit.
- **No guard:** nothing re-reads the tree, so there is no reader-only fault to catch (S1). Do not add one.
- **The diagnostics** come from `PackRefusals`, over `read.rationale().warnings()`. The reader's own warning list is
  not reported twice.
- **`PackSourceStagers` (`:73`)** calls through `register`, so there is one registration judgement (F8).
- **Delete `LoadChecks` (carried from WP05's review).** WP05 carved the load's rule judging into the Spring-free
  staging class `packs.infra.storage.LoadChecks`. Registration judging the readout's `Rationale` replaces it: delete
  the class and its test, and repoint whatever still calls it (`PackManifestDerivation`, `CompositionFactsBuilder`
  if WP05 left a call) onto `StoreRead`'s facts.
- **`WholePackCensus` reads the `TreeCensus` (carried from WP05's review, MINOR-3; T025).** WP05 left the whole-pack
  census gate on its own walk and classification, because its path-to-gate-kind map holds what the census does not yet
  hold: symlink entries, the `other:<segment>` buckets, the `retired:` and `excluded:` prefixes, and companion owner
  kinds (`companionsOf`). Extend the census with those facts (domain values, no `Path`), point `WholePackCensus.of` at
  it, and keep the gate's baseline lists and the drift test unchanged.
- **S8 (operator, 2026-11-17): every fault for a pack without a readable `pack.yaml`.** Registration answers
  `PackRefusals` over the verdict with no short-circuit; regenerate the golden, list the 38 descriptor rows (R2 rows
  22–24) under S8 in the review log, and write the `### Breaking` fragment.
- **Carried from WP07's review.**
  - MINOR-1: `AssemblyRules` (`:61`) can state a root refusal twice when the pack's name differs from its directory
    name (`pack <name> is not a directory` vs the store's wording). Skip the rule when a read fault is
    `pack_unreadable` at `pack` (code and location), with a unit test using a mismatched name (R2 row 25).
  - MINOR-2: `PackRefusalsTest` (`:220-234`) pins only the leading fault of the 38 descriptor rows. For each bare
    plant, the answer minus the descriptor fault equals its `+pack.yaml` twin's recorded violations (exact or
    S2-filtered); only plants without a twin keep the current check.
  - MINOR-3: a reader fault at `pack-manifest.yaml` sorts ahead of rule faults in `StorageRulesets.inRegistrationOrder`,
    while `loadStaged` lists the manifest's faults after the assembly's. Put them after the rule faults and before the
    orphans (or list the row under NFR-002), and fix `StorageRulesetsTest.Order`.
  - R2 row 32, from WP09: the policy file `mutability_policy` names is read only by the store half. Today validate
    mode answers nothing when it is missing and `policy.yaml: <parser line>` when it does not parse, while
    registration answers `unreadable` "named by mutability_policy, the file is missing / cannot be read: …". Make it
    a read fault, stated once, so both surfaces answer registration's wording; record the row in the R2 note.
  - MINOR-4: R2 row 26 keeps the generic `validation_failed` (`StorageCodes.ASSEMBLY_REFUSED`) instead of the
    invariant's own code. Settle it with the store-only faults (A-M1 rows 16–18, 32), which become read faults here,
    stated once.
  - NIT: the R2 note says an unparseable unstamped `registered.graph.yaml` now also gets `drg_schema_error` in
    validate mode.
- **Carried from WP05's second review.**
  - `OneReadTest.theRegistrationLoadReadsOnce` also asserts `PackFileSet.walks()` and `StoreRead.runs()`.
  - An `UNNAMED` file (an orphan companion, an unnamed `policy.yaml`) is not listed as classified in
    `census.files()` / `PackReadout.files()` beside its `file_not_held` warning; `HeldSetAgreementTest`'s "a not-held
    file is never one a rule maps" holds in general, with a plant for each `UNNAMED` case.
- **Carried from WP04's review (MINOR-3, MINOR-4).**
  - Registration answers one violation per descriptor finding: today `FilesystemPackStore.java:1450` joins a
    `pack.yaml`'s schema faults with `"; "`, so two unknown keys give one registration violation and two validate
    findings, which breaks the forward subset. Cover it in T053's subset test.
  - The refusal path no longer builds a second `FilesystemPackStore` to re-read `pack.yaml` for the pointer
    (`WholePackStore.java:720,730`): the pointer comes from the readout's reason, so no second read remains.
  - R2 row 19's two remaining differences (recorded in the R2 note): registration places a companion's fault at the
    artifact that names it while validate mode places it at the companion file, and a companion's structure-schema
    breach is `companion` at registration but `artifact_schema_error` in validate mode. On the one read both surfaces
    answer the same code at the same file; record the chosen answer in the R2 note and a `### Breaking` fragment.

### T049: `loadStaged` judges nothing

- **`WholePackStore.loadStaged`** (owned here since the 2026-11-16 analysis fold; it was WP02's) no longer refuses or
  reads. The `Loader` port (`WholePackWriteStore`, `packs.application`; WP02's file, out-of-map) becomes
  `Contents loadStaged(PackReadout read)`: it assembles from the readout registration judged, from its
  `PackContents` alone (WP05 made it the whole assembly input), so no storage type crosses into `packs.application`
  (S6). `removeNotHeld(StagedPack staged, List<NotHeldFile> notHeld)` takes the readout's not-held files.
  `Contents.notHeld` becomes `List<NotHeldFile>`, and `ExcludedFile` (with its `notHeld()` finding) is deleted:
  `NotHeldFile` replaces it, one type for one concept.
- **Remove `graphViolations` and the `loadStaged` judging sequence.** Its order now lives in `PackRefusals`.
- **The 63 `new WholePackStore(` test sites** that assert refusals move their assertions to
  `PackRegistrationServiceTest` or the rule tests. Store tests keep what the store still does, in
  `WholePackStoreReadTest`. Do not add a judge back into the store.

### T050: The compose read (A-M3)

- **S7 (operator, 2026-11-17):** on the full one read (`readTree`: the inspector's generic parse plus the store's DSO
  parse) the compose read, a hot path, cost 3.5 to 4.8 times today's (WP05), because every file is parsed twice.
  **`DomainRepositoryService.readable`** (owned here) runs only the store half of the one reader: `StoreRead`, the
  same implementation `readTree` runs over the same one walk, inside storage. It builds no second DSO reader, and it
  runs no `content()` over the inspector's `PackContents` facts.
- **The answer:** as today, from `StoreRead`'s assembly facts and faults (`PackContents.Assembly`): the first failure
  as `pack_unreadable`, with the endpoint prefix kept.
- **S4 (operator, 2026-11-16, "keep today's answers"):** holds by construction. The store half never produces S2's
  prechecks (`requires_core_invalid`, `pack_version_invalid`, `dependency_range_invalid`, `asset_mime_invalid`), so
  no filter is built, and there is no profile in core. `assets.feature:150` keeps serving a pack whose asset mime is
  malformed.
- **S5 (operator, 2026-11-16, "yes, fix the 500"):** already landed in WP04 through the store half: a repeated artifact
  id answers `pack_unreadable` with a detail instead of a 500. The golden row changed once, recorded as a fix (R2 open
  case 3, S5), and NFR-002's diff list names it. Keep it.
- **Known limit:** single-parse unification (the inspector's facts derived from the DSOs, each file parsed once) is
  later work, outside this Mission. `loadStaged` and validate mode also parse twice, but they are not hot paths.
- **Registration and validate mode are unaffected:** they still judge the full one read.
- **The `judged()` withholding stays,** in `PackAssembly`.
- **Pin it:** every compose-read 500 detail in the golden snapshot is unchanged, apart from the S5 row.

### T053: The subset tests

- **`SurfaceSubsetTest`**, over every planted, example and golden pack:
  - **forward:** registration's faults ⊆ validate mode's faults, with equal code and wording (SC-001). It holds by
    construction of the compositions; the test pins it;
  - **reverse:** registration's faults equal today's golden registration faults, apart from the rows the diff list
    of NFR-002 allows (R1's fault sets, F4).
- **`OneReadRegistrationTest`:** one walk of the staged tree per registration (FR-016).

### T069: S2's records

- **The fragment** `2026-11-16-registration-judges-content-and-format.md`, with exactly one `### Breaking` block:
  registration now refuses a repeated asset id (`duplicate_artifact_id`), a malformed `requires_core`, `pack_version`
  or `depends_on` range (`requires_core_invalid`, `pack_version_invalid`, `dependency_range_invalid`) and a malformed
  asset mime (`asset_mime_invalid`), as validate mode does. Name the golden rows (14, 15 and 38). Say that the compose
  read keeps serving stored packs with those formats (S4).
- **`CharterRefusalCode.yaml`**'s `validation_failed` list (WP04's file; out-of-map) names the new codes.
- **The scenarios:** add a registration scenario per S2 code. `assets.feature:150`: registration of such a pack is now
  refused; the compose read keeps serving a stored one (S4).
- **The golden:** regenerate; every new registration row cites S2, and the compose read's repeated-artifact-id row
  cites S5.
- **The open-packs draft:** hand the S2 codes to the orchestrator for `TBD-unmodelled-not-held` (planning pass).

## Definition of Done

- Registration reads once through `PackReading` and refuses on the readout's `Rationale` (`registration()` and the
  manifest gate); nothing in the store judges.
- `packs.application` names no `packs.infra` type, and ArchUnit is green with every rule unchanged (S6).
- No fail-closed guard exists.
- `LoadChecks` is gone (carried from WP05).
- `WholePackCensus` on the `TreeCensus` moved to WP13 (operator, 2026-11-17).
- The compose read runs only the store half of the one reader (`StoreRead`, S7), with no S4 filter and no second
  reader; its golden answers hold, apart from the S5 row.
- The subset tests and the one-read test pass.
- One registration violation per descriptor finding, and no re-read of `pack.yaml` on the refusal path (carried from WP04).
- The S2 fragment is valid (`ChangelogFragments.java:43-45`), and the scenarios pass in `:deployable:test`.
- `./gradlew verifyAll --max-workers=1` is green, including the native build.
- A narrowed PIT run on `packs.application` is reported.

## Risks

- **Size.** The store's refusal tests move here; keep the commits ordered and each green.
- **The gate and the impact check** read the assembled aggregate; check they see the same aggregate as before (F10).

## Reviewer guidance

- Is there exactly one read and one judgement per registration?
- Does `packs.application` name `TreeRead`, `StorageRulesets` or `StorageCodes`? It may not (S6).
- Is any judge left in the store?
- Does every changed registration row cite Q1, an R2 row or S2?
- **Open review item for the pre-merge squad (analysis L5, moot under S7):** does the compose read run `StoreRead`,
  the store half of the one reader, over the one walk, with no second reader, parser or profile in core?
