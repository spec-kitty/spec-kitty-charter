---
work_package_id: WP12
title: Wiring through the production path
dependencies:
- WP11
requirement_refs:
- FR-010
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-repositories-validation-01M5EET9
base_commit: 085463fbc3072d0d32752b61a67ce69e7ab4a4dd
created_at: '2026-10-21T15:38:31.047785+00:00'
subtasks:
- T051
- T052
- T053
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/bootstrap/src/main/java/ai/speckitty/charter/bootstrap/
create_intent:
- service/bootstrap/src/main/java/ai/speckitty/charter/bootstrap/PackPersistenceWiring.java
- service/bootstrap/src/test/java/ai/speckitty/charter/bootstrap/WiringRoundTripTest.java
execution_mode: code_change
owned_files:
- service/bootstrap/src/main/java/ai/speckitty/charter/bootstrap/PackPersistenceWiring.java
- service/bootstrap/src/test/java/ai/speckitty/charter/bootstrap/WiringRoundTripTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP12 – Wiring through the production path

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Make the capability live, not dead code: register the edge `DomainRepositoryService` as a bean in `bootstrap` (component-scan or `@Bean`) and prove it with a wiring test that round-trips a fixture pack **through the wired bean** resolved from the Spring context. This is IC-06.

## Context

- The wiring contract (stereotype at the edge, no Spring Data, domain port stays framework-free) and the live-path proof: [spec.md](../spec.md) FR-010, C-002; [research.md](../research.md) D4; [quickstart.md](../quickstart.md) §4.
- The bean type is WP11's `DomainRepositoryService`; the composition root is WP07's `bootstrap` (`CharterServiceApplication` component-scan).

### Subtask T051 — Register the edge @Service
- **Purpose**: Wire the adapter into the production context (FR-010).
- **Steps**: ensure `DomainRepositoryService` is picked up — either it is already in a scanned package, or add a thin `PackPersistenceWiring` `@Configuration` providing the `SerializationStrategy` + `FilesystemPackStore` collaborators and letting the `@Service` be scanned. Keep all Spring stereotypes at the bootstrap/edge; do not touch the domain ports.
- **Files**: `PackPersistenceWiring.java`.
- **Edge cases**: if `CharterServiceApplication`'s scan base already covers `packs.infra.storage`, `PackPersistenceWiring` only supplies the non-stereotyped collaborators (e.g. the pack-root `Path`).

### Subtask T052 — Wiring round-trip test through the WIRED bean
- **Purpose**: Prove it is live (FR-010).
- **Steps**: `WiringRoundTripTest` — boot the Spring context (slice/`@SpringBootTest` as the existing wiring tests do), fetch the `TacticRepository`/`PackRepository` bean **from the context**, and `save`→`load` a fixture tactic, asserting the round-trip resolves through the wired bean (not a hand-constructed instance).
- **Files**: `WiringRoundTripTest.java`.
- **Validation**: `./gradlew :bootstrap:test --tests '*Wiring*'`.

### Subtask T053 — Confirm port signatures stay domain-only through the live path
- **Purpose**: FR-002/SC-001 realized end-to-end.
- **Steps**: in the wiring test, address the repository by its **domain port type** (`TacticRepository`/`PackRepository`) with only `PackIdentifier`/`TacticIdentifier` arguments — no filesystem/YAML/adapter type in the call. This is the live-path counterpart to WP07's static port-signature rule.
- **Files**: within `WiringRoundTripTest.java`.

## Branch Strategy

- Planning branch `feat/pack-repositories-validation`; final merge target `feat/pack-repositories-validation`.
- Execution worktrees per lane from `lanes.json`. Depends on WP11 (the `@Service`) and WP07 (the `bootstrap` composition root + green gate). This is the feature + migration convergence point.

## Definition of Done

- `DomainRepositoryService` is a live bean in the production context.
- `WiringRoundTripTest` round-trips through the wired bean, addressing it by domain port type only.
- `./gradlew :bootstrap:test` green.

## Risks

- **Dead code** → the test must resolve the bean from the context, not `new` it.
- **Stereotype leak** → no Spring annotation on the domain port; keep wiring at bootstrap/edge.

## Reviewer guidance

- Confirm the bean comes from the Spring context and the call names only domain types.
- Confirm no Spring Data and no stereotype on the port interface.
