---
acknowledged: true
acknowledged_by: claude::java-jenny
acknowledged_at: '2026-11-05T07:20:00Z'
affected_files: []
cycle_number: 1
mission_slug: test-deadline-hang-caps-01M6MGHX
reproduction_command:
reviewed_at: '2026-11-05T07:12:07Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP01
---

# WP01 review, cycle 1: changes requested (reviewer-renata)

## Blocking

**Issue 1: with the cap at 30 s, a broken alias budget no longer fails the alias-bomb tests cleanly (User Story 2, FR-003, #320).**

How I reproduced it: I set `ParsedYaml.MAX_NODES` to `1_000_000_000` (Error Prone rejects `Integer.MAX_VALUE`). I ran only
`ParsedYamlTest.anAliasBombIsAPackUnreadableForPackYamlWithinTheTimeout` and then reverted. Result: after about 18 s,
Jackson's tree building throws `OutOfMemoryError: Java heap space`. JUnit rethrows the OOM as unrecoverable,
the Gradle Test Executor fails ("Could not complete execution"), and the XML marks the test `<skipped/>`, not
failed. This matches the implementer's plant1/plant1b logs (12–14 s).

The build does go red. But the failure names neither the 30 s cap nor the wrong cause, as the acceptance
scenario requires ("fails within the cap plus tear-down, naming the cap or the wrong cause"). It also aborts the
whole test fork. In a full `:packs:test` run, every test still queued in that executor is lost, so the report
hides the real regression. When the run fails depends on the heap size, not on the guard. The old 2 s preemptive
bound failed before the heap ran out. Widening it removed the only clean signal and put nothing in its place.
This is the "widen a bound until it passes" outcome that #320 and C-003 warn against.

**Fix (small, inside WP scope: test-only, owned file, no test added or removed, no main change):** #320 asks you
to assert the cause rather than the clock where a seam allows, and `ParsedYaml.parse` is that seam. The test
already calls it directly in `aTooDeepDocumentIsRefusedBeforeItIsComposedAndTheStackIsNotCaught`. In both
alias-bomb tests, before the reader call, assert that the node budget itself refuses the bomb, under `HANG_CAP`:

```java
ParsedYaml parsed = assertTimeoutPreemptively(HANG_CAP, () -> ParsedYaml.parse(DESCRIPTOR + aliasBomb(16), "pack.yaml"));
assertThat(parsed.failure()).as("the node budget refuses the bomb before anything walks it").isNotNull();
assertThat(parsed.failure().firstLine()).isEqualTo(TOO_LARGE);
```

(In the shard/fragment test, use the shard text, `HEADER + "nodes: []\n" + bomb`.) `parse` only counts the
expansion and builds no tree, so with the budget broken it returns with no failure instead of exhausting the
heap. I tried this edit with the plant in place and then reverted it. The test failed cleanly in **4.1 s**:
`AssertionError: [the node budget refuses the bomb …] Expecting actual not to be null`. It was reported as
FAILED, not skipped, and the executor survived. Keep every existing reader and finding assertion unchanged after
it. Then re-run the alias-budget plant for **both** alias-bomb tests and record the clean red and its time.

## Non-blocking observations

- Lock-skip plant (plant3): the test goes red in about 10 s. The failure is not the cap. It is the holder
  thread's `await(release)` latch, which uses the 10 s fixture helper `await`, and its message is a bare
  "Expecting value to be true but was false". This counts as a bounded red with the wrong cause, so I accept it.
  It is a better record if the PR states that the latch, not `HANG_CAP`, ends the planted deadlock.
- ReDoS plant (plant2) fires the cap at 30 s, and swap-wait (plant4) fails on the cause (`pack pack-a is not a
  configured pack`). Both are good.

## Checks that passed

- Diff scope: only the three owned test files changed. Six bounds are now `HANG_CAP` (30 s). Cause assertions
  are unchanged. No `timing` tag. No `@Test` added or removed. No main code changed. The PR #377 hunks (around
  339 and 382) are untouched.
- `:packs:spotlessCheck :packs:checkstyleTest` green. `:packs:test --rerun` green: 2743 tests, 0 failures, 4
  skipped (none of them these tests).
- The commit carries an attribution trailer naming the tool that wrote it, and no model id appears in the code or docs.

## Anti-pattern checklist
1 Dead code N/A · 2 Synthetic fixture PASS · 3 Silent empty return N/A · 4 FR coverage: FR-001 PASS, FR-003 FAIL
(the alias-budget plant does not fail cleanly; see Issue 1) · 5 Frozen surface PASS · 6 Locked decision PASS · 7 Shared-file
ownership PASS (no overlap with lane-b) · 8 Production fragility N/A
