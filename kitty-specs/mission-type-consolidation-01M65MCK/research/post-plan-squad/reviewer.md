# Post-plan review: #194 PR-B (mission-type consolidation): testability, gate honesty, delivery risk

Profile: reviewer-renata (read-only). Inputs: spec.md, plan.md, data-model.md, research.md, research/squad/gates.md,
ADR-0044, the named tests and ledgers, and the pinned open packs at 5df92a5d. No repo edits.

Verdict: **not ready for tasks as written.** There are 2 blockers and 9 majors. Most of them are cheap to fix in the plan text.

---

## B1 (blocker): the frontmatter `description` drop is called a repeat, but it is a different value in 24 of 24 cases

- **Evidence**:
  - ADR-0044:213-214 says "the pinned prompts' frontmatter `description` repeats the step's description (S5) and is
    dropped".
  - Plan IC-07 (plan.md:116) drops it "where the runtime has one".
  - A census at the pin compared the runtime `description` with the prompt frontmatter `description` for every runtime
    step that has both. Result: same 0, differ 24. Examples:
    - software-dev `plan`: "Design technical architecture" against "Create an implementation plan".
    - documentation `generate`: "Produce documentation artifacts under docs/." against "Generate documentation content
      against the locked design…".
  - divergence.md:594 (DP-4) raised the question. No decision record rules on it: `decisions/` has no frontmatter ruling.
- **Why it matters**: FieldMappingVerifier passes any "listed drop". The plan would bless a loss of meaning under a
  false S5 label, which is exactly what the verifier exists to stop.
- **Fix**:
  - Get an operator ruling. Either drop the value as a *conflicting value* (S6, with all 24 pairs listed in the research
    table and an open-packs issue), or keep it somewhere.
  - Correct ADR-0044 §5.
  - Make the verifier check text equality for every drop labelled "repeat" (S5). A drop that is not a repeat must be in
    a list with a reason.

## B2 (blocker): the red window is misdescribed, and as sequenced it is silently lax before it is red

- **Evidence**:
  - plan.md:146-147 says corpus tests are "red by design" from WP02's `file-kinds.yaml` change.
  - In fact, removing the seven exclusions (IC-03, plan.md:75) turns the retired files **Unclassified**, and an
    unclassified file is skipped, not refused (file-kinds.yaml:48-49; gates.md:244-246). So
    `WholePackOpenPacksRoundTripTest`, validate mode and the gate (whose `other:missions` kind entry still covers them)
    stay **green while blind** from WP02 until WP04.
  - The `retired:` section WP02 adds is **silently ignored** by the parser until WP04. `PackFileKind.parse`
    (PackFileKind.java:289-330) reads only named sections, and unknown top-level keys pass.
  - The real red starts at WP04, when retired refusal hits built-in, cave-kitty, qa and the repository's own `packs/qa`.
    `packs/qa` cannot be fixed early, because VendoredPacksTest.java:40-80 pins it byte-equal to `5df92a5d`.
  - From WP05, `ProcedureAndGuideCorpusTest#theMigrationHarnessIsGone` (line 114) is also red.
  - Result: WP04 and WP05 cannot be reviewed against a green `check`/`verifyAll`. "Red by design" then hides any
    unrelated regression in three or more open-packs test classes.
- **Fix (preferred)**: reorder so that no window exists.
  - WP02/WP03 add only the *new* `files` rules. They match no path in the old layout.
  - Keep the seven exclusions in the bundled table.
  - WP04 builds and tests retired refusal against fixture tables (the `PackFileKind(String)` constructor) and fixture
    packs only.
  - Do the exclusion removal, the `retired:` section and the re-pin atomically in WP06.
  - Put the harness in a separate `migration` source set that is not on the test runtime classpath, so the guard stays
    green.
  - Also make `PackFileKind.parse` refuse unknown top-level keys, with a test.
- **Fix (fallback)**: if the window is kept, list in the plan the exact test methods expected red per WP. Each WP's
  review then runs `check -x openPacksRoundTrip` plus `openPacksRoundTrip` with those methods only failing.

## M1 (major): WP02 breaks `:packs:test` unit tests that need WP03's binding, so WP02 cannot be green on its own

