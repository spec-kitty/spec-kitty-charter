---
okf_version: "0.2"
title: Reviews run in the runner's session
description: No cloud session is opened to review a Mission; the runner runs its own adversarial review with in-session subagents and posts it on the pull request.
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - development/cloud-session-setup
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you plan the reviews of a Mission run in cloud sessions.
---

# 2026-11-25 — Reviews run in the runner's session

**No cloud session is opened to review a Mission.** At least five sessions were open for the
reviews of one open-packs Mission, and the operator ruled that overkill.
[Set up a Claude Code cloud session](/development/cloud-session-setup/) ("Running several cloud
sessions"), `charter.md` and `AGENTS.md` now say:

- The runner runs its own adversarial review with in-session subagents, for every review round.
- It folds every finding, confirms the fold and posts the review on the pull request as a comment
  headed "Independent adversarial review (agent)".
- The orchestrator's brief asks for this, and the orchestrator plans no reviewer session.
