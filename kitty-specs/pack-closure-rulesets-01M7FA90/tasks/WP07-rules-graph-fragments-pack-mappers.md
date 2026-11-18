---
work_package_id: WP07
title: Content rules part 2, the storage rules, the compositions and PackRefusals
dependencies:
- WP06
requirement_refs:
- FR-001
- FR-002
- FR-008
- FR-011
- FR-012
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts for this mission were generated on slice-5-554-pack-closure-rulesets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-5-554-pack-closure-rulesets unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-closure-rulesets-01M7FA90
base_commit: 5a8ee1825f81a90e6a1edf837b211cd1f9e1102a
created_at: '2026-11-17T07:44:55.406481+00:00'
subtasks:
- T035
- T036
- T037
- T038
- T039
- T040
- T041
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
- at: '2026-11-16T12:00:00Z'
  actor: planner-priti
  action: Replanned for the S1 to S3 rulings (squad-shaping)
- at: '2026-11-16T18:00:00Z'
  actor: planner-priti
  action: Folded S6 (StorageRulesets.verdict; FileSystemPackInspector implements PackReading)
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PackRulesets.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/GraphRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/FragmentRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PromptSlotRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PackRulesets.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/StorageRulesets.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/NotHeldRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/ReservedShardRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/ManifestRules.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/GraphRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/FragmentRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PromptSlotRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackCompositionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/rules/StorageRulesetsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRefusalsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackReadingTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/GraphRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/FragmentRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PromptSlotRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PackRulesets.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/StorageRulesets.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/NotHeldRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/ReservedShardRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/ManifestRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRefusals.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepEnhancementRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/RelationshipEndpointRule.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/GraphRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/FragmentRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PromptSlotRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackCompositionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/rules/StorageRulesetsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRefusalsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/StepEnhancementRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackReadingTest.java
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP07 - Content rules part 2, the storage rules, the compositions and PackRefusals

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
- Start with `spec-kitty agent action implement WP07 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.
- An edit to a file another work package owns is an **out-of-map edit**: one purpose, recorded in the review log.

## Objective

Finish the content rules, write the storage rules, compose both by name, make the one reader implement the domain
port `PackReading` (S6), and write the API edge's mapper. When this work package is done, `StorageRulesets.verdict`
over the one `TreeRead` (the read faults, `registration()` and the manifest gate), returned through `PackReading` and
mapped by `PackRefusals`, reproduces registration's golden answer, and the read faults plus `content()` reproduce the
compose read's, apart from the listed rows. **Nothing is switched yet.** Spec FR-001, FR-002, FR-008 (`PackRefusals`), FR-011 and FR-012; S1 to S3;
plan IC-06 (part 2).

What changed in the 2026-11-16 replan (`research/design-r3.md` §8): there is no `Surface` enum and no `pack(surface)`;
there is no `ReadRules` class, because read faults are the one reader's (WP05); the manifest, reserved-shard and
not-held rules are storage rules in `packs.infra.storage.rules` (S3); `PackVerdicts` is written in `pack-validation`
by WP09.

## Subtasks

### T035: `content()`'s gates over the read

- **The read faults are the reader's** (WP05). `content()` reads `PackContents` and never re-derives one.
- **The edges-read gate:** keep it. A graph that could not be read switches off the edge rules (`graphsRead`). The
  load's gate was `graphs.violations().isEmpty()`. Unify the gate on the one read's fact, and pin it with a
  composition test.
- **A schema-invalid file gets no semantic rule:** the content rules skip a file the read marked unreadable.

### T036: `GraphRules`

- **What it covers:** `drg_schema_error` and the endpoint rule, through slice F's bridge (`RelationshipEndpointRule` /
  `EndpointRule`; FR-011). The wire code `relationship_endpoint_not_allowed` stays.
- **What it reads:** the bound graphs `PackContents` carries from WP03's `GraphBinding`. The rule never builds a graph.
- **`RelationshipEndpointRule`:** becomes, or feeds, this rule, with its constants kept.
- **The compose read's endpoint prefix** (`readable`'s special case) is kept by the compose read's own mapping (WP12).

### T037: `FragmentRules` and `PromptSlotRules`

- **`StepEnhancementRules`** (13 fault kinds) becomes `FragmentRules`. One `Reason` per fault, so the paired emitters
  (`stepEnhancementFindings` / `...Violations`) go (squad finding 5).
- **Orphan fragments:** from the fragment facts of `PackContents` (a fragment file no enhancement names), reported
  once: the census does not list an orphan as not held as well (`unreportedFiles` dedup;
  `prompt-fragment-refusals.feature:15`).
- **The companion remap** to `prompt_fragment_invalid` (`DomainRepositoryService:~1199`) is a read fact (WP05).
- **Precedence:** a binding-level fault is named before a rule fault.
- **Prompt slots:** `prompt_slot_malformed`, and `prompt_slot_not_empty` as two constants (VIOLATION, and WARNING for
  an unversioned descriptor).
- **Single source:** the unversioned warning comes from `content()` only, never also from the reader (C-9).

### T038: The storage rules (`packs.infra.storage.rules`, S3)

- **`NotHeldRules`:** `file_not_held` WARNING per not-held file of the `TreeCensus`, with its kind and reason. Its
  detail matches WP02's (`<file>: not held (<reason>)`).
- **`ReservedShardRules`:** from the census's stamp fact, not an unreadable graph (C-4). Registration lists it last.
- **`ManifestRules`:** the manifest must be a regular file (`unreadable`), a rule of `holding()`; and
  `manifestMembership(TreeRead)`, a plain function that compares the census's listed set with the derived set the
  census already carries (WP05's reader computed it through `PackManifestDerivation`). It never calls
  `PackManifestDerivation` (T039 says when it runs).
- **Tokens:** from `StorageCodes` (WP04). The package names only the kernel, `packs.domain`, its own types and `java.*`.
- **Who names it (S6):** nothing outside `packs.infra.storage` and `deployable`. `packs.application` and
  `pack-validation` see these rules only through the `Rationale` that `PackReading` returns (T039).

### T039: The compositions

- **`PackRulesets.content()`** (`packs.domain.rules`): graph and endpoint, mission type, step enhancement and orphan
  fragments, prompt slot and the repeated asset id (WP06's holders and T036, T037). No glossary rule and no asset-escape
  rule: both are edge checks (S2; WP06 D1, D2), so `content()` names neither.
- **The types (U1),** each checked against the kernel as it is (`ModularRuleset<T>.must(Rule<T>)` takes a rule over
  the same subject; `Rule<T>.on(Function<? super S, ? extends T>)` returns a `Rule<S>`):

  ```java
  // packs.domain.rules.PackRulesets
  public static ModularRuleset<PackContents> content();
  // packs.infra.storage.rules.StorageRulesets
  public static ModularRuleset<TreeRead> holding();          // NotHeldRules, ReservedShardRules, the manifest a regular file
  public static ModularRuleset<TreeRead> registration() {
      return holding().must(PackRulesets.content().on(TreeRead::contents));
  }
  public static List<Reason> manifestMembership(TreeRead read);   // plain function
  public static Rationale verdict(TreeRead read) {                 // the storage layer's composed check (S6)
      Rationale judged = new Rationale(read.readFaults()).and(registration().check(read));
      Rationale gated = judged.isPassing() ? judged.and(new Rationale(manifestMembership(read))) : judged;
      return inRegistrationOrder(gated);
  }
  ```

- **The manifest gate is a plain function, not a `must` member** (A-D1, C-12): `must` is collect-all, and the kernel
  has no combinator that runs a rule only when the rules before it passed. A surface calls
  `manifestMembership(read)` only when `read.readFaults()` hold no `VIOLATION` and `registration().check(read)` is
  passing (`Rationale.isPassing()`), as `WholePackStore.loadStaged` does today. Write it once, as
  `StorageRulesets.manifestMembership`, call it only from `verdict`, and pin the condition in `StorageRulesetsTest`.
- **`verdict` and the port (S6, operator 2026-11-16, "Port, no rule change"):** `StorageRulesets.verdict(TreeRead)`
  joins the read faults, `registration()` and the gate into one kernel `Rationale` with `Rationale.and`, the kernel's
  union ("this rationale followed by another; neither is changed"). `FileSystemPackInspector` (WP05's file,
  out-of-map, recorded) then implements `PackReading`:
  `read(root)` = `readTree(root)`, then `new PackReadout(tree.contents(), tree.census().files(),
  tree.census().notHeld(), StorageRulesets.verdict(tree))`. `PackReading.read` is its only public method that returns
  a read (`STORAGE_RETURNS_DOMAIN`). `PackReadingTest`: over every golden pack, the readout's `Rationale` equals
  `verdict` over the same `TreeRead`, its `notHeld` equals the census's, and one walk is made.
- **Registration's wire order is built here (S6):** `PackRefusals` (`packs.application`) cannot name `StorageCodes`,
  so it cannot place the reserved shard and manifest membership by code. `verdict` returns its reasons in
  registration's order (`inRegistrationOrder`, a plain function beside it): the violations as graph faults by path,
  then the read and rule faults in reader order, then the orphans by path, the reserved shard, then manifest
  membership; the warnings in today's order, then the not-held files by path. Pin it against the golden in
  `StorageRulesetsTest`.
- **No flag, no enum.** The compose read runs `content()` on `read.contents()` inside storage; registration takes the
  readout's `Rationale` (WP12); validate mode appends placement's `Rationale` to it with `Rationale.and` (WP09). `PackRulesets.closure()` is added in
  WP09, once WP08's `CompositionRules` exists.
- **Order:** the wire order is the one `inRegistrationOrder` gives, not the composition's rule order, so `holding()`
  before `content()` in `registration()` does not move a finding.

### T040: `PackRefusals` (`packs.application`, the API edge)

- **Placement (squad-shaping Q5, the default taken):** `packs.application.PackRefusals` already holds `packRefusal` /
  `asPackNotFound`, the read services' mapping of `PACK_UNREADABLE` to `PACK_NOT_FOUND`. The wire refusal mapper joins
  that existing package-private class (one pack-refusal mapping per edge).
- **`PackRefusals.of(Rationale)`**, over the readout's `Rationale` (S6: `packs.application` names `PackReadout` and
  the kernel, never `packs.infra.storage.rules`, so `ONION_APPLICATION_DEPENDS_ON_DOMAIN_ONLY` holds unchanged):
  - the violations (`violations()`), in the order `verdict` gives, which is registration's order;
  - the problem detail `"The pack cannot be registered: N fault(s), the first at X."`;
  - the diagnostics: the warnings (`warnings()`), in the order given: today's order, then the not-held files by path.
- It reads `failures()` only, keeps the order it is given, and names no storage code; it never switches on
  `value()`.

### T041: Composition and mapper tests

- **`PackCompositionTest` (M-7):** one test per interaction:
  - a schema-invalid file gets no semantic rule;
  - a codec fault wins over a marker;
  - the companions of a refused enhancement are not orphans;
  - the edges-read gate;
  - the manifest gate (in `StorageRulesetsTest`, over `TreeRead`);
  - a binding-level fault comes before a rule fault;
  - a not-held file plus a manifest mismatch;
  - the two `prompt_slot_not_empty` codes;
  - `content()`'s rules are a subset of `registration()`'s (the forward subset by construction).
- **Golden equivalence:** for every golden pack, `PackRefusals` over the `Rationale` that `PackReading` returns
  (the one read's faults, `registration()` and the gate) equals registration's golden answer, apart from the S2 rows (rows 14, 15 and 38) and
  the rows already listed; the read faults plus `content()` give the compose read's golden answer. A gap here is a bug
  to fix now, before WP12.

### Carried from WP05 (validate mode's rationale until WP09)

- `FileSystemPackInspector` already implements `PackReading` (WP05), with the read faults alone as the readout's
  rationale, and `PackValidation` reads through it. When this WP widens the storage verdict to
  `StorageRulesets.verdict`, keep `PackValidation` on the read faults only: `PackPolicy.judge` still judges content
  itself until WP09, so feeding it the widened rationale would report each content finding twice. WP09 switches
  validate mode to the verdict and deletes `PackPolicy.judge`'s own content judging in the same step.

### Carried from WP06's review

- `PlantedFixtures` (`rules/PlantedFixtures.java:213`) copies every plant into a temp directory it never deletes:
  delete it after the run (or put it under `build/`).
- `ArtifactRulesTest.expecting()` drops whole plants that hold an asset; filter the expected findings by kind instead.

## Definition of Done

- `content()`, `holding()`, `registration()`, `manifestMembership` and `verdict` exist, with the types above, and no
  `Surface` enum anywhere.
- `FileSystemPackInspector` implements `PackReading`, and `PackReadingTest` passes.
- `PackRefusals` reproduces registration's golden answer, apart from the listed rows.
- The composition tests pass.
- `verifyAll` is green.
- A narrowed PIT run on `packs.domain.rules`, `packs.infra.storage.rules` and `PackRefusals` is at or above 85.

## Reviewer guidance

- Is any storage fact read by a `packs.domain.rules` rule? None may (S3).
- Is the manifest gate a plain function, run only when the read faults and `registration()` pass?
- Does `PackRefusals` name any `packs.infra` type, or re-order by a storage code? It may not (S6).
- Is there a single source for each warning?
