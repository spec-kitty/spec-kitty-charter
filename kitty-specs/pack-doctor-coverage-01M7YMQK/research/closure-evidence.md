# Closure evidence

WP09, T036 (`docs/development/issue-tracker.md`, Rule 2; `issue-matrix.json`). This is the source for the issue
section of the PR body (`feat/pack-doctor-coverage` into `main`). It lists, for every issue this mission touched, the
verb the PR uses and the file or scenario that shows the behavior.

All paths are charter paths on the integrated mission code, `kitty/mission-pack-doctor-coverage-01M7YMQK-lane-h` at
`744df9b61`. ADR-0083 is `docs/adr/0083-one-answer-on-every-surface-preset-items-stale-manifests-and-pack-doctor.md`.
Test paths are shortened to `<module>/…/<Test>.java` under `service/<module>/src/test/java/ai/speckitty/charter/`.

## Summary for the PR body

```text
Closes #614, #629, #630 and #154.
Refs #632: item 1 (WP03) and item 2 (WP07) land, and item 3 lands narrowed by ruling R-s (WP05: delegations,
references, enhancement targets). The rest of item 3 remains for #636 slices 2-4 and 7: the specializes_from cycle,
the relation checks, the edge-index refusals and the own-closure walk.
Refs #636: slice 1 (#614) and the round-trip-codes slice (ruling R-t) land; slices 2-7 remain.
Closed with evidence, not by this PR's keywords: #52 item 3 (obsolete) and #535 (not_planned), ruling R-g.
Epics: tick #549 for #614, #629 and #630 (#632 stays open under it); tick #142 for #154.
Kept in view: #158 (manifest_stale is its first step).
Open-packs drafts (not filed): open-packs#TBD-pack-doctor-repin, #TBD-preset-item-refusals, #TBD-manifest-stale,
#TBD-retire-python-rules, #TBD-report-format, #TBD-anti-pattern-files (research/open-packs-drafts.md).
```

## Closes

### #614: ADR-0082 slice 1, one closure fault type

Verb: `Closes #614`. Delivered by WP01 (FR-001; ADR-0083 decision 15).

| Acceptance | Evidence |
| --- | --- |
| `Origin` on `Reason` | `kernel/src/main/.../kernel/validation/Origin.java:16` (`Origin.NONE` `:22`); `Reason.java:17` carries `origin`; `kernel/…/validation/OriginTest.java`, `ReasonTest.java` |
| One `ClosureFault.of(...)` factory | `packs/src/main/.../packs/domain/ClosureFault.java:79`, `:94`, `:111`; `packs/…/domain/ClosureFaultTest.java` |
| `CompositionFault` deleted | No `CompositionFault` type in any `service/*/src/main`. `packs/…/domain/OneClosureFaultTypeTest.java`: `noCompositionFaultRemains` `:35`, `everyFactoryTakesTheOrigin` `:47` |
| Wire codes and order unchanged | No change fragment for the slice (ADR-0083 decision 15: "No wire code, status, detail or order changed"). The surfaces' codes are pinned by the golden files `packs/src/test/resources/golden/surfaces.json` and `pack-validation/src/test/resources/golden/validate.json`, which `pack-doctor/…/parity/PlantedParityTest.java:102` reads |

Note for the issue comment: `ClosureVerdict` stays a fault list with a derived `Rationale`
(`packs/src/main/.../domain/ClosureVerdict.java:17`). A reason has no slot for the packs and violations a refusal
names. #636 slice 7 removes it (ADR-0083 decision 15). This is the "or" branch of the issue's acceptance, recorded,
not left implicit.

### #629: a preset `must_have` the closure does not hold

Verb: `Closes #629`. Delivered by WP03 (FR-002 and FR-003; rulings R-a, R-k, R-l and R-m; ADR-0083 decisions 1-4).

- **The rule:** `packs/src/main/.../domain/rules/PresetItemRules.java` (`RULE = "closure.preset-items"`, `:59`), run
  through `PackRulesets` on every registration and write, and at `CLOSURE` of `pack-doctor validate`.
