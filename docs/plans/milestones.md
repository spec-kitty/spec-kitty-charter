---
title: Milestones
description: The tracker's milestones, what closes each one, and the proposed distribution of the open spec-kitty-charter issues across them.
doc_status: draft
updated: 2026-10-19
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

The issue tracker groups the open work into three milestones. This page says what closes
each milestone and which issue goes where. The checkable steps are in the
[Roadmap](/plans/roadmap/), and the phases in the [High-level approach](/plans/overhaul-approach/).
All `#N` are `spec-kitty-charter` issues unless prefixed with another repository.

> **Status:** in effect (2026-10-19). Every open issue is assigned to the milestone listed
> here. The assignment is provisional, and it changes as the plan does.

## The milestones

| Milestone | Done when |
|---|---|
| **4.x cutover** | The charter offering's Python code in core is removed or inactive. Every Charter Pack and offering read goes through this repository's API, called from core's seam (`charter.offering.api`, the anti-corruption layer at core's entry point). This is [strangler stage 4](/architecture/strangler-transition/), "Java reads complete". Writes may still be Python. |
| **Post-cutover** | Open-ended. This is the work that builds on the cut-over charter: write migration (stage 5 onward), new charter capabilities, and quality follow-ups that block nothing. |
| **Backlog** | Not a delivery target. This holds design spikes and ideas that are not yet committed to a milestone. |

The operator set this definition on 2026-10-19. The cutover runs through the read shadow
(stage 2, epic #6), then one switch-over in which Java becomes authoritative and the Python
offering reads retire (stages 3 and 4 ship together, with no Python fallback). The
conformance corpus alone gates the switch-over. The cutover covers every offering read,
including activation's reads of offering data; compile keeps a guarded Python read path until
it is the first write moved to Java. The switch-over is the
[point of no return](/architecture/strangler-transition/#stepping-back). These are
provisional decisions, recorded as ADR-0010 and ADR-0011 in the decisions PR. Stage 1 is the
import move of core ADR `2026-10-12-2` (D-2), carried by epic spec-kitty#6099. It gates 4.0.0
GA. It is core work, so it is not tracked here, but nothing in this milestone can answer a
production read without it.

## Proposed distribution

### 4.x cutover

The cutover path, the work that keeps the contract releasable, and the conformance net.

| Issue | Title | Why it is here |
|---|---|---|
| #6 | Epic: Java charter service built against an OpenAPI contract | Stages 0 to 2 of the cutover |
| #7, #8 | Contract setup; Gradle build | Roadmap steps 1 and 2. Delivered by PR #54, which is still open; both close when it merges |
| #9 | Conformance corpus, first slice | Roadmap step 3; the shadow is judged against it |
| #10, #11 | Domain and YAML read adapter; org and project layers | Roadmap steps 4 and 5 |
| #12 | Decide how the Python charter reaches this repository | Gates #13 ([open question 2](/plans/open-questions/)) |
| #13 | Core ingests the contract | Roadmap step 6 |
| #14 | Read shadow | Roadmap step 7; stage 2, which stages 3 and 4 build on |
| #45 | Contract release publishing | #13 needs a released, checksummed bundle to vendor |
| #46 | `x-stability-level` for provisional operations | The release gate must not report a change to a provisional operation as breaking |
| #47 | `x-source` citation checker | The citations have to stay true while #10 and #11 grow the domain |
| #48 | Pack-level policy in the domain | Do it before #11 adds the org and project layers |
| #50 | Build structure follow-ups | Generate `charter-api` from the released bundle, so only one parser resolves `$ref` |
| #51 | Supply-chain and CI hygiene | The wrapper checksum and signature ruling belong before the first release |
| #29 | Epic: behavior-first overhaul (P1 to P3) | The scenarios are the conformance net the cutover relies on |
| #30, #31, #32, #33 | P1: boundary scenarios, Layer-B harness, doctrine in force, docsite rendering | The P1 outcome |
| #34 | P3 design spike: storage behind a repository layer | The crosswalk places P3 before step 4. Lower priority than the rest of the milestone; it may slip |
| #15 | Nightly acceptance tests with Allure | The evidence trail for the shadow |
| #38 | Pack-validate CI guard | The "healthy" claim has to be checked before the packs govern anything |
| #53 | Docs cite spec-kitty#645 instead of epic #6099 | A small factual fix to the pages this milestone depends on |
| #68 | Validate scenario responses against the contract bundle | Keeps the scenario net honest against the released contract |
| #62 to #64, #66, #67 | Persistent deactivation, core side | Fixed in core now, ahead of the cutover, so the corpus records the fixed answers |

#### Stages 3 and 4

These follow the evidence each stage needs in
[Strangler transition](/architecture/strangler-transition/).

| Issue | Stage | Done when |
|---|---|---|
| #69 Epic: retire the Python charter reads | 3, 4 | The children below are closed and core no longer answers a charter read in Python |
| #70 Launcher and service lifecycle | 2 | Core starts the service as a daemon per worktree, CI included, and it stops when idle. #14 needs this |
| #71 Cross-OS packaging of the service | 3, 4 | Each supported OS gets a service artifact ([open question 5](/plans/open-questions/)) |
| #72 Conformance as a blocking gate | 3, 4 | A divergence on the supported corpus fails core's CI. This is the only evidence for the switch-over |
| #73 Stale, slow and unavailable service behavior | 3, 4 | The seam fails closed: a down or slow service is a named error, and stale input is refused. Freshness is fingerprinted |
| #74 Java authoritative reads | 3, 4 | The seam answers from Java, in the same release as #76 |
| #76 Remove the Python read paths from core | 4 | The offering's read code is deleted or inactive in core. Every read in core goes through the seam to the API, and a ratchet keeps it that way |

### Post-cutover

| Issue | Title | Why it is here |
|---|---|---|
| #16, #17 to #23 | Epic and steps: Creeds and Values | New charter capability. #17 (re-baseline) and #18 (prose layer, which gates nothing) can start any time |
| #55, #57 to #61 | Epic and steps: Charter Pack governance (mutability policy) | New charter capability |
| #56, #65 | Epic; projection and resolution honor exclusions | The epic, and the step that lands on the Java offering side once reads go through the API. #62 to #64, #66 and #67 are fixed in core now and sit in 4.x cutover |
| #77, #78 to #80 | Epic and steps: the `impacts` vocabulary for the DRG | The graph layer of Creeds and Values. #16 carries the charter value layer, now including #82 |
| #87 | Core: org pack precedence is inconsistent | Must be fixed before the policy enforcement (#58) |
| #40 | Recommended-tools list per activated pack | Offering extension |
| #42 | Pack-declared profile-role resolution | Offering extension; core work comes first |
| #49 | Derive the hand-kept lists | Quality follow-up; blocks nothing |
| #52 | Closure checker and YAML adapter follow-ups | Quality follow-up; blocks nothing |
| #36, #37 | Bootstrap review follow-ups; home the internal pack upstream | Wait on `spec-kitty-open-packs` |

### Backlog

| Issue | Title | Why it is here |
|---|---|---|
| #24 | Design spike: deployable charter backend service | Marked backlog by its own scope note |
| #44 | Design: register an active charter configuration by ULID handle | For discussion; a step towards hosted deployment |

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