- **Evidence**: these tests break at WP02:
  - KindBindingsTest `theCompanionStructuresAreTheBindingsOwn` (~line 150-157): every `markdown` files rule must be a
    bound companion structure. WP02 adds the `mission-step-prompt`/`-guidelines` rules with no binding.
  - VocabularyEqualityTest `theInspectorsArtifactSchemaKindsAreTheMappedSchemasThatDeclareAnId` (~151-180): the new
    `mission-type` schema declares `id`. The derived set and the hand list of ten diverge.
  - PackFileKindTest's EXCLUDED samples (lines 53-59) and `FileSystemPackInspectorTest`:225-234
    (`excluded("missions/m/mission.yaml", …)`).
- **Evidence**: this test breaks at WP03: KindBindingsTest `theProcedureIsAppendedLast` (63-66: `hasSize(11)`, last is
  PROCEDURE).
- **Fix**:
  - Move the `files` rules and companion schemas into WP03. WP02 is then the kernel kind plus `mission-type.schema.json`
    (and the inspector list).
  - Name every test edit above in the owning WP's owned files.

## M2 (major): FR-007 ("a new registration is written to `missions/<id>/<id>.mission-type.yaml`") cannot pass with the planned changes

- **Evidence**:
  - `FilesystemPackStore.newFile` (FilesystemPackStore.java:836-849) refuses any target whose parent is not the pack
    directory ("does not name a file in the pack") and creates no directories.
  - KindBindingsTest `theDefaultFileNameClassifiesBackToTheKind` (130-139) writes `pack.resolve(name)` without
    `createDirectories`.
  - IC-04 changes `fileName(id)` but not `newFile`.
- **Fix**: WP03 changes `newFile` to allow `missions/<id>/` for this binding only, creating the directory and the
  `steps/` directory for companions. Keep the traversal and link refusals. Add a create acceptance test and a
  refusal test, for example an id of `../x`.

## M3 (major): `MarkdownCorpusTest` is missing from the plan, and its pin goes stale at the re-pin

- **Evidence**:
  - MarkdownCorpusTest.java:39-46 covers `glob:packs/*/missions/mission-steps/**.md` with `PINNED_COUNT = 72`
    (tagged `open-packs`, so it is in `check`).
  - At the new pin, that glob matches nothing (−47 files), and the new `missions/*/steps/*.md` companions (60) are not
    covered.
  - The test is not in gates.md and not in IC-06/IC-08.
- **Risk**: the easy fix is to delete the glob, which is a silent loosening.
- **Fix**: rewrite the glob to `packs/*/missions/*/steps/*.md` and pin the new count. That should be 72 − 47 + 60 = 85,
  but verify it at the pin. Add this to IC-06.

## M4 (major): US1 scenario 2 (placement) is only partly testable, because a misplaced file is skipped, not refused

- **Evidence**:
  - The files rule `*/missions/*/*.mission-type.yaml` (plan.md:73) matches only files directly in `missions/<dir>/`.
  - `built-in/x.mission-type.yaml`, `built-in/missions/a/b/x.mission-type.yaml` and
    `built-in/mission_types/x.mission-type.yaml` match no rule, so they are Unclassified and skipped
    (file-kinds.yaml:48-49).
  - `placementMismatch` (IC-04) only ever sees `missions/<other>/<id>.mission-type.yaml`.
- **Fix**:
  - Either make the files rule `**/*.mission-type.yaml` and let `placementMismatch` refuse every other location, or add
    a `retired`/refused catch-all for `**/*.mission-type.yaml` outside `missions/<id>/`.
  - Add one fixture per location. Do the same for stray `steps/*.prompt.md` at the wrong depth.

## M5 (major): no committed positive controls; the spec's independent tests depend on one manual step

- **Evidence**:
  - The spec's independent tests require them: US1 "changing one step title is reported as a difference"
    (spec.md:41-43), and US2 "editing one section… reported at that section" (spec.md:64-65).
  - The plan's only control is manual: "break the writer locally, see the gate go red, revert" (plan.md:130).
  - The precedent committed lossy-writer positive controls (procedure mission plan.md:245; tasks T009, T018).
