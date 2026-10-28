---
okf_version: "0.2"
title: Decision Records
description: The immutable log of decisions taken in the spec-kitty-charter repository, and the core Spec Kitty ADRs they build on.
doc_status: active
updated: 2026-10-28T00:00:00Z
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
| [0003](/adr/0003-docs-follow-doc-kitty/) | ADR-0003: Documentation follows the doc-kitty convention | Accepted, with Amendment 2026-10-19 | 2026-10-13 |
| [0004](/adr/0004-public-api-returns-generated-dtos/) | ADR-0004: The public API returns DTOs generated from the OpenAPI contract | Accepted | 2026-10-14 |
| [0005](/adr/0005-service-registers-and-distributes-packs/) | ADR-0005: The charter offering service registers and distributes packs; built-in is its first catfooding consumer | Accepted, with Amendment 2026-10-19 | 2026-10-18 |
| [0006](/adr/0006-onion-vertical-slices-archunit/) | ADR-0006: Onion layering cut by vertical concern slices, enforced by ArchUnit | Accepted | 2026-10-21 |
| [0007](/adr/0007-rest-hateoas-contract-of-record/) | ADR-0007: REST-HATEOAS is the v1 contract of record; GraphQL is planned-later, MCP deferred | Accepted, with Amendment 2026-10-28 (#208: the contract serves itself, first realized conditional GET) | 2026-10-18 |
| [0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/) | ADR-0008: The Java charter is a rewrite that replaces the Python charter, built against BDD and OpenAPI contracts | Accepted | 2026-10-18 |
| [0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/) | ADR-0009: Contract 1.0 reserves the cutover and impacts surface; new capabilities arrive as additive contract sets | Accepted, with Amendment 2026-10-19; superseded in part by ADR-0028 (2026-10-26) | 2026-10-19 |
| [0010](/adr/0010-cutover-scope-and-seam-boundary/) | ADR-0010: The 4.x cutover retires every Python offering read; core's ADR governs only the seam routing | Accepted | 2026-10-19 |
| [0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/) | ADR-0011: The seam fails closed, the service runs as a daemon, and freshness is fingerprinted | Accepted, with Amendment 2026-10-19; superseded in part by ADR-0034 (2026-10-26) and ADR-0038 (2026-10-27) | 2026-10-19 |
| [0012](/adr/0012-pack-mutability-policy/) | ADR-0012: Charter Pack authors declare open, extendable or locked per artifact | Accepted; superseded in part by ADR-0030 and ADR-0031 (2026-10-26) | 2026-10-19 |
| [0013](/adr/0013-persistent-deactivation/) | ADR-0013: Deactivation is a persisted exclusion in one activation ledger | Accepted; superseded in part by ADR-0033 (2026-10-26) | 2026-10-19 |
| [0014](/adr/0014-foundational-values-and-creed-rulings/) | ADR-0014: The creed decisions D-1 to D-5 are recorded here, with refinements | Accepted; superseded in part by ADR-0028 (2026-10-26) | 2026-10-19 |
| [0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/) | ADR-0015: This repository is canonical for the charter structure, responses, contracts and pack schemas | Accepted | 2026-10-19 |
| [0016](/adr/0016-conformance-corpus-harness-over-core-cli/) | ADR-0016: The conformance corpus is a harness over core's CLI, on open-packs and fixture projects | Accepted, with Amendment 2026-10-19 | 2026-10-19 |
| [0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/) | ADR-0017: Charter Pack schemas are imported from core's pydantic models as canonical JSON Schema | Accepted, with Amendments 2026-10-19 and 2026-10-20; superseded in part by ADR-0028 (2026-10-26) and ADR-0036 (2026-10-27) | 2026-10-20 |
| [0018](/adr/0018-action-context-read-and-seam-access-surface/) | ADR-0018: The action-context read is the offering's walk, and the seam reaches the service with a token, a fingerprint and a refresh | Accepted, with Amendments through 2026-10-28 (D10 reconciled with ADR-0026 write surface; 304-no-body carve-out #208); superseded in part by ADR-0038 (2026-10-27) | 2026-10-25 |
| [0019](/adr/0019-packs-resource-model-and-hypermedia/) | ADR-0019: Packs are a navigable REST resource with hypermedia links | Accepted | 2026-10-24 |
| [0020](/adr/0020-offering-and-activations/) | ADR-0020: The offering and activations are separate resources, and the service stores activations | Accepted; superseded in part by ADR-0033 (2026-10-26) | 2026-10-24 |
| [0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/) | ADR-0021: Core adopts the generated client now, behind a proxy that routes each operation | Accepted | 2026-10-24 |
| [0022](/adr/0022-contract-versions-start-at-0-x/) | ADR-0022: The contract is published as 0.x while it iterates, and 1.0.0 is minted once it is stable | Accepted | 2026-10-24 |
| [0023](/adr/0023-ci-gate-required-check/) | ADR-0023: An always-on CI gate is the single required status check | Accepted | 2026-10-22 |
| [0024](/adr/0024-doctrine-artifact-family-contract/) | ADR-0024: Charter kinds share a thin CharterArtifact interface over per-kind entities and ports | Accepted, with Amendment 2026-10-26; superseded in part by ADR-0035 and ADR-0037 (2026-10-27) | 2026-10-24 |
| [0025](/adr/0025-relationships-first-class-and-lazy-repository-walk/) | ADR-0025: Relationships are first-class domain value objects, walked lazily through per-kind repositories | Accepted; superseded in part by ADR-0028 (2026-10-26) and ADR-0036 (2026-10-27) | 2026-10-25 |
| [0026](/adr/0026-write-surface-is-functional-registration/) | ADR-0026: The write surface is functional registration of packs, artifacts and relationships, not CRUD over storage | Accepted; superseded in part by ADR-0030 (2026-10-26) | 2026-10-25 |
| [0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/) | ADR-0027: CRG relationship identity is a bounded, conditional unidirectional collapse | Accepted; superseded in part by ADR-0028 (2026-10-26); clarified by ADR-0036 (2026-10-27) | 2026-10-25 |
| [0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/) | ADR-0028: `impacts` is an artifact's per-value list, and `tension` is the relationship between two rules | Accepted | 2026-10-26 |
| [0029](/adr/0029-charter-glossary-is-owned-here/) | ADR-0029: This repository owns the charter glossary | Accepted | 2026-10-26 |
| [0030](/adr/0030-removal-is-distinct-from-deactivation/) | ADR-0030: Removal by a derived pack is distinct from deactivation by a project | Accepted | 2026-10-26 |
| [0031](/adr/0031-requirement-is-authored-as-a-preset/) | ADR-0031: Requirement is authored as a preset, and the required set is derived from it | Accepted | 2026-10-26 |
| [0032](/adr/0032-steps-name-roles-and-profile-selection-order/) | ADR-0032: Steps name roles, and a profile is selected by role fit, then specialization, then fallback | Accepted | 2026-10-26 |
| [0033](/adr/0033-local-active-charter-is-source-of-truth/) | ADR-0033: The project's local activation file is the source of truth for its active charter | Accepted | 2026-10-26 |
| [0034](/adr/0034-local-and-hosted-deployment-options/) | ADR-0034: The charter service has a local and a hosted deployment option | Accepted; decision 5 resolved in part by ADR-0041 (2026-10-28, proposed) | 2026-10-26 |
| [0035](/adr/0035-charter-artifact-interface-lives-in-the-kernel/) | ADR-0035: The shared artifact interface lives in the kernel | Accepted | 2026-10-27 |
| [0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/) | ADR-0036: Round-trip fidelity is equality of meaning, and unknown fields are refused | Accepted; extended in part by ADR-0039 (2026-10-27); superseded in part by ADR-0040 (2026-10-27, proposed) | 2026-10-27 |
| [0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/) | ADR-0037: A kind-keyed pack registry and the whole-pack seam | Accepted; extended in part by ADR-0039 and ADR-0040 (2026-10-27) | 2026-10-27 |
| [0038](/adr/0038-one-configuration-root-a-directory-per-domain/) | ADR-0038: The service keeps its files under one configuration root, a directory per domain | Accepted | 2026-10-27 |
| [0039](/adr/0039-a-kind-binding-can-carry-companion-files/) | ADR-0039: A kind binding can carry companion files | Accepted, with Amendment 2026-10-27 | 2026-10-27 |
| [0040](/adr/0040-content-form-and-payload-kinds/) | ADR-0040: A file-kind rule declares its content form, and asset payloads and templates are pack content | Proposed | 2026-10-27 |
| [0041](/adr/0041-containerized-configuration-model/) | ADR-0041: The containerized service is configured through a container profile, validated properties and mounted secrets | Proposed | 2026-10-28 |

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
