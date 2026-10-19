# Implementation Plan: Charter headers on the default Problem response

**Branch**: `fix/problem-response-charter-headers` | **Date**: 2026-10-19 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/problem-response-charter-headers-01M5A4PB/spec.md`

## Summary

Declare `Charter-Contract-Version`, `Charter-Worktree` and `Charter-Source-Fingerprint` on the
vendored shared `default` response `contracts/_shared/responses/Problem.yaml`, by reference to the
charter's single header definitions (operator ruling, DM `01M5A4PP44NQ3KWG4APT0PEJCT`). Guard the
ADR-0011 rule with a new rule in the charter's own lint ruleset (`contracts/lint/ruleset.yaml`):
every response of every operation in the bundle declares the three headers. The rule is red on
today's contract and green after the edit. A contract scenario pins that the 400 `Problem` for an
out-of-range `depth` carries the headers and conforms. Update `contracts/_shared/VENDORED.md` for the
operator's general ruling of 2026-10-19 (this repository is canonical for the charter structure,
responses, contracts and pack schemas; vendored files changed here are canonical and core adapts),
listing this file's change from its origin, and record the change in the contract CHANGELOG. The
general ruling itself is recorded as ADR-0015 in a separate change, not in this mission.

## Technical Context

**Language/Version**: OpenAPI 3.1 YAML (contract), Spectral-format ruleset run by vacuum 0.30.6, Gherkin contract scenarios run by Cucumber-JVM on Java 25
**Primary Dependencies**: none added; vacuum and oasdiff stay at their pinned, SHA-256-verified versions
**Storage**: N/A
**Testing**: `./gradlew :contracts:lintContract` (red-first proof), `:contracts:lintFixtures` (the new rule's planted fixture fails it and no other rule; `clean.yaml` passes), the `@contract` scenarios in `service/charter-service`, and the full `./gradlew verifyAll`
**Target Platform**: the charter contract bundle (`build/contracts/contract/charter-openapi.yaml`) that core vendors
**Project Type**: single repository: contract plus Java service
**Performance Goals**: N/A
**Constraints**: no runtime change (C-001); one header definition (C-002); unreleased `1.0.0-SNAPSHOT` only (C-003); only `_shared/responses/Problem.yaml` changes in `_shared/` (C-004)
**Scale/Scope**: one shared response, one lint rule plus fixtures, one scenario, two doc files

## Charter Check

- **Breaking the OpenAPI contract** (must never go wrong): declaring headers on an existing response
  of an unreleased version is additive; `checkContractCompatibility` still runs. Pass.
- **Divergence from the oracle**: no runtime change. Pass.
- **Contract change gets an adversarial squad** at the plan point and before merge: scheduled
  post-tasks and pre-PR. Pass (scheduled).
- **A call made without review is logged**: the vendored-file edit is an operator ruling recorded
  as a Decision Moment and in `VENDORED.md`. Pass.
- **Gates before merge**: `verifyAll` on Java 25, docs checks. Planned in every WP.
- **BDD, test first**: the lint rule is written and shown red before the contract edit; the 400
  scenario is added in the same change. Pass.

## Project Structure

### Documentation (this mission)

```
kitty-specs/problem-response-charter-headers-01M5A4PB/
├── spec.md
├── plan.md
├── research.md
├── checklists/requirements.md
└── tasks/
```

### Source Code (repository root)

```
contracts/
├── _shared/
│   ├── VENDORED.md                         # canonical-here rule and this file's change from its origin (FR-004)
│   └── responses/Problem.yaml              # declares the three headers (FR-001)
├── charter/
│   ├── headers/*.yaml                      # unchanged; the single definitions (C-002)
│   ├── scenarios/service.feature           # the 400 Problem scenario (FR-003)
│   └── CHANGELOG.md                        # 1.0.0-SNAPSHOT entry (FR-005)
└── lint/
    ├── ruleset.yaml                        # rule charter-headers-on-every-response (FR-002)
    ├── README.md                           # the rule's convention
    └── fixtures/
        ├── charter-headers-on-every-response.yaml   # planted: one response without the headers
        ├── clean.yaml                      # every response declares the headers
        └── <rule>.yaml                     # every other fixture gains the headers so it still fails only its own rule
```

**Structure Decision**: the check lives in the contract's own lint ruleset, where the contract's
other conventions live, and is proven by the existing `lintFixtures` mechanism.

## Implementation Concern Map

### IC-01 — The headers rule, red first

- **Purpose**: a lint rule that fails when any response of any operation lacks one of the three headers, shown failing on today's contract.
- **Relevant requirements**: FR-002, SC-002
- **Affected surfaces**: `contracts/lint/ruleset.yaml`, `contracts/lint/fixtures/*.yaml`, `contracts/lint/README.md`
- **Sequencing/depends-on**: none
- **Risks**: every existing fixture must gain the headers on every response, or it fails two rules; the rule's `given` must reach the `default` response in the bundle (inlined `$ref`s).

### IC-02 — The shared Problem response declares the headers

- **Purpose**: make the contract state the headers on the `default` `Problem`.
- **Relevant requirements**: FR-001, FR-004, SC-001
- **Affected surfaces**: `contracts/_shared/responses/Problem.yaml`, `contracts/_shared/VENDORED.md`
- **Sequencing/depends-on**: IC-01 (the rule is red before this lands)
- **Risks**: the `$ref` from `_shared/` into `charter/headers/` must bundle; `Charter-Source-Fingerprint` stays not required.

### IC-03 — Scenario and records

- **Purpose**: pin the 400 behavior against the contract and record the change.
- **Relevant requirements**: FR-003, FR-005
- **Affected surfaces**: `contracts/charter/scenarios/service.feature`, its step definitions if a step is missing, `contracts/charter/CHANGELOG.md`
- **Sequencing/depends-on**: IC-02
- **Risks**: the scenario conformance hook checks content type and body only, so the header steps are the only runtime proof; the dated docs changelog must not name the `.feature` file (forbidden term).
