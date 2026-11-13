---
work_package_id: WP01
title: Presets in a pack (#98, P1)
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-015
- C-001
- C-003
planning_base_branch: slice-a2-536-cutover-reads
merge_target_branch: slice-a2-536-cutover-reads
branch_strategy: Planning artifacts for this mission were generated on slice-a2-536-cutover-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-a2-536-cutover-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-slice-a2-cutover-reads-01M78RJD
base_commit: 71f4ca87df6b8c511807e0200b2389f187889675
created_at: '2026-11-12T21:48:06.750724+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
phase: ''
history:
- at: '2026-11-12T22:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/paths/
create_intent:
- contracts/charter/paths/packs_packId_presets.yaml
- contracts/charter/paths/packs_packId_presets_presetId.yaml
- contracts/charter/schemas/Preset.yaml
- contracts/charter/schemas/PresetItem.yaml
- contracts/charter/schemas/PresetList.yaml
- contracts/charter/scenarios/pack-presets.feature
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PresetReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PresetsController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PresetMapper.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PresetReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PresetsControllerTest.java
- contracts/charter/changes/2026-11-12-pack-presets.md
execution_mode: code_change
owned_files:
- contracts/charter/openapi.yaml
- contracts/charter/scenarios/switch-gate.yaml
- contracts/charter/scenarios/every-operation.feature
- contracts/charter/scenarios/unexpected-failure.feature
- contracts/charter/scenarios/read-refusals.feature
- docs/development/hypermedia-hateoas.md
- contracts/lint/ruleset.yaml
- contracts/charter/paths/packs_packId_presets.yaml
- contracts/charter/paths/packs_packId_presets_presetId.yaml
- contracts/charter/schemas/Preset.yaml
- contracts/charter/schemas/PresetItem.yaml
- contracts/charter/schemas/PresetList.yaml
- contracts/charter/examples/Preset*.yaml
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/scenarios/pack-presets.feature
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PresetReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PresetsController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PresetMapper.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationMapper.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/Relation.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PresetReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PresetsControllerTest.java
- contracts/charter/changes/2026-11-12-pack-presets.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP01 - Presets in a pack (#98, P1)

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/slice-a2-cutover-reads-01M78RJD/`:

- `spec.md`
- `plan.md`, its concern for this WP
- `research.md`
- `research/squad-post-plan.md`

## Objective

Serve a pack's presets (ADR-0074 decision 1; research.md R7; FR-001 and FR-002).

| Request | Answer |
|---|---|
| `GET /packs/{packId}/presets` | `200 PresetList` `{items: [Preset], _links: {self, pack}}`, items in id order, unpaged |
| `GET /packs/{packId}/presets/{presetId}` | `200 Preset` |
| unknown pack | `404 pack_not_found` (the pack is judged first) |
| preset the pack does not hold | `404 preset_not_found` |
| unreadable pack | `500 pack_unreadable` (`responses/OfferingUnreadable.yaml`) |

Both 200s carry `ETag` = the quoted pack revision, `Charter-Source-Fingerprint` and the shared headers, as `getPackDocument` does. A pack's answer links `presets`.

## Context

- **Domain:** `service/packs/src/main/java/ai/speckitty/charter/packs/domain/Preset.java` (`id`, `description`, `Map<ArtifactKind, List<PresetItem>> artifacts`) and `PresetItem.java` (`id`, `mustHave`), read through `Pack.presets()`.
- **File naming:** `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PresetYaml.java` and `PresetFiles.java` give the `activated_<plural>` naming and the kind order. Reuse them (or the kind's `plural` token) so the wire names cannot drift from the file names.
- **Shape:** `description` is required. `schemaVersion` is not answered. Each list is `activated<Plural>` in camel case: `activatedTactics`, `activatedAgentProfiles`, and so on. Emit only the kinds the preset lists, and keep the schema closed.
- **Refusal:** `preset_not_found` is at `contracts/charter/schemas/CharterRefusalCode.yaml:61-62` and `x-refusal` at about line 347. Widen its description from "the activation's closure" to "a pack, or the activation's closure". `getPackPreset` joins its raisers. Add no new code.
- **Precedent for a pack-scoped read:** `PackRegistrationController#getPackDocument`, plus a `ConsistentRead` with the pack check (`PACK_NOT_FOUND` / `PACK_UNREADABLE`), as `ForeignArtifactRead.read` does at `service/packs/src/main/java/ai/speckitty/charter/packs/application/ForeignArtifactRead.java:124-149`.

## Subtasks

### T001: Contract

- Add `paths/packs_packId_presets.yaml` (`listPackPresets`) and `paths/packs_packId_presets_presetId.yaml` (`getPackPreset`), with a new tag `Presets` (and a tag description at the top of `openapi.yaml`). Register both after `/packs/{packId}/doc`.
- Write the schemas `Preset`, `PresetItem` and `PresetList`. Each needs `x-source` or `x-derived` annotations, as neighbouring schemas have, `additionalProperties: false`, and examples under `examples/`.
- Write the description text the contract-description checks require: who judges first, every refusal, and the ETag meaning (a pack revision, not a cache validator of anything outside the pack).

### T002: `PresetReadService`

- Keep it thin, on `ConsistentRead`: `list(PackIdentifier)` and `read(PackIdentifier, String presetId)`, returning the revision with the value.
- Unit-test the two 404s, the ordering, and `mustHave` true and false.

### T003: Controller and link

- `PresetsController implements PresetsApi`, with a `PresetMapper` that maps the domain to the DTOs.
- Add a `presets` link to the pack answer (`PackRegistrationMapper`, beside `doc`), and a `Relation.PRESETS` value if needed.
- Controller test: headers and ETag.

### T004: Scenarios

Write `contracts/charter/scenarios/pack-presets.feature`, with `@contract` scenarios and a pack written inline (see `activation-presets.feature` for preset files). It covers:

- the list with two presets;
- the item with a must-have item and a plain item;
- `preset_not_found`;
- `pack_not_found`.

Add registry rows:

- the switch-gate `ready` entries;
- every-operation rows (401, 403);
- unexpected-failure rows (500, in the pack table);
- read-refusals rows if the pattern applies.

### T005: Relation table, lint and fragment

- In `docs/development/hypermedia-hateoas.md`, mark `presets` "In the contract" on a pack (the offering stays Proposed). Keep `contracts/lint/ruleset.yaml`'s `link-relations-are-known` enum in parity (`RelationTableParityTest`).
- Write the fragment `contracts/charter/changes/2026-11-12-pack-presets.md`: `### Added` for the two operations, plus a `### Changed` block for the widened `preset_not_found` description. See `contracts/charter/changes/README.md`.

**Shared registries.** This WP owns them. Later WPs add their rows as recorded out-of-map edits.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :contracts:check :packs:test
./gradlew --max-workers=1 :deployable:test   # contract scenarios, switch gate, link checks, ArchUnit
```
Run Gradle in the foreground, one run at a time; never `--stop`.

## Definition of Done

- Every row of the table is proven by a passing scenario.
- The switch gate reports both operations `ready`.
- Lint, closed-object, refusal-example, compatibility and fragment checks are green.
- `:packs:test` is green.

## Risks and reviewer guidance

- The `activated<Plural>` names come from the same source as the file names: no hand-typed list.
- Kind order is stable.
- `preset_not_found` gains no new enum value, and only its description widens.
- The ETag description does not claim to validate anything beyond the pack.

## Branch Strategy

- **Planning base and merge target:** `slice-a2-536-cutover-reads`.
- **Mission branch:** `kitty/mission-slice-a2-cutover-reads-01M78RJD`.
- **Worktrees:** allocated per computed lane (`lanes.json`).
- **Implement with:** `spec-kitty agent action implement WP01 --agent claude --mission slice-a2-cutover-reads-01M78RJD`.
- **Commits:** sign them if signing works. Add no model or `Co-Authored-By` trailer. Reference the issue, for example `(#98)`.

## Coordination

Slice C (#467) is in flight and changes:

- the `prompt` path and link relation;
- four schemas;
- `mission_type_not_in_force` in `CharterRefusalCode`;
- `ClosureQueries.closureFingerprint` ordering.

Do not use or change those names. Do not pin fingerprint values in tests.
