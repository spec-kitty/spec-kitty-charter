---
okf_version: "0.2"
title: Agent commits carry a Claude Co-Authored-By trailer
description: Every commit a Claude agent session makes in this repository ends with a Co-Authored-By trailer that names Claude and no model, replacing the earlier rule of adding none.
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - development/cloud-session-setup
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before an agent session commits to this repository.
---

# 2026-11-25 — Agent commits carry a Claude Co-Authored-By trailer

**Every commit a Claude agent session makes in this repository ends with
`Co-Authored-By: Claude <noreply@anthropic.com>`.** The operator ruled it on 2026-11-25. It
replaces the earlier rule, set in session briefs, of adding no Co-Authored-By trailer.
`charter.md` ("Orchestrating cloud sessions"), `AGENTS.md` and the brief checklist of
[Set up a Claude Code cloud session](/development/cloud-session-setup/) now say:

- The trailer names Claude and no model, and no commit message or PR body names a model.
- An orchestrator's brief tells each runner to add it.
