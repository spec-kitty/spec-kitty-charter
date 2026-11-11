---
title: Milestones
description: The tracker's milestones, what closes each one, and the proposed distribution of the open spec-kitty-charter issues across them.
doc_status: draft
updated: 2026-11-10T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/roadmap
  - plans/overhaul-approach
  - architecture/strangler-transition
  - adr/0055-cutover-sequencing-the-java-service-first
audience:
  - profile: charter-service-contributor
    guidance_text: Read this to find which milestone an issue belongs to and what has to close before the cutover.
  - profile: core-maintainer
    guidance_text: Read this for what the 4.x cutover needs from core, and which charter work waits until after it.
  - profile: governance-lead
    guidance_text: Read this for when the creed, pack governance and deactivation work is scheduled.
---

# Milestones

The issue tracker groups the open work into five milestones. This page says what closes
each milestone and which issue goes where. The checkable steps are in the
[Roadmap](/plans/roadmap/), and the phases in the [High-level approach](/plans/overhaul-approach/).
All `#N` are `spec-kitty-charter` issues unless prefixed with another repository.

> **Status:** in effect (updated 2026-11-10). The assignment is provisional and changes as the plan
> does. The operator approved the proposed homes on 2026-11-10, and the tracker matches this page.

## The milestones

| Milestone | Done when |
|---|---|
| **4.0 GA (contract adopted)** | What core's 4.0 GA needs from this repository (core epic spec-kitty#6518). Contract 0.1.0 and the activation minor (#125) are released. Core's integration, the generated client and the routing proxy of [ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/), is no longer part of GA: core changes its integration once the service is built, in the 4.x cutover ([ADR-0055](/adr/0055-cutover-sequencing-the-java-service-first/) D1). The service answers the first offering reads against open-packs on the canonical `Pack` model, with the artifact kinds and edge relations modelled here ([ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/)). |
| **4.x cutover** | The charter offering's Python code in core is removed or inactive. Every Charter Pack and offering read goes through this repository's API, called from core's seam (`charter.offering.api`, the anti-corruption layer at core's entry point). This is [strangler stage 4](/architecture/strangler-transition/), "Java reads complete". Writes may still be Python. The service is built first, then core's integration, and the `depends_on` resolver ([#145](https://github.com/spec-kitty/spec-kitty-charter/issues/145), [#146](https://github.com/spec-kitty/spec-kitty-charter/issues/146)) is on the cutover path ([ADR-0055](/adr/0055-cutover-sequencing-the-java-service-first/)). |
| **Post-cutover** | Open-ended. This is the work that builds on the cut-over charter: write migration (stage 5 onward), new charter capabilities, and quality follow-ups that block nothing. |
| **Infra & Enablers** | Not a delivery target either, and never on the release critical path: background work, enablers, automation and deployment concerns. An item whose outcome can change the contract before 1.0.0 moves to the milestone that needs it. |
| **Backlog** | Not a delivery target. This holds design spikes and ideas that are not yet committed to a milestone. |

The operator set this definition on 2026-10-19 and refined how the cutover happens on 2026-10-20
([ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/),
[ADR-0022](/adr/0022-contract-versions-start-at-0-x/)):

- **Contract first.** Core vendors a client generated from the published contract (0.x, from
  0.1.0) and routes every operation through a proxy at its seam. Each operation starts on core's
  Python.
- **One operation at a time.** Each operation moves to a shadow (compared with the service), then
  to the service, on its own, fail closed, with no Python fallback. ~~The conformance corpus gates
  each move.~~ Its BDD `@contract` scenarios and contract tests gate each move
  ([ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) D1).
- **1.0.0 before the first remote route.** The contract stays 0.x until the operator judges it
  stable, and is 1.0.0 before any operation is answered by the service.
