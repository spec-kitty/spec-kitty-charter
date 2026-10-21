# Tasks: Pack repositories and validation module

**Mission**: pack-repositories-validation-01M5EET9 | **Branch**: `feat/pack-repositories-validation`
**Plan**: [plan.md](./plan.md) · **Spec**: [spec.md](./spec.md) · **Data model**: [data-model.md](./data-model.md) · **Occurrence map**: [occurrence_map.yaml](./occurrence_map.yaml)

Thirteen work packages realize the Implementation Concern Map (IC-01..IC-08) as one phased, behaviour-preserving PR: **skeleton → migrate (bottom-up) → validation → domain → persistence → wiring → docs**. Tests ship **inside their concern WP** (DIRECTIVE_041 — no standalone test WP); every absence-assertion is paired with a named positive control; no shape-pins. IC-08 is the acceptance checklist across WP01/WP08/WP09/WP10/WP11/WP12, not a WP.

> **Numbering note**: the plan's logical sub-steps `IC-02 02a..02f` and `IC-05 a/b` are expressed here as the flat numeric WP IDs the tooling requires (WP02..WP07 = the six bottom-up migration steps; WP10/WP11 = the two persistence steps). Grouping is preserved in the titles.

The migration (WP02..WP07) moves the existing six modules onto the ADR-0006 slice layout verbatim, one source module per WP, bottom-up, keeping `verifyAll` green at each step. The feature (WP08..WP12) then adds the validation kernel, the aggregate/ports, the persistence edge and the wiring. WP13 writes the DDD docs.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `service/kernel` gradle module (java-library, jspecify; `kernel.domain` + `kernel.validation` homes) | WP01 | [P] |
| T002 | `service/packs` gradle module (depends `kernel`; 4-layer homes; snakeyaml/networknt/spring staged) | WP01 | [P] |
| T003 | `service/bootstrap` gradle module (spring-boot app; depends `kernel`+`packs`; archunit/junit/assertj) | WP01 | [P] |
| T004 | Bootstrap ArchUnit **skeleton shell** test (`SliceSkeletonArchTest`) asserting the package homes exist | WP01 | |
| T005 | Move the 13 firm-kernel classes `charter-domain → kernel.domain` (packages + imports) | WP02 | |
| T006 | `kernel.domain` `package-info` (`@NullMarked`) + move the 7 kernel-domain unit tests | WP02 | |
| T007 | Rewrite downstream `project()` deps + imports consuming the moved kernel classes; `verifyAll` green | WP02 | |
| T008 | Move the 15 stable pack classes `charter-domain → packs.domain` (keep names) + `package-info` | WP03 | |
| T009 | Move the 8 pack-domain unit tests; carve fixtures to the owning slice | WP03 | |
| T010 | Rewrite downstream `project()`/imports for pack types; tear down the drained `charter-domain` build file | WP03 | |
| T011 | Move `charter-read-app → packs.application` (packages + imports) | WP04 | |
| T012 | Move the 4 application query tests | WP04 | |
| T013 | Rewrite downstream `project()`/imports; `verifyAll` green | WP04 | |
| T014 | Move `adapter-out-yaml → packs.infra.storage` (**keep `Yaml…` class names**) | WP05 | |
| T015 | Move the adapter tests + fixtures (the `PackElement*Test` battery is replaced in WP10) | WP05 | |
| T016 | Rewrite downstream `project()`/imports; `verifyAll` green | WP05 | |
| T017 | Move `adapter-in-rest → packs.infra.api` (packages + imports) | WP06 | |
| T018 | Move the 6 web tests | WP06 | |
| T019 | Rewrite downstream `project()`/imports; `verifyAll` green | WP06 | |
| T020 | Move `charter-service → bootstrap` (composition root; packages + imports) | WP07 | |
| T021 | Rewrite `settings.gradle.kts` to the `{kernel, packs, bootstrap}` module set + projectDirs | WP07 | |
| T022 | Update `charter-reports` `serviceModules` map → `{kernel, packs, bootstrap}`; `build-logic`; verify `contracts/` | WP07 | |
| T023 | Move `HexagonalArchitectureTest → bootstrap` and rewrite the **full** ADR-0006 rule inventory | WP07 | |
| T024 | Co-locate every known-bad control in the NEW packages (defeat glob vacuity) | WP07 | |
| T025 | `verifyAll` green under the renamed modules; every repointed rule still biting | WP07 | |
| T026 | `Constraint` record (predicate + description; value-correct `equals` — fix the description-only defect) | WP08 | [P] |
| T027 | `Constraints` / `Checks` factories + guards (drop commons-lang3) | WP08 | |
| T028 | `ModularRuleset` with `guard()` (fail-fast, bespoke exception) + `check()` (collect-all) | WP08 | |
| T029 | `Evaluation` (PASS/FAIL + reasons), `Reason`, `Rationale` | WP08 | |
| T030 | `Constrained<T>` monadic result | WP08 | |
| T031 | Validation-purity ArchUnit rule (`..kernel.validation.. → {java, jspecify}`) + known-bad control | WP08 | |
| T032 | Apache-2.0 attribution + a "took/changed" ledger (omit Evidence/dynamic-descriptions/dsl) | WP08 | |
| T033 | `PackIdentifier` / `TacticIdentifier` identifier VO records | WP09 | [P] |
| T034 | `TacticStep` / `TacticReference` / `PackPaths` value-object records | WP09 | [P] |
| T035 | `Tactic` child entity (reshape: identity equals, holds `PackIdentifier`; fluent builder + pkg-private ctor) | WP09 | |
| T036 | `Pack` aggregate root (identity `PackIdentifier`; composes descriptor + tactics + read violations) | WP09 | |
| T037 | Leaf ports: `TacticRepository`, `PackDescriptorRepository` (domain-only signatures) | WP09 | |
| T038 | Aggregating `PackRepository` + `PackInspectionQuery` (returns the KEPT `PackElementViolation`) | WP09 | |
| T039 | Constructor guards via the `kernel.validation` vocabulary | WP09 | |
| T040 | `SerializationStrategy` interface | WP10 | [P] |
| T041 | `YamlSerializationStrategy` (reuses `YamlDumpEmitter`, `PackSchemas`) | WP10 | |
| T042 | `TacticDso` / `PackDescriptorDso` records (value equality carries the fidelity assertion) | WP10 | [P] |
| T043 | Filesystem storage mechanics: temp→`ATOMIC_MOVE`, `PackIdentifier`→path, pack-root containment | WP10 | |
| T044 | Round-trip literal oracle + write→refetch mutation control | WP10 | |
| T045 | Write-safety failure-injection control (target untouched, no stray temp) | WP10 | |
| T046 | Symlink-escape control + in-root positive control | WP10 | |
| T047 | `DomainRepositoryService` `@Service` realizing the ports | WP11 | |
| T048 | Hand-written DSO↔entity mapping | WP11 | |
| T049 | Retire `PackElementRepository` + `PackLocator` (deliberate, documented) | WP11 | |
| T050 | Replace the old `PackElement*Test` battery with the new ports' tests (absent-id empty + present control) | WP11 | |
| T051 | Register the edge `@Service` in `bootstrap` (component-scan / `@Bean`) | WP12 | |
| T052 | Wiring test that round-trips through the WIRED bean | WP12 | |
| T053 | Confirm the port signatures stay domain-only through the live path (port-signature rule bites) | WP12 | |
| T054 | `docs/development/code-style-and-conventions.md` | WP13 | [P] |
| T055 | "How to DDD here" guide | WP13 | [P] |
| T056 | `docs/architecture/architectural-patterns-applied.md` (concern↔pattern-role map) | WP13 | |
| T057 | Copy the canonical C4 + class PlantUML into `docs/architecture/diagrams/` and embed | WP13 | |
| T058 | Glossary entries (Domain vs Persistence Entity + VO/aggregate/port/adapter/projection/DTO vs DSO/identifier VO) | WP13 | [P] |
| T059 | `docs/changelog` entry (validation module + repository reshape + slice rename) | WP13 | [P] |
| T060 | Amend `docs/adr/0006-onion-vertical-slices-archunit.md` → Accepted/realized | WP13 | |

