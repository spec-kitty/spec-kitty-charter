---
title: "ADR-0021: Core adopts the generated client now, behind a proxy that routes each operation"
description: Proposes that the published contract is core's seam today, with a generated client and a proxy that routes each operation to core's Python or to the service.
doc_status: active
updated: 2026-10-24T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0001-java-service-with-openapi-contract
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0010-cutover-scope-and-seam-boundary
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
  - adr/0015-charter-repository-is-canonical-for-charter-structure
  - adr/0016-conformance-corpus-harness-over-core-cli
  - adr/0019-packs-resource-model-and-hypermedia
  - adr/0020-offering-and-activations
  - adr/0022-contract-versions-start-at-0-x
  - architecture/strangler-transition
  - api/service-contract
audience:
  - profile: core-maintainer
    guidance_text: Read this for what core adopts now, how the proxy routes each operation, and what switching one operation takes.
  - profile: charter-service-contributor
    guidance_text: Read this for why publishing the contract is on core's critical path.
  - profile: contract-consumer
    guidance_text: Read this for how the published contract becomes the compatibility contract between core and the charter.
---

# ADR-0021: Core adopts the generated client now, behind a proxy that routes each operation

## Status

**Accepted** — 2026-10-27 (provisional since 2026-10-24), proposed 2026-10-20 and merged with #123. Decider: Stijn Dejongh (operator). Decisions marked **(ruled)** are
the operator's; **(default)** decisions stand as decided until revisited. It is ratified as of 2026-10-27.

This record amends:

