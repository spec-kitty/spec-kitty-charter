---
work_package_id: WP07
title: Container guide, compose and changelog
dependencies:
- WP01
- WP05
requirement_refs:
- FR-020
- NFR-004
planning_base_branch: feat/container-guard-and-actuator
merge_target_branch: feat/container-guard-and-actuator
branch_strategy: Planning artifacts for this mission were generated on feat/container-guard-and-actuator. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/container-guard-and-actuator unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-container-guard-and-actuator-01M6P99A
base_commit: 5d1b3ffc8dba3445810fc92f64a6fdcc0a570417
created_at: '2026-11-05T17:40:24.523719+00:00'
subtasks:
- T023
- T024
- T025
history: []
agent_profile: scribe-sally
authoritative_surface: deploy/compose/
create_intent:
- docs/changelog/2026-11-05-container-guard-and-actuator-health.md
execution_mode: code_change
model: ''
owned_files:
- deploy/compose/compose.yaml
- deploy/compose/.env.example
- docs/changelog/2026-11-05-container-guard-and-actuator-health.md
role: implementer
tags: []
tracker_refs: []
---
# WP07: Container guide, compose and changelog

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Branch Strategy

Planning branch and final merge target: `feat/container-guard-and-actuator`. Execution worktrees are allocated per computed lane from
`lanes.json`; do not pick a base by hand.

Implementation command:
`spec-kitty agent action implement WP07 --agent claude --mission container-guard-and-actuator-01M6P99A`

## Objective

The container guide and the compose example match the shipped behaviour (FR-020). The changelog records it.

## Subtasks

### T023 — Guide (`docs/guides/run-in-a-container.md`; out-of-map edit, WP01 owns the file)

The edit is out of map because WP01 only adds the single-replica bullet; this WP rewrites the other sections.

- **"Where settings come from":** state the allowed-host forms (a host name, an IPv4 address or a bracketed
  IPv6 address; no port, wildcard or trailing dot) and that matching ignores case and port.
- **"What stops the start":** add these bullets:
  - a malformed host entry;
  - a widened actuator exposure, health details or management port;
  - request-detail logging.
- **New "Health check" section:**
  - `GET /actuator/health` answers `{"status":"UP"}` without a token; only that exact request, everything
    else needs the token;
  - the request still needs an allowed `Host`, so set the probe's `Host` header (Kubernetes `httpGet.httpHeaders`)
    rather than listing a wildcard or the pod IP;
  - nothing else is exposed.
- **"Limits for now":** drop the host-names and health-check limits. Keep the configuration root, one
  container per root, single replica (WP01) and inspecting the file. Add "Behind a proxy": forwarded headers
  are ignored.
- **See also:** add the ADR from WP01.

### T024 — Compose

- `compose.yaml`: add a `healthcheck` that uses a tool the image will have. No image exists yet (#234), so use
  `["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health"]` and comment that it assumes curl in the
  image. Alternatively, document the health check in a comment only. Choose the comment-only route if unsure,
  because a failing healthcheck marks the container unhealthy.
- Keep the loopback publish, and update its comment: the guard now reads `allowed-hosts`, and publishing beyond
  loopback is the operator's choice.
- `.env.example`: give an `allowed-hosts` example comment, `localhost` plus the real name.

### T025 — Changelog

Add `docs/changelog/2026-11-05-container-guard-and-actuator-health.md`, under 3,000 characters, covering
#268, #231 and #232. Run the docs checks as in WP01 T004.

## Definition of Done

The docs checks, markdownlint and cspell are green, and the guide matches the behaviour tested in WP05.
