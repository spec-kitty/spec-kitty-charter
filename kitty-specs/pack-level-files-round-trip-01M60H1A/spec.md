# Mission Specification: Pack-level files round trip

**Mission Branch**: `feat/195-pack-level-round-trip`
**Created**: 2026-10-28
**Status**: Draft
**Input**: Issue #195 (epic #186, Wave 2), "Round-trip pack-level files": `presets/` and `pack.md`, read and
write; `pack-manifest.yaml`, read and regenerated on write (freshness and fingerprint, see #110); `pack.yaml`,
lossless. Operator rulings on #195 (2026-10-27): presets carry a per-item must-have mark, and `required_<plural>`
lists are migrated, then refused (#206). ADR-0036 open question 3 was deferred to this issue. Binding operator
requirement (2026-10-28): every storage object has a sanitized, deliberately designed shape.

Grounding: a pre-spec squad of four lenses ([research/grounding-squad.md](research/grounding-squad.md)). The
mission's choices are in `decisions/`. The operator questions are posted on #195.

## Intent Summary (2026-10-28; author-confirmed from the operator rulings and brief, open points raised on #195)

A pack author or maintainer loads a whole Charter Pack into the service and writes it back. The four files that
describe the pack as a whole are read into the domain through closed, deliberately designed storage shapes, and
written back without loss of meaning:

- **activation presets** (`presets/<id>.yaml`), each list item able to carry a must-have mark;
- **the pack document** (`pack.md`), held in the `markdown` content form that sibling #194 builds (its PR-A). This
  part lands after PR-A;
- **the pack descriptor** (`pack.yaml`), whose schema is closed, so an unknown key is refused instead of being
  remembered by name and dropped;
- **the pack manifest** (`pack-manifest.yaml`), whose hashes are derived. The writer recomputes them from the
  bytes it writes, and the gate verifies them.

Where the pinned open-packs content does not have the designed shape, the service refuses it. It adds no tolerance
logic. The file goes on the gate's known-failure list, keyed by a drafted upstream issue.

## Domain Language

| Term | Meaning | Avoid |
|------|---------|-------|
| Activation preset | A named, pack-shipped selection of artifacts and mission types to activate (`presets/<id>.yaml`). Its `id` is its file stem. | "profile", "bundle" |
| Preset item | One entry of a preset's activation list: an artifact or mission-type `id`, optionally marked must-have. | "required entry" |
| Must-have mark | The per-item flag (`must_have: true`) that makes an item part of the preset's required set (ADR-0031, ruling on #195). | "required" (collides with the legacy `required_<plural>` lists and with JSON Schema's `required`) |
| Governing preset | A preset with at least one must-have item. A preset with none is a convenience preset. Derived, never declared. | "policy preset" |
| Kind gate | A preset's `activated_kinds`: the kinds the preset activates; a kind left out is switched off. | |
| Pack document | The pack's free-form Markdown description, `pack.md`. Held in #194's `markdown` content form. | "README" |
| Pack manifest | The machine-written inventory of a pack's constituent artifacts and presets, with a content hash for each and a hash over the whole. | "lock file" |
| Derived field | A field computed from other content (a manifest hash). The writer recomputes it and the gate verifies it. It is never carried as read. | |
| Sanitized shape | The deliberately designed, closed shape of a storage object, following the rules S1–S8 of [research/grounding-squad.md §3](research/grounding-squad.md#3-sanitized-shape-rules-applied). | "mirror of the files" |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A preset round-trips in its designed shape, must-have marks included (Priority: P1)

A pack author keeps presets under `presets/`. Loading the pack reads each preset into the domain, with its items and
their must-have marks. Writing it back produces a preset equal by meaning. A preset in any other shape is refused,
and the refusal names what is wrong.

**Why this priority**: Presets are the authoring form of requirement (ADR-0031). The per-item mark is how an org says
what must be on the plate, and no part of the service holds presets today.

**Independent Test**: Load and write a fixture pack whose presets use every list, with and without must-have marks.
Compare the written presets by meaning, and check the derived required set.

**Acceptance Scenarios**:

1. **Given** a preset `presets/house-rules.yaml` with `activated_directives: [{id: GUARD, must_have: true}, {id: STYLE}]`,
   **When** the pack is loaded, **Then** the domain holds preset `house-rules`, its required set is `directive GUARD`,
   and it is a governing preset; **When** written, **Then** the written preset is equal by meaning to the source.
2. **Given** a preset with no must-have item, **When** loaded, **Then** its required set is empty and it is a
   convenience preset.
3. **Given** a preset whose `id` differs from its file stem, **When** loaded, **Then** it is refused naming both; a
   preset whose `id` matches, in the same fixture, loads.
4. **Given** a preset with a key outside its schema (`name:`, `required_directives:`,
   `mission_type_activations:`), **When** loaded, **Then** it is refused naming the key; the clean preset in the same
   fixture loads.
5. **Given** a preset with an empty activation list (`activated_tactics: []`), **When** loaded, **Then** it is refused
   by the schema; the same preset with the list left out loads, and the kind is unrestricted.
6. **Given** a preset that lists one id twice in one list, **When** loaded, **Then** it is refused naming the id.

### User Story 2 - The pack descriptor never loses a key (Priority: P1)

A maintainer loads a pack whose `pack.yaml` carries a key outside the schema. The pack is refused as unreadable, and
the refusal names the key. The service no longer remembers the key by name and drops its value on write.

**Why this priority**: The issue's "lossless pack.yaml". ADR-0036 ruling 1 (unknown fields are refused) decides how,
and its follow-up has been open since #188.

**Independent Test**: Load a pack whose `pack.yaml` has `surprise: 1`, next to a clean pack.

**Acceptance Scenarios**:

1. **Given** `pack.yaml` with `surprise: 1`, **When** the pack is loaded, **Then** it is refused as `pack_unreadable`
   with `pack.yaml of <pack>: unknown key 'surprise'`; the clean pack in the same run loads.
2. **Given** `pack.yaml` with the retired `accompanies_doctrine_pack`, **When** loaded, **Then** it is refused the
   same way.
3. **Given** the two pinned `pack.yaml` files (built-in, internal), **When** the gate runs, **Then** both round-trip
   green.

### User Story 3 - The pack document round-trips byte for byte (Priority: P2)

A pack author ships `pack.md` beside `pack.yaml`. Loading the pack holds it in the `markdown` content form (#194), and
writing it back keeps its meaning: frontmatter (if any) as YAML, body as text. This story depends on #194's PR-A and
lands after it merges; until then `other:pack.md` stays expected-red.

**Independent Test**: Load and write a pack with `pack.md`; it compares equal under the markdown form's rule, and a
lossy writer is caught.

**Acceptance Scenarios**:

1. **Given** the pinned built-in `pack.md`, **When** the gate runs, **Then** it round-trips green.
2. **Given** a deliberately lossy write of the body, **When** the gate runs, **Then** it fails at `pack.md`.
3. **Given** a pack without `pack.md`, **When** it is written, **Then** no `pack.md` appears.

### User Story 4 - The manifest is regenerated on write and verified (Priority: P1)

A maintainer loads a pack with a `pack-manifest.yaml` and writes it to a new directory. The written manifest lists the
same constituents and presets. Its hashes are recomputed from the bytes that were actually written, so it describes
the written pack, not the source's bytes. A manifest in any other shape is refused.

**Why this priority**: The service rewrites YAML canonically and drops comments, so carrying hashes as read would
leave a manifest that describes bytes that no longer exist. This settles ADR-0036 open question 3.

**Independent Test**: Load and write a fixture pack with a sanitized manifest whose artifact files carry comments.
Every written `content_hash` equals SHA-256 of the written file's LF-normalised bytes. The written `manifest_hash`
equals the hash of the written manifest. Constituents and presets are equal by meaning to the source.

**Acceptance Scenarios**:

1. **Given** a sanitized manifest listing a commented directive and a preset, **When** the pack is written, **Then**
   the written directive's `content_hash` is the hash of the written bytes, which differs from the source hash, and
   the written `manifest_hash` verifies.
2. **Given** a manifest listing a path that is not in the pack, **When** it is written, **Then** the write is refused
   naming the path, and nothing is written.
3. **Given** a manifest with `charter:`, `fetched_at:` or `source_type:`, **When** loaded, **Then** it is refused naming
   the key.
4. **Given** a source manifest whose `content_hash` does not match its file, **When** the gate runs, **Then** the gate
   reports the stale hash at that file, and a fresh manifest in the same fixture is green.

### User Story 5 - The gate flips exactly these files (Priority: P1)

The maintainer runs the whole-pack round-trip gate at the pinned open-packs commit.

- The pack-level kinds are modelled.
- `pack.yaml` and `pack.md` are green.
- The built-in presets and manifest are the only red pack-level files. Each is pinned on the known-failure list to
  its exact refusal and keyed by a drafted upstream issue.

**Independent Test**: Run the gate. The `other:presets`, `other:pack.md` and `other:pack-manifest.yaml` expected-red
entries are gone, and the known-failure list gains exactly three built-in entries.

**Acceptance Scenarios**:

1. **Given** the pinned commit, **When** the gate runs, **Then** it passes with the three new known failures and no
   other change in red.
2. **Given** a known-failure entry whose file starts to round-trip, **When** the gate runs, **Then** it fails asking for
   the entry to be removed (self-expiry, #247).

### Edge Cases

- A pack with no `presets/` directory, no `pack.md` and no manifest: loads and writes with none of them, and no empty
  directory or file is created.
- A non-YAML file under `presets/` (a `README.md`): not a preset. It follows the existing rules for unclassified
  files, and no preset rule claims it.
- A nested directory under `presets/`: not a preset, because the rule matches `*/presets/*.yaml` only.
- A preset whose items reference artifacts the pack does not hold: loads. Resolving ids against the offering is not
  the round trip's job, and core checks it in `validate_pack`.
- A manifest with no constituents (`constituents: []`): valid. It writes `manifest_hash` over the empty list.
- A manifest whose constituent `kind` is not an artifact kind token: refused by the schema.
- `pack.md` that is not valid UTF-8: handled as #194's markdown form decides (refused, not decoded lossily).
- A comment-only preset or manifest (a YAML `null` document): refused, because the schema requires a mapping.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Preset schema | As a maintainer, I want `contracts/packs/schemas/preset.schema.json` to define the sanitized preset: `schema_version` `"1.0"`, `id`, `description`, the kind gate `activated_kinds`, the nine `activated_<plural>` lists and `activated_mission_types`, each list holding at least one `{id, must_have?}` item, closed at every level, so that one shape is accepted and every other is refused. | High | Open | [build] | no — paired with a clean preset accepted on the same fixture |
| FR-002 | Preset in the domain | As a pack author, I want each `presets/*.yaml` loaded into the pack as a preset (its id, description, kind gate and items with their must-have marks), with the required set derived from the marks, so that a governing preset is known without a second declaration. | High | Open | [build] | no |
| FR-003 | Preset invariants | As a maintainer, I want a preset refused when its `id` is not its file stem, or when one list names an id twice, each refusal naming the preset file. | High | Open | [build] | no — paired with a valid preset in the same fixture |
| FR-004 | Preset written back | As a pack author, I want each preset written to `presets/<id>.yaml` equal by meaning to its source, must-have marks included, and no preset file written for a pack that holds none. | High | Open | [build] | no — a dropped must-have mark fails the comparison |
| FR-005 | Pack document in the markdown form | As a pack author, I want `pack.md` held through #194's `markdown` content form and written back equal under that form's rule, once #194's PR-A has merged. | Medium | Open | [build] | no — paired with a lossy-body control |
| FR-006 | Manifest schema | As a maintainer, I want `contracts/packs/schemas/pack-manifest.schema.json` to define the sanitized authored-pack manifest (`schema_version`, `manifest_hash`, `constituents[{kind, id, path, content_hash}]`, `presets[{id, path, content_hash}]`), closed, with 64-hex hashes, so that the variant fields of fetched packs and charter bundles are refused. | High | Open | [build] | no — paired with a clean manifest |
| FR-007 | Manifest read | As a maintainer, I want `pack-manifest.yaml` read beside the pack aggregate: membership as written, hashes as written, and no freshness check on load (core's behaviour). | High | Open | [build] | no |
| FR-008 | Derived hashes declared | As a maintainer, I want `content_hash` defined as SHA-256 of the file's bytes after CRLF/CR → LF, and `manifest_hash` as SHA-256 of the UTF-8 RFC 8785 canonical JSON of the manifest without `manifest_hash`, so that both are reproducible in any language. | High | Open | [build] | no — a parity test reproduces both from fixed bytes |
| FR-009 | Manifest regenerated on write | As a maintainer, I want a pack write to recompute every constituent and preset `content_hash` from the bytes it writes, and `manifest_hash` from the result, keeping membership; a listed path missing from the written pack refuses the write. | High | Open | [build] | no — a commented source changes the hash on write |
| FR-010 | Manifest round trip proven | As a maintainer, I want a layout-preserving round trip of a fixture pack with a sanitized manifest to show constituents and presets equal by meaning and every derived field verified against the written bytes. | High | Open | [build] | no |
| FR-011 | Descriptor closed | As a maintainer, I want `pack-descriptor.schema.json` closed (`additionalProperties: false`), with its description text consistent (`pack_level` optional, `pack_version` required at every level). An unknown or retired `pack.yaml` key makes the pack unreadable, naming the key, and is never carried by name. | High | Open | [build] | no — paired with a clean pack loading in the same run |
| FR-012 | Undeclared keys retired in code | As a maintainer, I want `PackDescriptor.undeclaredKeys`, the `PACK_UNKNOWN_KEY` finding and the write-side undeclared-key refusal removed, and their tests moved to the refusal, with the contract's `DiagnosticCode` describing `pack_unknown_key` as retired, never raised (approved on #195), and no other contract change. | High | Open | [build] | no |
| FR-013 | Pack-level files fingerprinted | As an operator, I want a change to a preset, `pack.md` or `pack-manifest.yaml` to change the pack's source fingerprint, and a pack without them to keep its fingerprint. | High | Open | [build] | no — a one-byte edit of each changes the fingerprint |
| FR-014 | Gate flips exactly these files | As a maintainer, I want the gate to model the kinds `preset` and `pack-manifest` (and `pack-document` with FR-005), with `REQUIRED_KINDS` gaining exactly those. The expected-red list loses `other:presets` and `other:pack-manifest.yaml` (and `other:pack.md` with FR-005). The known-failure list gains exactly `presets/default.yaml` and `presets/minimal.yaml` (keyed `#TBD-preset-shape`) and `pack-manifest.yaml` (keyed `#TBD-manifest-shape`), each pinned to its exact refusal, which is the same text the seam test reports. The loader admits only those two placeholder keys. | High | Open | [build] | no — the two-way ratchet fails on an extra or stale entry |
| FR-015 | Gate verifies derived fields | As a maintainer, I want the gate to compare a manifest's non-derived fields by meaning and to verify each derived field against its own side's bytes, so that a stale source manifest is reported, not silently restamped. | High | Open | [build] | no — paired with a stale-hash control |
| FR-016 | Upstream issues drafted | As the parent session, I want one draft issue per divergence family (file, required change, rationale) and a core-first issue in `research/draft-upstream-issues.md`, so that the placeholders can be filed and replaced. | Medium | Open | [build] | no |
| FR-017 | Decisions recorded | As a maintainer, I want ADR-0043 to record the sanitized-shape rules for the pack-level storage objects, the preset shape and the per-item mark (superseding ADR-0031's open questions), the manifest's derived fields, the rule for kinds without a schema (closing ADR-0036 open question 3 and decision 6), and `pack.md` as bytes. ADR-0031, 0036, 0037 and 0040 get amendment notes. | High | Open | [build] | no |
| FR-018 | Validate mode agrees with load | As a maintainer, I want validate mode to report a preset or manifest that breaks its schema, or a preset whose id is not its file stem, as `ARTIFACT_SCHEMA_ERROR` (ADR-0036 decision 3's code for every other schema-mapped file), so that validate and load refuse the same files. Presets are not counted as artifacts. | High | Open | [build] | no — the built-in validate test pins exactly the three new findings |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Hash parity | The Java `content_hash` reproduces all 274 pinned built-in hashes (272 constituents and 2 presets), read from the pinned manifest with the raw YAML loader: 100% equal. Fixed vectors with CRLF, lone CR and mixed line endings, plus RFC 8785 vectors (escapes, non-ASCII, an empty `constituents`), pass 100%. | Compatibility | High | Open |
| NFR-002 | Fingerprint stability | Packs without presets, `pack.md` or a manifest keep their source fingerprint: 0 changed values across the existing fingerprint tests. | Compatibility | High | Open |
| NFR-003 | Build gates | `./gradlew verifyAll` passes on Java 25 with zero new style, Error Prone, NullAway, doclint or ArchUnit findings; the docs checks (`check_docs.py all`, markdownlint-cli2, cspell) pass. | Quality | High | Open |
| NFR-004 | Load cost | Reading the pinned built-in manifest (1388 lines) adds under 0.5 s to the built-in pack's whole-pack load on the CI runner. | Performance | Low | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | HTTP contract frozen | No operation, schema shape or refusal code under `contracts/charter/**` changes. The one approved edit (operator, #195, 2026-10-28) is the `DiagnosticCode` description of `pack_unknown_key` (retired, never raised) and its contract CHANGELOG line. | Technical | High | Open |
| C-002 | Shared files append-only | Shared files (`file-kinds.yaml`, `PackFileKind`, `PackFileSet.digested`, `WholePackStore`, the gate baseline and known-failure list, CHANGELOG, glossary, ADR index) get append-only edits for this mission's files only. The branch is rebased on main after sibling #194 merges. | Process | High | Open |
| C-003 | No tolerance logic | A pinned file in another shape is refused and filed upstream (ADR-0036 decision 5). No reader rule, rename or default keeps it. | Process | High | Open |
| C-004 | Merged storage objects untouched | Sanitized-shape violations in already-merged schemas are listed for an epic #186 follow-up and not changed here. | Process | High | Open |
| C-005 | No comparator exception | The preset's empty-versus-absent clash is resolved in the schema (empty lists refused), not by a per-kind rule in the gate's comparator. | Technical | Medium | Open |
| C-006 | Built-in is core-first | Every built-in divergence is drafted as core-first (core seeds `packs/built-in/`). No edit is proposed to open-packs `packs/built-in/` directly. | Process | Medium | Open |

### Key Entities

- **Preset**: id (the file stem), description, optional kind gate, an ordered list of items per activation key. Its
  required set is derived.
- **Preset item**: an id and the must-have mark.
- **Pack document**: `pack.md` in #194's markdown form.
- **Pack manifest**: schema version, ordered constituents (kind, id, path, content hash), ordered preset entries
  (id, path, content hash), and the manifest hash. It is held beside the aggregate, because `Pack` holds no paths.
- **Pack descriptor**: as today, without undeclared keys.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At the pinned open-packs commit, the gate passes with exactly 3 new known failures (2 presets, 1 manifest).
  `other:presets` and `other:pack-manifest.yaml` are gone from the expected-red list. — [build] · no-op passable: no
- **SC-001a**: Built-in `pack.yaml` stays green in the gate, and internal `pack.yaml` in the seam test, while the paired
  descriptor refusals (`surprise`, `accompanies_doctrine_pack`) refuse. — [ratchet] · no-op passable: yes — paired with
  the refusals, which a no-op fails
- **SC-001b**: Once #194's PR-A has merged, built-in `pack.md` is green and `other:pack.md` is gone from the expected-red
  list. — [build] · no-op passable: no
- **SC-002**: The fixture pack's presets round-trip equal by meaning, must-have marks included, and every refusal
  control (unknown key, id ≠ stem, duplicate id, empty list) is refused while its paired clean file loads. — [build] ·
  no-op passable: no
- **SC-003**: On a written pack, 100% of the manifest's derived fields verify against the written bytes, and a
  one-byte lossy write of any listed file is caught. — [build] · no-op passable: no
- **SC-004**: All 274 pinned built-in `content_hash` values, and the line-ending vectors, are reproduced by the service. — [ratchet] · no-op passable:
  no — needs the new hash code
- **SC-005**: Zero changes under `contracts/charter/**` and to already-merged pack schemas other than
  `pack-descriptor`. — [ratchet] · no-op passable: yes — paired with SC-001, which a no-op fails

## Assumptions

- `openPacksRef` stays at `01edb4eb` for this mission. The built-in presets and manifest stay on the known-failure list
  until core adopts the shapes and open-packs re-seeds (C-006). This may take longer than this epic.
- Core keeps reading presets in its current shape until then. The service's preset shape is ahead of core by
  design (the operator's sanitized-shape requirement). No service operation serves presets today.
- The parent session files the drafted issues and replaces the `#TBD-<slug>` placeholders before merge.
- ADR numbers are allocated by the parent session: 0041 is #227's, 0042 is #194's, and this mission records its
  decisions in **ADR-0043**.
- #194's PR-A (the `markdown` content form) merges before the `pack.md` work package; if it has not merged by
  hand-off, `pack.md` stays expected-red and the hand-off says so.

## Out of Scope

- Migrating `org-charter.yaml`'s `required_<plural>` lists into presets, and refusing them (#206).
- Enforcing a preset's required set when a charter is activated, or applying a preset to `charter.yaml` (activation
  slice; ADR-0031 decision 3).
- Restamping the manifest after a single-artifact registration, and regenerating manifest membership (which files
  are constituents). Both need core's enumeration rules (#110).
- A ULID pattern on `pack_id`: the contract scenarios use `pack_id: qa`.
- Building the `markdown` or `text` content form (#194 builds `markdown`). A frontmatter schema for `pack.md`.
- The fetched-pack and charter-bundle manifest variants.
- Changing already-merged storage objects (listed for epic #186: grounding-squad §6).
