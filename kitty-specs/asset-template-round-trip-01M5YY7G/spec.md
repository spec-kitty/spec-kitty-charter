# Mission Specification: Asset and template round trip

**Mission Branch**: `feat/asset-template-round-trip`
**Created**: 2026-10-27
**Status**: Draft
**Input**: Issue #193 (epic #186, Wave 1): "Model asset and template: payload-bearing kinds, read and write. Neither is
charter-activatable; both carry a file payload (binary or text). Load them into the domain with their metadata and
write the payload back byte-for-byte."

Grounding: pre-spec squad and format-dispatch squad (2026-10-27); operator rulings recorded on issue #193 and as this
mission's decisions (`decisions/`).

## Intent Summary (confirmed 2026-10-27)

A pack author or operator loads a whole Charter Pack into the service and writes it back to a new directory. Every
**asset** (its sidecar descriptor and the payload file the descriptor names) and every **template** under the pack's
top-level `templates/` directory is read into the domain and written back: descriptors compare by meaning, payloads
and templates compare byte for byte (scripts, Markdown, HTML, fonts, images, and the sidecar-less
`docs_structural_lint.config.yaml`). The whole-pack round-trip gate turns the asset, asset-payload and template kinds
green. How a file is read and compared is declared by its file-kind rule (its **content form**), never by its
extension or `mime`. Unknown sidecar fields and a sidecar whose payload is missing are refused. The six built-in
sidecars whose `path` is anchored at the pack root are a pack inconsistency: refused, pinned as expected, and fixed in
spec-kitty-open-packs.

## Domain Language

| Term | Meaning | Avoid |
|------|---------|-------|
| Asset | A pack resource described by a sidecar descriptor (`*.asset.yaml`: `id`, `mime`, `path`, optional `title`) and carrying one payload. Not activatable. | "blob" for the whole asset |
| Asset payload | The file an asset's `path` names, relative to the pack's `assets/` directory; also a file under `assets/` that no sidecar names (ruling R1). Held as bytes. | "attachment" |
| Template | A file under the pack's top-level `templates/` directory. Its local id is its path relative to that directory (`writing/meeting-minutes.md` → `template:writing/meeting-minutes.md`). Not activatable. Mission templates (`missions/*/templates/`) are not in this mission. | "mission template" for these |
| Content form | What a file-kind rule declares about how its files are read, held, written and compared: `yaml` (default), `markdown`, `text`, `bytes`. | "format" (collides with the YAML codec), "encoding", "mime" |
| Mime | The asset's declared media type: validated metadata, never a selector of content form. | |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - An asset round-trips whole (Priority: P1)

A pack author loads a pack whose `assets/` holds sidecars and payloads (scripts, Markdown, HTML, fonts, images) and
writes it to a fresh directory. Each sidecar is equal by meaning; each payload is byte-identical.

**Why this priority**: Assets are the bulk of the unmodelled content (20 sidecars, 23 payload files across the open
packs), including the only binary files.

**Independent Test**: Load and write a pack holding a text payload and a binary payload (PNG/OTF); compare sidecars by
meaning and payloads by bytes.

**Acceptance Scenarios**:

1. **Given** a pack with `assets/debrief/logo.png` and its sidecar, **When** it is loaded and written, **Then** the
   written PNG has the same bytes (same SHA-256) and the sidecar is equal by meaning.
2. **Given** a sidecar with `title` absent, **When** it is written, **Then** the written sidecar has no `title` and is
   equal to the source.
3. **Given** a sidecar with an undeclared key (`surprise: 1`), **When** the pack is loaded, **Then** the sidecar is
   refused naming the key, and a sidecar without it in the same fixture loads.
4. **Given** a sidecar whose `path` names no file under `assets/`, **When** the pack is loaded, **Then** the sidecar is
   refused as a violation naming the missing path, and a sidecar whose payload exists in the same fixture loads.
5. **Given** `assets/docs_structural_lint.config.yaml`, which no sidecar names, **When** the pack is loaded and
   written, **Then** it is written back byte-identical.

### User Story 2 - A template round-trips (Priority: P1)

A pack author loads a pack with `templates/writing/meeting-minutes.md` and writes it back. The template is an artifact
of the pack, identified by `template:writing/meeting-minutes.md`, and its bytes are unchanged, frontmatter included.

**Why this priority**: Templates are the second kind the issue names; without them the gate cannot go green for
`other:templates`.

**Independent Test**: Load and write a pack with a nested template; the template is listed as an artifact with the
expected id and its bytes are equal.

**Acceptance Scenarios**:

1. **Given** `templates/writing/meeting-minutes.md`, **When** loaded, **Then** the pack holds a template with local id
   `writing/meeting-minutes.md`; **When** written, **Then** the file is byte-identical.
