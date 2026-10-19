---
title: Contributing to spec-kitty-charter
description: How work lands in spec-kitty-charter during the bootstrap — branches and pull requests, documentation rules, the contract-first order, and where decisions go.
doc_status: draft
updated: 2026-10-18
type: Guide
kind: How-To
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you open a pull request.
---

# Contributing

## Branches and pull requests

- Work on a topic branch and open a pull request against `main`. Never push to `main`.
- Open pull requests as drafts; the operator (the repository owner, Stijn Dejongh) merges.
- Review is advisory today (see `.github/CODEOWNERS`); nothing on GitHub enforces it.

## Contract first

A change to what the service returns starts in `contracts/charter/` (authored here pending
[ADR-0002](/adr/0002-contract-source-of-record/)). The order is: contract
and fixtures, then the generated code, then the implementation. A pull request that changes
a response shape without a contract change is incomplete. See
[Contract and DTOs](/architecture/contract-and-dtos/).

## Both implementations, one answer

While the Python charter still answers in core, a behavior change needs a fixture that both
implementations pass. If the Python behavior is wrong, fix it in core first, with the
fixture, so the two never disagree on purpose. Where the fix lands depends on
[open question 2](/plans/open-questions/).

## Documentation

- Follow the doc-kitty convention ([ADR-0003](/adr/0003-docs-follow-doc-kitty/)):
  frontmatter on every page, `README.md` as each section's index, sections registered in
  `docs/_meta/sections.yaml`.
- Say what exists. A page describing planned work says so at the top.
- Use the charter vocabulary in [Domain](/context/domain/) and Spec Kitty's terminology
  canon (Mission, not "feature").
- Record a decision as an ADR under `docs/adr/`; add a dated page under `docs/changelog/`
  for notable changes.

## Quality gates

Run these before you open a pull request; CI runs the same checks.

- **The service build**, on Java 25:

  ```bash
  ./gradlew verifyAll
  ```

  It runs the charter's own contract checks (lint, planted fixtures, the bundle, closed
  response objects and the breaking-change check), contract generation, formatting,
  Checkstyle, Error Prone with NullAway, Javadoc with doclint, unit tests, the architecture
  rules, the contract scenarios, the open-packs round-trip, and the aggregated Javadoc and
  coverage reports. `./gradlew :contracts:check` runs the contract checks alone. What each
  task checks: [Building the service](/guides/service-build/).
- **The docs checks**, on every change to `docs/`: `python3 scripts/docs/check_docs.py all`,
  markdownlint and cspell, and in CI a full site build and the links in the built site. The
  commands and pinned versions: [Building the docsite](/guides/docsite/).

The conformance corpus joins these checks with roadmap step 3.
