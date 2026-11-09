---
work_package_id: WP09
title: 'Phase 2 · The effective-prompt query in packs: prompt in force, slots and ordered active fragments'
dependencies:
- WP07
requirement_refs:
- FR-010
- NFR-003
- C-004
planning_base_branch: issue-424-428-adr-0066-service-alignment
merge_target_branch: issue-424-428-adr-0066-service-alignment
branch_strategy: Planning artifacts for this mission were generated on issue-424-428-adr-0066-service-alignment. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-424-428-adr-0066-service-alignment unless the human explicitly redirects the landing branch.
subtasks:
- T040
- T041
- T042
- T043
phase: Phase 2 (after PR C merges)
agent: claude
history:
- timestamp: '2026-11-09T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/EffectivePromptQuery.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EffectivePromptQuery.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PromptText.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/EffectivePromptOrder.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PromptTextAdapter.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/EffectivePromptQueryTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/EffectivePromptOrderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PromptTextAdapterTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EffectivePromptQuery.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PromptText.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/EffectivePromptOrder.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PromptTextAdapter.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/EffectivePromptQueryTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/EffectivePromptOrderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PromptTextAdapterTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EdgeIndex.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureQueries.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackSources.java
role: implementer
tags: []
tracker_refs:
- '#424'
---
# WP09: Phase 2 · The effective-prompt query in packs: prompt in force, slots and ordered active fragments

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or read
`/home/user/spec-kitty-open-packs/packs/built-in/agent_profiles/java-jenny.agent.yaml` in full.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⛔ Blocked until

**This WP MUST NOT be started until PR C of `layer-merge-and-effective-reads-01M6R0J6` — its WP27, WP28 and WP29,
including #429, the OD-6 E6 amendment and the OD-9 shared "pack holds step" predicate — is merged into `main`, and
this mission branch has been rebased onto that `main`.** Operator ruling D2 / constraint C-004. `ClosureView` and
`EdgeIndex` are PR C-owned until then.

PR C symbols this WP consumes:

- WP27's `ClosureView` (merged closure, `actionContext(...)`) and `EdgeIndex`, with the **enhancer lookup for
  `action:M/s`** (E12, both directions) and the merged-closure name checks (`mission_type_not_found`,
  `action_not_found`).
- `InForceResolution` with the **OD-6 E6 amendment**: a selected step enhancer is in force exactly when its target's
  mission type is in force. Without it no fragment can ever appear (SC-001, US4 AS1).
- PR C's `NodeReference` value, if a step is referenced in an answer (reuse it, never a copy — DIRECTIVE_044).
- `ClosureViewSession.activatable()` drops `action:` URNs ([REN] C1): the step travels as a value, not through it.

## Objective

Build, in `packs`, the query behind the effective-prompt read (FR-010; ADR-0066 d4, d5, d9; D5): for an activation's
compose, mission type `M` and step `s`, answer the **prompt in force** as its canonical text (frontmatter included),
**every** slot it declares in declaration order, and for each slot the fragments of the **active, winning copies** of
the enhancements of `action:M/s`, ordered by (level built-in < org < project, descriptor `name`, enhancement id) by code
point, each with `{packId, packName, level, enhancement, text}`. The ordering and the walk stay in `packs`
(HexagonalArchitectureTest); WP10 serves it through `activations`.

Implementation command:
`spec-kitty agent action implement WP09 --agent claude --mission adr-0066-step-enhancement-service-alignment-01M6ZRYK`

## Context

- **Concern:** IC-07 of `plan.md`, the packs side (Flow steps 2–4, Determinism, the two new counter hooks of Cost).
  Read IC-07 in full, with the Complexity Tracking row for the `PromptText` port and the ruled OD-5, OD-6, OD-8.
- **Requirements:** FR-010, NFR-003, NFR-004, C-004; SC-001.
- **Rulings / findings:** D5 (every declared slot listed; `fragments: []` when none); OD-8 (a slot holding legacy text
  is listed with no flag; the renderer keeps the text and appends fragments); OD-7 (the read's `diagnostics` carry
  `prompt_slot_not_empty` for such a slot — answer it here as a domain diagnostic list); A1 (kernel-typed port:
  `Optional<String> promptInForce(Urn missionType, PackIdentifier winner, String step)` or equivalent, no
  `StepDocument`/`MissionType` in `packs.application`); A5 (canonical text, the same text WP05 parses); A11 (winning
  copy); C-04 / P6 (`packName` is the descriptor name, the second key); T-03 / P7 (fragment text = canonical render);
  T-09 (determinism, fingerprint fixed if needed, nothing excluded); T-10 (counters).
