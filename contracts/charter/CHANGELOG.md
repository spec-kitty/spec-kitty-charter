# Changelog

All notable changes to the `charter` contract are recorded here, newest first. The module
version is the `info.version` of `openapi.yaml`. The contract scenarios under `scenarios/` are
half of the contract, and an OpenAPI diff cannot see them, so every scenario change gets a line
here too.

## 1.0.0-SNAPSHOT

The first contract version core will vendor, not yet released: an unreleased contract version
carries the `-SNAPSHOT` suffix. It replaces the unreleased `0.1.0-SNAPSHOT` draft that landed
before the operator rulings of 2026-10-18. `Added` lists what this version holds that the draft
did not; `Changed` lists what differs from the draft.

### Added

- The `@contract` tag on the contract scenarios in `scenarios/`, which selects them, and the
  `@provisional` tag on `action_context.feature`.
- Provenance on every resource property: `x-source` names the Java producer of a read field,
  `x-derived` the rule and contract inputs of a computed one (`ActionContext.action`,
  `ActionContext.missionType`, `ActionContext.guidance`, `GuidanceEntry.via`); a citation on a
  referenced enum or scalar schema holds wherever that schema is used.
- `x-provisional` on the action-context operation and on `PackSummary.diagnostics`.
- Descriptions of `PackListing.packs`, `Diagnostic.detail` and `ActionContext.guidance`.
- Scenario "A directive scoped by both an action and its mission type is answered once": pins
  the built-in `DIRECTIVE_042` case (one entry, through the action).
- Scenario "A scope edge to a node the graph does not declare is answered without a label":
  pins today's answer (the entry is listed with a `null` label).
- Scenario "A context read over a pack whose graph shard is malformed is refused": pins
  today's `500` with `drg_schema_error`.
- Feature `errors.feature`, "Failures outside the charter's refusals": scenario "A path the
  contract does not define is answered with a not-found problem" pins `404` with the body
  `{"title": "Not Found", "status": 404}`, and scenario "A request that accepts no
  representation the operation has is answered with a not-acceptable problem" pins `406` with
  `{"title": "Not Acceptable", "status": 406}`, served as `application/problem+json`. Both
  bodies are the closed `Problem`; until now these failures answered with the framework's own
  error document (`timestamp`, `error`, `path`) or an empty body.
- The step `When I GET "<path>" accepting "<media type>"`, for the `406` scenario.
- `CharterRefusalCode` value `internal_error` (operator ruling DM
  01M599MEAJS44RYEY0X387BY4H): every 500 is a `CharterRefusal`, and a failure no other code
  names is refused with `internal_error` and a generic detail that names nothing of the
  failure. The `OfferingUnreadable` 500 response says so. Scenario "An unexpected failure is
  refused with internal_error and says nothing about it" in `errors.feature` pins the body,
  with the step `Given the service fails unexpectedly while it reads the packs`.
- The scenario suite checks every response against the contract: the content type must be
  one the operation declares for the status (or its `default`), and the body must validate
  against that content's schema. A path no operation defines must answer the shared
  `Problem` response.

### Changed

- `info.version` is `1.0.0-SNAPSHOT` (was `0.1.0-SNAPSHOT`).
- Every `CharterRefusalCode` and `DiagnosticCode` value is snake_case on the wire:
  `mission_type_not_found`, `action_not_found`, `pack_level_mismatch`, `pack_unreadable`,
  `drg_schema_error`, `pack_level_missing` (`unresolved_edge_endpoint` is unchanged). The
  integration seam in core maps them to core's own spellings. The scenarios in
  `packs.feature` and `action_context.feature` assert the new spellings.
- The action-context answer is sorted by full URN, the order core gives the resolved
  artifacts of an action; it was the order of the edges in the merged graph. Scenario "Action
  scope comes first, then mission-type scope, without repeats" is renamed "The scope of the
  action and of its mission type is answered once each, sorted by URN" and expects that order.
- `GuidanceEntry.via` is described as the node the edge starts from, so it stays true when the
  resolution walks further than one scope edge. `ActionContext.action` and `missionType` have
  descriptions of their own. The three keep their `Urn` type.
- Scenario "An action the mission type does not have is refused, not answered empty" is
  renamed "An action that is not a node of the graph is refused, not answered empty"; the
  answer is the same `404`.
- Every refusal scenario (three in `action_context.feature`, three in `packs.feature`) also
  asserts the whole body with `Then the response body is:`. A refusal body omits an absent
  optional member; it carried `"type": null` and `"instance": null`, which the closed
  `Problem` schema does not allow.
- The `ActionContext.guidance` derivation rule reads "an artifact in both scopes keeps its
  action entry" (wording only).
- Scenario "A pack descriptor without pack_level loads at its configured level, with a
  warning" is renamed "A pack descriptor without pack_level loads at its configured level,
  with a diagnostic", the name the answer gives it (`diagnostics`); the answer is unchanged.

- `CharterRefusalCode` states the consumer dispatch rules (description only, no scenario or
  schema change): a body with a `code` is a charter refusal; a body without one is a transport or
  routing failure; `internal_error` is a fault and may be retried; every other code is a
  deterministic refusal.

### Provisional

- The action-context operation (`x-provisional`): its resolution rules are decided by the
  conformance corpus (issue #9). Not yet done: the `requires`, `suggests` and `vocabulary`
  walks, activation filtering, and degrading a load error to an empty answer with a warning
  as core does. `action_context.feature` is tagged `@provisional`.
- An action that is not a node of the graph answers `404 action_not_found`; for the same input
  core falls back to its non-bootstrap context (`charter/activation/context.py`). The shadow
  comparison (roadmap step 7) maps the charter's `404 action_not_found` to core's fallback and
  counts the pair as matching. The scenario "An action that is not a node of the graph is
  refused, not answered empty" says so.
- A scope edge to a node the graph does not declare is listed with a `null` label; core skips
  that artifact (`charter/activation/context_renderers/delivery_table.py`). The scenario "A
  scope edge to a node the graph does not declare is answered without a label" pins the
  charter's answer and says core omits it.
- `PackSummary.diagnostics` (`x-provisional`): `pack_level_missing` becomes a refusal when core
  ADR 2026-10-11-2 closes its deprecation window.
