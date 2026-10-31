---
work_package_id: WP01
title: 'Kernel: tension and node fields in the domain model'
dependencies: []
requirement_refs:
- FR-001
- FR-003
- FR-005
- FR-006
- FR-007
- C-001
- C-003
planning_base_branch: issue-235-236-drg-round-trip
merge_target_branch: issue-235-236-drg-round-trip
branch_strategy: Planning artifacts for this mission were generated on issue-235-236-drg-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-235-236-drg-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
- T005
phase: Phase 1 - Foundation
history:
- at: '2026-10-31T07:10:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/
create_intent:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Tension.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/TensionTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/DrgNodeTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/NodeKindPluralTest.java
execution_mode: code_change
model: ''
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/DrgNode.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Tension.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/DoctrineGraph.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/NodeKind.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Relation.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RelationType.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/TensionTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/DrgNodeTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/DoctrineGraphTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/NodeKindPluralTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 – Kernel: tension and node fields in the domain model

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave
according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Give the kernel two capabilities. First, a tension: the ADR-0028 relationship between two rules,
held beside the structural relationships and not counted in their one-per-pair identity. Second, a
node that carries every field ADR-0036 makes part of its value. This is the domain half of #235 and
#236 (epic #186); WP02 wires it to the pack files.

## Context

- **Governing records**:
  - ADR-0028 D1–D5 (tension: authored; no strength; beside one structural relationship);
  - ADR-0036 L246-262 (a node's value is every declared field; `tags` is a sequence, E15; a tension
    is compared as written);
  - decision DM-01M68A2TYC1VMDPCJ8B76602YH; research.md D1–D3 and squad findings S4, S7, S16, S17.
- **Kernel purity** (ArchUnit `KERNEL_IS_PURE`): JDK types only.
- **Gates**:
  - javadoc `-Xdoclint:all -Werror`: every public type, member and record component documented;
  - NullAway (JSpecify);
  - Checkstyle `maxWarnings = 0`;
  - palantir format (`./gradlew spotlessApply`);
  - complexity ≤ 15 per method (held by review);
  - zero new suppressions.

## Branch Strategy

Planning base and merge target are both `issue-235-236-drg-round-trip`. The mission is
`single_branch`: the execution workspace is the lane `lanes.json` names, and completed work merges
back into `issue-235-236-drg-round-trip`.

Implement with:
`spec-kitty agent action implement WP01 --agent claude --mission drg-tension-and-node-fields-01M68A0T`

## Subtasks

### T001 — `DrgNode` gains `provenance`, ordered `tags`, `bodyPath`

- **Red first** (`DrgNodeTest`):
  - the two-argument constructor gives empty provenance, empty tags and empty body path;
  - tags keep their order and duplicates;
  - the tags list is an immutable copy, so a mutation of the source list is not seen;
  - two nodes differing only in tag order are not equal (S4).
- **Then** change the record to `DrgNode(Urn urn, Optional<String> label, Optional<String> provenance,
  List<String> tags, Optional<String> bodyPath)`:
  - the compact constructor does `requireNonNull` on each component and `tags = List.copyOf(tags)`;
  - add a documented convenience constructor `DrgNode(Urn, Optional<String>)` that delegates with
    `Optional.empty(), List.of(), Optional.empty()`.
- **Javadoc**:
  - `@param` for each component in the record javadoc and the compact constructor;
  - `provenance` and `tags` are shard-node fields; `bodyPath` is a fragment-node field;
  - each writer refuses the fields its shape cannot hold (WP02).
- **Callers**: `GraphWalk`, `RelationshipShard`, `PackPolicy` and the tests keep compiling through the
  two-argument constructor.

### T002 — `Tension`

- **Red first** (`TensionTest`):
  - from, to, when and rationale are kept;
  - a self-loop is refused with `IllegalArgumentException`, mirroring `Relationship`'s message style;
  - a `null` argument is refused;
  - equality is the directed comparison as written: `A→B` is not equal to `B→A`, and texts count.
- **Then** add `public record Tension(Urn from, Urn to, Optional<String> when, Optional<String>
  rationale)`. Its javadoc cites:
  - ADR-0028 D2–D3;
  - "no strength";
  - "not a `Relationship`, so neither pair identity nor `RelationshipMerge` applies";
  - that today's pack files write it `in_tension_with` (ADR-0036 D4), a storage spelling the kernel
    does not hold.
- **Naming**: `from`/`to` (S7), as in `Relationship`.

### T003 — `DoctrineGraph` holds tensions

- **Red first** (`DoctrineGraphTest`):
  - `tensions()` keeps document order;
  - the two-argument constructor gives no tensions;
  - `merge` concatenates tensions;
  - two graphs differing only by a tension are not equal and hash differently (S4);
  - `putIfAbsent` keeps the first node whole, tags included (S16; pin the behaviour).
- **Then**:
  - add a `List<Tension> tensions` field (`List.copyOf`);
  - add the three-argument constructor; the two-argument one delegates with `List.of()`;
  - add `tensions()`, include it in `merge`, `equals` and `hashCode`;
  - update the class and `merge` javadoc.

### T004 — `NodeKind.plural()`

- **Red first** (`NodeKindPluralTest`): for every `NodeKind`, `NodeKind.fromPlural(k.plural())` is
  `k`; `ACTION.plural()` is `actions` and `MISSION_TYPE.plural()` is `mission_types`.
- **Then**: `artifactKind().map(ArtifactKind::plural)`, with a switch for `ACTION` and `MISSION_TYPE`.
  Document it as the inverse of `fromPlural`, which WP02's fragment writer uses.
- **Rebase note**: PR-B (#194) moves `mission_types` into `ArtifactKind`. The property test keeps that
  rebase honest.

### T005 — Kernel javadoc

- **`Relation.IMPACTS`**: drop "Replaces `in_tension_with`" and the "negative strength is a tension
  claim" wording. Say ADR-0028 retires it as a relation, and that tension is the `Tension` value.
- **`Relation.RECONCILES_TENSION` and `RelationType.RECONCILES_TENSION`**: "one side of a pair joined by
  a tension (ADR-0028 D4)".
- **`RelationType.IMPACTS`**: drop "tension/reinforcement" and "the one symmetric relation". Keep
  `BIDIRECTIONAL`; changing it is out of scope.
- **Tests**: `RelationTypeTest` stays unchanged, and green.

## Definition of Done

- [ ] Every test above was seen red first, then green: `./gradlew :kernel:test`.
- [ ] `./gradlew :kernel:check` is green: javadoc, Checkstyle, Error Prone/NullAway, Spotless.
- [ ] No new suppression; every new or changed method has complexity ≤ 15.
- [ ] `:packs` still compiles (`./gradlew :packs:compileJava :packs:compileTestJava`).

## Risks

- **`Relationship.equals` ignores `type`**: never prove a relation type through graph equality.
- **Record doclint**: both the record javadoc and the compact constructor need the `@param`s. Follow
  `GraphDocument.ShardNode`.

## Reviewer guidance

- `Tension` must not be a `Relationship`, and no `RelationType` value is added (C-001).
- Check that the equality guards would fail if `equals` ignored tensions or tag order.
