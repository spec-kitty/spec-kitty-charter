---
work_package_id: WP02
title: DrgEdge→storage DSO + Relationship↔DrgEdge mapper + DRG-shard relationship persistence
dependencies:
- WP01
- WP03
requirement_refs:
- FR-005
- NFR-003
planning_base_branch: feat/regular-artefacts
merge_target_branch: feat/regular-artefacts
branch_strategy: Planning artifacts were generated on feat/regular-artefacts; completed changes must merge back into feat/regular-artefacts. Worktree per lane from lanes.json.
subtasks:
- T006
- T007
- T008
- T009
- T010
- T011
- T012
phase: Phase 3 - Storage graph seam
agent: claude
history:
- at: '2026-10-25T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DrgEdge.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RelationshipMapper.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RelationshipShard.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/RelationshipMapperTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/RelationshipShardRoundTripTest.java
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/DrgEdge.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/DoctrineGraph.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Relation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DrgEdge.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RelationshipMapper.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RelationshipShard.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GraphShardWriter.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/DoctrineGraphTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/DrgEdgeTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DrgEdgeTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GraphDocumentReaderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyGraphTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/RelationshipMapperTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/RelationshipShardRoundTripTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP02 — DrgEdge-as-DSO + relationship persistence

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and
behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Demote `DrgEdge` from a kernel domain type to a **storage DSO** (`packs.infra.storage`), make
`DoctrineGraph` (kernel) hold the WP01 `Relationship` instead, provide a **`RelationshipMapper`**
(`Relationship` ↔ `DrgEdge` at the YAML boundary), rewrite `GraphShardWriter` to serialise a graph of
`Relationship`s, rewrite `PackPolicy`'s edge binding to produce `Relationship`s, and add a
**`RelationshipShard`** persistence helper that reads a pack's DRG shard into `Relationship`s, upserts
one on its unordered `{from,to}` pair (idempotent, last-write-wins), and serialises back — the whole
round-trip losslessly preserving `when`/`rationale` (FR-005, NFR-003, PROJECT_002).

Ground truth: [data-model.md](../data-model.md) "Storage DSOs"; [spec.md](../spec.md) FR-005, NFR-003;
research [D2](../research.md); **ADR-0025 Decision 5** + **ADR-0027** (bounded collapse, upstream
normalisation, never a read-time rewrite).

**Sequencing:** runs after WP01 (`Relationship`, `RelationType`) and WP03 (the domain family no longer
references `DrgEdge` — `CharterArtifact`/entities now use `relationships()`). This is what lets you
move `DrgEdge` out of the kernel without breaking the domain.

## Context & Constraints (read the facts — they change the shape of this WP)

- **There is no production DRG-shard WRITE path today.** `GraphShardWriter.write(DoctrineGraph)` is
  a `String`-returning serializer exercised **only by `GraphDocumentReaderTest`**. The reader
  (`GraphDocumentReader`) reads shard/fragment YAML into `PackContents.RawGraph`/`RawEdge` (domain raw
  records); `PackPolicy.graph(packName, RawGraph)` binds a `RawGraph` into a `DoctrineGraph`. There is
  no artifact→edge→file writer. You are **building** the relationship persistence seam.
