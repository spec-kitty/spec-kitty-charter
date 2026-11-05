---
okf_version: "0.2"
title: A deployment runs one replica
description: "ADR-0060 records the operator's ruling on #233. A containerized deployment runs exactly one replica, because pack writes are serialized inside one process only."
doc_status: active
updated: 2026-11-05T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0060-one-replica-per-deployment
  - guides/run-in-a-container
audience:
  - profile: governance-lead
    guidance_text: Read this before you scale a containerized charter service.
---

# 2026-11-05 — A deployment runs one replica

**[ADR-0060](/adr/0060-one-replica-per-deployment/) records that a deployment of the charter service runs exactly
one replica** ([#233](https://github.com/spec-kitty/spec-kitty-charter/issues/233)). Pack writes are serialized by a lock
inside one process (ADR-0026). A second replica over the same configuration root could therefore pass the same
`If-Match` and lose a write. The active charter's copy of ADR-0033, once it is built, is also held per process.

Running more than one replica waits until a hosting option is chosen. That record then decides on shared storage or
sticky routing, probes, draining and session state. [Run the service in a container](/guides/run-in-a-container/)
now lists "single replica" under its limits.
