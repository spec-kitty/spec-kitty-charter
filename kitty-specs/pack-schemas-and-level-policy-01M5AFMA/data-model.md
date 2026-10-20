# Data Model: Pack schemas and pack-level policy in the domain

Every type below lives in `charter-domain` (`ai.speckitty.charter.domain`), unless it is marked
as an adapter type. Each one is plain Java, with no annotations (ADR-0006), and is hand-written
(ADR-0017 D5). The plan-point squad's changes are folded in ([research.md R9](research.md#r9-plan-point-squad-dispositions)).

## Inputs: facts only (the adapter decides nothing)

### `PackRoot` (new): the port's parameter

`PackRoot(String name, Optional<PackLevel> configuredLevel, String locator)`.

- **`locator`** is opaque to the domain. The adapter resolves it to a path, so no `java.nio` type
  enters the domain.
- **`configuredLevel`** is empty for a bare-path validation, which is #115 parity with core's
  `validate_pack(path)`.

### `PackDescriptor` (changed): what `pack.yaml` states

| Field | Type | Rule |
|---|---|---|
| `packId` | `String` | Required by the schema |
| `packVersion` | `String` | Required at every level (R4) |
| `parentPack` | `Optional<String>` | |
| `name` | `String` | Required by the schema |
| `declaredLevel` | `Optional<PackLevel>` | Empty when `pack_level` is absent |
| `requiresCore` | `Optional<String>` | Raw text, judged by `PackPolicy` |
| `undeclaredKeys` | `List<String>` | Sorted. Retired fields count as undeclared (R13) |

### `PackContents` (new) and its nested records

`PackContents(PackRoot root, FileRead<PackDescriptor> descriptor, List<FileRead<RawGraph>> shards, FileRead<RawGraph> fragment, List<ClassifiedFile> files, List<StepContractFacts> stepContracts, List<ArtifactFacts> artifacts, List<AssetFacts> assets, List<FileRead.Unreadable<?>> unreadableFiles)`, nine components.
`unreadableFiles` (added by WP08) holds every other classified file that could not be parsed or broke
its schema: an artifact, an org charter, a project charter or a graph document outside the load path.

- **`sealed interface FileRead<T>`** has three cases: `Absent(file)`, `Unreadable(file, ParseFailure | List<SchemaViolation>)` and `Read(file, T)`.
  A rule fires only on `Absent` or `Read`.
- **`SchemaViolation(String file, List<String> pointer, String keyword, Optional<String> property, Optional<String> value, Map<String,String> args)`**
  is networknt's error reduced to facts. No message text crosses the boundary.
- **`ParseFailure(String file, String firstLine)`** keeps only the parser's first line, so no
  host path appears (an existing invariant).
- **`RawGraph(String file, Shape shape, List<RawNode> nodes, List<RawEdge> edges)`**. A `RawNode`
  is `(urn or id, kind, label)` as strings. A `RawEdge` is
  `(source, target, relation, Optional<Double> impacts, when, reason)`.
- **`ClassifiedFile(String file, Optional<String> kind, Optional<String> exclusionClass)`**.
  Exactly one of the two optionals is set.
- **`StepContractFacts(file, List<String> stepIds)`**, **`ArtifactFacts(file, kind, id)`** and
  **`AssetFacts(file, id, path, mime, boolean containedInAssetsRoot)`**. The containment flag is
  computed by the adapter, symlink-aware, as core does.

`LOAD_PATH` scope fills `descriptor`, `shards` and `fragment`. `FULL` scope fills everything.

## Outputs

### `PackFindingCode` (new)

The catalogue is in [contracts/pack-finding-codes.md](contracts/pack-finding-codes.md).

- Each code has a fixed **severity**: `pack_level_missing` and `pack_unknown_key` are warnings,
  and every other code is blocking.
- `refusal()` and `diagnostic()` map a code to its wire value. They throw
  `IllegalStateException` for a validate-only code, which has no wire value.

### `PackFinding` (new)

`PackFinding(PackFindingCode code, String file, List<String> place, String detail)`.

- **`severity()`** comes from the code.
- **`toRefusal()`** and **`toDiagnostic()`** project the finding onto the wire, for the load path.
- **`file`** is relative to the pack root.

### `PackVerdict` (new)

`PackVerdict(List<ClassifiedFile> files, List<PackFinding> findings)`. Findings are in
precedence order (see the plan).

## Domain service: `PackPolicy` (new, pure, the single owner)

