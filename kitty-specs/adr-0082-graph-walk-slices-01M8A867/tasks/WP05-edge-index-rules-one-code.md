---
work_package_id: WP05
title: 'Slice 4b: edge-index rules, one code per fault (#639, #667)'
dependencies:
- WP04
requirement_refs:
- FR-009
- FR-010
- FR-011
- SC-002
- SC-003
planning_base_branch: feat/adr-0082-graph-walk-slices
merge_target_branch: feat/adr-0082-graph-walk-slices
branch_strategy: Planning artifacts for this mission were generated on feat/adr-0082-graph-walk-slices. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/adr-0082-graph-walk-slices unless the human explicitly redirects the landing branch.
subtasks:
- T020
- T021
- T022
- T023
phase: Phase 3 - Slice 4
history:
- at: '2026-11-25T22:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/EdgeIndex.java
create_intent:
- contracts/charter/changes/2026-11-26-edge-index-refusal-codes.md
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EdgeIndex.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/EdgeIndexTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewTest.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/RefusalHandler.java
- service/web-transport/src/test/java/ai/speckitty/charter/web/RefusalHandlerTest.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalCode.java
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/responses/OfferingUnreadable.yaml
- contracts/charter/scenarios/step-enhancement-compose.feature
- contracts/charter/scenarios/mission-types.feature
- contracts/charter/scenarios/registration/closure-faults.feature
- contracts/charter/scenarios/README.md
- contracts/charter/changes/2026-11-26-edge-index-refusal-codes.md
- docs/development/http-status-codes.md
- docs/architecture/offering-and-activation.md
- docs/conformance/ruleset-scenario-trace.md
tags: []
tracker_refs: []
---

# Work Package Prompt: WP05 - Slice 4b: edge-index rules, one code per fault (#639, #667)

## Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else.

## Objective

The edge-index refusals become relations-phase rules, and a read and a registration of the same closure answer each
fault with one code (operator ruling D2). `ClosureImpactCheck#relationFaults` and the hand-written gates are deleted.
This is a wire change.

Operator answers (2026-11-25): Q-A the ADR-0056 status model; Q-B C21 (`LayerMerge`'s forbidden layer override)
is in scope and answers `override_not_permitted` on both surfaces too; Q-C the stray link answers
`pack_reference_artifact_missing` with the violation `mission_step_unlinked`. Start from `main` after the #659
runner's `ClosureView` and `PackCompositionFacts` constructor deletions land (or rebase onto them); keep them.

Requirements: FR-009, FR-010, FR-011, NFR-001. Research: `research.md` R-5. Closes #667.

## Subtasks

### T020 Edge-index rules

- The re-pointed self-override (`EdgeIndex.refuseSelfOverrides`), the stray step link
  (`refuseStrayMissionTypeEdges`), the locked step enhanced (`ClosureView.MergeInputs.merged`) and C21's forbidden
  layer override (`LayerMerge`) become checks of
  a second relations phase that runs only when the relation rules pass (the relations gate, today's order).
- The qualified-endpoint faults (`EdgeIndex.of`) are unreachable after a compose: prove it with a test, then fold them
  into `references()` instead of moving them.
- `EdgeIndex` stays as the merge's index; whether it moves into the domain is decided here (squad S4): record it.

### T021 One code per fault

- Self-override answers `self_override`, judged on the re-pointed index on both surfaces; locked step answers
  `override_not_permitted`, every fault on both surfaces; the stray link answers `pack_reference_artifact_missing` with
  the violation `mission_step_unlinked`.
- Statuses per Q-A (recommended ADR-0056): 500 on reads, 422 on the 13 impact-checked operations through `x-refusal`
  operation overrides; `RefusalHandler` moves the two codes to the 500 default and adds them to the impact-checked
  set.
- Red first: a read and a registration of each faulty closure; `ClosureImpactCheckTest`'s layer-above override flips
  to a refusal (out-of-map, WP03 owns the test: recorded); #667's registration scenario answers 422, not 500.

### T022 Delete relationFaults and the gates

- `ClosureImpactCheck` (owned by WP03; out-of-map, recorded) runs one walker over relations, edge-index and own-closure
  phases per closure; delete `relationFaults` and the gate code. Census " (packs ...)" and write-path prefixes stay.
- Red first: a mixed fault (relation plus edge-index) reports only the relation fault, on both the census and the
  write path.

### T023 Contract and docs

- `CharterRefusalCode.yaml` (descriptions; `x-refusal` for the two codes), `OfferingUnreadable.yaml`, scenarios
  (`step-enhancement-compose.feature`, `mission-types.feature`, new registration scenarios), `scenarios/README.md`.
- Fragment `contracts/charter/changes/2026-11-26-edge-index-refusal-codes.md` under `### Breaking` (ADR-0022 D2), with
  what a client sees change.
- Docs: `http-status-codes.md`, `composition-and-activation-rules.md` (C20, C36; out-of-map, owned by WP01),
  `offering-and-activation.md`, `ruleset-scenario-trace.md` (A12).
- Consumer note: open-packs is unaffected (it reads only `/offering`); doc-kitty flagged to the dispatcher.

## Definition of Done

- Each edge-index fault answers one code on reads and registration; the contract tests, the scenarios and
  `ReadConsistencySteps` agree; `verifyAll`; #667 closed by the PR.
- NFR-003: `PackRegistrationScaleTest` times on `main` and on the branch, reported in the PR (report-only, ADR-0057).

## Risks

- Registration starts refusing two shapes it accepts today: say so in the fragment and the PR.
- `ReadConsistencySteps` checks every observed status against `x-refusal`: update both together.

Implementation command: `spec-kitty agent action implement WP05 --agent claude --mission adr-0082-graph-walk-slices-01M8A867`
