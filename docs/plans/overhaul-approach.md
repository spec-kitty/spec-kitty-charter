---
title: High-level approach to the charter overhaul
description: The behaviour-first, strangler-fig approach to splitting out the charter — scenarios and boundary first, then the OpenAPI contract, before any implementation is chosen.
doc_status: draft
updated: 2026-10-17
type: Plan
kind: Planning
related:
  - plans/roadmap
  - plans/open-questions
  - architecture/strangler-transition
  - architecture/behaviour-and-scenarios
  - adr/0002-contract-source-of-record
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the order of the phases and why behaviour comes before the contract.
  - profile: core-maintainer
    guidance_text: Read this for how the overhaul sequences against the core seam work.
  - profile: governance-lead
    guidance_text: Read this for what is decided now and what is deliberately deferred.
---

# High-level approach

This page records the agreed **shape** of the charter overhaul: the order of the phases and the
reasoning behind that order. The checkable steps live in the [Roadmap](/plans/roadmap/); the
end-state design lives in [Architecture](/architecture/); settled decisions live in
[Decision Records](/adr/).

## What is decided now

- **We do not move or extract the Python first.** The target implementation is a Java service
  ([ADR-0001](/adr/0001-java-service-with-openapi-contract/)); what is deferred is the *migration
  sequencing* — whether the Python charter first moves into this repository as an interim package
  or is replaced in place (see [open question 2](/plans/open-questions/)). Settling that before the
  behaviour is specified would bind the design to how the code happens to work today.
- **We lead with behaviour and boundary.** The first work is to define *what* the charter offering
  does and where its edges are, as promise-first acceptance scenarios — the consumer contract,
  independent of the command or service that delivers it.
- **The contract follows the behaviour.** Once the boundary is described, it is specified as an
  OpenAPI 3.1 / Swagger YAML contract, published in this repository as the external contract and
  rendered in the docsite.

## Why a strangler fig

The charter runs today as Python inside the core CLI. A strangler fig replaces it from the
outside in: a new surface grows around the existing behaviour, takes over one capability at a
time, and the old implementation is removed only once the new one answers for it. The scenario
suite is what makes this safe — it is the conformance net that both the current Python charter
and any future implementation must pass, so a capability can migrate without changing what a
consumer observes. See [Strangler transition](/architecture/strangler-transition/) for the stages
once an implementation exists.

## The phases

| Phase | Focus | Outcome |
|---|---|---|
| **P0 — Governance and seed** *(done)* | Activate the authoring doctrine; seed the behaviour contract. | The `internal` and `qa` org packs are registered and healthy; the charter-offering acceptance scenarios are copied into `tests/scenarios/charter/`. |
| **P1 — Scenarios and boundary** | Define the offering's role and boundary as promise-first scenarios. | The suite covers the offering's guarantees and edges; each promise is traced to an authority; the set is presented in the docsite. |
| **P2 — API specification** | Turn the described boundary into a published contract. | An OpenAPI 3.1 / Swagger YAML contract under `contracts/charter/`, versioned as the external contract and rendered in the docsite (see [ADR-0002](/adr/0002-contract-source-of-record/)). |
| **P3 — Low-level design** *(next focus)* | The artifact structure and storage model, behind a repository layer. | A design that decouples infrastructure from logic: a repository layer agnostic of whether charter content is file-based or database-provided. |
| **P4 — Implementation** *(deferred)* | Build the Java service ([ADR-0001](/adr/0001-java-service-with-openapi-contract/)); settle the migration sequencing. | Sequenced only once P1–P2 fix the behaviour and the contract; see [Roadmap](/plans/roadmap/) steps 1–7. |

P0, P1 and P2 can run in parallel with the core's seam work, because none of them answers a
production read.

## What P3 sets up

The next focus after the contract is the **lower-level design**: the charter offering's artifact
structure and storage models, and a **repository / infrastructure layer that is agnostic of
whether the content is file-based or database-provided**. Keeping that infrastructure-from-logic
decoupling behind a repository layer lets the domain be specified and tested against the
scenarios without committing to file storage, a database, or a particular service shape — which
keeps the P4 migration sequencing and storage decisions genuinely open.

## Governance in force for this work

The activated org doctrine governs how this overhaul is carried out:

- `feature-and-scenario-authoring` (styleguide) and `charter-offering-scenario-extraction`
  (procedure) — the promise-first, consumer-first method for the scenario work in P1.
- `qa-description-to-acceptance-ci` (procedure) with the `qa-001`/`qa-003`/`qa-007` directives —
  the description → acceptance → CI loop and its traceability and no-fake-green gates.

How those packs are put *in force* (charter synthesis and per-entry `charter activate`) is itself
P1 setup work and is tracked in the issue tracker.
