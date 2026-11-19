---
work_package_id: WP04
title: Tensions in force and the example pack
dependencies:
- WP01
- WP02
requirement_refs:
- FR-006
- FR-012
- NFR-001
- NFR-002
subtasks:
- T019
- T020
- T021
- T022
- T023
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/TensionsInForce.java
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/TensionsInForce.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/GraphProjection.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EdgeIndex.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/TensionsInForceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/GraphProjectionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewTest.java
- service/packs/src/testFixtures/resources/example-packs/tensions/**
- service/packs/src/testFixtures/java/ai/speckitty/charter/packs/examples/ExamplePacks.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ExamplePacksComposeTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ExamplePacksTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDigestBaselineTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ResolvedProfileCorpusTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ManifestMembershipTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionTypeCorpusTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProfileCorpusTest.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/roundtrip/WholePackExpectation.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/roundtrip/WholePackRoundTripTest.java
- docs/plans/charter-example-packs.md
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/packs/ExampleVocabularyReadTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/packs/ExampleVocabularyInForceTest.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/TensionsInForce.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/TensionsInForceTest.java
- service/packs/src/testFixtures/resources/example-packs/tensions/pack.yaml
- service/packs/src/testFixtures/resources/example-packs/tensions/pack-manifest.yaml
- service/packs/src/testFixtures/resources/example-packs/tensions/tensions.graph.yaml
planning_base_branch: feat/602-tensions
merge_target_branch: feat/602-tensions
branch_strategy: Planning artifacts for this mission were generated on feat/602-tensions. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge
  back into feat/602-tensions unless the human explicitly redirects the landing branch.
agent_profile: java-jenny
role: implementer
agent: claude
execution_mode: code_change
history:
- at: '2026-11-19T07:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
tracker_refs:
- '#602'
tags: []
---

# Work Package Prompt: WP04 - Tensions in force and the example pack

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else.

Then read, in `kitty-specs/serve-tensions-01M7RDX1/`: `spec.md`, `plan.md` (its last section, "Revisions after the
plan-point squad and rulings G1 to G3", wins over the concerns above it), `data-model.md`, `research.md`,
`research/squad-plan.md`; and `docs/adr/0045-tension-beside-the-relationships-and-nodes-keep-every-declared-field.md`
(accepted, with rulings G1 to G3). Read `.kittify/charter/charter.md` and `AGENTS.md`.

Charter rules that bind every subtask:

- The charter's model is canonical: no reader of `in_tension_with`, no shim, no deprecation window.
- Tests and examples carry only the canonical shape, except error-handling fixtures. Never exclude a field from a
  parity or round-trip check to make a test pass.
- Tests are held to the production bar: outcome assertions, a positive control beside every refusal, no fake green.

## Branch Strategy

- Planning base and final merge target: `feat/602-tensions` (PR #606 to `main`). The mission branch is
  `kitty/mission-serve-tensions-01M7RDX1`.
- The execution worktree is allocated per computed lane from `lanes.json`.
- Start with `spec-kitty agent action implement WP04 --agent claude --mission serve-tensions-01M7RDX1`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names. Conventional commit subjects naming
  `(#602)`.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time. Before `for_review`, run the module tests
  you touched and `./gradlew verifyAll --max-workers=1` when the WP changes the contract.
- An edit outside `owned_files` is allowed only when small and recorded in the WP's activity log with a one-line
  reason.


## Objective

One projection, `TensionsInForce`, answers the tensions in force under an activation with their reconcilers; the
effective graph takes its tension edges from it; and charter gets one example pack, `tensions`, that holds every case
#602 names (ADR-0045 decision 8, rulings G1 and G2; spec FR-006, FR-012, NFR-001, NFR-002; plan IC-05 and the
Revisions section). The reads themselves (activations module, contract) are WP05 and WP06.

## Context (re-check each line)

- `ClosureView.mergedGraph` (:710-713) → `GraphProjection.of(merged().edges(), closure, effective(states))`
  (:84-106): nodes are the winners in force and held steps; edges are `merged.links()` with both endpoints among the
  nodes; `when`/`reason` dropped at :97; `.distinct()` and sorted (:32-34).
- `EdgeIndex.merged(MergedClosure)` (:199, :215-230) keeps an edge only when its source place wins, and re-points one
  whose target place lost. `links()` (:510-521) yields `Link(source, relation, target, when, reason)` (:491);
  package-private.
- `EdgeIndex.of` (:166-182) reads `PackCompositionFacts.edges`, built by `CompositionFactsBuilder.typedEdges`
  (:257-264): graph documents, derived relationships, the registered shard, in the closure's writer order.
- Example packs: `testFixtures/resources/example-packs/{built-in, org-alt, org-base, project}`, accessed through
  `ExamplePacks` (`LEVELS` :36, `names()` :80). Do not put tensions into `built-in`: 27 scenarios configure it.

## Subtasks

### T019: `TensionsInForce`

In `packs.application` (beside `EdgeIndex`, so it may read the package-private links and places):

- `record TensionInForce(Urn source, Urn target, Optional<String> when, Optional<String> reason, SortedSet<Urn> reconciledBy)`,
  `source < target` on bare URNs.
- `static TensionsInForce of(MergedClosure merged, InForceStates states)`, or the equivalent inputs `ClosureView` holds:
  1. Take each **authored** tension edge with its authoring place (not `EdgeIndex.merged`'s source-place rule).
  2. **Symmetric resolution (G2):** for each side, if the authoring pack holds a version of that side and that version
     loses the layer merge, the tension is absent; otherwise the side resolves to the winning version's bare URN.
  3. Keep it only if both resolved sides are in force.
  4. Canonicalize (smaller bare URN as source); drop a resolved self-pair.
  5. Collapse on the pair: the first in merge order (the closure's writer order, then document order within a pack)
     keeps its `when` and `reason`.
  6. `reconciledBy`: every artifact `R` in force with resolved `reconciles_tension` links to both sides (use the
     merged links for `reconciles_tension`, which are structural and already resolved by `EdgeIndex`). A side
     cannot reconcile its own tension: it would need a `reconciles_tension` edge to itself, which is a self-loop.
- Accessors: `all()` sorted by `(source, target)`; `deliveredTo(Set<Urn> listed)`; `unreconciled()`.

### T020: `ClosureView.tensions(states)` and the graph

1. Public `ClosureView.tensions(InForceStates states)` next to `mergedGraph`, built once per call from the merged
   closure already held (no new pass over the packs: NFR-004).
2. `GraphProjection`: tension edges come only from `TensionsInForce`; the plain edge list excludes `TENSION` links;
   `distinct` stays for the other relations. The projection's `Edge` gains optional `when`, `reason` and
   `reconciledBy` that only a tension edge carries.
3. `GraphProjectionTest`: a closure with a tension in force answers one tension edge with its words; a duplicate in a
   second pack collapses; a `requires` on the same pair is a separate edge with no words.

### T021: The `tensions` example pack

At a writable level (`project` is taken: use `org` with `depends_on` the built-in, or `project` if `LEVELS` allows two
project packs; choose what `ExamplePacks` and `ScenarioPacks.addExamplePack` support, and record the choice). An
`open` policy for directives and tactics, so WP07 can register a relationship into it. Contents (directives unless
noted; keep IDs readable):

| Case | Artifacts | Edges in `tensions.graph.yaml` |
| --- | --- | --- |
| 1 reconciled | `TENSION_REC_A`, `TENSION_REC_B`, tactic `RECONCILE_REC` | `A tension B`; `RECONCILE_REC reconciles_tension A`, `… B` |
| 2 unreconciled | `TENSION_UNREC_A`, `TENSION_UNREC_B`, tactic `RECONCILE_HALF` | `A tension B`; `RECONCILE_HALF reconciles_tension A` only (one side: not a reconciler) |
| 3 one side out | `TENSION_ONE_A`, `TENSION_ONE_B` | `A tension B` (WP07's activation excludes B) |
| 4 reverse order | `TENSION_REG_A`, `TENSION_REG_B` | none (WP07 registers `B → A`) |
| 5 requires and tension | `TENSION_REQ_A`, `TENSION_REQ_B` | `A tension B`; `B requires A` |

Every edge canonical (WP02 must accept the pack). Write a `README.md` beside it naming the cases, a `pack.yaml`, a
generated `pack-manifest.yaml`, and a row in `docs/plans/charter-example-packs.md`.

### T022: Enumeration tests and the round trip

Add the pack to `ExamplePacks.names()` and `LEVELS`; update the expectations of every test that enumerates the
example packs (do not narrow them): `WholePackRoundTripTest` (:141-142), `WholePackExpectation`,
`ExamplePacksComposeTest` (:112), `ExamplePacksTest` (:90), `PackDigestBaselineTest` (:76), `ProfileCorpusTest`,
`ResolvedProfileCorpusTest`, `ManifestMembershipTest`, `ClosureViewTest` (:688), `ExampleVocabulary*Test`,
`MissionTypeCorpusTest` (:251). The round trip over the new pack passes (NFR-002).

### T023: Unit tests (`TensionsInForceTest`)

- Override of the **smaller** side's version in the authoring pack → absent; of the **larger** side's → absent (G2
  symmetry; both directions in one parameterized test).
- A tension authored in pack C on two sides C does not hold, one side overridden elsewhere → re-pointed, present.
- Permutations: shuffle link order and pack file order (at least 5 seeds); the answer is identical (NFR-001).
- Collapse across two packs with different words: the first writer's words win.
- A reconciler with one edge is not listed; a reconciler not in force is not listed; two reconcilers sorted.
- Only one side in force → absent. A resolved self-pair → dropped.
These tests feed the packs `domain+application` PIT floor (85).

## Definition of Done

`./gradlew :packs:test :pack-validation:test :pack-validation:wholePackRoundTrip --max-workers=1` pass, and
`:deployable:test` passes (corpus tests).

## Risks and reviewer guidance

- Do not reuse `EdgeIndex.merged`'s source-place rule for tensions; it is the asymmetry the squad found (D1, T3).
- Reviewers: check the example pack loads clean under WP02's rule and each case's edges are as tabled.
