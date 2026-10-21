---
work_package_id: WP07
title: Migrate charter-service → bootstrap + settings/reports/ArchUnit inventory
dependencies:
- WP06
requirement_refs:
- FR-013
- FR-002
- NFR-005
- SC-005
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-repositories-validation-01M5EET9
base_commit: ef3222a3b79d8133de27da891ddf5647c56eb9d7
created_at: '2026-10-21T14:06:51.114959+00:00'
subtasks:
- T020
- T021
- T022
- T023
- T024
- T025
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/bootstrap/src/test/java/ai/speckitty/charter/bootstrap/
create_intent:
- service/bootstrap/src/test/java/ai/speckitty/charter/bootstrap/HexagonalArchitectureTest.java
execution_mode: code_change
owned_files:
- service/charter-service/**
- settings.gradle.kts
- service/charter-reports/build.gradle.kts
- build-logic/**
- service/bootstrap/src/test/java/ai/speckitty/charter/bootstrap/HexagonalArchitectureTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP07 – Migrate charter-service → bootstrap + settings/reports/ArchUnit inventory

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Close the migration: relocate the composition root to `ai.speckitty.charter.bootstrap`, finalize the module set, and **rewrite the full ArchUnit rule inventory** onto the ADR-0006 slice layout — porting, keeping, and adding every rule per `occurrence_map.yaml`, with each known-bad control co-located in the NEW packages so no glob rule is vacuously green. This is the re-gating half of IC-01 plus step 6 of IC-02.

## Context

- The authoritative rule inventory — port / keep-as-supplement / add, and the known-bad mandate: [occurrence_map.yaml](../occurrence_map.yaml) `archunit_rules_rewrite` (read every bullet).
- Module set + `charter-reports` serviceModules + `contracts/` verify: [occurrence_map.yaml](../occurrence_map.yaml) `module_moves`; [plan.md](../plan.md) IC-01 "ArchUnit rewrite — inventory ALL".
- The expected green rule list: [quickstart.md](../quickstart.md) §1.
- This WP owns `settings.gradle.kts`, `build-logic/**` and `charter-reports/build.gradle.kts` as the single writer; the migration WPs deliberately left them untouched.

### Subtask T020 — Move the composition root → bootstrap
- **Steps**: move `CharterServiceApplication`, `CharterProperties`, `CharterWiring`, `GuardWiring`, `package-info` to `service/bootstrap/src/main/java/ai/speckitty/charter/bootstrap/`, rewriting packages/imports. Keep Spring wiring behaviour identical.
- **Files**: `charter-service` main → `bootstrap` main.
- **Validation**: `./gradlew :bootstrap:compileJava`.
- **Edge cases**: component-scan base package changes to `ai.speckitty.charter` roots — ensure the adapters in `packs.infra.*` are still scanned.

### Subtask T021 — Rewrite settings.gradle.kts to the slice module set
- **Steps**: replace the `charter-domain/charter-read-app/adapter-in-rest/adapter-out-yaml/charter-service` includes with `kernel`, `packs`, `bootstrap`; keep `charter-api`, `charter-reports`, `contracts`; set each `projectDir` to `service/<name>`.
- **Files**: `settings.gradle.kts`.
- **Validation**: `./gradlew projects` lists exactly `{kernel, packs, bootstrap, charter-api, charter-reports, contracts}`.
- **Edge cases**: the old module dirs must already be drained (WP02..WP06 + WP09/WP11) before their removal from the include list — coordinate landing order on the integration branch.

### Subtask T022 — charter-reports serviceModules + build-logic + contracts verify
- **Steps**:
  1. Update `service/charter-reports/build.gradle.kts` `serviceModules` map to exactly `{kernel, packs, bootstrap}` (it hard-asserts the hand-written service set or `verifyAll` breaks).
  2. Update any `build-logic` convention plugin that names modules by the old names.
  3. Verify `contracts/` closure vocabulary / package references survive the rename (scoped; likely no change) — run its check.
- **Files**: `charter-reports/build.gradle.kts`, `build-logic/**`.
- **Validation**: `./gradlew :charter-reports:build` and the contracts check pass.

### Subtask T023 — Move + rewrite the full ArchUnit inventory
- **Purpose**: Re-express every production guard family + known-bad on the ADR-0006 globs, dropping none.
- **Steps**: move `HexagonalArchitectureTest` to `service/bootstrap/src/test/java/ai/speckitty/charter/bootstrap/HexagonalArchitectureTest.java` and rewrite, mapping each rule per `occurrence_map.yaml`:
  - **PORT** `dependenciesPointInward` → `ONION_DEPENDS_INWARD` over `kernel`/`<slice>.domain`/`application`/`infra.storage`/`infra.api`; **add** `ENTRYPOINT_OVER_APPLICATION`, `PORTS_LIVE_IN_DOMAIN`, `STORAGE_RETURNS_DOMAIN`, `SLICES_DO_NOT_CROSS`.
  - **PORT** `theCoreIsPlainJava` → `DOMAIN_IS_PURE` allowing `java` + `..kernel..` (broadened to include `kernel.validation`, DM 01M5F6RC) + `jspecify` + own-slice domain. (The dedicated `..kernel.validation.. → {java, jspecify}` purity rule + its known-bad are WP08's file — reference, do not duplicate the known-bad here.)
  - **KEEP** `portSignaturesNameOnlyDomainTypes` (+ known-bad) → extend the allow-list to `..kernel.domain..` + `..kernel.validation..` (ports may name `Constraint`/`Evaluation`).
  - **PORT** `transportTypesStayInTheInboundAdapter` → `TRANSPORT_STAYS_IN_ADAPTER` over `..packs.infra.api..` + `charter-api`.
  - **KEEP** `onlyTheInboundAdapterRefusesWithInternalError` (+ known-bad) → repoint to `..packs.infra.api..`.
  - **KEEP** the three pack-policy rules `onlyThePolicyNamesThePackLevelFindings` / `onlyThePolicyNamesTheMissingDiagnostic` / `onlyThePolicyNamesTheMismatchRefusal` (+ known-bads) → repoint `PackPolicy` to `..packs.domain..`.
  - **KEEP** `onlyTheYamlAdapterUsesNetworknt` (+ known-bad) → keep an explicit "only `..packs.infra.storage..` depends on `com.networknt`" rule.
  - **KEEP** `everyHandWrittenMainPackageIsNullMarked` (+ known-bad) → cover the new kernel/packs packages.
  - **KEEP** `noMainClassDependsOnATestSeam` (+ known-bad) → unchanged intent.
  - **ADD** `GENERATED_API_NOT_IN_DOMAIN` → `..domain..` imports nothing from `charter-api`.
- **Files**: `HexagonalArchitectureTest.java` (bootstrap test).
- **Validation**: `./gradlew :bootstrap:test --tests '*HexagonalArchitectureTest'`.
- **Edge cases**: NO guard silently dropped — tick off each of the ~10 families + the ~20 `@Test` methods against the live file before.

### Subtask T024 — Co-locate every known-bad control in the NEW packages
- **Purpose**: Defeat glob vacuity — a glob rule over empty/old packages passes trivially.
- **Steps**: move the fixture packages (`archsample`, `networkntfixture`, `internalerrorfixture`, `packlevelfixture`, `seamfixture`, `unmarkedfixture`, `com/networknt/fixture`) into the bootstrap test tree and re-home each known-bad sample so it lives in (or imports into) the NEW `kernel`/`packs` packages the rule now governs, reproducing the violation it is meant to catch.
- **Files**: the fixture packages under `bootstrap` test sources.
- **Validation**: each known-bad reds exactly its own rule when the rule's allow-list is temporarily relaxed (document the control, do not leave it relaxed).

### Subtask T025 — Green gate under the renamed modules
- **Steps**: run `./gradlew verifyAll`; confirm green except the pre-existing environmental `spec-kitty-open-packs` pin on `VendoredPacksTest` (quickstart §5). Confirm every repointed rule still bites (no vacuous green).
- **Validation**: `./gradlew verifyAll`.

## Branch Strategy

- Planning branch `feat/pack-repositories-validation`; final merge target `feat/pack-repositories-validation`.
- Execution worktrees per lane from `lanes.json`. Depends on WP06; it is the migration's convergence point before the feature WPs' wiring (WP12) joins.

## Definition of Done

- The composition root lives in `bootstrap`; the module set is `{kernel, packs, bootstrap, charter-api, charter-reports, contracts}`.
- The full ArchUnit inventory is rewritten on ADR-0006 globs with every known-bad co-located and biting.
- `./gradlew verifyAll` green (modulo the pinned environmental `VendoredPacksTest`).

## Risks

- **Silently dropped guard** → inventory ALL ~10 families + ~20 `@Test`s; map each (port / keep / add); none dropped.
- **Vacuous glob** → known-bads must live in the new packages, reproducing real violations.
- **serviceModules mismatch** → `charter-reports` must equal exactly `{kernel, packs, bootstrap}` or `verifyAll` breaks.

## Reviewer guidance

- Diff the old vs new rule list method-by-method; confirm 1:1 coverage + the four added ADR-0006 rules.
- Confirm each known-bad sample resides in a new package and reds its rule.
- Confirm `settings.gradle.kts`, `charter-reports` serviceModules and `build-logic` name only the new module set.
