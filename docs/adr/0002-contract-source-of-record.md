---
title: "ADR-0002: This repository is the source of record for the charter contract"
description: The charter OpenAPI contract is authored here under contracts/charter/, in the layout of core's contract module with its own checks; core ingests pinned releases.
doc_status: active
updated: 2026-10-19
type: ADR
kind: ADR
related:
  - adr/0001-java-service-with-openapi-contract
  - architecture/contract-and-dtos
audience:
  - profile: core-maintainer
    guidance_text: Read this to rule on where the charter contract is authored and how core ingests it.
  - profile: charter-service-contributor
    guidance_text: Read this for where the contract lives and which tooling checks it.
---

# ADR-0002: This repository is the source of record for the charter contract

## Status

**Accepted** — 2026-10-18. Decider: Stijn Dejongh (operator). Proposed 2026-10-13. It changes
where core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) (the staged Java service strangler) puts the contract; that ADR,
accepted the same day, records the change in its Amendment 2026-10-18. The first risk below
is settled by [ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/):
the Python charter stays in core as the oracle, and the contract is authored here anyway.

### Amendment 2026-10-18

Operator ruling, the same day (DM `01M57WH7P0QZWHKYGBA2R0MA3K`, which supersedes DM
`01M57VVQG0E6CRAPEH597Q2VNY`; [open question 18](/plans/open-questions/)). The decision text
below is kept as accepted; this amendment changes two of its points:

- **Decision 4 is replaced.** The charter owns its contract checks: the `:contracts` project
  runs a lint ruleset the charter authors in `contracts/lint/`, the bundle, a checker for
  closed response objects and the breaking-change check, inside `./gradlew verifyAll`.
  Core's contract tools do not run here, and no copy of them is kept. Where the charter's
  conventions differ from core's, core adapts through its integration seam.
- **Decision 1 is narrowed.** The module keeps the layout of core's contract module (split
  files, plain relative `$ref`s, `_index.yaml` lists, the `/api/v1` server prefix, its own
  `info.version` and `CHANGELOG.md`); it does not follow core's conventions "exactly". The
  charter's own conventions are listed in
  [`contracts/lint/README.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md).

## Context

Core ADR `2026-10-03-1` places the charter's OpenAPI document at `contracts/charter/` in the
**core** repository, beside `contracts/mission-status/`, governed by the same layout, lint,
breaking-change and release checks. That was written before the charter had a repository of
its own.

[ADR-0001](/adr/0001-java-service-with-openapi-contract/) makes the contract the single
source of charter DTOs and has the core *ingest* it. The question is which repository
authors it.

The Mission Status contract stays in core because its producer (the Python reducer) and its
Java service both live in core for now (core ADR [`2026-10-01-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-01-2-mission-status-read-api-and-dashboard-extraction.md) (the Mission Status Read API), Amendment 2026-10-12). The
charter is different: its producer is moving here.

## Decision

1. The charter contract is authored here, in `contracts/charter/`, as a module that follows
   core's `contracts/README.md` conventions exactly (split files, plain relative `$ref`s,
   `_index.yaml` lists, `/api/v1` server prefix, its own `info.version` and `CHANGELOG.md`).
   *Narrowed by Amendment 2026-10-18: the layout only.*
   "Charter contract" here means all five parts listed in
   [Contract and DTOs](/architecture/contract-and-dtos/), not only the OpenAPI document.
2. Shared vocabulary (`Problem`, `PageInfo`, `PageCursor`) is reused from core's `_shared/`
   by vendoring a pinned copy, not redefined.
3. Each contract release publishes a single-file bundle and the conformance corpus. The core
   vendors a pinned, checksummed release, the way core ADR [`2026-08-16-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-08-16-2-open-packs-is-source-of-truth-for-built-in-doctrine.md) (open-packs as the built-in source) plans to vendor
   built-in pack content (core #3504, not built yet), and generates its Python DTOs from it.
4. Core's contract tooling (layout, lint, breaking-change checks) runs here in CI against
   this module. Until it is packaged for reuse, this repository runs a pinned copy.
   *Replaced by Amendment 2026-10-18: the charter owns its contract checks.*

## Consequences

### Positive

- The producer owns its contract, as `spec-kitty-events` owns the event contracts.
- A contract change and the service change that implements it land in one pull request.

### Negative

- The core cannot change the charter contract in its own pull request; it raises a pin.
- Core's contract tooling has to run in a second repository. *Superseded by Amendment
  2026-10-18: core's tools do not run here; the charter maintains its own checks instead.*

### Risks

- This record depends on [open questions](/plans/open-questions/), question 2. Under core
  ADR `2026-10-01-2` D-11 a contract stays beside the conformance oracle it is proven
  against. If the Python charter stays in core, so should the contract until Python reads
  retire.
- If the operator prefers core's `contracts/` as the single home for every OpenAPI module,
  this record is rejected and the service vendors the contract from core instead. Both work;
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
