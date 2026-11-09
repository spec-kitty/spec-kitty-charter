---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command: spec-kitty agent tasks move-task WP25 --to approved --mission layer-merge-and-effective-reads-01M6R0J6 --agent claude:opus:reviewer-renata
reviewed_at: '2026-11-08T21:03:42Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP25
---

Approved by claude:opus:reviewer-renata: approval:WP25

# WP25 review, cycle 1: reviewer-renata (claude:opus:reviewer-renata)

**Verdict: APPROVED.** No blocking finding. The non-blocking findings below go to WP26 and the closeout.

Scope: 18 signed commits `e6ea863d..fbce84f9` on lane-w, base `9e6d3f92` (main plus the approved WP21 to WP24). The lane
HEAD `3f07d35a` adds only the status files the review claim merged in.

## Commands run (foreground, one at a time)

1. `spec-kitty agent action review WP25 --mission layer-merge-and-effective-reads-01M6R0J6 --agent claude:opus:reviewer-renata`
   claimed the review. The only warnings were DRG directive-resolution warnings.
2. `./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=/home/user/open-packs :activations:check :packs:check :contracts:check`
   gave BUILD SUCCESSFUL. The test tasks were up to date against the current inputs, and
   `EffectiveReadOperationBudgetTest` ran 4 tests with 0 failures.
3. The same flags with `:deployable:test` gave BUILD SUCCESSFUL: 1,173 tests, 0 failures, 0 errors, 0 skipped.
   `OperationCoverageTest` (R-20) and `ReservedOperationCoverageTest` (activation set = 2) are green. No `@WIP` is left in
   `effective-reads.feature`, `layer-merge.feature` or the A10 scenario.
4. Red evidence, in a throwaway worktree (now removed). `:deployable:test --tests '*CharterContractSuite*'` gave:
   - at `e6ea863d`: 550 tests, 64 failed (63 scenarios and the R-20 executionError). All are assertion failures
     (45 `AssertionFailedError`, 19 `AssertionError`). No step was undefined or pending.
   - at `0e72b394` (after every feat commit, before the fixture commit): 39 failed, all on assertions.

   I also ran targeted unit suites:
   - at `3c27c422`: `:activations:test --tests '*EffectiveRead*' --tests '*ActivationControllerTest*'` gave 48 tests,
     41 failed, all on assertions. This matches the commit's stated 41.
   - at `66359a55`: `:packs:test --tests '*CompositionFactsBindingsTest*' --tests '*ClosureViewMergedTest*'` gave
     4 failed on the asserted binding (`expected MISSION_TYPE_NOT_IN_FORCE but was SELECT`, `{}` bindings).

   I did NOT run `timingBudgets` (the operator excludes timing checks).

## Rulings 1 to 8

### 1. Recorded red: CONFIRMED, with one caveat (N1)

`e6ea863d` binds every step, and every failure is an assertion failure (decision DM-01M6RN6GC7PJS8BGHMAYM4JSSW). The
count (63 scenarios plus R-20) and the shapes match the commit message: 501 against the status assertion, "ETag differs
from the stored ETag", and 304 where 200 is now expected.

Caveat: 8 effective-reads scenarios were red at `e6ea863d` only because their own fixtures were invalid. Their
`Given … PUT /activations/a-1 creates the activation` step got `500 pack_unreadable` (an upper-case tactic id). About 10
more were red, still at `0e72b394`, on `500 internal_error` (a seed that is not JCS canonical). They are US3 AS5, the
enhancer and A8 scenarios, A13, B6/B7, B4 deny, US3 AS2/AS3/AS4, US4 AS1 and B1.

`4268c794` corrected those fixtures only after the feat commits. For these scenarios, the step "seen failing for the
right reason" was therefore never isolated in a commit. They do assert behaviour the feat commits built, and the
service, HTTP and mapper tests at `3c27c422` were red for the right reason. This is not blocking, because no
assertion was changed (see ruling 2).

### 2. Test modifications: NO WEAKENING

