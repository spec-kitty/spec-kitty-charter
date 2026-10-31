# Implementation Plan: [MISSION]

**Branch**: `issue-125-activation-set` | **Date**: 2026-10-31 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/activation-set-01M691HW/spec.md`

**Note**: This template is filled in by the `/spec-kitty.plan` command. See `packs/built-in/missions/mission-steps/software-dev/plan/prompt.md` for the execution workflow.

The planner will not begin until all planning questions have been answered—capture those answers in this document before progressing to later phases.

## Summary

Declare the activation set of ADR-0020 in the charter contract, inside the unreleased
`0.1.0-SNAPSHOT` under its `### Breaking` subsection (operator ruling 2026-10-31): four closed document schemas plus the effective-read schemas, 13 reserved
operations under the tag `Activation` (each with `x-reserved` and a `501`), conditional headers,
six reserved refusal codes and two new refusal members. Three lint rules and an amended
`error-responses-are-problems` keep the reserved shape honest, one `@contract @reserved`
scenario per operation pins its `501`, and one service controller answers it. Work is red-first:
lint fixtures and scenarios before the contract and the controller.

## Technical Context

**Language/Version**: Java 25 (service, contract checks), OpenAPI 3.1.0 (contract), JSON Schema 2020-12 (`ActivationConfig.schema.json`)
**Primary Dependencies**: openapi-generator (spring, `interfaceOnly`, `useTags`), vacuum (lint, bundle), oasdiff, networknt json-schema-validator (already pinned in `service/packs`), Cucumber; no new dependency
**Storage**: N/A (no activation storage is built, C-001)
**Testing**: lint fixtures (`lintFixtures`), `ClosureChecker` (`checkClosedResponseObjects`), JUnit in `service/packs` (schema parity, content alignment, wire-token parity) and `service/deployable` (ArchUnit reserved-code pin, contract scenarios with `ContractConformance`)
**Target Platform**: the charter service (Spring Boot), and core's generated client
**Project Type**: single repository, Gradle multi-project (`contracts`, `service/*`, `build-logic`)
**Performance Goals**: N/A (every operation answers `501`)
**Constraints**: closed schemas; only keywords the bundler, ClosureChecker and ContractConformance accept (C-005); no new suppressions (NFR-002); no links to reserved operations (C-003)
**Scale/Scope**: 13 operations, ~12 new schema files, 6 refusal codes, 4 lint rule changes, ~19 scenarios

Supply chain (DIRECTIVE_051): no dependency is added, upgraded or removed; networknt is already
pinned with verification metadata and is only used by a new test in the module that already has it.

## Charter Check

| Charter rule | How this plan meets it |
|---|---|
| Never break the contract outside its versioning rules (ADR-0022) | Inside the unreleased `0.1.0-SNAPSHOT` (operator ruling), listed under its `### Breaking` subsection: the enum additions, the `Problem` members and the lint change |
| Designed against this repository's model (epic #186) | Vocabulary from `charter.schema.json` and `NodeKind`; core cited for content alignment only (C-004) |
| BDD / scenarios first | Scenarios and lint fixtures are written red before the contract and the controller |
| Test first, no fake green | Each scenario sends the real request and asserts `501` and the Problem shape through `ContractConformance` |
| Adversarial squad on contract changes at plan and before merge | Plan-point squad below; pre-PR squad with a contract/API lens at closeout |
| verifyAll on Java 25, style gates | NFR-001, NFR-002 |
| Docs checks | NFR-005 |

No violation; Complexity Tracking is empty.

## Project Structure

### Documentation (this mission)

```text
kitty-specs/activation-set-01M691HW/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/activation-operations.md   the 13 operations, statuses and headers
├── research/pre-spec-research.md
└── tasks/
```

### Source Code (repository root)

```text
contracts/
├── offering/schemas/ActivationConfig.schema.json        NEW canonical JSON Schema 2020-12
├── charter/openapi.yaml                                 tag Activation, 9 path entries, version
├── charter/paths/activations*.yaml                      NEW 9 path files, 13 operations
├── charter/schemas/Activation*.yaml, SkippedReport*.yaml, Effective*.yaml, PackPin.yaml,
│   ArtifactIdList.yaml, CascadeScope.yaml, SelectionRequest.yaml, ExclusionRequest.yaml,
│   PresetApplication.yaml, RequiringPack.yaml, Violation.yaml   NEW
├── charter/schemas/CharterRefusalCode.yaml              6 codes, raised: false
├── charter/parameters/ActivationId.yaml, ArtifactKindPath.yaml, ArtifactId.yaml, PresetId.yaml,
│   IfMatch.yaml, IfNoneMatch.yaml, ...                  NEW
├── charter/headers/ETag.yaml, Location.yaml             NEW (shared by the operations)
├── charter/responses/NotImplemented.yaml, PreconditionFailed.yaml, PreconditionRequired.yaml,
│   UnsupportedMediaType.yaml, Activation*Refused.yaml   NEW
├── charter/scenarios/activations.feature                NEW 13 @reserved 501 pins
├── charter/scenarios/errors.feature                     6 declaration pins
├── charter/scenarios/README.md                          @reserved admits the 501 form
├── charter/CHANGELOG.md                                 under ## 0.1.0-SNAPSHOT, ### Breaking
├── _shared/schemas/Problem.yaml, _shared/VENDORED.md    requiredBy, violations
└── lint/ruleset.yaml, lint/README.md, lint/fixtures/*   3 new rules, 1 amended, 1 no-body rule
service/
├── kernel/.../RefusalCode.java                          6 codes
├── packs/.../infra/api/ActivationController.java        NEW, answers 501
├── packs/.../infra/api/RefusalHandler.java              statuses and titles of the new codes
├── packs/src/test/.../ActivationConfigSchemaParityTest.java  NEW (FR-002, content alignment)
├── packs/build.gradle.kts                               test inputs: contracts/offering
└── deployable/src/test/.../bdd/*                         501 steps; ContractConformance minItems
docs/adr/0020-offering-and-activations.md, docs/architecture/offering-and-activation.md,
docs/api/service-contract.md, docs/development/http-status-codes.md
```

