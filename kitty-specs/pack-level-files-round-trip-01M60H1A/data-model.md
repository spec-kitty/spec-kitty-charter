# Data model: Pack-level files round trip

## Domain (`ai.speckitty.charter.packs.domain`)

### `Preset` (value)

| Field | Type | Rule |
|---|---|---|
| `id` | `String` | `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, at most 64 characters |
| `description` | `String` | not blank |
| `activatedKinds` | `List<ArtifactKind>` | kind gate, in authored order, unique; empty = no gate |
| `artifacts` | `Map<ArtifactKind, List<PresetItem>>` | keys only from the nine activatable kinds (directive, tactic, styleguide, toolguide, paradigm, procedure, agent_profile, mission_step_contract, anti_pattern); each list non-empty, in authored order, unique by item id |
| `missionTypes` | `List<PresetItem>` | in authored order, unique by item id; empty = absent |

Derived:

- `required()`: the must-have item ids, per kind and for mission types.
- `governing()`: true when `required()` is not empty.

Equality is by value.

### `PresetItem` (value)

| Field | Type | Rule |
|---|---|---|
| `id` | `String` | not blank |
| `mustHave` | `boolean` | defaults to false |

### `Pack` (aggregate, extended)

- `presets()`: `List<Preset>`, sorted by id. Two presets with one id breach an invariant.
- `document()`: `Optional<Payload>`, the bytes of `pack.md`.
- `toBuilder()` and `with(...)` keep both.

The class doc records the pack-level slots as asset files, presets and the document (ADR-0042). By the rule of three,
a fourth slot extracts a `PackLevelContent` value.

## Kernel

`PackDescriptor`: the `undeclaredKeys` component and the 7-argument constructor's parameter are removed.

## Storage (`ai.speckitty.charter.packs.infra.storage`)

### `PresetDso` / `PresetYaml`

These mirror the schema 1:1. `schemaVersion` is `"1.0"`. Lists are `List<PresetItemDso(id, mustHave)>` per YAML key,
in the schema's key order. The reader (after schema validation) builds the DSO. The store checks `id` against the
file stem and records a violation `presets/<stem>.yaml: preset id '<id>' is not its file stem '<stem>'`. The writer
emits keys in schema order, writes `must_have: true` only when true, and leaves an absent list out.

### `PackManifestDso`

| Field | Type |
|---|---|
| `schemaVersion` | `String` (`"1.0"`) |
| `manifestHash` | `String` (64 lower hex) |
| `constituents` | `List<Constituent(kind, id, path, contentHash)>` |
| `presets` | `List<PresetEntry(id, path, contentHash)>` |

### `PackManifests`

| Operation | What it does |
|---|---|
| `contentHash(byte[])` | SHA-256 hex of the bytes after CRLF/CR → LF |
| `manifestHash(PackManifestDso)` | SHA-256 hex of the UTF-8 JCS of `{schema_version, constituents:[{kind,id,path,content_hash}], presets:[{id,path,content_hash}]}`. `presets` is omitted when empty, as the YAML omits it. |
| `restamp(PackManifestDso, Function<String, Optional<byte[]>> bytesAt)` | Recomputes every derived field. A path with no bytes refuses (`CharterRefusal PACK_UNREADABLE`, naming the path). |
| `stale(PackManifestDso, Function<String, Optional<byte[]>> bytesAt)` | Lists each derived field that does not verify, as `constituents{<path>}.content_hash`, `presets{<path>}.content_hash`, `manifest_hash`, or a missing path. Used by the gate. |

### `WholePackStore.LoadedPack`

Gains `Optional<PackManifestDso> manifest`. An unreadable manifest is a violation on the pack (`pack-manifest.yaml`),
so the write refuses, as for any unreadable file.

### `PackFileSet`

- `Listing` gains `presets` (files of schema `preset`) and `documents` (bytes kind `pack-document`).
- `digested` appends presets, then `pack.md`, then `pack-manifest.yaml`.
- Constants: `PRESETS = "presets/"`, `PACK_DOCUMENT = "pack.md"`, `PACK_MANIFEST = "pack-manifest.yaml"`.

## State and lifecycle

None. All four are values read from and written to files. The manifest is the only derived data, recomputed on each
whole-pack write.
