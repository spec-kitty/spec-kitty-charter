# Plan Specification: Pack registration & distribution — design, ADR & BDD (no implementation)

**Plan Branch**: `feat/pack-registration-distribution`
**Created**: 2026-10-18
**Status**: Draft
**Input**: Design how the charter offering service registers, stores and distributes packs of every tier. Deliver the design, ADR(s), optionally BDD scenarios, and provisioned `src/test/resources` — implementation-independent, no Java yet.

## Problem Statement *(mandatory)*

The charter offering service must **register, store, verify and distribute packs of every tier** — built-in, org, and (newly) **consumer-created and non-open/private** packs — yet packs today are hand-vendored and reconciled by drift gates. This mission produces the **design and its decision record** for that capability — not production code — so the public surface and the layering are worked out and agreed before any Java is written. BDD scenarios (which directly become the public-surface design) and the `src/test/resources` fixtures that will exercise them may be provisioned now to enable the BDD flow.

## Goals & Non-Goals *(mandatory)*

### Goals

- **Architecture & API design** for pack registration/distribution: the onion layering with vertical splits by concern (pack registration & validation, mission types, profiles, other charter artefacts); the two planned API versions (OpenAPI-based HATEOAS REST and GraphQL) as entry-point adapters; the storage-agnostic layer returning plain domain models; ArchUnit as the layering/no-infra-in-domain enforcement.
- **ADR(s)** capturing the material decisions (source kinds, built-in-as-first-catfooding-consumer, pinning/trust, core-as-generated-client, storage agnosticism, the layering rules).
- **Optionally, high-quality BDD scenarios** (Given/When/Then) for the registration/distribution behaviours — authored to a high bar, since they become the public-surface design.
- **Provisioned `src/test/resources`** scaffolding to enable the BDD flow (feature files / fixtures), without Java implementation.
- A decomposition of the work with owners and sequencing, and crisp entry criteria for the eventual implementation mission.

### Non-Goals

- **No Java / production implementation** — no service, resolver, adapters or storage code; only design, decisions, BDD scenarios, and test-resource scaffolding.
- No change to core (its vendoring, ADRs, or in-flight PRs) — core is prior art / a generated client.

## Stakeholders & Decision Owners *(mandatory)*

| Stakeholder | Interest / Concern | Decision Authority |
|-------------|---------------------|---------------------|
| Operator (Stijn Dejongh) | Direction, the design calls, supply-chain risk | **Final sign-off; resolves every Decision Moment** |
| Charter offering service team (this repo) | Owns the design, the OpenAPI/GraphQL contracts, the layering | Proposes design; authors artifacts |
| Core maintainers (`spec-kitty/spec-kitty`) | Core consumes packs via the generated Python client | Consulted — prior art, not a constraint |
| Pack authors & consumers (org / consumer / private) | Registering & distributing packs; trust/privacy of non-open packs | Represented by the operator |
| Supply-chain / DIRECTIVE_051 owner | Risk of fetching/executing third-party-sourced doctrine | Operator ratifies accepted risk via ADR |

## Constraints *(mandatory)*

- **Interaction mode fixed**: OpenAPI contract → Swagger/docsite → generated Python client (ADR-0001/0002/0004). REST HATEOAS is one of the two entry-point API versions designed here.
- **Canonical source**: `spec-kitty-open-packs` is the source of truth for built-in; core re-vendors transitionally pending this service.
- **Architecture rules** (operator-set): onion layering; vertical concern splits; no infra annotations in domain packages; storage layer returns domain models; ArchUnit enforces it.
- **Branch**: PR-bound on `feat/pack-registration-distribution`; `main` protected.
- **No implementation**: Java production code is out; `src/test/resources` scaffolding is in.

## Success Criteria *(mandatory)*

1. The architecture design and the API structure/formats (REST + GraphQL) are documented and internally coherent, and the layering/ArchUnit rules are stated as enforceable invariants.
2. Every material trade-off has a recorded decision (ADR) with rejected alternatives, or is resolved via a Decision Moment.
3. BDD scenarios (if authored) are high-quality, map cleanly onto the proposed public surface, and are backed by provisioned `src/test/resources` fixtures.
4. A decomposition with owners + sequencing and clear entry criteria for the implementation mission exists.

## Approach *(how this run executes)*

- **Research + architecture squad first**: dispatch a profile-loaded squad (research + architect lenses) over the problem and the seed material; synthesize into the design and ADR drafts.
- **Decision Moments for open questions**: whenever the squad surfaces a genuine choice, open a `spec-kitty` Decision Moment for the operator to resolve before proceeding — do not resolve material ambiguity by assumption.
- **Highly iterative & interactive**: short cycles, operator in the loop at each decision; artifacts evolve with feedback.

## Open Questions *(carried into Research)*

- [ ] Source kinds in scope: {git URL, filesystem, service-stored}; is a published-artifact source in or out?
- [ ] Built-in's distribution path through the service, and migration given the ongoing core re-vendor.
- [ ] Trust & consent model for consumer/private git sources (resolves open-packs #23).
- [ ] Pin/integrity lockfile granularity; reuse of the `snapshot_sha256` primitive.
- [ ] Storage model for service-held consumer/private packs (privacy, retention).
- [ ] Engine/compatibility gating expressed in the contract.
- [ ] GraphQL vs REST surface parity — same domain operations exposed through both, or divergent?
- [ ] BDD scope for this mission: which behaviours get scenarios now, and how far `src/test/resources` is provisioned.

## Out of Scope

- Java / production implementation of the service, resolver, API adapters, or storage.
- Any change to core or open-packs beyond consuming them as prior art / canonical source.
- Retiring this repository's vendored `packs/internal` / `packs/qa` (a later execution step).
