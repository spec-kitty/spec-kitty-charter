# Research: Pack-level files round trip

Pre-spec grounding, the sanitized-shape rules, the divergence table and the merged-DSO follow-up list are in
[research/grounding-squad.md](research/grounding-squad.md). The draft upstream issues are in
[research/draft-upstream-issues.md](research/draft-upstream-issues.md). The specify decisions are in `decisions/`.
This file records the plan-time decisions and the post-plan squad.

## Decisions

### D1 Presets are pack-level domain values, not artifacts

- **Decision**: `Preset` and `PresetItem` are in `packs.domain`. A pack holds its presets in a slot of its own, and
  the storage layer pairs each preset with its file stem.
- **Rationale**:
  - Core gives a preset no URN and no `ArtifactKind`. `KindBinding` is typed to `CharterArtifact` and to
    `<id>.<token>.yaml` file names (`KindBinding.java`).
  - The glossary already names "activation preset". ADR-0031 makes the preset the authoring form of requirement,
    which makes it a domain concept.
- **Alternatives**:
  - A `preset` `ArtifactKind` with URNs: rejected, because it invents identity and widens the kind vocabulary and
    the fragment kinds that `VocabularyEqualityTest` pins.
  - A storage-only DSO with no domain type: rejected, because the required set has to be derived in the domain.

### D2 The sanitized preset shape, with the per-item mark

- **Decision**: The shape is `schema_version`, `id`, `description`, `activated_kinds`, nine `activated_<plural>`
  lists and `activated_mission_types`. Each list item is `{id, must_have?}`, every list has at least one item, and
  the schema is closed.
- **Rationale**: the operator's requirement of 2026-10-28, the ruling on #195, and squad findings F1 and F5. Closing
  the schema refuses `required_*` with no special rule.
- **Alternatives**:
  - Round-trip the files as written and add the mark in #206: #206 would break the same files a second time.
  - A nested `selection:` map: it would rename the established `activated_<plural>` family for no gain.
  - A preset-level `governing: true` flag: it says one thing two ways (S6).

### D3 `pack.md` is a bytes payload

- **Decision**: a `payloads` rule `*/pack.md`, kind `pack-document`, `bytes` form. The pack holds it as
  `Optional<Payload>`.
- **Rationale**: The file has no frontmatter and no fields. `bytes` is a built form and the strictest kind of
  equality. `markdown` and `text` are declared but not built (ADR-0040).
- **Alternatives**: building the `text` form, which adds a form for one file and gains nothing over bytes.

### D4 The descriptor is closed

- **Decision**: `additionalProperties: false`. `undeclaredKeys`, the `PACK_UNKNOWN_KEY` finding and the write-side
  refusal are removed. The `pack_unknown_key` value stays in the contract enum, and the description question is
  raised on #195.
- **Rationale**: ADR-0036 ruling 1 and decision 3, which are accepted. No operation references the diagnostic, and
  the HTTP contract is frozen (C-001).
- **Alternatives**: keeping the values of undeclared keys, which is the issue's original wording and is superseded by
  ruling 1 (ADR-0036 alternatives).

### D5 The manifest has derived hashes and sits beside the aggregate

- **Decision**: Every hash is recomputed on write from the staged bytes:
  - `content_hash` = SHA-256 of the file bytes with CRLF/CR → LF;
  - `manifest_hash` = SHA-256 of the UTF-8 JCS (RFC 8785) of the manifest without `manifest_hash`.

  Membership is carried as read, and a listed path that was not staged refuses the write. Nothing is checked for
  freshness on read.
- **Rationale**: Squad findings F3 and F9. A hash over core's ruamel YAML would tie Java to one emitter, and JCS is a
  standard. Core does not check freshness on load, so the service should not either.
- **Alternatives**:
  - (b) Carry the hashes as read: the manifest would then describe bytes that no longer exist.
  - (c) Compare the constituents by bytes: this contradicts ADR-0036 ruling 2 for YAML files.
  - Port ruamel's emitter: brittle across emitters.

