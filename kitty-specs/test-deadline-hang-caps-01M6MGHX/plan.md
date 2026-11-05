# Implementation Plan: Deadline checks become hang caps

**Branch**: `test-deadline-hang-caps` (merges to `main` by draft PR) | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/test-deadline-hang-caps-01M6MGHX/spec.md`

## Summary

Eight wall-clock bounds in five `packs` test files become 30 s hang caps (operator ruling, decision
`01M6MGJA27NG72JCTBXB1TWG80`). They stay in `:packs:test`, untagged (ADR-0057). Each test keeps or gains an
assertion of the cause it proves. The two fetcher tests lengthen their slow work (the lookup's sleep, the
body's drip) to 60 s, so a broken deadline would let the work outlast the cap rather than finish within it.
A planted break per guard proves the tests still go red.

## Technical Context

**Language/Version**: Java 25 (Temurin 25.0.5), JUnit Jupiter 6, AssertJ; Gradle wrapper 9.8.1
**Primary Dependencies**: none new
**Storage**: N/A (test code only)
**Testing**: `./gradlew :packs:test -PrequireSymlinks=true`, `./gradlew verifyAll -PrequireSymlinks=true`
**Target Platform**: Linux CI runners (and the cloud session)
**Project Type**: single (multi-module Gradle build)
**Performance Goals**: `:packs:test` wall time on a green run grows by at most 5 s (NFR-001)
**Constraints**: no `timing` tag; no new tests; no main-code change; do not touch `HttpsGuideFetcherTest.send` (#367)
**Scale/Scope**: 8 bounds in 5 files; 2 fixture durations

## Charter Check

| Charter rule | Status |
|---|---|
| Test first; no fake green (`QA_003`) | PASS: FR-003 planted break per guard |
| Tests as scaffold (`DIRECTIVE_041`) | PASS: no test added; bounds stop failing on slowness alone |
| ADR-0057 (budgets never block a PR) and the service build guide | PASS: these are deadline checks, not budgets; they stay untagged in `:packs:test` |
| Gates: `verifyAll` on Java 25 | PASS: planned |

Post-design re-check: unchanged.

## Project Structure

```
service/packs/src/test/java/ai/speckitty/charter/packs/infra/
├── storage/ParsedYamlTest.java              # 130, 146, 153: assertTimeoutPreemptively 2 s -> 30 s
├── storage/EmbeddedSchemasTest.java         # 80: assertTimeoutPreemptively 5 s -> 30 s
├── storage/InterruptedCommitRepairTest.java # 166 succeedsWithin 10 s -> 30 s; 200 assertTimeoutPreemptively 10 s -> 30 s
├── fetch/HttpsGuideFetcherTest.java         # 540: < 2 s -> < 30 s; lookup sleep 5 s -> 60 s
└── fetch/HttpsGuideFetcherLoopbackTest.java # 93: < 5 s -> < 30 s; drip 10 s -> 60 s
```

**Amendment (2026-11-05)**: the structure above is the plan as first merged. Op `01M6NB6N` replaced the
fetchers' `< 30 s` elapsed checks with `assertTimeoutPreemptively` at the cap, and moved the fetcher's other two
2 s bounds and the 10 s helpers of `InterruptedCommitRepairTest` to the cap. The landing pass on PR #379 made the
lock-skip test assert that the listing never waits on the held lock, lengthened the ReDoS input of
`EmbeddedSchemasTest` to 48 letters so a backtracking engine cannot finish within the cap, and dropped the
fragment parse check in `ParsedYamlTest` and the slow-lookup interrupt check, which repeated sibling checks.

No `contracts/` or `data-model.md`: no interface or data change (`meta.json` records `contracts: none`).

## Implementation Concern Map

### IC-01 — Storage guards: alias budget, linear-time patterns, commit lock

- **Purpose**: the six storage bounds become 30 s caps; the cause assertions stay.
- **Relevant requirements**: FR-001, FR-003, SC-001, SC-002
- **Affected surfaces**: `ParsedYamlTest`, `EmbeddedSchemasTest`, `InterruptedCommitRepairTest`
- **Sequencing/depends-on**: none
- **Risks**: a planted break of a preemptive-timeout guard leaves a runaway thread. Plant it, record the
  failure and the thread behaviour, revert. Name the cap once per file (a constant) rather than repeating 30.

### IC-02 — Fetcher deadline: lookup and body

- **Purpose**: the two fetcher bounds become 30 s caps over 60 s slow work; the `TIMEOUT` refusal stays asserted.
- **Relevant requirements**: FR-002, FR-003, SC-001, SC-002, NFR-001
- **Affected surfaces**: `HttpsGuideFetcherTest` (only `aSlowLookupCountsAgainstTheSameDeadline`), `HttpsGuideFetcherLoopbackTest`
- **Sequencing/depends-on**: none
- **Risks**: lengthening the drip could hold the loopback server thread past the test; `@AfterEach` calls
  `server.stop(0)`, which must still return promptly (measure). Do not touch the nested `send` class (#367).
