---
work_package_id: WP01
title: One statement of the pack-name grammar (tidy-first)
dependencies: []
requirement_refs:
- FR-003
- SC-003
planning_base_branch: main
merge_target_branch: main
branch_strategy: Planning artifacts for this mission were generated on main. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into main unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T005
phase: Phase 1 - Tidy first
history:
- at: '2026-11-20T19:15:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/
create_intent: []
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/IdentifierGrammar.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackQualifiedReference.java
- service/missions/src/main/java/ai/speckitty/charter/missions/domain/SubstepCandidate.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackSchemasTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/VocabularyEqualityTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP01 - One statement of the pack-name grammar (tidy-first)

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`.kittify/charter/charter.md`, the mission `spec.md` and `plan.md` (decisions D1 to D4), and
`research/grounding.md`.

## Objective

Make the pack-name part of a reference stated **once**, in the kernel, as a written form that both Java and the contract
pin tests read. **No behavior changes in this WP.** The
grammar stays `[a-z0-9][a-z0-9_-]*`, so WP02's change becomes a one-line kernel edit plus the schemas, with tests
already in place to catch drift.

## Branch Strategy

- Planning branch: `main`. Merge target: `main`. The Mission topic branch is
  `kitty/mission-pack-name-reference-grammar-01M7X5QX`.
- The workspace comes from `lanes.json` (single_branch topology). Implement with
  `spec-kitty agent action implement WP01 --agent claude --mission pack-name-reference-grammar-01M7X5QX`.

## Subtasks

### T001 — Kernel written form

- In `IdentifierGrammar`, add `public static final String SLUG_AS_WRITTEN = "[a-z0-9]+(-[a-z0-9]+)*";`.
  - Its javadoc: the `SLUG` as the contracts write it, in ECMA-262, without possessive repeats.
  - This WP adds it but does not use it yet; WP02 builds `QUALIFIER` from it.
  - **Alternative:** add it in WP02 instead, if an unused constant trips a gate (Error Prone does not flag an unused
    public constant).
- In `PackQualifiedReference`, add `public static final String QUALIFIER`. It is the optional qualifier as the contracts
  write it at the start of a reference pattern: `"(" + <unanchored pack-name grammar> + "::)?"`.
  - Here it is `([a-z0-9][a-z0-9_-]*::)?`, built from one private unanchored constant that `PACK_NAME_GRAMMAR` also uses
    (`"^" + PACK_NAME + "$"`).
  - Document it as the one written form: `SubstepCandidate` and the schema pin tests read it.
- Update the class javadoc of `IdentifierGrammar`, which points at `PACK_NAME_GRAMMAR`, only if the wording needs it.
  WP02 rewrites it.

### T002 — SubstepCandidate reads the written form

- Delete `packNameGrammar()`, which strips the anchors.
- Build `GRAMMAR` as `"^" + PackQualifiedReference.QUALIFIER + ID_GRAMMAR + "$"`. The resulting string must be identical
  to today's: `SubstepTest` line ~169 pins it to the mission-type schema.
- Keep `PACK_RULES` on `PackQualifiedReference::isPackName`.

### T003 — Pin tests read the written form

- `PackSchemasTest`:
  - `aReferencePatternQualifiesWithThePackNameGrammar` and
    `theContractQualifiedReferenceSchemaSpellsTheQualifierWithThePackNameGrammar` use `PackQualifiedReference.QUALIFIER`
    instead of rebuilding it from `PACK_NAME_GRAMMAR`.
  - `theDescriptorSchemaConstrainsNameToThePackQualifiedReferenceGrammar` stays as it is in this WP (WP02 changes it).
- `VocabularyEqualityTest`: `ALTERNATION` and `TARGET_ONLY_PREFIX` build their qualifier prefix from
  `Pattern.quote("^" + QUALIFIER)` (or a literal-prefix strip) instead of a hand-escaped copy of the grammar.

### T004 — moved to WP02

The qualifier parity check lands in WP02 (plan D4): the guard `implemented_by` and lifecycle `ref` sites already use
the slug, so a check against today's grammar would need a temporary allowlist.

### T005 — Blast radius

- Run `./gradlew :kernel:test :missions:test :packs:test --tests '*PackSchemasTest' --tests '*VocabularyEqualityTest'`
  and `:contracts:test --tests '*IdentifierPatternParityTest'` (unchanged, still green).
- Then run `:kernel:check :missions:check`, and the style gates (Spotless, Checkstyle, Error Prone).
- **Planted break** (do not commit it): change one character of `QUALIFIER`. `SubstepTest.theGrammarIsTheSchemas` and
  the `PackSchemasTest` pins must fail. Restore it.
- No PIT.

## Definition of Done

- `QUALIFIER` exists. `SubstepCandidate`, `PackSchemasTest` and `VocabularyEqualityTest` read it. No `substring(1, …)`
  anchor stripping remains (check with `grep -rn "PACK_NAME_GRAMMAR" service contracts`).
- The pin tests fail on a planted change to `QUALIFIER`.
- Every suite named above passes, and no behavior changes: no production refusal or acceptance differs.

## Risks

- The `SubstepCandidate.GRAMMAR` string must not change in WP01.
- An unused-constant or naming check in Checkstyle could flag `SLUG_AS_WRITTEN`. If it does, move it to WP02.

## Reviewer guidance

- Confirm that no behavior changes: diff the refusal messages and patterns.
- Confirm that the planted break was run, and that its output is in the WP's commit or review notes.
