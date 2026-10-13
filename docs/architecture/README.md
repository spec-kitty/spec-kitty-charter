---
title: Architecture
description: The target design of the Java charter service — containers, hexagonal modules, the OpenAPI contract and generated DTOs, the strangler stages and the per-worktree lifecycle.
doc_status: draft
updated: 2026-10-13
type: Architecture
kind: Hub
---

# Architecture

This section describes the **target design** of the charter service. doc-kitty's
convention keeps architecture pages in the present tense and puts future work under
[Plans](/plans/). This repository has no code yet, so each page here states the design as
agreed and marks what exists today. When code lands, the pages move to describing it.

The design is grounded in three core ADRs and this repository's own records:

- core ADR `2026-10-12-2` (Accepted) — the charter becomes an external contract package in
  its own repository;
- core ADR `2026-10-03-1` (Proposed in core) — a staged Java service strangler: reads first,
  writes later, one operation at a time;
- core ADR `2026-10-01-2` (Accepted) — the Mission Status Read API, the sibling Java service
  whose stack, binding and release practice the charter follows;
- [ADR-0001](/adr/0001-java-service-with-openapi-contract/) and
  [ADR-0002](/adr/0002-contract-source-of-record/) in this repository.

## The map

- [Overview](/architecture/overview/) — containers and the main flows. Read this first.

## Component detail

- [Hexagonal service](/architecture/hexagonal-service/) — the Gradle modules and the inward
  dependency rule.
- [Contract and DTOs](/architecture/contract-and-dtos/) — the OpenAPI contract, how the core and the
  service generate from it, and how it is versioned.
- [Resolution model](/architecture/resolution-model/) — what the domain computes: layer merge,
  activation, cascade, action-scoped context and profile resolution.
- [Service lifecycle](/architecture/service-lifecycle/) — per-worktree scope, loopback binding,
  credentials, freshness and fallback.
- [Events and the ledger](/architecture/events-and-ledger/) — why the charter publishes no events,
  and where it meets the publish, ledger, subscribe model.

## Transition

- [Strangler transition](/architecture/strangler-transition/) — the stages from Python-only to
  Java-only, the evidence each one needs, and the point of no return.