- **Shard shape today**: built-in graph shards are root `*.graph.yaml` (nodes by `urn`); the only
  `drg/` file is `drg/fragment.yaml` (nodes by `id`, plural `kind`). The mission text speaks of a
  `<pack>/drg/*.yaml` relationship shard — **that convention does not exist yet**. Decide the
  registered-relationship shard path with these fixed constraints and record the decision in the WP
  history + a short note in `RelationshipShard`'s javadoc:
  1. It must round-trip losslessly (PROJECT_002) and never rewrite an existing source shard on read
     (ADR-0027 Decision 2 — the charter does not silently drop or re-emit a source's edges).
  2. It must use the canonical shard YAML shape `GraphShardWriter` already emits (`schema_version`,
     `generated_at: STATIC`, `nodes`, `edges` with keys `source,target,relation,impacts?,when?,reason?`).
  3. The `ConsistentRead`/`PackSources.fingerprint()` read-path freshness digest must see the new file
     (it already digests root `*.graph.yaml` shards + `drg/fragment.yaml`; a registered-relationship
     shard must fall inside that digest set — prefer the existing root-`*.graph.yaml` convention or a
     clearly-named `drg/` shard the fingerprint covers; coordinate with the WP04 commit store, which
     performs the actual file placement/atomic swap).
  Recommended: persist registered relationships to a dedicated pack graph shard in the canonical
  `*.graph.yaml` shape (e.g. `registered.graph.yaml`), merged on the pair. Do **not** rewrite the
  built-in shards or `fragment.yaml`.
- **Onion boundary**: after the move, `DrgEdge` lives in `packs.infra.storage`, so `packs.domain`
  (`PackPolicy`) and `kernel` (`DoctrineGraph`) must **not** import it. `DoctrineGraph` holds
  `Relationship` (kernel, allowed); `PackPolicy` produces `Relationship` (kernel, allowed). The
  `Relationship ↔ DrgEdge` conversion lives only in `packs.infra.storage` (`RelationshipMapper`).
- `DrgEdge` the DSO keeps its flat record shape (`source, target, relation(token string), impacts?,
  when?, reason?`) — **record equality is the round-trip assertion** (ADR-0024). The relation is the
  **token string** in the DSO (DSO = persistence mirror), converted to `RelationType` at the mapper
  boundary.
- `impacts` and the legacy `in_tension_with` mapping in `PackPolicy` must be preserved unchanged in
  meaning (`in_tension_with` → `impacts` strength `-1.0`); keep `PackPolicy`'s other responsibilities
  (vocabulary, order, rules) intact — you only change the **edge binding** to produce `Relationship`s.

## Branch Strategy

- **Planning base / merge target**: `feat/regular-artefacts`. Lane worktree from `lanes.json`.

```bash
spec-kitty agent action implement WP02 --agent claude --mission regular-artefact-read-registration-01M5SC0T
```

Targeted build: `./gradlew :kernel:test --tests '*DoctrineGraphTest' --tests '*DrgEdgeTest'` and
`./gradlew :packs:test --tests '*RelationshipMapperTest' --tests '*RelationshipShardRoundTripTest' --tests '*GraphDocumentReaderTest' --tests '*PackPolicy*Test'`. No full heavy suites.

## Subtasks & Detailed Guidance

### T006 — Move `DrgEdge` kernel → `packs.infra.storage` as a flat DSO
- Create `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DrgEdge.java` as a
  package-private (or public if cross-package tests need it — keep it package-private, test is in the
  same package) flat `record`: `DrgEdge(String source, String target, String relation,
  Optional<Double> impacts, Optional<String> when, Optional<String> reason)`. Note `source`/`target`
  are the **URN token strings** and `relation` is the **token string** (persistence mirror). Compact
  ctor: non-null required fields, copy/validate; keep the `impacts`-iff-`impacts`-relation rule if you
  keep `impacts` meaningful here, or relax to a plain mirror and enforce the rule in the mapper —
  document the choice.
- **Delete** `service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/DrgEdge.java`.
- Move `DrgEdgeTest` from `kernel` to `packs.infra.storage` (delete the kernel copy, create the
  storage copy), asserting record-equality round-trip of the DSO (present/empty/absent optionals
  distinct).

### T007 — Rewrite `DoctrineGraph` (kernel) to hold `Relationship`
- Change `DoctrineGraph` to hold `List<Relationship>` instead of `List<DrgEdge>`. Its constructor
  takes `Collection<DrgNode> nodes, Collection<Relationship> relationships`. Keep `nodes()`,
  `node(Urn)`, `contains(Urn)`, `empty()`, `merge(DoctrineGraph)`, value `equals`/`hashCode`.
- Replace the `edges()` accessor with `relationships(): List<Relationship>`. The legacy
  `(source,relation)` outgoing index no longer drives runtime traversal (the WP03 walk does); you may
  drop the `outgoing(...)` index or keep a `Relationship`-typed equivalent only if a current caller
  needs it — check callers and keep the surface minimal. Update `DoctrineGraphTest` accordingly.
- Kernel stays pure (`Relationship`/`RelationType`/`Urn`/`DrgNode` only).

### T008 — `RelationshipMapper` (`packs.infra.storage`)
- Create the mapper: `DrgEdge toDso(Relationship r)` and `Relationship toDomain(DrgEdge e)`.
- `toDso`: `source = r.from().toString()`, `target = r.to().toString()`, `relation = r.type().token()`,
  `when = r.when()`, `reason = r.rationale()`, `impacts` = empty (unless the type is the `impacts`
  relation — then carry the strength per the legacy rule; for this mission relationships carry no
  strength, so empty is correct for the registered-relationship path).
- `toDomain`: parse `source`/`target` via `Urn.parse(...)` (handle a non-URN as a mapper fault →
  surface as a validation problem to the caller, do not silently drop — ADR-0027 no read-time drop),
  `type = RelationType.fromToken(relation).orElseThrow(...)`, carry `when`→`when`, `reason`→`rationale`.
- **Round-trip invariant (FR-005, NFR-003)**: `toDomain(toDso(r)).equals(r)` for any `r`, and the
  `when`/`rationale` text survives present-vs-absent. This is the mapper test's core.

### T009 — Rewrite `GraphShardWriter` to serialise a `Relationship` graph
- `write(DoctrineGraph graph)` now iterates `graph.relationships()`, mapping each to a `DrgEdge` via
  `RelationshipMapper`, then emits the same canonical YAML (`source,target,relation,impacts?,when?,
  reason?`, keys in the established order; `generated_at: STATIC`; BLOCK flow, width 4096,
  `Yaml.schema()`). Nodes emitted unchanged.
- Keep output byte-stable (the round-trip test in `GraphDocumentReaderTest` pins it).

### T010 — Rewrite `PackPolicy` edge binding to produce `Relationship`s
- `PackPolicy.graph(String packName, RawGraph raw)` currently binds each `RawEdge` → `DrgEdge` →
  `DoctrineGraph`. Change it to bind each `RawEdge` → `Relationship` (kernel) → `DoctrineGraph`:
  parse `source`/`target` URNs, resolve the relation token to `RelationType`, carry `when`/`reason`
  (as `when`/`rationale`), apply the legacy `in_tension_with`→`impacts(-1.0)` mapping **unchanged**
  (the resulting `Relationship` has `RelationType` of token `impacts`; the strength lives on the DSO
  mapping side, not on `Relationship` — preserve the existing finding text for an `in_tension_with`
  edge that also carries an explicit `impacts`).
- An unbindable node/edge remains a `PackFinding` and is dropped (do not throw; preserve the current
  fault order and finding messages). Keep `GraphBuild`, vocabulary/order/rules responsibilities and
  the `*_KEYS` constants intact; **do not** import `DrgEdge` (it is now infra) — `PackPolicy` is
  domain and must build `Relationship`s only.
- Update `PackPolicyGraphTest` to the new `relationships()` surface of `DoctrineGraph`; keep every
  other PackPolicy assertion intact.

### T011 — `RelationshipShard` persistence helper (`packs.infra.storage`)
- Create the helper that owns the **read → upsert-on-pair → serialise** cycle for registered
  relationships, operating on YAML **text** (the WP04 commit store does the file IO / atomic swap):
  - `List<Relationship> read(String shardYamlText)` — parse via `GraphDocumentReader.read(text,
    Shape.SHARD, file)` → `RawGraph` → `PackPolicy.graph(...)` or the mapper, into `Relationship`s.
    Never throws on content; a malformed shard surfaces as a refusal to the caller.
  - `String upsert(List<Relationship> existing, Relationship incoming)` or a `Set/Map`-based merge:
    the collection is keyed by the **unordered pair** (use `Relationship`'s equality). Upsert is an
    explicit **last-write-wins put** (remove-then-add, or a `Map<PairKey,Relationship>` put) — NOT a
    `Set.add`, which would keep the first (spec US1 scenario 3). Re-registering the reverse
    orientation replaces rather than adds (edge-case: a directed relationship cannot exist both ways).
  - Serialise the merged set back to canonical shard text via a `DoctrineGraph` + `GraphShardWriter`.
- Keep the nodes of the shard consistent (emit a node per endpoint URN that the shard needs, or carry
  the existing nodes through — follow the canonical shard shape so the read-back is lossless).

### T012 — Tests (test-first, red before green)
- `RelationshipMapperTest` (new): `toDomain(toDso(r)).equals(r)` for a directed and a bidirectional
  relationship, with and without `when`/`rationale` (present-vs-absent distinct — NFR-003); a
  non-URN endpoint surfaces a fault, not a silent drop.
- `RelationshipShardRoundTripTest` (new): writing a set of relationships and reading them back yields
  an equal set (FR-005); upserting the same pair twice leaves exactly one relationship with the
  second's type/when/rationale (last-write-wins, US1 scenario 3); upserting the reverse orientation
  replaces rather than adds; `when` text survives the round-trip.
- Update `DoctrineGraphTest`, `DrgEdgeTest` (now in storage), `GraphDocumentReaderTest`,
  `PackPolicyGraphTest` to the new APIs — keep all existing behavioural assertions (round-trip,
  key-order, findings) green.

## Test strategy

Test-first, record-equality round-trips are the fidelity oracle (ADR-0024). Each round-trip test
carries a present-vs-absent-optional control so fidelity cannot pass vacuously (NFR-003, DIRECTIVE_041).
Targeted `:kernel:test`/`:packs:test` only (NO_FULL_HEAVY_SUITES_IN_MISSION). The existing read path
must not regress (oracle non-divergence, PROJECT_002): keep `GraphDocumentReaderTest` +
`PackPolicy*Test` green.

## Definition of Done

- [ ] `DrgEdge` is a `packs.infra.storage` DSO; the kernel copy is deleted; nothing in `kernel`/
  `packs.domain` imports `DrgEdge`.
- [ ] `DoctrineGraph` holds `Relationship`; `PackPolicy` binds `Relationship`s; `GraphShardWriter`
  serialises via `RelationshipMapper` with byte-stable output.
- [ ] `RelationshipMapper` round-trips losslessly (`when`/`rationale` survive); `RelationshipShard`
  upserts on the unordered pair (last-write-wins, reverse replaces).
- [ ] Registered-relationship shard path/convention decided and documented (round-trip-safe, no
  read-time rewrite, covered by the fingerprint); coordinated with WP04's commit store.
- [ ] Red+green hashes recorded; targeted suites green; the existing DRG read path stays green.

## Risks

- **Onion violation**: importing `DrgEdge` from `PackPolicy` (domain) or `DoctrineGraph` (kernel)
  after the move fails the ArchUnit gate — keep the DSO purely in infra.
- **Silent edge loss on read** (PROJECT_002/ADR-0027): a malformed/unparsable endpoint must surface
  as a refusal, never be dropped to make a shard round-trip "look" clean.
- **Blast radius**: `DoctrineGraph`/`PackPolicy` sit on the READ path; a regression diverges from the
  oracle. Keep the read tests green and change only the edge representation, not the read semantics.
- **Shard path assumption**: the mission's `<pack>/drg/*.yaml` does not exist in code — do not invent
  an unfingerprinted path; honour constraint 3 above.

## Reviewer guidance

Confirm the DSO lives in infra and the domain/kernel no longer import it; the mapper round-trip
(with when/rationale present-vs-absent); the upsert is a true last-write-wins on the unordered pair
(not `Set.add`); the read path is unchanged in semantics; and the chosen shard path is covered by
`PackSources.fingerprint()` and never rewrites a source shard on read.
