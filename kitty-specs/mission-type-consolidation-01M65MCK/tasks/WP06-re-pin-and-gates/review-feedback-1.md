# WP06 review, round 1 (reviewer-renata)

Reviewed: `396bb2a` ("feat(packs): re-pin open-packs to the consolidated mission tier…"), at branch head `50fd379` of
`issue-194-mission-type-consolidation`. The open-packs checkout is at `86d9284`.

**Verdict: changes requested.** The re-pin is correct, and the qa pack, `file-kinds.yaml`, `expected-red.yaml` and the
harness deletion are all clean. The gates are green. Three items in the WP prompt's objective are not done, or are
done only in part: `mission-type` is missing from `REQUIRED_KINDS`, `PROVENANCE.md` was not updated, and the corpus
test's violation check is weaker than it needs to be. Each fix is small. No re-run of the migration is needed.

## Verified good

- **The pin.** `openPacksRef=86d9284695a18a71f9f7cc934f0305b1b03de19e` (gradle.properties:9). The checkout HEAD is
  `86d9284` and clean. Its tree is `a2c46684…`, the same tree as the verified bundle `12b0e55` in `/home/user/op-migrate`.
- **The re-vendored qa pack.** `diff -rq packs/qa <checkout>/packs/qa` differs only in `PROVENANCE.md` and `README.md`.
  `drg/fragment.yaml`'s `body_path` now names `missions/qa/qa.mission-type.yaml`, as upstream does. `LOCAL_DELTA`
  needs no change. `packs/internal` still differs from the new pin only in its three known files.
- **file-kinds.yaml.** All seven old mission exclusions are removed. The `retired` section has eight rules, each with
  a reason, in ADR-0044 decision 6 order. `*/missions/**/README.md` appears once (line 175). The diff touches no other
  hunk, so the #260 (`pack.md`) and #229 entries are unchanged. On the corpus and the fixture samples, no path matches
  two rules. The one rule-level overlap is listed below as L5.
- **expected-red.yaml.** Exactly two entries are removed: `kinds: other:missions` and `empty_kinds: mission-type`.
  Nothing else changed. The gate is green without them, so no `other:missions` file is left, and dropping the entry
  is better than narrowing it.
- **The test edits are justified:**
  - RetiredFilesTest and RetiredFileFixture now run on the bundled table. They drop the "until the re-pin" checks.
    The validate-mode check is stronger than before: the detail is compared in full with `isEqualTo`, where it used
    `contains`.
  - PackFileSetTest builds its table without retired rules by removing the `retired:` block. It asserts that the
    removal left no retired rule, so the comparison is not vacuous.
  - FileSystemPackInspectorTest: 386 − 74 + 4 = 316. I recounted at the two pins. `packs/built-in` holds 386 YAML
    files at `5df92a5` and 316 at `86d9284`; 74 YAML files were deleted and 4 mission-type files added. The listing
    test swaps the retired sample for a real exclusion, so the "an excluded file is listed" case keeps its coverage.
  - PackFileKindTest: the seven samples are dropped together with the seven exclusion rules. The retired samples get
    the same `hasSize(1)` check in `RetiredFilesTest.theOverlapCheckCoversRetiredRules`.
  - WholePackExpectationTest: the census now reports `retired:<rule>`, which is correct.
  - CompanionDigestTest is stronger than before. It pins 10 non-YAML files before the payloads and the order of the
    seven qa prompts right after the mission-type file.
  - MarkdownCorpusTest: the glob moves to the new layout, and 85 = 24 guides + `pack.md` + 60 step companions. That
    matches `find` at the pin.
- **The dropped empty-document assertion is justified.** No covered Markdown file is empty at `86d9284` (I checked
  with `find -empty`). The only empty files in the old corpus were the two 0-byte retrospect `prompt.md`. ADR-0044
  §8 lists them as an open-packs divergence, and the migrated `retrospect` on-demand steps carry no prompt. The old
  assertion tested nothing beyond the codec, and `MarkdownCodecTest.Canonical.emptyFile` still covers that:
  `""` and `"\n\n"` both parse to `MarkdownDocument.empty()`, which writes as `""`. No store-level check is lost.
- **MissionTypeCorpusTest proves a byte-for-byte write-back of all 66 files.**
  - 66 = 49 + 9 + 8, which is 6 mission-type files + 43 prompts + 17 guidelines.
  - Each file is deleted from the copy before the write. The type goes entity → DSO through the domain on the way
    back. Then `containsExactlyElementsOf` checks the set of files and `hasSameBinaryContentAs` checks each file's
    bytes.
  - If a type fails to read or a write is refused, files go missing and the test fails. Nothing in it is vacuous.
  - `noRetiredFileIsLeft` walks every pinned pack.
