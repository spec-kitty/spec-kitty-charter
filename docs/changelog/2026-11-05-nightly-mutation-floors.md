---
okf_version: "0.2"
title: The mutation nightly turns red below a floor, and mutates packs
description: A score below its fixed floor now turns the mutation nightly red and opens an issue; PIT also mutates packs domain, application, storage and fetch.
doc_status: active
updated: 2026-11-05T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0062-nightly-mutation-floor
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if the mutation nightly is red, or before you change a mutation floor.
---

# 2026-11-05 — The mutation nightly turns red below a floor, and mutates packs

**A falling mutation score now turns the nightly red and raises an issue**
([ADR-0062](/adr/0062-nightly-mutation-floor/), [#239](https://github.com/spec-kitty/spec-kitty-charter/issues/239)).
Mutation testing is no longer an experiment: each mutated module has a fixed floor, and `./gradlew pitest` fails a
module whose score is below it, naming the module, the group, the score and the floor. It still never runs on a pull
request or in the required check.

- **Floors:** kernel 65 %, missions 70 %, profiles 50 %, 85 % for packs `domain+application` and `infra.storage`,
  and 70 % for packs `infra.fetch`. A floor is raised by pull
  request once the score clears it by 5 points, and lowered only by amending the ADR.
- **Packs is mutated:** `packs.domain` and `packs.application` (without the Spring wiring) as one group,
  `packs.infra.storage` as a second and `packs.infra.fetch` (without `infra.fetch.config`) as a third, with their unit
  tests only. They scored 92.7 %, 90.7 % and 77.3 % on the first run.
- **The nightly reports every module:** it runs `pitest --continue`, writes a job summary per module and group,
  uploads every module's report, and opens or comments on one `mutation-nightly` issue when a scheduled run does not
  succeed. Its timeout is 90 minutes.
- **Kernel survivors triaged:** 14 surviving `kernel.domain` mutants are killed by strengthening existing tests and
  11 are marked equivalent, raising kernel from 72.4 % to 76 % (this pull request's own triage); kernel is 82.3 % after #384. The triage found a `Pep440Range.overlaps` defect,
  tracked in [#386](https://github.com/spec-kitty/spec-kitty-charter/issues/386).

[The service build guide](/guides/service-build/#mutation-testing) says how to run PIT, read the floors and narrow a
run.
