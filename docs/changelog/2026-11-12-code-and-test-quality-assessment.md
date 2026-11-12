---
okf_version: "0.2"
title: "Code and test suite assessed again; six test gaps proved"
description: "A dated re-run of the 2026-11-03 test suite assessment with a production-code lens, six test gaps proved by planted breaks, and four questions for the operator."
doc_status: active
updated: 2026-11-12T00:00:00Z
type: Changelog
kind: Changelog
related:
  - plans/engineering-notes/code-and-test-quality-assessment-2026-11-12
  - plans/engineering-notes/test-suite-assessment-2026-11-03
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for where the tests and the code stand and which gaps were proved.
  - profile: governance-lead
    guidance_text: Read this for the four questions the assessment puts to the operator.
---

# 2026-11-12 — Code and test suite assessed again; six test gaps proved

A new [engineering note](/plans/engineering-notes/code-and-test-quality-assessment-2026-11-12/)
repeats the [assessment of 2026-11-03](/plans/engineering-notes/test-suite-assessment-2026-11-03/)
and adds a lens on production code. It is judged against the charter's testing and model rules.

The suite is still strong, and it has more than doubled:

- 10,623 JUnit tests pass, against 4,488 nine days earlier;
- 96.6 % of lines and 89.9 % of branches are covered;
- a static triage flags 3.1 % of 4,652 test methods, and most flags do not hold up when read.

Six rules are proved unguarded: each was broken on purpose and its tests stayed green. The note ranks
five weaknesses by risk. It also asks the operator four questions: the unversioned pack descriptor,
the activation store's version 1 reader, the "Doctrine" type names, and the null-check constructor
tests. The assessment changes no code.
