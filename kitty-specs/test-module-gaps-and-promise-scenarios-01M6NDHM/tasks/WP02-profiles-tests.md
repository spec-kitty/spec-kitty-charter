---
work_package_id: WP02
title: profiles tested in profiles
dependencies: []
requirement_refs: [FR-002, FR-004, SC-001, SC-002]
planning_base_branch: test-module-gaps-and-promise-scenarios
merge_target_branch: test-module-gaps-and-promise-scenarios
branch_strategy: Planning artifacts for this mission were generated on test-module-gaps-and-promise-scenarios. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test-module-gaps-and-promise-scenarios unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-test-module-gaps-and-promise-scenarios-01M6NDHM
base_commit: 2bec6df9311c4f520a4bbabdedfafbf34f549aec
created_at: '2026-11-05T08:55:22.154654+00:00'
subtasks: [T005, T006, T007, T008]
phase: Phase 1 - Module gaps
agent: claude
history:
- at: '2026-11-05T09:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/profiles/src/test/java/ai/speckitty/charter/profiles/
execution_mode: code_change
owned_files:
- service/profiles/src/test/java/ai/speckitty/charter/profiles/**
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP02 — profiles tested in profiles

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

`profiles` reports 99 % aggregate line coverage, but its own tests leave 29 of 66 mutants untouched (53 % score).
All 29 are in `AgentProfile` (16 accessors, `hashCode`, `toString`) and `AgentProfile.Builder` (13); 2 more survive
in `AgentProfile`. No profile test checks that a built profile keeps every field: the round trip the profile store
and every read depend on. `SelfReviewStep` and `ModeDefault` are records whose only behaviour is refusing a null
field. (FR-002, FR-004.)

## Branch Strategy

Planning base and merge target: `test-module-gaps-and-promise-scenarios`; worktree per computed lane.

```bash
spec-kitty agent action implement WP02 --agent claude --mission test-module-gaps-and-promise-scenarios-01M6NDHM
```

## Subtask T005 — Read the aggregate and its entry points

Read `AgentProfile`, its `Builder`, the records, and `AgentProfileEntityTest` (the only test class). Find how
profiles are built in main code (the YAML reader or mapper in `packs` or `profiles`) and use realistic values: a
real built-in profile is a good fixture source (for example `java-jenny`).

## Subtask T006 — Strengthen `AgentProfileEntityTest` first (C-001)

- A profile built with **every** field set reads every field back, including `selfReviewSteps` and
  `modeDefaults` with their record fields, collaborations, references and specialization. Use one test with a
  focused `satisfies` per field group rather than one test per accessor.
- Equality and hash: two profiles built from the same values are equal and hash alike; changing one field breaks
  equality. Add this only if the class defines value semantics; read `equals` first.
- `toString`: assert it only if something relies on it (logs, refusals). Otherwise leave its mutants and
  triage them as accepted, with a reason.
- Null refusal of `SelfReviewStep` and `ModeDefault`: test it through the entry point that would receive a missing
  field (builder or parser), if that path is real; otherwise one small record test. Name the invariant.
- The assessment's FIX verdict on `AgentProfileEntityTest:101-109`: add the missing message or code assertion
  there.

## Subtask T007 — Planted breaks and PIT

Plant a break per test in main code (for example, the builder drops a field, or an accessor returns the wrong
list), run, record, revert. Main diff must end empty. Then `./gradlew :profiles:pitest`: the no-coverage count must
fall from 29. Triage what is left (kill by strengthening, equivalent, or accepted with a reason), never with a test
that exists only to kill a mutant.

## Subtask T008 — Gates

`./gradlew :profiles:spotlessApply`, then `:profiles:spotlessCheck :profiles:checkstyleTest :profiles:test`.
Record the added test time.

## Definition of Done

- [ ] Full-field round trip, record null refusal via a real entry point, and the `:101-109` FIX done.
- [ ] Planted breaks recorded; main diff empty; profiles' PIT no-coverage count below 29; leftovers triaged.
- [ ] Gates green.

## Reviewer guidance

Reject accessor-by-accessor tests that assert nothing a regression would break. Re-plant one break yourself.