**Structure Decision**: the existing split contract (`contracts/charter/**` with file `$ref`s) and
the existing service modules; one new contract directory, `contracts/offering/schemas/`, per
ADR-0017/ADR-0020 D4.

## Design decisions (see research.md for rationale)

1. `selected` and `deactivated` are closed objects with one explicit property per activatable
   kind (11), not maps: closed, generator-friendly, and within the supported keywords.
2. `ActivationConfig.schema.json` is self-contained (`$defs`); the OpenAPI component is its
   mirror split into files. A packs test resolves both and asserts the validation keywords are
   equal, and validates a shared corpus with networknt against the JSON file.
3. `If-Match` and `If-None-Match` are declared `required: false`; the `428` for a missing
   `If-Match` is a documented answer, so the generated method does not turn it into a `400`.
4. The controller answers `501` before reading any parameter or body; Spring's own body
   binding still answers `415` for a wrong media type, as the contract declares.
5. `ContractConformance` learns `minItems` (the only new keyword needed), with a fixture test.
6. The 501 scenario steps reuse the existing request steps and a new
   `Then the operation is refused as not implemented` step that checks status, problem media
   type, no `code`, and the Charter headers.

## Plan-point adversarial squad

Run on this plan before tasks, three lenses (contract/API, architecture, test quality), findings
and dispositions in `research.md`.

## Implementation Concern Map

### IC-01 — Lint rules for the reserved shape

- **Purpose**: make a half-reserved or half-built operation a lint error, and admit `204`.
- **Relevant requirements**: FR-012, FR-013
- **Affected surfaces**: `contracts/lint/ruleset.yaml`, `contracts/lint/fixtures/`, `contracts/lint/README.md`
- **Sequencing/depends-on**: none (fixtures first, red)
- **Risks**: vacuum core functions only; "reserved declares 501" and "built declares no 501" need path expressions over operations, tested with fixtures that fail exactly one rule.

### IC-02 — Scenarios first

- **Purpose**: pin each operation's `501` and each code's declaration before anything is built.
- **Relevant requirements**: FR-014, FR-016
- **Affected surfaces**: `contracts/charter/scenarios/activations.feature`, `errors.feature`, `README.md`, `service/deployable/src/test/.../bdd/`
- **Sequencing/depends-on**: none (red until IC-03 to IC-05)
- **Risks**: the after-scenario raised-code check; the `@reserved` README rule.

### IC-03 — Activation schemas

- **Purpose**: the closed documents and the canonical JSON Schema, with a parity and an alignment test.
- **Relevant requirements**: FR-001, FR-002, FR-003, FR-004, FR-006, FR-011
- **Affected surfaces**: `contracts/offering/schemas/`, `contracts/charter/schemas/`, `contracts/_shared/`, `service/packs` tests and build inputs
- **Sequencing/depends-on**: none
- **Risks**: `resource-property-cited` on unbuilt properties; ClosureChecker on `_links`; NullOmissionTest on request models.

### IC-04 — Reserved operations

- **Purpose**: declare the 13 operations in full under the tag `Activation`.
- **Relevant requirements**: FR-005, FR-007, FR-008, FR-009, FR-010
- **Affected surfaces**: `contracts/charter/openapi.yaml`, `paths/`, `parameters/`, `headers/`, `responses/`, `CharterRefusalCode.yaml`
- **Sequencing/depends-on**: IC-01, IC-03
- **Risks**: `charter-headers-on-every-response` on 201/204/501; generated model name clashes (`GeneratedModelNamesTest`).

### IC-05 — The service answers 501

- **Purpose**: one controller implementing `ActivationApi`, and the new codes in the service.
- **Relevant requirements**: FR-010, FR-015
- **Affected surfaces**: `service/kernel` `RefusalCode`, `service/packs` `RefusalHandler`, `ActivationController`, `service/deployable` tests
- **Sequencing/depends-on**: IC-04
- **Risks**: exhaustive switches; HexagonalArchitectureTest; the reserved-code ArchUnit pin.

### IC-06 — Version, CHANGELOG and docs

- **Purpose**: record the minor, its breaks and the rulings.
- **Relevant requirements**: FR-017, FR-018, FR-019
- **Affected surfaces**: `contracts/charter/CHANGELOG.md`, `openapi.yaml` `info.version`, ADR-0020, architecture and API docs, `_shared/VENDORED.md`
- **Sequencing/depends-on**: IC-04, IC-05
- **Risks**: the release gate's heading rules; docs `related:` slugs; cspell.
