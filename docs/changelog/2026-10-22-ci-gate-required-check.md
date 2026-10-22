---
title: A single CI gate check now guards every pull request
description: A new always-on CI gate workflow aggregates the path-filtered service and docs checks into one required status check, so a pull request merges only once CI is green.
doc_status: active
updated: 2026-10-22T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0023-ci-gate-required-check
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the single check your PR must pass and why the per-area workflows no longer trigger on pull_request directly.
  - profile: core-maintainer
    guidance_text: Read this for why main now requires one CI gate context and how auto-merge gates on green.
---

# 2026-10-22 — A single CI gate check now guards every pull request

Before, `main` required no status check at all, so an armed auto-merge could land a pull request
the moment it was opened — before CI finished. Now a single always-on **CI gate** check must pass
first, so a PR merges only once its checks are green.

## What changed

- **New `CI gate` workflow.** It runs on every pull request, detects which areas the PR touched,
  and runs the service build and the docs checks only for the areas that changed — then reports one
  verdict, `CI gate / gate`. That single context is what `main` requires.
- **The per-area workflows no longer trigger on `pull_request` directly.** `service` and
  `docs-cleanliness` now run on pull requests only through the CI gate (and still run on `main` and
  on demand), so nothing runs twice. Markdown lint stays advisory and never blocks a merge.

## Who is affected

Contributors: your PR shows one required `CI gate / gate` check instead of several per-area checks;
the per-area results still appear beneath it. Maintainers: arming `gh pr merge --rebase --auto` now
genuinely waits for green before it merges. The reasoning is in
[ADR-0023](/adr/0023-ci-gate-required-check/).
