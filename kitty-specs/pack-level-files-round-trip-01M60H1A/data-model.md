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

- `required()`: the must-have item ids per artifact kind (a kind with none is absent).
- `requiredMissionTypes()`: the must-have mission-type ids. Mission types are not an `ArtifactKind`, so they are kept
  apart rather than keyed into the same map (WP02 review 1).
- `governing()`: true when `required()` or `requiredMissionTypes()` is not empty.

Equality is by value.

### `PresetItem` (value)

| Field | Type | Rule |
|---|---|---|
| `id` | `String` | not blank |
| `mustHave` | `boolean` | written as `must_have: true` only; the schema has no `false` spelling |

### `Pack` (aggregate, extended)

- `presets()`: `List<Preset>`, sorted by id. Two presets with one id breach an invariant.
- `toBuilder()` and `with(...)` keep the presets.
- The pack document's domain slot comes with #194's `markdown` form (IC-08).

The class doc records the pack-level slots (ADR-0043). The rule of three has fired, and extracting a
`PackLevelContent` value is a follow-up on epic #186 (research P8).

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
| `contentHash(byte[])` | `Payload.of(lf(bytes)).sha256()`: SHA-256 hex of the bytes after CRLF/CR → LF |
| `manifestHash(PackManifestDso)` | SHA-256 hex of the UTF-8 JCS of `{schema_version, constituents:[{kind,id,path,content_hash}], presets:[{id,path,content_hash}]}`. `presets` is omitted when empty, as the YAML omits it. |
| `recompute(PackManifestDso, Function<String, Optional<byte[]>> bytesAt)` | Returns the manifest with every derived field recomputed, plus the listed paths that have no bytes. The writer refuses when any path is missing (`CharterRefusal PACK_UNREADABLE`, naming it). The gate diffs the recomputed fields against the read ones (`constituents{<path>}.content_hash`, `presets{<path>}.content_hash`, `manifest_hash`) and reports the missing paths. |

### `WholePackStore.LoadedPack`

Gains `Optional<PackManifestDso> manifest`; a 4-argument constructor keeps the old signature. `WholePackStore` is the
manifest's only owner: its load reads the file and records an unreadable one as a violation on the aggregate, so the
write refuses. `FilesystemPackStore` and `DomainRepositoryService` never parse it.

### `PackFileSet`

- `Listing` gains `presets` (files of schema `preset`). The pack document arrives with #194's form.
- `digested` appends presets, then `pack.md`, then `pack-manifest.yaml`.
- Constants: `PRESETS = "presets/"`, `PACK_MANIFEST = "pack-manifest.yaml"`.

## State and lifecycle

None. All four are values read from and written to files. The manifest is the only derived data, recomputed on each
whole-pack write.
