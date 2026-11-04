---
okf_version: "0.2"
title: "Complexity limits block a pull request; SonarCloud reports nightly"
description: "Four Checkstyle complexity limits now fail the build, with a shrink-only register of exceptions and a stale check. A nightly SonarCloud analysis reports on main."
doc_status: active
updated: 2026-11-04T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
  - adr/0023-ci-gate-required-check
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the new limits on a method, what to do when one fails, and where the nightly report lives.
---

# 2026-11-04 — Complexity limits block a pull request; SonarCloud reports nightly

Proposed ADR-0056 (PR #366) sets limits on how complex a method may be. This change applies them.

**Blocks a pull request:**

- **Four Checkstyle limits,** on main and test sources: `CyclomaticComplexity` 15 (a `switch` counts once),
  `NPathComplexity` 1000, `MethodLength` 60 lines (blank lines and comments not counted) and `NestedIfDepth` 1.
  `checkstyleMain` and `checkstyleTest` fail on a violation. Fix it by simplifying the method.
- **The register stays honest.** `config/checkstyle/suppressions.xml` lists the 15 methods that were over a limit,
  one `suppress-xpath` entry each, with `rule:`, `file:`, `reason:` and `sunset:`. New code gets no entry. The list
  only shrinks, under [#367](https://github.com/spec-kitty/spec-kitty-charter/issues/367).
- **The stale check.** `checkCheckstyleRegister`, part of `check`, runs the rules without the register and fails on
  an entry that no longer suppresses anything (`STALE`), a missing file or field, a wrong `Size:` count, or a finding
  it cannot tie to a method.

**Reported only:**

- **Sonar (nightly, reported)** analyzes `main` at 04:23 UTC with SonarCloud. It is not part of `CI gate / gate`, and
  a red build, scan or quality gate never turns it red. The job summary shows each outcome and the quality-gate verdict.

**Later, from the operator:** the SonarCloud project and the `SONAR_TOKEN` secret. Until then the run skips green and
says why. The project key `spec-kitty_spec-kitty-charter` is assumed until the operator confirms it.

[Complexity limits](/guides/service-build/#complexity-limits) and
[the nightly report](/guides/service-build/#the-nightly-sonarcloud-report) have the details.
