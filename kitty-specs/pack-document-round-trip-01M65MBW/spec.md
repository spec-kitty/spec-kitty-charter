# Mission Specification: Round-trip pack.md through the markdown content form

**Mission Branch**: `issue-260-pack-md-round-trip`
**Created**: 2026-10-30
**Status**: Draft
**Input**: Issue #260 (part of epic #186, split from #195): hold and write each pack's `pack.md` through the `markdown`
content form #194 built, so the whole-pack gate models it and `other:pack.md` leaves the expected-red list. Former WP08
of mission `pack-level-files-round-trip-01M60H1A` (FR-005, SC-001b, plan D7).

## Intent Summary

A Charter Pack carries a hand-authored Markdown description beside its descriptor: `pack.md`. The service reads every
other pack-level file into the `Pack` aggregate (asset files, presets, the derived manifest), but not this one, so the
whole-pack round-trip gate lists it as `other:pack.md` and expects it red.

This mission reads `pack.md` into the aggregate as a document with the Markdown model's shape (optional frontmatter, an
optional title, a preamble and the H2 sections), writes it back in the canonical form, digests it in the source
fingerprint between the presets and `pack-manifest.yaml`, validates it in validate mode, and models it in the gate as
`pack-document`. It uses #194's `markdown` content form, its codec and its comparison, and builds no form of its own
(ADR-0042 decision 8, `DM-01M60HM8BV7CE17HYE1QS829K4`).

Confirmed by the orchestrator's brief (semi-automatic session). Two calls are recorded as Decision Moments:
`01M65MKX2FG5E5SAM9D57BX2AE` (the domain holds a `PackDocument` value with an infra mapper, since the domain cannot
depend on infra) and `01M65MKYMAAQE6RQZEY361SBTP` (frontmatter compares as loaded YAML values). Clarified at the
plan-point squad: that frontmatter rule is *stricter* than ADR-0036 (`1` and `1.0` differ; empty and absent differ),
not ADR-0036 meaning; it is sound while one reader compares a source with its own canonical write, and it stays an
open review item for the operator. Moving #194's Markdown model into the domain (which would let `PackDocument` and
`Guide` share its rules) was weighed and left as a follow-up: it rewrites #194's merged code (C-002) and the files #194
PR-B is editing.

The issue cites "ADR-0043 decisions 8, 9, 10"; these are ADR-0042's (research R1). This mission amends ADR-0042,
ADR-0037 and ADR-0040.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The built-in pack's description round-trips (Priority: P1)

The whole-pack gate reads the built-in pack, writes it through the domain and reads it back. `pack.md` is part of the
pack it writes and means the same thing.

**Why this priority**: It is the point of #260 and the last pack-level file the gate does not model.

**Independent Test**: Run `:packs:wholePackRoundTrip` at the pinned open-packs commit and check that `pack.md` is
green under kind `pack-document`, with `other:pack.md` gone from the expected-red list.

**Acceptance Scenarios**:

1. **Given** the built-in pack at the pin, **When** the gate round-trips it, **Then** `pack.md` is classified
   `pack-document`, written at `pack.md`, and its written model equals its source model.
2. **Given** the expected-red list without `other:pack.md`, **When** the gate runs, **Then** it passes with no stale
   or unlisted entry.
3. **Given** a writer that changes one character of the document's preamble before the save, **When** the gate
   round-trips the built-in pack, **Then** `pack.md` is red at `preamble`, while the faithful run of the same pack is
   green.

---

### User Story 2 - A pack author's pack.md is judged by #194's rule (Priority: P1)

A pack author writes `pack.md` with or without frontmatter, with Windows line endings, or without a final newline. The
service treats each exactly as the `markdown` content form does.

**Why this priority**: Each case is a way a hand-edited file drifts; a silent normalization or a silent loss is the
failure the charter forbids (ADR-0036).

**Independent Test**: Load and write fixture packs whose `pack.md` covers each case through `WholePackStore`.

**Acceptance Scenarios**:

1. **Given** a `pack.md` with frontmatter, an H1, a preamble and two H2 sections, **When** the pack is loaded and
   written, **Then** the written document has the same model (frontmatter as loaded YAML values, every string
   exactly).
2. **Given** a `pack.md` with no frontmatter, **Then** the written document has no frontmatter either (absent stays
   absent; an empty `---`/`---` frontmatter stays empty).
3. **Given** a `pack.md` with no final newline, **Then** it loads, and the written file is the canonical text with one
   final newline, equal in meaning.
4. **Given** a `pack.md` with CRLF line endings, **Then** the load records a violation on `pack.md` naming the line and
   "a carriage return is not admitted", and the write refuses the pack: nothing is normalized and nothing is dropped.
5. **Given** an empty `pack.md`, **Then** it loads as the empty document and is written back.

---

### User Story 3 - The fingerprint and validate mode cover pack.md (Priority: P2)

A change to `pack.md` moves the pack's source fingerprint, and validate mode refuses a `pack.md` the load refuses.

**Why this priority**: A file the service loads must not escape the fingerprint (ADR-0042 decision 9), and validate
mode and the load must agree.

**Independent Test**: Digest a pack with and without `pack.md`, and with an edited `pack.md`; validate a pack whose
`pack.md` is refused.

**Acceptance Scenarios**:

1. **Given** a pack with presets, `pack.md` and a manifest, **Then** the digest list ends with the presets in path
   order, then `pack.md`, then `pack-manifest.yaml`.
