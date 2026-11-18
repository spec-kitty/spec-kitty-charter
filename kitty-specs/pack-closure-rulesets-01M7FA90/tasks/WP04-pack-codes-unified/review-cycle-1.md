---
affected_files: []
cycle_number: 1
mission_slug: pack-closure-rulesets-01M7FA90
reproduction_command:
reviewed_at: '2026-11-17T00:36:09Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP04
---

# WP04 review, cycle 1: changes requested

Reviewer: `claude::reviewer-renata` (independent). `:packs:test` is green on 6f7050ff4. The golden diff is fully explained, the codes are typed, each token is built once, and no ArchUnit rule is widened. One MAJOR finding blocks approval.

## Must fix

1. **M1. Validate mode moves a Markdown companion's parse failure to a third code, and nothing records it.**
   - Where: `FileSystemPackInspector.java:698-711` (`companion`) builds a `ParseFailure` with no code. `Wording.parseCodeFor` (`Wording.java:94,197`) therefore answers `unreadable` with `<x>.md: …`. Before WP04, validate mode answered `artifact_schema_error`.
   - Why it is wrong: registration answers `companion` (`FilesystemPackStore.java:909`), and R2 row 19 keeps `companion`.
   - Fix (option (a)): the inspector gives a companion's parse failure the companion code, via `ParseFailure`'s code, so validate mode answers `companion` as registration does (R2: one fault, one code).
   - Then:
     - Record any remaining file or pointer difference under row 19 in the R2 note.
     - Add the change to the validate-mode `### Breaking` fragment.
     - Pin code and detail in a test (`FileSystemPackInspectorTest` or a `PackPolicy.judge` test).
   - A domain class may not name `StorageCodes`. If the inspector cannot reach it, use the staging `PackFindingCode` twin, guarded as in item 2.

## Fold (cheap, in this cycle)

2. **MINOR-1. Guard the staging twins.** In `StorageCodesTest`, assert that each non-placement `PackFindingCode` constant equals its `StorageCodes` twin (token and severity), until WP13 deletes the view.
3. **MINOR-2. One duplicate-id wording.** `PackPolicy.java:559` and `DomainRepositoryService.java:1277` each build it inline. Move it into one static function and have both call it.
4. **MINOR-5. Test row 28 in validate mode.** Add a test that validate mode answers `reserved_shard`, in registration's words, for an unstamped `registered.graph.yaml`.
5. **MINOR-6. Record the out-of-map edits in a commit body.** Put the list from your report in the body of this cycle's commit (or a short addendum in the R2 note's WP04 outcomes). The orchestrator holds the DIV-023 landing item; no action is needed from you on that.

## Carried, not for this cycle

- **MINOR-3** (descriptor faults joined with `"; "` into one registration violation, `FilesystemPackStore.java:1450`) goes to WP12.
- **MINOR-4** (`WholePackStore.java:720,730` re-reads `pack.yaml` to choose the pointer) goes to WP12. It falls under the one read.
- **The NITs** (`violationOf` still takes a `String`; the `Wording.REASON` magic key; the c16 detail prefix) are optional. Fold them only if trivial.

## Exit

- `:packs:test` and `:pack-validation:test` are green.
- If the golden changes, regenerate it and explain the new rows.
- Commits are signed, end `(#554)`, and carry no trailer.
