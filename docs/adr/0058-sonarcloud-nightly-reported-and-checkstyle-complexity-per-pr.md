---
title: "ADR-0058: SonarCloud runs nightly and report-only; complexity is gated per pull request by Checkstyle"
description: "Static analysis beyond correctness: a nightly, never-required SonarCloud scan with new-code coverage, and Checkstyle complexity rules in the per-PR gate."
doc_status: active
updated: 2026-11-12T00:00:00Z
decision_status: Accepted; the description of mutation-nightly amended by ADR-0062 (2026-11-06)
decision_date: '2026-11-03'
type: ADR
kind: ADR
related:
  - plans/engineering-notes/test-suite-assessment-2026-11-03
  - adr/0023-ci-gate-required-check
  - adr/0047-generated-indexes-and-merge-queue
  - adr/0057-performance-budgets-never-block-a-pull-request
  - adr/0062-nightly-mutation-floor
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for which complexity rules block a pull request and where the nightly SonarCloud result is reported.
  - profile: governance-lead
    guidance_text: Read this for why SonarCloud is reported and not required, and what it costs.
---

# ADR-0058: SonarCloud runs nightly and report-only; complexity is gated per pull request by Checkstyle

## Status

**Accepted**, 2026-11-03. Decider: Stijn Dejongh (operator). The operator ruled, on the
[test suite assessment](/plans/engineering-notes/test-suite-assessment-2026-11-03/), "Nightly Sonar,
per PR CheckStyle", with new-code coverage reported through SonarCloud instead of a coverage
threshold.