## Work Packages

### WP01 — Slice skeleton & module scaffolding (IC-01)

- **Goal**: Stand up the `kernel`/`packs`/`bootstrap` modules and the `ai.speckitty.charter.<concern>.<layer>` package homes; add a bootstrap ArchUnit skeleton shell.
- **Priority**: P1 (foundational). **Independent test**: the three new modules configure (`:kernel:compileJava` etc.); `SliceSkeletonArchTest` asserts the homes exist.
- **Subtasks**:
  - T001 `service/kernel` gradle module (WP01)
  - T002 `service/packs` gradle module (WP01)
  - T003 `service/bootstrap` gradle module (WP01)
  - T004 Bootstrap ArchUnit skeleton shell test (WP01)
- **Prompt**: [tasks/WP01-slice-skeleton.md](./tasks/WP01-slice-skeleton.md)
- **Implementation sketch**: three `build.gradle.kts` applying the existing convention plugins; empty source trees; a shell ArchUnit test superseded by WP07.
- **Dependencies**: none.
- **Parallel notes**: T001–T003 independent `[P]`.
- **Risks**: glob rules over empty packages pass trivially — the shell is existence-only; the biting inventory lands in WP07.

### WP02 — Migrate charter-domain → kernel.domain (IC-02, bottom-up step 1)

