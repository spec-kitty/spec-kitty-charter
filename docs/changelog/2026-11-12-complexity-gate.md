---
okf_version: "0.2"
title: The complexity limits gate every pull request
description: Checkstyle now fails a method over cyclomatic complexity 15, NPath 1000, 60 non-empty lines or a nested-if depth of 1, in main and test sources.
doc_status: active
updated: 2026-11-12T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the four complexity limits a pull request must now keep, and what to do when a method goes over one.
---

# 2026-11-12 — The complexity limits gate every pull request

The four Checkstyle limits of
[ADR-0058](/adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr/) are on
([#371](https://github.com/spec-kitty/spec-kitty-charter/issues/371)). `checkstyleMain`, `checkstyleTest`,
`checkstyleTestFixtures` and `checkstyleDriftTest` fail a method over any of them, so `verifyAll` and
`CI gate / gate` do too:

| Checkstyle module | Limit |
| --- | --- |
| `CyclomaticComplexity` | 15 |
| `NPathComplexity` | 1000 |
| `MethodLength` | 60 non-empty lines |
| `NestedIfDepth` | 1 |

- **Before:** nothing measured size; the methods over the limits were listed in
  [#367](https://github.com/spec-kitty/spec-kitty-charter/issues/367).
- **After:** every method is within the limits, and none is grandfathered: `config/checkstyle/suppressions.xml`
  gained no entry. A method over a limit is refactored (an extracted method, a guard clause or a table), not
  suppressed.
