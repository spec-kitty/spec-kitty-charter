---
okf_version: "0.2"
title: A deployment runs one replica
description: "ADR-0061 records the operator's ruling on #233. A containerized deployment runs exactly one replica, because pack writes are serialized inside one process only."
doc_status: active
updated: 2026-11-05T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0061-one-replica-per-deployment
  - guides/run-in-a-container
audience:
  - profile: governance-lead
    guidance_text: Read this before you scale a containerized charter service.
---

# 2026-11-05 — A deployment runs one replica

**[ADR-0061](/adr/0061-one-replica-per-deployment/) records that a deployment of the charter service runs exactly
one replica** ([#233](https://github.com/spec-kitty/spec-kitty-charter/issues/233)). Pack writes are serialized by a lock
inside one process (`PackWriteLocks`; [one writer per pack](/architecture/service-lifecycle/#one-writer-per-pack)). A second replica over the same configuration root could therefore pass the same
`If-Match` and lose a write. The active charter's service-side copy (ADR-0033) is not built yet; once it is, the same one-writer rule applies.

Running more than one replica waits until a hosting option is chosen. The deferred scope (shared storage or
sticky routing, probes, draining, session state and tenancy) is tracked in
[#396](https://github.com/spec-kitty/spec-kitty-charter/issues/396). [Run the service in a container](/guides/run-in-a-container/)
now lists "single replica" under its limits.
