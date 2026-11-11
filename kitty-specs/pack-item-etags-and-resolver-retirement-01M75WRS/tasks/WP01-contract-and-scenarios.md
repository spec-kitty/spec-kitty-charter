---
work_package_id: WP01
title: 'Contract and scenarios: every pack read of one thing carries the revision'
dependencies: []
requirement_refs:
- FR-005
- FR-006
- NFR-001
- C-001
- C-002
- C-003
planning_base_branch: claude/feat-487-488-pack-item-etags
merge_target_branch: claude/feat-487-488-pack-item-etags
branch_strategy: Planning artifacts for this mission were generated on claude/feat-487-488-pack-item-etags. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/feat-487-488-pack-item-etags unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-item-etags-and-resolver-retirement-01M75WRS
base_commit: fb5851282c59976217e210f08240bb54d6c3626c
created_at: '2026-11-11T18:27:47.342761+00:00'
subtasks:
- T001
- T002
- T003
- T004
history:
- at: '2026-11-11T18:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/
create_intent:
- contracts/charter/changes/2026-11-11-pack-item-etags.md
execution_mode: code_change
owned_files:
- contracts/charter/paths/packs_packId_artifacts_kind_artifactId.yaml
- contracts/charter/paths/packs_packId_artifacts_mission_type_*.yaml
- contracts/charter/schemas/Artifact.yaml
- contracts/charter/schemas/ArtifactKindToken.yaml
- contracts/charter/changes/2026-11-11-pack-item-etags.md
- contracts/charter/scenarios/artifacts.feature
- contracts/charter/scenarios/mission-types.feature
- contracts/charter/scenarios/tactics.feature
- docs/api/service-contract.md
- docs/adr/0071-procedures-are-a-served-read-kind.md
role: implementer
tags: []
tracker_refs: []
---

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill (or `spec-kitty agent profile show java-jenny`) and run `spec-kitty charter context --action implement --json` before reading anything else. Read `.kittify/charter/charter.md` and AGENTS.md: the charter's model is canonical, tests use only the canonical shape, test first.

## Branch Strategy

Planning branch and final merge target: `claude/feat-487-488-pack-item-etags`. Execution worktrees (or the write checkout, for a single-branch Mission) come from `lanes.json` via `spec-kitty agent action implement WP01 --agent claude --mission pack-item-etags-and-resolver-retirement-01M75WRS`. Never pick a base branch by hand.

## Build

Java 25: `export JAVA_HOME=/usr/lib/jvm/temurin-25`. Targeted runs only (`./gradlew :<module>:test --tests '<Class>'`, `:contracts:build`, `spotlessApply`). The contract scenarios run with `./gradlew :deployable:test --tests '*CharterContractSuite'`. Leave `verifyAll` to CI.

## Objective

