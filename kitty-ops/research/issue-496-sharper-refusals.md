# Op 01M7643KRYB76ZK2QV7HDGHGDN: sharper refusals (#496)

Scope: #382, #254, #121. #381 is held for slice B (#466), which retires `org-charter.schema.json` and closes #381 as
not planned (operator ruling, 2026-11-11).

## Why an Op, not a Mission

Three small, independent refusals, each a few lines of production code behind a red-first test. None adds a wire
code, a schema or an operation, so there is no spec or work-package split to make. The two contract-visible changes
(a detail wording and a new graph fault under an existing code) still get the squad at the plan point and before
merge, as charter.md asks of a contract change.

## Grounding

- Load path, asset sidecars: `SidecarPayloads.refused` (infra/storage) is the one rule the load
  (`FilesystemPackStore.withPayloads`) and validate mode (`FileSystemPackInspector.refuseSidecarsWithoutPayload`) apply.
  It refuses every path not in the held set with `payload assets/<path> does not exist`, an escape included.
- Validate mode, assets: `PackPolicy.escapes` (absolute in POSIX or Windows form, a `..` part, or not contained after
  the adapter resolves links) reports `ASSET_PATH_ESCAPE`.
- Fragment `body_path`: read by `GraphDocumentReader` (validate) and `FilesystemPackStore.readGraphs` (load,
  composition, registration); no rule judges it. Graph faults surface as `drg_schema_error` violations at
  registration (`WholePackStore.graphViolations`) and as `pack_unreadable` on a composing read.
- Worktree token: `GuardWiring.configuredToken` reads the resolved `charter.worktree-token`; a placeholder in the
  environment value (`CHARTER_WORKTREE_TOKEN='${x}'`) resolves from argv or a system property and is accepted.
- The `service/packs/src/test/resources/planted/` corpus has had no reader since `PlantedFilesTest` was deleted
  (2bb3f762, 2026-10-21). The invalid packs here therefore live in the tests that use them: contract scenarios and
  unit tests that write the pack, in the canonical shape apart from the one fault.

## Decisions

1. **One path-escape rule in the domain.** `PackPaths.leavesRoot(String)` (packs domain): absolute in either flavour
   or a `..` part, the lexical half of `PackPolicy.escapes`, which now calls it. The asset rule, the sidecar rule and
   the fragment rule share it.
2. **#382.** `SidecarPayloads.refused` checks escape before existence. Reason:
   `asset path '<path>' escapes the pack's assets/ root`, the wording of `ASSET_PATH_ESCAPE`. A missing payload under
   `assets/` (a link the store does not hold included) still reads `payload assets/<path> does not exist`. Load and
   validate mode keep one wording. ADR-0040 decision 6 carries a dated amendment (squad, HIGH). The codes stay: the
   offering answers `500 pack_unreadable`, a registration lists the sidecar's violation.
3. **One fault, one violation at registration.** `WholePackStore.unreportedFiles` listed a refused sidecar a second
   time as `unmodelled`; it now skips every file that already has a violation, as its own Javadoc says.
4. **#254.** No new finding code (squad, MEDIUM): the fault is `DRG_SCHEMA_ERROR` in both modes, emitted by
   `PackPolicy`'s graph build at `[nodes, i]` in validate mode and by `FilesystemPackStore.readGraphs` on the load,
   both in `PackPolicy.bodyPathEscape`'s words: `node <id> body_path '<path>' escapes the pack root`. A registration
   answers `422 validation_failed` with a `drg_schema_error` violation at `drg/fragment.yaml`; a composing read
   answers `500 pack_unreadable`, `a graph document of <pack> cannot be read` (it names no file for a graph fault).
   `CharterRefusalCode.yaml`'s `drg_schema_error` description gains one clause naming the rule (coordinated with the
   orchestrator; slice B edits that file too).
5. **#121.** Refuse the start when the raw value of the source that supplies `charter.worktree-token` contains `${`.
   The supplier is found through Boot's `ConfigurationPropertySources`, in Boot's precedence and under its relaxed
   names (squad, HIGH: `CHARTER_WORKTREETOKEN` binds the property too; a configtree value is a `CharSequence`). The
   message names the property, never the value. Refused legitimate forms: an env-to-env indirection and a literal
   `${`, both acceptable. Not a contract change.

## Tests (red first)

- #382: `FileSystemPackInspectorTest` (load and validate, a file at the escaping target, `sub/../a.md`, an absolute
  path; a link that leaves the pack is still "does not exist"), `AssetStoreTest`, the SHARING-004 scenario, and a
  registration scenario. All were red on the old wording.
- #254: `PackPolicyGraphTest` (POSIX absolute, `..`, Windows drive, UNC; an inside path binds), a registration scenario
  and an offering scenario. Red before the rule.
- #121: `GuardWiringTest`: a placeholder resolved from argv, a system property, JSON on argv, with a default, under a
  relaxed env spelling, and in a mounted token file; a mounted token file is accepted. Red before the fix.

## Contract

- Change fragments `2026-11-11-sidecar-path-escape.md` and `2026-11-11-fragment-body-path-escape.md` (`### Changed`).
- Examples `PackRegistrationStatus.asset-path-escape` and `.fragment-body-path-escape`, listed in
  `examples/_index.yaml` and the pack-registration status path.

## Not done

- A sidecar or `body_path` that escapes only through a symbolic link is judged by the adapter's containment check in
  validate mode for assets; for `body_path` the rule is lexical only (core's semantics also resolve links).
- The dead `planted/` corpus: reported to the orchestrator, not filed (Rule 1).
- `CommitStorePackWriteStore.authored` skips graph documents it cannot read (squad, MEDIUM). Both removals that read
  it run the impact check next, which refuses a pack with an unreadable graph document `pack_unreadable`, so no
  removal passes; making `authored` fail closed itself is left as is.
- Validate mode reports an escaping `body_path` at the node and keeps the fragment's other nodes, while the load
  refuses the whole fragment, so the step-enhancement edge rules may be judged in one mode and not the other for a pack
  with both faults.
- Validate mode reports an escaping sidecar twice: the store's refusal and `ASSET_PATH_ESCAPE`, as before.
