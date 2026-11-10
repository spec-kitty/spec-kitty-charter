---
work_package_id: WP03
title: Phase 1 · The step-enhancement and fragment rules, pure
dependencies:
- WP01
- WP02
requirement_refs:
- FR-004
- FR-005
- FR-006
- C-003
- C-007
- SC-002
planning_base_branch: issue-424-428-adr-0066-service-alignment
merge_target_branch: issue-424-428-adr-0066-service-alignment
branch_strategy: Planning artifacts for this mission were generated on issue-424-428-adr-0066-service-alignment. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-424-428-adr-0066-service-alignment unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-adr-0066-step-enhancement-service-alignment-01M6ZRYK
base_commit: 93c73514ba29da983f65ba783f2a6a681a819780
created_at: '2026-11-09T17:32:54.285723+00:00'
subtasks:
- T012
- T013
- T014
- T015
phase: Phase 1 (before PR C)
agent: claude
history:
- timestamp: '2026-11-09T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepEnhancementRules.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepEnhancementRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepEnhancementFault.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepEnhancementFacts.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/StepEnhancementRulesTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepEnhancementRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepEnhancementFault.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepEnhancementFacts.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/StepEnhancementRulesTest.java
role: implementer
tags: []
tracker_refs:
- '#428'
---
# WP03: Phase 1 · The step-enhancement and fragment rules, pure

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or read
`/home/user/spec-kitty-open-packs/packs/built-in/agent_profiles/java-jenny.agent.yaml` in full.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Write `StepEnhancementRules`, a **pure** function in `packs.domain` that judges one pack's step enhancements, their
companions and the pack's graph edges, and answers typed faults (`StepEnhancementFault`), each naming the file it
belongs to. It covers FR-004 (fragment content), FR-005 (enhancement structure) and FR-006 (identity and target,
including operator ruling **R-OVR**: no `overrides` edge from a step enhancement at all), every rule with one failing
fixture and one passing twin that differ only in the fault (SC-002). This WP names **no**
`RefusalCode` and touches no load path: WP04 maps each fault to `prompt_fragment_invalid` and wires it into validate
mode, load and registration in the same commit that marks the code raised (T-15).

Implementation command:
`spec-kitty agent action implement WP03 --agent claude --mission adr-0066-step-enhancement-service-alignment-01M6ZRYK`

## Context

- **Concern:** IC-02 of `plan.md`, the pure part ("Can start now": `StepEnhancementRules`, precedence and companion
  twins at the domain level). Read IC-02 in full ("The rules", "Precedence", "Orphans").
- **Requirements:** FR-004, FR-005, FR-006, C-003, C-007; SC-002.
- **Rulings:** D8 (stem unique per pack across type directories; by operator ruling **A2**, 2026-11-09, WP04 also
  withholds the second file from the pack at load, using this WP's `STEM_REPEATED` faults), **A1** (2026-11-09: an id
  that differs from its file stem is a **schema** violation through the store's existing stem rule, so it never
  reaches these rules and has no fault here), D9 (exactly one `enhances` edge, to an
  `action:<type>/<step>`, bare or `<pack>::`-qualified), R-OVR (2026-11-09: a step enhancement is the source of no
  `overrides` edge, whatever its target, its own mission type included; an enhancement only adds), D10 (all of these answer `prompt_fragment_invalid` — mapped in
  WP04), D11 (one companion per entry, any stem matching the schema pattern, each named once, orphans refused), D12
  (an absent `prompt_fragments` key is refused; `[]` already fails the schema), C-003 (the directory is checked
  against the edge, never read instead of it), research P9 (precedence), T-08, T-21.
