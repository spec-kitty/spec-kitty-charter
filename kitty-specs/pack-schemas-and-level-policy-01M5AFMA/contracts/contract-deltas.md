# Contract deltas

These are the only edits under `contracts/charter/` (spec C-001, FR-013). Each starts as a red
scenario and gets a line under `## 1.0.0-SNAPSHOT` in `contracts/charter/CHANGELOG.md`.

| # | File | Change | Ruling | Breaking-change check |
|---|------|--------|--------|-----------------------|
| D1 | `schemas/DiagnosticCode.yaml` | Add the enum value `pack_unknown_key` | R5 | oasdiff `response-property-enum-value-added` would be breaking against a 1.x baseline. There is no `contract-charter-v*` tag, so the gate passes. Risk K3 |
| D2 | `schemas/CharterRefusalCode.yaml` | The `unresolved_edge_endpoint` description says it is no longer raised (endpoints are URNs) | R6 | Description only, compatible (`compatible-descriptions-reworded` fixture) |
| D3 | `scenarios/packs.feature` | New: a `pack.yaml` with an undeclared key loads with a `pack_unknown_key` diagnostic | R5 | — |
| D4 | `scenarios/packs.feature` | Changed: a bare edge endpoint → `drg_schema_error`, "edge endpoint 'QA_404' is not a URN" | R6 | — |
| D5 | `scenarios/packs.feature` | New: `requires_core: banana` → `pack_unreadable` | R3 | — |
| D6 | `scenarios/packs.feature` | New: an org pack with no `drg/fragment.yaml` → `pack_unreadable` | R16 | — |
| D7 | `scenarios/packs.feature` (and any other scenario file with such a fixture) | Fixtures: fragment endpoints become URNs, `pack_name` is dropped, and an org pack without a fragment gets one | R6, R7, R16 | — |
| D8 | `scenarios/packs.feature`, contract `CHANGELOG.md` | Changed: a graph shard that is not valid YAML is refused with `drg_schema_error` (was `pack_unreadable`); the text `<o> is not valid YAML: <first line>` is unchanged. `pack.yaml` and artifact parse failures keep their codes | R17 | The refusal code is a response enum value already in the contract, so oasdiff sees no schema change; record it in the CHANGELOG as a behavior change |
