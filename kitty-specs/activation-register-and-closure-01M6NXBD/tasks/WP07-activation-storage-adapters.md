---
work_package_id: WP07
title: activation storage (repository port, in-memory and file adapters)
dependencies:
- WP04
- WP06
requirement_refs:
- FR-019
- FR-003
- NFR-004
- NFR-005
- SC-003
- NFR-001
- C-005
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-activation-register-and-closure-01M6NXBD
base_commit: 9803c7d286b84ac8e7bbf496374f0fe2d7937d3e
created_at: '2026-11-05T20:34:38.620883+00:00'
subtasks:
- T041
- T042
- T043
- T044
- T045
- T046
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/
create_intent:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ActivationRepository.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/StoredActivation.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/InMemoryActivationRepository.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepository.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/ActivationFileCodec.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/ActivationRepositoryContract.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/InMemoryActivationRepositoryTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepositoryTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepositoryConcurrencyTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepositoryFailureTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/ActivationFileCodecTest.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ActivationStorageWiring.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationRestartTest.java
execution_mode: code_change
model: ''
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ActivationRepository.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/StoredActivation.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/InMemoryActivationRepository.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepository.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/ActivationFileCodec.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/ActivationRepositoryContract.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/InMemoryActivationRepositoryTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepositoryTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepositoryConcurrencyTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepositoryFailureTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/ActivationFileCodecTest.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ActivationStorageWiring.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationRestartTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProductionWiringTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/WiringRoundTripTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP07: activation storage (repository port, in-memory and file adapters)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Store activation documents behind a domain-owned port with compare-and-set on the revision (FR-019):

- an in-memory adapter, for unit tests and the scenario suite;
- a file adapter under `<config-root>/activations/<id>/activation.json`, which writes a temp file, fsyncs it, moves
  it with `ATOMIC_MOVE` and fsyncs the directory. It takes a per-id `ReentrantLock` and has a `Rename` failure seam
  mirroring `FilesystemPackStore`;
- proofs: 8 concurrent writers with one `If-Match`, exactly one wins, in 100 of 100 runs (NFR-004); a recreated
  application context reads every document back byte-identical, and an injected failure between the temp write and
  the move leaves the old document readable with no partial file (NFR-005, SC-003).

Implementation command:
`spec-kitty agent action implement WP07 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** A. **Concern:** IC-06a. **Research:** R4. **Decision:** `DM-01M6P92QKYFZW9KDGGQGX9H9VS` (atomic move plus
  in-process per-id lock; multi-process stays #349; one replica, ADR-0061 of PR #392).
- **Requirements:** FR-019, FR-003 (compare-and-set backs `If-Match`), NFR-004, NFR-005, SC-003.
- **Collisions:** PR #390 touches `FilesystemPackStore` (the seam being mirrored, not edited here); PR #392 adds
  ADR-0061 (one replica). Neither edits these files.
- **Grounding:**
  - `FilesystemPackStore` has `interface Rename { void move(Path temp, Path target) throws IOException; }` with
    `ATOMIC = (t, g) -> Files.move(t, g, ATOMIC_MOVE)`, and `writeAtomically` (lines ~1225–1244) deletes the temp file
    on any failure. Mirror the shape. Do not import it: activations may not name `packs.infra`.
  - `ActivationStorageRoot` (WP05, deployable) gives `Optional<Path>`. The adapter choice is made in `deployable`,
    which is the only module that may name `activations.infra.storage` from outside the slice.
  - WP05 generalised `PORT_INTERFACES_RESIDE_IN_DOMAIN`, `PORT_IMPLEMENTATIONS_RESIDE_IN_STORAGE` and
    `STORAGE_RETURNS_DOMAIN` per slice. The port goes in `activations.domain` and the adapters in
    `activations.infra.storage`.
  - The stored bytes are the JCS canonical form (WP06), so "byte-identical" is free: what is written is what the
    revision hashes.

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure`.
- Lane worktree from `lanes.json`. It depends on WP06 (the document and revision) and on WP04. WP07 is the single
  owner of `ProductionWiringTest` and `WiringRoundTripTest` (P5), and it makes any edit WP04 recorded as needed.
- Rebase onto the lane base before review.

## Subtask Guidance

### Subtask T041: The port and the record

