---
title: "ADR-0042: Pack-level files: sanitized storage shapes, presets with must-have items, and the derived manifest"
description: How presets, pack.yaml and pack-manifest.yaml are stored, read and written; how a preset marks its must-have items; and how the manifest's hashes are derived.
doc_status: active
updated: 2026-11-12T00:00:00Z
decision_status: 'Accepted; extended in part by ADR-0043 (2026-10-29, proposed: S1 to S8 applied to artifact kinds, S9 to S16 added); decision 2 superseded in part by ADR-0050 and decision 4 by ADR-0048 (2026-11-02); decision 2 amended 2026-11-07 (the kind gate removed from presets, any kind listed); decisions 8 and 9 amended 2026-11-11 (README.md is the pack document; pack.md, PROVENANCE.md and org-charter.yaml retired, #451, #431); decision 5 amended 2026-11-12 (manifest membership derived and restamped on every write, #110)'
decision_date: '2026-10-28'
type: ADR
kind: ADR
related:
  - adr/0031-requirement-is-authored-as-a-preset
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - adr/0040-content-form-and-payload-kinds
  - adr/0073-real-packs-register-as-they-are
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

# ADR-0042: Pack-level files: sanitized storage shapes, presets with must-have items, and the derived manifest

## Status

**Accepted** — 2026-10-29 (proposed 2026-10-28). Decider: Stijn Dejongh (operator). Records the operator rulings of
2026-10-27 and 2026-10-28 on [#195](https://github.com/spec-kitty/spec-kitty-charter/issues/195)
(part of epic [#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)), and the
operator's ruling of 2026-10-28 on #186 that the rewrite operates as if core does not exist. The
mission's choices are held under `kitty-specs/pack-level-files-round-trip-01M60H1A/decisions/`. The
code #195 ships implements this record, so the points it amends are in force for that code. The
operator accepted this record on 2026-10-29, the acceptance the pull request for #195 asked for.

It answers [ADR-0031](/adr/0031-requirement-is-authored-as-a-preset/)'s two open questions and its
decision 6 (Accepted).

It supersedes in part [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)
(Accepted): decision 6 and open question 3 are answered, decision 3's follow-up is done, and the
per-kind table gains its `preset` and `pack-manifest` rows.

It extends [ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/) (Accepted):
decision 4's "pack-level files (#195)" is built for presets and the manifest (`pack.md` was split off to
[#260](https://github.com/spec-kitty/spec-kitty-charter/issues/260), which built it on 2026-10-30; see the amendment below),
decision 5's load returns the manifest, and the write's undeclared-keys refusal is gone.

It extends [ADR-0040](/adr/0040-content-form-and-payload-kinds/) (Proposed): decision 9's
fingerprint order gains a pack-level tail.

Each of those records carries a dated amendment under its Status, with each point annotated where
it stands.

### Amendment 2026-10-30 (#260): `pack.md` is held, decision 8 is done

Made by [#260](https://github.com/spec-kitty/spec-kitty-charter/issues/260) (part of epic
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)), mission
`pack-document-round-trip-01M65MBW`, after #194's first pull request built the `markdown` content
form. The decision text below is unchanged, and each point this note changes is annotated where it
stands. #260 cited these decisions as "ADR-0043 decisions 8 to 10"; they are this record's: 0043
was reserved for #195 (`DM-01M60HM8BV7CE17HYE1QS829K4`) before the numbers changed.

- **Decision 8 is done.** `*/pack.md` is a `files` rule of `content_form: markdown` with the
  structure schema `pack-document` (optional frontmatter, an optional title, a free preamble and
  free H2 sections). The store reads it through #194's codec into the `Pack` aggregate as a domain
  `PackDocument` (the domain cannot hold the infra Markdown model, ADR-0006, so the value keeps the
  model's shape and an infra mapper converts it, as #194 did for the toolguide guide;
  `DM-01M65MKX2FG5E5SAM9D57BX2AE`). `WholePackStore` writes it in the canonical form before the
  manifest, and validate mode reads it through the same read. A refused `pack.md` (a carriage
  return, a byte order mark, an ambiguous heading) is a violation on its file; it is never
  normalized. The gate models it as `pack-document`, and `other:pack.md` is off the expected-red
  list: built-in `pack.md` is green.
- **Decision 9, the order.** The tail is the presets in path order, then `pack.md`, then
  `pack-manifest.yaml`. A `pack.md` is digested as text, whatever the load makes of it. A pack
  without one keeps its digest list; the built-in pack gets a new fingerprint and ETag once.
- **Decision 10.** The pack document is the third pack-level slot, `Pack.document()`, kept by
  `toBuilder` and `with`. The `PackLevelContent` extraction stays a follow-up on #186.
- **The document's title and the descriptor's `name` are independent.** The H1 of `pack.md` is
  prose the author writes; nothing ties it to `pack.yaml`'s `name`, and the service checks neither
  against the other.

### Amendment 2026-11-07: a preset names any kind and carries no kind gate

Made by the layer-merge mission (`layer-merge-and-effective-reads-01M6R0J6`), PR A0, on the operator's rulings of
2026-11-06 for the one kind model (A7, decision `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`; Q3, decision
`DM-01M6RCY40CQQTHC130RWF43W0D`; Q4, decision `DM-01M6RCY8MYNGNPJ247M2Y4880N`). The decision text below is unchanged;
decision 2 carries a note where it changes, and decision 3's "off in `activated_kinds`" no longer has a gate to
switch a kind off.

- **`activated_kinds` is refused.** A preset carrying it refuses the pack as `500 pack_unreadable`, as the preset item id grammar does; a `422` on registration is planned for PR A, in
  `contracts/packs/schemas/preset.schema.json` and in validate mode. The activation document has no kind gate to set
  (ADR-0020 amendment of 2026-11-07), so a key for it would say nothing.
- **A preset may name any kind.** The schema gains `activated_skills` and `activated_glossary_packs`, so the
  exclusion of `skill` and `glossary_pack` (a core leftover) is gone and one kind registry serves activations, presets,
  packs and the contract.
- **`activated_mission_types` is a plain member list.** It is applied as `selected.mission_type`, like the list of any
  other kind, and a must-have mission type is on the required floor, as `MISSION_TYPE` is requirable.
  `requiredMissionTypes()` and the separate `Preset.missionTypes` slot go, as ADR-0050 already said.
- **Divergence.** DIV-211 of the [divergence register](/conformance/divergence-register/): core's preset may carry
  `activated_kinds` and excludes skills and glossary packs.

### Amendment 2026-11-11 (#451, #431): `README.md` is the pack document, and three files are retired

Made by slice B (mission `slice-b-real-packs-register-01M7649C`,
[#466](https://github.com/spec-kitty/spec-kitty-charter/issues/466)) on the operator's rulings of 2026-11-11 on
[#451](https://github.com/spec-kitty/spec-kitty-charter/issues/451) and
[#431](https://github.com/spec-kitty/spec-kitty-charter/issues/431). The calls the mission made beyond the rulings are
in [ADR-0073](/adr/0073-real-packs-register-as-they-are/). The decision text below is unchanged, and each
point this note changes is annotated where it stands.

- **Decision 8: the pack document is `README.md`.** `*/README.md` is the `files` rule of `content_form: markdown` with
  the structure schema `pack-document`, held as the pack's `PackDocument` exactly as `pack.md` was (the codec, the
  canonical write, validate mode's read, the gate's `pack-document` model). Only the root file is the document; a
  `README.md` deeper in the pack keeps the exclusions and payload rules that already cover it. The document is served
  at `GET /packs/{packId}/doc` (ADR-0073 decisions 6 to 9).
- **`pack.md` and `PROVENANCE.md` are retired.** Each is a root-anchored `retired` rule of `file-kinds.yaml`, refused
  by name, with a reason, in classification, the load, validate mode and registration (`422`, violation code
  `retired`). `PROVENANCE.md`'s content moves into a `## Provenance` section of the README. A root file that differs
  from a rule's file only in letter case is refused under that rule (ADR-0073 decision 3).
- **Decision 9: the tail.** The tail is the presets in path order, then `README.md`, then `pack-manifest.yaml`, then
  `policy.yaml` (ADR-0063), then the retired files. A `README.md` is digested as text, whatever the load makes of it.
- **The per-kind table.** The `*/pack.md` row reads `*/README.md`.
- **#206 is superseded.** `org-charter.yaml` is retired with its schema (#431): a root-anchored `retired` rule refuses
  it by name, and `pack.yaml` loses `active_charter`, which the closed descriptor now refuses as an unknown key. Its
  `required_<plural>` lists map to the must-have items of a governing preset (decision 6); nothing migrates or reads
  them. The conversion is in [Enforce presets on an organization's projects](/guides/enforce-org-presets/).

### Amendment 2026-11-12 (#110): the manifest's membership is derived, and every write restamps it

Made by [#110](https://github.com/spec-kitty/spec-kitty-charter/issues/110), the close-out of slice B
([#466](https://github.com/spec-kitty/spec-kitty-charter/issues/466)), as
[ADR-0074](/adr/0074-the-remaining-cutover-reads-and-their-paths/) decision 7 asked. It records the operator's rulings
of 2026-11-12 on three questions the Op (`01M78KRYD8EWWZ57FNW3T1Q7RA`) put. The decision text below is unchanged, and
decision 5 carries a note where it changes.

- **Membership is derived, not carried as read** (operator). `pack-manifest.yaml` lists one constituent per artifact
  the pack holds, as `{kind, id, path}` with its kind token, its id and the file it is stored in, and one preset entry
  per preset, at `presets/<id>.yaml`. Each list is in path order (by UTF-16 code unit). The rule lives in one place,
  `ManifestMembership`, and every writer uses it over one assembly, the whole-pack load's: "holds" means what that load
  reads, so a file it refuses or withholds (a read violation, a step enhancement withheld for a repeated stem) is not
  listed.
- **Every write restamps it** (ADR-0074 decision 7; operator: one rule for every writer). The writers are:
  - a whole-pack registration, on the staged tree after the files it does not hold are removed and before its
    revision is computed, so a re-upload of a pack stored since this rule is still `unchanged`;
  - a per-artifact `PUT` and a `DELETE`, which commit through one choke point (`PackCommitStore`), as does a write that
    only changes the registered relationships;
  - the store's whole-pack write (`WholePackStore.write`);
  - the round-trip gate's manifest step.

  The derived fields are then stamped from the written bytes, as decision 5 already did.
- **A manifest is always generated** (operator). A write to a pack that held none writes one, so every pack the
  service writes holds a manifest, and such a pack gets a new fingerprint and ETag once. Every example pack holds its
  derived manifest: `org-base` gained one, and `built-in`'s lists grew from 22 constituents to all 40 of its artifacts.
- **A registration refuses a manifest whose lists differ** (operator). An uploaded manifest that lists an entry the
  pack does not hold, or leaves out one it holds, is refused `422 validation_failed`, one violation per entry, code
  `manifest_membership`, at `pack-manifest.yaml`. On an otherwise clean pack, the gate refuses such a source manifest
  with the same reasons, and writes the derived lists, as the store's write does.

The calls the Op made beyond the rulings, open to review:

- **Order and hashes are not judged.** The entries are compared as sets, and the write writes them in path order. The
  derived hashes are restamped, as decision 5 already held for the reader. The lists' order is therefore a derived
  field: the gate's comparator compares each list in path order (ADR-0036's `pack-manifest.yaml` row, amended the same
  day).
- **A `pack-manifest.yaml` that is not a regular file is refused.** A registration writes the manifest at that path, so
  a directory or a link there is refused `422` (`unreadable`) before the stamp.
- **The check runs on an otherwise clean upload.** An artifact refused for its own fault would also read as left out,
  so the membership check is made only when the upload has no other violation.
- **The general load does not check membership.** `WholePackStore.load` is unchanged, so a stored pack whose manifest
  predates this rule still loads; its next write restamps it.
- **Companions are not constituents.** A constituent is one artifact at its own file, as the schema's `{kind, id,
  path}` has it. A skill body, an asset payload or a guide is not listed, so editing one changes no `content_hash`
  ([ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/) left this to #110). Listing them
  would need a new entry shape in the manifest schema.
- **What it costs.** Every per-artifact write now loads the staged copy whole once more, to derive the lists, and reads
  the listed files to hash them; a `PUT` paid only the copy and the digest before. A registration loads the staged tree
  once more as well.
- **A pack stored before this rule changes revision on its next write.** A re-upload of such a pack, unchanged, at the
  same `pack_version`, is no longer `unchanged`: the staged tree gains the stamped manifest, its revision differs from
  the stored one, and the version rule refuses it `409` (ADR-0065). It needs a new `pack_version`, or any write first.
  No reader of the older state is kept (charter: the model is canonical).
- **Not done: validate mode.** Validate mode inspects each file on its own and does not assemble the pack, so it has no
  derived lists and does not report a membership difference. A registration does.

## Context

Issue #195 asks the whole-pack round trip to cover the four files that describe a pack as a whole:

- **activation presets**, `presets/<name>.yaml`;
- **the pack document**, `pack.md` *(`README.md` since the amendment of 2026-11-11, #451)*;
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
| S4. Closed objects | `additionalProperties: false` at every level. No pack-level object has an open map. *Amended 2026-11-02 (operator, #156): two kinds of value are open by declaration, each named in its kind's schema: a value that is itself a JSON Schema document (a guard's `config_schema`, checked at load against the Draft 2020-12 meta-schema and compiled offline), and a value whose shape another artifact's declared schema gives (a guard entry's `config`, checked at load against its guard's `config_schema`). Everything around them stays closed.* *Amended 2026-11-03 (operator, #156): a `config_schema` is also refused when it holds a reference keyword (`$ref`, `$dynamicRef`, `$dynamicAnchor`, `$recursiveRef`, `$recursiveAnchor`, `$anchor`) anywhere. Its patterns are evaluated by re2j in linear time, and a pattern RE2 cannot compile (a backreference, a lookaround) refuses the schema.* |
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
  at least one; *(amended 2026-11-07: refused, there is no kind gate)*;
- ten optional lists: `activated_directives`, `activated_tactics`, `activated_styleguides`,
  `activated_toolguides`, `activated_paradigms`, `activated_procedures`,
  `activated_agent_profiles`, `activated_mission_step_contracts`, `activated_anti_patterns` and
  `activated_mission_types` *(amended 2026-11-07: and `activated_skills` and `activated_glossary_packs`; a preset may
  name any kind)*. Each holds at least one item;
- an item is a closed object: `id` (required, not empty) and `must_have` (optional, `const: true`).

The kind gate and the selection lists are independent fields: a preset may gate a kind it lists no
items for, or list items for a kind it does not gate. This shape does not tie them; reconciling the
gate against the selections is the activating resolver's concern, not the stored preset's.

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
  *(Superseded 2026-11-11, #431: `org-charter.yaml` is retired and refused by name, so its lists are neither migrated
  nor read; an org writes must-have items of a governing preset instead. See the amendment under Status.)*

In the domain a preset is a value, not an artifact. Core gives a preset no URN and no kind.
`Preset(id, description, activatedKinds, artifacts, missionTypes)` holds `PresetItem(id, mustHave)`
lists, in authored order. It derives three things:

- `required()`: the must-have item ids of each artifact kind; a kind with none is absent;
- `requiredMissionTypes()`: the must-have mission-type ids, kept apart because a mission type is
  not an `ArtifactKind` *(since ADR-0044, 2026-10-30, it is one; the slot stays apart because a
  preset activates mission types in its own `activated_mission_types` list)*;
  *(Superseded in part by [ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/), accepted 2026-11-02: with `mission_type` an ordinary activatable kind, the separate domain slot goes.)*
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

*(Superseded in part by [ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/), accepted 2026-11-02: a ULID pattern is added to `pack_id` and `parent_pack`, which breaks the contract scenarios that use `pack_id: qa`.)*

### 5. The manifest: the authored-pack variant, with derived hashes

**The shape.** `pack-manifest.yaml` is validated by the closed schema
`contracts/packs/schemas/pack-manifest.schema.json`. It is the authored-pack variant only:

- `schema_version`: required, `"1.0"`;
- `manifest_hash`: required, derived;
- `constituents`: required, possibly empty. Each entry is `{kind, id, path, content_hash}`, all
  required, `kind` an artifact kind token, `content_hash` derived;
- `presets`: optional, at least one entry. Each entry is `{id, path, content_hash}`, all required.
  The schema holds `path` to the pattern `presets/<id grammar>.yaml`; the pattern cannot tie it to
  the entry's own `id`, so one storage rule, `PresetFiles.entryPathMismatch`, requires
  `presets/<id>.yaml`. The whole-pack load records a miss as a violation on the manifest
  (`preset entry '<id>' names <path>, not presets/<id>.yaml`), validate mode as
  `artifact_schema_error` at `/presets/<i>/path`, and the gate refuses it (operator ruling on #195,
  2026-10-28).

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
  <path>, which the write does not hold`. The code is `pack_unreadable`, as for the write's other
  path refusals.
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
*(Amended 2026-11-12, #110: membership is derived from the pack every write holds, never carried as read; every write
restamps the manifest and generates it where the pack held none; a registration and the gate refuse a manifest whose
lists differ. See the amendment under Status.)*

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
Main found none. *(Since [ADR-0040](/adr/0040-content-form-and-payload-kinds/)'s amendment of
2026-11-01 (#251): validate mode applies the store's payload rule, which adds one finding on each of the
six built-in sidecars whose payload the pack does not hold, 303 on nine files.)* No operation serves validate mode (`PackValidation` has no controller), so nothing
changes on the wire.

**A known gap.** A break that only the domain sees passes validate mode while the load refuses it.
An item id listed twice in one list is one: the schema cannot say "unique by `id`", and validate
mode does not build the domain value. This record covers schema breaks and the stem rule only.

**A divergence from core.** Core's `validate_pack` checks presets in its own shape, resolves their
ids and checks their kind gate, and does not check the manifest. The service holds both to these
shapes and does neither of core's preset checks. The divergence is DIV-301, in a block of its own for #195, in the
[divergence register](/conformance/divergence-register/).

### 8. `pack.md` uses #194's `markdown` content form (split off to #260; done 2026-10-30)

`pack.md` is held through the `markdown` content form that sibling
[#194](https://github.com/spec-kitty/spec-kitty-charter/issues/194) builds in its first pull
request (`DM-01M60HM8BV7CE17HYE1QS829K4`, which supersedes this mission's earlier `bytes` choice).
This mission builds no form of its own. The `pack.md` work is not built here: #194's pull request
had not opened, so the operator split it off to
[#260](https://github.com/spec-kitty/spec-kitty-charter/issues/260) on 2026-10-28, and #195 closes
on the scope it delivered. Until #260 lands, `other:pack.md` stays on the gate's expected-red list.
*(Done by #260 on 2026-10-30; see the amendment under Status. `other:pack.md` is off the list.)*
*(Amended 2026-11-11, #451: the pack document is `README.md` at the pack root, held through the same form and model;
`pack.md` and `PROVENANCE.md` are retired and refused by name. See the amendment under Status.)*

### 9. The fingerprint order

`PackFileSet.digested` lists, in order:

1. `pack.yaml`;
2. every file of every bound kind, each followed by its companions;
3. the root graph shards and `drg/fragment.yaml`;
4. the asset files, then the templates;
5. the pack-level tail, in one ordered step: the presets in path order, then `pack-manifest.yaml`.

When `pack.md` lands (decision 8), it joins the tail between the presets and the manifest *(it
did, in #260, 2026-10-30; since 2026-11-11, #451, the document in that place is `README.md`, the tail ends with
`policy.yaml` (ADR-0063) and the retired files follow it)*. A pack
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

Asset files (ADR-0040), presets, and the pack document (#260, `Pack.document()`) are three pack-level
slots beside the kind map. The rule of three has fired. Extracting them into one `PackLevelContent` value is a
follow-up on epic #186. It is not done here, because #194 edits `Pack` at the same time.

### Per-kind table

The rows this record adds to ADR-0036's per-kind table:

| Files (`match`) | Schema | Compared as | Sets (member identity) | Derived fields | Unknown fields refused |
|---|---|---|---|---|---|
| `*/presets/*.yaml` | preset | YAML | none: every list is a sequence | none | yes |
| `*/pack-manifest.yaml` | pack-manifest | YAML, less its derived fields | none: `constituents` and `presets` are sequences | `manifest_hash`; each entry's `content_hash`. Recomputed on write and verified by the gate on each side, never compared | yes |
| `*/pack.yaml` | pack-descriptor | YAML | none | none | yes: the schema is closed (decision 4) |
| `*/pack.md` (#260) *(`*/README.md` since 2026-11-11, #451; `pack.md` is retired)* | pack-document | the Markdown model (ADR-0040 as amended): frontmatter as loaded YAML values, every string exactly | none: the sections are a sequence | none | not applicable: the structure schema is closed over the model |

### The gate and the pinned files

The whole-pack gate models the kinds `preset` and `pack-manifest`. Its `REQUIRED_KINDS` gains
exactly those two, and its expected-red list loses `other:presets` and `other:pack-manifest.yaml`.
The gate adapter writes presets in place. It restamps the manifest over a pairing: each constituent
`(kind, id)` is paired with the file the adapter wrote for that artifact, and each preset path with
itself. A template constituent pairs by its own path, as the adapter writes templates in place. The store
pairs every entry by path; the adapter's flat layout cannot, so a constituent with a stale path but
a written id pairs in the gate while the store's write refuses it. A listed file the adapter did not
write, such as a constituent of a kind the registry does not bind, refuses the manifest: `pack-manifest.yaml lists <path>, which the round trip did not
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
- Built-in validate mode goes from 0 to 297 findings, on three files. *(Since ADR-0040's amendment of
  2026-11-01 (#251): 303, on nine files.)*
- A pack with an unknown `pack.yaml` key is now unreadable where it loaded with a warning.
- Packs that hold presets or a manifest get a new fingerprint and ETag once.
- A registration leaves the manifest stale until #110 decides regeneration. *(Done 2026-11-12, #110: every write
  restamps it.)*
- Validate mode passes a preset that breaks only a domain invariant, such as a duplicated item id
  (decision 7).
- `Pack` has two pack-level slots now, and a third is coming, before the extraction *(it came: the
  pack document, #260)*.
- The whole-pack load of the corrected built-in pack, presets and manifest included, takes 420 ms
  in the seam test, against a budget of 0.5 s (NFR-004). It is close to the budget; no figure before
  #195 was taken for the built-in pack. The internal pack, which holds no manifest, took 43 ms
  before and 45 ms after.

### Risks

- The placeholder keys could merge as they are. The loader admits only the two named slugs, and
  the hand-off lists "no `#TBD-` in the merge diff" as a pre-merge check.
- Core keeps its own preset and manifest shapes until the service is incorporated. A pack written
  for one may be refused by the other in the meantime.
- The JCS writer is narrow. A field that is not a string, a list or an object would need number
  canonicalization first.

### Follow-up work

- File OP-1 and OP-2, replace the placeholder keys, and remove the two slugs from the loader.
- [#260](https://github.com/spec-kitty/spec-kitty-charter/issues/260): `pack.md` through #194's
  `markdown` form, once #194's first pull request merges. *(Done 2026-10-30.)*
- #206: migrate `org-charter.yaml`'s `required_<plural>` lists into presets, then refuse them.
  *(Superseded 2026-11-11 by retiring `org-charter.yaml`, #431: the file is refused by name, and its lists map to a
  governing preset's must-have items.)*
- #110: manifest membership and restamping after a registration. *(Done 2026-11-12; see the amendment under Status.)*
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
  - `org-charter.schema.json` keeps the legacy `required_<plural>` lists (#206) *(the schema is deleted with the
    retired file, 2026-11-11, #431)*;
  - `charter.schema.json` has `mission_type_activations`, and its `activated_*` set leaves out
    `anti_patterns`;
  - the agent profile's `excluding` is an ad hoc union of an array, an object and `null`;
  - the graph shard requires `generated_at`, whose only value in the packs is `STATIC`.

## Open questions

1. **The manifest's known-failure text.** Its pinned `at` holds all 283 joined reasons, about 24 KB.
   The operator was asked whether to pin a shorter form. Not answered yet.
2. **Acceptance.** This record is Proposed until the operator accepts it on #195. *(Accepted
   2026-10-29; see Status.)*

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
