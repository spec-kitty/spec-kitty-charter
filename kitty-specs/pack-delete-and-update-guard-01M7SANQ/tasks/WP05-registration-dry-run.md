---
work_package_id: WP05
title: The registration dry run on PUT and POST
dependencies:
- WP02
- WP04
requirement_refs:
- FR-015
- SC-001
- SC-003
- FR-012
- FR-013
- NFR-002
- SC-002
planning_base_branch: feat/517-pack-delete-and-update-guard
merge_target_branch: feat/517-pack-delete-and-update-guard
branch_strategy: Planning artifacts for this mission were generated on feat/517-pack-delete-and-update-guard. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/517-pack-delete-and-update-guard unless the human explicitly redirects the landing branch.
subtasks:
- T018
- T019
- T020
- T021
history:
- at: '2026-11-19T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationJobs.java
create_intent:
- contracts/charter/scenarios/registration/registration-dry-run.feature
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationJobs.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RegistrationAdmission.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationJobsTest.java
- contracts/charter/scenarios/registration/registration-dry-run.feature
tags: []
tracker_refs: []
---

# WP05 — The registration dry run on PUT and POST

Plan: [plan.md](../plan.md). Squad dispositions: [research/squad-plan.md](../research/squad-plan.md).

## Subtasks

- **T018** `Mode` through `RegistrationJobs` and both registration entry points; a dry-run job neither commits, writes history nor records an audit entry
- **T019** The result: `dryRun: true`, no `pack` link on the status; `previousVersion` and `dryRun` never on `getPack`
- **T020** `registration-dry-run.feature` `@contract` scenarios: every story 3 case and its dry-run twin, with a byte comparison of the store
- **T021** `./gradlew verifyAll` green; close the gaps the scenarios show

## Done when

- Each subtask is recorded with `spec-kitty agent tasks mark-status`.
- `./gradlew verifyAll` is green on Java 25.
- Tests carry only the canonical shape (charter companion), except error-handling cases.
