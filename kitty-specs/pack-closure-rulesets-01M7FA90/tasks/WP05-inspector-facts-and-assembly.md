---
work_package_id: WP05
title: The one reader, the census, the Spring-free assembly and composition facts
dependencies:
- WP04
requirement_refs:
- FR-015
- FR-016
- FR-017
- NFR-004
- C-003
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts for this mission were generated on slice-5-554-pack-closure-rulesets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-5-554-pack-closure-rulesets unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-closure-rulesets-01M7FA90
base_commit: 8346f56e9bcac212c8f290ffd5952b474294d2a5
created_at: '2026-11-17T00:52:38.671525+00:00'
subtasks:
- T023
- T024
- T025
- T026
- T027
- T028
- T030
- T059
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
- at: '2026-11-16T12:00:00Z'
  actor: planner-priti
  action: Replanned for the S1 to S3 rulings (squad-shaping)
- at: '2026-11-16T18:00:00Z'
  actor: planner-priti
  action: Folded S6 (the domain port PackReading; no TreeReader port)
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackAssembly.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackManifestDerivation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CompositionFactsBuilder.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/TreeCensus.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/TreeRead.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackReading.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackReadout.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/NotHeldFile.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClassifiedFile.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackAssemblyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompositionFactsBuilderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/OneReadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/rules/TreeCensusTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackAssembly.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackManifestDerivation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CompositionFactsBuilder.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ManifestMembership.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackContents.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/TreeCensus.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/TreeRead.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackReading.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackReadout.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/NotHeldFile.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClassifiedFile.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackVerdict.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackAssemblyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompositionFactsBuilderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/OneReadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspectorTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ManifestMembershipTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/rules/TreeCensusTest.java
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP05 - The one reader, the census, the Spring-free assembly and composition facts

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
- Start with `spec-kitty agent action implement WP05 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.
- An edit to a file another work package owns is an **out-of-map edit**: one purpose, recorded in the review log.

## Coordination

- `WholePackStore.java` is WP12's (T049): T023, T024, T025 and T028 edit it out-of-map (the load builds through
  `PackAssembly`, `derivedAt` moves out, `modelledFiles` goes, `loadStaged` takes the one read); each is recorded.
- `DomainRepositoryService.java` is WP12's (T050): T023, T027 and T028 edit it out-of-map (`PackAssembly` carved out,
  `compositionFacts` delegates, `readable` runs the one reader's store half, `StoreRead`, S7); each is recorded.
- `FileSystemPackInspector.java` is owned here. WP07 (T039) edits it out-of-map to implement `PackReading`, once
  `StorageRulesets.verdict` exists; this work package declares the port's types and does not implement the port.
  WP09 edits it out-of-map again to drop `PackInspector` and `inspect(root, scope)` (N6).
- `PackVerdict.java` is owned here; WP09 (T060) retypes its `findings` to `List<Reason>` out-of-map (N2).
- `FileSystemPackInspectorTest.java` is owned here; WP09 repoints its `inspect` sites out-of-map (N6, N7).

## Objective

Build the **one reader** (S1): one walk of a pack tree produces `PackContents` (storage-agnostic facts, the aggregate's
inputs among them), a `TreeCensus` (the one owner of "held", S3) and the read faults, so that the aggregate and the
composition facts are built from the read that is judged, never from a second read. Declare the domain port of that
read, `PackReading`, and its value types (S6). The store's load and the
compose read consume it in this work package, behaviour-preserving; registration starts judging it in WP12. Spec
FR-015, FR-016, FR-017 (the prechecks) and NFR-004; plan IC-05; squad findings A-B3, A-M1, F2, F4, F5, A2/P2, A4,
P4, P7.

**The change preserves behaviour:** the golden snapshot is unchanged, apart from the clean control's rows (T059, open
case 8), which the review log lists.

## Subtasks

### T023: `PackAssembly`

- **Purpose:** a Spring-free class, not a bean, in `infra.storage` (F2), carved out of `DomainRepositoryService`
  (a `@Service`, `:112`). It **builds the aggregate from the one read and judges nothing** (S1).
- **What it takes:** the DSO-to-entity build of `assembly` (`:955` before F; re-check) and the `judged()` withholding
  (`:960-985`), which keeps `Pack.guardKind` from breaching. The withholding stays as aggregate shaping.
- **Callers:** `DomainRepositoryService` and `WholePackStore`'s load (out-of-map edits of WP12's files) build through
  it. A value-object breach or a reference fault it meets is already a read fault of the one read (T025); it is not
  re-derived here.
- **Acceptance:** `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` (`HexagonalArchitectureTest:~389`) stays green. Run
  `:deployable:test --tests '*HexagonalArchitecture*'`.

### T024: `PackManifestDerivation`

- **Purpose:** move `ManifestMembership.derived` and `derivedAt` off `WholePackStore` (A-B3: `derivedAt` names the
  `@Service`).
- **Who calls it:** the reader, when it builds the census's manifest fact (T025): the `TreeCensus` carries the
  derived set beside the listed set; and the manifest stamp (`PackCommitStore.java:396`). Both call this one
  function, so the two derivations never split (A-D1). The membership rule (WP07) never calls it: it compares the
  census's facts only, so `packs.infra.storage.rules` names no other `packs.infra.storage` class
  (`STORAGE_RULES_ARE_FRAMEWORK_FREE`, WP13).

### T025: The one reader, `TreeRead`, `PackContents`, `TreeCensus` and the domain port's types (S6)

- **The seam (S6, operator 2026-11-16, "Port, no rule change"):** the domain port **`PackReading`** in
  `packs.domain`, `PackReadout read(PackRoot root)`. Declare it here with its value types (data-model.md, "The
  port"):
  - `PackReadout(PackContents contents, List<ClassifiedFile> files, List<NotHeldFile> notHeld, Rationale rationale)`,
    copying its lists;
  - `NotHeldFile(String path, Kind kind, String reason)`, `Kind` being `EXCLUDED` or `UNMODELLED`: WP11's explicit
    kind moves here from `WholePackWriteStore.ExcludedFile`, which WP12 deletes;
  - `ClassifiedFile`, moved out of `PackContents` (`PackContents.ClassifiedFile` today) to a top-level
    `packs.domain` record, because the classification leaves `PackContents` and `PackVerdict` still carries it
    (update `PackVerdict`'s reference, owned here).
  The port names only `packs.domain`, the kernel and `java` (`PORT_SIGNATURES_NAME_ONLY_DOMAIN_TYPES`). WP07 makes
  `FileSystemPackInspector` implement it, with `StorageRulesets.verdict`; WP09 and WP12 consume it. No `TreeReader`
  port is built: nothing outside `packs.infra.storage` needs the `TreeRead`.
- **The reader:** `FileSystemPackInspector` (ruling 1 keeps it) becomes the one reader. Its method
  `TreeRead readTree(PackRoot root)` reads the whole tree and is **package-private**: `STORAGE_RETURNS_DOMAIN`
  refuses a public method of a port implementation that returns a storage type, and the store's load, its caller,
  shares its package; the compose read, in the same package, runs only the store half (`StoreRead`, S7). A composition root supplies the reader: `deployable` to registration and
  the compose read (WP12), and `PackCheck`'s callers to the validation edge (the tests now, pack-check's main in #478;
  WP09). Nothing else constructs it. The store's own read of the same tree (`FilesystemPackStore`'s `PackRead` and
  the bindings) becomes the walk's internal DSO half, mapped into `PackContents` during the walk. If the implementer
  finds the store's reader the better base, record why in the review log; the result is still one reader, in
  `infra.storage`.
- **Open case 9 (routed from WP11's review):** a mapped file the load does not take is listed by the store as
  UNMODELLED with a false reason ("no pack file kind models this file"), while validate mode judges it as held. Two
  cases:
  - a `policy.yaml` the descriptor does not name (no fault at it): the census decides once. Either it is not held,
    with its own reason (for example "not named by the descriptor") and judged on neither surface, or it is held and
    judged on both.
  - an orphan fragment keeps its refusal (`prompt_fragment_invalid`, US3 scenario 3, WP07 T037) whichever way the
    first case is settled. Only its false not-held reason goes: it is not listed as not held.

  Record the decision in the note's open case 9, add a plant for each case to `HeldSetAgreementTest`, and, in the same
  planning pass, amend NFR-002's diff list (and `NotHeldFile.Kind` if a new reason class needs it).
- **`TreeRead` (`packs.infra.storage.rules`)** is the storage rules' one input type: `TreeRead(PackContents contents,
  TreeCensus census, List<Reason> readFaults)`. `holding()` and `registration()` (WP07) run over it, `content()` is
  lifted onto it with `on(TreeRead::contents)`. Nothing outside `packs.infra.storage` and `deployable` names it (S6):
  `Placement` (WP09) wraps the `PackReadout` instead.
- **The read faults** are kernel `Reason`s typed with a kernel `ReasonCode` (the constant built in `PackCodes` or
  `StorageCodes`), the file as location, and the reader's wording. The schema wording is the reader's.
- **`PackContents` (`packs.domain`)** holds storage-agnostic facts only (A4, P4): the descriptor, the bound graphs
  (WP03's `GraphBinding`), the artifacts, steps, assets, fragments, prompt slots and glossary terms. It holds no read
  fault: a file the read refused yields no fact, and the bound graphs say whether every graph was read (WP07's
  edges-read gate). Move out of it what is storage: the classification, the manifest and the reserved-shard stamp go
  to the census.
- **`PackContents` is also the assembly input (S6).** Registration (`packs.application`) hands the store back only the
  `PackReadout` it judged, so `PackAssembly` builds the aggregate from a `PackContents` alone. Whatever the aggregate
  needs that `PackContents` does not yet carry (the per-artifact digest, the domain objects of the bound graphs) is
  added to it as a domain value; no package-private assembly input travels beside `TreeRead`. If a fact the aggregate
  needs cannot be a storage-agnostic domain value, stop and report it to the orchestrator rather than pass a storage
  type through the port.
- **The `PackInspector` port** keeps its shape until WP09 moves its last consumer (`PackValidation`, `PackLoad`) onto
  `PackReading` and deletes it with its `Scope` (N6). Until then `FileSystemPackInspector` implements it (and, from WP07, `PackReading`)
  over the same walk.
- **`TreeCensus` (`packs.infra.storage.rules`)**: every regular file with its `ClassifiedFile`, held or not held. A
  not-held file is a `NotHeldFile` with WP11's explicit kind (`EXCLUDED` or `UNMODELLED`) and its reason; a retired file
  is a read fault, never on the not-held list. It also holds the manifest (present, regular, its listed set, and T024's
  derived set, computed by the reader) and the reserved-shard stamp (a fact, not an unreadable graph, C-4). It is the **one owner of "held"**:
  the storage rules, the whole-pack census gate (`WholePackCensus`) and, through the readout's `files` and `notHeld`,
  `removeNotHeld` and validate mode read it, and `WholePackStore.modelledFiles` goes (out-of-map, recorded).
- **The A-M1 checklist**, each produced by the one read, each with a fixture:
  1. value-object `domain` breaches: file, kind and the breach's `Reason`, as read faults;
  2. `reference` faults (unclosed references), as read faults;
  3. the aggregate `build()` breach (`Pack.guardKind`);
  4. descriptor and not-a-pack-directory faults;
  5. the named mutability-policy file being unreadable;
  6. a misplaced preset entry;
  7. the graph read: `drg_schema_error`, and the endpoint rule's input;
  8. the companion-to-`prompt_fragment_invalid` remap;
  9. the files that are not held, with their kind and reason (census);
  10. the manifest: present, regular, and listed against derived (census);
  11. the reserved-shard stamp (census).
- **Purity:** `TreeCensus` and `TreeRead` name only the kernel, `packs.domain` and `java.*` value types: no `Path`, no
  framework (WP13 pins it with `STORAGE_RULES_ARE_FRAMEWORK_FREE`). The port's types are pure domain
  (`PACKS_DOMAIN_IS_PURE`). No ArchUnit rule is widened (S6).

### T026: The registration path's root and links (F4)

- **The root:** reading a staged root that cannot be read, or that is not a directory, must yield a read fault that
  registration answers as today, 422 `validation_failed` at `pack`. It must not throw a bare `pack_unreadable`.
- **Symlinks:** the reader follows no symlinks under a staged tree, as the store does not.
- **Tests:** add one for each case.

### T027: One composition-facts builder (F5, P7)

- **What it builds:** `PackUnderResolution` from the one read: the descriptor, the level, the defined and changed
  artifacts (from the `overrides` edges) and the held terms. It is Spring-free.
- **One derivation:** `DomainRepositoryService.compositionFacts` delegates to it, so the second derivation goes. No
  parity test between two builders is written: there is one.
- **`CompositionFactsBuilderTest`:** over every example pack, the facts equal those the golden composition answers
  were built from (`CompositionGoldenTest` stays green).

### T028: The store's load and the compose read consume the one reader

- **The store's load:** `WholePackStore.loadStaged` (out-of-map, recorded) takes the one read (`readTree`) and builds
  through `PackAssembly` from its `PackContents`. Its judging is unchanged until WP12: it refuses exactly today's faults, in today's order and shape.
- **The compose read (S7, operator 2026-11-17):** `DomainRepositoryService.readable` (WP12's file, out-of-map)
  consumes the store half of the one reader, `StoreRead`, over the one walk, not the full `readTree`: on the full one
  read it cost 3.5 to 4.8 times today's. It runs no `content()` over the inspector's facts; its answers are unchanged.
- **`OneReadTest`:** a counting file system (or a counting reader seam) proves one walk per `loadStaged`, and one
  walk and one store read per compose read. Today's second read through `contentsOf` / `compositionFacts` is gone.
- **Test placement:** keep the reader's fact tests in `FileSystemPackInspectorTest`, and split by fact if it grows past
  about 1,500 lines. `TreeCensusTest` covers the census over the planted packs, including WP11's narrowed files.
- **The held-set agreement (FR-016, I13):** WP11's `HeldSetAgreementTest` now compares the census's not-held list with
  the golden's `file_not_held` diagnostics (`golden/surfaces.json`) over the planted and example packs, not one
  derivation with itself.

### T030: S2's format prechecks in the reader

- **What:** the descriptor version format (`requires_core`, `pack_version`, a `depends_on` range; through WP03's
  `DescriptorChecks`) and the asset mime format (through `AssetMimeTable`) become **prechecks of the one reader**,
  reported as read faults with the `StorageCodes` tokens and the R2 wording (rows 14 and 38).
- **Who sees them now:** validate mode, as today (its answer is unchanged). The store's judging (T028) does not refuse
  them until WP12, which writes S2's `### Breaking` fragment.
