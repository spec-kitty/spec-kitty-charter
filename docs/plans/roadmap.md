---
title: Roadmap
description: The bootstrap steps for the charter service, from the contract skeleton and Gradle build to a shadow read, aligned with the core's seam work.
doc_status: draft
updated: 2026-10-13
type: Plan
kind: Planning
related:
  - architecture/strangler-transition
  - plans/open-questions
---

# Roadmap

Each step ends in something checkable. Steps 1 to 5 can run in parallel with the core's
seam work, because none of them answers a production read. Steps 6 and 7 wait for it. The
seam work is the import-move precondition of core ADR `2026-10-12-2` (D-2). It was tracked
as spec-kitty#645 until that issue was closed as not planned on 2026-10-13, and is now in
the scope of epic spec-kitty#6099.

| # | Step | Done when | Depends on |
|---|---|---|---|
| 1 | **Contract skeleton.** `contracts/charter/` with `info`, `servers`, the shared `Problem` schema, and the first read path: action-scoped context, keyed by mission type and action. | Core's layout and lint checks pass here; a `0.1.0-SNAPSHOT` bundle is built | ADR-0002 ruling |
| 2 | **Gradle build.** Multi-project build with the modules in [Hexagonal service](/architecture/hexagonal-service/), strict dependency verification, and a dependency-direction test. | An empty domain and a generated `charter-api` compile; the direction test fails a planted violation | 1 |
| 3 | **Fixture corpus, first slice.** Built-in pack only: load, merge, default preset, action-scoped context for each mission type and action, at both load depths. Expected results are produced by core's Python charter. | The corpus runs against Python in core and is published with the contract | 1, a core-side export script |
| 4 | **Domain and YAML read adapter.** Load packs with `pack_level` checks, merge layers, activation, cascade. | The first corpus slice passes against Java | 2, 3 |
| 5 | **Org and project layers.** Extend corpus and domain to org fragments, `required_<kind>`, project packs, `specializes_from` and `enhances` / `overrides`. | Corpus slice passes on both implementations | 4 |
| 6 | **Core ingests the contract.** Core vendors a pinned release and generates Python DTOs; the seam returns them. (If the Python charter moves here first, the seam and its DTO generation move with it.) | Core's seam tests pass on generated DTOs | the seam work, 1 |
| 7 | **Shadow read.** The launcher starts the service per worktree; the seam compares Java with Python out of band. | Differences are reported in diagnostics; none is unexplained on the corpus | 4 to 6, the launcher |

After step 7 the [strangler stages](/architecture/strangler-transition/) take over:
Java authoritative, then Java reads complete, then write migration one operation at a time.

## Before step 6: decide how Python reaches this repository

The likely path is to move the Python charter here first as a package (core ADR
`2026-10-12-2` D-3), so the strangler runs inside this repository. The alternative is to
rewrite in Java and replace the charter in core, so the Python code never moves. Choose by
which is safer and more economical ([open questions](/plans/open-questions/), question 2).
Steps 1 to 5 do not depend on the answer.

## Also needed, outside this repository

- **Core:** accept or amend ADR `2026-10-03-1`, and amend ADR `2026-10-12-2` so it names the
  Java direction and this repository's role.
- **Core:** re-home the D-2 import-move precondition after #645's closure; epic #6099 still
  names #645 as its precondition.
- **Core:** reconcile `docs/architecture/charter-backend-service-future.md`, which still
  lists the runtime service as backlog.
- **Core:** a fixture export that turns the Python charter's answers into corpus files.
- **spec-kitty-events:** the versioned ledger-row schema (operator decision of 2026-10-13).
  Not a dependency of this repository, but the runtime events that cite charter identifiers
  will use it.
