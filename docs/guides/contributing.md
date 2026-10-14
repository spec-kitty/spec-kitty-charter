---
title: Contributing
description: How work lands in spec-kitty-charter during the bootstrap — branches and pull requests, documentation rules, the contract-first order, and where decisions go.
doc_status: draft
updated: 2026-10-13
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
fixture, so the two never disagree on purpose. This assumes the Python charter stays in
core; if it moves here first ([open questions](/plans/open-questions/), question 2), the
fix lands in the Python package here.

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

The docsite workflow checks every change to `docs/`: frontmatter, source links, a full
build and the links in the built site ([Building the docsite](/guides/docsite/)). When the
Gradle build lands, it brings: contract layout and lint checks, the
breaking-change check, unit tests, the dependency-direction test and the conformance corpus.
This page will list the commands then.
