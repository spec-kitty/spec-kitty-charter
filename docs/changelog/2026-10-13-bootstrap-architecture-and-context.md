---
title: Bootstrap — Java direction, architecture and context
description: The docs tree moves to the doc-kitty convention and gains the context, target architecture, decision records and plans for the Java charter service.
doc_status: active
updated: 2026-10-13
type: Changelog
kind: Changelog
related:
  - adr/0001-java-service-with-openapi-contract
  - adr/0002-contract-source-of-record
  - adr/0003-docs-follow-doc-kitty
---

# 2026-10-13 — Bootstrap: Java direction, architecture and context

## What changed

- **Direction.** The README and docs now describe this repository as the home of the Java
  charter service and its OpenAPI contract, not a Python package
  ([ADR-0001](../adr/0001-java-service-with-openapi-contract.md)). `charter.offering.api`
  is described as the core's transitional Python seam.
- **Structure.** The placeholder `index.md` tree is replaced by the doc-kitty convention:
  `README.md` section indexes, `_meta/sections.yaml`, numbered ADRs and this changelog
  ([ADR-0003](../adr/0003-docs-follow-doc-kitty.md)). `development/` folds into `guides/`.
- **Content.** New pages for context (product, problem, domain, system context),
  architecture (overview, hexagonal service, contract and DTOs, resolution model, service
  lifecycle, events and the ledger, strangler transition), plans (roadmap, open questions),
  the planned API surface, and contributing.
- **Proposed.** [ADR-0002](../adr/0002-contract-source-of-record.md) puts the charter
  contract's source of record here instead of core's `contracts/`. It needs a ruling.

No code was added.