### D6 How the gate handles the three kinds

- **Decision**:
  - `DomainRoundTrip` writes presets and `pack.md` in place, paired by path.
  - It writes the manifest restamped over the target directory. Because the adapter writes artefacts at
    `<id>.<token>.yaml`, a manifest that lists real paths is refused at that adapter with the missing path named.
    This is honest, and the pinned manifest is refused earlier, at read.
  - `MeaningComparator` gains a declared `pack-manifest` rule that skips `manifest_hash` and every `content_hash`.
  - The gate driver verifies the derived fields on each side with `PackManifests.stale`.
  - The layout-preserving proof (FR-010) runs through `WholePackStore` on a fixture.
- **Rationale**: Squad finding F2. Switching the gate to `WholePackStore` stays deferred, as in ADR-0040, because
  the adapter is shared with #194.
- **Alternatives**: switching the gate adapter now, which would collide with #194.

### D7 The fingerprint appends the pack-level files

- **Decision**: Presets in path order, then `pack.md`, then `pack-manifest.yaml`, after the templates.
- **Rationale**: A pack without them keeps its exact digest list (NFR-002), following ADR-0040 decision 9.

### D8 The placeholder key for known failures

- **Decision**: `WholePackExpectation` admits `spec-kitty/spec-kitty-open-packs#TBD-<slug>` beside the numbered form.
- **Rationale**: Squad finding F6 and the parent-session instruction. The parent session removes the alternative
  when it replaces the placeholders.

## Coordination update (parent session, 2026-10-28)

- **`pack.md` consumes #194's `markdown` content form** (decision `DM-01M60HM8BV7CE17HYE1QS829K4`, which supersedes D3
  and the specify decision `pack_md_form`). Sibling #194 builds the form in its PR-A. This mission builds no form of
  its own. The `pack.md` work package comes last, depends on PR-A merging, and rebases onto it. Until then
  `other:pack.md` stays on the expected-red list, and only tests may stub the form.