- **Read first:** `plan.md` IC-02; `data-model.md` (PromptFragment, StepEnhancement invariant tables);
  `research.md` D8–D12, P8, P9; `contracts/packs/schemas/step-enhancement.schema.json`;
  `service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java` for how existing pack rules take
  facts and answer findings (mirror its shape so WP04's mapping is one switch).

### Files shared with other work packages

- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepEnhancement.java` (owned by WP01) — optional:
  switch the entry's slot to `kernel.markdown.SlotId` (WP02) if WP01 kept a plain string. Make only that edit and
  record `out-of-map: StepEnhancement.java — slot typed as kernel SlotId` in the commit message.

## Project constraints (apply to every subtask)

- **Test first, red first (C-007).** Every rule: a failing fixture and a passing twin, written first and seen failing.
- **Asserted expectations.** No existing assertion is edited. `PromptFragmentContractsTest:53-55` ("an enhancement
  without fragments stays valid", true at schema level) is **not** edited: the D12 rule test carries a cross-reference
  comment to it instead (T-21). Any other expectation edit: stop and escalate (class (c)).
- **Pure domain.** No I/O, no `infra` type, no `RefusalCode` constant (naming one would turn
  `ReservedRefusalCodesTest` red before WP04 raises it). `PACKS_DOMAIN_IS_PURE` holds; the rules may name
  `kernel.markdown.PromptSlots` and `SlotId` (A1).
- **Per pack.** The edge, the directory, the stem and the companions are facts of one pack. Target resolution across
  packs is the phase-2 mission's (#438); do not judge whether the target step exists here.
- **PR C boundary (C-004):** no PR C-owned file is touched (list in WP01).
- No disabled tests, no suppressions; Checkstyle limits CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if ≤ 1.
- **Gradle:** `JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=../spec-kitty-open-packs :packs:test`;
  never `verifyAll`.
- **Commits** end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01E5wJUD4c1fuEbXL3umMvKS`; no AI model name elsewhere.

## Branch Strategy

- Planning branch and merge target: `issue-424-428-adr-0066-service-alignment`.
- Lanes from `lanes.json`. Claimable once WP01 (the entity) and WP02 (the grammar) are approved or done.

## Subtasks

### T012: Red — one failing fixture and one passing twin per rule

**Purpose:** SC-002 at the domain level; the precedence and companion twins of T-08.

**Steps:**

1. `StepEnhancementRulesTest` (`packs.domain`, plain JUnit + AssertJ), built on in-memory `StepEnhancementFacts`
   (T013), not temp dirs. One `@Nested` class per rule family (content, structure, identity/target), each case a
   faulty fixture plus its twin that differs only in the fault. The twin asserts **no** fault.
2. Cases (each answers one fault naming the file; the twin none):
   - fragment blank; fragment with frontmatter (the fact "carries frontmatter" comes from the companion read); fragment
     containing `<!-- x:y:start -->`; fragment with a marker of an invalid id (`<!-- note: see x:start -->`); a
     marker inside a fenced code block (counts, d7 — pin it so a later exemption is a visible decision);
   - a slot named twice in one enhancement; an entry whose companion is missing; one enhancement naming the same path
     in two entries; two enhancements naming one companion; an orphan `*.prompt-fragment.md` beside a valid pair;
   - zero `enhances` edges; two `enhances` edges; an edge whose target is `tactic:x`; a target `action:other/plan`
     while the file sits in `missions/software-dev/enhancements/`; a qualified target `base::action:software-dev/plan`
     (valid twin); **R-OVR:** an enhancement that is the source of an `overrides` edge beside its single `enhances`
     edge (targets `mission_type:software-dev`, its own mission type, and, as a second case, `tactic:x`), each against
     the twin with only the `enhances` edge; **positive control for R-OVR:** an `overrides` edge in the same pack whose
     source is another kind (`tactic:a → tactic:b`) yields no step-enhancement fault, so the rule keys on the source
     kind; the same id (= stem) in `missions/a/enhancements/` and `missions/b/enhancements/` of one pack, with **exactly
     one** `enhances` edge, `step_enhancement:<id> → action:a/<step>` (D8; the answer is **exactly one** fault,
     `STEM_REPEATED` at the `missions/b/…` file, the second in path order: no `DIRECTORY_MISMATCH` for it and no
     `EDGE_COUNT`, fourth analysis A2); its twin, the second file renamed (file and id) with its own single edge to
     `action:b/<step>`, answers none; **the path-order twin (fourth analysis, A5):** the same id in
     `missions/plan/enhancements/` and `missions/plan-x/enhancements/`, one edge to `action:plan/<step>`, passed to the
     rules in `String` order (`plan-x/` first, since `'-'` sorts below `'/'`) → `STEM_REPEATED` at the `missions/plan-x/…`
     file, the second by `PackPolicy.comparePaths`, so the answer does not follow the input order; **edge-count
     interaction (A2):** an enhancement with two `enhances` edges, one to `tactic:x` and one to `action:other/plan`
     while it sits in `missions/software-dev/` → exactly `EDGE_COUNT` (no `EDGE_TARGET_NOT_STEP`, no
     `DIRECTORY_MISMATCH`); `prompt_fragments` key absent (D12, with the
     cross-reference comment to `PromptFragmentContractsTest:53-55`). The target forms: the qualified
     `base::action:software-dev/plan` and the **bare** `action:software-dev/plan` are both valid twins here — this
     test is the only place in the mission that uses the bare form (every composing fixture qualifies it, third
     analysis A6);
   - **orphan beside a refused enhancement (third analysis, A4):** a fragment in a directory whose facts record a
     step-enhancement file that could not be parsed into the facts (or that is withheld) answers **no**
     `COMPANION_ORPHAN`; its twin, the same fragment in a directory whose enhancements all parsed, answers
     `COMPANION_ORPHAN`;
   - **unreadable companion:** an entry whose companion the facts mark unreadable answers no fault from this WP (the
     companion read already reported it); its twin with the companion missing answers `COMPANION_MISSING`.
3. A **multi-fault** case: four faults in one pack are all reported, each at its file, in a deterministic order
   (pack-relative path by `PackPolicy.comparePaths`, then rule order). Use WP04's scenario shape so the count is the
   same there: a marker in `a`'s fragment, two `enhances` edges on `b` (which answers `EDGE_COUNT` only, A2), an
   `overrides` edge from `c`, a directory mismatch on `d` → exactly 4.

**Validation:** `:packs:test` shows every new case failing on its assertion against a stub that answers no faults.

**Edge cases:** `prompt_fragments: []` is refused by the schema (`minItems`, `PromptFragmentContractsTest:111-119`)
before any semantic rule runs; it is not a case here (a schema-invalid file is never parsed into an entity). Nor is
an id that differs from its file stem (operator ruling A1): the store's stem rule refuses it as a schema violation
(`FilesystemPackStore.readKind`, :686-690; `FileSystemPackInspector.misnamedArtifact`, :470-479), and WP04 pins that
side. Write a
one-line comment in the test saying so and pointing to WP04's precedence test. The same holds for an entry whose
`slot` breaks `<namespace>:<name>` (FR-005): the schema's `slot` pattern refuses it as a schema violation, so it is
not a rule case either. WP03 pins the `prompt_fragment_invalid` side of FR-005 (repeated slot, missing companion,
orphan); WP04 pins the schema side (T016 scenario, T019 precedence test). Comment both in the test.

### T013: The facts value and the fragment content rules (FR-004)

**Purpose:** a single input shape for validate mode and load, and the first rule family.

**Steps:**

1. `StepEnhancementFacts` (`packs.domain`, value): for one pack, the list of parsed enhancements with their
   pack-relative file path and type directory; per companion, its pack-relative path, whether it carries frontmatter,
   whether its body is blank, and its **canonical text** (A5: the caller supplies `MarkdownCodec.write(document)`),
   or that it is **unreadable** (the caller's companion read refused it); the set of `*.prompt-fragment.md` paths found
   under `missions/*/enhancements/` (for orphans: filled in validate mode only — at load the caller passes none,
   because the store's `PackRead` lists no unnamed file, so `COMPANION_ORPHAN` never fires there, third analysis A9);
   the set of `*.step-enhancement.yaml` paths the caller could not parse into the facts or withheld (A4); the pack's
   `enhances` and `overrides` edges whose source is a `step_enhancement:` node (relation, source URN, target string,
   qualifier). Plain values only: WP04 fills them from the store's DSOs at load and from the inspector's parsed
   documents in validate mode; no `infra` type appears here.
2. `StepEnhancementFault` (`packs.domain`): an enum of rule ids (e.g. `FRAGMENT_BLANK`, `FRAGMENT_FRONTMATTER`,
   `FRAGMENT_HAS_MARKER`, `SLOT_REPEATED`, `COMPANION_MISSING`, `COMPANION_SHARED`, `COMPANION_ORPHAN`,
   `EDGE_COUNT`, `EDGE_TARGET_NOT_STEP`, `OVERRIDES_EDGE`, `DIRECTORY_MISMATCH`, `STEM_REPEATED`, `NO_FRAGMENTS`;
   there is no id-stem member, operator ruling A1) plus a
   record `{rule, path, reason}`. The enum order is the tie-break order of P9.
3. Content rules: blank → `FRAGMENT_BLANK`; frontmatter → `FRAGMENT_FRONTMATTER`; `PromptSlots` finds any marker
   candidate in the canonical text → `FRAGMENT_HAS_MARKER`. An unreadable companion gets no content rule. Say in the
   javadoc that `FRAGMENT_BLANK` and `FRAGMENT_FRONTMATTER` are the pure statement of FR-004: on both real paths the
   companion read refuses those fragments first (the closed `prompt-fragment` structure schema), so a pack meets them
   as the companion read's fault (data-model.md, "Where each enhancement or fragment fault points").

**Validation:** the content cases of T012 green.

### T014: The structure rules (FR-005, D11)

**Purpose:** every entry maps to exactly one companion and every companion to exactly one entry.

**Steps:**

1. A slot id occurs at most once per enhancement (`SLOT_REPEATED`). The id's grammar is the schema's `slot` pattern: a
   bad id is a schema violation that WP04 pins, never a rule fault and never `prompt_fragment_invalid` (FR-005) — say so
   in the javadoc. `StepEnhancementFault` has no grammar member.
2. Each entry's companion exists beside the enhancement file (`COMPANION_MISSING`, at the enhancement's path, naming
   the missing companion).
3. Each companion is named by exactly one entry across the pack's enhancements of that directory: a second naming
   (same enhancement or another) is `COMPANION_SHARED` at the **second naming enhancement's** path (path order; within
   one directory `comparePaths` and the store's `String` order agree, A5), the
   file the store's own `named by two artifacts` refusal names at load (`FilesystemPackStore.java:804-814`), so validate
   mode and load point at the same file (third analysis, A4).
4. A `*.prompt-fragment.md` under `missions/*/enhancements/` named by no entry is `COMPANION_ORPHAN` at its path (D11),
   **unless** its directory holds a step-enhancement file recorded as unparsed or withheld (A4): that file's own
   fault already refuses the pack, and the fragment may be one it names. At the staged load WP04 reclassifies the
   store's `unmodelled` entry under the same exclusion (research P8).

**Validation:** the structure cases of T012 green, including the companion twins.

### T015: The identity and target rules (FR-006, D8, D9, D12, C-003) and deterministic order

**Purpose:** the graph edge stays the only statement of the target (ADR-0064) and ids cannot collide.

**Steps:**

0. **Path order is one comparator (fourth analysis, A5):** `PackPolicy.comparePaths` (`PackPolicy.java:989-999`,
   package-visible, segment by segment by code point; the same order `PackPolicy.PRECEDENCE` :100-102 and
   `artifactRules` :312 use in validate mode). Sort the enhancement files by it before any rule runs, whatever order
   the caller passed: the store's listing sorts by `String` (load), the inspector by `comparePaths` (validate), and
   the two disagree across sibling directories such as `plan/` and `plan-x/`. Every "path order" below means this
   comparator.
1. Exactly one `enhances` edge from `step_enhancement:<id>` in the pack's graph documents (`EDGE_COUNT` for 0 or ≥ 2).
   **Rule interactions (fourth analysis, A2):** the edges key on the URN, so the files of one id share them. Judge
   steps 1–3 once per id, at its **first** file in path order; when the count is not 1, skip steps 2 (target) and 3
   (directory) for that id. R-OVR (the `overrides` bullet of step 2) is judged on its own. A `STEM_REPEATED` file
   (step 5) gets no other identity or edge fault.
2. Its target is `action:<type>/<step>`, bare or `<pack>::`-qualified (`EDGE_TARGET_NOT_STEP` otherwise).
   - **R-OVR:** no `overrides` edge has `step_enhancement:<id>` as its source, whatever the target
     (`OVERRIDES_EDGE`, at the enhancement's path, naming the edge's target). An enhancement only adds; this prevents
     the E7 instability PR C flagged for an enhancement that overrides its own mission type. Red first: the T012
     R-OVR cases fail against the stub before this rule exists, and their positive control stays green after it.
3. The file's `<type>` directory equals the edge's `<type>` (`DIRECTORY_MISMATCH`). The directory is **checked
   against** the edge, never used as the target (C-003).
4. (No id-stem rule: operator ruling A1 makes it the store's schema-level stem rule; a file that reaches these rules
   has id = stem, so "stem repeated" and "id repeated" are one condition.)
5. The id is unique in the pack across type directories (`STEM_REPEATED`, at the second and every later file in path
   order). This rule must be judged **before** the generic duplicate-id rule (`DUPLICATE_ARTIFACT_ID` in validate
   mode; at load, `Pack.guardKind`'s same-id rule, `Pack.java:148-152`, at `build()`) so the author sees
   `prompt_fragment_invalid`. The rules run on facts built **before** the assembly, so WP04 can withhold each
   `STEM_REPEATED` file from `Pack.Builder` (operator ruling A2); keep the answer's `path` exactly the file's
   pack-relative path, which is how WP04 matches it.
6. At least one `prompt_fragments` entry (`NO_FRAGMENTS`, for the absent-key shape, D12).
7. Sort the answer by (pack-relative path, then `StepEnhancementFault` order). WP04 appends these faults last in the
   assembly, so among them the first is deterministic; the fault a read names is the first in assembly order (plan
   erratum E-3, contracts README canonical section). Do not sort anything outside this answer.

**Validation:** all T012 cases green; the multi-fault case reports in the asserted order on repeated runs.

**Edge cases:** a prose mention of an artifact in a fragment is text, never a reference (d3) — no rule reads it; add a
twin case showing a fragment that names `tactic:x` in prose has no fault.

## Test strategy

- Failing fixture + passing twin per rule (SC-002); the multi-fault case pins completeness and order; the fenced-marker
  and prose-mention cases pin the d3/d7 readings.

## Gates (keep green)

- `:packs:test` (whole), `HexagonalArchitectureTest` (`PACKS_DOMAIN_IS_PURE`), `ReservedRefusalCodesTest` (unchanged:
  no `RefusalCode` named), `PromptFragmentContractsTest` (unedited), Checkstyle.
- Commands: `:packs:test`, `:packs:spotlessCheck`, `:packs:checkstyleMain :packs:checkstyleTest`.

## Definition of Done

- Every subtask done and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- Every rule has a red-first failing fixture and a passing twin; the order is deterministic.
- No `RefusalCode` named; no load path changed; nothing outside `owned_files` (plus the optional listed edit).

## Risks

- Folding the stem rule into the generic duplicate-id rule would surface the wrong code: keep it a separate,
  first-run rule.
- Reading the target from the directory "for convenience" breaks C-003: the reviewer must see the edge as the only
  source.

## Reviewer guidance

- Check each twin differs from its fixture only in the fault.
- Check the javadoc of `StepEnhancementFacts` states the canonical-text input (A5).
- Check no class here imports `RefusalCode`, `infra` or `missions` types.
