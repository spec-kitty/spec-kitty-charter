---
work_package_id: WP01
title: The ownership rule in the packs walk
dependencies: []
requirement_refs:
- FR-008
- NFR-003
- NFR-004
- C-003
- C-007
planning_base_branch: slice-c/effective-step-context
merge_target_branch: slice-c/effective-step-context
branch_strategy: Planning artifacts were generated on the mission branch kitty/mission-effective-step-and-action-context-01M774HB; completed changes merge into slice-c/effective-step-context, then by pull request into main. Execution worktrees are allocated per computed lane from lanes.json.
subtasks:
- T001
- T002
- T003
- T004
- T005
agent: claude
history:
- timestamp: '2026-11-12T06:30:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/ActionContextWalk.java
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ActionContextWalkOwnershipTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ActionContextWalk.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EdgeIndex.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ActionContextWalkTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/EdgeIndexTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ActionContextWalkOwnershipTest.java
role: implementer
---

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or run `spec-kitty agent profile show java-jenny`.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

## Objective

Give `ActionContextWalk` an **effective form** that lists what the operator's ownership rule (research R1, Decision
Moment `01M774HQ8N5EVYDRTP16MBJCN3`) says applies to action `mt/a`. An in-force directive, tactic or paradigm applies
when any of these holds:

1. the action's walk reaches it (ADR-0018 D2, as built: pass 1);
2. `mission_type:mt` scopes it (pass 2);
3. no node of kind `action` or `mission_type` scopes it (pass 3, "project-wide").

What a pass-2 or pass-3 root reaches by ADR-0018 steps 2 to 5 comes with it. One scoped only by other actions or
other mission types stays out. **The offering read (`getActionContext`, ADR-0018 D1) keeps the five-step walk
unchanged** — only the effective read (`ClosureView.actionContext`, used by `getEffectiveActionContext`) takes the
passes.

## Context

- Spec: FR-008, NFR-003, NFR-004; research R1, R2 (revised: owners are every authored `scope` edge whose source kind
  is `action` or `mission_type`, held or not), R3 (revised: entries carry pass and root; explicit hops for pass roots;
  first reach wins, vocabulary included; a suggestion candidate carries the pass that lists it).
- Squad findings A6, A7 (`research/plan-squad-2026-11-12.md`).
- Code to read first: `ActionContextWalk.java` (the `Engine`, `walk`, `Entry`, `SuggestionCandidate`, `Context`),
  `EdgeIndex.java` (`links()`, `heldSteps()` memoization, `Link`), `ClosureView.actionContext` (~l.585) and
  `ClosureView.effective(states)`, `StepHolding`.
- Evidence the rule must reproduce: `spec.md`, section "Evidence (2026-11-12)" — 15 of the 16 directives listed for
  `software-dev/implement`, DIRECTIVE_003 left out because only other actions scope it.

## Subtasks

### T001 — Red: the effective-walk tests

**Purpose**: Pin the rule before the code, on hand-built merged indexes (the style of `ActionContextWalkTest`).

Create `ActionContextWalkOwnershipTest` with, at least:

- `anUnscopedDirectiveInForceIsListedProjectWideOnEveryActionOfAMissionTypeInForce` — `directive:U` with no `scope`
  edge, in force; two actions of `M`; both contexts list `U` with pass `PROJECT`.
- `aDirectiveScopedOnlyByAnotherActionIsAbsentWhetherOrNotItIsRequired` — `action:M/other -scope-> directive:X`; the
  context of `M/a` lacks `X` (the predicate says in force; "required" plays no role).
- `aDirectiveScopedByTheMissionTypeIsListedThroughTheMissionType` and its twin
  `aDirectiveScopedByAnotherMissionTypeIsAbsent`.
- `aScopeEdgeFromAnUnheldSourceStillOwnsItsTarget` — `action:qa/x -scope-> directive:Q` where the closure holds no
  `mission_type:qa`; `Q` is absent from `M/a` (squad A6). Twin: drop the edge, `Q` is listed project-wide.
- `onlyDirectivesTacticsAndParadigmsAreRoots` — an unscoped in-force `procedure`, `styleguide`, `toolguide`,
  `agent_profile` is absent; the same artifact reached by `requires` from a project-wide directive is listed (pass 3,
  step requires).
- `theFirstPassThatReachesAnArtifactListsIt` — a project-wide directive that the action's scoped directive
  `requires` is listed once, pass `ACTION`, step `REQUIRES`.
- `anEligibleRootFirstReachedByVocabularyIsListedThroughTheActionAndStillSeedsItsPass` — record the decision of R3.
- `aSuggestionCandidateCarriesThePassThatListsIt` — a pass-3 root suggests an out-of-force artifact; the candidate's
  pass is `PROJECT`.
