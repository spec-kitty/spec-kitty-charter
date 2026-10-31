# Research: the interrupted-commit repair for packs listed from `packs/` (#281)

Read-only research, 2026-10-31, on `issue-281-crash-repair` at `origin/main`. The operator's rulings
on #174 (PR #280) and #284–#287 (PR #289), recorded in ADR-0038 as amended 2026-10-31, bind:
a pack linked from inside the configuration root commits through its real target, one linked from
outside is read-only through the service, a linked `packs/` is followed, and overlapping real paths
and dangling links are refused. This note does not reopen them.

## Run shape

A `spec-kitty dispatch` Op (profile `java-jenny`), not a mission. It is one red-first bugfix in one
module (`service/packs`, `infra.storage`) with an ADR paragraph and javadoc to update. There is no
contract, API or domain-model change.

## Why the repair is unreachable

- `CommitStorePackWriteStore.exclusively` runs `resolve(pack)` first, which lists the packs
  (`StorageRootLocations.all`), and only then calls `PackCommitStore.restoreInterruptedSwap`.
- **Plain pack.** The live directory is missing, so `Files.list(packs)` has no entry for it.
  `isPack` drops the `<name>.pack-old-*` aside copy, so `resolve` answers `PACK_NOT_FOUND`.
- **Linked pack.** The link dangles, so `directoryOf` refuses it. If the repair were reached, it
  would still return early, because `Files.exists(link, NOFOLLOW_LINKS)` is true for the link. It
  would also look beside the link for `<link>.pack-old-*`. The aside copy sits beside the *target*
  and is named after it (`MOVE_ASIDE`).
- The only test of the repair, `PackCommitStoreTest.anInterruptedSwapIsRestoredBeforeTheNextWrite`,
  uses a fixed-list `Locations` lambda that still resolves the missing directory. That is why it
  passes. The two existing throwing `DirectoryRename`s throw before any move, so neither leaves an
  aside copy.

## Who lists packs

`Locations.all()` is reached from:

- every read port through `DomainRepositoryService.resolve`;
- `PackCommitStore.resolve`;
- `CommitStorePackWriteStore.resolve`, which serves `currentRevision` (the ETag on reads),
  `registeredRelationships` and `exclusively`;
- `CharterWiring.packLocations`, which serves `FileSystemPackSources.fingerprint` (`ConsistentRead`
  and `/info`).

Every registration service already calls `all()` while it holds the pack's write lock: `exclusively`
wraps load, precondition and commit, and `commit` re-enters `exclusively`.

## Options

| Option | Reads after a crash | Writes after a crash | Cost |
| --- | --- | --- | --- |
| (a) Repair before resolution, in `exclusively` only | `PACK_NOT_FOUND` until something writes; clients that read first never get there | repaired | needs its own discovery of the missing pack anyway |
| **(b) Repair on the listing, under the pack's write lock** | **repaired, then served** | **repaired (writes list first)** | **reads may move a directory; a lock shared by the listing and the write store** |
| (c) Serve the aside copy read-only | served under the wrong directory name | still unreachable | contradicts ADR-0038 decision 3 (an aside copy is not a pack) |

### Recommendation: (b), wait and serve

A listing that finds a half-swapped pack takes that pack's write lock, checks again, and moves the
sole aside copy back. It then lists again, so the overlap and level rules apply to the restored pack.

- **A read that meets the half-swapped state** is one of two cases:
  - **A crash.** The read restores the last committed state and serves it. The interrupted commit was
    never acknowledged, so nothing acknowledged is lost.
  - **A commit in flight in this JVM.** The read waits for the swap to finish and serves the new state.
    It does not answer a transient `PACK_NOT_FOUND`.
- **A read already inside another pack's write lock** (a registration that lists while it holds its
  own pack's lock) only *tries* the half-swapped pack's lock. If that lock is held, it skips the pack
  for that listing.
  - Today no cycle can form. A lock's holder leaves its pack missing only between `MOVE_ASIDE`'s two
    renames, which take no lock, or after a failed restore, which propagates out of `exclusively`.
  - A future work body that catches a commit failure and lists again while it holds the lock could
    otherwise deadlock two packs. The try-lock rules that out.
- **Decision 4** ("the service creates no directory") holds. A restore is a rename of the pack's own
  last committed copy.

## The repair's rules

