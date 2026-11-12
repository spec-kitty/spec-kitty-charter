---
okf_version: "0.2"
title: Guides
description: Task-oriented how-tos for contributors to the charter service and, later, for its consumers.
doc_status: draft
updated: 2026-11-12T00:00:00Z
type: Guide
kind: Hub
audience:
  - profile: charter-service-contributor
    guidance_text: Find the how-to for the task in front of you.
  - profile: pack-author
    guidance_text: Find the how-to for writing or reviewing a pack artifact.
---

# Guides

- [Contributing](/guides/contributing/) — how work lands in this repository while it is being
  bootstrapped.
- [Building the docsite](/guides/docsite/) — building and checking the doc-kitty site
  locally, and what CI publishes.
- [Building the service](/guides/service-build/) — building and testing the charter service,
  changing the contract or a dependency.
- [Run the service in a container](/guides/run-in-a-container/) — the `container` profile under
  docker compose, with its secrets mounted and none committed, and one
  container per configuration root.
- [Author a procedure](/guides/author-a-procedure/) — the checklist for a procedure: what the closed
  schema refuses, and the three refusals of ADR-0052 that review applies.
- [Compose packs](/guides/compose-packs/) — declaring `depends_on`, qualifying references across packs, settling
  a sibling conflict, and the table of the eleven composition refusals.
- [Enforce presets on an organization's projects](/guides/enforce-org-presets/) — a governing preset with must-have
  items and `policy.yaml` levels, and how to convert a pack that still ships `org-charter.yaml`.

- [Generate your own client](/guides/generate-a-client/) — fetch and verify the published contract, and generate a Java
  or Python client with the pinned generator and the options that tolerate additive 0.x changes.

More consumer how-tos (depending on a contract release, running the service locally) will be added when there is
something to depend on and run.
