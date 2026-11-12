---
title: Charter service bootstrap roadmap
description: The bootstrap steps for the charter service, from the contract skeleton and Gradle build to a read shadow, aligned with the core's seam work.
doc_status: draft
updated: 2026-11-12T00:00:00Z
type: Plan
kind: Planning
related:
  - adr/0018-action-context-read-and-seam-access-surface
  - architecture/strangler-transition
  - plans/open-questions
  - plans/milestones
  - adr/0010-cutover-scope-and-seam-boundary
  - adr/0055-cutover-sequencing-the-java-service-first
  - adr/0059-switch-gate-cascade-edges-and-activation-rulings
  - plans/engineering-notes/service-usability-run-2026-11-09
  - plans/next-slices-2026-11-12
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the next checkable step and what it depends on.
  - profile: core-maintainer
    guidance_text: Read this for the work needed outside this repository.
---

# Roadmap

Each step ends in something checkable. The cutover is built in two phases
([ADR-0055](/adr/0055-cutover-sequencing-the-java-service-first/) D1):

- **Phase 1, the service (steps 1 to 5, the pack-composition resolver and the activation register).** The service
  answers the cutover reads and writes from its own Java domain. None of it answers a production read, so it does not
  wait for core.
- **Phase 2, core's integration (steps 6 and 7).** Core vendors the client, builds the proxy and starts the shadow.
  It waits for phase 1.

The seam work is the import-move precondition of core ADR [`2026-10-12-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-12-2-charter-extraction-to-its-own-repository.md) (D-2). It is done:
spec-kitty#6188 moved the imports onto the offering seam, as core ADR
[`2026-10-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-16-1-extract-charter-offering-first-activation-rework-post-ga.md) records.

