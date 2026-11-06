# Implementation Plan: Module-local test gaps and promise-first scenario verdicts

**Branch**: `test-module-gaps-and-promise-scenarios` (merges to `main` by draft PR) | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/test-module-gaps-and-promise-scenarios-01M6NDHM/spec.md`

## Summary

Three module-local test gaps (#370) get direct tests, each proved by a planted break. The 27 promise-first
scenarios (#374) get a verdict each from the QA pack's `qa-coverage-gap-analysis`. The gaps it finds go to the
operator in one ruling, before their work packages are written.

## Technical Context

**Language/Version**: Java 25 (Temurin 25.0.5), JUnit Jupiter 6, AssertJ; Cucumber-JVM (existing `CharterContractSuite`); Gradle wrapper 9.8.1
**Primary Dependencies**: none new
**Storage**: N/A
**Testing**: `./gradlew :kernel:test :profiles:test :packs:test :deployable:test`, `./gradlew pitest --continue`, `./gradlew verifyAll -PrequireSymlinks=true`
**Target Platform**: Linux CI runners and the cloud session
**Project Type**: single (multi-module Gradle build)
**Performance Goals**: new tests add ≤ 5 s summed test time per module (NFR-002)
**Constraints**: tests only for #370 (C-002); strengthen before adding (C-001, `DIRECTIVE_041`); no threshold (C-003); wired scenarios join `CharterContractSuite` (C-004)
**Scale/Scope**: about 6 kernel.validation classes, 1 profiles aggregate and 2 records, 1 fetch class; 27 scenarios in 7 feature files

## Charter Check

| Charter rule | Status |
|---|---|
| Test first; no fake green | PASS: FR-004 planted break per new test |
| Tests as scaffold (`DIRECTIVE_041`) | PASS: C-001; no test whose only purpose is a mutant |
| BDD / specification by example; `contracts/charter/scenarios/` drives the implementation | PASS: FR-005 maps each promise to the contract scenarios, or wires it into them |
| Gates: `verifyAll` on Java 25 | PASS: planned |

## PIT inventory (baseline, `4b52bcb`)

- **kernel.validation, no coverage:**
  - `Constrained`: `adheresTo`, `ifValid` ×2, `isValid` ×2, `rationale`
  - `Checks`: `haveNonBlank`, `haveNonNullField`, `haveNullField` and their lambdas (10 mutants)
  - `ModularRuleset`: `emptyRules`, `must`
- **kernel.validation, survived:** `Constraints.isNotBlank`, `Constraints.of`, `Rationale.isFailing`. The only
  test class today is `ModularRulesetTest`.
- **profiles:** all 29 uncovered mutants sit in `AgentProfile` (16 accessors, `hashCode`, `toString`) and
  `AgentProfile.Builder` (13). No profile test checks that a built profile keeps every field. Two survivors in
  `AgentProfile`. `SelfReviewStep` and `ModeDefault` are records whose only behaviour is refusing a null field.
- **packs.infra.fetch:** `CappedBodySubscriber` (102 lines) has five behaviours: accept at the cap, refuse one
  byte over and cancel, `refusing(…)` cancels at once on subscribe, `onError` fails, and `onComplete` yields the
  bytes. It also ignores `onNext` after the result is done.

## Implementation Concern Map

### IC-01 — kernel.validation tested in kernel

- **Purpose**: `Constrained`, `Rationale`, `Checks`, `ModularRuleset`, `Constraints` tested through their public API with realistic values.
- **Relevant requirements**: FR-001, FR-004, SC-001, SC-002
- **Affected surfaces**: `service/kernel/src/test/java/ai/speckitty/charter/kernel/validation/` (new `ConstrainedTest`, `RationaleTest` or a strengthened `ModularRulesetTest`; the implementer decides by C-001)
- **Sequencing/depends-on**: none
- **Risks**: tests that merely restate implementation. Each test must state the invariant in its name and have a same-fixture positive control.

### IC-02 — profiles tested in profiles

- **Purpose**: a profile built with every field reads every field back, equality and hash follow the fields, and a missing required field is refused (`SelfReviewStep`, `ModeDefault` through the builder or parser entry point).
- **Relevant requirements**: FR-002, FR-004, SC-001, SC-002
- **Affected surfaces**: `service/profiles/src/test/java/ai/speckitty/charter/profiles/domain/AgentProfileEntityTest.java` (strengthen) and at most one new test class
- **Sequencing/depends-on**: none
- **Risks**: the parallel session's activation work may touch `profiles`; check before the PR (C-005).

### IC-03 — CappedBodySubscriber tested in packs

- **Purpose**: the five behaviours above, driven by a hand-written `Flow.Subscription` (no mocks).
- **Relevant requirements**: FR-003, FR-004, SC-002
- **Affected surfaces**: new `service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/CappedBodySubscriberTest.java`
- **Sequencing/depends-on**: none
- **Risks**: do not touch `HttpsGuideFetcherTest` (#367 and PR #379 overlap).

### IC-04 — Promise-first scenario verdicts

- **Purpose**: apply the 27 verdicts from research R-04 (map, wire or retire); update both READMEs; act on the operator's gap ruling.
- **Relevant requirements**: FR-005, FR-006, SC-003
- **Affected surfaces**: `src/test/resources/features/**`, `tests/scenarios/charter/**`, possibly `contracts/charter/scenarios/**` and their step definitions (for WIRE rows), the verdict record under this mission's `research/`
- **Sequencing/depends-on**: research R-04 and the operator's ruling (decision recorded before tasks)
- **Risks**: wiring a scenario changes the contract suite. Keep `@contract` semantics and the copy rule.
