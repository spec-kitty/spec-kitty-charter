---
title: "ADR-0043: Pack-level files: sanitized storage shapes, presets with must-have items, and the derived manifest"
description: How presets, pack.yaml and pack-manifest.yaml are stored, read and written; how a preset marks its must-have items; and how the manifest's hashes are derived.
doc_status: draft
updated: 2026-10-28T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0031-requirement-is-authored-as-a-preset
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - adr/0040-content-form-and-payload-kinds
  - architecture/logical-model
  - architecture/descriptor-schemas
  - conformance/divergence-register
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before touching how the store reads or writes presets, pack.yaml or pack-manifest.yaml, or how the gate checks them.
  - profile: pack-author
    guidance_text: Read this for the shape a preset and a manifest must have, how to mark a must-have item, and why the built-in files are refused.
  - profile: governance-lead
    guidance_text: Read this for how a preset states what every project must take, and how a governing preset is told from a convenience one.
  - profile: core-maintainer
    guidance_text: Read this for the sanitized preset and manifest shapes, the RFC 8785 manifest hash, and validate mode's new findings.
---

# ADR-0043: Pack-level files: sanitized storage shapes, presets with must-have items, and the derived manifest

## Status

**Proposed** — 2026-10-28. Decider: Stijn Dejongh (operator). Records the operator rulings of
2026-10-27 and 2026-10-28 on [#195](https://github.com/spec-kitty/spec-kitty-charter/issues/195)
(part of epic [#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)), and the
operator's ruling of 2026-10-28 on #186 that the rewrite operates as if core does not exist. The
mission's choices are held under `kitty-specs/pack-level-files-round-trip-01M60H1A/decisions/`. The
code #195 ships implements this record, so the points it amends are in force for that code. The
record stays Proposed pending the operator's acceptance, which the pull request for #195 asks for.

It answers [ADR-0031](/adr/0031-requirement-is-authored-as-a-preset/)'s two open questions and its
decision 6 (Accepted).

It supersedes in part [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)
(Accepted): decision 6 and open question 3 are answered, decision 3's follow-up is done, and the
per-kind table gains its `preset` and `pack-manifest` rows.

It extends [ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/) (Accepted):
decision 4's "pack-level files (#195)" is built for presets and the manifest (`pack.md` is pending,
WP08 and #194), decision 5's load returns the manifest, and the
write's undeclared-keys refusal is gone.

It extends [ADR-0040](/adr/0040-content-form-and-payload-kinds/) (Proposed): decision 9's
fingerprint order gains a pack-level tail.

Each of those records carries a dated amendment under its Status, with each point annotated where
it stands.

## Context

Issue #195 asks the whole-pack round trip to cover the four files that describe a pack as a whole:

- **activation presets**, `presets/<name>.yaml`;
- **the pack document**, `pack.md`;
- **the pack descriptor**, `pack.yaml`, written back without loss;
- **the pack manifest**, `pack-manifest.yaml`, read and regenerated on write.

Five facts force a decision:

- **The service held none of them.** Presets and the manifest were exclusions in
  `contracts/packs/file-kinds.yaml`. No part of the service modelled a preset. The source
  fingerprint covered none of them.
- **A preset could not carry the ruled mark.** The operator ruled on #195 that each preset item
  can be marked must-have. The pinned presets list bare strings, so they have nowhere to put it.
  Core's preset shape has no such mark either.
- **The pinned files are not designed shapes.** The presets name themselves with `name`, have no
  `schema_version`, and spell one list `mission_type_activations` where the rest are
  `activated_<plural>`. `minimal.yaml` lists directive file stems where every other list uses ids.
  The manifest merges three variants (an authored pack, a fetched pack and a charter bundle), so
  the authored pack carries the null fields of the other two.
- **The manifest's hashes describe bytes.** The service rewrites YAML in canonical form and drops
  comments (ADR-0036 ruling 2). Hashes carried as read would describe bytes that no longer exist.
  Core's `manifest_hash` is computed over its own YAML emitter's output, which ties every reader
  to that emitter.
- **`pack.yaml` was still open.** ADR-0036 decision 3 closed it on paper. The code still kept
  undeclared keys by name and dropped their values on write.

On 2026-10-28 the operator required that every storage object have a sanitized, deliberately
designed shape. The same day, on #186, the operator ruled that the rewrite operates as if core does
not exist: the shapes stand on their own merits, and a pinned file in another shape is an
open-packs issue only. Ground truth and probes are in the
[grounding squad](https://github.com/spec-kitty/spec-kitty-charter/blob/main/kitty-specs/pack-level-files-round-trip-01M60H1A/research/grounding-squad.md).

## Decision

### 1. The sanitized-shape rules

Every pack-level storage object follows eight rules. A storage object here is the persistence
shape of one file, its DSO.

| Rule | What it says |
|---|---|
| S1. One identity name | The identity field is `id`, never `name` or a kebab-case variant. Where the object has a file of its own, the `id` equals the file stem. |
| S2. One version header | An authored YAML object carries `schema_version`, a required string, `"1.0"`. |
| S3. One name per concept | A family of fields shares one pattern. All activation lists are `activated_<plural>`. One identity is keyed the same way everywhere in an object. |
| S4. Closed objects | `additionalProperties: false` at every level. No pack-level object has an open map. |
| S5. No retired, variant or always-null fields | A field of another variant, a field that repeats another, or provenance that no hash covers is not in the shape. |
| S6. One way to say a thing | No two spellings of one meaning. |
| S7. Explicit types | Every field has a JSON type. A hash is 64 lowercase hex characters. |
| S8. Derived data is declared | A field computed from other content is declared derived in the per-kind table. The writer recomputes it, and the gate verifies it rather than comparing it. |

The rules apply to the objects this record shapes. Already-merged objects that break them are not
changed here (see [Follow-up work](#follow-up-work)).

### 2. The preset shape, with a must-have mark per item

A preset is `presets/<id>.yaml`, validated by the closed schema
`contracts/packs/schemas/preset.schema.json`:

- `schema_version`: required, `"1.0"`;
- `id`: required, `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, at most 64 characters, equal to the file stem;
- `description`: required, not empty;
- `activated_kinds`: optional, the kind gate. It holds kind plurals from the 13 kinds, each once,
  at least one;
- ten optional lists: `activated_directives`, `activated_tactics`, `activated_styleguides`,
  `activated_toolguides`, `activated_paradigms`, `activated_procedures`,
  `activated_agent_profiles`, `activated_mission_step_contracts`, `activated_anti_patterns` and
  `activated_mission_types`. Each holds at least one item;
- an item is a closed object: `id` (required, not empty) and `must_have` (optional, `const: true`).

```yaml
schema_version: "1.0"
id: house-rules
description: What every project of the org must take, and a few suggestions.
activated_directives:
  - id: DIRECTIVE_001
    must_have: true
  - id: DIRECTIVE_010
activated_mission_types:
  - id: software-dev
```

This answers ADR-0031:

- **How a must-have item is marked (decision 6).** Per item, with `must_have: true`. An item
  without the key is not must-have. There is no `false` spelling (S6).
- **Telling a governing preset from a convenience preset (open question 2).** It is derived. A
  preset with at least one must-have item governs. One with none is a convenience preset. The
  preset carries no flag of its own, so it cannot say one thing two ways.
- **The compatibility reading of `required_*` (open question 1).** A preset never had
  `required_<plural>` lists, and the closed schema refuses any such key. Migrating
  `org-charter.yaml`'s lists into presets, and then refusing them there, is
  [#206](https://github.com/spec-kitty/spec-kitty-charter/issues/206).

In the domain a preset is a value, not an artifact. Core gives a preset no URN and no kind.
`Preset(id, description, activatedKinds, artifacts, missionTypes)` holds `PresetItem(id, mustHave)`
lists, in authored order. It derives three things:

- `required()`: the must-have item ids of each artifact kind; a kind with none is absent;
- `requiredMissionTypes()`: the must-have mission-type ids, kept apart because a mission type is
  not an `ArtifactKind`;
- `governing()`: true when either is not empty.

Its invariants refuse an id outside the grammar, an id twice in one list, a kind twice in the gate,
an empty list, a blank description, and a list of a kind presets do not govern. Each message names
the preset, and, where there is one, the offending kind or id.

The store checks that a preset's `id` is its file stem through one helper,
`PresetFiles.stemMismatch`. The load records `preset id '<id>' is not its file stem '<stem>'`
against the file. Validate mode uses the same helper (decision 7).

### 3. An empty activation list is refused

Every activation list has `minItems: 1`. A kind is switched off one way only: by leaving it out
of `activated_kinds`. A preset that selects nothing of a kind leaves its list out.

In core, `[]` means "activate none" and an absent list means "unrestricted". ADR-0036 makes an
absent list equal to an empty one. Refusing the empty spelling keeps both true, with no exception
in the gate's comparator. The manifest's `presets` list follows the same rule.

### 4. `pack.yaml` is closed

ADR-0036 decision 3's follow-up is done. `pack-descriptor.schema.json` is closed. Its description
now says that `pack_level` is optional and `pack_version` is required at every level. An unknown
key, a retired one such as `accompanies_doctrine_pack` included, makes `pack.yaml` unreadable: the
pack is refused as `pack_unreadable`, `pack.yaml of <pack>: unknown key '<name>'`.

`PackDescriptor.undeclaredKeys`, the `PACK_UNKNOWN_KEY` finding, its Java enum value and the
whole-pack write's undeclared-keys refusal are removed. `pack_unknown_key` stays in the contract's
`DiagnosticCode` enum as a retired value, never raised. Its description says so, with a contract
`CHANGELOG` line and no shape change (operator approval on #195, 2026-10-28). DIV-005 and DIV-006
are retired. No ULID pattern is added to `pack_id`: the contract scenarios use `pack_id: qa`.

### 5. The manifest: the authored-pack variant, with derived hashes

**The shape.** `pack-manifest.yaml` is validated by the closed schema
`contracts/packs/schemas/pack-manifest.schema.json`. It is the authored-pack variant only:

- `schema_version`: required, `"1.0"`;
- `manifest_hash`: required, derived;
- `constituents`: required, possibly empty. Each entry is `{kind, id, path, content_hash}`, all
  required, `kind` an artifact kind token, `content_hash` derived;
- `presets`: optional, at least one entry. Each entry is `{id, path, content_hash}`, all required.
  The schema holds `path` to the pattern `presets/<id grammar>.yaml`. Nothing checks yet that it
  names the entry's own `id`.

The fetched-pack and charter-bundle fields are not in it: `charter`, `fetched_at`, `source_url`,
`source_type`, `generated_at`, `generated_by` and each constituent's `provenance_path` (S5).

**The recipes.** Both are language-neutral, so any reader can reproduce them whatever YAML emitter
wrote the files:

| Derived field | Recipe |
|---|---|
| `content_hash` | SHA-256, lowercase hex, of the file's bytes after every CRLF, then every lone CR, is replaced by LF. |
| `manifest_hash` | SHA-256, lowercase hex, of the UTF-8 bytes of the RFC 8785 (JCS) canonical JSON of `{schema_version, constituents: [{content_hash, id, kind, path}], presets: [{content_hash, id, path}]}`, without `manifest_hash`. `presets` is left out when the manifest lists none, as the YAML leaves it out. |

The JCS writer is narrow on purpose: every value is a string, a list or an object of fixed keys,
so canonical number output is never needed.

**Where each side is handled.**

- **Read.** `WholePackStore` is the manifest's only owner. Its load reads the manifest through the
  serialization strategy and returns it on `LoadedPack.manifest`. An unreadable manifest is a
  violation on the aggregate, so the whole-pack write refuses the pack. `FilesystemPackStore` and
  `DomainRepositoryService` never parse it, so a registration pays nothing for it. The reader
  never checks the derived fields, as core does not.
- **Write.** The write stages every other file first. It then recomputes every derived field from
  the staged bytes and writes the manifest last. A listed path that the write did not stage
  refuses the write, before anything reaches the target: `pack-manifest.yaml of <pack> lists
  <path>, which the write does not hold`.
- **Verify.** The gate verifies the derived fields on both sides. Its comparator compares the rest
  by meaning and skips only `manifest_hash` and each entry's `content_hash`.

`PackManifests.stale(read, bytesAt)` does the verification. It checks each side against its own
bytes. A `content_hash` is checked against its file. The `manifest_hash` is checked against the
manifest's own fields as read. So a manifest copied beside rewritten files flags only the
`content_hash` fields of those files. The plan had `stale(read, recomputed)`; taking the bytes lets
one walk serve the check, and the WP05 review accepted it.

**Membership is carried as read.** The write keeps the listed constituents and presets. Which files
should be constituents, and restamping the manifest after a single-artifact registration, need the
enumeration rules that [#110](https://github.com/spec-kitty/spec-kitty-charter/issues/110) owns.
Until then, a registration leaves the presets byte-identical and the manifest stale.

### 6. Kinds without a schema

This answers ADR-0036 decision 6 and open question 3. Every file the round trip covers is one of
three things:

- a `files` rule with a closed schema (content form `yaml`);
- a `payloads` rule of a built form other than `yaml` (ADR-0040);
- an exclusion, with its reason.

A YAML file is never held without a schema. A derived field is declared per kind in a record, never
added as a reader rule. The manifest's `content_hash` is recomputed on write (decision 5), not
compared by bytes.

`file-kinds.yaml` gains two `files` rules, `*/presets/*.yaml` (`preset`) and
`*/pack-manifest.yaml` (`pack-manifest`). The exclusions `**/presets/*.yaml` and
`*/pack-manifest.yaml` are retired. The preset rule is narrower than the old exclusion: no pinned
preset is nested, and a file may match only one rule.

### 7. Validate mode agrees with the load

A preset or manifest that breaks its schema, or a preset whose `id` is not its file stem, is
`ARTIFACT_SCHEMA_ERROR` in validate mode, as ADR-0036 decision 3 has it for every other
schema-mapped file. The stem check calls the store's helper, so both refuse the same file. Validate
mode renders it as `'id' has an unsupported value '<id>'`. A preset is left out of the inspector's
artifact kinds: it declares an `id`, but it is not an artifact.

On the built-in pack at the pin, validate mode now reports findings on exactly three files:
`pack-manifest.yaml` (283), `presets/minimal.yaml` (10) and `presets/default.yaml` (4), 297 in all.
Main found none. No operation serves validate mode (`PackValidation` has no controller), so nothing
changes on the wire.

**A known gap.** A break that only the domain sees passes validate mode while the load refuses it.
An item id listed twice in one list is one: the schema cannot say "unique by `id`", and validate
mode does not build the domain value. This record covers schema breaks and the stem rule only.

**A divergence from core.** Core's `validate_pack` checks presets in its own shape, resolves their
ids and checks their kind gate, and does not check the manifest. The service holds both to these
shapes and does neither of core's preset checks. The divergence is DIV-301, in a block of its own for #195, in the
[divergence register](/conformance/divergence-register/).

### 8. `pack.md` uses #194's `markdown` content form (pending)

`pack.md` is held through the `markdown` content form that sibling
[#194](https://github.com/spec-kitty/spec-kitty-charter/issues/194) builds in its first pull
request (`DM-01M60HM8BV7CE17HYE1QS829K4`, which supersedes this mission's earlier `bytes` choice).
This mission builds no form of its own. Its `pack.md` work package (WP08) comes last and is not
built yet: #194's pull request has not opened. Until it lands, `other:pack.md` stays on the gate's
expected-red list.

### 9. The fingerprint order

`PackFileSet.digested` lists, in order:

1. `pack.yaml`;
2. every file of every bound kind, each followed by its companions;
3. the root graph shards and `drg/fragment.yaml`;
4. the asset files, then the templates;
5. the pack-level tail, in one ordered step: the presets in path order, then `pack-manifest.yaml`.

When `pack.md` lands (decision 8), it joins the tail between the presets and the manifest. A pack
with no presets and no manifest keeps its exact digest list and fingerprint. A pack that holds them
gets a new `Charter-Source-Fingerprint` and ETag once, as binding any kind does (ADR-0037).

The constants live with their owners: `PresetFiles.PRESETS` and `PackFileSet.MANIFEST`. The
mission's data model placed both in `PackFileSet`.

### 10. The `Pack` slots, and the rule of three

The `Pack` aggregate holds its presets in a slot of its own, `presets()`, sorted by id. Two presets
with one id breach an invariant. `toBuilder` and `with` keep them. The presets are keyed by id, so
ADR-0037's "the kind entities stay free of paths" still has one exception only, the asset files.

The manifest is not in the aggregate. It holds file paths and hashes, which `Pack` holds by design
only for asset files. It sits beside the aggregate on `LoadedPack`, as the graph documents do
(ADR-0037 decision 5). `LoadedPack` keeps a four-argument constructor that means "no manifest".

Asset files (ADR-0040), presets, and the pack document to come are three pack-level slots beside the
kind map. The rule of three has fired. Extracting them into one `PackLevelContent` value is a
follow-up on epic #186. It is not done here, because #194 edits `Pack` at the same time.

### Per-kind table

The rows this record adds to ADR-0036's per-kind table:

| Files (`match`) | Schema | Compared as | Sets (member identity) | Derived fields | Unknown fields refused |
|---|---|---|---|---|---|
| `*/presets/*.yaml` | preset | YAML | none: every list is a sequence | none | yes |
| `*/pack-manifest.yaml` | pack-manifest | YAML, less its derived fields | none: `constituents` and `presets` are sequences | `manifest_hash`; each entry's `content_hash`. Recomputed on write and verified by the gate on each side, never compared | yes |
| `*/pack.yaml` | pack-descriptor | YAML | none | none | yes: the schema is closed (decision 4) |

### The gate and the pinned files

The whole-pack gate models the kinds `preset` and `pack-manifest`. Its `REQUIRED_KINDS` gains
exactly those two, and its expected-red list loses `other:presets` and `other:pack-manifest.yaml`.
The gate adapter writes presets in place. It restamps the manifest over a pairing: each constituent
`(kind, id)` is paired with the file the adapter wrote for that artifact, and each preset path with
itself. A listed file the adapter did not write, such as a constituent of a kind the registry does
not bind, refuses the manifest: `pack-manifest.yaml lists <path>, which the round trip did not
write`.

The pinned built-in presets and manifest are not in the sanitized shapes, so they are refused on
read. Under ADR-0036 decision 5 they are pack inconsistencies, fixed in open-packs. They are pinned
on the known-failure list:

| Gate kind | Known failures | Issue key |
|---|---|---|
| `preset` | `presets/default.yaml`, `presets/minimal.yaml` | `spec-kitty/spec-kitty-open-packs#TBD-preset-shape` |
| `pack-manifest` | `pack-manifest.yaml` | `spec-kitty/spec-kitty-open-packs#TBD-manifest-shape` |

Both keys are placeholders for drafted issues. The known-failure loader admits exactly these two
slugs (`WholePackExpectation.PENDING_DRAFTS`). The hand-off files the issues, replaces the keys with
their numbers, and removes the two slugs. Each entry's `at` is the seam test's violation text,
`refused: <reason>`, character for character. The seam test pins nine refusals on the built-in
pack: the six asset sidecars of ADR-0040 and these three. A copy of the built-in pack with the
sidecars fixed and the three files in the sanitized shapes loads with no violation, writes whole,
and shows no stale derived field on either side.

The manifest's pinned `at` joins all 283 of its reasons, about 24 KB. Whether to shorten it is an
[open question](#open-questions).

Until #194 binds procedures, the manifest stays red after its fix. A corrected manifest still lists
the built-in procedures, which the round trip cannot write yet. Its entry's reason says so, and the
corrected copy lists only the modelled constituents until #194 lands.

### Divergences from the sanitized shapes

| ID | File | Field or shape | Rule | Required change |
|---|---|---|---|---|
| D-P1 | the built-in `default.yaml` and `minimal.yaml`, event-driven `event-driven.yaml`, the open-packs fixture `example.yaml` | `name:` is the identity | S1 | Rename `name` to `id` |
| D-P2 | the same four files | no `schema_version` | S2 | Add `schema_version: "1.0"` |
| D-P3 | built-in `default.yaml`, `minimal.yaml` | `mission_type_activations` | S3 | Rename to `activated_mission_types` |
| D-P4 | all four presets | list items are bare strings | the must-have ruling, S7 | Items become `{id: <id>}`, with `must_have: true` where required |
| D-P5 | built-in `minimal.yaml` | `activated_directives` lists file stems, where every other list uses ids | S1, S6 | Use `DIRECTIVE_001`, `DIRECTIVE_010`, `DIRECTIVE_024`, `DIRECTIVE_028`, `DIRECTIVE_030` |
| D-M1 | built-in `pack-manifest.yaml` | `charter`, `fetched_at`, `source_url` and each `provenance_path`, all null | S5 | Drop them |
| D-M2 | the same | `generated_at` (null), `generated_by` | S5 | Drop both: provenance lives in the pin |
| D-M3 | the same | `source_type` repeats `pack.yaml`'s `pack_level` | S5 | Drop it |
| D-M4 | the same | preset entries keyed `name`, constituents keyed `id` | S1, S3 | Key both by `id` |
| D-M5 | the same | `schema_version: '1'` | S2 | `"1.0"` |
| D-M6 | the same | `manifest_hash` over core's canonical YAML | S8 | Recompute over RFC 8785 canonical JSON |

The event-driven preset and the fixture are outside the gate: their packs have no `pack.yaml`. The
pinned `pack.yaml` files and `pack.md` diverge from nothing. The upstream issues are drafted as
OP-1 (`TBD-preset-shape`: D-P1 to D-P5) and OP-2 (`TBD-manifest-shape`: D-M1 to D-M6), in the
[draft upstream issues](https://github.com/spec-kitty/spec-kitty-charter/blob/main/kitty-specs/pack-level-files-round-trip-01M60H1A/research/draft-upstream-issues.md).
Under the ruling on #186 they are open-packs issues only. No core issue is drafted. That
`packs/built-in/` is seeded from core today is open-packs' concern.

## Consequences

### Positive

- An org can say what every project must take, in the file where it already says what to select.
  The required set and "governing" are derived, so neither is written twice.
- Each pack-level file has one closed shape. The service refuses the rest, with no tolerance logic.
- The manifest describes the bytes the service wrote. Any language can reproduce its hashes.
- An unknown `pack.yaml` key is refused, as in core, instead of being dropped on write.
- A change to a preset or to the manifest changes the pack's source fingerprint.
- Validate mode and the load refuse the same schema-breaking files.

### Negative

- The built-in presets and manifest stay red until open-packs adopts the shapes (OP-1, OP-2) and
  `openPacksRef` moves. The manifest then stays red until #194 binds procedures.
- Built-in validate mode goes from 0 to 297 findings, on three files.
- A pack with an unknown `pack.yaml` key is now unreadable where it loaded with a warning.
- Packs that hold presets or a manifest get a new fingerprint and ETag once.
- A registration leaves the manifest stale until #110 decides regeneration.
- Validate mode passes a preset that breaks only a domain invariant, such as a duplicated item id
  (decision 7).
- `Pack` has two pack-level slots now, and a third is coming, before the extraction.
- The load's cost was measured on the internal pack only, which holds no manifest: its whole-pack
  load took 43 ms before and 45 ms after, against a budget of 0.5 s. The built-in pack's load with
  its manifest was not timed separately, so NFR-004 stays open.

### Risks

- The placeholder keys could merge as they are. The loader admits only the two named slugs, and
  the hand-off lists "no `#TBD-` in the merge diff" as a pre-merge check.
- Core keeps its own preset and manifest shapes until the service is incorporated. A pack written
  for one may be refused by the other in the meantime.
- The JCS writer is narrow. A field that is not a string, a list or an object would need number
  canonicalization first.

### Follow-up work

- File OP-1 and OP-2, replace the placeholder keys, and remove the two slugs from the loader.
- WP08: `pack.md` through #194's `markdown` form, once #194's first pull request merges.
- #206: migrate `org-charter.yaml`'s `required_<plural>` lists into presets, then refuse them.
- #110: manifest membership and restamping after a registration.
- Epic #186: extract `PackLevelContent` (decision 10).
- Epic #186: merged storage objects that break the sanitized-shape rules. They are listed in the
  grounding squad's §6 and not changed here:
  - `agent-profile` is kebab-case, and mixes in snake_case;
  - display and summary fields are split (`title` and `name`; `summary`, `purpose`, `intent` and
    `description`);
  - `schema_version` takes six forms across kinds, and is absent from three;
  - references take four forms;
  - `path` is anchored at `assets/` for an asset and at the pack root elsewhere
    (open-packs#75);
  - optional lists default to `null` in the glossary pack and to `[]` elsewhere;
  - `org-charter.schema.json` keeps the legacy `required_<plural>` lists (#206);
  - `charter.schema.json` has `mission_type_activations`, and its `activated_*` set leaves out
    `anti_patterns`;
  - the agent profile's `excluding` is an ad hoc union of an array, an object and `null`;
  - the graph shard requires `generated_at`, whose only value in the packs is `STATIC`.

## Open questions

1. **The manifest's known-failure text.** Its pinned `at` holds all 283 joined reasons, about 24 KB.
   The operator was asked whether to pin a shorter form. Not answered yet.
2. **Acceptance.** This record is Proposed until the operator accepts it on #195.

## Alternatives considered

### Round-trip the presets as written, and add the mark in #206

Rejected (squad finding F5). The as-written shape cannot carry the mark, so #206 would break the
same files a second time.

### A preset-level `governing: true` flag

Rejected. It says one thing two ways (S6): a flag could contradict the items.

### A nested `selection:` map in the preset

Rejected. It renames the established `activated_<plural>` family for no gain.

### Allow `[]` and add a comparator exception for presets

Rejected (squad finding F1, spec C-005). The gate would need a per-kind rule, which is special logic
in disguise (ADR-0036 risks).

### Carry the manifest's hashes as read

Rejected (squad finding F3). The manifest would describe bytes that no longer exist.

### Compare the constituents by bytes

Rejected. It contradicts ADR-0036 ruling 2 for YAML files.

### Port core's YAML emitter for `manifest_hash`

Rejected (squad finding F9). A byte-exact port of one emitter is brittle, and ties every reader to
it. RFC 8785 is a standard.

### Hold the manifest in the `Pack` aggregate

Rejected. It holds paths and hashes, and `Pack` holds no paths by design except its asset files.

### A `preset` artifact kind with URNs

Rejected. It invents an identity core does not have, and widens the kind vocabulary that
`VocabularyEqualityTest` pins.

### Hold `pack.md` as a `bytes` payload

The mission's first choice. Superseded by the coordination with #194: one owner per content form,
and no second pack-level bytes kind.

## References

- Operator rulings: [#195](https://github.com/spec-kitty/spec-kitty-charter/issues/195)
  (2026-10-27, 2026-10-28); [#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)
  (2026-10-28).
- Mission: `kitty-specs/pack-level-files-round-trip-01M60H1A/` (`spec.md`, `plan.md`,
  `research.md`, `data-model.md`, `decisions/`).
- `contracts/packs/schemas/preset.schema.json`, `pack-manifest.schema.json`,
  `pack-descriptor.schema.json`; `contracts/packs/file-kinds.yaml`.
- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/`: `Preset.java`,
  `PresetItem.java`, `Pack.java`.
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/`: `PresetFiles.java`,
  `PresetYaml.java`, `PackManifests.java`, `PackManifestYaml.java`, `WholePackStore.java`,
  `PackFileSet.java`, `FileSystemPackInspector.java`.
- `service/packs/src/test/resources/whole-pack/known-failures.yaml`.
- [RFC 8785: JSON Canonicalization Scheme](https://www.rfc-editor.org/rfc/rfc8785).
- [ADR-0031](/adr/0031-requirement-is-authored-as-a-preset/),
  [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/),
  [ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/),
  [ADR-0040](/adr/0040-content-form-and-payload-kinds/).
