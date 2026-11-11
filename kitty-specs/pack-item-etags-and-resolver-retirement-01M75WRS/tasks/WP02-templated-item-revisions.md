---
work_package_id: WP02
title: Tactic and mission step contract items carry the revision
dependencies:
- WP01
- WP04
requirement_refs:
- FR-001
- FR-002
- FR-004
- NFR-002
planning_base_branch: claude/feat-487-488-pack-item-etags
merge_target_branch: claude/feat-487-488-pack-item-etags
branch_strategy: Planning artifacts for this mission were generated on claude/feat-487-488-pack-item-etags. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/feat-487-488-pack-item-etags unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-item-etags-and-resolver-retirement-01M75WRS
base_commit: 2265413a831fb75164da45261e36008f94dbe7e2
created_at: '2026-11-11T18:49:01.382976+00:00'
subtasks:
- T005
- T006
- T007
- T008
history:
- at: '2026-11-11T18:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/
create_intent: []
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/TacticReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactDtos.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/TacticReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ArtifactReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ArtifactControllerTest.java
role: implementer
tags: []
tracker_refs: []
---

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill (or `spec-kitty agent profile show java-jenny`) and run `spec-kitty charter context --action implement --json` before reading anything else. Read `.kittify/charter/charter.md` and AGENTS.md: the charter's model is canonical, tests use only the canonical shape, test first.

## Branch Strategy

Planning branch and final merge target: `claude/feat-487-488-pack-item-etags`. Execution worktrees (or the write checkout, for a single-branch Mission) come from `lanes.json` via `spec-kitty agent action implement WP02 --agent claude --mission pack-item-etags-and-resolver-retirement-01M75WRS`. Never pick a base branch by hand.

## Build

Java 25: `export JAVA_HOME=/usr/lib/jvm/temurin-25`. Targeted runs only (`./gradlew :<module>:test --tests '<Class>'`, `:contracts:build`, `spotlessApply`). The contract scenarios run with `./gradlew :deployable:test --tests '*CharterContractSuite'`. Leave `verifyAll` to CI.

## Objective

The templated item read (`getPackArtifact`) answers the pack revision as `ETag` for every kind it renders, tactic and
mission step contract included (FR-001, FR-002, FR-004). WP01's tactic and step-contract scenarios turn green.

## Subtasks

### T005 — Unit tests first (red)

- `ArtifactReadServiceTest`: `aTacticItemHasNoRevision` and the step-contract test that asserts "the kind has no
  write, so no ETag" (`item.revision()` empty) flip to asserting the fixture's `REVISION`. Extend
  `eachWritableKindItemCarriesItsRevision` to every item kind (rename it accordingly) instead of adding a new test.
- `ArtifactControllerTest.aTacticIsAnsweredInTheEnvelopeWithoutAnETag` flips to asserting the `ETag` header equals the
  revision token.
- FR-004: strengthen the existing stale-input test of a tactic item: inputs changing during the read refuse it
  `stale_input`, and the answer carries no ETag.
- NFR-002: in the same class, count `currentRevision` calls for one tactic item read with a counting `PackRevisions`
  fake: exactly one. Run them red.

### T006 — Tactic read carries the revision

`TacticReadService`: add `PackRevisions revisions` to the constructor and a
`tacticWithRevision(PackIdentifier, TacticIdentifier)` returning `ConsistentRead.Verified<Revised<Tactic>>`, copying
`StyleguideReadService.styleguideWithRevision` and its `revisionOf` refusal mapping exactly. Update every
`new TacticReadService(` site (tests: `ArtifactReadServiceTest`, `CompositeComponentResolverTest`,
`TacticReadServiceTest`, `ArtifactControllerTest`). WP04 ran first and removed the resolver's `TacticReadService` sites,
so `CompositeComponentResolverTest` needs no edit. Copy `directiveWithRevision`'s nesting: the revision is read inside
the tactic's own `ConsistentRead`, with its `packRefusal` mapping, never in `ArtifactReadService` outside it.

### T007 — Step contract reads the revision; `Item.revision()` is required

- `ArtifactReadService`: `OfTactic` and `OfMissionStepContract` gain `PackRevision at`; `Item.revision()` returns
  `PackRevision` (not `Optional`); update javadoc ("the writable kinds carry" → every item carries).
- `stepContract(...)`: read the revision inside the same `read.read(STALE_ONE, …)` lambda that loads the pack, through
  an injected `PackRevisions` (add it to the constructor; update the two test construction sites).
- Dispatch for tactic uses `tacticWithRevision`.

### T008 — Controller sets the ETag on every item

`ArtifactController` (~105): `answer.eTag(verified.value().revision().token())` unconditionally. Fix the `ArtifactDtos`
pattern matches if the records' shapes changed. `spotlessApply`, then run `:packs:test` for `ArtifactReadServiceTest`,
`ArtifactControllerTest`, `TacticReadServiceTest`, `CompositeComponentResolverTest`, and the contract suite: the
tactic/step-contract scenarios of WP01 are now green.

## Definition of Done

- Every templated item answers the revision; a refused item answers none (existing refusal tests stay green).
- No `Optional<PackRevision>` left on `Item`.
- Targeted tests green; the contract suite's remaining red is only WP01's recorded mission type, step and skeleton
  scenarios, unless WP03 already landed.

## Risks

Read the revision inside the same verified read as the body; never after it.
