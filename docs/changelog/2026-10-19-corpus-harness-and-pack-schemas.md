---
title: The corpus harness and the canonical pack schemas
description: ADR-0016 makes the conformance corpus a harness over core's CLI; ADR-0017 imports the Charter Pack schemas from core's models and owns them here.
doc_status: active
updated: 2026-10-19T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0016-conformance-corpus-harness-over-core-cli
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
  - plans/roadmap
  - plans/milestones
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what changed on this date.
---

# 2026-10-19 — The corpus harness and the canonical pack schemas

## What changed

- [ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/): the conformance corpus takes
  its inputs from spec-kitty-open-packs and a few fixture projects. Its expected answers come from
  a harness here that runs core's existing CLI and maps the output onto the contract shape. A
  reviewed register holds every deliberate difference from core. No new core code is needed.
- [ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/): the Charter Pack schemas
  are core's pydantic models exported to JSON Schema, imported and owned here, and validated in
  the YAML adapter. Rules a schema cannot express stay in the domain.
- The [Roadmap](/plans/roadmap/) states the harness for step 3, and that the first contract release
  waits on the reshaped reserved surface (#113). The [Milestones](/plans/milestones/) list the
  cutover read inventory (#94), #113, #114 and #115. [Open questions](/plans/open-questions/)
  records both decisions and the `requires_core` ruling: it stays in `pack.yaml` (ADR-0005 decision 9 amended).
