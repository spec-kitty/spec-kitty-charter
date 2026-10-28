# Contract: pack-level storage shapes

The authoritative shapes are the JSON Schemas under `contracts/packs/schemas/` (`preset`, `pack-manifest`,
`pack-descriptor`) and the rules in `contracts/packs/file-kinds.yaml`. This page summarizes what this mission adds to
that pack-file contract. It changes no HTTP contract (`contracts/charter/**` is untouched).

## `file-kinds.yaml`

```yaml
files:
  - match: "*/presets/*.yaml"
    schema: preset
  - match: "*/pack-manifest.yaml"
    schema: pack-manifest
payloads:
  - match: "*/pack.md"
    kind: pack-document
    content_form: bytes
```

The `exclusions` entries `**/presets/*.yaml` and `*/pack-manifest.yaml` are retired.

## `preset.schema.json`

The schema is closed. Required: `schema_version` (`const "1.0"`), `id`, `description`.

- `id`: pattern `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, `maxLength` 64.
- `description`: string, `minLength` 1.
- `activated_kinds`: array, `minItems` 1, `uniqueItems`, items from the enum of 13 kind plurals.
- `activated_{directives,tactics,styleguides,toolguides,paradigms,procedures,agent_profiles,mission_step_contracts,anti_patterns,mission_types}`:
  array of `$defs/item`, `minItems` 1.
- `$defs/item`: closed. Required `id` (string, `minLength` 1). Optional `must_have` (boolean, default `false`).

## `pack-manifest.schema.json`

The schema is closed. Required: `schema_version` (`const "1.0"`), `manifest_hash`, `constituents`.

- `manifest_hash`, `content_hash`: pattern `^[0-9a-f]{64}$`.
- `constituents[]`: closed `{kind (enum: artifact kinds), id, path, content_hash}`, all required.
- `presets[]`: closed `{id, path, content_hash}`, all required.

## Derived fields

| Field | Recipe |
|---|---|
| `content_hash` | SHA-256, lower hex, of the file's bytes after replacing CRLF and lone CR with LF |
| `manifest_hash` | SHA-256, lower hex, of the UTF-8 bytes of the RFC 8785 canonical JSON of the manifest object without `manifest_hash` |

The writer recomputes both on every whole-pack write. The reader does not check them. The gate verifies them on the
source and on the written side.

## `pack-descriptor.schema.json`

The fields are unchanged. `additionalProperties` becomes `false`. The description text now says `pack_level` is
optional and `pack_version` is required at every level.
