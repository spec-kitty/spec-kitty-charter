# Data Model: Asset and template round trip

All domain types live in `packs.domain` (ADR-0006, ADR-0035). Storage shapes stay in `packs.infra.storage`.

## Payload (value object)

| Field | Type | Rule |
|---|---|---|
| bytes | immutable copy of the file's bytes | any length, including 0; no charset assumed |

Equality and hash by content. `size()`; a short content digest for diagnostics. Never exposes its array.

## AssetFile (value object, held by the Pack)

| Field | Type | Rule |
|---|---|---|
| path | `String` | relative to the pack's `assets/` directory, `/`-separated, non-blank, no `..` segment, not absolute |
| payload | `Payload` | |

A file the `*/assets/**` payload rule classifies (README.md stays excluded by the gate's own list). Equal when path and
payload are equal.

## Asset (entity, `CharterArtifact`, kind `ASSET`)

| Field | Type | Rule |
|---|---|---|
| id | `AssetIdentifier` | non-blank (schema `minLength: 1`) |
| packId | `PackIdentifier` | |
| mime | `String` | non-blank; stored as authored; checked only by `PackPolicy` (validate mode) |
| path | `String` | non-blank; as authored; names an `AssetFile` of the same pack |
| title | `Optional<String>` | absent and `null` mean the same |

`schemaVersion()` empty; `label()` = title, else id; `description()` empty; `relationships()` none; URN `asset:<id>`.

## Template (entity, `CharterArtifact`, kind `TEMPLATE`)

| Field | Type | Rule |
|---|---|---|
| id | `TemplateIdentifier` | path under the pack's top-level `templates/`, `/`-separated, non-blank, no `..` |
| packId | `PackIdentifier` | |
| content | `Payload` | |

`schemaVersion()` empty; `label()` = the id's last segment; `description()` empty; relationships none;
URN `template:<id>` (for example `template:writing/meeting-minutes.md`).

## Pack (aggregate) — additions

- `assetFiles()`: the held `AssetFile`s, sorted by path; at most one per path (case-sensitive), built through
  `Pack.Builder.assetFile(..)`.
- `assets()`, `templates()`: typed views over the kind map (one-line delegates, like the existing five).
- Invariants (new): every `Asset` names a held `AssetFile` by `path`; no two assets name the same file. Breach →
  `ConstraintBreachException` (a hand-built pack); the store never builds a breaching pack (it refuses the sidecar as a
  violation first).
- `with(artifact)` unchanged; `withAssetFile(AssetFile)` adds or replaces one file and revalidates.

## Repository ports (new)

- `AssetRepository`: `save(Asset)` writes the sidecar; `save(PackIdentifier, AssetFile)` writes the payload file.
- `TemplateRepository`: `save(Template)` writes `templates/<id>`.

Realized by `DomainRepositoryService`.

## Storage (packs.infra.storage)

- `ContentForm` enum: `YAML`, `MARKDOWN`, `TEXT`, `BYTES`, parsed from `content_form`. The table accepts every
  declared form; this mission reads and writes only `YAML` and `BYTES`, and nothing in the table uses the others yet.
- `AssetDso(id, mime, path, @Nullable title)`; `AssetBinding` (`KindBinding<AssetDso, Asset>`, file token `asset`,
  file name `<id>.asset.yaml`); `AssetYaml` in `YamlFormats`.
- `FileBinding<E>` / `FileBindings` with `TemplateBinding`.
- `PackFileKind.Mapped` gains `contentForm`; a `payloads` rule classifies as `Mapped(kind, no schema, form)`.

## State and refusals

| Situation | Outcome |
|---|---|
| Sidecar with undeclared key | violation `schema` (unchanged schema path), sidecar skipped |
| Sidecar whose `assets/<path>` is not a held asset file | violation naming the sidecar file and the missing path, sidecar skipped |
| Two sidecars naming one file | the second is a violation, skipped |
| Payload or template not UTF-8 | no violation (bytes) |
| Write: two files folding to one path (case) | write refused, no target left |