**Steps:**
1. `activations.domain.StoredActivation(ActivationId id, ActivationDocument document, Revision revision)`.
2. `activations.domain.ActivationRepository`:
   - `Optional<StoredActivation> find(ActivationId id)`;
   - `Optional<StoredActivation> create(ActivationId id, ActivationDocument document)`, which stores only when absent
     and returns empty when the id exists (`If-None-Match: *`);
   - `CasResult replace(ActivationId id, Optional<Revision> expected, ActivationDocument document)`. With `expected`
     present, the write happens only when the stored revision equals it; with it empty, the last writer wins.
     `CasResult` is a sealed interface `Written(StoredActivation, boolean created) | Stale(Revision current) |
     Missing`;
   - `DeleteResult delete(ActivationId id, Optional<Revision> expected)`: `Deleted | Stale | Missing`.
3. The Javadoc states the contract the tests in T042 pin: atomic per id, linearisable per id, no partial document
   ever visible.

### Subtask T042: The shared contract test and the in-memory adapter

**Steps (red first):**
1. `ActivationRepositoryContract`, an abstract JUnit class with `abstract ActivationRepository repository()`, covers:
   - create then find (same revision);
   - create on an existing id returns empty and leaves the stored document unchanged;
   - replace with the current revision writes, and with a stale one answers `Stale(current)`;
   - replace without `expected` writes (last writer wins), and on a missing id creates (`created == true`; the
     service maps it to `201`);
   - delete with the current revision, then again: `Missing`; delete with a stale one: `Stale`;
   - two ids are independent.
2. `InMemoryActivationRepository`: a `ConcurrentHashMap<ActivationId, StoredActivation>` using `compute` for
   compare-and-set (the lambda decides; no check-then-act). `InMemoryActivationRepositoryTest` extends the contract.
3. Add `clear()` for the scenario suite, used per scenario by WP08's glue. Keep it on the in-memory class only,
   never on the port.

### Subtask T043: The file adapter

**Steps (red first, `FileActivationRepositoryTest extends ActivationRepositoryContract` over a `@TempDir`):**
1. Layout: `<root>/<id>/activation.json`, holding `Jcs.canonical(document.toCanonicalTree())` exactly.
   `ActivationFileCodec` reads it back: parse with Jackson (allowed in `infra.storage`) into the plain
   `Map`/`List` tree, then build the document with WP06's builder. A file that does not parse, or builds with
   violations, is a `CharterRefusal(INTERNAL_ERROR, …)` naming the id. A corrupt store is a fault of the service, not
   of the client. Unit-test the codec on its own (`ActivationFileCodecTest`).
2. Writes: `Files.createDirectories(dir)`; temp `activation.json.tmp-<uuid>` in `dir`; write with a `FileChannel`
   and `force(true)`; `rename.move(temp, target)` with `ATOMIC_MOVE` and `REPLACE_EXISTING`; then fsync the directory
   where the OS allows (open the directory `FileChannel` read-only and `force(true)`, catching the `IOException`
   some file systems throw, with a comment saying why). On any failure, delete the temp file, and throw
   `CharterRefusal(INTERNAL_ERROR, …)` with the cause.
   **Windows (J6):** `Files.move(…, ATOMIC_MOVE, REPLACE_EXISTING)` throws `AccessDeniedException` there when the
   target is open in another thread. Prevent it rather than retry blindly: **`find` also takes the id's lock** while it
   reads the file (reads are short). Then no reader holds the target open during a move. As a second guard, retry the
   move a bounded number of times (3, with no sleep longer than 10 ms) on `AccessDeniedException` only. Directory
   fsync stays best-effort: catch and comment. Document the platform notes in the class Javadoc.
3. Locks: `ConcurrentHashMap<ActivationId, ReentrantLock>`, `computeIfAbsent`; every `create`, `replace` and `delete`
   holds the id's lock across read-compare-write. `find` takes the lock too (J6); on POSIX the atomic move alone
   guarantees whole files, but on Windows an open reader blocks the move.
4. Delete: remove `activation.json`, then the directory if it is empty. A stray `*.tmp-*` in the directory is ignored
   by `find` and deleted on the next write.
5. Constructors: `FileActivationRepository(Path root)` with the atomic `Rename`, and a package-private
   `FileActivationRepository(Path root, Rename rename)` for the failure test.
6. The id never escapes the root: resolve `root.resolve(id.value())` and check `normalize().startsWith(root)`. The id
   grammar already forbids `/` and `..`, so this is defence in depth.

### Subtask T044: Concurrency (NFR-004)

**Steps:** `FileActivationRepositoryConcurrencyTest`:
1. For 100 iterations, store a document, read its revision R, start 8 threads on a `CountDownLatch` gate, and have each
   `replace(id, Optional.of(R), distinctDocument_i)`.
