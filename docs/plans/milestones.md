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

> **Status:** proposal (2026-10-19). The milestones exist on the tracker; no issue is assigned to
> one yet. The distribution below is waiting for the operator's review.

## The milestones

| Milestone | Done when |
|---|---|
| **4.x cutover** | The charter offering's Python code in core is removed or inactive. Every Charter Pack and offering read goes through this repository's API, called from core's seam (`charter.offering.api`, the anti-corruption layer at core's entry point). This is [strangler stage 4](/architecture/strangler-transition/), "Java reads complete". Writes may still be Python. |
| **Post-cutover** | Open-ended. This is the work that builds on the cut-over charter: write migration (stage 5 onward), new charter capabilities, and quality follow-ups that block nothing. |
| **Backlog** | Not a delivery target. This holds design spikes and ideas that are not yet committed to a milestone. |

The operator set this definition on 2026-10-19. The cutover therefore runs through three
strangler stages: the read shadow (stage 2, epic #6), Java authoritative reads (stage 3) and
the retirement of the Python reads (stage 4). Stage 4 is the
[point of no return](/architecture/strangler-transition/#stepping-back). Stage 1 is the
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

#### Not yet tracked

The cutover needs these issues, and none exists yet. They follow the evidence each stage
needs in [Strangler transition](/architecture/strangler-transition/).

| Proposed issue | Stage | Done when |
|---|---|---|
| Epic: retire the Python charter reads | 3, 4 | The children below are closed and core no longer answers a charter read in Python |
| Launcher and service lifecycle | 2 | Core starts and stops the service per worktree. #14 needs this ([open question 8](/plans/open-questions/)) |
| Cross-OS packaging of the service | 3 | Each supported OS gets a service artifact ([open question 5](/plans/open-questions/)). This is a fallback-retirement criterion |
| Conformance as a blocking gate | 3 | A divergence on the supported corpus fails core's CI, not only diagnostics |
| Stale and unavailable service behavior | 3 | Tests prove what the seam does when the service is stale, slow or down. The Python fallback is loud and observable, and never silently gives a second answer |
| Java authoritative reads | 3 | The seam adapter answers from Java, with Python as the observable fallback |
| Observation period | 4 | An agreed period of Java-authoritative reads ends with no unresolved semantic divergence |
| Remove the Python read paths from core | 4 | The offering's read code is deleted or inactive in core. Every read in core goes through the seam to the API, and a ratchet keeps it that way |

### Post-cutover

| Issue | Title | Why it is here |
|---|---|---|
| #16, #17 to #23 | Epic and steps: Creeds and Values | New charter capability. #17 (re-baseline) and #18 (prose layer, which gates nothing) can start any time |
| #55, #57 to #61 | Epic and steps: Charter Pack governance (mutability policy) | New charter capability |
| #56, #62 to #67 | Epic and steps: persistent deactivation | Defects in core's Python charter today; they are built here on the Java side |
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

## Candidates to move

- **#57 and #62.** The policy ADR and the ledger-authority decision are design work only.
  If the 1.0 contract should carry the `enforced` level or persisted exclusions, move them
  to the cutover. Adding either later is a minor contract change, so the default is to wait.
- **#63 to #66.** These describe behavior users hit in core today. Core may fix them before
  the cutover. If it does, the conformance corpus records the fixed answers, and the
  Java side inherits them through #9 and #11.
- **Creed drafts (E1).** Eight drafted issues duplicate epic #16 and #17 to #23, and were not
  filed. The proposal is to fold what they add (the `impacts` vocabulary and its migration)
  into epic #16 as new steps, in Post-cutover.

- **#62 to #67 against the observation period.** Persistent deactivation changes read
  answers. If core fixes it during the observation period, the corpus and both
  implementations have to change together, or the period restarts.
