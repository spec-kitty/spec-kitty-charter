---
okf_version: "0.2"
title: "Test suite assessed; SonarCloud ruled nightly and reported only"
description: "A dated assessment of the test suite against the packs' testing rubric, with ranked weaknesses, proposed issues and the Accepted ADR-0058 for SonarCloud."
doc_status: active
updated: 2026-11-03T00:00:00Z
type: Changelog
kind: Changelog
related:
  - plans/engineering-notes/test-suite-assessment-2026-11-03
  - adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for where the test suite stands and which follow-ups are proposed.
  - profile: governance-lead
    guidance_text: Read this for the SonarCloud ruling and where its record lives.
---

# 2026-11-03 — Test suite assessed; SonarCloud ruled nightly and reported only

A new [engineering note](/plans/engineering-notes/test-suite-assessment-2026-11-03/) assesses the
test suite against the packs' own testing rubric:

- the twelve test desiderata, test boundaries by responsibility, and the test pyramid;
- mutation testing and no fake green.

The suite is strong:

- 4,488 JUnit tests pass;
- 95.8 % of lines and 88.0 % of branches are covered;
- no test uses a mock, and none is disabled.

Where PIT mutates code, the tests kill 95–96 % of the mutants they reach. Five weaknesses are ranked
by risk:

1. Mutation testing gates nothing and skips `packs`.
2. Each module's own tests reach less than the aggregate coverage shows.
3. Tight wall-clock deadline checks remain in the parallel test run.
4. A third of the exception assertions check only the type.
5. Nothing measures complexity.

Nine follow-ups are proposed, not filed.

The operator ruled on three questions. SonarCloud runs nightly and is reported only, and Checkstyle
complexity rules gate each pull request; this is recorded as Accepted
[ADR-0058](/adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr/). The mutation
floors of #239 are adopted as designed. New-code coverage is reported through SonarCloud instead of
a JaCoCo threshold.

The note opens a new [engineering notes](/plans/engineering-notes/) folder for dated assessments.
