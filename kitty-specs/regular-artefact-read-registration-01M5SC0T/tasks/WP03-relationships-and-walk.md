---
work_package_id: WP03
title: CharterArtifact.relationships() migration + ComponentResolver port + GraphWalk driver
dependencies:
- WP01
requirement_refs:
- FR-003
- FR-004
- NFR-001
planning_base_branch: feat/regular-artefacts
merge_target_branch: feat/regular-artefacts
branch_strategy: Planning artifacts for this mission were generated on feat/regular-artefacts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/regular-artefacts unless the human explicitly redirects the landing branch.
subtasks:
- T013
- T014
- T015
- T016
- T017
- T018
phase: Phase 2 - Domain graph
history:
- at: '2026-10-25T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ComponentResolver.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/GraphWalk.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ArtifactRelationshipsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/GraphWalkTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/CharterArtifact.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ArtifactEdges.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Tactic.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Directive.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Styleguide.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Paradigm.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ComponentResolver.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/GraphWalk.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ArtifactRelationshipsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/CharterArtifactFamilyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/DirectiveEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/StyleguideEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ParadigmEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/TacticEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/GraphWalkTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP03 — relationships() migration + ComponentResolver + GraphWalk

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and
behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Migrate the `CharterArtifact` family from `edges()` to **`relationships(): List<Relationship>`**
(the WP01 kernel type), and build the **lazy, repository-backed walk**: a `ComponentResolver` port
(`packs.domain`) that resolves a neighbour URN on demand, and a `GraphWalk` driver
(`packs.application`) that visits a component's relationships, resolving each `to` on demand with a
cycle guard — never materialising the whole graph (FR-003, FR-004, NFR-001).

Ground truth: [data-model.md](../data-model.md) "CharterArtifact family" + "ComponentResolver" +
"Graph walk"; [spec.md](../spec.md) FR-003/FR-004, NFR-001, edge-cases (D6: unwired kinds resolve
empty and the walk continues); **ADR-0025 Decisions 3–4**; research [D6](../research.md).

**Sequencing note (important):** this WP runs **before** WP02 (the `DrgEdge` move). No production code
calls `CharterArtifact.edges()` today (only `PackPolicy`/`GraphShardWriter` use `DoctrineGraph.edges()`
and `RawGraph.edges()`, which are untouched here), so removing `edges()` from the family is safe and
compiles. You MUST NOT touch `DrgEdge`, `DoctrineGraph`, `PackPolicy`, `GraphShardWriter`, or `Pack` —
those are WP02/WP04.

## Context & Constraints

- **`relationships()` effective-origin contract (FR-003)**: a component returns every relationship
  whose `from` is itself, **plus** every `BIDIRECTIONAL` relationship whose `to` is itself. A purely
  `DIRECTED` relationship with the component as `to` is **NOT** returned (a walk does not climb back
  up a `requires`). This is the paired control the no-op-passable column of FR-003 demands.
