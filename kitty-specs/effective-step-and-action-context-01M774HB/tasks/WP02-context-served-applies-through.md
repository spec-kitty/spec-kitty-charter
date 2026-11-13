---
work_package_id: WP02
title: The context served with appliesThrough
dependencies:
- WP01
requirement_refs:
- FR-008
- FR-009
- FR-010
- NFR-004
- SC-004
- C-001
- C-004
- C-005
planning_base_branch: slice-c/effective-step-context
merge_target_branch: slice-c/effective-step-context
branch_strategy: Planning artifacts for this mission were generated on slice-c/effective-step-context. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-c/effective-step-context unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-effective-step-and-action-context-01M774HB
base_commit: 725583f9a092db7d92f09740d5ad331fa4625c44
created_at: '2026-11-12T08:48:58.415402+00:00'
subtasks:
- T006
- T007
- T008
- T009
- T010
history:
- timestamp: '2026-11-12T06:30:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveContext.java
create_intent:
- contracts/charter/schemas/EffectiveContextArtifact.yaml
- contracts/charter/scenarios/effective-context-scoping.feature
- contracts/charter/changes/2026-11-12-effective-context-ownership.md
execution_mode: code_change
owned_files:
- contracts/charter/schemas/EffectiveContextArtifact.yaml
- contracts/charter/schemas/EffectiveActionContext.yaml
- contracts/charter/scenarios/effective-context-scoping.feature
- contracts/charter/scenarios/effective-graph-and-context.feature
- contracts/charter/changes/2026-11-12-effective-context-ownership.md
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/EffectiveGraphSteps.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ActionContext.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveContext.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveTag.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSession.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/EffectiveContextTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/EffectiveTagTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapperTest.java
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

`GET /activations/{id}/mission-types/{type}/actions/{action}/context` answers the ownership rule's listing (WP01)
on the wire. Each item gains a required `appliesThrough` (`action`, `mission_type`, `project`). Scenarios first; the
contract change is `### Breaking`; every effective read's ETag moves to recipe `v3` (rule E17), because the context
now changes meaning under an unchanged revision and fingerprint.

## Context

- Spec: FR-008, FR-009, FR-010, NFR-004, SC-004, C-001, C-004, C-005; research R1, R3, R4 (revised), R10 (revised).
- Squad: R3 (existing scenarios change meaning), R5 (Breaking), R6 (parity), R7 (`appliesThrough` must be pinned),
  R8 (the 16-directive table lives once, in the scenario), D1 (E17 `v3`).