- **Goal**: Relocate the 13 firm-kernel classes to `kernel.domain`, verbatim, `verifyAll` green.
- **Priority**: P1. **Independent test**: `:kernel:test` green; consumers still compile.
- **Subtasks**:
  - T005 Move the 13 firm-kernel classes (WP02)
  - T006 `kernel.domain` package-info + 7 tests (WP02)
  - T007 Downstream `project()`/import rewrites (WP02)
- **Prompt**: [tasks/WP02-migrate-kernel-domain.md](./tasks/WP02-migrate-kernel-domain.md)
- **Implementation sketch**: move the DRG/descriptor/primitive/refusal vocab + 7 tests; add the `project(":kernel")` edge.
- **Dependencies**: WP01.
- **Risks**: `PackDescriptor` ripple into readers/writers/app — rewrite all callers in-lane.

### WP03 — Migrate charter-domain pack types → packs.domain (IC-02 step 2)

- **Goal**: Relocate the 15 stable pack classes to `packs.domain`, verbatim.
- **Priority**: P1. **Independent test**: `:packs:test` compiles the moved pack tests; `verifyAll` green.
- **Subtasks**:
  - T008 Move the 15 stable pack classes + package-info (WP03)
  - T009 Move the 8 pack-domain tests (WP03)
  - T010 Downstream rewrites + charter-domain build teardown (WP03)
- **Prompt**: [tasks/WP03-migrate-packs-domain.md](./tasks/WP03-migrate-packs-domain.md)
- **Implementation sketch**: move the stable cluster (incl. the KEPT `PackElementViolation`); leave the roundtrip cluster for WP09 and the retiring ports for WP11.
- **Dependencies**: WP02.
- **Risks**: do not delete `charter-domain` prematurely — WP09/WP11 still drain it.

### WP04 — Migrate charter-read-app → packs.application (IC-02 step 3)

- **Goal**: Relocate the read/application use cases to `packs.application`.
- **Priority**: P1. **Independent test**: `:packs:test` (application) green; `verifyAll` green.
- **Subtasks**:
  - T011 Move the application module (WP04)
  - T012 Move the 4 query tests (WP04)
  - T013 Downstream rewrites + de-registration (WP04)
