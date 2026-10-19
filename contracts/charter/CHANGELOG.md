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
- What the cutover needs, reserved before the release (ADR-0009, operator decisions of
  2026-10-19, provisional): a response field added after 1.0 is a breaking change, so these are
  in 1.0 from the start.
  - `Relation` value `impacts`: a directed, signed edge (core ADR 2026-07-26-3). A graph edge
    with relation `impacts` carries its strength in `impacts`, from -1 (sabotages) to +1
    (reinforces); a negative strength is a tension claim. A strength outside [-1, 1], a missing
    one, or one on another relation is refused as `drg_schema_error`.
  - `GuidanceEntry.impacts`: the strength of the edge that brought the artifact in, `null` for
    every relation but `impacts`; guidance is reached by scope edges only, so it is `null`
    throughout this version.
  - `CharterRefusalCode` values `governance_policy_violation` and `stale_input`, **reserved**: no
    operation raises them yet. `governance_policy_violation` is for the pack mutability policy
    (ADR-0012), `stale_input` for the freshness work (#73, ADR-0011); `stale_input` may be
    retried, like `internal_error`.
  - Query parameter `depth` on the action-context operation (integer, 1 to 1, default 1) and
    `ActionContext.depth`, which echoes it. This version answers depth 1 only; raising the
    maximum later is compatible. A depth outside the range is a `400` Problem, not a refusal.
  - `ActionContext.governanceFingerprint`: a `sha256:` digest of the packs in force and the
    merged graph the answer was computed from, independent of host paths, formatting and
    declaration order. New schema `Sha256Digest`.
  - Operation `getServiceInfo`, `GET /info` (tag `Service`), answering the closed `ServiceInfo`:
    `contractVersion`, `worktree` (an opaque id, `charter.worktree-id` or one derived from the
    pack directories, never a host path) and `sourceFingerprint` (a digest of the pack files the
    offering is loaded from). A pack that cannot be read refuses it with a `500`.
  - Response headers `Charter-Contract-Version`, `Charter-Worktree` and
    `Charter-Source-Fingerprint` on every response of the charter's operations, defined once in
    `headers/`. The source fingerprint is left out when a pack file cannot be read. Only the
    values are in this version; comparing them to refuse a stale read is the freshness work
    (#73), which raises `stale_input`.
- Scenarios in `action_context.feature`: "The depth of the walk is echoed, and this version
  answers depth 1 only", "A depth this version does not answer is a bad request, not a charter
  refusal", "The governance fingerprint is the same while the governance is, and changes with
  it", "An impacts edge brings nothing into scope" (which also reads a legacy
  `in_tension_with` edge), "An impacts edge without a strength in [-1, 1] is refused" and "A
  strength on an edge that is not impacts is refused".
- Feature `service.feature`, "What the service says about itself": the info operation, the
  three headers on an answer and on a refusal, and the source fingerprint following the files.
- Steps for digests and headers: `Then the response body, apart from "<field>", is:`, the
  field and header steps, and remembering a field to compare it later (`scenarios/README.md`).

### Changed

- `info.version` is `1.0.0-SNAPSHOT` (was `0.1.0-SNAPSHOT`).
- The three action-context scenarios that answer `200` also expect `depth` and `impacts: null`
  on every entry, and compare the body apart from `governanceFingerprint` (ADR-0009).
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

### Removed

- `Relation` value `in_tension_with`, replaced by `impacts` (ADR-0009, core ADR 2026-07-26-3).
  `reconciles_tension` now points at the sides of a pair joined by a negative `impacts`. Until
  core migrates its graphs, the service reads a legacy `in_tension_with` edge as `impacts` with
  strength -1; it never answers `in_tension_with`.

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
