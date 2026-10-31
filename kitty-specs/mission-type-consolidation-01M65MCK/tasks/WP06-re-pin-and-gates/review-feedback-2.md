# WP06 review, round 2 (reviewer-renata)

Reviewed: `1eb8e92` ("fix(packs): fold the WP06 review…"), at branch head `431fba5` of
`issue-194-mission-type-consolidation`. The open-packs checkout is at `86d9284`. This round checks only the round 1
issues and the regressions the fix could cause.

**Verdict: approve.** M1–M3 and L1–L4 are resolved. L5 is resolved for every path in the pinned and vendored packs,
but one overlap is left at the old built-in layout. It is listed as N1. N1 is low and not blocking; it can be fixed
in WP07 or in a follow-up.

## Gates

- `./gradlew -PopenPacksDir=/home/user/spec-kitty-open-packs verifyAll :packs:openPacksRoundTrip
  :packs:wholePackRoundTrip`: BUILD SUCCESSFUL.
  - `:packs:test`: 2098 tests, 0 failures, 3 skipped. That is 2 more tests than round 1, and the same 3 skips.
  - `openPacksRoundTrip`: 41/41. `wholePackRoundTrip`: 7/7.
- `python3 scripts/docs/check_docs.py all`: every check is ok.

## Round 1 issues

| Issue | Status | Evidence |
|---|---|---|
| M1 | Resolved | `WholePackRoundTripTest.java:77`: `"mission-type"); // #194` is in `REQUIRED_KINDS`. |
| M2 | Resolved | `packs/qa/PROVENANCE.md` names `86d9284…` (open-packs#95, 2026-10-31). The `mission-steps/` paragraph is now in the past tense. It describes `missions/qa/qa.mission-type.yaml` and `steps/<step>.prompt.md`, and all seven prompts exist on disk. It says the port-time verification no longer applies as written. |
| M3 | Resolved | `MissionTypeCorpusTest.java:174-181`: no violation is allowed outside `presets/`, in any pack. The preset count is pinned at 14 for built-in (`:46`) and 0 for qa and cave-kitty. The comment names OP-1 only. |
| L1 | Resolved | `PackContents.ParseFailure` has a typed `Optional<String> retiredReason`. Only `retired(...)` sets it. The two-argument constructor defaults it to empty. Nothing sniffs a prefix any more. `PackPolicyTest.aRetiredFileReadsAsRetiredAndAParseFailureKeepsItsWording` covers the three cases round 1 asked for. |
| L2 | Resolved | The `gradle.properties` comment, `docs/guides/service-build.md:30`, `packs/internal/PROVENANCE.md` and the `FileSystemPackInspectorTest` comment all name the new pin. The unused `YAML_FILE_COUNT` is deleted. |
| L3 | Resolved | `MissionTypeCorpusTest.java:78-94` collects every `template_path` of each type and of its steps and on-demand steps, in all three packs. It asserts there are 13 and that each is a regular file. This is equivalent to the `namedFiles` route round 1 suggested. |
| L4 | Resolved | `noRetiredFileIsLeft` (`:139-152`) also walks `<charter.repositoryRoot>/packs`. |
| L5 | Resolved, with a residual (N1) | The README exclusion is split into `*/missions/README.md`, `*/missions/*/README.md` and `*/missions/*/templates/**/README.md`. A README in a mission-type directory's `mission-steps/` or `command-templates/` is now retired. See N1 for the overlap that remains. |

The nit on `theRetiredRulesReplaceTheMissionExclusions` was answered: the test is kept, with a reason. That is
acceptable.

## Regression checks

- **The split README exclusions still exclude every pinned README under `missions/`.** At `86d9284` there are 13
  such files:
  - `built-in/missions/README.md` matches `*/missions/README.md`;
  - the four `built-in/missions/<type>/README.md` match `*/missions/*/README.md`;
  - the five `templates/README.md` and the three `templates/<dir>/README.md` match `*/missions/*/templates/**/README.md`.
    `**/` matches zero segments in this glob, so both depths are covered.

  The vendored `packs/qa/missions/qa/templates/README.md` is covered the same way. The gates would also fail on an
  unclassified file, and they are green. `PackFileKindTest` has a sample for each new exclusion.
- **`ParseFailure` equality and its call sites.**
  - All nine `new ParseFailure(file, line)` call sites in main use the two-argument constructor, so their
    `retiredReason` is empty: `FilesystemPackStore:948`, `FileSystemPackInspector:321,334,557,565`,
    `WholePackStore:223` and `ParsedYaml:126`.
  - The only producer of a retired failure is still `ParseFailure.retired`, at `FileSystemPackInspector:259`.
  - The test `isEqualTo(new ParseFailure(f, l))` comparisons in `FileSystemPackInspectorTest`, `PackValidationTest`
    and others compare parse failures, which have an empty reason on both sides. They stay correct.
  - `FilesystemPackStore:994` still reads `firstLine()`, which is still `"the file is retired: <reason>"` for a
    retired file. Its text is unchanged.
  - The record is not serialized anywhere, so the new component changes no wire shape.
- **The overlap of retired and exclusion rules.** I checked the new globs against each retired rule with the same
  regex translation as `PackFileKind.glob`. Only N1 remains.

## New findings

### N1 (low, not blocking): the top README of the old built-in `missions/mission-steps/` still matches two rules

`contracts/packs/file-kinds.yaml:179` and `:221`. At the old built-in layout, `mission-steps/` sits directly under
`missions/`: at `5df92a5` it is `built-in/missions/mission-steps/<type>/<step>/…`. So `*/missions/*/README.md` reads
`mission-steps` as a mission-type id:

- `built-in/missions/mission-steps/README.md` matches `*/missions/*/README.md` (exclusion) and
  `**/mission-steps/**/*` (retired). Exclusions are consulted first, so the file is excluded without a word.
- `built-in/missions/mission-steps/templates/README.md` matches `*/missions/*/templates/**/README.md` and the same
  retired rule. This path is contrived.
- `qa/missions/qa/command-templates/README.md` and any deeper `mission-steps/` README now match the retired rule
  only. That part of L5 is fixed.

No pinned or vendored path hits these cases. The new test (`RetiredFilesTest.java:69-78`) samples
`qa/missions/plan/mission-steps/specify/README.md`, which is not where either old layout put `mission-steps/`. So it
misses this case. The two README samples were also not added to `RetiredFileFixture`, as round 1 suggested, so the
`hasSize(1)` overlap loop still does not see them.

**Fix:**
- Add `missions/mission-steps/README.md` to the test, with a `matchingRules(...)` assertion that only the retired
  rule matches. Do the same for `missions/plan/command-templates/README.md`, which the test classifies but does not
  check with `matchingRules`.
- Then do one of these:
  - make the table say what the test expects, for example by checking the retired rules before the README
    exclusions;
  - or accept the precedence: document in the table comment that this one README is excluded, not retired, and pin
    that in the test.

### Nit

- `PackPolicy.java:566-567`: the reflowed Javadoc still breaks early after "holds exactly". Join it with the next
  line.
