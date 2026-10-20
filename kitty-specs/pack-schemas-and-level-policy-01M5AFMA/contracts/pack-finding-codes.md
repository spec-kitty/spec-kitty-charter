# Pack finding codes

These are the stable codes `PackPolicy` and the adapter emit. A code on the load path projects
onto an existing contract code. A validate-mode-only code never reaches the wire in this mission.

## Catalogue

| Code | Severity | Emitted on | Wire projection (load path) | Spec |
|---|---|---|---|---|
| `pack_level_missing` | warning | load and validate | `Diagnostic` `pack_level_missing` (unchanged) | FR-001 |
| `pack_level_mismatch` | blocking | load and validate | refusal `pack_level_mismatch` (unchanged) | FR-001 |
| `pack_unknown_key` | warning | load and validate | `Diagnostic` `pack_unknown_key` (**new contract value**) | R5, R13 |
| `pack_unreadable` | blocking | load and validate | refusal `pack_unreadable` | FR-008 |
| `requires_core_invalid` | blocking | load and validate | refusal `pack_unreadable` | R3 |
| `org_fragment_missing` | blocking | load and validate | refusal `pack_unreadable` | R16 |
| `drg_schema_error` | blocking | load and validate | refusal `drg_schema_error` | FR-008, R6, R7 |
| `artifact_schema_error` | blocking | validate | none | FR-010 |
| `org_charter_schema_error` | blocking | validate | none | FR-010 |
| `duplicate_step_id` | blocking | validate | none | FR-009 |
| `duplicate_artifact_id` | blocking | validate | none | R16 |
| `asset_path_escape` | blocking | validate | none | R16 |
| `asset_mime_invalid` | blocking | validate | none | R16 |

## Pinned detail texts (FR-003)

| Text | Pinned by | Changed? |
|---|---|---|
| `<lvl> pack <name> has no pack.yaml; the configured level <lvl> is used` | `packs.feature:43`, `OpenPacksRoundTripTest` | no |
| `pack.yaml of <name> has no pack_level; the configured level <lvl> is used` | `packs.feature:62`, `ProductionWiringTest` | no |
| `Pack '<name>' is configured as <lvl> but declares pack_level <lvl>` | `packs.feature:80` | no |
| `<origin>: unknown key '<k>'` | `packs.feature:115`, `GraphDocumentReaderTest` | no |
| `<origin> node: expected a mapping` | `GraphDocumentReaderTest` | no |
| `<origin>: a node of kind '<k>' has an empty id` | `GraphDocumentReaderTest` | no (the pattern on `id` maps to it) |
| `<origin>: '<k>' must be a number` | `GraphDocumentReaderTest` | no |
| `<origin>: 'impacts' <v> is outside [-1, 1]` (NaN included) | `GraphDocumentReaderTest` | no (stays in `DrgEdge`) |
| `<origin>: an impacts edge is missing 'impacts'` | `GraphDocumentReaderTest` | no |
| `<origin>: a <rel> edge has no 'impacts'` | `GraphDocumentReaderTest` | no |
| `pack.yaml of <name> has no <key>` | `PackDescriptorReader` (pinned by a new test first) | no |
| `pack.yaml of <name>: <key> must be a string` | `PackDescriptorReader` (pinned by a new test first) | no |
| `pack.yaml of <name> declares an unknown pack_level '<t>'` | pinned by a new test first | no |
| `pack.yaml of <name> is not a mapping` | pinned by a new test first | no |
| `<origin>: edge endpoint '<t>' is neither a URN nor an id this document declares` | `packs.feature:98` | **yes**: it becomes `drg_schema_error` with `<origin>: edge endpoint '<t>' is not a URN` (R6) |

## New texts (pinned by WP06 and WP07 tests)

| Code | Detail |
|---|---|
| `requires_core_invalid` | `pack.yaml of <n>: requires_core '<v>' is not a PEP 440 version specifier` |
| `pack_unknown_key` | `pack.yaml of <n> declares unknown key '<k>'; it is ignored` |
| `org_fragment_missing` | `org pack <n> has no drg/fragment.yaml` |
| `duplicate_step_id` | `<f>: step id '<id>' is declared more than once` |
| `duplicate_artifact_id` | `<f>: <kind> id '<id>' is already declared in <first-file>` |
| `asset_path_escape` | `<f>: asset path '<p>' escapes the pack's assets/ root` |
| `asset_mime_invalid` | `<f>: asset mime '<m>' is not a well-formed 'type/subtype' value`, or `<f>: asset mime '<m>' does not match the '.<ext>' extension (<expected>)` |
| `drg_schema_error` (R6) | `<o>: edge endpoint '<t>' is not a URN` |

## Core mapping

This is the #115 parity mapping (FR-016). Comparison is on `(file, code, severity)`, after core's
findings are de-duplicated by `(file, category)`.

| Core category / severity | Charter code / severity | Note |
|---|---|---|
| pack.yaml `schema_invalid` (extra key) / error | `pack_unknown_key` / warning | Divergence (R5) |
| `RETIRED_PACK_FIELD` / error | `pack_unknown_key` / warning | Divergence (R13) |
| pack.yaml `schema_invalid` on `requires_core` / error | `requires_core_invalid` / blocking | |
| pack.yaml other `schema_invalid`, `parse_error` / error | `pack_unreadable` / blocking | |
| `PACK_LEVEL_MISSING` / advisory | `pack_level_missing` / warning | |
| `PACK_LEVEL_MISMATCH` / error | `pack_level_mismatch` / blocking | |
| drg `schema_invalid`, `parse_error` / error | `drg_schema_error` / blocking | |
| `drg_dangling_edge` (malformed or unresolved) / error | `drg_schema_error` / blocking | A bare local id core accepts is a divergence (R6) |
| `org_pack_missing` / error | `org_fragment_missing` / blocking | |
| artifact `schema_invalid` / error | `artifact_schema_error` / blocking | |
| step contract "duplicate step IDs" / error | `duplicate_step_id` / blocking | |
| `duplicate_id` / error | `duplicate_artifact_id` / blocking | |
| `asset_path_escape`, `asset_mime_invalid` / error | same / blocking | MIME table divergence possible (P8) |
| org-charter schema (no category) / error | `org_charter_schema_error` / blocking | |
| `drg_external_endpoint`, `drg_kind_drift`, `profile_skipped`, `intent_conflict`, `unknown_target`, `same_id_collision`, `pack_sanction`, `preset_*`, `drg_root_graph_missing`, `duplicate_drg_edge`, `drg_builtin_unavailable`, `org_charter_unchecked`, org-charter advisories | excluded | They need the built-in layer, or are out of this mission's scope (#115 follow-ups) |