2. **Given** a pack without `pack.md`, **Then** its digest list is unchanged from today.
3. **Given** a `pack.md` with a carriage return, **When** the pack is validated, **Then** validate mode reports the
   same refusal on `pack.md` as the load.

### Edge Cases

- A `pack.md` below the pack root (`docs/pack.md`) is not the pack document: the rule matches `*/pack.md` only.
- Any other Markdown file a `files` rule classifies, other than a companion, still fails loudly (`notStored`): this
  mission routes `pack-document` alone.
- A `pack.md` that breaks the structure schema is a violation on `pack.md`, like a preset that breaks its schema. No
  file on disk reaches this today: every model the codec reads fits the permissive `pack-document` schema, and the
  codec refuses first (plan-point squad). The validation is kept so the rule obeys ADR-0040 decision 1.
- A `PackDocument` built in code whose part cannot stand as Markdown (a column-0 `# X` in the preamble) refuses the
  write as `VALIDATION_FAILED`, naming `pack.md`, and leaves no target.
- `pack-manifest.yaml` does not list `pack.md`; the manifest restamp is unchanged.
- A byte order mark, an unclosed fence or another ambiguity is refused at its line, as #194's codec refuses it.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `contracts/packs/file-kinds.yaml` gains a `files` rule `*/pack.md` with `schema: pack-document` and
  `content_form: markdown`, and `contracts/packs/schemas/pack-document.schema.json` is its structure schema over the
  markdown model (optional frontmatter object, optional non-empty title, a preamble, H2 sections). (#260 "Rule")
- **FR-002**: The domain `Pack` holds an optional `PackDocument`, a value with the markdown model's shape, carried by
  `toBuilder`, `with` and the builder. An infra mapper converts it to and from `MarkdownDocument`. (#260 "domain slot";
  DM `01M65MKX2FG5E5SAM9D57BX2AE`)
- **FR-003**: The store lists `pack.md` in its own slot, reads it through `MarkdownCodec`, validates the model against
  `pack-document`, and records a refusal or a breach as a violation on `pack.md`. Every other non-companion Markdown
  file still fails loudly. (#260 "Read the document in the store")
- **FR-004**: `WholePackStore` reads the document into the aggregate, counts `pack.md` as modelled, and writes it in
  the canonical form before the manifest; `DomainRepositoryService` writes it in place for the gate. (#260 "write it in
  WholePackStore")
- **FR-005**: The source fingerprint digests `pack.md` between the presets and `pack-manifest.yaml`; a pack without it
  keeps its digest list. (ADR-0042 decision 9)
- **FR-006**: Validate mode validates `pack.md` against its structure schema, refusing what the load refuses.
  (ADR-0040 amendment: "the first consumer wires … validate mode")
- **FR-007**: `DomainRoundTrip` models `pack-document`, writing it in place and pairing it by path;
  `WholePackRoundTripTest.REQUIRED_KINDS` gains it; `withPayloads` keeps the document. (#260 "Gate")
- **FR-008**: `expected-red.yaml` loses `other:pack.md`, and nothing else in it changes. (#260 "Gate")
- **FR-009**: A lossy body writer goes red at `pack.md`, beside a green faithful run of the same pack. (#260 "Gate")
- **FR-010**: Tests cover `pack.md` with and without frontmatter, with CRLF, and with no final newline, each judged by
  #194's rule. (#260 "Tests")
- **FR-011**: ADR-0042 decision 8 is marked done and its "pack.md pending" qualifiers are lifted, as are ADR-0037's;
  ADR-0040 records the first stored `markdown` files rule and the frontmatter-equality ruling; a changelog entry and the
  logical model say what changed. (#260 "Docs")

### Non-Functional Requirements

- **NFR-001**: No new suppression; method complexity ≤ 15; `./gradlew verifyAll` green on Java 25.
- **NFR-002**: A pack without `pack.md` keeps its exact fingerprint and digest list.

### Constraints

- **C-001**: `contracts/charter/**` is not edited. The pack schema set grows by one additive schema.
- **C-002**: No markdown form, codec or comparison of our own: #194's `MarkdownCodec`, `MarkdownDocument` and
  `MeaningComparator.compareMarkdown` are used as they are.
- **C-003**: `PackLevelContent` extraction (ADR-0042 decision 10) is out of scope.
- **C-004**: Shared files with #194 PR-B (`file-kinds.yaml`, `expected-red.yaml`, the gate) are edited append-only, and
  PR-B's entries are not touched.
- **C-005**: Test first (red, then green); every refusal is paired with a clean control on the same fixture.

### Key Entities

- **PackDocument** (domain): optional frontmatter (a mapping of loaded YAML values), optional title, preamble, sections
  (each a title and its content).
- **pack-document** (gate kind and file kind): the `*/pack.md` rule's schema name.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In `:packs:wholePackRoundTrip`, built-in `pack.md` is green as `pack-document`, and `other:pack.md` is
  not in `expected-red.yaml`.
- **SC-002**: `./gradlew verifyAll` and `./gradlew :packs:wholePackRoundTrip` pass.
- **SC-003**: The lossy-writer test is red at `pack.md` (`preamble`) and the faithful run is green.

## Assumptions

- The pinned open-packs commit (`5df92a5d`) stays the pin; its only `pack.md` is the built-in pack's.
- The built-in pack's fingerprint (and ETag) changes once, by design (ADR-0042 decision 9).
