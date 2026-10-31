# WP05 review 2 (reviewer-renata): changes requested

Scope: 9c00274, d6e5d13, d123c6d and 81289f6, on top of round 1. The branch is at e5c1d32.

## Verified good

**Verifier and self-test**
- `python3 -I verify_migration.py /home/user/spec-kitty-open-packs /home/user/op-migrate` reports 0 problems.
- The counts match `verifier-report.txt`: 6 types, 40 sequenced and 6 on-demand steps, 43 prompts, 17 guidelines,
  66 added files (60 of them companions) and 153 retired files.
- `self_test.py /home/user/op-migrate 5df92a5d` detects 10 of 10 and exits 0. Its output matches
  `verifier-self-test.txt`. It removes its worktrees: `git worktree list` on op-migrate shows only the existing
  `op-pin`, and op-migrate stays clean at 12b0e55.

**Patch**
- The committed patch is byte-identical to `git -C /home/user/op-migrate format-patch --stdout 5df92a5..12b0e55`,
  headers included (`cmp`).

**Open-packs CI on op-migrate**
- `check_vocabulary.py` scans 595 files with 0 findings.
- `gen_pack_reference.py --check` finds no drift.

**Load-then-write**
- I ran `:packs:wholePackRoundTrip -PopenPacksDir=/home/user/op-migrate -PopenPacksRef=12b0e553…` without the
  re-pin script. 6 of 7 tests pass. The one red test names only the two `expected-red.yaml` entries that the re-pin
  removes (`other:missions` and `mission-type`).
- I also ran a scratch JUnit test, not committed. It reads every mission type of built-in, cave-kitty and qa from
  op-migrate, maps it `toEntity` and `toDso`, and writes it through the production `FilesystemPackStore.write`. All 66
  files are byte-equal to their sources: 6 type files and 60 companions, including the 28 prompts with frontmatter.

**Numbers**: each one recomputed from the pinned and migrated data
- 17 display names differ from the runtime title. Round 1's count of 16 was wrong; software-dev `discovery` is the
  17th.
- 4 step descriptions come only from frontmatter: software-dev `charter`, `tasks-finalize`, `tasks-outline` and
  `tasks-packages`.
- 44 step descriptions, 19 to 119 characters. The longest is documentation `discover`.
- 28 prompt frontmatter descriptions, 29 to 111 characters.
- 153 retired files and 66 added files.

**Issue 5 pushback**
- The claim about the indexes is true. The documentation, research and software-dev `retrospect` action indexes
  each name `retrospective-facilitator`, and no step there names a profile. Round 1's "no pinned index uses it" was
  wrong. See issue B for how the rule is stated.

**Refined Q1 (9c00274)**
- The schema and the domain twin agree on 160, counted in code points.
  - networknt 3.0.8 `MaxLengthValidator` calls `String.codePointCount` (checked in the bytecode).
  - `MissionStep` uses `codePointCount` too.
  - The schema test covers 161 characters. The domain test covers 160 astral code points (accepted) and 161
    characters (refused).
- Guidelines frontmatter is refused. A prompt frontmatter key other than `description` is refused, and so are a
  non-string, empty, null or missing description and an empty frontmatter block. I checked each case with a scratch
  test: the structure schema refuses them before the binding runs.
- The operator's three points are implemented:
  - the step description in the mission-type file is the description;
  - prompt frontmatter stays as the prompt's detailed description;
  - the bound is a sensible default.

**Duplicate templates (d6e5d13)**
- The refusal is deterministic. The listing is sorted by `String`, and `missions/` sorts before `templates/`, so the
  mission type's copy is always kept and the top-level file is always the violation.
- The test asserts the store violation, the pack violation and the one place recorded.

**Round-1 issues 2 and 3**
- Issue 2 (dropped text) is resolved. `step-descriptions.md` lists all 17 dropped titles, and
  `mission-type-consolidation.md` lists the dropped field classes, the dropped comments and the two qa rationales.
- Issue 3 (open-packs CI) is resolved. The patch drops the stale vocabulary row and regenerates `docs/packs/`, and
  `mission-docs.md` lists the remaining stale references.

