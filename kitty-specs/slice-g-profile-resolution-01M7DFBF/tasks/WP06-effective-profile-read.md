---
work_package_id: WP06
title: 'Effective profile read (R8) and the #380 scenario'
dependencies:
- WP05
requirement_refs:
- FR-009
- FR-010
- SC-003
- SC-004
planning_base_branch: slice-g-538-profile-resolution
merge_target_branch: slice-g-538-profile-resolution
branch_strategy: Planning artifacts were generated on the mission branch kitty/mission-slice-g-profile-resolution-01M7DFBF (target slice-g-538-profile-resolution). Completed changes merge back into slice-g-538-profile-resolution; the slice lands as one PR (#573), held until slice F lands.
subtasks:
- T027
- T028
- T029
- T030
phase: Phase 3 - Reads
agent: claude
history:
- at: '2026-11-15T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/schemas/EffectiveAgentProfileRead.yaml
create_intent:
- contracts/charter/schemas/LineageLink.yaml
- contracts/charter/changes/2026-11-15-performer-and-effective-profile.md
execution_mode: code_change
owned_files:
- contracts/charter/schemas/EffectiveAgentProfileRead.yaml
- contracts/charter/schemas/LineageLink.yaml
- contracts/charter/paths/activations_activationId_artifacts_agent_profile_artifactId.yaml
- contracts/charter/scenarios/profiles.feature
- contracts/charter/scenarios/effective-agent-profile.feature
- contracts/charter/changes/2026-11-15-performer-and-effective-profile.md
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/EffectiveProfileDtos.java
- service/profiles-edge/src/test/java/ai/speckitty/charter/profiles/infra/api/EffectiveProfileDtosTest.java
- service/profiles/src/main/java/ai/speckitty/charter/profiles/application/ProfileReadService.java
- service/profiles/src/main/java/ai/speckitty/charter/profiles/application/EffectiveProfiles.java
- service/profiles/src/main/java/ai/speckitty/charter/profiles/infra/activations/ActivationsEffectiveProfiles.java
- tests/scenarios/charter/PROMISES.md
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP06 — Effective profile read (R8) and the #380 scenario

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

`getEffectiveAgentProfile` answers the **effective** profile (R8, #102; rulings D5, D6, D7):
- each profile on the `specializes_from` chain is merged with its own in-force enhancers;
- the chain is folded root to leaf by per-field replacement;
- `lineage` is answered;
- a profile stored before the rule that would drop a field answers `500 profile_enhancement_drops_field`.

The pack read (`getPackAgentProfile`) keeps answering the stored body.

## Subtasks

### T027 Contract
- `EffectiveAgentProfileRead.yaml`:
  - the body is the effective profile;
  - add `lineage: [LineageLink {id, pack, depth}]`, nearest first, `[]` for a root;
  - add an `enhances` link when the profile is itself an enhancer, whose effective read answers its own body.
- Reword the path description (it says "enhances edges are not resolved ... not the effective profile of #380") and
  the `_links.artifact` text ("the same body").
- Add `profile_enhancement_drops_field` (500) to this operation.
- Write the Breaking fragment `2026-11-15-performer-and-effective-profile.md` (one `### Breaking` block): `sentinel`
  replaced by `performer`, and the effective read's body is now merged and inherited.

### T028 Wiring
- `ActivationsEffectiveProfiles`, `ProfileReadService` and `EffectiveProfiles`: build the chain (WP04 T017), merge the
  enhancers per profile (T018), then fold (T019). Map `ProfileEnhancementDropsField` to the 500 refusal, with the
  enhancing pack first in `packs`.
- The ETag covers the enhancers and ancestors; reuse the existing artifact tag's inputs, extended.

### T029 Scenarios
- `profiles.feature`: remove `@WIP`, add `@contract`, and remove the header's "decided, not built" block. Bind the
  steps. The approved scenario stays as written (D5): an enhancer whose `specialization` leaves out the base's is
  refused, naming the pack and `specialization`. Use a registered test pack, stored before the rule, so the read
  path is exercised.
- Add a positive control: the org-base `reviewer-renata-org` enhancement is merged, and `java-jenny` has
  `lineage [{implementer-ivan, built-in, 1}]` with an inherited field.
- Update `effective-agent-profile.feature` where the body changed.

### T030 Promises
- `tests/scenarios/charter/PROMISES.md:24`: `PR-CHARTER-REFUSAL-001` is no longer a gap; point it to
  `profiles.feature`.

## Definition of Done
- SC-003: no `sentinel` outside history.
- SC-004: no `@WIP` in `profiles.feature`.
- `./gradlew verifyAll` is green.

## Branch Strategy

Planning base and final merge target: `slice-g-538-profile-resolution` (mission branch
`kitty/mission-slice-g-profile-resolution-01M7DFBF`). The execution workspace is allocated per computed lane from
`lanes.json`; do not pick a base branch by hand. Sign every commit. Add no Co-Authored-By trailer and no model names.

```bash
spec-kitty agent action implement WP06 --agent claude --mission slice-g-profile-resolution-01M7DFBF
```

## Read first
`spec.md`, `plan.md`, `research.md` §6a (rulings) and `research/squad-post-plan.md`, all in
`kitty-specs/slice-g-profile-resolution-01M7DFBF/`. Also `.kittify/charter/charter.md` ("Designing during the
rewrite": canonical shape only).
