# OpenAPI contract delta

Canonical contract: `contracts/charter/openapi.yaml` (split-file style, `_shared/` components). This mission **changes the inbound REST contract** (C-004); `meta.json` records `contracts` accordingly.

## Add (tactic retrieval — the new read path)

| Method | Path | operationId | Response |
|--------|------|-------------|----------|
| GET | `/packs/{packId}/tactics` | `listPackTactics` | `200` array of `Tactic`; `404` Problem when the pack is absent/unreadable |
| GET | `/packs/{packId}/tactics/{tacticId}` | `getPackTactic` | `200` one `Tactic`; `404` Problem when pack or tactic is absent |

- `404` uses the shared `Problem` response (`_shared/responses/Problem.yaml`) — client-facing, not `INTERNAL_ERROR`.
- `Tactic` response schema is a new transport schema under `contracts/charter/` (projection of the domain `Tactic`: schema version, name, ordered steps, optional purpose/notes, failure modes, applies-to languages, references). Transport type stays in the inbound adapter (`TRANSPORT_STAYS_IN_ADAPTER` gate).
- Fragment: [`tactics.yaml`](./tactics.yaml).

## Remove (endpoints whose Pack-model read model is not yet ruled)

| Method | Path | operationId (current) | Reason |
|--------|------|-----------------------|--------|
| GET | `/packs` | `listPacks` | pack-listing retired (PacksController deleted) |
| POST | `/packs/refresh` | `refreshPacks` | part of the pack-listing surface (returns the refreshed listing) |
| GET | `/mission-types/{missionType}/actions/{action}/context` | action-context | action-context retired (ContextController deleted) |

Removing these also retires their generated response schemas (`PackListing`, `PackListing1`, `ActionContext`, …) where no longer referenced, and their generated API interfaces.

## Keep

| Method | Path | Owner |
|--------|------|-------|
| GET | `/info` | `InfoController` / `ServiceInfoQuery` (unchanged) |

## Regeneration notes

- Regenerate the API interfaces after editing `openapi.yaml`; the generated `ai.speckitty.charter.api.*` must match (ArchUnit `GENERATED_API_NOT_IN_DOMAIN` / `TRANSPORT_STAYS_IN_ADAPTER` stay green).
- Update `contracts/charter/CHANGELOG.md` for the added/removed operations.
- No pagination component is needed for the tactic list (a pack's tactics are a bounded set); reuse `_shared` Problem only.
