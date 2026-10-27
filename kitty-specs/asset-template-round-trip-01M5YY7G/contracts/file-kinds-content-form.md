# Contract delta: `contracts/packs/file-kinds.yaml`

No HTTP contract changes (`contracts/charter/openapi.yaml` untouched). This is the pack-vocabulary data contract.

## `files` rules

```yaml
files:
  - match: "**/*.asset.yaml"
    schema: asset
    # content_form: yaml   # optional; yaml is the default and is not written for existing rules
```

`content_form` ∈ `yaml | markdown | text | bytes`. Absent means `yaml`. Every existing rule keeps its meaning.

## `payloads` (new section): files with no schema

```yaml
payloads:
  - match: "*/assets/**"
    kind: asset-payload
    content_form: bytes
  - match: "*/templates/**"
    kind: template
    content_form: bytes
```

- Consulted only when no `files` or `exclusions` rule matches the path; payload rules must not overlap each other.
- `kind` is the gate kind and the census kind. `template` maps to `ArtifactKind.TEMPLATE`; `asset-payload` names the
  pack-level asset files.
- `*/templates/**` covers only a pack's top-level `templates/` (the single leading `*/`); mission templates
  (`*/missions/*/templates/**`) stay unclassified (#194).

## `exclusions`

Retired: `**/*.config.yaml` (its only match is the asset payload `built-in/assets/docs_structural_lint.config.yaml`,
operator ruling R1).

## Classification

`classify(path)` → `Mapped(kind, schema?, contentForm)` | `Excluded` | `Unclassified`, in the order `files`,
`exclusions`, `payloads`. Validate mode and `PackSchemas` keep iterating `files` (schema-bearing) only.
