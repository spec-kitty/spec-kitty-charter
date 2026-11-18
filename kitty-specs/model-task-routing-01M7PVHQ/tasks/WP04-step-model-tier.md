---
work_package_id: WP04
title: The step's model tier
dependencies:
- WP02
requirement_refs:
- FR-010
planning_base_branch: feat/108-model-task-routing
merge_target_branch: feat/108-model-task-routing
branch_strategy: Planning artifacts for this mission were generated on feat/108-model-task-routing. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/108-model-task-routing unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-model-task-routing-01M7PVHQ
base_commit: 3b150eb371dcfee3b14df28286f0b12c64e7e7f9
created_at: '2026-11-18T12:13:18.368156+00:00'
subtasks:
- T013
- T014
- T015
phase: Phase 1 - Foundations
history:
- at: '2026-11-18T08:55:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/missions/
create_intent:
- contracts/charter/schemas/CostTier.yaml
execution_mode: code_change
owned_files:
- contracts/packs/schemas/mission-type.schema.json
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/MissionTypeDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/MissionTypeBinding.java
- service/missions/src/main/java/ai/speckitty/charter/missions/domain/MissionStep.java
- service/missions/src/test/java/ai/speckitty/charter/missions/domain/**
- contracts/charter/schemas/MissionStep.yaml
- contracts/charter/schemas/MissionStepSummary.yaml
- contracts/charter/schemas/CostTier.yaml
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/application/StepView.java
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/infra/api/MissionBodies.java
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/infra/api/MissionJson.java
- service/missions-edge/src/test/java/**
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapper.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP04 – The step's model tier

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according to
its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`). Address every feedback item, and record
each change with `spec-kitty agent tasks add-history WP04 --note "..."`.

## Branch Strategy

- **Planning base branch**: `feat/108-model-task-routing`
- **Merge target branch**: `feat/108-model-task-routing`
- The execution worktree is allocated per computed lane from `lanes.json`; never pick a base branch by hand.

```bash
spec-kitty agent action implement WP04 --agent claude --mission model-task-routing-01M7PVHQ
```

## Before you start (every WP)

1. **#355 must be merged.** No code starts before #355 (PR #599) has merged and this branch is rebased onto that main
   (C-001). If it has not merged, stop and report.
2. **Recheck every path below on the merged main.** This prompt was written against main `8284bd36` with #599 open. #599
   rewrites `PackContents`, `PackRulesets`, `PackAssembly`, `StoreRead`, `StorageRulesets` and
   `ReferenceFacts`/`ReferenceClosure`, and deletes `MissionTemplates` and `GuardClosure` (research R1). Follow the
   merged code, not this text, where they differ, and record the difference in the WP history.
3. **Read the authority:**
   - `docs/adr/0080-the-routing-catalog-kind-and-the-model-recommendation.md` (accepted);
   - `kitty-specs/model-task-routing-01M7PVHQ/{plan.md,data-model.md,research.md,contracts/operations.md}`;
   - the charter companion `.kittify/charter/charter.md`.

   Core's `/opt/spec-kitty/src/charter/offering/model_task_routing/` is evidence only (ADR-0015).
4. **Tests use the canonical shape only**, except tests of invalid content (charter companion; ADR-0070). Never exclude
   a field from a parity or round-trip check to make a test pass.
5. **Commits:** signed (`git commit -S`), ending with the `Claude-Session:` trailer, with no model names and no
   `Co-Authored-By`.

## Objective

A mission step may declare an optional `model_tier`, one of the kernel's `CostTier` values (WP02). It is stored,
round-tripped, and served as `modelTier` on the step reads, absent when undeclared. This is ADR-0080 decision 12, and
plan IC-03 (squad findings A4, A6).

## Context

- Today a step has `role` and no tier: `contracts/packs/schemas/mission-type.schema.json` `$defs.Step` (lines 5–81),
  `MissionTypeDso.Step` (around line 100), and `missions/domain/MissionStep.java` (`Optional<String> role` at line 34).
- Core's `recommended_model_tier` is a free string. Charter's field is `model_tier`, and it is a cost tier; that
  difference is DIV-704.
- `missions.domain` may name the kernel but never `routing.domain` (`SLICES_DO_NOT_CROSS`). That is why the tier is the
  kernel `CostTier`.

## Subtasks

### T013 – Pack schema, DSO and domain

- **Schema.** In `$defs.Step` of `mission-type.schema.json`, add `"model_tier": {"title": "Model tier", "description":
  "The cost tier of model this step advises (ADR-0080 decision 12); a recommendation the client may override.",
  "enum": ["low","medium","high","premium"]}`. It is optional.
- **DSO.** Add `@Nullable String model_tier` (or the DSO's naming convention) to `MissionTypeDso.Step`, and map it in
  `MissionTypeBinding`.
- **Domain.** Add `Optional<CostTier> modelTier` to `MissionStep`. An unknown token cannot arrive, because the schema
  refuses it at load, but map defensively with `CostTier.ofToken` and fail loudly if it is empty.
- **Round trip.** Writing the step back must emit `model_tier` only when it is present. The whole-pack round trip (WP06)
  proves this on an example step. Add a binding unit test here too.

### T014 – Contract

- Create `contracts/charter/schemas/CostTier.yaml`: a string enum `low`, `medium`, `high`, `premium`. Its description
  names the order, and it has an `x-source` pointing at `service/kernel/.../CostTier.java`. Register it in
  `schemas/_index.yaml`. If WP08 owns `_index.yaml` by then, make this a recorded edit outside the map. WP08 depends on
  this WP, so there is no conflict.
- Add `modelTier` to `schemas/MissionStep.yaml`: `$ref: CostTier.yaml`, optional (absent when the step declares none,
  as `role` is), with `x-source` pointing at `MissionStep.modelTier`. Check `MissionStepSummary.yaml`: if it lists the
  step's fields, add it there too; if it does not, record that.

### T015 – Step reads

- Map the tier in `StepView` and `MissionBodies` (the pack step read), and in `EffectiveReadMapper` (the activation
  step read).
- Absent means omitted: extend the `MissionJson` mix-in, and its `RuntimeHints` if the file registers hints, so the
  native image writes no `null`. Prove it with the existing null-omission test pattern
  (`service/deployable/src/test/java/.../NullOmissionTest.java`); add a row there as a recorded edit if it enumerates
  DTOs.
- Unit tests: a step with a tier answers `modelTier`, and a step without one omits the key.
- The contract scenario that asserts `modelTier` on a step read is written in WP06, once an example step carries a
  tier, as a recorded edit in the step-read feature.

## Definition of Done

- A step declares and serves `model_tier`/`modelTier`, which is absent when undeclared, in both step reads.
- `./gradlew verifyAll` passes on Java 25 for the modules this WP touches, with no new style-gate finding.
- Every subtask's status is recorded with `spec-kitty agent tasks mark-status`.
- Nothing outside `owned_files` changed, except a recorded edit outside the map, named in the WP history with its
  reason.

## Reviewer Guidance

- Check that absent stays absent on the wire (no `null`), and that the schema's enum equals `CostTier`.
- **Independent reviewer.** Review is by a separate reviewer session, never by the implementer.