- **The compose read (S4, operator 2026-11-16; S7, operator 2026-11-17):** "keep today's answers". The compose read
  runs only the store half (`StoreRead`), which never produces the S2 precheck faults (`requires_core_invalid`,
  `pack_version_invalid`, `dependency_range_invalid`, `asset_mime_invalid`), so no filter is built: S4 holds by
  construction. `assets.feature:150` keeps serving a pack whose asset mime is malformed.

### T059: Open case 8, the clean control

- **The defect:** `planted/control-a-clean-org-pack` has a sidecar whose payload `assets/doc.txt` is missing, so all
  three surfaces refuse it, though its `expected.json` says it loads with no findings (pack-code-unification note, open
  case 8, routed here by the WP01 review).
- **The fix:** add the payload, an out-of-map edit of WP01's planted tree. Regenerate the golden for that subject only,
  and list its rows in the review log as "open case 8: fixture defect".

## Definition of Done

- One reader produces one `TreeRead`, whose `PackContents` is the whole assembly input; `OneReadTest` sees one walk
  per call, and one store read per compose read (S7).
- `PackReading`, `PackReadout`, `NotHeldFile` and `ClassifiedFile` exist in `packs.domain`; no `TreeReader` port
  exists, and `readTree` is package-private.
- Every A-M1 row has a fact and a fixture.
- `WholePackStore.modelledFiles` is gone; the census is the one owner of "held".
- `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` and `PACK_VALIDATION_IS_FRAMEWORK_FREE` are green.
- The golden snapshot is unchanged, apart from the clean control's rows.
- `verifyAll` is green, including `:pack-validation:wholePackRoundTrip` and the drift test.
- A narrowed PIT run on `infra.storage` and `infra.storage.rules` is reported.

## Risks

- **Size.** The reader merge is the largest step of the mission. Keep commits ordered: census, then contents, then
  assembly input, then the consumers.
- **The whole-pack census gate.** It now reads the `TreeCensus`. Check the baseline lists and the drift test (Pr6).

## Reviewer guidance

- Is there one read, one assembly, one manifest derivation and one composition-facts builder?
- Does anything outside `packs.infra.storage` name `TreeRead` or `TreeCensus`? Nothing may (S6).
- Does `PackContents` hold any storage fact (a path, a classification, a manifest)? It must not.
- Is any fault judged in `PackAssembly`? None may be.
