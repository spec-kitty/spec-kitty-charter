---
work_package_id: WP02
title: The per-file write judge (409 registration_conflict)
dependencies:
- WP01
requirement_refs:
- FR-004
planning_base_branch: feat/355-closure-in-pack-ruleset
merge_target_branch: feat/355-closure-in-pack-ruleset
branch_strategy: Planning artifacts for this mission were generated on feat/355-closure-in-pack-ruleset. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/355-closure-in-pack-ruleset unless the human explicitly redirects the landing branch.
subtasks:
- T009
- T010
- T011
- T012
history:
- at: '2026-11-18T07:00:00Z'
  actor: java-jenny
  action: Prompt generated from plan.md and research.md
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CommitStorePackWriteStore.java
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CommitStoreClosureTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackWithoutTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CommitStorePackWriteStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRemovalStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactRemovalService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Pack.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CommitStoreClosureTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackWithoutTest.java
role: implementer
tags: []
tracker_refs:
- '#355'
---
# Work Package Prompt: WP02 - The per-file write judge

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else. Then read, in
`kitty-specs/closure-symmetry-01M7PNFT/`: `spec.md`, `plan.md` (the Implementation Concern Map), `research.md` (D1 to D6) and `data-model.md`.

## Branch Strategy

Planning base: `feat/355-closure-in-pack-ruleset`. Final merge target: `feat/355-closure-in-pack-ruleset`, then `main`
by the pull request. The execution worktree is allocated per computed lane from `lanes.json`.

```bash
spec-kitty agent action implement WP02 --agent claude --mission closure-symmetry-01M7PNFT
```

## Objective

Every per-file write (`CommitStorePackWriteStore.commit`) and every artifact removal (`.remove`) is judged with
`PackClosure` on the pack as it will be after the write, under the registry lock and `PackWriteLocks`, before the
impact check and before anything is staged. A finding refuses with `CharterRefusal(REGISTRATION_CONFLICT, <the load's
reason>)` (409). A delete of an asset a guard names is refused, not cascaded (operator ruling, 2026-11-17).

## Subtasks

### T009 Red tests at the choke point (`CommitStoreClosureTest`)

Over a copy of the `guard-lifecycle` fixture, through a real `CommitStorePackWriteStore`:
1. `commit` of `Change.artifact(loaded.with(dangling), dangling)` (a guard naming `asset:missing-lint`) is refused with
   `REGISTRATION_CONFLICT` and the load's reason; the tree's bytes are unchanged (compare a digest before and after).
2. `remove` of `asset:carrier-lint` with the pack after the removal is refused with `REGISTRATION_CONFLICT`
   naming guard `traceability-ready`; the tree is unchanged.
3. Controls: the same commit with a guard that closes passes the closure judge; a relationship-only change is not
   refused.

### T010 The commit judge

In `commit`, inside `exclusivelyAt`, before `requireClosuresCompose`: `requireCloses(change.revalidated())`.

### T011 `Pack.without` and `Removal.after`

`Pack.without(Collection<Urn>)` (domain; keeps id, descriptor, the other artifacts, asset files, presets, document,
violations). `PackRemovalStore.Removal` gains `Optional<Pack> after`; `ofArtifacts` takes it; `ofRelationship` has
none. `ArtifactRemovalService` passes `current.without(removed)`.

### T012 The remove judge

In `remove`, inside `exclusivelyAt`, first thing: `removal.after().ifPresent(this::requireCloses)`.

## Definition of Done

- `CommitStoreClosureTest`, `PackWithoutTest` green, red first; all registration and removal tests green.
- The Javadoc of `CommitStorePackWriteStore` names the closure judge beside the impact check.

## Risks

- A loaded aggregate already leaves out what does not close, so the judge never refuses an unrelated write; T009's
  control proves it.