- **The code:** `preset_item_not_held`, 422, in `contracts/charter/schemas/CharterRefusalCode.yaml:331` and
  `:1131-1143`. Fragment: `contracts/charter/changes/2026-11-21-preset-item-refusals.md`.
- **Scenarios:** `contracts/charter/scenarios/registration/preset-items.feature`:
  - `:38` "A must_have item no pack of the own closure holds is refused preset_item_not_held at the item".
  - `:99` "The same preset registers once the pack holds its items".
- **Tests:** `packs/…/domain/rules/PresetItemRulesTest.java`:
  - `anUnheldMustHaveIsNotHeld` `:86`
  - `heldOnlyByADependentIsNotHeld` `:110`
  - `heldByTheParentIsAccepted` `:125`
  - `declaredOnlyMustHaveIsNotHeld` `:151`
  - the planted example cases `:370`
- **Same code on both surfaces:** `deployable/…/bdd/FullPassParityTest.java:822`
  (`theRegistrationAndValidateFullGiveTheSameCode`).
- **A composing read still reports `requiredNotHeld`:** `deployable/…/bdd/RequiredNotHeldCoverageTest.java:32`.
- **The example packs carry the canonical shape** (FR-010): `org-base` gains the two `spdd` tactics
  (`service/packs/src/testFixtures/resources/example-packs/org-base/tactics/reasons-canvas-*.tactic.yaml`), and
  `built-in` holds `anti_pattern:indirection-for-repetition` as a file.

### #630: a stale `content_hash` without `--round-trip`

Verb: `Closes #630`. Delivered by WP04 (FR-004 and FR-005; ruling R-b; ADR-0083 decision 5).

- **The rule:** `packs/src/main/.../infra/storage/rules/ManifestRules.java:89-109`. The code is
  `StorageCodes.MANIFEST_STALE` (`StorageCodes.java:83`), judged in the storage manifest gate beside
  `manifest_membership`. The contract describes it at `CharterRefusalCode.yaml:431-433`. Fragment, `### Breaking`:
  `contracts/charter/changes/2026-11-21-manifest-stale.md`.
- **Scenarios:** `contracts/charter/scenarios/registration/whole-pack-registration.feature`:
  - `:565` "An uploaded manifest with a stale content_hash is refused at the manifest, naming the field".
  - `:595`, its control, "An uploaded manifest whose hashes match registers, its order restamped".
- **Tests:**
  - `packs/…/infra/storage/rules/ManifestRulesTest.java`: `aStaleConstituentHashIsOneManifestStale` `:87`,
    `aStaleManifestHashIsOneManifestStale` `:253`, `aPathMembershipNamesGetsNoManifestStale` `:121` and
    `aStaleHashWaitsForTheOtherFaults` `:296`.
  - `pack-doctor/…/StaleManifestTest.java:55`, `aStaleContentHashIsExactlyOneManifestStale`: one problem without
    `--round-trip`, and no `round_trip_red` beside it with `--round-trip`.
- **The repair (#632 item 2):** `pack-doctor remediate` (`packs/src/main/.../infra/storage/PackRemediation.java`;
  `pack-doctor/…/RemediateTest.java`).

### #154: the `anti_pattern` kind, its schema and its refusals (ADR-0052)

Verb: `Closes #154`. Delivered whole by WP10, WP11 and WP12 (FR-013 to FR-015; rulings R-p, R-q and R-r; ADR-0083
decisions 12-14; SC-004). The rows follow the acceptance criteria.

