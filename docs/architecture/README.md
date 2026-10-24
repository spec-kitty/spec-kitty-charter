---
okf_version: "0.2"
title: Architecture
description: The target design of the Java charter service — containers, hexagonal modules, the OpenAPI contract and generated DTOs, the strangler stages and the per-worktree lifecycle.
doc_status: draft
updated: 2026-10-24T00:00:00Z
type: Architecture
kind: Hub
audience:
  - profile: charter-service-contributor
    guidance_text: Use this map to find the design page for the part you are building.
---

# Architecture

This section describes the **target design** of the charter service. doc-kitty's
convention keeps architecture pages in the present tense and puts future work under
[Plans](/plans/). The first code has landed (the contract skeleton and the Gradle build,
[Roadmap](/plans/roadmap/) steps 1 and 2); each page states the design as agreed and marks
what exists today. As more code lands, the pages move to describing it.

The design is grounded in three core ADRs and this repository's own records:

- core ADR [`2026-10-12-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-12-2-charter-extraction-to-its-own-repository.md) (Accepted) — the charter becomes an external contract package in
  its own repository;
- core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) (Accepted, Amendment 2026-10-18) — a staged Java service strangler: reads first,
  writes later, one operation at a time;
- core ADR [`2026-10-01-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-01-2-mission-status-read-api-and-dashboard-extraction.md) (Accepted) — the Mission Status Read API, the sibling Java service
  whose stack, binding and release practice the charter follows;
- [ADR-0001](/adr/0001-java-service-with-openapi-contract/) and
  [ADR-0002](/adr/0002-contract-source-of-record/) in this repository.

## The map

- [Overview](/architecture/overview/) — containers and the main flows. Read this first.
- [Behavior and acceptance scenarios](/architecture/behavior-and-scenarios/) — the promise-first
  contract and acceptance scenarios that define the offering's role and boundary, and the conformance net they form.

## The charter model

- [Logical model](/architecture/logical-model/) — the charter components and how they relate:
  charter, Charter Packs and pack levels, artifact kinds, the DRG, activation and profiles.
- [Descriptors](/architecture/descriptors/) — every descriptor file: purpose, fields, naming,
  location and DRG links.
- [Descriptor schemas](/architecture/descriptor-schemas/) — entity diagrams of the descriptor
  schemas.

## Component detail

- [Hexagonal service](/architecture/hexagonal-service/) — the Gradle modules and the inward
  dependency rule.
- [Contract and DTOs](/architecture/contract-and-dtos/) — the OpenAPI contract, how the core and the
  service generate from it, and how it is versioned.
- [Architectural patterns applied](/architecture/architectural-patterns-applied/) — the hexagonal,
  onion and screaming layout as built, with the C4 and class diagrams.
- [Pack registration and distribution](/architecture/pack-registration-distribution/) — how the service
  registers, stores, verifies and distributes packs of every level: the layering, the four source kinds, the
  trust and lockfile model, the storage ports, and the API surface.
- [Packs resource model](/architecture/packs-resource-model/) — the proposed REST resource tree for
  packs and their contents, the API root and the hypermedia links (ADR-0019).
- [Offering and activation](/architecture/offering-and-activation/) — the proposed `/offering`
  layer, the activation documents the service stores and how core registers or builds one
  (ADR-0020).
- [Resolution model](/architecture/resolution-model/) — what the domain computes: layer merge,
  activation, cascade, action-scoped context and profile resolution.
- [Service lifecycle](/architecture/service-lifecycle/) — per-worktree scope, loopback binding,
  credentials, freshness and failure behavior.
- [Events and the ledger](/architecture/events-and-ledger/) — why the charter publishes no events,
  and where it meets the publish, ledger, subscribe model.

## Transition

- [Strangler transition](/architecture/strangler-transition/) — the stages from Python-only to
  Java-only, the evidence each one needs, and the point of no return.
