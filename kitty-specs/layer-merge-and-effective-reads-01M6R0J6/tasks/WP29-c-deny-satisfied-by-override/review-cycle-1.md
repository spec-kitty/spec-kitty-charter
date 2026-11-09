---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-09T16:33:36Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP29
---

# WP29 review, cycle 1 (claude:opus:reviewer-renata)

Mission `layer-merge-and-effective-reads-01M6R0J6`, lane-{ head `f1245116`. WP29 code is `66481b02..0fa69ecc`, six
commits on `dc218130`: contract `66481b02`, red `9d0e8179`, green `7476ed97`, docs `148f0b7b`, `8aac19d7`,
`0fa69ecc`. The commits after `0fa69ecc` are planning merges and status commits, not WP29 code. I reviewed
`git diff dc218130 0fa69ecc` only.

## Verdict: REJECT

One MAJOR finding (MAJOR-1). The deactivate's early floor refusal now judges only a deny entry the write adds. So a
deactivate that names a locked artifact whose deny entry is already stored answers `200` where it answered
`409 artifact_locked`. A deactivate that names a required artifact whose deny entry is stored, with no override,
also answers `200`. I reproduced both against the lane head. The second one, against `dc218130`, still answers `409`.
The change contradicts the deactivate's own contract text and the WP's rule that a locked `T` stays
`409 artifact_locked`. No ruling covers it, it is not documented and no test pins it.

The rest of the WP is sound:
- IC-13 reads `satisfiedBy` from the shared `InForceResolution` through `PacksSession::inForce`. There is no second copy of the rule.
- Red-first holds, and I re-ran it.
- The contract text, the fragment, the scenarios, the units, the rules page, ADR-0059 R6, the divergence register and the trace are all in place.

There are also four MINOR and three NIT findings.

## What I applied

- Profile `reviewer-renata` (`spec-kitty agent profile show reviewer-renata`): review, flag, assess, and no fixes.
  Directives 001, 024, 030, 032, 041 and 051. Tactics code-review-incremental, delete-the-assertion-not-the-test and
  test-scaffolding-as-design-smell.
- `spec-kitty charter context --action review` gave DIRECTIVE_010 (spec fidelity), 034 (test first), 037 (living docs),
  QA_003_NO_FAKE_GREEN, NO_FULL_HEAVY_SUITES_IN_MISSION (module tasks only) and RECONCILE_CHANGE_SCOPE_TENSIONS.
  The context reports that the `internal` and `qa` pack paths are missing (`packs/internal`, `packs/qa`). That is an
  environment gap. I noted it and moved on.
- I checked behaviour against these sources:
  - the WP file;
  - plan IC-13 and "PR C scope added by operator rulings (2026-11-09)";
  - contract-delta "The relaxed write rule (IC-13)";
  - spec FR-013, FR-014, FR-016, C-002 and C-007;
  - decision `DM-01M6R3YX23H46PXGNEFC31E6CT` (Q9);
  - DIV-204;
  - the wp27, wp28 and wp29 hand-offs. I verified the WP29 hand-off rather than trusting it.

## Check 1: behaviour against IC-13 and the rulings

| Item | Result |
|---|---|
| A deny on a required, not locked `T` is accepted iff the resolution of the stored-after document reports `satisfiedBy` | OK. `DocumentRules.unsatisfied`/`satisfied` (`DocumentRules.java:459-475`) read `ArtifactState.satisfiedBy` from `facts.inForce(EffectiveSet.queryOf(stored))`. `InForceResolution.satisfiedBy` (packs, `:443`) is empty for a locked or in-force artifact and is set only from an overrider in force, so an enhancer never satisfies. |
| No second copy of the rule | OK. `InForceFacts` is a functional interface, and its only production implementation is `PacksSession::inForce`. The domain holds no override walk. |
| Locked always wins, and the facts are not asked for it | OK on create, replace, the commit path and a first deactivate (`floorRefusal`, `:427`). **Not OK** on a re-deactivate of a stored entry: see MAJOR-1. |
| Create judges every deny | OK. `ActivationService.checked` (POST) gives `check(normalized, snapshot, session::inForce)`. `created` (PUT, nothing stored) gives `checkReplace` against the empty document. |
| Replace judges only added denies, on the stale-dropped `kept` document | OK (`checkReplace`, `:216`). The scenario where the body adds the override and the deny together returns `200`. |
| Deactivate, force preset and select with `includeDeactivated` | All go through `ActivationWrites.commit` → `checkWrite(..., session::inForce)` (`ActivationWrites.java:387`). The deactivate's early refusal is MAJOR-1. |
| Removing the override later is not refused, and the read lists `denyNotApplied` (reason `required`) | OK (scenario 7, unit `removingTheOverrideLater...`). |
| The merge refusal (`500 pack_unreadable`) reaches only a write that adds a required deny | OK. The facts are asked lazily (`unsatisfied` returns early on an empty list). Scenario 8 shows `500` for a deny of `OP` and `201` for a deny of free `X` on the same refused closure. `packs.session(...)` is lazy: `AvailablePacks.snapshot` was already `session(...).snapshot()`. |
| `CascadeRules` unchanged (F2) | OK. The file is not in the diff. |
| `checkWrite` no longer short-circuits on a clean whole-document floor | Equivalent. The added denies are a subset of the document's denies, so a clean document gives an empty floor refusal either way. |

## Check 2: contract

- The descriptions of `createActivation`, `replaceActivation`, `deactivateActivationArtifact` and
  `ArtifactPolicyRefused.yaml` say the rule, and so does the `artifact_required` text in `CharterRefusalCode.yaml`
  (`:51-52`, one hunk). `:charter-api:validateContract` and `:contracts:check` are green.
- Fragment `2026-11-07-c-deny-satisfied-by-override.md` has one `###` block (`### Changed`). See MINOR-1 on
  Changed vs Breaking.
- Collision check for `CharterRefusalCode.yaml`. The step-enhancement mission
  (`origin/issue-424-428-adr-0066-service-alignment`, plan `:300-306` and `:747-792`) plans hunks at `:29-35`,
  `:59-71`, `:135-142` and `:160-166`, and in the per-code entries of the load codes. WP29's hunk is `:51-52`, at
  least 13 lines from the nearest planned edit. That is outside a 3-line diff context, so there is no textual
  collision. The other plan already lists "WP29's `artifact_required` text" as a separate hunk resolved at the
  post-PR C rebase. WP29 does not touch `ReservedRefusalCodesTest`. None of the four parallel branches has edited
  either file yet.

## Check 3: red first, re-run by me

I ran this in a temporary detached worktree at `9d0e8179` under the scratchpad, and removed it afterwards.

| Commit | Run | Result |
|---|---|---|
| `9d0e8179` (red) | `:activations:test --tests '*DocumentRulesOverrideTest*'` | 18 tests, **9 failed**, all on AssertJ assertions (`Expecting an empty Optional but was containing value: CharterRefusal: A pack requires directive:T`, `Expecting code to raise a throwable`, `Expecting actual: [] to contain exactly...`). There is no compile or collection error: the stubs ignore the facts. |
| `9d0e8179` (red) | contract suite filtered to `activation-deny-satisfied.feature` | 8 scenarios, **5 failed**, each on the status assertion (`409 artifact_required` where `200`, `201` or `500` was asserted). The 3 "regression control, green before" scenarios pass. No step is undefined or pending. |
| `7476ed97` → head (green) | both runs at `f1245116` | 18/18 and 8/8 pass. |

`DocumentRulesOverrideTest` is byte-identical between red and green. The only change to the feature between red and
green is in scenario 1's in-force list, which went from `F` to `F, LK, T2`. That is a correction that adds two
controls (the locked `LK` and the required `T2` are in force). It does not weaken anything. Scenario 1 was red on
its status line before that step was reached.

## Check 4: tests and fakeability

- The unit cases cover each scope the WP lists: create, replace (both added, stored entry kept), deactivate, force
  preset and select with `includeDeactivated`. They also cover the enhancer, a lock beside a satisfied entry, an
  unknown state, the merge refusal, the shape-invalid case and the query contents (language filter and pins). The
  facts are a fake. The scenarios close the loop with the real resolution, and each scenario pairs its claim with
  an in-answer control.
- There is no `@Disabled`, `@WIP`, `@SuppressWarnings`, NOPMD or Checkstyle suppression in the added lines. No
  existing test file is modified. `:activations:check` (Checkstyle, PMD, SpotBugs) is green.

## Check 5: docs

- Rules page:
  - E6 is amended to OD-6, including the unheld-target case (`composition-and-activation-rules.md:838-867`).
  - E14 and C31 are amended for D16 (`:344`, `:950`).
  - E15 and E16 state the node predicate and the OD-9 step predicate (`:956-997`).
  - W20 and W26 are updated, and so are the status rows (`:1140`, `:1206-1216`).
  - Each amended rule names its pinning tests. I checked every cited test class and every method name in the diff,
    and all of them exist (18 classes and methods, plus all long backticked method names).
- ADR-0059 amendment R6 has the write rule and R11's read rules for D2 and D3, with the follow-up ticks.
- DIV-204 is updated, and DIV-239..DIV-244 are added to the register and to `divergences.yaml`.
  `DivergenceRegisterTest` passes (3/3). DIV-238 does not exist on `origin/main` or on any adr-0066 branch. It is
  claimed only in the step-enhancement mission's plan, so 239..244 do not collide.
- Ruleset trace: I parsed every row. All 105 cited scenario names exist in the named feature files. No row is left
  `planned` or `partly built`.
- T165's 501 text:
  - The `http-status-codes.md` 501 row now says no operation of the activation set is reserved.
  - `docs/api/README.md` and `service-contract.md` drop the reserved/501 wording.
  - `offering-and-activation.md` drops the 501 scenario.
- The order-sweep list is in the hand-off, as the WP's acceptance asks.
- `check_docs.py all`: frontmatter, related, freshness, adr, changelog, terms, orphans and links all ok. markdownlint
  0 errors (301 files). cspell over `docs/**/*.md` finds 0 issues.

## Findings

- **[MAJOR] MAJOR-1** `service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationExclusionService.java:154-161`
  (`refuseFloor`).
  - **What changed.** The early refusal used to judge the named target alone: packs plus the target's deny entry,
    through `DocumentRules.check`. It now runs `checkWrite(before, before.withDeny(target), ...)`, which judges only
    a deny entry the write adds. When the target is already in the stored deny list, `addedDenies` is empty and
    nothing is refused. The commit-time `checkWrite(loaded, after)` adds nothing either.
  - **Failure scenarios** (reproduced at `f1245116` with a scratch feature using the WP's own background; the probe
    is saved at `/tmp/claude-0/-home-user-spec-kitty-charter/ef0c42be-39ee-5611-ad1c-8094ad9455aa/scratchpad/zz-probe.feature`):
    - (a) A stored document denies the locked `directive:LK` (tolerated after a pack upgrade, ADR-0059 D4).
      `PUT /activations/a-1/exclusions/directive/LK` answers **200** with an empty `applied`. At `dc218130` the same
      probe answers **409 artifact_locked**.
    - (b) Deactivate `T` with `F` selected (200). Unselect `F` (200). Deactivate `T` again: **200**, although nothing
      overrides `T`.
    - With `cascade` tokens the write would now also run a cascade from a locked or unsatisfied target and store its
      unselects. That request used to be refused outright.
  - **Why it matters.** The deactivate's own description (`activations_activationId_exclusions_kind_artifactId.yaml:15`)
    says "A named artifact a pack locks is 409 artifact_locked, and one a pack requires is 409 artifact_required ...
    unless an active override of it satisfies the requirement". The WP says "locked `T` stays `409 artifact_locked`".
    No ruling, fragment, divergence entry or test covers the new answer. The hand-off (deviation 2) presents the
    change as an approximation of the stored document only.
  - **Required change.** Restore the judgement of the named target whether or not its entry is stored. For example,
    judge `checkWrite(before.withoutDeny(kind, id), before.withDeny(kind, id), snapshot, session::inForce)`: lock
    first, then the requirement through the facts. Pin both cases (a stored locked entry and a stored unsatisfied
    required entry) with a scenario or a service test that fails at the lane head. If the operator wants the new
    behaviour instead, it needs a ruling, the contract text, the fragment and DIV-204, with the same tests.
- **[MINOR] MINOR-1** `contracts/charter/changes/2026-11-07-c-deny-satisfied-by-override.md:1` uses `### Changed`.
  - **Why it is ambiguous.** The fragment changes a documented response: `409 artifact_required` becomes
    `200`/`201` for the same input. The mission's own precedent classifies "a refusal becomes a success" as
    `### Breaking` (plan `:1014`, "a `### Breaking` block where a `422` becomes a drop"; fragment
    `2026-11-07-a0-ruling-breaking.md`). On the other hand, contract-delta `:228` and the WP explicitly rule
    `### Changed` ("a write now accepts more"), the response shapes do not change, and oasdiff will not flag it.
  - **Not an implementer defect.** The WP was followed.
  - **Required change.** The runner decides. If it is `### Breaking`, say what core's seam changes: core's
    in-process backend still refuses with 409, which DIV-204 records.
- **[MINOR] MINOR-2** The commits `9d0e8179` and `0fa69ecc` have unrecorded out-of-map files:
  - `service/activations/src/main/java/ai/speckitty/charter/activations/domain/InForceFacts.java` (new production
    type, `9d0e8179`) has no `out-of-map:` line.
  - `docs/api/service-contract.md` (`0fa69ecc`) is neither in `owned_files` nor in the shared list. Its commit
    message mentions it but gives no `out-of-map:` line. The hand-off lists it as "(owned/listed)", which is not
    correct.

  **Required change.** Record both rationales in the hand-off's out-of-map list or in a follow-up commit message.
  No history rewrite is needed.
- **[MINOR] MINOR-3** `DocumentRules.java:87,112,157,199`: the facts-less overloads `check(document, snapshot)`,
  `check(built, snapshot)`, `checkWrite(before, after, snapshot)` and `checkReplace(built, stored, snapshot)`.
  - **The risk.** They are now test-only. No production caller is left (grep over `service/*/src/main`). They encode
    the pre-IC-13 rule: `InForceFacts.NONE` refuses every required deny. A future caller that picks the shorter
    overload silently gets #399's behaviour back.
  - **Required change.** Either mark them as the no-override form in their Javadoc and in a test name, or move the
    old unit tests to the facts overloads with `InForceFacts.NONE` passed explicitly and delete the old overloads.
- **[MINOR] MINOR-4** The force-preset and `includeDeactivated` scopes are pinned only at the rule level.
  - `DocumentRulesOverrideTest.aPresetAppliedWithForce...` and `aSelectWithIncludeDeactivated...` call `checkWrite`
    with hand-built documents.
  - No service test or scenario drives `ActivationPresetService` (force) or `ActivationSelectionService`
    (`includeDeactivated`) with an override in the closure.
  - The wiring is shared through `ActivationWrites.commit`, which the deactivate scenario covers, so the risk is low.

  **Required change.** Add one service-level case for the force preset, or state in the hand-off that the shared
  commit path is the evidence.
- **[NIT]** `contracts/charter/paths/activations.yaml:10`, `activations_activationId.yaml:64` and
  `activations_activationId_exclusions_kind_artifactId.yaml:15`: the new description text is spliced into the folded
  scalars as single lines of more than 400 characters, unlike the surrounding wrapped text. Lint passes. Rewrap them.
- **[NIT]** `docs/changelog/2026-11-07-c-graph-and-context.md`: the file is named 2026-11-07 (the WP's `create_intent`
  name) but its text is dated 2026-11-09. This is harmless. Note it for the release assembly.
- **[NIT]** `contracts/charter/changes/2026-11-07-c-deny-satisfied-by-override.md:4` uses "behaviour". It is outside
  the cspell scope (`docs/**`), and three other fragments use it too. Record only.

## Anti-pattern checklist

| # | Item | Result |
|---|---|---|
| 1 | Dead code | PASS for new code: `InForceFacts` and the facts overloads are called from `ActivationService`, `ActivationWrites` and `ActivationExclusionService`. The older overloads became test-only (MINOR-3). |
| 2 | Synthetic-fixture test | PASS. The unit facts are fakes, but the 8 scenarios run the real resolution, and deleting the rule turns 5 of them red (shown at `9d0e8179`). |
| 3 | Silent empty return | PASS. `unsatisfied` returns the empty list early, which is documented and is the laziness the merge-refusal rule needs. |
| 4 | FR coverage (FR-013, FR-014, FR-016) | PASS. Scenarios 1 and 7 assert the in-force list, `denyNotApplied` and the `required` reason. |
| 5 | Frozen surface | PASS. `CascadeRules` is untouched, and no existing feature is modified. |
| 6 | Locked decision | **FAIL**: "locked `T` stays `409 artifact_locked`" breaks on a re-deactivate of a stored entry (MAJOR-1). "No second copy of the resolution" passes. |
| 7 | Shared-file ownership | PASS with a note. The `CharterRefusalCode.yaml` hunk is separate from the step-enhancement mission's planned hunks. Two out-of-map rationales are missing (MINOR-2). |
| 8 | Production fragility | PASS. There is no new `raise`. The facts may throw `CharterRefusal(PACK_UNREADABLE)`, which is documented on `InForceFacts.inForce` and pinned by scenario 8. |

## Commands run (JAVA_HOME=/usr/lib/jvm/temurin-25; all Gradle runs in the foreground, one at a time, with `--max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PopenPacksDir=/home/user/open-packs -PcontractBaseline=none`)

| Where | Command | Result |
|---|---|---|
| `/home/user/wp29-review` (`f1245116`) | `:charter-api:validateContract :activations:test --tests '*DocumentRulesOverrideTest*'` | BUILD SUCCESSFUL. 18 tests, 0 failed |
| same | `:deployable:test --tests '*CharterContractSuite*'` with `-Dcucumber.features=classpath:scenarios/activation-deny-satisfied.feature` (through `JAVA_TOOL_OPTIONS`) | 8/8 scenarios pass. The R-20 coverage check and the engine node fail only because of the feature filter (expected, as in the hand-off) |
| same | `:activations:test :deployable:test --tests '*CharterContractSuite*' --tests '*HexagonalArchitectureTest*' :packs:test --tests '*DivergenceRegisterTest*'` | BUILD SUCCESSFUL. activations 804/0 failed, deployable 633/0 failed (the whole contract suite, R-20 coverage included, and the architecture test), packs 3/0 failed |
| same | `:contracts:check` | BUILD SUCCESSFUL (fragments, compatibility, refusal examples, closed responses, lint) |
| same | `:activations:check` | BUILD SUCCESSFUL |
| same | `python3 scripts/docs/check_docs.py all` | 8/8 ok |
| same | `npx markdownlint-cli2@0.18.1` (the edited docs and the repo glob) | 0 errors |
| same | `npx cspell@10.4.0 --no-progress` (the edited docs and the fragment) | 0 in `docs/**`. 1 in the fragment, outside the CI scope (NIT) |
| temporary worktree at `9d0e8179` | `:activations:test --tests '*DocumentRulesOverrideTest*'` | 18 tests, 9 failed on assertions (red confirmed) |
| same | filtered contract suite, as above | 8 scenarios, 5 failed on the status assertion, 3 controls pass |
| temporary worktree at `f1245116` plus the scratch probe | filtered contract suite on `zz-probe.feature` | probe A (stored required, no override) 200; probe B (stored locked) 200 |
| temporary worktree at `dc218130` plus the scratch probe | same | probe B 409 `artifact_locked`, which passes. Probe A is unreachable before IC-13 (its first deactivate is 409) |
| scripts | trace parser and test-citation check | 105/105 trace scenario names exist. All cited test classes and methods exist |

Both temporary worktrees were removed. Nothing in `/home/user/wp29-review` or the lane was edited.

## Issue matrix (proposed verdicts for this WP's approval; values as `spec-kitty agent issue-verdict` allows)

These are all the issue numbers the mission's `kitty-specs` directory mentions. A "keep" means the row is unchanged.
I ran no `issue-verdict` command.

| Issue | Proposed verdict | Reason |
|---|---|---|
| #10, #11, #103, #357 | in-mission (keep), then fixed at the PR C merge | Mission inputs. WP29 completes PR C's docs and IC-13. The issue comments are orchestrator closeout actions (WP "Context") |
| #45 | not-applicable | Release hold cited as "cut no tag" |
| #72 | not-applicable | The conformance gate is cited as not yet existing (register context) |
| #97, #99, #406, #407, #409, #411 | not-applicable | Explicitly out of scope ("Do not take on ...") |
| #131 (spec-kitty-open-packs#131) | not-applicable | Cross-repo follow-up, already carried by the #405 row |
| #186 | not-applicable | Epic context ("core is not yet a consumer") |
| #234, #240, #281 | not-applicable (keep) | Context only |
| #358 | deferred-with-followup (Follow-up: #358) | Only the distribution pack may settle a sibling contest. It stays open, WP26 recorded it, and the ADR-0053 d5 note says "#358 open" |
| #368 | not-applicable | Process precedent for identity-only review cycles |
| #374 | not-applicable (keep) | Closed input analysis. The comment is a closeout action |
| #377, #390, #392, #393 | not-applicable (keep, add #392) | PR and ADR-numbering references |
| #397 | not-applicable (keep) | Context |
| #398, #399 | not-applicable (keep) | Base PRs. WP29 changes #399's shipped write rule, which the fragment records |
| #403 | verified-already-fixed (keep) | Merged on main |
| #404, #405, #416, #417, #426 | deferred-with-followup (keep) | Existing follow-ups |
| #408 | in-mission (keep), then fixed at the PR C merge | Rulings 1 to 3 are now all built. WP29 turned the "conditional suggests" trace row to built |
| #410 | in-mission (keep) | Built in A0 (WP05) |
| #414 | not-applicable | PR reference (PR A0 merged) |
| #418 | verified-already-fixed (keep) | |
| #419, #427 | in-mission (keep) | This mission's PRs A and B |
| #423, #425, #428, #430, #436, #438 | not-applicable (keep) | PRs or other missions' issues |
| #424 | not-applicable | The other mission's phase. It is cited only as the reason OD-6 and OD-9 have no scenario |
| #429 | in-mission (keep), then fixed at the PR C merge | WP28 built it. WP29 adds the rules page (E14, C31 D16) and DIV-239 |
| #439 | deferred-with-followup (Follow-up: #439) (keep) | WP29 documents the smallest-URN tie as found and points at #439 |

## Runner dispositions (cycle 1)

- **MAJOR-1: fix in this cycle.** The deactivate's early refusal judges the named target whether or not its deny entry is
  already stored (for example `checkWrite(before.withoutDeny(t), before.withDeny(t), snapshot, session::inForce)`, keeping
  only floor refusals). Lock first, then the requirement through the facts. Red first: scenarios for (a) a stored deny on
  locked `LK`, where re-deactivating must answer `409 artifact_locked`, and (b) deactivate `T` with `F` selected, unselect
  `F`, deactivate `T` again, which must answer `409 artifact_required`. Both must fail at `f1245116` and pass after the fix.
  Add a cascade variant if it is cheap. Keep the WP's existing scenarios green.
- **MINOR-1: keep `### Changed`** (decision `01M70JFXZHY2NKA6Y6RQ66AYA9`). Amend the fragment so it says that a documented
  `409 artifact_required` now succeeds when an active override satisfies the requirement, and that core's in-process
  backend still refuses (DIV-204).
- **MINOR-2: fix.** Put `out-of-map:` rationales for `InForceFacts.java` and `docs/api/service-contract.md` in the fix
  commit message and correct the hand-off.
- **MINOR-3: fix by deletion.** Move the old unit tests that call the overloads without facts to the facts overloads,
  passing `InForceFacts.NONE` explicitly, and delete those overloads (no production caller is left). No assertion is
  weakened or dropped.
- **MINOR-4: fix.** Add one service-level case for the force preset (`ActivationPresetService`, force) with an active
  override in the closure: the preset's deny of the required `T` is accepted.
- **NIT long lines: fix.** Rewrap the three YAML descriptions to match the surrounding folded text.
- **NIT changelog name:** keep it; noted for release assembly. **NIT "behaviour":** record only.
