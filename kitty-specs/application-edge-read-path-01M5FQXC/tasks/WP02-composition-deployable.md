---
work_package_id: WP02
title: 'Composition root: deployable rename + aggregate config + revised ArchUnit gate'
dependencies: []
requirement_refs:
- FR-006
- FR-007
- FR-011
- NFR-003
- SC-003
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-application-edge-read-path-01M5FQXC
base_commit: 815db4ae3ad97608b166a2750be6311187d66fb7
created_at: '2026-10-21T19:38:31.096193+00:00'
subtasks:
- T006
- T007
- T008
- T009
- T010
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/deployable/
create_intent:
- service/deployable/build.gradle.kts
- service/deployable/src/main/java/ai/speckitty/charter/deployable/CharterServiceApplication.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/DeployableConfiguration.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/CharterProperties.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/CharterWiring.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/GuardWiring.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/PackPersistenceWiring.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/package-info.java
- service/deployable/src/main/resources/application.yaml
- service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
execution_mode: code_change
owned_files:
- service/deployable/**
- service/bootstrap/**
- settings.gradle.kts
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP02 – Composition root: deployable rename + aggregate config + revised ArchUnit gate

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Rename the `bootstrap` module/package to `deployable`, add the aggregate `DeployableConfiguration`, and revise the ArchUnit gate so the application ring is an **edge** (`APPLICATION_IS_EDGE`). Keep the context booting after the rename. This is IC-01 (code) + IC-02 (composition). Decisions: [research.md](../research.md) DMs `gate.application-edge-rule`, `structure.deployable-package`.

## Context
- Current gate: `service/bootstrap/src/test/java/ai/speckitty/charter/bootstrap/HexagonalArchitectureTest.java`. Study `APPLICATION_IS_PLAIN_JAVA` (+ control `ReachesStorage`) and every `BOOTSTRAP` reference.
- Current wiring: `CharterWiring`, `GuardWiring`, `PackPersistenceWiring`, `CharterServiceApplication` (`@SpringBootApplication(scanBasePackageClasses={CharterServiceApplication, ContextController})`), `CharterProperties`.
- The `@Service DomainRepositoryService` lives in `packs.infra.storage` (already annotated).

### T006 — Rename `bootstrap` → `deployable`
- `git mv service/bootstrap service/deployable`; move the Java package `ai.speckitty.charter.bootstrap` → `ai.speckitty.charter.deployable` (update `package`/imports). The ArchUnit badfixtures under `service/bootstrap/src/test/.../packs/**/badfixture` and `kernel/validation/badfixture` keep their own packages — only their physical module moves. Update `settings.gradle.kts` (`:bootstrap`→`:deployable`) and any `:bootstrap` project references in build files.

### T007 — Revise the ArchUnit gate
- Replace `APPLICATION_IS_PLAIN_JAVA` with **`APPLICATION_IS_EDGE`**: application (`..packs.application..`) may depend on domain, kernel, `java`, jspecify **and framework**, but MUST NOT depend on another slice's `..infra..`, on cross-slice domain, or on `..deployable..`. Keep a biting known-bad control (repurpose `ReachesStorage` so the rule still fails naming a violator; the control proves the cross-boundary half still bites). Leave `DOMAIN_IS_PURE`, `KERNEL_IS_PURE`, `VALIDATION_LIBRARY_IS_PURE`, and the other rules **unchanged** (NFR-003 — purity stays double-gated). Retarget every `BOOTSTRAP` constant/reference and the `ONION_ONLY_BOOTSTRAP_COMPOSES_ADAPTERS` + NullMarked fixtures to `deployable`.
- **Repoint fixtures that import soon-deleted scaffolding.** The known-bad fixture `DomainWithInfraImport` imports `FileSystemCharterPackRepository` (deleted in WP04) to prove `PORT_SIGNATURES_NAME_ONLY_DOMAIN_TYPES` bites, and the gate asserts that class name in the message. Repoint it (and any other fixture naming a to-be-deleted scaffolding type) to a **surviving** infra type (e.g. `FilesystemPackStore` or `DomainRepositoryService`) and update the asserted message substring, so the branch stays green after WP04's deletion. These fixtures live under `service/deployable/src/test` (this WP owns them).

### T008 — `DeployableConfiguration`
- Add `DeployableConfiguration` (a `@Configuration`) that `@ComponentScan`s the three layer **config** packages: `ai.speckitty.charter.packs.application.config`, `...packs.infra.storage.config`, `...packs.infra.api.config` (they may be empty until WP04/05/06 — an empty scan is fine). `CharterServiceApplication` keeps `@SpringBootApplication` and imports/holds `DeployableConfiguration`. This is the aggregate that makes modular `@SpringBootTest` slices cheap (SC-003).

### T009 — Drop the explicit `DomainRepositoryService` `@Bean`
- Remove the explicit `DomainRepositoryService` `@Bean` from `PackPersistenceWiring` (it becomes scan-driven via WP04's `StorageConfiguration`). **Keep** the `DomainRepositoryService.Locations` `@Bean` (operator-config-derived) and the guard beans here. Note in your report: between this WP and WP04 the storage bean is absent by design — do not add a full-context smoke test that requires it in this WP.

### T010 — Adapt moved tests
- Fix package/imports in the moved deployable tests. Any existing full-context `@SpringBootTest` (e.g. `ProductionWiringTest`, `WiringRoundTripTest`) that now fails because `DomainRepositoryService` is not yet scan-provided: narrow it to what WP02 can prove, or `@Disabled("re-enabled when StorageConfiguration lands in WP04")` with a clear note — WP05's wired test is the end-state proof. Do NOT weaken the ArchUnit gate to make it pass.

## Definition of Done
- `:deployable:test --tests '*HexagonalArchitectureTest'` green; `APPLICATION_IS_EDGE` present with a biting control; domain/kernel purity rules untouched and still passing.
- Module builds under `:deployable`; `settings.gradle.kts` updated; no `:bootstrap` references remain.

## Reviewer guidance (test economy — DIRECTIVE_041)
- The gate control must actually fail naming its violator (non-vacuous). Confirm no purity rule was loosened. Confirm disabled tests carry a precise re-enable note, not a silent skip. No new test should merely assert the rename.
