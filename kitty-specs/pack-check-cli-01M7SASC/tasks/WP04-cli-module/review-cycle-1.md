---
affected_files: []
cycle_number: 1
mission_slug: pack-check-cli-01M7SASC
reproduction_command:
reviewed_at: '2026-11-19T18:29:50Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP04
---

# WP04 review, cycle 1 (claude::reviewer-renata): changes requested

Reviewed `git diff 15263e41e..HEAD` on lane-d (d079552b8, 826a167cb, 3f4a45f4d, a07c8a0a2), plus the base merge.
Two MEDIUM findings block approval. Everything else holds; the LOW items are listed for you to fold in.

## What holds (no change needed)

- **Merge 15263e41e.** Recomputing the merge (`git merge-tree` of db3a9683d with 1cb7115c2, then with 6741fa298)
  gives a tree identical to 15263e41e. WP01's hunks are there: the `unlinkedFile` guards in `readDirectory`, the
  census over `walk.regular()`, and `StoreRead.linked` on the aggregate. WP02/WP03's `read(root)` builds its readout
  from `readWithStore`'s one read.
- **Ruling A.** `@Bean DomainRepositoryService` returns the concrete type, so Spring still resolves every port by
  interface. The bean name is unchanged, nothing uses a qualifier, and `WiringRoundTripTest` asserts there is exactly
  one bean. `missingLiveDirectory`, `asidesOf` and `MAX_LINK_HOPS` moved verbatim, so behaviour is preserved.
  `:deployable:test` passes in full.
- **Problems against RefusalHandler.** The title rule, the detail with `leftOut`, the caps (100 violations, 100
  packs), the `RefusalViolation` and `RefusalPack` fields, and the `getPackRegistration` statuses all match, for
  every code the CLI can emit:
  - `validation_failed` 422, `pack_unreadable` 500 and `registration_conflict` 409;
  - the 8 `CLOSURE` codes, 422.

  The handler's other 422 overrides (`pack_reference_*`, `prompt_slot_missing`, `profile_enhancement_drops_field`)
  cannot be emitted by the CLI. The one intended difference is `instance`.
- **ArchUnit.** The exemption is narrow: it names the main by its fully qualified name and allows only the two
  adapters, and it fails if the main is missing. The badfixture control bites in all three rules, and the transitive
  half walks from the main with no exemption.
- **Signatures.** All 24 commits above db3a9683d carry `gpgsig` and no trailer.

## MEDIUM-1: an internal error exits 1 with no report

- **Where:** `PackCheckMain.java:96-103` (`check` catches only `UncheckedIOException`) and `:55-57` (`main`).
- **Failure scenario:** any other `RuntimeException` escapes `main`, and the JVM exits 1 with a stack trace and an
  empty stdout. Causes include:
  - `Problems.statusOf` for a code outside the table, which is thrown on purpose as "refused loudly";
  - `Problems.ofFault` for a token that is not a refusal code;
  - the `IllegalStateException` of `CompositionFactsBuilder.composeFacts` for `Assembly.None`;
  - any NPE or ISE in a reader on unexpected content.

  This breaks the data-model invariant that 0 means `problems` is empty, 1 means otherwise, and 2 writes nothing on
  stdout. Exit 1 must mean "the report lists problems". A CI job then sees "your packs have problems" when the CLI has
  a bug, and gets no report to read.
- **Fix:**
  - Catch `RuntimeException` around everything that runs before the report is printed (verdict, `Problems.of`,
    `ReportWriter.report`).
  - Print `pack-check: internal error: <class>: <message>` and the stack trace on stderr, and return `FAILED` (2)
    with nothing on stdout.
  - Build the report string before printing it, as now, so no partial report reaches stdout.
  - Update the exit-code javadoc. Ask the orchestrator to add "an internal failure" to data-model's exit-2 row.
- **Test:** a run that reaches an unexpected exception exits 2, with stdout empty and the message on stderr. If
  needed, add a package-private `run` overload that takes the `PackReading`.

## MEDIUM-2: `--config-root` reads entries the service does not treat as packs

- **Where:** `ConfigRoot.java:38-41`.
- **Rule broken:** the spec's assumption is that "`--config-root` follows the service's layout". The service's
  listing (`StorageRootLocations.isPack`, `:464-467`) skips any entry whose name starts with `.` or contains
  `.pack-old-`. It also treats a dangling link entry as a half-swapped pack, which it repairs or refuses. The CLI does
  none of this.