- `aMissionTypeNotInForceAnswersNoEntryInEveryPass`.
- `theOfferingWalkIsUnchanged` — the five-step form answers exactly what it answered before for an index holding an
  unscoped directive (it does not list it).
- `theEvidenceListsFifteenOfSixteen` — an index shaped like the evidence table (7 implement-scoped, DIRECTIVE_003
  scoped by plan/review/specify, 8 unscoped): exactly 15 listed, 003 absent.

Run them; they fail on compilation or assertion (commit the red test: `test(packs): pin the ownership rule ...`).

### T002 — Scope owners in `EdgeIndex`

- Add a package-private, memoized `scopeOwners()` (or `ownersOf(Urn)`): for each target URN, the set of sources of
  authored `scope` links whose source kind is `NodeKind.ACTION` or `NodeKind.MISSION_TYPE`, **held or not**, read from
  `links()` (graph-document edges only, C-003). Memoize like `heldSteps()` (one pass over the links).
- Helpers: `isProjectWide(Urn)` (no owner), `isScopedBy(Urn, Urn missionType)`.
- `EdgeIndexTest`: owners include an unheld source; a `requires` edge is no owner; memoized (same instance).

### T003 — The three passes

- Add `Pass { ACTION, MISSION_TYPE, PROJECT }` and give `Entry` (and `SuggestionCandidate`) a `pass` field; for passes
  2 and 3 also the root (`Optional<Urn> root`). Keep the existing constructors' meaning for the offering form (pass
  `ACTION`, no root) so its tests stay unchanged.
- Add `walkEffective(missionType, action, depth, inForce)` (name to taste; keep `walk` as the offering form):
  1. `requireKnown`; mission type not in force ⇒ empty context, exactly as today.
  2. Pass 1: the five steps from `action:mt/a`, as `walk` runs them.
  3. Pass 2 roots: in-force targets of `scope` edges from `mission_type:mt` whose kind is directive, tactic or
     paradigm, not already listed. List them as step `SCOPE`, pass `MISSION_TYPE`, `via = mission_type:mt`,
     `hops = 1` **seeded explicitly** (do not call `hopsOf` on `mission_type:mt`; the engine only knows the start
     node's hops — squad A7). Then run steps 2 to 5 seeded from these roots; nothing listed earlier is listed again,
     but listed nodes still seed (D3).
  4. Pass 3 roots: in-force directives, tactics, paradigms with no owner (T002), not already listed: as pass 2, with
     `via = action:mt/a`, `hops = 1`.
  5. Entries sorted by full URN string (D3), as today; the context's enhancers cover every pass's entries (E12).
- Keep one `Engine` instance across the passes (one listing, one `refused` map); record the pass on what each
  `expand` lists. Determinism: iterate roots in URN order (TreeSet), as the engine already does.

### T004 — `ClosureView.actionContext` takes the effective form

- `ClosureView.actionContext(...)` (~l.585) calls the effective form; the offering service's `getActionContext` path
  (find its caller of `walk`) keeps `walk`. `ClosureView` is owned by WP05: this is an **out-of-map** one-call edit —
  record it in the commit message.
- `ClosureViewTest.theWalkStartsAtTheHeldStepOfAMissionTypeInForceAndListsOnlyArtifactsInForce` may now see more
  entries if its fixture holds unscoped in-force D/T/P: update the expectation only where the rule says so, and say
  why in the commit (a legitimate change of meaning, `### Breaking` in WP02).

### T005 — NFR-003 and NFR-004 at the packs level

- Operation count: instrument with a counting `Predicate` and a counting link iteration (as the effective reads'
  existing NFR gates do — search for an operation-count test in `service/packs/src/test` and follow it). Build indexes
  with 1,000 and 2,000 in-force directives (a mix of scoped, type-scoped and unscoped); assert
  `count(2000) / count(1000) <= 2.5`, the memo build included.
- Determinism: 100 repeated walks give equal `Context` values; shuffle the link order of the index (every permutation
  of three source packs' link blocks) and assert equal contexts.

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
- Implementation command: `spec-kitty agent action implement WP01 --agent claude --mission effective-step-and-action-context-01M774HB`

## Definition of Done

- T001's tests are green, and each one failed before its code (red commit present).
- `ActionContextWalkTest` and the offering read's tests pass unchanged.
- `./gradlew :service:packs:test` green; `HexagonalArchitectureTest` green.
- `Entry`/`SuggestionCandidate` carry the pass; the offering form reports `ACTION`.

## Risks and reviewer guidance

- Reviewers check: the offering walk is byte-identical; pass roots never call `hopsOf` on an unreached node; owners
  include unheld sources; only D/T/P are roots; the evidence test names DIRECTIVE_003 absent.
- A pass 3 root reached first by vocabulary in pass 1 is listed `ACTION` (R3); the test documents it.
