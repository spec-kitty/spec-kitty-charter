---
work_package_id: WP01
title: Phase 1 · The step enhancement is held, round-trips and composes
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- NFR-001
- C-001
- C-002
- C-003
- C-006
- C-007
- SC-003
- SC-006
planning_base_branch: issue-424-428-adr-0066-service-alignment
merge_target_branch: issue-424-428-adr-0066-service-alignment
branch_strategy: Planning artifacts for this mission were generated on issue-424-428-adr-0066-service-alignment. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-424-428-adr-0066-service-alignment unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-adr-0066-step-enhancement-service-alignment-01M6ZRYK
base_commit: 0b60fd2de345cafa1f43c1944761323e743a86fb
created_at: '2026-11-09T16:18:33.826763+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
- T007
phase: Phase 1 (before PR C)
agent: claude
history:
- timestamp: '2026-11-09T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementBinding.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepEnhancement.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PromptFragment.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementYaml.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementYamlTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementCompositionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDigestBaselineTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PlacedWriteTest.java
- contracts/charter/scenarios/step-enhancements.feature
- contracts/charter/changes/2026-11-09-step-enhancements-held.md
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepEnhancement.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PromptFragment.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBindings.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlFormats.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/ArtifactKind.java
- service/packs/src/test/resources/whole-pack/expected-red.yaml
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/KindBindingsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspectorTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackOpenPacksRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GeneratedPackTree.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTrip.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementYamlTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementCompositionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDigestBaselineTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PlacedWriteTest.java
- contracts/charter/scenarios/step-enhancements.feature
- contracts/charter/scenarios/activation-presets.feature
- contracts/charter/changes/2026-11-09-step-enhancements-held.md
role: implementer
tags: []
tracker_refs:
- '#424'
- '#428'
---
# WP01: Phase 1 · The step enhancement is held, round-trips and composes

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or read
`/home/user/spec-kitty-open-packs/packs/built-in/agent_profiles/java-jenny.agent.yaml` in full.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Make the charter **hold** every step enhancement and its `*.prompt-fragment.md` companions: the store reads them,
writes them back at their paths with equal meaning (ADR-0036), and the URN `step_enhancement:<id>` joins the compose
through its kind binding, so it wins or loses the layer merge and is selectable directly and through presets. This WP
raises **no** prompt refusal code (T-15): the content checks are WP03–WP05.

Implementation command:
`spec-kitty agent action implement WP01 --agent claude --mission adr-0066-step-enhancement-service-alignment-01M6ZRYK`

## Context

- **Concern:** IC-01 of `plan.md` (read it in full, with "Gates and how each stays green" and the ruled table
  "Operator decisions (plan point, 2026-11-09): ruled").
- **Requirements:** FR-001, FR-002, FR-003, NFR-001, C-002, C-003, C-006, C-007; SC-003, SC-006 (a held step
  enhancement is selectable directly and through presets, and its selection survives a re-write).
- **Rulings that bind this WP:** D7 (layout kept), D8 (ids unique per pack — enforced in WP03, but fixtures must
  already obey it), D11 (one companion per entry, any stem), D19 (KindBindingsTest:198-210 flips), D20 (`empty_kinds`
  entry), OD-10 rows 1–3 and row 10 (operator ruling A5, 2026-11-09), OD-11 (the reason text), A8 (no rule or load
  edit to `DomainRepositoryService`; its one WP01 edit is the A2-harness write seam below), A11 (winning copy), A12
  (`fileName` refuses), A2-harness (operator ruling, fifth analysis, 2026-11-09: a place-aware write seam for
  `DomainRoundTrip`, T005 step 3), T-02, T-03, T-04, T-11, T-12, T-13; third analysis A6 (qualified edge targets in
  fixtures) and A10 (the entity accepts zero entries); fifth analysis A6 (the three companion overrides, T004) and A13
  (when the bare target is refused, T002).
- **Read first:** `plan.md` IC-01; `spec.md` US1, US2; `data-model.md` (StepEnhancement, PromptFragment);
  `research.md` D7, D8, D11, D20, P8, P10; `contracts/README.md` "Other contract edits (IC-04)"; `quickstart.md` Flow 1.
  All paths in this prompt are repo-root relative; the mission directory is
  `kitty-specs/adr-0066-step-enhancement-service-alignment-01M6ZRYK/`.

