### Added

- `listPackPresets` (`GET /packs/{packId}/presets`) and `getPackPreset` (`GET /packs/{packId}/presets/{presetId}`),
  tag `Presets`: a pack's activation presets in id order, unpaged, and one preset, each with its `description` and,
  per kind it lists, an array `activated<Plural>` of `PresetItem {id, mustHave}` (schemas `Preset`, `PresetItem`,
  `PresetList`). Both answer the pack revision as `ETag` and the source fingerprint. An unknown pack is
  `404 pack_not_found`, a preset the pack does not hold `404 preset_not_found`. A pack answer (`getPack`, a succeeded
  registration's result) links `presets`, and both operations are `ready` in the switch-gate register (#98).
