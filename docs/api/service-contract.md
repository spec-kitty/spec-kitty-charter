---
title: Service contract
description: The OpenAPI 3.1 contract of the charter service — the two built reads, the planned reads and writes, the error model and the transports.
doc_status: draft
updated: 2026-10-19
type: API
kind: Reference
related:
  - architecture/contract-and-dtos
  - adr/0002-contract-source-of-record
  - api/compatibility
audience:
  - profile: contract-consumer
    guidance_text: Read this for the planned operations, the error model and the transports of the service.
---

# Service contract

The planned operations, error model and transports of the charter service's OpenAPI contract. The surface derives from the reads core's Python charter answers today (`charter.activation` and `charter.offering` in core), from core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) (the staged Java service strangler), [ADR-0001](/adr/0001-java-service-with-openapi-contract/) and [ADR-0002](/adr/0002-contract-source-of-record/). Every operation is a proposal until it is in `contracts/charter/openapi.yaml`.

> **Partly built.** Three read operations are in the contract and served by the Java service; the other operations on this page are designed only. See the [API overview](/api/).

## The contract files

The contract is in [`contracts/charter/`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/openapi.yaml), at `1.0.0-SNAPSHOT`, unreleased:

- [`openapi.yaml`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/openapi.yaml): the header and the map from each path to its file;
- `paths/`, `schemas/`, `parameters/`, `responses/` and `examples/`: one definition per file;
- [`scenarios/`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/scenarios/README.md): the contract scenarios, the behavior half of the contract;
- [`CHANGELOG.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/CHANGELOG.md): every change, scenario changes included, and what is provisional.

The single-file bundle that core vendors is built from them by `./gradlew :contracts:bundleContract`
and uploaded by CI as the `contract-bundle` artifact
([Building the service](/guides/service-build/)).

The contract is OpenAPI 3.1, served under `/api/v1`, with closed response schemas and
`Problem` bodies for refusals. How it is laid out, generated and versioned is in
[Contract and DTOs](/architecture/contract-and-dtos/).

## Built read surface

| Operation | Answers | Path |
|---|---|---|
| `listPacks` | The configured Charter Packs, their levels and versions, with the diagnostics of each | `GET /packs` |
| `getActionContext` | The guidance for one action of one mission type, the `depth` of the walk (1 in this version) and a fingerprint of the governance it was computed from; provisional (`x-provisional`) until the conformance corpus decides its rules | `GET /mission-types/{missionType}/actions/{action}/context` |
| `getServiceInfo` | The contract version the service was built against, the worktree it serves and a fingerprint of the pack files it answers from | `GET /info` |

Every response of these operations carries the `Charter-Contract-Version`, `Charter-Worktree`
and `Charter-Source-Fingerprint` headers, so the seam in core can check it talks to the right
service about the right files. Some of the 1.0 contract is reserved for the cutover work before
that work exists, because a response field added after 1.0 is a breaking change: the `impacts`
relation, two refusal codes and these values (ADR-0009, provisional).

## Planned read surface

| Operation | Answers | Today in core |
|---|---|---|
| Action-scoped context | The guidance for one action of one mission type in the active charter (action nodes are `action:<mission-type>/<action>`; the CLI's bootstrap set is specify, plan, implement and review). The request carries the mission type and the load depth | `spec-kitty charter context --action <name> --mission-type <type>` |
| Profile resolution | `(profile_id, action)` to the resolved profile with lineage and overlays, plus compact context | `spec-kitty agent profile show`, profile-load contract |
| Active charter | The project's activations per kind and mission type, with the source of each (preset, project, org requirement) | `spec-kitty charter` status commands |
| Pack listing | Configured packs, their levels, versions and presets | `spec-kitty charter pack list` |
| Artifact lookup | One artifact by URN, with provenance (which pack and layer) | `charter.offering` repositories |
| Graph query | Edges and closures over the active DRG | `charter.offering.drg` callers |
| Diagnostics | Load and merge refusals, skipped profiles, unresolved edges | `spec-kitty doctor charter-packs --json` |

Pack listing and action-scoped context are the first two in the contract ([Roadmap](/plans/roadmap/), step 1).

## Planned write surface (later)

Write operations come only after the read stages, one at a time: activation plan and commit,
charter document save, compile. See [Strangler transition](/architecture/strangler-transition/).

## Error model

- Every refusal is a `Problem` body. The shared `Problem` schema and response in
  `contracts/_shared/` are canonical in this repository (ADR-0015), and every answer, the
  `default` `Problem` response included, carries the three `Charter-*` headers.
- Refusal codes are part of the contract, not implementation detail. They are snake_case on
  the wire; the integration seam in core maps them to core's own spellings. The codes and
  when each applies are defined in [`CharterRefusalCode`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/schemas/CharterRefusalCode.yaml),
  and the load findings that do not stop a read in [`DiagnosticCode`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/schemas/DiagnosticCode.yaml).
  Two codes are reserved and raised by no operation yet: `governance_policy_violation`, for the
  pack mutability policy, and `stale_input`, for a read whose files changed while it was
  answered.
- Fail loud: a dropped artifact, an unresolved edge, an unresolved profile or a stale input is
  a named refusal or a diagnostic, never an empty success.

## Transports

- **REST** over loopback, for the core's seam. See [Service lifecycle](/architecture/service-lifecycle/).
- **MCP**, for agent harnesses, after its authorization decision
  ([open questions](/plans/open-questions/), question 7).

Both call the same read application. Neither implements resolution on its own.
