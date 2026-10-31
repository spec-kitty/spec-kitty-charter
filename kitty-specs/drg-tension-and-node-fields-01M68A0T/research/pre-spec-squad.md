# Pre-spec research: DRG tension edges and node fields (#235, #236)

Read-only research before the specification. Five lenses: the read/write path, the governing records,
the gates, the test seams, and churn and overlap. Base: `origin/main` at `80dc8825`; open-packs pinned at
`5df92a5d`.

## 1. Read/write path and responsibility map

```text
<kind>.graph.yaml ──GraphDocumentReader.read──▶ RawGraph{RawNode, RawEdge}      (facts as written; decides nothing)
                  ──PackPolicy.graph──────────▶ DoctrineGraph{DrgNode, Relationship} + findings  (binding, rules)
                  ──GraphShardWriter.write────▶ shard text   (via RelationshipMapper → DrgEdge DSO)
```

| Unit | Responsibility | Loss today |
| --- | --- | --- |
| `GraphDocumentReader.raw` (`service/packs/.../infra/storage/GraphDocumentReader.java:63-86`) | Reads a shard or fragment into `RawNode(ref, kind, label)` and `RawEdge(...)` | Drops node `provenance`, `tags` and fragment `body_path` |
| `PackContents.RawNode` (`packs/domain/PackContents.java:238`) | Node facts | No field for them |
| `PackPolicy.graph` / `edge` (`packs/domain/PackPolicy.java:437-532`) | Binds nodes and edges; strength rules | Maps `in_tension_with` → `RelationType.IMPACTS`, strength −1 (`LEGACY_TENSION`, :60-61, :502-515) |
| `DrgNode` (`kernel/domain/DrgNode.java`) | Kernel node: `urn`, `label` | No `provenance`, `tags`, `body_path` |
| `DoctrineGraph` (`kernel/domain/DoctrineGraph.java`) | Nodes by URN plus a list of relationships | No place for a tension |
| `RelationshipMapper.toDso` | `Relationship` → `DrgEdge`; always an empty `impacts` | An `IMPACTS` relationship is written with no strength |
| `GraphShardWriter.write` (`infra/storage/GraphShardWriter.java:42-77`) | Shard text from a `DoctrineGraph` | Writes only `urn`, `kind`, `label` per node; writes the tension as `impacts` |
| `GraphDocument` / `GraphDocumentYaml` | Whole-pack store copy of the documents; **already keeps** `provenance`, `tags`, `body_path` | Reference for key order: `urn, kind, label, provenance, tags` / `id, kind, title, body_path` |

Consumers of `PackPolicy.graph`'s `DoctrineGraph`: the gate adapter (`DomainRoundTrip.shard`, test
source), validation findings (`PackPolicy.validate`), and `RelationshipShard` (the service-written
`registered.graph.yaml`). No HTTP response renders a built-in graph's relationships. Registration
already refuses an `impacts` relationship (`RelationshipMerge.upsert`), so no registered shard holds a
tension written by the service.

## 2. Governing records

- **ADR-0028**
  - D2: "`tension` is the relationship between two rules. It replaces `in_tension_with` and the signed
    `impacts` edge."
  - D3: at most one structural relationship per pair "and, beside it, at most one `tension`". Tension is
    counted apart from the one-per-pair rule.
  - D4: `reconciles_tension` targets pairs joined by `tension`.
  - D5: "Representation is not decided here … No contract path or schema changes with this record."
- **ADR-0036 Decision 4** (L250-262)
  - Every relation is structural except tension.
  - "In today's pack files the tension relationship of ADR-0028 is written `in_tension_with`", with the
    lexicographically smaller URN as `source`.
  - The triple is "compared as written"; the reader does not turn an edge around.
  - The `impacts` relation is the one "which ADR-0028 retires".
- **ADR-0036, nodes and #236**
  - Context (L128-129): "the graph writer drops node `provenance` and `tags` and fragment node `body_path`".
  - Node value (L246-249): every declared field: `kind`, `label`, `provenance`, `tags`; `title`, `body_path`.
  - E10 (L384): "the reader must keep `tags`".
  - E15 (L389): `tags` is a sequence.
  - Follow-up (L442-444).
- **Schemas** (`contracts/packs/schemas/graph-shard.schema.json`)
  - Shard `DRGNode`: `provenance` (string|null) and `tags` (array of string; no `uniqueItems`).
  - Fragment node: `body_path` (string|null).
  - The Relation enum holds both `in_tension_with` and `impacts`.
- **HTTP contract.**
  - `RelationshipRegistration.type` is a free string, checked by `RelationType.fromToken`
    (`RelationshipWire.java:78`).
  - `schemas/Relation.yaml` is a 15-value enum. No path references it, and its description says
    `in_tension_with` "is read as impacts with strength -1".
  - CHANGELOG L559-562: the service "never answers `in_tension_with`".
  - `RelationTypeTest` pins `RelationType` ↔ `Relation` one for one.
- **Divergences.** DIV-018 (`docs/conformance/divergences.yaml:297-312`, register L326-331) records the
  legacy reading as impacts −1.

### The operator-decision trigger

