# #131 research: `activated_glossary_packs` in `charter.schema.json`

Op `01M68TMATHMNHVSEYGWK4ARW70` (lightweight dispatch Op, not a Mission: one optional key in one
schema, one test, no domain or API change, no ADR). Read-only research, 2026-10-31.

## 1. Core's shape (core `512567ee`, `/opt/spec-kitty`)

| Fact | Where |
| --- | --- |
| The key is in core's flat activation vocabulary: `ACTIVATION_YAML_KEYS = ("activated_kinds", *YAML_KEY_MAP.values(), *_TOKENLESS_ACTIVATION_KEYS)`; at runtime it holds `activated_glossary_packs` | `src/charter/activation/pack_manager.py:172` (`YAML_KEY_MAP`), `:205` |
| The value written is a plain list of id strings: `config_data[plan.yaml_key] = list(plan.new_list)`, with `new_list = list(dict.fromkeys([*base, *chosen]))` | `src/charter/activation/activation_engine.py:679-680`, `:401` |
| The single charter.yaml writer puts each activation key **flat at the root** (`document[key] = copy.deepcopy(value)`) and deletes it on removal (`document.pop(key, None)`) | `src/charter/activation/charter_yaml_io.py:682-687` |
| The reader is three-state: absent/`null` → `None` (unrestricted), else must be a list, read as strings | `src/charter/activation/pack_context.py:750-756` (`_read_list_key`), `:845-847` (`_read_activated_glossary_packs`) |
| The resolver gates glossary packs by that set like every other kind | `src/charter/activation/resolver.py:325-329` |
| Core's own pydantic `CharterYaml` lacks the field (it ends at `activated_mission_step_contracts`, `extra="forbid"`): the schema imported from it inherited the gap | `src/charter/activation/schemas.py:360`, `:399` |

Shape: `list[str] | None`, absent = unrestricted, `[]` = fail-closed empty, a list = the
activated set. Identical to the ten existing `activated_*` / `mission_type_activations` entries
of `charter.schema.json` (`anyOf: [array of string, null]`, `default: null`). Core and the
schema's existing keys agree, so no operator decision is needed.

Out of scope, noted for a follow-up: core's vocabulary also holds `activated_skills` and
`activated_anti_patterns`, which `charter.schema.json` does not declare either.

## 2. The schema and its tests

- `contracts/packs/schemas/charter.schema.json`: closed at the root (`additionalProperties:
  false`). The root description says each `activated_*` field is three-state.
- `file-kinds.yaml` maps `**/charter.yaml` to `charter` so a stray one inside a pack is
  validated; the service validates it only there. Nothing validates the project's own
  `.kittify/charter/charter.yaml` today.
- `PackSchemas.bundled().validate(kind, file, tree)` is the validator the tests use
  (`GlossaryPackAndSkillSchemaTest`, `PackLevelSchemasTest`); `:packs:test` passes
  `charter.repositoryRoot`.
- Validating the repository's charter.yaml today (Python `jsonschema`): one finding,
  `$: Additional properties are not allowed ('activated_glossary_packs' was unexpected)`.

## 3. Versioning (ADR-0022, ADR-0015)

- The pack schemas are canonical here (ADR-0015, ruling R20); a pack-schema change is not an
  OpenAPI change, but load-path behavior changes are recorded in `contracts/charter/CHANGELOG.md`
  (precedent: #195's "Pack-level files round trip" entry).
- This change is additive (an optional, nullable key): a stray pack `charter.yaml` holding the
  key was refused and is now accepted. Not breaking under ADR-0022; one CHANGELOG line.
- Descriptor docs: `docs/architecture/descriptor-schemas.md` says `charter.schema.json` "is not
  drawn" and lists none of its keys, so it needs no update. `contracts/packs/README.md` lists
  no keys either.

## 4. Plan

1. Red: a `:packs:test` case that validates `.kittify/charter/charter.yaml` with
   `PackSchemas.bundled()` and expects no violation, plus the three states of the key.
2. Green: add `activated_glossary_packs` to the schema in the shape above.
3. Add the charter.yaml as an input of `:packs:test`; one CHANGELOG line.
