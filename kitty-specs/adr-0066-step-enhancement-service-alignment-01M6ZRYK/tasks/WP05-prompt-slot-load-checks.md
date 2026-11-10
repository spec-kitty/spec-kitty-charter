---
work_package_id: WP05
title: Phase 1 · Base-prompt slot checks behind the version gate, the warnings list and the registration diagnostics
dependencies:
- WP02
- WP04
requirement_refs:
- FR-007
- FR-011
- NFR-002
- C-001
- C-004
- C-007
- SC-002
- SC-004
planning_base_branch: issue-424-428-adr-0066-service-alignment
merge_target_branch: issue-424-428-adr-0066-service-alignment
branch_strategy: Planning artifacts for this mission were generated on issue-424-428-adr-0066-service-alignment. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-424-428-adr-0066-service-alignment unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-adr-0066-step-enhancement-service-alignment-01M6ZRYK
base_commit: 799bd66eddbca9ada545bb389840ad8baca80468
created_at: '2026-11-09T19:53:41.695431+00:00'
subtasks:
- T022
- T023
- T024
- T025
- T026
phase: Phase 1 (before PR C)
agent: claude
history:
- timestamp: '2026-11-09T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
create_intent:
- contracts/charter/scenarios/prompt-slots.feature
- contracts/charter/changes/2026-11-09-prompt-slot-codes-raised.md
- contracts/charter/changes/2026-11-09-prompt-slot-checks-and-diagnostics.md
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PromptSlotLoadChecksTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PromptSlotLoadChecksTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionTypeCorpusTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/WholePackWriteStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRegistrationService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRegistrationServiceTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationMapper.java
- contracts/charter/schemas/PackRegistration.yaml
- contracts/charter/schemas/DiagnosticCode.yaml
- contracts/charter/examples/PackRegistrationStatus.succeeded.yaml
- contracts/charter/scenarios/prompt-slots.feature
- contracts/charter/changes/2026-11-09-prompt-slot-codes-raised.md
- contracts/charter/changes/2026-11-09-prompt-slot-checks-and-diagnostics.md
role: implementer
tags: []
tracker_refs:
- '#424'
---
# WP05: Phase 1 · Base-prompt slot checks behind the version gate, the warnings list and the registration diagnostics

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or read
`/home/user/spec-kitty-open-packs/packs/built-in/agent_profiles/java-jenny.agent.yaml` in full.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Judge every step prompt's slots at load, in validate mode and at registration (FR-007): `prompt_slot_malformed`
always; `prompt_slot_not_empty` when the pack descriptor declares `schema_version: "1.0"`; for an unversioned
descriptor a `prompt_slot_not_empty` **warning** on a separate list, never a violation, so built-in at the pin keeps
composing (NFR-002). Deliver the warning on the wire through a new `diagnostics` member of the registration result
(operator ruling **OD-7**). Raise the two slot codes in the commit that names them (T-15).

Implementation command:
`spec-kitty agent action implement WP05 --agent claude --mission adr-0066-step-enhancement-service-alignment-01M6ZRYK`

## Context

- **Concerns:** IC-03 of `plan.md` (wiring: "The gate", "Where") and the IC-04 parts for the slot codes and
  `DiagnosticCode`; OD-7's registration half. Read IC-03 and IC-04 in full, OD-3, OD-7 and the gates table.
- **Requirements:** FR-007, FR-011 (two codes and the diagnostic on the wire), NFR-002, C-001, C-004 (no PR C-owned
  file; the shared hunks are reconciled at the rebase), C-007; SC-002, SC-004.
- **Rulings:** OD-3 (a) (violations with `kind` = token; raised includes a `validation_failed` violation code); OD-7
  (a `diagnostics` member reusing `Diagnostic.yaml` on the registration result, `### Breaking` for a closed object —
  the prompt-read half is the phase-2 mission's, #438); A4 (warnings apart from violations; the check runs in
  `DomainRepositoryService.assembly`, which holds the descriptor; `KindBinding.toEntity` gets no descriptor); A5
  (canonical text); T-17 (exactly 5 warnings, positive control); the descriptor version is
  `PackDescriptor.schemaVersion()` (`service/kernel/.../PackDescriptor.java`, kept through a store write since
  `2ced50b7`).
