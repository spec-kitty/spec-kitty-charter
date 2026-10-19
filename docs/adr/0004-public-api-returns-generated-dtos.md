---
title: "ADR-0004: The public API returns DTOs generated from the OpenAPI contract"
description: The public API, package and service, returns DTOs generated from the OpenAPI contract, one set per client language; core hand-written types are transitional.
doc_status: active
updated: 2026-10-14T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0001-java-service-with-openapi-contract
  - adr/0002-contract-source-of-record
  - architecture/contract-and-dtos
  - api/python-package
audience:
  - profile: contract-consumer
    guidance_text: Read this to learn which types the charter API returns in your client language.
  - profile: core-maintainer
    guidance_text: Read this for the core follow-up on the seam's return types.
---

# ADR-0004: The public API returns DTOs generated from the OpenAPI contract

## Status

**Accepted** — 2026-10-14. Decider: Stijn Dejongh (operator). Ruling, verbatim: "OpenAPI
DTOs, these can be generated to python, java, .... depending on our integration client
surface."

## Context

Two sources disagreed about what the extracted charter's public API returns:

- [Contract and DTOs](/architecture/contract-and-dtos/) said the seam
  `charter.offering.api` returns DTOs generated from the OpenAPI contract.
- Core's `src/charter/offering/api.py` (constraint C-003) is an in-process Python contract
  with its own hand-written types.

The disagreement was recorded as an open question while [Python package](/api/python-package/)
described today's core types.

## Decision

The public API returns DTOs generated from the OpenAPI contract. This holds for every
surface: the in-process Python package and the service return the same contract-defined
shapes.

- **One contract, one generated DTO set per client language.** The OpenAPI contract is the
  single description of every request and response shape. DTOs are generated from it for
  whichever integration client surface needs them: Python for core's seam and the CLI, Java
  for the service, others as a new client surface appears.
- **No hand-written response shapes.** Neither side writes a charter response type by hand.
- **Core's types are transitional.** The hand-written types behind `charter.offering.api`
  today are the current shapes that the generated DTOs replace.

## Consequences

### Positive

- One shape per concept across languages; a contract change reaches every client by
  regeneration.
- The conformance corpus compares like with like.
- A new client language needs a generator, not a hand-port.

### Negative

- The Python package's return types change from core's current types to generated DTOs,
  which is a breaking change for any caller of `charter.offering.api`.
- Callers depend on generator output and its conventions (naming, optionality, enums).

### Risks

- Generated Python DTOs may read less idiomatically than hand-written types; the generator
  configuration needs care.
- Core's C-003 ("in-process own types") conflicts with this decision until amended.

### Follow-ups

- **Core follow-up (not done here):** amend constraint C-003 in core's
  `src/charter/offering/api.py` and its spec so the seam returns generated DTOs. This
  repository does not edit core.
- Relation to other records: [ADR-0001](/adr/0001-java-service-with-openapi-contract/)
  fixes the service as Java against an OpenAPI contract, which this record builds on.
  [ADR-0002](/adr/0002-contract-source-of-record/) still decides where the contract's
  source of record lives; this record decides only what the contract's consumers receive.

## Alternatives considered

### Option A: The in-process package returns core's own types

Keeps the existing Python ergonomics, but gives two parallel shapes for one concept and
leaves a hand-maintained mapping to the contract. Not chosen.

## References

- [Contract and DTOs](/architecture/contract-and-dtos/)
- [Python package API](/api/python-package/)
- [API](/api/#decision-the-api-returns-generated-dtos)
