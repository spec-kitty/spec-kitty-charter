# Contracts touched by this mission

- `toolguide-registration.delta.yaml` — the HTTP registration change (PR-A2), authorized by the operator on #194.
  Lands in `contracts/charter/schemas/ToolguideRegistration.yaml` with a scenario under
  `contracts/charter/scenarios/` and a `### Breaking` entry in `contracts/charter/CHANGELOG.md`. Refusals for a bad
  guide or a refused fetch reuse existing refusal codes where they fit; any new code is listed in the CHANGELOG.
- Pack schemas (`contracts/packs/schemas/`): `procedure.schema.json` rewritten, `toolguide.schema.json` (relative
  `guide_path`), new `toolguide-guide.schema.json` over the parsed Markdown model. Shapes: see `../data-model.md`.
