# Implementation Plan: pack name reference grammar

**Branch**: `kitty/mission-pack-name-reference-grammar-01M7X5QX` | **Date**: 2026-11-20 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/pack-name-reference-grammar-01M7X5QX/spec.md` (#582)

## Summary

The pack-name part of a pack-qualified reference becomes a pack id (`slug(63)`, `PackIdentifier.isPackId`) everywhere
it is stated: the kernel, the two Java copies in `missions` and `packs`, and the 13 qualifier patterns and four pack-name
or pack-id patterns of the contracts. The old grammar is replaced outright. The work is done in two steps:

1. **Tidy first.** The grammar is stated once in the kernel, every Java site reads it, and the parity test learns to see
   qualifier sites. Behavior does not change.
2. **The change.** A red test refuses `a_b::…` at parse. Then the grammar changes in the kernel, and the contracts follow
   under `### Breaking`.

## Technical Context

**Language/Version**: Java 25 (Gradle, `./gradlew`), JSON Schema 2020-12 and OpenAPI 3.1 YAML.
**Primary Dependencies**:
- `:kernel`, which is pure Java;
- `:packs`, which validates with networknt json-schema-validator against `contracts/packs/schemas`;
- `:missions`;
- `:contracts`, where `IdentifierPatternParityTest` has test scope only on `:kernel`.

**Storage**: N/A.
**Testing**: JUnit 5 and AssertJ. The red test comes first. Planted breaks show that the parity test bites. No PIT (ADR-0062).
**Target Platform**: the charter service, its contracts, and pack authors validating against the packs schemas.
**Project Type**: single (multi-module Gradle).
**Performance Goals**: NFR-001. The pack-name check checks the length before any match.
**Constraints**:
- C-001 to C-005;
- NFR-002: no lookahead in the charter contract.

**Scale/Scope**: about 12 Java sources, 11 schema files, about 6 test files and 4 living docs.

## Design decisions

- **D1 — The kernel predicate.** `PackQualifiedReference.isPackName(name)` delegates to `PackIdentifier.isPackId`.
  `PACK_NAME_GRAMMAR`, a regex stated in the kernel, goes away. In its place is the written form below and
  `PackIdentifier.GRAMMAR_DETAIL` for the refusal.
- **D2 — One written form.** `PackQualifiedReference.QUALIFIER` is the optional qualifier as the contracts and
  `SubstepCandidate` write it: `([a-z0-9]+(-[a-z0-9]+)*::)?`.
  - It is built from a new `IdentifierGrammar.SLUG_AS_WRITTEN` (`[a-z0-9]+(-[a-z0-9]+)*`), the ECMA spelling of `SLUG`,
    which `KernelGrammar.SLUG` already restates.
  - `SubstepCandidate.GRAMMAR` and the schema pin tests read `QUALIFIER`, and nothing strips anchors any more.
  - In WP01, `QUALIFIER` still spells today's grammar (`([a-z0-9][a-z0-9_-]*::)?`), so behavior does not change.
- **D3 — The 63 cap is a documented exception.** A pattern cannot cap one part of a longer string without lookahead,
  and the charter contract carries none (ADR-0078, RE2-safe).
  - The schemas restate the slug; the service refuses a qualifier of 64 or more characters.
  - `IdentifierPatternParityTest` pins this exception as #580 pins the slot part.
  - Pack-name and pack-id fields that stand alone (the descriptor `name`, `PackRegistration.name`, `RequiringPack`) do
    carry `maxLength: 63`, and so map to `PACK_ID` under the existing rule.
- **D4 — Parity sees qualifiers.**
  - A pattern holding `::` is a qualifier site. The test strips the leading `^` and the newline guard, then requires the
    pattern to start with `QUALIFIER`; a `::` site that does not fails, naming the site.
  - The qualifier, compiled alone, gives `isPackName`'s verdict on every corpus row of at most 63 characters. Over 63,
    the schema accepts and the kernel refuses (D3).
- **D5 — GuardImplementation.**
  - The `<pack>::asset:` qualifier is read by `SPELLING_SHAPE`, then checked by `isPackName`.
  - The pre-match length guard uses `PackIdentifier.MAX_LENGTH` (63).
  - It moves in WP02, because today's guard is stricter than the old grammar; moving it in WP01 would loosen it.
- **D6 — The refusal text.**
  - `PackQualifiedReference` refuses with `"A pack-qualified reference names its pack by a pack id. " + GRAMMAR_DETAIL`,
    then the name.
  - `KindRef`, `Relationship` and `SubstepCandidate` reuse the same detail.