### Files shared with other work packages

Two listed edits, both for the A2-harness seam (T005 step 3), each with an `out-of-map: <file> — <why>` line in the
commit message:

- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java` (owned by no WP) —
  a place-aware form of `write` (:884-911): the same checks and companion plan, but a create lands at a given
  pack-relative place instead of `newFile(directory, kind, elementId)` (:1125-1145, which calls `kind.fileName`). The
  place is normalised and refused, as `newFile` refuses, when it leaves the pack or is already taken, and also when
  its file name is not `<id>.<fileToken>.yaml` for the element (the rule `FileStems.mismatch` applies on read; sixth
  analysis A3); an existing file
  of that id (`locate`, :1108-1119) is replaced in place as today. `write` and `newFile` are otherwise unchanged.
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java` (owned by WP05,
  which runs after WP04, which runs after this WP) — a package-private `saveArtifact(PackIdentifier, CharterArtifact,
  String place)` beside `saveArtifact` (:210-231) that writes through the place-aware form, plus, if needed, its own
  private generic helper to capture the binding's DSO type (as `write`, :659-662, does for `writeArtifact`). It refuses
  a `Template` or a kind placed by id with `IllegalArgumentException`. Nothing else in the file (sixth analysis A2).

Later WPs (WP04, WP05) make listed edits to `FileSystemPackInspector`, `WholePackStore` and their
tests, which this WP owns. `StepEnhancementBinding` gets no later edit: the store records a companion fault with the
hard-coded kind `"companion"` (`FilesystemPackStore.java:813`) and `KindBinding` has no hook for it, so WP04 remaps it
in `DomainRepositoryService.assembly`'s graph-taking form (plan erratum E-4). No seam is needed in the binding.

## Project constraints (apply to every subtask)

- **Test first, red first (C-007).** Write the failing test or `@contract` scenario first, run it, and see it fail
  for the right reason (an assertion failure with every step bound, not a compile error or an undefined step). Then
  write the code. Record the red run in the commit message body (`red: <test> — <failure line>`).
- **Asserted expectations.** The only edits to existing assertions this WP may make are: D19
  (`KindBindingsTest:198-210` flips) and OD-10 rows 1–3 and 10 (below). Any other change to an existing assertion, expected
  value, scenario outcome or `expected-red.yaml` entry is forbidden: stop and escalate to the run lead (escalation
  class (c)). Extending a test scaffold (`GeneratedPackTree.Kind`, `DomainRoundTrip` pairing) is not an assertion edit.
- **PR C boundary (C-004).** Do not edit any PR C-owned file: `ActionContextWalk`, `GraphWalk`, `GraphProjection`,
  `EffectiveGraph*`, `LayerOverride`, `EffectiveActionContext`, the graph and context paths,
  `effective-graph-and-context.feature`, `EffectiveGraphSteps`, `ArtifactPolicyRefused`,
  `activation-deny-satisfied.feature`, `logical-model.md`, `InForceResolution`, `ClosureView`, `EdgeIndex`.
- **Hexagonal slices.** Entity in `packs.domain`; DSO, YAML and binding in `packs.infra.storage`.
  `HexagonalArchitectureTest` stays green.
- **No target field (C-003).** The enhancement's target is its `enhances` edge only; `NoArtifactFieldMintsAnEdgeTest`
  gates this once the kind is bound.
- **No disabled tests, no new suppressions.** Checkstyle limits: CC ≤ 15, NPath ≤ 1000, ≤ 60 lines per method,
  nested-if depth ≤ 1. No `@Disabled`, no `@WIP` left on a scenario this WP makes green.
- **No dependency added** (DIRECTIVE_051): `gradle/verification-metadata.xml` shows no diff.
- **Out of scope (C-005):** #429, #430, #431 (`org-charter.yaml`: if touched, cite #431 and escalate), #434, #435,
  #432, #433, core's renderers.
