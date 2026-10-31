---
work_package_id: WP02
title: 'Packs: read, bind and write tension edges and node fields; retire both expected-red entries'
dependencies:
- WP01
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-005
- FR-006
- FR-007
- FR-008
- FR-009
- FR-011
- NFR-001
- NFR-002
- NFR-003
- NFR-004
- C-002
- C-004
- C-005
planning_base_branch: issue-235-236-drg-round-trip
merge_target_branch: issue-235-236-drg-round-trip
branch_strategy: Planning artifacts for this mission were generated on issue-235-236-drg-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-235-236-drg-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T006
- T007
- T008
- T009
- T010
- T011
- T012
phase: Phase 2 - The round trip
history:
- at: '2026-10-31T07:10:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GraphShardWriterTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackContents.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GraphDocumentReader.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GraphShardWriter.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RelationshipShard.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RelationshipMapper.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyGraphTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GraphDocumentReaderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GraphShardWriterTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/RelationshipShardRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/VocabularyEqualityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTrip.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTripTest.java
- service/packs/src/test/resources/load-fixtures/shard-legacy-tension-is-impacts-minus-one/**
- service/packs/src/test/resources/load-fixtures/shard-impacts-on-legacy-tension-edge/**
- service/packs/src/test/resources/whole-pack/expected-red.yaml
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 – Packs: read, bind and write tension edges and node fields

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave
according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

On the domain path of a graph document (`GraphDocumentReader` → `PackPolicy.graph` → `GraphShardWriter`):

- an `in_tension_with` edge reads as WP01's `Tension` and is written back as written (#235);
- node `provenance`, ordered `tags` and fragment `body_path` survive (#236);
- the whole-pack gate's ratchet then demands removing both expected-red entries.

## Context

- **Squad findings folded here**: S1–S3, S5, S9–S15, S20 (see `research.md`).
- **Key orders**: shard nodes `urn, kind, label?, provenance?, tags?`, written only when non-empty, as in
  `GraphDocumentYaml`. Fragment nodes `id, kind, title?, body_path?`. Fragment edges `source, target,
  relation, impacts?, reason?`, with no `when` (org-fragment schema) and no root header.
- **Out of scope**:
  - the `impacts` relation keeps today's rules;
  - #194 PR-B's `kinds: other:missions` entry and `PackPolicy` ~L560-580 stay untouched.

## Branch Strategy

Planning base and merge target are both `issue-235-236-drg-round-trip`. The mission is
`single_branch`: the execution workspace is the lane `lanes.json` names, and completed work merges
back into `issue-235-236-drg-round-trip`.

Implement with:
`spec-kitty agent action implement WP02 --agent claude --mission drg-tension-and-node-fields-01M68A0T`

## Subtasks

### T006 — Reader and `RawNode` carry the node fields

- **Red first** (`GraphDocumentReaderTest`):
  - a shard node with `provenance: p` and `tags: [b, a]` reads with both, in order;
  - a fragment node with `body_path: x.md` reads with it;
  - absent fields read empty.
- **Then**:
  - `RawNode(ref, kind, label, provenance, tags, bodyPath)`, keeping a documented three-argument
    constructor;
  - in `GraphDocumentReader.raw`, extract `shardNode(Map)` and `fragmentNode(Map)` helpers (S15);
  - read `tags` with `YamlFields.strings`, as `GraphDocumentYaml` does.
- **Javadoc**: correct the class javadoc's "legacy `in_tension_with` mapping" sentence. The word stays
  on the page here; `PackPolicy` binds it to a tension.

### T007 — `PackPolicy`

- **Nodes**: `shardNode` and `fragmentNode` build the five-field `DrgNode`.
- **Tension (S13)**:
  - at the top of `edge`, `if (TENSION.equals(raw.relation())) return tension(raw, origin);`;
  - `tension` checks, in today's order and with today's texts:
    1. `impacts` present → "an in_tension_with edge has no 'impacts'";
    2. endpoint not a URN;
    3. self-loop, checked before `new Tension`;
  - share the endpoint and self-loop checks with the structural path in one helper;
  - remove `LEGACY_TENSION_STRENGTH` and the IMPACTS mapping, and rename `LEGACY_TENSION` to `TENSION`;
  - `graph()` adds a `Tension` outcome to the tensions list and builds the three-argument
    `DoctrineGraph`.
- **Registered shard (S1)**:
  - add `public static GraphBuild registeredGraph(String packName, RawGraph raw)`;
  - it builds as `graph` does, but each `in_tension_with` edge is a `DRG_SCHEMA_ERROR` finding at
    `edges/<i>`: "`<origin>`: a registered relationship is never a tension; in_tension_with is
    authored in a pack's graph shards";
  - the edge is not bound.
- **Javadoc**: correct `graph` and `edge` (S8).
- **Red first** (`PackPolicyGraphTest`, S11):
  - rewrite `aLegacyInTensionWithEdgeBecomesTheImpactsRelation` as
    `anInTensionWithEdgeBindsAsATension`: one `Tension` with from, to, when and rationale; no
    relationship;
  - the impacts-on-tension finding test asserts `tensions()` is empty too, with a valid tension on
    the same fixture that binds (positive control);
  - a tension and a `requires` on the same pair both bind (FR-003);
  - node fields bind (provenance, tags in order, fragment body path);
  - `registeredGraph` reports a tension and binds the same shard without it (FR-011, positive control).

### T008 — `GraphShardWriter`

- **Red first** (new `GraphShardWriterTest`):
  - node fields are written in key order, tags in order;
  - a node without them has none of the keys in the text (S9: assert on the text, same fixture);
  - a tension is written `relation: in_tension_with` with `when` and `reason` and no `impacts`;
  - `write` refuses a node with a `bodyPath` (`IllegalArgumentException`, S5);
  - `writeFragment` writes `id`, plural `kind`, `title`, `body_path` and fragment edges with no root
    header;
  - `writeFragment` refuses a node with provenance or tags, and an edge with `when` (S3);
  - the fragment text passes `GraphDocumentReader.read(..., FRAGMENT, ...)`;
  - the fragment round trip via `MeaningComparator.adr0036().compareTexts` reports nothing, and a
    dropped-`body_path` control reports something (S20).
- **Then**:
  - nodes are written as in `GraphDocumentYaml.write`; tensions after the relationships (edges are a
    set, ADR-0036);
  - add `writeFragment(DoctrineGraph)`, whose javadoc says it proves the fragment round trip and has no
    other production caller yet (S20);
  - the `in_tension_with` spelling goes in one storage constant (or reuses `PackPolicy`'s if it is
    made accessible). Do not spread the literal.

### T009 — `RelationshipShard.read` uses `registeredGraph`

- **Red first** (`RelationshipShardRoundTripTest`):
  - a registered shard holding a tension yields a finding and no relationships;
  - the same shard without the tension binds.
- **Then**: switch `read` to `PackPolicy.registeredGraph`. `CommitStorePackWriteStore` already refuses
  a shard with findings, so the tension is never dropped silently.

### T010 — Gate adapter and production-path fixture

- **Re-bind (S10)**: in `DomainRoundTrip.shard`, after the read-back, rebuild the written shard with
  `PackPolicy.graph`. It is `Refused` unless it has no findings and equal nodes, tensions and
  relationships, with each relationship's `type`, `when` and `rationale` compared pairwise. Don't trust
  `Relationship.equals`.
- **Fixture (S2)**: add a `DomainRoundTripTest` case. Its shard has:
  - a node with `provenance` and `tags: [b, a]`;
  - `A in_tension_with B` with `when` and `reason`;
  - `B requires A`.

  Assert the outcome is `Written` and `MeaningComparator.adr0036().compareTexts("graph-shard", src,
  written)` is empty. Add a control: a mutated written text, with the tag order swapped, is reported.

### T011 — Rewrite what pins the −1 reading

- **`VocabularyEqualityTest`**:
  - rename the test to `theSchemasRelationsAreTheDomainsRelationsPlusExactlyTheTensionSpelling`;
  - fix the class javadoc (L30-31);
  - add an assertion that `PackPolicy.graph` binds that spelling to a `Tension` (S12).
- **Load fixtures**:
  - `shard-legacy-tension-is-impacts-minus-one`: rename it to `shard-tension-binds-as-tension` if no
    reference breaks (grep first). Otherwise keep the directory and fix `expected.json` to the new
    meaning: no `impacts -1.0` line; the tension is recorded as a tension.
  - `shard-impacts-on-legacy-tension-edge`: keep its finding text. Their runner is gone (S14); WP03
    drafts its restoration.

### T012 — The gate and the ratchet

1. Run `./gradlew :packs:wholePackRoundTrip`. It must fail with `UNEXPECTEDLY_GREEN` for exactly
   `directive.graph.yaml` and `anti_pattern.graph.yaml`. Record the output.
2. Remove both `files:` entries from `expected-red.yaml`, and nothing else.
3. Re-run; it must pass.
4. Run `./gradlew :packs:check :packs:openPacksRoundTrip`.

## Definition of Done

- [ ] Every test above was seen red first, then green.
- [ ] `./gradlew :packs:check :packs:wholePackRoundTrip` is green.
- [ ] `expected-red.yaml` lost exactly the two `files:` entries, through the ratchet.
- [ ] No new suppression; no new known-failure or exclusion; complexity ≤ 15 per method.
- [ ] Half-by-half: reverting the reader, the policy or the writer part turns at least one new test red.
      Note which.

## Risks

- `PackPolicy` is 900+ lines; keep the new methods small.
- `DomainRoundTrip` is test source the whole gate depends on: the re-bind must not turn the passing
  shards red.

## Reviewer guidance

- No relationship of type IMPACTS is produced from `in_tension_with`, and no contract file changes.
- Every absent-field assertion has a present-field twin on the same fixture.
