# Research: Slice A2 reads

The operator asked for the mission to run end to end, with reversible calls made by the agent and reported at check-in (2026-11-12). Each call below is one of those, and the operator can reverse any of them.

## R1. Effective skill and references reads answer whatever the state

- **Decision**: under an activation, a skill or a references read is answered for any artifact the closure holds, with its state and reason. A `404 artifact_not_found` means only that no pack of the closure holds it.
- **Rationale**: `getEffectiveArtifact` and `getEffectiveAgentProfile` both answer "whatever its state" (`EffectiveReadService.artifactOf`). A state gate on only two reads would split the tree's semantics.
- **Alternatives**: 404 when not in force, as the spec first said. Rejected for the split it causes. The spec is amended.

## R2. An asset under an activation: the held winner, with no state

- **Decision**: per the operator ruling `DM-01M78S3RNPZ5ZFV5AR6H1X6HV1`, an asset is in force when the closure holds it, and the layer merge picks the copy. The packs-side `ClosureView.MergedView` already merges every kind. The activations side keeps activatable kinds only (`ClosureViewSession.activatable`), so a new `PacksSession` method answers a non-activatable winner from the packs-side merge. `EffectiveAssetRead` carries `activationId`, `revision`, `governanceFingerprint`, `kind`, `artifactId`, `packId`, `level`, `asset` and `_links`, with no `state`, `reason`, `policy` or `required`.
- **Rationale**: an asset is never selected, denied or cascaded to, so a state would be invented. ADR-0074 decision 10 gives each new kind its own schema.
- **Alternatives**: a new `EffectiveArtifactReason` such as `held`. Rejected: it widens an enum slice C also touches, and it states a resolution that never ran.

## R3. `?kind=asset` under an activation

- **Decision**: the generic effective list (`/activations/{a}/artifacts?kind=`) filters by `ActivatableKind`, so `asset` is not one of its tokens there. The list of held assets under an activation is the asset collection path, `/activations/{a}/artifacts/asset`. `?kind=skill` works in both trees, and `?kind=asset` works in the pack tree.
- **Rationale**: the effective list's items carry state and reason (R2). ADR-0074 decision 9's "under an activation, the new kinds included" holds for skill. For asset, its kind-shaped list is the read. This is recorded as a status note on ADR-0074 for the operator's check-in.
- **Alternatives**: list assets in the effective list with an invented reason. Rejected (R2).

## R4. Absent asset or skill

`artifact_not_found`, per operator ruling `DM-01M78RKPH6W3YT9YRPB98DP102`.

## R5. Activation links (#409)

- **Decision**: per operator ruling `DM-01M78RKM6Q23PA9QAC80QJKQFF`, an activation links these relations:
  - `self` and `artifacts` (as today);
  - `offering`;
  - `edit` and `delete`;
  - `select` and `unselect`, `exclude` and `include` (templated);
  - `apply-preset` (templated);
  - `packs`, `graph`, `skipped` and `effective-context` (templated).

  `unselect` and `include` are added beside `select` and `exclude`, because they share a path and the table already pairs them.
- **Alternatives**: only #409's list. Not chosen: the operator ruled for the built reads too.

## R6. Payload headers

- **Decision**: the payload answers the stored bytes, and `Content-Type` is the mime only when it is well-formed `type/subtype` (one rule, shared with `PackPolicy.mimeProblem`) and Spring's `MediaType` parses it. Otherwise it is `application/octet-stream`. The payload also carries `X-Content-Type-Options: nosniff` and `Content-Security-Policy: sandbox`.
- **Rationale**: the mime is pack-authored. A `text/html` payload must not run in a browser that follows a link.

## R7. Preset shape

- **Decision**: `Preset { id, description?, activatedTactics?, activatedDirectives?, …, _links }`, with one array member per activatable kind of `preset.schema.json`, named `activated<Plural>` in camel case. Each item is `PresetItem { id, mustHave }`. `mustHave` is always present (false when the file omits `must_have`). `PresetList { items: [Preset], _links }` lists them in id order, unpaged: a pack holds few presets.
- **Rationale**: ADR-0074 decision 1, with the contract's camel case.

## R8. Skill shape

- **Decision**: `Skill { id, title, description, form, triggers, parameters, invocation?, expandsTo?, tools, body? }`, mirroring `skill.schema.json` in camel case. `body` is present exactly for a prompt skill. The list item is `SkillSummary { id, title, description, form, _links }`.

## R9. Asset shape

- **Decision**: `Asset { id, mime, path, title?, executable? { runtime, minVersion?, entry, args } }`, with `_links.payload`. `AssetSummary { id, mime, executable: boolean, _links }`.

## R10. References twin

- **Decision**: computed from the packs-side merged edge index, with edges re-homed through the winners. It answers the same `ArtifactReferenceList` schema as the pack read, with `effective-artifact` and `artifact` links.
- **Rationale**: the issue asks for "the references of the version in force as the effective graph resolves them". Every kind is answered, so a reference to an asset or a template is kept (the pack read keeps it too).

## R11. The asset id pattern

- **Decision**: pin `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, maxLength 64, in `asset.schema.json` and `AssetIdentifier`.
- **Evidence**: on 2026-11-12, every `*.asset.yaml` in the repository already matched the pattern, so no fixture changes.

## R12. Tags and controllers

- **Decision**: the new tags are `Presets`, `Assets`, `Skills`, `EffectiveAssets` and `EffectiveSkills`. The references twin joins the existing `Activation` effective-read controller tag. One controller per tag, as today.

## Squad findings

Post-plan brownfield squad: see `research/squad-post-plan.md`.
