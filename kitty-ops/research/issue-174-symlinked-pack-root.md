# Research: a pack root configured through a symlink loads as an empty pack (#174)

Read-only research before the fix, 2026-10-31, against origin/main `8451da66`.

## The defect, confirmed

`PackFileSet.regularFiles` walks with `Files.walkFileTree(directory, visitor)` and no
`FOLLOW_LINKS`. When `directory` is a symbolic link to a directory, the walk reports the link
itself to `visitFile`, and `Files.isRegularFile` is false for it. The listing is therefore empty,
so the load and the digest both skip every kind file, asset file, template, preset, `pack.md`
and retired file. `pack.yaml`, the root graph shards (`Files.list`, which follows the start) and
`drg/fragment.yaml` (`resolve`) are still read and digested.

The load (`listing`/`files`, through `loaded`) and the fingerprint (`digested`) share that one
walk. Fixing `regularFiles` fixes both. `FileSystemPackSources.fingerprint()` reads each digested
file through the lexical root (`root.resolve(relative)`), and that works through the link.

## Where pack roots are resolved

Every resolver is lexical (`toAbsolutePath().normalize()`), with no `toRealPath`:

- `StorageRootLocations` (the only production `Locations`);
- `FileSystemPackSources.digest`;
- `DomainRepositoryService.over`;
- `PackCommitStore.resolve`;
- `CommitStorePackWriteStore.normalized`;
- `FilesystemPackStore.directoryOf`;
- `PackFileSet.normalized`.

`CharterWiring.derivedWorktreeId` uses `toRealPath`, but only for the config root.

## The issue's premise is out of date

- `charter.packs[].path` is retired: `StorageConfiguration` fails startup when it is set.
- Packs come from `StorageRootLocations(<config-root>/packs)`, which never lists a symbolic link,
  by design. Its javadoc: "a commit stages a copy of the pack and swaps it into place, and on a
  link that would replace the link with a copy holding only the written file."
- So in the deployed service, a symlinked pack never reaches `PackFileSet`. The empty walk is hit
  by any caller that builds a `PackLocation` (or a validate-mode `PackRoot`) whose root is a link.
- The commit hazard the javadoc names is real. `PackCommitStore.copyTree` walks without
  `FOLLOW_LINKS`, so a linked root would be staged as a single entry and the swap would replace
  the link.

The operator was asked whether to also admit linked packs in `StorageRootLocations` (and make
commits safe through a link). Until they answer, the run does option A: fix the walk only, at
depth 0.

## Other walks that break the same way, left alone

These are outside the load and fingerprint path, and none of them is reached from a production
`Locations`:

- `FileSystemPackInspector.packFiles` (validate mode, `Files.walk`);
- `WholePackStore.unmodelled` (`Files.walk`);
- `PackCommitStore.copyTree` (`walkFileTree`).

## The fix, and why it is safe

In `regularFiles`, walk `directory.toRealPath()` and relativize against that same real root. The
classification prefix (`nameOf(directory)`, the configured directory's name) and the refusal's
pack name are unchanged.

- Links below the root are still not followed: no `FOLLOW_LINKS`.
- `FileCollector`'s `root.relativize(file)` and `file.equals(root)` use the walked root.
- Nothing compares a real path with a link path:
  - `Companions` and `namedFileProblem` work on relative strings, or real-to-real;
  - `realPathContained` is real-to-real.
- A root that does not exist: `toRealPath` raises `NoSuchFileException`, which is refused as
  `PACK_UNREADABLE`, as the walk refused it before.

## The exactness test

`PackFileSetExactnessTest`, over `GeneratedPackTree`, observes the load through
`FilesystemPackStore.read`, `DomainRepositoryService.tacticsOf` and `load`, and the digest through
`FileSystemPackSources.fingerprint()`. A pack's configured root is today either the directory or
`<dir>/.`. The symlinked-root row adds a third form: `<home>/<directoryName>` is a link to the real
directory, which lives elsewhere under a different name. The exhaustive tree plants it in one pack,
and the test asserts that every root form was planted.

## Interactions

- PR #275 (#194 PR-B): its commits are on main (`9370fc8e`..`8451da66`). At the PR's head,
  `PackFileSet` is identical to main, so there is no conflict with `markdownHolder`.
- #184 (single-source pack id -> directory): this fix does not touch that resolution, so it is not
  folded in.
- #131 touches contracts only. The #235/#236 session touches packs infra.storage; whoever lands
  second rebases.