| Acceptance | Evidence |
| --- | --- |
| The closed schema | `contracts/packs/schemas/anti-pattern.schema.json`. Required: `schema_version`, `id`, `title`, `description`, `severity` (`smell`/`severe`/`breaking`, no default) and `bad_example`. Optional: `good_example`. The `files` rule is `contracts/packs/file-kinds.yaml:231-232`, and the token `anti_patterns: anti_pattern` is at `:134`. Fragment: `contracts/charter/changes/2026-11-21-anti-pattern-file-kind.md` |
| The dropped fields | `contracts/packs/schemas/procedure.schema.json` and `styleguide.schema.json` no longer carry `anti_patterns`. `contracts/charter/schemas/StyleguideAntiPattern.yaml` and `ProcedureAntiPattern.yaml` are deleted. `Styleguide.yaml`, `StyleguideRegistration.yaml` and `Procedure.yaml` lose `antiPatterns`. The domain type `packs/domain/StyleguideAntiPattern.java` is deleted. Fragment: `contracts/charter/changes/2026-11-21-inline-anti-patterns-dropped.md` |
| The store binding | `packs/src/main/.../infra/storage/AntiPatternBinding.java`, `AntiPatternDso.java` and `AntiPatternYaml.java`. Tests: `packs/…/infra/storage/AntiPatternBindingTest.java`, with `theFileReadsIntoTheDomain` `:86`, `theFileWritesBackByteForByte` `:105`, `anUnknownSeverityIsRefused` `:159` and `aLoadRefusesAMisnamedAndAMisplacedFile` `:205` |
| The four refusal tests and their codes | 1. A missing `severity` is `artifact_schema_error`: `pack-validation/…/antipattern/AntiPatternFileCheckTest.java:65`. 2. A missing `bad_example` is `artifact_schema_error`: same file, `:76`. 3. An inline `anti_patterns` block is `artifact_schema_error`, unknown key: `InlineAntiPatternCheckTest.java:98` (styleguide) and `:115` (procedure). 4. A file-less `anti_pattern` node is `drg_schema_error`: `AntiPatternNodeCheckTest.java:84` (shard) and `:95` (fragment). |
| The four refusals on the registration surface | `contracts/charter/scenarios/registration/anti-pattern-files.feature:57` (Outline: the closed schema's refusals) and `:90`. `anti-pattern-nodes.feature:71` and `:90`. `inline-anti-patterns.feature:58` and `:91` |
| `rejects` across packs and no cascade | `anti-pattern-nodes.feature:115` (outside `depends_on`: refused) and `:164` (inside: resolves). `activations/…/domain/RejectsIsNotCascadedTest.java` |
| The canonical example packs | 18 files under `service/packs/src/testFixtures/resources/example-packs/built-in/anti_patterns/`, each with its node and a `rejects` edge in `procedure.graph.yaml` or `styleguide.graph.yaml`. The inline blocks are removed from the five example files that carried them (two procedures, three styleguides). `packs/…/examples/ExamplePackCoverageTest.java`: `everyPresetAntiPatternIsAFileItsPackHolds` `:93`, `everyExampleAntiPatternNodeNamesAFileItsPackHolds` `:117`, `theExampleAntiPatternsCoverEverySeverity` `:142` and `noExampleStyleguideOrProcedureCarriesAnInlineAntiPattern` `:160` |

Open-packs' consequence is draft (f), `open-packs#TBD-anti-pattern-files`. Link it from the closing comment on #154.

## Refs

### #632: pack-doctor covers the cross-pack rules open-packs runs in Python

Verb: `Refs #632`. Ruling R-s; ADR-0083 decisions 8-9. R-s governs over R-q's "#632 still closes in this PR"
(ADR-0083 `:263`).

| Item | State | Evidence |
| --- | --- | --- |
| 1: the undeclared preset id | landed (WP03) | `preset_item_undeclared`: `CharterRefusalCode.yaml:332` and `:1144-1151`. `preset-items.feature:63`. `PresetItemRulesTest.java`: `anUndeclaredItemIsUndeclared` `:162` and `anotherKindNeverSatisfiesTheEntry` `:181` |
| 2: manifest derivation | landed (WP07) | `pack-doctor remediate`: `PackRemediation.java`, `pack-doctor/…/RemediateTest.java`. Guide: `docs/guides/check-packs.md:182-216` |
| 3: the closure walk, narrowed | landed (WP05) | `validate --full` is the `REFERENCES` check: delegations, references and enhancement targets, per own closure, with registration's codes. `pack-validation/…/set/ReferencesPhaseTest.java` (the one body, `PackRulesets.references`, ruling R-ab; gated on composition faults only, R-aa). `FullPassParityTest.java:822`. Fragment: `contracts/charter/changes/2026-11-22-pack-doctor-validate-full.md` |

**The remainder, to name on the issue:** item 3's `specializes_from` cycle check, the relation checks
(`ImpactRelations`), the edge-index refusals (`EdgeIndex`) and the own-closure walk (`OwnClosureWalk`: the fallback
profile, role pins and profile enhancements). They land in the domain factory with #636 slices 2-4 and 7 (ADR-0083
decision 9). Registration keeps refusing each of them. Charter `docs/guides/check-packs.md:165-167` and `:283-295`
tell users.