- **Read first:** `plan.md` IC-03; `data-model.md` (Slot, Code projection); `research.md` P2, P4;
  `DomainRepositoryService.java` around `assembly` (:684-702) and `readable` (:599-608);
  `contracts/charter/schemas/{PackRegistration,PackRegistrationStatus,Diagnostic,DiagnosticCode}.yaml`.

### Files shared with other work packages

This WP owns `DomainRepositoryService.java`. Two earlier WPs made listed edits to it: WP01's A2-harness
`saveArtifact(pack, artifact, place)` overload (leave it as is), and WP04's (plan erratum E-1): a form of `assembly` that takes the pack's graph read and records the step-enhancement rule faults last,
called by both `WholePackStore.load` and `assemble`. Rebase on it and put the slot check in that same form, in its own
hunk, **before** the step-enhancement rule faults (assembly order, E-3), so the check runs on the registration's staged
load and on every composing read. Do not move or re-sort WP04's hunk.

Make only the edits named below, each with `out-of-map: <file> — <why>` in the commit message (`lanes.json` write
scopes come from `owned_files` only; this list and tasks.md's hot-files table are the complete record):

- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackFindingCode.java` (WP04) — add
  `PROMPT_SLOT_MALFORMED`, `PROMPT_SLOT_NOT_EMPTY` (blocking) and `PROMPT_SLOT_NOT_EMPTY_UNVERSIONED` (warning →
  `Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY`).
- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java` (WP04) — map `PromptSlots` faults to
  those codes by descriptor version.
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java` (WP01) — run the
  slot check after the parse in validate mode.
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore.java` (WP01) — pass a
  `List<PackFinding> warnings` to the graph-taking `assembly` in `load`; add a `warnings` component to `LoadedPack`
  (:88-94), keeping the two shorter constructors (`WholePackStore.java:123-130` and `:140`) with an empty list so
  their 13 call sites compile unedited; carry it through `loadStaged` into `WholePackWriteStore.Contents` (third analysis, A8).
- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackContents.java` (owned by no WP; WP04 added the
  `StepEnhancementFacts` component; fourth analysis, A3) — add the slot inputs validate mode needs to reach
  `PackPolicy.judge(PackContents)`: per step prompt, its pack-relative path and its `PromptSlots` answer over the
  canonical text, or its canonicalisation refusal. The descriptor version is already there
  (`contents.descriptor()`). Keep the 9-argument constructor **and** WP04's, each passing an empty list, so no call
  site is edited.
- `service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackFindingTest.java` (owned by no WP) — **OD-10 row
  11** (operator ruling A1, 2026-11-09), in T023's commit: `LOAD_PATH_BLOCKING` (:17-24) gains `PROMPT_SLOT_MALFORMED`
  and `PROMPT_SLOT_NOT_EMPTY`; `onlyTheLevelMissingCodeIsAWarning` (:30-35, `containsExactly`), the severity
  expectation of `aFindingTakesItsSeverityFromItsCode` (:37-44) and the skip at :70 (which names `PACK_LEVEL_MISSING`
  as the one warning) gain `PROMPT_SLOT_NOT_EMPTY_UNVERSIONED`. Its `diagnostic()` projection is required:
  `VocabularyEqualityTest:282-284` calls `diagnostic()` on every WARNING code. No other assertion changes; list the
  edit in the PR.
- `contracts/charter/schemas/CharterRefusalCode.yaml` (WP04) — `prompt_slot_malformed` and `prompt_slot_not_empty`
  raised (OD-3 (a)); their descriptions (C-15: `prompt_slot_not_empty` points to `DiagnosticCode.prompt_slot_not_empty`
  instead of "a warning otherwise"); the reserved paragraph keeps only `prompt_slot_missing`. `drg_schema_error` and
  the definition of `raised` are WP04's and already done; do not touch them.
- `contracts/charter/scenarios/errors.feature` (WP04) — OD-10 row 6: the raised-code table gains the two codes.
- `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspectorTest.java` (WP01) —
  **OD-10 row 12** (operator ruling A1, fifth analysis, 2026-11-09), in T024's commit:
  `validateModeOverTheBuiltInPackFindsWhatMainFoundAndListsTheConfigPayload` (:670-679, `@Tag("open-packs")`) changes
  its `assertThat(verdict.findings()).isEmpty()` (:679) to exactly five `PROMPT_SLOT_NOT_EMPTY_UNVERSIONED` warnings,
  one at each software-dev step prompt file of the pinned built-in (assert code, severity `WARNING` and path, as a
  set of five), and its comment (:677-678) says the slots return to empty when open-packs#175 empties them (#435). No
  other assertion of the method or the file changes.
- `ContractDescriptionsTest` (WP04) gets **no** edit from this WP: WP04 pins only the sentences this WP keeps
  (`prompt_fragment_invalid` raised, the C-07 reword, `prompt_slot_missing` reserved; fifth analysis A3). If the
  rewrite of the reserved paragraph breaks a WP04 pin anyway, stop and escalate (class (c)); do not edit the test.
- `ReservedRefusalCodesTest` gets **no** edit from this WP: it reads `raisedCodes()`/`unraisedCodes()` from the YAML
  flags, and its `NamesEveryCode` probe (:290-345) must keep naming every code (erratum E-2). Run it as a gate.

## Project constraints (apply to every subtask)

- **Test first, red first (C-007);** `prompt-slots.feature` before code.
- **Raise in the same commit (T-15)** as the main class that names `RefusalCode.PROMPT_SLOT_MALFORMED` /
  `PROMPT_SLOT_NOT_EMPTY`.
- **Asserted expectations:** only OD-10 row 6 (rows for these two codes), OD-10 row 11 (the slot-code half, in
  `PackFindingTest`), OD-10 row 12 (`FileSystemPackInspectorTest:679`, the five warnings) and the D19 description
  wording.
  `MissionTypeCorpusTest` gains new assertions only. Any other expectation edit: stop and escalate (class (c)).
- **A warning is never a violation (A4).** Built-in at the pin must compose without `pack_unreadable`.
- **PR C boundary (C-004):** no PR C-owned file (list in WP01). `DomainRepositoryService.compositionFacts` gains slot
  facts later (the phase-2 mission, #438) — do not pre-build them here.
- No disabled tests, no suppressions; Checkstyle limits; no dependency added.
- **Gradle:** `JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=../spec-kitty-open-packs <task>`;
  never `verifyAll`.
- **Commits** end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01E5wJUD4c1fuEbXL3umMvKS`; no AI model name elsewhere.

## Branch Strategy

- Planning branch and merge target: `issue-424-428-adr-0066-service-alignment`.
- Lanes from `lanes.json`. Claimable once WP02 (grammar) and WP04 (finding codes, contract owner) are approved or done.

## Subtasks

### T022: Red — `prompt-slots.feature` and the three moments

**Purpose:** US3 AS3 and AS4 on the wire, first.

**Steps:**

1. `contracts/charter/scenarios/prompt-slots.feature` (`@contract`, README vocabulary). Fixtures: a versioned pack
   (its `pack.yaml` carries `schema_version: "1.0"`) and an unversioned twin.
   - a prompt with a start and no end, a repeated id, nested slots → registration `422 validation_failed`, violation
     `code` `prompt_slot_malformed` at the prompt file, in both packs (version does not matter);
   - a slot holding text in the versioned pack → `prompt_slot_not_empty` at the prompt file;
   - the same in the unversioned pack → registration **succeeds** and the result's `diagnostics` lists
     `prompt_slot_not_empty` naming the file (OD-7);
   - read path: a closure holding the malformed pack answers `GET /offering` `500 pack_unreadable` naming the prompt
     file; the twin answers `200`.
2. `PromptSlotLoadChecksTest`: the descriptor gate at **three moments** — validate (`FileSystemPackInspector`), load
   (`DomainRepositoryService.assembly`), the registration's staged load — each with versioned and unversioned twins.
3. `MissionTypeCorpusTest` (new assertions, T-17): the pinned built-in yields **exactly 5** `prompt_slot_not_empty`
   warnings, one per software-dev prompt, each naming its file; and built-in at the pin composes without
   `pack_unreadable` (A4).

**Validation:** every case red on an assertion.

### T023: Finding codes and the raise — one commit

**Purpose:** codes in one place (A3); honest raise (T-15).

**Steps:**

1. Shared edit: `PackFindingCode` +`PROMPT_SLOT_MALFORMED` (blocking → `prompt_slot_malformed`),
   `PROMPT_SLOT_NOT_EMPTY` (blocking → `prompt_slot_not_empty`), `PROMPT_SLOT_NOT_EMPTY_UNVERSIONED` (warning →
   `Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY`). Two codes for one condition because severities are fixed per code
   (Complexity Tracking).
2. Shared edit: `PackPolicy` maps a `PromptSlots` fault: `MALFORMED` → `PROMPT_SLOT_MALFORMED` whatever the version;
   `NOT_EMPTY` → `PROMPT_SLOT_NOT_EMPTY` when `schemaVersion()` is `"1.0"`, else the unversioned warning.
3. Same commit: flags in `CharterRefusalCode.yaml` and rows in `errors.feature`. `ReservedRefusalCodesTest` follows
   the flags with no edit (E-2).
4. Same commit, **OD-10 row 11**: `PackFindingTest`'s expected sets gain the three codes (see "Files shared").

**Validation:** `PackFindingTest`, `ReservedRefusalCodesTest`, `VocabularyEqualityTest:274-292` (projected codes already in
`DiagnosticCode` ∪ `CharterRefusalCode`), `WireTokenParityTest`, `ActivationWireTokenParityTest` green, no new row.

### T024: The load check in `assembly`, the warnings list, validate mode

**Purpose:** FR-007 at moments V and L, with warnings apart (A4, A5).

**Steps:**

1. In the graph-taking `DomainRepositoryService.assembly` form WP04 added (beside :684), which holds
   `read.descriptor()`, before the step-enhancement rule faults: for each step and on-demand step of each stored
   mission type, parse the **canonical text** `MarkdownCodec.write(document)` (frontmatter included) with
   `PromptSlots`. **`document` is pinned (third analysis, A11):** the companion `MarkdownDocument` the store read into
   the DSO, `MissionTypeDso.Step.prompt()` of `read.elements(MissionTypeBinding.INSTANCE)` (filled by
   `MissionTypeBinding.withCompanions`, `MissionTypeBinding.java:139-147`). Never the built `MissionType`'s
   `StepDocument` re-rendered through `markdownOf(StepDocument)` (:341-349), a second rendering through the domain.
   A blocking fault becomes a `PackElementViolation` at the prompt companion's pack-relative path
   (resolved from the mission type file and `promptPath()`) with `kind` = its wire token.
   **The warnings seat (A8):** `assembly` returns `Pack.Builder` and `Pack.java` is frozen (E-3), so add a
   `List<PackFinding> warnings` out-parameter to the graph-taking form, filled as `places` is. `assemble`
   (:666-670) passes a list it discards (no read serves warnings in phase 1); `WholePackStore.load` passes the list it
   puts on `LoadedPack`.
2. Shared edit: `WholePackStore.LoadedPack` gains the `warnings` component and `loadStaged` carries it to
   `WholePackWriteStore.Contents` (:32, this WP's file); a warning never enters the violation list.
3. Shared edit: `FileSystemPackInspector` runs the same check after its parse and puts each prompt's path and
   `PromptSlots` answer on `PackContents`' new slot component (listed edit, A3); `PackPolicy.judge` maps them to
   warnings or blocking findings per the gate, reading `contents.descriptor()`. Same commit, **OD-10 row 12** (see
   "Files shared"): the built-in validate-mode test at `FileSystemPackInspectorTest:670-679` now expects exactly the
   five `PROMPT_SLOT_NOT_EMPTY_UNVERSIONED` warnings; run `:packs:openPacksRoundTrip` and see it red before the edit
   (the five warnings listed) and green after.
4. A canonicalisation refusal (CR, BOM) is reported before the grammar runs (A5).
5. Follow-on corpus assertion (from WP02 T011, A12), **on the exact load path (A11):** in `PromptSlotLoadChecksTest`
   (package `infra.storage`, so it can call `FilesystemPackStore.read` and see `MissionTypeDso`), for the five pinned
   built-in software-dev prompts (tag `open-packs`) and every slot fixture prompt this mission now holds (this WP's and
   the `prompt-slots.feature` fixtures), `PromptSlots.parse(raw)` equals the parse of `MarkdownCodec.write(document)`,
   where `document` is `MissionTypeDso.Step.prompt()` as `FilesystemPackStore.read` fills it — the same document step 1
   parses — in ids, order and emptiness. Include a fixture prompt with frontmatter (its only allowed key is the
   detailed description, `mission-step-prompt.schema.json`) so the frontmatter's part of the canonical text is
   covered.

**Validation:** T022 unit tests green; `:packs:openPacksRoundTrip` green on warnings only (NFR-002);
`:packs:wholePackRoundTrip` unaffected.

**Edge cases:** a mission type with a step that has no prompt — nothing to parse, no fault. A prompt whose slot ids
are valid but undeclared elsewhere is fine here; the compose-time `prompt_slot_missing` is the phase-2 mission's.

### T025: The registration result's `diagnostics` member (OD-7)

**Purpose:** FR-011's "diagnostic emitted" gets a wire.

**Steps:**

1. `contracts/charter/schemas/PackRegistration.yaml`: add `diagnostics` exactly as drafted in
   `kitty-specs/…/contracts/registration-diagnostics.fragment.yaml` (`schemas/PackRegistration.yaml#properties/diagnostics`):
   an **optional** member (not in `required`, like `outcome` and `notices`), an array of `Diagnostic.yaml` items, with
   the draft's description and `x-derived` verbatim. It is present in the result of a succeeded registration (empty
   when the load found nothing to warn about) and **`getPack` carries none**. Keep the object closed. This shape is
   bound; any change to it is a contract decision: stop and escalate to the run lead.
2. Update `contracts/charter/examples/PackRegistrationStatus.succeeded.yaml` to show the member at
   `value.result.diagnostics` (`PackRegistrationStatus` is closed; the member belongs to the result, not the top
   level), as the draft does (its example path follows the pinned layout, `missions/software-dev/steps/plan.prompt.md`).
3. `PackRegistrationService` carries the staged contents' warnings into its outcome; `PackRegistrationMapper` maps
   them to the generated DTO. **Keep the existing `RegistrationOutcome` constructor** (`PackRegistrationService.java:123`,
   built at :607) as a secondary constructor passing an empty diagnostics list, as `LoadedPack` and `PackContents`
   keep theirs: `PackRegistrationMapperNoticesTest:36` and `PackRegistrationMapperSourceTest:36` construct it and are
   outside this WP's files (fifth analysis A7). `getPack` (stored pack, no outcome) never sets `diagnostics`; a test pins that.
4. `PackRegistrationServiceTest`: the warnings reach the outcome; no warning, empty list; `getPack` has no
   `diagnostics`.
5. The `### Breaking` fragment (T026) states this shape: "`PackRegistration` gains an optional `diagnostics` member
   (closed object), present in the result of a succeeded registration and listing load warnings, today only
   `prompt_slot_not_empty` for an unversioned descriptor; `getPack` carries none."

**Validation:** the unversioned scenario of T022 green; `:contracts:check` (`checkClosedResponseObjects`, lint).

### T026: Contract descriptions and fragments

**Purpose:** C-001.

**Steps:**

1. `DiagnosticCode.yaml`: "reserved for #424" removed from `prompt_slot_not_empty`; say where it is delivered (the
   registration result's `diagnostics`; the effective-prompt read's `diagnostics` arrives with the phase-2 mission, #438).
2. Shared edit `CharterRefusalCode.yaml`: the two descriptions state the facts (registration violation at the prompt
   file; `500 pack_unreadable` naming the file on composing reads); C-15 pointer.
3. Fragments (one `###` block each; rename to the landing date if later):
   - `2026-11-09-prompt-slot-codes-raised.md`, `### Changed`: the two codes raised as registration violation codes;
     `DiagnosticCode` description.
   - `2026-11-09-prompt-slot-checks-and-diagnostics.md`, `### Breaking`: malformed markers and versioned non-empty
     slots are refused (pinned packs unaffected); the registration result gains the optional `diagnostics` member
     in the words of T025 step 5 (closed object; `getPack` carries none);
     "core: nothing while it is not a consumer (epic #186)"; read-side effect `500 pack_unreadable`.

**Validation:** `:contracts:check`, `:deployable:test --tests '*CharterContractSuite' --tests
'*ReservedRefusalCodesTest' --tests '*ContractDescriptionsTest'`.

## Test strategy

- Red first per subtask. Positive controls: exactly 5 warnings on the pinned built-in (T-17); built-in composes
  without `pack_unreadable`; each refusal scenario's twin answers success.

## Gates (keep green, honestly)

- `ReservedRefusalCodesTest`, `ContractDescriptionsTest`, `checkRefusalExamples`, `OperationCoverage` /
  `OperationCoverageTest`, `HexagonalArchitectureTest`, `VocabularyEqualityTest`, `WireTokenParityTest`,
  `ActivationWireTokenParityTest`, `MissionTypeCorpusTest`, `:packs:openPacksRoundTrip`, `WholePackRoundTripTest`,
  `KindBindingsTest`, `PackFileSetExactnessTest`, contract lint / compatibility / changelog-fragment checks,
  `checkClosedResponseObjects`.
- Commands: `:kernel:test`, `:packs:test`, `:packs:openPacksRoundTrip`, `:packs:wholePackRoundTrip`,
  `:deployable:test --tests '*CharterContractSuite' --tests '*ReservedRefusalCodesTest' --tests
  '*ContractDescriptionsTest' --tests '*HexagonalArchitectureTest' --tests '*OperationCoverageTest'`,
  `:contracts:check`, and `:kernel:spotlessCheck :packs:spotlessCheck :deployable:spotlessCheck` for the touched
  modules.

## Definition of Done

- Every subtask done and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- Tests and scenarios seen failing first, now passing; the raise in the naming commit.
- Built-in at the pin: 0 new refusals, exactly 5 warnings, composes.
- Only OD-10 row 6, OD-10 row 11 (slot-code half), OD-10 row 12 and D19 description expectation edits; no edit to `ReservedRefusalCodesTest`; nothing outside
  `owned_files` beyond the listed shared edits.

## Risks

- A versioned built-in descriptor without emptied slots (open-packs#175) would refuse built-in; the pin does not carry
  it, and #435 bumps the pin later (D21) — out of scope.
- Putting the warning on the violation list turns every read of built-in into `pack_unreadable`: the A4 test guards it.

## Reviewer guidance

- Check the parse input is the canonical text and the gate reads `PackDescriptor.schemaVersion()`.
- Check the warning never becomes a violation, and the `diagnostics` member is closed and documented.
- Check the exact count of 5 warnings and that each names its file, in the scenario and in the OD-10 row 12 edit of
  `FileSystemPackInspectorTest:670-679`, which is the only change to that file.
