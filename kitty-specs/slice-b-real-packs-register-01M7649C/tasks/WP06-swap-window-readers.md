---
work_package_id: WP06
title: Readers ride out the swap window
dependencies:
- WP05
requirement_refs:
- FR-013
- FR-014
- FR-015
- NFR-001
- NFR-002
- SC-004
planning_base_branch: feat/466-slice-b-real-packs-register
merge_target_branch: feat/466-slice-b-real-packs-register
branch_strategy: Planning artifacts for this mission were generated on feat/466-slice-b-real-packs-register. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/466-slice-b-real-packs-register unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-slice-b-real-packs-register-01M7649C
base_commit: 73ea71a1338933985450b5010d3c1d35ae3452e2
created_at: '2026-11-12T03:06:34.266046+00:00'
subtasks:
- T028
- T029
- T030
- T031
- T032
- T033
phase: Phase 4 - The swap window
history:
- at: '2026-11-11T21:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDirectories.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDirectoriesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/SwapWindowReadTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackCommitStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CommitStorePackWriteStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StorageRootLocations.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackSources.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDirectories.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/SlowSwapCommitStores.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/InterruptedCommitRepairTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackCommitStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDirectoriesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/SwapWindowReadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StorageRootLocationsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationConcurrencyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRegistrationServiceTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP06 - Readers ride out the swap window

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` (or read `java-jenny.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/a79c36318b7a506ca2ab26d74705c5e3e406004f/packs/built-in/agent_profiles/`) and adopt
it. Then read `research.md` D8 and §D, `data-model.md` ("Pack directory during a replace") and
`research/squad-plan-point.md` D1 to D8 in full: the design below comes from them.

## Objective

Operator ruling on #490: a reader that finds a pack's live directory missing while a `.pack-old-*` sibling exists reads
that sibling (the last committed state); a reader that finds neither refuses as today; hot reads take no read lock; a
deterministic test pauses the swap between its two renames; `RegistrationConcurrencyTest.
aReplaceRacingPutsWithIfMatchLeavesOneLinearETagChain` stops flaking by the code fix, with no test-only retry.
Requirements: FR-013, FR-014, FR-015, NFR-001, NFR-002. (`ConsistentRead` is WP07.)

## Context

`PackCommitStore.MOVE_ASIDE` (:86-114): rename 1 live → `<name>.pack-old-<unique>`, rename 2 staging → live, then the
aside is hidden (renamed `.pack-discard-*`) and deleted (:113, `hidden` :850-858). Crash repair :625-718
(`missingLiveDirectory`, `restoreInterruptedSwap`, `asidesOf`, `hideStaleAsides`). Readers: research §D and squad D2.
**Coordination:** the #495 session (#184) adds a package-private seam that resolves a pack id to its configured
location and edits the `resolve()` hunks of `PackCommitStore`, `CommitStorePackWriteStore` and
`DomainRepositoryService`. If it has merged when you start, call the resolver from that seam; otherwise keep your edits
next to (not inside) those hunks so a rebase is mechanical.

## Subtasks

### T028 - A phase hook in the real swap

Add a package-private `SwapPhases` hook (no-op default) to `MOVE_ASIDE` with three points: `afterAside(live, aside)`
(between the renames), `afterLive(live, aside)` (after rename 2, before hiding), `afterHide(live)`. Inject it through
the existing package-private constructors (:162, :176). Rewrite `T/SlowSwapCommitStores.java` to use the real
`MOVE_ASIDE` with the hook (today it re-implements the swap and deletes the aside without hiding it, squad D6); keep its
public factory signature so `PackRegistrationServiceTest.Rig` (~:1003-1018) is unchanged in shape.

### T029 - `PackDirectories`: resolve and re-read

New package-private `infra/storage/PackDirectories.java`:

- `resolve(Path entry)`: the entry's real path if it exists and is not an empty reserved create directory with no
  `pack.yaml` (squad D7: an empty entry with no aside reads as absent); else the missing live directory
  (`PackCommitStore.missingLiveDirectory`, which follows a dangling link chain) and its asides (`asidesOf`): exactly one
  → that aside; zero or several → absent (callers refuse as today). Never serve an aside beside a read-only linked target
  (`mayRestore`/`whyReadOnly`, squad D5).
- `read(Path entry, String liveName, Function<Path, T> body)`: resolve; record `(path, fileKey)` of the resolved
  directory (`BasicFileAttributes.fileKey()`); run `body`; resolve again; accept if the same path has the same fileKey;
  otherwise retry. Count only attempts where the identity changed (progress); cap 8; at the cap refuse `STALE_INPUT`
  with the cause, never `PACK_UNREADABLE`. If `body` throws `NoSuchFileException`/an unlistable refusal and the identity
  did not change, rethrow as today (a genuinely unreadable pack). Pass the live name in so classification under the
  aside's name uses the live name (squad D8; `PackFileSet.loaded` :378).
- `PackDirectoriesTest`: live, aside only, two asides, none, empty reserved, dangling link, read-only linked target.

### T030 - Route every lock-free resolution through it

Squad D2 lists the paths; cover each: `CommitStorePackWriteStore.currentRevision` (:167-181), `digestOf` (:163),
`registeredRelationships` (:184), `authored` (:285); `DomainRepositoryService.over/resolve/declaresId` (:1120-1150) and
`FilesystemPackStore.existingDirectory` (:1109-1114), `realPath` (:773-779), `realPathContained` (:1351-1362) so that a
whole `packs.load` runs inside `PackDirectories.read`; `CommitStorePackWriteStore.declaresId` (:353-363),
`PackCommitStore.declaresId` (:756-761) so a pack addressed by id is found during the window;
`StorageRootLocations.declaredLevel` (:438-441); `FileSystemPackSources.addPack` (:94-112); `WholePackStore.unmodelled`
(:790-806) and `PackFileSet.regularFiles` (:482-492) via their callers (those two files are owned by WP02/WP03: change
them only if needed, as recorded out-of-map edits). Write paths under the lock keep their current behavior.

### T031 - Listing and create

`StorageRootLocations` (:110-268, :374-391): half-swapped and stale entries use `tryLock` instead of `underLock`'s
10-second wait; a half-swapped entry whose lock is held is listed with its directory from `PackDirectories.resolve`
(no WARN, no `refuse`); when the lock is free, the crash repair runs as today. A pack listed through its aside keeps its
declared level (`declaredLevel` reads the resolved directory). Update `StorageRootLocationsTest` and
`InterruptedCommitRepairTest:185, :237-242` to the ruling.

### T032 - Deterministic tests

New `SwapWindowReadTest`, all on the T028 hook, each asserting no thread queued on `PackWriteLocks.of(live)` and a
1-second bound:

- **T1 AsideOnly** (`afterAside`): `currentRevision`, `digestOf`, `repository.load`, `locations.all()` (with the right
  level) and a pack addressed by id all answer R0; release; `currentRevision` equals the commit's revision R1.
- **T2 LivePlusAside** (`afterLive`): reads answer R1; the listing does not wait.
- **T3 torn read**: a reader-side seam pauses a read between listing and reading bytes; run a whole swap; the read
  answers exactly R0 or R1 (fails before the fix).
- **T4 rollback**: rename 2 throws; reads before and after answer R0.
- **T5** T1 on a linked pack (`LinkedPackCommitTest` setup).
- **T6 create**: pause between reserve and move; the read answers `pack_not_found`.
- **T7 cap**: force identity changes past the cap; the read refuses `stale_input`, not `pack_unreadable`.

### T033 - The concurrency test and the lock-wait test

- `InterruptedCommitRepairTest:154` `aReadThatMeetsASwapInFlightWaitsForItAndServesTheNewState` → rename
  `aReadThatMeetsASwapInFlightServesTheLastCommittedStateWithoutWaiting`: start the reader inside the window, `join`
  it before rename 2 (the old `awaitQueuedOnTheLockOf` would hang), assert it finished while the lock was held with no
  queued thread and served the old toolguides; after the commit a read serves the new; no residue.
- `RegistrationConcurrencyTest`: no change to its logic and no retry added. Run it 20 times:
  `for i in $(seq 20); do ./gradlew --max-workers=1 :packs:test --tests '*RegistrationConcurrencyTest*' --rerun -q || break; done`
  and record the count in the WP's activity log (NFR-002).

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :packs:test
./gradlew --max-workers=1 :deployable:test
```

## Branch Strategy

Planning base and merge target: `feat/466-slice-b-real-packs-register`; worktrees per lane. Implement with
`spec-kitty agent action implement WP06 --agent claude --mission slice-b-real-packs-register-01M7649C`. Commits signed,
no model trailers, refs `(#490)`.

## Definition of Done

T1 to T7 green; T3 shown red before the fix (commit order: test first, or record the red run); the concurrency test
20/20; no lock added to a read path (NFR-001).

## Risks and reviewer guidance

Look for any read path still calling `toRealPath` on `packs/<name>` outside `PackDirectories`. Check the re-read never
retries on an unchanged identity. Check the hook is a no-op in production wiring.
