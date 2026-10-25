---
okf_version: "0.2"
title: Development notes
description: Pre-decision investigation notes that feed future ADRs — findings captured before a decision is recorded, not conventions in force.
doc_status: draft
updated: 2026-10-25T00:00:00Z
type: Guide
kind: Hub
audience:
  - profile: charter-service-contributor
    guidance_text: Read a note for the investigation behind a decision that is still being shaped; the decision itself, once made, lands in an ADR.
  - profile: core-maintainer
    guidance_text: Read these for design questions under active investigation before they become decision records.
---

# Development notes

Investigation notes captured at a point-cut before a decision is recorded. Each note states a
question, the evidence gathered, and a recommendation; the decision it feeds lands in an
[ADR](/adr/), not here. Notes are not conventions in force.

- [Relationship identity and the multigraph-collapse question](/development/notes/relationship-identity-collapse-review/) —
  whether DRG multigraph pairs can collapse to one unidirectional relationship, the data and
  architecture findings, and the conditions a collapse must carry. Feeds a future identity ADR that
  would revise [ADR-0025](/adr/0025-relationships-first-class-and-lazy-repository-walk/).
