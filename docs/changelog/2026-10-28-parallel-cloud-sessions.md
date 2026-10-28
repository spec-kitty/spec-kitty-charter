---
okf_version: "0.2"
title: Running parallel cloud sessions — charter rules and guide caveats
description: The charter gains rules for orchestrating cloud sessions, and the cloud-session guide records what went wrong running several in parallel and how to avoid it.
doc_status: active
updated: 2026-10-28T00:00:00Z
type: Changelog
kind: Changelog
related:
  - development/cloud-session-setup
  - guides/contributing
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you dispatch Missions to several cloud sessions at once.
---

# 2026-10-28 — Running parallel cloud sessions — charter rules and guide caveats

**Running several Claude Code cloud sessions in parallel now has written rules.** Runs on
2026-10-27 and 2026-10-28 showed that a child session's permission classifier can refuse setup
steps the operator approved (push access to `spec-kitty-open-packs`, installing the CLI from a core
checkout), inconsistently between sibling sessions, and that a blocked session cannot recover.

The [cloud-session setup guide](/development/cloud-session-setup/) gains a section on running
several sessions: the caveats, the mitigations (tooling in the setup script, read-only sibling
clones, draft issues handed back through the orchestrator with `open-packs#TBD-<slug>`
placeholders), a setup-script addition for the CLI, a checklist for briefs, and troubleshooting
for ADR-number collisions, strict `related:` slugs in the site build, mode-only dirty checkouts and
Maven Central rate limits.

The project charter's companion (`.kittify/charter/charter.md`) gains a short "Orchestrating cloud
sessions" rule set that points to the guide.
