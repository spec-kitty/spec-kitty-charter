# Mission Specification: pack name reference grammar

**Mission Branch**: `kitty/mission-pack-name-reference-grammar-01M7X5QX`
**Created**: 2026-11-20
**Status**: Draft
**Input**: spec-kitty/spec-kitty-charter#582, "The pack-name part of a reference follows the pack-id grammar" (epic #549,
ADR-0075 decision 2). Tier R: a contract and grammar change.

## Context

Since #552 (PR #580), a pack id is `slug(63)`: lower-case letters and digits in hyphen-separated runs, with no leading,
trailing or doubled hyphen, at most 63 characters (`PackIdentifier.isPackId`). A pack-qualified reference
(`<pack-name>::<urn>`) names a pack by its id: `PackSourceStagers` requires the descriptor `name` to equal the registered
pack id. Yet the pack-name part of a reference still follows the older grammar `^[a-z0-9][a-z0-9_-]*$`, capped at 64
where capped at all. A reference such as `a_b::directive:D1`, `a--b::…`, `a-::…` or one with a 64-character pack name is
accepted when it is parsed and fails later as an unresolved reference, because no pack can carry that name.

Grounding on `main` 17d5ca2 (Phase 1 research) found the grammar in these places:

- **Kernel**: `PackQualifiedReference.PACK_NAME_GRAMMAR` and `isPackName`, used by `parse`, `typed`, `KindRef.inPack` and
  `Relationship`.
- **Missions**: `SubstepCandidate`, which rebuilds its written grammar from `PACK_NAME_GRAMMAR` by stripping the anchors.
- **Packs**: `GuardImplementation`, whose `<pack>::asset:<id>` qualifier is a second copy of the grammar
  (`IdentifierGrammar.SLUG`, capped at 64, not 63).
- **Charter contract** (`contracts/charter`, `contracts/_shared`):
  - the qualifier of `PackQualifiedReference.yaml` and `PackQualifiedReferenceWrite.yaml`;
  - the `seeAlso` items of `GlossaryTerm.yaml`, `GlossaryContextTerm.yaml` and `VocabularyTerm.yaml`;
  - the pack ids of `RequiringPack.packId` and `via[]`;
  - `PackRegistration.name`, through `Slug.yaml`.
- **Packs contract** (`contracts/packs/schemas`):
  - the descriptor `name`;
  - the qualifier of the graph-shard and org-fragment edge `source` and `target`, and of their term-target `if`;
  - the glossary `see_also` qualifier;
  - the mission-type substep `candidates` qualifier.

