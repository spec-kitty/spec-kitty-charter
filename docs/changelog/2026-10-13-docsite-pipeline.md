---
title: Docsite build and Pages deploy
description: The docs tree is now built into a doc-kitty site by CI on every docs pull request and published to GitHub Pages from main.
doc_status: active
updated: 2026-10-13
type: Changelog
kind: Changelog
related:
  - guides/docsite
  - adr/0003-docs-follow-doc-kitty
---

# 2026-10-13 — Docsite build and Pages deploy

## What changed

- **`site/`** is an Astro project that renders `../docs` with `@spec-kitty/doc-toolkit`
  0.2.0 and the Spec Kitty brand theme, plus the toolkit's RSS, `llms.txt` and agent-API
  routes.
- **`.github/workflows/docs-site.yml`** validates frontmatter and links, builds the site and
  checks every link in the build on each docs pull request, and deploys to GitHub Pages from
  `main`.
- **Links** between pages are now root-absolute routes, so they work under the Pages base
  path.

One setting is needed once, outside the repository: Pages set to deploy from GitHub
Actions. See
[Building the docsite](/guides/docsite/).