**Quality gates**
- `:missions:test` passes 53 tests and `:packs:test` passes 2090, with 3 skipped.
- `:packs:wholePackRoundTrip` passes 7 tests on the pin, and `:deployable:test` passes 324.
- Checkstyle (main, test and migration) and Spotless pass.
- No new suppression (`SuppressWarnings`, `NOPMD`, `noqa`, `@Disabled`) appears in `7fc7f27..e5c1d32`.
- Every changed method has a cyclomatic complexity of 6 or less, well under 15. The largest is
  `repeatsOfTheRuntime`.

## Issue A (medium): the domain lets guidelines carry a detailed description, and the writer then writes a file the reader refuses

**Where.** `MissionStep.java:89` only null-checks `guidelines`. `MissionTypeBinding.markdownOf(StepDocument)`
(MissionTypeBinding.java:351) writes `description` as frontmatter for any `StepDocument`.

**What goes wrong.** Take a `MissionStep` whose guidelines are
`new StepDocument(title, Optional.of("x"), preamble, sections)`. The domain accepts it, and `toDso` and `write`
produce `steps/<id>.guidelines.md` with frontmatter. The next read refuses that file. "Guidelines hold no
frontmatter" is enforced only on read, so the round trip is asymmetric.

**Fix.**
- Guard the invariant in `MissionStep`: `guidelines.flatMap(StepDocument::description).isEmpty()`, with a
  `ConstraintBreachException` message.
- Add the negative case to `MissionValuesTest`.

## Issue B (medium): ADR-0044 states the role fallback inaccurately

**Where.** ADR-0044:157-160 says the fallback applies to "the three `retrospect` steps (documentation, research,
software-dev) … whose `step.yaml` has `agent_profile: null`".

**What is wrong.** software-dev has no `retrospect` step: there is no `mission-steps/software-dev/retrospect/`. The
ADR itself says so at :107 and :310, and so do `retrospect-steps.md` and `migration-report.md:30`. The fallback gives
`facilitator` to two steps, documentation and research. Answer 5 of `review-response-1.md` repeats the same
inaccuracy.

**The rule is also not precise.** Both tools take `agent_profiles[0]` and drop any later entries silently
(verify_migration.py:189-190, MissionTierMigration.java:481). Every pinned list has one entry, but the ADR does not
say what a list of two means.

**Related.** ADR-0044:203-205 calls `manifest_version` and the whole plan `runtime` block repeats that are checked
equal. `manifest_version: "1"` repeats nothing; the verifier's KEY_TABLE rightly calls it `drop`. The runtime block's
`step_transition: manual` and `prompt_template_dir` are drops that are not checked. Only `loop_type` and
`terminal_step` are checked (see issue D).

**Fix.**
- Say "the two `retrospect` steps (documentation, research)", and note that software-dev's `retrospect` index has no
  step and is not carried.
- State that an index with more than one profile is refused, and make both tools fail on `len(agent_profiles) > 1`.
- Move `manifest_version`, `step_transition` and `prompt_template_dir` to the dropped list. Do the same in
  `mission-type-consolidation.md:55-57`.

## Issue C (medium): the gate evidence overstates what `:packs:wholePackRoundTrip` proves

**Where.** `gate-evidence.md:16-17` says "Every pack loads … and is written back byte for byte, mission types and
their `steps/` companions included". `mission-type-consolidation.md:27-28` says the same.

**What the gate does.**
- It covers only the built-in pack (`WholePackRoundTripTest.BUILT_IN`). The qa and cave-kitty mission types are not
  in it.
- It compares Markdown by model (`compareMarkdown`, WholePackRoundTripTest.java:562-568) and YAML by meaning
  (ADR-0036).

So no committed artifact proves User Story 2, scenario 3 (spec.md:79-81): "the file is byte-identical" for the 28
real prompts with frontmatter. Only one fixture prompt is tested byte for byte (`theFixtureWritesBackByteForByte`). The
property holds: my scratch run showed 66 of 66 files byte-equal. The evidence just does not show it.

**Also.** The re-pin script that the evidence depends on is not committed.

**Fix.**
- Correct the wording: built-in only; Markdown compared by model, YAML by meaning.
- Commit a byte-level check of the migrated tree. For example, a WP06 test over every `missions/**` file of each
  pinned pack: read, `toEntity`, `toDso`, `write`, `hasBinaryContent`. Or record its run and command in
  `gate-evidence.md`.
