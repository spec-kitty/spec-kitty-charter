---
work_package_id: WP04
title: Retire the resolver's unused walk path
dependencies: []
requirement_refs:
- FR-007
- FR-008
- SC-003
- SC-004
planning_base_branch: claude/feat-487-488-pack-item-etags
merge_target_branch: claude/feat-487-488-pack-item-etags
branch_strategy: Planning artifacts for this mission were generated on claude/feat-487-488-pack-item-etags. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/feat-487-488-pack-item-etags unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-item-etags-and-resolver-retirement-01M75WRS
base_commit: 37ca151d5d3b8f6614bbcb3551befa1f257fb49a
created_at: '2026-11-11T18:28:10.334384+00:00'
subtasks:
- T013
- T014
- T015
- T016
history:
- at: '2026-11-11T18:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/CompositeComponentResolver.java
create_intent: []
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/CompositeComponentResolver.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/CompositeComponentResolverTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/GraphWalkTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/RegularArtefactWiringTest.java
role: implementer
tags: []
tracker_refs: []
---

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill (or `spec-kitty agent profile show java-jenny`) and run `spec-kitty charter context --action implement --json` before reading anything else. Read `.kittify/charter/charter.md` and AGENTS.md: the charter's model is canonical, tests use only the canonical shape, test first.

## Branch Strategy

Planning branch and final merge target: `claude/feat-487-488-pack-item-etags`. Execution worktrees (or the write checkout, for a single-branch Mission) come from `lanes.json` via `spec-kitty agent action implement WP04 --agent claude --mission pack-item-etags-and-resolver-retirement-01M75WRS`. Never pick a base branch by hand.

## Build

Java 25: `export JAVA_HOME=/usr/lib/jvm/temurin-25`. Targeted runs only (`./gradlew :<module>:test --tests '<Class>'`, `:contracts:build`, `spotlessApply`). The contract scenarios run with `./gradlew :deployable:test --tests '*CharterContractSuite'`. Leave `verifyAll` to CI.

## Objective

Remove the per-kind resolution path of `CompositeComponentResolver` that no production walk reaches (#488, FR-007,
SC-004), keeping the closure holder check the references read and `ComposedRelationshipTargetCheck` use (FR-008).

## Context

Research (verified): `GraphWalk` is built only over `EdgeIndex` in production (`Reachability.java:195, 300`);
`forPack` and `Composed.inPack` are reached only from `GraphWalkTest`, `CompositeComponentResolverTest` and
`RegularArtefactWiringTest`. `over(...)`, `Composed.holder(...)` and `holderIn(...)` are live.

## Subtasks

### T013 — Prove the ratchet first

Run the references and relationship-registration scenarios (`CharterContractSuite` filtered to `artifacts.feature`
references and `relationships.feature`, or the whole suite) and `ComposedRelationshipTargetCheck`'s tests, green, before
the change. Record the command and count.

### T014 — Remove the dead path

In `CompositeComponentResolver`: delete `forPack`, `Composed.inPack`, `resolve`, `read`, and the five read-service fields
and constructor parameters (directives, styleguides, paradigms, toolguides, tactics). `Composed` implements
`ComposeResolver` (~103), which declares `inPack` abstract: drop `implements ComposeResolver` and keep
`holdingPack`/`holder` as plain methods (no production caller uses `Composed` as a `ComposeResolver`; confirm by grep).
Leave `ComponentResolver`, `ComposeResolver`, `GraphWalk` and `EdgeIndex` untouched: `EdgeIndex implements
ComposeResolver` and its `inPack` is live. Rewrite the class javadoc: it resolves which pack of a compose closure holds a
reference; graph walks use `EdgeIndex`. Remove the "D6 … procedure (served, not walked yet: #488)" comment.

### T015 — Tests

- `CompositeComponentResolverTest`: delete the `resolve` cases (they test removed code); keep and, if thin, strengthen
  the holder cases.
- `GraphWalkTest`: the case that drives a walk through `forPack` moves onto an `EdgeIndex` fixture if it tests walk
  behaviour not covered elsewhere, else is deleted; say which in the commit.
- `RegularArtefactWiringTest`: drop the resolver-walk wiring assertion; keep what it asserts about the live wiring.
- Constructor sites in `ArtifactReadServiceTest` and `ArtifactControllerTest` (WP02's files): one-line fixes are an
  accepted out-of-map edit.

### T016 — Verify

`spotlessApply`; `:packs:test` (the four test classes above plus `ArtifactReadServiceTest`, `ArtifactControllerTest`),
`:deployable:test --tests '*RegularArtefactWiringTest'`, and the ratchet of T013 green with the same counts.
`grep -n "forPack\|inPack\|resolve(" service/packs/src/main/java/ai/speckitty/charter/packs/application/CompositeComponentResolver.java`
is empty. WP04 runs before WP02, so the resolver arguments at the constructor sites of `ArtifactReadServiceTest` and
`ArtifactControllerTest` (WP02's files) go here: an accepted out-of-map edit, named in the commit.

## Definition of Done

- No production or test reference to the removed methods.
- References and relationship scenarios unchanged and green.
