# Research: linked-pack hardening (#284, #285, #286, #287)

Follow-ups of #174 (PR #280, ADR-0038 as amended 2026-10-31). Read-only research, 2026-10-31,
on `issue-174-symlinked-pack-root-clean` at `cff1044e`. The operator's #174 rulings bind; this note
does not reopen them.

## Run shape

A `spec-kitty dispatch` Op, not a mission: four small, independent hardening changes in one module
(`service/packs`, `infra.storage`), one CI workflow line and one ADR amendment. No contract, API or
domain-model change. The one design choice (#285) goes to the operator before it is built.

## Walkers over a pack directory (production code)

| Walker | Start | Linked root today |
| --- | --- | --- |
| `PackFileSet.regularFiles` | `directory.toRealPath()` (#174) | entered |
| `WholePackStore.unmodelled` | `Files.walk(packDir)` | **not entered**: lists nothing (#284) |
| `FileSystemPackInspector.packFiles` (validate mode, `*.yaml` and `*.md`) | `Files.walk(directory)` | **not entered**: lists nothing (#284) |
| `PackCommitStore` copy / delete walks | the real live directory (#174) | entered |
| `StorageRootLocations.all` | `Files.list(packs)`, guarded by `isDirectory(packs, NOFOLLOW_LINKS)` | `packs/` as a link lists nothing (#285) |

`Files.walk` without `FOLLOW_LINKS` reports a start that is a link as one non-directory entry and
does not descend. Both #284 walkers relativize against the configured path, so the fix walks the
real root and maps each path back under the configured one (`directory.resolve(real.relativize(p))`
for the inspector, whose callers relativize against `directory`; `real.relativize(p)` for
`unmodelled`). Links below the root stay unfollowed: neither walk passes `FOLLOW_LINKS`.

Reach: `WholePackStore.load` is the whole-pack gate's loader; `FileSystemPackInspector.inspect`
(full scope) is validate mode. Neither is driven from `StorageRootLocations` today, so the tests
link the root directly (a `packs/<name>` link to a pack copied elsewhere).

## Save paths (`DomainRepositoryService`)

Every write resolves the pack with `resolve(pack)` and writes in place through a
`FilesystemPackStore` rooted at the configured directory's parent:

- `save(Tactic|Directive|Styleguide|Toolguide|Paradigm)` — public domain-port methods
  (`TacticRepository.save` and siblings in `packs.domain`), via `saveArtifact`;
- `save(AgentProfile)` — public, no port;
- `save(PackIdentifier, PackDescriptor)` — `PackDescriptorRepository.save`, public port;
- `saveArtifact(PackIdentifier, CharterArtifact)`, `writeAssetFile`, `writePreset`,
  `writePackDocument` — package-private, the whole-pack gate's seam.

None checks `Locations.whyReadOnly`; only `PackCommitStore.commit` does (`PACK_UNREADABLE`,
`"pack <id> " + why`). No production code calls any of these saves (grep of `src/main`): registration
commits through `PackCommitStore`.

### #287 recommendation: check, do not narrow

Narrowing is not clean: six of the saves implement public domain ports (`*Repository.save`) that
live in `packs.domain` and are visible to every slice. Narrowing them means removing `save` from the
ports, a domain-port change across the read slices and their tests, for a guard. The check is one
private `writable(pack)` that every write path calls instead of `resolve(pack)`: resolve, take the
directory's real path, ask `locations.whyReadOnly(real)`, refuse with the commit store's exact
message. Reads keep `resolve`. Chosen: **the check**.

## `Locations` implementations

- `StorageRootLocations` — the only production one (`StorageConfiguration`), overrides
  `whyReadOnly` (real path outside the real config root → read-only).
- `List::of` — `StorageConfiguration` when no root is configured.
- Test lambdas (`() -> List.of(...)`) — the default `whyReadOnly` (always writable).

## #285: `<config-root>/packs` that is itself a link

Today: `Files.isDirectory(packs, NOFOLLOW_LINKS)` is false for a link, so `all()` returns `List.of()`
with no log line. The service serves no packs and says nothing.

Option A — follow it, under the #174 rules:

- list the link's target; `whyReadOnly` already compares each pack's real path with the real config
  root, so a `packs/` linked from outside makes every pack read-only, and one linked to a directory
  inside the config root keeps its packs writable (the pre-PR squad added: not one in
  `activations/`, which is refused);
- the overlap refusal is unchanged (it compares the candidates' real paths);
- `whyNotAPack` must take the config root from `packs.getParent().toRealPath()`, not from the real
  domain's parent, which is the target's parent once `packs/` is a link;
- new refusal: a `packs/` that resolves to the config root or a directory above it (it would list
  `activations/` and itself as packs), or to no directory, lists nothing and warns once;
- cost: about 15 lines in `StorageRootLocations`, four tests, an ADR line replacing
  "Unchanged: `packs` itself is still not followed".

Option B — warn once: keep listing nothing, log
`"pack domain <path> is a link; it is not read"` once (and again after it was fixed and breaks).
About 5 lines and one test.

**Recommendation: A.** It is the #174 ruling applied one level up: the operator accepted that a link
names packs that live elsewhere, read-only when they live outside the root. Linking a whole
`packs/` to a dotfiles or shared checkout is the same use, and A serves it with no new rule beyond
refusing a `packs/` that holds the config root. B keeps a silent-looking empty service (only a log
line explains it) for a layout the operator has already accepted for single packs.

## Link tests that skip (#286)

| Suite | How it skips |
| --- | --- |
| `PackFileSetExactnessTest` | `assumeTrue(symlinksWork(home))` in four tests |
| `StorageRootLocationsTest` | `link(..)` → `abort(..)` |
| `LinkedPackCommitTest` | `linkTo(..)` → `abort(..)` |
| `AssetStoreTest` | `assumeTrue(link(..))` |
| `TemplateStoreTest` | `assumeTrue(AssetStoreTest.link(..))` |

Other link tests (`FileSystemPackInspectorTest`, `CompanionFilesTest`, `PackWriteSafetyTest`,
`FileSystemPackSourcesTest`) call `Files.createSymbolicLink` directly: they already fail, not skip.

Plan: one test helper, `SymbolicLinks`, that every skipping suite uses. It creates a link, and when
it cannot: aborts (skip) by default, fails when the test JVM's `charter.requireSymlinks` is `true`.
`service/packs/build.gradle.kts` passes the Gradle property `requireSymlinks` to `:packs:test` as
that system property (a declared task input). CI passes `-PrequireSymlinks=true` in
`service.yml`'s Verify step. A guard test fails when `GITHUB_ACTIONS=true` and the property is not
set, so the CI wiring cannot silently drop off.

CI groups: `ci.yml`'s `service_re` includes `.github/workflows/service.yml`, so the change runs the
Service group (`CI gate / gate` requires it). `whole-pack.yml` runs only the `whole-pack` tag, which
has no link test; it is not changed.

## Interactions

- #280 must merge first; this branch stacks on it.
- #131 touches contracts only. The #235/#236 session touches the DRG in `infra.storage`: likely
  near `FilesystemPackStore`/`WholePackStore`; whoever lands second rebases.
- Out of scope: #281 (crash repair), #184.