- **Scope.** The cutover covers every offering read, including activation's reads, and the
  activation store; compile keeps a guarded Python read path until it is the first write moved to
  Java. Removing the Python reads is the
  [point of no return](/architecture/strangler-transition/#stepping-back).

Stage 1, the import move of core ADR `2026-10-12-2` (D-2), is done in core (spec-kitty#6188).
Core makes adopting the charter activation contract a 4.0 GA deliverable (spec-kitty#6518).

## Distribution

Refreshed 2026-11-10 against the tracker (142 open issues). Closed issues have left the tables. The order of work
inside and across milestones is the slice order in the [Roadmap](/plans/roadmap/#toward-a-service-core-can-use).

### 4.0 GA (contract adopted)

Not close: four P0s are open, two of them in flight.

| Issue | Title | Slice | Note |
|---|---|---|---|
| #407, #99 | Agent-profile reads; mission-type reads (with #96, #148) | A | In flight |
| #405 | Language profiles cannot reach their conventions styleguide | B | Fixed upstream in open-packs; the pin bump remains |
| #406 | Reserved operations' `501` is logged as an error with a stack trace | D | With #454 |
| #97 | R3: merged CRG and graph queries, decide and shape | C | The effective graph read exists (PR #448) |
| #45, #196 | Contract release publishing; tag ruleset | D | Built; open until a release is cut, which the operator holds |
| #312, #367 | Orphaned `ActionNotFound` response; refactor the over-limit methods | D | #367 before the complexity gate #371 |
| #206, #397, #408, #409, #411 | `required_<plural>` to presets; retire the CRG walk; cascade controls; and follow-ups | B to D | #397: check whether #448 made it moot. #411 (unserved kind gives a bare 400) shrinks with slice A |

Also in 4.0 GA since 2026-11-10: the usability-run issues #450 to #454, the ADR-0066 track #424, #434, #435 and #438
(#428 and #437 close with PR #460), the ADR requests #456 to #458, #431, #432, and #148, moved forward from
Post-cutover. #103 closed as shaped into #450.

### 4.x cutover

Far: stages 3 and 4 have not started, and the read inventory is mostly undecided.

| Issue | Title | Note |
|---|---|---|
| #6 | Epic: Java charter service built against an OpenAPI contract | Children #9, #13, #14, #47, #50, #51, #68, #127, #128, #130, #133 |
| #13 | Core ingests the contract | Slice E; phase 2 ([ADR-0055](/adr/0055-cutover-sequencing-the-java-service-first/) D1) |
| #14 | Read shadow | Runs in core's proxy, one operation at a time (ADR-0021 D4) |
| #9, #72 | Conformance corpus; conformance as a blocking gate | **Re-scoped 2026-11-10 to [ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) D1:** BDD `@contract` scenarios and contract tests gate each switch |
| #47 | `x-source` citation checker | PR #436 checks the paths; narrow to the symbol check or close |
| #127, #128 | Hosted-first reshape; mint contract 1.0.0 | Before the first switched route (ADR-0022 D6) |
| #130, #133 | Refusal-code enums; `x-extensible-enum` | Contract shape; decide before 1.0.0 |
| #94, #96, #98, #100 to #102, #104 to #110, #112 | Epic and steps: the cutover read inventory | #96 and #100 are in slice A |
| #29, #30, #31, #33, #15, #38, #68 | Behavior-first overhaul and the scenario net | The scenarios now gate each switch, so this net is on the critical path |
| #69, #70 to #74, #76, #332, #343 | Stages 3 and 4 | #70 and #71 are in slice E. #76 still cites the closed #75 |
| #357 to #360, #67, #185, #306 | Resolver deferrals and follow-ups | #67: the 2026-11-02 triage proposed Post-cutover. #185: re-check against [ADR-0065](/adr/0065-pack-registration-replaces-the-whole-pack/) |

### Post-cutover

Open-ended by definition: Creeds and Values (#16 to #23), pack governance (#55, #57 to #61), exclusions (#56, #65),
the `impacts` vocabulary (#77, #79, #80, #82), offering extensions (#40, #42), quality follow-ups (#49, #52, #36), the
core follow-ups epic #361 (#333 to #342), and #142, #149, #154, #160, #163, #164, #178, #181, #184, #349, #350, #355.

- **#148** (mission types and step contracts as full kinds) moved forward to 4.0 GA on 2026-11-10: the usability run
  makes it a core-readiness blocker, and slice A delivers it.
- **The id spelling at the seam** (slug versus `PROJECT_001`), under #361, blocks the first core integration: pull it
  into slice E.
- **#181** is overtaken by [ADR-0063](/adr/0063-pack-mutability-policy-file/) (the mutability policy file).

### Infra & Enablers

| Issue | Title | Note |
|---|---|---|
| #207, #211, #212, #214 | Epic: read, fetch and try the contract | Close: three steps left |
| #217, #219 to #223, #225 | Epic: a generated Java client and client guidance | Not started |
| #226, #230, #234 | Epic: hosted configuration and the container image | |
| #121, #134, #140, #166, #197, #205, #243, #308, #324, #371 | CI, docs tooling and hardening | |

**Added 2026-11-10:** mutation CI (#387, #388, #389, and #461, which closes once a nightly is green),
the hardening batch (#380 to #383, #386, #416, #417, #426, #429, #439, #443, #444, #447), and the SonarCloud cleanup,
issues #463 to #465 below.

### Backlog

Issues #143, #151, #152, #157, #158, #159.

### Placed on 2026-11-10

The issues that had no milestone were placed as proposed: the usability-run gaps, the ADR-0066 track, the ADR
requests #456 to #458 and #431 and #432 in 4.0 GA; #433, #404, #412, #396, #203 and #67 in Post-cutover; #182, #132
and #254 in the 4.x cutover (#132 and #254 still to re-scope against the 2026-10-28 ruling); mutation CI and the
hardening batch in Infra & Enablers. #136 closed as superseded by
[ADR-0029](/adr/0029-charter-glossary-is-owned-here/).

### SonarCloud findings

The nightly scan (2026-11-10) shows 43.4k lines, 94.6% coverage and 1.0% duplication, with 31 open issues: Security
rating D, from one vulnerability, and Reliability rating E, from three critical bugs. Three work items, filed in
Infra & Enablers on 2026-11-10:

1. **Security and runtime bugs** ([#463](https://github.com/spec-kitty/spec-kitty-charter/issues/463), P1): S5443, a world-writable temp directory used as git HOME in
   `GitVersionCheck.java:117`; S6416 ×3, reachable exceptions in `ActivationFileCodec.java:167,181` and
   `PackFileKind.java:527`; S2095, `RegistrationJobs` never closed in `RegistrationWiring.java:185`; S2583 ×2, conditions
   that are always false in `ExposureStartupCheck.java:245` and `Relationship.java:89`; S6856 and S3752 on
   `ProblemErrorController.java:42`.
2. **Test hygiene** ([#464](https://github.com/spec-kitty/spec-kitty-charter/issues/464)): S6103 ×2, assertions that never run in `PackRegistrationServiceTest:813,819`; S2699; S1872; S8786
   ×2.
3. **Conventions, needing a ruling** ([#465](https://github.com/spec-kitty/spec-kitty-charter/issues/465)): S1845 ×12 (constant and member differ only in case) and S1314 ×3 (octal literals,
   zip permission bits). Either rename or disable them in the quality profile with a recorded reason. S9428 on
   `Fingerprint.java:17` is likely a false positive and can be closed with a reason.

## Decided on 2026-10-19

The operator ruled on the open questions this page raised. The decisions are provisional.

- **Contract 1.0** reserves what the cutover needs and models the `impacts` relation. New
  capabilities (activation and exclusions, pack policy, the creed) arrive as additive
  contract sets that clients opt into (ADR-0009).
- **Persistent deactivation** is fixed in core now: #62 to #64, #66 and #67 moved to the
  cutover; #65 stays here (ADR-0013).
- **Creeds and Values** is split by layer: #77 carries the `impacts` vocabulary, #16 the
  charter value layer. #81, #83, #84 and #85 were closed as duplicates (ADR-0014).
- **The observation period (#75)** was dropped: the conformance corpus gates the switch-over (now the BDD scenarios and contract tests, ADR-0059 D1).
- **The conformance corpus** is a harness over core's CLI (ADR-0016). *Superseded:* it is no longer the oracle
  (ADR-0055 D3) or the switch gate (ADR-0059 D1). **Pack schemas** are
  imported from core's models and owned here (ADR-0017). #12 was closed (ADR-0008), and #87 was
  closed once core fixed org precedence.