- **D7 — Contract versioning.** The 0.x line may break on a minor (ADR-0022), so each contract gets a `### Breaking`
  entry:
  - charter contract: a fragment `contracts/charter/changes/2026-11-20-pack-name-reference-grammar.md`;
  - packs contract: a line under `## 0.1.0-SNAPSHOT` in `contracts/packs/CHANGELOG.md`;
  - the repository changelog: an entry `docs/changelog/2026-11-20-pack-name-reference-grammar.md`.

  Core's seam: regenerate the client's validation patterns. No field changes shape.

## Charter Check

- **The canonical model:** the grammar is replaced outright, with no legacy reader (C-001). Fixtures use the new shape
  only; `open_packs-2::` survives only as a refusal case. Pass.
- **Contract versioning:** `### Breaking` is written in both contracts (FR-008). Pass.
- **Round trip:** no example pack changes, and the round-trip gate runs in CI. Pass.
- **No PIT in mission work (ADR-0062):** none is run. Pass.
- **Squad and review:** Tier R. A separate reviewer session reviews each WP and the whole Mission (C-005). The plan
  point-cut squad is folded into the WP01 review.
- **Issue rules:** #582 is closed by the PR (`Closes #582`). Epic #549 is ticked. Open-packs gets no issue, since the
  census found 0 breaks.

## Project Structure

### Documentation (this mission)

```
kitty-specs/pack-name-reference-grammar-01M7X5QX/
├── spec.md
├── plan.md
├── research/grounding.md   # the Phase 1 grounding map and fold table
└── tasks.md, tasks/WP01-*.md, tasks/WP02-*.md
```

### Source Code (repository root)

```
service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/
    PackQualifiedReference.java, IdentifierGrammar.java, KindRef.java, Relationship.java
service/missions/src/main/java/ai/speckitty/charter/missions/domain/SubstepCandidate.java
service/packs/src/main/java/ai/speckitty/charter/packs/domain/GuardImplementation.java
contracts/charter/schemas/{PackQualifiedReference,PackQualifiedReferenceWrite,GlossaryTerm,GlossaryContextTerm,
    VocabularyTerm,PackRegistration}.yaml
contracts/_shared/schemas/RequiringPack.yaml
contracts/packs/schemas/{pack-descriptor,graph-shard,org-fragment,glossary,mission-type}.schema.json
contracts/src/test/java/ai/speckitty/charter/contracts/identifiers/*  (parity test)
tests: PackQualifiedReferenceTest, PackSchemasTest, VocabularyEqualityTest, SubstepCandidate*, GuardImplementation*,
    KindRef*, Relationship*
docs: glossary/charter/README.md, guides/compose-packs.md, architecture/descriptors.md (+ generated contextive)
```

**Structure Decision**: no new module. The grammar lives in `:kernel`; the other modules read it.

## Complexity Tracking

None.

## Implementation Concern Map

### IC-01 — One statement of the pack-name grammar

- **Purpose**: state the qualifier once in the kernel, and make every Java site and pin test read it, with no change in
  behavior.
- **Relevant requirements**: FR-003, FR-007 (the qualifier mapping, against today's grammar).
- **Affected surfaces**:
  - `PackQualifiedReference`, `IdentifierGrammar`, `SubstepCandidate`;
  - `PackSchemasTest`, `VocabularyEqualityTest`;
  - the parity test (`KernelGrammar`, `IdentifierPatternParityTest`).
- **Sequencing/depends-on**: none.
- **Risks**:
  - `VocabularyEqualityTest` parses the qualifier prefix with a regex. It must read `QUALIFIER` instead (via
    `Pattern.quote`).
  - The parity extension must not loosen `identifierLike` for non-qualifier patterns.

### IC-02 — The pack-name part is a pack id

- **Purpose**: change the grammar to `slug(63)` in the kernel, in `GuardImplementation` and in every contract site.
- **Relevant requirements**: FR-001, FR-002, FR-004, FR-005, FR-006, FR-008, FR-009, NFR-001, NFR-002.
- **Affected surfaces**:
  - the kernel;
  - `GuardImplementation`;
  - 11 schema files;
  - the changelog entries;
  - 3 living docs;
  - the tests named above.
- **Sequencing/depends-on**: IC-01.
- **Risks**:
  - A service test fixture may carry an old-grammar name (the census found only `open_packs-2`). Run the `:packs` and
    `:missions` suites in full.
  - `PackRegistration.name` drops its `$ref: Slug.yaml`, which other fields keep.
  - The generated client (`client-java`) regenerates from the contract. Check `:client-java` compiles.
