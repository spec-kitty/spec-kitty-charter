# The 13 reserved activation operations

Every operation: tag `Activation`; `x-reserved: {contractSet: activation, issue: "#125"}`; a
`501` Problem with no code; the three Charter headers on every response; `401`/`403`/`default`
as every other operation. Base path `/api/v1`. `{id}` is `activationId`.

| # | operationId | Method and path | Request | Success | Refusals |
|---|---|---|---|---|---|
| 1 | `createActivation` | `POST /activations` | `ActivationConfig` | `201` Activation, `Location`, `ETag` | 409 `artifact_required`/`artifact_locked`, 415, 422 `activation_invalid` |
| 2 | `replaceActivation` | `PUT /activations/{id}` | `ActivationConfig`; `If-Match?`, `If-None-Match?` | `200` Activation, `ETag`; `201` Activation, `Location`, `ETag` | 409, 412, 415, 422 |
| 3 | `getActivation` | `GET /activations/{id}` | — | `200` Activation, `ETag` | 404 `activation_not_found` |
| 4 | `deleteActivation` | `DELETE /activations/{id}` | `If-Match?` | `204` | 404, 412 |
| 5 | `selectActivationArtifact` | `PUT /activations/{id}/selections/{kind}/{artifactId}` | `SelectionRequest`; `If-Match` | `200` ActivationChange, `ETag` | 404 `activation_not_found`/`artifact_not_found`/`mission_type_not_found`, 412, 415, 422, 428 |
| 6 | `unselectActivationArtifact` | `DELETE /activations/{id}/selections/{kind}/{artifactId}` | `If-Match` | `200` ActivationChange, `ETag` | 404, 412, 428 |
| 7 | `deactivateActivationArtifact` | `PUT /activations/{id}/exclusions/{kind}/{artifactId}` | `ExclusionRequest`; `If-Match` | `200` ActivationChange, `ETag` | 404, 409 `artifact_required`/`artifact_locked`, 412, 415, 422, 428 |
| 8 | `reactivateActivationArtifact` | `DELETE /activations/{id}/exclusions/{kind}/{artifactId}` | `If-Match` | `200` ActivationChange, `ETag` | 404, 412, 428 |
| 9 | `applyActivationPreset` | `PUT /activations/{id}/presets/{presetId}` | `PresetApplication`; `If-Match` | `200` ActivationChange, `ETag` | 404 `activation_not_found`/`preset_not_found`, 412, 415, 422, 428 |
| 10 | `listEffectiveArtifacts` | `GET /activations/{id}/artifacts{?kind,state,pageSize,cursor}` | — | `200` EffectiveArtifactList, `ETag` | 404 |
| 11 | `getActivationSkippedReport` | `GET /activations/{id}/skipped` | — | `200` ActivationSkippedReport, `ETag` | 404 |
| 12 | `getEffectiveGraph` | `GET /activations/{id}/graph` | — | `200` EffectiveGraph, `ETag` | 404 |
| 13 | `getEffectiveActionContext` | `GET /activations/{id}/mission-types/{missionType}/actions/{action}/context{?depth}` | — | `200` EffectiveActionContext, `ETag` | 404 `activation_not_found`/`mission_type_not_found`/`action_not_found` |

Every operation also declares `500` (`OfferingUnreadable`). `412`, `415`, `428` and `501` are
`Problem` bodies without a code (ADR-0020 D9). Request bodies are `required: true`.

Validators: `ETag` is the quoted `"sha256:<64 hex>"`. On `Activation` answers it is the quoted
`revision`; `If-Match` carries it or `*`; `If-None-Match` on `PUT` is `*` only. On the effective
reads it is opaque, a digest over `revision` and `governanceFingerprint`.

Until built, every operation answers `501` before its request is bound (research D-11), so a
malformed request to a reserved operation also gets `501`; only a non-JSON body gets the `415`
its mapping declares.
