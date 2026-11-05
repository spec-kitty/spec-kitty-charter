---
work_package_id: WP05
title: activations module skeleton and generalised architecture rules
dependencies:
- WP01
requirement_refs:
- C-002
- C-005
- FR-019
- NFR-001
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
subtasks:
- T026
- T027
- T028
- T029
- T030
- T031
- T032
- T033
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: service/activations/
create_intent:
- service/activations/build.gradle.kts
- service/activations/src/main/java/ai/speckitty/charter/activations/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/AvailablePacks.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/config/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/config/ActivationApplicationConfiguration.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationController.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationJson.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/config/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/config/ActivationApiConfiguration.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/PacksAvailability.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/config/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/config/PacksAvailabilityConfiguration.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationJsonTest.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ActivationStorageRoot.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationStorageRootTest.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/domain/badfixture/package-info.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/domain/badfixture/ActivationDomainReachesApplication.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/domain/badfixture/ActivationDomainNamesPacks.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/application/badfixture/package-info.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/application/badfixture/ApplicationNamesPacks.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/infra/api/badfixture/package-info.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/infra/api/badfixture/ApiNamesPacksApplication.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/infra/api/badfixture/ReservedMethodReachesApplication.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/infra/packs/badfixture/package-info.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/infra/packs/badfixture/AdapterNamesPacksStorage.java
execution_mode: code_change
model: ''
owned_files:
- settings.gradle.kts
- service/activations/build.gradle.kts
- service/activations/src/main/java/ai/speckitty/charter/activations/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/AvailablePacks.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/config/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/config/ActivationApplicationConfiguration.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationController.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationJson.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/config/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/config/ActivationApiConfiguration.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/PacksAvailability.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/config/package-info.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/config/PacksAvailabilityConfiguration.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationJsonTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ActivationJson.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ActivationJsonTest.java
- service/charter-reports/build.gradle.kts
- service/deployable/build.gradle.kts
- service/deployable/src/main/java/ai/speckitty/charter/deployable/DeployableConfiguration.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ActivationStorageRoot.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationStorageRootTest.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/domain/badfixture/package-info.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/domain/badfixture/ActivationDomainReachesApplication.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/domain/badfixture/ActivationDomainNamesPacks.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/application/badfixture/package-info.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/application/badfixture/ApplicationNamesPacks.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/infra/api/badfixture/package-info.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/infra/api/badfixture/ApiNamesPacksApplication.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/infra/api/badfixture/ReservedMethodReachesApplication.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/infra/packs/badfixture/package-info.java
- service/deployable/src/test/java/ai/speckitty/charter/activations/infra/packs/badfixture/AdapterNamesPacksStorage.java
- .github/workflows/sonar.yml
- docs/architecture/hexagonal-service.md
role: implementer
tags: []
tracker_refs: []
---

