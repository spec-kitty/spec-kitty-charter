# Data model: the activation set

Every object is closed (`additionalProperties: false`). `null` is never a value. Tokens are
snake_case. Lists without `minItems` may be empty.

## Vocabulary

- **ActivatableKind**: `directive`, `tactic`, `styleguide`, `toolguide`, `paradigm`, `procedure`,
  `agent_profile`, `mission_step_contract`, `glossary_pack`, `skill`, `anti_pattern` (NodeKind
  minus `template`, `asset`, `action`, `mission_type`).
- **SelectableKind**: ActivatableKind plus `mission_type` (path `{kind}` of selections only;
  exclusions take ActivatableKind).
- **CascadeKind**: ActivatableKind plus `all`.
- **ArtifactId**: the declared id, the tail of the artifact's URN; string, `minLength: 1`,
  `maxLength: 128`, pattern `^[A-Za-z0-9][A-Za-z0-9_.-]*$`.
- **ActivationId**: pattern `^[A-Za-z0-9][A-Za-z0-9-]{0,63}$` (ADR-0020 D2).
- **Revision**: `Sha256Digest`.

## ActivationConfig (the document; `PUT`/`POST` body, `Activation.config`)

| Member | Type | Required | Absent means | Notes |
|---|---|---|---|---|
| `schemaVersion` | integer, `minimum: 1`, `maximum: 1` | yes | — | |
| `packs` | array of `PackPin`, `minItems: 1` | no | the service's offering | merge order; a later pack wins |
| `activatedKinds` | array of ActivatableKind | no | every kind | `[]` = no kind |
| `missionTypes` | array of `Slug` | no | none in force | `[]` = none; kept distinct from absent |
| `selected` | `SelectedArtifacts` | no | every kind at its default | |
| `deactivated` | `DeactivatedArtifacts` | no | no exclusion | |
| `languages` | array of `LanguageToken` | no | no language filter | as authored; derived languages are not sent |

Left out by ruling: per-entry provenance, `contextActivations`, `appliedPresets`.

- **PackPin**: `packId` (`Slug`, required), `version` (string, `minLength: 1`, `maxLength: 64`;
  absent = the version the service holds).
- **SelectedArtifacts**: one optional property per ActivatableKind, each an array of ArtifactId.
  Absent = the kind's default (every artifact; for `skill` only the required skills). `[]` = none.
- **DeactivatedArtifacts**: one optional property per ActivatableKind, each an array of
  ArtifactId with `minItems: 1`.
- **LanguageToken**: string, pattern `^[a-z][a-z0-9+#._-]*$`, `maxLength: 32`.

**Canonical form** (what the service stores and returns): ids in declared spelling, id and token
lists deduplicated and sorted (`packs` keeps merge order; a duplicate `packId` is `422`), empty
deny lists omitted, members in schema order.
**Revision**: `sha256:` + hex SHA-256 of the JCS (RFC 8785) serialization of the canonical config.

## Activation (envelope; `200`/`201` of `PUT`, `POST`, `GET`)

| Member | Type | Source |
|---|---|---|
| `activationId` | ActivationId | path, or minted on `POST` |
| `revision` | Revision | derived from `config` |
| `config` | ActivationConfig | canonical form |
| `governanceFingerprint` | Sha256Digest | the offering the summary was derived for |
| `summary` | `ActivationSummary` | derived: `active`, `inactive`, `excluded`, `required`, `locked`, `skipped` (integers >= 0, all required) |
| `_links` | `Links` | relations (`self`) |

- **Links** (ADR-0019 D3): open relation keys, each a closed **Link** (`href` required,
  `templated`, `method`, `title`). Shared with #126.

## ActivationChange (`200` of every sub-resource write)

| Member | Type | Meaning |
|---|---|---|
| `revision` | Revision | the new revision |
| `applied` | array of `ArtifactReference` | ids whose state the write changed |
| `cleared` | array of `ArtifactReference` | exclusions the write cleared (select, `includeDeactivated`, preset `force`) |
| `skipped` | SkippedReport | what the walk skipped |
| `_links` | Links | `self` is the activation |