Adding a `RelationType` constant for tension would grow the `Relation` catalogue and the `Relation.yaml`
enum, and `/relationships` would start to accept and answer a new token. That is an HTTP contract
change, the trigger the brief reserves for the operator. A kernel `Tension` value held **beside** the
relationships in `DoctrineGraph` follows ADR-0028 D3, which counts tension apart from pair identity. It
needs no `RelationType`, no `Relation` value and no wire change. It is the route recommended here; the
plan records it as a decision, and an ADR fixes the domain representation that ADR-0028 D5 left open.

## 3. Gates and expected-red entries

- **`:packs:wholePackRoundTrip`** (`service/packs/build.gradle.kts:134-157`)
  - JUnit tag `whole-pack`; not part of `check`, so `verifyAll` does not run it.
  - The comparator is `MeaningComparator.adr0036()`: edges as a set of canonical forms, nodes by URN,
    `tags` as a sequence.
- **The ratchet** (`WholePackExpectation.java:270-385`)
  - `UNEXPECTEDLY_GREEN`: a listed file round-trips; remove its entry.
  - `CHANGED_RED`: a red file's first difference is not the listed `at`.
  - `UNEXPECTED_RED`: an unlisted file is red.
  - `STALE_BASELINE`: a listed path is gone or excluded.
- **Entries**
  - `expected-red.yaml:12-17`: `anti_pattern.graph.yaml` (#236), 13 `nodes{…}.tags`.
  - `expected-red.yaml:18-24`: `directive.graph.yaml` (#235), two tension edges lost and two
    `impacts` edges added.
  - Both leave in this change; the ratchet demands it once they round-trip.
- **Unaffected:** `known-failures.yaml` and `exclusions.yaml` hold nothing on tension, tags or graph
  shards. `planted/core-verdicts.json` has no `in_tension_with` plant.
- **The gate covers shards only.** `DomainRoundTrip` round-trips no org fragment, so `body_path` can be
  proven only by unit tests.
- **Kept green honestly**
  - Remove the entries only when the gate reports `UNEXPECTEDLY_GREEN`.
  - Update `VocabularyEqualityTest` so `in_tension_with` is the written spelling of tension, not a
    legacy alias; the invariant itself does not change.
  - Rewrite the tests that pin the impacts −1 reading (`PackPolicyGraphTest:143,154`).
  - Correct the orphaned load fixtures `shard-legacy-tension-is-impacts-minus-one` and
    `shard-impacts-on-legacy-tension-edge`, whose runner was deleted in `2bb3f762`.
  - Update DIV-018 and the `Relation.yaml` description text so neither claims the −1 reading. This is
    descriptive text only: no enum value, path or wire shape changes.
- **Static gates**
  - Checkstyle `maxWarnings = 0`; there is no complexity module, so the brief's ≤ 15 is held by review.
  - NullAway (JSpecify); javadoc `-Xdoclint:all -Werror`.
  - ArchUnit `KERNEL_IS_PURE`: new kernel types use JDK types only.

## 4. Test seams

- **Unit:** `GraphDocumentReaderTest` (the `read` helper and `HEADER`; :140 already writes and reads a
  graph back). `PackPolicyGraphTest` (`shard/fragment/edge/build` helpers, :24-42).
- **Gate verdict without the pack:** `MeaningComparator.adr0036().compareTexts("graph-shard", source,
  written)`, as `MeaningComparatorTest.GraphShards` uses it.
- **Adapter:** `DomainRoundTripTest` (`put`, `roundTrip`, `written`), with a tension or tagged shard
  under `tactic.graph.yaml`.
- **Kernel:** `DoctrineGraphTest` and `RelationTypeTest`.

Constructor blast radius:

- `new DrgNode(` in `GraphWalk`, `RelationshipShard` and `PackPolicy`, plus the tests.
- `new RawNode(` in `GraphDocumentReader`, plus the tests.

A two-argument convenience constructor on `DrgNode` keeps the callers that have no extra fields
unchanged.

## 5. Churn (90 days) and overlap

- **Stable since 2026-10-25:** the kernel graph files and `GraphDocumentReader` (one to two commits
  each). `GraphShardWriter` has four commits, mostly landing fixes.
- **Churning:** `PackPolicy` (5 commits; #194, #195) and `expected-red.yaml` (10 commits).
- **Branch `issue-194-mission-type-consolidation`** (PR-B; 145 ahead of main, merge base `feeff0a5`)
  - It removes only the `kinds: other:missions` entry from `expected-red.yaml`, a distant hunk, and does
    not touch the `files:` entries.
  - It edits `PackPolicy` unreadable-file findings (~L560-580), away from the graph-building methods
    changed here.
  - It rewrites qa fragment `body_path` values (data only). This mission's fixtures are self-contained
    and depend on no pinned fragment.
  - Whoever lands second rebases. This mission does not touch PR-B's entries.
- **No other branch or commit** works on #235 or #236.

## 6. Environment notes

- `spec-kitty charter context --action <a> --mission-type software-dev` fails here: three dangling
  `refines` edges to `directive:RED_MAIN_RELEASE_DISCIPLINE` in the vendored internal org pack. The
  plain `--action` render works and is used. This is pre-existing and out of scope.
- The baseline `./gradlew verifyAll :packs:wholePackRoundTrip` on `origin/main` runs on Java 25 (Temurin,
  `JAVA_HOME`), with the Maven Central mirror init script restored from
  `docs/development/cloud-session-setup.md`.
