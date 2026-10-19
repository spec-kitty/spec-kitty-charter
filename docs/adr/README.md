---
title: Decision Records
description: The immutable log of decisions taken in the spec-kitty-charter repository, and the core Spec Kitty ADRs they build on.
doc_status: active
updated: 2026-10-19
type: ADR
kind: Hub
audience:
  - profile: charter-service-contributor
    guidance_text: Find the decisions taken here and the core decisions they build on.
  - profile: core-maintainer
    guidance_text: See which core decisions this repository builds on and where it asks core to rule.
  - profile: evaluator
    guidance_text: Read this to see which decisions are taken, proposed or inherited from core.
---

# Decision Records

Decisions taken in this repository. Records are numbered `NNNN-<slug>.md` and copied from
[the template](https://github.com/spec-kitty/spec-kitty-charter/blob/main/docs/_meta/templates/adr.template), as in doc-kitty. The *decision* in an accepted record is immutable: a
later decision supersedes it rather than editing it. A record may still carry a dated,
decision-preserving **clarification** (an annotation that sharpens scope or wording without
changing what was decided); a clarification is labelled and dated, and anything that changes the
decision is a new record instead. The one exception, as in core's records, is a dated
**amendment** under the record's Status that cites the operator ruling changing part of the
decision: the original decision text stays, and each point it changes is annotated where it
stands.

| ID | Title | Status | Date |
|----|-------|--------|------|
| [0001](/adr/0001-java-service-with-openapi-contract/) | ADR-0001: The charter is a Java service built against an OpenAPI contract | Accepted | 2026-10-13 |
| [0002](/adr/0002-contract-source-of-record/) | ADR-0002: This repository is the source of record for the charter contract | Accepted, with Amendment 2026-10-18 | 2026-10-18 |
| [0003](/adr/0003-docs-follow-doc-kitty/) | ADR-0003: Documentation follows the doc-kitty convention | Accepted | 2026-10-13 |
| [0004](/adr/0004-public-api-returns-generated-dtos/) | ADR-0004: The public API returns DTOs generated from the OpenAPI contract | Accepted | 2026-10-14 |
| [0005](/adr/0005-service-registers-and-distributes-packs/) | ADR-0005: The charter offering service registers and distributes packs; built-in is its first catfooding consumer | Proposed, with Amendment 2026-10-19 | 2026-10-18 |
| [0006](/adr/0006-onion-vertical-slices-archunit/) | ADR-0006: Onion layering cut by vertical concern slices, enforced by ArchUnit | Proposed | 2026-10-18 |
| [0007](/adr/0007-rest-hateoas-contract-of-record/) | ADR-0007: REST-HATEOAS is the v1 contract of record; GraphQL is planned-later, MCP deferred | Proposed | 2026-10-18 |
| [0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/) | ADR-0008: The Java charter is a rewrite that replaces the Python charter, built against BDD and OpenAPI contracts | Accepted | 2026-10-18 |
| [0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/) | ADR-0009: Contract 1.0 reserves the cutover and impacts surface; new capabilities arrive as additive contract sets | Accepted (provisional) | 2026-10-19 |
| [0010](/adr/0010-cutover-scope-and-seam-boundary/) | ADR-0010: The 4.x cutover retires every Python offering read; core's ADR governs only the seam routing | Accepted (provisional) | 2026-10-19 |
| [0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/) | ADR-0011: The seam fails closed, the service runs as a daemon, and freshness is fingerprinted | Accepted (provisional) | 2026-10-19 |
| [0012](/adr/0012-pack-mutability-policy/) | ADR-0012: Charter Pack authors declare open, extendable or locked per artifact | Accepted (provisional) | 2026-10-19 |
| [0013](/adr/0013-persistent-deactivation/) | ADR-0013: Deactivation is a persisted exclusion in one activation ledger | Accepted (provisional) | 2026-10-19 |
| [0014](/adr/0014-foundational-values-and-creed-rulings/) | ADR-0014: The creed decisions D-1 to D-5 are recorded here, with refinements | Accepted (provisional) | 2026-10-19 |
| [0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/) | ADR-0015: This repository is canonical for the charter structure, responses, contracts and pack schemas | Accepted (provisional) | 2026-10-19 |
| [0016](/adr/0016-conformance-corpus-harness-over-core-cli/) | ADR-0016: The conformance corpus is a harness over core's CLI, on open-packs and fixture projects | Accepted (provisional) | 2026-10-19 |
| [0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/) | ADR-0017: Charter Pack schemas are imported from core's pydantic models as canonical JSON Schema | Accepted (provisional) | 2026-10-19 |

## Core decisions this repository builds on

These live in [`spec-kitty/spec-kitty`](https://github.com/spec-kitty/spec-kitty) under
`docs/adr/`. They are not copied here.

| Core ADR | Title | Status in core | What it gives this repository |
|---|---|---|---|
| [`4.x/2026-10-12-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-12-2-charter-extraction-to-its-own-repository.md) | The charter becomes an external contract package in its own repository | Superseded (2026-10-19) by [ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/) and [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/) | Why this repository exists; its import-move precondition is done (#6188) |
| [`4.x/2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) | Replace charter reads and writes through a staged Java service strangler | Accepted (2026-10-19, with the amendment of 2026-10-19) | Governs only the seam routing ([ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)): the stages, the switch at `charter.offering.api`, the seam's failure behavior, and the per-worktree daemon boundary ([ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/)) |
| [`4.x/2026-10-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-16-1-extract-charter-offering-first-activation-rework-post-ga.md) | Extract the charter offering first; activation rework after GA | Accepted, P4 amended 2026-10-19 | The import move is done (#6188); activation stays in core until its post-GA rework. Step P4 now replaces the Python offering in place instead of moving it here ([ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)) |
| [`4.x/2026-10-14-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-14-1-event-native-core-one-canonical-event-writer.md) | Event-native core: one canonical event writer | Accepted | Charter-related events, if any, go through this writer; the charter service writes none |
| [`4.x/2026-10-01-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-01-2-mission-status-read-api-and-dashboard-extraction.md) | A mission status read API replaces the CLI-bundled dashboard | Accepted | The sibling Java service: stack, binding, contract conventions, release model |
| [`4.x/2026-10-06-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-06-2-charter-offering-active-charter-and-activation-presets.md) | Charter offering, active charter and activation presets | Accepted | The domain vocabulary; presets as pack data; no aliases or shims |
| [`4.x/2026-10-11-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-11-2-a-pack-declares-its-level-in-pack-yaml.md) | A Charter Pack declares its level in `pack.yaml` | Accepted | `pack_level` and its refusal codes |
| [`3.x/2026-05-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-05-16-1-doctrine-layer-merge-semantics.md) | Doctrine layer merge semantics | Accepted | How built-in, org and project layers merge |
| [`3.x/2026-08-02-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-08-02-1-charter-wheel-assessment.md) | Charter wheel assessment | Accepted | The earlier, deferred wheel split that 2026-10-12-2 extends |
| [`3.x/2026-04-25-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-04-25-1-shared-package-boundary.md) | Shared package boundary | Accepted | How external contract packages are consumed |