- **Prompt**: [tasks/WP04-migrate-packs-application.md](./tasks/WP04-migrate-packs-application.md)
- **Implementation sketch**: whole-module move; rewrite imports to `kernel.domain`/`packs.{domain,application}`.
- **Dependencies**: WP03.
- **Risks**: NFR-002 — `ConsistentLoad`/listing/action-context queries move-only, no logic edits.

### WP05 — Migrate adapter-out-yaml → packs.infra.storage (IC-02 step 4)

- **Goal**: Relocate the YAML adapter to `packs.infra.storage`, keeping `Yaml…` names.
- **Priority**: P1. **Independent test**: `:packs:test` (storage, minus the retired `PackElement*Test`) green.
- **Subtasks**:
  - T014 Move the adapter main (WP05)
  - T015 Move the adapter tests + fixtures (WP05)
  - T016 Downstream rewrites + de-registration (WP05)
- **Prompt**: [tasks/WP05-migrate-packs-infra-storage.md](./tasks/WP05-migrate-packs-infra-storage.md)
- **Implementation sketch**: move all 19 adapter classes + tests/fixtures; `PackElementRoundTripTest`/`PackElementWriteSafetyTest` move with the module but are replaced in WP10.
- **Dependencies**: WP04.
- **Risks**: keep `com.networknt` confined here; no retirement/logic change.

### WP06 — Migrate adapter-in-rest → packs.infra.api (IC-02 step 5)

- **Goal**: Relocate the REST inbound adapter to `packs.infra.api`.
- **Priority**: P1. **Independent test**: `:packs:test` (web) green; `verifyAll` green.
- **Subtasks**:
  - T017 Move the web adapter main (WP06)
  - T018 Move the 6 web tests (WP06)
  - T019 Downstream rewrites + de-registration (WP06)
- **Prompt**: [tasks/WP06-migrate-packs-infra-api.md](./tasks/WP06-migrate-packs-infra-api.md)
- **Implementation sketch**: whole-module move; HTTP contract (paths/operationIds/relations) unchanged.
- **Dependencies**: WP05.
- **Risks**: transport must stay in the adapter (rule asserted in WP07).

### WP07 — Migrate charter-service → bootstrap + settings/reports/ArchUnit inventory (IC-02 step 6, IC-01)

- **Goal**: Relocate the composition root to `bootstrap`, finalize the module set, and rewrite the full ArchUnit inventory on ADR-0006 with co-located known-bads.
- **Priority**: P1. **Independent test**: `verifyAll` green; `*HexagonalArchitectureTest` asserts all rules + known-bads red.
- **Subtasks**:
  - T020 Move the composition root → bootstrap (WP07)
  - T021 Rewrite `settings.gradle.kts` to the slice module set (WP07)
  - T022 charter-reports serviceModules + build-logic + contracts verify (WP07)
  - T023 Move + rewrite the full ArchUnit inventory (WP07)
  - T024 Co-locate every known-bad in the NEW packages (WP07)
  - T025 Green gate under the renamed modules (WP07)
- **Prompt**: [tasks/WP07-migrate-bootstrap-and-archunit.md](./tasks/WP07-migrate-bootstrap-and-archunit.md)
- **Implementation sketch**: port/keep/add every rule per `occurrence_map.yaml` `archunit_rules_rewrite`; serviceModules = `{kernel, packs, bootstrap}`.
- **Dependencies**: WP06.
- **Risks**: a silently-dropped guard or vacuous glob — inventory ALL families + co-locate every known-bad.

### WP08 — Validation kernel sub-package (IC-03)

