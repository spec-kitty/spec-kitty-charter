---
work_package_id: WP03
title: The :artifacts-edge module
dependencies: []
requirement_refs:
- NFR-001
- NFR-004
planning_base_branch: slice-a2-536-cutover-reads
merge_target_branch: slice-a2-536-cutover-reads
branch_strategy: Planning artifacts for this mission were generated on slice-a2-536-cutover-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-a2-536-cutover-reads unless the human explicitly redirects the landing branch.
subtasks:
- T012
- T013
- T014
- T015
phase: ''
history:
- at: '2026-11-12T22:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/artifacts-edge/
create_intent:
- service/artifacts-edge/build.gradle.kts
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/package-info.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/package-info.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/package-info.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/activations/package-info.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/ArtifactsEdgeWiringTest.java
execution_mode: code_change
owned_files:
- service/artifacts-edge/build.gradle.kts
- settings.gradle.kts
- service/deployable/build.gradle.kts
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/package-info.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/package-info.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/package-info.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/activations/package-info.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/config/**
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/config/**
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/config/**
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/activations/config/**
- service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/artifactsfixture/**
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/ArtifactsEdgeWiringTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP03 - The :artifacts-edge module

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/slice-a2-cutover-reads-01M78RJD/`:

- `spec.md`
- `plan.md`, its concern for this WP
- `research.md`
- `research/squad-post-plan.md`

## Objective

Add a new edge module, `:artifacts-edge`, for the asset, skill and references reads (research.md R13, squad BLOCKER #1). It is modelled on `:missions-edge`: it can name the packs slice only from `infra.packs` and the activations slice only from `infra.activations`, and neither slice names it back.

## Context

- `service/missions-edge/build.gradle.kts`: the template for the dependencies, the plugins and the mutation floor.
- `settings.gradle.kts:24-30`: the edge-module entries.
- `service/deployable/build.gradle.kts:12-16`: the `implementation(project(":…-edge"))` entries.
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java`: the missions-edge rules from about :1203, including the `MISSIONS_EDGE_RINGS` list, the "edge is not named back" rule, the "each ring holds a class" test and the per-slice ring checks. Read how `SLICES` and the known-bad fixtures (`assertBites`) work.
- `docs/architecture/hexagonal-service.md`, ADR-0006 (amended 2026-11-09) and ADR-0037.

## Subtasks

### T012: Module

- Write `service/artifacts-edge/build.gradle.kts`, mirroring missions-edge. It has no domain module of its own: its application ring speaks in kernel types and its own small value records. Dependencies: `:packs`, `:activations`, `:web-transport` and `:charter-api` (api); spring-webmvc; jackson.
- Register the module in `settings.gradle.kts` with the same comment style, and in `:deployable`.

### T013: Rings

- Add the packages `ai.speckitty.charter.artifacts.application`, `.infra.api`, `.infra.packs` and `.infra.activations`, each with a `package-info.java` that states its role and its `@NullMarked` (copy the missions-edge pattern), and a `config` subpackage where missions-edge has one.
- Add the minimum placeholder needed so every ring holds a class: a Spring `@Configuration` in each `config`. WP04 to WP07 add the real classes.

### T014: ArchUnit

In `HexagonalArchitectureTest`, add the artifacts-edge rules, mirroring the missions-edge block:

- infra.packs is the only ring that names `..packs..`, and only its application and domain rings;
- infra.activations is the only ring that names `..activations..`;
- packs and activations never name `..artifacts..`;
- the application ring obeys `APPLICATION_IS_EDGE`;
- an `ARTIFACTS_EDGE_RINGS` "each ring holds a class" test.

Add known-bad fixtures under `service/deployable/src/test/java/ai/speckitty/charter/deployable/artifactsfixture/` so each new rule is proven to bite (`assertBites`).

### T015: Mutation floor and smoke test

- Add a `mutationFloors` group `artifacts-edge`, at floor 85 with the config excludes, as missions-edge has.
- Write `ArtifactsEdgeWiringTest`: the module's configurations load in a slice context.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :artifacts-edge:check
./gradlew --max-workers=1 :deployable:test   # contract scenarios, switch gate, link checks, ArchUnit
```
Run Gradle in the foreground, one run at a time; never `--stop`.

## Definition of Done

- The module builds and is on the deployable's classpath.
- The new ArchUnit rules pass and each has a fixture that bites.
- No existing rule is weakened.

## Risks and reviewer guidance

- An empty ring is silently unchecked; the "holds a class" test guards against that.
- Keep this module off `charter.domain-conventions`, as missions-edge does.

## Branch Strategy

- **Planning base and merge target:** `slice-a2-536-cutover-reads`.
- **Mission branch:** `kitty/mission-slice-a2-cutover-reads-01M78RJD`.
- **Worktrees:** allocated per computed lane (`lanes.json`).
- **Implement with:** `spec-kitty agent action implement WP03 --agent claude --mission slice-a2-cutover-reads-01M78RJD`.
- **Commits:** sign them if signing works. Add no model or `Co-Authored-By` trailer. Reference the issue, for example `(#536)`.

## Coordination

Slice C (#467) is in flight and changes:

- the `prompt` path and link relation;
- four schemas;
- `mission_type_not_in_force` in `CharterRefusalCode`;
- `ClosureQueries.closureFingerprint` ordering.

Do not use or change those names. Do not pin fingerprint values in tests.
