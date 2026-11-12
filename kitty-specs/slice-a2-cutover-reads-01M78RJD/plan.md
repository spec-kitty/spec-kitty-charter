# Implementation Plan: Slice A2: preset, asset and skill reads

**Branch**: `kitty/mission-slice-a2-cutover-reads-01M78RJD` → `slice-a2-536-cutover-reads` → `main` (PR) | **Date**: 2026-11-12 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/slice-a2-cutover-reads-01M78RJD/spec.md`

## Summary

Serve the reads ADR-0074 decided into slice A, without the glossary (#106, split out):

- **presets in a pack** (#98, P1);
- **`asset` as a served kind, with its payload** (#105);
- **`skill` as a served kind, with its body** (#107);
- **the references read's activation twin** (#532);
- **links on an activation to its writes and effective reads** (#409).

Every read follows ADR-0074 decisions 9 to 14. The reads are built the way the existing literal kind reads (`agent_profile`, `mission_type`) are built: a contract path file per operation, a tag per controller, a packs-side read for the pack tree, and an `EffectiveReadService` read for the activation tree. Each read is proven by `@contract` scenarios that pass the switch-gate register.

The planning interrogation was skipped at the operator's instruction ("drive end to end; make reversible calls yourself and check in later"). Each call made in its place is recorded in [research.md](research.md) as R1 to R12, for the operator's check-in.

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain); OpenAPI 3.1 contract in `contracts/charter/`
**Primary Dependencies**: Spring Boot web (existing), the generated `ai.speckitty.charter.api` interfaces (openapi-generator, `interfaceOnly`, `useTags`), Cucumber for the contract scenarios. No new dependency (NFR-004).
**Storage**: pack files on disk via the existing `PackRepository` / `ClosureView`; activation documents via the existing activation store. No new storage.
**Testing**: unit tests per module (`:packs:test`, `:activations:test`); contract scenarios in `contracts/charter/scenarios/*.feature`, run by `:deployable:test` (`CharterContractSuite`, `SwitchGate`, `LinkChecks`); contract checks (`:contracts:lintContract`, `checkContractCompatibility`, `checkClosedResponseObjects`, `checkRefusalExamples`, `checkContractChangelogFragments`); the whole gate `./gradlew verifyAll --max-workers=1`.
**Target Platform**: the charter service (Linux container), contract consumers via the generated client.
**Project Type**: multi-module service (`service/*`) plus contract tree.
**Performance Goals**: no new budget. Reads reuse one verified `ConsistentRead` / `PacksSession` per request, as today.
**Constraints**: ADR-0074 decisions 9 to 14; the charter's canonical-model rule (no legacy shape); slice C's names are not used (C-004); contract stays additive (0.x).
**Scale/Scope**: 13 new operations, 2 new link relations (`payload`, `presets` on a pack), 1 relation set on an activation, 4 new schemas plus the preset schema.

## Charter Check

| Gate | How this plan meets it |
| --- | --- |
| Divergence from the charter's own model | Shapes come from the charter's pack schemas (`preset.schema.json`, `asset.schema.json`, `skill.schema.json`) in camel case. No core shape is copied. |
| Breaking the contract | Additive only: new paths, schemas and link relations, plus a widened description of `preset_not_found`. Change fragments `### Added` and `### Changed`. |
| Canonical model, no legacy | ADR-0019's path-addressed `/assets/{path}` rows are not built, and the relation table's `assets` row is removed. The asset id pattern is pinned in the canonical schema; every existing fixture already meets it. |
| Tests use only canonical shapes | Invalid shapes appear only in the refusal scenario for a bad asset id. |
| Adversarial squad at plan and before merge | A post-plan brownfield squad runs, and its findings are folded before tasks. A pre-merge squad runs on the aggregate diff. |
| Gates before merge | `verifyAll` on Java 25 with the whole-pack round trip, the docs checks, and the switch-gate register. |
| Issue rules | `Closes #98 #105 #107 #532 #409`; tick #536. Squad minors are folded or listed under "Not done", never filed. |

Result: pass, no violations to justify.

## Project Structure

### Documentation (this mission)

```
kitty-specs/slice-a2-cutover-reads-01M78RJD/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/a2-reads.openapi.yaml   # sketch; contracts/charter is the source of record
├── decisions/                        # Decision Moments of specify
└── research/                         # squad reports (post-plan, pre-merge)
```

### Source Code (repository root)

```
contracts/charter/
├── openapi.yaml                         # 15 path registrations, 2 tags (Presets, Assets, Skills + Effective* twins)
├── paths/packs_packId_presets*.yaml, packs_packId_artifacts_{asset,skill}*.yaml,
│   activations_activationId_artifacts_{asset,skill}*.yaml,
│   activations_activationId_artifacts_kind_artifactId_references.yaml
├── schemas/Preset*.yaml, Asset*.yaml, Skill*.yaml, EffectiveAssetRead.yaml, EffectiveSkillRead.yaml,
│   ArtifactKindToken.yaml, Artifact.yaml, Activation.yaml, CharterRefusalCode.yaml
├── scenarios/pack-presets.feature, assets.feature, skills.feature, effective-assets-and-skills.feature,
│   effective-references.feature, activation-links.feature, every-operation.feature, unexpected-failure.feature,
│   read-refusals.feature, switch-gate.yaml
└── changes/2026-11-12-slice-a2-reads.md (+ -changes.md)
contracts/packs/schemas/asset.schema.json   # id pattern pinned
service/packs/…/application/ArtifactReadService.java, PresetReadService.java (new), AssetReadService.java (new),
    SkillReadService.java (new), ClosureView.java (held winner + references over the merged index)
service/packs/…/infra/api/PresetsController.java, AssetsController.java, SkillsController.java (new), mappers
service/activations/…/application/EffectiveReadService.java, PacksSession.java;
    infra/packs/ClosureViewSession.java, PacksAvailability.java;
    infra/api/EffectiveAssetsController, EffectiveSkillsController, ActivationMapper (links), references twin
docs/api/service-contract.md, docs/development/hypermedia-hateoas.md, docs/adr/0074-… (status note)
```

**Structure Decision**: follow the module boundaries the existing reads use. Pack-tree reads live in `:packs` (as directive and toolguide do). Activation-tree reads live in `:activations`, behind `PacksSession`. ArchUnit's onion rules (ADR-0006) stay unchanged.

## Implementation Concern Map

### IC-01: Presets in a pack (#98, P1)

- **Intent**: `listPackPresets` and `getPackPreset` answer `Pack.presets()` in a `Preset` schema with camel-case `activated<Plural>` lists of `{id, mustHave}`. A pack links `presets`. The description of `preset_not_found` widens to a pack.
- **Touches**: contract paths, schemas and examples; a new `PresetReadService` plus `PresetsController` in `:packs`; the pack mapper link; `CharterRefusalCode.yaml` text; scenarios.
- **Proof**: `pack-presets.feature` covers 200 list, 200 item, 404 `preset_not_found`, 404 `pack_not_found` and 500; switch-gate entries are added.
- **Sequencing/depends-on**: none (first).

### IC-02: Kind gates and the asset id pattern

- **Intent**: `ASSET` and `SKILL` join `SERVED` and `LISTED` (and so `?kind=` in the pack list), and the `ArtifactKindToken` enum. They stay out of `ITEM_KINDS`: the templated item read refuses them with `kind_not_found` and names the literal path. The asset id gets the kebab pattern in `asset.schema.json` and `AssetIdentifier`, so a pack with a bad id is refused as invalid content.
- **Touches**: `ArtifactReadService` sets plus `ABSENT` codes (`ARTIFACT_NOT_FOUND`), `ArtifactKindToken.yaml`, `Artifact.yaml` (members `asset` and `skill`, and the kind description), `asset.schema.json`, `AssetIdentifier`, the pinned-set tests (`ArtifactReadServiceTest`, `WireTokenParityTest`, `EffectiveReadServiceArtifactTest`), and a planted fixture for the bad id.
- **Proof**: unit tests for the sets and the id; a scenario that refuses the bad id at registration; `?kind=asset` and `?kind=skill` list scenarios.
- **Sequencing/depends-on**: none. It must land before IC-03 and IC-04.

### IC-03: Asset reads in a pack (#105)

- **Intent**: `listPackAssets` (`GET …/artifacts/asset`: id, mime, executable), `getPackAsset` (descriptor, with a `payload` link) and `getPackAssetPayload` (bytes).
  - The payload's `Content-Type` is the mime when it is well-formed `type/subtype` and Spring can parse it, and `application/octet-stream` otherwise.
  - It carries `X-Content-Type-Options: nosniff` and `Content-Security-Policy: sandbox`, because the mime is pack-authored.
  - The ETag is the pack revision.
- **Touches**: contract, `AssetReadService`, `AssetsController`, `Payload` streaming, and a shared `MediaTypes.wellFormed` (domain) that `PackPolicy.mimeProblem` also uses, so there is one rule, not two.
- **Proof**: `assets.feature` covers the list, the item, the payload byte equality on a binary fixture (NFR-003), the malformed-mime fallback, the 404s and the headers.
- **Sequencing/depends-on**: IC-02.

### IC-04: Skill reads in a pack (#107)

- **Intent**: `listPackSkills` (id, title, description, form) and `getPackSkill` (the skill's fields, plus `body` for a prompt skill).
- **Touches**: contract, `SkillReadService`, `SkillsController`, `Skill` schema (`SkillParameter`, `SkillInvocation`, `SkillExpansion` mapped camel case).
- **Proof**: `skills.feature` covers the list, a prompt skill with its body verbatim, a wrapper skill without one, and the 404s.
- **Sequencing/depends-on**: IC-02.

### IC-05: Activation twins of the asset and skill reads

- **Intent**:
  - **Skill:** the effective list and item, through `EffectiveReadService.artifact(…, SKILL, …)`, with the body as the winner's pack stores it, and state and reason as every effective read gives them.
  - **Asset:** a new `PacksSession.heldWinner(kind, id)` answers the winner of a non-activatable kind from the packs-side merge (`ClosureView` already merges every kind). `EffectiveAssetRead` carries the activation facts without state and reason, because an asset is not resolved (R2).
  - **Asset list:** the effective asset list is the closure's held assets after the layer merge.
  - **Asset payload:** the winner's bytes.
  - **Conditional reads:** each effective read has an `EffectiveTag` and answers `304`.
- **Touches**: `PacksSession`, `ClosureViewSession`, `EffectiveReadService`, `EffectiveTag`, new controllers under `:activations` infra api, contract and scenarios.
- **Proof**: `effective-assets-and-skills.feature`:
  - a layer override (project over built-in) wins for an asset and its payload;
  - a skill is answered in force, and also not in force;
  - `304`;
  - the 404s: `activation_not_found` and `artifact_not_found`.
- **Sequencing/depends-on**: IC-03, IC-04.

### IC-06: References twin (#532)

- **Intent**: `listEffectiveArtifactReferences` at `/activations/{a}/artifacts/{kind}/{id}/references` answers the winning version's outgoing references, from the merged closure's edge index (all kinds, so a profile's `requires asset:x` is listed). Each reference links `effective-artifact` when its target is a listed kind the closure holds, and links `artifact` to the holder's pack read.
- **Touches**: `ClosureView` (a references query over `MergedView.edges`), `PacksSession`, `EffectiveReadService`, a controller, contract and scenarios.
- **Proof**: `effective-references.feature` covers a requires edge, an overridden source (the edges of the winner are answered), and the 404s.
- **Sequencing/depends-on**: none (it runs beside IC-03 to IC-05).

### IC-07: Activation links (#409)

- **Intent**: `ActivationMapper` adds these links:
  - `offering`;
  - `edit` (PUT self) and `delete` (DELETE self);
  - `select`, `unselect`, `exclude` and `include` (templated `{kind}`/`{artifactId}`);
  - `apply-preset` (templated `{presetId}`, PUT);
  - `packs`, `graph`, `skipped` and `effective-context` (templated).

  The relation table marks each "In the contract" and drops the `assets` row (ADR-0074 decision 13). Slice C adds `prompt`; it is not touched here.
- **Touches**: `ActivationMapper`, `Activation.yaml` `_links` description, `hypermedia-hateoas.md`, `root.feature` / a new `activation-links.feature`.
- **Proof**: a scenario that from the root creates an activation and selects an artifact by links only; `LinkChecks` keeps checking that every link resolves to a built operation.
- **Sequencing/depends-on**: none.

### IC-08: Contract bookkeeping and docs

- **Intent**:
  - `switch-gate.yaml` entries for the 13 operations;
  - `every-operation.feature`, `unexpected-failure.feature` and `read-refusals.feature` rows;
  - change fragments;
  - `docs/api/service-contract.md` rows;
  - an ADR-0074 status note (built in this mission, and the R3 reading of decision 9 for assets under an activation);
  - the `ProfileRoutingTest`-style routing pins for the literal versus templated paths.
- **Proof**: `SwitchGate`, `ReservedOperationCoverageTest`, `ExampleCoverageTest`, docs checks, `verifyAll`.
- **Sequencing/depends-on**: rides with each IC. A final WP sweeps.

## Risks

- **Slice C overlap** in `openapi.yaml`, `switch-gate.yaml`, `every-operation.feature`, the relation table and `Activation.yaml`. The mitigation is to rebase as C lands; the conflicts are additive line conflicts.
- **The asset under an activation has no state.** A client that expects every effective read to carry `state` gets a different shape for assets. Mitigation: its own schema and its own tag, as ADR-0074 decision 10 asks ("each answers its kind's own schema").
- **Serving pack-authored bytes with a pack-authored media type.** Mitigation: the `nosniff` and sandbox CSP headers, the read is token-guarded like every read, and there is no path input (decision 13).
