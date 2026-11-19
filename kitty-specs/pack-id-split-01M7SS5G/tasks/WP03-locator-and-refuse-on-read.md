---
work_package_id: WP03
title: One locator per identity; refuse a non-pack-id directory on read
dependencies:
- WP02
requirement_refs:
- FR-005
- FR-009
- SC-001
planning_base_branch: feat/583-pack-id-split
merge_target_branch: feat/583-pack-id-split
branch_strategy: Planning artifacts for this mission were generated on feat/583-pack-id-split. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/583-pack-id-split unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-id-split-01M7SS5G
base_commit: 0d8c05415abc6f5ccec7d7dd485c89a6d8008943
created_at: '2026-11-19T13:21:48.369872+00:00'
subtasks:
- T010
- T011
- T012
- T013
history:
- at: '2026-11-19T12:00:00Z'
  actor: java-jenny
  action: Prompt generated from plan.md and research.md
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/
create_intent: []
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/rules/**
role: implementer
tags: []
tracker_refs:
- '#583'
---
# Work Package Prompt: WP03 - One locator per identity; refuse a non-pack-id directory on read

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else. Then read, in
`kitty-specs/pack-id-split-01M7SS5G/`: `spec.md`, `plan.md` (the Implementation Concern Map), `research.md`, `data-model.md`,
`research/decisions.md` (D1 to D9, ruled) and `research/census.md` (the file:line of each site).

## Branch Strategy

Planning base: `feat/583-pack-id-split`. Final merge target: `feat/583-pack-id-split`, then `main` by PR #610. The execution worktree is allocated per
computed lane from `lanes.json`.

```bash
spec-kitty agent action implement WP03 --agent claude --mission pack-id-split-01M7SS5G
```

Commits are signed. They carry no model name and no Co-Authored-By trailer.

## Objective

Make read agree with write. A stored directory whose name is not a pack id is refused with a named storage code, and
the locator answers the registered id only (research D3, D5).

Note: `PackLocator.java` and `StorageRootLocations.java` sit in `infra/storage/`, which WP02 owns. WP02 is done before
this WP starts, so editing them here is a recorded out-of-map edit (sequential lane; no collision).

## Subtasks

### T010 Locator

Delete `PackLocator.declaresId` and `declares`, and the second loop in `locate`. Update the Javadoc ("the registered pack
id"). Any caller that passed a descriptor ULID (`GraphWalk`, `ForeignArtifactRead`, `PackMutabilityPolicy.rootOf`,
`RegistrationImpact`) must pass the registered id. Find them with a test that fails, and fix them in WP04 if they sit in
`application/`. List them in the WP's activity log.

### T011 Code

Add `PACK_ID_INVALID = violation("pack_id_invalid")` to `StorageCodes`, with Javadoc. Extend `StorageCodesTest`
(vocabulary census, disjointness).

### T012 Refuse on read

In `StorageRootLocations`, before an entry becomes a `location(...)` (census item 6, around :157), refuse an entry
whose name fails `PackIdentifier.isPackId` through `refuse(entry, reason)`. The reason names `pack_id_invalid`, the
entry and `PackIdentifier.GRAMMAR_DETAIL`. The aside and dot-prefixed exclusions in `isPack` stay as they are.

### T013 Tests

A domain holding `a--b/pack.yaml` and `good-pack/pack.yaml`: the configured list is `[good-pack]`, and the log (or the
refusal sink the class exposes) names `pack_id_invalid` and `a--b`. The same fixture with only `good-pack` refuses
nothing. `PackLocator.locate` by a pack's ULID answers `PACK_NOT_FOUND`, and by its registered id it finds the pack.

## Definition of Done

`./gradlew :packs:check` is green, with the US1 scenarios covered.
