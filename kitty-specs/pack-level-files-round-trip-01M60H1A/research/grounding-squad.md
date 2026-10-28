# Pre-spec grounding squad — pack-level files round trip (#195)

Point-cut: pre-spec (procedure `adversarial-squad-deployment`). Question: *what is the ground truth for
the pack-level files (presets, `pack.md`, `pack.yaml`, `pack-manifest.yaml`) in core, in the pinned packs
and in the charter code, and what sanitized storage shape should each have?*

Run 2026-10-28 against charter `f790273` (main), core `spec-kitty` main, and `spec-kitty-open-packs` at
`openPacksRef` `01edb4eb`. Four profile-loaded, read-only delegates ran in parallel:

| Lens | Profile | Model tier | Loaded |
|---|---|---|---|
| Researcher | `researcher-robbie` | mid | `agent profile show`, `charter context --action research` |
| Analyst | `analyst-annie` | top | `agent profile show`, `charter context --action specify` (DIRECTIVE_003, 032, 047) |
| Architect | `architect-alphonso` | top | `agent profile show`, `charter context --action plan` (DIRECTIVE_001, 003, 031, 032; DDD, C4) |
| Planner | `planner-priti` | mid | `agent profile show`, `charter context --action tasks` (DIRECTIVE_003, 010, 025; QA_003) |

