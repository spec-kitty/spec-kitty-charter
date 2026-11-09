# WP29 review, cycle 2 (fix cycle 1) (claude:opus:reviewer-renata)

Mission `layer-merge-and-effective-reads-01M6R0J6`, lane-{ head `b3ca8b44`. The fix is seven commits on `888412b4`:

- `b3ad259c`: red scenarios
- `63b9d47c`: the fix and a service unit test
- `3e47ad95` and `3ad4d7f2`: the force-preset service test
- `9f3b2a0a`: the overloads deleted
- `14252849`: fragment and rewrap
- `64f35d40`: scenario assertion swap and the `out-of-map:` lines

The other commits in `888412b4..b3ca8b44` are planning merges and status commits. `git diff 888412b4 b3ca8b44 -- . ':!kitty-specs'`
touches only the 11 files of those seven commits. I re-checked the whole WP29 delta (`git diff dc218130 b3ca8b44`,
30 files) where the fix touches it.

## Verdict: APPROVE

MAJOR-1 is fixed:
- The deactivate now judges the named target whether or not its deny entry is already stored.
- Lock is judged first and the facts are not asked for it. Requirement is judged next, through the shared in-force resolution.
- IC-13 still holds on both a first and a repeated deactivate.
- The new scenarios and the new unit test fail at `888412b4` on an assertion and pass at head. I ran this myself.
- Six extra probes found no other path where a stored entry escapes being judged, outside the scope the ruling sets:
  the cascade body, a case-variant stored spelling, a URN path spelling, and stored entries beside the target.

There is no BLOCKER and no MAJOR. There are 2 MINOR findings and 4 NIT findings. None blocks approval:
- **MINOR-5:** after the fix, the docs still describe the deactivate's floor scope inaccurately.
- **MINOR-6:** the new force-preset service test is not IC-13 evidence.

## What I applied

- **Profile.** `spec-kitty agent profile show reviewer-renata`: review, flag, assess, and no fixes. Directives 001, 024,
  030, 032, 041 and 051. Tactics code-review-incremental, delete-the-assertion-not-the-test and
  test-scaffolding-as-design-smell.
- **Charter context.** `spec-kitty charter context --action review`:
  - It gave DIRECTIVE_010, 034 and 037, QA_003_NO_FAKE_GREEN, NO_FULL_HEAVY_SUITES_IN_MISSION and
    RECONCILE_CHANGE_SCOPE_TENSIONS.
  - The `internal` and `qa` pack paths are missing. This is the known environment gap.
- **Sources I checked against:**
  - the CLI review prompt (`wp29-review-prompt-c2.md`);
  - the WP file;
  - plan IC-13;
  - the cycle-1 review with the runner's dispositions;
  - the fix hand-off and the original hand-off;
  - decision `DM-01M70JFXZHY2NKA6Y6RQ66AYA9` (the fragment stays `### Changed`);
  - the rules page W6, W25 and W26;
  - ADR-0059 R6;
  - DIV-204.

## Check 1: MAJOR-1 (the deactivate's early floor refusal)

| Item | Result |
|---|---|
| The fix | OK. `ActivationExclusionService.refuseFloor` (`:157-167`) is now `checkWrite(before.withoutDeny(t), before.withDeny(t), snapshot, session::inForce)`, still filtered to floor refusals only. `addedDenies` therefore always holds the target, and `floorRefusal` judges it. |
| Lock first, without asking the facts | OK. `DocumentRules.floorRefusal` (`:377-399`) returns `artifact_locked` before `unsatisfied` is called. `unsatisfied` is the only place the facts are asked. |
| IC-13 kept on a first deactivate | OK. Scenario `:130` "deactivating a required T with an active override of it is 200" passes at head. |
| IC-13 kept on a repeated deactivate | OK. Probe C: deactivate `T` with `F` selected, then deactivate `T` again, gives 200 and 200, and the deny list is exactly `[T]`. The second call judges `T` as added. The facts report `satisfiedBy=F`, so the call is accepted. The commit then returns early, because the document is unchanged. |
| Red first: the new scenarios | Confirmed, with the head feature and head unit test copied onto a temporary worktree at `888412b4`. "deactivating a locked LK whose deny entry is already stored is 409 artifact_locked" FAILS: status 200, with `applied:[]`. "deactivating T again after the override was unselected is 409 artifact_required" FAILS: status 200. The other 8 scenarios pass. No step is undefined. |
| Red first: the unit test | Confirmed. At `888412b4`, `ActivationExclusionServiceTest.aTargetOnTheFloorWhoseDenyEntryIsAlreadyStoredIsRefusedAgainAndNothingIsStored` fails with `Expecting code to raise a throwable` (22 tests, 1 failed). |
| Green at head | 10/10 scenarios in `activation-deny-satisfied.feature`. The unit test passes. `:activations:test` gives 806 tests, 0 failed. |
| Other escape paths (probe `zz-probe2.feature`, all at head) | Head results below. Probes D and F answer 200 at `888412b4`, which is the MAJOR-1 bug, so they also fail red there. |

The other escape-path probes, all run at head:

| Probe | Request | Result at head |
|---|---|---|
| D | Stored deny on `LK`, then deactivate `LK` with `{"cascade":["all"]}` | **409** `artifact_locked` |
| E | Stored deny in a case variant (`"lk"`), then deactivate `LK` | **409**. `StaleIds.drop` removes the spelling the closure does not declare, so `LK` is added. |
| F | Stored `LK`, path spelled `directive:lk` | **409**. `ActivationWrites.declared` maps the path to the declared id before `withoutDeny`. |
| G | Stored `T` and the unsatisfied `T2`, `F` selected; re-deactivate `T` | 200: the other stored entry is tolerated. Deactivate `T2`: **409** `artifact_required`. |
| H | Stored `LK` and `T`, `F` selected; re-deactivate `T` | 200: the stored `LK` entry is tolerated, as W25 says. |

The other writes, judged against the ruled scope:
- **Create** judges every deny entry: `check`, `floorRefusal(document, document)`.
- **Replace** judges only the entries it adds, on purpose (A4, GET then PUT is never refused).
- **A preset (force or not) and a select with `includeDeactivated`** never add a deny entry. `withDeny` is called only
  from `CascadeRules.applyDeactivate` (`:308`) and from the deactivate itself. They therefore never judge the floor,
  which is the ruled tolerance (W25, ADR-0059 D4).

I found no path outside DIV-204's scope where a stored entry escapes being judged.

## Check 2: MINOR-1 to MINOR-4 and the NITs from cycle 1

| Item | Result |
|---|---|
| MINOR-1 fragment | OK. It still has one `### Changed` block, as the decision rules. The added lines (`:17-18`) say that a documented `409 artifact_required` now succeeds with `200`/`201` when an active override satisfies the requirement, and that core's in-process backend still refuses it (DIV-204). |
| MINOR-2 out-of-map lines | OK. `64f35d40` carries `out-of-map:` lines for `InForceFacts.java` and `docs/api/service-contract.md`, and the fix hand-off has an "Out-of-map corrections" list. See NIT-3 for the test files the fix touched. |
| MINOR-3: the deleted overloads | OK. The four overloads without facts are gone (`9f3b2a0a`). The only lines removed from main code are those overloads and their Javadoc, with one `{@link}` retargeted. `grep` over `service/*/src` finds no call to a two- or three-argument `check`, `checkWrite` or `checkReplace` without facts, and every module compiles at head. |
| MINOR-3: no assertion weakened | OK, checked mechanically. For `DocumentRulesTest` and `PacksAvailabilityTest` I stripped the imports and whitespace from the old and new bodies and removed `,InForceFacts.NONE` from the new body. The two strings are byte-identical. `@Test` counts are unchanged (32/32 and 22/22), and `NONE` is passed 27 and 3 times. |
| MINOR-4 | Partly. The deviation is factually correct: `PresetRules.apply` only removes the members' deny entries under force and never adds one, so the runner's "the preset's deny of T is accepted" cannot happen. The substitute test is meaningful only as a tolerance regression, not as IC-13 evidence. See MINOR-6. |
| NIT: YAML rewrap | OK. With whitespace collapsed, all three path files have the same words before and after `14252849`. The longest added line is 118 columns. |
| NIT: changelog name and "behaviour" | Recorded, as ruled. cspell flags "behaviour" in the fragment, which is outside the `docs/**` CI scope. |

## Check 3: the scenario change in `64f35d40`

The scenario "deactivating a locked LK whose deny entry is already stored is 409 artifact_locked" originally ended with
`the field at "/revision" is the stored revision`. That step needs an activation registered through the API, and this
one is seeded as a stored file. The commit swaps it for "`/config/deactivated/directive` holds exactly `LK`".

- **Not a weakening of the claim.** The `409` and `artifact_locked` assertions and `requiredBy/0/packId` are untouched,
  and the scenario failed red on its status line before it reached the swapped step.
- **A mild weakening of the side check "nothing changes".** A deny list that still holds `LK` would also hold after a
  stored write that kept `LK`. The unit test pins that nothing is stored (`repository.replaces()` is zero and the
  revision is unchanged), so the claim is covered. A stronger step exists for the scenario, though (NIT-1).

## Check 4: the whole WP29 delta (`dc218130..b3ca8b44`)

- The cycle-1 checks still hold. `InForceFacts` has one production implementation (`PacksSession::inForce`), and
  `CascadeRules` is untouched. The merge refusal reaches only a write that adds a required deny (scenario 10 is green).
  The contract text, rules page, ADR-0059 R6, DIV-204, DIV-239..244 and the trace are unchanged by the fix, apart
  from the fragment and the rewrap.
- **Files outside `owned_files` touched by the fix:**
  - `ActivationExclusionService.java` is already recorded in a cycle-1 `out-of-map` line.
  - The four test files are recorded only in the hand-off (NIT-3).

## Findings

- **[MINOR] MINOR-5: after the fix, the docs misstate the deactivate's floor scope.**
  - **Where the text says it.** The sentence "a replace, a deactivate, a preset applied with `force` and a select with
    `includeDeactivated` judge only the deny entries they add" appears in:
    - `docs/architecture/composition-and-activation-rules.md:727-728` (W26), with ":731 no write judges a stored deny entry";
    - `docs/adr/0059-switch-gate-cascade-edges-and-activation-rulings.md:413-417` ("a write judges only what it adds");
    - `docs/conformance/divergence-register.md:500` and `divergences.yaml:639` (DIV-204);
    - `contracts/charter/changes/2026-11-07-c-deny-satisfied-by-override.md:11-12`.
  - **What the code does.** The deactivate now judges its named target even when the entry is stored. W25 (`:712-714`)
    and the deactivate's own contract sentence ("A named artifact a pack locks is 409 artifact_locked ...") agree with
    the code. Concrete case: deactivate `T` with `F` selected (200), unselect `F` (200), deactivate `T` again
    (**409**). W26:731 reads as if that third write could not be refused.
  - **Writes that add no deny entry.**
    - The fragment (`:5`) lists "apply-preset with `force`, select with `includeDeactivated`" as "the other writes that
      add a deny entry", which "answered `409` ... and now answer `200`".
    - Neither write can add a deny entry: `PresetRules.apply` and `CascadeRules.applySelect` never call `withDeny`.
    - The fix hand-off itself records this for the preset.
  - **Contract text.** `activations_activationId_exclusions_kind_artifactId.yaml:24-26` ("only the deny entry the write
    adds is judged against that floor, so a stored one a pack upgrade put on it is tolerated") was already ambiguous at
    `dc218130`, and pre-PR C behaviour matches the fix. WP29 wrote the W26, R6, DIV-204 and fragment text, however.
  - **Why it does not block.** The behaviour matches the contract's explicit sentence, W25 and the cycle-1 disposition,
    and it is pinned by two scenarios and one unit test.
  - **Suggested fix.** One clause in each of those places, for example: "a deactivate always judges its named target,
    stored or not (W25); a stored entry on any other id is tolerated". Also drop the claim that a preset or select adds
    deny entries, or say that it is vacuous for them. This can go in a follow-up or at the PR C docs pass.
- **[MINOR] MINOR-6: the force-preset service test is not IC-13 evidence.**
  `ActivationPresetServiceTest.java:121-135` (`forceKeepsTheDenyEntryOfARequiredArtifactAnOverrideInTheClosureSatisfiesAndStoresIt`).
  - **The comment is not true of the fixture.** It says "F, a member of the preset, overrides it". The fixture
    (`:39-47`) holds `F` and `T` and has `T` required, but it has no `overrides` edge. The test never configures
    `packs.resolves(...)`, so the facts never report `satisfiedBy`.
  - **It survives IC-13 being broken.** I mutated `DocumentRules.satisfied` to always return `false` in a temporary
    worktree at head. That broke IC-13: 7 of the 18 `DocumentRulesOverrideTest` cases failed. This test still passed.
  - **What it pins.** That a force preset keeps a stored entry on a required artifact, which is W25 and D4 tolerance and
    already held before WP29. It is a useful regression, but its name and comment overclaim.
  - **Related.** `DocumentRulesOverrideTest.aPresetAppliedWithForce...` and `aSelectWithIncludeDeactivated...` feed
    `checkWrite` documents that those writes cannot produce.
  - **Suggested fix.** Rename the test and fix its comment, for example
    `forceKeepsAStoredDenyEntryOfARequiredArtifactAndAddsNone`. State in the hand-off that the force-preset and
    `includeDeactivated` scopes are vacuous for IC-13, because neither write adds a deny entry. MINOR-5 has the doc side.
- **[NIT] NIT-1:** `contracts/charter/scenarios/activation-deny-satisfied.feature:339-343`. The swapped assertion could
  instead use the existing steps `I remember the stored file of the activation "a-1"` and
  `the stored file of the activation "a-1" is the remembered one` (`ActivationSteps.java:96,101`). That pins "nothing
  changes" byte for byte, as the scenario's description says.
- **[NIT] NIT-2:** `DocumentRules.check(ActivationDocument, ClosureSnapshot, InForceFacts)` (`:88`) and
  `InForceFacts.NONE` (`InForceFacts.java:18`) are now used only by tests. Production calls the `Built` overload
  (`ActivationService.java:307`). This is harmless: both require facts explicitly, so the cycle-1 risk is gone.
- **[NIT] NIT-3:** the fix commits `9f3b2a0a`, `63b9d47c` and `3e47ad95` edit `DocumentRulesTest`,
  `PacksAvailabilityTest`, `ActivationExclusionServiceTest` and `ActivationPresetServiceTest`, which belong to other
  WPs, without an `out-of-map:` line. The runner's dispositions directed these edits, and the fix hand-off names them.
  Record only.
- **[NIT] NIT-4 (carried):** "behaviour" in the fragment (outside the cspell scope), and the changelog file is named
  2026-11-07 but dated 2026-11-09. Both are recorded as ruled.
- **Observation (not WP29):** a stored file seeded in non-canonical JSON (spaces, unsorted keys) makes `GET` answer
  `500 internal_error`. I hit it while writing probes G and H and rewrote them in canonical form. This is pre-existing
  harness or store behaviour, outside this WP.

## Anti-pattern checklist

| # | Item | Result |
|---|---|---|
| 1 | Dead code | PASS. The test-only overloads are deleted. `InForceFacts.NONE` and the `ActivationDocument` overload of `check` are test-only, but both require facts explicitly (NIT-2). |
| 2 | Synthetic-fixture test | PASS for the FRs. The scenarios run the real resolution, and breaking `satisfied` turns the IC-13 units red. MINOR-6 is a misleading name and comment, not a synthetic fixture standing in for an FR. |
| 3 | Silent empty return | PASS. Nothing new. |
| 4 | FR coverage (FR-013, FR-014, FR-016) | PASS. Unchanged from cycle 1, plus the two new floor scenarios. |
| 5 | Frozen surface | PASS. `CascadeRules` is untouched, and no existing feature other than WP29's own is modified. |
| 6 | Locked decision | PASS, where cycle 1 FAILED. "Locked `T` stays `409 artifact_locked`" now holds on a re-deactivate of a stored entry (scenario, unit test, probes D/E/F). |
| 7 | Shared-file ownership | PASS with a note: four test files are recorded only in the hand-off (NIT-3). |
| 8 | Production fragility | PASS. No new `raise`. The refusal is the documented `CharterRefusal`. |

## Commands run

All Gradle runs used `JAVA_HOME=/usr/lib/jvm/temurin-25`, ran in the foreground one at a time, and passed
`--max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PopenPacksDir=/home/user/open-packs -PcontractBaseline=none`.

| Where | Command | Result |
|---|---|---|
| `/home/user/wp29-review` (`b3ca8b44`) | `:charter-api:validateContract :contracts:check :activations:check` | BUILD SUCCESSFUL. Activations 806 tests, 0 failed, 0 skipped |
| same | `:deployable:test --tests '*CharterContractSuite*' --tests '*HexagonalArchitectureTest*' :packs:test --tests '*DivergenceRegisterTest*' --rerun` | BUILD SUCCESSFUL. Deployable 635/0 (the whole contract suite with R-20 coverage, and the architecture test). Packs 3/0 |
| same | `python3 scripts/docs/check_docs.py all` | 8/8 ok (frontmatter, related, freshness, adr, changelog, terms, orphans, links) |
| same | `npx markdownlint-cli2@0.18.1` (the 13 changed md files of the WP29 delta, plus the config glob) | 302 files, 0 errors |
| same | `npx cspell@10.4.0 --no-progress "docs/**/*.md"` | 301 files, 0 issues. The fragment alone gives 1 ("behaviour", NIT-4) |
| temporary worktree at `888412b4`, with the head feature, the head `ActivationExclusionServiceTest` and the probe | `:activations:test --tests '*ActivationExclusionServiceTest*'` | 22 tests, **1 failed** (the new test: `Expecting code to raise a throwable`). Red confirmed |
| same | `:deployable:test --tests '*CharterContractSuite*'` with `JAVA_TOOL_OPTIONS=-Dcucumber.features=classpath:scenarios/activation-deny-satisfied.feature,classpath:scenarios/zz-probe2.feature` | The 2 new scenarios **FAIL** on the status assertion (200 where 409 is expected). The other 8 pass. Probes D and F fail, which is the old bug. Probe G could not run there (500 from a non-canonical seed). The R-20 and engine failures are expected under a feature filter |
| temporary worktree at `b3ca8b44` plus the probe | same, filtered | 10/10 feature scenarios pass. Probes C, D, E, F, G and H pass (6/6) |
| temporary worktree at `b3ca8b44`, `DocumentRules.satisfied` forced to `false` (mutation) | `:activations:test --tests '*ActivationPresetServiceTest*' --tests '*DocumentRulesOverrideTest*' --tests '*ActivationExclusionServiceTest*'` | 50 tests, 7 failed, all in `DocumentRulesOverrideTest`. `forceKeeps...` **passed** (MINOR-6) |
| scripts | Test-body equivalence of `9f3b2a0a` (old against new, with `,InForceFacts.NONE` removed) | Identical for both files |

Both temporary worktrees were removed (`git worktree list` shows none under the scratchpad). Nothing in
`/home/user/wp29-review` or the lane was edited. The probe is at
`/tmp/claude-0/-home-user-spec-kitty-charter/ef0c42be-39ee-5611-ad1c-8094ad9455aa/scratchpad/zz-probe2.feature`.

## Issue matrix

**Current file.** `kitty-specs/layer-merge-and-effective-reads-01M6R0J6/issue-matrix.json` is identical in the
planning checkout and the coordination worktree. It holds 33 rows:
- #10, #11, #103, #357, #408 (WP30), #410 (WP05), #419, #427 and #429 (WP28) are `in-mission`.
- #403 and #418 (WP21) are `verified-already-fixed`.
- #404, #405, #416, #417 (WP20), #426 and #439 are `deferred-with-followup`.
- #234, #240, #281, #374, #377, #390, #393, #397, #398, #399, #423, #425, #428, #430, #436 and #438 are `not-applicable`.

**Cited but missing.** `grep -E '#[0-9]+'` over the mission's `kitty-specs` dir finds 48 numbers. These 15 have no row:
#45, #72, #97, #99, #131, #186, #358, #368, #392, #406, #407, #409, #411, #414 and #424. The set is the same as at
cycle 1, and no new numbers appeared.

**Rows to add for WP29 approval.** The values are ones `spec-kitty agent issue-verdict --verdict` allows. I ran no
`issue-verdict` command.

| Issue | Verdict | Evidence-ref |
|---|---|---|
| #45 | not-applicable | Context: release hold, "cut no tag"; no work owed |
| #72 | not-applicable | Context: the conformance gate does not exist yet (divergence-register note) |
| #97 | not-applicable | Out of scope: WP prompts say "Do not take on #406, #407, #409, #411, #97 or #99" |
| #99 | not-applicable | Out of scope (as #97) |
| #131 | not-applicable | Cross-repo spec-kitty-open-packs#131 (edges), carried by the #405 follow-up row |
| #186 | not-applicable | Context: epic; "core is not yet a consumer" |
| #358 | deferred-with-followup | Follow-up: #358 (only the distribution pack may settle a sibling contest; WP26 recorded it in the divergence register; ADR-0053 d5 note "#358 open") |
| #368 | not-applicable | Context: process precedent for identity-only review cycles |
| #392 | not-applicable | Context: PR/ADR-numbering reference (0061 held by #392/#393) |
| #406 | not-applicable | Out of scope (as #97) |
| #407 | not-applicable | Out of scope (as #97) |
| #409 | not-applicable | Out of scope (as #97) |
| #411 | not-applicable | Out of scope (as #97) |
| #414 | not-applicable | PR reference: PR A0 merged as #414 |
| #424 | not-applicable | Context: the step-enhancements mission's phase; cited only as why OD-6/OD-9 have no scenario |

**Rows to change for WP29 approval: none.** Keep #10, #11, #103, #357, #408 and #429 as `in-mission`. They move to
`fixed` at the PR C merge, which is not WP29's approval. All other existing rows stay as they are. Optionally, stamp
`--wp WP29` on #429 or #408 when they are set to `fixed` at merge.

## Runner dispositions (cycle 2)

- **Verdict accepted: APPROVE** (no BLOCKER or MAJOR finding).
- **MINOR-5: fold at the PR C closeout**, into the WP29 commit. W26, ADR-0059 R6, DIV-204 (register and `divergences.yaml`) and the
  fragment get the post-fix scope: a deactivate always judges the named target (W25), and the other writes judge only the deny
  entries they add. The fragment drops the claim that a force preset or a select with `includeDeactivated` adds a deny entry.
- **MINOR-6: fold at the closeout.** Rename `ActivationPresetServiceTest.forceKeeps…` and fix its comment to what it actually is:
  a tolerance regression for a stored entry on a required artifact. Do not claim it as IC-13 evidence (there is no overrides
  edge in the fixture).
- **NIT-1** (pin "nothing changes" byte for byte with the stored-file steps) and **NIT-2** (the test-only `InForceFacts.NONE`
  and the `check` overload): fold at the closeout. **NIT-3, NIT-4:** recorded only.
- **Issue matrix:** the 15 rows were recorded with `spec-kitty agent issue-verdict` (actor `claude:opus:reviewer-renata`,
  WP29), mirrored from the status branch onto the planning branch (`8f4780a8`) and into lane-{ (`3c81f44c`).
