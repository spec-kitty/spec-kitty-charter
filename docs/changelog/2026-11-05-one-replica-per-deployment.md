---
okf_version: "0.2"
title: A deployment runs one replica
description: "ADR-0060 records the operator's ruling on #233. A containerized deployment runs exactly one replica, because the activation copy and the ETags live in memory."
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
one replica** ([#233](https://github.com/spec-kitty/spec-kitty-charter/issues/233)). The service keeps two kinds of
state in memory, per project: the write-through copy of the active charter (ADR-0033) and the ETags (ADR-0018). A
second replica would answer stale reads and pass stale conditional writes.

Running more than one replica waits until a hosting option is chosen. That record then decides on shared storage or
sticky routing, probes, draining and session state. [Run the service in a container](/guides/run-in-a-container/)
now lists "single replica" under its limits.
