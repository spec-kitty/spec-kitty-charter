---
work_package_id: WP02
title: CI path filters and the build guide
dependencies:
- WP01
requirement_refs:
- FR-013
- FR-016
planning_base_branch: claude/client-java-218-224
merge_target_branch: claude/client-java-218-224
branch_strategy: Planning artifacts for this mission were generated on claude/client-java-218-224. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/client-java-218-224 unless the human explicitly redirects the landing branch.
subtasks:
- T007
- T008
- T009
history: []
agent_profile: java-jenny
authoritative_surface: .github/workflows/
create_intent:
- docs/changelog/2026-11-05-generated-java-client.md
execution_mode: code_change
model: ''
owned_files:
- .github/workflows/ci.yml
- .github/workflows/service.yml
- docs/guides/service-build.md
- docs/changelog/2026-11-05-generated-java-client.md
role: implementer
tags: []
tracker_refs: []
---

# WP02: CI path filters and the build guide

Design: plan.md D-4, D-6.

## Subtasks

- **T007** Append `client-java/` to `ci.yml`'s `service_re` (append only; a parallel session edits this file).
- **T008** Add `client-java/**` to `service.yml`'s `push.paths`.
- **T009** `docs/guides/service-build.md`: a section on the client module; changelog entry.

## Done when

A change under `client-java/` alone selects the service group; the docs checks pass.