- **ADR number 0043** (0041 is #227's, 0042 is #194's). Every "ADR-0042" in this dossier written before this update
  means ADR-0043.

## Post-plan squad (2026-10-28)

Lenses: `architect-alphonso` (top tier), `reviewer-renata` (top tier) and `randy-reducer` (mid tier). All were
profile-loaded and read-only. Each finding has exactly one disposition.

| # | Sev | Lens | Finding | Disposition |
|---|---|---|---|---|
| P1 | HIGH | Architect | Validate mode changes behaviour. Presets and the manifest move from `Excluded` to `Mapped`, so `FileSystemPackInspector` schema-checks them. The pinned ones become BLOCKING `ARTIFACT_SCHEMA_ERROR` findings, and `FileSystemPackInspectorTest`'s built-in validate test (empty findings) breaks. | **changed**: validate mode reports them, as ADR-0036 decision 3's table already says for "every other schema-mapped file". Load and validate agree, so there is no split-brain. The built-in validate test is re-pinned to exactly the 3 findings. ADR-0043 records the new divergence from core's `validate_pack`, and the conformance register gains it. Nothing is served: `PackValidation` has no controller. |
| P2 | HIGH | Architect | `preset.schema.json` declares `id`, so the inspector's derived `ArtifactKinds` would treat presets as artifacts. The id == stem check would also live only in storage. | **changed**: `ArtifactKinds` leaves out `preset` explicitly, and `VocabularyEqualityTest` stays green. One storage helper (`PresetFiles.stemMismatch`) is called by both the store and the inspector, so validate and load refuse the same preset. |
| P3 | MED | Architect | The manifest is read in `PackRead` while its value lives on `LoadedPack`. `DomainRepositoryService.load` would parse it on every registration, and the gate has no seam for it. | **changed**: `WholePackStore` is the manifest's only owner. It reads the manifest itself, records an unreadable one as a violation on the aggregate it builds, and writes it last. `FilesystemPackStore` and `DomainRepositoryService` never parse it, so registrations pay nothing (NFR-004). The gate reads and writes the manifest through `PackManifestYaml` and calls the same `PackManifests.recompute`. |
| P4 | MED | Architect | Restamping refuses a real manifest whose constituents include unbound kinds (built-in: 23 procedures, which are unbound until #194), and `DomainRoundTrip` writes artefacts at `<id>.<token>.yaml`. | **changed**: the gate adapter restamps through a pairing. Each constituent `(kind, id)` is paired with the file the adapter wrote for that artefact, and each preset is written in place. A constituent of an unbound kind refuses, naming the path. The post-OP-2 outcome is recorded: until #194 binds procedures, the manifest's red reason moves to "constituent procedures/… not written", and ADR-0043 says so. The corrected-copy proof lists only modelled constituents until #194 lands, then all of them. |
| P5 | MED | Architect | A registration copies the live tree and cannot drop presets or `pack.md`. A test "through a registration" proves nothing about `Pack.with`. | **changed**: `toBuilder` and `with` are tested directly. The registration test asserts the presets stay byte-identical and the manifest becomes stale (the known gap, deferred to #110). |
| P6 | LOW | Architect | Plan and data model disagree on where the stem check lives and on `restamp`'s signature. | **accepted**: the stem check lives in the store, through `PresetFiles`. `recompute(dso, bytesAt)` takes `bytesAt` over the staging directory. |
| P7 | LOW | Architect, Reviewer | `presets: []` and an absent `presets` are two spellings of one meaning. | **accepted**: `presets` gets `minItems: 1`. |
| P8 | LOW | Architect | The rule of three has fired: asset files, presets and the document are three pack-level slots. | **deferred_with_rationale**: extracting a `PackLevelContent` value touches `Pack`, which #194 also edits. ADR-0043 records that the trigger has fired and names the extraction as a follow-up on epic #186. |
| P9 | LOW | Architect | Adding a component to the public record `LoadedPack` is an API break. | **accepted**: a secondary 4-argument constructor defaults to no manifest. |
| R1 | HIGH | Reviewer | The seam test's corrected-copy property (0 violations, a whole write) breaks once presets and the manifest are refused. | **accepted**: the corrected copy also applies OP-1 and OP-2. A test helper generates the sanitized presets and manifest. The copy then loads with 0 violations and writes whole, and the manifest's derived fields verify on both sides. The original gives exactly 9 pinned refusals. |
| R2 | HIGH | Reviewer | "`pack.yaml` (2 files) green in the gate" is wrong (the gate runs only on built-in) and would pass with no change. | **accepted**: SC-001 is split. Built-in `pack.yaml` is green in the gate, internal `pack.yaml` in the seam test, and both are marked [ratchet] and no-op passable. The non-fakeable part is the paired descriptor refusals. |
| R3 | HIGH | Reviewer | None of the 274 pinned files has a CR, so hash parity passes without the normalisation. The pinned manifest can only be read raw. | **accepted**: NFR-001 adds fixed CRLF, lone-CR and mixed vectors. The parity test reads the pinned manifest with the raw YAML loader. |
| R4 | HIGH | Reviewer | The manifest's known-failure entry would not self-expire when OP-2 lands, because a service-side refusal follows. | **changed**: see P4. The known-failure reason states the follow-on, and ADR-0043 records the adapter's dependency on #194. |
| R5 | HIGH | Reviewer | The gate's refusal text and the seam test's violation text must be the same string. | **accepted**: binding acceptance check: each new known failure's `at` equals the seam violation `refused: <reason>` character for character, with no kind prefix and no unknown-field suffix. |
| R6 | HIGH | Reviewer | The lossy-bytes control does not cover `pack.md`, and `withPayloads` rebuilds `Pack` without presets. | **accepted**: `withPayloads` keeps the presets. The `pack.md` controls move to the `pack.md` work package (markdown form, #194). There, a lossy `pack.md` writer goes red. |
| R7 | MED | Reviewer | An explicit `must_have: false` is written without the key, and the comparator applies no defaults, so it goes red. It is also two spellings of one meaning. | **accepted**: `must_have` is `const: true`. Present means must-have; there is no false. A paired control covers it. |
| R8 | MED | Reviewer | Python `json.dumps` equals JCS only with `ensure_ascii=False`. | **accepted**: the vectors are computed with `ensure_ascii=False`, and an RFC 8785 example vector is added. |
| R9 | MED | Reviewer | The comparator's manifest rule skips hashes, so a writer that keeps stale hashes passes unless verification is wired in. | **accepted**: a paired control. A writer that keeps a stale `content_hash` goes red at `constituents{<path>}.content_hash`. |
| R10 | MED | Reviewer | A `#TBD-<slug>` loader alternative is tolerance. | **accepted**: only the two named slugs are admitted, and the alternative is deleted when they are replaced. The hand-off lists it as a pre-merge check, together with "no `#TBD-` in the merge diff". |
| R11 | MED | Reviewer | Validate output for built-in goes from 6 to 9 findings, a second behaviour change. | **accepted**: see P1. It is stated in the plan's Charter Check and pinned in tests. |
| R12 | MED | Reviewer | Some C-002 edits are deletions or narrowings: the two exclusions, three expected-red entries, `REQUIRED_KINDS`/`bound`, and `**/presets` narrowed to `*/presets`. | **accepted**: the plan lists each non-append edit with its justification. |
| R13 | LOW | Reviewer | The gate kind names are not given. | **accepted**: FR-014 names `preset` and `pack-manifest` (and `pack-document` comes with #194's form). |
| R14 | LOW | Reviewer | Traceability: SC-005 and NFR-002 are not mapped, FR-016 is already delivered, and preset entry paths are unchecked. | **accepted**: they are mapped in tasks. FR-016 is marked delivered (research). The manifest's preset entry `path` must equal `presets/<id>.yaml`, and the reader checks it. |
| R15 | LOW | Reviewer | "3 presets" leaves out the open-packs fixture. | **accepted**: counts corrected in the plan. |
| D1r | MED | Reducer | A third hand-rolled SHA-256. | **accepted**: use `Payload.of(bytes).sha256()`. `Fingerprint` was rejected because of its framing and `sha256:` prefix. |
| D2r | MED | Reducer | `restamp` and `stale` walk the derived fields twice. | **accepted**: one `recompute(dso, bytesAt)` returns the stamped DSO and the missing paths. The writer refuses when paths are missing. The gate's stale list is a diff between the recomputed and the read DSO. |
| D3r | MED | Reducer | The preset and manifest reads would copy the parse/validate/read body a third time. | **accepted**: a private `readValidated(schema, text, file, YamlFormat)`. `PresetYaml` and `PackManifestYaml` implement `YamlFormat` and reuse `YamlFields` and `YamlDumpEmitter`. |
| D4r | MED | Reducer | The gate builds a restamp-and-write path whose only output is a refusal. | **changed**: superseded by P4. The pairing restamp gives a real green once the upstream fixes land, so the path does more than refuse. |
| D5r | LOW | Reducer | `PackDescriptorWriter` is dead code, and its javadoc links `undeclaredKeys`. | **accepted**: delete it in the descriptor-closure work package. It is already in that work package's touch set. |
| D6r | LOW | Reducer | `Listing.documents` is a list for one path. | **changed**: the `pack.md` listing is #194-form work. The tail of `digested` (presets, then `pack.md`, then the manifest) is built in one ordered step. |
| D7r | LOW | Reducer | Duplicate keyed-sort logic in `Pack`. | **accepted**: generalise `sortedByPath` to a keyed helper. |
