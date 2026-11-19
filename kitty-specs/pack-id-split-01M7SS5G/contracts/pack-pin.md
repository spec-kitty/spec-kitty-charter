# Contract delta (D7, D8)

- `contracts/offering/schemas/ActivationConfig.schema.json` `$defs/PackPin/properties/packId`: `$ref #/$defs/Slug`
  becomes the pack id grammar `{type: string, minLength: 1, maxLength: 63, pattern: ^[a-z0-9]+(-[a-z0-9]+)*$}`.
- `contracts/charter/schemas/PackPin.yaml` `packId`: the same, in parity.
- `contracts/charter/parameters/PackId.yaml` `description`: names the registry, not the descriptor.
- Fragment `contracts/charter/changes/2026-11-19-pack-pin-follows-the-pack-id-grammar.md` under `### Breaking`. A pin
  with `_` or 64 characters was always unconfigurable (422 `activation_invalid` at use). It is now refused by the schema.
