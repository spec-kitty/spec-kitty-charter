---
work_package_id: WP07
title: The ranker
dependencies:
- WP05
requirement_refs:
- FR-007
- FR-008
- FR-009
- NFR-001
- NFR-002
planning_base_branch: feat/108-model-task-routing
merge_target_branch: feat/108-model-task-routing
branch_strategy: Planning artifacts for this mission were generated on feat/108-model-task-routing. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/108-model-task-routing unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-model-task-routing-01M7PVHQ
base_commit: 821eab6dbc823d37a54ec9f8a4ab40d76522f6eb
created_at: '2026-11-18T18:02:00.039468+00:00'
subtasks:
- T023
- T024
- T025
- T026
phase: Phase 3 - Reads
history:
- at: '2026-11-18T08:55:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/routing/src/main/java/ai/speckitty/charter/routing/domain/ranking/
create_intent:
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/ranking/Ranker.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/ranking/Recommendation.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/ranking/CatalogCandidate.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/ranking/TiersInPlay.java
- service/routing/src/test/java/ai/speckitty/charter/routing/domain/ranking/RankerTest.java
execution_mode: code_change
owned_files:
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/ranking/**
- service/routing/src/test/java/ai/speckitty/charter/routing/domain/ranking/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP07 – The ranker

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according to
its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`). Address every feedback item, and record
each change with `spec-kitty agent tasks add-history WP07 --note "..."`.

## Branch Strategy

- **Planning base branch**: `feat/108-model-task-routing`
- **Merge target branch**: `feat/108-model-task-routing`
- The execution worktree is allocated per computed lane from `lanes.json`; never pick a base branch by hand.

```bash
spec-kitty agent action implement WP07 --agent claude --mission model-task-routing-01M7PVHQ
```

## Before you start (every WP)

1. **#355 must be merged.** No code starts before #355 (PR #599) has merged and this branch is rebased onto that main
   (C-001). If it has not merged, stop and report.
2. **Recheck every path below on the merged main.** This prompt was written against main `8284bd36` with #599 open. #599
   rewrites `PackContents`, `PackRulesets`, `PackAssembly`, `StoreRead`, `StorageRulesets` and
   `ReferenceFacts`/`ReferenceClosure`, and deletes `MissionTemplates` and `GuardClosure` (research R1). Follow the
   merged code, not this text, where they differ, and record the difference in the WP history.
3. **Read the authority:**
   - `docs/adr/0080-the-routing-catalog-kind-and-the-model-recommendation.md` (accepted);
   - `kitty-specs/model-task-routing-01M7PVHQ/{plan.md,data-model.md,research.md,contracts/operations.md}`;
   - the charter companion `.kittify/charter/charter.md`.

   Core's `/opt/spec-kitty/src/charter/offering/model_task_routing/` is evidence only (ADR-0015).
4. **Tests use the canonical shape only**, except tests of invalid content (charter companion; ADR-0070). Never exclude
   a field from a parity or round-trip check to make a test pass.
5. **Commits:** signed (`git commit -S`), ending with the `Claude-Session:` trailer, with no model names and no
   `Co-Authored-By`.

## Objective

A pure, Spring-free ranker in `routing.domain.ranking`: given a `RoutingCatalog`, a task type id and an optional
effective `CostTier`, it answers the catalog candidates and the tiers in play exactly as `data-model.md` "Rules" 1–4,
6 and 7 state them. These are ADR-0080 decisions 10–12 (FR-007, FR-008, FR-009, NFR-001, NFR-002).

The profile candidate and the tier's source are not the ranker's: WP09 joins them. The ranker takes only the
effective tier.

## Subtasks

### T023 – Score and rank key

- **Score.** `score(model, fit, weights)` is the weighted sum of `data-model.md` rule 1:
  - Constants: cost `low` 1, `medium` 0.66, `high` 0.33, `premium` 0. Risk, from the confidence: `high` 1, `medium`
    0.66, `low` 0.33, absent 0.5. Latency: `low` 1, `medium` 0.66, `high` 0.33, absent 0.5.
  - All are `BigDecimal` constants built from strings (`new BigDecimal("0.66")`), never from `double`.
  - No rounding, and no `MathContext` that rounds.
- **Rank key.** `quality_first` → (`fit.score`, `score`); `balanced` and `cost_first` → (`score`). Compare with
  `compareTo`, never `equals`, because scale differs: `0.30` against `0.3`.
- **Javadoc.** State that `balanced` and `cost_first` compute alike today and differ in meaning (decision 10).

### T024 – Tiers in play and the two top-ranked sets

- **The tiers in play** (`TiersInPlay`): the task type's `max_tier`, if the catalog declares one, and the effective
  tier, if given. A model is *inside* when its `cost.tier` is `atMost` every tier in play (data-model rule 3). The two
  always combine: a `premium` effective tier never lifts a `medium` cap.
- **The candidates:**
  - `fitting` = models with a fit for the task type;
  - `inside` = the fitting models that are inside;
  - answer `top(inside) ∪ top(fitting)`, where `top(S)` = every model whose rank key equals the maximum in S (all
    ties);
  - no model is answered twice;
  - an empty `inside` contributes nothing.
- **No fit.** A task type that the catalog declares but no model fits answers no candidates.
- **Unknown task type.** It is a refusal (WP09), so the ranker throws on a task type the catalog does not declare,
  with a typed exception WP09 maps to `task_type_not_found`.

### T025 – Flags and order

- **Each candidate** (`CatalogCandidate`) carries: `modelId`, `score`, `fitScore`, `rationale` (Optional), `costTier`,
  `withinTierConstraint` (true when no constraint) and `withinModelTier`. `withinModelTier` is an `Optional<Boolean>`:
  present only when there is an effective tier.
- **Order.** Rank descending, then model id by code point (`String.compareTo`), never by `Collator`. The order of
  `models` in the catalog must never matter.
- **`Recommendation`** carries the task type, the objective, `Optional<CostTier> tierConstraint` and the ordered
  candidates.
- **Wire form.** WP09 renders a score as `stripTrailingZeros().toPlainString()`; expose `score` as a `BigDecimal`.

### T026 – Unit tests that fail when their rule is removed

- **Fixtures.** Build the R7 catalogs in code (the three of `research.md` R7). Do not read the example packs: this is
  a domain test.
- **One test per R7 ranking row (1–7).** Each asserts the **exact ordered candidate list** with every flag, never
  containment (D3).
- **Plus these tests:**
  - **Float against decimal.** Org `design` under `balanced` answers `model-a` and `model-b` tied. A control computing
    the same two scores in `double` shows they differ (`0.30000000000000004` against `0.3`), proving the test bites.
  - **All-zero weights.** Under `balanced`, every fitting model ties and is answered. Under `quality_first`, fit alone
    decides.
  - **File order.** Reverse `models` in a catalog, and the answer is identical.
  - **Overlap.** When `top(inside)` equals `top(fitting)`, each model is answered once.
  - **Ties straddling a tier.** Two models tie at the top, one inside and one outside. Both are answered, and the
    top-inside set is computed separately.
  - **Unknown task type.** It throws the typed exception.
- **The "fails when removed" check.** In the WP history, record for each test which line of the ranker it pins.
  Temporarily break that rule (for example, swap the rank key or drop a tier), see the test fail, and restore.

## Definition of Done

- The ranker implements data-model rules 1–4, 6 and 7, with tests that bite.
- `./gradlew verifyAll` passes on Java 25 for the modules this WP touches, with no new style-gate finding.
- Every subtask's status is recorded with `spec-kitty agent tasks mark-status`.
- Nothing outside `owned_files` changed, except a recorded edit outside the map, named in the WP history with its
  reason.

## Reviewer Guidance

- Recompute two R7 rows by hand against the tests.
- Check that `compareTo` is used everywhere, and that nothing passes through `double`.
- **Independent reviewer.** Review is by a separate reviewer session, never by the implementer.
