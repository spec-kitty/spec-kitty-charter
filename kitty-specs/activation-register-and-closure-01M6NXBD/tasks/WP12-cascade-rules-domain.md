---
work_package_id: WP12
title: cascade rules (activations domain)
dependencies:
- WP11
requirement_refs:
- FR-012
- FR-013
- FR-014
- FR-015
- C-002
- NFR-001
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-activation-register-and-closure-01M6NXBD
base_commit: b4eb6a3a4cf48b031d8117d18c952246bf51541e
created_at: '2026-11-05T23:39:10.829230+00:00'
subtasks:
- T068
- T069
- T070
- T071
- T072
- T073
- T074
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/domain/CascadeRules.java
create_intent:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ArtifactRef.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/CascadeScope.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/Reach.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ReachPlan.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/SkippedReport.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ActivationChange.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/CascadeRules.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeScopeTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesSelectTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesLineageTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesClearingTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesDeactivateTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/Reaches.java
execution_mode: code_change
model: ''
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ArtifactRef.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/CascadeScope.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/Reach.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ReachPlan.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/SkippedReport.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ActivationChange.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/CascadeRules.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeScopeTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesSelectTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesLineageTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesClearingTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/CascadeRulesDeactivateTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/Reaches.java
role: implementer
tags: []
tracker_refs: []
---

# WP12: cascade rules (activations domain)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Build the cascade **policy** as pure domain functions. `packs` does the traversal (WP11); this WP only decides what a
reach result means for the document:

- the kind scope, and the refusal of `["all"]` mixed with kinds (422 at `/cascade`);
- the select delta: what joins `selected` and what is reported in `skippedByScope`, and `skippedDeactivated` with
  `viaRequires` and `requiredBy`;
- `unreachedBehindDeactivated`;
- the lineage pass-through: `specializes_from` ancestors, `requires` and `scope` only, transitive, stopped by a
  denied ancestor, never in force;
- the `includeDeactivated` clearing set: the denied artifacts the walk reaches, plus the deny entries the read-time
  `enhances` pull **would reach** from what the walk selects. That pull runs both ways (enhancers of a selected
  artifact, and targets of a selected enhancer) and is transitive over `enhances` (ADR-0059 D2.2 read literally, R1);
- the cascading-deactivate `skippedShared` check, where the active set is `selected` ∪ the floor, and a reference by an
  active enhancer counts.

It **never** adds an enhancer to `selected` (ADR-0059 D2.2).

Implementation command:
`spec-kitty agent action implement WP12 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** B. **Concern:** IC-08. **Squad:** B1 (the 2026-11-05 rulings supersede the 13:15 decisions), R13 (the active
  set is selected plus the floor), A1 (the domain receives values, never a port), A5 (no traversal here).
- **Rulings** (ADR-0059 on `main`, as revised on PR #376 on 2026-11-05, which supersedes decisions
  `DM-01M6NXERTNKMWK4MTDTX48358B` and `DM-01M6NXKAHRQNR4RK14FFJN2TVM`):
  - **D2.1:** `requires`, `suggests`, `refines`, `scope` and `instantiates`, forward, unbounded, with a visited
    guard; stop at deny and report.
  - **D2.2:** `enhances` is pulled at **read**, never written into `selected`. A write touches it only for
    `includeDeactivated` deny-clearing and for deactivate's shared check.
  - **D2.4:** lineage passes `requires` and `scope` only, up the whole chain; a denied ancestor stops it and is
    reported, and what lies behind it goes in `unreachedBehindDeactivated`. The `cascade` scope does not filter an
    ancestor.
  - ADR-0013 D4 (the report buckets) and ADR-0020 D6 (the `ActivationChange` answer).
- **Contract shapes** (read them): `SkippedReport`, `SkippedDeactivated` (`viaRequires`, `requiredBy`: artifacts),
  `SharedSkip` (`referencedBy`: one `ArtifactReference`), `ActivationChange` (`applied`, `cleared`), `CascadeScope`
  and `CascadeKind`.
- **Grounding:** WP11's `ReachResult` gives reached nodes with every `Via(predecessor, relation)`, stopped nodes with
  every `Via`, `behindStopped` and `ancestorsPassed`. The activations domain may not name it (slices do not cross),
  so this WP defines a mirror value `Reach`, and WP13's adapter copies into it.

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure` (PR B, stacked on PR A).
- Lane worktree from `lanes.json`. It depends on WP11.
- Rebase onto the lane base before review.