# WP05: activations module skeleton and generalised architecture rules

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Create the `activations` slice as its own Gradle module over `packs` (planning decision `DM-01M6P6T4MTB6SPK0F0F6BSBR7N`),
move the reserved activation controller into it, and generalise the ArchUnit gate (#140) so the new slice is held
to the same onion as `packs` and may name `packs` **only** from `activations.infra.packs`, and only
`packs.application`. All 13 activation operations stay reserved (501). No behaviour changes.

Implementation command:
`spec-kitty agent action implement WP05 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** A. **Concern:** IC-04. **Research:** R1; squad A1, A4, A9.
- **Requirements:** C-002 (the hexagonal slices, no shadow logic), C-005, NFR-001, FR-019 (the storage root).
- **Collisions:** PR #391 touches `settings.gradle.kts` and the build-logic conventions; PR #390 touches
  `docs/architecture/hexagonal-service.md`. Rebase after them and re-apply the module line and the doc section.
- **Grounding:**
  - `settings.gradle.kts` lists the service modules in one `listOf(...)` and maps each to `service/<name>`.
  - `service/charter-reports/build.gradle.kts` has a `serviceModules` map that **fails the build** when a
    hand-written module under `service/` is missing from it. Add `"activations" to "ai.speckitty.charter.activations"`.
  - `.github/workflows/service.yml` filters on `service/**` and has **no** module list. `sonar.yml` lists modules in
    four `-Dsonar.*` properties (lines ~154–157). Only `sonar.yml` changes.
  - `DeployableConfiguration` scans the `packs.*.config` packages by name.
  - `ConfigRoot` (with `activations()`) lives in `packs.infra.storage`. The activations slice may not name it, so the
    composition root (`deployable`) resolves the storage root and hands it over as a plain `Path` value.
  - `HexagonalArchitectureTest` has `SLICES = List.of("packs", "profiles", "missions")`, `PACKS_*` constants, a
    type-scoped `RESERVED_CONTROLLER_READS_NOTHING`, and `EVERY_SLICE_IS_REGISTERED` (slice coverage).
  - `ActivationJson` and its test live in `packs.infra.api`.

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure`.
- Lane worktree from `lanes.json`. It depends on WP01 (the kernel marker and the deployable interceptor).
- Rebase onto the lane base, after #391 and #390 if merged, before review.

## Out-of-map edits (sequenced by dependency)

- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ActivationController.java` (owner WP01): delete it;
  the controller moves to `activations.infra.api`.
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java` (owner WP01): the
  generalisation in T031 (WP01 only fixed imports).

## Subtask Guidance

### Subtask T026: The Gradle module

**Steps:**
1. `service/activations/build.gradle.kts`: plugins `charter.spring-conventions` and `charter.hand-written`, as in
   `service/packs/build.gradle.kts`. Dependencies: `api(project(":kernel"))`, `implementation(project(":packs"))`,
   `api(project(":charter-api"))`, `implementation(catalog.lib("spring-boot-starter-webmvc"))`,
   `implementation(catalog.lib("jackson-databind"))`; test: `spring-boot-starter-test`, `archunit`. Add a header
   comment in the style of the other build files. No new library (DIRECTIVE_051).
2. `settings.gradle.kts`: add `"activations"` after `"missions"` in the list.
3. Package skeleton with `package-info.java` (`@NullMarked`, one Javadoc sentence each) for `activations`,
   `domain`, `application`, `application.config`, `infra.api`, `infra.api.config`, `infra.packs` and
   `infra.packs.config`. Storage packages come in WP07.
4. `service/deployable/build.gradle.kts`: `implementation(project(":activations"))`.

**Validation:** `./gradlew :activations:check` (empty module compiles; Javadoc, Checkstyle, Spotless green).

### Subtask T027: Reports and CI lists

**Steps:**
1. `service/charter-reports/build.gradle.kts`: add `"activations" to "ai.speckitty.charter.activations"` in
   `serviceModules`, after `missions`.
2. `.github/workflows/sonar.yml`: add `service/activations/...` to `sonar.sources`, `sonar.tests`,
   `sonar.java.binaries` and `sonar.java.test.binaries`, in the same order as the other modules.
3. If a Sonar guard test reads the workflow (`git grep -n "sonar.yml" service build-logic`), update its expectation.

**Validation:** `./gradlew :charter-reports:check` (the projectsEvaluated guard passes).

### Subtask T028: Move the controller and the JSON mix-in

**Steps:**
1. Create `activations/infra/api/ActivationController.java` from the packs one. The methods and signatures are
   unchanged. Put `@ReservedOperation` (kernel) on **each method** rather than on the class, so WP08 can unmark
   operations one at a time. Each body throws a private `notBuilt()` `ResponseStatusException(NOT_IMPLEMENTED)`.
   Update the class Javadoc ("a slice of its own" is now true).
2. Move `ActivationJson` to `activations/infra/api/ActivationJson.java` and `ActivationJsonTest` to the activations
   test tree. Update `NullOmissionTest` only if it names the class (it does not today; check).
3. Delete the packs copies (`ActivationController` is an out-of-map delete; `ActivationJson` and its test are owned
   here).
4. `ActivationApiConfiguration` (`@Configuration @ComponentScan("ai.speckitty.charter.activations.infra.api")`)
   mirrors `packs.infra.api.config.ApiConfiguration`.
5. `DeployableConfiguration`: add `activations.application.config`, `activations.infra.api.config` and
   `activations.infra.packs.config` to the scan list. Add `activations.infra.storage.config` in WP07.

**Validation:** `./gradlew :deployable:test --tests '*CharterContractSuite*' --tests '*ReservedOperationCoverageTest*'
--tests '*NullOmissionTest*'`. All 14 pins still answer 501 (now through method-level marks).

### Subtask T029: The `AvailablePacks` port and the adapter stub

**Purpose:** one application-ring port for every pack question (R1). WP08 fills it in.

**Steps:**
1. `activations/application/AvailablePacks.java`: the port interface, **with no method yet**. Its return type,
   `ClosureSnapshot`, is an activations-domain value that WP06 creates, and WP08 adds
   `ClosureSnapshot snapshot(Optional<List<String>> packIds)` when it builds the adapter. Do not declare a placeholder
   signature (an `Object` return would leak into every caller). The Javadoc says: the one port for every pack
   question; only `activations.infra.packs` implements it; called once per request (NFR-007).
2. `activations/infra/packs/PacksAvailability.java`: `@Component` implementing `AvailablePacks`. It depends on nothing
   yet. Its Javadoc states the rule that it is the only class in the slice that may name `packs`, and only
   `packs.application`.
3. `PacksAvailabilityConfiguration` scans `activations.infra.packs`.
4. `ActivationApplicationConfiguration` scans `activations.application`.

### Subtask T030: The storage root from the composition root

**Purpose:** FR-019's file location, `<config-root>/activations/<id>/`, with in-memory storage when no root is
bound.

**Steps (red first, `ActivationStorageRootTest`):**
1. With a `ConfigRoot` bean, the `ActivationStorageRoot` bean holds `ConfigRoot.activations()`.
2. Without one, it is empty (`ActivationStorageRoot.inMemory()`).
3. Create `deployable/ActivationStorageRoot.java`: a record `(Optional<Path> directory)` with a `@Bean` factory in a
   small `@Configuration` (or in `CharterWiring`; prefer a new nested configuration in this file so that
   `CharterWiring` stays out of the map) reading `ObjectProvider<ConfigRoot>`. WP07 consumes it to pick the adapter.
   Nothing here creates a directory, as `ConfigRoot`'s Javadoc requires.

### Subtask T031: Generalise `HexagonalArchitectureTest` (#140)

**Steps (out-of-map edit, red first: add the bad fixtures of T032 and see the new rules fail on them):**
1. `SLICES = List.of("packs", "profiles", "missions", "activations")`. `EVERY_SLICE_IS_REGISTERED` then covers it.
2. Replace the pack-pinned rules with per-slice factories, as `sliceDomainDoesNotCross(slice)` already does:
   `domainIsPure(slice)`, `applicationIsEdge(slice)` and `entrypointOverApplication(slice)`. Apply them to every slice
   that has the ring; check `classes.that().resideInAPackage(...)` is non-empty, or use `allowEmptyShould(true)` for
   the rings a slice lacks (profiles and missions have only `domain`).
3. **Generated types:** `GENERATED_API` may be named from any slice's `infra.api` (`..infra.api..`), not only
   `PACKS_API`. Update the rule at line ~570.
4. **The cross-slice adapter rule:** only `activations.infra.packs..` may depend on `packs..`, and only on
   `packs.application..` (and the `packs.domain` value types those signatures expose, if any: check which types
   `ClosureView` exposes, and allow exactly those by package, `packs.domain..`). `packs.infra..` is never allowed.
   Write it as two rules: `ACTIVATIONS_NAME_PACKS_ONLY_FROM_ADAPTER` and `ACTIVATIONS_ADAPTER_NAMES_PACKS_APPLICATION`.
5. **Method-scoped reserved rule:** `RESERVED_CONTROLLER_READS_NOTHING` becomes "a method annotated
   `@ReservedOperation` calls no application, domain or storage method and touches no field of such a type". Use
   `methods().that().areAnnotatedWith(ReservedOperation.class).should().onlyCallMethodsThat(...)` or
   `noMethods()...should().accessTargetWhere(...)`. Keep the type-level rule for type-annotated controllers.
6. `PORT_INTERFACES_RESIDE_IN_DOMAIN`, `PORT_IMPLEMENTATIONS_RESIDE_IN_STORAGE` and `STORAGE_RETURNS_DOMAIN` become
   per-slice too (`<slice>.infra.storage..`), so WP07's `ActivationRepository` and its adapters are gated from the
   start. The adapter `PacksAvailability` implements an **application** port, not a domain repository, so check that
   the port-placement rule's predicate (repository/gateway interfaces) does not misfire on it.
7. `ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS` must allow the activations adapters (`activations.infra..`) to be named
   from deployable only. Generalise it to `..infra..` of every slice.

**Validation:** `./gradlew :deployable:test --tests '*HexagonalArchitectureTest*'`. Every old rule still passes, every
new rule bites on its fixture.

### Subtask T032: Bad fixtures for the new slice

**Steps:** under `service/deployable/src/test/java/ai/speckitty/charter/activations/...badfixture/` (each with a
`package-info.java`), add one class per rule. Each must be the smallest class that breaks exactly one rule:
- `domain/badfixture/ActivationDomainReachesApplication`: the domain names an `activations.application` type;
- `domain/badfixture/ActivationDomainNamesPacks`: the domain names a `packs.domain` type (slices do not cross);
- `application/badfixture/ApplicationNamesPacks`: the application names `packs.application`;
- `infra/api/badfixture/ApiNamesPacksApplication`: the controller side names `packs.application`;
- `infra/api/badfixture/ReservedMethodReachesApplication`: a method marked `@ReservedOperation` calls an
  application service;
- `infra/packs/badfixture/AdapterNamesPacksStorage`: the adapter names `packs.infra.storage`.

Add an `assertBites` test per fixture, as the existing `the…RuleFailsOn…` tests do. The fixtures are test classes, so
they never ship.

### Subtask T033: Document the cross-slice adapter rule

**Steps:** in `docs/architecture/hexagonal-service.md`:
1. Add `activations` to the Modules table (depends on `kernel`, `packs`; "the activation register: the document,
   its storage, its write rules and its HTTP adapter; asks `packs` through one port").
2. Under "The rule", add a short subsection "A slice over another slice": only one adapter package
   (`<slice>.infra.<other>`) may name the other slice, and only its `application` ring, through one application port
   of its own. Cite ADR-0006 and planning decision 2026-11-05.
3. Update `updated:` in the frontmatter. Run `python3 scripts/docs/check_docs.py all` and the spelling check.

## Checkstyle limits (NFR-001)

CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1 (by construction). The ArchUnit test is long; keep each rule
factory short. No new suppression, and no disabled test (C-005).

## Gate commands

```bash
./gradlew :activations:check
./gradlew :charter-reports:check
./gradlew :deployable:test --tests '*HexagonalArchitectureTest*' --tests '*ActivationStorageRootTest*' \
  --tests '*CharterContractSuite*' --tests '*ReservedOperationCoverageTest*' --tests '*ProductionWiringTest*'
python3 scripts/docs/check_docs.py all
```

## Definition of Done

- [ ] `:activations` is a module in settings, charter-reports, deployable and `sonar.yml`.
- [ ] `ActivationController` and `ActivationJson` live in `activations.infra.api`. Every method is marked reserved,
      and all 14 pins answer 501.
- [ ] `ActivationStorageRoot` gives `<config-root>/activations` or in-memory, with its test.
- [ ] `HexagonalArchitectureTest` covers `activations` with per-slice rules, the generated-type rule for any
      `infra.api`, the adapter-only `packs` rule and the method-scoped reserved rule; each new rule bites on a fixture.
- [ ] `hexagonal-service.md` documents the cross-slice adapter rule; the docs checks pass.
- [ ] No new suppression, and no disabled test.

## Risks

- **Over-broad generalisation** can weaken an old rule. Keep every old `assertBites` test, and run them all.
- **Native image:** a new module's beans are found by component scan. Check `CharterRuntimeHintsTest` and the
  native build's reflection needs with `./gradlew :deployable:test --tests '*CharterRuntimeHintsTest*'`. The native
  executable itself is built in CI.
- **#391 conventions churn** may change plugin ids; rebase and adopt them.

## Reviewer guidance (reviewer-renata)

- **Every new rule must bite.** For each rule, find its fixture and its `assertBites` test. A rule without a failing
  fixture is unproven.
- **The adapter rule is two-sided:** only `infra.packs` may name `packs`, **and** only `packs.application`. Check both
  fixtures (`ApplicationNamesPacks`, `AdapterNamesPacksStorage`).
- **Method-level reserved:** check that `ActivationController` has no class-level `@ReservedOperation` and that the
  reserved-coverage test still counts 13.
- **Non-fakeable evidence:** `./gradlew :charter-reports:check` output (the guard), and the ArchUnit test report
  listing the new tests.