Every success schema below (and `Activation` above) carries a required `_links` (`Links`, `self` only).

- **ArtifactReference**: `kind` (SelectableKind), `artifactId` (ArtifactId).

## SkippedReport (`ActivationChange.skipped`, `GET …/skipped`, effective context)

| Member | Items |
|---|---|
| `skippedByScope` | ArtifactReference: reached but outside the cascade scope |
| `skippedDeactivated` | `SkippedDeactivated`: `kind`, `artifactId`, `viaRequires` (boolean), `requiredBy` (array of ArtifactReference: the artifacts whose edge reached it) |
| `unreachedBehindDeactivated` | ArtifactReference: reachable only through a deactivated artifact |
| `skippedShared` | `SharedSkip`: `kind`, `artifactId`, `referencedBy` (ArtifactReference: the active artifact that still references it) |

All four required (possibly empty). `GET …/skipped` answers `ActivationSkippedReport`:
`activationId`, `revision`, `governanceFingerprint`, `report` (SkippedReport), `_links`.
`SkippedDeactivated.requiredBy` lists artifacts; `CharterRefusal.requiredBy` lists packs; each
description names its item type and points at the other.

## Effective reads

- **EffectiveArtifact**: `kind` (ActivatableKind), `artifactId`, `state` (`active`, `inactive`,
  `excluded`), `required` (boolean), `policy` (`open`, `extendable`, `locked`; ADR-0012), `reason`
  (`selected`, `kind_default`, `required`, `locked`, `deactivated`, `not_selected`,
  `kind_not_activated`, `language_filtered`).
- **EffectiveArtifactList**: `activationId`, `revision`, `governanceFingerprint`, `items`
  (EffectiveArtifact), `pageInfo` (`_shared` PageInfo), `_links`. Query: `kind` (ActivatableKind),
  `state`, and the shared page parameters.
- **EffectiveGraph**: `activationId`, `revision`, `governanceFingerprint`, `_links`, `nodes`
  (`EffectiveGraphNode`: `urn` (Urn), `kind` (NodeKind)), `edges` (`EffectiveGraphEdge`: `source` (Urn),
  `relation` (Relation), `target` (Urn)).
- **EffectiveActionContext**: `activationId`, `revision`, `governanceFingerprint`, `missionType`
  (Slug), `action` (Slug), `artifacts` (EffectiveArtifact), `skipped` (SkippedReport), `_links`.
  Query: `depth` (bounded as ADR-0018).

## Request bodies

- **SelectionRequest**: `cascade` (`CascadeScope`), `includeDeactivated` (boolean). Both optional.
- **ExclusionRequest**: `cascade`. Optional.
- **PresetApplication**: `force` (boolean). Optional; absent means `false`.
- **CascadeScope**: array of CascadeKind, `minItems: 1`; `all` stands alone: a list mixing `all`
  with kinds is valid by schema and refused semantically, `422 activation_invalid` with a
  `violations` entry at `/cascade` (squad S23).
- All three bodies are `required: true` on their operations (send `{}` for the defaults).

## Refusals

- New `CharterRefusalCode` values, all `raised: false`: `activation_not_found` (404),
  `artifact_not_found` (404), `preset_not_found` (404), `artifact_required` (409),
  `artifact_locked` (409), `activation_invalid` (422).
- `Problem` (vendored, `_shared`) gains optional `requiredBy` (array of `RequiringPack`:
  `packId`, `via` (array: the `extends:` chain, possibly empty); only on `409`
  `artifact_required`/`artifact_locked`) and `violations` (array of `RefusalViolation`: `pointer`
  (RFC 6901), `code` (snake_case), `detail`; only on `422 activation_invalid`). `RequiringPack`
  and `RefusalViolation` live in `_shared/schemas` with inline patterns.
- Existing codes reused: `mission_type_not_found` (an unknown mission type on select or op 13),
  `action_not_found` (op 13); their descriptions are rewritten.
