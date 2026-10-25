---
title: Milestones
description: The tracker's milestones, what closes each one, and the proposed distribution of the open spec-kitty-charter issues across them.
doc_status: draft
updated: 2026-10-25T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/roadmap
  - plans/overhaul-approach
  - architecture/strangler-transition
audience:
  - profile: charter-service-contributor
    guidance_text: Read this to find which milestone an issue belongs to and what has to close before the cutover.
  - profile: core-maintainer
    guidance_text: Read this for what the 4.x cutover needs from core, and which charter work waits until after it.
  - profile: governance-lead
    guidance_text: Read this for when the creed, pack governance and deactivation work is scheduled.
---

# Milestones

The issue tracker groups the open work into four milestones. This page says what closes
each milestone and which issue goes where. The checkable steps are in the
[Roadmap](/plans/roadmap/), and the phases in the [High-level approach](/plans/overhaul-approach/).
All `#N` are `spec-kitty-charter` issues unless prefixed with another repository.

> **Status:** in effect (updated 2026-10-24). The assignment is provisional and changes as the plan
> does. Nine open issues have no milestone yet; they are listed under
> [Not yet assigned](#not-yet-assigned).

## The milestones

| Milestone | Done when |
|---|---|
| **4.0 GA (contract adopted)** | What core's 4.0 GA needs from this repository (core epic spec-kitty#6518). Contract 0.1.0 and the activation minor (#125) are released. Core's generated client and routing proxy run with every operation still on core's Python ([ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/)). The service answers the first offering reads against open-packs on the canonical `Pack` model, with the artifact kinds and edge relations modelled here ([ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/)). |
| **4.x cutover** | The charter offering's Python code in core is removed or inactive. Every Charter Pack and offering read goes through this repository's API, called from core's seam (`charter.offering.api`, the anti-corruption layer at core's entry point). This is [strangler stage 4](/architecture/strangler-transition/), "Java reads complete". Writes may still be Python. |
| **Post-cutover** | Open-ended. This is the work that builds on the cut-over charter: write migration (stage 5 onward), new charter capabilities, and quality follow-ups that block nothing. |
| **Backlog** | Not a delivery target. This holds design spikes and ideas that are not yet committed to a milestone. |

The operator set this definition on 2026-10-19 and refined how the cutover happens on 2026-10-20
([ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/),
[ADR-0022](/adr/0022-contract-versions-start-at-0-x/)):

- **Contract first.** Core vendors a client generated from the published contract (0.x, from
  0.1.0) and routes every operation through a proxy at its seam. Each operation starts on core's
  Python.
- **One operation at a time.** Each operation moves to a shadow (compared with the service), then
  to the service, on its own, fail closed, with no Python fallback. The conformance corpus gates
  each move.
- **1.0.0 before the first remote route.** The contract stays 0.x until the operator judges it
  stable, and is 1.0.0 before any operation is answered by the service.