2. **Given** a template whose bytes differ by one byte after a lossy write, **When** compared, **Then** the gate reports
   a difference for that file.

### User Story 3 - The whole-pack gate records the kinds as green (Priority: P1)

The maintainer runs the whole-pack round-trip gate against the pinned open-packs commit. The asset, asset-payload and
template kinds are compared and green, except exactly the six built-in sidecars that are pinned as expected.

**Independent Test**: Run the gate; the kind summary shows the three kinds with only the pinned exceptions.

**Acceptance Scenarios**:

1. **Given** the pinned open-packs commit, **When** the gate runs, **Then** every built-in asset payload (other than the
   excluded READMEs) and both top-level templates are green, and the six built-in sidecars are the only expected
   reds, each pinned to its first difference or refusal.
2. **Given** a deliberately lossy payload writer (one flipped byte), **When** the gate runs, **Then** it fails at that
   file.
3. **Given** the open-packs fix lands and the pin moves, **When** the gate runs, **Then** it fails asking for the six
   pinned entries to be removed.

### User Story 4 - Every open pack still loads honestly (Priority: P2)

The maintainer runs the open-packs round trip over every pack. Packs without the inconsistency load with no
violations and round-trip; the built-in pack's six refused sidecars are reported as exactly the pinned, issue-linked
expected violations.

**Acceptance Scenarios**:

1. **Given** the internal pack (fonts, PNG, executable scripts), **When** it is loaded and written, **Then** every
   payload is byte-identical (file mode is not compared).
2. **Given** the built-in pack, **When** loaded, **Then** the violations are exactly the six pinned ones; a new
   violation, or a pinned one that no longer occurs, fails the test.

### Edge Cases

- A sidecar `path` that escapes `assets/` (`..`, absolute, symlink): refused, as the pack policy already rules.
- A payload file that two sidecars name, or that a sidecar names and another rule also classifies: a pack
  inconsistency, refused and fixed upstream; no precedence logic.
- An empty payload or template file (0 bytes): round-trips as empty.
- A binary payload with NUL bytes, CR/LF byte pairs and no final newline: byte-identical.
- `README.md` under `assets/`: stays excluded, not an asset payload.
- A template named like a mission template (`<dir>/<file>`): a duplicate template id in a pack is refused by the pack's
  unique-id invariant.