### #636: ADR-0082 migration epic

Verb: `Refs #636`.

- **Slice 1** is #614, closed above.
- **The round-trip-codes slice** is folded into WP04 by ruling R-t (ADR-0083 decision 16). `EXCLUDED_WRITTEN` is
  retired outright for `NOT_HELD_WRITTEN`, and the `STALE_BASELINE` and `STALE_KNOWN_FAILURE` wordings for such an
  entry merge into one.
  - Code: `pack-validation/src/main/.../roundtrip/WholePackExpectation.java:497`.
  - Tests: `pack-validation/…/roundtrip/WholePackExpectationTest.java:1016-1056`.
  - Fragment: `contracts/charter/changes/2026-11-21-round-trip-not-held-code.md`.
- **Tick** slice 1 and the round-trip-codes slice. Slices 2-4 and 7 carry #632's remainder; slices 5 and 6 are
  untouched.

## Closed with evidence (ruling R-g)

These close through a comment, not a PR keyword (Rule 2, step 3). Each comment names the PR and ADR-0083 decision 17
(`:209-213`).

- **#52 item 3** (a bare id under two kinds): close the item as **obsolete**. Every edge endpoint is a `<kind>:<id>`
  URN (ADR-0017 R6, DIV-007), and no map of bare ids remains (`research/rule-coverage.md` rule 9b, finding F5), so
  the case cannot arise.
  - If #52 has no other open item, close #52 `completed` with that comment. If it has, edit its body to tick item 3
    and link the comment.
  - Not checked here: #52's other items. This session cannot read GitHub issues.
- **#535** (a file-name collision across pack layers): close **`not_planned`**. The charter keys an artifact by its
  kind and id, never by its file name, and checks a file stem only for the kinds whose id is their stem. The collision
  is core's activation-token concern (finding F6). Core work is not tracked here until incorporation (Rule 5).

## Epics

- **#549:** tick #614, #629 and #630. #632 stays open under it, with `Refs`, until #636 lands the remainder.
- **#142:** tick #154. Close #142 only if #154 was its last open child.

## Kept in view

- **#158** (pack authoring: content-hash integrity protection) stays open. `manifest_stale` is its first step: every
  surface now verifies each `content_hash` and the `manifest_hash` of `pack-manifest.yaml` against the pack's bytes.
  A stale manifest is refused, not silently restamped (ADR-0042's 2026-11-12 amendment is amended). `remediate` is the
  one repair.
  - What stays open is the rest of #158 beyond `manifest_stale`, out of scope (C-005; ADR-0083 decision 18). Per
    finding F4 of `research/rule-coverage.md`, per-asset hashes would extend the same derivation (`PackManifests`).
  - Comment on #158 with this, and do not close it.
- **#478** (context only): plan.md cites its CI timing step, relabelled for pack-doctor. No WP delivers it, so the PR
  does not name it.

## Not in this PR's issue list

- **open-packs #343** (`charter:main-red`) is open-packs' issue. Its two lagging fixtures are item 8 of draft (a). The
  alignment pull request in open-packs closes it.
- **The six `open-packs#TBD-*` drafts** are in `research/open-packs-drafts.md`. The orchestrator files them before
  merge and replaces each placeholder here and in the PR body.
