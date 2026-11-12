---
work_package_id: WP02
title: Kind gates and the asset id pattern
dependencies:
- WP01
requirement_refs:
- FR-006
- FR-011
- C-002
planning_base_branch: slice-a2-536-cutover-reads
merge_target_branch: slice-a2-536-cutover-reads
branch_strategy: Planning artifacts for this mission were generated on slice-a2-536-cutover-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-a2-536-cutover-reads unless the human explicitly redirects the landing branch.
subtasks:
- T006
- T007
- T009
- T010
- T011
phase: ''
history:
- at: '2026-11-12T22:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/
create_intent:
- contracts/charter/schemas/Asset.yaml
- contracts/charter/schemas/AssetExecutable.yaml
- contracts/charter/schemas/Skill.yaml
- contracts/charter/schemas/SkillParameter.yaml
- contracts/charter/schemas/SkillInvocation.yaml
- contracts/charter/schemas/SkillExpansion.yaml
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/AssetIdentifierTest.java
- contracts/charter/changes/2026-11-12-asset-and-skill-kinds.md
- contracts/charter/examples/Artifact.asset.yaml
- contracts/charter/examples/Artifact.skill.yaml
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactRemovalService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AssetIdentifier.java
- contracts/charter/schemas/ArtifactKindToken.yaml
- contracts/charter/parameters/ArtifactKind.yaml
- contracts/charter/schemas/Artifact.yaml
- contracts/charter/schemas/Asset.yaml
- contracts/charter/schemas/AssetExecutable.yaml
- contracts/charter/schemas/Skill.yaml
- contracts/charter/schemas/SkillParameter.yaml
- contracts/charter/schemas/SkillInvocation.yaml
- contracts/charter/schemas/SkillExpansion.yaml
- contracts/packs/schemas/asset.schema.json
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ArtifactReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ForeignArtifactReadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/WireTokenParityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/AssetIdentifierTest.java
- service/packs/src/test/resources/planted/asset-id-not-kebab/**
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadServiceArtifactTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSessionArtifactTest.java
- contracts/charter/scenarios/artifacts.feature
- contracts/charter/scenarios/pack-removal.feature
- contracts/charter/changes/2026-11-12-asset-and-skill-kinds.md
- contracts/charter/examples/Artifact.asset.yaml
- contracts/charter/examples/Artifact.skill.yaml
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP02 - Kind gates and the asset id pattern

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/slice-a2-cutover-reads-01M78RJD/`:

- `spec.md`
- `plan.md`, its concern for this WP
- `research.md`
- `research/squad-post-plan.md`

## Objective

`asset` and `skill` become served, listed kinds (FR-011), without opening DELETE for them or the templated item read. The asset id gets the canonical pattern (FR-006). This WP builds no read operation: WP04 and WP05 add the literal paths.

## Context

- `service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactReadService.java`:
  - `SERVED` at :86, `ITEM_KINDS` at :101 (derived as SERVED minus AGENT_PROFILE plus MISSION_STEP_CONTRACT), `LISTED` at :110, `ABSENT` at :127;
  - `item()` at :456 refuses non-item kinds with a "read it at …" message;
  - `servedItem` at about :635 has a `default -> IllegalStateException`.
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactRemovalService.java:189-197`: `requireHeld` gates removal through `listedKind` and `absentCode`.
- Squad findings #2, #11, #16, #17 and #20 (research/squad-post-plan.md), and research.md R11 and R14.

## Subtasks

### T006: Kind sets

- Add `ASSET` and `SKILL` to `SERVED`, before `AGENT_PROFILE`; agent_profile stays last, as `ArtifactReadServiceTest:547-615` pins.
- Add one `LITERAL_ITEM_KINDS = List.of(AGENT_PROFILE, ASSET, SKILL)`.
- Derive `ITEM_KINDS` as SERVED minus the literal kinds, plus `MISSION_STEP_CONTRACT`, so neither new kind reaches `servedItem`.
- `ABSENT`: both map to `ARTIFACT_NOT_FOUND`.
- Update the javadoc of the three sets.

### T007: The removal gate

- Add `REMOVABLE` to `ArtifactRemovalService`: exactly the kinds removable today, which are the old `LISTED`. A DELETE of an asset or skill answers as it does today.
- Check what that answer is today (likely `400`/`404 kind_not_found`) and pin it with an Examples row in `pack-removal.feature`.

### T009: Contract kind lists and body schemas

- Add `asset` and `skill` to the `ArtifactKindToken.yaml` enum (in `LISTED` order) and to `parameters/ArtifactKind.yaml`.
- In `Artifact.yaml`:
  - add the members `asset` (`$ref Asset.yaml`) and `skill` (`$ref Skill.yaml`) and their one-of entries;
  - update the kind description to say "asset and skill are answered by their own literal reads".
- Write `Asset.yaml` (`id`, `mime`, `path`, `title`?, `executable`? → `AssetExecutable` `{runtime, minVersion?, entry, args}`).
- Write `Skill.yaml` (R8: `schemaVersion`, `id`, `title`, `description`, `form`, `triggers`, `parameters`, `invocation`?, `expandsTo`?, `tools`, `version`?, `maintainers`?, `body`?), with sub-schemas mirroring `contracts/packs/schemas/skill.schema.json` in camel case.
- All schemas are closed, with `x-source` pointing at the domain classes (`Asset.java`, `Executable.java`, `Skill.java`, `SkillParameter.java`, `SkillInvocation.java`, `SkillExpansion.java`).
- Add examples.

### T010: Asset id pattern

- In `contracts/packs/schemas/asset.schema.json`, set `id` to the exact skill pattern `^(?!.*\n)[a-z][a-z0-9]*(-[a-z0-9]+)*$` with `maxLength: 64`.
- Back `AssetIdentifier` with `IdentifierGrammar.isLetterFirstSlug` and the length, as `SkillIdentifier` does.
- Add a planted fixture `service/packs/src/test/resources/planted/asset-id-not-kebab/`, following the neighbours' layout. Prove it is refused as invalid content at load and registration.
- `AssetIdentifierTest` covers `a/payload`, `Run`, an id longer than 64 characters, and a newline.

### T011: Pinned tests and list scenarios

- Update `ArtifactReadServiceTest`, `WireTokenParityTest`, `ForeignArtifactReadTest:237`, `EffectiveReadServiceArtifactTest:57` and `ClosureViewSessionArtifactTest:248` to the new sets. Update tests to the new model; never delete an assertion to get green.
- Update the 400 detail text pinned at `artifacts.feature:290`.
- Add scenarios: `GET /packs/{p}/artifacts?kind=asset` and `?kind=skill` list the pack's assets and skills (`ArtifactSummary`; an asset's title falls back to its id).
- Re-check the governance or reference entries that name `asset:`/`skill:` targets: they now carry `artifact` links. Fix the expectations in their features (`pack-removal.feature:561`, `activation-kind-model.feature`) as recorded out-of-map edits if they are not owned here.
- Write the fragment `2026-11-12-asset-and-skill-kinds.md` (`### Added`: kinds listed, schemas; `### Changed`: the token enum).

**Shared registries (out-of-map rows, recorded).** Add rows for this WP's operations to the files WP01 owns:

- `contracts/charter/openapi.yaml`
- `contracts/charter/scenarios/switch-gate.yaml` (`ready`)
- `every-operation.feature`, `unexpected-failure.feature` and `read-refusals.feature`
- the relation table and the lint enum, only for relations this WP introduces

Record each edit in the commit message as "out-of-map: registry rows for <operationIds>". Keep each file's existing order: path order in openapi.yaml, and the table orders.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :contracts:check :packs:test :activations:test
./gradlew --max-workers=1 :deployable:test   # contract scenarios, switch gate, link checks, ArchUnit
```
Run Gradle in the foreground, one run at a time; never `--stop`.

## Definition of Done

- `item()` never reaches `servedItem` for asset or skill; a unit test proves `kind_not_found` with the literal path in the message.
- DELETE is unchanged and pinned.
- A bad asset id is refused.
- `?kind=asset` and `?kind=skill` list.
- All pinned tests are updated, not deleted.

## Risks and reviewer guidance

- Check the derivation, not only the set.
- Check that no fixture needed changing for the id pattern (R11 evidence). If one did, it is fixed to the canonical shape.

## Branch Strategy

- **Planning base and merge target:** `slice-a2-536-cutover-reads`.
- **Mission branch:** `kitty/mission-slice-a2-cutover-reads-01M78RJD`.
- **Worktrees:** allocated per computed lane (`lanes.json`).
- **Implement with:** `spec-kitty agent action implement WP02 --agent claude --mission slice-a2-cutover-reads-01M78RJD`.
- **Commits:** sign them if signing works. Add no model or `Co-Authored-By` trailer. Reference the issue, for example `(#105)`.

## Coordination

Slice C (#467) is in flight and changes:

- the `prompt` path and link relation;
- four schemas;
- `mission_type_not_in_force` in `CharterRefusalCode`;
- `ClosureQueries.closureFingerprint` ordering.

Do not use or change those names. Do not pin fingerprint values in tests.
