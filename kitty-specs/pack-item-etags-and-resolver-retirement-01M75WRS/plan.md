# Implementation Plan: Pack item ETags and resolver retirement

**Branch**: `kitty/mission-pack-item-etags-and-resolver-retirement-01M75WRS` → lands on
`claude/feat-487-488-pack-item-etags` | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/pack-item-etags-and-resolver-retirement-01M75WRS/spec.md`

## Summary

Every pack-scoped read of one thing answers the pack revision as its `ETag` (#487): the tactic and mission step contract
item reads, and the mission type, mission step and mission skeleton reads, join the kinds that already do. The revision
is read inside the same verified read as the body. The mission-type family documents it as a concurrency token naming
the pack, not a cache validator for closure content. No pack read starts honouring `If-None-Match`; activation reads are
untouched. Separately, `CompositeComponentResolver`'s per-kind walk path, which no production walk reaches, is retired
(#488); its closure holder check stays. Research and operator rulings: [research.md](research.md).

## Technical Context

**Language/Version**: Java 25 (Temurin), Gradle 9.8 with strict SHA-256 dependency verification
**Primary Dependencies**: Spring Boot (existing), OpenAPI generator for `ai.speckitty.charter.api.model`; no new dependency
**Storage**: Pack files on disk through `PackWriteStore` / `PackRevisions` (existing); no change
**Testing**: JUnit 5 + AssertJ unit tests per module; Cucumber contract scenarios (`:deployable` `CharterContractSuite`); `:contracts:build` lint and compatibility
**Target Platform**: Linux server (the charter service)
**Project Type**: single (multi-module Gradle service plus contract)
**Performance Goals**: at most one pack-revision digest per affected request (NFR-002)
**Constraints**: additive wire change only (NFR-001); no conditional reads on pack routes (C-001); activation answers unchanged (C-002)
**Scale/Scope**: 5 read routes, about 8 contract texts, about 6 scenario assertions, about 4 unit tests flipped; one resolver class trimmed

## Charter Check

- **Charter's model is canonical / canonical shape only**: no shape changes; tests and examples keep the canonical shape (C-003). Pass.
- **Contract versioning (ADR-0022)**: an added optional response header; change note under `### Added`. Pass.
- **BDD / test first**: scenario assertions flipped to "ETag appears once" before the code; unit tests flipped red first. Pass (planned).
- **Adversarial squad**: research squad ran at the plan point; a review squad runs before merge.
- **Gates before merge**: `:contracts:build`, targeted module tests, `CharterContractSuite`, docs checks locally; `verifyAll` in CI.

## Project Structure

### Documentation (this mission)

```
kitty-specs/pack-item-etags-and-resolver-retirement-01M75WRS/
├── spec.md
├── plan.md
├── research.md
├── contracts/etag-headers.md
└── tasks.md              # /spec-kitty.tasks
```

### Source Code (repository root)

```
service/packs/src/main/java/ai/speckitty/charter/packs/
├── application/ArtifactReadService.java        # Item.revision() required; step contract reads the revision
├── application/TacticReadService.java          # + PackRevisions, tacticWithRevision
├── application/CompositeComponentResolver.java # walk path retired; over/holder/holderIn kept
└── infra/api/ArtifactController.java           # ETag on every item
service/missions-edge/src/main/java/.../        # pack mission type / step / skeleton reads carry the revision
contracts/charter/paths/packs_packId_artifacts_*.yaml, schemas/Artifact.yaml, ArtifactKindToken.yaml, changes/
contracts/charter/scenarios/artifacts.feature, mission-types.feature, tactics.feature
```

**Structure Decision**: existing modules only; no new module or package.

## Implementation Concern Map

### IC-01 — Templated item reads carry the revision

- **Purpose**: tactic and mission step contract items answer the pack revision like every other item kind.
- **Relevant requirements**: FR-001, FR-002, FR-004, SC-001
- **Affected surfaces**: `TacticReadService`, `ArtifactReadService` (`Item`, `stepContract`, constructor), `ArtifactController`, `ArtifactDtos` pattern matches; tests `ArtifactReadServiceTest`, `ArtifactControllerTest`, `TacticReadServiceTest`, constructor sites.
- **Sequencing/depends-on**: IC-03 scenarios written first (red).
- **Risks**: read the revision inside the same `ConsistentRead` as the body; a revision refusal maps like `revisionOf` does.

### IC-02 — Mission type, step and skeleton pack reads carry the revision

- **Purpose**: the three missions-edge pack reads answer the pack revision.
- **Relevant requirements**: FR-003, FR-004, C-002, SC-001
- **Affected surfaces**: packs `MissionTypeHoldingRead` (a pack-only `revised(...)` read that returns the holding and a required `PackRevision`, read inside the same `read.read` lambda with `DirectiveReadService.directiveWithRevision`'s `packRefusal` mapping; `holding()` stays revision-free), its tests (`MissionTypeHoldingReadTest`, `MissionTypeHoldingCorpusTest`); missions-edge `PackMissionTypes` (a pack-only `revised` port method), `PacksMissionTypes`, `MissionTypeReadService` (pack reads return a pack-only wrapper carrying the revision; `missionTypeOf/stepOf/skeletonOf` stay shared and revision-free), `MissionBodies`, `MissionTypeController` and its javadoc; their tests. `MissionTypeHolding` and the effective path's types are unchanged.
- **Sequencing/depends-on**: IC-03 scenarios first.
- **Risks**: `EffectiveMissionTypeReadService.storedBeside` calls the same holding read; it must keep calling the revision-free `holding()`, so an activation read never computes a pack revision and cannot be refused for one (C-002). A scenario asserts the activation mission-type read's tag is not the pack revision.

### IC-03 — Contract and scenarios

- **Purpose**: declare and describe the ETag on the five reads and withdraw every "no ETag" statement.
- **Relevant requirements**: FR-005, FR-006, NFR-001, SC-002
- **Affected surfaces**: see [contracts/etag-headers.md](contracts/etag-headers.md) and research.md's list.
- **Sequencing/depends-on**: none; its scenario flips are the red half of IC-01 and IC-02.
- **Risks**: the mission-type family's caveat text must match the agent profile's wording style.

### IC-04 — Resolver walk path retired

- **Purpose**: remove the per-kind resolution path no production walk reaches.
- **Relevant requirements**: FR-007, FR-008, SC-003, SC-004
- **Affected surfaces**: `CompositeComponentResolver` (`forPack`, `Composed.inPack`, `resolve`, `read`, five read-service fields; `Composed` stops implementing `ComposeResolver`, which declares `inPack`, and keeps `holdingPack`/`holder` as plain methods; `ComponentResolver` and `EdgeIndex` untouched); `CompositeComponentResolverTest`, `GraphWalkTest`, `RegularArtefactWiringTest`, and every `new CompositeComponentResolver(` site.
- **Sequencing/depends-on**: before IC-01's code: IC-01 would otherwise add a `PackRevisions` argument to `new TacticReadService(` sites in `CompositeComponentResolverTest` that IC-04 deletes.
- **Risks**: `holder`/`holderIn` must answer as before: the references and relationship-registration scenarios are the ratchet.
