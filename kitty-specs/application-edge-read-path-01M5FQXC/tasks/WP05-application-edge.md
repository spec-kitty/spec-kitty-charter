---
work_package_id: WP05
title: 'Application edge: TacticReadService + config + scaffolding removal'
dependencies:
- WP02
- WP03
- WP04
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-007
- NFR-004
- SC-001
- SC-003
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-application-edge-read-path-01M5FQXC
base_commit: d164a5f45c0ee07d34ce21ec02b3c3541ca78bef
created_at: '2026-10-21T19:57:23.247589+00:00'
subtasks:
- T023
- T024
- T025
- T026
- T027
- T028
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/config/ApplicationConfiguration.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/config/package-info.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/TacticReadService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/TacticReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/TacticReadWiredTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/**
- service/packs/src/test/java/ai/speckitty/charter/packs/application/**
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP05 – Application edge: TacticReadService + config + scaffolding removal

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Give the application layer its own config and the read use case: `TacticReadService` over the repository ports, and remove the application scaffolding. This is the first application-as-edge code. IC-02 (application) + IC-03. Depends WP02 (gate/aggregate), WP03 (ports), WP04 (storage realizes the ports).

## Context
- The `application` package currently holds `ConsistentLoad`, `PackListingQuery`, `ActionContextQuery`, `ActionContext`, `ActionContextWalk`, `GuidanceEntry`, `PackSummary`, `PackListing`, `ServiceInfo`, `ServiceInfoQuery`. Keep `ServiceInfo`/`ServiceInfoQuery` (InfoController). See [data-model.md](../data-model.md), [spec.md](../spec.md) FR-001..003.

### T023 — `ApplicationConfiguration`
- New `...application/config/ApplicationConfiguration.java` (`@Configuration`) `@ComponentScan("ai.speckitty.charter.packs.application")` to pick up the application `@Service` beans. This is the layer config WP02's `DeployableConfiguration` scans.

### T024 — `TacticReadService` (`@Service`)
- Methods: list a pack's tactics by `PackIdentifier`; get one by `(PackIdentifier, TacticIdentifier)`. Orchestrates `PackRepository`/`TacticRepository`. Returns domain `Tactic`(s) or a **client-facing not-found refusal** (reuse the refusal model; do not map an absent pack/tactic to `INTERNAL_ERROR`). No transport types in the application ring (gate `TRANSPORT_STAYS_IN_ADAPTER`). Honour point-read efficiency (NFR-001).

### T025 — Delete application scaffolding
- Delete `ConsistentLoad`, `PackListingQuery`, `ActionContextQuery`, and the support types that become dead with them (`ActionContext`, `ActionContextWalk`, `GuidanceEntry`, `PackSummary`, `PackListing` — verify each is unused after the controllers go in WP06; if a type is still referenced by a kept surface, leave it). **Keep** `ServiceInfo`/`ServiceInfoQuery`.

### T026 — Modular wired `@SpringBootTest`
- `TacticReadWiredTest`: `@SpringBootTest(classes = {StorageConfiguration.class, ApplicationConfiguration.class})` — provisions the storage + application slice through real beans, points a configured pack root at a fixture, and reads its tactics through the production path (SC-001, SC-003). Assert exactly one of each repository-port bean.

### T027 — Application unit tests
- `TacticReadServiceTest`: read happy-path (returns the fixture's tactics) + absent-pack refusal (4xx-class, not internal-error), each non-vacuous (a wrong pack id does not return another pack's tactics; the refusal path is proved by a control, not a tautology).

### T028 — Keep `ServiceInfoQuery`
- Leave `ServiceInfo`/`ServiceInfoQuery` intact; InfoController depends on them.

## Definition of Done
- `:packs:test` application tests green; `TacticReadWiredTest` boots the slice and reads through the production path; scaffolding queries removed; `ServiceInfoQuery` intact.

## Reviewer guidance (test economy — DIRECTIVE_041)
- The wired test must exercise real beans (not hand-instantiated collaborators) and a real read — it is the SC-001/SC-003 proof, keep it. The unit test's refusal arm must be non-vacuous. Reject any test that duplicates the storage round-trip or pins bean names.