- **Read first:** `plan.md` IC-07; `data-model.md` (EffectiveStepPrompt and its invariants); `research.md` P2, P6,
  P7, P10; `EffectiveReadOperationBudgetTest` (`:activations:test`) for the existing counters.

### Files shared with other work packages

None outside this WP's `owned_files`. `ClosureView.java` and `EdgeIndex.java` become owned here after PR C merges
(merge-hot): add only the accessors the query needs; keep PR C's behaviour (their tests unchanged).
`ClosureQueries.java` and `FileSystemPackSources.java` are edited only if T043's determinism test shows the closure
fingerprint depends on configured order.

## Project constraints (apply to every subtask)

- **Test first, red first (C-007).**
- **Asserted expectations:** no existing assertion edits. Any: stop and escalate (class (c)).
- **Hexagonal:** `PromptText` port in `packs.application`, kernel types only; `PromptTextAdapter` in `infra.storage`
  reads the `MissionType` and renders with `MarkdownCodec`; the comparator in `packs.domain`; nothing in `activations`.
- **Ordering is total and code-point based**; never activation, configured or iteration order.
- No disabled tests, no suppressions; Checkstyle limits; no dependency added.
- **Gradle:** `JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=../spec-kitty-open-packs <task>`;
  never `verifyAll`.
