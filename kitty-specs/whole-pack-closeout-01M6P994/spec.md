# Mission Specification: Whole-pack round trip closeout (epic #186)

**Mission Branch**: `epic-186-closeout`
**Created**: 2026-11-05
**Status**: Draft
**Input**: Operator ruling of 2026-11-05 on epic #186 ("the epic's closeout"), issues #299 and #319, and the
parent session's brief.

## Intent Summary

The operator ruled on 2026-11-05 that epic #186 (the whole-pack round trip) closes once two known gaps are fixed
and the whole-pack round-trip check becomes part of the one required CI check.

- **Primary actors**: a pack author or service caller who builds mission step texts (#299); an operator who
  configures a pack root (#319); a contributor whose pull request must keep packs round-tripping (closeout).
- **Triggers and outcomes**:
  - A mission step's prompt or guidelines text that the pack's Markdown form refuses is refused when the step
    text is built, with the same verdict the store's write gives, not only later at write.
  - Validate mode over a pack root spelled with `..` (or `.`) gives the same verdict as over the same directory
    spelled plainly.
  - Every pull request that changes what the whole-pack round trip covers must pass it before it can merge.
- **Rule that must always hold**: the mission domain stays pure (it reaches no packs code); the pack root's
  configured name (a link's name included) is what classification reads.
- **Assumption**: the brief is comprehensive, so brief-intake mode needs no gap questions. The one design choice,
  #299's option, is recorded as Decision Moment `01M6P99GKMK38VC2YVES9T3N5D` (kernel move, per the brief).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Mission step text is refused when built (Priority: P1)

A caller builds a mission step's document (its title, preamble and sections). Text the Markdown form refuses (a
heading with surrounding blanks or a line break, a block starting or ending with a blank line, a carriage return, a
line that would read as a heading or fence inside a block, an opening that would read as frontmatter) is refused
right there, with a constraint breach naming the rule, instead of passing until the step is written.

**Why this priority**: it closes #299; the ruling makes it a precondition of closing the epic.

**Independent Test**: build step documents and sections from refused and accepted texts; each refused text breaches,
each accepted one builds, and the store write accepts everything that builds.

**Acceptance Scenarios**:

1. **Given** a step section whose content has a leading blank line, **When** it is built, **Then** it is refused
   with a constraint breach.
2. **Given** a step document whose title contains a line break, **When** it is built, **Then** it is refused.
3. **Given** a step document with no title and no description whose preamble starts with `---`, **When** it is
   built, **Then** it is refused, as the codec would refuse the written file.
4. **Given** a valid step document read from a pack, **When** the pack is loaded and written back, **Then** the
   round trip is unchanged.

### User Story 2 - A pack root with `..` validates like its plain spelling (Priority: P1)

An operator configures a pack root as `<dir>/x/../pack`. Validate mode over it reports the same verdict as over
`<dir>/pack`: a mission type whose output templates exist is not reported unreadable.

**Why this priority**: it closes #319; the ruling makes it a precondition of closing the epic.

**Independent Test**: a validate-mode test roots a mission-type pack with a named template at `<dir>/x/../pack`
and at `<dir>/pack` and compares the verdicts; it fails before the fix.

**Acceptance Scenarios**:

1. **Given** a mission-type pack whose output names an existing template, rooted at `<dir>/x/../pack`, **When**
   validate mode runs, **Then** no file is reported unreadable, the same as at `<dir>/pack`.
2. **Given** the same pack whose output names a missing template, **When** validate mode runs at either
   spelling, **Then** the mission-type file is reported, so the check still bites.
3. **Given** a pack root that is a symbolic link, **When** validate mode runs, **Then** it classifies under the
   link's configured name, as before.

### User Story 3 - The whole-pack round trip is a required check (Priority: P2)

A contributor opens a pull request that touches what the whole-pack round trip covers. The required check
`CI gate / gate` runs the whole-pack round trip and fails when it fails; a pull request that touches none of it
skips the group and still passes the gate.

**Why this priority**: the operator's closeout ruling; it depends on stories 1 and 2 landing green.

**Independent Test**: the workflow definitions show the gate needs the whole-pack group and treats only `success`
or `skipped` as passing; this pull request's own CI runs the group under the gate.

**Acceptance Scenarios**:

1. **Given** a pull request changing service code, **When** CI runs, **Then** `CI gate / gate` runs the whole-pack
   round trip and requires it to pass.
2. **Given** a docs-only pull request, **When** CI runs, **Then** the whole-pack group is skipped and the gate passes.
3. **Given** the whole-pack workflow, **When** a pull request is opened, **Then** it does not also run as a separate,
   non-required check (no double run).

### Edge Cases

- A pack root spelled with a `.` segment behaves as one with `..`.
- A pack root that does not exist keeps its "is not a directory" refusal.
- A step document with a description keeps its frontmatter, so the opening rule does not apply to its preamble.
- Text that passes the line rules but could not round-trip (the detailed description's YAML guards) is still caught by the codec's write check; agreement is exact on the line rules, not on the YAML path.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Shared line rules | As a maintainer, I want the line-level Markdown rules held once where both the mission and the pack domain can use them, so that #299's step texts and the pack's documents refuse the same texts. | High | Open | [build] | no — the mission domain cannot reach the rules today |
| FR-002 | Step document refuses at build | As a caller, I want a step document and a step section to refuse, when built, every title, preamble, section or opening text the Markdown form's line rules refuse (the rules the codec applies), and every step that builds to be writable, so that a bad step text never reaches the write. | High | Open | [build] | no — refused texts build today; paired with accepted texts on the same fixtures |
| FR-003 | Pack documents unchanged | As a pack author, I want the pack document and guide checks unchanged by the move, so that nothing that loads today is refused. | High | Open | [ratchet] | yes — the existing tests of those checks are the positive control |
| FR-004 | Pack root normalized once in validate mode | As an operator, I want validate mode to read a pack root spelled with `..` or `.` as its normalized path, so that the verdict matches the plain spelling. | High | Open | [build] | no — the red-first test fails before the fix |
| FR-005 | Named-file check still bites | As a pack author, I want a missing output template still reported at either spelling, so that the fix cannot pass by switching the check off. | High | Open | [ratchet] | no — same fixture with and without the template |
| FR-006 | Whole-pack under the required gate | As a contributor, I want the whole-pack round trip run by `CI gate / gate` when its paths change, so that every pull request keeps packs round-tripping. | High | Open | [build] | no — the gate's needs list and verdict step change |
| FR-007 | Docs and changelog match | As a reader, I want the gate guide, contributing guide, plans, roadmap, ADR-0023 and a changelog entry to say the whole-pack round trip is required, so that no doc calls it optional. | Medium | Open | [build] | no — today's docs say "not a required check" |
| FR-008 | Epic closed | As the operator, I want #299, #319 and #186 closed by the merged pull request, so that the epic's closeout is recorded. | Medium | Open | [build] | no — the issues are open |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Build gates green | `./gradlew verifyAll` passes on Java 25 with 0 failures, and the whole-pack round trip passes at `openPacksRef`. | Reliability | High | Open |
| NFR-002 | Docs checks green | `check_docs.py all`, markdownlint-cli2 and cspell report 0 errors; every frontmatter description is 50-180 characters. | Maintainability | High | Open |
| NFR-003 | Small, reviewable diff | Production changes stay within 15 Java files outside tests; no Gradle module or ArchUnit rule is added. | Maintainability | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Mission domain stays pure | `MISSIONS_DOMAIN_IS_PURE` and `KERNEL_IS_PURE` hold unchanged. | Technical | High | Open |
| C-002 | Spellings untouched | The fix for #319 does not change `Spellings`; it normalizes the root once where validate mode resolves it. | Technical | High | Open |
| C-003 | Configured name kept | The root is normalized, not resolved to its real path, so a linked root keeps its configured name (#284). | Technical | High | Open |
| C-004 | One required check | The required check stays `CI gate / gate` (ADR-0023); no new required check name. | Process | High | Open |
| C-005 | Parallel sessions | No edits to files owned by sessions B, C and D (service domain + contracts/charter + open-packs; WorktreeGuard + deployable config; client module + docs) beyond what this mission needs. | Process | Medium | Open |
| C-006 | No release | No release cut, no contract tag, no push to `main`; the operator merges. | Process | High | Open |

### Key Entities

- **Step document / step section**: a mission step's prompt or guidelines text: title, description, preamble, sections.
- **Line rules**: the Markdown form's per-line checks for headings and blocks, and the document opening check.
- **Pack root**: the configured directory of a pack, as validate mode receives it.
- **CI gate**: the one required check, `CI gate / gate`, which needs every triggered group to pass.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every step text the line rules refuse is refused when the step is built, field by field; every accepted text builds, writes and reads back equal. Write-time-only refusals of the description's YAML (FrontmatterYaml guards) stay at write. — [build] · no-op passable: no
- **SC-002**: Validate mode gives identical verdicts for a pack at its plain and its `..` spelling, 0 spurious unreadable files. — [build] · no-op passable: no
- **SC-003**: 100% of pull requests changing whole-pack paths need the whole-pack round trip to pass before merge. — [build] · no-op passable: no
- **SC-004**: 0 docs describe the whole-pack round trip as not required after the change. — [build] · no-op passable: no

## Assumptions

- The whole-pack round trip is green on `main` at `openPacksRef` (it runs green behind its pinned known-failure list),
  so requiring it does not block unrelated pull requests.
- Running the whole-pack group under the gate on the same path filter as the service group is acceptable cost
  (about one extra 15-minute job on service changes).
