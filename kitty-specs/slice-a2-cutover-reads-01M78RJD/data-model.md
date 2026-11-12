# Data model: Slice A2 reads

These are wire shapes; `contracts/charter/schemas` is the source of record. All of them are camel case and closed (`additionalProperties: false`).

| Schema | Members | Source in the domain |
| --- | --- | --- |
| `Preset` | `id`; `description`?; `activated<Plural>`? (an array of `PresetItem`) for each activatable kind the file lists; `_links` (`self`, `pack`) | `packs.domain.Preset` |
| `PresetItem` | `id`, `mustHave` | `PresetItem` |
| `PresetList` | `items`: [`Preset`], `_links` (`self`, `pack`) | `Pack.presets()` |
| `Asset` | `id`, `mime` (as written), `path`, `title`?, `executable`? | `Asset`, `Executable` |
| `AssetExecutable` | `runtime`, `minVersion`?, `entry`, `args` | `Executable` |
| `AssetSummary` | `id`, `mime`, `executable` (boolean), `_links` (`self`, `payload`) | `Asset` |
| `AssetList` | `items`: [`AssetSummary`], `_links` | `Pack.assets()` / the closure's held assets |
| `Skill` | `id`, `title`, `description`, `form`, `triggers`, `parameters`, `invocation`?, `expandsTo`?, `tools`, `body`? | `Skill` and its parts |
| `SkillSummary` | `id`, `title`, `description`, `form`, `_links` | `Skill` |
| `SkillList` | `items`: [`SkillSummary`], `_links` | `Pack` skills / the closure's winners |
| `Artifact` (extended) | new members `asset` and `skill` in the one-of | — |
| `EffectiveAssetRead` | `activationId`, `revision`, `governanceFingerprint`, `kind`, `artifactId`, `packId`, `level`, `asset`, `_links` (`self`, `artifact`, `payload`, `up`) | R2 |
| `EffectiveSkillRead` | the members of `EffectiveArtifactRead`, plus `skill` | `EffectiveArtifactRead` |

## Invariants

- An asset id and a skill id are one lowercase kebab segment of at most 64 characters (R11), so `…/asset/{id}/payload` is unambiguous.
- `mustHave` is true exactly when the preset file marks `must_have: true`.
- `body` is present exactly when `form` is `prompt`.
- The payload's bytes equal the file's bytes (NFR-003).
- Under an activation, an asset or skill answer names the winning copy's pack and level. The asset answer has no state (R2).
