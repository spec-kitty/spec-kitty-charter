---
okf_version: "0.2"
title: "Cutover sequencing ruled: the Java service first, accepted against the design"
description: "ADR-0055 records the operator rulings of 2026-11-03: build the service before core's integration, on the depends_on resolver, accepted against the design."
doc_status: active
updated: 2026-11-03T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0055-cutover-sequencing-the-java-service-first
  - adr/0021-contract-first-seam-generated-client-and-routing-proxy
  - adr/0010-cutover-scope-and-seam-boundary
  - adr/0016-conformance-corpus-harness-over-core-cli
  - plans/roadmap
audience:
  - profile: governance-lead
    guidance_text: Read this for the new order of the cutover and what core now waits for.
  - profile: charter-service-contributor
    guidance_text: Read this for what is on the cutover path and how the service is now accepted.
---

# 2026-11-03 — Cutover sequencing ruled: the Java service first, accepted against the design

The operator ruled how the 4.x cutover is built, while planning the resolution domain (#10, #11) and the activation
register (#291). [ADR-0055](/adr/0055-cutover-sequencing-the-java-service-first/) records the rulings.

**Before:**

- Core's client, proxy and shadow were to be built alongside the service's domain.
- The `depends_on` resolver was planned after the cutover.
- The service was to be accepted by matching core's Python answers on a conformance corpus.

**Now:**

- **Two phases.**
  1. The service is built in Java: the layer merge, activation, cascade, the org and project layers, the effective
     reads and the activation register.
  2. Only then does core change its integration.

  ADR-0021 D2 and D6 are amended.
- **The resolver comes first.** The layer merge is built on ADR-0048's `depends_on` resolver, so #145 and #146 move
  to the 4.x cutover milestone.
- **The design is the oracle.** The packs are authored or updated to the design first, and the service is accepted
  against what the ADRs say they resolve to, not against core's Python answers. This supersedes the corpus over
  core's CLI as the acceptance criterion (ADR-0016; ADR-0010; ADR-0021 D5). What gates each core switch is an open
  question for phase 2.
- **Enhancing artifacts.** An enhancing artifact lives beside the kind it adds to, with its `enhances` edge in the
  pack's graph shard (ADR-0053 open question 2).
- The roadmap, the strangler-transition page and the milestones page follow the two phases.