- Read first: `contracts/charter/scenarios/README.md` (step vocabulary, fixture style, the post-check that each
  observed refusal is declared), `effective-graph-and-context.feature` (US5 AS4 ~l.422, the #484 case ~l.556),
  `EffectiveGraphSteps.java`, `schemas/EffectiveActionContext.yaml`, `schemas/EffectiveArtifact.yaml`,
  `activations/domain/{ActionContext, EffectiveContext, EffectiveTag}.java`, `infra/packs/ClosureViewSession.contextOf`,
  `infra/api/EffectiveReadMapper.toContext`, `contracts/charter/changes/README.md` and the precedent
  `2026-11-09-c-node-reference-step-targets.md` (an item-type swap, Breaking).

## Subtasks

### T006 — Scenarios first

1. New `effective-context-scoping.feature` (`@contract`), fixture packs written inline in the scenarios' usual way
   (canonical shape):
   - **Scenario Outline "the 16 directives of software-dev/implement"**: an example table with one row per directive
     of `spec.md`'s evidence: `| directive | scoped by | expected |` — the 7 implement-scoped (`listed / action`),
     DIRECTIVE_003 scoped by `action:software-dev/plan`, `review`, `specify` (`absent`), the 8 unscoped
     (`listed / project`). This table is the one home of the evidence (squad R8); WP08's docs link to it.
   - type-scoped directive: `listed / mission_type`; another type's directive: `absent`; another type's directive
     while that type is **not in force**: still `absent` (R2 leak control); a **required** directive scoped only to
     another action: `absent`; an unscoped in-force `procedure`: `absent`, the same procedure `requires`-reached from
     a project-wide directive: `listed / project`.
2. Rewrite, before the code, the scenarios whose meaning changes (squad R3): US5 AS4 ("O is selected and in force but
   no step reaches it: the context does not list it") — O becomes a `project` entry if it is a D/T/P; keep a non-D/T/P
   negative twin. The #484 case: `DX` and `t3` now listed; keep `px`, `s3`, `g3` as the negative twins. State the
   change of meaning in the scenario's text.
3. Audit every other `/context` scenario and the effective-read tests that run on the example packs: unscoped
   in-force D/T/P there (`DIRECTIVE_038`, `PROJECT_001`, paradigms, tactics) now appear in every context. Update each
   expectation and list them in the commit message.

### T007 — The step vocabulary prints `appliesThrough`

- Extend the step `Then the context holds exactly the artifacts:` (and its binding in `EffectiveGraphSteps`) so each
  row is `<kind>:<id> <state>/<reason> <appliesThrough>`; update the scenarios README's vocabulary. Without this a
  constant `appliesThrough: action` would pass (squad R7).

### T008 — The contract

- New `schemas/EffectiveContextArtifact.yaml`: copy `EffectiveArtifact`'s members (as `EffectiveArtifactRead` does —
  follow its description: "the members of this schema are copied by ..."; add this schema to that sentence in
  `EffectiveArtifact.yaml`, an out-of-map one-line edit), plus required `appliesThrough`
  (`enum: [action, mission_type, project]`), every member with `x-derived` (`resource-property-cited`).
- `EffectiveActionContext.yaml`: `artifacts.items` → `EffectiveContextArtifact`; rewrite the description to state the
  ownership rule (the three conditions, kinds directive/tactic/paradigm, closure by ADR-0018 steps 2–5, scoped only
  elsewhere ⇒ absent, owners are authored scope edges held or not) and that a suggestion carries the `appliesThrough`
  of the pass that lists it.
- `schemas/_index.yaml`, the context examples (every item gets `appliesThrough`).
- Fragment `changes/2026-11-12-effective-context-ownership.md`: `### Breaking` — the item swap and new required
  member (closed schema, ADR-0022 D4), the wider listing for `getEffectiveActionContext`, the ETag recipe `v3` for every
  effective read; "core: nothing while it is not a consumer (epic #186)".
- Run `:contracts:check` (or the contract lint task the build defines) and the lint tests.

### T009 — Activations carries `appliesThrough`

- `ActionContext` (activations domain) entries and candidates carry `AppliesThrough` (a domain enum mirroring
  `Pass`); `ClosureViewSession.contextOf` maps `Pass` → `AppliesThrough` (this file is the only place naming packs).
- `EffectiveContext`: a new item record (or a field beside `EffectiveArtifact`) carrying `appliesThrough`; a
  suggestion takes its candidate's. `EffectiveContextTest`: each source pass maps to its value; a suggestion of a pass-3
  entry is `project`.
- `EffectiveReadMapper.toContext`: map to the generated `EffectiveContextArtifact`; `EffectiveReadMapperTest`.
- `WireTokenParityTest` (in packs `infra/api`): add the pair `EffectiveContextArtifact.appliesThrough` ↔
  `AppliesThrough` if the test's pattern pins domain/contract enum pairs (out-of-map edit, recorded).

### T010 — E17 `v3`

- `EffectiveTag`: the domain separator `"charter-effective-read\0v2\n"` → `v3`; `EffectiveTagTest` pins the new
  prefix and that a v2 tag no longer matches. Every effective read's tag changes once; record it in the fragment.
- Re-run the effective-read scenarios that compare tags (`effective-reads.feature`, `effective-artifact.feature`) and
  fix any literal tag.

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
- Implementation command: `spec-kitty agent action implement WP02 --agent claude --mission effective-step-and-action-context-01M774HB`

## Definition of Done

- The scoping scenarios, the rewritten US5 AS4 and #484 cases and the audited scenarios pass; the new scenarios failed
  first.
- `./gradlew :service:activations:test :service:deployable:test --tests '*Contract*'` (or the cucumber task the build
  defines) and the contract lint pass.
- The 16-directive scenario lists 15 and leaves DIRECTIVE_003 out.

## Risks and reviewer guidance

- Reviewers check: `appliesThrough` is asserted per row; every changed expectation is justified by the rule; the
  fragment is Breaking; the offering read (`/offering/.../context`) is untouched.
