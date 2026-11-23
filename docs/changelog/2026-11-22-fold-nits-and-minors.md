---
okf_version: "0.2"
title: Squad nits and minors are folded
description: A squad finding below MEDIUM is folded before the merge, also when it predates the change; dropping it is no longer an option.
doc_status: active
updated: 2026-11-22T00:00:00Z
type: Changelog
kind: Changelog
related:
  - development/issue-tracker
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you close a review squad's findings.
---

# 2026-11-22 — Squad nits and minors are folded

**A squad finding below MEDIUM is folded before the merge.** The operator ruled that deferred nits
and minors tend to cause big problems later on. [Keep the issue tracker small](/development/issue-tracker/)
Rule 1, `charter.md` and `AGENTS.md` now say:

- A finding below MEDIUM, a nit included, is folded into the pull request, also when it predates
  the change.
- It is recorded under "Not done" only with a concrete reason it cannot be folded.
- Dropping a cosmetic finding, allowed since 2026-11-11, is withdrawn. Such findings are still
  never filed as issues.
