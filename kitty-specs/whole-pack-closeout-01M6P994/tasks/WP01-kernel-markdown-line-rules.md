---
work_package_id: WP01
title: '#299: the Markdown line rules move to the kernel; step texts refuse when built'
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- NFR-003
- C-001
- SC-001
planning_base_branch: epic-186-closeout
merge_target_branch: epic-186-closeout
branch_strategy: Planning artifacts for this mission were generated on epic-186-closeout. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into epic-186-closeout unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-whole-pack-closeout-01M6P994
base_commit: e8c0bb678663ad29f47cb31f112ecf8123f1b819
created_at: '2026-11-05T16:48:45.681722+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
phase: Phase 1 - Fixes
history:
- at: '2026-11-05T17:10:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/markdown/
create_intent:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/markdown/MarkdownScanner.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/markdown/MarkdownLines.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/markdown/package-info.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/markdown/MarkdownScannerTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/markdown/MarkdownLinesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StepDocumentRulesTest.java
execution_mode: code_change
model: ''
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/markdown/**
- service/kernel/src/test/java/ai/speckitty/charter/kernel/markdown/**
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/markdown/**
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/markdown/**
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/markdown/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StepDocumentRulesTest.java
- service/missions/src/main/java/ai/speckitty/charter/missions/domain/StepDocument.java
- service/missions/src/main/java/ai/speckitty/charter/missions/domain/StepSection.java
- service/missions/src/test/java/ai/speckitty/charter/missions/domain/MissionValuesTest.java
- docs/adr/0040-content-form-and-payload-kinds.md
- docs/adr/0044-mission-types-consolidated-into-one-sanitized-shape.md
- docs/architecture/hexagonal-service.md
role: implementer
tags: []
task_type: implement
tracker_refs:
- '#299'
---

# Work Package Prompt: WP01 – #299: the Markdown line rules move to the kernel

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave
according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Issue #299: `StepDocument` (`:missions`) checks its Markdown only at write, because `MISSIONS_DOMAIN_IS_PURE` keeps it
from `packs.domain.markdown`. Move the line-level rules into the kernel (both `:missions` and `:packs` already depend
on `:kernel` with `api`; `KERNEL_IS_PURE` allows `java..` and JSpecify), and have `StepDocument` and `StepSection`
check them when built. Design: plan D1–D3, D6, D7; research R1 and the squad table.

## Branch Strategy

Planning branch and merge target: `epic-186-closeout`. Execution worktrees are allocated per lane from `lanes.json`.
Implement with `spec-kitty agent action implement WP01 --agent claude --mission whole-pack-closeout-01M6P994`.

## Subtasks

### T001 — Move the scanner; add `MarkdownLines`

- `git mv` `service/packs/src/main/java/ai/speckitty/charter/packs/domain/markdown/MarkdownScanner.java` to
  `service/kernel/src/main/java/ai/speckitty/charter/kernel/markdown/MarkdownScanner.java`; change the package.
  Public: `scan`, `Kind`, `Line`, `Refused`, `refused` (the packs codec uses them). `headingProblem`/`blockProblem`
  stay package-private; `FenceTracker`/`Fence` stay package-private.
- New `MarkdownLines` (final, private constructor) with the three public statics moved verbatim from
  `MarkdownDocument`: `headingProblem`, `blockProblem`, `openingProblem`, each `Optional<String>` (not null).
- New `package-info.java` (`@NullMarked`): the `markdown` form's line rules, shared by the packs domain's model and
  codec and by the missions domain's step texts (#299); the structural model stays in `packs.domain.markdown`.
- Move `MarkdownScannerTest` to `service/kernel/src/test/java/ai/speckitty/charter/kernel/markdown/`.
- Check `service/kernel/build.gradle.kts` has the test dependencies the moved test needs (JUnit, AssertJ). If not,
  follow how `kernel.validation` tests are wired; add no new dependency.

### T002 — Repoint the packs side

- `MarkdownDocument`: the three statics become one-line delegates to `MarkdownLines` (keeps the four call sites:
  `PackDocument`, `Guide`, `GuideSection`, `GuideMarkdown`), javadoc saying the rule lives in the kernel. Its
  constructor and `Section` keep throwing `IllegalArgumentException`.
- `MarkdownCodec` and `infra.storage.markdown/package-info.java`: imports and wording for the kernel scanner.
- `packs.domain.markdown/package-info.java`: the model lives here; its line rules live in `kernel.markdown`.

### T003 — Step texts refuse when built

- `StepDocument`: after the existing `TITLE_RULES`/`DESCRIPTION_RULES` (keep them first, so a blank title still
  reports "title is not blank"), guard `title` with `headingProblem`, `preamble` with `blockProblem`, and, when both
  title and description are empty, `openingProblem(preamble)`. Throw `ConstraintBreachException(part + " " + why)`,
  as `PackDocument.guard` does. Update the javadoc (the text rules are the `markdown` form's line rules, from the
  kernel; the codec still checks the description's YAML at write).
- `StepSection`: guard title with `headingProblem` and content with `blockProblem`; javadoc `@throws`.

### T004 — Tests

- `MarkdownLinesTest` (kernel): the problem-function cases of `MarkdownDocumentTest` (titles, closing `#`, accepted
  titles, blocks, openings incl. BOM) move here as `@MethodSource` providers. `MarkdownDocumentTest` keeps its
  model cases and may keep a delegation smoke test.
- `StepDocumentRulesTest` (`:packs`, `infra.storage`, since `:missions` cannot see `MarkdownDocument`): for each
  refused text, assert field by field that `StepDocument` (title; preamble) and `StepSection` (title; content)
  throw `ConstraintBreachException`, and `MarkdownDocument` throws `IllegalArgumentException` whose message, minus its
  field prefix, is contained in the breach's. Opening: refused with no title and no description; accepted with a
  title; accepted with a description (D3 controls). For each accepted text: build the step text into a mission-type
  pack (reuse `MissionTypeBindingTest`'s fixture and write path) and assert write then read gives an equal
  `StepDocument` (builds-means-writes).
- `MissionValuesTest`: add negative cases (multi-line title, preamble with a leading blank line, section content
  with an H1, opening `---`) to keep a `:missions`-local guard.

### T005 — Docs

- ADR-0040: a dated note (2026-11-05, #299) under its amendment of 2026-10-29: the line rules live in
  `kernel.markdown`, the model in `packs.domain.markdown`; `StepDocument` and `StepSection` check them when built;
  the description's YAML guards stay write-time refusals.
- ADR-0044 decision 7: one dated line: the missions domain checks step texts through the kernel's line rules (#299).
- `docs/architecture/hexagonal-service.md` kernel row (~L43): add "the `markdown` form's line rules".
- Update `updated:` frontmatter where the docs checks require it.

## Definition of Done

- `./gradlew :kernel:test :missions:test :packs:test :deployable:test spotlessCheck checkstyleMain checkstyleTest`
  green on Java 25; `HexagonalArchitectureTest` unchanged and green.
- No file under `packs.domain.markdown` holds line-rule logic; `MarkdownDocument` delegates.
- Each refused table row fails building for each field it applies to; each accepted row writes and reads back.

## Reviewer guidance

Check the opening rule's exemption (title or description present), that `TITLE_RULES` runs first, that no public
surface widened beyond what the codec uses, and that the agreement test is table-driven from one source.
