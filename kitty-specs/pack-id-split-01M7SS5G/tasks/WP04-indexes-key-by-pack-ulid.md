---
work_package_id: WP04
title: Closure and edge indexes key by PackUlid; typed refusal pack
dependencies:
- WP03
requirement_refs:
- FR-003
- FR-004
- FR-007
- FR-008
planning_base_branch: feat/583-pack-id-split
merge_target_branch: feat/583-pack-id-split
branch_strategy: Planning artifacts for this mission were generated on feat/583-pack-id-split. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/583-pack-id-split unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-id-split-01M7SS5G
base_commit: 9845369bbe2f56d609f090965dd924044011ff5b
created_at: '2026-11-19T15:33:40.332093+00:00'
subtasks:
- T014
- T015
- T016
- T017
- T018
- T019
- T020
history:
- at: '2026-11-19T12:00:00Z'
  actor: java-jenny
  action: Prompt generated from plan.md and research.md
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/
create_intent: []
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalPack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/**
- service/packs/src/main/java/ai/speckitty/charter/packs/application/**
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/**
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/**
- service/packs/src/test/java/ai/speckitty/charter/packs/application/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/**
role: implementer
tags: []
tracker_refs:
- '#583'
---
# Work Package Prompt: WP04 - Closure and edge indexes key by PackUlid; typed refusal pack

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else. Then read, in
`kitty-specs/pack-id-split-01M7SS5G/`: `spec.md`, `plan.md` (the Implementation Concern Map), `research.md`, `data-model.md`,
`research/decisions.md` (D1 to D9, ruled) and `research/census.md` (the file:line of each site).

## Branch Strategy

Planning base: `feat/583-pack-id-split`. Final merge target: `feat/583-pack-id-split`, then `main` by PR #610. The execution worktree is allocated per
computed lane from `lanes.json`.

```bash
spec-kitty agent action implement WP04 --agent claude --mission pack-id-split-01M7SS5G
```

Commits are signed. They carry no model name and no Co-Authored-By trailer.

## Objective

The indexes name the identity they key by. Artifacts carry the registered id, and nothing rewraps one identity as the
other (research D2, D6, D9; census items 2 to 4).

## Subtasks

### T014 `RefusalPack`

Change it to `(PackUlid descriptorPackId, PackIdentifier packId, String name)`. Its 15 callers pass typed values.
`RefusalHandler` (web-transport) writes `.value()`, so the wire is unchanged. `ClosureView.refusalPack`'s fallback
(:927) put the registered id into `descriptorPackId`: it must find the pack's real descriptor, or the refusal must not
name the pack. Read the code to decide, and record the choice in the activity log. Callers in activations,
web-transport and artifacts-edge are WP05's, so compile-fix them minimally here or leave `:packs:check` as this WP's gate.

### T015 Merge

`MergedClosure.Winner.descriptorPackId()` returns `PackUlid`. `LayerMerge` keys languages by `PackUlid`.
`Winner.packId()` stays the registered id.

### T016 `EdgeIndex`

`Place(PackUlid, Urn)`. `byPack: Map<PackUlid, ...>`. `idOf` returns `PackUlid.of`-style construction from the
descriptor (the only conversion allowed). `EdgeNode.packId()` returns the configured (registered) id from `byPack`.
`ComposeResolver.inPack(PackUlid)`. `holdingPack(reference, PackUlid referring)` returns `Optional<PackUlid>`, without
the `orElse(referringPack)` tolerance. Update `ComposeResolver` and `PackCompositionResolver` to match.

### T017 Callers

Each site of census item 3 takes the typed value. Any `new PackIdentifier(winner.descriptorPackId())` becomes the
`PackUlid` directly. Callers that passed a ULID to `PackLocator` (WP03 T010) pass the registered id.

### T018 Enhancement

`Dropped.pack` and `Enhancing.pack` become `PackIdentifier`, and the rewraps at ClosureView:886 and
ProfileEnhancementRule:95 go.

### T019 Inbound references

`StagedTree` gains the registered id the upload targets (from `PackRegistrationService`, `upload.packId()`).
`membersOf` uses it in place of `descriptor().name()`. Add a test: the refusal for a new pack's broken reference names
the upload id.

### T020 Tests

Domain and application fixtures: fake ULIDs (`01PACK`, `02OTHER`, `01TOOLPACK`) become real ULIDs in `PackUlid`.
Registered ids stay slugs.

## Definition of Done

- `./gradlew :packs:check` is green.
- `grep -rn "new PackIdentifier(" service/packs/src/main | grep -E "descriptor|Winner|dropped"` shows 0 hits.

## Risks

The order and answers of closure reads must not move. The golden snapshots and contract scenarios are the guard; any
diff is a defect, not a fixture update.
