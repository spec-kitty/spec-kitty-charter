# Data model: DRG tension edges and node fields

## Kernel (`ai.speckitty.charter.kernel.domain`)

### `Tension` (new, record)

| Field | Type | Rule |
| --- | --- | --- |
| `source` | `Urn` | required; as written |
| `target` | `Urn` | required; not equal to `source` |
| `when` | `Optional<String>` | as written; never `null` |
| `rationale` | `Optional<String>` | the edge's `reason`, as written; never `null` |

- **Identity**: record equality, a directed comparison as written (ADR-0036 D4: the reader never turns
  an edge around).
- **No strength** (ADR-0028 D1).
- It is not a `Relationship`, so it is not part of a pair's one-structural-relationship identity
  (ADR-0028 D3).

### `DrgNode` (changed, record)

| Field | Type | Rule |
| --- | --- | --- |
| `urn` | `Urn` | required |
| `label` | `Optional<String>` | shard `label` / fragment `title` |
| `provenance` | `Optional<String>` | shard nodes only |
| `tags` | `List<String>` | shard nodes only; ordered; immutable copy; empty when absent |
| `bodyPath` | `Optional<String>` | fragment nodes only |

The constructor `DrgNode(urn, label)` is kept and means no provenance, no tags and no body path.

### `DoctrineGraph` (changed)

- **Added**:
  - `tensions(): List<Tension>`, in document order;
  - the constructor `DoctrineGraph(nodes, relationships, tensions)`. The two-argument form means no
    tensions.
- **`merge`**: concatenates the tensions.
- **`equals` and `hashCode`**: include the tensions.

## Packs domain (`PackContents`)

`RawNode(ref, kind, label, provenance, tags, bodyPath)` holds the facts as written. The constructor
with three arguments is kept for callers with no extra fields.

## Mapping

| Document | Read into | Written from |
| --- | --- | --- |
| shard `nodes[].{urn, kind, label, provenance, tags}` | `DrgNode` | `GraphShardWriter.write` (key order: urn, kind, label, provenance, tags) |
| fragment `nodes[].{id, kind, title, body_path}` | `DrgNode` (`Urn(kind, id)`) | `GraphShardWriter.writeFragment` (key order: id, kind, title, body_path) |
| edge `relation: in_tension_with` (+ `when`, `reason`; no `impacts`) | `Tension` | the same edge, `relation: in_tension_with` |
| any other edge | `Relationship` (unchanged) | unchanged |