## Subtask Guidance

### Subtask T068: Values (`ArtifactRef`, `Reach`, `ReachPlan`, `SkippedReport`, `ActivationChange`)

**Steps:**
1. `ArtifactRef(ArtifactKind kind, String id)`, comparable (kind token, then id) so reports are deterministically
   sorted, with a `urn()` text helper.
2. `Reach`: `Map<ArtifactRef, List<Via>> reached`, `Map<ArtifactRef, List<Via>> stopped`, `Set<ArtifactRef>
   behindStopped` and `Set<ArtifactRef> ancestorsPassed`, where `Via(ArtifactRef from, Relation relation)` and
   `Relation` is a small enum of the relations this domain cares about (`REQUIRES`, `SUGGESTS`, `REFINES`, `SCOPE`,
   `INSTANTIATES`, `ENHANCES`, `SPECIALIZES_FROM`), or the kernel `RelationType` (the kernel is allowed). Prefer the
   kernel type.
3. `ReachPlan(Set<ArtifactRef> starts, Set<RelationType> relations, Set<ArtifactRef> stopAt, boolean lineage)`: what
   the application asks `packs` to walk.
4. **`applied` and `cleared` (A1, the contract rule):** `applied` lists every id the write **added to or removed from**
   `selected`, `deactivated` or `missionTypes` (`contracts/charter/schemas/ActivationChange.yaml`); `cleared` lists the
   deny entries removed. A kind (`activatedKinds`) is not an `ArtifactReference` and never appears. A no-op has both
   empty and keeps the revision.
5. `SkippedReport` (four sorted lists, with `SkippedDeactivated(ref, viaRequires, List<ArtifactRef> requiredBy)` and
   `SharedSkip(ref, ArtifactRef referencedBy)`), and `ActivationChange(ActivationDocument after, List<ArtifactRef>
   applied, List<ArtifactRef> cleared, SkippedReport skipped)`.
6. `Reaches`, a test builder for `Reach` values, mirroring `ClosureSnapshots`.

### Subtask T069: `CascadeScope`

**Steps (red first, `CascadeScopeTest`):**
1. Absent means no cascade (`CascadeScope.none()`); `["all"]` covers every activatable kind; `["directive",
   "tactic"]` covers those kinds.
2. `["all", "tactic"]` gives a violation `/cascade`, `all_mixed_with_kinds` (US3 AS8, 422).
3. An unknown kind token gives `/cascade/i`, `unknown_kind`.
4. `admits(ArtifactKind)`.

### Subtask T070: Select: the delta and the report

**Purpose:** FR-011 and FR-012, US3 AS1, AS2, AS6 and AS8.

