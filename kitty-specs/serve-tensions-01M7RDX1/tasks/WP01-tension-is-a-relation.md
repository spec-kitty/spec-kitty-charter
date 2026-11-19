---
work_package_id: WP01
title: Tension is a relation of the model
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-011
- C-001
planning_base_branch: feat/602-tensions
merge_target_branch: feat/602-tensions
branch_strategy: Planning artifacts for this mission were generated on feat/602-tensions. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/602-tensions unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-serve-tensions-01M7RDX1
base_commit: 600f682a270241542a1cf6f7caae63fcbb445b4f
created_at: '2026-11-19T06:47:32.630610+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
- T007
- T008
agent: claude
history:
- at: '2026-11-19T07:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/application/TensionIsNotWalkedTest.java
- service/packs/src/test/resources/load-fixtures/shard-in-tension-with-is-unknown/files/tactic.graph.yaml
- service/packs/src/test/resources/load-fixtures/shard-in-tension-with-is-unknown/expected.json
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RelationType.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/EndpointTable.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/EndpointRule.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Relationship.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/DoctrineGraph.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/NodeKind.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Tension.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/TensionTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/DoctrineGraphTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/EndpointRuleTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/RelationshipTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/RelationTypeTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/GraphBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/RelationshipEndpointRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackEdgeRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GraphShardWriter.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RelationshipShard.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTrip.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CompositionFactsBuilder.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/KindTaxonomyReadService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/GraphBindingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/RelationshipEndpointRuleTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GraphShardWriterTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/RelationshipShardRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GraphDocumentReaderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/VocabularyEqualityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackEdgeAdapterTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/EndpointTableAdrTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/WireTokenParityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/TensionIsNotWalkedTest.java
- service/packs/src/test/resources/load-fixtures/shard-tension-binds-as-tension/**
- service/packs/src/test/resources/load-fixtures/shard-in-tension-with-is-unknown/**
- contracts/packs/schemas/graph-shard.schema.json
- contracts/packs/schemas/org-fragment.schema.json
- contracts/charter/schemas/Relation.yaml
- contracts/charter/schemas/TaxonomyRelation.yaml
- contracts/charter/schemas/RelationRow.yaml
- contracts/charter/schemas/KindTaxonomy.yaml
- contracts/charter/paths/offering_kinds.yaml
- contracts/charter/examples/KindTaxonomy.model.yaml
- contracts/charter/scenarios/offering.feature
- contracts/charter/scenarios/effective-references.feature
role: implementer
tags: []
tracker_refs:
- '#602'
---

# Work Package Prompt: WP01 - Tension is a relation of the model

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
- Start with `spec-kitty agent action implement WP01 --agent claude --mission serve-tensions-01M7RDX1`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names. Conventional commit subjects naming
  `(#602)`.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time. Before `for_review`, run the module tests
  you touched and `./gradlew verifyAll --max-workers=1` when the WP changes the contract.
- An edit outside `owned_files` is allowed only when small and recorded in the WP's activity log with a one-line
  reason.


## Objective

Make a tension a relationship of the one catalogue: `RelationType.TENSION`, token `tension`, bidirectional, `G ↔ G`,
with identity "unordered pair plus pair class". Remove the kernel `Tension` value, the `in_tension_with` spelling and
every special path built for them (ADR-0045 decisions 1, 2, 7 and 13; spec FR-001 to FR-003, FR-011; plan IC-01 and
IC-04 and the Revisions section). This WP is atomic: the kernel change breaks the packs module's compilation, so the
whole switch lands here, and every module compiles and passes its tests at the end.

Out of scope here: the pack consistency rules (WP02), the registration and removal behaviour (WP03), the projection of
tensions in force and every read beyond what keeps today's answers valid (WP04 to WP06).

## Context (from the surveys, re-check each line)

- `Tension.java` (record, `TOKEN = "in_tension_with"`, BIDIRECTIONAL) is used by `EndpointTable` (a separate tension
  set, `tensionAllows`, `tensionPairs`), `EndpointRule.bindTension` (:131-136), `DoctrineGraph` (tensions list,
  3-arg constructor, merge, equals/hash), `GraphBinding` (:49 `TENSION`, :97-99 `registeredGraph`, :105, :121-123 the
  registered-shard finding, :127, :203-205, :228-237, :245), `RelationshipEndpointRule` (:32-33, :88, :160-161),
  `KindTaxonomyReadService` (:121-141 builds an extra row), `GraphShardWriter` (:106-121 writes tensions),
  `RelationshipShard` (docs), `DomainRoundTrip.sameGraph` (:832-845 compares tensions).
- Tensions are dropped today in `CompositionFactsBuilder.typedEdge` (:289-310), `WholePackStore.salvageEdge`
  (:534-560) and `DomainRepositoryService.edges` (:433-436, an explicit `continue`).
- `EndpointTable.of` (:47-73) is the only exhaustive switch over `RelationType`; `EndpointTable` (:39) and
  `KindTaxonomyReadService` (:126) iterate `RelationType.values()`.
- Every walk uses an explicit relation set: `CascadeRules.CASCADE_RELATIONS`, `EffectiveSet.HARD_RELATIONS`,
  `ActionContextWalk.Step` (and its root set at :323), `GovernanceWalk.Step`, `ReachRequest`.
- `RelationEnum.fromValue(relation.token())` is called in `EffectiveReadMapper` (:166), `EffectiveProfileDtos`
  (:148, :188), `ProfileDtos` (:123), `EffectiveBodies` (:233), `ArtifactController` (:142). With `tension` in
  `Relation.yaml` the generated enum accepts it.

## Subtasks

### T001: The relation and its endpoints

1. Add `TENSION(Directionality.BIDIRECTIONAL)` to `RelationType`, after `REJECTS` and before `IMPLEMENTED_BY`.
   Javadoc: the authored tension between two rules that pull against each other (ADR-0028, ADR-0045); a pair class of
   its own (see `Relationship`); never walked.
2. Rewrite the enum's class javadoc: a tension is a relation of the catalogue (no longer "a `Tension`").
3. `EndpointTable.of`: `case TENSION -> product(GUIDANCE, GUIDANCE)`. Remove the separate tension set and its
   accessors (`tensionAllows`, `tension()`, `tensionPairs()`).
4. `EndpointRule`: remove `bindTension`; `bind(TENSION, …)` covers it. Fix callers.
5. `NodeKind.edgeTargetOf(Optional<RelationType>)`: callers pass `Optional.of(TENSION)`; remove the "empty means
   tension" branch if it exists only for tensions.
6. `RelationTypeTest`: the token is `tension`, directionality BIDIRECTIONAL, not derived, endpoints the GUIDANCE
   product.

### T002: Pair-class identity

1. `Relationship`:
   - Add `public enum PairClass { STRUCTURAL, TENSION }` and `pairClass()` (TENSION only for `RelationType.TENSION`).
   - `equals`/`hashCode`: the unordered bare pair **and** the pair class. Keep the hash order-independent.
   - `public boolean samePair(Relationship other)`: the unordered bare pair only. `public boolean sameSlot(Relationship
     other)`: `equals`.
   - Factory `tension(KindRef<? extends Guidance> from, KindRef<? extends Guidance> to)`, following the naming
     convention `EndpointTableAgreementTest` checks.
   - The self-loop guard: a tension whose two sides have the same **bare** URN is refused, even when one side is
     pack-qualified (ADR-0045 decision 1). Structural relationships keep today's rule.
   - Rewrite the class javadoc (:26-36) for the pair class (ADR-0027 as amended by ADR-0045).
2. `DoctrineGraph`: remove the tensions list, the 3-arg constructor and `tensions()`. `merge` upserts on the slot
   (first declaration wins, ADR-0045 decision 7), instead of concatenating.
3. Delete `Tension.java` and `TensionTest.java`.
4. `RelationshipTest` (create cases if missing): a `requires` and a `tension` on the same pair are not equal and both
   survive in a `Set`; two tensions in opposite orientation are equal; `samePair` is true for both; equal objects have
   equal hashes; the qualified self-tension is refused while a qualified structural self-edge keeps today's rule.
   These cases feed the kernel PIT floor (75).
5. `DoctrineGraphTest`: merge keeps a `requires` and a tension on one pair; a second tension on the pair keeps the
   first.

### T003: Binding

1. `GraphBinding`: drop `TENSION`, the `tensions` list and the `case Tension` path. A `tension` edge goes through
   `RelationType.fromToken` like any relation and binds with `EndpointRule.bind`.
2. Remove the registered-shard finding (:121-123): `registered.graph.yaml` may hold tensions (ADR-0045 decision 5).
   Update `registeredGraph`'s javadoc.
3. `RelationshipEndpointRule`: remove the `TENSION` special case.
4. `GraphBindingTest`: replace the in_tension_with cases with `tension`; the registered-shard tension now binds; a
   tension between a directive and an agent profile is refused with `relationship_endpoint_not_allowed` (positive
   control: directive–tactic binds).

### T004: Storage

1. `GraphShardWriter`: delete the tension loop; `RelationshipMapper.toDso` spells `tension`.
2. `RelationshipShard`: javadoc only (a tension is a relationship like any other).
3. `DomainRoundTrip.sameGraph`: delete the tensions comparison. `relationshipFields` also compares `fromPack` and
   `toPack` (the charter forbids leaving a field out).
4. `DomainRepositoryService.edges`: remove the `continue` that skips tensions.
5. `CompositionFactsBuilder.typedEdge` and `WholePackStore.salvageEdge`: nothing to special-case once the token
   resolves; check that their javadocs and the `EdgeKey` comment describe the pair class.
6. `PackEdgeRepository`: javadoc (a tension is a relationship; it is no longer left out).

### T005: The wire tokens

1. `contracts/packs/schemas/graph-shard.schema.json` and `org-fragment.schema.json`: the `Relation` enum replaces
   `in_tension_with` with `tension`; rewrite the description's tension paragraph for the charter's model (stored
   orientation: the pack owns the source; two owned sides smaller first; ADR-0045 decision 1).
2. `contracts/charter/schemas/Relation.yaml`: add `tension` (after `rejects`); rewrite the description (a tension is a
   relation of its own pair class; `reconciles_tension` points at the sides of a pair joined by a tension).
3. `WireTokenParityTest` (:253-263, :290-296): `Relation` equals `RelationType.values()` minus `implemented_by`;
   `RelationRow` equals `RelationType.values()`. No special tension term remains.

### T006: The kind taxonomy

1. `KindTaxonomyReadService`: delete the extra row (:134-139); the `tension` row comes from `RelationType.values()`.
   `heldAs` is `tension` for `RelationType.TENSION` and `relationship` otherwise; `directed` false.
2. Contract: `TaxonomyRelation.yaml` (the enum and description), `RelationRow.yaml` (description; the `heldAs` x-source
   points at `Relationship.pairClass`, not the removed `Tension.java`), `KindTaxonomy.yaml` (:24),
   `offering_kinds.yaml` (:7), `examples/KindTaxonomy.model.yaml` (the row moves into RelationType order).
3. `offering.feature` (:374, :416-422): the relations list in RelationType order with `tension` after `rejects`; the
   field index of the tension row changes; assert `heldAs: tension` and `directed: false` on it.

### T007: Fixtures and tests

1. `load-fixtures/shard-tension-binds-as-tension`: rewrite with `relation: tension`; `expected.json` has no
   `tensions` member (the tension is one of the relationships).
2. New refusal fixture `shard-in-tension-with-is-unknown`: a shard writing `in_tension_with` is refused as an unknown
   relation (`drg_schema_error`, the same as any unknown token). This is the only place the old token stays.
3. Update `GraphShardWriterTest` (:56), `GraphDocumentReaderTest` (:178), `VocabularyEqualityTest` (:346),
   `PackEdgeAdapterTest`, `EndpointTableAdrTest`, `RelationshipShardRoundTripTest` (:91: the registered tension now
   round-trips), `DomainRoundTripTest` (:377).
4. `git grep -n in_tension_with -- service contracts/charter contracts/packs` lists only the refusal fixture, the
   divergence register (WP08) and `planted/core-verdicts.json` (core's own text). Record the grep in the activity log.

### T008: Not walked; references

1. `TensionIsNotWalkedTest` (packs application): over a closure where `A tension B` and nothing else, the governance
   walk, the action-context walk and the reach from `A` never list `B`; with `A requires B` beside it, `B` is reached
   (positive control).
2. `effective-references.feature`: a scenario where a pack's graph document writes `A tension B` (the pack owns both,
   `A < B`), and `A`'s references list the tension edge with relation `tension`; `B`'s references do not list it.

## Definition of Done

- `Tension`, `bindTension` and the separate tension table are gone; `git grep -n "\bTension\b" service` matches no
  type.
- `./gradlew :kernel:test :packs:test :pack-validation:test --max-workers=1` pass; the offering and references
  scenarios pass; `./gradlew verifyAll --max-workers=1` passes.
- The activity log records the `in_tension_with` grep.

## Risks and reviewer guidance

- `equals` now includes the class: look for any `Set`/`Map`/`contains`/`distinct` over relationships that meant the
  pair. `ArtifactRemovalService:175` is WP03's (it moves to an identity filter there).
- `GraphProjection` now emits tension edges as plain edges until WP04; that answer is schema-valid because `Relation`
  gains `tension` here. No example pack holds a tension yet.
- Reviewers: check the javadocs no longer say "a tension is not a relation".