- **Commits** end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01JuXv6fZ4jMGna4kLBUA2jr`; no AI model name elsewhere.

## Branch Strategy

- Planning branch and merge target: `issue-424-428-adr-0066-service-alignment`.
- Lanes from `lanes.json`. Claimable once WP07 is approved or done (and the "Blocked until" condition holds). May run
  in parallel with WP08.

## Subtasks

### T040: Red — query, order and adapter tests

**Purpose:** pin the answer before building it.

**Steps:**

1. `EffectivePromptOrderTest`: three fragments from built-in, org and project → that order; within a level by
   descriptor `name`, then enhancement id, by code point; names using `-`, `_` and digits; a case where `packId` order
   and `name` order disagree (the key is `name`).
2. `EffectivePromptQueryTest` on a fixture closure (two-pack `spdd` shape plus org and project enhancing packs):
   - US4 AS1: three active enhancements filling `spdd:reasons-block` at three levels → ordered as above, with
     provenance;
   - US4 AS2: an enhancement held but not selected → its fragment absent (its target being active does not pull it);
   - US4 AS3: a declared slot no active enhancement fills → listed with `fragments: []`;
   - a step whose prompt declares no slots → base text, empty slot list; a step with no prompt → `prompt: ""` and no
     slots (planning reading; flag it in the hand-off for review);
   - OD-8: an unversioned prompt whose slot holds legacy text plus one active fragment → slot listed with its
     fragment, and a `prompt_slot_not_empty` diagnostic for that slot (OD-7);
   - A11: a lower-level same-id copy's fragments never appear; only the winner's;
   - one enhancement filling two slots → one item per (winning enhancement, entry) in each slot; nothing merged or
     de-duplicated (d6).
3. `PromptTextAdapterTest`: the adapter's text equals `MarkdownCodec.write(document)` of the held prompt (frontmatter
   included), i.e. exactly the text WP05's load check parsed; fragment text is the canonical render of the fragment
   document.

**Validation:** red on assertions.

### T041: The `PromptText` port and its adapter

**Purpose:** the prompt in force as canonical text, kernel-typed (A1, A5).

**Steps:**

1. `PromptText` (`packs.application`): kernel types only, e.g. `Optional<String> promptInForce(Urn missionType,
   PackIdentifier winner, String step)` and a way to get a fragment's canonical text for a winning enhancement entry.
2. `PromptTextAdapter` (`infra.storage`): reads the winner's `MissionType`, takes the step's prompt document, renders
   it with `MarkdownCodec.write`; the same for fragment documents. Wire it as a Spring bean the way sibling adapters are.
3. Count one canonical render per read (hook used by WP10's budget test).

**Validation:** `PromptTextAdapterTest` green; `HexagonalArchitectureTest` green (`APPLICATION_IS_EDGE`).

### T042: `EffectivePromptQuery` and `EffectivePromptOrder`

**Purpose:** slots and ordered active fragments (FR-010 steps 2–4).

**Steps:**

1. `EffectivePromptOrder` (`packs.domain`): the comparator on (level rank, descriptor `name`, enhancement id), code
   point.
2. `EffectivePromptQuery` (`packs.application`), on one compose and one in-force answer (both from the caller's
   session): the prompt in force for `action:M/s` (layer-merge winner of `mission_type:M`, step `s`) via `PromptText`;
   `PromptSlots.parse` once over that text; the enhancers of `action:M/s` via WP27's lookup, filtered by in force
   (d4) and to the winning copy (A11); for each entry naming a declared slot, an item `{packId, packName, level,
   enhancement (declared id, no kind prefix, C-14), text}`; sorted per slot by `EffectivePromptOrder`.
3. Unknown mission type / action → reuse WP27's merged-closure checks (`mission_type_not_found`, `action_not_found`).
   The held-but-not-in-force case (`409 mission_type_not_in_force`, OD-5) is decided in WP10's service from the
   in-force answer; expose what it needs (e.g. whether `mission_type:M` is in the effective set).
4. Two counter hooks for NFR-004: enhancement visits and slot parses (WP10 asserts them). The per-slot sort is excluded
   from the visit count; say why in a comment.
5. Diagnostics: a `prompt_slot_not_empty` entry for each listed slot that is not empty (OD-7/OD-8).

**Validation:** T040 query tests green.

### T043: Determinism at the packs level, and the fingerprint if needed

**Purpose:** NFR-003's precondition: the answer and the closure fingerprint do not depend on configured order (T-09).

**Steps:**

1. In `EffectivePromptQueryTest`, run the query over every permutation of three packs' configured order on a fixture
   built so no other order can pass: a `depends_on` forces merge order away from name order; configured name,
   descriptor `name` and `pack_id` sort differently; enhancement ids authored in reverse file order. Compare the whole
   answer and the closure fingerprint.
2. If the fingerprint differs across permutations (`FileSystemPackSources.fingerprintOf` :40-51 and
   `ClosureQueries.closureFingerprint` :112-118 take packs in configured or closure order), fix it to a canonical order
   — nothing is excluded from the comparison. If the fix would change existing fingerprints asserted elsewhere, stop
   and escalate (it is an expectation change).

**Validation:** permutation test green; `:packs:test` and `:activations:test` green.

## Test strategy

- Red first. Positive controls: the AS3 empty slot listed beside a filled one; the unselected enhancement absent beside
  a selected one. Non-vacuity: the order fixture where id, configured name and descriptor name sort differently.

## Gates (keep green, honestly)

- `HexagonalArchitectureTest` (`APPLICATION_IS_EDGE`, `PACKS_DOMAIN_IS_PURE`), `InForceResolutionPropertyTest`,
  `EffectiveReadOperationBudgetTest` (unchanged), `KindBindingsTest`, `PackFileSetExactnessTest`,
  `WholePackRoundTripTest`, `ReservedRefusalCodesTest`.
- Commands: `:kernel:test`, `:packs:test`, `:activations:test`, `:deployable:test --tests
  '*HexagonalArchitectureTest'`.

## Definition of Done

- Every subtask done and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- Started only after PR C merged and the branch was rebased.
- Tests seen failing first, now passing; ordering total; determinism over permutations.
- Nothing outside `owned_files`.

## Risks

- Without OD-6 from PR C every fragment list is empty and the tests look "green" on empty data: the AS1 test asserts
  three fragments, never "at most".
- A second copy of `NodeReference` would violate DIRECTIVE_044: reuse PR C's.

## Reviewer guidance

- Check the text served equals the text the load check parsed (A5), and fragment text is canonical.
- Check the ordering key's second component is the descriptor `name`, not `packId`.
- Check no `activations` type appears in `packs`, and the port carries kernel types only.
