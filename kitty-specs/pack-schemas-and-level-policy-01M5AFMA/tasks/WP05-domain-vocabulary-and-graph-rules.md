---
work_package_id: WP05
title: Domain vocabulary and graph rules
dependencies:
- WP01
requirement_refs:
- FR-003
- FR-008
- SC-004
planning_base_branch: feat/pack-schemas-and-level-policy
merge_target_branch: feat/pack-schemas-and-level-policy
branch_strategy: Planning artifacts for this mission were generated on feat/pack-schemas-and-level-policy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-schemas-and-level-policy unless the human explicitly redirects the landing branch.
subtasks:
- T020
- T021
- T022
- T023
history:
- at: '2026-10-19T18:10:00Z'
  event: created
  by: /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/charter-domain/
create_intent:
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyTest.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackPolicy.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/NodeKindTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyVocabularyTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyGraphTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyOrderTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackPolicy.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/NodeKind.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/NodeKindTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyVocabularyTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyGraphTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyOrderTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP05 — Domain vocabulary and graph rules

## ⚡ Do This First: Load Agent Profile

Before reading anything else, load your assigned agent profile with the `/spk-charter-profile-load`
skill (profile: `java-jenny`). Then load governance context: `spec-kitty charter context --action implement`.
The charter wins over this prompt.

## Objective

`PackPolicy` becomes the single owner of every finding text and of graph building (plan:
"One owner", "The detail vocabulary", "Precedence", "Vocabulary kept in sync"). Concretely:

- schema-violation **facts** (`PackContents.SchemaViolation`, built synthetically here; WP04's
  `PackSchemas` produces the real ones) become findings, with today's texts;
- `RawGraph` facts become a `DoctrineGraph`, and the URN binding, the legacy `in_tension_with`
  mapping and the `impacts` rules are collected as findings;
- the order is deterministic;
- one name per node kind (R14) is checked against the bundled schemas.

All of this is pure domain code, testable without YAML. WP07 wires it into the adapter.

Read first:
- [plan.md](../plan.md): the vocabulary table and the precedence rules;
- [contracts/pack-finding-codes.md](../contracts/pack-finding-codes.md): the pinned-text table;
- [data-model.md](../data-model.md): `PackContents` nested types and `PackPolicy`;
- today's texts in `service/adapter-out-yaml/.../GraphDocumentReader.java` (all of it) and
  `PackDescriptorReader.java`.

## Branch Strategy

- Planning base and merge target: `feat/pack-schemas-and-level-policy`.
- Lane worktree from `lanes.json` (same lane as WP01).
- Implement with:
  `spec-kitty agent action implement WP05 --agent claude --mission pack-schemas-and-level-policy-01M5AFMA`.

## Subtasks

### T020 — `NodeKind.fromPlural`

**Steps**:

1. Red: `NodeKindTest` asserts that `fromPlural` maps:
   - each of the 13 `ArtifactKind` plurals to its node kind;
   - `mission_types` to `MISSION_TYPE`;
   - `actions` to `ACTION`;

   and that it returns empty for `mission_steps`, `mission_type`, `anti_pattern` (singular) and
   `""`.
2. Add `static Optional<NodeKind> fromPlural(String)` to `NodeKind`, with Javadoc citing R14:
   one name per concept, with core's aliases refused. It replaces `GraphDocumentReader.pluralKind`;
   WP07 removes that method from the adapter.

### T021 — Detail vocabulary: `SchemaViolation` → text

**Steps**:

1. Red: `PackPolicyVocabularyTest`. Write one test per row of the plan's vocabulary table, each
   with a synthetic `SchemaViolation` and the expected **exact** text. Cover both origin styles:
   - the graph style, `<origin>: …` with the `node` and `edge` prefixes taken from the pointer;
   - the descriptor style, `pack.yaml of <name> …`.

   Include **every** literal from contracts/pack-finding-codes.md "Pinned detail texts":
   - `<o>: unknown key 'surprise'` (`packs.feature:115`);
   - `<o> node: expected a mapping`;
   - `<o>: a node of kind 'directives' has an empty id` (keyword `minLength` on a fragment node
     `id`);
   - `<o>: 'impacts' must be a number`;
   - `pack.yaml of <n> has no <key>`;
   - `pack.yaml of <n>: <key> must be a string`;
   - `pack.yaml of <n> declares an unknown pack_level '<t>'` (keyword `enum` on `pack_level`);
   - `pack.yaml of <n> is not a mapping` (`type` object at the root);
   - `<o>: edge endpoint 'QA_404' is not a URN` (the new R6 text).
2. Implement the mapping in `PackPolicy`:
   - one table keyed on `(keyword, place-class)`, never on networknt's message;
   - the code is chosen by the file kind: `pack_unreadable` for a descriptor, `drg_schema_error`
     for a shard or fragment, `artifact_schema_error` for an artifact, `org_charter_schema_error`
     for an org charter;
   - **`anyOf` collapse**: for a nullable `anyOf[T, null]`, report only the non-null branch's
     error. Detect it from the violation facts (the `anyOf` keyword plus nested `type` violations
     at the same pointer). Document the rule;
   - **fixed fallback** for any other keyword: `<o>: '<pointer>' breaks schema keyword '<keyword>'`.
     It is locale-free.
