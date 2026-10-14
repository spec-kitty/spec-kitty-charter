---
title: Service contract
description: The designed OpenAPI 3.1 contract of the charter service — planned read and write operations, the error model and the transports. Designed only; no contract exists yet.
doc_status: draft
updated: 2026-10-14
type: API
kind: Explanation
related:
  - architecture/contract-and-dtos
  - adr/0002-contract-source-of-record
  - api/compatibility
---

# Service contract

> **Designed API — not implemented here.** No contract and no service code exist in this
> repository yet. Every operation below is a proposal until it is in
> `contracts/charter/openapi.yaml`. The surface is derived from the reads core's Python
> charter answers today (`charter.activation` and `charter.offering` in
> `spec-kitty/spec-kitty`), from core ADR `2026-10-03-1` and from
> [ADR-0001](/adr/0001-java-service-with-openapi-contract/) and
> [ADR-0002](/adr/0002-contract-source-of-record/).

The contract is OpenAPI 3.1, served under `/api/v1`, with closed response schemas and
`Problem` bodies for refusals. How it is laid out, generated and versioned is in
[Contract and DTOs](/architecture/contract-and-dtos/).

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

The first path to be specified is action-scoped context ([Roadmap](/plans/roadmap/), step 1).

## Planned write surface (later)

Write operations come only after the read stages, one at a time: activation plan and commit,
charter document save, compile. See [Strangler transition](/architecture/strangler-transition/).

## Error model

- Every refusal is a `Problem` body, reusing the shared `Problem` schema of core's
  `contracts/_shared/`.
- Refusal codes are part of the contract, not implementation detail. The codes core uses
  today carry over unchanged, for example `PACK_LEVEL_MISMATCH`, `PACK_LEVEL_MISSING` and
  `unresolved_edge_endpoint`.
- Fail loud: a dropped artifact, an unresolved edge, an unresolved profile or a stale input is
  a named refusal or a diagnostic, never an empty success.

## Transports

- **REST** over loopback, for the core's seam. See [Service lifecycle](/architecture/service-lifecycle/).
- **MCP**, for agent harnesses, after its authorization decision
  ([open questions](/plans/open-questions/), question 7).

Both call the same read application. Neither implements resolution on its own.
