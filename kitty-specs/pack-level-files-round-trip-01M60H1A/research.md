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

## Post-plan squad (to be filled when it runs)

See the section appended below after the squad.
