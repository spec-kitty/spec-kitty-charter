---
work_package_id: WP03
title: 'Slice 3b: the walker, the own-closure rules and REFERENCES (#638)'
dependencies:
- WP02
requirement_refs:
- FR-005
- FR-006
- FR-007
- NFR-001
- C-001
- C-002
- C-004
planning_base_branch: feat/adr-0082-graph-walk-slices
merge_target_branch: feat/adr-0082-graph-walk-slices
branch_strategy: Planning artifacts for this mission were generated on feat/adr-0082-graph-walk-slices. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/adr-0082-graph-walk-slices unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-adr-0082-graph-walk-slices-01M8A867
base_commit: 7d286df12d799a1618f51b8a047b3ddb48b0c417
created_at: '2026-11-26T17:22:58.995179+00:00'
subtasks:
- T012
- T013
- T014
- T015
- T016
phase: Phase 2 - Slice 3
history:
- at: '2026-11-25T22:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ValidationWalker.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GraphCheck.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Findings.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ProfileReader.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/FallbackProfileRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/RolePinRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ProfileEnhancementRule.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ValidationWalkerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/OwnClosureRulesTest.java
- docs/changelog/2026-11-25-own-closure-rules-from-the-factory.md
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ValidationWalker.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GraphCheck.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Findings.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClosureWalk.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClosureFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ImpactCheck.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PackRulesets.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ProfileReader.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/FallbackProfileRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/RolePinRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ProfileEnhancementRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OwnClosureWalk.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/FallbackProfileRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RolePinRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ProfileEnhancementRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureImpactCheck.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureImpactCheckTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/OwnClosureWalkTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackUninstallServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/QualifierLoadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ValidationWalkerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/OwnClosureRulesTest.java
- docs/architecture/architectural-patterns-applied.md
- docs/changelog/2026-11-25-own-closure-rules-from-the-factory.md
tags: []
tracker_refs: []
---

# Work Package Prompt: WP03 - Slice 3b: the walker, the own-closure rules and REFERENCES (#638)

## Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else.

## Objective

One `ValidationWalker` runs the rules the factory hands out. The own-closure rules (fallback profile, role pins, profile
enhancements) move into `packs.domain.rules` and run on it, `REFERENCES` runs on it, and the `ClosureWalk` port and
`OwnClosureWalk` are deleted. Order of findings unchanged.

Requirements: FR-005, FR-006, FR-007, NFR-001, C-001, C-003, C-006. Design: `research.md` R-3, R-4;
`data-model.md`. The parked draft on branch `refactor/638-validation-walker` is a starting point only: it is
subject-major (squad S1) and gates on any fault; rebase it, do not keep its shape.

## Subtasks

### T012 ValidationWalker, GraphCheck, Findings

- `ValidationWalker<N> implements GraphWalker<N, N>`; sealed `Check` with `OnNode(name, kind, predicate, rule)` and
  `OnClose(name, rule over the selected nodes in visit order)`; `Phase(name, checks)`.
- `run(Traversal)`: per phase, fresh buckets (one per check), one traversal, `OnClose` after it, buckets joined in
  check order; stop after a phase whose ledger has a reason of `Severity.VIOLATION`.
- `GraphCheck(Walk, phases).run(graph, scope)`; `Findings(List<ClosureFault>)` with `rationale()` and `violated()`.
- Red first, `ValidationWalkerTest`: 2 subjects x 2 rules give `r1s1, r1s2, r2s1, r2s2`; a WARNING does not gate, a
  VIOLATION does; one traversal per phase; an `OnClose` bucket sits in its declared place; a `PackNode` rule never sees
  an `EdgeNode`; `rationale()` keeps the order.

### T013 Move the three rules

- `git mv` `FallbackProfileRule`, `RolePinRule`, `ProfileEnhancementRule` to `packs.domain.rules`; `ClosureWalk.Profiles`
  becomes `ProfileReader`.
- Split `RolePinRule` into the pack's own pins (per node) and the pin conflicts (whole closure), keeping today's order:
  own pins first, then conflicts.

### T014 Factory: ownClosure and references

- `PackRulesets.ownClosure(ClosureFacts, ProfileReader, Optional<ProfileEnhancementGateway>)` returns a
  `GraphCheck(PACKS, one phase)`: `fallback_profile` (when the descriptor names one), `role_pins`,
  `role_pin_conflicts`, `profile_enhancements`. `PackRulesets.ownClosureOf(declaring)` gives the scope selecting the
  declaring `PackNode`.
- `references(closure, members, factsOf)` keeps its signature (callers `OfferingReadService`, `ReferencesPhase`) and
  becomes three one-check phases: delegations, references, enhancement targets. Delete the hand-written loop.
- `OwnClosureRulesTest` (moved from `OwnClosureWalkTest`, cases unchanged): fallback first then pins; codes and
  origins; no fallback, no fallback rule; enhancements present and no merge throws.

### T015 Delete the port; rewire ClosureImpactCheck

- Delete `ClosureWalk` and `OwnClosureWalk`; `ClosureImpactCheck` loses the field and the constructor parameter, builds
  one `PackGraph` per judge and per census, and calls `PackRulesets.ownClosure(..).run(graph, scope)` per single-root
  closure (closure-major order kept). Preset items stay reported beside the breaches, ungated.
- Tests that build the check: `PackUninstallServiceTest`, `QualifierLoadTest`, `ClosureImpactCheckTest`.
- Red first in `ClosureImpactCheckTest`: two single-root closures with mixed breaches keep closure-major order; a
  preset item beside a breach is reported; the census keeps " (packs ...)".

### T016 Docs

- Architecture page (Proposed): built notes for slice 3; the determinism guard amended to "phase order, then rule
  order, then the graph's visit order (packs in the caller's merge order, a pack's edges in declared order); a
  whole-closure rule orders its own findings"; note `Selected` rules return `ClosureFault`s until slice 7.
- Changelog fragment `2026-11-25-own-closure-rules-from-the-factory.md` (update the parked draft's).

## Definition of Done

- No `ClosureWalk` type; `grep -rn "OwnClosureWalk\|ClosureWalk\b" service` finds nothing but history docs.
- `:packs:test :pack-validation:test :pack-doctor:test` green, then `verifyAll` and the docs checks.
- NFR-003: run `PackRegistrationScaleTest` on `main` and on the branch and report both times in the PR (report-only,
  ADR-0057).

## Risks

- Order: closure-major on the write path; rule-first inside a phase; preset items ungated (squad S8).
- The own-closure rules read the merge through `winnerPackOf` (#667): keep today's catch in `walkFaults`; WP05 fixes
  the leak.

Implementation command: `spec-kitty agent action implement WP03 --agent claude --mission adr-0082-graph-walk-slices-01M8A867`
