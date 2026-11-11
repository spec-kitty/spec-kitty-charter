# Issue triage: spec-kitty/spec-kitty-charter, 2026-11-11 (Phase 1, read-only)

Op `01M75XTFKEJG0AGWB78QZMCC12` (planner-priti), branch `op/issue-triage-2026-11-11`. Baseline: `origin/main` (spec-kitty/spec-kitty-charter itself, current). Five read-only classifiers, about 38 issues each. Per-issue JSON: `scratchpad/class_{A..E}.jsonl`. No issue was edited.

## Headline

- **191 open issues.** About 85 (44%) are recommended for closing or moving out: 21 already delivered, 14 overtaken by decisions, 2 duplicates (plus 3 to fold), 20 that are core work, 2 that are pack content, and 8 squad-deferral noise. Another 46 are stale and need a priority call or a won't-do.
- **Why the count rises:**
  1. **No closing pass after delivery.** 21 delivered issues are still open. PRs say `Refs`, missions deliver several issues at once (PR#398, #427, #448, #474), and epics are not ticked off.
  2. **Core work is tracked here.** 20 issues are core work (#332–#343, #361, #73/#74/#76, …). charter.md (2026-10-28 and 2026-11-10) now says core work is planned at incorporation.
  3. **Squad and mission deferrals become issues.** 64 of 191 (34%) were filed from a mission closeout, a landing review or a squad deferral. The boy-scout tactic `how-we-apply-directive-025` says "anything else is filed as an issue and deferred", and `charter.yaml` repeats it. The landing skill says squads "routinely file MINOR findings as separate issues".
  4. **Breakdowns filed far ahead of scheduling.** 96 issues are human-planned breakdowns. 27 of those are stale and 9 overtaken: the Creeds & Values 1/7–7/7 children, the cutover reads R4–R16, and the #143 verifiability children, all untouched since 10-14 to 10-22.
  5. **Re-scopes decided but never applied.** The 2026-11-02 triage drafted re-scopes for about 66 issues (`docs/plans/triage-2026-11-02-rescopes.md`). Almost none reached the issue bodies, so those issues read as stale and get re-triaged again.
- **The PR#481 race is already tracked as #490.** `RegistrationConcurrencyTest.aReplaceRacingPutsWithIfMatchLeavesOneLinearETagChain`: `putUntilCommitted` reads `currentRevision` outside the lock (RegistrationConcurrencyTest.java:337), lands between the two renames, and gets `NoSuchFileException`, answered as 500 `pack_unreadable` (run 43071438389). Nothing new needs filing. It is the residue of #185 item 1. Proposal: close #185 as completed (items 2–3 by PR#302 and PR#419 `StagingSweeper`, item 1 for listing repair by PR#302), keep #490 as the canonical issue, and raise it to P2.

## Counts per class

| Class | n | |
|---|---|---|
| DONE | 21 | 15 confirmed, 6 plausible |
| OBSOLETE | 14 | |
| DUPLICATE | 2 | plus #469→#409 and #166→#197 to fold; #182 is done |
| MOVE-OTHER (core) | 20 | |
| MOVE-OPENPACKS | 2 | |
| PROCESS-NOISE | 8 | |
| STALE | 46 | |
| KEEP | 78 | about 60 need hygiene fixes |

Source of the 191 open issues: human-planned 96, mission follow-up 46, squad deferral 18, usability run 9, CI/Sonar 3, other 19.

## Trend (opened / closed per week)

| Week | Opened | Closed (completed/dup/not_planned) | Net open |
|---|---|---|---|
| 10-12 | 30 | 0 | 31 |
| 10-19 | 118 | 20 (15/4/1) | 129 |
| 10-26 | 83 | 45 (45/0/0) | 167 |
| 11-02 | 54 | 57 (47/6/4) | 164 |
| 11-09 | 45 | 18 (17/0/1) | 191 |

- The 11-02 week was the only one that closed more than it opened, and that was the week a triage pass ran.
- Excluding triage weeks, intake is about 45–55 a week and closes are about 18–45.
- Median time to close a closed issue is 4 days. What does get closed is closed by its own PR. Nothing closes the others.
- Mission and squad follow-ups were filed at 4, 44, 33, 17 and 17 a week, and about 61% are still open.
- The 2026-11-09 usability run filed 9 issues, and all 9 are still open.

## DONE: close as completed

| Issue | Evidence | Action |
|---|---|---|
| #57 | ADR-0063, `policy.schema.json`, `PolicyYaml.java:31`, PR#398 | close completed |
| #60 | `EffectiveSet.java:69` LOCKED, `DocumentRules.java:45`, PR#427 | close completed |
| #67 | `ActivationExclusionService.java:35,66` artifact_locked/required, PR#398 and #399 | close completed |
| #56 (epic) | children #65 and #67 delivered; 4/6 closed already | close completed |
| #140 | PR#141 meta-rule plus PR#398 `SLICE_RINGS` (`HexagonalArchitectureTest.java:641,921`) | close completed |
| #181 | PR#398 `DomainRepositoryService.java:646`, 409 `governance_policy_violation` | close completed |
| #182 | PR#403 references read, PR#448 effective graph | close completed |
| #185 | PR#302 (items 1 and 3), PR#419 `StagingSweeper` (item 2); residue tracked in #490 | close completed, link #490 |
| #357 | PR#427 and PR#448, `layer-merge.feature:348` | close completed; stale text in `PackOverride.java:8` |
| #359, #360 | PR#398 closure read `listActivationPacks`, `composition.feature:1038` | close completed |
| #408 | PR#414 depth; PR#427 and #448 languageFiltered/suggests | close completed |
| #429 | PR#448 `EnhancerWithoutTarget.yaml:32`, DIV-239 | close completed |
| #457, #458 | PR#474, ADR-0068 and ADR-0069 | close completed |
| #47 (plausible) | PR#436 `XSourceCitationsTest.java:44` (path half; symbol half not built) | close completed |
| #58 (plausible) | `PackMutabilityPolicy.java:33-57` | close completed |
| #65 (plausible) | effective reads mark deactivated (`effective-reads.feature:95`) | close completed |
| #71 (plausible) | PR#297 native builds and `service-v*` workflow; no release cut yet | close or hold to the first tag |
| #101 (plausible) | ADR-0044 plus PR#472 `expectedOutputArtifacts` | close completed |
| #412 (plausible) | PR#419 impact check `CommitStorePackWriteStore.java:72` | re-run the repro first |

## OBSOLETE: close as not_planned

| Issue | Evidence |
|---|---|
| #29, #30, #31, #33 | behaviour-first epic: ADR-0008 and ADR-0055 D3; PR#384 reduced `tests/scenarios/charter` to pointers |
| #38 | charter.md 2026-11-05 (op#118) and ADR-0070 |
| #59 | core CLI; the service refuses at registration; offline validation is in #478 |
| #77, #79, #80 | reversed by ADR-0028 and ADR-0045. Side finding: `IMPACTS` is still in `Relation.java` |
| #132 | ADR-0036 canonical YAML form |
| #149 | ADR-0070 D3 and op#188 |
| #203 | PR#364: packs are synced, not vendored |
| #435 | ADR-0070: example packs already carry spdd enhancements |
| #471 (plausible) | core compatibility deferred to incorporation (or park in Post-cutover per milestones.md:143) |

## DUPLICATE / fold

| Issue | Canonical | Why |
|---|---|---|
| #424 | #438 | phase 1 shipped (PR#460); #438 "Closes #424" |
| #469 | #409 | same missing activation link relations; root link done (`InfoController.java:63`) |
| #166 | #197 | one CI path-taxonomy change |
| #490 / #185 | #490 | see headline |
| #489 Procedure.yaml bounds item | #443 | id length caps |

## MOVE-OTHER: core (spec-kitty/spec-kitty)

| Issue | Evidence | Action |
|---|---|---|
| #332–#342 (C1–C11), epic #361 | bodies say "code change lands in core"; charter.md: core work planned at incorporation; design record in `pack-ecosystem-work-breakdown.md:117`; core already tracks some (spec-kitty#5236/#5238 ↔ #333, #3420/#2535 ↔ #339, #2599 ↔ #340) | close not_planned with a forward note; refile in core at incorporation |
| #343 | core review prompts; wrongly under cutover epic #69 | same |
| #74, #76 | core-only strangler stages; bodies contradict themselves | keep #69 as the charter-side record; close these |
| #73 | service half done (fingerprint header, `stale_input`); the rest is core's seam | move caching to #416; close |
| #151 | core's model-task routing catalog | close not_planned (with #108 R14 "out of the cutover") |
| #243 | defect 1 is spec-kitty#6768; defects 2 and 3 not yet filed in core | operator files 2 and 3 in core, then close |
| #452, #485 | core synthesizer and CLI slug spelling (slice E) | keep under #13 as slice E pointers, or route to core |

## MOVE-OPENPACKS

| Issue | Evidence | Note |
|---|---|---|
| #160 | the policy schema is done; what remains is authoring `policy.yaml` per member pack (op#198 split) | no op# covers it |
| #453 | java-supply-chain says `jvm`, java-conventions says `java`; the fix is in pack content | no op# covers it |

Checked and not misplaced: #476, #477 and #479 are service or CI issues on charter's own example packs (ADR-0070). #478 (pack-check CLI) and #480 stay in charter. None of the charter issues matches op#182 or op#194–#197. This session can read open-packs but cannot write to it, so the operator or a session with access has to file there.

## PROCESS-NOISE: close not_planned or fold

| Issue | Note |
|---|---|
| #36 | item 1 delivered by op#47; item 2 is retired concepts |
| #49, #50, #52 | below-MEDIUM squad grab-bags from 10-19, untouched; carry #50 item 1 into #68 |
| #388, #389 (+#387) | #239 squad deferrals; fold into one P3 mutation-CI task or drop |
| #426 | 11-item PR B squad list; split out one real risk (unbounded `activationsAffected` under RegistryWriteLock → 503), close the rest |
| #489 | #484 squad minors; fold into PR#486 or close; bounds item goes to #443 |

## STALE: needs an operator call (46)

- **Creeds & Values #16–#23, #82, #152:** Post-cutover, untouched since 10-14. Apply the drafted re-scope or park as one epic. #152 is a won't-do candidate.
- **Cutover reads R4–R16 (#98, #104–#110), #112:** each needs a recorded decision. #108 is likely "out of the cutover". #104 may be done by PR#472's skeletons.
- **Verifiability epic #143, #157–#159, #40:** Backlog. #159 is a won't-do candidate.
- **#14, #15:** re-scope or lower.
- **#42:** retitle per ADR-0068 and ADR-0069.
- **#61, #69:** retitle.
- **#154, #163, #164:** re-scope; P1 in Post-cutover is a mismatch.
- **#184, #205, #225, #254:** P3 or close.
- **#308, #324:** small bugs.
- **#349, #387, #432–#434, #439, #447:** typeless, need priority or ruling.

## KEEP: hygiene (78)

- **Native type missing on about 52 issues**, almost all filed since 11-05 (#367, #371, #380–#489). The repo carries both native types and legacy `type:task`, `type:epic`, `enhancement` and `bug` labels, and they disagree: #134 is a Bug labelled `type:task`; #308 and #324 are Bugs without `bug`.
- **No priority** on about 15 issues, and **no milestone** on #469 and #476–#489.
- **Slice epics #466, #467, #468 have no native sub-issues.**
  - Add to #466: #451, #431, #432.
  - Add to #467: #438, #450, #97.
  - Add to #468: #196, #45, #454, #312, #367.
- **Children of closed parents:** #10, #11, #145 and #291 are closed. That leaves #357–#360, #358, #424, #429, #438 and #439 orphaned.
- **#128** has no parent; it should be #6.
- **Body fixes:**
  - #127 and #128 link ADRs on a deleted branch.
  - #381 says to change core's schema first, which is against the canonical-model rule.
  - #178 should drop aliasing.
  - #220 still asks for jars, against the sources-only zip ruling.
- **#463:** the code is done (PR#481). The maintainer marks the 5 false positives in SonarCloud, then closes it. S9428 is listed in both #463 and #465.
- **#461 (mutation nightly):** red on 11-10 and 11-11 because ActivationWireTokenParityTest and then PackFileSetExactnessTest fail unmutated under PIT. It is not a floor drop.

## Structural recommendations

1. **Squad minors: fold or drop, never file.** A squad finding below MEDIUM is either folded into the PR under review or recorded in the PR body's "Not done" list. A separate issue is filed only when it is (a) a correctness or security risk, or (b) explicitly accepted by the operator. Amend `how-we-apply-directive-025` / `charter.yaml` so that "items outside the domain are filed as issues" applies only to defects, not to tidy-ups.
2. **A closing pass in every mission closeout and landing.** Before the closeout PR merges:
   - list every issue the mission touched or the PR references;
   - close each delivered one with evidence (prefer `Closes #` over `Refs #` when fully delivered);
   - tick the parent epic;
   - delete any follow-up that the merged work answered.

   Add this as a checklist step to the closeout and landing skills and to `docs/development`.
3. **Mark auto-filed follow-ups and give them an expiry.** Add a `follow-up` label (mission or squad origin), filed P3 with no milestone. After 21 days with no milestone or assignee, a triage pass closes it as not_planned. A nightly or weekly job lists the expired ones.
4. **Pack content goes to open-packs by default.** An issue whose fix is in pack YAML or doctrine wording is filed in spec-kitty-open-packs (or as an `open-packs#TBD-<slug>` draft in the mission research directory, per charter.md). The charter issue stays only if the service or contract must change.
5. **No core-work issues here until incorporation.** Keep core consequences in `docs/plans/pack-ecosystem-work-breakdown.md` (design record), and file core issues in spec-kitty/spec-kitty when incorporation is planned. This removes 15–20 issues now and stops that intake.
6. **Break down epics just in time.** File children when the epic is scheduled into a slice or milestone. Until then, keep the breakdown as a checklist in the epic body or the plan doc.
7. **Apply a re-scope when it is decided.** A triage that drafts a re-scope edits the issue (or closes it). It does not leave the re-scope in a plan document only.
8. **One kind carrier.** Use native issue types only, and retire the `type:task`, `type:epic`, `enhancement` and `bug` labels so type is set once and cannot disagree.
9. **Weekly flow check.** A light weekly triage of new and done issues (or the spk-report-debrief milestone snapshot) keeps closes in line with intake. The only net-negative week in the data was a triage week.