- **Gradle:** module tasks only, never `verifyAll` (the run lead runs it at closeout):
  `JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=../spec-kitty-open-packs <task>`.
  On a Maven Central `429`, wait and re-run; never turn dependency verification off.
- **Commits:** every commit message ends with these two lines, exactly:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01E5wJUD4c1fuEbXL3umMvKS`.
  No AI model name anywhere else in commits or files.

## Branch Strategy

- Planning branch: `issue-424-428-adr-0066-service-alignment`; final merge target: the same branch.
- Execution worktrees are allocated per computed lane from `lanes.json` (written by finalize-tasks); do not pick a base
  branch by hand. Dependencies are a claim gate: none for this WP, so it may start at once, in parallel with WP02.
- Phase 1 work: it lands before PR C merges, so keep off every PR C-owned file (above).

## Subtasks

### T001: Capture the NFR-001 digest baseline before any binding

**Purpose:** prove that packs without step enhancements keep a byte-identical source fingerprint (NFR-001, T-04). The
baseline must be captured on code that has **no** step-enhancement binding, so this subtask is committed first.

**Steps:**

1. On the WP branch, before any other change (so on the WP's base commit, which has no step-enhancement binding),
   compute `digestOfPack` for every pack at the pinned `openPacksRef=22c14b2d` that holds no `enhancements/` directory
   (at `22c14b2`: all five packs, `built-in`, `cave-kitty`, `event-driven`, `internal` and `qa`; none has an
   `enhancements/` directory; NFR-001), as `PackFileSetTest:100-109` pins a value captured earlier.
2. Create `PackDigestBaselineTest` in `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/`, tagged
   `open-packs` like the other pinned-corpus tests, with the five digests as constants and a comment naming the
   commit they were captured on (the WP's base commit) and the pin.
3. Run it green on the unchanged code. Commit it alone (`test(packs): pin the digests of the five pinned packs
   before the step-enhancement binding (NFR-001)`).

**Validation:** the test passes before T004 and must still pass, unedited, after T004–T006. If it goes red after the
binding is appended, the binding moved a fingerprint: fix the code (append, never insert), never the constants.

**Edge cases:** confirm the list against `../spec-kitty-open-packs/packs/` first (checked at planning: five pack
directories, none with `enhancements/`) and pin every pack the pin holds that has no `enhancements/` directory. Record
the list in the test's javadoc. If the checkout differs from the planned five, pin what it holds and say so in the
hand-off note.

**Positive control:** added in T006 (a fixture with one enhancement changes its digest).

### T002: Red scenarios — selection, registration, preset and sibling

**Purpose:** state the contract behaviour first (C-007, T-11), on fixtures that already satisfy every later rule
(T-12), so no later WP, in this mission or in the phase-2 mission, edits these outcomes.

**Steps:**

1. Create `contracts/charter/scenarios/step-enhancements.feature` (tag `@contract`), using only the step vocabulary of
   `contracts/charter/scenarios/README.md` (`UndefinedStepGuard` refuses an unbound step). Fixture shape, inline:
   - a **base pack** whose `missions/software-dev/` mission type has a step `plan` whose prompt declares
     `<!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->` empty;
   - an **enhancing pack** that holds no mission type, only
     `missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml` with one `prompt_fragments` entry
     `{slot: spdd:reasons-block, path: spdd-plan.prompt-fragment.md}`, that companion (plain Markdown body, no
     frontmatter, no marker), one `enhances` edge `step_enhancement:spdd-plan → base::action:software-dev/plan` in
     its graph document (the target **qualified** with the base pack's descriptor `name`, here `base`), and
     `depends_on` the base pack. A bare `action:software-dev/plan` is refused `pack_reference_unqualified` only
     when the base's graph declares the action node (`DomainRepositoryService.declaredWithoutFile`, :579-597, feeds
     `PackCompositionResolver.checkReferences`, `PackCompositionResolver.java:442-477`, bare branch :463-475); when no
     graph declares it, the bare form passes silently (third analysis A6, fifth analysis A13). Every two-pack fixture
     of this mission uses the qualified form, and the base's graph declares `action:software-dev/plan`; the bare form
     appears only in WP03's unit twin.
2. Scenarios:
   - **Selection (US2 AS1):** `PUT …/selections/step_enhancement/spdd-plan` answers `200` and the activation lists it.
     **Positive control in the same fixture:** a tactic selection answers `200`, showing a `404` comes from the kind,
     not a broken fixture. Today the enhancement selection answers `404 artifact_not_found`.
   - **Registration (US1 AS1):** registering the enhancing pack succeeds (today `422` "unmodelled").
   - **Sibling (US2 AS3):** two packs at one level defining `step_enhancement:spdd-plan` refuse with
     `pack_sibling_conflict`; the **positive twin**, two sibling enhancers of the same step with different ids,
     composes.
3. In `contracts/charter/scenarios/activation-presets.feature`, add a scenario (US2 AS2): a preset listing
   `activated_step_enhancements: [spdd-plan]`, applied, then the activation written again — the id is kept, not
   reported stale. Add only a new scenario; do not edit existing ones.
4. Run `:deployable:test --tests '*CharterContractSuite'` and record which scenarios fail and why.

**Validation:** each new scenario fails on an assertion (status or body), every step bound.

**Edge cases:** if a needed step does not exist in the vocabulary, prefer a combination of existing steps; adding glue
in `service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/` is out of this WP's `owned_files`, so
escalate before adding a step.

### T003: The entity, the fragment value, the DSO and the YAML format

**Purpose:** model the artifact (FR-001, FR-002) and prove its round trip by meaning (T-03) at unit level first.

**Steps:**

1. Red: `StepEnhancementYamlTest` (corpus style, as `GuardAndLifecycleYamlTest` does for guards): read an
   enhancement with two entries and two companions, write it, and compare meaning, **including each fragment body**.
   Add a **mutant test**: the same fixture with one written fragment altered and one dropped makes the round-trip
   assertion fail (`acceptance-criteria-non-vacuity`).
2. `StepEnhancement` (`packs.domain`, implements `kernel.domain.CharterArtifact`): `id`, `schemaVersion` (`"1.0"`),
   `promptFragments: List<Entry>`, `Entry {SlotId-like slot string, String path, PromptFragment fragment}`. Keep the slot
   as a validated string or a domain value for now; WP02 adds `kernel.markdown.SlotId` and WP03 may switch to it. **No
   target, range, anchor, position or priority field** (C-003, d3, d5, d7). No guards or emits (D6, #434). **The
   entity accepts an empty `promptFragments` list** (third analysis, A10): the absent-key shape (D12) is refused by
   WP03's `NO_FRAGMENTS` rule as `prompt_fragment_invalid`, never by the constructor, whose breach `collect` would
   record as a `domain` violation named by the id, not the file (`DomainRepositoryService.java:761-763`). Add a unit
   case: a DSO with no entries builds an entity with an empty list.
3. `PromptFragment` (`packs.domain`): the companion's path and its parsed Markdown body. Meaning is the canonical
   render (P7).
4. `StepEnhancementDso` carries each companion's body, so `binding.dsoOf(artifact)` exposes the bodies to the
   comparators. `StepEnhancementYaml` reads and writes the closed schema
   `contracts/packs/schemas/step-enhancement.schema.json`; its format joins `YamlFormats`.

**Files:** the three new classes above, `YamlFormats.java`, `StepEnhancementYamlTest.java`.

**Validation:** `StepEnhancementYamlTest` green; its mutant case fails the assertion it should.

**Edge cases:** CR or BOM in a companion is refused by `MarkdownCodec` (`MarkdownCodec.java:139-145`); this WP does
not map that to `prompt_fragment_invalid` (WP04 does) — let the generic companion refusal stand for now.

### T004: The binding, appended; the deferred branch deleted; scaffolding and gate entries in one commit

**Purpose:** bind the kind without moving any existing fingerprint (NFR-001) and keep every gate honest in the same
commit, because each of these turns red the moment the binding exists.

**Steps (one commit):**

1. `StepEnhancementBinding` (`infra.storage`): `idIsFileStem()` true; `placedAtItsFileName()` false (the type
   directory is not derivable from the id; the whole-pack write uses the held `PackLayout` place);
   `companionStructures()` = `{prompt-fragment}`. **Override `fileName` to refuse** (A12): the default
   (`KindBinding.java:138-140`, used by `PackLayout.pathOf` and `FilesystemPackStore.java:1127`) would put the file at
   the pack root. Add a test that a canonical layout cannot write one. `FileStems.mismatch` (`FileStems.java:40-51`)
   never calls `fileName` for a kind whose `placedAtItsFileName()` is false (the `||` at :45-47 short-circuits), so
   the store's stem rule still reads the file. **Override `fileToken()` and `schemaName()` to answer
   `step-enhancement`** (fourth analysis, A7): the defaults (`KindBinding.java:108-110`, `:118-120`) answer
   `kind().token()`, which is `step_enhancement` (`ArtifactKind.java:63`), and would break the stem suffix
   `"." + fileToken() + ".yaml"` of `FileStems.mismatch` (`FileStems.java:44`), `KindBindings.bySchema` (:87) and the
   listing. Because OD-10 row 10 skips this binding in the two `fileName()` loops, add **new positive assertions** (not
   edits) to `KindBindingsTest`, as it does for the mission type's binding (:73-74):
   `StepEnhancementBinding.INSTANCE.fileToken()` and `.schemaName()` are `"step-enhancement"`, and
   `KindBindings.bySchema("step-enhancement")` holds `StepEnhancementBinding.INSTANCE`. Seen red against the defaults
   first. **Override the three companion hooks** (fifth analysis, A6; `KindBinding.java:195-222`):
   `hasCompanions()` answers `true`; `companions(dso)` answers one `Companion` per `prompt_fragments` entry, in entry
   order, at the entry's `path`, Markdown form, structure `prompt-fragment`, carrying the body the DSO holds; and
   `withCompanions(dso, contents)` fills each entry's body from `contents` keyed by that path. Without them the store
   never reads or writes the fragments (they stay `unmodelled`), and `CompanionFilesTest.boundKindsDeclareTheirCompanions`
   (:284-292) requires `hasCompanions()` to hold exactly when `companions` is overridden.
2. Append it **last** to `KindBindings.ALL` (never insert: that protects the digest order).
3. Delete the deferred-companion branch at its three sites, not as dead code: `KindBindings`
   `DEFERRED_COMPANION_STRUCTURES` and `isDeferredCompanionStructure`; `PackFileSet.markdownHolder`;
   `FileSystemPackInspector`'s Markdown loop.
4. `GeneratedPackTree.Kind` gains `STEP_ENHANCEMENT`, **inserted before `ASSET`** (not appended): `ASSET` stays last
   on purpose (`GeneratedPackTree.java:119-120`), because `randomKind(false)` leaves the asset out by dropping the last
   kind (:507-510). Its plant writes `missions/<t>/enhancements/<id>.step-enhancement.yaml`, its fragment companion
   and an invalid twin, and **no** `enhances` edge: a single generated tree holds no base pack for a qualified
   `base::` target, and the digest oracle reads files, not edges (fourth analysis, A10). Without it `PackFileSetExactnessTest`'s `Kind.valueOf(omitted.name())` (:138) throws (T-02).
5. `expected-red.yaml` gains, under `empty_kinds`, `kind: step-enhancement`, `issue: "#428"`, with the **OD-11 reason
   text exactly**: "Step enhancements and their fragments round-trip in StepEnhancementYamlTest, WholePackStoreTest and
   the DomainRoundTrip fixture test; built-in has none at the pin (open-packs#175 ships them in packs/spdd)."
   (`WholePackExpectation:392` turns an entry for an unmodelled kind into STALE_BASELINE, so it lands with the binding.)
6. `ArtifactKind.java`: javadoc only (the "#428" note).
7. **Approved expectation edits (only these):**
   - D19: `KindBindingsTest:198-210` flips — the deferred branch becomes a companion of the bound kind.
   - OD-10 row 1: `KindBindingsTest:64` `hasSize(14)` → `hasSize(15)`, plus `kinds.get(14)` is `STEP_ENHANCEMENT`.
   - OD-10 row 2: `KindBindingsTest:85-90` display name (:85) and comment (:88-89) reworded: guard and lifecycle at
     12–13, the step enhancement last; `subList(12, 14)` (:90) unchanged.
   - OD-10 row 3: `FileSystemPackInspectorTest:380-401` (javadoc :380-384, assertion :394-396) — "no store holds … yet
     (#424)" becomes: the enhancement yaml **is listed under `ArtifactKind.STEP_ENHANCEMENT`** by `listing.byKind()`,
     and the fragment is **held as its companion** (assert it through the store's read of the enhancement, its
     `StepEnhancementDso` carrying the body; `byKind()` never lists a companion, so the fragment is not asserted there)
     (third analysis, A7). The javadoc and the method name (`…HeldByNoStore`) are reworded with it.
   - **OD-10 row 10 (operator ruling A5, 2026-11-09):** `KindBindingsTest:141-150`
     (`eachFileNameIsClassifiedToItsBindingsSchema`) and `:172-185` (`theDefaultFileNameClassifiesBackToTheKind`) call
     `binding.fileName(...)` on every binding of `KindBindings.all()`; the refusing `fileName` turns both red. Both
     loops skip the bindings whose files are not placed by id alone, named in the test as a set holding only
     `ArtifactKind.STEP_ENHANCEMENT`. Add a **positive control** in the same commit: `StepEnhancementBinding.INSTANCE.fileName("x")`
     refuses (it throws rather than answering a pack-root path), and the skipped set equals the set of bindings whose
     `fileName` refuses, so the skip cannot grow silently.

**Validation:** `:packs:test` green; `PackDigestBaselineTest` unedited and green; `:packs:wholePackRoundTrip` passes
with the new `empty_kinds` entry and no other change.

**Edge cases:** `ActivationFileCodec.versionOneKind` now accepts `step_enhancement` in a v1 document ([REN] C5) —
review note only (D4e), do not change it.

### T005: The whole-pack store and the round-trip comparators

**Purpose:** the store models the enhancement through its place and its companions through `companionFiles`, and the
round trip compares fragment bodies (FR-002, C-002, T-03, T-13).

**Steps:**

1. Red: in `WholePackStoreTest`, a temp-dir pack with an enhancement and two companions loads, writes back to the same
   paths, and compares by meaning. Extend `WholePackStoreTest.meaning` (:121-140) and
   `WholePackOpenPacksRoundTripTest.meaning` (:84-100) to compare fragment bodies through `binding.dsoOf(artifact)`;
   add the mutant case (one written fragment altered, one dropped → assertion fails).
2. `WholePackStore.load` models the enhancement through its `PackLayout` place and its companions through
   `companionFiles`. **No prompt code is raised.** A `*.prompt-fragment.md` that no entry names stays where the store
   already puts it: outside `companionFiles`, listed `unmodelled` (`WholePackStore.java:212,623-675`); `loadStaged`
   already refuses it (:244-248). WP04 reclassifies it.
3. **A2-harness (operator ruling, fifth analysis, 2026-11-09).** `DomainRoundTrip.writeAll` (:230-249) writes each
   artifact through `to::saveArtifact`, which creates it at `kind.fileName(id)` (`FilesystemPackStore.newFile`,
   :1125-1145); the refusing `fileName` (T004, A12) makes that fail, and the refusing `fileName` stays. Add the
   place-aware write seam ("Files shared" above): `FilesystemPackStore` writes a create at a given pack-relative place,
   and `DomainRepositoryService.saveArtifact(pack, artifact, place)` writes through it. In `DomainRoundTrip`:
   - Compute the source places (a (kind, id) → pack-relative path map from the census walk) before `writeAll`, which
     is static and today runs before `sourceIds` (:128-147), and pass them in. Define the not-placed-by-id set once
     as a `DomainRoundTrip` constant, used by `writeAll` and `writtenByKindAndId`, and assert it equals the set
     `KindBindingsTest` skips. Update the class javadoc (:42-43, "at the pack root, wherever its source lived") for
     these kinds (sixth analysis A4).
   - `writeAll` writes a kind that is not placed by id alone (the same set `KindBindingsTest` skips under OD-10 row 10:
     only `STEP_ENHANCEMENT` today) through the new seam, at the place its file was read from in the source pack
     (the source path the census already walks, keyed by kind and id); every other kind keeps `to::saveArtifact`.
   - `writtenByKindAndId` (:337-360) looks in subdirectories only for `placedAtItsFileName()` kinds; extend the
     candidate test so a nested file also pairs for a kind of that same set, so the step enhancement is paired where
     it was written.
   Red first: the fixture-level `DomainRoundTripTest` case over an enhancing pack (it fails today on the refused
   write), so the mask cannot first surface at #435. Add a new test class `PlacedWriteTest` (this WP's, in
   `infra.storage`) for the seam: a create lands at the given place with its fragments beside it; a place that climbs
   out of the pack and a place already taken are refused, as `newFile` refuses them; an existing id is replaced in
   place; a place whose file name is not `<id>.step-enhancement.yaml` is refused. The OD-11 reason text then holds as
   written.

**Validation:** the new tests pass; the mutant fails; `DomainRoundTripTest` green over the enhancing fixture, writing
the enhancement at `missions/<t>/enhancements/<id>.step-enhancement.yaml` and its fragments beside it.

**Edge cases:** one enhancement filling two slots with two companions — both held, each named by exactly one entry.

### T006: Composition, the URN in `defined`, same-id handling, and the NFR-001 positive control

**Purpose:** show the URN composes through the binding alone (A8) and that the "nothing changed" gate is not vacuous.

**Steps:**

1. Red: `StepEnhancementCompositionTest` (new, `infra.storage`):
   - the URN `step_enhancement:spdd-plan` is in `defined` after `DomainRepositoryService` composes the two-pack
     fixture (qualified target, A6) (`assembly` iterates `KindBindings.all()` at :700-702; `compositionFacts` adds every
     `authoredInGraphs()` kind at :399-401). **Do not edit `DomainRepositoryService`** beyond the A2-harness overload of T005 (A8; WP05 owns the file).
   - **A11:** a same-id enhancement at a higher level replaces the lower one by layer override (ADR-0048 d10); a
     sibling contest settled by a descriptor `overrides` entry (C15, `LayerMerge.java:319-337`) leaves one winner.
     One test each. Step enhancements are not excluded from layer override.
2. **NFR-001 positive control:** a fixture pack with one enhancement has a different `digestOfPack` from the same pack
   without it; editing only a fragment's bytes changes the fingerprint.
3. Run the T002 scenarios: selection, registration, preset and sibling must now be green.

**Validation:** `StepEnhancementCompositionTest` green; `PackDigestBaselineTest` green and unedited; T002 scenarios
green.

**Edge cases:** selection and holding do not depend on being in force (OD-6 is PR C's): the selection scenario asserts
the activation lists the id as selected, not that it is in force.

### T007: The `### Changed` fragment for the held kind

