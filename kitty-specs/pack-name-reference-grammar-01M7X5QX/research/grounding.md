# Grounding: pack-name part of a reference (#582)

Phase 1 research on `main` 17d5ca2, 2026-11-20.

## Sites

| Site | Grammar today | Disposition |
|------|---------------|-------------|
| kernel `PackQualifiedReference.PACK_NAME_GRAMMAR`, `isPackName`, `parse`, `typed` | `^[a-z0-9][a-z0-9_-]*$`, no cap | change (FR-001) |
| kernel `KindRef.inPack`, `Relationship.requirePack` | via `isPackName` | follow |
| missions `SubstepCandidate.GRAMMAR`, `PACK_RULES` | strips anchors off `PACK_NAME_GRAMMAR` | tidy, then follow |
| packs `GuardImplementation.SPELLING_SHAPE` | own copy: `IdentifierGrammar.SLUG`, cap 64 | change to `isPackName`, cap 63 |
| missions `GuardReference` (`<pack>::guard:<id>`) | own copy: `IdentifierGrammar.SLUG`, cap 64 | change to `isPackName`, cap 63 (found by the plan squad) |
| activations `PackRef` | `PackIdentifier` since #583 (8a44dfc) | ALREADY-FIXED |
| pack-validation, pack-check | no own parsing | none |
| charter `PackQualifiedReference.yaml`, `PackQualifiedReferenceWrite.yaml` | qualifier `[a-z0-9][a-z0-9_-]*` | change |
| charter `GlossaryTerm`, `GlossaryContextTerm`, `VocabularyTerm` `seeAlso` | same | change (VocabularyTerm not named in the issue) |
| charter `PackRegistration.name` (`$ref: Slug.yaml`) | `^[a-z0-9][a-z0-9_-]*$`, 64 | change to the pack-id form |
| _shared `RequiringPack.packId`, `via[]` | same, 64 | change to the pack-id form |
| packs `pack-descriptor` `name` | same, 64 | change to the pack-id form |
| packs `graph-shard`, `org-fragment` edge `source`/`target`/term `if` (6) | qualifier | change |
| packs `glossary` `see_also` | qualifier | change |
| packs `mission-type` `candidates` | qualifier | change |
| packs `guard` `implemented_by` | qualifier inside an alternation, already the slug | parity only |
| packs `lifecycle` guard `ref` | `(slug::guard:)?`, already the slug, `maxLength` 136 | `maxLength` 135 |

`IdentifierPatternParityTest` (#585/#586) skips a pattern holding `_` or `::`, so it checked none of these.

## Fold and tidy-first

| Candidate | Class | Why |
|-----------|-------|-----|
| One kernel written form; `SubstepCandidate` and the pin tests read it; parity sees qualifiers | TIDY-FIRST | makes the change one edit and tests it |
| VocabularyTerm `seeAlso`, the pack-schema qualifiers | FOLD | the same defect at more sites |
| `RequiringPack`, `PackRegistration.name` | FOLD | pack ids or names on the old grammar |
| `GuardImplementation` and `GuardReference` cap 64 → 63 | FOLD | second and third copies of the grammar |
| missions `GuardReference` (`<pack>::guard:<id>`) | own copy: `IdentifierGrammar.SLUG`, cap 64 | change to `isPackName`, cap 63 (found by the plan squad) |
| activations `PackRef` | ALREADY-FIXED | #583 |
| #556 closure walk | RELATED-SEPARATE | no grammar change |
| profile-id grammar (`RolePins`, `fallback_profile`, `role_pins`, `MissionSubstepDelegation.kind`) | RELATED-SEPARATE | #552 ruled it out of decision 2 |
| `Slug.yaml` for mission types and actions | RELATED-SEPARATE | not a pack id |

## Breakage census

- **spec-kitty-open-packs** `main` 3bedeef:
  - 24 pack names, 0 off `slug(63)`;
  - 607 qualified references in `source`, `target`, `see_also`, `candidates` and `implemented_by`, 0 off it;
  - qualifiers in use: foundation, testing, writing, built-in, architecture, planning-analysis, std, refactoring,
    javascript, consumer, python, nowhere, java.
- **This repository**:
  - example packs: 0 off it;
  - test fixtures: 1, `PackQualifiedReferenceTest`'s `open_packs-2::`, which becomes a refusal case.
