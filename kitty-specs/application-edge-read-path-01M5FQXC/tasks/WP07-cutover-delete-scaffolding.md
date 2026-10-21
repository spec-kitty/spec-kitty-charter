---
work_package_id: WP07
title: 'Cutover: delete the shared domain scaffolding'
dependencies:
- WP04
- WP05
- WP06
requirement_refs:
- FR-004
- FR-005
- C-001
- SC-002
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-application-edge-read-path-01M5FQXC
base_commit: 142864ae1ee02df09ca7caa2e1562c71a5464eec
created_at: '2026-10-21T20:18:30.171981+00:00'
subtasks:
- T035
- T036
- T037
- T038
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent: []
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/CharterPack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/CharterOffering.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/CharterPackRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackInspectionQuery.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP07 – Cutover: delete the shared domain scaffolding

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

With every consumer removed (storage WP04, application WP05, api WP06), delete the shared domain scaffolding so exactly one model remains. IC-05 (final). FR-004, FR-005, C-001, SC-002.

## Context
- By now `FileSystemCharterPackRepository` (WP04), `ConsistentLoad`/`PackListingQuery`/`ActionContextQuery` (WP05) and `PacksController`/`ContextController` (WP06) are gone. The only remaining references to the scaffolding should be the types themselves.

### T035 — Delete `CharterPack`, `CharterOffering`
- Remove both. Verify no production reference remains (grep `main` sources).

### T036 — Delete `CharterPackRepository` port
- Remove the port; its only implementer (`FileSystemCharterPackRepository`) is already gone.

### T037 — Delete `PackInspectionQuery` port
- Remove the port; `Pack.violations()` is the canonical owner (WP03/WP04).

### T038 — Final single-model verification
- Repo-wide search finds **zero** references to `CharterPack`, `CharterOffering`, `CharterPackRepository`, `PackInspectionQuery` in production code (FR-004/SC-002). Remove any now-dead domain test that targeted these types (out-of-map test deletions are acceptable with a one-line rationale). Run the full ArchUnit gate (`:deployable:test --tests '*HexagonalArchitectureTest'`) and `:kernel:test :packs:test` to confirm the branch tip is green with one aggregate root.

## Definition of Done
- The four scaffolding types are gone; zero production references remain; `:kernel:test :packs:test` and the ArchUnit gate are green; one canonical `Pack` model.

## Reviewer guidance (test economy — DIRECTIVE_041)
- This WP mostly deletes. Confirm no test was kept alive only to exercise a deleted type (retire it). Confirm the single-model absence check is a real search, not an assertion that always passes.
