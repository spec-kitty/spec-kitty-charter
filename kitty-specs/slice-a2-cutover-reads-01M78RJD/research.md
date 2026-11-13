# Research: Slice A2 reads

The operator asked for the mission to run end to end. Reversible calls are made here and reported at check-in (2026-11-12). Each call below is one of those, and the operator can reverse any of them. The post-plan brownfield squad (`research/squad-post-plan.md`) revised R2, R3, R6, R7, R8, R10 and R11, and added R13 to R15.

## R1. Effective skill and references reads answer whatever the state

- **Decision**: under an activation, a skill or references read is answered for any artifact the closure holds, with its state and reason. A `404 artifact_not_found` means only that no pack of the closure holds it.
- **Rationale**: `getEffectiveArtifact` and `getEffectiveAgentProfile` answer whatever the state (`EffectiveReadService.artifactOf`). A state gate on two reads alone would split the tree.

## R2. An asset under an activation: the closure's winner, with no state

- **Decision**: operator ruling `DM-01M78S3RNPZ5ZFV5AR6H1X6HV1`. The asset item reuses `ClosureView.winnerPackOf` / `effectiveArtifact`, which already answer a winner of any kind from the packs-side merge. The only new query is a listing of the held winners of a non-activatable kind, for the asset list.
- **Answer shape**: `EffectiveAssetRead` carries `activationId`, `revision`, `governanceFingerprint`, `kind` (const `asset`), `artifactId`, `packId`, `level`, `policy`, `asset` and `_links`. It has no `state`, `reason`, `required`, `satisfiedBy` or `suggestion`: an asset is never selected, denied or cascaded to, so those would be invented. `policy` is a real fact of the winning copy.
- **Alternatives**: an invented `EffectiveArtifactReason` such as `held`. Rejected: it states a resolution that never ran, and it widens an enum slice C touches.

## R3. Assets and the generic effective list (amends ADR-0074 decision 9)

- **Decision**:
  - `?kind=skill` lists skills in both trees. Under an activation it already does: `skill` is an `ActivatableKind`. This change adds the `effective-artifact` and `artifact` links its items lack today.
  - `?kind=asset` lists assets in a pack. The effective list does not take `asset`: `ActivationController.kindFilter` answers 400 for a non-activatable kind, as today.
  - Under an activation, the held assets are listed at `/activations/{a}/artifacts/asset`.
  - The templated effective item read refuses `asset` with `kind_not_found` and names the literal path.
- **Rationale**: the effective list's items carry state, reason, policy and required (R2). The packs-side merge already rules that assets are never listed by the effective reads (decision `01M702A71YF7WNDTG2AA89K8ZA`, `ClosureView.java:704-706`).
- **Recording**: an ADR-0074 amendment section (dated 2026-11-12). It is the agent's call under the operator's "make reversible calls" instruction, and an open review item at check-in.

## R4. Absent asset or skill

`artifact_not_found` (operator ruling `DM-01M78RKPH6W3YT9YRPB98DP102`).

## R5. Activation links (#409)

- **Decision**: operator ruling `DM-01M78RKM6Q23PA9QAC80QJKQFF`. An activation links these relations:
  - `self` and `artifacts`, as today;
  - `offering`;
  - `edit` (PUT) and `delete` (DELETE);
  - `select` and `exclude`, templated `{kind}`/`{artifactId}` (PUT);
  - `apply-preset`, templated `{presetId}` (PUT);
  - `packs`, `graph`, `skipped` and `effective-context` (templated).
- **Dropped after the squad**: `unselect` and `include`. They suit an artifact that is already selected or excluded, and they stay as the table places them.
- **Relation table**: the `packs` row is rewritten to cover both placements (the root's registered packs, and an activation's packs). The `select`/`exclude` rows add "an activation (templated)". The `edit` row's "not used" note is removed. The lint ruleset's `link-relations-are-known` enum follows (`RelationTableParityTest`).

## R6. Payload media type: one rule (adjudicated)

- **Decision**: "well-formed" is the existing domain rule, `PackPolicy.mimeProblem`: a slash with text on both sides. The rule is extracted to one domain function, which both validate and the payload read call.
  - The payload's `Content-Type` is the mime when that rule holds. Otherwise it is `application/octet-stream`.
  - The transport falls back to octet-stream only if Spring cannot emit the header value. That is a limit of the transport, documented in the path file, and not a second judgment.
  - The payload carries `X-Content-Type-Options: nosniff` and `Content-Security-Policy: sandbox`, both declared under `contracts/charter/headers/`.
- **Rationale**: tightening the domain rule to RFC 6838 would make `asset_mime_invalid` (BLOCKING) refuse packs that register today. ADR-0074 rejected exactly that ("Refuse a malformed mime at load"). The architect and reducer findings asked for one rule, and this keeps one rule.

