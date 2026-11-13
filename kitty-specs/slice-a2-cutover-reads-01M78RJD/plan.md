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
├── openapi.yaml                         # 13 path registrations; tags Presets, Assets, Skills, EffectiveAssets, EffectiveSkills
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
service/packs/…/application/ArtifactReadService.java (kind sets), ArtifactRemovalService.java (REMOVABLE),
    ForeignArtifactRead.java (held, payload), PresetReadService.java (new), ClosureView.java (held winners);
    domain/PackPolicy.java (one media-type rule), AssetIdentifier.java; infra/api/PresetsController.java (new)
service/activations/…/application/EffectiveReadService.java (templated gate, asset winner, EffectiveTag values)
service/artifacts-edge/ (new): application, infra.api (Assets, Skills, EffectiveAssets, EffectiveSkills,
    EffectiveReferences controllers + one body mapper), infra.packs, infra.activations
service/activations/…/infra/api/ActivationMapper.java (links)
service/deployable/src/test/…/HexagonalArchitectureTest.java, ActivationRoutingTest.java
docs/api/service-contract.md, docs/development/hypermedia-hateoas.md, docs/adr/0074-… (status note)
```

**Structure Decision**: presets live in `:packs`; they are pack-only, as `getPackDocument` is. The asset, skill and references reads live in a new edge module, `:artifacts-edge` (R13), because a ring that renders `packs.domain` types under an activation must be able to name both slices, as `:missions-edge` and `:profiles-edge` do. ArchUnit gains the new module's ring rules; the existing rules do not change.

## Implementation Concern Map

The post-plan brownfield squad (`research/squad-post-plan.md`) changed this map. The main change is that the asset, skill and references reads move to a new edge module (IC-00), and they reuse the existing read seams instead of adding new services.

### IC-00: The `:artifacts-edge` module (R13)

- **Intent**: a new Gradle module, `service/artifacts-edge`, modelled on `:missions-edge`:
  - packages `ai.speckitty.charter.artifacts.{application,infra.api,infra.packs,infra.activations}`;
  - registered in `settings.gradle.kts` and `:deployable`;
  - its rings registered in `HexagonalArchitectureTest`, alongside the missions-edge rules: it names packs only from `infra.packs` and activations only from `infra.activations`, and it is not named back;
  - a mutation floor group, as missions-edge has.
- **Proof**: `HexagonalArchitectureTest` and its known-bad fixtures bite on the new rings.
- **Sequencing/depends-on**: none. It must land before IC-03 to IC-06.

### IC-01: Presets in a pack (#98, P1)

- **Intent**: `listPackPresets` and `getPackPreset` answer `Pack.presets()` in `Preset` / `PresetList` (R7).
  - A thin `PresetReadService` in `:packs`, on `ConsistentRead`, with the existing pack check.
  - A `PresetsController` in `packs.infra.api`.
  - A pack answer links `presets`.
  - The `preset_not_found` description widens to a pack.
- **Touches**: contract paths, schemas and examples; `CharterRefusalCode.yaml` text; the relation table row `presets` (a pack; the offering stays proposed).
- **Proof**: `pack-presets.feature` covers the 200 list, the 200 item with `mustHave` true and false, `404 preset_not_found`, `404 pack_not_found` and 500. Switch-gate entries are added.
- **Sequencing/depends-on**: none (first).

### IC-02: Kind gates and the asset id pattern

- **Intent**:
  - In `ArtifactReadService`, ASSET and SKILL join `SERVED` and `LISTED`.
  - One `LITERAL_ITEM_KINDS` set (agent_profile, asset, skill), with `ITEM_KINDS` derived as the listed kinds less the literal ones and less `mission_type`. The derivation changes, not only the set, and agent_profile stays last in `SERVED`.
  - `ABSENT` maps both new kinds to `ARTIFACT_NOT_FOUND`.
  - `ArtifactRemovalService` gates on an explicit `REMOVABLE` set, today's kinds (R14).
  - On the activation side, the templated effective item read gates on activatable ∩ listed, so `asset` is `kind_not_found` and named.
  - `ArtifactKindToken.yaml`, `parameters/ArtifactKind.yaml` and `Artifact.yaml` (members `asset` and `skill`, the kind description) are updated.
  - Asset id: `asset.schema.json` gets the exact skill pattern, and `AssetIdentifier` is backed by `IdentifierGrammar.isLetterFirstSlug` (R11).
- **Touches**: the pinned-set tests (`ArtifactReadServiceTest:547-615,778`, `WireTokenParityTest`, `EffectiveReadServiceArtifactTest:57`, `ClosureViewSessionArtifactTest:248`, `ForeignArtifactReadTest:237`), the text of `artifacts.feature:290`, and a planted fixture for a bad asset id.
- **Proof**:
  - unit tests for the sets and the id;
  - the bad id is refused at registration;
  - `?kind=asset` and `?kind=skill` list in a pack;
  - a DELETE of an asset or skill answers as today (`kind_not_found`), pinned in `pack-removal.feature`;
  - the templated effective read of `asset` gives `kind_not_found`.
- **Sequencing/depends-on**: none. It must land before IC-03 and IC-04.

### IC-03: Asset reads in a pack (#105)

- **Intent**:
  - `listPackAssets` gives id, mime and executable.
  - `getPackAsset` gives the `Artifact` envelope with member `asset` and links `payload` and `collection`.
  - `getPackAssetPayload` gives the bytes.
  - The items ride `ForeignArtifactRead.read`. Beside it, `held(pack, kind)` (lists) and `payload(pack, assetId)` (`Pack.assetFiles()` by `asset.path()`, in the same `ConsistentRead`).
  - The payload is modelled on `getPackDocument` and `PackDocumentETag`: ETag is the pack revision, `If-None-Match` including `*`, 304.
  - `Content-Type` follows R6's single rule; `nosniff` and the sandbox CSP are declared headers.
- **Touches**: contract, the `:artifacts-edge` controllers and body mapper, `ForeignArtifactRead`, and the extraction of one domain media-type rule from `PackPolicy.mimeProblem`, with a test that validate findings are unchanged.
- **Proof**: `assets.feature`:
  - the list and the item;
  - the payload equal byte for byte on a binary (PNG) fixture (NFR-003);
  - the malformed-mime fallback;
  - 304;
  - the headers;
  - the 404s;
  - a request with `Accept: application/json` on the payload.
- **Sequencing/depends-on**: IC-00, IC-02.

### IC-04: Skill reads in a pack (#107)

- **Intent**: `listPackSkills` and `getPackSkill` (the `Artifact` envelope with member `skill`: R8's fields, plus `body` for a prompt skill), riding `ForeignArtifactRead`.
- **Proof**: `skills.feature` covers the list, a prompt skill with its body verbatim, a wrapper skill without a body, and the 404s.
- **Sequencing/depends-on**: IC-00, IC-02.

### IC-05: Activation twins of the asset and skill reads

- **Skill**:
  - `listEffectiveSkills`: the closure's skill winners with state and reason, via the existing effective list query filtered to `skill`.
  - `getEffectiveSkill`: `EffectiveSkillRead`, via `EffectiveReadService.artifact(…, SKILL, …)`. The edge renders the body through the shared mapper, and the pack is read through `infra.packs`.
- **Asset**:
  - `listEffectiveAssets`: the closure's held asset winners, from a new held-winners listing for a non-activatable kind on the packs-side merge.
  - `getEffectiveAsset`: `EffectiveAssetRead` (R2), with the winner from `winnerPackOf` / `effectiveArtifact`.
  - `getEffectiveAssetPayload`: R15. The winner and fingerprint come from activations; packs reads the bytes against that fingerprint, and a mismatch is `stale_input`.
- **Shared**: each read gets a new `EffectiveTag.Read` value, so no two URLs share a tag, and answers 304, following the agent-profile precedent.
- **Proof**: `effective-assets-and-skills.feature`:
  - a project copy wins over a built-in for an asset and its payload;
  - a skill is answered both in force and not in force;
  - 304;
  - `activation_not_found` and `artifact_not_found`;
  - a unit test for `stale_input`.
- **Sequencing/depends-on**: IC-03, IC-04.

### IC-06: References twin (#532)

- **Intent**: `listEffectiveArtifactReferences` (R10) answers the pack read of the winner's pack, verified against the session fingerprint, with `effective-artifact` links where the closure holds the target. It lives in `:artifacts-edge`.
- **Proof**: `effective-references.feature`:
  - a `requires` edge;
  - an overridden source (the winner's references);
  - a parity scenario: the same referrer answers the same items in both trees, including an agent profile's field-derived references;
  - the 404s.
- **Sequencing/depends-on**: IC-00.

### IC-07: Activation links (#409)

- **Intent**: `ActivationMapper` adds:
  - `offering`;
  - `edit` (PUT) and `delete` (DELETE);
  - `select` and `exclude` (templated, PUT);
  - `apply-preset` (templated, PUT);
  - `packs`, `graph`, `skipped` and `effective-context` (templated).

  The relation table rewrites the `packs`, `select`, `exclude`, `edit`, `delete`, `skipped`, `effective-context`, `apply-preset` and `offering` rows, adds `payload`, and drops `assets` (ADR-0074 decision 13). The lint ruleset enum follows. Slice C adds `prompt`; this concern does not touch it.
- **Proof**: `activation-links.feature`, which from the root creates an activation and selects an artifact by links only. `LinkChecks` and `RelationTableParityTest` stay green.
- **Sequencing/depends-on**: none.

### IC-08: Contract bookkeeping and docs

- **Intent**:
  - `switch-gate.yaml` entries for the 13 operations;
  - `every-operation.feature`, `unexpected-failure.feature` and `read-refusals.feature` rows;
  - change fragments (`### Added`, `### Changed`);
  - `docs/api/service-contract.md` rows;
  - the ADR-0074 amendment of decision 9 (R3) and the "built" status, and the ADR-0019 annotation;
  - routing pins in `ActivationRoutingTest` and a pack-tree twin for the literal-versus-templated paths, including `…/asset/{id}/payload` versus `{kind}/{id}/references`.
- **Proof**: `SwitchGate`, `ReservedOperationCoverageTest`, `ExampleCoverageTest`, the docs checks, `verifyAll`.
- **Sequencing/depends-on**: rides with each concern. A final WP sweeps.

## Risks

- **Slice C overlap** in `openapi.yaml`, `switch-gate.yaml`, `every-operation.feature`, the relation table and `Activation.yaml`. The mitigation is to rebase as C lands; the conflicts are additive line conflicts.
- **The asset under an activation has no state.** A client that expects every effective read to carry `state` gets a different shape for assets. Mitigation: its own schema and its own tag, as ADR-0074 decision 10 asks ("each answers its kind's own schema").
- **Serving pack-authored bytes with a pack-authored media type.** Mitigation: the `nosniff` and sandbox CSP headers, the read is token-guarded like every read, and there is no path input (decision 13).
