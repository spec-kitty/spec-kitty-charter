# WP29 hand-off (claude:sonnet:java-jenny)

Worktree `.worktrees/layer-merge-and-effective-reads-01M6R0J6-lane-{`, branch `kitty/mission-layer-merge-and-effective-reads-01M6R0J6-lane-{`.
Six commits on top of `dc218130`, all signed (`gpgsig` present), `git status` clean. T143, T144, T145, T146, T165 marked done
(`mark-status` ran without `--allow-sparse-checkout`; it only warned). Nothing pushed, no move-task, no planning-file edit.

## What was built

### IC-13 (code)
- `activations.domain.InForceFacts` (new, public functional interface): `InForceAnswer inForce(InForceQuery)`, with `NONE`
  (empty answer). `PacksSession::inForce` is the one implementation, so the write asks the same resolution the reads share
  (no second copy of the rule).
- `DocumentRules`: new overloads `check(..., facts)`, `checkWrite(before, after, snapshot, facts)`, `checkReplace(built, stored, snapshot, facts)`;
  the old signatures delegate with `InForceFacts.NONE` (used by the older unit tests). `floorRefusal(denying, stored, snapshot, facts)`:
  locked wins and never asks; a required, not locked deny entry is accepted iff the resolution of the document as it will
  be stored reports `satisfiedBy` for it (`ArtifactState.satisfiedBy`: set only for a required artifact out of force that an
  overrider in force satisfies, never an enhancer). The facts are asked once, lazily, only when a write adds a deny entry on a
  required, not locked artifact. `checkWrite` no longer computes the whole-document floor (it judged the added entries anyway),
  so a stored deny entry never asks. The document judged is `built.document()` (create), `after` (write) or the stale-dropped
  `kept` (replace), i.e. the document as stored.
- `ActivationService`: `create`/`put` keep the `PacksSession` (not only its snapshot) through `created`/`replaced`/`latest`/`judged`.
  `ActivationWrites.commit` and `ActivationExclusionService.refuseFloor` pass `session::inForce`. The deactivate's early
  refusal (before the walk) now judges `checkWrite(before, before.withDeny(target))` and keeps only floor refusals; the document
  the write computes, cascade included, is judged again at commit. `CascadeRules` unchanged.
- A merge refusal reaches only a write that adds a deny entry on a required artifact (`500 pack_unreadable`), as the scenario pins.

