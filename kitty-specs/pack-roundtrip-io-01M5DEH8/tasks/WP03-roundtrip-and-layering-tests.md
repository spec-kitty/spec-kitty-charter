---
work_package_id: WP03
title: Round-trip & layering verification
dependencies:
- WP01
- WP02
requirement_refs:
- FR-005
- FR-006
- FR-009
- NFR-001
- NFR-002
- NFR-003
- SC-001
- SC-002
- SC-003
- FR-002
- FR-007
- FR-008
planning_base_branch: feat/pack-roundtrip-io
merge_target_branch: feat/pack-roundtrip-io
branch_strategy: Planning artifacts for this mission were generated on feat/pack-roundtrip-io. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-roundtrip-io unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-roundtrip-io-01M5DEH8
base_commit: 36fbfe9e2ceb41e7b58ae413b2292a773a7be1e0
created_at: '2026-10-20T21:07:05.282329+00:00'
subtasks:
- T013
- T014
- T015
- T016
- T017
- T018
- T019
phase: Phase 3 - Verification
history:
- at: '2026-10-20T20:38:22Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/
create_intent:
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PackElementRoundTripTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PackElementWriteSafetyTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/archsample/DomainWithInfraImport.java
execution_mode: code_change
model: ''
owned_files:
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PackElementRoundTripTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PackElementWriteSafetyTest.java
- service/adapter-out-yaml/src/test/resources/roundtrip-fixtures/**
- service/charter-service/src/test/java/ai/speckitty/charter/service/HexagonalArchitectureTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/archsample/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP03 – Round-trip & layering verification

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the profile in the frontmatter before parsing the rest.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

---

## Objectives & Success Criteria

Prove the semantic round-trip and the enforced layering, **non-vacuously**, through the port. Done when `./gradlew verifyAll` is green and every test below guards a distinct failure mode via the production port (`PackElementRepository`), not a helper or an in-memory tautology.

## Context & Constraints

- Depends on **WP01** (domain + port) and **WP02** (adapter). Read [quickstart.md](../quickstart.md) (the test-to-spec map), [spec.md](../spec.md) FR-005/006/008/009, NFR-001/002/004, SC-001/002/003, and [research.md](../research.md) D4/D7/D8.
- **Test economy (DIRECTIVE_041)**: one focused test per distinct failure mode. Do not add a shape-pin, an inert duplicate, or a second planted control where one proves non-vacuity. Each KEEP is judged at closeout.
- **ATDD posture**: author the round-trip assertions against the port surface; the WP02 adapter makes them green. Exercise the real port, never a lower-level helper.
- Mirror the existing fixture-driven `OpenPacksRoundTripTest` structure, but assert at the **domain-model (semantic)** level — this battery is distinct from that byte-level test (D7); FR-005 is satisfied only here.
- **Two-module span**: this WP owns test files in `adapter-out-yaml/src/test` (round-trip + write-safety) AND `charter-service/src/test` (HexagonalArchitectureTest + archsample). Confirm `charter-service`'s test classpath sees the `charter-domain` port and the `adapter-out-yaml` types so the signature rule and sample compile.

## Branch Strategy
- **Strategy**: (populated by finalize-tasks) · **Planning base**: feat/pack-roundtrip-io · **Merge target**: feat/pack-roundtrip-io

## Subtasks & Detailed Guidance

### Subtask T013 – Proving fixtures  *(parallel-safe)*
- **Purpose**: The minimal fixture set the tests share.
- **Steps**: Under `service/adapter-out-yaml/src/test/resources/roundtrip-fixtures/`:
  - `pack-a/pack.yaml` — valid descriptor with **no undeclared keys** but **carrying `active_charter` and `pack_drg`** (the D8 control). Quote `schema_version: '1.0'` and any coercible scalar.
  - `pack-a/<t1>.tactic.yaml` — tactic with **some optionals present, some absent**, a top-level `references` list with a **repeated id in a fixed order**, and at least one `step` with **nested references**.
  - `pack-a/<t2>.tactic.yaml` — tactic omitting every optional field. (Kept distinct from `<t1>`: it is the minimal all-absent member in T015's equality set, exercising the writer's "omit every absent optional" path that `<t1>`'s mixed presence does not fully drive.)
  - `pack-a/<bad>.tactic.yaml` — deliberately schema-invalid (e.g. empty `steps`).
  - `pack-descriptor-only/pack.yaml` — descriptor, no tactic files.
- **Files**: `roundtrip-fixtures/**`.
- **Notes**: Keep the invalid file in `pack-a` only; the pure round-trip assertion (T015/T016) filters to valid elements.

### Subtask T014 – Fetch-fidelity literal oracle  *(guards reader-drop blindness — M1)*
- **Purpose**: A `fetch==refetch` compare is blind to a field the reader drops on both sides; this asserts fetched values against the **fixture literals**.
- **Steps**: `fetch(pack-a)`; assert the valid tactic models' fields equal the known literals — every step field, each reference's `name/type/id/when`, nested step references, and duplicate order. Assert the descriptor's `activeCharter`/`packDrg` equal the fixture literals.
- **Files**: `PackElementRoundTripTest.java`.

### Subtask T015 – Round-trip equality + write-is-load-bearing  *(FR-005, FR-007, SC-001)*
- **Purpose**: Semantic round-trip, proven non-vacuous and write-load-bearing.
- **Steps**:
  - `fetch → write (**fresh dir**) → refetch`; assert refetch equals the original snapshot's valid elements. (Keep this a genuine fresh-directory write, distinct from the in-place replace below.)
  - **Non-vacuity control (same fixture)**: take a tactic, mutate one field, **write it, refetch**, assert the refetch differs from the unmutated baseline. (Route through the port — NOT `original.equals(original.withX())`, which only tests record `equals`.)
  - **Replace-existing**: write a mutated tactic for an existing id, refetch, assert the mutation is returned (proves the write path is load-bearing — a no-op write + mis-wired reread cannot pass).
  - Assert the descriptor's `active_charter`/`pack_drg` survive the round-trip (D8).
- **Files**: `PackElementRoundTripTest.java`.

### Subtask T016 – Presence/absence + empty-not-null collection  *(FR-006, FR-009)*
- **Purpose**: Assert the FR-006 same-fixture presence/absence contract and the one thing equality is *blind* to — an empty-vs-null collection. **Down-scoped (test-economy):** do NOT re-assert `references` order/duplicates here — T015's round-trip over `<t1>` (which carries the repeated reference id) already uses order- and duplicate-sensitive structural equality (NFR-002), so an explicit order/dup assertion would re-guard the same failure mode. Add a one-line comment pointing to T015 as the order/duplicate guard.
- **Steps**: On the single `<t1>` fixture: after round-trip, assert present optionals stay present with value and absent stay absent (no default injected) — the FR-006 same-fixture pairing. Assert `pack-descriptor-only` round-trips with an **empty (not null)** tactic collection (equality cannot distinguish this because null/absent collapse to one empty state per D4, so it needs its own assertion). Collection membership/order for a populated pack is covered by T015's equality over `pack-a`.
- **Files**: `PackElementRoundTripTest.java`.

### Subtask T017 – Partial-tolerant fetch  *(FR-008)*
- **Purpose**: A bad file neither hides nor crashes the pack.
- **Steps**: `fetch(pack-a)` (which contains the invalid tactic): assert the valid descriptor + valid tactic(s) ARE returned (same-fixture valid positive control) AND the invalid file appears as a `PackElementViolation` (negative), from one `fetch` call.
- **Files**: `PackElementRoundTripTest.java`.

### Subtask T018 – Write safety  *(NFR-004)*
- **Purpose**: Atomicity + containment, proven.
- **Steps**:
  - **Atomicity**: using WP02's failure-injection seam, force a failure between temp-write and rename; assert the target file is **unchanged** and **no stray temp** remains.
  - **Containment**: assert a write whose resolved path escapes the pack root is refused; **same-fixture positive control** — an in-root path is accepted.
- **Files**: `PackElementWriteSafetyTest.java`.

### Subtask T019 – ArchUnit port-signature rule + committed known-bad sample  *(NFR-001, SC-002)*
- **Purpose**: Prove the layering rule is enforced AND able to fail.
- **Steps**:
  - Extend `HexagonalArchitectureTest` with a rule that the `charter-domain` port and its signatures reference only domain/`java`/jspecify types (belt-and-suspenders atop `theCoreIsPlainJava`).
  - Add a **committed known-bad sample** class under `service/charter-service/src/test/java/ai/speckitty/charter/service/archsample/` that intentionally imports an infra type; add a test that points the SAME production rule at that sample's package/class and asserts a violation IS reported. (Not a manual/commented-out toggle — a real assertion that the rule fires.) ⚠️ The control MUST invoke the **identical** production `ArchRule` object (a shared constant/method the production battery also uses) scoped to the sample — **not** a re-expressed/hand-rebuilt copy of the rule. A copied rule would stay green when the production rule is weakened, making the control decorative. Ensure the sample does not red the real domain-purity rule (it lives in a test-only `archsample` package the production rule does not scan; the known-bad test scopes the one shared rule to it explicitly).
- **Files**: `HexagonalArchitectureTest.java`, `archsample/DomainWithInfraImport.java`.
- **Notes**: Document in an inline comment why SC-002 ("100% through the port") is near-vacuous for this vertical (no domain caller reaches storage yet) and that this control is the real guard.

## Test Strategy

This WP **is** the test strategy. Run the targeted battery then the full gate:

```bash
./gradlew :adapter-out-yaml:test --tests '*PackElement*'
./gradlew :charter-service:test --tests '*HexagonalArchitectureTest'
./gradlew verifyAll
```

New test files must satisfy the marker/placement conventions the repo enforces (check neighbouring tests). Compiler diagnostics must pass.

## Risks & Mitigations

- **Vacuous round-trip** → T014's literal oracle + T015's through-the-port control are mandatory; do not reduce T015 to an in-memory `equals()`.
- **Decorative ArchUnit control** → T019 must assert a violation is reported against the sample, not just "a rule exists".
- **Atomicity untestable** → depends on WP02's injection seam; if missing, raise it against WP02 rather than skipping the assertion.
- **Over-testing** → keep to one test per failure mode; the fixtures are shared, not duplicated per assertion.

## Review Guidance

- Confirm every test drives the real `PackElementRepository`.
- Confirm the fetch-fidelity oracle compares against fixture literals (not a refetch).
- Confirm the known-bad sample genuinely makes the rule report a violation, and that the control invokes the **same** `ArchRule` object the production battery uses (not a copied rule).
- Confirm the invalid fixture is excluded from the pure round-trip assertion set.
- Vet each test at closeout against the KEEP/RETIRE rubric — no shape-pins or inert duplicates survive.

## Progress & Status
- `spec-kitty agent tasks add-history WP03 --note "..."` · `spec-kitty agent tasks move-task WP03 --to <status>`