**The 28 restated scenarios.** Each is at least as strong as before under the declared contract (decisions 01M6Y11E and
01M6Y3GZ):

- A1 (17): "nothing stored" moved from the read's ETag to `/revision` = stored revision, which is the same proof. The
  added "ETag differs from the stored ETag" pins the closure tag.
- A2 (8): `/revision` equals the remembered revision, and the write's ETag is the quoted `/revision`. Where a scenario
  replays the GET tag as If-Match (register :75, kind-model :442), it now also exercises the either-tag rule, which is
  stronger. `4268c794` restored the final remembered-header comparison at register :675.
- kind-model :454 compares with the first PUT's header. The always-true check is fixed.
- A3 (2): 304 on the remembered closure tag, then 200 on the revision (the control). Register :571 asserts 200 with a
  new tag after a closure file is deleted, and still asserts that the body is served as stored.
- C (ruling-changes :212): the stale GET tag gives 412, then the stored revision gives 200. This adds an assertion.

**Assertion counts** (base to HEAD): effective-reads 303 to 303 (only `E` became `e`), layer-merge 50 to 52,
activation-* features +4 to +8 each, and no scenario was removed. The 21 removed lines are unused archive Givens (N9).

**Fixture corrections.** JCS seeds, id case (the pack schemas), B6/B7 (Q8/E6: a target that is merely not denied is
not in force, so the old control contradicted its own header), and LK beside OP (a lock keeps LK in force, E3/E9;
OP's provenance assertions are kept). All are corrections, not weakenings. For "required T hidden by an override",
see ruling 3.

**Five read-count pins, 0 to 1: LEGITIMATE.**
- Decision 01M6Y3GZ requires the server to recognise the current getActivation tag. Both tags are opaque quoted
  `sha256:` values, and the If-Match pattern admits only that shape, so a stale tag cannot be told from a closure tag
  without computing the closure tag.
- `IfMatchTags.names` already does the cheap thing: it tries the revision first. `*`, the revision and a header with no
  `sha256:` tag cost no read (`ActivationIfMatchTagTest` pins that a revision costs 0).
- The pins still prove what they were written for: 412 before the body, the path and the preset are judged, and
  nothing is stored. Only the tag read was added.
- NFR-007 governs effective reads, not writes, so this is within it. See N2 for the second read a closure-tag write
  takes.

### 3. Open question 1 (E9 order): E9 IS AUTHORITATIVE, and the scenario still proves T113

These sources agree, and no operator ruling is needed:
- E9 puts `not_selected` before `overridden`.
- The data-model Precedence table puts row 6 before row 7.
- Research R11 step 3 says an out-of-force required T satisfied by an override "keeps its state and reason" and gains
  `satisfiedBy`.

An unselected required T is therefore `inactive, not_selected` with `satisfiedBy F`. WP22's expectation of
`overridden` contradicted the rules.

With T selected, the scenario still proves "a required T hidden by an active override is satisfied". If satisfaction
by the override were not recognised, E5 would put T in force (`active, required`), and the `inactive`/`overridden`,
`satisfiedBy F` and `requiredNotInForce = []` assertions would all fail. The unselected variant is pinned at unit
level (`InForceResolutionTest.aRequiredOverriderOfAnotherRequiredArtifactSatisfiesIt`: NOT_SELECTED plus satisfiedBy)
but only stated in prose in the scenario (N3).

### 4. Defects in earlier WPs' code, fixed in packs: BOTH CORRECT; the out-of-map edits are necessary and minimal

**Binding.** `ClosureView.MergedView.resolutionEdges()` passed `Map.of()`, so E6 stratum 4 never fired in production.
- Facts now carry `bindings`: a `MissionStepContract` is bound to `mission_type:<mission>`, read in the same assembly
  (`DomainRepositoryService`).
- `bindingsOf` takes the winner's pack's binding, as E6 and "the binding of the version in force" require.
- `WriteFacts` carries the binding and drops a removed artifact's binding.
- This matches A7 Q1 (DM-01M6RCXTS0) and the rules page's `bnd(u)`. Action steps are not activatable kinds and are out
  of W, so they need no entry here. Step enhancers (`stepBinding` in data-model) are deferred to PR C (hand-off).

**Overflow.** `bound*bound*bound` in `int` wraps negative at 1,291 (1291³ = 2,151,685,171 > 2³¹−1), so `bounded()` threw
`IllegalStateException`, which surfaced as a 500. The `(long)` product fixes it, and
`InForceResolutionTest`'s 2,000-artifact case pins it.

**Out-of-map files.**
- `ClosureRoots`, `RegistrationImpact`, `ActivationImpact`, `PackRegistrationMapper`, `PackCompositionFacts`,
  `DomainRepositoryService`, `ClosureView`, `WriteFacts`, and the `CompositionFactsCache` and `InForceResolution`
  hooks each carry an out-of-map line and are minimal for their purpose.
- Exception (N4): the WP22-owned contract files edited in `e13a8c14` carry no line.

### 5. Behaviour against the contract and the decisions: CONFORMS

**The two reads** (`EffectiveReadService`):
- Order: kind token, then id, both in code points (`ArtifactRef.ORDER`). The `kind` and `state` filters apply.
- The cursor is base64url of `<set tag>\n<kind>/<id>`. The set tag covers the revision, the closure digest, kind and
  state. A foreign set, a changed document or closure, another filter, or a malformed cursor, kind, state or page size
  answers 400 with no code, judged before the 304.
- `summary` covers the whole set on every page. Each representation has its own tag (the query, cursor and page size),
  and a matching If-None-Match answers 304 before the merge and the resolution.
- Refusals: 404 `activation_not_found`; 500 with the compose or merge code, or `pack_unreadable` for an unconfigured
  pack.
- One session per request, with the stale-input retry inside the session (E19).

**getActivation:**
- The ETag is `EffectiveTag.activation(revision, snapshot)` over `closureFingerprint`.
- `denyNotApplied` is set only when it is non-empty, as sorted, distinct URNs.
- 304 is answered before `inForce`. No `Charter-Source-Fingerprint` header is sent.
- The two effective reads send `closure.fingerprint()` in that header. The bodies' `governanceFingerprint` is
  `closureFingerprint()`, the same digest as the tag.

**If-Match:**
- One class, `IfMatchTags`, used by `ActivationService` (put, delete) and `ActivationWrites`. It accepts the revision,
  `*` or the current closure tag. A stale closure tag, or a closure that cannot be read, is 412.
- Write answers keep the revision ETag and carry no `denyNotApplied`.

**warnings:** computed once, from the write's own `PacksSession` (`session.inForce`), before the CAS. Only artifacts
the write applied are considered, and only the codes `enhanced_target_not_in_force` and `overridden`. A no-op asks
nothing, and a refusal of the resolution refuses the write before anything is stored.

**activationsAffected:**
- Bounded: only activations whose root set's closure holds the written pack, deduplicated and sorted.
- Computed inside the impact check, before the commit, from the closures the check already composed.
- An unmergeable closure counts as changed. Ruling: RIGHT. A before closure that cannot be merged means the reads
  answered 500. A mergeable after closure changes what the activation puts in force from "no answer" to a set, so the
  activation is affected. Two unmergeable closures are the same "no answer". A committed registration cannot leave
  the after closure unmergeable (FR-025 is strict).
- Nit N6 covers a before closure that failed to compose.

**Notice kinds:** activatable only (#417). `activationsAffected` names activation ids, not artifacts.

**Per-artifact writes and removals:** CONFIRMED. `PackRegistrationNotices` reaches the wire only through
`PackRegistration` ("Only in the result of a succeeded registration"). The per-artifact writes answer
`DirectiveRegistration`/`Artifact`. `impact.committed` stays a no-op hook used only by registrations.

### 6. NFR-003 gate: PRESENT AND GREEN in `check`

`EffectiveReadOperationBudgetTest` exercises 10 packs and 2,000 artifacts through MockMvc, with real `ClosureQueries`,
`ConsistentRead` and `CompositionFactsCache`.

| Case | Facts | Resolutions | Walk visits | Digests |
|---|---|---|---|---|
| Cold | 10 loads, 0 hits | 1 | > 0 and ≤ 6,000 | 2 whole, 1 closure |
| Warm | 0 loads, 10 hits | 1 | ≤ 6,000 | 2 whole, 1 closure |

A 304 resolves nothing. A changed revision reloads the facts.

`EffectiveReadBudgetTest` is tagged `timing` and runs only in `timingBudgets`. It does 10 warm-ups and 50 runs, varies
the cache key per run (`fixture.revise()`), and reports p95 against 300 ms at 1× and 3×. It was not run, as instructed.

### 7. Contract fragments: CORRECT CLASSIFICATION

- `2026-11-08-b-scenarios-closure-etag.md` is `### Changed`: it changes scenarios only, with no operation, schema or
  response. The getActivation ETag change itself is already `### Breaking` in WP22's `2026-11-07-b-effective-reads.md`.
- `2026-11-08-b-effective-reads-wording.md` is `### Changed`:
  - N6 (the 400 sentences): the If-Match pattern is unchanged, and a revision was always a quoted `sha256:` tag.
  - N7: the stray `),.` is removed.
  - N8: `EffectiveETag` names the built reads.
  - The `ActivationSummary` and `EffectiveArtifact` wording: on `main`, `listEffectiveArtifacts` and the action
    context are `x-reserved` (501), so no documented response a client receives changes. `Changed` is right.

### 8. Leftovers: ACCEPTABLE

- WP21 N3: `OfferingReadServiceCacheWiringTest` pins the deployed wiring (loads, then hits). It was green on its first
  run, which is acceptable: it pins existing wiring and would fail without the cache.
- `ActivationService.get` has no route now. It is dead outside tests, and its Javadoc says so and names its removal.
  Keeping it in this WP is acceptable. Remove it, with its revision-tag tests, at the closeout or in PR C (N5), so that
  nobody wires a revision-tagged document read again.

## Anti-pattern checklist

| # | Item | Result |
|---|---|---|
| 1 | Dead code | PASS for new code. Every new production class or method has a live caller. The test hooks (`loads`, `hits`, `clear`, `resolutions`, `walkVisits`) are the sanctioned NFR-003 hooks. The compatibility constructors `MergedView(closure, edges)` and the 7-argument `PackCompositionFacts` are called only by tests (N7). `ActivationService.get` became dead (ruling 8). |
| 2 | Synthetic fixture | PASS. The tests run the real service, controller, mapper, `ClosureQueries` and resolution, and the scenarios run the deployed service. |
| 3 | Silent empty return | PASS. `IfMatchTags.closureTag` returns empty on a refusal (documented: 412). `ActivationImpactAdapter.inForce` returns empty for an unmergeable closure (documented). |
| 4 | FR coverage | PASS. FR-013, FR-017, FR-020, FR-021, FR-027 and FR-032 are asserted in the scenarios and in the service, HTTP and mapper tests. NFR-003 is covered by the gate. NFR-007 is covered by the one-session assertions. |
| 5 | Frozen surface | PASS. |
| 6 | Locked decision | PASS. |
| 7 | Shared-file ownership | PASS with a note. The packs files belong to WP15, WP21 and WP23, the activation write services to WP04 and WP08, and the contract files to WP22. All carry out-of-map lines except `e13a8c14`'s contract edits (N4). This note records the coordination. |
| 8 | Production fragility | PASS. `StaleCursorException` maps to 400. `open()` refuses `pack_unreadable` for an unconfigured pack, which is declared. |

## Findings (all non-blocking)

**N1 (low, process). The red for about 18 effective-reads scenarios was masked by invalid fixtures.**
- Where: `contracts/charter/scenarios/effective-reads.feature`, fixed in `4268c794` after the feat commits.
- Cause: upper-case tactic ids gave `500 pack_unreadable`, and non-canonical seeds gave `500 internal_error`.
- Fix: nothing to change now. Next time, correct the fixtures inside the red commit, so that every scenario is seen
  failing on its own assertion.

**N2 (low). A write whose If-Match is a closure tag reads the packs twice, and the two reads can disagree.**
- Where: `IfMatchTags.java:63-74`, then the write's own session.
- Cost: one verified read for the tag (`packs.snapshot`), then another for the write's judgment.
- Risk: a pack change between the two reads lets a closure tag that has just gone stale pass.
- Fix (optional, PR C or a follow-up): open the write's `PacksSession` first and compute the tag from
  `session.snapshot()`, still answering 412 before the body and path faults. This saves a read and closes the window.

**N3 (low). The contract scenario does not assert the unselected required-T case it states in prose.**
- Where: `effective-reads.feature:452-516`.
- The scenario's parenthesis states that the unselected required T is `not_selected` with `satisfiedBy F`, and no
  assertion checks it.
- The description also says "the governing preset requires T and F", but the preset lists only T.
- Fix (closeout): add a second activation in the same scenario that does not select T. Assert `/items/1/reason` is
  `not_selected`, `satisfiedBy` is F, and `requiredNotInForce` is empty. Fix the wording.

**N4 (low, traceability). `e13a8c14` carries unrelated edits that its message does not mention.**
- The commit is named for the NFR-003 gate.
- It also changes the WP22-owned contract wording: four `paths/activations_*.yaml`, `CharterSourceFingerprint.yaml`,
  `EffectiveETag.yaml`, `ActivationSummary.yaml` and `EffectiveArtifact.yaml`.
- It also adds `changes/2026-11-08-b-effective-reads-wording.md` and removes the 21 unused archive Givens.
- Its message names none of these, and the contract files have no `out-of-map` line.
- Fix: no history rewrite. The PR B description and closeout notes list these edits as "WP22 N6, N7, N8, N9 and
  WP24 F4, folded by WP25".

**N5 (low). `ActivationService.get` is dead outside tests.**
- Where: `ActivationService.java:243-260`.
- Fix: remove it, with its revision-tag tests, at the closeout or in PR C.

**N6 (nit). A before closure that failed to compose counts as an empty in-force set, not as "no answer".**
- Where: `RegistrationImpact.viewBefore` returns empty, and the adapter treats that as `Set.of()`.
- Effect: a registration that repairs a closure which could not compose, leaving nothing in force, names no
  activation.
- Fix (optional): separate "did not exist" from "could not compose" in `ClosureChange.before`.

**N7 (nit). The compatibility constructors are kept only for the tests.**
- Which: `ClosureView.MergedView(closure, edges)` and the 7-argument `PackCompositionFacts`.
- Fix (optional): move the tests to the full constructors and delete these two.

**N8 (nit, contract wording).** `activations_activationId.yaml:148` still says "The delete takes no snapshot of the
packs". A DELETE whose If-Match is a tag other than the revision now reads the closure to compute the tag (a refusal
there is 412, so "its only 500 is internal_error" still holds). Fix: reword at the closeout or in WP26.

## Hand-offs

**For WP26 (docs and divergences):**
- getActivation answers 500 when the closure cannot compose or merge (already in WP22's Breaking fragment).
- The If-Match either-tag read cost (N2).
- Bindings are fed from facts, for step contracts only. `stepBinding` for step enhancers stays in PR C.
- The NFR-003 gate counts 3 digests per read (WP24's N2 closure digest).
- The page links repeat `pageSize` only when the request named it.
- Amend ADR-0020 D6 and ADR-0059 D5, as already handed off.
- N8 wording.

**For the closeout:** N1, N3, N4 (the PR B description), N5, N6 and N7. Merge the planning documents' E9 statement
with research R11 "keeps its state and reason", which rulings 3 and 2 confirm.