- **Fix**: add committed tests, as an extension of `WholePackRoundTripTest`'s existing "positive control through the
  production write path" pattern, or a fixture test in `:packs:test`:
  - step title changed: red at `steps{id}.title`;
  - two steps swapped: red, which proves order is meaning;
  - an on-demand step dropped;
  - one prompt section edited: red at that section;
  - a reference reordered: red.
  - Also add a verifier self-test: plant a mapper defect (a field renamed, a value changed, a reference dropped) and
    assert that `FieldMappingVerifier` fails.

## M6 (major): the harness verification is not independent of the mapper, and two of its checks prove nothing

- **Evidence** (IC-07, plan.md:119-121):
  - "round-trips its output through the new store" proves that the writer and the reader agree. It says nothing about
    whether the mapping is faithful.
  - "counts in equal counts out" is false by design: `discovery`+`research` merge, `done` is dropped, and
    `required_by_step` keys `tasks_outline`/`tasks_packages`/`tasks_finalize` are renamed to hyphenated step ids
    (software-dev `expected-artifacts.yaml`).
  - "a second run is a no-op" is vacuous: the output has no retired files left to read.
- **Fix**:
  - The verifier parses the *output* with raw snakeyaml, not through `MissionTypeDso`/`MissionTypeYaml`.
  - It checks against an expectation table typed from ADR-0044 §3, with no shared constants or helpers with
    `MissionTierMapper`.
  - Pin explicit in/out counts (see M7).
  - Redefine idempotence: loading the migrated tree and writing it gives byte-identical files.
  - Check that every migrated directive id (62 references, stem to `DIRECTIVE_nnn`) **resolves** to a directive in the
    pinned packs. Today nothing checks this, because references "mint nothing" (US3).
  - Commit the verifier report under `research/migration/`, because the harness is deleted.

## M7 (major): the mapper rule is ambiguous at the pin, and the counts are "about"

- **Evidence**:
  - The rule "steps with `in_action_sequence: false` and no runtime entry go to `on_demand_steps`" (plan.md:115) also
    captures:
    - `documentation/retrospect` and `research/retrospect`: both `in_action_sequence: false` with 0-byte prompts;
    - `software-dev/research`: `in_action_sequence: false` with no runtime entry. It must instead merge into
      `discovery`.
  - ADR-0044:102-103 names only software-dev's four on-demand steps.
  - The plan's scale (plan.md:29-30, "about 50 steps, about 60 companions", "about 150 files") cannot be gated.
- **Fix**:
  - State rule precedence: the merge comes first.
  - Rule on retrospect: either an on-demand step with no prompt, or dropped with an issue.
  - Pin exact per-type numbers in the plan and in `MissionTypeCorpusTest`:
    - sequenced steps 7/4/6/8/8/7 = 40 (documentation, plan, research, software-dev, cave-dev, qa);
    - on-demand steps 4 (+2 if retrospect is kept);
    - prompts 43 (built-in 28, cave 8, qa 7);
    - guidelines 17;
    - retired files 153 (built-in 121, cave 13, qa 19).

## M8 (major): NFR-003 ("no tolerance of a retired or variant field") has no static guard, and the property tests never reach the new rules

- **Evidence**:
  - The precedent had a retired-name source scan (procedure mission plan.md:247). This plan has none.
  - GeneratedPackTree.java:65-67 keeps `missions`, `mission_types` and `actions` out of the alphabet. IC-06 says only
    "revisited".
- **Fix**:
  - Add a source-scan test over `:missions`/`:packs` main sources. It forbids the tokens `display_name`, `agent_profile`,
    `sequence_index`, `in_action_sequence`, `depends_on`, `mission-runtime`, `governance-profile` and
    `expected-artifacts`, except in `file-kinds.yaml` `retired`.
  - Add GeneratedPackTree shapes `MISSION_TYPE` (LOADED, with companions) and `RETIRED_MISSION_FILE` (a REFUSED
    verdict), so the generated trees test the new rules.

## M9 (major): the pack-manifest constituent `kind` enum is left without `mission_type`

