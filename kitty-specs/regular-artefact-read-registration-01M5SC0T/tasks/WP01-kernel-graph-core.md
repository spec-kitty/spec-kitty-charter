---
work_package_id: WP01
title: Kernel graph core — Relationship, RelationType, Directionality, GraphWalker, Descent
dependencies: []
requirement_refs:
- FR-001
- FR-002
planning_base_branch: feat/regular-artefacts
merge_target_branch: feat/regular-artefacts
branch_strategy: Planning artifacts for this mission were generated on feat/regular-artefacts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/regular-artefacts unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
- T005
phase: Phase 1 - Kernel
history:
- at: '2026-10-25T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/
create_intent:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Relationship.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RelationType.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Directionality.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/GraphWalker.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Descent.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/RelationshipTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/RelationTypeTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/GraphWalkerContractTest.java
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Relationship.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RelationType.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Directionality.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/GraphWalker.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Descent.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/RelationshipTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/RelationTypeTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/GraphWalkerContractTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 — Kernel graph core

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and
behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Build the pure, framework-free **relationship/graph vocabulary** every later slice depends on, in
the kernel (`service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/`). This is the
foundation: `Relationship` (a first-class value object with unordered-pair identity, FR-001),
`RelationType` (a behaviour-carrying enum whose directionality derives from the type, FR-002),
`Directionality`, and the `GraphWalker`/`Descent` visitor types the lazy walk (WP03) will drive.

Ground truth: [data-model.md](../data-model.md) "Kernel graph core", [spec.md](../spec.md)
FR-001/FR-002, research [D1](../research.md), and **ADR-0025 Decisions 1–2** + **ADR-0027** (bounded
unidirectional collapse). The kernel takes **no external dependency** (ArchUnit-enforced, ADR-0006):
only `java.*`, `org.jspecify.annotations`, and sibling kernel types.

## Context & Constraints

- **House style (memory, non-negotiable)**: a value object is a `record` or a `final` class with
  identity/value equality; many-field types use a **package-private all-args constructor + public
  fluent chainable builder**. `Relationship` is small, so a `record`-like `final` class or `record`
  with a canonical constructor that validates is appropriate — follow the kernel's existing VO shape
  (`Urn`, `DrgEdge`, `PackDescriptor` are records with compact validating constructors). Prefer a
  `record` here.
- `Relationship` must `equals`/`hashCode` on the **unordered** `{from, to}` pair — a `A→B` relationship
  equals a `B→A` relationship — while **storing** the authored `from`/`to` orientation so directed
  types keep their direction (ADR-0027 Decision 1). This is the single most important invariant in
  this WP; it is what makes "at most one relationship per pair" true (FR-001).
- `RelationType` **supersedes** the kernel `Relation` enum conceptually, but **do not delete or edit
  `Relation.java` in this WP** — it is still used by the storage DSO and `PackPolicy`, and its
  retirement/tokenisation is owned by WP02. You only **add** `RelationType`.
- `propagation()` is **deferred** (ADR-0025 remediation / ADR-0027): ship only `directionality()` and
  `token()` on `RelationType`. Do not add a `Propagation` type or a `propagation()` method.
- Kernel purity: no Spring, no snakeyaml, no generated DTOs, no `packs.*` import.

## Branch Strategy

- **Planning base / merge target**: `feat/regular-artefacts`. Work in this WP's lane worktree from
  `lanes.json` (generated by finalize-tasks); do not edit files outside `owned_files`.

```bash
spec-kitty agent action implement WP01 --agent claude --mission regular-artefact-read-registration-01M5SC0T
```

Targeted build: `./gradlew :kernel:test --tests '*RelationshipTest' --tests '*RelationTypeTest' --tests '*GraphWalkerContractTest'`. Do **not** run full heavy suites (NO_FULL_HEAVY_SUITES_IN_MISSION).

## Subtasks & Detailed Guidance

### T001 — `Directionality` enum `[P]`
- Purpose: name the two orientations a relation can carry.
- Create `Directionality` with two constants: `DIRECTED` (holds `from → to` only) and
  `BIDIRECTIONAL` (holds both ways). Javadoc each constant with the semantics above.
