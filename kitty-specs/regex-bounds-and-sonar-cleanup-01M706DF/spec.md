# Mission Specification: Bounded identifier patterns and Sonar cleanup

**Mission Branch**: `fix/regex-bounds-and-sonar-cleanup`
**Created**: 2026-11-09
**Status**: Draft
**Input**: Issues #440, #441, #442 (SonarCloud first analysis, 2026-11-09), a research squad on main `5458e1fa`, and four operator decisions recorded as Decision Moments.

## Intent Summary

A pack author, an operator registering a pack, or the service loading its bundled and synced packs reads pack and
mission YAML. Today an identifier or a YAML number scalar long enough (about 10 000 characters of `a-a-a-…` or
`1:5:5:…`) overflows the stack in its pattern match. The `StackOverflowError` escapes every refusal path, so a
registration answers a bare 500 with no refusal code, a background fetch dies, and a load at startup fails the
service. The desired outcome: such input is refused as an unreadable pack, or read as an ordinary value, and never
crashes the reader. The invariant: the language each identifier accepts at or below its cap does not change.

Operator decisions (2026-11-09), recorded as Decision Moments:

- `01M706DW5XHBA4781BYHVDFNT5`: three layers of defence: pattern matching that cannot recurse, a domain length
  cap, and a schema `maxLength` on pack ids.
- `01M706DYNJRHY13DDN5C269JTT`: kebab identifiers are capped at 64 characters, and path-like values (a toolguide
  executable, a mission expected-artifact key) at 255.
