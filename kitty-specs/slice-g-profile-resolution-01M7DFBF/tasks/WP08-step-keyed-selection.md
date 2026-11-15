---
work_package_id: WP08
title: Step-keyed selection read and links (after slice F)
dependencies:
- WP06
requirement_refs:
- FR-012
planning_base_branch: slice-g-538-profile-resolution
merge_target_branch: slice-g-538-profile-resolution
branch_strategy: Planning artifacts for this mission were generated on slice-g-538-profile-resolution. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-g-538-profile-resolution unless the human explicitly redirects the landing branch.
subtasks:
- T034
- T035
- T036
phase: Phase 5 - After slice F
history:
- at: '2026-11-15T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/paths/activations_activationId_mission-types_missionType_actions_action_profile-selection.yaml
create_intent:
- contracts/charter/paths/activations_activationId_mission-types_missionType_actions_action_profile-selection.yaml
- contracts/charter/scenarios/step-profile-selection.feature
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/StepProfileSelectionController.java
execution_mode: code_change
owned_files:
- contracts/charter/paths/activations_activationId_mission-types_missionType_actions_action_profile-selection.yaml
- contracts/charter/scenarios/step-profile-selection.feature
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/StepProfileSelectionController.java
role: implementer
tags:
- blocked-on-slice-f
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP08 — Step-keyed selection read and links (after slice F)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective — BLOCKED until slice F (#537, PR #567) lands on `main`

Do not claim this WP before the mission branch is rebased onto `main` with slice F merged.

Serve `selectAgentProfileForStep` at `/activations/{activationId}/mission-types/{missionType}/actions/{action}/profile-selection`.
It resolves the step through F's reshaped `requireAction` and `stepPlace`, reads the step's `role`, and answers
exactly what `selectAgentProfile` answers for that role. A step with no role answers the fallback, since any role
may act. Add a `profile-selection` link to the effective step read and to the action-context read.

F facts (`research.md` §7):
- `MissionStep` has `substeps`;
- the `step` link relation is gone;
- a substep never delegates to an `agent_profile`, so the role is the only source of a step's profile;
- `Relationship` is built only through the typed factories.

## Subtasks
- **T034** Contract: path file, `openapi.yaml` entry (recorded out-of-map edit; WP05 owns it), and the links on the
  step and action-context schemas.
- **T035** Service: resolve the step, delegate to the WP05 path, add the links. Rebase any test fixtures that build
  `Relationship` onto F's factories.
- **T036** `step-profile-selection.feature` (`@contract`): `software-dev` `review` → the reviewer selection;
  `implement` → ivan; a role-less step → the fallback; an unknown step → 404.

## Definition of Done
- `./gradlew verifyAll` is green on the branch rebased onto F.
- Then the mission is ready for acceptance, and PR #573 can leave draft once the operator agrees.

## Branch Strategy

Planning base and final merge target: `slice-g-538-profile-resolution` (mission branch
`kitty/mission-slice-g-profile-resolution-01M7DFBF`). The execution workspace is allocated per computed lane from
`lanes.json`; do not pick a base branch by hand. Sign every commit. Add no Co-Authored-By trailer and no model names.

```bash
spec-kitty agent action implement WP08 --agent claude --mission slice-g-profile-resolution-01M7DFBF
```

## Read first
`spec.md`, `plan.md`, `research.md` §6a (rulings) and `research/squad-post-plan.md`, all in
`kitty-specs/slice-g-profile-resolution-01M7DFBF/`. Also `.kittify/charter/charter.md` ("Designing during the
rewrite": canonical shape only).
