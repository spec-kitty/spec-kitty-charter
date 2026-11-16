---
work_package_id: WP10
title: Tests, floors and records
dependencies:
- WP09
requirement_refs:
- FR-012
- NFR-003
- NFR-005
- SC-005
- C-006
- C-004
- C-002
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts were generated on slice-5-554-pack-closure-rulesets (via the mission branch kitty/mission-pack-closure-rulesets-01M7FA90). Completed changes merge back into slice-5-554-pack-closure-rulesets, then into main by one pull request (R4).
subtasks:
- T054
- T055
- T056
- T057
- T058
agent: claude
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackCodesCoverageTest.java
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackCodesCoverageTest.java
- service/pack-validation/build.gradle.kts
- service/packs/build.gradle.kts
- docs/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check.md
- docs/adr/0062-nightly-mutation-floor.md
- docs/adr/0065-*.md
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP10 - Tests, floors and records

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else.

Then read, in `kitty-specs/pack-closure-rulesets-01M7FA90/`:

- `spec.md` (the Intent Summary's rulings table);
- `plan.md` (Decisions, and the IC named below);
- `data-model.md`;
- `research/design-r3.md` (with its §7 folds) and `research/squad-plan.md`;
- `research/squad-similar-surfaces.md` (file:line map, as of before slice F; re-check each reference).

Charter rules that bind every subtask:

- The charter's model is canonical. There is no legacy reader and no shim.
- Tests carry only the canonical shape, apart from error-path fixtures.

## Branch Strategy

- Planning base and final merge target: `slice-5-554-pack-closure-rulesets`. The mission branch is `kitty/mission-pack-closure-rulesets-01M7FA90`.
- The execution worktree is allocated per computed lane from `lanes.json`; this mission has one lane.
- Start with `spec-kitty agent action implement WP10 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.

## Objective

Close the slice:

- collapse what remains of the old tests;
- prove that every code is exercised;
- measure the mutation floors and re-set the documented margins (R3-b (i));
- amend the ADRs, so that the records say what the service now does.

Spec FR-012, FR-013, NFR-003 and NFR-005, SC-005; plan IC-09.

## Subtasks

### T054: The test collapse audit

- **Audit:** for each test class listed in `research/squad-similar-surfaces.md` §5, list where each of its behaviours
  is now tested, in a rule, composition or mapper test. This includes `FileSystemPackInspectorTest`,
  `WholePackStoreTest` and `DomainRepositoryServiceTest`.
- **Put the audit table in the PR body,** under "Test collapse". Add a test for any behaviour that has none.
- **Keep the tests in their subject's module** (ADR-0062).

### T055: `PackCodesCoverageTest`

- Enumerate every `PackCodes` constant and every composition code. For each one, find at least one failing and one
  passing fixture, among the rule tests' fixtures and the planted packs.
- **Fail the test when a code has no fixture,** and name the code.

### T056: Mutation floors (R3-b (i))

- **Run** narrowed PIT on `packs.domain.rules`, the mappers, `infra.storage` and `packvalidation`
  (`pit-targets.init.gradle`), then the full nightly task locally if time allows. Use `--max-workers=1`, one run at a
  time.
- **Re-set the documented margins from the measurement:**
  - `pack-validation/build.gradle.kts`, whose comment says "margin 0 until #554";
  - `packs/build.gradle.kts`;
  - ADR-0062's row 105, with an amendment dated today.
- **No floor is lowered without an ADR-0062 amendment.**
- **Record the scores in the PR.**

### T057: The ADRs

- **ADR-0075:** add the amendment of 2026-11-16 from `research/design-r3.md` §6, folded with §7. The rules live in
  the packs domain, there is no judging port, and `pack-validation` is pack-check's library.
  - Decision 4: the converters go, and R2 unifies the codes.
  - Decision 7's wording becomes "needs every pack's contents, never the closure walk".
  - Update the title or description only if the operator agreed to it in R3; otherwise add a note.
- **ADR-0065:** annotate Q1. An unmodelled file is not held, extending D1.
- **Docs checks:** run `check_docs.py`, markdownlint-cli2 and cspell.

### T058: The final golden check, and the drafts

- **The golden snapshot:** the diff list in the PR body groups each row under Q1 or an R2 row. There are no
  unexplained rows.
- **The open-packs draft** (`research/open-packs-drafts/`) lists every expected-code change, from Q1 and R2. It is a
  planning file: write it in the planning pass and hand it to the orchestrator.

## Definition of Done

- The coverage test is green.
- The audit table is complete.
- The floors are measured, and the margins are re-set honestly.
- The ADRs are amended.
- The docs checks pass.
- `verifyAll` is green.

## Reviewer guidance

- Was every collapsed behaviour accounted for?
- Are the margins measured, not guessed?
