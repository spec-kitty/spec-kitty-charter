---
affected_files: []
cycle_number: 1
mission_slug: slice-b-real-packs-register-01M7649C
reproduction_command:
reviewed_at: '2026-11-11T23:22:13Z'
reviewer_agent: claude-reviewer
wp_id: WP03
---

# WP03 review, cycle 1 (Reviewer Renata)

Scope: `0f0d3863`, `6bb62596`, `3612c104` on `kitty/mission-slice-b-real-packs-register-01M7649C-lane-b`
(the WP02 tip `8a55b325` came in through the merge `2c40d103`; the kitty-specs/status files in
`8a55b325..3612c104` are from that merge, not WP03).

## Verdict: REJECT

One HIGH: the Java style gate (Spotless, part of `:packs:check` and `verifyAll`) fails at every WP03 commit.
WP02's merge `2c40d103` passes it. Everything else is correct or acceptable. Only LOW findings remain, and
none of them blocks approval once the HIGH is fixed.

## What I ran

All runs were in a throwaway detached worktree, one Gradle run at a time, `--max-workers=1`, JDK temurin-25.

| Commit | Run | Result |
|---|---|---|
| `0f0d3863` | `:packs:compileJava :packs:compileTestJava`, `:packs:test` | green (4676 tests, 0 failed) |
| `6bb62596` | `:packs:test` | green (4685, 0 failed) |
| `3612c104` | `:packs:test :contracts:check :deployable:test --tests '*CharterContractSuite*'` | green (packs 4700; deployable 714, 0 failed; the 4 new scenarios ran and passed) |
| `3612c104` | `:packs:wholePackRoundTrip` | green. Report: 87 files, 85 green, 1 expected red, **1 excluded** (`missions/software-dev/templates/README.md`), 0 red |
| `3612c104` | `:packs:check --continue -x spotlessJavaCheck` | green (Checkstyle, javadoc, Error Prone, NullAway) |
| `2c40d103` (base) | `:packs:spotlessJavaCheck` | green |
| `0f0d3863`, `6bb62596`, `3612c104` | `:packs:spotlessJavaCheck` | **FAILED** (see H1) |
| changed `.md` files | markdownlint-cli2 | 0 errors. The cspell hits in the `.feature` file are on pre-existing lines only |

## Findings

### H1 (HIGH): Spotless format violations at every WP03 commit; `:packs:check` / `verifyAll` is red

Evidence: `./gradlew :packs:spotlessJavaCheck`:
- `0f0d3863`: `PackFileKindTest.java`, `RetiredFilesTest.java`
- `6bb62596`: adds `WholePackStore.java` (e.g. the 8-argument `new LoadedPack(...)` on one line at about :343, and the
  `unreportedFiles(...)` call at about :375), `PackFindingTest.java`, `WholePackStoreTest.java`
- `3612c104`: adds `FileSystemPackInspector.java:365-367` (comment lines over the limit) and `:729-731` (the lambda wrap),
  `CompanionFilesTest`, `ExamplePacksTest`, `ExcludedFilesAgreementTest`, `FileSystemPackInspectorTest`,
  `WholePackExpectationTest`

The charter's "Gates before merge" makes the Java style check part of `verifyAll`. Per-commit greenness was required.

Fix: run `./gradlew --max-workers=1 :packs:spotlessApply` and fold the formatting into each commit it belongs to
(rebase with fixups, so each of the three commits passes `:packs:spotlessJavaCheck` alone). Then re-run
`:packs:check` on the tip. In `FileSystemPackInspector.java:365-368`, rewrap the comment by hand rather than keeping
the broken lines spotless produces ("YAML / is, / ... is / held").

### L1 (LOW): `LoadedPack.excluded` javadoc is narrower than the behaviour

`WholePackStore.java:115`: "every file of the pack that an exclusion names". The list also holds the Markdown that
only the `ignored` fallback takes, and `file-kinds.yaml` says the fallback "is a fallback and not an exclusion".
Fix: "every file the table excludes (an `exclusions` rule names it, or only the `ignored` fallback takes it)".

### L2 (LOW): the restated listing rule in `FileSystemPackInspectorTest` is narrower than the implementation

`FileSystemPackInspectorTest.java:778` adds only `file.endsWith("/README.md")`. The inspector lists *all* excluded
Markdown. The test still fails safe (an extra listed file breaks it), but the comment says "restated from the file
layout alone". Fix: restate it as "every `.md` (any case) the table does not map", or say in the comment that the
built-in example holds only README Markdown below the root.

### L3 (LOW): the gate proves the table exclusion on a canonical pack, not the fallback

