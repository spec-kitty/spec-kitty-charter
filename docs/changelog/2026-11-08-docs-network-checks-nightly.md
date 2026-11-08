---
okf_version: "0.2"
title: The external link check runs nightly
description: CI no longer runs the external link check on pull requests; a nightly workflow runs it, still report-only. The PlantUML diagram check stays on pull requests.
doc_status: active
updated: 2026-11-08T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/docsite
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you look for the external link check of a pull request.
---

# 2026-11-08 — The external link check runs nightly

**The external link check (lychee) no longer runs on pull requests.** It asks remote sites and downloads lychee from
github.com, so when either was down a pull request showed a red check it did not cause, even though the check never
blocked a merge.

The new `docs-nightly.yml` workflow runs it every night and on demand (`workflow_dispatch`), and `docs-cleanliness.yml`
drops it. It is unchanged and report-only: a broken link never fails the run. The lychee download now retries.

The source-level link and anchor check (`check_docs.py links`) still runs on every pull request, and so does the
PlantUML diagram check, whose pinned jar is now cached ([2026-11-08 — CI keeps its pinned tools](/changelog/2026-11-08-ci-checks-offline-tools/)).
