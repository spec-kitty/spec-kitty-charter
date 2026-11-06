---
work_package_id: WP03
title: CappedBodySubscriber tested in packs
dependencies: []
requirement_refs: [FR-003, FR-004, SC-002]
planning_base_branch: test-module-gaps-and-promise-scenarios
merge_target_branch: test-module-gaps-and-promise-scenarios
branch_strategy: Planning artifacts for this mission were generated on test-module-gaps-and-promise-scenarios. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test-module-gaps-and-promise-scenarios unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-test-module-gaps-and-promise-scenarios-01M6NDHM
base_commit: 9b59cb9b897851c713245f68da030ad88262946e
created_at: '2026-11-05T08:55:45.192431+00:00'
subtasks: [T009, T010, T011]
phase: Phase 1 - Module gaps
agent: claude
history:
- at: '2026-11-05T09:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/CappedBodySubscriberTest.java
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/CappedBodySubscriberTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP03 — CappedBodySubscriber tested in packs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

`CappedBodySubscriber` is the guide fetcher's fail-closed byte cap, an outbound network boundary, and it has no
test of its own (`packs.infra.fetch` has 77 % line coverage). Pin its behaviours directly (FR-003), each proved by a
planted break (FR-004).

## Branch Strategy

Planning base and merge target: `test-module-gaps-and-promise-scenarios`; worktree per computed lane.

```bash
spec-kitty agent action implement WP03 --agent claude --mission test-module-gaps-and-promise-scenarios-01M6NDHM
```

## Context

`service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/CappedBodySubscriber.java` (102 lines, package-private):

- `new CappedBodySubscriber(maxBytes)`: `onSubscribe` requests everything.
- `onNext`: accumulates bytes until `body.size() + buffer.remaining() > maxBytes`. Then it cancels the subscription
  and completes exceptionally with `BodyTooLargeException(maxBytes)`.
- After completion, further `onNext` calls are ignored.
- `refusing(maxBytes)`: on subscribe it cancels and fails at once, before reading (used when `Content-Length` already
  passes the cap).
- `onError` fails with the throwable; `onComplete` yields the bytes.

**Do not touch `HttpsGuideFetcherTest`.** PR #379 and #367's refactor list both cover it. Create the new class
`CappedBodySubscriberTest` in the same package.

## Subtask T009 — Tests with a hand-written `Flow.Subscription` (no mocks)

One recording subscription records `request(n)` and `cancel()`. Then:

1. Exactly at the cap, across several buffers: completes with the exact bytes; not cancelled.
2. One byte over the cap, in the last buffer: fails with `BodyTooLargeException` naming the cap; cancelled; the
   later `onComplete`/`onNext` change nothing (the result stays failed, with no partial body).
3. Over the cap in the first buffer: the same.
4. `refusing(cap)`: cancels on subscribe and fails before any `onNext`; no `request` is made.
5. `onError` after some bytes: fails with that throwable, not with a body.
6. An empty body (`onComplete` straight away): completes with zero bytes.

Prefer a parameterized test for 1–3 with realistic sizes (a 1 MiB cap and a guide-sized body).

## Subtask T010 — Planted breaks

In main code: change `>` to `>=` (at-cap refused); drop the `cancel()`; drop the `if (result.isDone()) return`; make
`refusing` request instead of cancel. Each must turn a test red. Record, revert; the main diff must end empty.

## Subtask T011 — Gates

`./gradlew :packs:spotlessApply`, then `:packs:spotlessCheck :packs:checkstyleTest :packs:test
-PrequireSymlinks=true -PopenPacksDir=/home/user/spec-kitty-open-packs`. The `:packs:test` count grows only by the
new class's tests.

## Definition of Done

- [ ] Six behaviours pinned; four planted breaks red and recorded; main diff empty; gates green.

## Reviewer guidance

Check that the subscription double is honest: it records, and asserts nothing hidden. Re-plant the `>=` break yourself.