**API:** `CascadeRules.planSelect(document, target, scope)` returns a `ReachPlan` (starts {target}; the five relations;
stopAt = the document's deny entries, unless `includeDeactivated`; lineage true). `CascadeRules.applySelect(document,
target, scope, includeDeactivated, reach, pulled)` returns an `ActivationChange`, where `pulled` is the
`enhancesPull` answer (empty without `includeDeactivated`).

**Steps (red first, `CascadeRulesSelectTest`, with the fixture of US3: D requires T, D requires X, X denied, E enhances
T):**
1. With scope `["all"]`: `selected.directive` = [D], `selected.tactic` = [T]; `applied` = [D, T]; X is in
   `skippedDeactivated` with `viaRequires` true and `requiredBy` [D]; E is **not** selected and appears nowhere in the
   report.
2. With scope `["directive"]`: only D is selected, and T is in `skippedByScope`.
3. With no cascade: only D; reach is not consulted (`planSelect` returns a plan with no relations, or the
   application skips the call; pick one and test it).
4. An absent `selected.directive` becomes exactly [D] (absent means none, US3 AS6).
5. Select clears the **target's own** deny entry (FR-011): D denied, then selected, gives D in `cleared`.
6. `viaRequires` is true when **any** `Via` of the stopped node has relation `REQUIRES`; `requiredBy` lists every
   `Via.from`, sorted.
7. Already-selected reached artifacts are not re-listed in `applied`: `applied` is what changed state (A1). It lists
   every id added to or removed from `selected`, `deactivated` or `missionTypes`. Compute it as one function,
   `ActivationChange.of(before, after, cleared, skipped)`, from the before and after documents, so select,
   deactivate, reactivate and preset share one definition. Pin it with a unit test: an unselect lists the id; a
   deactivate lists D and T; a reactivate lists the id (also in `cleared`); a no-op lists nothing.

### Subtask T071: `unreachedBehindDeactivated`

**Steps (red first):**
1. X denied, and X requires Y, which nothing else reaches: Y is in `unreachedBehindDeactivated`.
2. When Y is also reached over another path, it is selected and not reported as behind.
3. Behind items are reported regardless of the kind scope (they are not candidates); add the test that pins it.

### Subtask T072: Lineage (FR-014, US3 AS4)

**Steps (red first, `CascadeRulesLineageTest`):**
1. P `specializes_from` Q; Q requires R; Q suggests S. Selecting P with a cascade selects P and R, and neither Q nor
   S. Q is in no report bucket (`ancestorsPassed` is internal).
2. Q denied: R is in `unreachedBehindDeactivated`, and Q is in `skippedDeactivated` (`viaRequires` false,
   `requiredBy` [P]).
3. A transitive chain P → Q → Q2, where Q2 scopes R2: R2 is selected.
4. The cascade scope does not filter the ancestor (`["agent_profile"]` with Q a profile: still not selected, still
   passed through), but it does filter what the pass-through reaches (R, a directive, is in `skippedByScope` under
   `["agent_profile"]`).

### Subtask T073: `includeDeactivated` clearing (FR-013, US3 AS3)

**API:** with `includeDeactivated`, the plan has an empty `stopAt`. The application then asks
`enhancesPull(walkSelected)` (WP11: both directions, transitive over `enhances` only) and passes the answer in as
`Set<ArtifactRef> pulled`. The clearing set is (walk-reached ∪ `pulled`) ∩ the deny entries. Only the walk-reached ones
are selected; the pulled ones are cleared and **never selected**. The write's own cascade still walks only the five
relations (R1).

**Steps (red first, `CascadeRulesClearingTest`):**
1. Selecting D with a cascade and `includeDeactivated`: X is selected, and X's deny entry is in `cleared`.
2. An enhancer F of T that is denied: F's deny entry is cleared (it appears in `cleared`), and F is **not** added to
   `selected`. Expected `applied`: D, T, X and **F** (F was removed from `deactivated`, A1); F is in `applied` and
   `cleared`, not in `selected`.
3. An enhancer that is not denied: nothing happens to it (it stays out of `selected`, and is not in `cleared`).
3a. **A selected enhancer of a denied target (R1, the other direction):** the walk selects E, E enhances T, and T is
    denied. T's deny entry is cleared, and T is not selected (unless the five-relation walk also reaches it). Expected
    `applied`: the walk's selections plus T (removed from `deactivated`); `cleared`: [T].
3b. **A chain (R1, transitive):** E1 enhances E2, E2 enhances T, the walk selects T, and E1 and E2 are both denied.
    Both deny entries are cleared; neither is selected. Expected `applied` includes E1 and E2 (removed from
    `deactivated`), and `cleared` is [E1, E2].
3c. The pull does not cross five-relation edges: E enhances T, E requires Z, Z is denied, and the walk selects T. Z's
    deny entry stays.
4. A deny list that empties is dropped from the document (the canonical form).
5. **Property:** for every case, `after.selected` never contains an enhancer that the walk did not reach over the five
   relations. Assert it in a helper that every test in the class calls.

### Subtask T074: Deactivate and `skippedShared` (FR-015, US4 AS1, AS2)

**API:** a cascading deactivate drives **two** reaches:
- the target's own reach (the five relations, no stop), giving the candidates;
- the reach of the other active artifacts. Its starts are (`selected` ∪ floor) minus the target and minus the
  candidates, since candidates must not keep one another in force. The target is a stop.

The plans depend on each other: the second needs the first's answer, so `planDeactivate` returns the first plan, and
`planShared(document, target, ownReach, floor)` returns the second.

The application also asks `enhancersOf(candidates)`. `applyDeactivate(document, target, scope, ownReach, othersReach,
enhancers, floor)` returns an `ActivationChange`.

**Steps (red first, `CascadeRulesDeactivateTest`):**
1. D is selected with T (only D reaches it) and T2 (another selected directive D2 also requires it). Deactivate D with a
   cascade: D is in `deactivated`; D and T leave `selected`; T2 stays and is in `skippedShared` with `referencedBy`
   D2. Only D gets a deny entry.
2. T3 is reached only through D, and an **active** selected artifact E2 enhances T3 (E2 is in `selected` in the
   starting document): T3 stays, and is in `skippedShared` with `referencedBy` E2 (ADR-0059 D2.2). The naming
   matches WP13's fixture (R6): E enhances T in US3, and E2 enhances T3 in US4.
3. An enhancer that is **not** active (not selected, not on the floor) does not keep T3.
4. A floor artifact (required by a governing preset) referencing a candidate keeps it shared (the active set includes
   the floor, squad R13).
5. Without a cascade: only D leaves `selected` and gets a deny entry; `applied` is [D].
1a. Case 1's `applied` is [D, T]: D was added to `deactivated` and removed from `selected`, and T was removed from
    `selected`. T2 is not in `applied` (A1).
6. Scope: candidates outside the cascade scope are left alone and reported in `skippedByScope`.
7. The 409 for a required or locked target is **not** here: it is `DocumentRules` (WP06), applied by the service
   before planning. Add a test that `applyDeactivate` is never reached in that case at the service level (WP13).

## Checkstyle limits (NFR-001)

The limits are still off on `main`, held until the methods over them are refactored, so they are held by
construction: the reviewer measures new and changed methods by hand.

CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1, by construction. `applySelect` and `applyDeactivate` must be
compositions of small named steps (`scopeSplit`, `deniedReport`, `behindReport`, `clearing`, `shared`). No new
suppression, and no disabled test.

## Gate commands

```bash
./gradlew :activations:test --tests '*Cascade*'
./gradlew :activations:check
./gradlew :deployable:test --tests '*HexagonalArchitectureTest*'
```

## Definition of Done

- [ ] `CascadeScope` handles absent, `["all"]` and kinds, and refuses the mix at `/cascade`.
- [ ] `applySelect` produces the delta and the four buckets per ADR-0059 D2.1 and D2.4; the US3 fixtures pass.
- [ ] Lineage passes `requires` and `scope` only, transitively; a denied ancestor stops it and is reported; no
      ancestor enters `selected`.
- [ ] `includeDeactivated` clears the reached deny entries and the enhancers' deny entries; no enhancer is ever
      selected (the property helper runs in every clearing test).
- [ ] `applyDeactivate` keeps shared artifacts (selected ∪ floor, active enhancers count) and denies only the target.
- [ ] The domain is pure; there is no traversal code in it.
- [ ] No new suppression, and no disabled test.

## Risks

- **The 13:15 decisions in `decisions/` are superseded** (write-time `enhances` into `selected`; lineage over every
  relation). An implementer reading only `decisions/` will build the wrong rule; the spec's Context and ADR-0059 D2.2
  and D2.4 govern.
- **Report ordering.** Sort every list by `ArtifactRef` so answers are byte-stable across runs.

## Reviewer guidance (reviewer-renata)

- **Check the enhancer property:** find the helper asserting no enhancer enters `selected`, and check that every
  clearing test calls it.
- **Check the lineage test with `suggests`:** S must not be selected. A rule that passes every relation would fail it.
- **Check the shared test with an inactive enhancer** (case 3): without it, "any enhancer keeps it" passes as well.
- **Purity:** `git grep -n "packs\.\|springframework\|Deque\|Queue" service/activations/src/main/java/ai/speckitty/charter/activations/domain`
  must be empty.
