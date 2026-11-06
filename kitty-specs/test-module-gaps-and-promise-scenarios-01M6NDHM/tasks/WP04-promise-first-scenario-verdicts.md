---
work_package_id: WP04
title: Apply the promise-first scenario verdicts
dependencies: []
requirement_refs: [FR-005, FR-006, SC-003]
planning_base_branch: test-module-gaps-and-promise-scenarios
merge_target_branch: test-module-gaps-and-promise-scenarios
branch_strategy: Planning artifacts for this mission were generated on test-module-gaps-and-promise-scenarios. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test-module-gaps-and-promise-scenarios unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-test-module-gaps-and-promise-scenarios-01M6NDHM
base_commit: df1fd89a1d6c6e1b4c965fc91218e4d67dbd23ea
created_at: '2026-11-05T08:56:09.127278+00:00'
subtasks: [T012, T013, T014, T015, T016]
phase: Phase 2 - Scenario verdicts
agent: claude
history:
- at: '2026-11-05T09:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/scenarios/
execution_mode: code_change
owned_files:
- contracts/charter/scenarios/**
- src/test/resources/features/**
- tests/scenarios/charter/**
- docs/architecture/behavior-and-scenarios.md
- contracts/charter/changes/**
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP04 — Apply the promise-first scenario verdicts

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Each of the 27 promise-first scenarios gets the verdict the gap analysis and the operator gave it (FR-005,
FR-006). Afterwards no scenario file is left that no runner executes and nothing accounts for, and neither README
says "not yet wired".

## Branch Strategy

Planning base and merge target: `test-module-gaps-and-promise-scenarios`; worktree per computed lane.

```bash
spec-kitty agent action implement WP04 --agent claude --mission test-module-gaps-and-promise-scenarios-01M6NDHM
```

## Inputs (read in full first)

- `kitty-specs/test-module-gaps-and-promise-scenarios-01M6NDHM/research/374-gap-analysis.md`: the per-row
  verdicts, the evidence and the proposed README wording.
- `research.md` R-04: the operator's rulings.
- How the contract suite runs: `CharterContractSuite` runs `@contract` and excludes `@WIP`. The build copies
  `contracts/charter/scenarios/**` (`service/deployable/build.gradle.kts` ~80-82). A contract change adds one changelog
  fragment under `contracts/charter/changes/` named `YYYY-MM-DD-slug.md` with one `###` block (ADR-0047;
  `ChangelogFragments.java`; read `contracts/charter/changes/README.md`); never edit `contracts/charter/CHANGELOG.md`.

## Subtask T012 — Move the ADR-0005/0019 cluster under the contract, tagged `@WIP`

Move the 15 registration and distribution scenarios (from `src/test/resources/features/`, per the table) into
`contracts/charter/scenarios/<cluster>/`, tagged `@WIP`, so they live next to the contract without running. Keep
the copy rule unchanged.

- Add a header comment: decided (ADR-0005, ADR-0019), not built (`docs/api/service-contract.md` "Decided, not
  built"; ADR-0055 D1).
- Note what changes when they are built: snake_case refusal codes, levels instead of tiers, same-level-only
  collisions.
- Note the ADR-0048 versus ADR-0005 tension for TRUST-003 and DISTRIBUTE-002.

Move the fixtures they need too, or point to them. Check that `CharterContractSuite` still runs the same scenario
count, since `@WIP` is excluded: record before and after.

## Subtask T013 — Wire SHARING-001 and SHARING-004, red-first

Add one `@contract` scenario each to `contracts/charter/scenarios/**/offering.feature` (or the feature the analysis
names), using existing steps only:

- **SHARING-001:** a pack with a file that fails its schema makes the offering refuse with 500 `pack_unreadable`.
- **SHARING-004:** an asset whose path points outside `assets/` is refused when the pack loads.

Write the scenario first. Run `:deployable:test` and pin the exact `detail` from the real response; if a step
doesn't exist, stop and report rather than add Java. Add one changelog fragment under `contracts/charter/changes/` for the two new scenarios.

## Subtask T014 — Placeholder for #380 and pointers for the rest

1. A `@WIP` contract scenario for REFUSAL-001 that points to #380 (an `enhances` merge that drops fields is
   refused).
2. SHARING-003 → #381; no scenario until #381 is built.
3. Reduce `tests/scenarios/charter/**` (README, `PROMISES.md`, the three feature folders) to a pointer per promise:
   the covering contract scenario (MAP), the `@WIP` or reserved placeholder plus its ADR (RETIRE), or the issue
   (GAP: #380, #381). Delete the old `.feature` files there, since nothing runs them and the pointers now account for
   them. Remove the "not yet wired" text and the core-path authority note.

## Subtask T015 — README and architecture page

- Rewrite `src/test/resources/features/README.md`, or remove the folder if it ends up empty and point to the new
  location from `docs/architecture/behavior-and-scenarios.md`. Use the wording proposed in the analysis.
- Update the "Where they live" table of `docs/architecture/behavior-and-scenarios.md` (~52-53).
- Run `python3 scripts/docs/check_docs.py all`, `npx --yes markdownlint-cli2 <files>` and
  `npx --yes cspell --no-progress <files>`.

## Subtask T016 — Verdict record and gates

1. Put a final 27-row verdict table (scenario, verdict, where it lives now or what covers it) in the PR-facing note.
2. `grep` that every one of the 27 titles is accounted for.
3. Run `./gradlew :deployable:test -PopenPacksDir=/home/user/spec-kitty-open-packs`: the two new `@contract`
   scenarios pass, and the `@WIP` ones are excluded.

## Definition of Done

- [ ] 27/27 verdicts applied; 0 unaccounted scenario files; "not yet wired" gone.
- [ ] Two new `@contract` scenarios pass (red-first noted); the #380 placeholder is `@WIP`; the copy rule is
  unchanged; the contract changelog convention is followed.
- [ ] Docs checks clean.

## Reviewer guidance

Count the 27 yourself against the analysis. Confirm `CharterContractSuite` runs exactly the old scenarios plus the
two new ones.
