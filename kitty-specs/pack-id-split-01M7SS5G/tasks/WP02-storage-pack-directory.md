---
work_package_id: WP02
title: 'Storage edge: PackDirectory names directories'
dependencies:
- WP01
requirement_refs:
- FR-006
- FR-004
planning_base_branch: feat/583-pack-id-split
merge_target_branch: feat/583-pack-id-split
branch_strategy: Planning artifacts for this mission were generated on feat/583-pack-id-split. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/583-pack-id-split unless the human explicitly redirects the landing branch.
subtasks:
- T004
- T005
- T006
- T007
- T008
- T009
history:
- at: '2026-11-19T12:00:00Z'
  actor: java-jenny
  action: Prompt generated from plan.md and research.md
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDirectory.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/*.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/*.java
- service/packs/src/test/resources/**
role: implementer
tags: []
tracker_refs:
- '#583'
---
# Work Package Prompt: WP02 - Storage edge: PackDirectory names directories

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else. Then read, in
`kitty-specs/pack-id-split-01M7SS5G/`: `spec.md`, `plan.md` (the Implementation Concern Map), `research.md`, `data-model.md`,
`research/decisions.md` (D1 to D9, ruled) and `research/census.md` (the file:line of each site).

## Branch Strategy

Planning base: `feat/583-pack-id-split`. Final merge target: `feat/583-pack-id-split`, then `main` by PR #610. The execution worktree is allocated per
computed lane from `lanes.json`.

```bash
spec-kitty agent action implement WP02 --agent claude --mission pack-id-split-01M7SS5G
```

Commits are signed. They carry no model name and no Co-Authored-By trailer.

## Objective

Give the store a key that names a directory, not a pack: `PackDirectory`. Every staging, aside and raw directory name
travels as one, and no storage-edge code builds a `PackIdentifier` from a directory name or a descriptor value
(census items 5 and 6; research D4).

## Subtasks

### T004 `PackDirectory`

A package-private `record PackDirectory(String name)` in `packs.infra.storage`. Its constructor refuses blank text, a
`/` or `\`, `.` and `..` with `IllegalArgumentException`. Factories: `of(PackIdentifier)`, `of(Path)` (the file name).
Unit test it.

### T005 `FilesystemPackStore`

Every method keyed by `PackIdentifier` (`directoryOf`, the `read` overloads, `readDescriptor`, `findDescriptor`,
`readAll`, `readOne`, `readGraphs`, `write*`, `writePayload`, `existingDirectory`, `descriptorOf`) takes a
`PackDirectory`. Keep `directoryOf`'s under-the-root check. Callers that hold a registered id convert with
`PackDirectory.of(id)`.

### T006 `PackCommitStore`

Sites :303, :494, :938 (census): the `.pack-commit-*` staging name and the temp or archive directory name become
`PackDirectory`. `revisionOf`'s label takes a `PackDirectory` (or its name). :622 and :711 keep the registered id.

### T007 The other directory sites

`WholePackStore` :295/:385/:416, `CommitStorePackWriteStore` :359/:375/:443, `DomainRepositoryService` :650/:677,
`DomainRoundTrip` :186, `StoreRead` :199/:260 and `PackLocator` :116 become `PackDirectory`.
`CommitStorePackWriteStore` :339 and `DomainRepositoryService` :348 keep the registered id.

### T008 The registered id on assembly

`StoreRead.of(...)` and `ArtifactFiles.of(...)` take the registered `PackIdentifier` the pack is assembled under, as a
parameter from the caller. `StoreRead` :113 and `ArtifactFiles` :42 stop wrapping `descriptor().packId()`. Callers:
`WholePackStore` (registration: the upload's id), `DomainRepositoryService` (the configured id),
`PackManifestDerivation` and `CompositionFactsBuilder` (their caller's id), `CommitStorePackWriteStore` (the pack being
written), `FileSystemPackInspector` (the caller-passed id, defaulting to the directory name; see WP04's `PackCheck`).
Where a directory name is the default and is not a pack id, the read refuses it with `pack_id_invalid` (WP03 adds the code;
until then use `PackIdentifier.isPackId` and refuse with the existing unreadable path, and leave a TODO for WP03).

### T009 Tests

Move storage tests to `PackDirectory` where they address a directory. A fake ULID in a test `pack.yaml` (`01PACK`,
`02OTHER`, ...) becomes a real ULID. A `PackIdentifier` built from a ULID becomes a `PackUlid` or the registered id that
fits. Invalid literals stay only in error-path tests.

## Definition of Done

- `./gradlew :packs:check` is green.
- `grep -n "new PackIdentifier(" service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/*.java` shows
  only registered-id sites (census list).

## Risks

#517 (PR #609) edits `PackCommitStore` and `PackRegistrationService`. Rebase on it when it lands, and tell its session
about any new site.
