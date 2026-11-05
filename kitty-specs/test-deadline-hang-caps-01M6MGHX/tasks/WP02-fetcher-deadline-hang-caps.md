---
work_package_id: WP02
title: Fetcher deadline checks get 30 s hang caps over 60 s slow work
dependencies: []
requirement_refs:
- FR-002
- FR-003
- SC-001
- SC-002
- SC-003
planning_base_branch: test-deadline-hang-caps
merge_target_branch: test-deadline-hang-caps
branch_strategy: Planning artifacts for this mission were generated on test-deadline-hang-caps. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test-deadline-hang-caps unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-test-deadline-hang-caps-01M6MGHX
base_commit: 5c700122ac33f4371ada645de65dbc187a62b931
created_at: '2026-11-05T06:46:32.992924+00:00'
subtasks:
- T005
- T006
- T007
- T008
phase: Phase 1 - Hang caps
agent: claude
history:
- at: '2026-11-05T07:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/HttpsGuideFetcherTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/HttpsGuideFetcherLoopbackTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP02 — Fetcher deadline checks get 30 s hang caps over 60 s slow work

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

The fetcher's two elapsed-time checks become **30 s hang caps** (decision `01M6MGJA27NG72JCTBXB1TWG80`), and the
slow work they race (a DNS lookup that sleeps; a body that drips) lengthens to **60 s**, so that a broken deadline
lets the work outlast the cap instead of finishing within it. The `TIMEOUT` refusal stays asserted. Never tag
them `timing` (ADR-0057). (FR-002, FR-003, NFR-001.)

## Branch Strategy

Planning base and final merge target: `test-deadline-hang-caps`. The worktree is allocated per computed lane.

```bash
spec-kitty agent action implement WP02 --agent claude --mission test-deadline-hang-caps-01M6MGHX
```

## Context

| Site (main `85c20fa`) | Today | Proves |
|---|---|---|
| `HttpsGuideFetcherTest:520-542` `aSlowLookupCountsAgainstTheSameDeadline` | 200 ms deadline; the lookup sleeps 5 s; elapsed < 2 s; `Reason.TIMEOUT`; no request sent | the deadline covers DNS resolution |
| `HttpsGuideFetcherLoopbackTest:81-95` `aBodyThatDripsPastTheDeadlineIsRefused…` | 1.5 s deadline; the body drips for 10 s; elapsed < 5 s; `Reason.TIMEOUT`; headers seen | the deadline covers a slow body after fast headers |

**Do not touch** the nested `send` class in `HttpsGuideFetcherTest`: it is on #367's refactor list, owned by
draft PR #368's mission. Edit only the one method.

## Subtask T005 — Measure

Run both tests (`--tests`) on current code and record each test's duration from the JUnit XML, and the time
`@AfterEach server.stop(0)` takes in the loopback test.

## Subtask T006 — Caps over longer slow work

1. `aSlowLookupCountsAgainstTheSameDeadline`: lookup sleep 5 s → 60 s; elapsed bound 2 s → 30 s, through a
   `HANG_CAP` constant with a one-line comment (a cap far above the deadline's own time; the guard is proved by
   `TIMEOUT` and by the lookup being cut off, not by the clock; #320). Keep `Reason.TIMEOUT` and
   `transport.requests` empty.
2. Loopback: the drip's total duration 10 s → 60 s (read `drip(HttpExchange)` around line 127 to see how it is
   built); elapsed bound 5 s → 30 s via `HANG_CAP`; update the comment at line 92. Keep `TIMEOUT` and
   `seenHeaders`.
3. NFR-001: a green run must not get slower by more than 5 s per class, because the deadline cuts the slow work
   off. Prove that the 60 s sleep thread and the 60 s drip do not delay the test or `@AfterEach`. If
   `server.stop(0)` or an executor waits for the drip, make the drip interruptible or stop promptly instead.
   Record before and after durations.

## Subtask T007 — Planted-break proof (FR-003)

- **Lookup**: in `HttpsGuideFetcher`, plant a break so the lookup no longer counts against the deadline (for
  example, wait for the lookup without a timeout). Expect the test to fail: elapsed above 30 s, or not `TIMEOUT`.
- **Body**: plant a break so the body read no longer counts against the deadline. Expect the loopback test to
  fail at the cap.
- Record each failing output and the elapsed time. Revert, and confirm `git diff -- service/packs/src/main` is
  empty.

## Subtask T008 — Gates

`./gradlew :packs:spotlessApply`, then `:packs:spotlessCheck :packs:checkstyleTest :packs:test
-PrequireSymlinks=true -PopenPacksDir=/home/user/spec-kitty-open-packs`. The execution count must be unchanged,
with no `timing` tag.

## Definition of Done

- [ ] Both bounds `HANG_CAP` (30 s); slow work 60 s; `TIMEOUT` still asserted; no `timing` tag; no main change.
- [ ] Green-run durations before and after recorded (NFR-001 ≤ 5 s growth); planted breaks red and recorded.
- [ ] `HttpsGuideFetcherTest.send` untouched; gates green.

## Reviewer guidance

Diff `HttpsGuideFetcherTest` and confirm only the one method changed. Time the green run yourself. Re-plant one
break yourself.