The orchestrator checked the load-bearing claims against the source: the preset id-space divergence
(`minimal.yaml` lists `001-architectural-integrity-standard`, the directive's `id` is `DIRECTIVE_001`), the
built-in seeding rule (`packs/built-in.source.yaml:8-10`), the gate census (`WholePackCensus`,
`DomainRoundTrip`), and whether any contract surface carries presets (only `@WIP` journey prose:
`contracts/charter/scenarios/journeys/journey-01-getting-started.feature:42-46`,
`journey-04-org-house-rules.feature:33-36`).

## 1. Ground truth

### Presets (core `src/charter/offering/packs/presets.py`, `schemas/activation-preset.schema.yaml`)

- `<pack>/presets/<name>.yaml`; `name` is required and equals the file stem; grammar
  `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, at most 64 characters. `description` is required (non-empty).
- Closed key set: `name`, `description`, `activated_kinds` (13 kind plurals), `mission_type_activations`, and
  nine `activated_<plural>` lists (directives, tactics, styleguides, toolguides, paradigms, procedures,
  agent_profiles, mission_step_contracts, anti_patterns). Unknown keys are refused; `activated_skills` and
  `activated_glossary_packs` get "presets do not govern this kind".
- Lists hold unique non-empty strings. Three states per key, as in `charter.yaml` (charter contract G3):
  absent = the kind is unrestricted, `[]` = explicit fail-closed empty, a list = the activated set.
- **No must-have marker exists anywhere in core**, and no `required_*` key exists in a preset:
  `required_<plural>` lives only in `org-charter.yaml`.
- The flat `activated_*` keys mirror `charter.yaml` on purpose, so applying a preset is a copy
  (`contracts/packs/schemas/charter.schema.json:718`).
- Pinned presets: `built-in/presets/{default,minimal}.yaml`, `event-driven/presets/event-driven.yaml`, and
  the open-packs fixture `tests/fixtures/valid/example-pack/presets/example.yaml`. The gate walks only pack
  directories that hold a `pack.yaml` (`WholePackOpenPacksRoundTripTest`), so only the two built-in presets
  are in the gate.
- The service holds no preset model today: no hit for "preset" in `service/*/src/main`, and the contract only
  names presets in `@WIP` journey scenarios.

### `pack-manifest.yaml` (core `pack_manifest.py`, `hashing.py`, `builtin_manifest.py`)

- Core model `PackManifest` (`extra="forbid"`): `schema_version` (`"1"`), `generated_by`, `generated_at`,
  `source_url`, `source_type`, `fetched_at`, plus fetched-only `pack_version`, `etag`, `source_fingerprint`,
  `source_uses_query`, `snapshot_sha256`, `artifact_counts`; `manifest_hash`; `constituents[]`
  (`kind`, `id`, `path`, `content_hash`, `provenance_path`); `charter` (a charter-bundle profile); `presets[]`
  (`name`, `path`, `content_hash`). It merges three variants: the built-in pack, a fetched pack and a charter
  bundle.
- `content_hash` is SHA-256 over the whole file's bytes after CRLF/CR → LF. `manifest_hash` is SHA-256 of
  core's ruamel `canonical_yaml` (sorted keys) of the model dump, without `manifest_hash`, `generated_at` and
  `generated_by`.
- Constituents are the YAML artifacts of nine kinds only (no step contracts, templates, skills, missions,
  shards, `pack.yaml`, `pack.md`). Of the built-in pack's 505 files, 231 are not listed.
- **The pinned manifest is fresh**: all 272 constituent hashes, both preset hashes and the `manifest_hash`
  recompute exactly (researcher and analyst, independently).
- Core never checks freshness on load. Only `charter pack regenerate-graph --check` and the pin's tree
  digest notice a stale manifest.

### `pack.md`

- Only the built-in pack has one. It is plain Markdown with no frontmatter. Core never reads it. It is
  covered by the pin's tree digest only.

### `pack.yaml`

- Core's `PackDescriptor` is closed (`extra="forbid"`), and it refuses the retired `accompanies_doctrine_pack`.
- The charter schema `pack-descriptor.schema.json` is still open (`additionalProperties: true`), and the
  service carries `PackDescriptor.undeclaredKeys` and the `PACK_UNKNOWN_KEY` warning. ADR-0036 decision 3 and
  its follow-up list say to close it; #189 did not (planner: `kitty-specs/whole-pack-seam-189/spec.md:91`).
- The schema text contradicts itself on `pack_level` ("Required" in the description, "Optional" in the
  property, not in `required`).
- Both pinned `pack.yaml` files (built-in and internal) already fit a closed schema.
- The contract scenarios write `pack_id: qa`, so a ULID pattern on `pack_id` would break them.
- `pack_unknown_key` is not referenced by any operation (`contracts/charter/CHANGELOG.md`). The
  "undeclared key loads" scenario was already removed. Closing the schema changes served behaviour (a pack
  with an unknown key goes from loading with a warning to `pack_unreadable`), but no wire shape. Only the
  `DiagnosticCode.yaml` *description* of `pack_unknown_key` ("never refused") would become stale.

### The fingerprint and #110

- The service's verified-source fingerprint (`PackFileSet.digested`) covers `pack.yaml`, bound kinds and
  their companions, shards, the fragment, asset files and templates. It does **not** cover presets,
  `pack.md` or the manifest.
- #110 is a contract-scope decision about core's pin and manifest reads, not a fingerprint implementation.

## 2. Findings and dispositions

| # | Sev | Lens | Finding | Disposition |
|---|---|---|---|---|
| F1 | HIGH | Architect | The comparator treats `[]` as absent (ADR-0036 E2), but a preset's `[]` means "activate none". The meaning would change silently, and the gate would call it equal. | **changed**: the sanitized preset shape refuses an empty activation list (`minItems: 1`). A kind is switched off one way only, by leaving it out of `activated_kinds`. There is no comparator exception (spec C-005, ADR-0043). |
| F2 | HIGH | Architect | `DomainRoundTrip` writes artefacts at `<id>.<token>.yaml`, so a recomputed manifest finds no file at its listed paths. | **changed**: at the pin the manifest is refused on read (sanitized-shape divergence, D-M1..M5). The derived-hash path is proven on a layout-preserving fixture through `WholePackStore` (spec FR-010). The gate seam switch stays deferred, as in ADR-0040. |
| F3 | HIGH | Architect | Carrying hashes as read leaves a manifest describing bytes that no longer exist. | **accepted**: hashes are derived and recomputed on every write (spec FR-009, ADR-0043). |
| F4 | HIGH | Analyst | `minimal.yaml` lists file stems where directive ids belong. | **accepted**: divergence D-P5, drafted upstream, file on the known-failure list. |
| F5 | HIGH | Analyst | The as-written preset shape cannot carry the ruled per-item mark, so #206 would break the same files twice. | **accepted**: the sanitized preset shape carries `must_have` per item (spec FR-002). |
| F6 | HIGH | Planner | The known-failure loader accepts only numeric open-packs issue numbers. The `#TBD-<slug>` placeholder would not load. | **accepted**: the loader admits `spec-kitty/spec-kitty-open-packs#TBD-<slug>`, tested. The parent session replaces the placeholders before merge (spec FR-014). |
| F7 | HIGH | Planner | Closing `pack.yaml` was left undone by #189 and touches a contract description. | **changed**: close the schema and remove `undeclaredKeys`/`PACK_UNKNOWN_KEY` in code (ADR-0036 ruling 1, already accepted). Leave `contracts/charter/**` untouched and ask the operator on #195 about the description (spec C-001, FR-012). |
| F8 | MED | Architect | Registrations rewrite constituents without restamping the manifest. | **deferred_with_rationale**: restamping on the commit path and regenerating membership need core's enumeration rules, which belong to #110. Recorded as a known gap (spec Out of scope). |
| F9 | MED | Architect | Recomputing `manifest_hash` with core's recipe needs a byte-exact Java port of ruamel's emitter. | **changed**: the sanitized manifest defines `manifest_hash` over RFC 8785 canonical JSON, which is language-neutral (ADR-0043). Core adopts it with the shape change (draft core issue). |
| F10 | MED | Architect | `Pack` gains pack-level slots beyond its one recorded exception. | **accepted**: recorded in ADR-0043, class doc updated. |
| F11 | MED | Analyst | The built-in pack is seeded from core, and core's loaders refuse the sanitized shapes. Every built-in fix is core-first, then a re-seed. | **changed** (operator ruling on #186, 2026-10-28): the rewrite operates as if core does not exist, so the fixes are open-packs issues only and no core issue is drafted; seeding is open-packs' concern (spec C-006). |
| F12 | MED | Architect, Planner | Shared files with #194 (`file-kinds.yaml`, `expected-red.yaml`, `PackFileSet`, `WholePackStore`, `DomainRoundTrip`, `PackFileKind`). | **accepted**: append-only edits, and a reconcile task after #194 merges (spec C-002). |
| F13 | MED | Architect | ADR number: 0041 is reserved for #227, so 0042 is the next free one, but #194 may take it too. | **accepted**: first to merge keeps 0042, the other renumbers on rebase (plan). |
| F14 | LOW | Architect | The issue body says "keep the values of undeclared keys", which ADR-0036 ruling 1 supersedes. | **accepted**: the spec states that "lossless" is met by refusal (spec FR-011). |
| F15 | LOW | Analyst | The event-driven presets and the open-packs fixture are outside the gate (no `pack.yaml`). | **accepted**: drafted upstream, plus a charter test fixture pack exercises presets (spec FR-004). |
| F16 | LOW | Researcher | Core's manifest constituents omit step contracts, templates and skills. | **deferred_with_rationale**: membership is carried as read; membership rules belong to #110. |

## 3. Sanitized-shape rules applied

These rules are applied to the four pack-level storage objects (DSOs). They follow the operator's binding
requirement of 2026-10-28 and ADR-0036. They are recorded in ADR-0043.

- **S1. One identity name.** An object's identity field is `id` (the majority convention). It is never
  `name` and never a kebab-case variant. Where an object has a file of its own, the `id` equals the file stem.
- **S2. One version header.** An authored YAML object carries `schema_version`, a required string, `"1.0"`.
- **S3. One name per concept.** A family of fields shares one pattern. Activation lists are all
  `activated_<plural>`, so `mission_type_activations` becomes `activated_mission_types`. The same identity is
  keyed the same way inside one object (manifest constituents and preset entries both use `id`).
- **S4. Closed objects.** `additionalProperties: false` at every level. There are no open maps in the
  pack-level DSOs.
- **S5. No retired, legacy, variant or always-null fields.** A field that belongs to another variant (the
  manifest's charter-bundle `charter`, the fetched `fetched_at`/`source_url`, the charter-only
  `provenance_path`) is not in the authored-pack shape. Neither is a field that duplicates another
  (`source_type` duplicates `pack.yaml`'s `pack_level`), nor provenance that is excluded from the hash
  anyway (`generated_at`/`generated_by`).
- **S6. One way to say a thing.** No two spellings of one meaning. A kind is switched off by leaving it out
  of `activated_kinds`, never by an empty list. The schema therefore refuses empty activation lists, which
  also keeps ADR-0036's "absent equals empty" rule true without an exception.
- **S7. Explicit types.** Every field has a JSON type. Hashes are 64 lowercase hex characters. Booleans are
  booleans.
- **S8. Derived data is declared.** A field computed from other content (a manifest hash) is declared
  derived in the ADR's per-kind table. The writer recomputes it, and the gate verifies it rather than
  comparing it to the source.

## 4. Proposed sanitized shapes

### `preset` (`presets/<id>.yaml`)

```yaml
schema_version: "1.0"          # required, const "1.0"
id: minimal                    # required, ^[a-z][a-z0-9]*(-[a-z0-9]+)*$, <= 64, == file stem
description: A small curated…  # required, minLength 1
activated_kinds: [directives, tactics]   # optional kind gate, unique, minItems 1, enum of 13 plurals
activated_directives:          # optional, minItems 1; items {id, must_have?}
  - id: DIRECTIVE_001
    must_have: true            # optional boolean, default false: the per-item must-have mark (ruling 1)
  - id: DIRECTIVE_010
activated_mission_types:       # optional, minItems 1; same item shape
  - id: software-dev
# also: activated_{tactics,styleguides,toolguides,paradigms,procedures,agent_profiles,
#                  mission_step_contracts,anti_patterns}
```

- A preset with at least one `must_have` item is a governing preset; one without any is a convenience
  preset. The required set is derived from the marks (ADR-0031 decisions 1 and 2), so the preset itself
  carries no second flag.
- `required_<plural>` keys are refused by the closed schema. Migrating `org-charter.yaml`'s lists is #206.
- An item `id` appears once per list (domain invariant).

### `pack-descriptor` (`pack.yaml`)

The fields are as today. The schema is closed (`additionalProperties: false`), and the description text is
corrected: `pack_level` is optional (`PACK_LEVEL_MISSING` covers its absence), and `pack_version` is required
at every level. No ULID pattern is added, because the contract scenarios use `pack_id: qa`.

### `pack-manifest` (`pack-manifest.yaml`)

```yaml
schema_version: "1.0"                  # required
manifest_hash: <64 hex>                # required, derived
constituents:                          # required (may be empty)
  - kind: directive                    # artifact kind token
    id: DIRECTIVE_001
    path: directives/001-architectural-integrity-standard.directive.yaml   # pack-relative POSIX
    content_hash: <64 hex>             # derived
presets:                               # optional
  - id: minimal
    path: presets/minimal.yaml
    content_hash: <64 hex>             # derived
```

- `content_hash` is SHA-256 over the file's bytes with CRLF/CR → LF, as core does today.
- `manifest_hash` is SHA-256 over the UTF-8 RFC 8785 (JCS) canonical JSON of the manifest without
  `manifest_hash`.

### `pack-document` (`pack.md`)

A pack-level payload of the `bytes` form, through a `payloads` rule. It has no schema, because it has no
fields.

## 5. Divergence table (pinned content against the sanitized shape)

| ID | File | Field / shape | Rule | Required change | Gate |
|---|---|---|---|---|---|
| D-P1 | built-in `presets/default.yaml`, `presets/minimal.yaml`; event-driven `presets/event-driven.yaml`; open-packs fixture `tests/fixtures/valid/example-pack/presets/example.yaml` | `name:` is the identity | S1 | Rename `name` to `id` | known-failure (built-in) |
| D-P2 | the same four files | no `schema_version` | S2 | Add `schema_version: "1.0"` | known-failure |
| D-P3 | built-in `default.yaml:10`, `minimal.yaml:12` | `mission_type_activations` | S3 | Rename to `activated_mission_types` | known-failure |
| D-P4 | all four presets | list items are bare strings | ruling 1, S7 | Items become `{id: <id>}`, plus `must_have: true` where the item is required | known-failure |
| D-P5 | built-in `minimal.yaml:19-23` | `activated_directives` lists file stems (`001-architectural-integrity-standard`, …), where every other list uses artifact ids (`DIRECTIVE_001`; event-driven `EVD_001`) | S1, S6 | Use `DIRECTIVE_001`, `DIRECTIVE_010`, `DIRECTIVE_024`, `DIRECTIVE_028`, `DIRECTIVE_030` | known-failure |
| D-M1 | built-in `pack-manifest.yaml:1`, `:1375`, `:1388`, `:7` (×272) | `charter:`, `fetched_at:`, `source_url:` and `provenance_path:`, all null | S5 | Drop them from the authored-pack manifest | known-failure |
| D-M2 | built-in `pack-manifest.yaml:1376-1377` | `generated_at:` (null), `generated_by:` | S5 | Drop both (provenance lives in the pin) | known-failure |
| D-M3 | built-in `pack-manifest.yaml:1387` | `source_type: built-in` duplicates `pack.yaml` `pack_level` | S5 | Drop | known-failure |
| D-M4 | built-in `pack-manifest.yaml:1381`, `:1384` | preset entries keyed `name`, constituents keyed `id` | S1, S3 | Rename to `id` | known-failure |
| D-M5 | built-in `pack-manifest.yaml:1386` | `schema_version: '1'` | S2 | `"1.0"` | known-failure |
| D-M6 | built-in `pack-manifest.yaml:1378` | `manifest_hash` over core's ruamel canonical YAML | S8, F9 | Recompute over RFC 8785 canonical JSON | known-failure |

No divergence was found in `built-in/pack.yaml`, `internal/pack.yaml` (both fit the closed schema) or
`built-in/pack.md` (bytes). List style, comments and folded scalars carry no meaning (ADR-0036).

Every divergence is an open-packs issue only. The operator's ruling on #186 (2026-10-28) has the rewrite operate as if
core does not exist: no core issue or "core first" step is drafted, and that `packs/built-in/` is seeded from core today
is open-packs' concern. The draft issues are in [`draft-upstream-issues.md`](draft-upstream-issues.md).

## 6. Already-merged DSOs that break the sanitized-shape rule (epic #186 follow-up, not changed here)

1. `agent-profile` is kebab-case (`profile-id`, `schema-version`, `directive-references`) where every other
   kind is snake_case, and it mixes the two internally (`applies_to_languages`).
2. Display and summary fields are split: `title` vs `name`, and `summary` / `purpose` / `intent` /
   `description`.
3. `schema_version` takes six forms: required string, `const "1.0"` (skill), optional kebab-case
   (agent-profile), integer `2` (org-charter), `"2.0.0"` (charter), and absent (asset, glossary-pack,
   org-fragment).
4. References take four forms: `references` as `array<string>` (styleguide, toolguide), `references` as
   `array<object>` (tactic, paradigm), `directive_refs` (paradigm), and `directive-references`
   (agent-profile).
5. `path` is anchored differently by kind: relative to `assets/` in `asset.schema.json`, relative to the
   pack root elsewhere. This is the root cause of open-packs#75.
6. Optional lists default to `null` in glossary-pack and to `[]` elsewhere.
7. `org-charter.schema.json` still has the legacy `required_<plural>` lists (#206).
8. `charter.schema.json` has `mission_type_activations` (where `activated_<plural>` is the pattern), and
   its `activated_*` set omits `anti_patterns`.
9. agent-profile `excluding` is `anyOf[array, object, null]`, an ad-hoc union.
10. graph-shard requires `generated_at`, whose only value in the packs is the placeholder `STATIC`.

## 7. Operator questions raised on #195

1. Preset shape: sanitized, with the per-item `must_have` mark now (the author's choice; the alternative is
   as written, with the mark in #206).
2. `contracts/charter/schemas/DiagnosticCode.yaml`: may this PR change the `pack_unknown_key` description to
   "retired, never raised", with a contract CHANGELOG line? Until the operator answers, `contracts/charter/**`
   is untouched.
3. Manifest: confirm the sanitized authored-pack shape and the RFC 8785 `manifest_hash`. Core adopts both
   (draft core issue).
4. The known-failure loader admits the `#TBD-<slug>` placeholder until the parent session files the issues.
