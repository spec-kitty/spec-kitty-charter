# Research: Asset and template round trip

Sources: pre-spec squad (planner-priti, researcher-robbie, architect-alphonso), format-dispatch squad
(researcher-robbie, architect-alphonso, doctrine-daphne), operator rulings R1–R11 (issue #193, `decisions/`), and the
code at `main` 90de8f9 with open-packs pinned at 01edb4e.

## Evidence

- Census at the pin: 20 sidecars (built-in 6, event-driven 1, internal 10, qa 3); 23 non-sidecar files under
  `*/assets/` (2 README, excluded); 2 top-level templates (`built-in/templates/writing/*.md`). Binary payloads:
  `internal/assets/debrief/logo.png`, three `.otf` fonts (NUL bytes, CR/LF byte pairs, no final newline). Five
  internal `.py` payloads are mode 0755. No BOM, no CRLF in text payloads, no symlinks.
- All six built-in sidecars write `path: assets/...` (pack-root relative); the schema, core
  (`charter/offering/assets/models.py`, `repository.py`) and the other 14 sidecars use paths relative to `assets/`.
  Core hides the mismatch with built-in-only anchoring (`AssetRepository._anchor_for`).
- Core never parses an asset payload or a template; `mime` is checked against `mimetypes.guess_type(path)`, i.e. the
  extension. Templates have no id field and an empty glob; core mints `template:<mission>/<file>`.
- The storage seam is text-only: `SerializationStrategy` returns `String`, `WholePackStore.put` and
  `FilesystemPackStore.writeAtomically` use `Files.writeString`, `PackFileSet` walks `*.yaml` only, and the gate's
  `MeaningComparator` parses every file as YAML (ADR-0037 decision 4 leaves the widening to this mission).
- `WholePackOpenPacksRoundTripTest` requires zero violations per pack; `WholePackStore.write` refuses a pack with any.
- `PackSchemas` and `FileSystemPackInspector` iterate `files` rules by schema; a schema-less rule in `files` would
  break them, so schema-less rules need their own section.
- `**/*.config.yaml` exclusion: its only match at the pin is `built-in/assets/docs_structural_lint.config.yaml`, and
  its reason calls it "tool configuration shipped as an asset".

## Decisions

### D1 Content form is declared by the file-kind rule

- **Decision**: `files` rules take an optional `content_form` (`yaml` default; `markdown`, `text`, `bytes`). A new
  `payloads` section holds rules without a schema (`match`, `kind`, `content_form`), consulted only when no `files` or
  `exclusions` rule matches; payload rules must not overlap one another. Rules: `*/assets/**` → `asset-payload`,
  `bytes`; `*/templates/**` → `template`, `bytes`. The `**/*.config.yaml` exclusion is retired (R1).
- **Rationale**: one authority (file-kinds.yaml already decides which files exist and which schema validates each);
  every file under one rule; extension- and mime-based choice rejected by the operator and by core evidence.
- **Alternatives**: asset `mime` chooses (rejected: extension restated, assets only, a second authority);
  by-reference classification only (rejected by the operator: leaves the config file excluded, against R1); schema-less
  rows in `files` (rejected: breaks `PackSchemas`/validate-mode iteration); format on `KindBinding` (rejected: second
  authority).

### D2 Asset metadata and asset files are separate domain values

- **Decision**: `Asset` (id, mime, path, optional title, no schema version) is a `CharterArtifact`; each payload file
  is an `AssetFile(path relative to assets/, Payload)` held by the `Pack`. An asset names its file by `path`.
- **Rationale**: the sidecar-less config file is an asset payload with no id (R1); one home for each byte; mirrors the
  pack on disk without putting paths on artifacts other than the authored `path` field.
- **Alternatives**: bytes on `Asset` plus a separate list for unclaimed payloads (rejected: two homes for payloads);
  `Optional` payload on `Asset` (rejected by R3).

### D3 A missing payload is a violation; the domain guards the link

- **Decision**: the store refuses a sidecar whose resolved payload (`assets/<path>`) is not a held asset file, as a
  `PackElementViolation` naming the sidecar file and the missing path; the sidecar does not join the pack. `Pack`
  guards that every asset names a held asset file and that no two assets name the same one.
- **Rationale**: R3; ADR-0037's "an unreadable artifact file is a violation on the aggregate".

### D4 Template is a bytes artifact with a path identity

- **Decision**: `Template(TemplateIdentifier, packId, Payload)`; local id = path under `templates/`
  (`writing/meeting-minutes.md`); label = file name; no schema version, description or relationships.
- **Rationale**: R2; mirrors core's `<dir>/<file>` template ids; frontmatter is opaque (R9).

### D5 A separate binding for file-shaped kinds

- **Decision**: `FileBinding<E>` (kind, entity type, `toEntity(pack, relativePath, Payload)`, `pathOf(E)`,
  `payloadOf(E)`) and a `FileBindings` list (`TemplateBinding`). Asset sidecars use an ordinary `KindBinding`
  (`AssetBinding`, `AssetDso`, `AssetYaml`). Asset files are pack-level values, read and written by the store.
- **Rationale**: `KindBinding` is YAML- and schema-shaped (KindBindingsTest); a sealed union would touch every sibling's
  binding. Rule of three: #192 (markdown) can generalize.

### D6 Fingerprint and load order

- **Decision**: `PackFileSet` classifies every regular file once. The YAML kinds keep their order and digests; the
  asset sidecars join through `KindBindings` (appended); asset files and then template files are digested **last**,
  after `drg/fragment.yaml`, so every pack without them keeps its exact digest list.
- **Rationale**: NFR-002, ADR-0037 "new kinds are appended".

### D7 Gate adapter and comparison

- **Decision**: keep `DomainRoundTrip`; add asset/asset-file/template saves and outcomes in an appended block;
  `WholePackRoundTripTest.observe` compares files of a `bytes` content form by bytes (first differing offset in the
  detail); a positive control flips one payload byte.
- **Alternatives**: switch the gate to `WholePackStore` (deferred: shared with all Wave 1 siblings).

### D8 Pinned expected violations (R10)

- **Decision**: `whole-pack/expected-violations.yaml` lists `pack`, `file`, `reason`, `issue` for the six built-in
  sidecars; the open-packs test fails on an unlisted violation and on a listed one that no longer occurs; a pack whose
  violations are exactly the pinned ones is not written (the write refuses it) and is reported as pinned.

### D9 Byte-level hazards

- **Decision**: payloads are read with `Files.readAllBytes` and written with `Files.write` (no charset); a file whose
  bytes are not UTF-8 is not a violation for a `bytes` kind; file mode is not preserved (R5); case-folded path
  collisions stay refused by the write.

## Open-packs issue (to file; this session cannot write there)

Title: `bug: built-in asset sidecars anchor path at the pack root instead of assets/`. Body: pack `built-in`, files
`assets/**/*.asset.yaml` (6), kind `asset`, rule ADR-0036 Decision 5 / asset schema `path` "relative to the pack's
assets/ directory", gate output (the six refusals), `openPacksRef` 01edb4e. Fix: drop the `assets/` prefix from the six
`path` values.

## Squad findings and dispositions

| # | Finding (source) | Disposition | Evidence |
|---|---|---|---|
| S1 | Store/strategy/walk are text-only (architect, planner) | accepted | D5, D9, IC-03 |
| S2 | Fingerprint split-brain if payloads load undigested (architect) | accepted | D6, FR-011 |
| S3 | Violation would redden all of built-in (architect) | changed: operator ruled refusal + pinned list | R3, R10, D8 |
| S4 | README under assets/ EXCLUDED_MODELLED trap (researcher) | changed: own census kind | R4, D1 |
| S5 | ADR-0036 `*.md` vs bytes overlap (all) | changed: content form on the rule | R8, R11, D1 |
| S6 | Exec bit lost by byte writes (researcher) | accepted: not meaning | R5, C-005 |
| S7 | Template has no identity (researcher, architect) | accepted | D4 |
| S8 | Binaries only in internal pack; gate sees built-in only (planner) | accepted | FR-003 tests over internal fixture; open-packs test |
| S15 | With all six built-in sidecars refused, the gate holds no green `asset` file, so an empty reader would pass it (planner) | changed: green sidecars are proven over the other packs by the open-packs round trip (aggregate meaning) and by unit tests over a fixture with a consistent sidecar | D8, FR-002 tests |
| S9 | `atOf` pins only `refused` for refused files (planner) | deferred_with_rationale: gate mechanics are shared; the exact reasons are pinned by D8's list | D8 |
| S10 | Relationship endpoints widen silently (architect) | accepted | R6, IC-04 |
| S11 | manifest `content_hash` (researcher) | deferred_with_rationale: #195 owns it | spec Out of Scope |
| S12 | No `.gitattributes` binary/eol upstream (researcher) | deferred_with_rationale: open-packs concern; pinned checkout is LF | open-packs issue note |
| S13 | `OPEN_MAPS` restates ADR-0036 (architect) | deferred_with_rationale: not this mission's kind | — |
| S14 | Term "format" collides (doctrine) | accepted: "content form" | R8, glossary |
