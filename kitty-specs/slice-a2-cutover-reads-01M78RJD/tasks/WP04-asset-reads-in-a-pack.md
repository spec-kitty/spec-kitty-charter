---
work_package_id: WP04
title: Asset reads in a pack (#105)
dependencies:
- WP02
- WP03
requirement_refs:
- FR-003
- FR-004
- FR-005
- FR-010
- NFR-003
- C-002
planning_base_branch: slice-a2-536-cutover-reads
merge_target_branch: slice-a2-536-cutover-reads
branch_strategy: Planning artifacts for this mission were generated on slice-a2-536-cutover-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-a2-536-cutover-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-slice-a2-cutover-reads-01M78RJD
base_commit: 13c0700c81c2468400222f88ebb3f5bc9335f173
created_at: '2026-11-12T23:22:02.399866+00:00'
subtasks:
- T016
- T017
- T018
- T019
- T020
- T021
phase: ''
history:
- at: '2026-11-12T22:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/
create_intent:
- contracts/charter/paths/packs_packId_artifacts_asset.yaml
- contracts/charter/paths/packs_packId_artifacts_asset_artifactId.yaml
- contracts/charter/paths/packs_packId_artifacts_asset_artifactId_payload.yaml
- contracts/charter/schemas/AssetSummary.yaml
- contracts/charter/schemas/AssetList.yaml
- contracts/charter/headers/XContentTypeOptions.yaml
- contracts/charter/headers/ContentSecurityPolicy.yaml
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MediaTypes.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/MediaTypesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ForeignArtifactReadHeldTest.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/AssetReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/AssetView.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/PacksAssets.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/AssetsController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/AssetBodies.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/PayloadResponses.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/AssetsControllerTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/PayloadResponsesTest.java
- contracts/charter/scenarios/assets.feature
- contracts/charter/changes/2026-11-12-asset-reads.md
execution_mode: code_change
owned_files:
- contracts/charter/paths/packs_packId_artifacts_asset.yaml
- contracts/charter/paths/packs_packId_artifacts_asset_artifactId.yaml
- contracts/charter/paths/packs_packId_artifacts_asset_artifactId_payload.yaml
- contracts/charter/schemas/AssetSummary.yaml
- contracts/charter/schemas/AssetList.yaml
- contracts/charter/headers/XContentTypeOptions.yaml
- contracts/charter/headers/ContentSecurityPolicy.yaml
- contracts/charter/examples/AssetList*.yaml
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ForeignArtifactRead.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MediaTypes.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/MediaTypesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ForeignArtifactReadHeldTest.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/AssetReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/AssetView.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/PacksAssets.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/AssetsController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/AssetBodies.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/PayloadResponses.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/AssetsControllerTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/PayloadResponsesTest.java
- contracts/charter/scenarios/assets.feature
- contracts/charter/changes/2026-11-12-asset-reads.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP04 - Asset reads in a pack (#105)

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/slice-a2-cutover-reads-01M78RJD/`:

- `spec.md`
- `plan.md`, its concern for this WP
- `research.md`
- `research/squad-post-plan.md`

## Objective

Serve a pack's assets (#105; ADR-0074 decisions 2, 9 to 11 and 13; research.md R6, R9 and R15; FR-003 to FR-005 and FR-010; NFR-003).

| Request | Answer |
|---|---|
| `GET /packs/{p}/artifacts/asset` (`listPackAssets`, tag `Assets`) | `200 AssetList` `{items: [AssetSummary{id, mime, executable, _links.self}], _links}`, id order, unpaged |
| `GET /packs/{p}/artifacts/asset/{artifactId}` (`getPackAsset`) | `200 Artifact` with member `asset`; envelope `_links`: `self`, `payload`, `collection` (the asset list) |
| `GET …/asset/{artifactId}/payload` (`getPackAssetPayload`) | `200`, exact bytes; `Content-Type` = mime when well-formed, else `application/octet-stream`; `X-Content-Type-Options: nosniff`; `Content-Security-Policy: sandbox`; `ETag` = pack revision |
| matching `If-None-Match` (including `*`) on the payload | `304` |
| unknown pack / absent asset | `404 pack_not_found` / `404 artifact_not_found` |
| unreadable | `500 pack_unreadable` |

## Context

- `service/packs/src/main/java/ai/speckitty/charter/packs/application/ForeignArtifactRead.java:124-149` reads any kind in one `ConsistentRead`, with the pack check, `ARTIFACT_NOT_FOUND`, the revision and `STALE_INPUT`.
- `Pack.assets()` is at `Pack.java:297`, and `Pack.assetFiles()` at :315 gives `AssetFile(path, Payload)`. `Payload` holds the exact bytes and `sha256()`.
- The well-formed rule is `PackPolicy.java:560-569` `mimeProblem`.
- The precedent for a non-JSON read is `PackRegistrationController#getPackDocument` (:139-158), with `PackDocumentETag` / `ContractDocumentETag`, because Spring's own `If-None-Match` check mishandles `*` and malformed tags.
- Placement: `:artifacts-edge` (WP03). The edge's `infra.packs` adapter calls `ForeignArtifactRead`; `infra.api` maps the domain asset into the DTOs. `infra.api` must not name packs types: map through the application ring's `AssetView` record.
- Squad findings #6, #8, #9 and #13.

## Subtasks

### T016: Contract

Write the three path files, modelled on `packs_packId_artifacts_agent_profile_artifactId.yaml` and `packs_packId_doc.yaml`:

- the `Assets` tag;
- `AssetSummary`, `AssetList` and a list example;
- `headers/XContentTypeOptions.yaml` and `headers/ContentSecurityPolicy.yaml`;
- the payload `200` declared as `content: {'*/*': {schema: {type: string, format: binary}}}`, with the description stating the Content-Type rule and the transport fallback (R6);
- the `304`;
- a 406 is not declared, unless the doc read declares one: payload negotiation is "whatever it is". State that the payload ignores `Accept` beyond what Spring needs, and test `Accept: application/json`.

### T017: `ForeignArtifactRead.held` and `payload`

- `held(PackIdentifier, ArtifactKind)` returns the pack's artifacts of a kind, with the revision.
- `payload(PackIdentifier, String assetId)` returns the asset, its `AssetFile` bytes and the revision, in one `ConsistentRead`.
- `payload(PackIdentifier, String assetId, String expectedFingerprint)` is the same, but refuses `STALE_INPUT` when the read's source fingerprint differs. WP06 uses it.
- The unit tests cover `held`, `payload`, the fingerprint mismatch, and an asset whose payload file is missing, which is impossible after registration but asserts a refusal rather than an NPE.

### T018: One media-type rule

- Extract the slash rule from `PackPolicy.mimeProblem` into `domain/MediaTypes.wellFormed(String)`, and make `mimeProblem` call it.
- Write `MediaTypesTest`.
- Prove validate findings are unchanged: the existing `PackPolicy` tests pass untouched.

### T019: Asset list and item

- `AssetReads` (application) with `AssetView` (id, mime, path, title, executable).
- `PacksAssets` (infra.packs) adapts `ForeignArtifactRead`.
- `AssetsController implements AssetsApi`. The `AssetBodies` mapper (infra.api) is the one mapper WP06 reuses.
- The item answers the `Artifact` envelope (`kind: asset`, `id`, `packId`, `_links`, `asset`), with the ETag being the pack revision, as `getPackAgentProfile` does.

### T020: Payload

- `PayloadResponses` (infra.api) builds a `ResponseEntity<byte[]>`:
  - the ETag, with 304 through the same `If-None-Match` handling as `PackDocumentETag` (reuse it if it is reachable from the edge through web-transport; otherwise extract it there, as a recorded out-of-map edit);
  - `Content-Type`: `MediaTypes.wellFormed(mime)`, and Spring `MediaType.parseMediaType` succeeds, gives the mime; otherwise octet-stream;
  - `nosniff` and `sandbox`.
- WP06 reuses `PayloadResponses` for the activation twin.

### T021: Scenarios

Write `contracts/charter/scenarios/assets.feature`, with the pack written inline. It covers:

- an executable `text/x-python` asset and a binary `image/png` asset (base64 in the step, or reuse an existing binary step); the payload must equal byte for byte (NFR-003);
- a malformed-mime asset, if registration accepts one (check whether `asset_mime_invalid` is blocking at registration: if it refuses, test the fallback in the unit test only and say so);
- the 304, the headers, `Accept: application/json` on the payload, and the 404s.

Add the registry rows for the three operations, and the fragment `2026-11-12-asset-reads.md` (`### Added`).

**Shared registries (out-of-map rows, recorded).** Add rows for this WP's operations to the files WP01 owns:

- `contracts/charter/openapi.yaml`
- `contracts/charter/scenarios/switch-gate.yaml` (`ready`)
- `every-operation.feature`, `unexpected-failure.feature` and `read-refusals.feature`
- the relation table and the lint enum, only for relations this WP introduces

Record each edit in the commit message as "out-of-map: registry rows for <operationIds>". Keep each file's existing order: path order in openapi.yaml, and the table orders.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :contracts:check :packs:test :artifacts-edge:test
./gradlew --max-workers=1 :deployable:test   # contract scenarios, switch gate, link checks, ArchUnit
```
Run Gradle in the foreground, one run at a time; never `--stop`.

## Definition of Done

- Every row of the table is proven.
- The payload is byte-equal.
- No packs type is named in the edge outside `infra.packs` (ArchUnit green).
- The switch gate reports `ready` ×3.

## Risks and reviewer guidance

- Spring's implicit ETag/304 or content negotiation must not answer anything the contract does not declare.
- `nosniff` and the CSP are present on 200, not only declared.
- The domain rule stays the validate rule, so there is no second rule (R6).

## Branch Strategy

- **Planning base and merge target:** `slice-a2-536-cutover-reads`.
- **Mission branch:** `kitty/mission-slice-a2-cutover-reads-01M78RJD`.
- **Worktrees:** allocated per computed lane (`lanes.json`).
- **Implement with:** `spec-kitty agent action implement WP04 --agent claude --mission slice-a2-cutover-reads-01M78RJD`.
- **Commits:** sign them if signing works. Add no model or `Co-Authored-By` trailer. Reference the issue, for example `(#105)`.

## Coordination

Slice C (#467) is in flight and changes:

- the `prompt` path and link relation;
- four schemas;
- `mission_type_not_in_force` in `CharterRefusalCode`;
- `ClosureQueries.closureFingerprint` ordering.

Do not use or change those names. Do not pin fingerprint values in tests.
