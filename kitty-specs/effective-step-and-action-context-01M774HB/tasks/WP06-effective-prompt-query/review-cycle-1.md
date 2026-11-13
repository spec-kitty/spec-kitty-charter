---
affected_files: []
cycle_number: 1
mission_slug: effective-step-and-action-context-01M774HB
reproduction_command:
reviewed_at: '2026-11-12T19:48:28Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP06
---

# WP06 review, cycle 1: changes requested

Reviewer: claude::reviewer-renata. Lane: lane-f at e2a09791c (and the merge 4163267d3).

## What passes

- `:packs:test` passes. The FULL `:deployable:test` passes: 188 classes, 1,740 tests, 0 failures, so the Spring
  context boots. A temporary probe in the 5-argument constructor, since reverted, printed
  `PROBE-5ARG-CHOSEN ...DomainRepositoryService` under `ConfigRootWiringTest`. The `@Autowired` 5-argument
  constructor is therefore the one Spring chooses.
- `spotlessCheck`, `checkstyleMain`, `checkstyleTest` and `javadoc` on `:packs` pass.
- Ordering (ADR-0066 d5). The id-only mutation fails 1 test, and the name-only mutation fails 2. The three-levels
  fixture also rejects level-then-id and level-then-pack-id. A `String::compareTo` mutation survives, but it is an
  equivalent mutant: ids follow `ArtifactId`'s ASCII pattern, and a descriptor `name` follows `^[a-z0-9][a-z0-9_-]*$`.
  This is not a defect.
- In-force gate (d4). Dropping the in-force test fails `anEnhancementHeldButNotInForceIsAbsent`. The losing copy is
  pinned by `onlyTheWinningCopyOfAnEnhancementIsServed`.
- Place (R8, A7). Reading the mission type winner's pack in place of `winnerOf(action:M/s)` fails 5 tests. The
  example-pack case compares the bytes with `Files.readAllBytes` of the stored companion.
- Commits are signed, with no trailer and no model name. The out-of-map edits are recorded.

## Issue 1 [MEDIUM]: the NFR-002 gate does not count the scan that could go quadratic

`EffectivePromptQuery.java:249-251` and `:264-271`. The gate counts slots, step-enhancement winners and fills, but not
the edges that `enhancersOf` reads. The per-pack memo (`computeIfAbsent`) is the only thing that keeps the query
linear, and the gate does not see it.

I planted this mutation:

```java
enhancersByPack.put(winner.packId(), enhancersOf(held, step));
if (!enhancersByPack.get(winner.packId()).contains(urn)) {
```

This rescans every edge of the pack for every in-force winner, which is O(winners × edges). Both NFR-002 tests still
pass. The gate therefore misses the most likely regression, so it gives a false green (QA_003). A planted quadratic
loop over the winners that does call `visits.visit()` is caught.

**Fix:** call `visits.visit()` for each edge or enhancement that `enhancersOf` reads (or that `stepEnhancementsOf`
indexes for it). Then confirm that the un-memoized mutation above fails both NFR-002 tests, and keep the ratio at
2.5 or below.

## Issue 2 [MEDIUM]: no fixture pins `STALE_INPUT` on the prompt read

`ClosureQueries.java:256-267` (`stepPrompt`) and the `@throws` in the Javadoc of `ClosureView.effectivePrompt` at
`ClosureView.java:661`.

The new read promises `STALE_INPUT` when the packs move after the view was verified. Nothing tests it, so deleting the
`checkUnchanged` call from `stepPrompt` leaves every test green. Sharing `checkUnchanged` with `read` is not enough on
its own:

- The call site is the thing that can regress.
- This path returns raw stored bytes. A stale read here serves a prompt from a pack that has moved, beside fragments
  taken from the verified facts. That is exactly the mixed answer the check exists to prevent.
- C-004 requires a failing fixture and a passing twin for every refusal.
- `ClosureViewGovernanceTest` (around l.320 and l.453) already pins the `effectiveArtifact` twin.

**Fix:** add a `ClosureViewTest` or `ClosureViewGovernanceTest` case. Change a closure pack after `allConfigured()`
and assert that `effectivePrompt` raises `CharterRefusal` with `STALE_INPUT`. Its twin, with the packs unchanged,
answers the prompt.

## Notes, not blocking (fold in at will)

- [LOW] `ClosureQueries.java:77`. The 4-argument constructor now has no production caller. Its callers are
  `ProfileGovernanceCorpusTest`, `ForeignArtifactReadTest`, `ClosureViewGovernanceTest` and `PacksProfileSourceTest`.
  A view built from it, or from the 3-argument constructor, fails loudly with `IllegalStateException` on its first
  prompt read. It is not a silent trap. Production reaches the 5-argument constructor through Spring, and the
  3-argument one through `RegistrationImpact.java:177`, whose staged views never read a prompt. Consider removing the
  4-argument constructor and moving its test callers to the 5-argument one. The charter's model rules discourage an
  old path kept beside a new one.
- [LOW] `ClosureView.java:679`. `names.getOrDefault(pack, pack.value())` would quietly sort by pack id if a winner's
  pack had no descriptor. Every closure pack has one, so the fallback cannot happen. Prefer to fail loudly
  (`IllegalStateException`) so that a wiring fault cannot quietly change the d5 order.
- [LOW] The red commit a98e46342 (`test(WP06)`) already carries the real `ClosureQueries.stepPrompt` body. Keep
  production logic out of the red commit.
