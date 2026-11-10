# Tasks: Mission type, step, step contract and skeleton reads

**Mission:** `mission-type-reads-01M70ZN5` | **Plan:** [plan.md](plan.md) | **Spec:** [spec.md](spec.md)

Subtask completion is recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`, never by editing this file.

## Work packages

| WP | Title | Depends |
|---|---|---|
| WP01 | Missions edge module, wiring and ArchUnit rules | — |
| WP02 | Contract: schemas, operations, refusals, relations, fragments; red scenarios | — |
| WP03 | LISTED kinds, mission_step_contract item, list and references | WP02 |
| WP04 | Composite read and pack-scoped mission type, step and skeleton reads | WP01, WP02 |
| WP05 | Activation-scoped mission type, step and skeleton reads | WP04 |
| WP06 | Step links on action graph nodes and the action context (#450 part) | WP05 |
| WP07 | DELETE for both kinds with the mission-type cascade, docs and usability run | WP03, WP04 |

## Requirements met without a task

- **FR-011**: the governance walks of these kinds reuse the existing walker rules; nothing is built, and WP05's
  scenarios exercise the action context unchanged.
- **NFR-004**: the gate (`verifyAll`, `:packs:wholePackRoundTrip`, the docs checks) runs at the pre-PR step and in CI,
  never in-mission (NO_FULL_HEAVY_SUITES_IN_MISSION).

## MVP

WP02 → WP03 → WP04: the pack-scoped reads. WP05 and WP06 follow the profiles PR.

## Subtask Index

| ID | Subtask | WP |
|---|---|---|
| T001 | Add `:missions-edge` (settings, build file, deployable dependency, charter-reports `servic | WP01 |
| T002 | ArchUnit: the profiles mission's edge-ring rule shapes applied to missions, plus MISSIONS_ | WP01 |
| T003 | Known-bad fixtures under service/deployable/src/test/.../missions/*/badfixture that each n | WP01 |
| T004 | Schemas MissionType, MissionStep, MissionStepContract, MissionSkeleton with x-source/x-der | WP02 |
| T005 | Paths and the MissionTypes tag: the three pack operations and the three activation operati | WP02 |
| T006 | Refusal codes mission_step_contract_not_found and skeleton_not_found (minimal hunk, rebase | WP02 |
| T007 | Link relations mission-type, step-contract, skeleton in hypermedia-hateoas.md and in link- | WP02 |
| T008 | Change fragments: ### Breaking and ### Added | WP02 |
| T009 | Red scenarios contracts/charter/scenarios/mission-types.feature: mission type, sequence an | WP02 |
| T010 | ArtifactReadService: LISTED (the five, mission_type, mission_step_contract) vs the generic | WP03 |
| T011 | ArtifactDtos maps the step contract with links: mission-type, artifact for each delegation | WP03 |
| T012 | Scenarios for the step contract item, ?kind= lists and /references turn green | WP03 |
| T013 | packs.application MissionTypeHoldingRead.holding(pack, typeId): one verified read of the t | WP04 |
| T014 | missions.application MissionTypeReadService over the PackMissionTypes port | WP04 |
| T015 | missions.infra.packs adapter narrowing CharterArtifact to MissionType | WP04 |
| T016 | Scenarios for the pack-scoped reads turn green | WP04 |
| T017 | activations.application EffectiveReadService.missionTypeHolding(activationId, typeId) on t | WP05 |
| T018 | missions.infra.activations adapter for ActivationMissionTypes | WP05 |
| T019 | Scenarios: activation reads, unreached mission type is mission_type_not_found, unknown act | WP05 |
| T020 | EffectiveReadMapper: action:<type>/<step> graph nodes and the action context's action carr | WP06 |
| T021 | Scenarios: the graph and action context of this repository's charter link software-dev's s | WP06 |
| T022 | ArtifactRemovalService and PackRemovalStore.Removal: a mission type's removal includes its | WP07 |
| T023 | Scenarios: remove a step contract | WP07 |
| T024 | Docs: docs/api/service-contract.md rows for #99/#100/#96, the skeleton naming rule (D9), t | WP07 |
| T025 | Extend the shared usability script to follow /api/v1/ → software-dev → 13 steps → 5 contra | WP07 |

## WP01 · Missions edge module, wiring and ArchUnit rules

**Depends on:** none

## WP02 · Contract: schemas, operations, refusals, relations, fragments; red scenarios

**Depends on:** none

## WP03 · LISTED kinds, mission_step_contract item, list and references

**Depends on:** WP02

## WP04 · Composite read and pack-scoped mission type, step and skeleton reads

**Depends on:** WP01, WP02

## WP05 · Activation-scoped mission type, step and skeleton reads

**Depends on:** WP04

## WP06 · Step links on action graph nodes and the action context (#450 part)

**Depends on:** WP05

## WP07 · DELETE for both kinds with the mission-type cascade, docs and usability run

**Depends on:** WP03, WP04
