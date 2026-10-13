---
title: API
description: The charter OpenAPI contract — planned surface, where it will live, and how the service and the core generate DTOs from it.
doc_status: draft
updated: 2026-10-13
type: API
kind: Hub
related:
  - architecture/contract-and-dtos
  - adr/0002-contract-source-of-record
---

# API

> **State:** no contract exists yet. This page lists the surface the first releases are
> expected to cover so that the contract work has a target. Every operation below is a
> proposal until it is in `contracts/charter/openapi.yaml`.

The contract is OpenAPI 3.1, served under `/api/v1`, with closed response schemas and
`Problem` bodies for refusals. How it is versioned and generated is in
[Contract and DTOs](/architecture/contract-and-dtos/).

## Planned read surface

| Operation | Answers | Today in core |
|---|---|---|
| Action-scoped context | The guidance for one action (specify, plan, implement, review, merge) in the active charter | `spec-kitty charter context --action <name>` |
| Profile resolution | `(profile_id, action)` to the resolved profile with lineage and overlays, plus compact context | `spec-kitty agent profile show`, profile-load contract |
| Active charter | The project's activations per kind and mission type, with the source of each (preset, project, org requirement) | `spec-kitty charter` status commands |
| Pack listing | Configured packs, their levels, versions and presets | `spec-kitty charter pack list` |
| Artifact lookup | One artifact by URN, with provenance (which pack and layer) | charter offering API |
| Graph query | Edges and closures over the active DRG | `charter.drg` callers |
| Diagnostics | Load and merge refusals, skipped profiles, unresolved edges | `spec-kitty doctor charter-packs --json` |

## Planned write surface (later)

Write operations come only after the read stages, one at a time: activation plan and commit,
charter document save, compile. See [Strangler transition](/architecture/strangler-transition/).

## Transports

- **REST** over loopback, for the core's seam.
- **MCP**, for agent harnesses, after its authorisation decision.

Both call the same read application. Neither implements resolution on its own.
