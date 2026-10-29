# Mission Specification: Procedure, anti-pattern and toolguide guide round trip

**Mission Branch**: `feat/194a-procedure-antipattern-toolguide`
**Created**: 2026-10-28 (revised 2026-10-29 after the post-plan review squad)
**Status**: Draft
**Input**: Charter #194 PR-A (part of epic #186; absorbs #168): round-trip the procedure kind with a sanitized storage
shape, carry inline anti-patterns with their owners (graph-node tags stay under #236), and round-trip the toolguide
guides in a structured shape. Grounding: `research.md` and the squad reports under `research/`.

## Intent Summary (confirmed 2026-10-28)

A charter maintainer runs the whole-pack round trip against the pinned Charter Pack content. Procedures are read
into the charter domain and written back with equal meaning (ADR-0036), in a deliberately designed storage shape;
the anti-patterns that procedures and styleguides carry inline ride inside their owner; and the guide each toolguide
points to is read and written together with its toolguide, in a structured shape. The rule that must always hold:
storage shapes are designed, not mirrored from the pack, and no reader tolerates a retired or variant field.

Operator decisions shaping this mission (2026-10-28/29, recorded on #194 and under `decisions/`):

- The sanitized-shape requirement wins over #194's "round-trip as-is" wording.
- Operate as if core does not exist (epic #186): no core issue, no compatibility with core's current formats.
- `procedure.schema.json` is sanitized in place. Pinned actors map to roles: `human` becomes `operator`; `agent` and
  `system` steps carry no role.
- Anti-pattern graph-node tags stay under #236.
- The toolguide guide is structured (fixed blocks plus freeform sections of `title` and `content`), stored as a
  Markdown companion and parsed by ADR-0040's `markdown` content form, which this mission builds for #195 too.
- `content` names a body field in new shapes; renaming merged kinds is a follow-up on #186.
- The pinned packs are migrated to the new shapes with this service's own writer and re-pinned; the known-failure
  list holds residual divergences only.
- The HTTP toolguide registration and response carry the guide's content (registration may instead send a URI the
  service fetches, directly and never through a proxy); the API never assumes a shared filesystem.
- Delivery is two PRs: PR-A1 (the `markdown` form) lands first so #195 can rebase; PR-A2 carries the rest.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Markdown documents compare by structure (Priority: P1, PR-A1)

A maintainer's pack holds Markdown files governed by a `markdown` file-kind rule. Each is read as a structured
document (frontmatter, title, preamble, sections) and written back so that it compares equal by that structure.

**Why this priority**: sibling #195 needs it for `pack.md`; the toolguide guide builds on it.

**Independent Test**: a fixture pack with a `markdown` rule round-trips through the whole-pack store; every pinned
Markdown file (72 at the current pin) parses and writes back byte for byte.

**Acceptance Scenarios**:

1. **Given** a Markdown file under a `markdown` rule, **When** the pack round-trips, **Then** it compares equal.
2. **Given** the same file with one character changed inside a section, **When** compared, **Then** the gate reports
   a difference at that section (positive control).
3. **Given** a line that looks like a heading inside a code fence, **When** parsed, **Then** it does not start a
   section; the same line outside a fence does.
4. **Given** a change of blank lines between blocks only, **When** compared, **Then** the files compare equal.
5. **Given** an unclosed fence, a CR or BOM, an H1 that is not the first block, an H1 or H2 indented by 1–3 spaces,
   or a setext underline, **When** read, **Then** the read is refused naming the line; each refusal has an accepted
   neighbour fixture.

### User Story 2 - Toolguide guides travel with their toolguide (Priority: P1, PR-A2)

A maintainer loads a pack whose toolguides name a guide. Each guide is read as a structured guide belonging to its
toolguide and written back with equal meaning.

**Independent Test**: all 24 toolguides of the built-in, internal and qa packs at the new pin round-trip with their
guides; the gate no longer reports `other:toolguides`.

**Acceptance Scenarios**:

1. **Given** a toolguide naming `<id>.guide.md` beside it, **When** the pack round-trips, **Then** the guide compares
   equal block by block and section by section.
2. **Given** a guide missing `overview`, with fixed blocks out of order or duplicated, with a preamble, with
   frontmatter, or with an H1 that differs from the toolguide title, **When** read, **Then** it is refused naming
   the problem; the corrected guide is accepted.
3. **Given** a toolguide naming a guide that does not exist, that leaves the pack, that differs from another file
   only by letter case, or that another rule already classifies (such as `pack.md`), **When** the pack is read,
   **Then** the read is refused naming the toolguide and the path.
4. **Given** a guide whose prose links to a styleguide or tactic, **When** read, **Then** the link stays content and
   no relationship is created.

### User Story 3 - Procedures round-trip in a sanitized shape (Priority: P1, PR-A2)

A maintainer loads a pack with procedures. Each is read into a domain procedure and written back in the designed
shape: identity, title, description, entry and exit conditions, ordered steps that may name a role, anti-pattern
entries, and references.

**Independent Test**: all 41 procedure files of the three packs at the new pin round-trip; a legacy-shaped fixture is
refused while its sanitized twin is accepted.

**Acceptance Scenarios**:

1. **Given** a sanitized procedure, **When** it round-trips, **Then** it is equal in meaning.
2. **Given** the same procedure using a retired field (`name`, `purpose`, step `actor`, anti-pattern `name` or
   `description`), **When** read, **Then** it is refused (ADR-0036); its sanitized twin is accepted.
3. **Given** a step naming a role, **When** it round-trips, **Then** the role is kept; a step without a role stays
   without one.
4. **Given** a procedure with references whose targets exist, **When** the pack is loaded, **Then** the references
   are kept on the procedure and no relationship is created from them, while a tactic carrying the same reference
   shape in the same fixture does create its relationship (positive control).

### User Story 4 - Inline anti-patterns ride with their owner (Priority: P2, PR-A2)

**Acceptance Scenarios**:

1. **Given** a procedure anti-pattern with only a title, with a title and content, or with one or both examples,
   **When** the procedure round-trips, **Then** the entry is equal.
2. **Given** the pinned styleguides with inline anti-patterns, **When** they round-trip, **Then** they stay equal
   (ratchet: already green).
3. **Given** the pinned `anti_pattern.graph.yaml`, **When** the gate runs, **Then** it is red for node tags only,
   exactly as its expected-red entry under #236 says.

### User Story 5 - Register and read a toolguide with its guide over HTTP (Priority: P2, PR-A2)

An API client registers a toolguide and supplies its guide either as content or as a URI, and reads it back with the
guide's content. The service never expects a shared filesystem.

**Independent Test**: contract scenarios for registration with content, with a URI (stub fetcher), and the refusals;
a fetcher test matrix runs offline.

**Acceptance Scenarios**:

1. **Given** a registration carrying `guide` content, **When** it is accepted and the toolguide is read, **Then** the
   response carries the same structured guide (an H1 added from the title when the content had none).
2. **Given** a registration carrying an allowlisted HTTPS `guideUri`, **When** it is accepted, **Then** the fetched
   guide is stored and read back as if sent as content; a later PUT with the same URI fetches again.
3. **Given** both `guide` and `guideUri`, neither, or an empty value, **When** registered, **Then** it is refused.
4. **Given** guide content over 1 MiB, or content that is not a valid guide, **When** registered, **Then** it is
   refused naming the problem and nothing is stored.
5. **Given** a URI that is not HTTPS, whose host is not on the allowlist (the allowlist is empty by default), carries
   user information or is an IP literal, resolves to a non-global address (directly or after a redirect), redirects
   more than 3 times or to a non-HTTPS or non-allowlisted target, takes longer than 10 s in total, exceeds 1 MiB, is
   encoded or not UTF-8, **When** registered, **Then** it is refused with a typed refusal and nothing is stored;
   each limit has an accepted value just inside it.

### Edge Cases

- A guide or procedure in a retired shape is refused, never coerced (C-005).
- Two toolguides naming the same guide, or a guide differing from another file only by case: refused, as a client
  error (409/422) over HTTP, never a server error.
- An empty Markdown file is a valid document; a document that is only frontmatter is valid.
- A guide that leaves the pack root or names a YAML file is refused (ADR-0039).
- The internal and qa packs are vendored in this repository and must equal the pinned open-packs content (except the
  listed local delta); they are migrated and re-vendored with the re-pin.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Sanitized procedure shape | As a maintainer, I want the procedure schema and storage shape to follow the cross-kind sanitized-shape rules (required string `schema_version` "1.0", `id` equal to the file stem, `title`, `description`, steps with an optional `role`, anti-pattern entries `{title, content?, bad_example?, good_example?}`, one shared `Reference` shape, closed objects) so that procedures are stored consistently with the other kinds. | High | Open | [build] | no |
| FR-002 | Procedure round trip | As a maintainer, I want procedures read into a domain procedure and written back with equal meaning so that the `procedure` kind leaves the gate's red baseline. | High | Open | [build] | no |
| FR-003 | Procedure anti-patterns | As a maintainer, I want procedure anti-pattern entries to round-trip in the new entry shape with their procedure so that no anti-pattern is lost. | High | Open | [build] | no |
| FR-003a | Styleguide anti-patterns unchanged | As a maintainer, I want styleguide anti-patterns to keep round-tripping in their merged shape so that this mission does not regress them. | Medium | Open | [ratchet] | yes — paired with FR-003's new entries on the same pinned pack |
| FR-004 | Structured toolguide guide | As a maintainer, I want the guide a toolguide names read as a structured guide (fixed blocks `overview` (required), `when_to_use`, `setup`, `usage`, `cautions`, `known_limitations`, `references`, plus freeform `sections` of `title` and `content`) at `<id>.guide.md` beside the toolguide and written back with equal meaning so that `other:toolguides` leaves the gate's red baseline. | High | Open | [build] | no |
| FR-005 | Markdown content form | As a maintainer, I want ADR-0040's `markdown` content form built (a document of optional frontmatter, optional title, preamble and H2 sections, compared by that structure) for `files` rules, including applying a rule's structure schema, so that #195's pack.md, the guides and later mission step prompts share one Markdown model. | High | Open | [build] | no |
| FR-005a | Markdown companions | As a maintainer, I want a companion to declare a content form (`text` as today, or `markdown` with a structure schema) and the gate to classify, pair and compare companions by their form so that a guide is never compared as YAML. | High | Open | [build] | no |
| FR-006 | Divergence fixed upstream | As a maintainer, I want the pinned packs (and the vendored internal and qa packs) migrated to the new shapes by this service's own writer through a throwaway harness, verified by an old-to-new field mapping that fails on any unmapped key, landed in spec-kitty-open-packs with an issue per class of change, and `openPacksRef` moved to the merged commit, so that the gate is exact without tolerance logic. | High | Open | [build] | no |
| FR-007 | Anti-pattern graph unchanged | As a maintainer, I want `anti_pattern.graph.yaml` to stay expected-red for its node tags only, under #236. | Medium | Open | [ratchet] | yes — paired with FR-003's positive round trip on the same pinned pack |
| FR-008 | Procedure references kept, not minted | As a maintainer, I want procedure references stored but never turned into relationships until #189 rules, proven against a tactic that does create its relationship from the same reference shape in the same fixture. | Medium | Open | [build] | no |
| FR-009 | Baseline and file kinds updated | As a maintainer, I want the `procedure` and `other:toolguides` entries removed from the gate's expected-red baseline and a `markdown` file-kind rule for guides added, so that the files are classified by their new rules. | Medium | Open | [build] | no |
| FR-010 | Guide over HTTP without shared files | As an API client, I want a toolguide registration to carry exactly one of the guide's content or a URI the service fetches, and the toolguide response to carry the guide's content instead of a path, so that the API assumes no shared filesystem. | High | Open | [build] | no |
| FR-011 | Guide H1 | As a maintainer, I want the guide's H1 written from the toolguide title, an incoming guide allowed to omit it, and an incoming H1 that differs from the title refused, so that the title has one home. | Medium | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Corpus coverage | 41 of 41 procedure files and 24 of 24 toolguides with their guides in the built-in, internal and qa packs at the new pin round-trip; the known-failure list holds no procedure or guide entry unless a residual divergence is named with its open-packs issue. | Reliability | High | Open |
| NFR-002 | Gate exactness | The whole-pack gate passes with zero unlisted reds and zero listed items that round-trip, at the new pin; lossy-writer positive controls (YAML, Markdown, procedure) turn it red. | Reliability | High | Open |
| NFR-003 | Build | `./gradlew verifyAll` passes on Java 25 in CI, including the Java style check, the ArchUnit gate and the contract suite, and the docs checks pass. | Maintainability | High | Open |
| NFR-004 | Safe guide fetch | A guide URI is fetched directly (no proxy), only over HTTPS, only from an exactly matched allowlisted host (empty by default), only to global unicast addresses checked at resolution and on every redirect (DNS rebinding closed by a resolver guard), with at most 3 redirects, a 10-second deadline over the whole exchange, a 1 MiB streamed cap, identity encoding and UTF-8 only, and no credentials forwarded; every refusal is typed. | Security | High | Open |
| NFR-005 | Request bounds | An inline `guide` over 1 MiB is refused; the fetch runs before the pack's write lock is taken. | Performance | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Only the authorized HTTP change | The charter HTTP contract changes only in the toolguide registration and response (guide content or URI in, guide content out, `guidePath` removed), under the 0.x rules (ADR-0022) with a `### Breaking` CHANGELOG entry and scenarios. | Technical | High | Open |
| C-002 | Shared files append-only | Edits to shared files are append-only, except removing this mission's own baseline entries, moving `openPacksRef`, re-vendoring the internal and qa packs, and refreshing other entries whose recorded text the re-pin changes. | Process | High | Open |
| C-003 | Decision recorded | The sanitized-shape rules and the shapes this mission designs are recorded in ADR-0042; ADR-0039 and ADR-0040 are amended. | Process | High | Open |
| C-004 | Design against the charter, not core | Shapes follow the charter's own model; no compatibility with core's current formats and no core issue (operator ruling on epic #186, 2026-10-28). | Process | High | Open |
| C-005 | No tolerance logic | No reader, writer or binding accepts a retired or variant field; a test scans the main sources for retired field names. | Technical | High | Open |
| C-006 | Two PRs | PR-A1 carries only the `markdown` form, its `files`-rule schema application and the ADR-0040 amendment, and lands first; PR-A2 carries the rest. | Process | Medium | Open |
| C-007 | Harness is throwaway | The migration harness lives in the test sources only, never runs in the default build, and is deleted before PR-A2 is reviewed. | Process | High | Open |

### Key Entities

- **Procedure**: identity, title, description, entry and exit conditions, ordered steps (each optionally naming a
  role), anti-pattern entries, references.
- **Anti-pattern entry**: a value inside its owner: title, optional content, optional bad and good examples.
- **Guide**: a value of its toolguide: fixed blocks and freeform sections, stored as Markdown beside the toolguide.
- **Markdown document**: optional frontmatter, optional title, preamble, sections of title and content.
- **Known-failure entry**: one pinned pack file expected to differ, with where, why and its open-packs issue.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At the new pin the gate passes with the `procedure` and `other:toolguides` entries removed from its red baseline. — [build] · no-op passable: no
- **SC-002**: 41 of 41 procedure files round-trip at the new pin, and a legacy-shaped procedure is refused while its sanitized twin is accepted. — [build] · no-op passable: no
- **SC-003**: 24 of 24 toolguides round-trip with their guides at the new pin; a one-character change inside a guide section is reported by the gate. — [build] · no-op passable: no
- **SC-004**: Every procedure anti-pattern entry at the new pin survives the round trip. — [build] · no-op passable: no
- **SC-005**: No main source accepts a retired field name, proven by a source scan and by retired-shape fixtures that are refused. — [build] · no-op passable: no
- **SC-006**: 72 of 72 Markdown files at the current pin parse and write back byte for byte (PR-A1). — [build] · no-op passable: no

## Assumptions

- The gate covers the built-in pack; internal and qa are covered by corpus tests and the vendored-pack check.
- Linking inline anti-patterns to graph nodes is out of scope (#154).
- The 14 sanitized-shape violations in merged schemas, and renaming body fields to `content` in merged kinds
  (styleguide anti-patterns, templates, assets), are listed on epic #186 and not changed here.
- Deployments whose only egress is a proxy cannot use `guideUri` (operator decision); they send content.

## Out of Scope

- Mission types, mission steps and mission templates (PR-B of #194).
- Graph-node tags (#236); edge authority for references (#189); the fate of the standalone procedure kind (#150).
- Any change to core.