The gate (`:packs:wholePackRoundTrip`) runs over `built-in` only, so `org-base/docs/authoring-notes.md` is proven by
`ExamplePacksTest` and the example round trip, not by the gate. The commit message ("so the gate and the example
round trips prove the rules") holds only jointly. The gate's EXCLUDED check also looks at `store.write`, which never
wrote unmodelled files, and not at the registration's strip plus `copyTree`. The strip path is proven by
`PackRegistrationServiceTest`, `ExcludedFilesAgreementTest` and the scenarios, so nothing is unproven.
No change required. Optional: put a fallback-excluded note in `built-in` too, so the gate shows an
`excluded:**/*.md` row.

### Deviation rulings

1. **`Diagnostic` has only `code` and `detail`, so the path goes in `detail`. Accepted (LOW, no change).**
   FR-021 asks for a diagnostic "naming the file and the rule's reason", and the detail
   `<file>: not held (<reason>)` does that. `Diagnostic.yaml` describes `detail` as the place that names
   pack-relative paths, and the existing codes (`prompt_slot_not_empty`, `pack_level_missing`) carry their path the
   same way. A `pointer` belongs on `RefusalViolation`, not on this closed object. Adding one would be a shape change
   that the plan and data-model never called for. The WP prompt's "pointer = the file" was written against a field
   that does not exist. Record this in the WP history. If core needs a machine-readable path, the canonical-shape
   fix is a `file` field on `Diagnostic` for every code, in its own change.
2. **The 7-argument `LoadedPack` constructor. Accepted.** It is one more step in the record's existing chain of
   convenience constructors (`WholePackStore.java:177-225`: the 6, 5 and 4 argument forms already exist). It reads
   no old shape and means "excludes no file", which is canonical. No main code uses it to drop `excluded`: the only
   main sites are the 8-argument load (:343) and `withNotHeld` (:164). It is not a legacy reader in the charter's
   sense.
3. **Validate mode digests lazily on a second walk. Correct.** `FileSystemPackInspector.java:362-377`.
   `PackFileSet.companions` returns no companions for unreadable YAML and does not throw (`PackFileSet.java:342-354`),
   so validate mode cannot crash on a broken pack. The held set is the digest's, which agrees with the load and the
   census. `ExcludedFilesAgreementTest` pins this across classification, the claim check, validate mode, the load,
   the digest, the census and `loadStaged` plus the strip (fold A9).
4. **A broken skill YAML lists its body as excluded. Accepted (LOW).** On a refused registration, `loadStaged`
   throws before `withNotHeld` (`WholePackStore.java:384-392`), so the HTTP answer carries only the YAML's
   violation. This is better than before, when the body was also an `unmodelled` violation (one fault, one
   violation). Only validate mode lists the body as excluded beside the YAML finding, and the finding is the
   actionable item.
5. **`removeNotHeld`. Safe.** `WholePackStore.java:477-498`:
   - It acts only on the service's own `FILE_NOT_HELD` findings. Each path is resolved and normalised under the
     staged root; it skips anything outside the root or equal to it, and anything that is not a regular file
     (`NOFOLLOW_LINKS`).
   - The listed paths come from a walk that does not follow links below the root.
   - `removeEmptyParents` stops at the root.
   - Configured sources are copied into staging by `LocalSourceReader`, git and HTTPS fetchers, so a configured
     source is never touched. The live directory is never touched either: the strip runs before `exclusively(...)`
     and the commit.
   - It runs before `store.revisionOf(staged)` (`PackRegistrationService.java:260-263`), the only `revisionOf` on
     the write path. `aRegistrationThatDiffersOnlyInAnExcludedFileIsUnchangedAndStillReportsIt` proves the
     re-send ends UNCHANGED with the same revision and no swap, and so does the first new scenario.
6. **No separate red-first run for the unit and store tests. Accepted, with a process note (LOW).** The new tests
   assert outcomes, and every one would fail against the base: an excluded file would be refused and listed as
   unmodelled. The charter asks for test-first; next time, note the red run in the WP history.

### Spec fidelity: holds

- **FR-004.** The fallback is consulted last, after WP02's case variant (`PackFileKind.java:337-345`), and matches
  case-insensitively. It stays out of `classifyFolded`, so a companion claim wins (skill bodies, `NOTES.MD` in
  `CompanionFilesTest`). It stays out of `matchingRules` and the overlap check. It is not digested.
- **FR-005.** Every table exclusion is now not refused: READMEs, `plan-field-declaration.yaml` and dot-files under
  `assets/` and `templates/`.
- **FR-021.** One `file_not_held` warning per file, in path order, with the rule's reason. Stored pack = staged tree
  less those files.
- **FR-022 / SC-002.** `pack.md` and `PROVENANCE.md` give exactly two `retired` violations and no `unmodelled`; the
  duplicate is removed in `unreportedFiles`. `notes.txt` stays `unmodelled`.

### Brownfield integrity: holds

Every flipped test changes meaning on purpose, and each adds a positive assertion of the new class:
- `PackDocumentRoundTripTest`, `WholePackStoreTest` and `MarkdownFilesRuleTest` now assert the excluded class.
- `PackFileKindTest` moves its unknown `.md` samples to `.txt` and adds the fallback, the invariant and the
  `matchingRules` tests.
- `WholePackExpectationTest` keeps the `other:` case through `notes.txt` and adds the EXCLUDED, EXCLUDED_WRITTEN,
  never-checked and stale-baseline cases.
- `WholePackRoundTripTest` replaces the planted README with the canonical fixture and keeps a non-vacuous check.

Nothing is excluded from a parity or round-trip check. `expected-red.yaml` and the gate's `exclusions.yaml` are
untouched.

### Contract: holds

- `DiagnosticCode` gains `file_not_held`, and its Java twin `Diagnostic.Code.FILE_NOT_HELD` and the vocabulary test
  pass.
- The fragment is a single `### Changed` block that states core's seam. This follows the precedent for raised and
  added codes; the change relaxes a 422 to 2xx.
- `PackRegistration.yaml` and `packs_packId.yaml` text is updated.
- The four scenarios are untagged and pass in `CharterContractSuite`; no new steps were needed.
- `:contracts:check` is green.

### Code quality and commits

- Onion and ArchUnit: the port method sits on `WholePackWriteStore.Loader` (application) and the implementation in
  infra. ArchUnit runs inside `:packs:test`, which is green. Error Prone and NullAway are green.
- Naming: "excluded" is the term used, and an `ignored` rule yields an `Excluded` classification.
- Commits are signed (`gpgsig` present), have no trailers, and carry no model identifiers in the messages or the diff.
  The author is "Claude <noreply@anthropic.com>", as on WP02's approved commits.
