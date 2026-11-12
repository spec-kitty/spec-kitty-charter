---
work_package_id: WP11
title: An override comes from an activatable artifact (#447)
dependencies:
- WP04
requirement_refs:
- FR-014
- SC-006
- C-001
planning_base_branch: slice-c/effective-step-context
merge_target_branch: slice-c/effective-step-context
branch_strategy: Planning artifacts for this mission were generated on slice-c/effective-step-context. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-c/effective-step-context unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-effective-step-and-action-context-01M774HB
base_commit: d2f74476ceee3a9c266bd2d065b675aa550fdc81
created_at: '2026-11-12T10:49:37.267871+00:00'
subtasks:
- T047
- T048
- T049
history:
- timestamp: '2026-11-12T06:30:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/OverrideSourceRule.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/OverrideSourceRule.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/OverrideSourceRuleTest.java
- contracts/charter/scenarios/override-source-kind.feature
- contracts/charter/changes/2026-11-12-override-source-activatable.md
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipRegistrationService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RelationshipRegistrationServiceTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/OverrideSourceRule.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/OverrideSourceRuleTest.java
- contracts/charter/scenarios/override-source-kind.feature
- contracts/charter/changes/2026-11-12-override-source-activatable.md
role: implementer
tags: []
tracker_refs: []
---

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or run `spec-kitty agent profile show java-jenny`.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

## Objective

Operator ruling of 2026-11-12 on #447, option 1 (FR-014): an `overrides` edge whose **source** is not of an activatable
kind (`template`, `asset`, `guard`, `lifecycle`; `Kₐ` of the rules page) is refused:

- at registration: a violation `{pointer: <pack-relative graph document>, code: overrides_source_not_activatable}`
  inside `422 validation_failed`, one per edge;
- on `putPackRelationship`: `422 validation_failed` with the same violation code, nothing written;
- on a composing read that meets one in a stored pack: `500 pack_unreadable`, naming the file.

So the read (D16 drops the overrider) and the IC-13 write (no satisfier) can never disagree. `### Breaking`.

## Context

- Spec FR-014, SC-006; research `research/in-force-rulings-439-447.md`; rules page C25, C27, D16; ADR-0066 annotation
  R-OVR/R-REL (the precedent: a step enhancement's `overrides` edge is refused as `prompt_fragment_invalid` at the file at
  load, and the relationship write refuses it, `RelationshipRegistrationService.refuseStepEnhancementEdge` ~l.134).
- Find the load-violation seam R-OVR uses (`StepEnhancementRules` and where its faults become per-file violations of
  `422 validation_failed` at registration and `500 pack_unreadable` on reads; OD-3 (a) annotation of ADR-0066). Put the new
  rule beside it. **If the only seam is in `DomainRepositoryService`** (slice B's file, C-006), implement the pure rule
  and the relationship write now and leave the load hunk until the rebase onto #501; tell the run lead.
- Contract: the violation-code vocabulary (`RefusalViolation.code` or the schema that lists violation codes; search
  `prompt_fragment_invalid` and `removed_member` in `contracts/charter/schemas`) gains
  `overrides_source_not_activatable`.

## Subtasks

### T047 — Red

- `override-source-kind.feature` (`@contract`): registering a pack whose graph document has
  `template:t -overrides-> directive:D` → `422 validation_failed`, violation at the graph file with the code; twin:
  `directive:E -overrides-> directive:D` → accepted. `putPackRelationship` of the template edge → 422, nothing written;
  twin accepted. A stored pack holding the edge (planted below the service, as the existing load-refusal scenarios
  plant a fault) → composing reads `500 pack_unreadable`.
- `OverrideSourceRuleTest`: each non-activatable kind refused, each activatable kind passes; a qualified source
  (`<pack>::template:t`) judged like a bare one.

### T048 — The rule and the write

- `packs.domain.OverrideSourceRule` (pure): over a pack's edges, the `overrides` edges whose source kind is not
  activatable (`ArtifactKind.activatable()` or the kernel's equivalent).
- `RelationshipRegistrationService`: refuse such an edge beside `refuseStepEnhancementEdge`, with the 422 and the code.

### T049 — Load, contract, fragment

- Wire the rule into the load-violation seam (see Context); a composing read answers `500 pack_unreadable`.
- Contract: the violation code; the `validation_failed` description; examples. Fragment
  `changes/2026-11-12-override-source-activatable.md`: `### Breaking` (registrations, relationship writes and reads that
  answered before are refused; open-packs authors no `overrides` edge).

## Project constraints (every subtask)

- **Test first (C-004)**: the scenario and a failing unit test come before the code; commit the red test first when
  the behaviour is new. Every refusal has a failing fixture and a passing twin that differ only in the faulty part.
- **Canonical shape only (C-005)**: fixtures, examples and scenarios use the canonical shape; an invalid shape appears
  only to exercise a refusal. Never exclude a field from a parity or round-trip check.
- **Stay out of slice B (C-006)**: do not edit `PackCommitStore`, `PackLocator`, `PackDirectories`,
  `CommitStorePackWriteStore`, `FileSystemPackSources`, `Loader`/`LoadedPack`, `CharterWiring`, `file-kinds.yaml`, or
  the example packs' `pack.md`/`README.md`/`org-charter.yaml`. `DomainRepositoryService` only in WP03's one hunk.
- **Hexagonal**: `HexagonalArchitectureTest` must stay green: only `activations.infra.packs` names packs, and only
  `packs.application`/`packs.domain`; generated `api` types only in `*.infra.api` and `web`.
- **Quality gates**: Checkstyle, palantir-java-format (Spotless), Error Prone, NullAway and doclint are on; no
  suppression, no disabled test, no new dependency.
- **Gradle**: `JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon --max-workers=1 <module>:test` (or
  `:contracts:...`, `:service:deployable:test --tests ...`), foreground, one run at a time, never `--stop`, never
  `verifyAll` locally.
- **Commits**: signed, conventional (`feat(...)`, `test(...)`, `docs(...)`), no AI model name and no
  `Co-Authored-By` trailer; an edit to a file another WP owns is recorded in the message as
  `out-of-map: <file> — <why>`.
- **Contract changes** each carry a dated fragment under `contracts/charter/changes/2026-11-12-*.md` with the class
  `contracts/README.md` of the mission names (Added, Changed wording only, Breaking).

## Branch Strategy

- Planning branch: `kitty/mission-effective-step-and-action-context-01M774HB`; final merge target: `slice-c/effective-step-context` (then a pull request into `main`).
- Execution worktrees are allocated per computed lane from `lanes.json`; never pick a base by hand.
- Implementation command: `spec-kitty agent action implement WP11 --agent claude --mission effective-step-and-action-context-01M774HB`

## Definition of Done

- The scenarios and tests failed first and pass; `:packs:test`, the contract lint and the scenarios are green.

## Risks and reviewer guidance

- The rule judges the **source** kind only; an `overrides` edge between two activatable kinds is unchanged (C27).
