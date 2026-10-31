# Draft follow-up issues from #174

The pre-PR squad deferred these. They are drafts for the operator to file in
`spec-kitty/spec-kitty-charter`.

## 1. The interrupted-commit repair is unreachable for packs listed from `packs/`

`PackCommitStore.restoreInterruptedSwap` runs in `CommitStorePackWriteStore.exclusively`, after the
pack is resolved.

- **Plain pack.** A crash between the swap's two renames leaves the live directory missing.
  `StorageRootLocations` does not list a missing directory, and filters out its
  `<name>.pack-old-*` aside copy, so `resolve` answers `PACK_NOT_FOUND` before the repair runs.
- **Linked pack.** The aside copy sits beside the link's target and is named after the target, and
  the link dangles. `StorageRootLocations` refuses the dangling link. Even if the repair ran, it
  checks the link path, which exists as a link, so it returns without restoring anything.

Wanted:

- run the repair on the listing path, or before resolution, under a lock;
- for a link, restore the aside copy at the link's target, found with `Files.readSymbolicLink`;
- red-first tests for both kinds of pack.

## 2. Other walks over a pack do not enter a linked root

`WholePackStore.unmodelled` and `FileSystemPackInspector.packFiles` (validate mode) call
`Files.walk(packDir)`, which does not follow a start that is a link, so they see nothing for a
linked root. Neither is reached from a production `Locations` today.

Wanted: walk from the root's real path, as `PackFileSet.regularFiles` now does, and test a linked
root.

## 3. A `packs/` domain directory that is itself a link lists nothing, silently

`StorageRootLocations.all` returns no packs when `<config-root>/packs` is a link, and does not
warn. ADR-0038 as amended keeps this as it was.

Wanted: follow it, or warn once.

## 4. Link tests skip, rather than fail, where links cannot be made

The exactness test, `StorageRootLocationsTest` and `LinkedPackCommitTest` abort when the file system
cannot create links. On Linux CI they always run.

Wanted: a property that makes links mandatory in CI, so a CI file system without links fails the
build instead of skipping these tests.