- [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/) decision 4 ("stages 3 and 4 ship
  together", one switch-over): the switch-over happens one operation at a time, each one fail
  closed (D5).
- [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/) decision 2: the service has
  to run only once an operation is routed to it (D5).
- Issue [#13](https://github.com/spec-kitty/spec-kitty-charter/issues/13) ("core ingests the
  contract and returns generated DTOs from the seam") grows from DTOs to the whole generated client
  and the proxy (D2, D3).

## Context

The strangler plan ([Strangler transition](/architecture/strangler-transition/), ADR-0010) keeps
core's Python behind the seam `charter.offering.api` until one switch-over, after a read shadow.
Until then, the seam returns core's own shapes, and the contract is something core only meets at
the switch. Every shape the two sides disagree on is found late, at the most expensive moment.

The operator set a different approach on 2026-10-20:

> This repository mints the new API as an OpenAPI contract and publishes it as 1.0. We use the
> OpenAPI generator to create a client. Core uses this client and adds it today. We ensure this
> becomes the canonical seam, the compatibility contract. An ACL proxy in core routes the behavior
> to the as-is code now, but can be switched to the service when we are ready. That would
> significantly simplify the future cutover.
>
> I am aware the minting of the API contract will be blocking for the core side progress. That is
> acceptable: it enables long-term orthogonal compatibility at the cost of a temporary slowdown.

## Decision

### D1. The published contract is the seam (ruled)

The published contract is the compatibility contract between core and the charter. It is
published as 0.x while it iterates, and 1.0.0 follows once it is stable
([ADR-0022](/adr/0022-contract-versions-start-at-0-x/)). Core's seam
exposes the generated client's operations and DTOs, and code above the seam uses only those. A
shape the seam needs that the contract lacks is requested here as an issue, because this
repository owns the contract (ADR-0015); core does not add it locally.

### D2. Core vendors a client generated from the released bundle, now (ruled)

Core vendors the released, checksummed bundle (#45) and generates a Python client from it. It does
so as soon as 0.1.0 is published, before any operation is served by the service, and
regenerates on every 0.x minor it adopts (ADR-0022 D3).

- **Generator (default):** `openapi-generator` with its `python` generator, at a pinned version. It
  is the generator this repository already uses for the server interfaces, so both sides resolve
  `$ref`s and closed schemas the same way. The duplicate-DTO defect
  ([#119](https://github.com/spec-kitty/spec-kitty-charter/issues/119)) must be fixed in the
  bundle first, or both sides inherit it. Rejected: `openapi-python-client` and
  `datamodel-code-generator`, which would add a second interpretation of the same contract.
- **Regeneration:** a new minor contract version is regenerated and vendored by core; a client
  generated from 1.x keeps working against any 1.y service (ADR-0009).

### D3. A proxy in core routes each operation (ruled)

An anti-corruption layer in core sits between the seam and two backends:

- **in-process:** the as-is Python, adapted to the generated client's interface. It answers in
  contract terms: generated DTOs, `CharterRefusalCode` values as typed errors, and, for
  activations, the same `revision` the service computes (ADR-0020 D4);
- **remote:** the generated client calling the service.

A route table maps each `operationId` to a backend. Every operation starts on in-process. Reserved
operations (ADR-0020 D8) are served in-process from day one, so core never sees their `501`.

### D4. The proxy is where the shadow runs (default)

A third route, **shadow**, answers from in-process and calls the service in the background, then
records any difference against the contract. This is stage 2 (#14) moved into the proxy: one
mechanism for comparing, per operation, instead of a separate harness. The conformance corpus
(ADR-0016) still gates every switch. Rejected: a shadow outside the proxy, which duplicates the
routing.

### D5. Each operation switches on its own, fail closed (default)

An operation moves from in-process (or shadow) to remote when the service implements it and the
corpus passes for it. After it moves:

- it fails closed: there is no fallback to Python for that operation (ADR-0011 D1 holds per
  operation);
- the service must be running for any command that calls it, so the lifecycle work (#70) is needed
  by the first switch, not before.

The 4.x cutover milestone is reached when every read operation is remote and the Python read paths
are removed (#76). This replaces ADR-0010 D4's single switch-over with a sequence of small,
reversible ones; the point of no return is still the removal of the Python reads. Rejected: one
switch for every operation, which keeps the risk the proxy exists to remove.

### D6. Publishing the contract is on core's critical path, and that is accepted (ruled)

Core's adoption waits on the first release. The slowdown is the price of a contract both sides
code against from the start. Under ADR-0022 the first release is 0.1.0, published from today's
contract once [#119](https://github.com/spec-kitty/spec-kitty-charter/issues/119) is fixed, so the
wait is short: the reshape of ADR-0019 and ADR-0020 follows in 0.x minors, with core regenerating
on each one. The first route switched to the service needs 1.0.0 (ADR-0022 D6).

## Consequences

### Positive

- Core codes against the final surface today. Shape mismatches are found when core adopts the
  client, not at the switch-over.
- The switch-over becomes a route change per operation, which can be reverted until the Python
  path is deleted.
- Core's own deny-list and activation work lands behind the same interface the service will
  serve, so none of it is thrown away at the switch.
- The shadow, the switch and the rollback are one mechanism.

### Negative

- Core waits for 0.1.0, and every breaking 0.x minor costs it a regeneration and an adapter
  change.
- The in-process backend must translate core's Python into contract shapes, including refusal codes
  and revisions. That adapter is work core would not otherwise do.
- Between the first and the last switch, two backends answer different operations. A command that
  uses both needs the service running and the Python loaded.

### Risks

- **The in-process adapter hides a divergence.** If it maps a Python answer onto a contract shape
  loosely, the shadow compares the adapter, not the behavior. Mitigation: the corpus runs against
  the CLI (ADR-0016), not the adapter, and the in-process backend uses the contract's JSON Schemas
  to validate what it returns.
- **1.0.0 freezes a shape that proves wrong.** Mitigation: the shapes are used by core through
  0.x first (ADR-0022), and after 1.0.0 additive contract sets apply (ADR-0009). A wrong
  reserved operation is replaced by a new one in an additive set; while it is reserved, only the
  in-process backend serves it, so no client depends on the service's behavior for it.

## Alternatives considered

### B: keep core's own seam shapes until the switch-over

ADR-0010 as it stands. Rejected by the operator: it finds every mismatch at the switch.

### C: hand-written Python models that mirror the contract

Rejected: ADR-0001 says no response shape is hand-written on either side.

## Revisit when

- The generator cannot represent a contract shape (closed schemas, `oneOf`, the open `_links` map).
- A switched operation needs a fallback to stay usable, which would contradict D5.

## Requests to core

- Vendor the released bundle and generate the client with the pinned generator.
- Build the proxy with the route table, and the in-process backend over today's Python.
- Route the deny-list and activation work (branch `core-align-3-persistent-deactivation`) through
  the in-process backend, in contract shapes.
- Amend core ADR `2026-10-03-1` (staged ownership) for the per-operation switch.

**Core's response, 2026-10-20** (activation ledger mission `persistent-deactivation-ledger-01M59YAA`):

- The approach is accepted, and the mission's work becomes the in-process backend.
- The seam module is `charter.activation.activation_contract`, exported through
  `charter.activation.api`. It maps core's files to the contract, computes the revision,
  translates refusals and builds the skipped report.
- The proxy and the generated client live in `specify_cli`.
- Switching activation to the charter surface is a core 4.0 GA deliverable: epic
  spec-kitty/spec-kitty#6518, on core milestone #11.
- Core's blocking dependencies are this repository's #119 (before 0.1.0) and #125 (the
  activation minor).

## References

- [ADR-0020](/adr/0020-offering-and-activations/), [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/),
  [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/),
  [ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/).
- [Strangler transition](/architecture/strangler-transition/).
- Issues: [#13](https://github.com/spec-kitty/spec-kitty-charter/issues/13),
  [#14](https://github.com/spec-kitty/spec-kitty-charter/issues/14),
  [#45](https://github.com/spec-kitty/spec-kitty-charter/issues/45),
  [#70](https://github.com/spec-kitty/spec-kitty-charter/issues/70),
  [#76](https://github.com/spec-kitty/spec-kitty-charter/issues/76).
