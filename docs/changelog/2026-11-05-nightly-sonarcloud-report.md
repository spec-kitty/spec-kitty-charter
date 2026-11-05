---
okf_version: "0.2"
title: "SonarCloud reports on main every night; the complexity limits wait for the refactors"
description: "A nightly SonarCloud analysis reports on main and never blocks anyone. The Checkstyle complexity limits stay off until the methods over them are refactored."
doc_status: active
updated: 2026-11-05T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
  - adr/0023-ci-gate-required-check
  - adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for where the nightly report lives, and for why the complexity limits do not fail a build yet.
---

# 2026-11-05 — SonarCloud reports on main every night; the complexity limits wait for the refactors

[ADR-0058](/adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr/) decides that SonarCloud reports
nightly and that Checkstyle gates complexity on every pull request. This change delivers the report and prepares the
gate.

**Report-only:**

- **Sonar (nightly, reported)** analyzes `main` at 04:23 UTC with SonarCloud. It is not part of `CI gate / gate`, and
  a red build, scan or quality gate never turns it red. The job summary shows each outcome and the quality-gate verdict.

**Not on yet: the complexity limits.**

- No build fails on complexity yet. There is no grandfathering, so the limits turn on only when no method is over them.
  [#367](https://github.com/spec-kitty/spec-kitty-charter/issues/367) refactors the 14 methods still over a limit, and
  [#371](https://github.com/spec-kitty/spec-kitty-charter/issues/371) then turns the limits on.
- Six methods are already within them: `ComposedBundle.checkReferences` and `.reaches`,
  `ActivationConfigSchemaParityTest.inline`, `Pep440Range.candidates`, `GraphWalk.visit` and
  `PackCompositionResolver.checkReferences`. Their behavior does not change.

**Later, from the operator:** the SonarCloud project and the `SONAR_TOKEN` secret. Until then the run skips green and
says why. The project key `spec-kitty_spec-kitty-charter` is assumed until the operator confirms it.

[The nightly report](/guides/service-build/#the-nightly-sonarcloud-report) has the details.
