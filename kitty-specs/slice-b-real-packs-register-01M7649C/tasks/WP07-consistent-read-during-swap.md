---
work_package_id: WP07
title: Verified reads accept an unchanged read during a swap
dependencies:
- WP06
requirement_refs:
- FR-023
planning_base_branch: feat/466-slice-b-real-packs-register
merge_target_branch: feat/466-slice-b-real-packs-register
branch_strategy: Planning artifacts for this mission were generated on feat/466-slice-b-real-packs-register. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/466-slice-b-real-packs-register unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-slice-b-real-packs-register-01M7649C
base_commit: 8b0e2e13a230939d4c39492377f2dcb1b5f8c826
created_at: '2026-11-12T05:46:17.489760+00:00'
subtasks:
- T034
- T035
phase: Phase 4 - The swap window
history:
- at: '2026-11-11T21:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/application/VerifiedReadDuringSwapTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ConsistentRead.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ConsistentReadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/VerifiedReadDuringSwapTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP07 - Verified reads accept an unchanged read during a swap

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` (or read `java-jenny.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/a79c36318b7a506ca2ab26d74705c5e3e406004f/packs/built-in/agent_profiles/`) and adopt
it.

## Objective

FR-023, a **mission call flagged to the operator** (squad D3): `ConsistentRead.read` refuses `STALE_INPUT` (HTTP 500,
`RefusalHandler.java:385`) as soon as the write generation is odd (`ConsistentRead.java:96-99`), so every verified read
(artifacts, closures, summaries, effective reads) still answers 500 in the window #490 closes, whatever WP06 does. Accept
a read that starts and ends in the **same odd generation** when the digests it read before and after are equal: writers
are serialized by the registry lock, so one odd window moves old to new once, and equal digests prove the load saw one
state. Everything else keeps today's behavior. This WP is one commit, revertible on its own.

## Context

`service/packs/src/main/java/ai/speckitty/charter/packs/application/ConsistentRead.java:26-130`, `PackWriteGeneration`
(a seqlock), `ClosureQueries.java:107-110` (`awaitWrites`), `CompositionFactsCache.java:113-115`. Read the existing
`ConsistentReadTest` first (find it with `grep -rl ConsistentRead service/packs/src/test`).

## Subtasks

### T034 - The rule

In `ConsistentRead.read`: today, an odd generation at the start (or a changed generation at the end) refuses or
retries. New: if the generation is odd at the start, read; at the end, if the generation is the same odd value and the
digest the read computed before equals the digest after, accept. If the generation changed (the swap ended or another
began), retry as today (bounded as today). Do not wait on a lock. Javadoc: cite #490, the operator call, and why equal
digests in one odd window prove a single state. Leave `ClosureQueries.awaitWrites` as is unless a test shows it answers
500 in the window; if it does, record that in the activity log and stop for the orchestrator.

### T035 - Tests

- `ConsistentReadTest`: (a) odd generation, unchanged digests, same generation → accepted; (b) odd, digests differ →
  retried/refused as today; (c) odd at start, even at end → retried as today; (d) even path unchanged.
- `VerifiedReadDuringSwapTest`: on the WP06 phase hook, pause at `afterAside` and run one verified read (an artifact item
  read through its read service, e.g. `DirectiveReadService`, or `PackSummaryQuery`); it answers R0, no 500.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :packs:test
```

## Branch Strategy

Planning base and merge target: `feat/466-slice-b-real-packs-register`. Implement with
`spec-kitty agent action implement WP07 --agent claude --mission slice-b-real-packs-register-01M7649C`. One commit,
`fix(packs): a verified read during a swap answers the state it read (#490)`, signed, no model trailers.

## Definition of Done

Tests green; the commit reverts cleanly on its own.
