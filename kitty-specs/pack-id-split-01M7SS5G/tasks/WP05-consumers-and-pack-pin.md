---
work_package_id: WP05
title: 'Consumers: activations, artifacts-edge, web transport, pack-check; PackPin grammar'
dependencies:
- WP04
requirement_refs:
- FR-003
- FR-004
- FR-011
- NFR-002
planning_base_branch: feat/583-pack-id-split
merge_target_branch: feat/583-pack-id-split
branch_strategy: Planning artifacts for this mission were generated on feat/583-pack-id-split. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/583-pack-id-split unless the human explicitly redirects the landing branch.
subtasks:
- T021
- T022
- T023
- T024
- T025
history:
- at: '2026-11-19T12:00:00Z'
  actor: java-jenny
  action: Prompt generated from plan.md and research.md
agent_profile: java-jenny
authoritative_surface: service/activations/
create_intent:
- contracts/charter/changes/2026-11-19-pack-pin-follows-the-pack-id-grammar.md
execution_mode: code_change
owned_files:
- service/activations/**
- service/web-transport/**
- service/artifacts-edge/src/main/**
- service/pack-validation/**
- contracts/charter/schemas/PackPin.yaml
- contracts/offering/schemas/ActivationConfig.schema.json
- contracts/charter/changes/2026-11-19-pack-pin-follows-the-pack-id-grammar.md
role: implementer
tags: []
tracker_refs:
- '#583'
---
# Work Package Prompt: WP05 - Consumers: activations, artifacts-edge, web transport, pack-check; PackPin grammar

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else. Then read, in
`kitty-specs/pack-id-split-01M7SS5G/`: `spec.md`, `plan.md` (the Implementation Concern Map), `research.md`, `data-model.md`,
`research/decisions.md` (D1 to D9, ruled) and `research/census.md` (the file:line of each site).

## Branch Strategy

Planning base: `feat/583-pack-id-split`. Final merge target: `feat/583-pack-id-split`, then `main` by PR #610. The execution worktree is allocated per
computed lane from `lanes.json`.

```bash
spec-kitty agent action implement WP05 --agent claude --mission pack-id-split-01M7SS5G
```

Commits are signed. They carry no model name and no Co-Authored-By trailer.

## Objective

Carry the split through the modules that consume `packs`, and narrow the activation pin to the pack id grammar (D7).

## Subtasks

### T021 Activations

`ClosureViewSession` :540 uses the winner's `PackUlid`. `ClosureSnapshot`'s descriptor ids are typed `PackUlid` where
they are keys. `PacksAvailability` :104 and `ClosureRoots` :157 (`PackIdentifier::new`) take registered ids only.

### T022 Web and artifacts-edge

`RefusalHandler` maps the typed `RefusalPack` to the same wire strings. `ReferenceReads` and `EffectiveBodies` :239
(`HoldingPack(registered, ULID, name)`) take the typed values.

### T023 `PackCheck`

`validate` takes the registered `PackIdentifier` the pack is assembled under. Keep an overload that takes none and
defaults to the directory name. A directory name that is not a pack id is reported with `pack_id_invalid` as an error
finding (map the storage code through `PackVerdicts` as other storage codes are). Message #478's session
(session_019C2Tk3DWXUYckDJtrKcbGg) with the final signature.

### T024 PackPin

`PackRef(PackIdentifier packId, Optional<String> version)`: drop its private `SLUG` regex. Contract:
`contracts/offering/schemas/ActivationConfig.schema.json` `$defs/PackPin/properties/packId` and
`contracts/charter/schemas/PackPin.yaml` `packId` become `{type: string, minLength: 1, maxLength: 63, pattern:
'^[a-z0-9]+(-[a-z0-9]+)*$'}` (in parity). Write the fragment
`contracts/charter/changes/2026-11-19-pack-pin-follows-the-pack-id-grammar.md` under `### Breaking`: an activation pin's
`packId` follows the pack id grammar (a kebab slug of at most 63 characters, ADR-0075 decision 2). A pin with `_` or 64
characters, which no registered pack can carry, is now refused by the schema (422 `activation_invalid` as before, now at
validation). Check `contracts/charter/changes/README.md` for the fragment format. Add a contract example or scenario
for the refused pin if the scenarios cover PackPin.

### T025 Tests

Fixtures in these modules use the canonical shape.

## Definition of Done

`./gradlew verifyAll` is green apart from the remaining test trees that WP06 owns. List any failures that are WP06's.