3. Several violations at the same place, for example `required` and `additionalProperties` at the
   root: today's reader reports the unknown key first (`checkKeys` runs before the field reads).
   Keep that by ordering keywords within a place: `type` first, then `additionalProperties`, then
   `required`, then the rest. Test it.

### T022 — Graph building from `RawGraph`

**Steps**:

1. Red: `PackPolicyGraphTest` (synthetic `RawGraph`s) asserts:
   - **shard nodes** bind to their URN (the `kind` versus prefix check now comes from the schema;
     here only parse with `Urn.parse`);
   - **fragment nodes** bind `NodeKind.fromPlural(kind)` + `id` → `Urn`;
   - **edges** bind both endpoints with `Urn.parse`. Under R6, a non-URN endpoint is already a
     schema violation. If one still reaches here, which must not happen when the schema ran, emit
     `drg_schema_error` "edge endpoint '<t>' is not a URN" instead of throwing;
   - a **legacy `in_tension_with`** edge becomes `impacts` with strength -1. With an `impacts`
     value present, it gives "an in_tension_with edge has no 'impacts'";
   - the **`impacts` rules**, with today's exact texts:
     - "an impacts edge is missing 'impacts'";
     - "a <rel> edge has no 'impacts'";
     - "'impacts' <v> is outside [-1, 1]" for out-of-range, `NaN`, `Infinity` and `-Infinity`.

       Check how today's `GraphDocumentReader` formats NaN (`Double.toString`) and match it.
   - an **unknown relation** gives "unknown relation '<v>'" (normally caught by the schema enum;
     keep the domain check consistent).
2. Implement `DoctrineGraph graph(RawGraph)` plus its findings in `PackPolicy`:
   - return the graph together with the findings, for example as a small result record;
   - an edge with a finding is not added to the graph;
   - `DrgEdge` keeps its constructor check as a programming-error guard, so validate before
     constructing.
3. **Add** the overload `static CharterPack load(PackContents contents)`. It builds the graph from
   `contents.shards()` and `contents.fragment()` (`Read` values) in today's merge order (shards
   sorted, then the fragment), and adds the graph findings to the restricted verdict. **Keep**
   WP01's `load(contents, graph)`: the adapter still calls it until WP07, which switches the
   adapter and deletes the old overload. That way every commit in the lane compiles and stays
   green. `PackPolicyTest` (WP01's) is in your owned files for the overload's tests.

### T023 — Precedence ordering

**Steps**:

1. Red: `PackPolicyOrderTest` asserts:
   - **Group order**: `pack.yaml`, then the shards by name, then the fragment, then the pack-level
     and pack-wide findings, then (validate mode) the other files by path.
   - **Within a file, pointer-segment order**:
     - object keys follow a declared key order per document kind (shard: `schema_version`,
       `generated_at`, `generated_by`, `nodes`, `edges`; fragment: `nodes`, `edges`; node and edge
       keys in schema order);
     - unknown keys sort after the known ones, lexically;
     - array indices compare **numerically**, so `/nodes/2` comes before `/nodes/10`;
     - `nodes` comes before `edges`.
   - **Within a place**: the keyword order from T021.
   - **A multi-defect shard** (an unknown root key, a bad node at index 10, a bad edge at index 0)
     orders as today's reader would report it: the root key first, then the node, then the edge.
   - **A two-fault pack** (a level mismatch and a bad shard): the first blocking finding is the
     shard's, which preserves today's behavior.
2. Implement the comparator in `PackPolicy`. The key orders are a small constant table, with a
   comment citing the schema `properties` order.

### Moved: the vocabulary equality test

The vocabulary equality test (T024) and the schema keyword-coverage walk both read the bundled
schemas, so they now live in **WP07**. That keeps this WP free of the dependency on WP04.

## Definition of Done

- [ ] Every vocabulary row, every pinned literal, every graph rule and every ordering rule has a
      test. Each was red before it went green.
- [ ] `PackPolicy.load` builds the graph from `RawGraph`, and WP01's tests are still green.
- [ ] The `charter-domain` module is still plain Java: ArchUnit is green.
- [ ] Commits carry the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Risks

- **Text drift.** Copy the texts verbatim from today's code, not from memory.
- **NaN formatting.** Match `Double.toString`.

## Reviewer guidance (opus, reviewer-renata)

- Diff every string literal in today's `GraphDocumentReader` and `PackDescriptorReader` against
  the vocabulary tests. Each one is either reproduced or deliberately changed under R6.
- Try to find a keyword in the bundled schemas with no vocabulary row; the fallback must cover it,
  and it must be locale-free.
