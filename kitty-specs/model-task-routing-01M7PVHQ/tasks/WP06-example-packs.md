---
work_package_id: WP06
title: Example packs
dependencies:
- WP04
- WP05
requirement_refs:
- SC-003
- FR-005
- C-002
planning_base_branch: feat/108-model-task-routing
merge_target_branch: feat/108-model-task-routing
branch_strategy: Planning artifacts for this mission were generated on feat/108-model-task-routing. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/108-model-task-routing unless the human explicitly redirects the landing branch.
subtasks:
- T020
- T021
- T022
phase: Phase 2 - The kind
history:
- at: '2026-11-18T08:55:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/testFixtures/resources/example-packs/
create_intent:
- service/packs/src/testFixtures/resources/example-packs/built-in/routing_catalogs/default.routing-catalog.yaml
- service/packs/src/testFixtures/resources/example-packs/org-base/routing_catalogs/default.routing-catalog.yaml
- service/packs/src/testFixtures/resources/example-packs/org-base/routing_catalogs/cost-led.routing-catalog.yaml
execution_mode: code_change
owned_files:
- service/packs/src/testFixtures/resources/example-packs/**
- service/packs/src/testFixtures/resources/whole-pack/**
- service/packs/src/test/java/ai/speckitty/charter/packs/examples/ExamplePackCoverageTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP06 – Example packs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according to
its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`). Address every feedback item, and record
each change with `spec-kitty agent tasks add-history WP06 --note "..."`.

## Branch Strategy

- **Planning base branch**: `feat/108-model-task-routing`
- **Merge target branch**: `feat/108-model-task-routing`
- The execution worktree is allocated per computed lane from `lanes.json`; never pick a base branch by hand.

```bash
spec-kitty agent action implement WP06 --agent claude --mission model-task-routing-01M7PVHQ
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

Charter's example packs carry the research R7 fixture matrix exactly. The whole-pack round trip passes over them with
no field excluded (SC-003; ADR-0070; plan IC-01; squad findings D1, D8, D12, A8).

## Subtasks

### T020 – The R7 catalogs

- **Write the three catalogs** exactly as `research.md` R7 "Catalogs" states them. Use models `model-a` … `model-e` and
  no vendor names. Each needs `generated_at`, at least one `sources` entry (for example
  `{name: example, url: https://example.invalid/catalog, access_method: manual, snapshot_at: …}`) and a `title` per
  task type.
  - `built-in/routing_catalogs/default.routing-catalog.yaml`: `quality_first`; weights quality 1, cost 0.2, risk 0,
    latency 0; task type `design`; `model-a` (fit 0.3, `premium`) and `model-b` (fit 0.1, `low`).
  - `org-base/routing_catalogs/default.routing-catalog.yaml` (it replaces built-in's by the same id): `balanced`; the
    same weights.
    - Task types: `design` (as built-in), `review` (with `tier_constraints: [{task_type: review, max_tier: medium}]`)
      and `triage` (declared, with no fit).
    - Models: `model-b` **listed before** `model-a` (the order-independence fixture, D12); `model-c` (review fit 0.9,
      `premium`), `model-d` (review fit 0.6, `medium`) and `model-e` (review fit 0.5, `low`).
  - `org-base/routing_catalogs/cost-led.routing-catalog.yaml`: `cost_first`; weights quality 0.5, cost 0.5, risk 0,
    latency 0; `design` with `model-a` (fit 0.3, `premium`) and `model-b` (fit 0.1, `low`).
- **Check the arithmetic in a comment** at the top of each file. Org `review`: `model-d` = 0.6 + 0.2 × 0.66 = 0.732,
  and `model-e` = 0.5 + 0.2 = 0.7. Org `design` ties at 0.3 under `balanced`.

### T021 – Profile model, step tier, presets and policy

- **Profile with a model.** One built-in agent profile, not the fallback and not locked, declares `model: model-x` and
  `effort: high`. `generic-agent` keeps no `model`. Check that no existing scenario asserts that profile's full body;
  if one does, update its expectation as a recorded edit outside the map.
- **Step with a tier.** One built-in mission step declares `model_tier: low` (WP04's field).
- **Presets.**
  - The org-base preset that the project closure applies selects `default` (`activated_routing_catalogs: [default]`).
  - `cost-led` is in no preset, so selecting it in a scenario gives the two-catalog conflict (R7 rows 3 and 8).
  - Built-in's preset selects its `default`, so an activation over built-in alone has its `quality_first` catalog in
    force (R7 row 1).
- **Policy.** Add `routing_catalog: open` to `built-in/policy.yaml` `kind_defaults`, so org-base's same-id override is
  allowed. A locked variant for the policy scenario belongs to WP08's scenario fixture, not to the example packs.

### T022 – Manifests, ripple and the round trip

- **Manifests.** Regenerate `pack-manifest.yaml` of every touched pack (content hashes and `manifest_hash`), with the
  repo's generator. Find it with `grep -rn manifest_hash scripts service/packs/src/test`; do not edit hashes by hand.
- **Ripple.** Run `./gradlew verifyAll`. Scenarios that assert exact counts, or every item of an activation, preset or
  skipped report, will change. Update each expectation as a recorded edit outside the map, naming the scenario, and
  confirm that each change is caused by the new artifacts and not by a regression.
- **Coverage.** Extend `ExamplePackCoverageTest` so the catalog kind, a profile `model`, a step `model_tier` and a
  catalog at two levels are required content (ADR-0070 decision 2).
- **Round trip.** `:pack-validation:wholePackRoundTrip` passes. `WholePackExpectation.emptyKindProblems` now demands a
  catalog file, which this WP provides. Update a whole-pack fixture (`exclusions`, `expected-red`, `known-failures`)
  only if the gate's own rule asks for it, never to exclude a field.
- **Step read.** Add the contract scenario that a step read answers `modelTier: low` for the example step, in the
  step-read feature, as a recorded edit outside the map (WP04 deferred it here).

## Definition of Done

- The example packs hold the R7 matrix exactly, and the round trip and coverage pass.
- `./gradlew verifyAll` passes on Java 25 for the modules this WP touches, with no new style-gate finding.
- Every subtask's status is recorded with `spec-kitty agent tasks mark-status`.
- Nothing outside `owned_files` changed, except a recorded edit outside the map, named in the WP history with its
  reason.

## Reviewer Guidance

- Recompute every score in the R7 table from the files. Check that `model-b` precedes `model-a` in org-base's `default`.
- Check that no expectation was changed except as a consequence of the new content.
- **Independent reviewer.** Review is by a separate reviewer session, never by the implementer.