## R7. Preset shape

- **Decision**: `Preset { id, description, activated<Plural>?…, _links }`. `description` is required, as `preset.schema.json` and the domain `Preset` require it.
  - There is one array per activatable kind, named after `PresetYaml` / `PresetFiles` (the file's `activated_<plural>`, in camel case), in the file's kind order.
  - Each item is `PresetItem { id, mustHave }`.
  - `schemaVersion` is not answered: the domain does not hold it.
  - `PresetList { items, _links }` is in id order and unpaged, deliberately, because a pack holds few presets. Paging it later would be an additive change.

## R8. Skill shape

- **Decision**: `Skill { schemaVersion, id, title, description, form, triggers, parameters, invocation?, expandsTo?, tools, version?, maintainers?, body? }`, mirroring `skill.schema.json` in camel case. `body` is present exactly for a prompt skill.
- **Left out**:
  - `bodyPath`: the contract answers content, not paths, as `Toolguide.guide` does.
  - `overrides` and `enhances`: edges are read through references and the graph, as for every kind.
- **Lists**: `SkillSummary { id, title, description, form, _links }`. `SkillList` is unpaged, deliberately.

## R9. Asset shape

- **Decision**: `Asset { id, mime, path, title?, executable? { runtime, minVersion?, entry, args } }`. `AssetSummary { id, mime, executable: boolean, _links }`. `AssetList` is unpaged, deliberately.
- **Links**: `payload` and `collection` (the asset list) go on the `Artifact` envelope's `_links`.

## R10. References twin: the pack read of the winner

- **Decision**: `listEffectiveArtifactReferences` answers `ArtifactReadService.references(winnerPack, kind, id)`, using the same `Reference` record and mapper as the pack read. It is read in a `ConsistentRead` checked against the session's fingerprint, and a mismatch is `stale_input`.
  - Each reference adds an `effective-artifact` link when the activation's closure holds its target and the kind is listed.
  - The winner is the closure's winner of `(kind, id)`, so an overridden artifact answers its winning copy's references.
- **Rationale**: one rule per fact. `ReferencedArtifact`'s `x-source` stays true. A parity scenario reads the same referrer in both trees.

## R11. The asset id pattern

- **Decision**: copy `skill.schema.json`'s pattern exactly, `^(?!.*\n)[a-z][a-z0-9]*(-[a-z0-9]+)*$`, with `maxLength` 64, into `asset.schema.json`. Back `AssetIdentifier` with `IdentifierGrammar.isLetterFirstSlug`.
- **Evidence**: every `*.asset.yaml` and inline fixture id already matches it (checked 2026-11-12, confirmed by the squad).

## R12. Tags

The new tags are `Presets`, `Assets`, `Skills`, `EffectiveAssets`, `EffectiveSkills` and `EffectiveReferences`, with one controller per tag.

## R13. The `:artifacts-edge` module (squad BLOCKER)

- **Decision**: a new edge module, `:artifacts-edge`, with the packages `ai.speckitty.charter.artifacts.{application, infra.api, infra.packs, infra.activations}`. It is modelled on `:missions-edge` and holds:
  - the pack-tree and activation-tree asset and skill reads, with one body mapper;
  - the references twin.

  It names packs only from `infra.packs`, and activations only from `infra.activations`. `HexagonalArchitectureTest` registers its rings as it does for missions-edge, and `:deployable` depends on it. Presets stay in `:packs`: they are pack-only and are not artifacts.
- **Rationale**: `ACTIVATIONS_NAME_PACKS_ONLY_FROM_ADAPTER` forbids rendering `packs.domain` `Asset`/`Skill` from `:activations`, and copying their shapes into `activations.domain` would be split-brain. The profiles and missions edges are the precedent (ADR-0006 as amended 2026-11-09, ADR-0037). Both trees live in one module so the body mapping exists once.

## R14. Removal keeps its own kind set

- **Decision**: `ArtifactRemovalService` gates on an explicit `REMOVABLE` set, today's removable kinds. So `DELETE …/artifacts/{asset|skill}/{id}` answers as it does today (`kind_not_found`), and a scenario pins it.
- **Rationale**: widening `LISTED` must not silently open a write. Removing an asset needs a payload rule (`AssetBinding` maps no companion), which is out of scope. "Asset and skill removal" goes under the PR's "Not done".

## R15. A verified payload under an activation

- **Decision**: the missions-edge pattern. The activations side answers the winner pack, level, policy and the session's source fingerprint. The packs side reads the asset and its bytes (`Pack.assetFiles()` by `asset.path()`) in a `ConsistentRead`, compares the fingerprint, and on a mismatch answers `500 stale_input`. Both payload reads follow `getPackDocument` and `PackDocumentETag` for the ETag, `If-None-Match` (including `*`) and 304.