- **Goal**: A lean jspecify-only port of `modular-validators` core as `kernel.validation`, gated pure.
- **Priority**: P1. **Independent test**: `:kernel:test` — `guard()` throws first, `check()` accumulates; purity rule green, known-bad reds.
- **Subtasks**:
  - T026 `Constraint` record + value-correct equals (WP08)
  - T027 `Constraints` / `Checks` factories (WP08)
  - T028 `ModularRuleset` guard() + check() (WP08)
  - T029 `Evaluation` / `Reason` / `Rationale` (WP08)
  - T030 `Constrained<T>` monadic result (WP08)
  - T031 Validation-purity ArchUnit rule + known-bad (WP08)
  - T032 Attribution + took/changed ledger (WP08)
- **Prompt**: [tasks/WP08-kernel-validation.md](./tasks/WP08-kernel-validation.md)
- **Implementation sketch**: zero third-party imports; drop commons-lang3; fix the description-only equals; omit Evidence/dynamic-descriptions/dsl.
- **Dependencies**: WP02.
- **Parallel notes**: T026 `[P]`.
- **Risks**: accidental third-party import — the purity rule + known-bad close it.

### WP09 — Domain model: aggregate, entity, value objects, ports (IC-04)

- **Goal**: `Pack` aggregate root + `Tactic` child entity + VO/identifier records + the four ports; guards via `kernel.validation`.
- **Priority**: P1. **Independent test**: `:packs:test` — builder yields an equal entity, package-private ctor unreachable out-of-package; ports domain-only.
- **Subtasks**:
  - T033 Identifier VOs (WP09)
  - T034 Value-object records (WP09)
  - T035 `Tactic` child entity (WP09)
  - T036 `Pack` aggregate root (WP09)
  - T037 Leaf ports (WP09)
  - T038 Aggregating repository + inspection query (WP09)
  - T039 Constructor guards via validation (WP09)
- **Prompt**: [tasks/WP09-domain-model.md](./tasks/WP09-domain-model.md)
- **Implementation sketch**: reshape the moved roundtrip cluster into identity entity + VO records; add the aggregate, identifiers, ports; fold `PackSnapshot` into `Pack`.
- **Dependencies**: WP03, WP08.
- **Parallel notes**: T033/T034 `[P]`.
- **Risks**: identity-equals vs the old value-equal record — fidelity moves to the DSO (WP10).

### WP10 — Persistence: strategy, DSO, filesystem storage + fidelity controls (IC-05a)

- **Goal**: `SerializationStrategy` + `YamlSerializationStrategy` + DSO records + filesystem storage, with the round-trip/write-safety/symlink controls.
- **Priority**: P1 (the load-bearing invariant). **Independent test**: `*RoundTrip*`/`*WriteSafety*` — DSO round-trip equal + mutation control; failure-injected write untouched; symlink refused with in-root positive control.
- **Subtasks**:
  - T040 `SerializationStrategy` interface (WP10)
  - T041 `YamlSerializationStrategy` (WP10)
  - T042 DSO records (WP10)
  - T043 Filesystem storage mechanics (WP10)
  - T044 Round-trip literal oracle + mutation control (WP10)
  - T045 Write-safety failure-injection control (WP10)
  - T046 Symlink-escape + in-root positive control (WP10)
- **Prompt**: [tasks/WP10-persistence-storage.md](./tasks/WP10-persistence-storage.md)
- **Implementation sketch**: strategy over the value-equal DSO; `PackIdentifier`→path; temp→`ATOMIC_MOVE`; normalize+realpath containment; literal-oracle fixtures replacing the old battery.
- **Dependencies**: WP09, WP05.
- **Parallel notes**: T040/T042 `[P]`.
- **Risks**: vacuity (literal oracle guards it); the failure-injection seam must be real.

### WP11 — Persistence: repository @Service, mapping, port retirement (IC-05b)

- **Goal**: `DomainRepositoryService` realizing the ports with hand-written DSO↔entity mapping, retiring the old port.
- **Priority**: P1. **Independent test**: `:packs:test` — absent id returns empty (paired with present control); aggregating repository assembles a `Pack`.
- **Subtasks**:
  - T047 `DomainRepositoryService` @Service (WP11)
  - T048 Hand-written DSO↔entity mapping (WP11)
  - T049 Retire `PackElementRepository` + `PackLocator` (WP11)
  - T050 Replace the old test battery with the new ports' tests (WP11)