- No fields or behaviour beyond the constants (it is derived from `RelationType`, never stored on a
  `Relationship`).
- Validation: a tiny enum; the test in T005 asserts both constants exist and are distinct.

### T002 — `RelationType` behaviour-carrying enum
- Purpose: the **closed catalogue** of DRG relation meanings, each constant declaring its
  `directionality()` and `token()` (FR-002, ADR-0025 Decision 2).
- Mirror the catalogue of the existing `Relation` enum
  (`service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Relation.java`): the same
  constants and the same snake_case tokens (`requires`, `suggests`, `applies`, `scope`, `vocabulary`,
  `instantiates`, `refines`, `specializes_from`, `delegates_to`, `enhances`, `overrides`, `replaces`,
  `impacts`, `reconciles_tension`, `rejects`). **Do not invent new tokens** — the catalogue is closed
  (C-003) and the wire spelling is contractual.
- Each constant holds a `Directionality`. Assign directionality from the relation's meaning: the
  asymmetric relations (`requires`, `suggests`, `applies`, `scope`, `vocabulary`, `instantiates`,
  `refines`, `specializes_from`, `delegates_to`, `enhances`, `overrides`, `replaces`,
  `reconciles_tension`, `rejects`) are `DIRECTED`; `impacts` is the one symmetric/tension relation and
  is `BIDIRECTIONAL`. Add a Javadoc note on `impacts`/`delegates_to` that minting a genuinely
  symmetric relation is the ADR-0027 **revisit trigger** for the identity collapse.
- API: `Directionality directionality()`, `String token()` (lower-case constant name, like
  `Relation.token()`), and a `static Optional<RelationType> fromToken(String token)` (copy the
  `Relation.fromToken` pattern). **No `propagation()`.**
- Keep it pure; constructor takes the `Directionality`.

### T003 — `Relationship` value object (unordered-pair identity)
- Purpose: a first-class relationship between two components (FR-001).
- Fields: `Urn from`, `Urn to`, `RelationType type`, `Optional<String> when`, `Optional<String>
  rationale`. (`when` is the authored edge condition that must round-trip, FR-005; `rationale` is the
  authored reason.)
- Prefer a `record` with a compact validating constructor; but because `equals`/`hashCode` must be
  **overridden** to compare the unordered pair (not the generated component-wise equality), implement
  it as a `final class` with explicit `equals`/`hashCode`, **or** a record that overrides `equals`,
  `hashCode` and keeps accessors. Choose the `final class` with a package-private-free public
  canonical constructor (kernel VOs are public records/classes constructed directly, like `Urn`).
  Document clearly that equality is the unordered pair.
- Invariants in the constructor (throw `IllegalArgumentException`): `from`, `to`, `type` non-null;
  `when`, `rationale` non-null `Optional`s (empty when absent, never null); **`from` must not equal
  `to`** (no self-loop).
- `equals`: true when the other is a `Relationship` and `{from,to} == {other.from,other.to}` as an
  **unordered** set (i.e. `(from.equals(o.from) && to.equals(o.to)) || (from.equals(o.to) &&
  to.equals(o.from))`) — **type/when/rationale are NOT part of identity** (at most one relationship per
  pair regardless of type). `hashCode`: order-independent, e.g. `from.hashCode() + to.hashCode()` or
  `Objects.hash` over a sorted pair, so the two orientations hash equal.
- Add a `boolean orientedFrom(Urn component)` helper or document how WP03 will compute effective
  origin — **optional**; keep the VO minimal. The effective-origin logic (FR-003) lives in WP03's
  `CharterArtifact.relationships()`, not here.
- Edge cases to cover in tests: `A→B` equals `B→A` (and hash equal); a `DIRECTED` relationship still
  reports its stored `from`/`to` orientation via accessors; `from == to` rejected; null optionals
  rejected.

### T004 — `GraphWalker` visitor interface + `Descent` enum
- Purpose: the kernel-side traversal contract the application-layer `GraphWalk` driver (WP03) consults
  (ADR-0025 Decision 4). The kernel defines the **type**; it never fetches.
