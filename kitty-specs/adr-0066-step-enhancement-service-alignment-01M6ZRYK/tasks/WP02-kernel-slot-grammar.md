---
work_package_id: WP02
title: 'Phase 1 · The slot grammar in the kernel: PromptSlots and SlotId'
dependencies: []
requirement_refs:
- FR-007
- C-007
planning_base_branch: issue-424-428-adr-0066-service-alignment
merge_target_branch: issue-424-428-adr-0066-service-alignment
branch_strategy: Planning artifacts for this mission were generated on issue-424-428-adr-0066-service-alignment. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-424-428-adr-0066-service-alignment unless the human explicitly redirects the landing branch.
base_branch: issue-424-428-adr-0066-service-alignment
base_commit: ce559ea0fa13b18749d4a7583e727f051e2ea52e
created_at: '2026-11-09T16:19:13.209410+00:00'
subtasks:
- T008
- T009
- T010
- T011
phase: Phase 1 (before PR C)
agent: claude
history:
- timestamp: '2026-11-09T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/markdown/PromptSlots.java
create_intent:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/markdown/PromptSlots.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/markdown/SlotId.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/markdown/PromptSlotsTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/markdown/SlotIdTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/markdown/PromptSlotsCanonicalCorpusTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/markdown/PromptSlots.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/markdown/SlotId.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/markdown/PromptSlotsTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/markdown/SlotIdTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/markdown/PromptSlotsCanonicalCorpusTest.java
role: implementer
tags: []
tracker_refs:
- '#424'
---
# WP02: Phase 1 · The slot grammar in the kernel: PromptSlots and SlotId

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or read
`/home/user/spec-kitty-open-packs/packs/built-in/agent_profiles/java-jenny.agent.yaml` in full.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Build the **pure** slot grammar of ADR-0066 decision 2 in the kernel: `kernel.markdown.SlotId` (the value
`<namespace>:<name>`) and `kernel.markdown.PromptSlots` (a parser over canonical prompt text that answers the declared
slots in order, or typed faults). Pin every edge case in a table test and prove that `MarkdownCodec`'s canonical
render loses no marker. Nothing here names a refusal code or is wired into a load path: WP03 uses it for fragments,
WP05 wires it into the base-prompt load check, and the phase-2 mission (#438) uses it for the read.

Implementation command:
`spec-kitty agent action implement WP02 --agent claude --mission adr-0066-step-enhancement-service-alignment-01M6ZRYK`

## Context

- **Concern:** IC-03 of `plan.md`, its pure part ("Can start now": `PromptSlots`, `SlotId`, the edge-case table, the
  corpus property test). Read IC-03 in full, the Structure Decision bullets "The slot grammar is a pure kernel parser"
  (A1) and "One parse input everywhere: the canonical text" (A5).
- **Requirements:** FR-007 (the grammar half), C-007; feeds FR-004 (no marker in a fragment) and the phase-2 mission's
  effective-prompt read (now adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#FR-003, slots of the read).
- **Rulings / findings:** A1 (kernel placement; `missions.domain` would break `PACKS_DOMAIN_IS_PURE`,
  `sliceDomainDoesNotCross`, `APPLICATION_IS_EDGE`), A5, T-05 (the edge-case table and the corpus property test with
  a mutant), research P1, P2.
- **Read first:** `plan.md` IC-03; `data-model.md` (SlotId, Slot); `research.md` P1, P2;
  `service/kernel/src/main/java/ai/speckitty/charter/kernel/markdown/{MarkdownLines,MarkdownScanner}.java` for the
  house style of the existing pure Markdown helpers, and
  `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/markdown/MarkdownCodecTest.java`
  (`refusalsWithNeighbours`) for the table-test shape.

## Project constraints (apply to every subtask)

- **Test first, red first (C-007).** The table test is written first and seen failing (compile the empty parser stub
  first so the failure is an assertion, not a compile error).
- **Asserted expectations.** This WP edits no existing assertion. Any change to an existing test's expectation:
  stop and escalate to the run lead (class (c)).
- **Pure kernel code.** No dependency on any `packs`, `missions` or `activations` class; no I/O; no regex engine
  other than what the kernel already uses (re2j is on the classpath; plain character scanning is preferred). No
  reflection (native image).
- **PR C boundary (C-004):** no PR C-owned file is touched (list in WP01).
- **No disabled tests, no new suppressions;** Checkstyle limits CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if ≤ 1. A
  marker scanner tends to grow long methods: split it into small private steps.
- **No dependency added** (DIRECTIVE_051).
- **Gradle:** module tasks only, never `verifyAll`:
  `JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=../spec-kitty-open-packs <task>`.
- **Commits** end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01E5wJUD4c1fuEbXL3umMvKS`; no AI model name elsewhere.

## Branch Strategy

- Planning branch and merge target: `issue-424-428-adr-0066-service-alignment`.
- Lanes are computed into `lanes.json` by finalize-tasks; use the worktree it allocates. No dependencies: this WP runs
  in parallel with WP01.

## Subtasks

### T008: Red — the edge-case table and the SlotId tests

**Purpose:** state every answer of the grammar before writing it (T-05).

**Steps:**

1. `SlotIdTest`: valid `a:b`, `spdd:reasons-block`, `x1:y-2`; refused `A:b`, `a:B`, `a`, `a:b:c`, `:b`, `a:`, `1a:b`,
   `a_b:c`, empty. Compare by code point (`a-b:c` < `a:c` < `ab:c` as code points say; assert the actual order).
2. `PromptSlotsTest`, a parameterized "refused / accepted neighbour" table, one row per line below, each stating the
   expected answer:

   | Input | Answer |
   |---|---|
   | `<!--a:b:start-->` … `<!--a:b:end-->` (no spaces) | candidate, valid, empty |
   | `<!--   a:b:start   -->` | candidate, valid |
   | `<!-- A:b:start -->` | candidate, bad id → `MALFORMED` |
   | `<!-- note: see x:start -->` | candidate (ends in `:start`), bad id → `MALFORMED` |
   | a comment spanning lines, `<!--\na:b:start\n-->` | candidate (trimmed content), valid |
   | two markers on one line (inline empty slot) | valid, empty |
   | a marker inside an HTML block | candidate |
   | a marker in inline code or in a fenced block | candidate ("any marker counts", d7) |
   | a marker-shaped comment in a frontmatter value | candidate (frontmatter is part of the canonical text) |
   | an end with no start; an end before its start | `MALFORMED` |
   | a start with no end | `MALFORMED` |
   | the same id declared twice | `MALFORMED` |
   | a start inside an open slot (nesting) | `MALFORMED` |
   | `<!-- decision_id: x -->` | not a candidate |
   | a slot holding only a tab, LF, spaces, or nothing | empty |
   | a slot holding NBSP U+00A0 or zero-width space U+200B | **not** empty |
   | a non-ASCII or non-BMP character between the markers | not empty |
   | a slot holding text | `NOT_EMPTY` (a fault distinct from `MALFORMED`) |

   "Whitespace" is the explicit set ASCII space U+0020, tab U+0009 and LF U+000A, never `Character.isWhitespace`; a
   CR cannot reach the parser (`MarkdownCodec` refuses it first).
3. Add rows for the "any marker" query used for fragments: `PromptSlots.containsMarker(text)` (or an equivalent
   answer of the parse) is true for any candidate of any id, valid or not.

**Validation:** run `:kernel:test`; every row fails on its assertion against an empty stub.

### T009: `SlotId`

**Purpose:** one value for the slot id in every ring (A1, A13).

**Steps:**

1. `kernel.markdown.SlotId`: a record or final class over the string, validated against
   `^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$` (ADR-0066 d2; the `slot` pattern of `step-enhancement.schema.json`).
2. `namespace()` and `name()` accessors; `Comparable` by code point; `toString` the wire form.
3. A static factory that answers `Optional<SlotId>` (or throws an `IllegalArgumentException` named in its javadoc) so
   the parser can classify a bad id as `MALFORMED` without exceptions on the hot path.

**Validation:** `SlotIdTest` green. Javadoc says: the namespace names a concern, not a pack; renaming a slot is a
breaking change for the packs that fill it.

### T010: `PromptSlots` — candidates, ids, pairing, nesting, emptiness

**Purpose:** the parser itself (FR-007 grammar half).

**Steps:**

1. A marker **candidate** is an HTML comment `<!-- … -->` whose trimmed content ends in `:start` or `:end`. Other
   comments are text (the pinned built-in prompts carry `<!-- decision_id: <id> -->`).
2. Its id (content minus the suffix, trimmed) must be a `SlotId`; otherwise the fault is `MALFORMED` at that marker.
3. Pairing: a start precedes its end; each id is declared once; no start opens inside an open slot; an unclosed start
   or an orphan end is `MALFORMED`.
4. Emptiness: only ASCII space, tab and LF between a pair, else `NOT_EMPTY` for that slot. Emptiness is reported per
   slot (`Slot {id, position, empty}`), and the fault list carries `NOT_EMPTY` faults separately from `MALFORMED`, so
   WP05 can make `NOT_EMPTY` blocking or a warning by descriptor version while `MALFORMED` is always blocking.
5. Answer type: e.g. `PromptSlots.Parse {List<Slot> slots; List<Fault> faults}` with `Fault {Kind kind (MALFORMED,
   NOT_EMPTY), Optional<SlotId> slot, int offset or line, String reason}`. Declared slots are in declaration order.
   Deterministic: same text → equal answer.
6. Linear in the text length (the phase-2 mission (#438) counts one parse per read under its bounded-cost gate,
   now adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#NFR-002).

**Validation:** every T008 row green; `:kernel:test` green; `HexagonalArchitectureTest` (run from `:deployable:test
--tests '*HexagonalArchitectureTest'`) green — the kernel names no outer ring.

**Edge cases:** markers separated by content that itself contains `<!--` without `-->`; a comment never closed (text,
not a candidate — pin it with a row); very long input (no catastrophic backtracking: do not use a backtracking regex
over the whole text).

### T011: The canonical-text corpus property test, with a mutant

**Purpose:** load, validate mode and the read all parse `MarkdownCodec.write(document)`, not the raw file (A5). Prove
the canonical render keeps every marker, its order and its emptiness (T-05).

**Steps:**

1. `PromptSlotsCanonicalCorpusTest` in `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/markdown/`
   (it needs `MarkdownCodec`, which lives in `packs.infra.storage.markdown`). For every step prompt of the five pinned
   built-in software-dev prompts (read from the synced open-packs checkout at `openPacksRef=22c14b2d`; tag the test
   `open-packs` as the other pinned-corpus tests are) and every fixture prompt this WP writes itself (the later WPs'
   fixtures do not exist yet: WP02 runs beside WP01), assert `PromptSlots.parse(raw)` equals
   `PromptSlots.parse(MarkdownCodec.write(document))`, where `document` comes from the public entry point
   `MarkdownCodec.parse(raw)`, an `Outcome` that must be `MarkdownCodec.Parsed` (`Parsed.document()`), in ids, order
   and emptiness. `MarkdownCodec.read` is private; do not widen it. WP05 adds the follow-on assertion over the
   mission's slot fixture prompts (WP05 T024), and extends the same property to the exact document the load seat
   parses — the companion `MarkdownDocument` of `MissionTypeDso.Step.prompt()` as `FilesystemPackStore.read` fills it —
   which this test cannot reach: it lives in `infra.storage.markdown`, and `FilesystemPackStore.read` and
   `MissionTypeDso` are package-private to `infra.storage` (third analysis, A11).
2. Add the rows that the canonical render changes: a column-0 `## ` inside a comment is split across sections by
   `MarkdownCodec` (A5) — pin the parse of the **canonical** text for that row.
3. **Mutant:** a deliberately broken canonicaliser (e.g. a function that drops the first comment) makes the property
   assertion fail. Keep it as a test that asserts the failure, so the property is shown non-vacuous.
4. Assert the expected corpus facts: the pinned built-in has 10 markers over 5 prompts, all `spdd:reasons-block`, all
   non-empty (they become warnings in WP05).

**Validation:** `:packs:test` green, including the mutant case.

**Edge cases:** a prompt with frontmatter (the detailed description `MissionTypeBinding.markdownOf` writes, :341-347)
— frontmatter is part of the canonical text; include one such fixture.

## Test strategy

- Table test first (T008), then the value and the parser. Non-vacuity: the corpus property mutant (T011). The 10
  markers / 5 prompts assertion is the positive control for the corpus input.

## Gates (keep green)

- `:kernel:test`, `:packs:test` (whole), `HexagonalArchitectureTest` (kernel placement), Checkstyle.
- Commands also include `:kernel:spotlessCheck :packs:spotlessCheck` for the touched modules.
- Unaffected and must stay green: `ReservedRefusalCodesTest`, `KindBindingsTest`, `PackFileSetExactnessTest`,
  `WholePackRoundTripTest`, `WireTokenParityTest`, `ActivationWireTokenParityTest`.

## Definition of Done

- Every subtask done and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- Every table row and the corpus property test were seen failing first and now pass; the mutant fails as asserted.
- No refusal code is named; nothing outside `owned_files` changed.

## Risks

- Defining "whitespace" with `Character.isWhitespace` admits more than ASCII space/tab/LF: use an explicit set.
- A regex-based scanner can backtrack badly; prefer a single forward scan.

## Reviewer guidance

- Walk the table row by row against ADR-0066 d2 and d7 ("a slot marker, of any slot id").
- Check the parse input in T011 is the canonical text and the mutant genuinely breaks it.
- Check the kernel classes import nothing outside the kernel and the JDK.
