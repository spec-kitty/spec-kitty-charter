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
| **4.x cutover** | The charter read side cuts over to this repository for the 4.x line. The contract is released, and core vendors it. Core's seam returns DTOs generated from the contract. The Java read shadow passes the conformance corpus, and every divergence is explained. This is [strangler stage 2](/architecture/strangler-transition/), and the milestone closes with epic #6. |
| **Post-cutover** | Open-ended. This is the work that builds on the cut-over charter: Java authoritative reads (stage 3) and write migration, new charter capabilities, and quality follow-ups that block nothing. |
| **Backlog** | Not a delivery target. This holds design spikes and ideas that are not yet committed to a milestone. |

The cutover milestone stops at the read shadow because the tracker stops there: epic #6
ends at step 7, and the strangler stages take over after it. Stage 1 is the import move of
core ADR `2026-10-12-2` (D-2), carried by epic spec-kitty#6099. It gates 4.0.0 GA. It is core
work, so it is not tracked here, but #13 and #14 cannot close without it.

## Proposed distribution

### 4.x cutover

The cutover path, the work that keeps the contract releasable, and the conformance net.

| Issue | Title | Why it is here |
|---|---|---|
| #6 | Epic: Java charter service built against an OpenAPI contract | Its closing step is the milestone's exit |
| #7, #8 | Contract setup; Gradle build | Roadmap steps 1 and 2. Delivered by PR #54, which is still open; both close when it merges |
| #9 | Conformance corpus, first slice | Roadmap step 3; the shadow is judged against it |
| #10, #11 | Domain and YAML read adapter; org and project layers | Roadmap steps 4 and 5 |
| #12 | Decide how the Python charter reaches this repository | Gates #13 ([open question 2](/plans/open-questions/)) |
| #13 | Core ingests the contract | Roadmap step 6 |
| #14 | Read shadow | Roadmap step 7; the exit criterion |
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

## Open decision

**What "cutover" means.** This page takes the read shadow, stage 2, as the cutover. If the
operator means Java authoritative reads (stage 3), the milestone also needs issues for the
stage 3 entry evidence: conformance as a blocking gate, and proven freshness and failure
behavior. No such issues exist yet.
