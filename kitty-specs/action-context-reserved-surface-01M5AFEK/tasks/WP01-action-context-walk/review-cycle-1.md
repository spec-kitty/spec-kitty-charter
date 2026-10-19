---
affected_files: []
cycle_number: 1
mission_slug: action-context-reserved-surface-01M5AFEK
reproduction_command:
reviewed_at: '2026-10-19T17:08:58Z'
reviewer_agent: claude
wp_id: WP01
---

# WP01 review feedback, cycle 1 (reviewer-renata)

Verdict: changes requested.

What I verified:
- `./gradlew verifyAll --rerun-tasks -PopenPacksDir=/home/user/spec-kitty-open-packs` is green on Java 25: 203 tests, 0 skipped, 0 failed.
- Red commit f65e38c, checked out in a temp worktree: the contract suite has 21 action_context scenarios. 14 fail with `AssertionFailedError`, with 0 errors and no undefined steps, so the red is genuine.
- `ActionContextWalk` matches the D2/D3 table as the prompt was written. It walks breadth-first with minimum steps, uses the parent tie-break (step, hops, URN), applies first-listing precedence, never lists the endpoint nodes and does not walk the mission-type scope. I found no counterexample to the via/hops invariant.
- `DoctrineGraph` index, the diagnostics in load order and the depth range 0..2 with a 400 are correct.

Before you start, work from the current WP prompt and research.md in the MAIN checkout (`/home/user/spec-kitty-charter/kitty-specs/action-context-reserved-surface-01M5AFEK/`). The lane worktree still holds the pre-finalize copy of `tasks/WP01-action-context-walk.md`. Its T003 differs from the authoritative one; see Issue 2.

## Issue 1 (required): Orchestrator design correction, DIV-010 narrowed (verbatim from the orchestrator)

**The finding.** I measured the walk variants over open-packs built-in with core's library, summing |entries| across all actions:

| Depth | Core | + `requires` of suggested | Full DIV-010 (+ `suggests` seeded from required) |
|---|---|---|---|
| 1 | 397 | 434 | 550 |
| 2 | 467 | 497 | 657 |

Core's own docstring (`query.py:196-197`) states `suggests` is walked "from scoped artifacts". That is core's intent, not a bug, so seeding `suggests` from required artifacts is a design change, not a bug fix. It adds about 30% more governance; for example, `software-dev/tasks` at depth 1 goes from 28 to 69.

**The decision.** DIV-010 is narrowed to the hard-dependency follow-through only:

- **Step 3:** `suggests` is seeded from the SCOPED artifacts only, as core does.
- **Step 4:** the `requires` of suggested artifacts is still listed. That follows core's stated intent ("requires = hard dependencies") and is the operator's "don't replicate bugs" steer.

**The rework.**

- ActionContextWalk step 3 seeds from scope entries only.
- Update the scenarios and unit tests:
  - the required-artifact-suggests scenario now asserts the suggested artifact is NOT listed through a required artifact, matching core;
  - the suggested-artifact-requires scenario is unchanged.
- Update the code comments.
- The `via`/`hops` invariant must still hold. Re-check the random-graph test.

Research D2, spec and data-model are being updated in the main checkout by the orchestrator.

Reviewer's concrete checklist for Issue 1:

- **1a.** `ActionContextWalk.run`: step 3 seeds from the list step 1 returned (`scoped`), not from `listed.keySet()`. Rewrite the DIV-010 comment there, the class Javadoc and the `ActionContextQuery` class Javadoc to describe step 4 only, and state that step 3 matches core (`query.py:196-197`).
- **1b.** Contract text:
  - `ActionContext.yaml` `guidance.description` ("the suggests edges of every artifact the first two steps listed") and `x-derived.rule` ("suggests from every artifact in force") become "from the scoped artifacts".
  - Check `GuidanceEntry.yaml` and the path description for the same wording.
  - Regenerate `examples/ActionContext.software-dev-plan.yaml` from the real service (open-packs, software-dev/plan, depth 1) if the answer changes.
- **1c.** `action_context.feature`, "What a required artifact suggests is listed (... DIV-010)": rewrite it so the artifact is NOT listed when its only path is through a required artifact. Cite `query.py:196-197` as core's intent. Then re-derive every exact body that the background's required-suggests shard affects (the depth 0/1/2 bodies, default depth, precedence and others), and re-run red, then green, for the changed scenarios.
- **1d.** Unit test `aRequiredArtifactIsInForceSoWhatItSuggestsIsListed`: invert it, so a required artifact's `suggests` target is not listed at any depth.
- **1e.** New passing-through pin. The narrowing makes this case real: a required node is no longer a seed of step 3, but it can still be reached by `suggests`. Scoped S `suggests` R, R is already listed by step 2, and R `suggests` C. At depth 2, C must be listed with relation `suggests`, via R and hops `hops(R) + 1`, and R must keep its `requires` entry. At depth 1, C is absent. Add this case as a unit test and as a scenario. It pins the WP prompt's "passing through: Yes" decision.
- **1f.** `amongParentsAtTheSameDistanceTheFewerHopsWinThenTheSmallerUrn`, and its scenario at about `action_context.feature:627`: the fewer-hops case currently relies on a required node (`dependency`, hops 2) seeding step 3. That no longer happens, so the test would pass for the wrong reason or fail. Rebuild it so two parents reach the target at the same suggests step with different hops. For example, S `suggests` R, where R is already required with hops 3; S `suggests` B, which is new with hops 2; and both R and B `suggest` X. X must take B as its parent.