- The entities today derive edges from their reference fields via `ArtifactEdges.build(...)` →
  `List<DrgEdge>`. You will **replace** that with relationship derivation → `List<Relationship>`.
  Keep the interim relation mapping consistent with ADR-0025's `ArtifactEdges` contract: typed
  references → `RelationType` of token `suggests`; bare references (a directive's `procedures`, a
  paradigm's `directiveRefs`) → `RelationType` of token `requires`. Styleguide and toolguide mint
  **no** relationships (bare citation strings only) → `relationships()` returns `List.of()`.
- `CharterArtifact` loses `edges()` and the `DrgEdge` import; it gains `relationships()`. Keep
  `kind()`, `packId()`, `localId()`, `schemaVersion()`, `label()`, `description()`, default `urn()`.
- `ArtifactEdges` (package-private helper) is **rewritten** to build `Relationship`s (rename its
  method/record as sensible, e.g. keep `TypedRef`, return `List<Relationship>` built from the owner
  `Urn`, the target `Urn`, and the `RelationType`). The `from` is the owner's `urn()`, the `to` is
  `new Urn(targetKind, id)`.
- The entities' stored fields and `ModularRuleset` invariants are **unchanged** — only `edges()` →
  `relationships()` changes. Do not alter identity equality, builders, or DSO mapping.
- `ComponentResolver` is a **port** (functional interface) in `packs.domain`: `Optional<CharterArtifact>
  resolve(Urn urn)`. It returns empty for a kind that is out of scope / unwired / external (D6) — the
  resolver never throws on an unresolvable kind.
- `GraphWalk` lives in `packs.application` (where ports exist, ADR-0025 D4 / C-005). It is pure
  orchestration over the `ComponentResolver` port and a `GraphWalker` visitor, with a **visited-URN
  cycle guard**. It does NOT depend on Spring (it is a plain class the composition root constructs);
  no `@Service` needed here (the production resolver bean is WP10).

## Branch Strategy

- **Planning base / merge target**: `feat/regular-artefacts`. Lane worktree from `lanes.json`.

```bash
spec-kitty agent action implement WP03 --agent claude --mission regular-artefact-read-registration-01M5SC0T
```

Targeted build: `./gradlew :packs:test --tests '*ArtifactRelationshipsTest' --tests '*EntityTest' --tests '*CharterArtifactFamilyTest' --tests '*GraphWalkTest'`. No full heavy suites.

## Subtasks & Detailed Guidance

### T013 — `CharterArtifact.relationships()` on the interface spine
- Replace `List<DrgEdge> edges()` with `List<Relationship> relationships()` on `CharterArtifact`.
- Update the javadoc to the effective-origin contract (above). Remove the `DrgEdge` import; add the
  `Relationship` import (kernel).
- Keep the default `urn()` and all other members.

### T014 — Rewrite `ArtifactEdges` to build `Relationship`s
- Rewrite the helper so `build(Urn owner, List<TypedRef> typed, NodeKind bareTargetKind, List<String>
  bare)` returns `List<Relationship>`: each typed ref → `new Relationship(owner, new Urn(kind,
  ref.id()), RelationType.fromToken("suggests").orElseThrow(), ref.when(), ref.reason())`; each bare id
  → a `requires` relationship `owner → new Urn(bareTargetKind, id)` with empty when/rationale.
- Keep `TypedRef` as-is. Keep the typed-first-then-bare order.
- Consider renaming to `ArtifactRelationships` for screaming-architecture clarity — **optional**; if
  you rename, update the four entities' imports accordingly and keep the file single-owned (it is this
  WP's file). If you rename, update `create_intent`/`owned_files` expectations by keeping the new file
  under this WP only. Simpler: keep the name `ArtifactEdges` to minimise churn.

### T015 — Migrate the four entities' `relationships()`
- For each of `Tactic`, `Directive`, `Paradigm` (which currently implement `edges()` via
  `ArtifactEdges.build`): change the override to `relationships()` returning `List<Relationship>` via
  the rewritten helper. Tactic: typed refs → suggests, bare target kind `DIRECTIVE`, no bare list
  (preserve its current `edges()` mapping shape). Directive: typed refs → suggests, bare
  `procedures` → requires with target kind `PROCEDURE`. Paradigm: typed refs → suggests, bare
  `directiveRefs` → requires with target kind `DIRECTIVE`.
- `Styleguide`: its `edges()` returns an empty list today (bare citations mint none). Change to
  `relationships()` returning `List.of()`.
- Do not change any stored field, invariant, builder, or `equals`/`hashCode`.

### T016 — `ComponentResolver` port (`packs.domain`)
- Create the functional interface: `@FunctionalInterface public interface ComponentResolver {
  Optional<CharterArtifact> resolve(Urn urn); }`.
- Javadoc the D6 contract: dispatches on `urn.kind()` to that kind's read path; returns `Optional.empty()`
  for an out-of-scope / unwired / external kind (procedure, asset, agent_profile, …) so a walk
  continues past it; never throws for an unresolvable kind.
- The port stays in `packs.domain` (it is a domain-owned port; adapters live outside). No
  implementation here — a test double drives the walk tests; the production composite impl is WP10.

### T017 — `GraphWalk` driver (`packs.application`)
- Create `GraphWalk` with a constructor taking a `ComponentResolver`. Method:
  `void walk(CharterArtifact start, GraphWalker visitor)`.
- Algorithm (lazy, bounded — NFR-001): maintain a `visited` set of `Urn`. Visit `start`: call
  `visitor.onNode(start's DrgNode)`. For a node, honour the returned `Descent` — `STOP` halts the whole
  walk, `SKIP` does not descend into this node's relationships, `CONTINUE` descends. For each
  `relationship` in `node.relationships()` (effective origin): call `visitor.onRelationship(rel)`; on
  `CONTINUE`, resolve `rel.to()` via the resolver — if present and not yet visited, recurse; if empty
  (unwired kind, D6) skip it and continue; if already visited, skip (cycle guard). `SKIP` on a
  relationship means do not descend across it; `STOP` halts.
- Build the `DrgNode` for a `CharterArtifact` from its `urn()` + `label()` (see `DrgNode`'s
  constructor); keep it minimal. The walk issues **at most one resolver call per distinct reachable
  component** (the visited-set guarantees this — NFR-001) and never enumerates the pack's full artifact
  set.
- Pure orchestration: no IO, no Spring. The resolver is the only collaborator.

### T018 — Tests (test-first, red before green) + update legacy family tests
- **Author the new tests BEFORE the production changes; record red+green hashes.**
- `ArtifactRelationshipsTest` (new): for a directive with one procedure (bare) and one typed ref,
  `relationships()` yields a `requires` to `procedure:<id>` and a `suggests` to the typed target, in
  that order, with `from == directive urn`; a paradigm's `directiveRefs` → `requires` to
  `directive:<id>`; a styleguide → `List.of()`. **Effective-origin paired control (FR-003)**: build a
  `BIDIRECTIONAL` relationship (type `impacts`) with the component as `to` and assert it IS returned,
  and a `DIRECTED` relationship with the component as `to` and assert it is NOT returned. (Construct
  these `Relationship`s directly for the control; the entity derivations only produce `from==self`.)
- `GraphWalkTest` (new): a stub `ComponentResolver` backed by an in-memory `Map<Urn, CharterArtifact>`
  resolves in-scope neighbours; walking from a start with a chain A→B→C visits A,B,C once each;
  **NFR-001 control**: count resolver invocations == number of distinct reachable components, and a
  component NOT reachable from the start is never resolved (reads strictly fewer than the map holds);
  a cycle A→B→A terminates (cycle guard); a relationship to an **unwired** kind (resolver returns
  empty) does not fail and the walk continues to other neighbours (D6); `Descent.STOP`/`SKIP` are
  honoured.
- Update the legacy tests you own to the new API: `CharterArtifactFamilyTest`, `DirectiveEntityTest`,
  `StyleguideEntityTest`, `ParadigmEntityTest`, `TacticEntityTest` — replace `edges()` assertions with
  `relationships()` assertions (same expected targets, now as `Relationship`s with `RelationType`).
  Keep every other assertion in those tests intact (Boy Scout: do not weaken unrelated coverage).

## Test strategy

Test-first specification-by-example. Each behaviour (effective origin, lazy resolution, cycle guard,
D6 empty-on-unwired, bounded reads) is a scenario with a same-fixture positive control so no assertion
passes vacuously (DIRECTIVE_041). Targeted `:packs:test` only (NO_FULL_HEAVY_SUITES_IN_MISSION).

## Definition of Done

- [ ] `CharterArtifact.relationships()` replaces `edges()`; the four entities + `ArtifactEdges` build
  `Relationship`s; `Styleguide.relationships()` is empty.
- [ ] `ComponentResolver` port + `GraphWalk` driver exist; the walk is lazy, cycle-guarded, continues
  past unwired kinds (empty resolve), and issues ≤1 resolve per distinct reachable component (NFR-001).
- [ ] Effective-origin contract proven with the bidirectional-as-`to` / directed-as-`to` paired
  control (FR-003).
- [ ] No `DrgEdge`, `DoctrineGraph`, `PackPolicy`, `GraphShardWriter`, or `Pack` file touched.
- [ ] Legacy family/entity tests updated to `relationships()`; red+green hashes recorded; targeted
  `:packs:test` green; domain ArchUnit purity gate passes (no infra import from domain/application
  except the application→domain-port direction).

## Risks

- **Climbing back up a `requires`**: if `relationships()` returns directed edges naming the component
  as `to`, the walk explodes and FR-003 breaks. Only bidirectional-as-`to` is included.
- **Unbounded walk**: forgetting the visited-set cycle guard risks infinite recursion and violates
  NFR-001; the cycle + bounded-reads tests guard it.
- **Touching WP02 files**: do not edit `DoctrineGraph`/`DrgEdge`/`PackPolicy`/`GraphShardWriter`;
  leaving `edges()` callers is fine because none exist in production.

## Reviewer guidance

Verify the effective-origin paired control, that the walk resolves on demand (resolver-invocation
count == distinct reachable, with an unreached component never resolved), the cycle guard, D6
empty-on-unwired continuation, and that the domain/application purity boundary holds (GraphWalk in
application depends only on the domain port, not on any adapter).