- Name the re-pin script, or inline its four edits, so the run can be reproduced.

## Issue D (low): the verifier's key coverage still has gaps on nested and cross-file keys

All of these pass on today's data, because the values agree. The verifier would not catch them if they did not.

1. **The runtime block.** It has no KEY_TABLE entry, and only `loop_type` and `terminal_step` are compared
   (verify_migration.py:139-141). An extra key in plan's `runtime` block passes silently.
   - Fix: add a `runtime block` table with `loop_type` and `terminal_step` as repeat and `step_transition` and
     `prompt_template_dir` as drop, and run `covered()` on it.
2. **`step.yaml` `agent_profile`.** It is labelled `map` (:57). When the runtime also names a profile, the
   runtime's wins without a comparison (:227, and MissionTierMigration.java:348-349). qa's 7 pairs are equal today.
   - Fix: `same()` when both are present, in both tools.
3. **Action indexes with no step.** They are never key-checked, and nothing asserts they are reported (:175-179 runs
   only from `build`). software-dev's `retrospect` index is dropped without the verifier seeing it.
   - Fix: enumerate `actions/*/index.yaml` and fail on any action with no step that is not in an explicit
     `KNOWN_UNCARRIED` list.
4. **`template_path`.** Nothing checks that it resolves to a file in NEW.
   - Fix: assert `os.path.exists(f"{tdir}/{template_path}")`.
5. **The 17 display-name drops and the 4 frontmatter-only descriptions.** The drafts state these numbers, but the
   verifier neither computes nor asserts them.
   - Fix: compute both independently and add them to `expected`.
6. **A latent bug at :202.** `step.yaml` is re-read by `sid` instead of `sdir`, so a runtime step whose directory is
   named differently (software-dev `discovery`/`research`) would lose its template.
   - Fix: use `sdir`.
7. **`self_test.py` plants only one repeat class**, the governance-profile id.
   - Fix: also plant a differing runtime `mission.key`, a differing nested plan step, and an extra runtime-block key.

## Issue E (low): tests for 9c00274 miss negative cases

1. **A blank detailed description.** `description: " "` passes the schema (`minLength: 1`). It is refused only at
   domain load, as a `domain` violation on file `research` rather than on the prompt file, and no test covers it.
   - Fix: add `"pattern": "\\S"` to `frontmatter.description` in `mission-step-prompt.schema.json` (and the fixture
     copy) as the twin of `StepDocument`'s not-blank rule, and test it.
2. **Other frontmatter refusals.** A non-string, missing or empty description and an empty frontmatter block are
   each refused today, but none is tested; `promptFrontmatterIsTheDetailedDescription` covers only `owner: me` and
   guidelines.
   - Fix: make it a parameterized test.
3. **The schema bound in code points.** No schema-side test shows that 160 astral code points pass.
   - Fix: add one row to `MissionTypeSchemaTest` so that the twins are shown to agree at the boundary.

## Issue F (low): small points in d6e5d13 and its neighbours

1. **A misleading duplicate violation.** `FilesystemPackStore.java:406` records the claimant before
   `readPayload`. If the first file leaves the pack or cannot be read, the second is refused as "already read from"
   a file that was never read, and neither copy joins the pack.
   - Fix: record the claimant only after a successful read, or word the violation "is also given by".
2. **Stale javadoc in PackFileSet.** At :99 and :192-196 it still says that templates "start with `templates/`" and
   that "Mission templates … are not among them". `templateId` and the listing now include
   `missions/<type>/templates/`.
   - Fix: update the javadoc.
3. **Stale lines in plan.md.** `plan.md:69` and `:89` still say that frontmatter is forbidden. This is optional,
   since the plan is historical; a one-line note pointing to the refined ruling is enough.

## Disposition

The verdict is changes requested, for A, B and C. All three are small:
- A is one guard and one test.
- B is ADR and issue-draft text, plus a `len > 1` check.
- C is the evidence wording and a committed byte-level check.

D to F can land in the same pass or be carried into WP06, which owns the re-pin and the harness deletion.