- **Probe:** I ran each case in-process in a scratch copy, over a config root of the five example packs (baseline
  exit 0):
  - `packs/.staging/` (any dot directory: `.git`, `.idea`, a staging dir) is checked as a pack named `.staging`.
    The result is exit 1, `pack_unreadable`, and `CLOSURE` is skipped for the whole set.
  - `packs/built-in.pack-old-123/` (an aside copy left by an interrupted or finished commit in a copied live root) is
    checked as a second pack. The result is exit 1, with `pack_duplicate_id`, `pack_name_conflict` and two
    `pack_sibling_conflict`, where the service serves the set.
  - `packs/dangling -> nowhere` is skipped silently (`Files.isDirectory` is false) and the run exits 0. A pack the
    author expects to be checked is dropped, so this passes when it should fail.
- **Fix:**
  - Select entries as the service does: no leading `.`, no `.pack-old-`. Keep one predicate, not a copy: move it to
    a type both can name, such as a small `packs.domain` helper used by `StorageRootLocations` and `ConfigRoot`. The
    CLI may not name `infra.storage` types other than the two adapters.
  - Make a dangling entry under `packs/` a usage fault (exit 2, naming the entry). Do not drop it.
- **Tests:** one each in `ConfigRootTest` for a dot directory, an aside directory and a dangling link.

## LOW (fold in)

1. **FR-009, `Problems.java:130`.** `if (problems.isEmpty())` decides again the D3 rule that
   `PackSetVerdict.unreportedRefusal()` already owns. It is redundant, because `setCheck` is empty whenever the facts
   are `Unreadable` (`PackSetCheck.levelMissing`). My mutant `if (true)` survives as an equivalent mutant. Render
   `unreportedRefusal()` unconditionally, so `pack-validation` is the only owner of the rule.
2. **Draft contract (`contracts/PackCheckReport.yaml`, mission artifact).**
   - `packs.items.required` is `[name, level, path]`. That contradicts ruling S14, data-model, the spec edge case
     and this code, which is right.
   - So the DoD bullet "the report matches the draft schema" is false for the no-level case, which
     `aPackWithNoKnownLevelHasNoLevelInTheReportAndFails` covers.
   - The `problems` description ("one validation_failed problem per pack") also leaves out the `pack_unreadable`
     problem of D3 and the pack's separate round-trip problem.
   - Tell the orchestrator. WP05 must drop `level` from `required` before it pins the schema.
3. **Temporary directory.** The `finally` is correct (my mutant that skips the delete is killed). Only the success
   path is tested, though. Add a test where the round trip throws midway, asserting the run directory is gone and the
   exit is 2. No shutdown hook covers SIGINT; note this in the javadoc.
4. **`Arguments.packRoot`.** For `--pack x:org`, when both `x:org` and `x` are directories, the CLI silently checks
   `x` at `org`. Prefer the whole path when it is a directory, or refuse the ambiguity. A value starting with `--` is
   taken as a missing value: document `./--dir`.
5. **Overlap.** Nested packs (one `--pack` inside another) are accepted. The service's listing refuses overlapping
   entries. This is optional, to match the service.
6. **For WP05.** `instance` is a file-system path, not the URI reference that `Problem.yaml` describes (data-model
   chose this). A round-trip problem's detail reads "The pack cannot be registered: ..." (`PackRefusalMapping`), and
   data-model says the detail "names the pack", which it does not.

## Tests and mutants (lane-d, HEAD a07c8a0a2)

- **Tests:**
  - With per-task `--rerun -PrequireSymlinks=true`:
    - `:pack-check:test` 55/55;
    - `:packs:test` 6868 (6 skipped);
    - `:pack-validation:test` 446 (1 skipped).
  - `:deployable:test --tests '*HexagonalArchitectureTest*'` green.
  - `:deployable:test` in full, 2290/2290.
  - All with 0 failures.
  - A single trailing `--rerun` reruns only the last task, so give one per task.
- **Mutants** (scratch worktree):
  - Survived, as an equivalent mutant (LOW-1): `Problems` D3 guard replaced with `if (true)`.
  - Killed:
    - delete of the run directory skipped (`PackCheckMainTest`);
    - the a07c8a0a2 fold `|| Unreadable` removed from `PackSetCheck.refused` (`PackSetCheckTest$RoundTrip`);
    - the `ConfigRoot` directory filter removed;
    - `pack_unreadable` status 500 changed to 422.
