---
title: Contract and build brought under governance
description: A retroactive Mission reviewed roadmap steps 1 and 2, recorded the operator rulings, and added the charter's own contract checks and the quality gates.
doc_status: active
updated: 2026-10-19T00:00:00Z
type: Changelog
kind: Changelog
related:
  - plans/open-questions
  - plans/roadmap
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what the build checks now and the rulings behind it.
  - profile: core-maintainer
    guidance_text: Read this for the contract changes core will vendor and the upstream issues.
---

# 2026-10-18 — Contract and build brought under governance

The Mission `charter-contract-and-build-retro-01M57QHJ` reviewed what landed for roadmap
steps 1 and 2 and closed their open items.

## Rulings

Recorded in the [open questions](/plans/open-questions/), questions 13 to 20:

- `pack_level_missing` stays a non-blocking diagnostic, ratified as is and stricter than core.
- The action-context rules are amended and provisional until the conformance corpus (#9).
- `contracts/charter/scenarios/` keeps its name; three scenario homes are recorded.
- The charter owns its contract checks; core's tools do not run here.
- OpenRewrite is not enabled; jqwik is rejected.

## What changed

- The contract is `1.0.0-SNAPSHOT`: snake_case codes, `x-source`/`x-derived` citations and
  `x-provisional` markers (`contracts/charter/CHANGELOG.md`).
- The `:contracts` project runs the charter's own checks with pinned vacuum and oasdiff: a
  lint ruleset with at least one planted fixture per rule, the bundle, a Java checker for
  closed response objects and the breaking-change check.
- The quality gates: palantir-java-format, Checkstyle, Error Prone with NullAway, doclint
  and JaCoCo, with Mockito as a test library; PIT runs nightly. The generated `charter-api`
  is exempt.
- The Javadoc is built and staged for the docsite; it is published with the first docs-site
  run on `main` ([Java API reference](/api/javadoc/)).
- 735 tests, none skipped, green on Java 25, locally and in the pull request's first CI run.
  Steps 1 and 2 are done ([roadmap](/plans/roadmap/)).

## Upstream

- [spec-kitty-open-packs#46](https://github.com/spec-kitty/spec-kitty-open-packs/issues/46):
  advisory against jqwik.
- [spec-kitty-open-packs#47](https://github.com/spec-kitty/spec-kitty-open-packs/issues/47):
  every pack declares `pack_level` and `pack_version`.
- [spec-kitty#6402](https://github.com/spec-kitty/spec-kitty/issues/6402): core's enum-case
  rule and `built-in`, the record that core may adapt.
- [spec-kitty#6403](https://github.com/spec-kitty/spec-kitty/issues/6403): core's glossary
  calls a missing `pack_level` a refusal.
- A comment on [#12](https://github.com/spec-kitty/spec-kitty-charter/issues/12): ADR-0008
  answers it.
