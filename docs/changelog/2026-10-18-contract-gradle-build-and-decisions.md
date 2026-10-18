---
title: Contract skeleton, Gradle build and three decisions
description: The operator accepted ADR-0002 and core ADR 2026-10-03-1 and chose rewrite-and-replace; the first contract paths, BDD scenarios and the Gradle build of the service landed.
doc_status: active
updated: 2026-10-18
type: Changelog
kind: Changelog
related:
  - adr/0002-contract-source-of-record
  - adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts
  - plans/roadmap
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what changed on this date.
  - profile: core-maintainer
    guidance_text: Read this for the decisions that change where the contract and the Python charter live.
---

# 2026-10-18 — Contract skeleton, Gradle build and three decisions

## Decisions

- [ADR-0002](/adr/0002-contract-source-of-record/) is **accepted**: the charter contract is
  authored here.
- Core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) (the staged Java service strangler) is **accepted**, with
  Amendment 2026-10-18.
- [ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/): **rewrite
  and replace**. The Java service is built against BDD contracts and the OpenAPI document;
  the Python charter stays in core; the open-packs content must round-trip through the
  file-based infrastructure layer.

## What landed

- `contracts/charter/` at `0.1.0-SNAPSHOT`: `GET /packs` and
  `GET /mission-types/{missionType}/actions/{action}/context`, with BDD scenarios.
- The Gradle build: convention plugins in `build-logic/`, six modules under `service/`,
  strict dependency verification, an ArchUnit gate, the BDD suite and the open-packs
  round-trip, run in CI on Java 25. See [Building the service](/guides/service-build/).
