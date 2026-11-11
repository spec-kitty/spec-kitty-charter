---
okf_version: "0.2"
title: The mutation nightly guards its scope, shows its margins and keeps PIT's output
description: PIT cannot be reached from the required check, the nightly lists its modules from the build and keeps its log, and the summary shows margins; eight constants are renamed.
doc_status: active
updated: 2026-11-11T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0062-nightly-mutation-floor
  - adr/0060-native-build-runs-nightly-and-per-release
  - adr/0072-sonar-s1845-and-s1314-rulings
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what the nightly now shows and guards, and for the renamed constants.
---

# 2026-11-11 — The mutation nightly is hardened

A red nightly had no test to blame, and nothing kept PIT out of the required check
([#387](https://github.com/spec-kitty/spec-kitty-charter/issues/387),
[#388](https://github.com/spec-kitty/spec-kitty-charter/issues/388),
[#389](https://github.com/spec-kitty/spec-kitty-charter/issues/389),
[#461](https://github.com/spec-kitty/spec-kitty-charter/issues/461),
[#465](https://github.com/spec-kitty/spec-kitty-charter/issues/465)).

- **PIT can no longer be reached from `verifyAll` or `check`** ([ADR-0062](/adr/0062-nightly-mutation-floor/)).
  Before: nothing stopped a task dependency from pulling `pitest` into the required check. After: the root
  build fails any such invocation, naming the gate. A guard test pins the nightly's triggers.
- **The nightly takes its module list from the build.** Before: a hand-kept list that had already missed
  `:missions-edge`. After: `./gradlew mutationModules` writes `modules.txt`, and `mutation_summary.py` requires
  `--modules`. Without the list the summary says so and is never green.
- **The summary shows margins and timed-out mutants.** Before: score, strength, uncovered mutants and floor. After:
  **Timed out** and **Margin** columns (points over the floor, and the detected mutants a group can lose).
- **The artifact keeps `pitest.log` and `modules.txt`.** Before: PIT's output lived only in a job log that can expire.
  After: both go up in the `mutation-report` artifact, and the summary lists the "did not pass without mutation" lines.
- **The `floors.json` shape changes.** Each group gains `timedOut`, `marginMutants` and `marginPoints`. No reader of
  the older shape is kept: a group without them is invalid and fails.
- **Eight constants are renamed** ([ADR-0072](/adr/0072-sonar-s1845-and-s1314-rulings/)): the six `TOKEN`, `CODE`,
  `SPELLING` and `ROLE` pattern constants become `*_SHAPE`, `CascadeScope.ALL` becomes `ALL_TOKEN`, and
  `GitVersionCheck.MINIMUM` becomes `MINIMUM_VERSION`.
- **Octal literals carry a `0_` prefix.** Before: `0170000`, `0100000`, `040000` in the zip reader. After: `0_170000`,
  `0_100000`, `0_40000`, same values.