- `Descent` enum: `CONTINUE` (descend into this node/relationship's neighbours), `SKIP` (do not
  descend past this one, but keep walking siblings), `STOP` (halt the whole walk). Javadoc each.
- `GraphWalker` interface: `Descent onNode(DrgNode node)` and `Descent onRelationship(Relationship
  relationship)`. (`DrgNode` is the existing kernel node type.) Document that the driver calls
  `onNode` when it visits a component and `onRelationship` when it crosses a relationship, and obeys
  the returned `Descent`.
- Keep it a plain interface (no default methods needed); it is pure.

### T005 — Unit tests (test-first, red before green)
- **Author these BEFORE the production types and record both the red and the green commit hashes in
  this WP's history** (DIRECTIVE_034 test-first; the charter mandates specification-by-example first).
- `RelationshipTest`: unordered-pair identity (`A→B` equals `B→A`; equal hashCodes — a `Set<Relationship>`
  de-duplicates the reverse; **paired positive control**: two relationships on genuinely different
  pairs are not equal and both survive in the set); orientation is stored and readable; `from==to`
  rejected; null-optional rejected; a present vs. empty `when` are both representable (round-trip
  precursor for FR-005).
- `RelationTypeTest`: every `Relation` token has a `RelationType` with the **same** token
  (iterate `Relation.values()` and assert `RelationType.fromToken(r.token())` is present — this pins
  the catalogues together and is the non-vacuity control); `impacts` is `BIDIRECTIONAL`, a directed
  example (`requires`) is `DIRECTED`; `fromToken` returns empty for an unknown token; no
  `propagation()` method exists (compile-time: simply do not reference one).
- `GraphWalkerContractTest`: `Descent` has exactly `CONTINUE/SKIP/STOP`; a trivial `GraphWalker`
  implementation (anonymous class or lambda-ish) can be invoked with a `DrgNode` and a `Relationship`
  and returns the `Descent` it chooses (proves the interface is usable and pure).

## Test strategy

Test-first, JUnit Platform + AssertJ (kernel test idiom — see `UrnTest`, `DrgEdgeTest`). Every
invariant gets a failing assertion first. The unordered-pair identity test MUST include a same-fixture
positive control (a distinct pair that is NOT collapsed) so the assertion cannot pass vacuously
(tactic `acceptance-criteria-non-vacuity`, DIRECTIVE_041).

## Definition of Done

- [ ] T001–T005 complete; `Directionality`, `RelationType`, `Relationship`, `GraphWalker`, `Descent`
  exist in `kernel.domain`, pure (no non-`java`/`jspecify`/kernel imports).
- [ ] `Relationship` equality/hashCode is on the **unordered pair**; `from==to` rejected; `when`
  round-trips as a present-or-empty `Optional`.
- [ ] `RelationType` tokens match the `Relation` catalogue one-for-one; `impacts` is `BIDIRECTIONAL`;
  no `propagation()`.
- [ ] `Relation.java` is **unchanged** (owned by WP02).
- [ ] Red-then-green hashes recorded in history. Targeted `:kernel:test` runs green. Kernel ArchUnit
  purity gate still passes.

## Risks

- **Identity bug**: the classic error is leaving the record's generated `equals` (component-wise),
  which makes `A→B ≠ B→A` and silently breaks "one per pair". The test with the reverse-in-a-`Set`
  control guards it — do not skip it.
- **Scope creep**: resist adding `propagation()`, a `Propagation` interface, or reverse-discovery
  behaviour — all explicitly deferred (ADR-0027 Decision 3, ADR-0025).
- **Catalogue drift**: if you add or rename a token, the contract and `PackPolicy` mapping break.
  Mirror `Relation` exactly.

## Reviewer guidance

Confirm the unordered-pair identity (with the reverse-collapse positive control), that directionality
is derived from the type and never stored on `Relationship`, that the `RelationType` catalogue matches
`Relation` token-for-token, that `propagation()` is absent, and that the kernel stays dependency-free.
