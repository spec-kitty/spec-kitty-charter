---
work_package_id: WP04
title: Pinned refusals in the gate expectation
dependencies: []
requirement_refs:
- FR-009
- FR-010
- C-002
- C-003
planning_base_branch: feat/asset-template-round-trip
merge_target_branch: feat/asset-template-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/asset-template-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/asset-template-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T014
- T015
- T016
phase: Phase 1 - Foundations
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
role: implementer
authoritative_surface: service/packs/src/test/
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectation.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectationTest.java
- service/packs/src/test/resources/whole-pack/expected-red.yaml
create_intent: []
tags: []
task_type: implement
---
# Work Package Prompt: WP04 — Pinned refusals in the gate expectation

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Give the whole-pack gate's expectation list the shape this mission needs and pin the six built-in sidecar refusals
**once**, in `whole-pack/expected-red.yaml`, so both ratchets — the gate (WP07) and the open-packs round trip (WP05) —
read the same entries (operator ruling R10, post-plan P9, P15). Read `research.md` D8, `WholePackExpectation`,
`WholePackExpectationTest`, `expected-red.yaml` and `exclusions.yaml` first.

Known and accepted: the whole-pack gate is red from WP01 until WP07; it is not part of `:packs:check`.

## Branch Strategy

Planning branch and final merge target: `feat/asset-template-round-trip`. Execution worktrees are allocated per
computed lane from `lanes.json`; do not pick a base branch by hand. Implement with:

```bash
spec-kitty agent action implement WP04 --agent claude --mission asset-template-round-trip-01M5YY7G
```

## Validation discipline

Run the targeted tests for the files you touch and the owning module's fast tier (`./gradlew :packs:test
:packs:check`; `:deployable:test` when named). Never run a whole-repo heavy sweep (`verifyAll` is CI's,
directive NO_FULL_HEAVY_SUITES_IN_MISSION); record the exact commands and pass/fail counts in the review handoff.
Java 25 via `JAVA_HOME=/usr/lib/jvm/temurin-25`; Maven Central HTTP 429 → wait and retry.

## Subtasks

### T014 — File entries with an exact refusal and a pack

- `FileEntry` gains optional `refused` (the exact refusal reason, matched verbatim against the outcome detail after
  the `refused: ` prefix) and optional `pack` (default `built-in`). Rules: an entry has `at` **or** `refused`, never
  both; `refused` matches only a refused file with exactly that reason (a different reason is an unexpected red, a
  green file is `UNEXPECTEDLY_GREEN`); an entry whose `pack` is not the pack being evaluated is ignored by that
  evaluation. Update `load` (allowed keys, messages), `evaluate`, `atOf` usage.
- `WholePackExpectationTest` (same fixture across cases): matching reason passes; different reason fails; the file
  now written and green gives `UNEXPECTEDLY_GREEN` ("remove it"); an entry for another pack does not match and is not
  stale for this pack; `refused` + `at` together fail to load; unknown key fails.

### T015 — Pinned refusals for other readers

- Add a small accessor on `WholePackExpectation` (or a package-private record list) that returns the `refused`
  entries for a given pack: `(pack, path, reason, issue)`. WP05's open-packs test reads it; do not duplicate the
  list anywhere.
- Test: the bundled list returns exactly the six built-in entries (after T016) and none for other packs.

### T016 — `expected-red.yaml`

- Remove the `kinds` entries `asset`, `other:assets`, `other:templates` (issue #193). Change nothing else.
- Add six `files` entries — one per built-in sidecar:
  `assets/docs_structural_lint.py.asset.yaml` and `assets/audiences/{agentic-framework-core-team,automation_agent,
  line_manager,nontech_educator,software_engineer}.md.asset.yaml` — each with
  `refused: "payload assets/<its path value> does not exist"` (e.g.
  `payload assets/assets/audiences/line_manager.md does not exist`, the exact reason WP05's store emits), reason
  "the built-in sidecars anchor `path` at the pack root, not at `assets/` (pack inconsistency, ADR-0036 Decision 5;
  fixed in spec-kitty-open-packs)", issue `"#193"`. Interim link: the open-packs issue number replaces/augments it
  when the operator files it (spec FR-009 as amended).
- Verify each path against the pinned checkout `/home/user/spec-kitty-open-packs/packs/built-in`.

## Definition of Done

- `./gradlew :packs:test --tests '*WholePackExpectation*'` and `:packs:test :packs:check` green.
- `git diff` of `expected-red.yaml`: three kind entries removed, six file entries added, nothing else.

## Risks / reviewer guidance

- Shared with every sibling: touch only our entries; rebase after a sibling merge.
