# Data model: Glossary pack and skill

Both are artifacts of a pack (`CharterArtifact`, `packs.domain`). Neither mints relationships. Field
names are the file's; an optional scalar keeps whether it was written.

## GlossaryPack (kind `glossary_pack`, URN `glossary_pack:<id>`)

| Field | Type | Rule |
|---|---|---|
| id | `GlossaryPackIdentifier` | required, non-blank; the local id |
| provenance | text | required |
| description | optional text | |
| terms | list of `GlossaryTerm`, authored order | at least one; surfaces unique within the pack |

### GlossaryTerm (value)

| Field | Type | Rule |
|---|---|---|
| surface | text | required, non-blank |
| definition | text | required |
| confidence | number | required; kept as a number |
| status | text | required; free text (core has no enum) |
| see_also, synonyms_to_avoid, aliases, banned_synonyms | list of text | absent, null and empty mean the same (ADR-0036 decision 2); empty is not written |
| introduced_in_mission | optional text | |

## Skill (kind `skill`, URN `skill:<id>`)

| Field | Type | Rule |
|---|---|---|
| id | `SkillIdentifier` | required; the schema's kebab-case pattern, max 64 |
| schema_version | text | `1.0` |
| title, description | text | required, non-empty |
| triggers | list of text | empty is not written |
| form | `SkillForm` (`prompt`, `wrapper`) | required |
| body_path | optional text | present exactly when `form` is prompt |
| body | optional text | the companion file's text; present exactly when `body_path` is |
| expands_to | optional `SkillExpansion` (target, optional args) | present exactly when `form` is wrapper |
| parameters | list of `SkillParameter` (name, optional required, description, default) | empty is not written |
| invocation | optional `SkillInvocation` (optional user_invocable, model_invocable, side_effects) | kept as written; never forced |
| tools, maintainers | list of text | empty is not written |
| version, overrides, enhances | optional text | |

## Persistence (not domain)

- `<id>.glossary-pack.yaml`: one glossary pack. A new pack is created under that name, while an
  existing pack keeps its authored path, whatever its stem.
- `<id>.skill.yaml` plus the file at `body_path`, which is relative to the skill file and stays
  inside the pack. Both are written together, and the fingerprint digests both.
