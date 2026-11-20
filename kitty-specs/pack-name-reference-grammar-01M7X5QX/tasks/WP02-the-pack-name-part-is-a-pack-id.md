---
work_package_id: WP02
title: The pack-name part of a reference is a pack id
dependencies:
- WP01
requirement_refs:
- C-001
- C-002
- FR-001
- FR-002
- FR-004
- FR-005
- FR-006
- FR-007
- FR-008
- FR-009
- FR-010
- FR-011
- NFR-001
- NFR-002
- NFR-003
- SC-001
- SC-002
- SC-004
planning_base_branch: main
merge_target_branch: main
branch_strategy: Planning artifacts for this mission were generated on main. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into main unless the human explicitly redirects the landing branch.
subtasks:
- T004
- T006
- T007
- T008
- T009
- T010
- T011
- T012
- T013
- T014
phase: Phase 2 - The change
history:
- at: '2026-11-20T19:15:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/schemas/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/QualifierLoadTest.java
- contracts/charter/changes/2026-11-20-pack-name-reference-grammar.md
- docs/changelog/2026-11-20-pack-name-reference-grammar.md
execution_mode: code_change
owned_files:
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/PackQualifiedReferenceTest.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/KindRef.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Relationship.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GuardImplementation.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/GuardTest.java
- service/missions/src/main/java/ai/speckitty/charter/missions/domain/GuardReference.java
- service/missions/src/test/java/ai/speckitty/charter/missions/domain/IdentifierBoundsTest.java
- contracts/src/test/java/ai/speckitty/charter/contracts/identifiers/**
- contracts/packs/schemas/guard.schema.json
- contracts/packs/schemas/lifecycle.schema.json
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/QualifierLoadTest.java
- contracts/charter/schemas/PackQualifiedReference.yaml
- contracts/charter/schemas/PackQualifiedReferenceWrite.yaml
- contracts/charter/schemas/GlossaryTerm.yaml
- contracts/charter/schemas/GlossaryContextTerm.yaml
- contracts/charter/schemas/VocabularyTerm.yaml
- contracts/charter/schemas/PackRegistration.yaml
- contracts/_shared/schemas/RequiringPack.yaml
- contracts/packs/schemas/pack-descriptor.schema.json
- contracts/packs/schemas/graph-shard.schema.json
- contracts/packs/schemas/org-fragment.schema.json
- contracts/packs/schemas/glossary.schema.json
- contracts/packs/schemas/mission-type.schema.json
- contracts/packs/CHANGELOG.md
- contracts/charter/changes/2026-11-20-pack-name-reference-grammar.md
- docs/changelog/2026-11-20-pack-name-reference-grammar.md
- docs/glossary/charter/README.md
- docs/guides/compose-packs.md
- docs/architecture/descriptors.md
- site/.contextive/definitions.yaml
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP02 - The pack-name part of a reference is a pack id

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`.kittify/charter/charter.md`, `spec.md`, `plan.md` (D1 to D7) and WP01's result.

## Objective

The pack-name part of every pack-qualified reference is a pack id: `slug(63)`, through `PackIdentifier.isPackId`. The
grammar is replaced outright, with no legacy reader (charter, 2026-11-10). The contracts follow under `### Breaking`.

## Out-of-map edits (recorded)

WP01 owns these files, and this WP must change them:

- `PackQualifiedReference.java` and `IdentifierGrammar.java`: the grammar change itself;
- `SubstepCandidate.java`: only if its message names the old grammar;
- `PackSchemasTest.java`: the descriptor pin.

WP02 depends on WP01 and runs after it on the same branch, so the edits do not collide.

## Branch Strategy

- Planning branch: `main`. Merge target: `main`. Single-branch topology, on the Mission topic branch.
- Implement with `spec-kitty agent action implement WP02 --agent claude --mission pack-name-reference-grammar-01M7X5QX`.

## Subtasks

### T004 — Parity test discovers qualifier sites (moved from WP01, plan D4)

- In `contracts/src/test/.../identifiers/`, add a qualifier check:
  - A **qualifier site** is any `IdentifierSite` whose pattern contains `::`.
  - Every `(<form>::` group in the pattern is a qualifier, wherever it sits. There are 15 sites:
    - 13 with the group at the start;
    - guard `implemented_by`, with the group inside an alternation;
    - lifecycle `ref`, as `(<form>::guard:)?`.
  - Every `<form>` must equal `IdentifierGrammar.SLUG_AS_WRITTEN`. A `::` site with no such group fails, naming its
    `location` and pattern.
  - **Verdict:** `^<form>$`, compiled as an `EcmaPattern`, agrees with `PackQualifiedReference.isPackName` over the
    corpus and the cap rows for every row of at most 63 characters. Over 63 is the documented exception (T010).
  - Assert the site count per file. The sites are discovered from the files, not from a hand-kept list.
- **Planted break:** put `_` into one qualifier. The test must fail, naming the site. Restore it.

### T006 — Red test first (C-002)

- In `PackQualifiedReferenceTest`, replace `aPackNameMayHoldDigitsUnderscoresAndHyphens` with tests that:
  - refuse `a_b::directive:D1` at `parse`, with a message containing `PackIdentifier.GRAMMAR_DETAIL`;
  - refuse, as a parameterized test, `a--b::…`, `a-::…`, `-a::…`, `open_packs-2::…` and a 64-character pack name;
  - accept `a-b::…`, `1a::…` and a 63-character pack name on the same fixture (the positive control).
- Add `isPackName` rows: `a_b` false, `a-b` true, 63 `p`s true, 64 `p`s false.
- Update `aPackNameOutsideTheGrammarIsRefused` to expect the new detail.
- **Commit the test alone, and record the red run**: the `:kernel:test --tests '*PackQualifiedReferenceTest'` failure
  output, in the commit body or the review notes.

### T007 — Kernel grammar change

- `PackQualifiedReference`:
  - `isPackName(name)` returns `PackIdentifier.isPackId(name)`.
  - Remove `PACK_NAME_GRAMMAR` and the compiled `PACK_NAME` pattern.
  - `QUALIFIER` becomes `"(" + IdentifierGrammar.SLUG_AS_WRITTEN + "::)?"`.
  - The constructor refuses with `"A pack-qualified reference names its pack by a pack id. " +
    PackIdentifier.GRAMMAR_DETAIL + " It names: " + name`. To keep the refusal bounded, echo at most the first 64
    characters followed by `…`, or keep the echo but note it.
  - Update the javadoc.
- `IdentifierGrammar`: rewrite the class javadoc paragraph on the pack name. It now *is* a pack id, so it no longer
  "follows a grammar of its own".
- `KindRef.inPack` and `Relationship.requirePack`: update the messages to name the pack-id detail. The predicate stays
  `isPackName`.
- `SubstepCandidate`: its `PACK_RULES` message names `PackIdentifier.GRAMMAR_DETAIL` instead of `PACK_NAME_GRAMMAR`.
  `GRAMMAR` follows `QUALIFIER` on its own.
- Run the red test: it is now green.

### T008 — GuardImplementation follows

- `MAX_PACK_LENGTH` becomes `PackIdentifier.MAX_LENGTH`, 63.
- `SPELLING_SHAPE` may keep `IdentifierGrammar.SLUG` for the qualifier group. After the match, the qualifier is checked
  with `PackQualifiedReference.isPackName`, so one predicate decides.
- Update the javadoc ("over 64 characters" becomes 63).
- `GuardTest`:
  - `a-b::asset:x` is accepted;
  - `a_b::asset:x` is refused;
  - a qualifier of 63 characters is accepted;
  - a qualifier of 64 characters is refused.

### T013 — GuardReference follows (plan D5)

- The pack part's cap becomes `PackIdentifier.MAX_LENGTH` (63), checked before the match. The match stays possessive;
  the pack part is then checked with `PackQualifiedReference.isPackName`.
- `MAX_SPELLING_LENGTH` becomes 63 + 8 + 64 = 135.
- In `IdentifierBoundsTest`, `aGuardReferenceIsCappedAtItsPartsAndItsTotal` becomes:
  - a 63-character pack is accepted;
  - a 64-character pack is refused;
  - the total is capped at 135;
  - `a_b::guard:x` and `a--b::guard:x` are refused.
- `lifecycle.schema.json` guard `ref`: `maxLength` goes from 136 to 135 (breaking line in the packs CHANGELOG).

### T014 — Load path refuses a 64-character qualifier (plan D8, FR-011)

- Add `QualifierLoadTest` in `:packs`, or the nearest existing load test.
- A pack whose graph shard has an edge `target: <64 p's>::directive:D1` passes the schema, and is refused at load. The
  refusal names the pack-id detail and is not reshaped silently.
- Positive control: 63 `p`s is not refused for its qualifier. It may still fail as an unresolved pack; assert the code
  that differs.
- If `FileSystemPackInspector`'s step-enhancement fallback (`target.replace("::", ":")`) hides the refusal, fix it at the
  root.

### T009 — Contract and pack schemas

- Replace the qualifier `([a-z0-9][a-z0-9_-]*::)?` with `([a-z0-9]+(-[a-z0-9]+)*::)?` at all 13 sites:
  - charter `PackQualifiedReference.yaml` and `PackQualifiedReferenceWrite.yaml`, and the `seeAlso` items of
    `GlossaryTerm.yaml`, `GlossaryContextTerm.yaml` and `VocabularyTerm.yaml`;
  - pack schemas: graph-shard (source, target, the term `if`), org-fragment (the same three), glossary `see_also`
    (keep its newline guard and its lookaheads on the term parts) and mission-type `candidates`.
- Move these to the pack-id form, `pattern: '^[a-z0-9]+(-[a-z0-9]+)*$'` with `maxLength: 63` (and `minLength: 1` where
  the PackId parameter has it):
  - the descriptor `name` (`pack-descriptor.schema.json`): also update its description and the top-level description
    paragraph on `name`;
  - `PackRegistration.name`: drop `$ref: Slug.yaml` and inline the pattern, keeping its description and `x-derived`;
  - `RequiringPack.packId` and `via.items`.
- In each of the five charter qualifier sites' `description`, state: "The pack name is a pack id: a kebab slug of at
  most 63 characters; the service refuses a longer one." (FR-010)
- Do not touch `Slug.yaml` itself, `fallback_profile`, `role_pins`, `MissionSubstepDelegation.kind`, or `Artifact`/
  `ArtifactSummary` `kind` (C-004).
- `PackSchemasTest.theDescriptorSchemaConstrainsNameToThePackQualifiedReferenceGrammar`: pin the descriptor `name`
  pattern to `"^" + IdentifierGrammar.SLUG_AS_WRITTEN + "$"` and `maxLength` to `PackIdentifier.MAX_LENGTH`. Rename the
  test to match.
- No lookahead may be added to the charter contract (NFR-002).

### T010 — Parity: the documented exception and the pack-name sites

- The qualifier check from WP01 now gets a documented exception for rows over 63 characters: the schema accepts them and
  the kernel refuses them. Add a dedicated test, as `aSlotPartOverSixtyFourPasses…` does: a 64-character qualifier
  passes every qualifier site's pattern, and `PackQualifiedReference.parse` refuses it.
- The descriptor `name`, `PackRegistration.name` (`charter/schemas/PackRegistration.yaml`) and `RequiringPack` are now
  identifier-like with `maxLength` 63, so the
  existing rule maps them to `PACK_ID`. Assert that `theDiscoveryReachesEveryGrammarAndTheNamedSchemas` now includes
  `packs/schemas/pack-descriptor.schema.json` and `_shared/schemas/RequiringPack.yaml`.
- Run the planted break: put `_` back into one qualifier, see the test fail naming the site, and restore it.

### T011 — Changelog entries and living docs

- `contracts/charter/changes/2026-11-20-pack-name-reference-grammar.md`: one `### Breaking` block (#582).
  - The pack-name part of `PackQualifiedReference`, `PackQualifiedReferenceWrite` and the `seeAlso` of `GlossaryTerm`,
    `GlossaryContextTerm` and `VocabularyTerm` is a pack id (`slug(63)`, ADR-0075 decision 2). `_`, a doubled, leading
    or trailing hyphen, and more than 63 characters are refused.
  - A `putPackRelationship` body naming such a pack is refused `422 validation_failed`.
  - `PackRegistration.name` and `RequiringPack.packId`/`via` take the pack-id pattern with `maxLength` 63.
  - **Core's seam:** regenerate the client's patterns. No field changes shape.
  - The documented exception: the schema does not cap the qualifier inside a reference; the service refuses one over
    63.
- `contracts/packs/CHANGELOG.md`, under `## 0.1.0-SNAPSHOT`: add a `### Breaking` subsection.
  - The descriptor `name` is a pack id, `slug(63)`, so `PackSourceStagers`' requirement that it equal the pack id can be
    met.
  - The `<pack>::` qualifier of graph-shard, org-fragment, glossary `see_also` and mission-type `candidates` follows.
  - Census: spec-kitty-open-packs `main` 3bedeef has 0 of 24 names and 0 of 607 references to change.
- `docs/changelog/2026-11-20-pack-name-reference-grammar.md`: a dated entry. Copy the frontmatter shape of
  `2026-11-19-tensions.md`. Say that it supersedes the 64-character qualifier cap of
  `2026-11-09-pack-id-length-caps.md`; that entry stays unedited.
- Note in the review notes: `-PcontractBaseline=none` skips the oasdiff comparison, so CI is the first compatibility
  check.
- Living docs that state the old grammar for the pack `name`:
  - `docs/glossary/charter/README.md` (~l.626);
  - `docs/guides/compose-packs.md` (~l.51);
  - `docs/architecture/descriptors.md` (~l.81).
- `site/.contextive/definitions.yaml` (~l.253): check whether it is generated from the glossary. If so, regenerate it;
  otherwise edit it.
- ADRs are historical: do not edit them.

### T012 — Fixtures canonical and gates

- `grep -rnE "[a-z0-9]*_[a-z0-9_-]*::" service contracts packs --include=*.java --include=*.yaml --include=*.json` must
  find no accepted fixture. Old shapes may appear only in refusal tests.
- Run:
  - `./gradlew :kernel:check :missions:check :packs:test :pack-validation:test :contracts:check -PcontractBaseline=none`;
  - `:client-java:compileJava`, if the module exists;
  - `:pack-validation:wholePackRoundTrip`.
- Run the docs checks: `python3 scripts/docs/check_docs.py` (or the repo's runner), markdownlint-cli2 on the changed
  `.md` files, and cspell.
- No PIT. Do not run the full heavy `verifyAll` locally; CI runs it.

## Definition of Done

- FR-001 to FR-009 are met. The red test was committed first and seen red.
- No `PACK_NAME_GRAMMAR` remains. The kernel, `GuardImplementation` and every contract site agree, and the parity test
  proves it.
- `### Breaking` entries are written for both contracts, plus the docs changelog entry.
- The suites named above and the style gates pass.

## Risks

- `PackRegistrationService` and the stagers may have tests with names off the grammar; the census found none, but run
  `:packs:test` in full.
- The glossary `see_also` pattern has lookaheads that must stay intact. Edit only the qualifier group.

## Reviewer guidance

- Check every site in `research/grounding.md` against the diff.
- Check that the red test is its own commit, and that the red output is recorded.
- Check that no lookahead was added to the charter contract.
- Check that the `### Breaking` text says what core's seam changes.