**Purpose:** C-001 — every contract behaviour change lands with a dated fragment (exactly one `###` block per file,
`contracts/charter/changes/README.md`). The fragment claims **only** what this WP changes on the wire, and each claim
has a red-first scenario from T002.

**Steps:**

1. Create `contracts/charter/changes/2026-11-09-step-enhancements-held.md` (rename to the day you write it, same slug)
   with one `### Changed` block listing (C-12), each item backed by the T002 scenario named:
   - `PUT /activations/{activationId}/selections/step_enhancement/{artifactId}` answers `200` for a held enhancement
     instead of `404 artifact_not_found` (selection scenario, US2 AS1);
   - preset `activated_step_enhancements` ids are kept, no longer reported stale, when the activation is written again
     (preset scenario in `activation-presets.feature`, US2 AS2);
   - a registration of a pack carrying step enhancements is accepted (today `422` "unmodelled",
     `paths/packs_packId.yaml:33-34`), and the whole-pack write keeps the enhancement files (registration scenario,
     US1 AS1);
   - and this sentence: "A selected step enhancement is still reported not in force until PR C's E6 amendment lands
     (operator ruling OD-6). What `listEffectiveArtifacts`, `getActivationSkippedReport` and
     `ActivationChange.dropped` show for it is delivered, with its scenarios, by PR C and the phase-2 mission (#438)."
   Name #428, and #424 as Refs.
   Do **not** claim that the effective artifacts, the skipped report or `ActivationChange.dropped` carry
   `step_enhancement` items: under E6 as built no step enhancement is in force (OD-6), and no scenario here shows it.
2. Run `:contracts:check` (changelog fragments, lint, compatibility with `-PcontractBaseline=none`).

**Validation:** `checkContractChangelogFragments` green; every claim in the fragment maps to a T002 scenario that was
red first and is now green.

## Test strategy

- Red first per subtask, as stated. Positive controls: the tactic selection (T002), the digest change on a fixture
  with one enhancement and on a fragment byte edit (T006). Non-vacuity: the round-trip mutant (T003, T005).
- Fixtures follow the two-pack `spdd` shape and every later rule (exactly one `enhances` edge, stem = id, named
  companion, empty base slot), so WP03–WP05 and the phase-2 mission (#438) never edit these outcomes (T-12).

## Gates (keep green, honestly)

- `KindBindingsTest` (D19 flip; OD-10 rows 1–2 and 10), `FileSystemPackInspectorTest` (OD-10 row 3),
  `CompanionFilesTest` (unedited: the three companion overrides, A6), `DomainRoundTripTest` and `PlacedWriteTest`
  (A2-harness).
- `PackFileSetExactnessTest` (through `GeneratedPackTree.Kind.STEP_ENHANCEMENT`; appending protects digest order
  only), `PackFileSetTest`, `PackDigestBaselineTest`.
- `WholePackRoundTripTest` / `:packs:wholePackRoundTrip`: the expected-red `empty_kinds: step-enhancement` entry with
  the OD-11 text, in the binding's commit.
- `:packs:openPacksRoundTrip`, `NoArtifactFieldMintsAnEdgeTest`, `HexagonalArchitectureTest`, `ReservedRefusalCodesTest`
  (unchanged: this WP names no prompt code), `WireTokenParityTest` / `ActivationWireTokenParityTest` (no enum change),
  `OperationCoverage` / `OperationCoverageTest`, `checkRefusalExamples`, contract lint, compatibility and
  changelog-fragment checks.
- Commands: `:kernel:test`, `:packs:test` (all of it, not a filter), `:packs:openPacksRoundTrip`,
  `:packs:wholePackRoundTrip`, `:deployable:test --tests '*CharterContractSuite' --tests '*HexagonalArchitectureTest'
  --tests '*ReservedRefusalCodesTest'`, `:activations:test`, `:contracts:check`, and
  `:kernel:spotlessCheck :packs:spotlessCheck :deployable:spotlessCheck` for the touched modules.

## Definition of Done

- Every subtask done and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; T001 was committed before T004.
- `PackDigestBaselineTest` constants are the ones captured before the binding, unedited.
- No assertion edit beyond D19 and OD-10 rows 1–3 and 10; no disabled test; no new suppression.
- Changes stay inside `owned_files`, plus the two A2-harness listed edits (`FilesystemPackStore`,
  `DomainRepositoryService`), each with its `out-of-map` line.

## Risks

- Inserting the binding instead of appending moves every pack's digest: the baseline test catches it.
- `DomainRoundTrip`'s pairing mask hides a mis-placed write until #435: the fixture-level test closes it now.
- The deferred branch deletion touches `FileSystemPackInspector`'s Markdown loop; keep the other kinds' behaviour
  (existing tests unchanged).

## Reviewer guidance

- Check the T001 commit precedes the binding commit and its constants were never edited.
- Check `KindBindings.ALL` was appended, `fileName` refuses, and no target field exists on the entity.
- Check the expected-red reason is the OD-11 text verbatim.
- Check every assertion edit is one of D19 / OD-10 rows 1–3 and 10, and nothing outside `owned_files` changed except
  the A2-harness seam (a place-aware create in `FilesystemPackStore`, one `saveArtifact` overload in
  `DomainRepositoryService`), which refuses a place outside the pack or already taken.
- Check every two-pack fixture's `enhances` target is qualified (`base::action:…`) and the base is in `depends_on`.
