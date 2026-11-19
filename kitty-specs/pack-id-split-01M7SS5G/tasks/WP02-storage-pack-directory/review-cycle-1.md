---
affected_files: []
cycle_number: 1
mission_slug: pack-id-split-01M7SS5G
reproduction_command:
reviewed_at: '2026-11-19T13:02:08Z'
reviewer_agent: claude::generic-reviewer
wp_id: WP02
---

# WP02 independent review: changes requested (one finding)

Reviewed lane-b 59b996f. `./gradlew :packs:check` is green (run by the reviewer).

## Finding (MEDIUM, bulk_edit occurrence-map adherence)

`DomainRoundTrip.java:186` still builds `new PackIdentifier(name)` from the round-trip source directory's name. T007 and
`occurrence_map.yaml` (code_symbols) say this site becomes `PackDirectory`, and the census (line 79) classes it OTHER.
The WP02 DoD grep therefore shows a site that is not on the registered-id list.

The code is arguably right: `name` is the id the round-trip configures and loads the pack under (`service(name, ...)`,
`load(from, id)`), so it is a registered id, the D2b default of the directory name. `PackDirectory` would be the wrong
type. Then the artifacts and the code must agree, and the site needs the same guard as `StoreRead`:

1. Reclassify DomainRoundTrip:186 as REG (D2b default: directory name as the registered id) in `research/census.md`
   and in `occurrence_map.yaml`, and correct T007's list.
2. Do not build a `PackIdentifier` from a name that is not a pack id. Guard it with `PackIdentifier.isPackId` as
   `StoreRead.registeredIdOf` does (refuse through the existing unreadable path), with `TODO(#583 WP03)` so WP03
   switches it to `pack_id_invalid`. Add an error-path test with a source directory whose name is not a pack id.

## Checked and fine

- `PackDirectory`: one segment; blank, `/`, `\`, `.` and `..` refused; `of(PackIdentifier)` and `of(Path)`; tested.
- `FilesystemPackStore` is keyed by `PackDirectory`, and `directoryOf` keeps its under-the-root check.
- The PackCommitStore staging/temp sites use `PackDirectory`; :622/:711 keep the registered id. `revisionOf` takes a label string.
- `StoreRead.of`, `ArtifactFiles.of`, `PackManifestDerivation.derivedAt` and `loadStored`/`salvageStored` take the
  caller's registered id. The descriptor wraps at StoreRead:113 and ArtifactFiles:42 are gone; the remaining fallback is TODO(WP03).
- D6: `loadStaged` uses `held.id()`, which comes from `PackRoot(upload packId)` through `ofNamed`: the upload's id.
- Test changes assert the registered id AND keep the descriptor ULID assertion; no field was dropped.

Nit (no action): the CommitStorePackWriteStore:339 and DomainRepositoryService:348 `location.name()` sites stay REG as the census says.