Make the contract say, and the contract scenarios assert, that every pack-scoped read of one thing answers the pack
revision as its `ETag` (#487, FR-005, FR-006). This WP is the red half: after it, the flipped scenarios fail until WP02
and WP03 land. Record the red run.

## Context

Read `kitty-specs/pack-item-etags-and-resolver-retirement-01M75WRS/research.md` (the full list of "no ETag" texts, with
lines) and `contracts/etag-headers.md` in the same directory. The agent profile pack read
(`contracts/charter/paths/packs_packId_artifacts_agent_profile_artifactId.yaml:31-38`) already declares the revision
ETag and explains why it takes no `If-None-Match`: copy its wording style for the mission-type family.

## Subtasks

### T001 — getPackArtifact: tactic and mission step contract carry the revision

- `contracts/charter/paths/packs_packId_artifacts_kind_artifactId.yaml`: in the description (about lines 26-29) replace
  "a tactic or a mission_step_contract, which has no write, carries none, and neither does a mission_type" with: every
  item carries the pack revision as its ETag; for a kind with a write it is the If-Match token of that write; the read
  takes no If-None-Match and never answers 304.
- The 200 `ETag` header description (about 49-53): sent for every kind this read serves.
- The DELETE guidance (about 109, "Neither mission kind answers an ETag, so a client sends If-Match: *"): a client sends
  the read's ETag, or `*`.
- `schemas/Artifact.yaml:10` and `schemas/ArtifactKindToken.yaml`: drop "Neither ... answers an ETag" for the pack reads;
  keep any statement about activation reads unchanged (C-002).

### T002 — Mission type, step and skeleton reads declare the ETag

- `paths/packs_packId_artifacts_mission_type_missionTypeId.yaml` (line ~16 "carries no ETag"), `..._steps_stepId.yaml`
  (~15), `..._skeletons_skeletonName.yaml` (~14): replace with the revision ETag, and add to each 200 response a header:
  ```yaml
  ETag:
    description: >-
      The pack revision the answer was read at: a concurrency token naming the pack, not a cache validator. The answer
      can carry content resolved from the packs this pack depends on, which the revision does not cover, so the read
      takes no If-None-Match and never answers 304.
    required: true
    schema:
      type: string
  ```
  Declare it inline, as `packs_packId_artifacts_agent_profile_artifactId.yaml:56` does; there is no shared pack-revision
  header file. The examples `Artifact.mission-type.yaml` and `Artifact.mission-step-contract.yaml` hold bodies only and
  need no edit.
- Do not touch the `activations_*` paths.

### T003 — Scenarios assert the ETag (red first)

- `scenarios/artifacts.feature` (~74-78): "A tactic is read in the envelope, with no ETag" becomes "... with the pack
  revision as its ETag": `the response header "ETag" appears once`. Strengthen with a second read of a directive in the
  same scenario and assert both ETags are equal (`When I remember the header "ETag"` / `is the remembered header`).
- `scenarios/mission-types.feature`: the mission type (~211), step (~280), skeleton (~369) and step contract (~417,
  ~422) assertions flip from `has no "ETag" header` to `appears once`; the DELETE prose (~635) follows T001.
- `scenarios/tactics.feature` has no ETag assertion on the item read today: add one.
- Every flipped read is compared, not only counted: read a writable kind of the same pack first, `When I remember the
  header "ETag"`, then assert the flipped read's ETag `is the remembered header` (`CharterSteps.java:337/342`). Read
  the flipped item again and assert the same ETag (same state, same revision).
- Mandatory, in `mission-types.feature`: change the pack (a DELETE of a pack artifact, steps around
  `mission-types.feature:634-636`, or a registration write) and assert the mission type's, the tactic's and the step
  contract's ETags differ from the remembered one.
- `effective-mission-types.feature`'s `has no "ETag"` lines (183, 232, 262, 339) are activation routes: leave them
  (C-002). The activation-leak check is WP03's (T012).
- Run `./gradlew :deployable:test --tests '*CharterContractSuite'` and save the failing scenario names in the WP's
  activity log: that is the red-first evidence.

### T004b — Published docs and ADR

- `docs/api/service-contract.md`: line ~60 ("an `ETag` for a writable kind") becomes every item; lines ~68-70 (mission
  type, step, skeleton "No `ETag`") state the revision ETag as a concurrency token; line ~93 ("neither answers an
  `ETag`") follows. Leave the activation rows (~71-74) alone.
- `docs/adr/0071-procedures-are-a-served-read-kind.md`: add "Amendment 2026-11-11" under decision 5: the tactic, the
  mission step contract and the mission-type family now carry the revision too (#487); the asymmetry is gone.
- Run `python3 scripts/docs/check_docs.py all`.

### T004 — Change note

`contracts/charter/changes/2026-11-11-pack-item-etags.md`, heading `### Added`: the five reads now carry the pack
revision; the published "no ETag" statements are withdrawn; the mission-type family's ETag is a concurrency token, not
a cache validator; no `If-None-Match`; core's seam: nothing; additive. Closes the contract half of #487.

## Definition of Done

- `./gradlew :contracts:build` green (lint, compatibility; deepen the clone if it refuses a shallow one).
- `grep -rn -i "no ETag\|carries none\|answers an ETag" contracts/charter/paths/packs_* contracts/charter/schemas docs/api/service-contract.md`
  lists only statements about activation reads; record the expected leftover lines (e.g. `ArtifactKindToken.yaml`
  "under an activation") in the activity log.
- Scenario run recorded red, failing only on the new ETag assertions; record their names, which WP02 and WP03 use to
  tell their own red from WP01's.

## Risks / Reviewer guidance

- Do not change activation path texts (C-002).
- Do not add `If-None-Match` to any pack path (C-001).