- **Prompt**: [tasks/WP11-persistence-repository.md](./tasks/WP11-persistence-repository.md)
- **Implementation sketch**: one `@Service` ctor-injecting strategy + store; hand-map DSO↔entity; delete the superseded port; replace its tests.
- **Dependencies**: WP10.
- **Risks**: multi-pack via explicit `PackIdentifier`; new-element creation out of scope (C-008).

### WP12 — Wiring through the production path (IC-06)

- **Goal**: Register the edge `@Service` in `bootstrap` and prove the round-trip through the WIRED bean.
- **Priority**: P1. **Independent test**: `*Wiring*` — `load`/`save` resolve through the wired bean; port signatures domain-only.
- **Subtasks**:
  - T051 Register the edge `@Service` (WP12)
  - T052 Wiring round-trip test through the WIRED bean (WP12)
  - T053 Confirm port signatures stay domain-only through the live path (WP12)
- **Prompt**: [tasks/WP12-wiring.md](./tasks/WP12-wiring.md)
- **Implementation sketch**: component-scan / `@Bean` in `bootstrap`; a wiring test resolving the bean from the context; no Spring Data.
- **Dependencies**: WP11, WP07.
- **Risks**: dead code — the test must resolve the bean from the context, not `new` it.

### WP13 — DDD docs, glossary, changelog, ADR amendment (IC-07)

- **Goal**: The pattern-setting docs embedding the C4 + class diagrams, the glossary, the changelog, and the ADR-0006 amendment.
- **Priority**: P2. **Independent test**: the named docs exist and document the specific rules; ADR-0006 reads Accepted/realized.
- **Subtasks**:
  - T054 `code-style-and-conventions.md` (WP13)
  - T055 How-to-DDD guide (WP13)
  - T056 `architectural-patterns-applied.md` (WP13)
  - T057 Copy + embed the canonical diagrams (WP13)
  - T058 Glossary entries (WP13)
  - T059 Changelog entry (WP13)
  - T060 Amend ADR-0006 → Accepted/realized (WP13)
- **Prompt**: [tasks/WP13-docs.md](./tasks/WP13-docs.md)
- **Implementation sketch**: `planning_artifact` WP, all under `docs/**`; copy the canonical `.puml`, do not redraw.
- **Dependencies**: WP12.
- **Parallel notes**: T054/T055/T058/T059 `[P]`.
- **Risks**: keep strictly to `docs/**` (no `service/**`).

## Cross-WP dependency graph

```
WP01 ─┬─ WP02 ─ WP03 ─ WP04 ─ WP05 ─ WP06 ─ WP07 ───────────────┐
      │     └─ WP08 ───────────────┐                             │
      │                            └─ WP09 ─ WP10 ─ WP11 ─ WP12 ──┴─ WP13
      └───────────────────────────────────────── (WP10 also needs WP05)
```

## Ownership & test discipline

- Code WPs own files under `service/**` (plus the root build files `settings.gradle.kts` / `build-logic/**` for WP07); WP13 owns files under `docs/**` only; no WP owns `kitty-specs/**`.
- `charter-domain` is drained by WP02 (kernel classes), WP03 (stable pack classes), WP09 (reshaped roundtrip cluster) and WP11 (retired ports) — each file has exactly one owner.
- `HexagonalArchitectureTest` is owned solely by WP07 (moved + fully rewritten); the validation-purity rule is a **separate** test file owned by WP08; the skeleton shell is a **separate** file owned by WP01 — no ArchUnit-test file is co-owned.
- Tests live with their concern WP; every absence-assertion (absent-id empty, no-public-ctor, zero third-party imports, path-containment refusal) is paired with a named positive control.