- **Scope.** The cutover covers every offering read, including activation's reads, and the
  activation store; compile keeps a guarded Python read path until it is the first write moved to
  Java. Removing the Python reads is the
  [point of no return](/architecture/strangler-transition/#stepping-back).

Stage 1, the import move of core ADR `2026-10-12-2` (D-2), is done in core (spec-kitty#6188).
Core makes adopting the charter activation contract a 4.0 GA deliverable (spec-kitty#6518).

## Distribution

### 4.0 GA (contract adopted)

Created on 2026-10-24 by the operator. Two tracks run in parallel: unblock core by publishing the
contract, and model the artifact kinds before more of them are exposed.

Delivered so far: issues #120 and #173, by PR #177 (every read answers with the source fingerprint
it verified).

| Issue | Title | Why it is here |
|---|---|---|
| #119 | Duplicate DTOs from code generation | Core's generated client must be clean before 0.1.0 |
| #45 | Contract release publishing | Core vendors a released, checksummed bundle; the first is 0.1.0 |
| #125 | The activation set, as a 0.x minor | Core's activation alignment (spec-kitty#6518) types against it ([ADR-0020](/adr/0020-offering-and-activations/)) |
| #13 | Core ingests the contract: generated client and routing proxy | The contract is core's seam from 0.1.0 (ADR-0021) |
| #126 | Offering reads under `/offering`, the API root, `_links`, and the generic artifact routes | The reads PR #139 removed come back on the canonical `Pack` model ([ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/)) |
| #131 | `charter.schema.json` lacks `activated_glossary_packs` | The activation mapping needs the schema to match what core writes |
| #171 | `CharterArtifact` family: its home under ADR-0006, and what every kind has | Settle the model before more kinds |
| #169 | Doctrine-kinds adapter tidy-ups (kind registry) | A generic artifact read seam before more kinds |
| #167 | Own the doctrine-artifact edge-relation table here | The relations each field creates are this repository's to define |
| #168 | Toolguide and procedure as first-class pack artifacts | After #169 and #171 |
| #175 | Contract: no fingerprint declaration on refusals; the fingerprint required on artifact 200s | A contract shape change, so it belongs before 1.0.0 |
| #176 | `OfferingUnreadable`'s examples name codes the service never raises | The contract must describe what the built reads answer |
| #174 | A pack root configured through a symlink loads as an empty pack | A symlinked open-packs checkout would silently serve nothing |

### 4.x cutover

The cutover path, the work that keeps the contract releasable, and the conformance net.
Delivered so far: issues #7 and #8 (contract and build), #34 (storage behind repository ports,
in PRs #137 and #139), #44 (decided by ADR-0020), #48 (pack-level policy), #53, #113 (reserved
surface), #114 (canonical pack schemas) and #124 (0.x versioning, PR #129).

| Issue | Title | Why it is here |
|---|---|---|
| #6 | Epic: Java charter service built against an OpenAPI contract | Stages 0 to 2 of the cutover |
| #127 | The hosted-first reshape, as a 0.x minor | The contract must not assume a worktree or project files at 1.0.0 |
| #128 | Mint contract 1.0.0 | The first route switched to the service needs it (ADR-0022 D6) |
| #9 | Conformance corpus: a harness over core's CLI | Roadmap step 3; each switch is judged against it ([ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/)) |
| #94, #95 to #112 | Epic and steps: the cutover read inventory (R1 to R18) | Decides what the contract, and so the corpus, must cover |
| #10, #11 | Domain and YAML read adapter; org and project layers | Roadmap steps 4 and 5 |
| #14 | Read shadow | Runs in core's proxy, one operation at a time (ADR-0021 D4) |
| #46 | `x-stability-level` for provisional operations | Under review: no provisional operation remains, and the 0.x rule may cover the need |
| #47 | `x-source` citation checker | The citations have to stay true while #10 and #11 grow the domain |
| #50 | Build structure follow-ups | Generate `charter-api` from the released bundle, so only one parser resolves `$ref` |
| #51 | Supply-chain and CI hygiene | The wrapper checksum and signature ruling belong before the first release |
| #29 | Epic: behavior-first overhaul (P1 to P3) | The scenarios are the conformance net the cutover relies on |
| #30, #31, #32, #33 | P1: boundary scenarios, Layer-B harness, doctrine in force, docsite rendering | The P1 outcome |
| #15 | Nightly acceptance tests with Allure | The evidence trail for the shadow |
| #38 | Pack-validate CI guard | The "healthy" claim has to be checked before the packs govern anything |
| #68 | Validate scenario responses against the contract bundle | Keeps the scenario net honest against the released contract |
| #115 | Pack doctor parity on open-packs | The first pack goal: Java verdicts match core's validator |
| #62 to #64, #66, #67 | Persistent deactivation, core side | Fixed in core now, ahead of the cutover, so the corpus records the fixed answers |

#### Stages 3 and 4

These follow the evidence each stage needs in
[Strangler transition](/architecture/strangler-transition/). Under ADR-0021 they happen one
operation at a time.

| Issue | Stage | Done when |
|---|---|---|
| #69 Epic: retire the Python charter reads | 3, 4 | The children below are closed and core no longer answers a charter read in Python |
| #70 Launcher and service lifecycle | 3 | The service runs wherever a routed operation needs it. Needed by the first switch, not before (ADR-0021 D5) |
| #71 Cross-OS packaging of the service | 3, 4 | Each supported OS gets a service artifact ([open question 5](/plans/open-questions/)) |
| #72 Conformance as a blocking gate | 3, 4 | A divergence on the supported corpus fails core's CI. It gates each operation's switch |
| #73 Stale, slow and unavailable service behavior | 3, 4 | The seam fails closed per routed operation: a down or slow service is a named error, and stale input is refused |
| #74 Java authoritative reads | 3, 4 | Every read is routed to the service |
| #76 Remove the Python read paths from core | 4 | The offering's read code is deleted or inactive in core, and a ratchet keeps it that way |

### Post-cutover

| Issue | Title | Why it is here |
|---|---|---|
| #16, #17 to #23 | Epic and steps: Creeds and Values | New charter capability. #17 (re-baseline) and #18 (prose layer, which gates nothing) can start any time |
| #55, #57 to #61 | Epic and steps: Charter Pack governance (mutability policy) | New charter capability |
| #56, #65 | Epic; projection and resolution honor exclusions | The epic, and the step that lands on the Java offering side once reads go through the API. #62 to #64, #66 and #67 are fixed in core now and sit in 4.x cutover |
| #77, #78 to #80 | Epic and steps: the `impacts` vocabulary for the DRG | The graph layer of Creeds and Values. #16 carries the charter value layer, now including #82 |
| #40 | Recommended-tools list per activated pack | Offering extension |
| #42 | Pack-declared profile-role resolution | Offering extension; core work comes first |
| #49 | Derive the hand-kept lists | Quality follow-up; blocks nothing |
| #52 | Closure checker and YAML adapter follow-ups | Quality follow-up; blocks nothing |
| #36, #37 | Bootstrap review follow-ups; home the internal pack upstream | Wait on `spec-kitty-open-packs` |

### Backlog

| Issue | Title | Why it is here |
|---|---|---|
| #24 | Design spike: deployable charter backend service | Hosting direction now set by ADR-0020 D12; caching and precompute remain open |

### Not yet assigned

Filed after the last distribution, or never placed; to be placed when the milestones are next
revised.

| Issue | Title | Note |
|---|---|---|
| #132 | Remaining YAML scalar differences from core | Conformance detail |
| #133 | `x-extensible-enum` for open code enums | Contract shape; decide before 1.0.0 |
| #130 | Narrow the refusal-code enum per HTTP response | Contract shape; decide before 1.0.0 |
| #121 | Worktree token: refuse a placeholder resolved from the command line | Local deployment profile |
| #140 | ArchUnit slice-coverage meta-rule | Partly delivered by PR #141 |
| #166 | CI gate: single-source the path taxonomy | CI hygiene |
| #134 | `check_docs.py` slugify drops underscores | Docs tooling |
| #135 | Redraw the older descriptor diagrams on the pack schemas | Docs |
| #136 | Ask core to carry the pack terms in its glossary | Cross-repository |

## Decided on 2026-10-19

The operator ruled on the open questions this page raised. The decisions are provisional.

- **Contract 1.0** reserves what the cutover needs and models the `impacts` relation. New
  capabilities (activation and exclusions, pack policy, the creed) arrive as additive
  contract sets that clients opt into (ADR-0009).
- **Persistent deactivation** is fixed in core now: #62 to #64, #66 and #67 moved to the
  cutover; #65 stays here (ADR-0013).
- **Creeds and Values** is split by layer: #77 carries the `impacts` vocabulary, #16 the
  charter value layer. #81, #83, #84 and #85 were closed as duplicates (ADR-0014).
- **The observation period (#75)** was dropped: the conformance corpus gates the switch-over.
- **The conformance corpus** is a harness over core's CLI (ADR-0016). **Pack schemas** are
  imported from core's models and owned here (ADR-0017). #12 was closed (ADR-0008), and #87 was
  closed once core fixed org precedence.