## Issue 2 (required): T003's breaking fixture and ContractShapeTest are missing (post-tasks finding 9, research §9 row 9)

The authoritative WP prompt (main checkout, T003) requires two things.

- **2a.** Commit `contracts/compat/fixtures/breaking-response-depth-max-raised/` (`base.yaml` and `revision.yaml`): a response property `depth` with `maximum: 2`, raised to 3, together with the request maximum. Commit it only if `:contracts:compatibilityFixtures` reports it at ERR under `severity-levels.txt`. Your history says oasdiff reported it as breaking, so commit it. Confirm that the task discovers it by its `breaking-` prefix and that it fails it at ERR, and record the task's fixture counts in history (they are 5 breaking and 2 compatible today). If it is not at ERR, record that and skip the fixture.
- **2b.** Add `service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ContractShapeTest.java` (create_intent):
  - It reads `ActionContext.yaml` (or the bundled contract) from the test classpath.
  - It asserts that `properties.depth` has `minimum: 0` and NO `maximum` key.
  - Red first: commit it on its own, first with a deliberately failing assertion, or show red against a temporary `maximum` you then remove. Record the failing assertion message and both commit hashes in history.
  - Suggested, not required: also assert that `parameters/Depth.yaml` has default 2 and maximum 2, so the contract and `ActionContextQuery.DEFAULT_DEPTH` / `MAX_DEPTH` cannot drift.

You followed the stale worktree copy of the prompt in good faith and flagged it, which was right. The authoritative prompt now requires both items.

## Issue 3 (required, dead code / anti-pattern item 1): `ActionContextQuery.DEFAULT_DEPTH` has no production caller

T006 step 1 says "Default the depth to `ActionContextQuery.DEFAULT_DEPTH`". `ContextController.getActionContext` instead relies on the generated `@RequestParam(defaultValue = "2")` and unboxes `Integer depth` directly. Use the constant: `int requested = depth == null ? ActionContextQuery.DEFAULT_DEPTH : depth;`, then range-check and pass `requested`. This gives the constant a caller and removes the latent unboxing NPE. The other option is to remove the constant, but the prompt names it, so keep it and use it.

## Issue 4 (required, test strength): the random-graph invariant check can hide a broken `via`

In `ActionContextQueryTest.assertViaInvariant`, the line `current = byUrn.get(current) == null ? PLAN : byUrn.get(current).via();` treats any `via` that is not an entry as the action. It counts one step and stops. A `via` pointing at an unlisted node would therefore pass whenever the hop count happens to match.

- Assert instead that each `via` is either `PLAN` (and only for `relation == SCOPE` with hops 1) or a key of `byUrn`.
- Add a step bound, as `ActionContextSteps.assertChain` has, so that a `via` cycle fails the test instead of hanging it.
- Keep this check strict after the Issue 1 change.

## Issue 5 (required, precedence coverage, T005 step 6)

T005 lists "one both required and reached by `vocabulary` (first listing wins)". Only suggested-versus-vocabulary is covered: `glossary` in `anArtifactTheStepsCouldReachSeveralTimesKeepsTheEarliestStepsEntry`. Add the required-versus-vocabulary case: an artifact that is both `requires` and `vocabulary` from a scoped artifact must be listed as `requires`.

## Non-blocking notes

- `ActionContextSteps` re-GETs the path. That is acceptable as a stopgap until WP02's `ScenarioHttp`. The re-GET is not conformance-checked, though; only the scenario's own `When I GET` is, by the `CharterSteps` after-hook. It also does not assert the status. Add `assertThat(response.getStatus()).isEqualTo(200)` before parsing, so that a non-200 fails with a clear message rather than as "the answer lists guidance".
- `ContextController` Javadoc: `{@code MIN_DEPTH}` and `{@code MAX_DEPTH}` read better as `{@link ActionContextQuery#MIN_DEPTH}`. The line also exceeds 120 columns; check that palantir/Checkstyle are fine after the edit.
- The history note's list of 14 red scenario names should match the re-run. When you re-run red for Issues 1 and 2, record the names from the JUnit XML, not from memory.
- Shared files: `service.feature` and `scenarios/README.md` are WP02's and are recorded out-of-map in history. That is fine; keep the README line in step with any step change.
- The CHANGELOG list in your history must be updated. Item (7) currently says "suggests from everything in force"; make it "suggests from the scoped artifacts", and add the breaking fixture and ContractShapeTest.
