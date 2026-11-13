# Contract delta of this mission (planning copy)

Nothing here is bundled. IC-01 writes the real files under `contracts/charter/`, each change with a dated fragment
under `contracts/charter/changes/` (C-001, ADR-0022, 0.x line).

## `effective-step-prompt.draft.openapi.yaml`

The parked draft of the former phase-2 mission (branch `issue-424-adr-0066-phase-2-effective-prompt`), kept as input.
Its open placeholders are now decided:

| Placeholder | Decided |
|---|---|
| OD-1 | Only an enhancement in force refuses (`prompt_slot_missing` wording narrowed, `### Changed`). |
| OD-3 | Phase 1 ruled (a): the load codes are violation codes of `422 validation_failed`; `raised` per that rule. Not touched here. |
| OD-4 | `prompt_slot_missing`: an `operations` map listing **every** operation that answers 422: `getPackRegistration`, the impact-checked pack writes (the per-artifact register/put writes, `putPackRelationship`, `deletePackArtifact`, `deletePackRelationship`) and the activation writes (create, replace, select, unselect, deactivate, reactivate, apply preset); each with a declaration pin in `errors.feature` (squad R4). |
| OD-5 | Stands (2026-11-12): `409 mission_type_not_in_force`, a new enum value, `### Breaking` (research R5). |
| OD-8 | A slot with legacy text is listed with its fragments and no flag; the renderer keeps the text and adds the fragments after it. |

## Changes beyond the draft

| File | Change | Class |
|---|---|---|
| `schemas/EffectiveContextArtifact.yaml` (new) | `EffectiveArtifact`'s members plus required `appliesThrough` (`action`, `mission_type`, `project`), each member with `x-derived` | `### Breaking` (closed schema, item swap; squad R5) |
| `schemas/EffectiveActionContext.yaml` | `artifacts` items are `EffectiveContextArtifact`; the description states the ownership rule; `_links.prompt` | `### Breaking` (the listing's meaning), `### Added` (link) |
| `schemas/CharterRefusalCode.yaml` | enum `mission_type_not_in_force` (409, raised by `getEffectiveStepPrompt`); `prompt_slot_missing` raised, active-only, 422 operations; `override_not_permitted` covers an enhancement of a step of a locked mission type | `### Breaking` (a new enum value; writes and registrations refused that are accepted today, squad R5); `### Changed` for wording only |
| `contracts/lint/ruleset.yaml` | `link-relations-are-known` gains `prompt` | `### Added` |
| `scenarios/errors.feature` | raised-code rows for `prompt_slot_missing` and `mission_type_not_in_force` | — |

One `### Breaking` fragment names every newly refusing operation, the item swap and the context's wider listing, and says
"core: nothing while it is not a consumer (epic #186)". The read and the `prompt` link are `### Added`.
