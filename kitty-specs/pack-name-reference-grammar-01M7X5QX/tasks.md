# Tasks: pack name reference grammar (#582)

**Mission**: `pack-name-reference-grammar-01M7X5QX` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Two work packages in sequence: a tidy-first refactor that changes no behavior (WP01), then the grammar change (WP02).
This is Tier R: a separate reviewer session reviews each WP.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Kernel: `IdentifierGrammar.SLUG_AS_WRITTEN` and `PackQualifiedReference.QUALIFIER` (today's grammar) | WP01 | |
| T002 | `SubstepCandidate.GRAMMAR` reads `QUALIFIER`; no anchor stripping | WP01 | |
| T003 | Pin tests read `QUALIFIER`: `PackSchemasTest`, `VocabularyEqualityTest` | WP01 | [P] |
| T004 | Parity test: all 15 qualifier sites discovered (any position) and mapped to `isPackName` | WP02 | |
| T005 | Blast radius; a planted change to `QUALIFIER` fails the pins | WP01 | |
| T006 | Red test: `a_b::…` and its siblings refused at parse, `a-b::…` accepted (commit seen red) | WP02 | |
| T007 | Kernel: `isPackName` = `PackIdentifier.isPackId`; `QUALIFIER` = slug; refusal names `GRAMMAR_DETAIL` | WP02 | |
| T008 | `GuardImplementation`: qualifier through `isPackName`, cap 63 | WP02 | |
| T009 | Contracts: 13 qualifier patterns, descriptor `name`, `PackRegistration.name`, `RequiringPack` | WP02 | |
| T010 | Parity: the 63-cap documented exception for qualifiers; the pack-name and pack-id sites map to `PACK_ID` | WP02 | |
| T011 | `### Breaking` entries (charter fragment, packs CHANGELOG), docs changelog entry, living docs | WP02 | [P] |
| T012 | Fixtures canonical; blast-radius suites and gates green | WP02 | |
| T013 | `GuardReference`: pack part through `isPackName`, cap 63, total 135; lifecycle `ref` 135 | WP02 | |
| T014 | Load test: a 64-character qualifier on a graph edge is refused at load with the pack-id detail | WP02 | |

## WP01 — One statement of the pack-name grammar (tidy-first)

**Prompt**: [tasks/WP01-one-statement-of-the-pack-name-grammar.md](tasks/WP01-one-statement-of-the-pack-name-grammar.md)
**Goal**: state the qualifier grammar once in the kernel, and make `SubstepCandidate` and the pin tests read it. No
change in behavior. **Priority**: P1. **Dependencies**: none.
**Independent test**: all of `:kernel`, `:missions`, `:packs` and `:contracts` pass unchanged. A planted change to
`QUALIFIER` fails the pin tests.

T001 Kernel written form (WP01)
T002 SubstepCandidate reads the written form (WP01)
T003 Pin tests read the written form (WP01)
T005 Blast radius and planted pin break (WP01)

**Risks**:
- `VocabularyEqualityTest` parses the qualifier prefix by regex.
- `identifierLike` must not start mapping other patterns.

## WP02 — The pack-name part of a reference is a pack id

**Prompt**: [tasks/WP02-the-pack-name-part-is-a-pack-id.md](tasks/WP02-the-pack-name-part-is-a-pack-id.md)
**Goal**: change the grammar to `slug(63)` in the kernel, in `GuardImplementation` and `GuardReference`, and in every
contract site; parity sees the 15 qualifier sites; under
`### Breaking`. **Priority**: P1. **Dependencies**: Depends on WP01.
**Independent test**: `a_b::directive:D1` is refused at parse with `PackIdentifier.GRAMMAR_DETAIL`, while `a-b::…`
parses. The parity test passes with the documented exception.

T004 Parity test discovers qualifier sites (WP02)
T006 Red test first (WP02)
T007 Kernel grammar change (WP02)
T008 GuardImplementation follows (WP02)
T009 Contract and pack schemas follow (WP02)
T010 Parity: documented exception and pack-name sites (WP02)
T011 Changelog entries and living docs (WP02)
T012 Fixtures canonical and gates (WP02)
T013 GuardReference follows (WP02)
T014 Load path refuses a 64-character qualifier (WP02)

**Risks**:
- A fixture in `:packs` or `:missions` may carry an old-grammar name.
- The generated client must still compile.
- No lookahead may enter the charter contract.
