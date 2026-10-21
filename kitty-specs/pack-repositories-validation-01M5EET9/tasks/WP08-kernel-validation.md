---
work_package_id: WP08
title: Validation kernel sub-package (pure, gated)
dependencies:
- WP07
requirement_refs:
- FR-006
- FR-014
- NFR-001
- SC-002
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-repositories-validation-01M5EET9
base_commit: f0f50b48146596dfc6cdf5ac75d21401d6e87eb0
created_at: '2026-10-21T15:06:48.297621+00:00'
subtasks:
- T026
- T027
- T028
- T029
- T030
- T031
- T032
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/
create_intent:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/package-info.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/Constraint.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/Constraints.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/Checks.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/ModularRuleset.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/Evaluation.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/Reason.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/Rationale.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/Constrained.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/ConstraintBreachException.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/validation/ModularRulesetTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/validation/ValidationPurityArchTest.java
- service/kernel/src/main/resources/kernel-validation-ATTRIBUTION.md
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/validation/**
- service/kernel/src/test/java/ai/speckitty/charter/kernel/validation/**
- service/kernel/src/main/resources/kernel-validation-ATTRIBUTION.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP08 – Validation kernel sub-package (pure, gated)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Build `ai.speckitty.charter.kernel.validation` — a lean, framework-free (`java.*` + `org.jspecify` only) port of the `modular-validators` core, offering `guard()` fail-fast and `check()` collect-all over `Constraint`/`ModularRuleset`. Gate its purity with a dedicated ArchUnit rule + a committed known-bad control. This is IC-03.

## Context

- The exact keep/drop manifest (fix the description-only `Constraint.equals`; drop commons-lang3; omit `Evidence`, dynamic descriptions, the `dsl` module): [research.md](../research.md) D6; [spec.md](../spec.md) C-001, C-006, FR-006, FR-014, NFR-001.
- The member list: [data-model.md](../data-model.md) `kernel.validation` section.
- The purity-rule wording and the broadened `DOMAIN_IS_PURE`: [occurrence_map.yaml](../occurrence_map.yaml) `archunit_rules_rewrite` (`theCoreIsPlainJava`).
- `kernel.validation` is a **sub-package of the kernel module** (DM 01M5F6RC), not a separate gradle module.

### Subtask T026 — `Constraint` record *(parallel-safe)*
- **Purpose**: A named predicate with a human description.
- **Steps**: `record Constraint<T>(String description, java.util.function.Predicate<T> predicate)` (or equivalent). Provide a value-correct `equals`/`hashCode`: the original `modular-validators` bug compared on **description only** — do NOT reproduce it; a `Constraint` is equal iff description AND predicate identity/semantics match (document that predicates are reference-compared, so equality is primarily identity-based, and the description is not the sole key). Use `java.util.Objects` (NOT commons-lang3).
- **Files**: `Constraint.java`.
- **Validation**: a unit assertion that two constraints with the same description but different predicates are NOT equal (the fixed-defect control).
- **Edge cases**: no `Evidence`, no dynamic description.

### Subtask T027 — `Constraints` / `Checks` factories
- **Purpose**: Ergonomic constraint construction + reusable guard helpers.
- **Steps**: static factories (`Constraints.of(description, predicate)`, common ones like non-null / non-blank using `String.isBlank` not commons-lang3); `Checks` holding the guard helpers the entities will call. Keep the surface minimal — only what WP09's ctor guards need.
- **Files**: `Constraints.java`, `Checks.java`.

### Subtask T028 — `ModularRuleset` with `guard()` + `check()`
- **Purpose**: The dual evaluation strategy (spec US4).
- **Steps**: `ModularRuleset<T>` aggregating `Constraint<T>`s with:
  - `void guard(T value)` — fail-fast: on the first breach throw a **bespoke** `ConstraintBreachException` naming the breached constraint (not a generic `IllegalArgumentException`).
  - `Evaluation check(T value)` — collect-all: accumulate every breach.
- **Files**: `ModularRuleset.java`, `ConstraintBreachException.java`.
- **Validation**: `guard` throws on first breach; `check` reports two breaches for a two-breach value.

### Subtask T029 — `Evaluation` / `Reason` / `Rationale`
- **Purpose**: The collect-all result shape.
- **Steps**: `Evaluation` (PASS/FAIL + ordered `List<Reason>`); `Reason` (a breached constraint + message); `Rationale` (grouping/explanation carrier). Records where value-shaped.
- **Files**: `Evaluation.java`, `Reason.java`, `Rationale.java`.

### Subtask T030 — `Constrained<T>` monadic result
- **Purpose**: A result wrapper carrying either the validated value or the `Evaluation`.
- **Steps**: `Constrained<T>` with map/flatMap-style combinators as the core offers, trimmed to what is used. No `dsl` package.
- **Files**: `Constrained.java`.

### Subtask T031 — Validation-purity ArchUnit rule + known-bad
- **Purpose**: Enforce `..kernel.validation.. → {java, jspecify}` only.
- **Steps**:
  1. `ValidationPurityArchTest` asserting classes in `..kernel.validation..` depend only on `java..` + `org.jspecify..` + own package.
  2. A committed known-bad sample under `...validation/knownbad/` that *would* import a third-party type, proving the rule bites (keep it excluded from the production rule's scope but asserted-against, per the known-bad pattern used in `HexagonalArchitectureTest`).
- **Files**: `ValidationPurityArchTest.java` (+ a `knownbad` sample under the test tree).
- **Validation**: rule green on the real package; known-bad reds.
- **Edge cases**: this is a **separate test file** from WP07's `HexagonalArchitectureTest` (single-owner discipline); WP07 only broadens `DOMAIN_IS_PURE` to admit `..kernel..`.

### Subtask T032 — Attribution + took/changed ledger
- **Purpose**: Apache-2.0 compliance (C-006).
- **Steps**: `kernel-validation-ATTRIBUTION.md` under `kernel` main resources recording the upstream `sddevelopment-be/modular-validators` source, its Apache-2.0 licence, and a "took / changed / omitted" ledger (took: Constraint/Constraints/Checks/ModularRuleset/Evaluation/Reason/Rationale/Constrained; changed: value-correct `Constraint.equals`, dropped commons-lang3; omitted: Evidence, dynamic descriptions, `dsl`).
- **Files**: `kernel-validation-ATTRIBUTION.md`.

## Branch Strategy

- Planning branch `feat/pack-repositories-validation`; final merge target `feat/pack-repositories-validation`.
- Execution worktrees per lane from `lanes.json`. Depends on WP02 (the `kernel` module + validation sub-package home exist). Runs in parallel with the rest of the migration chain.

## Definition of Done

- The validation members exist, framework-free, with `guard()`/`check()` behaving per US4.
- `Constraint.equals` is value-correct (the description-only defect is fixed and controlled).
- The purity rule is green and its known-bad reds; `kernel-validation-ATTRIBUTION.md` records the ledger.
- `./gradlew :kernel:test` green.

## Risks

- **Accidental third-party import** → the purity rule + known-bad close it; keep the surface minimal.
- **Re-introducing the equals defect** → the fixed-defect control test is mandatory.

## Reviewer guidance

- Confirm zero imports outside `java..`/`org.jspecify..`.
- Confirm `guard` throws the bespoke exception on first breach and `check` accumulates all.
- Confirm Evidence/dynamic-descriptions/`dsl` are absent and the ledger is accurate.
