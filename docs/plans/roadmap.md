---
title: Charter service bootstrap roadmap
description: The bootstrap steps for the charter service, from the contract skeleton and Gradle build to a read shadow, aligned with the core's seam work.
doc_status: draft
updated: 2026-10-31T00:00:00Z
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
| 1 | **Contract skeleton.** `contracts/charter/` with `info`, `servers`, the shared `Problem` schema, and the first read path: action-scoped context, keyed by mission type and action. | The charter's own contract checks pass and a `0.1.0-SNAPSHOT` bundle is built (core's tools do not run here; [open question 18](/plans/open-questions/)) | ADR-0002 ruling |
| 2 | **Gradle build.** Multi-project build with the modules in [Hexagonal service](/architecture/hexagonal-service/), strict dependency verification, and a dependency-direction test. | An empty domain and a generated `charter-api` compile; the direction test fails a planted violation; `./gradlew verifyAll` runs green on Java 25 in CI | 1 |
| 3 | **Conformance corpus, first slice.** Built-in pack only: load, merge, default preset, action-scoped context for each mission type and action, at depths 0, 1 and 2 (the seam maps first load to 2, a recorded load to 1). Expected results come from a harness here over core's existing CLI, mapped onto the contract shape, with deliberate differences in a reviewed register ([ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/)). | The harness produces the first slice from open-packs and fixture projects, and the corpus is published with the contract | 1, the reads decided by the inventory (#94) |
| 4 | **Domain and YAML read adapter.** Load packs with `pack_level` checks, merge layers, activation, cascade. | The first corpus slice passes against Java | 2, 3 |
| 5 | **Org and project layers.** Extend corpus and domain to org fragments, `required_<kind>`, project packs, `specializes_from` and `enhances` / `overrides`. | Corpus slice passes on both implementations | 4 |
| 6 | **Core ingests the contract.** Core vendors a pinned 0.x release, generates a client from it, and routes every operation through a proxy at its seam, each starting on core's Python ([ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/); [ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/): the Python charter stays in core until each operation switches). | Core's seam tests pass through the generated client, all operations in-process | the seam work, 0.1.0 (#45, #119) |
| 7 | **Read shadow.** Core's proxy routes an operation to a shadow: it answers from Python and compares the service's answer, one operation at a time (ADR-0021 D4). | Differences are reported in diagnostics; none is unexplained on the corpus | 4 to 6, a running service (#70), released on the `service-v*` line ([ADR-0046](/adr/0046-service-release-line-and-native-packaging/)) |

Steps 1 to 7 are the start of the **4.x cutover** milestone on the tracker. Work that builds on
the cut-over charter goes in **Post-cutover**. [Milestones](/plans/milestones/) has the
proposed distribution of issues.

After step 7 the [strangler stages](/architecture/strangler-transition/) take over, one operation
at a time (ADR-0021 D5). Each operation moves from shadow to the service once the corpus passes
for it, fail closed; the first such move needs contract 1.0.0
([ADR-0022](/adr/0022-contract-versions-start-at-0-x/) D6). The **4.x cutover** is reached when
every offering read, including activation's, goes through this repository's API and the Python
offering reads are removed or inactive; compile keeps a ratcheted Python read until it is the first
write migrated (provisional, [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)). Write
migration then follows, one operation at a time.

## Progress

As of 2026-10-27.

- **Step 1, done, then reshaped.** `contracts/charter/` is at `0.1.0-SNAPSHOT`, unreleased, and is
  published as 0.x until it is stable (ADR-0022, built by PR #129). PR #139 rebuilt the service on
  one canonical `Pack` aggregate. It removed `GET /packs`, `POST /packs/refresh` and the
  action-context read, so the contract now holds `GET /info` and the tactic reads
  (`/packs/{packId}/tactics[/{tacticId}]`). The contract's own checks (lint, closed responses, the
  bundle, the release gate) run in the build and pass.
- **Step 2, done.** The build is cut into onion-layered vertical slices (`kernel`, `packs`,
  `deployable`), enforced by ArchUnit
  ([ADR-0006](/adr/0006-onion-vertical-slices-archunit/)), behind one required CI gate
  ([ADR-0023](/adr/0023-ci-gate-required-check/)).
- **Step 4, under way.** Packs are validated against the canonical schemas
  ([ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/), PR #137). The `Pack`
  aggregate owns tactics behind repository ports and a filesystem adapter (PR #139). PR #170 added
  directive, styleguide and paradigm ([ADR-0024](/adr/0024-doctrine-artifact-family-contract/)), and PR #180 added toolguide and registration for all four. Merge, activation and cascade are not built.
- **Next.** Two tracks:
  - **unblock core:** #119 is fixed and the publishing workflow (#45) is built. Cutting 0.1.0 is
    held (operator, 2026-10-27); the tag ruleset (#196) comes first when it is cut. Then the
    activation set is 0.2.0 (#125), and an additive change ready earlier, such as
    `GET /openapi.yaml` (#208), rides that minor;
  - **model before breadth:** the whole-pack round trip
    ([#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)): read every pack file
    into the domain and write it back. #171 comes first; the read and registration services of
    the kind registry (#169) are extracted after two or three more kinds are built
    ([ADR-0024](/adr/0024-doctrine-artifact-family-contract/), Amendment 2026-10-26), while the
    storage and aggregate plumbing is keyed by kind first, with the whole-pack seam (#189,
    [ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/), accepted 2026-10-27). Then the
    offering reads under `/offering` (#126).

  Step 3, the first corpus slice ([ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/)),
  needs the reads #126 brings back.

The action-context walk is decided in
[ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/). Its operation returns as
`GET /offering/mission-types/{missionType}/actions/{action}/context`
([ADR-0020](/adr/0020-offering-and-activations/) D1, #126). What applies in one activation is the
effective context under `/activations/{activationId}` (R9,
[#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103)), and **R9 gates cutover stage
3**: the seam does not switch its context read to the service before it exists.

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

The first release is 0.1.0 ([ADR-0022](/adr/0022-contract-versions-start-at-0-x/)). Both things it
waited on are built: the fix for the duplicate classes code generation emitted (#119), so that
core's generated client is clean, and the publishing workflow (#45). What remains is cutting the
release, which the operator holds for now (2026-10-27), following
[the release procedure](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md#releasing-a-contract-version). Shapes may still change in a 0.x minor; they freeze at 1.0.0
(#128).
