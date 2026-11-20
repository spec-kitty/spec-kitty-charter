---
okf_version: "0.2"
title: "Codebase assessed: hotspots, design, tests and static analysis"
description: "A dated assessment of where change concentrates in the code and how costly it is to change there, with the 2026-11-12 top 5 re-checked and the actions ranked with their issues."
doc_status: active
updated: 2026-11-20T00:00:00Z
type: Changelog
kind: Changelog
related:
  - plans/engineering-notes/codebase-assessment-2026-11-20
  - plans/codebase-health-2026-11-20
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for where the hotspots are and which actions the assessment ranks first.
  - profile: governance-lead
    guidance_text: Read this for the verdict and the questions put to the operator.
---

# 2026-11-20 — Codebase assessed: hotspots, design, tests and static analysis

A new [engineering note](/plans/engineering-notes/codebase-assessment-2026-11-20/) measures where
change concentrates in the code and how complex those places are, and adds lenses on design, tests
and static analysis. Its one-page summary is the [codebase health page](/plans/codebase-health-2026-11-20/).

- `packs.infra.storage` holds half of the hotspot score; seven classes are over 1,000 lines, against six on 2026-11-12.
- The complexity gate is on and no method is over it; `PackPolicy` is gone.
- The CPU-time budget nightly and the SonarCloud gate fail without saying why.
- Five of the six test gaps proved on 2026-11-12 are still open.

The assessment changes no code and files no issue. Its scripts are under `scripts/analysis/`.
