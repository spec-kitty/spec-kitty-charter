---
affected_files: []
cycle_number: 1
mission_slug: pack-doctor-coverage-01M7YMQK
reproduction_command:
reviewed_at: '2026-11-21T17:23:59Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP02
---

# WP02 review, cycle 1 (reviewer-renata): CHANGES REQUESTED

The rename itself is clean. CI, the release scripts and build-logic agree with each other. `validate` gives
byte-identical output: the reviewer built both jars and compared their stdout, exit codes and stderr (ignoring the
`pack-check:`/`pack-doctor:` prefix) over the example packs (with and without `--round-trip`) and the first 40
planted packs. Every run matched. One wiring defect blocks approval.

**Issue 1 [HIGH] service/pack-doctor/build.gradle.kts:70-98 and NoPackCheckSurfaceTest.java:36: the surface scan fails under PIT, so `:pack-doctor:pitest` produces no report (#461)**

- The repository root reaches the test only through `tasks.test.jvmArgumentProviders` (`SurfaceScanArguments`).
- `charter.mutation-conventions` passes PIT's minions only `test.systemProperties`
  (`MutationFloors.minionSystemProperties(project.path, unitTests.get().systemProperties)`, plus the excluded tags).
  It does not pass the argument providers.
- In the minion, `charter.test.repo-root` is therefore absent. `ROOT` becomes `Path.of("")`, which resolves to
  `service/pack-doctor`, and `trackedFiles()` throws `IllegalStateException("no git checkout at ...")`.
- `noTrackedFileOutsideTheAllowlistNamesTheOldTool` and `everyAllowlistEntryIsTrackedAndSaysWhy` then fail in PIT's
  unmutated run. That leaves pack-doctor with no mutation report on the scheduled run, the exact failure the
  convention's comment (#461) guards against.
- The NoPackCheckSurfaceTest class matches PIT's default target tests (`ai.speckitty.charter.packdoctor.*`).
- `deployable` uses the same pattern for `ShippedConfigurationTest`, but `deployable` does not apply mutation
  conventions.

Fix, either of:
- (a) Tag the scan's nested class (for example `@Tag("surface")`). Exclude that tag from `test`, so PIT inherits the
  exclusion, and run it in its own `Test` task wired into `check`, as `jarTest` is done for `jar`.
- (b) When the property is absent, resolve `ROOT` by walking up from `user.dir` to the first directory that holds
  `.git` (a file or a directory, for worktrees).

Prefer (a). It keeps the build-cache input wiring and keeps a git subprocess out of every mutant run.

**Issue 2 [LOW] service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/PackDoctorReportContractTest.java:643: a test name still names the tool**

- `theStatusTableOfPackCheckHoldsExactlyTheseCodes` tests the CLI's status table (`PackDoctorSeam.statusTableCodes()`,
  "pack-doctor's table" in `Problems`). It does not test the domain `PackCheck`.
- It passes the scan only because `\bPackCheck` needs a word boundary, so a camel-case compound slips through.
  `theDomainTermAndTheKeptFileNamesPass` lists it as an allowed case.

Fix: rename it to `theStatusTableOfPackDoctorHoldsExactlyTheseCodes` and drop it from that negative-control list.
Optionally, add a positive control for a camel-case compound that names the tool.

**Note (no change needed):** the red-first claim (6 of 8 failing before the rename) cannot be checked from history,
because the test and the rename land in one commit (53b008f15). The test is not tautological: it scans the real
tracked tree, has positive and negative controls for the pattern, and checks that every allowlist entry is still
tracked. Its Gradle input, the `ls-files` plus `git grep` candidate lines with an `@Internal` root, is sound.
