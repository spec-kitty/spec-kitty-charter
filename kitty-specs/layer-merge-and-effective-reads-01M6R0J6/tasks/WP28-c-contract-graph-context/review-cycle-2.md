---
affected_files: []
cycle_number: 2
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command: spec-kitty agent tasks move-task WP28 --to approved --mission layer-merge-and-effective-reads-01M6R0J6 --agent claude:opus:reviewer-renata
reviewed_at: '2026-11-09T15:15:47Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP28
---

Approved by claude:opus:reviewer-renata: approval:WP28

# WP28 review, cycle 2 (claude:opus:reviewer-renata)

Lane-z head `69afd60a`: fixes red `302a475c` and fix `0f439309`, then the planning merges of `93ad894f` and `0714ba9a`,
which touch planning files only.

## Verdict: APPROVE

All dispositions are met, the red commit fails for the right reason, the MINOR-1 pins bite, and the gates are green.
Two NITs remain and need no further review cycle.

## What I applied

The same profile and charter context as in cycle 1 (reviewer-renata; DIRECTIVE_010, QA_003_NO_FAKE_GREEN,
NO_FULL_HEAVY_SUITES_IN_MISSION). Decision `01M70CTZP7P2EHSCK2G0RET74B` was read in full.

## Check 1: the dispositions

| Disposition | Result |
|---|---|
| MINOR-1, the adapter-level once-per-request pin | Met. `theInForceStatesOfOneRequestAreResolvedOnceForTheSetTheGraphAndTheWalk` runs inForce, graph (the same request, other order) and actionContext, then asserts `isSameAs` the first states. `aDifferentRequestOfTheSameSessionResolvesAgain` is the control (`isNotSameAs`, then `isSameAs`). **Confirmed to bite:** in a temporary worktree at `0f439309` I made the `statesOf` condition always true (`... \|\| request.equals(resolvedFor)`; error-prone refuses a constant `true`). Both tests fail with "Expecting actual: ...", 2 of 21. With the cache restored they pass, as the gate run shows. The pins read `statesOf` (package-private now), not a count of `view.inForce`. That is enough: a second resolution cannot give the same instance. |
| MINOR-2, decision `01M70CTZ...` | Met, with no second copy of the rule. `EffectiveSet.suggested` now filters on `artifact.suggestion().isPresent()`, the listing's own carrier that `suggestionsOf` fills over every artifact in force, and only replaces the suggestion's `when`/`reason` with the walk's edge. The "reached from a listed entry through suggests" half stays with the walk (WP27). So context suggestions are a subset of listing suggestions, as the decision rules. The `EffectiveActionContext.yaml` description and the Javadoc state the rule. The new test has X in force outside the context with `X requires S`: S gets no listing suggestion and no context suggestion, and S2 is the control in the same set. |
| MINOR-3 | Accepted as a record by the runner: the closeout commit message lists the shared files. Nothing to check. |
| MINOR-4, 404 before 304 | Met. `EffectiveReadService.actionContext` calls `session.requireAction(mt, action)` before computing and comparing the tag. `ClosureViewSession.requireAction` asks `view.actionContext` with empty `InForceStates` at depth 0. The packs walk judges the names on the merged closure before the not-in-force check (WP27 contract), so it neither resolves nor walks. Service test: an unknown action with its matching tag raises `ACTION_NOT_FOUND`, with no walk and no merge, and a known name with its tag still answers `NotModified`. Adapter test, on a real merged closure: `missing/s` gives `MISSION_TYPE_NOT_FOUND`, `M/missing` gives `ACTION_NOT_FOUND`, and `M/s` passes as the control. The `Unresolved` session's no-op is unreachable because `open()` refuses with `pack_unreadable` first, as pinned in cycle 1. |
| NITs | Mostly met: `HexagonalArchitectureTest`, `ClosureViewSessionStepRefsTest` and the `EffectiveTag` Javadoc are reflowed. Two new NITs follow. |

## Check 2: red first (temporary worktree `/tmp/claude-0/review-wt-z`, removed)

| Red commit | Run | Result | Green |
|---|---|---|---|
| `302a475c` | `:activations:test` | 3 of 786 fail on assertions: `EffectiveReadServiceTest.anUnknownNameIsRefusedBeforeTheTag...` and `ClosureViewSessionStepRefsTest.theNamesOfAnActionAreJudged...` ("Expecting code to raise a throwable", from the no-op `requireAction` stub); `EffectiveSetTest.anArtifactAnotherArtifactInForceRequiresOutsideTheContext...` ("Expecting an empty Optional but was containing value: EffectiveArtifact[...S...]"). The right reasons. | `0f439309` |
| MINOR-1 pins | They were never red, since the cache already existed. I mutated the cache off at `0f439309`. | Both fail as described above, so they are not vacuous. | n/a |

## Check 3: fixture edits

- `EffectiveSetTest` other-reason case: each denied, overridden, filtered, unbound or ungrounded artifact now also gets a
  `suggests` edge from an in-force `D`. This **strengthens** the negative: without the edge, the old fixture passed under
  either rule. The assertions are unchanged: each other-reason artifact is still empty and `Unselected` is still present.
- `EffectiveSetTest.anEdgeThatStatesNeitherWhenNorReason...`: adds an in-force `D` that suggests `S` with null when and reason, which the rule
  needs. The assertion is unchanged.
- `EffectiveReadServiceTest` and `EffectiveReadHttpTest` fixtures: add the `A suggests B` edge to the merged snapshot, so that B's listing
  suggestion exists. The assertions are unchanged; the expected context suggestion for B is still asserted.
- `EffectiveContextTest` helper: a NOT_SELECTED artifact now carries a listing suggestion, other reasons carry none. This adapts
  the fixture to the rule, and the other-reason negatives still bite. A not-selected artifact that the listing denies a suggestion is now
  tested only in `EffectiveSetTest` (the new case), which is the class that owns the rule. That is acceptable.
- No assertion was removed or relaxed. I checked the `0f439309` test diff line by line.

## Findings

- **[NIT]** `service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSession.java:104-106` - The new
  `requireAction` comment is broken mid-sentence ("in force" / "the" / "walk stops there"), the same formatter artifact as
  cycle 1. Reflow it.
- **[NIT]** `contracts/charter/schemas/EffectiveActionContext.yaml:8` - One description line is now about 190 characters, where the file wraps
  at about 120. Rewrap it.

## Check 4: gates on the lane head `69afd60a`

`JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=/home/user/open-packs :contracts:check :activations:check :packs:check :deployable:check :client-java:test`

**BUILD SUCCESSFUL in 1m 40s** (89 tasks, 11 executed: deployable:test ran, the rest were up to date with matching inputs).

| Module | Tests | Failed | Skipped |
|---|---|---|---|
| activations | 786 | 0 | 0 |
| deployable | 1201 | 0 | 0 |
| packs | 4221 | 0 | 4 (existing filesystem/symlink skips) |
| client-java | 19 | 0 | 0 |
| contracts | oasdiff, vacuum, changelog fragments, compatibility: pass | | |

Log: `/tmp/claude-0/wp28-gate.log` (cycle 1) and `/tmp/claude-0/wp28-c2-gate.log` (cycle 2).
