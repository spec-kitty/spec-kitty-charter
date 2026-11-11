---
work_package_id: WP03
title: Mission type, step and skeleton pack reads carry the revision
dependencies:
- WP01
requirement_refs:
- FR-003
- FR-004
- C-002
planning_base_branch: claude/feat-487-488-pack-item-etags
merge_target_branch: claude/feat-487-488-pack-item-etags
branch_strategy: Planning artifacts for this mission were generated on claude/feat-487-488-pack-item-etags. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/feat-487-488-pack-item-etags unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-item-etags-and-resolver-retirement-01M75WRS
base_commit: 41d21a9a5a6d7649718517254f102f254165756d
created_at: '2026-11-11T18:42:53.062411+00:00'
subtasks:
- T009
- T010
- T011
- T012
history:
- at: '2026-11-11T18:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/missions-edge/
create_intent: []
execution_mode: code_change
owned_files:
- service/missions-edge/src/main/java/ai/speckitty/charter/missions/**
- service/missions-edge/src/test/java/ai/speckitty/charter/missions/**
- service/packs/src/main/java/ai/speckitty/charter/packs/application/MissionTypeHoldingRead.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/MissionTypeHoldingReadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionTypeHoldingCorpusTest.java
role: implementer
tags: []
tracker_refs: []
---

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill (or `spec-kitty agent profile show java-jenny`) and run `spec-kitty charter context --action implement --json` before reading anything else. Read `.kittify/charter/charter.md` and AGENTS.md: the charter's model is canonical, tests use only the canonical shape, test first.

## Branch Strategy

Planning branch and final merge target: `claude/feat-487-488-pack-item-etags`. Execution worktrees (or the write checkout, for a single-branch Mission) come from `lanes.json` via `spec-kitty agent action implement WP03 --agent claude --mission pack-item-etags-and-resolver-retirement-01M75WRS`. Never pick a base branch by hand.

## Build

Java 25: `export JAVA_HOME=/usr/lib/jvm/temurin-25`. Targeted runs only (`./gradlew :<module>:test --tests '<Class>'`, `:contracts:build`, `spotlessApply`). The contract scenarios run with `./gradlew :deployable:test --tests '*CharterContractSuite'`. Leave `verifyAll` to CI.

## Objective

`getPackMissionType`, `getPackMissionStep` and `getPackMissionSkeleton` answer the pack revision as `ETag` (FR-003,
FR-004), read inside the same verified read as the holding. Activation reads (`EffectiveMissionTypesController`,
`EffectiveMissionTypeReadService`) answer exactly as before (C-002).

## Context

- `MissionTypeController` (pack routes) answers through `MissionBodies.ok(read)`, documented "a 200 that carries the one
  fingerprint … and no ETag".
- Data path: `MissionTypeReadService.missionType/step/skeleton` → `PackMissionTypes.holding(pack, id)` →
  `infra/packs/PacksMissionTypes` (a verified holding read from the packs slice) → `MissionTypeHolding(type, skeletons,
  stepContracts, stepFiles, fingerprint)`.
- `EffectiveMissionTypeReadService` builds `MissionTypeHolding` too and reuses `missionTypeOf/stepOf/skeletonOf`.

## Subtasks

### T009 — Tests first (red)

`MissionTypeControllerTest`: the three pack routes assert an `ETag` equal to the revision the fake holding read returns.
`EffectiveMissionTypesControllerTest`: assert the activation routes still answer their own tag, never the pack
revision (strengthen an existing test, do not add a class). `PacksMissionTypesTest`: the holding carries the revision.

### T010 — A pack-only revised holding read

The holding is built in the packs slice: `service/packs/.../application/MissionTypeHoldingRead.java` (record ~56, built
~139 from `verified.fingerprint()`). Do not put the revision on the shared holding: the activation path
(`EffectiveMissionTypeReadService.storedBeside`, ~162-165) calls the same `holding()` and must never compute a pack
revision (C-002). Add a second read, `revised(pack, missionTypeId)`, returning the holding plus a required
`PackRevision`, both inside one `read.read(STALE, …)` lambda, with `DirectiveReadService.directiveWithRevision`'s
`packRefusal` mapping (`DirectiveReadService.java:102-129`). Inject `PackRevisions` (same package). `holding()` is
unchanged. On the missions-edge side add a pack-only port method `PackMissionTypes.revised(...)` returning a small
`PackHolding(MissionTypeHolding holding, String revision)`, implemented in `PacksMissionTypes`. `MissionTypeHolding`
and the effective path's types do not change.

### T011 — Read service and controller

`MissionTypeReadService`'s three pack reads call `revised` and return a pack-only `PackRead<T>(value, fingerprint,
revision)`; `missionTypeOf/stepOf/skeletonOf` stay shared and revision-free. `MissionBodies` gets an
`ok(read, revision)` that also sets `ETag`; the three pack controller methods use it. Remove the "no ETag" javadoc in
`MissionTypeController` (~24) and `MissionBodies` (~230-240).

### T012 — Verify

`spotlessApply`; `:missions-edge:test`; `:packs:test --tests '*MissionTypeHolding*'`; the contract suite: WP01's
mission-type, step and skeleton scenarios are green and the activation mission-type scenarios unchanged. Activation
leak (C-002): in `EffectiveMissionTypesControllerTest`, strengthen a test so an activation mission-type read's ETag is
asserted to be its own tag, not the pack revision, and in `EffectiveMissionTypeReadServiceTest` that the read never
calls the revised holding read. The suite's remaining red is only WP01's recorded tactic and step-contract
scenarios, unless WP02 already landed.

## Definition of Done

- Three pack routes answer the revision; activation routes unchanged.
- A refusal (unknown type, step or skeleton) answers no ETag.
