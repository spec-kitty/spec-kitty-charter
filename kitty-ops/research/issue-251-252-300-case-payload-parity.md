# Research: case and payload parity in the packs storage layer (#251, #252, #300)

Follow-ups of #193 (ADR-0040) and #277 (PR #298, ADR-0044). Read-only research, 2026-11-01, on
`issue-251-packs-case-payload-parity` at `92fa7dd` (origin/main). Op `01M6BRGTVYSWZZ9R0XFHVVRYD6`.

## Run shape

A `spec-kitty dispatch` Op, not a mission: three small behaviour changes in `service/packs`
(`infra.storage`), each red-first. There is no contract or API change. No operation serves validate
mode (ADR-0042 decision 7), and C-001 of #193 froze `contracts/charter/openapi.yaml`, which none of
this touches. The whole-pack gate (`:packs:wholePackRoundTrip`) is unaffected: it reads the store, and
the store's verdicts do not change.

## #251: validate mode and the store judge asset payloads differently

- **Store.** `FilesystemPackStore.withPayloads` holds the asset files the listing classifies as
  `asset-payload` (`PackFileSet.loaded`) and compares a sidecar's `path` to them by exact string. It
  reports `payload assets/<path> does not exist`, or `payload assets/<path> is already named by asset
  <id>`, on the sidecar's file. It goes through the schema-valid sidecars in `String` path order.
- **Validate mode.** `FileSystemPackInspector` walks `*.yaml` files only. It records containment
  (`containedInAssetsRoot`) and never checks that the payload is held. Markdown and binary payloads and
  templates are not listed in `files()`. ADR-0040 decision 2 records this as a known gap.
- **Fix.** Validate mode applies the store's rule as the store words it, through one shared function.
  A refused sidecar is an unreadable file carrying the store's refusal (`ParseFailure.refused`), as
  `MissionTypeChecks` already does for mission types. Its finding is `artifact_schema_error`, so no new
  `PackFindingCode` is needed. The asset facts stand, so the escape and MIME rules still judge the
  sidecar. `files()` lists every payload and template the listing classifies.
- **Pinned corpus.** `validateModeOverTheBuiltInPack…` (tag `open-packs`) pins 297 findings. The six
  built-in sidecars that the load refuses (open-packs#75, `known-failures.yaml`) become six findings,
  303 in all. The file count grows by the non-YAML payloads and templates. Both numbers are tightened
  and explained in the test, not loosened.

## #252: the single payload write has no case-collision guard

- `FilesystemPackStore.writePayload` checks containment and classification, then writes. On a
  case-insensitive file system, writing `templates/a.md` replaces `templates/A.md`.
- The whole-pack write folds case through `Companions.Inside.claim()`, but only within one write. The
  companion upsert refuses a case-only rename with `REGISTRATION_CONFLICT … differs only in case from
  <old>`.
- **Fix.** Before writing, read the pack-relative path's spelling segment by segment from the
  directory listings. A segment that the listing holds only in another case is refused as
  `REGISTRATION_CONFLICT`, `payload <file> differs only in case from <on-disk path>`. This is the
  companion upsert's code and wording for the same conflict.
- **Test.** Plant `templates/A.md`, then write `templates/a.md`. On a case-sensitive file system the
  write lands today, as a second file, so this is a real red on Linux CI.

## #300: the template_path file check and the id resolution disagree on case

- `FilesystemPackStore.namedFileProblem` asks the file system (`Files.exists`, `isRegularFile`) by
  name. On a case-insensitive file system, `templates/Spec.md` passes against an on-disk `spec.md`.
  The resolution (`MissionType`, `Set.contains` on ids from walked names) is exact, so the load then
  refuses the path. ADR-0044's #277 amendment records the gap.
- **Fix (recommended in the issue: compare the exact on-disk name).** The file check reads the
  spelling from the listings with the same shared function as #252. A case-only difference is
  refused as `differs only in case from <on-disk path>`. Because the decision comes from listed names
  and never from opening a name, it is the same on case-sensitive and case-insensitive file systems.
  That is why a Linux test covers it. The function picks the entry a case-insensitive file system
  would open: the exact name if listed, otherwise the one entry whose name folds equal.
- The check compares each link's own name, never its real path, so an in-pack symlink
  `spec-template.md -> real.md` stays a template (`MissionTypeBindingTest`).
- The fix covers all three callers of `missingNamedFile`: the load, the per-file write and validate
  mode (`MissionTypeChecks`).
- **#301 (queued).** The template-read failure is the same disagreement, caused by a read instead of
  case. The listing-based spelling function is not a read seam, and #301 needs its own.

## Docs to amend in place

- ADR-0040: decision 2 (validate mode payload gap), decision 4 (#252), and consequences.
- ADR-0044: a 2026-11-01 amendment of the #277 amendment's named-file check (#300 gap).

## Folded from the pre-PR review squad

- **Payloads the store does not hold.** The store holds only the asset files its read holds; a payload
  link that leaves the pack, or a file that cannot be read, is refused on its own file and names no
  payload. Validate mode reads asset files through the same function (`readPayload`), so it refuses
  the same files and sidecars. Template read failures stay with #301.
- **A path absent in every case is `missing` without opening it.** The spelling is three-way
  (exact, other case, absent), and the other-case lookup walks the whole path, so a path is reported
  as respelled only when a case-insensitive file system would open a file for it. Names that the
  `Locale.ROOT` fold leaves apart (Unicode normalization, say) count as different names, so they are
  `missing` on every file system.
- **Store tests plant two spellings.** On a case-insensitive file system they are one file, so those
  tests assume a case-sensitive one. The verdict tests (`SpellingsTest`, `MissionTypeBindingTest`) run
  on both.
