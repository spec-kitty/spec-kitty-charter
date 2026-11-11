# Research: pack item ETags (#487) and resolver retirement (#488)

Research squad, 2026-11-11: three read-only researchers (contract/HTTP, service code, graph walks), findings checked
against the code by the orchestrator where they reverse a premise.

## #487 — every pack item read carries the pack revision as its ETag

**Today.** The templated item read sets `ETag` only when `Item.revision()` is present
(`service/packs/.../infra/api/ArtifactController.java:105`). Directive, styleguide, paradigm, toolguide and procedure
carry it; tactic (`Item.OfTactic`, `TacticReadService` has no `PackRevisions`) and mission step contract
(`stepContract`, no revision read) answer none. `getPackMissionType` (`missions-edge/.../MissionTypeController.java:46`,
`MissionBodies.ok`, documented "no ETag"), `getPackMissionStep` and `getPackMissionSkeleton` answer none. The agent
profile pack read already carries the revision (`AgentProfilesController.java:51-56`).

**Contract and scenarios that state "no ETag".**
- `contracts/charter/paths/packs_packId_artifacts_kind_artifactId.yaml:26-29, 49-53, 109`
- `contracts/charter/paths/packs_packId_artifacts_mission_type_missionTypeId.yaml:16` (and its 200 declares no ETag)
- `..._steps_stepId.yaml:15`, `..._skeletons_skeletonName.yaml:14`
- `contracts/charter/schemas/Artifact.yaml:10`, `ArtifactKindToken.yaml` ("Neither ... answers an ETag")
- `scenarios/artifacts.feature:74-78`; `scenarios/mission-types.feature:211, 280, 369, 417, 422, 635`
- Activation reads (`activations_*` mission type, step contract, step, skeleton) say "no ETag" too; out of scope.

**Unit tests that assert absence.** `ArtifactReadServiceTest` `aTacticItemHasNoRevision` and the step-contract item
test; `ArtifactControllerTest.aTacticIsAnsweredInTheEnvelopeWithoutAnETag`.

**If-None-Match / 304.** No pack-scoped read honours it, including the agent profile (explicitly refused) and the
procedure read. Only activation reads answer 304. Adding 304 for some pack reads would be inconsistent; out of scope.

**Revision semantics.** `CommitStorePackWriteStore.currentRevision` digests every file of the pack: pack-wide. A tactic's
ETag over-invalidates (any pack change moves it). A mission type, step or skeleton resolves content from dependency
packs that the pack revision does not cover: the ETag may under-invalidate, the agent profile's caveat. So it is a
concurrency token naming the pack revision, not a cache validator.

**Versioning.** An optional response header is additive (ADR-0022): change note under `### Added`, naming that the
published "no ETag" statements are withdrawn.

**Risks.** Read the revision inside the same `ConsistentRead` as the body. `MissionTypeHolding` and `missionTypeOf` are
shared with the activation mission-type read: the pack revision must not leak into activation answers, which carry the
activation's own tag.

## #488 — CompositeComponentResolver's walk path

**Finding (verified).** No production graph walk calls `CompositeComponentResolver.resolve`. `GraphWalk` is built only
over an `EdgeIndex` (`Reachability.java:195, 300`); `GovernanceWalk.over/overWritten(EdgeIndex)` and the activation walks
(`ClosureView`, `EdgeIndex.inPack`, `placesOf`) are kind-neutral and already walk procedures. `forPack` and
`Composed.inPack` are reached only from tests (`GraphWalkTest`, `CompositeComponentResolverTest`,
`RegularArtefactWiringTest`). The references read and `ComposedRelationshipTargetCheck` use `holder`/`holderIn`, a
closure "holds the URN" check that does not go through `resolve`.

So #488 is dead-code drift, not a behaviour bug, and PR #486's javadoc "procedure: served, not walked yet" is wrong.
Research Decision 6 (`kitty-specs/regular-artefact-read-registration-01M5SC0T/research.md:52-55`) described the
resolver as the walk's component source; `EdgeIndex` superseded it.

## Operator rulings (2026-11-11)

1. #488: retire the dead path (`forPack`, `Composed.inPack`, `resolve` and the per-kind read dependencies); keep
   `holder`/`holderIn`; correct the javadoc.
2. #487 scope: tactic and mission step contract item reads, mission type, mission step and mission skeleton reads.
3. The mission-type, step and skeleton ETag is documented as a concurrency token naming the pack revision, not a cache
   validator for closure content. No If-None-Match / 304 on pack reads. Activation reads unchanged.
4. Work lands on a new branch cut from the PR #486 branch, rebased onto `main` when #486 merges.