- **Where it looks.**
  - **Plain pack:** a `<name>.pack-old-*` directory in the domain with no `<name>` entry.
  - **Linked pack:** a link that does not resolve.
  - A link is followed one hop at a time with `Files.readSymbolicLink`, since `toRealPath` fails on a
    dangling link. Each hop is resolved against the *real* path of the directory that holds the link,
    never lexically: `..` under a linked `packs/` would go wrong. The walk stops after 40 hops, the
    usual `ELOOP` bound.
  - The directory to restore is the chain's final, missing path, written as its real parent plus its
    name. That is where `MOVE_ASIDE` put the aside copy, because the commit swaps the real path.
- **Where it acts.**
  - Only when that directory's real parent is inside the pack domain's real directory, mirroring
    `whyNotAPack`.
  - Only when the pack is writable through the service (`Locations.whyReadOnly` is empty). The service
    never commits outside the configuration root, so an aside copy there is not its own. It is left
    untouched and the dangling link is refused as before.
- **What it moves.** Exactly one aside copy, back into place. With zero or several, nothing is
  guessed.
- **A restore that fails** (a read-only mount, for example) is warned once and the entry is skipped.
  It must not break every read.
- **The live directory exists:** nothing is done. A stray aside copy beside a live pack is left
  untouched.

### A hazard the automatic repair creates, and its fix

`MOVE_ASIDE` deletes the aside copy after a successful swap, and `deleteTree` swallows failures, so a
half-deleted `<name>.pack-old-*` can remain. Today that is inert. With an automatic repair, an
operator who later removes `packs/<name>` on purpose (decision 4) would see the half-deleted copy come
back. The fix:

- After a successful swap, `MOVE_ASIDE` first renames the aside copy to a hidden
  `.pack-discard-*` sibling, then deletes it.
- Hidden entries are never packs and never repair candidates, so a failed delete leaves only inert
  residue.
- Only a crash, or a failed restore inside the swap, leaves a `.pack-old-*` copy.

### Accepted residuals

- **Cross-process.** A second JVM's listing during this JVM's swap window can restore the old copy.
  This JVM's second rename then fails, and the commit is refused unacknowledged. The result is
  consistent: the old state, as after a crash. The in-JVM lock never serialized separate processes
  (ADR-0026).
- **Relocated aside copies.** An aside copy an operator moves elsewhere is not looked for.

## Lock sharing

A JVM-wide registry, `PackWriteLocks`, keyed by the normalized entry path (`packs/<name>`), which is
the key `exclusively` already uses. `StorageRootLocations` and `CommitStorePackWriteStore` share it
without constructor changes. The alternative, a Spring bean injected into both, would let a test
that builds the two objects separately silently get two registries. ArchUnit allows a new
`infra.storage` class.

## Tests (red first)

All of them use `StorageRootLocations` over `<host>/config/packs`, and a `DirectoryRename` that moves
the live directory aside and then throws (`crashAfterAside`). Each issues a write through
`CommitStorePackWriteStore.exclusively` and `PackCommitStore.commit`, with the pre-crash revision as
`If-Match`.

1. **Plain pack, crash, then a write.** Expected: the last committed bytes plus the new artefact, no
   aside copy left. Red today: `PACK_NOT_FOUND`.
2. **Linked pack (`packs/.vendor` target), crash, then a write.** Expected: still a link to the same
   target, which holds the restored state plus the write. Red today: `PACK_NOT_FOUND`.
3. **Plain pack, crash, then a read.** Expected: the listing serves the restored pack.
4. **Two aside copies.** Expected: nothing guessed; both untouched.
5. **A stray aside copy beside a live pack.** Expected: untouched.
6. **A pack linked from outside the configuration root with an aside copy beside its target.**
   Expected: untouched, and not served.
7. **A crash in a `packs/` linked to a directory inside the configuration root.** Expected: restored,
   and the domain link intact.
8. **A read inside another pack's write lock while a half-swapped pack's lock is held.** Expected: it
   returns at once without that pack, and a later listing serves it.
9. **A read that meets a swap in flight.** Expected: it waits, then serves the new state.

Not tested: a successful commit whose aside copy cannot be deleted. The suite runs as root in the
cloud sessions, so a permission cannot make the delete fail. The hidden `.pack-discard-*` rename is
covered by review only.

Symlink tests use `SymbolicLinks.create`, which fails rather than aborts under `-PrequireSymlinks=true`.
