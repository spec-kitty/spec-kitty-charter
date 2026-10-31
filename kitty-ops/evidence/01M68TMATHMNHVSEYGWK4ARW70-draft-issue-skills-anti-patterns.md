# Draft issue (spec-kitty-charter): `charter.schema.json` lacks `activated_skills` and `activated_anti_patterns`

Found while delivering #131 (Op `01M68TMATHMNHVSEYGWK4ARW70`); for the orchestrator to file.

Core's flat activation vocabulary, `ACTIVATION_YAML_KEYS` (`src/charter/activation/pack_manager.py:205`
at core `512567ee`), holds thirteen keys. After #131, `contracts/packs/schemas/charter.schema.json`
declares eleven of them; it still lacks `activated_skills` (a `YAML_KEY_MAP` value) and
`activated_anti_patterns` (a tokenless key, `pack_manager.py:202-204`). Core's writer can put
either at the root of `charter.yaml` (`charter_yaml_io.py:682-687`), and the closed schema then
refuses the file with `unknown key`.

Proposed: declare both in the shape of the other `activated_*` keys (`anyOf: [array of string,
null]`, `default: null`), extend `ProjectCharterYamlSchemaTest`, one contract CHANGELOG line.
Size S.

Not filed against core: the charter designs against this repository's own model while the service
is rewritten (operator, 2026-10-28, epic #186), so core's `CharterYaml` pydantic model, which lacks
the three keys, is not a prerequisite; it changes when the service is incorporated into core.
