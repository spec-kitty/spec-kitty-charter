---
work_package_id: WP05
title: 'Selection read: contract, activation wiring and scenarios'
dependencies:
- WP03
- WP04
requirement_refs:
- FR-001
- FR-002
- FR-005
- NFR-003
- SC-001
- SC-002
planning_base_branch: slice-g-538-profile-resolution
merge_target_branch: slice-g-538-profile-resolution
branch_strategy: Planning artifacts were generated on the mission branch kitty/mission-slice-g-profile-resolution-01M7DFBF (target slice-g-538-profile-resolution). Completed changes merge back into slice-g-538-profile-resolution; the slice lands as one PR (#573), held until slice F lands.
subtasks:
- T022
- T023
- T024
- T025
- T026
phase: Phase 3 - Reads
agent: claude
history:
- at: '2026-11-15T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/paths/
create_intent:
- contracts/charter/paths/activations_activationId_roles_role_profile-selection.yaml
- contracts/charter/schemas/ProfileSelectionRead.yaml
- contracts/charter/schemas/ProfileSelectionCandidate.yaml
- contracts/charter/scenarios/profile-selection.feature
- contracts/charter/changes/2026-11-15-profile-selection.md
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ProfileCandidates.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ProfileCandidatesTest.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/ProfileSelectionController.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/ProfileSelectionDtos.java
- service/profiles-edge/src/test/java/ai/speckitty/charter/profiles/infra/api/ProfileSelectionDtosTest.java
- service/profiles/src/main/java/ai/speckitty/charter/profiles/application/ProfileSelectionService.java
- service/packs/src/testFixtures/resources/example-packs/org-base/agent_profiles/reviewer-rita.agent.yaml
execution_mode: code_change
owned_files:
- contracts/charter/openapi.yaml
- contracts/charter/paths/activations_activationId_roles_role_profile-selection.yaml
- contracts/charter/schemas/ProfileSelectionRead.yaml
- contracts/charter/schemas/ProfileSelectionCandidate.yaml
- contracts/charter/examples/ProfileSelectionRead.*.yaml
- contracts/charter/scenarios/profile-selection.feature
- contracts/charter/scenarios/errors.feature
- contracts/charter/changes/2026-11-15-profile-selection.md
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ProfileCandidates.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveTag.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ProfileCandidatesTest.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/ProfileSelectionController.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/ProfileSelectionDtos.java
- service/profiles-edge/src/test/java/ai/speckitty/charter/profiles/infra/api/ProfileSelectionDtosTest.java
- service/profiles/src/main/java/ai/speckitty/charter/profiles/application/ProfileSelectionService.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ProfileSteps.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java
- service/packs/src/testFixtures/resources/example-packs/org-base/agent_profiles/reviewer-rita.agent.yaml
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP05 — Selection read: contract, activation wiring and scenarios

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Serve `selectAgentProfile`, the read the usability run found missing (ruling D1, plan P4). It runs WP03's domain over
the profiles in force under an activation, with WP02's pin and fallback in force.

## Contract (P4)
```
GET /activations/{activationId}/roles/{role}/profile-selection?language=&framework=
  operationId: selectAgentProfile, tag EffectiveAgentProfiles
  role: path, pattern ^(?!generic$)[a-z][a-z0-9-]{0,63}$
200 ProfileSelectionRead { role, context: {language?, framework?},
                           candidates: [{ id, pack, fit: primary|alt|fallback, _links.effective }],
                           tie: boolean, _links.self }
304 If-None-Match
400 shared problem (pattern)
404 activation_not_found | no_profile_matched
422 fallback_profile_conflict | role_pin_conflict
```
- Refusal order: activation, then the conflicts (fallback, pin), then matching.
- `stale_input` and the double-digest ETag follow the existing effective reads.

## Subtasks

### T022 Contract
- Add the path file, the schemas, the `openapi.yaml` entry and two examples (a match and a tie).
- Flip `no_profile_matched` and `fallback_profile_conflict` to `raised: true`. Their default statuses are already
  404 and 422, so no override is needed. Name them in main code.
- `role_pin_conflict` (WP02) also applies to this operation: add it to the code's operations list.
- Add pin scenarios for the raised codes, as at `errors.feature:138-147`, and add them to the list at :247-310.
- Write the Added fragment `contracts/charter/changes/2026-11-15-profile-selection.md` (one `### Added` block, see
  the changes README), naming the operation, the codes now raised, the new codes and `role_pins`.

### T023 Activation wiring
- `ProfileCandidates` in `service/activations`: under an opened activation session, list the in-force agent
  profiles. Exclude enhancers. For each, give the pack, closure position and `depth` (WP04's chain). Add the pin and
  fallback facts from `ClosureRoleFacts` (WP02).
- `EffectiveReadService.profileSelection(...)` follows the order of `actionContext`/`prompt` (:486-560): found, open,
  then the conflicts, then select, then the tag, then 304.
- `EffectiveTag.SelectionQuery`: a sibling of `ContextQuery` and `PromptQuery` (:124, :149), hashing the role, the
  context, the in-force profile set with their bodies, the policies and the descriptors.
- Overlap: #106 also edits `EffectiveReadService` and `EffectiveTag`. Keep the additions in their own methods.

### T024 Edge
- `ProfileSelectionService` (profiles application) adapts candidates to `ProfileSelection`.
  `ProfileSelectionController` and `ProfileSelectionDtos` map the answer. Follow
  `EffectiveAgentProfilesController` for the 304 and ETag handling.

### T025 Example
- New `org-base/agent_profiles/reviewer-rita.agent.yaml`: `role: reviewer`, no `specializes_from`, no
  `routing-priority` (50, equal to renata), giving the org-base tie. Restamp the org-base manifest (recorded
  out-of-map edit).

### T026 Scenarios
`profile-selection.feature`, tagged `@contract`, one scenario per row of the plan's example table:

| Context | Ask | Answer |
| --- | --- | --- |
| built-in | `implementer` | `implementer-ivan`, primary |
| built-in | `implementer`, `language=java` | `java-jenny` |
| built-in | `implementer`, `language=kotlin` | `implementer-ivan` |
| built-in | `facilitator` | `generic-agent`, fallback |
| built-in | `operator` | `404 no_profile_matched` |
| org-base | `reviewer` | the renata and rita tie |
| org-base | `operator` | `human-operator` |
| project | `reviewer` | renata, by the pin |
| project | `implementer` | ivan, with `project-reviewer`'s alt fit losing |

Also: the conflict refusals using registered test packs, `304`, and `400` for `generic`. Bind steps in
`ProfileSteps.java` with the vocabulary of `contracts/charter/scenarios/README.md`.

## Definition of Done
- `ContractConformanceTest`, the contract scenarios and `ReservedRefusalCodesTest` are green.
  `./gradlew verifyAll` is green.
- SC-001: selecting `reviewer` answers a profile or a marked tie without a client walk.

## Branch Strategy

Planning base and final merge target: `slice-g-538-profile-resolution` (mission branch
`kitty/mission-slice-g-profile-resolution-01M7DFBF`). The execution workspace is allocated per computed lane from
`lanes.json`; do not pick a base branch by hand. Sign every commit. Add no Co-Authored-By trailer and no model names.

```bash
spec-kitty agent action implement WP05 --agent claude --mission slice-g-profile-resolution-01M7DFBF
```

## Read first
`spec.md`, `plan.md`, `research.md` §6a (rulings) and `research/squad-post-plan.md`, all in
`kitty-specs/slice-g-profile-resolution-01M7DFBF/`. Also `.kittify/charter/charter.md` ("Designing during the
rewrite": canonical shape only).