The activations `PackRef` named in the issue is already a `PackIdentifier` (#583); nothing is left to do there.

`IdentifierPatternParityTest` (#585/#586) checks no site above: it skips every pattern that contains `_` or `::`.

**Breakage census:** no reference or pack breaks.

- **spec-kitty-open-packs** `main` (3bedeef):
  - pack names: 0 of 24 are off `slug(63)`;
  - qualified references in reference fields: 0 of 607 are off it.
- **This repository**:
  - example packs: none off it;
  - test fixtures: one, `PackQualifiedReferenceTest`'s `open_packs-2::…`, which asserts that the old grammar is accepted.

No open-packs issue is needed.

## User Scenarios & Testing

### User Story 1 - A reference to a pack that cannot exist is refused when it is read (Priority: P1)

A pack author writes `my_pack::directive:D1`, or a client sends it. Before, it was accepted and later reported as an
unresolved reference. Now it is refused where the reference is parsed, and the refusal says what a pack name must be.

**Independent Test**: `PackQualifiedReference.parse("a_b::directive:D1")` throws, and the message carries
`PackIdentifier.GRAMMAR_DETAIL`. The same fixture with `a-b::directive:D1` parses.

**Acceptance Scenarios**:

1. **Given** the reference `a_b::directive:D1`, **When** it is parsed, **Then** it is refused, naming the pack-id grammar.
2. **Given** `a--b::…`, `a-::…`, `-a::…` or a 64-character pack name, **When** parsed, **Then** each is refused.
3. **Given** `a-b::…`, `1a::…` or a 63-character pack name, **When** parsed, **Then** each parses (positive control).

### User Story 2 - The contracts state the same grammar the service enforces (Priority: P1)

A contract consumer or pack author validates against the schemas. Every pattern that restates a pack name or pack id,
alone or as the `<pack>::` qualifier of a reference, gives the kernel's verdict. The one exception is documented: a
schema cannot cap the qualifier inside a longer pattern without lookahead, so the service refuses a 64-character
qualifier that the schema accepts.

**Independent Test**: the extended parity test maps every qualifier site and every pack-name or pack-id site to
`PackIdentifier.isPackId`, and agrees with it over the hostile corpus. A planted `_` in one qualifier fails it, naming the
site.

**Acceptance Scenarios**:

1. **Given** a pack whose descriptor `name` is `my_pack`, **When** the pack is validated, **Then** the descriptor schema
   refuses it.
2. **Given** a graph edge whose `target` is `a_b::directive:D1`, **When** the shard is validated, **Then** the schema
   refuses it.
3. **Given** a `PackQualifiedReferenceWrite` value `a_b::directive:D1`, **When** a client sends it, **Then** the request
   is refused `422 validation_failed`, as it is for any other malformed reference.

### User Story 3 - One grammar, stated once (Priority: P2)

A maintainer changes the pack-name grammar in one place and every site follows: kernel, missions, packs, and the schema
pin tests.

**Independent Test**: no class outside the kernel restates the pack-name grammar or strips anchors off it; a test pins
each schema's qualifier to the kernel's written form.

### Edge Cases

- A URN after `::` that contains `_` (for example `directive:DIRECTIVE_009`) stays valid: only the pack-name part
  changes.
- A substep candidate's id may still hold `_` and upper case; only its qualifier changes.
- A built-in guard (`builtin:<id>`) is unaffected; only the `<pack>::asset:` qualifier changes.
- The profile-id grammar (`RolePins`, `fallback_profile`, the policy `role_pins` keys, `MissionSubstepDelegation.kind`)
  keeps its underscores: #552 ruled it out of decision 2.

## Requirements

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Kernel pack-name part is a pack id | As a pack author, I want a reference's pack name checked by `PackIdentifier.isPackId` so that a reference to a pack that cannot exist is refused at parse. | High | Open | [build] | no — the red test on `a_b::` fails on `main` |
| FR-002 | Refusal names the grammar | As a pack author, I want the refusal to carry `PackIdentifier.GRAMMAR_DETAIL` so that I know what to write. | High | Open | [build] | no — `main` names `^[a-z0-9][a-z0-9_-]*$` |
| FR-003 | One predicate and one written form | As a maintainer, I want `KindRef`, `Relationship`, `SubstepCandidate` and `GuardImplementation` to check the pack name through the kernel's one predicate, and the schema pin tests to read the kernel's one written form, so that the grammar is stated once. | Medium | Open | [build] | no — `GuardImplementation` and `SubstepCandidate` hold their own copies on `main` |
| FR-004 | Charter contract qualifiers follow | As a contract consumer, I want the qualifier of `PackQualifiedReference`, `PackQualifiedReferenceWrite`, and the `seeAlso` of `GlossaryTerm`, `GlossaryContextTerm` and `VocabularyTerm`, written as the slug so that the contract refuses what the service refuses. | High | Open | [build] | no — the parity test fails on `main` once it maps the sites |
| FR-005 | Packs contract qualifiers follow | As a pack author, I want the qualifiers of the graph-shard, org-fragment, glossary and mission-type schemas written as the slug. | High | Open | [build] | no — as FR-004 |
| FR-006 | Pack names and ids follow | As a pack author, I want the descriptor `name`, `PackRegistration.name` and `RequiringPack.packId` and `via[]` on the pack-id form, the slug capped at 63, so that a name no pack id can equal is refused. | High | Open | [build] | no — `main` accepts `a_b` and 64 characters |
| FR-007 | Parity covers qualifiers and pack names | As a maintainer, I want `IdentifierPatternParityTest` to map every `::` qualifier site, and every pack-name or pack-id site, to `PackIdentifier.isPackId`, with the 63 cap on a qualifier as a documented exception, so that the sites cannot drift again. | High | Open | [build] | no — a planted `_` fails it |
| FR-008 | Breaking change recorded | As a consumer, I want the change under `### Breaking` in the charter contract fragment and in the packs contract CHANGELOG, saying what core's seam changes (ADR-0022). | High | Open | [build] | no — review checks that the entries are present |
| FR-009 | Fixtures canonical | As a maintainer, I want every fixture and example to carry only the canonical shape; the old shape appears only in refusal tests. | Medium | Open | [build] | no — `PackQualifiedReferenceTest` asserts that `open_packs-2::` is accepted on `main` |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Bounded check | The pack-name check stays linear and bounded: the length is checked before any match, and every repeat is possessive (#440). A 10,000-character qualifier is refused in under 50 ms. | Performance | High | Open |
| NFR-002 | No lookahead in the charter contract | No charter contract pattern gains a lookahead (ADR-0078: RE2-safe). | Compatibility | High | Open |
| NFR-003 | Gates green | `:kernel`, `:packs`, `:missions`, `:contracts` and `:pack-validation` tests, the style gates and the whole-pack round trip pass. | Reliability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Canonical model | Replace the grammar outright: no legacy reader, no deprecation window, no "core first" (charter, 2026-11-10). | Technical | High | Open |
| C-002 | Red first | The test refusing `a_b::…` at parse is committed and seen red before the fix. | Process | High | Open |
| C-003 | No PIT | No `pitest` or `checkMutationFloors` in this Mission (ADR-0062). | Process | High | Open |
| C-004 | Out of scope | The profile-id grammar, `Slug.yaml` as used for mission types and actions, and #556's closure walk are not changed. | Scope | Medium | Open |
| C-005 | Tier R review | Every WP review and the final review are done by a separate reviewer session. | Process | High | Open |

### Key Entities

- **Pack name (qualifier)**: the `<pack-name>` before `::` in a reference. It is a registered pack id, `slug(63)`.
- **Pack id**: `PackIdentifier`, the slug capped at 63 (ADR-0075 decision 2).
- **Descriptor `name`**: the pack's handle in `pack.yaml`, equal to its registered pack id.

## Success Criteria

### Measurable Outcomes

- **SC-001**: `a_b::directive:D1` is refused at parse with the grammar detail, and `a-b::directive:D1` parses on the same
  fixture — [build] · no-op passable: no
- **SC-002**: every contract and pack-schema site that restates a pack name or the `::` qualifier is discovered and
  agrees with `PackIdentifier.isPackId` (allowing the documented exception) — [build] · no-op passable: no
- **SC-003**: no source outside the kernel restates the pack-name grammar — [build] · no-op passable: no
- **SC-004**: the open-packs census stays at 0 references broken — [ratchet] · no-op passable: yes (positive control: 607 qualified references counted)
