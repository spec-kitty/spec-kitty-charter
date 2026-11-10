---
work_package_id: WP04
title: Phase 1 · Enhancement and fragment refusals at validate, load and registration; raised redefined
dependencies:
- WP03
requirement_refs:
- FR-004
- FR-005
- FR-006
- FR-011
- FR-012
- C-001
- C-003
- C-004
- C-007
- SC-002
- SC-004
planning_base_branch: issue-424-428-adr-0066-service-alignment
merge_target_branch: issue-424-428-adr-0066-service-alignment
branch_strategy: Planning artifacts for this mission were generated on issue-424-428-adr-0066-service-alignment. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-424-428-adr-0066-service-alignment unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-adr-0066-step-enhancement-service-alignment-01M6ZRYK
base_commit: 6cb6fe1b995283b23f39ae33274c83c8dda1cf68
created_at: '2026-11-09T18:05:58.886627+00:00'
subtasks:
- T016
- T017
- T018
- T019
- T020
- T021
phase: Phase 1 (before PR C)
agent: claude
history:
- timestamp: '2026-11-09T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/schemas/CharterRefusalCode.yaml
create_intent:
- contracts/charter/scenarios/prompt-fragment-refusals.feature
- contracts/charter/changes/2026-11-09-prompt-fragment-invalid-raised.md
- contracts/charter/changes/2026-11-09-prompt-fragment-checks.md
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyStepEnhancementTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementSchemaDescriptionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementLoadChecksTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackFindingCode.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyStepEnhancementTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementSchemaDescriptionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementLoadChecksTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ContractDescriptionsTest.java
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/scenarios/errors.feature
- contracts/charter/scenarios/prompt-fragment-refusals.feature
- contracts/_shared/schemas/Problem.yaml
- contracts/_shared/schemas/RefusalViolation.yaml
- contracts/packs/schemas/step-enhancement.schema.json
- contracts/packs/file-kinds.yaml
- contracts/charter/changes/2026-11-09-prompt-fragment-invalid-raised.md
- contracts/charter/changes/2026-11-09-prompt-fragment-checks.md
role: implementer
tags: []
tracker_refs:
- '#428'
---
# WP04: Phase 1 · Enhancement and fragment refusals at validate, load and registration; raised redefined

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or read
`/home/user/spec-kitty-open-packs/packs/built-in/agent_profiles/java-jenny.agent.yaml` in full.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Put WP03's pure rules (FR-004..FR-006, including **R-OVR**: no `overrides` edge from a step enhancement) on the wire
as `prompt_fragment_invalid`, in validate mode and at the one load seat both the registration and every composing read
reach: `DomainRepositoryService.assembly` given the pack's graph read (plan erratum E-1). The wire effect is stated
once in `contracts/README.md`, "How a load fault reaches the wire (canonical)": read it and build exactly that. In the
same commits, apply operator ruling **OD-3 (a)**: `prompt_fragment_invalid` and `drg_schema_error` become
`raised: true` (this WP is the single owner of the `drg_schema_error` flip), and the off-wire gate, the `_shared`
texts and the raised-code scenario table say so. This WP becomes the owner of the shared contract hot files
(`CharterRefusalCode.yaml`, `ReservedRefusalCodesTest`, `errors.feature`); WP05 makes listed edits to them later in
this mission, and the phase-2 mission (#438) after this mission merges.

Implementation command:
`spec-kitty agent action implement WP04 --agent claude --mission adr-0066-step-enhancement-service-alignment-01M6ZRYK`

## Context

- **Concerns:** IC-02 (wiring: "Where", "Faults of the companion itself", "Orphans", "Precedence") and IC-04 (contract
  delta A, the parts for `prompt_fragment_invalid`, the raised definition, the reserved paragraph and the schema
  description) of `plan.md`. Read both in full, plus OD-3, OD-10 and the gates table.
- **Requirements:** FR-004, FR-005, FR-006 (on the wire), FR-011 (one code + the raised definition), FR-012 (schema
  description), C-001, C-003, C-004 (no PR C-owned file; the shared hunks are reconciled at the rebase), C-007;
  SC-002, SC-004 (for this code).
- **Rulings that bind this WP:** OD-3 (a) (build-to default confirmed: `PackElementViolation` with `kind` = token; the
  definition of raised amended; `drg_schema_error` flips; `OFF_WIRE`, `offWireRules`, `_shared` texts amended); OD-10
  rows 4, 6, 7, 8, 9; R-OVR; the A8 fixture handling; D10 (description broadened to "an invalid step enhancement or
  prompt fragment"); D11; D12; D19 (met by the YAML flags; erratum E-2);
  C-07 (reword `CharterRefusalCode.yaml:165-166`); OD-1 and OD-2 are **not** applied here (the phase-2 mission (#438) does, with
  `prompt_slot_missing` and the integrity check); T-07, T-08, T-15, T-21.
- **Third-analysis rulings and errata (2026-11-09):** **A1** (operator ruling: id ≠ file stem is a schema violation
  through the existing `idIsFileStem` path, not `prompt_fragment_invalid`; the schema description says so);
  **A2** (operator ruling: the second same-id enhancement is withheld from `Pack.Builder` and recorded as
  `STEM_REPEATED` → `prompt_fragment_invalid` at its file, at load and registration); E-4 (A3: the companion-fault
  mapping sits in the graph-taking `assembly`, by file); E-5 (A4: no orphan in a directory holding a refused or
  withheld enhancement; every fault's pointer per data-model.md "Where each enhancement or fragment fault points");
  E-6 (A6: qualified targets); E-8 (A9: validate mode's orphan is `COMPANION_ORPHAN` from the facts); A12 (registration
  twins for the duplicate id, a non-step target and an absent `prompt_fragments` key).
- **Fourth-analysis rulings (operator, 2026-11-09):** **OD-10 row 11** (A1: `PackFindingTest`'s `LOAD_PATH_BLOCKING`
  gains `PROMPT_FRAGMENT_INVALID` in the commit that adds the code; listed in the PR); **R-REL** (A4: the relationship
  write refuses, before commit, an edge that breaks a held step enhancement's R-OVR or single-`enhances`-to-`action:`
  rule, `422 prompt_fragment_invalid`; plan ruled table and IC-02 "Where"). Fourth-analysis fixes folded here: A2 (rule
  interactions, single-edge duplicate-id fixture), A3 (`PackContents` carries the facts), A9 (the validate-mode
  duplicate-id mechanism), A14 (line refs).
- **Read first:** `plan.md` IC-02, IC-04, OD-3; `contracts/README.md` "`CharterRefusalCode` changes" and "Other
  contract edits (IC-04)"; `data-model.md` "How a load fault reaches the wire"; `research.md` P3, P8, P9;
  `WholePackStore.java:183-225, 236-249, 471-481, 623-675`; `DomainRepositoryService.java` `readable` (:599-608),
  `assemble` (:666-670), `assembly` (:684-728) and `collect` (:736-765, `builder.artifact` at :759);
  `FilesystemPackStore.java` `PackRead` (:143-150), `readKind` (:675-705) and `withCompanions` (:785-819, the hard-coded
  `"companion"` at :813); `Pack.java` `guardKind` (:139-154, the same-id rule :148-152); `ReservedRefusalCodesTest.java:35-131, 189-194, 280-345` (the last
  block is the `NamesEveryCode` probe: never edit it); plan errata E-1 to E-3.

### Files shared with other work packages

These files are owned by another WP; make only the edits named and record a one-line rationale in the commit message
(`out-of-map: <file> — <why>`). `lanes.json` write scopes come from `owned_files` only, so these listed edits are the
complete list; tasks.md's hot-files table repeats it.

- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java` (owned by WP05,
  which follows this WP and rebases on it) — E-1, the one seat of the rule faults: add an `assembly` form that also
  takes the pack's `FilesystemPackStore.GraphRead`. In this order:
  1. build `StepEnhancementFacts` from `read.elements(StepEnhancementBinding.INSTANCE)` (each `Stored` file and DSO,
     the companion bodies' canonical text) and the graph read, with no orphan set (A9), the unparsed step-enhancement
     files taken from `read.violations()` (A4); run `StepEnhancementRules`;
  2. derive a judged copy of the `PackRead` record (`FilesystemPackStore.java:143-150`, a package-visible record):
     - **A3 (E-4):** each read violation of kind `companion` whose file ends in `.step-enhancement.yaml` gets the kind
       `prompt_fragment_invalid` (the token from `PackPolicy`), same file, same reason, same position. The store
       hard-codes `"companion"` (`FilesystemPackStore.java:813`) and `KindBinding` has no hook, so the binding cannot do
       it; `FilesystemPackStore.java` (beyond WP01's A2-harness write) and `KindBinding.java` are not edited by WP04;
     - **A2 (operator ruling):** every file the rules answer `STEM_REPEATED` for is dropped from the copy's
       step-enhancement elements, so `collect` (:736-765) never reaches `builder.artifact` (:759) for it and
       `Pack.guardKind`'s same-id rule (`Pack.java:148-152`) cannot breach at `build()`;
  3. call the three-argument `assembly` (:684) with the copy;
  4. record each rule fault through `PackPolicy` as a `PackElementViolation` at its file with
     `kind = prompt_fragment_invalid`, appended **last** (E-3). The edge rules run only when the graph read has no
     violation.

  `assemble` (:666-670) reads the graphs (`resolved.store.readGraphs(resolved.key)`) and calls that form, so
  `readable` and every composing read see the faults. Keep the three-argument `assembly` for `ArtifactFiles` (:41) and
  the tests (`MissionTypeBindingTest`, `TemplateStoreTest`); it keeps the generic `companion` kind. No other edit to the
  file; `readable` and `collect` are unchanged. WP05 later adds a `List<PackFinding> warnings` out-parameter to this
  form (A8).
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore.java` (owned by WP01) — move
  `store.readGraphs(key)` above the assembly and call the graph-taking `assembly` form (the `drg_schema_error`
  throw on a graph violation stays where it is); reclassify an orphan `unmodelled` entry under
  `prompt_fragment_invalid` in `loadStaged`, **except** a fragment whose `enhancements/` directory holds a
  `*.step-enhancement.yaml` that is **not held**: refused by the store (it is absent from `read.elements`, e.g. a
  remapped `companion` violation) or withheld (`STEM_REPEATED`). A rule fault on a **held** enhancement
  (`EDGE_COUNT`, R-OVR, a repeated slot and so on) does **not** exclude its directory, so the test is "the yaml is not
  among the held step enhancements", never "the yaml carries a violation" (fifth analysis A5, matching WP03's facts,
  data-model's orphan row and research P8). A companion fault drops the enhancement from `read.elements`, so `companionFiles` (:623-644) stops
  modelling all its companions, and reclassifying them would report named fragments as orphans (A4, E-5). Such a
  fragment stays an `unmodelled` entry, as every file of a refused artifact does (the refused yaml is listed
  `unmodelled` too, existing behaviour, `WholePackStoreTest:752-771` asserts with `contains`). No rule fault is
  recorded here.
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java` — run the rules
  in FULL validate mode, the facts carrying every fragment the inspector read (so `COMPANION_ORPHAN` catches orphans:
  validate mode has no unmodelled list, A9); map its own companion read of a `prompt-fragment` file
  (`readCompanion`, :332-337) to `prompt_fragment_invalid`. Its stem rule (`misnamedArtifact`, :470-479) already
  refuses id ≠ stem as a `const` violation at `/id` (A1): no edit for that.
- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackContents.java` (owned by no WP; fourth analysis,
  A3) — validate-mode findings come only from `PackPolicy.judge(PackContents)` (`PackPolicy.java:162-188`), and the
  9-component record (`PackContents.java:25-34`, built by `FileSystemPackInspector.inspect`, :170-185) has no channel
  for the facts. Add a `StepEnhancementFacts` component (filled in FULL scope, empty in `LOAD_PATH` scope) and **keep a
  9-argument constructor** that passes empty facts, so the existing call sites (`Judged`, `PackPolicyOrderTest`,
  `PackPolicyRulesTest`, `PackPolicyTest`, `PackValidationTest`, `FileSystemPackInspectorTest`, `ImpactRelations`,
  `PackValidation`, …) compile unedited. No other change.
- `service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackFindingTest.java` (owned by no WP) — **OD-10 row
  11** (operator ruling A1, 2026-11-09): in the commit that adds `PackFindingCode.PROMPT_FRAGMENT_INVALID` (T017), add
  it to `LOAD_PATH_BLOCKING` (:17-24); otherwise `aValidateOnlyCodeHasNoWireValue` (:67-77) expects `refusal()` to
  throw for it. No other assertion of the file changes; list the edit in the PR.
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipRegistrationService.java` and
  `service/packs/src/test/java/ai/speckitty/charter/packs/application/RelationshipRegistrationServiceTest.java` (owned
  by no WP of this mission; the service is WP12's of PR A, the test in no lane; neither PR C-owned, C-004 checked) —
  **R-REL** (T019 step 5): the refusal before the commit, and new test cases only.
- `service/web-transport/src/main/java/ai/speckitty/charter/web/RefusalHandler.java` (owned by no WP; in no PR C lane)
  — **R-REL** (T020): `STATUS_BY_OPERATION` answers `PROMPT_FRAGMENT_INVALID` as `422` for `putPackRelationship` only,
  matching the new `operations` entry (`ReadConsistencySteps` holds the two equal). The load codes keep their 500.
- `contracts/charter/paths/packs_packId_relationships.yaml` (owned by no WP; WP11's of PR A) — **R-REL** (T020): the
  `422` description names `prompt_fragment_invalid` for an edge that would break a held step enhancement's edge rules.
- `StepEnhancementBinding.java` (WP01) — **no edit** (withdrawn, E-4): it cannot change the store's companion kind.
- `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspectorTest.java` — OD-10 row 4
  only, plus its twin test, plus the **A8 setup** (ruled 2026-11-09): add a graph document holding the single
  `enhances` edge (`step_enhancement:spdd-specify → built-in::action:software-dev/specify`, qualified as every phase-1
  fixture is, A6) to the setup of both `SPDD_ENHANCEMENT` tests (fixture :371-378, tests :385-401 and :403-417), so
  the FR-006 edge rule finds what it needs. Setup only. **If any assertion
  beyond OD-10 rows 3–4 must still change, stop and ask the operator** (through the run lead); do not edit it.
- `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackStoreTest.java` — new cases only.

## Project constraints (apply to every subtask)

- **Test first, red first (C-007);** scenarios before code for every contract behaviour.
- **Raise in the same commit (T-15).** The commit whose main class first names `RefusalCode.PROMPT_FRAGMENT_INVALID`
  also flips its `raised` flag in `CharterRefusalCode.yaml`; never before, never after. `ReservedRefusalCodesTest`
  reads the flags, so the flip is the whole move: no list in the test is edited (E-2). The same commit carries the
  `drg_schema_error` flip, the amended definition of `raised`, OD-10 rows 6 (the two table rows, fifth analysis A4),
  8 and 9, and the A5 paragraph rewrites (T017).
- **Approved expectation edits — only these (OD-10, D19):**
  - Row 4: `FileSystemPackInspectorTest.java:403-417` — validate mode answers `prompt_fragment_invalid` for the
    frontmatter fragment and the blank one; the blank orphan is refused at the staged load (A9); add a twin test.
  - Row 6: `errors.feature:192-238`, the table "Then the contract says exactly these refusal codes are raised by an
    operation:" gains `prompt_fragment_invalid` and `drg_schema_error` (as OD-3 (a) defines raised).
  - Row 7: `errors.feature:171-191` prose, including the stale "no operation reaches the whole-pack load" (:186-188).
  - Row 8: `ReservedRefusalCodesTest.java:48-52` javadoc (the off-wire paragraph) corrected; `:69-72` `OFF_WIRE` and `:106-131`
    `offWireRules()` changed per OD-3 (a).
  - Row 9 (A3, approved 2026-11-09): `ReservedRefusalCodesTest.java:189-194` — `unraisedCodes().contains(
    "DRG_SCHEMA_ERROR")` becomes `raisedCodes().contains("DRG_SCHEMA_ERROR")`, with its comment (:191-192) saying the
    code is raised as a registration violation code; in the same commit as the YAML flag and the amended definition.
  - Row 11 (operator ruling A1, 2026-11-09): `PackFindingTest.java:17-24` — `LOAD_PATH_BLOCKING` gains
    `PROMPT_FRAGMENT_INVALID`, in T017's commit. (WP05 makes the slot-code half of row 11.)
  - D19: refusal-description wording in `CharterRefusalCode.yaml` and its pinned tests. (D19's "move to raised" is the
    YAML flag; the `NamesEveryCode` probe at :290-345 is never edited, E-2.)
  - Setup, not an assertion edit: the A8 graph document in `FileSystemPackInspectorTest` (see "Files shared").
  Any other assertion edit — including `PromptFragmentContractsTest:53-55` and `:262-283`, which stay as they are
  (T-21) — is forbidden: stop and escalate to the run lead.
- **Codes chosen in one place.** `PackPolicy` names the `RefusalCode` constants; nothing calls
  `PackFindingCode.refusal()` outside `PackFinding`. `WholePackStore.load` never throws for a prompt fault (its
  partial-failure contract, :53-56).
- **PR C boundary (C-004):** no PR C-owned file (list in WP01). `CharterRefusalCode.yaml` and
  `ReservedRefusalCodesTest` are also edited by PR C (WP28 `action_not_found`, WP29 `artifact_required` text): keep
  your edits in separate hunks; they are reconciled at the post-PR C rebase (A10).
- No disabled tests, no suppressions; Checkstyle limits. No dependency added.
- **Gradle:** `JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=../spec-kitty-open-packs <task>`;
  never `verifyAll`.
- **Commits** end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01E5wJUD4c1fuEbXL3umMvKS`; no AI model name elsewhere.

## Branch Strategy

- Planning branch and merge target: `issue-424-428-adr-0066-service-alignment`.
- Lanes from `lanes.json`. Claimable once WP03 is approved or done (WP03 depends on WP01 and WP02).

## Subtasks

### T016: Red — registration and read scenarios, and unit tests at each moment

**Purpose:** the wire behaviour first (C-007, T-07, T-11).

**Steps:**

1. Create `contracts/charter/scenarios/prompt-fragment-refusals.feature` (`@contract`, README vocabulary only). On the
   WP01 two-pack fixture shape (the `enhances` target qualified, `base::action:software-dev/plan`, with `depends_on`
   the base, A6), one scenario per rule family, each beside its passing twin (SC-002). Each scenario asserts
   `Then the field at "/violations/0/code" is "…"` and `Then the field at "/violations/0/pointer" is "…"` with the
   pointer data-model.md "Where each enhancement or fragment fault points" gives (A4, E-5). Assembly order puts the
   fault at `/violations/0`; a store-refused enhancement also lists its yaml and companions as `unmodelled` after
   it, so those scenarios assert no count:
   - a blank fragment, a fragment with frontmatter → `422 validation_failed`, code `prompt_fragment_invalid`, pointer
     the **enhancement yaml** (`missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml`: the store's
     companion read refuses it at the yaml, `FilesystemPackStore.java:813`, remapped by E-4), and
     `Then the field at "/violations/0/detail" contains "spdd-plan.prompt-fragment.md"`;
   - a fragment with a slot marker → code `prompt_fragment_invalid`, pointer the **fragment**
     (`…/spdd-plan.prompt-fragment.md`; the enhancement is held, the rule names the companion);
   - an enhancement with two `enhances` edges; a directory that disagrees with the edge's type; **an `enhances` edge
     whose target is not a step (`tactic:x`, A12)**; **an enhancement with no `prompt_fragments` key (D12, A12)** →
     code `prompt_fragment_invalid`, pointer the enhancement yaml; each twin registers;
   - **duplicate id (operator ruling A2, A12):** the base pack also declares a `research` mission type with one step;
     the enhancing pack holds `missions/research/enhancements/spdd-plan.step-enhancement.yaml` and
     `missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml`, each with its own companion, and its graph
     holds **exactly one** `enhances` edge, `step_enhancement:spdd-plan → base::action:research/<step>` (the first
     file's step; fourth analysis A2: with two edges `EDGE_COUNT` on the research yaml would sort first, and with one
     edge to the software-dev step the research yaml would get `DIRECTORY_MISMATCH`) → `422 validation_failed`, code
     `prompt_fragment_invalid`, pointer the `missions/software-dev/…` yaml (the second in path order,
     `PackPolicy.comparePaths`), and **not** a single `pack` violation from a thrown
     `VALIDATION_FAILED` (the behaviour after WP01 and before this WP: `Pack.guardKind`, `Pack.java:148-152`, breaks `build()` in
     `WholePackStore.load`, :206-210, and `loadRefusingAsInvalid`, :472-481, answers one violation at pointer `pack`).
     Twin: the second enhancement renamed `spdd-research` (file, id and companion), with its own single `enhances`
     edge to `base::action:software-dev/plan` (a renamed enhancement needs its own edge), registers;
   - **id ≠ file stem (operator ruling A1):** `spdd-plan.step-enhancement.yaml` declaring `id: spdd-other` → `422
     validation_failed` at the yaml with a **schema** violation, asserting the code is not `prompt_fragment_invalid`;
     twin with `id: spdd-plan` registers;
   - **R-OVR:** an enhancement that is the source of an `overrides` edge (to its own mission type,
     `mission_type:software-dev`) beside its single `enhances` edge → `422 validation_failed`, violation code
     `prompt_fragment_invalid` at the enhancement file; its twin, with only the `enhances` edge, registers;
   - **FR-005, both shapes:** an enhancement naming one slot twice, and one naming a missing companion → `422
     validation_failed` with violation code `prompt_fragment_invalid` at the file; an entry whose `slot` breaks
     `<namespace>:<name>` (e.g. `Bad:Id`) → `422 validation_failed` with the step-enhancement **schema** violation at
     the file, **not** `prompt_fragment_invalid` (assert the code is not `prompt_fragment_invalid`), as
     `prompt_fragments: []` answers;
   - a **multi-fault** pack (four faults in four files) → the registration lists all of them,
     `holds exactly 4 item(s)` at `/violations`, each at its file (T-07). The four faults are ones whose enhancements
     **stay held**, so no `unmodelled` entry joins them and the count is exact (A4, E-5): a slot marker in fragment
     `a.prompt-fragment.md` (pointer the fragment), two `enhances` edges on `b` (pointer `b`'s yaml), an `overrides`
     edge from `c` (pointer `c`'s yaml) and a directory mismatch on `d` (pointer `d`'s yaml), in four enhancements of
     one enhancing pack. A store-refused fault (blank, frontmatter, missing companion) is never part of a counted
     fixture;
   - an orphan fragment beside a valid pair → refused at registration under `prompt_fragment_invalid`, pointer the
     orphan; twin without the orphan registers. (The A4 case — the companions of a store-refused enhancement are not
     reported as orphans — is a `StepEnhancementLoadChecksTest` case in step 2, since the scenario vocabulary cannot
     say "no violation at this pointer has this code".);
   - **read path** (moved here from IC-05's list, since it needs no PR C surface): a closure holding a faulty pack
     answers `GET /offering` with `500 pack_unreadable` whose detail names the faulty file. The fault is an **edge**
     fault (the R-OVR `overrides` edge, or two `enhances` edges), not a binding-level one, so the scenario fails unless
     the composing read runs the edge rules (E-1). Beside it, a positive control: the same closure with the twin pack
     answers `200`;
   - **relationship write (operator ruling R-REL):** on the registered twin pack (one valid `spdd-plan`), `PUT
     /packs/<enhancing>/relationships` with the remembered ETag and an `overrides` edge from
     `step_enhancement:spdd-plan` to `mission_type:software-dev` → `422`, `Then the field at "/code" is
     "prompt_fragment_invalid"`, and a read of the pack keeps its ETag (nothing written); a second case PUTs a second
     `enhances` edge from `step_enhancement:spdd-plan` → `422` with the same code. Twin: the fixture also holds tactics `a`
     and `b` in the enhancing pack, and the same `overrides` PUT from `tactic:a` to `tactic:b` answers `2xx`, so the
     refusal keys on the source kind (if a relation rule refuses that pair for another reason, pick the edge type an
     existing scenario already registers between two artifacts of one pack, keeping the step-enhancement source as the
     only difference, and say so in the scenario). README vocabulary only (`composition.feature:1337` already PUTs relationships).
2. Unit red: `PackPolicyStepEnhancementTest` (each `StepEnhancementFault` → `prompt_fragment_invalid` finding at its
   path); `StepEnhancementLoadChecksTest` (the store's load: violations recorded, nothing thrown, `load` still
   answers the other artifacts; `loadStaged` lists every fault at its file). Staged-load cases with twins, beside the
   scenarios (A12): the duplicate id (the second file's violation, and `loaded.pack()` holds exactly one
   `step_enhancement:spdd-plan`, from the first file), a non-step target, an absent `prompt_fragments` key, and an id
   that differs from its stem (a `schema` violation at the yaml, not `prompt_fragment_invalid`). The A4 case: an
   enhancement with two entries, one companion blank and one valid → `loadStaged`'s violations hold
   `prompt_fragment_invalid` once, at the yaml, and the valid companion only as `unmodelled`, never as
   `prompt_fragment_invalid`; its twin (an orphan beside a fully valid pair) is reclassified.
3. OD-10 row 4 in `FileSystemPackInspectorTest:403-417` and its twin (validate mode).
4. `RelationshipRegistrationServiceTest` (new cases, R-REL), each with a twin: an `overrides` relationship from a held
   step enhancement is refused `PROMPT_FRAGMENT_INVALID` and the shard is unchanged (twin: from a tactic, registered);
   a second `enhances` relationship from it is refused (twin: re-putting an `enhances` relationship already in the
   shard, an idempotent upsert, succeeds); a relationship of another type whose `{from, to}` pair equals the shard's
   `enhances` edge of a held enhancement (which `RelationshipMerge.upsert` would replace) is refused (twin: the same
   type between two tactics, registered).

**Validation:** all red for the right reason (assertion failures, steps bound).

### T017: `PackFindingCode`, `PackPolicy`, and the raise — one commit

**Purpose:** choose the code in one place and raise it honestly (A3, C-02, T-15).

**Steps:**

1. `PackFindingCode` gains `PROMPT_FRAGMENT_INVALID` (blocking), projecting to `RefusalCode.PROMPT_FRAGMENT_INVALID`.
   Leave the three slot codes to WP05. **Same commit, OD-10 row 11:** `PackFindingTest`'s `LOAD_PATH_BLOCKING`
   (:17-24) gains `PROMPT_FRAGMENT_INVALID`; without it `aValidateOnlyCodeHasNoWireValue` (:67-77) fails on the new
   code's projection. (The optional route of leaving `refusal()` throwing is not taken: the operator ruled the set
   edit.)
2. `PackPolicy` maps each `StepEnhancementFault` to a `PackFinding` with that code at the fault's path, and exposes the
   wire token for a violation `kind`. Preserve WP03's order (path, then rule order); do not re-sort anything else
   (E-3).
3. **Same commit:** in `CharterRefusalCode.yaml`, amend the `x-refusal` definition of `raised` (:59-71) so
   `raised: true` includes "raised as a violation code of `422 validation_failed`", and say beside it what `status`
   means for such a code: the HTTP status of the problem that carries it is `422` at registration, and a composing
   read answers `500 pack_unreadable`; the declared `status: 500` is kept and read as the status of the read that
   meets the fault (`contracts/README.md`, A19); mark `prompt_fragment_invalid`
   `raised: true`; mark `drg_schema_error` `raised: true` by the same rule, and drop the definition's example "a code
   that only a part no operation reaches refuses with is not raised (drg_schema_error)" (:69-71). In the same commit
   rewrite the two description paragraphs the flip makes false (A5): :32-35 ("pack_level_mismatch and
   drg_schema_error are no longer raised … not reached by any operation") so that it says `drg_schema_error` is raised
   as a registration violation code and only `pack_level_mismatch` stays unraised; and :58-60 ("outside the two classes
   no operation reaches (the whole-pack load and its findings)") so that it names only `PackFindingCode`'s off-wire
   projection. In `ReservedRefusalCodesTest` (no list edit, E-2): under OD-3 (a), remove `DRG_SCHEMA_ERROR` from
   `OFF_WIRE` (:69-72) and restate `offWireRules()` (:106-131) and the javadoc (:48-52, "No operation reaches either"
   is stale) so they guard only what is still unraised (`PACK_LEVEL_MISMATCH`) (row 8); change :189-194 to expect
   `DRG_SCHEMA_ERROR` in `raisedCodes()` (row 9). If the rewrite would loosen a check of a code that stays
   `raised: false`, stop and escalate. **Same commit, OD-10 row 6 (fifth analysis A4):** the table of
   `errors.feature:192-238` ("Then the contract says exactly these refusal codes are raised by an operation:") gains
   the rows for `prompt_fragment_invalid` and `drg_schema_error`, so the contract suite is never red between the flag
   flip and the table.
4. Run `:deployable:test --tests '*ReservedRefusalCodesTest' --tests '*CharterContractSuite'` and `:packs:test`.

**Validation:** `ReservedRefusalCodesTest` green with the flags; `WireTokenParityTest`,
`ActivationWireTokenParityTest` and `VocabularyEqualityTest` green with **no** new row (no enum value added, D10).

### T018: Validate mode — the inspector runs the rules

**Purpose:** the author sees the fault before registering (FR-004..006 at moment V).

**Steps (shared edit on `FileSystemPackInspector`):**

1. In FULL mode, build `StepEnhancementFacts` from the parsed enhancements, each companion's canonical text
   (`MarkdownCodec.write(document)`, A5) and the pack's raw graphs, and put them on the new `PackContents` component
   (A3, listed edit above); `PackPolicy.judge` runs `StepEnhancementRules` over them and reports findings. In
   `LOAD_PATH` scope the component is empty (the 9-argument constructor).
2. Companion faults of a `prompt-fragment` file found by the inspector's own companion read (`readCompanion`,
   :332-337: `MarkdownCodec` refusal (CR, BOM), structure-schema breach (blank; frontmatter, which the closed
   structure schema refuses) — are reported under kind `prompt_fragment_invalid` at the **fragment**, not as a generic
   unreadable companion; the facts mark that companion unreadable, so no rule judges it again. A named-but-missing
   companion is the rule `COMPANION_MISSING` at the yaml. No edit to `StepEnhancementBinding` (E-4).
3. Orphans in validate mode come from the facts: the inspector puts every `*.prompt-fragment.md` it read under
   `missions/*/enhancements/` into them, and `COMPANION_ORPHAN` answers at the fragment. There is no unmodelled list
   to reclassify in validate mode (A9, E-8). A directory holding a step-enhancement file the inspector could not parse
   (schema-invalid, id ≠ stem) is recorded in the facts, so its fragments are not judged orphans (A4).
4. **The validate-mode mechanism for D8 (fourth analysis, A9):** both findings would land on the same second file,
   where `PRECEDENCE` (`PackPolicy.java:100-102`) has nothing to order them by. So `PackPolicy.artifactRules`
   (:310-327) raises **no** `DUPLICATE_ARTIFACT_ID` for kind `step_enhancement`; its `STEM_REPEATED` finding speaks
   for it, and validate mode answers exactly one finding per same-id file, as load does. Pin the code and the count in
   a test, with a positive control that `DUPLICATE_ARTIFACT_ID` still fires for two same-id tactics.
   An id ≠ stem stays the existing `const` violation at `/id` (`misnamedArtifact`, :470-479; operator ruling A1): pin
   it beside the twin, asserting no `prompt_fragment_invalid` for that file.
5. A8: add the graph document with the single `enhances` edge to the setup of the two `SPDD_ENHANCEMENT` tests
   (:371-417; target `built-in::action:software-dev/specify`, A6), so the fixtures obey every rule this WP adds.
   Neither test calls `PackPolicy.judge` (they assert the listing and `unreadableFiles`), so no `EDGE_COUNT` could
   reach their assertions; the setup keeps the fixtures valid, it does not rescue an assertion (fifth analysis A12).
   Setup only;
   stop and ask the operator if any assertion beyond OD-10 rows 3–4 must still change.
6. R-OVR in validate mode: an enhancement with an `overrides` edge answers `prompt_fragment_invalid` at its file; its
   twin answers none.

**Validation:** OD-10 row 4 and its twin green; `FileSystemPackInspectorTest` otherwise unchanged in its assertions
and green.

### T019: Load and registration — violations, not throws; precedence pinned

**Purpose:** every fault at once, at its file (OD-3 (a), A3, A9, T-08).

**Steps (shared edits on `DomainRepositoryService` and `WholePackStore`; no edit to `StepEnhancementBinding`, E-4):**

1. E-1: the graph-taking `assembly` form in `DomainRepositoryService` (steps 1–4 under "Files shared": facts and
   rules first, the judged `PackRead` copy with the A3 remap and the A2 withholding, the three-argument assembly, the
   rule faults appended last) records each fault as a `PackElementViolation` at its file with
   `kind = prompt_fragment_invalid`; `WholePackStore.load` reads the graphs before the assembly (today the assembly is
   :194 and the graph read :195) and calls it; `DomainRepositoryService.assemble` (:666-670) reads the graphs and calls
   it. Nothing is thrown for a prompt fault, and a duplicate id no longer throws `VALIDATION_FAILED` from `build()`
   (A2). `loadStaged` already copies `violation.kind()` into the violation code (:239-243), so a registration
   lists every fault at its pack-relative path, and `readable` answers `pack_unreadable` for the same faults. Pin both
   paths in `StepEnhancementLoadChecksTest` with an **edge** fault (R-OVR or two `enhances` edges): the staged load
   lists it, and a composing read through `DomainRepositoryService` answers `pack_unreadable` naming its file. Pin A2 on
   both paths too: the staged load lists `prompt_fragment_invalid` at the second same-id file and holds one
   `step_enhancement:spdd-plan`; a composing read answers `pack_unreadable` naming that file, never a
   `ConstraintBreachException` from `build()`. Pin A3: a blank companion's violation at the yaml has kind
   `prompt_fragment_invalid` through `WholePackStore.load` and through `assemble`, and the generic `companion` kind
   through the three-argument form (unchanged for `ArtifactFiles`).
2. In `loadStaged`, reclassify an orphan's `unmodelled` entry (:244-248) under `prompt_fragment_invalid`, except in a
   directory holding a refused or withheld enhancement file (A4, E-5; see "Files shared"); a rule fault on a held
   enhancement does not exclude (fifth analysis A5). Pin it with a twin at both moments in
   `StepEnhancementLoadChecksTest` and `FileSystemPackInspectorTest`'s new cases: an orphan beside an enhancement with
   two `enhances` edges (`EDGE_COUNT`) is `prompt_fragment_invalid` at the orphan at the staged load **and**
   `COMPANION_ORPHAN` → `prompt_fragment_invalid` at validate, beside the `EDGE_COUNT` fault at the yaml. On a composing read an
   orphan is not held, like every unmodelled file (reading of D11, P8); the whole-pack round trip is untouched.
3. Precedence tests (T-08), one each, in `StepEnhancementLoadChecksTest`:
   - `prompt_fragments: []` answers the schema path (`minItems`), **not** `prompt_fragment_invalid`; an absent key
     answers `prompt_fragment_invalid` (D12). Both shapes tested.
   - FR-005's two shapes: an entry whose `slot` breaks `<namespace>:<name>` answers the schema path (the `slot`
     pattern), **not** `prompt_fragment_invalid`; a repeated slot, a missing companion and an orphan answer
     `prompt_fragment_invalid`. All four tested, in validate mode and at the staged load.
   - A schema-invalid file gets no semantic rule; only the schema violation is reported. The same for an id that
     differs from its file stem (operator ruling A1): the store's `schema` violation at the yaml
     (`FilesystemPackStore.readKind`, :686-690) and validate mode's `const` at `/id`, never `prompt_fragment_invalid`.
   - A fragment that breaks `MarkdownCodec` **and** holds a marker answers the codec fault only.
   - On a read, the single `pack_unreadable` names the **first** violation in assembly order (E-3; this WP owns the
     claim and its test): plant two rule faults in two files and assert the same file (the first in WP03's order) is
     named on every read; plant a binding-level fault beside a rule fault and assert the binding-level one is named
     (`DomainRepositoryService.readable`, :599-608, is read, not edited).
   - Companion twins: two enhancements naming one companion; one enhancement naming the same path twice; a named but
     missing companion; an orphan beside a valid pair — each at its file, all listed at once.
4. **R-REL (operator ruling, 2026-11-09), after the raise commit (T-15).** Listed edit on
   `RelationshipRegistrationService.registerRelationship` (`RelationshipRegistrationService.java:88-119`): after the
   self-loop check (:102-108) and before `targets.require` (:110) and `writeStore.commit` (:115-116), read
   `writeStore.registeredRelationships(pack)` once (it is read at :112 today; reuse that read) and throw
   `CharterRefusal(RefusalCode.PROMPT_FRAGMENT_INVALID, <detail naming the enhancement URN and the rule>)` when:
   - the relationship is `overrides` (`RelationType.OVERRIDES`) and its `from` is a held step enhancement (a URN of
     `NodeKind.STEP_ENHANCEMENT` in `current.artifacts()`) — R-OVR;
   - it is `enhances` from a held step enhancement and the shard holds no equal `enhances` relationship with that
     `from`: the pack loaded, so the enhancement already has its one `enhances` edge and this would be a second (a
     non-`action:` target falls under the same refusal); an idempotent re-put of the shard's own edge is accepted;
   - its upsert would replace a shard `enhances` edge whose `from` is a held step enhancement (`Relationship.equals` is
     the unordered pair, `Relationship.java:197-205`; `RelationshipMerge.upsert` drops an equal entry), leaving it with
     none.

   The check reads only `current`, the relationship and the shard, so the constructor and its seven test call sites
   are unchanged. No other edit to the service.
5. Make the T016 scenarios green.

**Validation:** `:packs:test`, `:packs:openPacksRoundTrip` (0 new refusals at the pin, NFR-002),
`:packs:wholePackRoundTrip`, the scenarios.

### T020: Contract text — the reserved paragraph, C-07, `_shared`, `errors.feature`, the pinned description

**Purpose:** the contract says what the service now does (FR-011, C-001).

**Steps:**

1. `CharterRefusalCode.yaml`:
   - `prompt_fragment_invalid` description broadened to "an invalid step enhancement or prompt fragment" (D10) and
     stating the facts: at registration a violation `code` inside `422 validation_failed` at the file; on a composing
     read the fault answers `500 pack_unreadable` naming the file (not "default 500").
   - Rewrite the reserved paragraph (:160-166) for this code; keep `prompt_slot_missing` reserved (the phase-2 mission (#438) raises it) and the two
     slot codes reserved (WP05). Do **not** narrow "an active or inactive enhancement" here — the phase-2 mission
     (#438) does it with the raise (OD-1).
   - C-07: reword the sentence "pack_reference_pack_missing, pack_reference_artifact_missing and artifact_locked keep
     their existing meanings for the other ADR-0066 refusals" (:165-166) so it no longer suggests `artifact_locked`
     for a step-integrity refusal. Leave the "two reads that stay reserved" sentence alone (PR C's, pinned by
     `ContractDescriptionsTest`).
2. `contracts/_shared/schemas/Problem.yaml`: `violations` ("Only on a 422 activation_invalid") also covers a
   registration's `validation_failed`. `RefusalViolation.yaml`: `pointer` is a pack-relative file path on a
   registration violation.
3. `errors.feature` OD-10 row 7 (prose). Row 6's two table rows landed in T017's commit (fifth analysis A4).
4. `ContractDescriptionsTest`: add an assertion pinning the rewritten paragraph's key sentences (new assertion
   only), and pin **only sentences WP05 keeps** (fifth analysis A3): that `prompt_fragment_invalid` is raised as a
   registration violation code, the C-07 reword, and that `prompt_slot_missing` stays reserved. Do not pin any
   sentence that names `prompt_slot_malformed` or `prompt_slot_not_empty` as reserved: WP05 raises them and rewrites
   that part, and has no listed edit on this file.
5. **R-REL contract side, one commit with T019 step 4:** `prompt_fragment_invalid` in `CharterRefusalCode.yaml` gains
   `operations: {putPackRelationship: {status: 422}}`, and its description says so (the only operation that carries it
   as the top-level `code`; at registration it stays a violation code); `RefusalHandler.STATUS_BY_OPERATION` gains
   the same entry for `putPackRelationship` only (listed edit). `statusByOperation()` (`RefusalHandler.java:89-94`)
   today gives one shared `registrationStatusCodes()` map to the registration and to all eleven
   `IMPACT_CHECKED_WRITES`, so give `putPackRelationship` its own copy of that map plus
   `PROMPT_FRAGMENT_INVALID → 422`, leaving every other operation's map unchanged (fifth analysis A11); the `422` description of
   `contracts/charter/paths/packs_packId_relationships.yaml` adds "an edge that would give a held step enhancement an
   `overrides` edge or a second `enhances` edge, or replace its one `enhances` edge (prompt_fragment_invalid)" (listed
   edit). Run `ReadConsistencySteps` (through `CharterContractSuite`) and `:web-transport:test --tests '*RefusalHandlerTest'`.

**Validation:** `:contracts:check` (lint, `checkRefusalExamples`, compatibility), `:deployable:test --tests
'*ContractDescriptionsTest' --tests '*CharterContractSuite'`.

### T021: Schema description, file-kinds comment, the FR-012 positive control, and the fragments

**Purpose:** C-001, FR-012 (schema description), with a test that pins the text.

**Steps:**

1. `contracts/packs/schemas/step-enhancement.schema.json`, **descriptions only, no property change**: the rules of
   D8–D12; "one companion per entry, any stem matching the pattern, each named by exactly one entry" (D11); the
   `path` property drops "the `<id>.prompt-fragment.md` companion"; `prompt_fragments: []` is refused by the schema
   (`minItems`) and an absent key by the D12 rule; guards and emits are fields `add_guards`, `add_lifecycle_guards`,
   `emits`, built under #434 (D6), not edges. **Operator ruling A1:** the top-level sentence "A semantic check beside
   this schema requires the id to equal the file stem, each slot to occur at most once, and each named companion to
   exist beside the enhancement; violations are prompt_fragment_invalid" is rewritten so that an id that differs from
   its file stem is refused by the store's stem rule as a **schema** violation (as the `slot` pattern and `minItems`
   are), while the repeated slot, the missing, shared or orphan companion, the edge rules, the directory, a repeated
   id (the second file withheld, A2) and the absent key are `prompt_fragment_invalid`. The `id` property keeps "equal
   to its file stem".
2. `contracts/packs/file-kinds.yaml`: the step-enhancement comment, same wording.
3. `StepEnhancementSchemaDescriptionTest` (in the `PromptFragmentContractsTest` style, a new file): pins the key
   phrases of the descriptions (FR-012's positive control), including the A1 phrase that the id-stem rule is a schema
   violation, and that the description no longer lists the id-stem rule among the `prompt_fragment_invalid` rules. `PromptFragmentContractsTest:262-283` stays green
   unedited (it asserts `contains` only).
4. Fragments (one `###` block per file, `contracts/charter/changes/README.md`; rename to the landing date if later):
   - `2026-11-09-prompt-fragment-invalid-raised.md`, `### Changed`: the definition of raised; `prompt_fragment_invalid`
     raised as a registration violation code; `drg_schema_error` raised by the same rule; the `_shared` wording; the
     description broadened; and one line for the `step-enhancement.schema.json` description (contract text, A1): "an
     enhancement whose id differs from its file stem is refused by the store's stem rule as a schema violation, not
     `prompt_fragment_invalid`; the description lists the rules that answer `prompt_fragment_invalid`".
   - `2026-11-09-prompt-fragment-checks.md`, `### Breaking`: content that validated or registered before is now
     refused (list the rules, including one line for R-OVR: "a step enhancement that is the source of any `overrides`
     edge is refused with `prompt_fragment_invalid`; an enhancement only adds"; and one line for R-REL: "`PUT
     /packs/{packId}/relationships` refuses, with `422 prompt_fragment_invalid` and nothing written, an edge that would
     give a held step enhancement an `overrides` edge or a second `enhances` edge, or replace its one `enhances` edge;
     it answered `2xx` before and left every composing read of the pack answering `500`"); "core: nothing while it is not a consumer (epic #186)"; the read-side effect: every
     verified read of a closure holding such a pack answers `500 pack_unreadable`.

**Validation:** `:contracts:check` (changelog fragments), `:packs:test`.

## Test strategy

- Scenarios and unit tests red first (T016). Positive controls: each refusal scenario's passing twin; the `200` read
  beside the `500` one. Non-vacuity: the multi-fault count assertion; the deterministic-first-violation test.

## Gates (keep green, honestly)

- `ReservedRefusalCodesTest` (flags moved with the code, OD-10 row 8), `ContractDescriptionsTest`,
  `checkRefusalExamples`, `OperationCoverage` / `OperationCoverageTest`, `HexagonalArchitectureTest`,
  `WireTokenParityTest`, `ActivationWireTokenParityTest`, `VocabularyEqualityTest`, `PromptFragmentContractsTest`
  (unedited), `KindBindingsTest`, `PackFileSetExactnessTest`, `WholePackRoundTripTest` (expected-red unchanged),
  contract lint / compatibility / changelog-fragment checks.
- Commands: `:packs:test`, `:packs:openPacksRoundTrip`, `:packs:wholePackRoundTrip`, `:deployable:test --tests
  '*CharterContractSuite' --tests '*ReservedRefusalCodesTest' --tests '*ContractDescriptionsTest' --tests
  '*HexagonalArchitectureTest' --tests '*OperationCoverageTest'`, `:web-transport:test` (`RefusalHandlerTest`),
  `:contracts:check`, and `:packs:spotlessCheck :deployable:spotlessCheck :web-transport:spotlessCheck` plus
  `checkstyleMain checkstyleTest` for those three modules (fifth analysis A8).

## Definition of Done

- Every subtask done and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- Every scenario and test above seen failing first, now passing; the raise landed in the commit that names the code.
- Only OD-10 rows 4, 6, 7, 8, 9, 11 (the `PROMPT_FRAGMENT_INVALID` half) and the D19 description edits were made to existing expectations; the A8 graph
  document is setup only; the `NamesEveryCode` probe is untouched.
- Two fragments present (`### Changed`, `### Breaking`); nothing outside `owned_files` beyond the listed shared edits.

## Risks

- The generic duplicate-id rule may fire before the stem rule: pinned by a test (T018).
- Rewriting `offWireRules` can silently drop protection for `PACK_LEVEL_MISMATCH`: keep a rule for it and show it
  still fails on a planted access.
- Hunks in `CharterRefusalCode.yaml` / `ReservedRefusalCodesTest` will conflict textually with PR C at the rebase:
  keep them small and separate.

## Reviewer guidance

- Check the raise commit contains both the main class naming the code and the YAML flag (T-15).
- Check no `throw` was added to `WholePackStore.load` for a prompt fault.
- Check the `pointer` values in the scenarios are pack-relative paths and the multi-fault count is exact.
- Check every expectation edit maps to OD-10 rows 4/6/7/8/9/11 or D19, and that the A8 change adds setup only.
- Check R-REL: the relationship refusal precedes the commit, keys on a held step enhancement (the tactic twin
  registers), and the `operations` entry, `RefusalHandler` and the path's `422` text agree.
- Check the rule faults are recorded in `DomainRepositoryService.assembly`'s graph-taking form, reached by both
  `WholePackStore.load` and `assemble` (E-1), and that the read scenario's fault is an edge fault.
- Check the second same-id enhancement never reaches `Pack.Builder` (A2), the `companion` remap happens only in the
  graph-taking form and only for `*.step-enhancement.yaml` files (A3), `FilesystemPackStore.java` (beyond WP01's
  A2-harness write), `KindBinding.java`,
  `StepEnhancementBinding.java` and `Pack.java` are untouched, and no fragment of a directory with a refused enhancement
  is reported as an orphan (A4).