- `01M706E15NPBQMX2AGC88F65NF`: the schema cap is recorded as a Breaking contract change (ADR-0022, 0.x rule).
- `01M706E3N6ZHX1SJQXNZ9DY6ZJ`: the git source path pattern is hardened too, and a regression guard is added.
  The charter request id caps (#443) and a shared kernel grammar (#444) are follow-ups, out of scope.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A long identifier is refused, not a crash (Priority: P1)

An operator registers a pack whose guard, procedure, toolguide, lifecycle, mission type or other id is longer than
the cap. The service refuses the pack as unreadable, with the refusal code the contract names, and keeps serving.

**Why this priority**: today the same input answers a 500 with no refusal code, or kills a worker, or stops startup
(#440). It is reachable from pack content.

**Independent Test**: register a pack carrying a 65-character id and a 10 000-segment id; both are refused as
`500 pack_unreadable`, and a following request is served normally.

**Acceptance Scenarios**:

1. **Given** a pack whose guard id is 65 characters of valid kebab grammar, **When** it is registered, **Then** the
   schema refuses it and the answer is `500 pack_unreadable`.
2. **Given** an identifier of 10 000 `-a` segments built directly in the domain, **When** the domain type is
   constructed, **Then** it is refused with the type's ordinary refusal, and no `Error` is thrown.
3. **Given** an id of exactly 64 characters, **When** it is read, **Then** it is accepted.

---

### User Story 2 - A long YAML number scalar is read, not a crash (Priority: P2)

A pack file holds a plain scalar such as `1:5:5:5…` tens of thousands of characters long. The reader resolves it as
it resolves any scalar (a number or a string) and does not overflow (#441).

**Why this priority**: the YAML resolver runs on every plain scalar that starts with a digit, before schema
validation, so a schema cap cannot protect it.

**Independent Test**: read a YAML document holding a 10 000-segment sexagesimal scalar; it reads without an `Error`.

**Acceptance Scenarios**:

1. **Given** a plain scalar of `1` followed by 10 000 `:5` segments, **When** the YAML is read, **Then** reading
   completes without an `Error`.
2. **Given** short integer, float and sexagesimal scalars, **When** they are read, **Then** they resolve exactly as
   before.

---

### User Story 3 - Maintainers keep the guarantee (Priority: P3)

A maintainer adding a new identifier pattern is warned by the build when the pattern uses a repeated group that
can recurse, and the guarded `Optional` access in the pack policy message reads as one expression (#442).

**Why this priority**: it keeps the fix from regressing; it carries no user-facing behaviour.

**Independent Test**: a deliberately recursive pattern planted in a fixture is flagged by the guard; the real main
code passes it.

**Acceptance Scenarios**:

1. **Given** main code that compiles a pattern with a repeated group that is not possessive, **When** the guard
   runs, **Then** it fails naming the file and line.
2. **Given** the pack policy message for a fragment node of a known and of an unknown kind, **When** it is
   rendered, **Then** the text is unchanged from today.

### Edge Cases

- An id at exactly the cap is accepted; one character over is refused.
- Edge strings keep their verdict: empty, leading or trailing `-`, `--`, upper case, a leading digit where the type
  requires a letter.
- `GuardReference` and `GuardImplementation` still expose the same captured parts (pack name, guard name,
  implementation).
- A git source URL path at the 2048-character URL cap still matches as before.
- A short sexagesimal scalar (`1:30`) still resolves as a number, as today.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Identifier matching cannot recurse | As an operator, I want every pack and mission identifier check to complete on input of any length so that a long id never overflows the stack. Covers GuardReference, LifecycleIdentifier, MissionTypeIdentifier, ExpectedArtifact, GuardIdentifier, ProcedureIdentifier, ToolguideIdentifier, GuardImplementation, Executable, Preset and SkillIdentifier. | High | Open | [build] | no — a 10 000-segment input throws today |
| FR-002 | Accepted language unchanged | As a pack author, I want every id that is valid today and within its cap to stay valid, and every invalid id to stay invalid, so that no pack changes meaning. | High | Open | [ratchet] | yes — paired with FR-001 on the same edge-string table |
| FR-003 | Captured parts unchanged | As a maintainer, I want GuardReference and GuardImplementation to expose the same captured parts so that callers keep working. | High | Open | [ratchet] | yes — paired with FR-001 on the same inputs |
| FR-004 | Domain length cap | As an operator, I want kebab identifiers refused above 64 characters and executable paths and expected-artifact keys above 255, checked before any pattern match, so that a second line of defence holds. | High | Open | [build] | no — a 65-character id is accepted today |
| FR-005 | Schema length cap | As an operator, I want every pack id field with a pattern in the pack schemas to carry maxLength 64 (255 for path-like fields) so that a long id is refused as `pack_unreadable` before the domain sees it. | High | Open | [build] | no — a 65-character id passes the schema today |
| FR-006 | Breaking contract fragment | As a client author, I want the schema cap recorded under `### Breaking` in a contract change fragment so that the narrowing is announced. | Medium | Open | [build] | no — no fragment exists |
| FR-007 | Real packs within the caps | As an operator, I want every id in the pinned open-packs and in this repository's packs checked against the caps so that the narrowing refuses no real pack. | High | Open | [ratchet] | yes — positive control: the whole-pack round trip still passes |
| FR-008 | YAML number resolver cannot recurse | As an operator, I want a long sexagesimal scalar read without an overflow so that a pack file cannot crash the YAML reader. | High | Open | [build] | no — a 10 000-segment scalar throws today |
| FR-009 | YAML resolution unchanged | As a pack author, I want short int, float and sexagesimal scalars to resolve exactly as before. | High | Open | [ratchet] | yes — paired with FR-008 on the same resolver |
| FR-010 | Git source path hardened | As an operator, I want the git source URL path check to be unable to recurse, so that it no longer depends on the URL length cap alone. | Low | Open | [build] | yes — positive control: a path at the URL cap still matches |
| FR-011 | Regression guard | As a maintainer, I want the build to flag main code that compiles a pattern with a repeated group that is not possessive, so that the defect does not return. The guard is heuristic and says so. | Medium | Open | [build] | no — a planted recursive pattern must be flagged |
| FR-012 | Pack policy message as one expression | As a maintainer, I want the fragment-node message in the pack policy to resolve the kind once, with the same text for a known and an unknown kind. | Low | Open | [ratchet] | yes — paired with the existing message tests |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Bounded match time | Each identifier check on a 1 000 000-character input completes in under 100 ms on a developer machine, and never throws an `Error`. | Reliability | High | Open |
| NFR-002 | Mutation floors held | The mutation floors of ADR-0062 hold: packs domain 85, missions 70, packs infra.storage 85, packs infra.fetch 70. | Quality | High | Open |
| NFR-003 | No full suites in mission | Only targeted Gradle tasks run during the mission; CI owns verifyAll. | Process | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Out of scope | Charter request schema id caps (#443) and a shared kernel kebab grammar (#444) are not changed here. | Scope | High | Open |
| C-002 | Contract versioning | The schema change follows ADR-0022 and ADR-0047: a fragment under `contracts/charter/changes/`, no release cut. | Technical | High | Open |
| C-003 | Red first | Each fix lands after a test that fails for the defect's own reason (an `Error` on a long input, or an over-cap id accepted). | Process | High | Open |
| C-004 | Charter model | Behaviour is judged against the charter's own model, contract and packs (charter, PROJECT_002). | Governance | High | Open |

### Key Entities

- **Kebab identifier**: a lower-case, hyphen-separated id (guard, procedure, toolguide, lifecycle, mission type,
  preset, skill). Capped at 64.
- **Path-like value**: a toolguide executable path or a mission expected-artifact key. Capped at 255.
- **YAML plain scalar**: an unquoted YAML value resolved to an int, a float or a string.

## Success Criteria *(mandatory)*

- **SC-001**: No identifier or YAML scalar input of any length makes the service throw an `Error`: 0 of the 13
  affected patterns overflow on a 1 000 000-character input — [build] · no-op passable: no
- **SC-002**: Every id in the pinned open-packs and this repository's packs remains readable: 100 % pass the whole-pack
  round trip — [ratchet] · no-op passable: yes, paired with SC-001
- **SC-003**: The edge-string tables show 0 verdict changes for ids within their caps — [ratchet] · no-op passable:
  yes, paired with SC-001
- **SC-004**: The SonarCloud findings named in #440, #441 and #442 are no longer reported after the next nightly
  analysis — [build] · no-op passable: no

## Assumptions

- A 64-character cap fits every real kebab id: the longest found locally is 44 characters. FR-007 confirms it
  against the pinned open-packs.
- A registration refused by the schema answers `500 pack_unreadable`, as other schema refusals of pack content do
  today.
- Possessive quantifiers accept exactly the same language here, because each repeated segment begins with a
  character (`-`, `.`, `/`, `:`) the preceding class cannot consume; the research squad fuzzed 300 000 strings per
  pattern with zero differences.
