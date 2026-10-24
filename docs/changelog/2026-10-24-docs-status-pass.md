---
title: ADR-0019 to ADR-0022 accepted, and the plans follow main
description: ADR-0019 to ADR-0022 are accepted (provisional); the service contract, milestones and roadmap describe the contract as PR #139 left it.
doc_status: active
updated: 2026-10-24T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0019-packs-resource-model-and-hypermedia
  - adr/0020-offering-and-activations
  - adr/0021-contract-first-seam-generated-client-and-routing-proxy
  - adr/0022-contract-versions-start-at-0-x
  - plans/milestones
  - plans/roadmap
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what changed on this date.
---

# 2026-10-24 — ADR-0019 to ADR-0022 accepted, and the plans follow main

## What changed

- [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/),
  [ADR-0020](/adr/0020-offering-and-activations/),
  [ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/) and
  [ADR-0022](/adr/0022-contract-versions-start-at-0-x/) are **Accepted (provisional)**. Their
  default decisions stand until revisited. ADR-0019 notes that `main` serves tactics on one
  route per kind, which #126 and #169 align with its generic artifact route.
- The [service contract](/api/service-contract/) lists what is built on `main`: `GET /info` and
  the tactic reads. PR #139 removed the pack listing, the refresh and the action-context read.
  The action-context decision stands for when that read returns under `/offering`.
- The [packs resource model](/architecture/packs-resource-model/) and
  [offering and activation](/architecture/offering-and-activation/) design pages now describe the
  post-#139 surface in their status callouts, and mark ADR-0019 and ADR-0020 as decided rather
  than proposed.
- [Milestones](/plans/milestones/) and the [Roadmap](/plans/roadmap/) describe the contract-first,
  per-operation cutover (ADR-0021, ADR-0022), list what is delivered, and name the open issues
  not yet placed in a milestone.
- A new milestone, **4.0 GA (contract adopted)**, holds what core's 4.0 GA needs from this
  repository, ahead of the 4.x cutover.