- Two templates or payloads whose paths differ only by case: refused by the write, never silently merged.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Content form on file-kind rules | As a maintainer, I want every file-kind rule to declare its content form (`yaml` default, `markdown`, `text`, `bytes`) so that how a file is read and compared never depends on its extension or `mime`. | High | Open | [build] | no — a rule declaring `bytes` changes how the gate compares its files |
| FR-002 | Asset descriptor in the domain | As a pack author, I want each `*.asset.yaml` loaded as an asset artifact (id, mime, path, optional title, no schema version) and written back equal by meaning so that descriptors are not lost. | High | Open | [build] | no |
| FR-003 | Asset payload as bytes | As a pack author, I want the payload an asset names loaded with it and written back byte-identical (text and binary) so that scripts, documents, fonts and images survive. | High | Open | [build] | no — paired with a one-byte lossy-writer control |
| FR-004 | Sidecar-less payload | As a pack author, I want a file under `assets/` that no sidecar names (other than an excluded README) held as an asset payload and written back byte-identical. | Medium | Open | [build] | no |
| FR-005 | Missing payload refused | As a maintainer, I want a sidecar whose payload does not exist refused as a violation naming the path, so that a broken pack is never written as if whole. | High | Open | [build] | no — paired with a present-payload sidecar on the same fixture |
| FR-006 | Unknown sidecar fields refused | As a maintainer, I want a sidecar with an undeclared key refused naming the key. | High | Open | [build] | no — paired with a clean sidecar on the same fixture |
| FR-007 | Template in the domain | As a pack author, I want each file under the top-level `templates/` loaded as a template artifact whose local id is its path under `templates/`, and written back byte-identical. | High | Open | [build] | no |
| FR-008 | Census kind for payloads | As a maintainer, I want asset payloads counted by the gate as their own kind, with `README.md` under `assets/` still excluded, so that the gate can turn the kind green without dropping an exclusion. | High | Open | [build] | no |
| FR-009 | Gate flips exactly our kinds | As a maintainer, I want the gate's expected-red list to lose its `asset`, `other:assets` and `other:templates` kind entries and gain exactly six built-in sidecar file entries, each pinned to its exact refusal reason (the missing resolved path) and linked to #193 until the open-packs issue is filed (then to that issue), with nothing else changed; the gate then shows zero expected reds for `asset-payload` and `template`. | High | Open | [build] | no — the gate's two-way ratchet fails on any extra or stale entry |
| FR-010 | Pinned expected violations | As a maintainer, I want the open-packs round trip to accept exactly the six pinned built-in sidecar violations, matched by file and reason from the same pinned list the gate uses, failing on a new one or on a pinned one that no longer occurs; and a copy of the built-in pack with the six paths corrected round-trips whole (zero violations, six green sidecars, every file compared). | High | Open | [build] | no |
| FR-011 | Payloads in the source fingerprint | As an operator, I want a change to a payload or template to change the pack's source fingerprint, so that served content never goes stale behind an unchanged fingerprint. | High | Open | [build] | no — editing one payload byte must change the fingerprint |
| FR-012 | Relationship endpoints | As a pack author, I want `asset:` and `template:` URNs accepted as relationship endpoints once the artifacts exist. | Medium | Open | [build] | no — paired with an unknown-URN refusal on the same fixture |
| FR-013 | Mission templates untouched | As a maintainer, I want files under `missions/*/templates/` to stay outside this mission's kinds (counted as missions, #194). | Medium | Open | [ratchet] | yes — paired with FR-007's positive case on the same pack |
| FR-014 | Decision recorded | As a maintainer, I want ADR-0040 to record content form and the non-YAML widening of the pack registry, and ADR-0036's extension-based rows revised to point at the rule. | High | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Byte fidelity | 100% of asset payload and template files at the pinned open-packs commit (all packs) round-trip with equal SHA-256, other than the six pinned built-in cases. | Reliability | High | Open |
| NFR-002 | Fingerprint stability | Packs without assets or templates keep their source fingerprint unchanged (0 changed values across the test packs that have none). | Compatibility | High | Open |
| NFR-003 | Build gates | `./gradlew verifyAll` passes on Java 25 with zero new style, Error Prone, NullAway or ArchUnit findings. | Quality | High | Open |
| NFR-004 | Load cost | Loading the largest open pack holding payloads (internal, ~0.6 MB of payloads) adds under 1 second to its whole-pack load on the CI runner. | Performance | Low | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | HTTP contract frozen | `contracts/charter/openapi.yaml` is not changed; if a change becomes unavoidable, stop and raise it on #193. | Technical | High | Open |
| C-002 | Stay in our kinds | Shared files (registry wiring, file-kinds.yaml, gate baseline and exclusions, CHANGELOG, glossary, ADR index) get append-only blocks for asset, asset payload and template only. | Process | High | Open |
| C-003 | No special logic for packs | Pack inconsistencies are fixed in spec-kitty-open-packs; no pack-specific path anchoring (as core's built-in special case does). | Process | High | Open |
| C-004 | Bytes only | This mission builds only the `bytes` content form; `markdown` (#192) and `text` are declared names, built by the mission that first needs them. | Technical | Medium | Open |
| C-005 | File mode not meaning | File mode (executable bit) is not compared and not preserved. | Technical | Medium | Open |

### Key Entities

- **Asset**: pack artifact with id, mime, path (as authored, relative to `assets/`), optional title, and its payload.
- **Asset payload**: bytes plus the payload's path under `assets/`; equal when the bytes are equal.
- **Template**: pack artifact whose local id is its path under `templates/`, holding its bytes.
- **File-kind rule**: an entry of the pack vocabulary that maps files to a kind, schema and content form.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The whole-pack gate reports the asset, asset-payload and template kinds with zero unexpected reds and
  exactly six expected reds, at the pinned open-packs commit. — [build] · no-op passable: no
- **SC-002**: All 4 binary payloads (3 fonts, 1 image) and every text payload and template across the open packs
  round-trip byte-identical. — [build] · no-op passable: no
- **SC-003**: A one-byte change to any payload or template is reported by the gate as a difference at that file in
  100% of control runs. — [build] · no-op passable: no
- **SC-004**: Zero changes to the HTTP contract and zero edits outside this mission's kinds in shared files beyond
  append-only blocks. — [ratchet] · no-op passable: yes — paired with SC-001, which a no-op fails

## Assumptions

- The pinned open-packs commit (`openPacksRef`, 01edb4e) stays the pin for this mission; the open-packs fix for the six
  built-in sidecars lands later and its pin move removes the six entries.
- The sidecar `path` is relative to the pack's `assets/` directory, as the schema and core define it.
- Payload sizes stay at the kilobyte scale (largest ~156 KB), so holding payloads in memory is acceptable.
- Sibling #192 adds the `markdown` content form on the key this mission introduces.

## Out of Scope

- Mission templates and command templates (`missions/*/templates/`, #194); toolguide `guide_path` bodies (#186);
  `pack-manifest.yaml` `content_hash` treatment (#195); skill and glossary-pack bodies (#192).
- Activating assets or templates (they are not activatable); serving them over new endpoints.
