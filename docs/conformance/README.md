---
okf_version: "0.2"
title: Conformance
description: Where the charter service's answers differ from core's on purpose, and the single register that records each difference.
doc_status: draft
updated: 2026-10-19T00:00:00Z
type: API
kind: Hub
related:
  - adr/0016-conformance-corpus-harness-over-core-cli
audience:
  - profile: contract-consumer
    guidance_text: Read this to find where the service answers differently from core and why.
  - profile: charter-service-contributor
    guidance_text: Find the register to update when an answer differs from core on purpose.
---

# Conformance

The service must give the same answer as core's Python charter for the same input, except where
a difference is decided and recorded. [ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/)
sets the conformance corpus and the harness that hold the two together.

- [Divergence register](/conformance/divergence-register/) — the single register of deliberate
  differences, each with a stable id, grouped by block.
