---
title: "ADR-0058: SonarCloud runs nightly and reported only; complexity is gated per pull request by Checkstyle"
description: "Static analysis beyond correctness: a nightly, never-required SonarCloud scan with new-code coverage, and Checkstyle complexity rules in the per-PR gate."
doc_status: active
updated: 2026-11-03T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-03'
type: ADR
kind: ADR
related:
  - plans/engineering-notes/test-suite-assessment-2026-11-03
  - adr/0023-ci-gate-required-check
  - adr/0047-generated-indexes-and-merge-queue
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for which complexity rules block a pull request and where the nightly SonarCloud result is reported.
  - profile: governance-lead
    guidance_text: Read this for why SonarCloud is reported and not required, and what it costs.
---

# ADR-0058: SonarCloud runs nightly and reported only; complexity is gated per pull request by Checkstyle

## Status

**Accepted**, 2026-11-03. Decider: Stijn Dejongh (operator). The operator ruled, on the
[test suite assessment](/plans/engineering-notes/test-suite-assessment-2026-11-03/), "Nightly Sonar,
per PR CheckStyle", with new-code coverage reported through SonarCloud instead of a coverage
threshold.

This ADR records that ruling. The implementation is the follow-up mission
`ci-sonar-nightly-checkstyle-complexity`, in its own pull request.

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

Core runs SonarCloud on two surfaces. Both are reported only, outside the required
`aggregate-gate`:

- a nightly `sonar.yml`, which compiles Java only on trusted, merged code;
- a per-PR `sonar-pr` job that analyzes Python without compiling it.

Java analysis needs compiled classes (`sonar.java.binaries`). Core deliberately never compiles a
pull request's build next to `SONAR_TOKEN`.

The required-check rules this must respect:

- [ADR-0023](/adr/0023-ci-gate-required-check/) makes `CI gate / gate` the one required check, and
  sets the precedent that advisory jobs run with `continue-on-error`.
- [ADR-0047](/adr/0047-generated-indexes-and-merge-queue/) keeps workflows that produce no required
  check off `merge_group`.

## Decision

1. **Checkstyle gates complexity per pull request.** `config/checkstyle/checkstyle.xml` gains
   `CyclomaticComplexity`, `NPathComplexity`, `MethodLength` and `NestedIfDepth`.
   - The limits are set from a measurement run over the current code, so the change lands green.
   - Any exception goes into `config/checkstyle/suppressions.xml`, the one register, as a
     shrink-only entry.
   - The rules run inside `verifyAll`, so `CI gate / gate` enforces them with no new job or tool.
2. **SonarCloud runs nightly and is reported only.** A new `.github/workflows/sonar.yml` runs on a
   schedule and on manual dispatch, on `main` only.
   - **Analysis.** It runs `verifyAll` on trusted code, then the SHA-pinned generic
     `SonarSource/sonarqube-scan-action` that core uses. No Gradle plugin is added.
   - **Inputs.** It passes the modules' `build/classes/java/main` as `sonar.java.binaries`, and the
     aggregated report as `sonar.coverage.jacoco.xmlReportPaths`. It excludes the generated
     `charter-api`.
   - **Reported only.** Every step is `continue-on-error`. The quality-gate result goes to the job
     summary. The job is never part of `CI gate / gate` and never runs on `merge_group` or a pull
     request.
   - **Without a token.** It skips green with a notice when `SONAR_TOKEN` is absent, so it can land
     before the SonarCloud project exists.
3. **Coverage stays a report.** No JaCoCo threshold is added. New-code coverage is read from
   SonarCloud's new-code period on `main`.

## Consequences

### Positive

- Complexity regressions block in the gate the contributor already runs, with no new tool and no
  token.
- Duplication, security hotspots and new-code coverage become visible, reported the same way core
  reports them.
- The required check and the merge queue are unchanged.

### Negative

- Someone has to provision a SonarCloud project and a `SONAR_TOKEN` secret.
- The nightly run costs about 8 minutes of runner time.
- The default SonarCloud profile overlaps Error Prone and Checkstyle, so it needs triage and tuning.
- New-code coverage is seen a day late, per period on `main`, not per pull request.

### Risks

- A reported-only signal can be ignored. The four-week review below is the check on that.
- Complexity limits set too loosely gate nothing. The limits are recorded with the measurement that
  set them, and lowered only by a reviewed change.

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
- Core's `sonar.yml`, the `sonar-pr` job in `ci-aggregate.yml`, and CLAUDE.md "Sonar Expectations"
  (spec-kitty/spec-kitty)
- [#239](https://github.com/spec-kitty/spec-kitty-charter/issues/239) — mutation floors
