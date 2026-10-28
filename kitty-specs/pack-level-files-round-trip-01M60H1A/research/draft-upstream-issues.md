# Draft upstream issues: pack-level files (#195)

These issues are drafted for the parent session to file; this session cannot file them. Each one follows ADR-0036
decision 5. The known-failure entries in
`service/packs/src/test/resources/whole-pack/known-failures.yaml` use the placeholder key
`spec-kitty/spec-kitty-open-packs#TBD-<slug>`. When an issue is filed, replace its placeholder with the real number.

All divergences are open-packs issues only. Under the operator ruling on spec-kitty-charter#186 (2026-10-28), the
charter rewrite operates as if core does not exist: no core issue and no "core first" step is drafted. That
`packs/built-in/` is seeded from core today (`packs/built-in.source.yaml`) is open-packs' concern, not a blocker here.

---

## OP-1 `TBD-preset-shape`: presets in the sanitized shape, with the per-item must-have mark

**Repository:** spec-kitty/spec-kitty-open-packs (bug report template)
**Pinned `openPacksRef`:** `01edb4ebab308249a10ed4a4a01d77741ac1d14b`

**Files:**

- `packs/built-in/presets/default.yaml`
- `packs/built-in/presets/minimal.yaml`
- `packs/event-driven/presets/event-driven.yaml`
- `tests/fixtures/valid/example-pack/presets/example.yaml`

**File kind:** `preset`. The new closed schema is `contracts/packs/schemas/preset.schema.json` in
spec-kitty-charter (ADR-0043).

**Rules broken:** ADR-0043, sanitized-shape rules S1, S2, S3 and S7; the operator ruling on spec-kitty-charter#195
(each preset item can be marked must-have); ADR-0036 decision 5.

**Required change:**

1. Rename `name:` to `id:`. The value stays the file stem.
2. Add `schema_version: "1.0"`.
3. Rename `mission_type_activations` to `activated_mission_types`.
4. Make every list item an object: `- id: <artifact id>`. Add `must_have: true` to each item the preset
   requires.
5. `minimal.yaml` only: `activated_directives` lists file stems. Use the directive ids instead:
   `DIRECTIVE_001`, `DIRECTIVE_010`, `DIRECTIVE_024`, `DIRECTIVE_028`, `DIRECTIVE_030`.

Example, `minimal.yaml` after the change:

```yaml
schema_version: "1.0"
id: minimal
description: A small curated baseline of foundational directives and tactics for software-dev.
activated_mission_types:
  - id: software-dev
activated_directives:
  - id: DIRECTIVE_001
  - id: DIRECTIVE_010
  - id: DIRECTIVE_024
  - id: DIRECTIVE_028
  - id: DIRECTIVE_030
activated_tactics:
  - id: acceptance-test-first
```

**Gate output:**

- `presets/default.yaml` and `presets/minimal.yaml` are refused on read, `unknown key 'name'` first.
- The event-driven preset and the fixture are outside the gate, because their packs have no `pack.yaml`.

**Rationale:** the charter service holds presets in one deliberately designed shape, and refuses any other shape
instead of tolerating it (operator requirement, 2026-10-28). Mixing ids and file stems in one field is two id
spaces for one concept.

**Known-failure entries:** `presets/default.yaml` and `presets/minimal.yaml` in the built-in pack.

---

## OP-2 `TBD-manifest-shape`: `pack-manifest.yaml` in the sanitized authored-pack shape

**Repository:** spec-kitty/spec-kitty-open-packs (bug report template)
**Pinned `openPacksRef`:** `01edb4eb`
**File:** `packs/built-in/pack-manifest.yaml`
**File kind:** `pack-manifest` (`contracts/packs/schemas/pack-manifest.schema.json`, ADR-0043)
**Rules broken:** ADR-0043, sanitized-shape rules S1, S2, S3, S5 and S8

**Required change (the generator, not a hand edit):**

1. Drop `charter`, `fetched_at`, `source_url`, `source_type`, `generated_at`, `generated_by`, and every
   constituent's `provenance_path`.
2. Key preset entries by `id`, not `name`.
3. Set `schema_version: "1.0"`.
4. Compute `manifest_hash` as SHA-256 over the RFC 8785 (JCS) canonical JSON of the manifest without
   `manifest_hash`. Leave `content_hash` as it is: SHA-256 of the file bytes after CRLF/CR → LF.

**Gate output:** `pack-manifest.yaml` is refused on read, `unknown key 'charter'` first.

**Rationale:**

- The current file merges three variants: a built-in pack, a fetched pack and a charter bundle. The authored
  pack's manifest carries null fields of the other two.
- `source_type` repeats `pack_level`.
- A hash over one emitter's YAML output ties every reader to that emitter. Canonical JSON (RFC 8785) is defined
  independently of any emitter.

**Known-failure entry:** `pack-manifest.yaml` in the built-in pack.

---

## Placeholder map

| Placeholder | Draft | Known-failure paths (built-in pack) |
|---|---|---|
| `spec-kitty/spec-kitty-open-packs#TBD-preset-shape` | OP-1 | `presets/default.yaml`, `presets/minimal.yaml` |
| `spec-kitty/spec-kitty-open-packs#TBD-manifest-shape` | OP-2 | `pack-manifest.yaml` |

When the placeholders are replaced with real issue numbers, the loader's `TBD-<slug>` alternative in
`WholePackExpectation` can be removed in the same change.
