---
title: Charter service bootstrap roadmap
description: The bootstrap steps for the charter service, from the contract skeleton and Gradle build to a read shadow, aligned with the core's seam work.
doc_status: draft
updated: 2026-10-19
type: Plan
kind: Planning
related:
  - architecture/strangler-transition
  - plans/open-questions
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the next checkable step and what it depends on.
  - profile: core-maintainer
    guidance_text: Read this for the work needed outside this repository.
---

# Roadmap

Each step ends in something checkable. Steps 1 to 5 can run in parallel with the core's
seam work, because none of them answers a production read. Steps 6 and 7 wait for it. The
seam work is the import-move precondition of core ADR [`2026-10-12-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-12-2-charter-extraction-to-its-own-repository.md) (D-2). It was tracked
as spec-kitty#645 until that issue was closed as not planned on 2026-10-13, and is now in
the scope of epic spec-kitty#6099.

| # | Step | Done when | Depends on |
|---|---|---|---|
| 1 | **Contract skeleton.** `contracts/charter/` with `info`, `servers`, the shared `Problem` schema, and the first read path: action-scoped context, keyed by mission type and action. | The charter's own contract checks pass and a `1.0.0-SNAPSHOT` bundle is built (core's tools do not run here; [open question 18](/plans/open-questions/)) | ADR-0002 ruling |
| 2 | **Gradle build.** Multi-project build with the modules in [Hexagonal service](/architecture/hexagonal-service/), strict dependency verification, and a dependency-direction test. | An empty domain and a generated `charter-api` compile; the direction test fails a planted violation; `./gradlew verifyAll` runs green on Java 25 in CI | 1 |
| 3 | **Conformance corpus, first slice.** Built-in pack only: load, merge, default preset, action-scoped context for each mission type and action, at both load depths. Expected results are produced by core's Python charter. | The corpus runs against Python in core and is published with the contract | 1, a core-side export script |
| 4 | **Domain and YAML read adapter.** Load packs with `pack_level` checks, merge layers, activation, cascade. | The first corpus slice passes against Java | 2, 3 |
| 5 | **Org and project layers.** Extend corpus and domain to org fragments, `required_<kind>`, project packs, `specializes_from` and `enhances` / `overrides`. | Corpus slice passes on both implementations | 4 |
| 6 | **Core ingests the contract.** Core vendors a pinned release and generates Python DTOs; the seam in core returns them ([ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/): the Python charter stays in core). | Core's seam tests pass on generated DTOs | the seam work, 1 |
| 7 | **Read shadow.** The launcher starts the service per worktree; the seam compares Java with Python out of band. | Differences are reported in diagnostics; none is unexplained on the corpus | 4 to 6, the launcher |

After step 7 the [strangler stages](/architecture/strangler-transition/) take over:
Java authoritative, then Java reads complete, then write migration one operation at a time.

## Progress

As of 2026-10-18, steps 1 and 2 are brought under governance by the Mission
`charter-contract-and-build-retro-01M57QHJ`
([changelog](/changelog/2026-10-18-retroactive-contract-and-build-mission/)).

- **Step 1, done pending the first CI run on the pull request.** `contracts/charter/` holds
  `1.0.0-SNAPSHOT`, unreleased, with two read paths (`GET /packs`,
  `GET /mission-types/{missionType}/actions/{action}/context`), the vendored `_shared`
  vocabulary and the contract scenarios. The charter's own contract checks run in the build
  (`./gradlew :contracts:check`): its lint ruleset with at least one planted fixture per
  rule, the bundle core vendors, a checker for closed response objects and the
  breaking-change check. All of them pass locally. No contract version is released yet, so
  in a full clone the breaking-change check finds no baseline, says so and passes; it
  refuses a shallow clone, and CI fetches the full history.
- **Step 2, done pending the first CI run on the pull request.** `build-logic/` convention
  plugins, the six modules under `service/` plus the `charter-reports` aggregation, a
  version catalog, strict SHA-256 dependency verification and the ArchUnit gate, which fails
  a planted violation. `./gradlew verifyAll` also runs the quality gates, the contract
  scenarios, the open-packs round-trip
  ([ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/)) and the
  Javadoc and coverage reports ([Building the service](/guides/service-build/)): 644 tests,
  none skipped, green locally on Java 25. `.github/workflows/service.yml` runs it on Java 25;
  that run has not happened yet.
- **Next: step 3**, the first corpus slice, which needs the core-side export script.

The action-scoped context answered today is a first slice, marked `x-provisional` in the
contract: the targets of the scope edges of the action node and of the mission type node,
each once, sorted by URN, over the merged offering without activation filtering. The corpus ([#9](https://github.com/spec-kitty/spec-kitty-charter/issues/9))
decides the real rules and the contract version that carries them
([open question 14](/plans/open-questions/)).

## Before step 6

[Open question 2](/plans/open-questions/) is answered: rewrite and replace
([ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/)). The seam and
the shadow comparison stay in core.

## Also needed, outside this repository

- **Core:** amend ADR `2026-10-12-2` so it names the Java direction and this repository's
  role. ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) (the staged Java service strangler) is accepted, with an amendment that
  points its contract here and records rewrite-and-replace.
- **Core:** re-home the D-2 import-move precondition after #645's closure; epic #6099 still
  names #645 as its precondition.
- **Core:** reconcile `docs/architecture/charter-backend-service-future.md`, which still
  lists the runtime service as backlog.
- **Core:** a fixture export that turns the Python charter's answers into corpus files.
- **Core and spec-kitty-events:** the one canonical event writer (core ADR
  [`4.x/2026-10-14-1`](https://github.com/spec-kitty/spec-kitty/pull/6217), in review), which writes only spec-kitty-events contract events, each
  with its `schema_version`. Not a dependency of this repository, but the events that cite
  charter identifiers will go through it.