- **Evidence**:
  - `pack-manifest.schema.json` `$defs/constituent/properties/kind` has 13 tokens and no `mission_type`.
  - Plan IC-03 (plan.md:78) says the `$defs` copies "are not widened".
  - The plan's own Risks (plan.md:156) expect upstream follow-ups on manifest hashes.
  - `WholePackOpenPacksRoundTripTest.sanitizedManifest` (491-516) keeps every listed constituent whose kind is bound,
    and its count is pinned at 272 (line 446).
- **Risk**: if the upstream manifest starts listing mission types, the schema refuses them. If it does not, a bound
  kind is silently absent from the manifest.
- **Fix**: decide whether a mission type is a manifest constituent. If yes, widen that one enum, add a vocabulary-
  equality assertion and pin the new count. If no, state why in ADR-0044 and add a test that asserts the absence.

## m1 (minor): the spec and the plan disagree on the retired families

- **Evidence**:
  - spec.md:100-102 (US4) lists seven files.
  - ADR-0044 §6 and plan.md:30/77 say eight families, including `prompt.md`, `guidelines.md` and
    `command-templates/*.md`.
  - US4's independent test ("one retired file of each family") is therefore ambiguous.
- **Fix**: enumerate the exact `retired` globs in the spec: 7 YAML families plus `mission-steps/**/prompt.md`,
  `mission-steps/**/guidelines.md` and `missions/*/command-templates/*.md`. Add one PackFileKindTest sample per glob,
  and one US4 fixture file per glob.

## m2 (minor): the migration leaves pinned non-model files stale or at risk

- **Stale READMEs**: `built-in/missions/README.md:21-24`, `software-dev/README.md:44-47` and `plan/README.md:14-15`
  describe the retired layout. They are gate-excluded, so nothing catches the drift.
- **Stale provenance**: `packs/qa/PROVENANCE.md:31-33` (a local file, which VendoredPacksTest.java:29 never compares)
  says `mission-steps/` must stay.
- **Excluded file**: `plan-field-declaration.yaml` stays excluded (file-kinds.yaml:176). That keeps `other:missions`
  non-empty, which is correct, but the narrowed reason must name it.
- **Gate exclusion at risk**: `built_in_step_contracts/__init__.py` is gate-excluded (`**/__init__.py`). If a broad
  delete removes it, the gate reports `STALE_EXCLUSION`.
- **Fix**:
  - The harness deletes only files matching the `retired` globs (an allowlist), and asserts that the set of non-retired
    files is unchanged.
  - The README updates go in the issue drafts.
  - WP06 updates `packs/qa/PROVENANCE.md`.

## m3 (minor): REQUIRED_KINDS and the template invariants are missing

- **REQUIRED_KINDS**: WholePackRoundTripTest:62-74 should gain `mission-type` (gates.md:26). IC-06 does not say so.
- **`template_path` base**: data-model.md:52 says "inside the pack, `templates/...`". ADR-0044:140 says "relative to
  the mission-type file". Pick one.
- **`template_path` existence**: no test checks that the file exists. All 13 pinned targets exist under
  `missions/<t>/templates/` today. Add a load-time or corpus check.

## m4 (minor): complexity and suppression risk

- **Complexity**: the ≤ 15 limit is not enforced by a tool (gates.md:162-163). `MissionTypeYaml`, which writes
  two-level optional fields with S16 empty-list omission, and the companion enumeration are high-branching.
  - **Fix**: split the writer per sub-item (step, expected artifact, reference), and record the complexity measured by
    a one-off tool in the review evidence.
- **NullAway**: it covers only `@NullMarked` packages, so the new `:missions` package needs a `package-info`, or it
  silently escapes NullAway.
- **Build file leftovers**: the `packMigration` task and the `migration` tag exclusions in `build.gradle.kts` must be
  removed at the re-pin. `theMigrationHarnessIsGone` checks only the package, so nothing enforces it.
  - **Fix**: extend the guard, or use the separate source set from B2.

## m5 (minor): on-demand steps are a `Set` with record equality

- **Evidence**: `Set<MissionStep>` (data-model.md:21) deduplicates by record equality, not by id.
- **Fix**: the builder enforces id uniqueness across both collections (FR-006). Add a test with two steps that share
  an id but have different titles. Make the writer's order deterministic (insertion order), with a test.
