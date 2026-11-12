---
work_package_id: WP08
title: Activation links (#409)
dependencies:
- WP01
requirement_refs:
- FR-013
- FR-014
planning_base_branch: slice-a2-536-cutover-reads
merge_target_branch: slice-a2-536-cutover-reads
branch_strategy: Planning artifacts for this mission were generated on kitty/mission-slice-a2-cutover-reads-01M78RJD, landing on slice-a2-536-cutover-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-a2-536-cutover-reads unless the human explicitly redirects the landing branch.
subtasks:
- T035
- T036
- T037
- T038
phase: ''
agent: claude
history:
- at: '2026-11-12T22:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/
create_intent:
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationMapperLinksTest.java
- contracts/charter/scenarios/activation-links.feature
- contracts/charter/changes/2026-11-12-activation-links.md
execution_mode: code_change
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationMapperLinksTest.java
- contracts/charter/schemas/Activation.yaml
- contracts/charter/examples/Activation*.yaml
- contracts/charter/scenarios/activation-links.feature
- contracts/charter/changes/2026-11-12-activation-links.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP08 - Activation links (#409)

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/slice-a2-cutover-reads-01M78RJD/`:

- `spec.md`
- `plan.md`, its concern for this WP
- `research.md`
- `research/squad-post-plan.md`

## Objective

Make an activation discoverable by links (#409; operator ruling `DM-01M78RKM6Q23PA9QAC80QJKQFF`; research.md R5; FR-013 and FR-014). The root `activations` link already exists.

An activation answer (`getActivation`, and the activation `createActivation` and the writes answer) links the following relations:

| Relation | Target | Method | Templated |
|---|---|---|---|
| `self` | `/api/v1/activations/{id}` | GET | no |
| `artifacts` | `…/artifacts{?kind,state}` | GET | yes (as today) |
| `offering` | `/api/v1/offering` | GET | no |
| `edit` | self | PUT | no |
| `delete` | self | DELETE | no |
| `select` | `…/selections/{kind}/{artifactId}` | PUT | yes |
| `exclude` | `…/exclusions/{kind}/{artifactId}` | PUT | yes |
| `apply-preset` | `…/presets/{presetId}` | PUT | yes |
| `packs` | `…/packs` | GET | no |
| `graph` | `…/graph` | GET | no |
| `skipped` | `…/skipped` | GET | no |
| `effective-context` | `…/mission-types/{missionType}/actions/{action}/context{?depth}` | GET | yes |

`unselect` and `include` are not on the activation. Slice C adds `prompt`; do not add it here.

## Context

- `ActivationMapper.java:103-105` puts `self` and `artifacts` (its own `Link` from `ai.speckitty.charter.api.model`).
- `LinkChecks.java` and `ContractConformance.builtOperationTemplate` check that every link resolves to a built operation with the declared method.
- The relation table is in `docs/development/hypermedia-hateoas.md:146-153`, and `RelationTableParityTest` keeps the lint enum equal to it.
- Check the exact methods of the selection, exclusion and preset paths in `contracts/charter/paths/activations_activationId_*.yaml`, and the PUT/DELETE of `activations_activationId.yaml`.

## Subtasks

### T035: Mapper

Emit the table's links, with the `method` field when it is not GET, and `templated: true` where the path has a template.

### T036: Contract

- Update the `Activation.yaml` `_links` description to list the relations.
- Update the examples.

### T037: Relation table

Recorded out-of-map rows in the WP01-owned table and lint enum:

- the `packs` row covers the root (the registered packs) and an activation (its packs);
- the `select` and `exclude` rows add "an activation (templated by kind and id)";
- the `edit` row drops "Not used in this minor" and is "In the contract on an activation";
- the `delete`, `skipped`, `effective-context`, `apply-preset` and `offering` rows say "In the contract on an activation";
- `graph` is in the contract on an activation.

### T038: Scenario

`contracts/charter/scenarios/activation-links.feature`: starting from `GET /api/v1/`, follow `activations` (POST) to create an activation, then expand its `select` link with a kind and id and PUT it. The artifact is selected. Every href the scenario uses comes from a link.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :contracts:check :activations:test
./gradlew --max-workers=1 :deployable:test   # contract scenarios, switch gate, link checks, ArchUnit
```
Run Gradle in the foreground, one run at a time; never `--stop`.

## Definition of Done

- The scenario passes without building a URL by hand.
- `LinkChecks` is green: no link to a reserved operation.
- The table and the lint enum are in parity.

## Risks and reviewer guidance

- A link with the wrong `method` passes the key check but fails `builtOperationTemplate`. Check each one.

## Branch Strategy

- **Planning base and merge target:** `slice-a2-536-cutover-reads`.
- **Mission branch:** `kitty/mission-slice-a2-cutover-reads-01M78RJD`.
- **Worktrees:** allocated per computed lane (`lanes.json`).
- **Implement with:** `spec-kitty agent action implement WP08 --agent claude --mission slice-a2-cutover-reads-01M78RJD`.
- **Commits:** sign them if signing works. Add no model or `Co-Authored-By` trailer. Reference the issue, for example `(#409)`.

## Coordination

Slice C (#467) is in flight and changes:

- the `prompt` path and link relation;
- four schemas;
- `mission_type_not_in_force` in `CharterRefusalCode`;
- `ClosureQueries.closureFingerprint` ordering.

Do not use or change those names. Do not pin fingerprint values in tests.