This ADR records that ruling. The mission `ci-sonar-nightly-checkstyle-complexity` (#368) delivers
the nightly SonarCloud workflow. #367 refactored the methods over the complexity limits, and #371
turned the limits on (2026-11-12).

## Context

The build's static gates block on correctness and nullness:

- `-Xlint:all -Werror`;
- Error Prone, and NullAway in JSpecify mode on every `@NullMarked` package;
- Checkstyle, with no suppressions;
- Spotless;
- the ArchUnit onion rules;
- the contract lint and breaking-change check;
- dependency verification.

None of them measures size: there is no complexity, method-length, nesting or duplication rule, and
no security-hotspot analysis. The `testing-principles` styleguide says such metrics belong to static
analysis, not to tests.

JaCoCo produces an aggregated XML report as part of `check`. `service.yml` uploads it, with no
threshold, as FR-008 of the contract-and-build mission asked.

Unlike [ADR-0057](/adr/0057-performance-budgets-never-block-a-pull-request/)'s performance budgets,
which measure shared hardware, complexity rules are deterministic static analysis, so they can gate a
pull request.

Core runs SonarCloud on two surfaces. Both are report-only, outside the required
`aggregate-gate`:

- a nightly `sonar.yml`, which compiles Java only on trusted, merged code;
- a per-PR `sonar-pr` job that analyzes Python without compiling it.

Java analysis needs compiled classes (`sonar.java.binaries`). Core deliberately never compiles a
pull request's build next to `SONAR_TOKEN`.

This repository already has the local shape to follow: `.github/workflows/mutation-nightly.yml`
runs nightly and on manual dispatch, has no pull-request trigger and gates nothing. The SonarCloud
workflow follows it.

*2026-11-06: amended by [ADR-0062](/adr/0062-nightly-mutation-floor/): the mutation nightly now turns red below a floor; it still gates no pull request.*

The required-check rules this must respect:

- [ADR-0023](/adr/0023-ci-gate-required-check/) makes `CI gate / gate` the one required check, and
  sets the precedent that advisory jobs run with `continue-on-error`.
- [ADR-0047](/adr/0047-generated-indexes-and-merge-queue/) keeps workflows that produce no required
  check off `merge_group`.

## Decision

1. **Checkstyle gates complexity per pull request.** `config/checkstyle/checkstyle.xml` gains
   `CyclomaticComplexity`, `NPathComplexity`, `MethodLength` and `NestedIfDepth`.
   - **Limits.** As ruled by the operator: cyclomatic complexity 15, NPath 1000, 60 lines per
     method and nested-if depth 1.
   - **No grandfathering.** Operator ruling of 2026-11-04. The existing methods over a limit,
     listed in [#367](https://github.com/spec-kitty/spec-kitty-charter/issues/367), are
     refactored below the limits before the gate turns on, so #367 is a prerequisite of the gate.
     They get no suppression and no baseline entry.
   - **The register is unchanged.** `config/checkstyle/suppressions.xml` stays the one register of
     exceptions, for "the code is right and the rule is wrong", each with a sunset date. It is not
     a place to park existing over-limit methods.
   - **The register is checked for its own fields only.** `checkCheckstyleRegister`, part of `check`,
     fails on an entry lacking `rule:`, `file:`, `reason:` or `sunset:`, on a passed sunset date and on a
     `Size:` header that does not count the entries, comment-only entries included. It runs no probe and does not detect a stale
     entry.
   - The rules run inside `verifyAll`, so `CI gate / gate` enforces them with no new job or tool.
2. **SonarCloud runs nightly and is report-only.** A new `.github/workflows/sonar.yml` runs on a
   schedule and on manual dispatch, on `main` only.
   - **Analysis.** It runs `verifyAll` on trusted code, then the SHA-pinned generic
     `SonarSource/sonarqube-scan-action` that core uses. No Gradle plugin is added.
   - **Inputs.** It passes the modules' `build/classes/java/main` as `sonar.java.binaries`, and the
     aggregated report as `sonar.coverage.jacoco.xmlReportPaths`. It excludes the generated
     `charter-api`.
   - **Report-only.** Every step after the token probe is `continue-on-error`. The quality-gate result goes to the job
     summary. The job is never part of `CI gate / gate` and never runs on `merge_group` or a pull
     request.
   - **Without a token.** It skips green with a notice when `SONAR_TOKEN` is absent, so it can land
     before the SonarCloud project exists.
   - **Supply chain.**
     - The action is pinned by full commit SHA, with a version comment.
     - The action's SHA pin also fixes the scanner version: the workflow leaves the action's
       `scannerVersion` input at its default.
     - The job has `permissions: contents: read`.
     - `SONAR_TOKEN` is exposed only to the token probe, the scan and the quality-gate steps, never
       to the build or `verifyAll` steps.
     - The scanner and SonarCloud's analyzers are analysis tools outside the build's dependency
       graph, so the build's strict SHA-256 dependency verification is unchanged.
3. **Coverage stays a report.** No JaCoCo threshold is added. New-code coverage is read from
   SonarCloud's new-code period on `main`.

## Consequences

### Positive

- Complexity regressions block in the gate the contributor already runs, with no new tool and no
  token.
- Duplication, security hotspots and new-code coverage become visible, reported the same way core
  reports them.
- The required check and the merge queue are unchanged.
- The charter is unchanged: Checkstyle is already among `verifyAll`'s gates, and SonarCloud never
  gates. [The service build guide](/guides/service-build/) describes the nightly report, and gains the
  complexity rules when #371 lands.
- The gate turns on only after #367 has refactored the existing over-limit methods, so the
  complexity gate arrives later than the SonarCloud workflow.

### Negative

- Someone has to provision a SonarCloud project and a `SONAR_TOKEN` secret.
- The nightly run costs about 8 minutes of runner time.
- The default SonarCloud profile overlaps Error Prone and Checkstyle, so it needs triage and tuning.
- New-code coverage is seen a day late, per period on `main`, not per pull request.

### Risks

- A report-only signal can be ignored. The four-week review below is the check on that.
- Complexity limits set too loosely gate nothing. The limits are the ones the operator ruled, and
  change only by a reviewed change to this ADR.
- A compromised action or scanner sees the source and the token. Mitigations: the SHA pin, minimal
  permissions, the token kept away from the build steps, and report-only status, so it never gates.

## Alternatives considered

### Option A: SonarCloud per pull request as well

This is core's full shape. It is not chosen. A Java scan needs compiled classes, so a safe per-PR
shape needs a compiled-classes artifact and a `workflow_run` consumer that keeps the token away from
pull-request builds. Checkstyle gives the complexity signal in the gate for less.
To be revisited after four weeks of nightly signal.

### Option B: Checkstyle only, no SonarCloud

Not chosen, because it leaves duplication, security hotspots and new-code coverage unmeasured.

### Option C: A JaCoCo coverage threshold

Not chosen. Aggregate coverage is already 95.8 % of lines, and it overstates each module's own
reach. Mutation floors ([#239](https://github.com/spec-kitty/spec-kitty-charter/issues/239)) are
the stronger signal.

## References

- [Test suite assessment, 2026-11-03](/plans/engineering-notes/test-suite-assessment-2026-11-03/)
- Core's [`sonar.yml`](https://github.com/spec-kitty/spec-kitty/blob/3620217255267f3e0a4062d123c8333a73a4b5cf/.github/workflows/sonar.yml),
  the [`sonar-pr` job in `ci-aggregate.yml`](https://github.com/spec-kitty/spec-kitty/blob/de459ad177eed0fb32a955a07f1c8735f3f0b419/.github/workflows/ci-aggregate.yml#L468),
  and CLAUDE.md [Sonar Expectations](https://github.com/spec-kitty/spec-kitty/blob/c14c045d5fe388a39885b1340a3f59443264257d/CLAUDE.md#L387)
  (spec-kitty/spec-kitty, pinned to commits on its `main`)
- [#367](https://github.com/spec-kitty/spec-kitty-charter/issues/367) — refactor the existing
  over-limit methods; a prerequisite of the complexity gate
- [#371](https://github.com/spec-kitty/spec-kitty-charter/issues/371) and [#372](https://github.com/spec-kitty/spec-kitty-charter/issues/372) — the complexity gate and the nightly scan
- `.github/workflows/mutation-nightly.yml` — the local nightly, report-only shape (*2026-11-06: amended by [ADR-0062](/adr/0062-nightly-mutation-floor/): the mutation nightly now turns red below a floor; it still gates no pull request.*)
- [#239](https://github.com/spec-kitty/spec-kitty-charter/issues/239) — mutation floors