### Contract (T143, committed before the code, `66481b02`)
Descriptions of `createActivation`, `replaceActivation`, `deactivateActivationArtifact`; `ArtifactPolicyRefused.yaml`; the
`artifact_required` text in `CharterRefusalCode.yaml` (one two-line hunk); fragment `2026-11-07-c-deny-satisfied-by-override.md`
(`### Changed`, says #399's shipped write behaviour changes).

### Scenarios (T144) `contracts/charter/scenarios/activation-deny-satisfied.feature`, 8 scenarios, one org pack `main`
Red first (and green now): deactivate required T with an active `F overrides T` (200); replace adding override and deny (200);
unselect of the override afterwards (200, `denyNotApplied` lists T, reason `required`); create judging every deny (201, plus the
enhancer-only 409 beside it); merge refusal reaching a deny write (500 `pack_unreadable`, plus a deny on a free artifact stored 201
beside it). Labelled "regression control, green before": enhancer only is 409 `artifact_required`; locked LK with an active
override `L` is 409 `artifact_locked`; a replace with the deny and no override is 409. (The create case is one more red case than
the WP lists; the merge case is the WP's "one merge refusal".)

### Unit tests (T145) `DocumentRulesOverrideTest`, 18 cases
Per scope: create, replace (incl. adds-both and stored-entry-kept), deactivate, force preset, select with `includeDeactivated`
(adds no deny, never asks); enhancer, lock, unknown-state, stored entry, removing the override later, only-when-required, merge
refusal reaches only the required deny, query carries languages and pins.

### Docs (T146, T165)
`offering-and-activation.md` (status, tree, graph and context section: node predicate, layerOverrides, enhancements, suggestions,
404 before 304, order sources; mapping table; the 501 scenario removed), `logical-model.md`, `service-contract.md` (reserved rows
gone), `resolution-model.md`, ADR-0059 amendment R6 (write rule and R11's read rules for D2, D3; follow-up ticks), the rules page
(E6 OD-6 with the unheld-target case, E12, E14/C31 D16, E15/E16 node predicate and the OD-9 step predicate in both forms,
start-step enhancers, suggestions as a subset of the listing's, W20 note for the cascade change, W26 and the status rows to Built
(PR C); each amended rule lists the pinning tests, all verified to exist by a script), DIV-204 updated and DIV-239..244 added in the
register and in `divergences.yaml`, the ruleset trace (PR C rows filled, each naming an existing scenario, checked by a script;
marked complete), docs changelog `2026-11-07-c-graph-and-context.md` (dated text says 2026-11-09).

## DIV numbers used
Checked `main` and `origin/issue-424-428-adr-0066-service-alignment`: neither register has DIV-238 as an entry; the other mission's
plan claims it ("next free id at main: DIV-238; PR C may take it first"); I treated it as claimed, as instructed, and took 239..244.
- DIV-204 updated (the write rule, scope, merge refusal).
- DIV-239 the floor holds activatable kinds only (D16) and step references are `NodeReference` (`requiredNotInForce`, `enhancedTargetNotInForce`).
- DIV-240 kinds with no activation state absent from the effective reads and `layerOverrides`; graph nodes = Eff + held steps.
- DIV-241 step enhancer binding (OD-6) and the unheld target.
- DIV-242 the cascade follows only held steps (OD-9; W20).
- DIV-243 a step is held per pack and on the closure (A7 kept; strict per-pack; architect's dissent noted).
- DIV-244 the action context: suggestions (listing's subset), `enhancements`, URN order, `missionTypeNotInForce` is the mission type, `depth` 0/1, 404 before 304.
Not a divergence, documented as found: the smallest-URN tie of `satisfiedBy`/`overriddenBy` (rules E9, resolution-model, register prose), pointing at #439.

## Red-first evidence
- Contract fragment `66481b02`, then red `9d0e8179`, then green `7476ed97`.
- Scenarios at red (full-feature run against the lane head with only the stubs), all steps bound, no undefined or pending step:
  `IC-13 - deactivating ...` `expected: 200 but was: 409` (409 `artifact_required`, "A pack requires directive:T");
  `IC-13 - a replace that adds both ...` `expected: 200 but was: 409`; `IC-13 - unselecting the override afterwards ...`
  `expected: 200 but was: 409`; `IC-13 - a create judges every deny entry ...` `expected: 201 but was: 409`;
  `IC-13 - a merge refusal reaches a write ...` `expected: 500 but was: 409`. The three controls and the replace-without-override
  control passed at red and pass now (the run shows 573 tests, 6 failed = the five scenarios plus the R-20 coverage test, which the
  name filter I used made fail; not a real failure). Log: `/tmp/claude-0/wp29-red-suite.log`.
- `DocumentRulesOverrideTest` at red (stub overloads ignore the facts): 18 cases, 9 failed on assertions (e.g. "Expecting an empty
  Optional but was containing value: CharterRefusal: A pack requires directive:T, so the activation cannot deny it."; "Expecting
  code to raise a throwable" for the merge refusal), 9 pin unchanged behaviour and pass (lock, invalid ids, no deny, stored entry...).
- Green: the five scenarios and the controls pass in the full `CharterContractSuite`; the 18 unit cases pass. One assertion of my own
  first scenario (the in-force directive list) omitted the locked LK and the required T2; I fixed the scenario in the green commit
  (a wrong expectation of mine, not a weakened check; F stays the control).

## Gates (all green)
- `:contracts:check :activations:check` BUILD SUCCESSFUL (Checkstyle, PMD, SpotBugs, spotless included).
- `:deployable:check`: 1209 tests, 2 failed only because the build copy has no git checkout (`ShippedConfigurationTest`); rerun
  after `git init` of the copy: BUILD SUCCESSFUL. `HexagonalArchitectureTest` and the whole contract suite incl. R-20 coverage green.
- `:packs:test --tests '*DivergenceRegisterTest'` green (the page and the yaml list the same ids).
- Docs: `python3 scripts/docs/check_docs.py all` all ok (frontmatter, related, freshness, adr, changelog, terms, orphans, links);
  `npx markdownlint-cli2@0.18.1` 0 errors; `npx cspell@10.4.0 "docs/**/*.md"` 0 issues (no dictionary additions; I used "behavior").
- No suppression, no `@Disabled`, no `@WIP`, no assertion weakened.

## Deviations and things to know
1. **Build path.** The lane directory name contains `{`. `:charter-api:validateContract` (openapi-generator) fails on it with
   "Illegal character in path at index 87", so no Gradle task that compiles the API (`:activations:*`, `:deployable:*`) can run in
   the lane. Gradle canonicalizes symlinks, so I built from a copy at `/tmp/claude-0/wp29-build` (tar of the working tree without
   `.git`, `.kittify` except `charter.yaml`, `kitty-specs`; `git init` there for the two git-reading tests), synced by
   `/tmp/claude-0/wp29-sync.sh`, with `spotlessApply` run there (one reflow of `DocumentRules.java` was copied back). Commits and
   file edits are all in the lane. `:contracts:check` also ran once in the lane directly. The orchestrator may want to rename the
   lane or fix the build for `{` before the PR C gate.
2. The deactivate's early refusal is `checkWrite(before, before.withDeny(t))`, an approximation of the stored document; the
   commit-time `checkWrite(before, after)` is definitive (only a contrived non-monotone cascade could differ).
3. Out-of-list docs edits (stale 501 text): `docs/api/README.md`, `docs/development/http-status-codes.md`.
4. The ADR-0059 amendment is headed "Amendment 2026-11-09 (R6)" (anchor `#amendment-2026-11-09-r6`); the research names it R6 (2026-11-06).
5. OD-6 and OD-9 have no scenario (no pack file can author a step enhancement before #424), so the trace names no row for them; a
   note under the new "Rulings of 2026-11-09" table says they are pinned by unit tests, which the rules page names.
6. The 404-before-304 has a scenario for the 404 but not for the matching tag (the tag of an unknown name cannot be built from a
   scenario); `EffectiveReadServiceTest.anUnknownNameIsRefusedBeforeTheTagIsComparedWhateverIfNoneMatchSays` pins it (WP28's finding).

## Out-of-map edits (each in its commit message)
`DocumentRules`, `ActivationWrites`, `ActivationService`, `ActivationExclusionService` (WP04); the three path yaml files (WP01);
`CharterRefusalCode.yaml` (WP11); ADR-0059, divergence register and yaml, ruleset trace, rules page, ADR-0063 (WP09);
`offering-and-activation.md`, `resolution-model.md` (WP26); `docs/api/service-contract.md`, `docs/architecture/logical-model.md`
(owned/listed), `docs/api/README.md` and `docs/development/http-status-codes.md` (stale text, not listed).

## Order sweep: sentences checked (each now cites its ruling or the refusal; edits marked +)
**composition-and-activation-rules.md.** Banner paragraph "Explicit rulings, never accidental order" (+); C5 tie by `lv` then `cid`
in code points (+ ADR-0048 d9, I1); C14 sibling conflict (ADR-0048 d9, A14); C15 settlement by exactly one winner (+ the #427 landing: two
different winners refused); C16 identity per level; C17 layer winner (+ no tie reaches the winner: C14/C15, ADR-0059 decision 3);
C18 whole replacement; C20; C21 override gate; C22 strictest level and `setBy` first pack in closure order (+ order only names the pack);
C27 and C28 overrides and cycle refusal; C29 winning preset, last in closure order (+ the child's copy wins, decision
`01M6ZCXAJ9MTTF9XJSR89F3NYG`); C31 within-level copy replaces the others (+ same decision); C34 same-level preset conflict;
W6 "locked is judged first" (+ ADR-0012 d7); W7 and W8 precondition order and last-writer-wins (ADR-0020 D6); G17 last-writer-wins
(ADR-0026 D5); W17 winning preset; E4 hiding chain; E5 requirement; E9 first-row precedence, `not_selected` before `overridden`
(research R11 step 3) and the smallest-URN tie of `satisfiedBy`/`overriddenBy` (+ documented as found, #439); E11 paging order;
E15 sort orders (+); E16 entries in URN order (+ ADR-0018 D3); I1.
**resolution-model.md.** Merge step 2 "later-declared pack wins" (+ core's rule vs the service's refusal, ADR-0048 d9, C14, C15);
"level that applies takes the place whole" (+ ADR-0053 d6, ADR-0059 decision 3); the sibling note; walk "first step that reaches it"
(+ ADR-0018 D3); layer-merge items 1 to 4 (+ ADR-0048 d9, ADR-0053 d5, the #427 two-winner refusal, decision `01M6ZCXAJ9...`);
"a winning mission type does not take the unique steps of its losers" (+ A7, `01M7037V93...`, dissent noted, replaces "PR C confirms
this"); What is in force strata 2, 3, 4 (+ A3, Q6/Q8, A8, OD-6); the precedence paragraph (+ R11 step 3, the smallest-URN tie and #439);
the node-predicate paragraph (+); the Writes sentence for W26 (+).
**offering-and-activation.md.** Presets "the last in merge order wins" (+ C29, A1 decision, C34); `requiredBy`/`referencedBy` "first
in code-point order" (+ activation mission finding F3, re-review R2); the new suggestion "nearest edge" (ADR-0018 D3 tie rule) and the
URN entry order (ADR-0018 D3); the deactivate refusal (+ W26).
**logical-model.md.** "local wins, visibly" (+ ADR-0033 decision 3); Identity "except where an `overrides` edge settles it" (+ ADR-0053
d6, C14, C15); layer override "the level that applies wins whole" (+ ADR-0059 decision 3); Sibling conflict "no last loaded wins" (+
ADR-0048 d9, A14, C15, and the stale "until #357" text corrected); profile selection "Tie handling is an open question" (+ ADR-0032
rules none); the stale "`enhances` both ways" pull sentence corrected (ADR-0059 A4).
**ADR-0063.** D3 "the highest level wins" and "the first pack, in closure order, that declared that level" (+ order only, the level is the
maximum, ADR-0048 d9, C5, C22).
**ADR-0059.** Amendment R6 cites each ruling it records.
