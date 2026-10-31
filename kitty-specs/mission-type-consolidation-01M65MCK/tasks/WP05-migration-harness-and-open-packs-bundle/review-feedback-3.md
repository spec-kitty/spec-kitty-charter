# WP05 review 3 (reviewer-renata): approved

Scope: the commits after `2c9852d` (223c31a, b821fa9, 7b6d16b, 3c04788, 75d7e30 and two status commits). The branch is
at `1c1af94`. This round checks only that issues A to F of `review-feedback-2.md` are resolved, as
`review-response-2.md` says.

## Re-run

- `python3 -I verify_migration.py /home/user/spec-kitty-open-packs /home/user/op-migrate` reports 0 problems and exits 0.
  Its counts match `verifier-report.txt`, including the new ones: 13 template paths, 17 dropped titles, 4
  frontmatter-only descriptions and 1 uncarried action index.
- `python3 -I self_test.py /home/user/op-migrate 5df92a5d` plants 14 defects, detects 14 and exits 0. Afterwards
  op-migrate is clean at `12b0e55`, and its worktree list shows only `op-pin`.
- `./gradlew -PopenPacksDir=/home/user/spec-kitty-open-packs :missions:test :packs:test :packs:wholePackRoundTrip`
  passes. `:missions` ran 54 tests and `:packs` ran 2105, with 0 failures and 3 skipped.
- `:packs:packMigration -PmigrationSource=/home/user/spec-kitty-open-packs` wrote into a fresh empty directory.
  `diff -r` of its `packs/` against `/home/user/op-migrate/packs` differs in one place only:
  `packs/built-in.source.yaml:19`, the hand-seeded `tree_digest` value.

## Issues

**A: guidelines with a detailed description. Resolved.**
- `MissionStep` now guards guidelines with `GUIDELINES_RULES` (MissionStep.java:61-64, 93). A detailed description
  on the guidelines is a `ConstraintBreachException`.
- `MissionValuesTest.guidelinesCarryNoDetailedDescription` (MissionValuesTest.java:171-195) covers both sides: the
  guidelines are refused, and the same document is accepted as the prompt.
- The domain can no longer build a step that the writer turns into a file the reader refuses.

**B: role fallback and repeats in ADR-0044. Resolved.**
- ADR-0044:157-162 now names the two `retrospect` steps (documentation, research). It says software-dev's
  `retrospect` index belongs to no step and is not carried, and it states the one-profile rule and the
  runtime/`step.yaml` equality check.
- Both tools refuse a list of more than one profile:
  - the harness throws at MissionTierMigration.java:492-497;
  - the verifier adds a problem at verify_migration.py:195-196.
- `manifest_version`, `step_transition` and `prompt_template_dir` moved to a new "Runtime mechanics with no place"
  list (ADR-0044:208-209, `mission-type-consolidation.md:57-58`). The repeats now name only `loop_type` and
  `terminal_step`.
- Answer 5 of `review-response-1.md` is corrected and marked as such.

**C: gate evidence. Resolved.**
- `gate-evidence.md` now states that the whole-pack gate covers the built-in pack only, compares YAML by meaning and
  Markdown by model, and is not a byte check.
- `repin.py` is committed, and its four edits are listed.
- The byte-level test is staged in `/home/user/wt-repin` and lands with WP06:
  `MissionTypeCorpusTest.everyMissionFileWritesBackByteForByte` (MissionTypeCorpusTest.java:85-116). It does what the
  response says:
  - it copies each of built-in, cave-kitty and qa;
  - it reads the copy through the production `FilesystemPackStore`;
  - it deletes every `*.mission-type.yaml` and every `missions/*/steps/*.md`;
  - it writes each type back through `toEntity`, `toDso` and `store.write`;
  - it asserts the per-pack file counts (49, 9 and 8, so 66), the exact file set (`containsExactlyElementsOf`) and
    `hasSameBinaryContentAs` against the pinned source.
- `theCorpusCountsArePinned` pins the 28 prompts with a detailed description.
- A missing or dropped DSO would leave a file unwritten and fail the test, so the test does not rely on the read being
  violation-free.
- The draft `mission-type-consolidation.md:27-30` now attributes the byte claim to this test, not to the whole-pack
  gate.

**D: verifier coverage. Resolved.** I checked each of the seven points:
1. A `runtime block` KEY_TABLE entry exists, and `covered()` runs on it (verify_migration.py:50-51, 145). The
   self-test plants `parallel: false` and catches it.
2. The runtime profile and the `step.yaml` profile are compared when both are present, in both tools
   (verify_migration.py:235-236, MissionTierMigration.java:354-359).
3. Every `actions/*/index.yaml` is enumerated, and its keys are checked. An index with no step that is not in
   `KNOWN_UNCARRIED` is a problem (verify_migration.py:255-262).
4. The verifier checks that each `template_path` names a file, and asserts the count of 13 (verify_migration.py:309-314).
   The self-test removes a template and catches it.
5. The 17 dropped titles, the 4 frontmatter-only descriptions and the 1 uncarried index are computed independently and
   asserted in `expected`.
6. `step.yaml` is read from `sdir` in the verifier (:210). The harness had the same latent bug, and `withTemplate` now
   resolves the step's directory through `stepDirs` (MissionTierMigration.java:526-536). The harness still reproduces
   the migrated tree exactly (see Re-run).
7. The self-test plants 14 defect classes. The four new ones are a differing `mission.key`, a differing nested step,
   an extra runtime-block key and a missing template.

**E: negative tests for 9c00274. Resolved.**
1. `frontmatter.description` has `"pattern": "\\S"` in the contract schema and in its fixture copy.
2. `anyOtherPromptFrontmatterIsRefused` (MissionTypeBindingTest.java:282-304) has seven cases.
   - It reads through the store (`read()`), not through the domain. The blank row (`' '`) therefore fails without the
     new pattern, so it really tests the schema twin.
   - Each case asserts one violation on the type file, with the reason prefixed by the prompt path.
3. `MissionTypeSchemaTest.theDescriptionBoundCountsCodePoints` (MissionTypeSchemaTest.java:79-90) shows that 160
   astral code points pass the schema and 161 give `maxLength`. This matches the domain test.

**F: small points. Resolved.**
1. In `readTemplates`, a file claims its template id only after `readPayload` succeeds
   (FilesystemPackStore.java:407-416). An unreadable first copy no longer blocks the second. The javadoc says so.
2. The `PackFileSet` javadoc names `missions/<type>/templates/` (PackFileSet.java:99-100, 193-197).
3. `plan.md:69-70` and `:90` note that the refined ruling Q1 supersedes the frontmatter ban.

## New findings

No blocking finding.

- **N1 (nit, optional; WP06 or WP07).** ADR-0044:143, the `role` row of the field table, still says "the action
  index's first `agent_profiles` entry". The index may now hold only one profile, so "its single `agent_profiles`
  entry" would match the rule at :161. Nothing else depends on the wording.

## Disposition

Approved. Issues A to F are resolved. The migrated tree `12b0e553` and its patch stand. `MissionTypeCorpusTest` and
the re-pin land with WP06 as planned.
