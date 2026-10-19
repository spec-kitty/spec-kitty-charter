---
title: Charter service bootstrap roadmap
description: The bootstrap steps for the charter service, from the contract skeleton and Gradle build to a read shadow, aligned with the core's seam work.
doc_status: draft
updated: 2026-10-19T00:00:00Z
type: Plan
kind: Planning
related:
  - adr/0018-action-context-read-and-seam-access-surface
  - architecture/strangler-transition
  - plans/open-questions
  - plans/milestones
  - adr/0010-cutover-scope-and-seam-boundary
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the next checkable step and what it depends on.
  - profile: core-maintainer
    guidance_text: Read this for the work needed outside this repository.
---

# Roadmap

Each step ends in something checkable. Steps 1 to 5 can run in parallel with the core's
seam work, because none of them answers a production read. Steps 6 and 7 wait for it. The
seam work is the import-move precondition of core ADR [`2026-10-12-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-12-2-charter-extraction-to-its-own-repository.md) (D-2). It is done:
spec-kitty#6188 moved the imports onto the offering seam, as core ADR
[`2026-10-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-16-1-extract-charter-offering-first-activation-rework-post-ga.md) records.

| # | Step | Done when | Depends on |
|---|---|---|---|
| 1 | **Contract skeleton.** `contracts/charter/` with `info`, `servers`, the shared `Problem` schema, and the first read path: action-scoped context, keyed by mission type and action. | The charter's own contract checks pass and a `1.0.0-SNAPSHOT` bundle is built (core's tools do not run here; [open question 18](/plans/open-questions/)) | ADR-0002 ruling |
| 2 | **Gradle build.** Multi-project build with the modules in [Hexagonal service](/architecture/hexagonal-service/), strict dependency verification, and a dependency-direction test. | An empty domain and a generated `charter-api` compile; the direction test fails a planted violation; `./gradlew verifyAll` runs green on Java 25 in CI | 1 |
| 3 | **Conformance corpus, first slice.** Built-in pack only: load, merge, default preset, action-scoped context for each mission type and action, at depths 0, 1 and 2 (the seam maps first load to 2, a recorded load to 1). Expected results come from a harness here over core's existing CLI, mapped onto the contract shape, with deliberate differences in a reviewed register ([ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/)). | The harness produces the first slice from open-packs and fixture projects, and the corpus is published with the contract | 1, the reads decided by the inventory (#94) |
| 4 | **Domain and YAML read adapter.** Load packs with `pack_level` checks, merge layers, activation, cascade. | The first corpus slice passes against Java | 2, 3 |
| 5 | **Org and project layers.** Extend corpus and domain to org fragments, `required_<kind>`, project packs, `specializes_from` and `enhances` / `overrides`. | Corpus slice passes on both implementations | 4 |
| 6 | **Core ingests the contract.** Core vendors a pinned release and generates Python DTOs; the seam in core returns them ([ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/): the Python charter stays in core). | Core's seam tests pass on generated DTOs | the seam work, 1 |
| 7 | **Read shadow.** The launcher starts the service per worktree; the seam compares Java with Python out of band. | Differences are reported in diagnostics; none is unexplained on the corpus | 4 to 6, the launcher |

Steps 1 to 7 are the start of the **4.x cutover** milestone on the tracker. Work that builds on
the cut-over charter goes in **Post-cutover**. [Milestones](/plans/milestones/) has the
proposed distribution of issues.

After step 7 the [strangler stages](/architecture/strangler-transition/) take over. Stages
3 and 4, Java authoritative and Java reads complete, ship together as the **4.x cutover**: every
offering read, including activation's reads of offering data, goes through this repository's
API, and the Python offering reads are removed or inactive. The seam fails closed, and the only
gate is the conformance corpus; compile keeps a ratcheted Python read until it is the first
write migrated (provisional, [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)). Write
migration then follows, one operation at a time.