| # | Step | Done when | Depends on |
|---|---|---|---|
| 1 | **Contract skeleton.** `contracts/charter/` with `info`, `servers`, the shared `Problem` schema, and the first read path: action-scoped context, keyed by mission type and action. | The charter's own contract checks pass and a `0.1.0-SNAPSHOT` bundle is built (core's tools do not run here; [open question 18](/plans/open-questions/)) | ADR-0002 ruling |
| 2 | **Gradle build.** Multi-project build with the modules in [Hexagonal service](/architecture/hexagonal-service/), strict dependency verification, and a dependency-direction test. | An empty domain and a generated `charter-api` compile; the direction test fails a planted violation; `./gradlew verifyAll` runs green on Java 25 in CI | 1 |
| 3 | ~~**Conformance corpus, first slice.** Built-in pack only: load, merge, default preset, action-scoped context for each mission type and action, at depths 0, 1 and 2 (the seam maps first load to 2, a recorded load to 1). Expected results come from a harness here over core's existing CLI, mapped onto the contract shape, with deliberate differences in a reviewed register ([ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/)).~~ **Superseded 2026-11-03 ([ADR-0055](/adr/0055-cutover-sequencing-the-java-service-first/) D3). Author or update the packs to the design:** the packs the service ships and loads, and fixture packs that exercise each rule (`depends_on`, levels, activation, cascade, `required_<kind>`, `specializes_from`, `enhances` and `overrides`). | ~~The harness produces the first slice from open-packs and fixture projects, and the corpus is published with the contract~~ The packs match the design, and the ADRs say what each resolves to | 1, the reads decided by the inventory (#94) |
| 3a | **Pack-composition resolver** ([#145](https://github.com/spec-kitty/spec-kitty-charter/issues/145), [#146](https://github.com/spec-kitty/spec-kitty-charter/issues/146)). The `depends_on` and `parent_pack` checks, and the dependency resolver of [ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/): each level is resolved through `depends_on` before the levels stack. Moved onto the cutover path 2026-11-03 (ADR-0055 D2). **Done 2026-11-03** ([changelog](/changelog/2026-11-03-pack-composition-depends-on-and-resolver/), [Compose packs](/guides/compose-packs/)); deferred: #357, #358, #359, #360, #363. | The resolver orders, range-checks and dedupes the packs of one level, and refuses cycles and sibling conflicts | 2 |
| 4 | **Domain and YAML read adapter.** Load packs with `pack_level` checks, merge layers, activation, cascade. **Done** (#10 closed; PRs #414, #419, #427, #448). | ~~The first corpus slice passes against Java~~ The domain resolves the step 3 packs to what the ADRs say (ADR-0055 D3) | 2, 3, 3a |
| 5 | **Org and project layers.** Extend the packs and the domain to org fragments, `required_<kind>`, project packs, `specializes_from` and `enhances` / `overrides`. **Done** (#11 closed; same PRs). | ~~Corpus slice passes on both implementations~~ The domain resolves the step 3 packs for these rules to what the ADRs say | 4 |
| 6 | **Core ingests the contract** (phase 2; waits for phase 1). Core vendors a pinned 0.x release, generates a client from it, and routes every operation through a proxy at its seam, each starting on core's Python ([ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/); [ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/): the Python charter stays in core until each operation switches). | Core's seam tests pass through the generated client, all operations in-process | the seam work, 0.1.0 (#45, #119), and phase 1 (steps 1 to 5, 3a and the activation register, [#291](https://github.com/spec-kitty/spec-kitty-charter/issues/291)) |
| 7 | **Read shadow** (phase 2). Core's proxy routes an operation to a shadow: it answers from Python and compares the service's answer, one operation at a time (ADR-0021 D4). | Differences are reported in diagnostics; none is unexplained. What counts as the reference answer is an open question in ADR-0055 | 4 to 6, a running service (#70), released on the `service-v*` line ([ADR-0046](/adr/0046-service-release-line-and-native-packaging/)) |

The activation register ([#291](https://github.com/spec-kitty/spec-kitty-charter/issues/291)) is part of phase 1.
It is built: no activation operation answers `501` any more (usability run of 2026-11-09).

Steps 1 to 7 and 3a are the start of the **4.x cutover** milestone on the tracker. Work that builds on
the cut-over charter goes in **Post-cutover**. [Milestones](/plans/milestones/) has the
proposed distribution of issues.

After step 7 the [strangler stages](/architecture/strangler-transition/) take over, one operation
at a time (ADR-0021 D5). Each operation moves from shadow to the service once its gate passes,
fail closed. The gate is the operation's design-derived BDD `@contract` scenarios and its contract tests
([ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) D1); the corpus over core's CLI no longer
is it. The first such move needs contract 1.0.0
([ADR-0022](/adr/0022-contract-versions-start-at-0-x/) D6). The **4.x cutover** is reached when
every offering read, including activation's, goes through this repository's API and the Python
offering reads are removed or inactive; compile keeps a ratcheted Python read until it is the first
write migrated (provisional, [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)). Write
migration then follows, one operation at a time.

## Progress

As of 2026-11-12: slice B is built (PR #501), slice A closed (#492), the quick wins of 2026-11-11 closed except #494 (green nightly runs), the switch
gate is checked per operation (PR #527, #9 and #72), and ADR-0074 decided the remaining cutover reads (PR #529). The
next slices are in [Next slices after the 2026-11-12 slice review](/plans/next-slices-2026-11-12/). The notes below
are as of 2026-11-11. Sources: the [usability run of 2026-11-09](/plans/engineering-notes/service-usability-run-2026-11-09/),
an architecture and planning review of the plan pages against `main`, the tracker, and the nightly SonarCloud scan.

- **Phase 1 steps 1 to 5, 3a and the activation register are built.** The contract holds the API root, the offering
  reads, the generic artifact reads (`/packs/{packId}/artifacts[/{kind}/{artifactId}]`), pack registration and the
  activation register with its effective reads (`/artifacts`, `/skipped`, `/graph` and the effective action context,
  R9, the cutover read of the effective activation sets). The whole-pack round trip is a required check. Contract `0.1.0` is still uncut (#45, #196).
- **What the usability run found (2026-11-10).** The service is not yet usable by core. Artifact bodies were served for
  five kinds only (tactic, directive, styleguide, paradigm, toolguide; [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/)), so
  agent profiles, mission types, step contracts, procedures and templates could not be read, although their domain and
  storage bindings exist. Two of those gaps are closed since (see **Landed since** below). No real pack registers unedited (#451, #431, #452), language tags do not alias (#453), the
  effective action context lists fewer directives than core and links no bodies (#450), and startup refusals are hard
  to read (#454).
- **Landed since.** PR #470 (2026-11-10) made agent profiles readable, pack-scoped and under an activation, and closed issue
  #407. PR #472 (2026-11-11) did the same for mission types, their steps, step contracts and skeletons, and closed
  issues #99, #100 and #148. Their literal paths in the contract (for example `/packs/{packId}/artifacts/agent_profile/{artifactId}`
  and `/packs/{packId}/artifacts/mission_type/{missionTypeId}/steps/{stepId}`) are concrete instances of the generic kind
  route, sharing its `_links` and refusals ([ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/)). PR #460 landed phase 1 of
  step enhancements and prompt fragments ([ADR-0066](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/)); #428
  and #437 are closed.
- **Still open from the reads.** Procedure and template bodies have no read yet. #96 (artifact bodies) stays open for
  them, and #104 covers the template catalog. PR #472 took only the item-link half of #450, so its action-context
  scoping half is still open. #469 asks the activation to link its graph and an action-context template.

## Toward a service core can use

The slices, in order. Each ticket carries a `slice:A` to `slice:G` label next to its milestone, and each slice has
a tracking issue. Re-run the usability run after slices B and C; its checklist is the readiness gate. The state of
each slice and the reasons for the order are in [Next slices after the 2026-11-12 slice review](/plans/next-slices-2026-11-12/).

| Slice | Scope | Done when | Depends on |
|---|---|---|---|
| **A. Every kind is readable** | Done (#492): profiles, mission types, steps, step contracts, skeletons, procedures; the template read decided (#104) | Bodies are served through the generic kind route with `_links` | — |
| **B. Real packs register as they are** ([#466](https://github.com/spec-kitty/spec-kitty-charter/issues/466)) | Built in PR #501 (#451, #431, #432, #490; ADR-0073). Left: #110 (manifest restamping, built 2026-11-12; open-packs must regenerate `built-in`'s `pack-manifest.yaml` (open-packs #250), and a pack registered before gets a one-time `409` on its first unchanged re-registration, so send a new `pack_version`), and the proof: the drift watch (#509) green, then `openPacksRef` raised | The drift watch registers open-packs `main` unedited, and the usability run's registration checks pass | open-packs #241 and #242 for the proof |
| **C. The effective step and action context match core** ([#467](https://github.com/spec-kitty/spec-kitty-charter/issues/467)) | #438, #450, #97, #439, #447. In flight | Core's 16 directives for `software-dev/implement` are explained, and each item links to its body | — |
| **D. Release machinery and clean-up** ([#468](https://github.com/spec-kitty/spec-kitty-charter/issues/468)) | #196, #45, #367, #371 left; #454, #406, #312, #47, #9 and #72 done | Its tickets close. A release is not tied to any slice: the operator cuts a 0.x release when what the contract offers suits them (ruling, 2026-11-12) | — |
| **A2. The reads ADR-0074 added** ([#536](https://github.com/spec-kitty/spec-kitty-charter/issues/536)) | #98 presets, #105 assets, #107 skills, #532, #409; #106 glossary last | Presets, assets, skills and glossary contexts are served in both trees by ADR-0074's paths, each through the ADR-0059 D1 gate | — (runs after C and D); open-packs follows #106 |
| **F. The kind taxonomy and the mission-type schema** ([#537](https://github.com/spec-kitty/spec-kitty-charter/issues/537)) | #112 (R18), #514, #520, #515 (needs a ruling), #533 | The kind table is served as data; `mission_step_contract` is retired; steps are the single authoring surface | A2 |
| **G. Agent profile resolution** ([#538](https://github.com/spec-kitty/spec-kitty-charter/issues/538)) | #102 (R8), #42, #512, #380 | Profile resolution is a contract operation; a human can serve an `operator` step | F |
| **E. Core ingests** (phase 2, tracked on [#13](https://github.com/spec-kitty/spec-kitty-charter/issues/13)) | #13, #452 and the id spelling at the seam (from #361), #70 | Core's seam tests pass through the generated client | A released contract |

After G, #108 (R14, the routing catalog) runs as a Mission of its own, with the build ADR of ADR-0074 decision 8
first. Then every read in epic #94 has a contract operation.

Alongside, not on the critical path: the SonarCloud findings (Security rating D, Reliability rating E, from a handful
of issues; #463 to #465, see [Milestones](/plans/milestones/#sonarcloud-findings)). Hosted work (#127, #396) and contract 1.0.0
(#128) follow slice E.

The action-context walk is decided in
[ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/). Its operation is
`GET /offering/mission-types/{missionType}/actions/{action}/context`
([ADR-0020](/adr/0020-offering-and-activations/) D1). What applies in one activation is the effective context under
`/activations/{activationId}` (R9, [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103)), built in PR #448;
its follow-up is [#450](https://github.com/spec-kitty/spec-kitty-charter/issues/450).

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
- **Core and spec-kitty-events:** the one canonical event writer (core ADR
  [`4.x/2026-10-14-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-14-1-event-native-core-one-canonical-event-writer.md), Accepted), which writes only spec-kitty-events contract events, each
  with its `schema_version`. Not a dependency of this repository, but the events that cite
  charter identifiers will go through it.

## Before the first contract release

The first release is 0.1.0 ([ADR-0022](/adr/0022-contract-versions-start-at-0-x/)). Both things it
waited on are built: the fix for the duplicate classes code generation emitted (#119), so that
core's generated client is clean, and the publishing workflow (#45). What remains is cutting the
release. The operator cuts it when what the contract offers is to their liking; it waits for no slice (ruling, 2026-11-12), following
[the release procedure](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md#releasing-a-contract-version). Shapes may still change in a 0.x minor; they freeze at 1.0.0
(#128).