| Method | Does |
|---|---|
| `judge(PackContents) → PackVerdict` | Runs every rule, chooses every code, formats every text with the plan's vocabulary table, and orders by precedence |
| `load(PackContents) → CharterPack` | `firstBlocking(judge(c) restricted to loadPath(c))`. Throws its refusal, or builds the `CharterPack` with the restricted warnings as `Diagnostic`s |
| `loadPath(PackContents)` | The one shared restriction: `pack.yaml`, the root shards and `drg/fragment.yaml` |
| `graph(RawGraph) → DoctrineGraph` | URN binding, the legacy `in_tension_with` mapping, and the `impacts` rules (NaN and infinity included), each as a collected finding |

**Rules.**
- the level rules (#48): missing and mismatch;
- `requires_core_invalid`, via `Pep440.isValidSpecifierSet`;
- `pack_unknown_key`;
- `org_fragment_missing`, keyed on the configured level, or on the declared level for a bare path;
- `duplicate_step_id` and `duplicate_artifact_id`;
- `asset_path_escape` and `asset_mime_invalid` (fixed extension table, plan call P8).

**Invariants.**
- **I1.** Only `PackPolicy` (and the enums themselves) accesses the pack-level constants (ArchUnit).
- **I2.** `load` refuses if and only if `judge`, restricted to the load path, has a blocking
  finding. The code and text are the same. The diagnostics equal the restricted warnings. It
  holds by construction, and a parametrized adapter test checks it over planted and open-packs.
- **I3.** The order is deterministic. Findings are grouped by precedence, then sorted by pointer
  segments, with schema property order for keys and numeric order for indices.
- **I4.** No detail names an absolute path.

## Domain service: `PackValidation` (new): FR-010's entry point

`PackValidation(PackInspector inspector)` with `PackVerdict validate(PackRoot root)`, which
returns `PackPolicy.judge(inspector.inspect(root, FULL))`. It is pure and lives in the domain
(plan call P12), and #115 composes it.

`PackPolicy` is a `final` utility class with static methods only. During the chain, `load` has a
transitional `load(contents, graph)` overload. WP07 deletes it (see the plan).

## Port: `PackInspector` (new)

```java
public interface PackInspector {
    enum Scope { LOAD_PATH, FULL }
    /** Reads one pack root as facts; never throws on file content, only on I/O failure. */
    PackContents inspect(PackRoot root, Scope scope);
}
```

## Changed existing types

- **`CharterPack`.** The record shape is unchanged. The canonical constructor keeps an
  `IllegalArgumentException` guard for a declared level that differs from the configured one.
  That guard is a programming-error invariant; the contract refusal comes from `PackPolicy`.
- **`NodeKind`.** Gains `fromPlural(String)`, the one Java owner of the fragment plurals (R14).
  It replaces `GraphDocumentReader.pluralKind`.
- **`Diagnostic.Code`.** Gains `PACK_UNKNOWN_KEY("pack_unknown_key")`.
- **`RefusalCode`.** No new constant. `UNRESOLVED_EDGE_ENDPOINT` is documented as no longer raised.
- **`CharterPackRepository`.** Its Javadoc notes that the file-system implementation composes
  `PackInspector` and `PackPolicy` until #115 moves that composition into the application ring.
- **`Pep440` (new).** `static boolean isValidSpecifierSet(String)`, with the grammar of the
  `packaging` version that core pins (recorded with the test corpus).

## Adapter types (`adapter-out-yaml`)

| Type | Role |
|---|---|
| `PackSchemas` | Strict 2020-12 registry. Allows only bundled IRIs, checks the metaschema, and compiles and caches the schemas |
| `PackFileKind` | Reads the bundled `file-kinds.yaml` and classifies files without validating them |
| `FileSystemPackInspector` | Implements `PackInspector` for both scopes through one reader path |
| `PackDescriptorReader`, `GraphDocumentReader` | Parse, run the schema, and return `FileRead<…>` facts. No codes and no texts |

## Write model: the authored pack files (R14)

`docs/architecture/diagrams/pack-write-model.puml` draws the classes below.

- `Pack` has:
  - an optional `PackDescriptor`;
  - zero or more `GraphShard`s;
  - an optional `OrgFragment`, which org packs require;
  - one `ArtifactFile` per artifact;
  - an optional `OrgCharter`.
- `GraphShard` has nodes (`urn`, `kind`) and edges.
- `OrgFragment` has nodes (`id`, plural `kind`) and edges.
- `Edge` has a source URN, a target URN, a relation, and `impacts` only on an `impacts` edge.
- `AssetManifest` has a path inside `assets/` and a MIME type.

The diagram uses canonical names only: `mission_step_contract`, `action`, `anti_pattern` and
`mission_type`.
