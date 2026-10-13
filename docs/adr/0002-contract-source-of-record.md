---
title: "ADR-0002: This repository is the source of record for the charter contract"
description: The charter OpenAPI contract is authored in spec-kitty-charter under contracts/charter/, following core's contracts conventions, and the core ingests pinned releases of it.
doc_status: draft
updated: 2026-10-13
type: ADR
kind: ADR
related:
  - adr/0001-java-service-with-openapi-contract
  - architecture/contract-and-dtos
---

# ADR-0002: This repository is the source of record for the charter contract

## Status

**Proposed** — 2026-10-13. Needs the operator's ruling, because it changes where core ADR
`2026-10-03-1` puts the contract.

## Context

Core ADR `2026-10-03-1` places the charter's OpenAPI document at `contracts/charter/` in the
**core** repository, beside `contracts/mission-status/`, governed by the same layout, lint,
breaking-change and release checks. That was written before the charter had a repository of
its own.

[ADR-0001](./0001-java-service-with-openapi-contract.md) makes the contract the single
source of charter DTOs and has the core *ingest* it. The question is which repository
authors it.

The Mission Status contract stays in core because its producer (the Python reducer) and its
Java service both live in core for now (core ADR `2026-10-01-2`, Amendment 2026-10-12). The
charter is different: its producer is moving here.

## Decision

1. The charter contract is authored here, in `contracts/charter/`, as a module that follows
   core's `contracts/README.md` conventions exactly (split files, plain relative `$ref`s,
   `_index.yaml` lists, `/api/v1` server prefix, its own `info.version` and `CHANGELOG.md`).
2. Shared vocabulary (`Problem`, `PageInfo`, `PageCursor`) is reused from core's `_shared/`
   by vendoring a pinned copy, not redefined.
3. Each contract release publishes a single-file bundle and the fixture corpus. The core
   vendors a pinned, checksummed release, the way it vendors built-in pack content, and
   generates its Python DTOs from it.
4. Core's contract tooling (layout, lint, breaking-change checks) runs here in CI against
   this module. Until it is packaged for reuse, this repository runs a pinned copy.

## Consequences

### Positive

- The producer owns its contract, as `spec-kitty-events` owns the event contracts.
- A contract change and the service change that implements it land in one pull request.

### Negative

- The core cannot change the charter contract in its own pull request; it raises a pin.
- Core's contract tooling has to run in a second repository.

### Risks

- If the operator prefers core's `contracts/` as the single home for every OpenAPI module,
  this record is withdrawn and the service vendors the contract from core instead. Both work;
  what matters is that exactly one repository authors it.

## Alternatives considered

### Option A: Author it in core's `contracts/charter/` (core ADR 2026-10-03-1 as written)

Keeps every OpenAPI module under one tooling tree. Not chosen as the default because the
service, its fixtures and its contract would then change in two repositories for every
contract change.

## References

- Core `contracts/README.md`
- Core ADR `4.x/2026-10-03-1-charter-read-write-service-strangler.md`, "Service stack and
  contract"