- **The harness is gone.** There is no `service/packs/src/migration`, no `migration` source set, no `packMigration`
  task, and the `errorprone` import is removed with them. `MissionTierMigration` and `packMigration` appear only in
  kitty-specs history and in the guard test. `docs/guides/service-build.md:170-185` labels its `packMigration` block as
  historical; it is the ADR-0043 harness. The guard reads the build file through `charter.repositoryRoot`, which
  Gradle sets (service/packs/build.gradle.kts:119,168).
- **Quality gates.** `./gradlew -PopenPacksDir=/home/user/spec-kitty-open-packs verifyAll :packs:openPacksRoundTrip
  :packs:wholePackRoundTrip` passes (BUILD SUCCESSFUL).
  - `:packs:test`: 2096 tests, 0 failures, 3 skipped (all three were already skipped before this WP).
  - `openPacksRoundTrip`: 41/41. `wholePackRoundTrip`: 7/7.
  - The diff adds no `@SuppressWarnings`, `NOPMD`, `CHECKSTYLE` or `@Disabled`.
  - Every new or changed method is far below complexity 15; the largest is `everyMissionFileWritesBackByteForByte`,
    at about 4.

## Issues

### M1 (medium, blocking): `mission-type` is not in `REQUIRED_KINDS`

`service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackRoundTripTest.java:62-76`. The WP prompt
says: "add `mission-type` to `REQUIRED_KINDS`". This commit removes `empty_kinds: mission-type` but does not add the
kind here. The `isEqualTo(bound)` check does not cover it, because `bound` is built from `KindBindings.all()`. If
`MissionTypeBinding` were dropped from the registry, both sides would shrink together and the gate would stay green.
`REQUIRED_KINDS` exists to catch exactly that.

**Fix:** add `"mission-type", // #194` to `REQUIRED_KINDS`.

### M2 (medium, blocking): `packs/qa/PROVENANCE.md` was not updated with the re-vendor

The WP prompt says to re-vendor "`packs/qa` exactly (and `LOCAL_DELTA`, `PROVENANCE.md`)". The file is local, so it
may differ from upstream, but it now states things that are false:

- `packs/qa/PROVENANCE.md:59` still says the copy was re-vendored at `5df92a5d…` on 2026-10-29.
- `packs/qa/PROVENANCE.md:31-33` still says, in the present tense, that "`mission-steps/` stays…" and that "the step
  prompts come from `missions/qa/command-templates/`". The pack no longer has either directory, and both are retired
  families.