2. Assert exactly 1 `Written` and 7 `Stale`, and that the stored document is the winner's.
3. Use a fixed thread pool, join with a timeout (the test fails, never hangs), and never `sleep`.
4. Run the same body against the in-memory adapter (the contract class can host it as a parameterised test).
5. Tag nothing `timing`: this is correctness, and it runs in `test`.

### Subtask T045: Durability (NFR-005)

**Steps:**
1. `FileActivationRepositoryFailureTest`: with a `Rename` that throws an `IOException`, store document A, then
   attempt to replace it with B. The call throws, `find` returns A byte-identical (compare the file bytes), and the
   directory holds no `*.tmp-*` file. Repeat in 100 iterations with a random document each time (the "100% of runs").
2. A `Rename` that throws a `RuntimeException`: the same guarantees hold.
3. `service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationRestartTest.java`: boot
   `CharterServiceApplication` on a temp config root (follow `ConfigRootTestConfiguration` and `ConfigRootWiringTest`
   for how to point the context at a root), write N=20 documents through the `ActivationRepository` bean, close the
   context, boot a new one on the same root, and read each back. Assert byte-identical files and equal revisions (SC-003).
   Until WP09 builds the HTTP operations, the test drives the repository bean directly.

### Subtask T046: Wiring in the composition root

**Steps (red first, in `ActivationRestartTest`, or a small `ActivationStorageWiringTest` beside it):**
1. With a config root, the `ActivationRepository` bean is a `FileActivationRepository` on
   `ActivationStorageRoot.directory()`.
2. Without one (the scenario suite and the unit slices), it is `InMemoryActivationRepository`.
3. `deployable/ActivationStorageWiring.java`: `@Configuration(proxyBeanMethods = false)` with one `@Bean
   ActivationRepository activationRepository(ActivationStorageRoot root)`. Nothing creates the directory at startup;
   the first write does (as `ConfigRoot` requires).
4. Run `:deployable:test --tests '*ProductionWiringTest*' --tests '*WiringRoundTripTest*'`. This WP owns both tests
   (P5): fix any assertion that enumerates beans, and apply any edit WP04 recorded for `ClosureQueries`.

## Checkstyle limits (NFR-001)

CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1 (by construction; the limits are still off on `main`, so the
reviewer measures by hand). `FileActivationRepository.replace` is the
hotspot; split it into `underLock(id, action)`, `currentRevision` and `writeAtomically`. No new suppression, and no
disabled or skipped test. Do not mark the concurrency test flaky; fix the cause.

## Gate commands

```bash
./gradlew :activations:check
./gradlew :deployable:test --tests '*ActivationRestartTest*' --tests '*HexagonalArchitectureTest*' \
  --tests '*ProductionWiringTest*' --tests '*WiringRoundTripTest*'
```

## Definition of Done

- [ ] `ActivationRepository` (domain) with create, find, replace (CAS or last writer wins) and delete; both adapters
      pass the shared contract test.
- [ ] The file adapter writes temp, fsync, `ATOMIC_MOVE` and directory fsync, under a per-id lock, with a `Rename`
      seam.
- [ ] The concurrency test: 1 winner and 7 `Stale` in 100 of 100 iterations, on both adapters.
- [ ] The failure test: the old document is byte-identical and no temp file remains, in 100 of 100 iterations.
- [ ] `ActivationRestartTest`: 20 documents survive a context restart byte-identical.
- [ ] The adapter choice is wired in `deployable` from `ActivationStorageRoot`.
- [ ] No new suppression, and no disabled test.

## Risks

- **Directory fsync is not portable** (it fails on some file systems and on Windows). Catch the exception, comment
  why, and never fail the write for it.
- **Lock map growth:** one lock per id ever touched. That is acceptable at this scale (one project per activation);
  note it in the Javadoc. Removing a lock on delete is racy; do not.
- **The restart test boots Spring twice.** Keep N small and the test in `deployable`, not `activations`.

## Reviewer guidance (reviewer-renata)

- **Check the CAS is atomic:** in the in-memory adapter the comparison must happen **inside** `compute`; in the file
  adapter, inside the id's lock. A `find` followed by a `put` outside a lock is a bug the concurrency test may miss on
  a fast machine.
- **Non-fakeable evidence:** the concurrency test's loop count (100) and its assertion of exactly one winner; the
  failure test listing the directory after the injected failure; `ActivationRestartTest` comparing file bytes, not
  only revisions.
- **Check the stored bytes are the JCS bytes** (open a written file in the test and compare with
  `Jcs.canonical(...)`).