## Progress

As of 2026-10-18, steps 1 and 2 are brought under governance by the Mission
`charter-contract-and-build-retro-01M57QHJ`
([changelog](/changelog/2026-10-18-retroactive-contract-and-build-mission/)).

- **Step 1, done.** `contracts/charter/` holds
  `1.0.0-SNAPSHOT`, unreleased, with two read paths (`GET /packs`,
  `GET /mission-types/{missionType}/actions/{action}/context`), the vendored `_shared`
  vocabulary and the contract scenarios. The charter's own contract checks run in the build
  (`./gradlew :contracts:check`): its lint ruleset with at least one planted fixture per
  rule, the bundle core vendors, a checker for closed response objects and the
  breaking-change check. All of them pass, locally and in CI. No contract version is released yet, so
  in a full clone the breaking-change check finds no baseline, says so and passes; it
  refuses a shallow clone, and CI fetches the full history.
- **Step 2, done.** `build-logic/` convention
  plugins, the six modules under `service/` plus the `charter-reports` aggregation, a
  version catalog, strict SHA-256 dependency verification and the ArchUnit gate, which fails
  a planted violation. `./gradlew verifyAll` also runs the quality gates, the contract
  scenarios, the open-packs round-trip
  ([ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/)) and the
  Javadoc and coverage reports ([Building the service](/guides/service-build/)): 735 tests,
  none skipped. `.github/workflows/service.yml` runs it on Java 25; its first run, on the
  pull request of this Mission, was green ([run 39205978799](https://github.com/spec-kitty/spec-kitty-charter/actions/runs/39205978799)).
- **Next: step 3**, the first corpus slice, produced by a harness here over core's existing CLI
  rather than a core-side export script ([ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/)).

The action-scoped context is the offering's walk, decided in
[ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/) and still marked
`x-provisional`: scope from the action node, `requires`, `suggests` up to `depth`, the `requires`
of the suggested artifacts and `vocabulary`, each artifact once, sorted by URN, without activation
filtering. The corpus ([#9](https://github.com/spec-kitty/spec-kitty-charter/issues/9)) decides
the registered divergences ([divergence register](/conformance/divergence-register/)). What applies in one
project is the effective-context operation of the activation contract set (R9,
[#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103)), and **R9 gates cutover
stage 3**: the seam does not switch its context read to the service before it exists
([open question 14](/plans/open-questions/)).

## Before step 6

[Open question 2](/plans/open-questions/) is answered: rewrite and replace
([ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/)). The seam and
the shadow comparison stay in core.

## Also needed, outside this repository

- **Core:** amend ADR `2026-10-03-1` so it governs only the seam routing; the offering
  internals, including the Charter Pack structure, are decided here
  ([ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)).
- **Core:** amend step P4 (spec-kitty#6288) of ADR `2026-10-16-1`: the Python offering does not
  move here ([ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/)).
- **Core:** amend ADR `2026-10-12-2` so it names the Java direction and this repository's
  role. ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) (the staged Java service strangler) is accepted, with an amendment that
  points its contract here and records rewrite-and-replace.
- **Core:** reconcile `docs/architecture/charter-backend-service-future.md`, which still
  lists the runtime service as backlog.
- **Core:** a fixture export that turns the Python charter's answers into corpus files.
- **Core and spec-kitty-events:** the one canonical event writer (core ADR
  [`4.x/2026-10-14-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-14-1-event-native-core-one-canonical-event-writer.md), Accepted), which writes only spec-kitty-events contract events, each
  with its `schema_version`. Not a dependency of this repository, but the events that cite
  charter identifiers will go through it.

## Before the first contract release

The contract's first release (#45) waited on #113, which reshaped the reserved 1.0 surface:
`depth`, the service version in `ServiceInfo`, the source fingerprint's inputs, the token and
refresh. Those are now decided in [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/)
and built. A change to them after release is breaking.
