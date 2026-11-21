---
work_package_id: WP01
title: '#614: one closure fault type (slice 1 of #636)'
dependencies: []
requirement_refs:
- FR-001
- NFR-001
- C-001
- C-002
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
- T005
phase: Phase 1 - Foundation
history:
- at: '2026-11-21T13:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/Origin.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/validation/ReasonTest.java
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/Reason.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/Origin.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/validation/ReasonTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClosureFault.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ClosureVerdict.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/CompositionRules.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ClosureFaultTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/CompositionRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackRulesetsClosureTest.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/PackSetVerdict.java
- service/pack-check/src/main/java/ai/speckitty/charter/packcheck/Problems.java
- service/pack-check/src/test/java/ai/speckitty/charter/packcheck/ProblemsTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP01 - #614: one closure fault type (slice 1 of #636)

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md`, ADR-0082, the mission `spec.md` and `plan.md` (the decisions this WP
names), and `research/squad-dispositions.md`.

## Objective

Deliver #614 as **slice 1 of epic #636** (ADR-0082). Re-scoped 2026-11-21:

- a `Reason` records which check produced it (`Origin`);
- the closure checks have one fault type, `ClosureFault`, built through one `ClosureFault.of(...)` factory;
- `CompositionFault` is deleted outright;
- `ClosureVerdict` becomes a view over `Rationale`, or is deleted.

**No behavior change:** the wire codes, the HTTP statuses and the order of findings are exactly as before (plan D1;
spec FR-001).

## Branch Strategy

- Planning branch and merge target: `feat/pack-doctor-coverage` (PR to `main`). Mission topic branch:
  `kitty/mission-pack-doctor-coverage-01M7YMQK`.
- The workspace comes from `lanes.json`. Implement with
  `spec-kitty agent action implement WP01 --agent claude --mission pack-doctor-coverage-01M7YMQK`.
- Commits: no model names and no Co-Authored-By trailer (repository rule). Never push to `main`.

## Context

- `CompositionRules.CompositionFault(ReasonCode, List<RefusalPack>, String)` is the CLOSURE fault today
  (`CompositionRules.java`: `faults`, `presetFaults`, `codeOf`). `PackRulesets.closureFaults` returns it, and
  `PackSetCheck.closure` and `PackSetVerdict` carry it.
- `pack-check`'s `Problems.of` maps it through `ofCharterRefusal` and `statusOf`.
- `ClosureFault` (`packs.domain`, from PR #618) is the fault of every other closure check. It has two factories,
  `of(Refusal)` and `of(CompositionFault)`, the second a bridge.
- `ClosureVerdict` (`packs.domain`) is a third result shape beside the kernel `Rationale`.
- The epic text is in #636 (slice 1) and the issue text in #614. Read both.

## Subtasks

### T001 — `Origin` on `Reason` (red-first)
- Add a kernel value `Origin`, the check that produced a reason. Keep it to `java.*` plus JSpecify
  (`VALIDATION_LIBRARY_IS_PURE`). The minimal shape is a record holding the check's name (a slug, for example
  `closure.compose`, `closure.presets`, `closure.references`). Do not encode a storage location: that is `Location`.
- `Reason` gains the `Origin` component. This replaces its shape outright (C-002): update every constructor call,
  and use a factory where many sites share an origin.
- Write `ReasonTest` cases first: equality covers `Origin`, and `Origin` refuses a blank name.
- Decide where the `Origin` of a non-closure reason comes from (the rule name already carried is a candidate).
  Record the choice in the WP's review notes. ADR-0083 cites it.

### T002 — One `ClosureFault.of(...)`; delete `CompositionFault`
- Replace `ClosureFault.of(Refusal)` and `of(CompositionFault)` with one factory that takes what both carry: the code,
  the detail, the packs and the origin. Keep `of(code, detail, packs, violations, requiredBy)` only if a caller needs
  the long form. One way in, not three.
- `CompositionRules.faults(..)` and `presetFaults(..)` return `List<ClosureFault>`. `codeOf` stays if still needed.
- Delete `CompositionFault` and every import of it.
- Keep `CompositionRules.RULE` and `PRESETS_RULE` as the rule names the reasons carry.

### T003 — `ClosureVerdict`
- Read every caller of `ClosureVerdict`. If it only wraps `List<ClosureFault>` plus notices, make it a view over
  `Rationale`, a thin accessor with no state of its own. If nothing needs it beyond `PackRefusals`, delete it and map
  from the `Rationale` directly.
- `PackRefusals.of(...)` remains the one mapping to `CharterRefusal` (ADR-0082: no second refusal model).

### T004 — Consumers and a deletion guard
- `PackRulesets.closureFaults` and `PackRulesets.closure(..)` emit `ClosureFault`. `PackSetVerdict` and
  `PackSetCheck.closure` carry it. `Problems` maps it through the same refusal mapping as before.
- Note: `PackRulesets.java` and `PackSetCheck.java` are owned by WP03. Edit them here only for the type change, and
  record that as a justified out-of-map edit.
- Add a guard test, red before T002: no class named `CompositionFault` exists (reflection over the packs classes, or
  ArchUnit `noClasses().should().haveSimpleName("CompositionFault")` in `HexagonalArchitectureTest`, which WP07 owns;
  prefer a packs-local test).

### T005 — The pins hold
- Run `CompositionRulesTest`, `PackRulesetsClosureTest`, `ClosureFaultTest`, `PackSetCheckTest`, `ProblemsTest`,
  `PackCheckReportContractTest` and `CompositionGoldenTest`. Call sites change (the type), but **no expected code,
  status, detail or order changes**. If one must, stop and report: that is a behavior change outside this WP.
- Fixtures and contract examples that serialize a reason assert `Origin` where it reaches them. No test excludes a
  field to pass (AGENTS.md).

## Test commands

```bash
./gradlew :kernel:test :packs:test :pack-validation:test :pack-check:test :deployable:test --tests '*Closure*' --tests '*Composition*' --tests '*Problems*' --tests '*PackCheckReport*'
./gradlew :packs:spotlessCheck :pack-validation:spotlessCheck :pack-check:spotlessCheck :kernel:spotlessCheck
```

Then the full module test tasks once before handing over.

## Definition of Done
- [ ] `CompositionFault` no longer exists, and the guard test proves it.
- [ ] One `ClosureFault` factory, and `Reason` has `Origin`.
- [ ] `ClosureVerdict` is a `Rationale` view or deleted.
- [ ] Every pinning test is green with no expected-value edit.
- [ ] Javadoc updated, including `ClosureFault`'s "#614 unifies" note.

## Risks and reviewer guidance
- The order of findings decides the refusal code. A reviewer diffs every expected list in the tests: there must be
  none.
- A reviewer checks that no second factory or legacy reader survives beside the new one.