**Fix:**
- Change line 59 to `86d9284695a18a71f9f7cc934f0305b1b03de19e` (open-packs#95, the consolidated mission tier) and
  today's date.
- Replace the `mission-steps/` paragraph with a sentence saying the mission tier is now
  `missions/qa/qa.mission-type.yaml` plus its `steps/*.prompt.md` (ADR-0044), and that the step prompts are the
  former command templates.
- The "Verified at port time" section is historical and may stay as it is.

### M3 (medium, blocking): the corpus test's violation check is narrower than it needs to be

`MissionTypeCorpusTest.java:150-154` keeps only violations whose file starts with `missions/`. I ran a throwaway
probe (not committed) to see what that hides at the pin:

- `qa`: 0 violations. `cave-kitty`: 0 violations.
- `built-in`: 14 violations, all in `presets/minimal.yaml` and `presets/default.yaml`.

So the filter hides nothing today. But it would also hide any future violation the mission tier causes in another
file, or any pack-level violation, in every pack. Two more problems:

- The filter is applied to qa and cave-kitty, which need no filter at all.
- The comment names OP-2 (the manifest), but the read reports no manifest violation.

**Fix:** pin what is known and nothing more. For example, assert that
`read.violations()` filtered on `v -> !v.file().startsWith("presets/")` is empty. For `built-in`, also pin the count
at 14 (or the exact set), so a new presets violation also fails the test. Correct the comment to "the pinned presets
(OP-1)".

### L1 (low): the retired marker is a string prefix that `retiredReason()` sniffs

`PackContents.java:197-219` and `PackPolicy.java:576`. A retired file is stored as
`ParseFailure(file, "the file is retired: " + reason)`. `retiredReason()` then sniffs that prefix on `firstLine` to
choose the wording.

- **It works today.** The parsers' messages never start with that text. `RETIRED` is private, and the only producer
  is `ParseFailure.retired`, so the inspector cannot mark a file by accident.
- **But it is a typed fact stored as text.** The `firstLine` Javadoc ("the first line of the parser's message") is no
  longer true for these records. The record's other eight producers (`new ParseFailure(` in main) could produce the
  marker by accident.
- **The prefix logic has no unit test.** It is tested only end to end in `RetiredFilesTest.ValidateMode`. Nothing
  checks that a real parse failure keeps "is not valid YAML: …", or that a retired `.md` file is worded as retired.
  The Markdown sample passes only because of `contains`.

**Fix (preferred):** give `ParseFailure` an explicit marker:
- add an `Optional<String> retiredReason` component, with a two-argument constructor that defaults it to empty;
- `retired(...)` sets the component;
- `retiredReason()` becomes the component's accessor;
- `firstLine` keeps its documented meaning.

**Fix (minimum):** add `PackPolicy` unit tests for three cases:
- `ParseFailure.retired("missions/a/mission.yaml", "r")` gives `qa/missions/a/mission.yaml is retired: r`;
- `new ParseFailure("x.tactic.yaml", "while parsing …")` keeps `is not valid YAML: while parsing …`;
- a retired `.md` file gives the retired wording, not `is not valid Markdown`.

Also reflow the Javadoc at `PackPolicy.java:563` (about 180 columns).

### L2 (low): stale pin references beside the pin

- `gradle.properties:7-8`. The comment still says "Currently the landed open-packs main tip 5df92a5 (open-packs PR #91
  …)". **Fix:** "Currently `86d9284` (open-packs#95: the mission tier consolidated, ADR-0044)."
- `OpenPacksCorpus.java:23-24`. `YAML_FILE_COUNT = 516` is "re-counted at 5df92a5", but there are 431 YAML files under
  `packs/` at the new pin. The constant is also unreferenced. **Fix:** delete it, or set it to 431, re-count it at
  `86d9284`, and assert it somewhere.
- `FileSystemPackInspectorTest.java:553`. The comment says "386 at 90de8f9", but `90de8f9` is not in the open-packs
  history. 386 is the count at `5df92a5`. **Fix:** "386 at 5df92a5".
- `docs/guides/service-build.md:30` ("currently `5df92a5d…`") and `packs/internal/PROVENANCE.md:25` ("re-vendored …
  at commit `5df92a5d…`") are outside WP06's owned files. **Fix:** either fold them into this commit as a shared-file
  edit, or hand them to WP07 explicitly. `internal`'s content is unchanged at the new pin, so its provenance line
  only needs the commit and the note that the files are unchanged.

### L3 (low): `template_path` resolution is only implied

The WP prompt asks `MissionTypeCorpusTest` to show "every `template_path` resolves". It does so only indirectly:

- the store refuses a type whose named file is missing (`FilesystemPackStore.missingNamedFile`);
- that refusal lands under `missions/`, so M3's filter catches it;
- the type count would also drop.

That chain holds, but nothing in the test says it, and the 13 template paths that WP05's verifier counted are not
pinned. **Fix:** in `theCorpusCountsArePinned`, collect `MissionTypeBinding.INSTANCE.namedFiles(dso)` across the three
packs. Assert there are 13, and that each one `Files.isRegularFile` under its pack.

### L4 (low): `noRetiredFileIsLeft` checks pinned packs only

The prompt says "no retired file in pinned or vendored packs". `VendoredPacksTest` makes this hold transitively, since
the vendored packs equal the pin apart from `LOCAL_DELTA`. Nothing states it directly. **Fix:** also loop over
`packs/qa` and `packs/internal` under `charter.repositoryRoot`. Two lines.

### L5 (low): the README exclusion overlaps two retired rules

`contracts/packs/file-kinds.yaml:175`. `*/missions/**/README.md` and two of the retired rules both match a README in
a retired directory:

- `**/mission-steps/**/*` (line 214) matches `built-in/missions/mission-steps/x/README.md`;
- `**/missions/*/command-templates/*` (line 216) matches `qa/missions/qa/command-templates/README.md`.

Exclusions are consulted before retired rules, so such a stale file is excluded without a word instead of refused.
No pinned path hits this, but the table says it "promises at most one" rule per path. The overlap check runs only on
samples, which is why it was missed.

**Fix:** add the two README paths to `RetiredFileFixture` so the overlap check sees them. Then either:
- narrow the README exclusion so it does not cover retired directories; or
- document in the table comment that a README is never pack content, even in a retired directory, and assert that
  precedence in a test.

### Nits

- `RetiredFilesTest.java:56-63`. `theRetiredRulesReplaceTheMissionExclusions` is fully covered by two existing checks:
  `everyFamilyIsClassifiedRetired` (a sample hit by an exclusion would classify `Excluded`) and the `hasSize(1)` loop
  at :95-97. Delete it, or turn it into an assertion that the bundled table has no exclusion whose `match` is one of
  the seven old globs, which checks something new.
- Out of scope, already broken before this WP: `packs/qa/README.md:84` links `toolguides/QA-PROMISE-TRACE-LINT.md`,
  which does not exist. Upstream links `qa-promise-trace-lint-guide.guide.md`.

## To re-review

M1 to M3, and L1 (at least the unit tests) and L2, for the files WP06 owns. L3 to L5 may land in the same fix-up
commit. Re-run `verifyAll :packs:openPacksRoundTrip :packs:wholePackRoundTrip`.
