---
work_package_id: WP10
title: Tests, floors and records
dependencies:
- WP13
requirement_refs:
- FR-012
- FR-013
- NFR-003
- NFR-005
- SC-005
- C-006
- C-004
- C-002
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts for this mission were generated on slice-5-554-pack-closure-rulesets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-5-554-pack-closure-rulesets unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-closure-rulesets-01M7FA90
base_commit: 3153b30078ab82ffd318b995695db361c9776181
created_at: '2026-11-17T20:08:25.504442+00:00'
subtasks:
- T054
- T055
- T056
- T057
- T058
- T063
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
- at: '2026-11-16T12:00:00Z'
  actor: planner-priti
  action: Replanned for the S1 to S3 rulings (squad-shaping)
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackCodesCoverageTest.java
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackCodesCoverageTest.java
- service/pack-validation/build.gradle.kts
- service/packs/build.gradle.kts
- docs/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check.md
- docs/adr/0062-nightly-mutation-floor.md
- docs/adr/0065-*.md
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP10 - Tests, floors and records

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else.

Then read, in `kitty-specs/pack-closure-rulesets-01M7FA90/`:

- `spec.md` (the Intent Summary's rulings table, S1 to S3 included);
- `plan.md` (Decisions, and the IC named below);
- `data-model.md` (placement, the one reader, the compositions);
- `research/squad-shaping.md` (the S1 to S3 rulings, their recorded readings and the target shape);
- `research/design-r3.md` (§1 to §7, read with §8, which lists what S1 to S3 and S6 superseded) and `research/squad-plan.md`;
- `research/squad-similar-surfaces.md` (file:line map, as of before slice F; re-check each reference).

Charter rules that bind every subtask:

- The charter's model is canonical. There is no legacy reader and no shim.
- Tests carry only the canonical shape, apart from error-path fixtures.

## Branch Strategy

- Planning base and final merge target: `slice-5-554-pack-closure-rulesets`. The mission branch is `kitty/mission-pack-closure-rulesets-01M7FA90`.
- The execution worktree is allocated per computed lane from `lanes.json`; this mission has one lane.
- Start with `spec-kitty agent action implement WP10 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.
- An edit to a file another work package owns is an **out-of-map edit**: one purpose, recorded in the review log.

## Objective

Close the slice:

- collapse what remains of the old tests;
- prove that every code is exercised;
- measure the mutation floors and re-set the documented margins (R3-b (i)), now that `pack-validation` holds #554's
  validation edge;
- amend the ADRs and the issue texts, so that the records say what the service now does.

Spec FR-012, FR-013, NFR-003 and NFR-005, SC-005; plan IC-09.

## Subtasks

### T054: The test collapse audit

- **Audit:** for each test class listed in `research/squad-similar-surfaces.md` §5, list where each of its behaviours
  is now tested, in a rule, composition or mapper test. This includes `FileSystemPackInspectorTest`,
  `WholePackStoreTest`, `DomainRepositoryServiceTest`, and the tests WP12 moved off the store and WP13 deleted.
- **Put the audit table in the PR body,** under "Test collapse". Add a test for any behaviour that has none.
- **Keep the tests in their subject's module** (ADR-0062): the core and storage rules in `:packs`, the validation edge
  in `:pack-validation`.

### T055: `PackCodesCoverageTest`

- Enumerate every `PackCodes` constant, every `StorageCodes` constant and every composition code; `PlacementCodes`'
  constants are enumerated beside their subject, in `:pack-validation:test` (ADR-0062). For each one, find at
  least one failing and one passing fixture, among the rule tests' fixtures and the planted packs.
- **Fail the test when a code has no fixture,** and name the code.

### T056: Mutation floors (R3-b (i))

- **Run** narrowed PIT on `packs.domain.rules`, `packs.infra.storage.rules`, `PackRefusals`, `infra.storage` and
  `packvalidation` (`pit-targets.init.gradle`), then the full nightly task locally if time allows. Use
  `--max-workers=1`, one run at a time.
- **Where `packs.infra.storage.rules` is counted (squad-shaping Q6, the default taken):** it joins `:packs`'
  domain+application PIT group. It is pure, so it is held to the same floor; set the margin from the measurement.
- **Re-set the documented margins from the measurement:**
  - `pack-validation/build.gradle.kts`, whose comment says "margin 0 until #554"; its module comment now says the
    module is the validation edge (`PackCheck`, `PackVerdicts`, placement, the round trip);
  - `packs/build.gradle.kts`;
  - ADR-0062's row 105, with an amendment dated today.
- **No floor is lowered without an ADR-0062 amendment.**
- **Record the scores in the PR.**

### T057: The ADRs

- **ADR-0075:** add the amendment of 2026-11-16 from `research/design-r3.md` §6, folded with §7 and **§8** (R3 as
  superseded by S1 to S3 and S6):
  - the consistency rules live in the packs domain. The core's rules are plain rulesets with no judging port; S6 adds
    one reading port (`PackReading`) whose readout carries the storage edge's verdict;
  - **`PackReading` replaces `PackInspector` (S6):** the domain port `PackReading` (`PackReadout read(PackRoot)`)
    replaces the `PackInspector` port, and its `Scope`, that the 2026-11-13 amendment names; no `TreeReader` port is
    built, and **no ArchUnit rule is widened** (the onion rules and `PACK_VALIDATION_IS_FRAMEWORK_FREE` stand; only
    `STORAGE_RULES_ARE_FRAMEWORK_FREE` and `PACK_RULES_READ_NO_FILE` are added, and rules A to C re-pointed);
  - `pack-validation` is the **validation edge**: it reaches the core and storage rules through `PackReading`'s
    readout for validate mode and pack-check, and adds the round trip, placement and, from #478, the pack set and the
    CLI;
  - one reader in the storage edge; the storage rules and their tokens in `packs.infra.storage.rules`, named by nothing
    outside `packs.infra.storage` and `deployable` (S1, S3, S6);
  - the compositions as data-model.md types them: `registration() = holding().must(content().on(TreeRead::contents))`,
    `StorageRulesets.verdict` joined with `Rationale.and`, and `PackCheck.validate` =
    `read.rationale().and(placement().check(...))`;
  - S2's placement of the validate-only checks;
  - decision 4: the converters go, and R2 unifies the codes;
  - decision 7's wording becomes "needs every pack's contents, never the closure walk".
  - Update the title or description only if the operator agreed to it in R3; otherwise add a note.
- **ADR-0065:** annotate Q1 and open case 6. An unmodelled file is not held, extending D1, and validate mode judges
  nothing in it.
- **Docs checks:** run `check_docs.py`, markdownlint-cli2 and cspell.

### T058: The final golden check, and the drafts

- **The golden snapshot:** the diff list in the PR body groups each row under an entry of the diff list of NFR-002.
  There are no unexplained rows.
- **The open-packs draft** (`research/open-packs-drafts/`) lists every expected-code change, from Q1, open case 6, R2
  and S2. It is a planning file: write it in the planning pass and hand it to the orchestrator.

### T063: The issue texts

- **#554:** amend its text to the S1 to S3 and S6 shape (the validation edge, the one reader, the storage rules), and close it
  with evidence at the landing; tick epic #549.
- **#478:** fold into its text what it inherits, with no new issue: the CLI entry over `PackCheck`, the pack-set check
  (`PackCheck` per pack plus `PackRulesets.closure()`), and the `pack-check` module merge (squad Pr8). The orchestrator
  posts both texts.

### Carried from WP04's review (NIT)

- Pin `companion` for a companion's `MarkdownRefusal` and for a companion that is not UTF-8 (the `coded()` branch of
  `FileSystemPackInspector`), one small test each, as part of the every-code coverage.

### Carried from WP13

- **WP13 review m1:** give `STORAGE_RULES_ARE_FRAMEWORK_FREE`'s `ANOTHER_STORAGE_CLASS` branch and
  `PACK_RULES_READ_NO_FILE`'s `java.io` branch a bite: a fixture that names a `packs.infra.storage` class (for example
  `PackFileSet`) and one that uses `java.io.InputStream`, both asserted in `assertBites`.
- **m2:** narrow `PACK_CODES_HAVE_ONE_OWNER`'s exclusion from the package `packvalidation.roundtrip` to
  `WholePackRoundTripRule` by name.
- **m3:** add the `expected.json` oracle method to `PromptSlotRulesTest` for its three codes, as `StepRulesTest` does.
- **m4:** a parameterised test over the planted and scenario packs: every walked path is in `WholePackCensus.of` with a
  kind, and `companionsOf` agrees with the store's companion claims.
- **m5:** in the R2 note, strike the stale "Not done here, for WP13 (NIT-B)" bullet, and name all four storage-edge
  mappings onto the aggregate record (`StoreRead.heldBack`, the `FragmentRules` lambda in `StoreRead.assembly`,
  `recordPromptSlotFaults`, `FilesystemPackStore.endpointViolations`), noting `endpointViolations` repeats the order
  `GraphRules` uses.
- **N1:** WP13's T051 check is the word-bounded `git grep -w`. **N2:** say in `VocabularyEqualityTest`'s javadoc that
  only its warning half asserts something new. **N3:** build the storage rules' reasons through `Findings.at`.
- Stale test names in fixture metadata: `planted/c13-nullable-fields/expected.json` and `core-verdicts.json` name
  `PackPolicyVocabularyTest`; `docs/plans/charter-example-packs.md` names `PromptSlotLoadChecksTest`. Point them at the
  tests that now hold those checks.

### Carried from WP09's review

- Restore the assertion lost with the deleted `LOAD_PATH` test: an unparseable root shard is one read fault. In
  `FileSystemPackInspectorTest.aFileThatIsNotYamlOrBreaksItsSchemaIsUnreadableAndNeverThrows` (`:157`), assert the
  read faults' files contain `a.graph.yaml` and `b.graph.yaml` only once each.
- Rejoin the comment broken by reflow at `CharterWiringTest:191-193`.
- The R2 note keeps S2's term "prechecks" (lines ~290, ~344): add it to the cspell dictionary and restore the word.
- DIV-028's core evidence cites `_validate_pack_descriptor` by name only: add its line at the pinned core commit
  `9554e7b2`.

## Definition of Done

- The coverage test is green.
- The audit table is complete.
- The floors are measured, and the margins are re-set honestly.
- The ADRs are amended, and the issue texts are drafted.
- The docs checks pass.
- `verifyAll` is green.

## Reviewer guidance

- Was every collapsed behaviour accounted for?
- Are the margins measured, not guessed?
- Does ADR-0075 say where each layer's rules live after S1 to S3 and S6?
