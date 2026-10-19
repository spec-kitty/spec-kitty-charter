---
work_package_id: WP01
title: The action-context walk
dependencies: []
requirement_refs:
- FR-002
- FR-003
- FR-004
- FR-005
- FR-006
- FR-018
- NFR-001
- NFR-003
- NFR-005
- C-002
- C-003
- C-004
- C-005
planning_base_branch: feat/action-context-reserved-surface
merge_target_branch: feat/action-context-reserved-surface
branch_strategy: Planning artifacts for this mission were generated on feat/action-context-reserved-surface. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/action-context-reserved-surface unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-action-context-reserved-surface-01M5AFEK
base_commit: 8a52ff0493091b721479d1d7b0f38fca38019fb0
created_at: '2026-10-19T16:50:49.680659+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
phase: Phase 1 - The read
history:
- at: '2026-10-19T17:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/charter-read-app/src/main/java/ai/speckitty/charter/read/
create_intent:
- contracts/compat/fixtures/compatible-depth-max-raised/base.yaml
- contracts/compat/fixtures/compatible-depth-max-raised/revision.yaml
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ContractShapeTest.java
execution_mode: code_change
model: sonnet
owned_files:
- contracts/charter/parameters/Depth.yaml
- contracts/charter/schemas/ActionContext.yaml
- contracts/charter/schemas/GuidanceEntry.yaml
- contracts/charter/paths/mission-types_missionType_actions_action_context.yaml
- contracts/charter/examples/ActionContext.software-dev-plan.yaml
- contracts/charter/scenarios/action_context.feature
- contracts/compat/fixtures/compatible-depth-max-raised/**
- contracts/compat/fixtures/breaking-response-depth-max-raised/**
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ContractShapeTest.java
- service/charter-read-app/src/main/java/ai/speckitty/charter/read/ActionContext.java
- service/charter-read-app/src/main/java/ai/speckitty/charter/read/ActionContextQuery.java
- service/charter-read-app/src/main/java/ai/speckitty/charter/read/GuidanceEntry.java
- service/charter-read-app/src/test/java/ai/speckitty/charter/read/ActionContextQueryTest.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/DoctrineGraph.java
- service/adapter-in-rest/src/main/java/ai/speckitty/charter/rest/ContextController.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01: The action-context walk

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ IMPORTANT: Review Feedback

- **Has review feedback?**: check the `review_ref` field in the event log (`spec-kitty agent tasks status`).
- Address every feedback item, and record each with `spec-kitty agent tasks add-history WP01 --note "..."`.

## Review Feedback

*[Filled from the status event log when this WP returns from review.]*

---

## Objectives & Success Criteria

`getActionContext` (`GET /mission-types/{missionType}/actions/{action}/context`) becomes the
offering's full walk for one action (issue #95, decisions D1 to D4 and D13 in `research.md` §3).

| Step | From | Reach | Entry relation |
|---|---|---|---|
| 1. scope | the **action node only** | 1 hop | `scope` |
| 2. requires | the scoped artifacts | transitive | `requires` |
| 3. suggests | the scoped artifacts (step 1) | `depth` suggests-steps | `suggests` |
| 4. requires of suggested | the step-3 artifacts | transitive | `requires` |
| 5. vocabulary | every artifact steps 1 to 4 list | 1 hop | `vocabulary` |

Every step is breadth-first and cycle-safe. A step never lists a node an earlier step listed,
but it still seeds later steps from it. Step 4's artifacts are not walked for `suggests`.

**Operator steer: do not replicate core's bugs.**

- **Step 4 differs from core's code,** which lists suggested artifacts without their
  `requires`. It follows core's stated intent instead: `requires` means "hard dependencies"
  (`query.py:194-195`). This is registered as DIV-110.
- **Step 3 matches core's stated intent:** `suggests` is walked "from scoped artifacts". Seeding
  it from required artifacts was measured at +30% governance and rejected (research D2).

**Not walked:** the mission-type node's scope edges, which are core's activation-cascade entry
point (research §2 and §8, O1). The action and mission-type nodes are never entries.

**Each entry** carries:

- `relation`: the relation of the step that listed it.
- `via`: its parent in that step. For a scope entry, this is the action node; otherwise it is
  always another entry of the answer.
- `hops`: 1 for scope, and `hops(via) + 1` for every other entry.

**Invariant:** following `via` reaches the action node in exactly `hops` edges, along real graph
edges.

**Parent choice within a step:** the minimum step distance (breadth-first), then the parent
with the fewest `hops`, then the smallest URN string.

**Dedup:** first listing wins, in step order. Entries are sorted by full URN string.

**`depth`:**

- Request parameter: `suggests` hops, minimum 0, default 2, maximum 2. Out of range is a 400
  Problem, as today.
- `ActionContext.depth`: minimum 0 and **no maximum**.

**The answer** gains a required `diagnostics: Diagnostic[]`: the offering's load findings, the
same as `PackSummary.diagnostics` across all packs in load order.

**Done when:**

- the new scenarios were seen failing on assertions, then pass;
- `./gradlew verifyAll -PopenPacksDir=<open-packs>` is green on Java 25;
- the unit tests cover a cycle, a diamond, and each dedup rule.

## Context & Constraints

**Read first:**

- `.kittify/charter/charter.md`;
- `spec.md`: User Story 1, FR-002 to FR-006 and FR-018, NFR-003, NFR-005, C-002 to C-005;
- `research.md`: §2 (core evidence), §3 (D1 to D5, D13), §5 (register), §8;
- `data-model.md`;
- `contracts/contract-delta.md`.

**Constraints:**

- **The charter repository is canonical for the contract (ADR-0015).** Core's
  `src/charter/offering/drg/query.py:184-243` is evidence. Match its walk semantics where D2
  says so (breadth-first, seeds, cycle safety). Where D2 differs on purpose (DIV-110, the operator steer: do not replicate core bugs), follow D2.
- **No activation filtering, selection roots or tension annotation.** These belong to R9 (#103)
  and the creed set (D5, D6). The read is the offering's walk for good.
- **Runner B boundary (C-001):** do not edit `contracts/packs/**`, `PackDescriptor*`,
  `CharterPack*`, or pack-level code in `adapter-out-yaml`.
- **CHANGELOG:** do **not** edit `contracts/charter/CHANGELOG.md`. WP05 owns it. List your
  contract changes in the WP history note, so WP05 can record them.
- **ArchUnit:** read-app stays plain Java plus JSpecify, every main package is `@NullMarked`,
  and contract DTOs are reached only by `adapter-in-rest`.
- **Style gates:** palantir-java-format (`./gradlew spotlessApply`), Checkstyle, Error Prone with
  NullAway, and Javadoc (`-Xdoclint:all -Werror`) on every public member.
- **Complexity ≤ 15 per method.** Split the walk into small methods.

## Branch Strategy

- **Planning base branch**: `feat/action-context-reserved-surface`
- **Merge target branch**: `feat/action-context-reserved-surface`
- Execution worktrees are allocated per computed lane from `lanes.json`. Never pick a base
  branch by hand.

```bash
spec-kitty agent action implement WP01 --agent claude --mission action-context-reserved-surface-01M5AFEK
```

**Build environment:** `export JAVA_HOME=/opt/jdk-25 PATH=/opt/jdk-25/bin:$PATH`. Open-packs is
at `/home/user/spec-kitty-open-packs`, pinned to `OPEN_PACKS_REF`. On a Maven Central 429, wait
and retry with `--max-workers=1`. Never touch `gradle/verification-metadata.xml` to get past a
fetch error.

## Subtasks & Detailed Guidance

### Subtask T001: Red scenarios for the full walk

**Purpose:** scenarios first. They are the specification (charter: BDD).

**Steps:** rewrite `contracts/charter/scenarios/action_context.feature`. Keep the file's
`@contract @provisional` tags and its style: exact JSON bodies via "the response body, apart
from "governanceFingerprint", is:". Change it as follows.

1. **Feature description:** the walk as tabled above.
2. **Background:** remove the mission-type scope edges' role. Keep `mission_type:software-dev`
   as a node, since the 404 scenarios need it. Keep a mission-type scope edge to a directive
   that nothing else reaches, so a scenario can prove it is **not** walked. Add graph shards
   that give:
   - a `requires` chain of 2 (scoped tactic → directive → styleguide);
   - a `suggests` chain of 2 from a scoped artifact, plus a **diamond**: X reachable at 1 and
     at 2 hops, whose child Z must appear at depth 2;
   - a `requires` cycle (A requires B, B requires A);
   - a suggested artifact that `requires` something, which **is** listed (step 4, relation
     `requires`, via the suggested artifact);
   - a required artifact that `suggests` something, which is **not** listed through it (core's
     intent);
   - a `vocabulary` edge from a required artifact to a `glossary_pack` node;
   - an artifact both scoped and required (scope wins), and one both required and suggested
     (requires wins);
   - two parents at equal distance (the smallest `via` wins).

   Use existing step phrases only (`Given the built-in pack "built-in" with the file "<f>":`).
   If you must add a step, add it in a new glue class in
   `service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/`. Record that
   out-of-map file with `add-history`.
3. **Rewrite these scenarios, do not delete them:**
   - `:81` "scope of the action and of its mission type": now "the action's scope is walked;
     the mission type's is not".
   - `:100` "scoped by both": now "an artifact reached by two walks keeps its strongest entry".
   - `:139` (undeclared target, null label): kept. Its `hops` is 1.
   - `:207` "depth 1 only": now "depth counts suggests hops", with three bodies at
     depth 0, 1 and 2.
   - `:226` (`depth=2` → 400): now `depth=3` and `depth=-1` → 400.
   - **Out-of-map:** `service.feature:44-52` also expects `?depth=2` → 400 ("A bad request carries
     the headers too"). Change its request to `depth=3`. That file is WP02's; record the edit.
4. **New scenarios:**
   - requires chain;
   - requires cycle terminates;
   - suggests diamond at depth 2;
   - follow-through of `requires` from suggested artifacts (DIV-110), with a comment naming
     core's code and why the charter differs;
   - no `suggests` from required artifacts (core's intent);
   - the `via`/`hops` invariant: walk the `via` chain of the deepest entry back to the action;
   - vocabulary entry;
   - default depth is 2 and is echoed;
   - `diagnostics` is empty for packs that declare `pack_level`, and carries
     `pack_level_missing` for a pack whose `pack.yaml` has no `pack_level`. To trigger the
     second, put a pack without `pack.yaml` in a separate scenario. No `PackDescriptorReader`
     edit is needed.
5. **Bodies:** every `guidance` entry in every body carries `hops`, and every body carries
   `diagnostics`.
6. **Red run:** run the suite before T002 to T006 and capture the assertion failures (not
   undefined steps, not a compile error):

   ```bash
   ./gradlew :charter-service:test --tests '*CharterContractSuite*' -PopenPacksDir=/home/user/spec-kitty-open-packs
   ```

   Record the failing scenario names and the suite's scenario count in the WP history
   (`add-history`). Commit the red scenarios on their own.

**Validation:** every new scenario fails on a body or status assertion.

### Subtask T002: Contract reshape for the context read

**Files:**

- `parameters/Depth.yaml`;
- `schemas/ActionContext.yaml`;
- `schemas/GuidanceEntry.yaml`;
- `paths/mission-types_missionType_actions_action_context.yaml`;
- `examples/ActionContext.software-dev-plan.yaml`.

**Steps:**

1. **`Depth.yaml`:**
   - Description: "How many `suggests` hops the walk follows from the artifacts the action
     scopes. 0 lists no suggested artifact. Scope is one hop and `requires` is transitive, so
     neither depends on depth."
   - Explain the seam mapping: first load → 2, after a recorded load → 1, the public bundle
     read → 0. First-load state stays in core's seam (ADR-0011 D3).
   - "A later version may raise the maximum, which is compatible. A depth outside the range is
     a 400 Problem, not a charter refusal."
   - Schema: `minimum: 0`, `maximum: 2`, `default: 2`.
2. **`ActionContext.yaml`:**
   - `depth`: `minimum: 0`, **no `maximum`**. Its `x-derived` rule is "the depth query
     parameter, or 2".
   - Add required `diagnostics`: an array of `$ref: Diagnostic.yaml`. Description: the load
     findings of the offering the answer was computed from, the same findings the pack
     listing reports per pack, in load order. Give it an `x-source` citation to the read-app
     record.
   - Rewrite the `guidance` description and its `x-derived` rule for the four walks and the
     dedup rule.
   - `governanceFingerprint` is unchanged.
3. **`GuidanceEntry.yaml`:**
   - Add required `hops`: `integer`, `format: int32`, `minimum: 1`, with a description of the
     producing-walk distance and an `x-source`.
   - `via`: describe it as the producing walk's parent, which may itself appear under a
     stronger relation. Its `x-derived.inputs` gain the guidance entries.
   - `relation`: no schema change. Describe that this read answers `scope`, `requires`,
     `suggests` or `vocabulary`.
   - `impacts`: "null for every relation this read walks".
4. **The path description:**
   - Rewrite it: the offering's walk for good; what applies in a project is the activation
     set's operation (#103); type-wide scope is reached through that operation, not this one.
   - Keep `x-provisional`. Rewrite its `open_decision` to name only what the corpus (#9) still
     decides: the registered divergences in `docs/conformance/divergence-register.md` (WP05 creates
     that page; link it by path).
5. **The example:** regenerate it from the real service answer for open-packs
   `software-dev/plan` at depth 1, trimmed to 3 to 6 entries, with `hops` and `diagnostics`.
   It must still validate. `ContractConformanceTest` checks examples.
6. **Contract checks:** run them.

   ```bash
   ./gradlew :contracts:check
   ```

   Lint needs `x-source` or `x-derived` on every property. Closed objects must keep
   `additionalProperties: false`.

### Subtask T003: Compat fixtures and the no-maximum pin

**Purpose:** prove D4's claim. Raising the maximum later stays compatible only because the
response `depth` has no maximum.

The meaningful evidence is the breaking counterpart. So also:

- add `contracts/compat/fixtures/breaking-response-depth-max-raised/`: a response property
  with `maximum: 2` raised to 3. Commit it **only if** the compatibility task reports it at ERR
  under `severity-levels.txt`. If it is not ERR, record that in history, and keep only the
  no-maximum test below;
- add a small JUnit test, out-of-map new file
  `service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ContractShapeTest.java`,
  that reads the contract's `ActionContext.yaml` from the test classpath and asserts `depth`
  has `minimum: 0` and no `maximum`. Red first: write it before T002.

**Steps:**

1. Copy the shape of `contracts/compat/fixtures/compatible-descriptions-reworded/` (a
   `base.yaml` and a `revision.yaml`).
2. Write a minimal OpenAPI document with one GET operation:
   - a query parameter `depth` with `maximum: 2`;
   - a response object with `depth` (`minimum: 0`, no maximum).
3. The revision raises the parameter's maximum to 3.
4. The `compatibilityFixtures` task in `contracts/build.gradle.kts` must pick it up by its
   `compatible-` prefix. Check how the task discovers fixtures.
5. Run `./gradlew :contracts:compatibilityFixtures`.
6. The breaking fixture and `ContractShapeTest` are above.

### Subtask T004: `DoctrineGraph` outgoing index

**Purpose:** `outgoing(source, relation)` is a linear scan (`DoctrineGraph.java:88`). A
transitive walk over open-packs (355 `requires` and 475 `suggests` edges) calls it per visited
node.

**Steps:**

1. Build an immutable index (source, relation) → `List<DrgEdge>`, either lazily once or in the
   constructor. Keep the declaration order of edges, so `via` tie-breaks stay deterministic.
2. Keep the method's contract and Javadoc.
3. Make sure `CharterOffering.governanceFingerprint` does not change. The fingerprint test
   (`GovernanceFingerprintTest`) must still pass unchanged.

**Out-of-map note:** add a unit test to `service/charter-domain/src/test/.../DoctrineGraphTest.java`
(new) if none covers `outgoing`. It is a new test file, recorded in history.

### Subtask T005: `ActionContextQuery` walk and unit tests

**Files:**

- `ActionContextQuery.java`, `GuidanceEntry.java` (gains `int hops`) and `ActionContext.java`
  (gains `List<Diagnostic> diagnostics`), all in read-app;
- `ActionContextQueryTest.java`.

**Steps:**

1. `MAX_DEPTH = 2`, `MIN_DEPTH = 0`, `DEFAULT_DEPTH = 2`. The controller uses these.
2. **Structure** (each method ≤ 15 complexity):
   - A `Map<Urn, GuidanceEntry> listed`, in insertion order, plus a step function
     `step(graph, listed, seeds, relation, OptionalInt maxSteps)`.
     - It runs a breadth-first walk over `outgoing(node, relation)` from the seeds.
     - It adds each reached node that is not yet listed and is not the action or mission-type
       node.
     - **Parent choice:** the minimum step, then the fewest `hops` among that step's frontier
       parents, then the smallest URN.
     - `hops` is `listed.get(parent).hops() + 1`, or 1 when the parent is the action.
     - Already-listed nodes are traversed as frontier nodes, so a later step can pass through
       them, but they are not re-listed.
   - **Steps:**
     1. scope from `{action}`, max 1;
     2. `requires` from the scope entries, unbounded;
     3. `suggests` from the scope entries, max `depth` (skip when `depth` is 0);
     4. `requires` from the step-3 entries, unbounded;
     5. `vocabulary` from all listed entries, max 1.
   - **Passing through:** decide whether a node that is already listed but reached at
     suggests-step k should expand at step k+1 within the same step. **Yes:** it is a node of
     the walk, just not a new entry. Pin that with the diamond-through-required case in the unit
     tests.
3. **Sorting:** sort the result by URN.
4. **Exclusions:**
   - The action node and the mission-type node never appear as entries, even when an edge
     points back at them. Core removes the action from the seed set; check the background's
     `mission_type requires action` edge.
   - A walk target the graph does not declare is still listed with a null label (DIV-102).
     Nothing is walked from it, because it has no outgoing edges.
5. **Diagnostics:** collect `offering.packs()`' diagnostics in load order. Check how
   `PackListingQuery` reads them.
6. **Unit tests** (`ActionContextQueryTest`), with an in-memory repository as the existing
   tests use:
   - each walk alone;
   - depth 0, 1, 2;
   - `IllegalArgumentException` for depth -1 and 3;
   - a cycle;
   - a diamond (the child appears at depth 2);
   - `requires` of suggested listed (DIV-110); `suggests` of required not listed;
   - the `via`/`hops` invariant over every entry of a dense random graph (a seeded generator,
     a handful of seeds, asserting the invariant and termination);
   - every precedence rule, including an artifact both suggested and reached by `vocabulary`, and
     one both required and reached by `vocabulary` (first listing wins);
   - equal-distance parent tie-break;
   - action and mission-type never listed;
   - the mission type's scope not walked;
   - diagnostics passed through;
   - undeclared target with a null label.
7. Assert outcomes (entries and their fields), not calls (charter: no fake green).

### Subtask T006: `ContextController` mapping

**Steps:**

1. Default the depth to `ActionContextQuery.DEFAULT_DEPTH`. Range-check it against `MIN_DEPTH`
   and `MAX_DEPTH`, and keep the `ResponseStatusException(400)` path. Check what the
   generated interface gives for the parameter's default: openapi-generator puts the default
   in `@RequestParam(defaultValue = "2")`.
2. Map `hops` and `diagnostics` to the generated DTOs. Reuse the `Diagnostic` mapping
   `PacksController` already has. Extract a small shared mapper in `adapter-in-rest` only if
   duplication would exceed a few lines. That would be an out-of-map new file, recorded.
3. Run the whole suite and `verifyAll`:

   ```bash
   ./gradlew verifyAll -PopenPacksDir=/home/user/spec-kitty-open-packs
   ```

## Red-first evidence (required)

The WP history must hold:

1. the red run of T001: the assertion-failing scenario names, and the scenario count before and
   after;
2. the green run after T006;
3. the commit hashes of both.

## Definition of Done

- [ ] T001 to T006 done. Every new or rewritten scenario was seen failing on an assertion.
- [ ] `verifyAll` green on Java 25. No test skipped, disabled or quarantined.
- [ ] No edit to runner B's files, and none to `contracts/charter/CHANGELOG.md`.
- [ ] The history lists every contract change for WP05's CHANGELOG entry, and every out-of-map
  file with its rationale.

## Risks

- **The background fixture serves many scenarios.** Changing it changes every body. Rewrite the
  bodies deliberately; never loosen assertions to "apart from guidance".
- **Defaults:** openapi-generator may generate `Integer depth` with a default. Keep the 400 for
  out-of-range values.
- **Examples:** `ContractConformanceTest` validates them against the schemas. Regenerate the
  example after the schema change.

## Reviewer guidance

Check that:

- breadth-first minimum-step semantics hold (the diamond scenario);
- the mission-type node's scope is not walked;
- the `via`/`hops` invariant holds;
- DIV-110's follow-through is implemented and commented as an intentional difference from
  core's code;
- the precedence is exactly D3;
- `depth` has no response maximum;
- the red-first evidence is a real assertion failure.

Reject any loosened assertion.
