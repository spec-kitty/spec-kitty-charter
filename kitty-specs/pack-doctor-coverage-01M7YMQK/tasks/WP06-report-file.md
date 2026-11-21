---
work_package_id: WP06
title: The report file (--report, --format yaml|xml)
dependencies:
- WP03
- WP04
requirement_refs:
- FR-008
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage (Mission topic branch kitty/mission-pack-doctor-coverage-01M7YMQK). During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage, which is PR'd to main.
subtasks:
- T025
- T026
- T027
phase: Phase 3 - The CLI surfaces
agent: claude
history:
- at: '2026-11-21T13:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/pack-doctor/src/main/java/ai/speckitty/charter/packdoctor/
create_intent:
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/ReportWriterTest.java
- contracts/charter/changes/2026-11-21-pack-doctor-report.md
execution_mode: code_change
owned_files:
- service/pack-doctor/src/main/java/ai/speckitty/charter/packdoctor/Problems.java
- service/pack-doctor/src/main/java/ai/speckitty/charter/packdoctor/ReportWriter.java
- service/pack-doctor/src/main/java/ai/speckitty/charter/packdoctor/report/**
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/ProblemsTest.java
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/ReportWriterTest.java
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/report/**
- contracts/charter/schemas/PackDoctorReport.yaml
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/PackDoctorReportContractTest.java
- contracts/charter/changes/2026-11-21-pack-doctor-report.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP06 - The report file (--report, --format yaml|xml)

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md`, ADR-0082, the mission `spec.md` and `plan.md` (the decisions this WP
names), and `research/squad-dispositions.md`.

## Objective

`pack-doctor validate --report <file> [--format yaml|xml]` writes the run's findings to a structured file (rulings
R-i, R-n; spec FR-008; plan D6). The report is **one model**: the same tree as the stdout problem JSON, with
`Problems.Problem` reused verbatim, plus three top-level keys (`tool`, `inputs`, `summary`). The tree is serialized
three ways: JSON on stdout, YAML (the default for `--report`) and XML. There is one normative schema.

## Branch Strategy

- Planning branch and merge target: `feat/pack-doctor-coverage` (PR to `main`). Mission topic branch:
  `kitty/mission-pack-doctor-coverage-01M7YMQK`.
- The workspace comes from `lanes.json`. Implement with
  `spec-kitty agent action implement WP06 --agent claude --mission pack-doctor-coverage-01M7YMQK`.
- Commits: no model names and no Co-Authored-By trailer (repository rule). Never push to `main`.

## Context
- `ReportWriter.report` writes `{checks, packs, skipped, problems[Problem]}` from `PackSetVerdict` through
  `Problems.of`, with Jackson 3 (`tools.jackson`) and a fixed key order.
- `contracts/charter/schemas/PackDoctorReport.yaml` (renamed by WP02) pins the shape, and
  `PackDoctorReportContractTest` checks a run against it.
- The data model is in `data-model.md`, "pack-doctor report".

## Subtasks

### T025 — The report tree
- Red first, in `ReportWriterTest`: a run over a planted set yields a tree with `tool.name == "pack-doctor"` and
  `tool.version`; `inputs` with the config root or the packs and the options (`round_trip`, and `full` once WP05 lands
  or is ruled); and `summary` counts (packs, violations, warnings, notices, exit code) that match the problems.
- The existing keys are unchanged. Do not invent a second problem shape. A problem in the file is a
  `Problems.Problem`, as on stdout.
- Stdout's shape: the new keys appear on stdout too (one model). Record this as `### Changed` in the fragment. If the
  contract test or a consumer pins stdout exactly, update the schema, which is the source of truth, and the test.

### T026 — Serializers, and the dependency check first
- Before writing code, check `gradle/verification-metadata.xml` and the pack-doctor module's dependencies for a
  verified Jackson 3 YAML and XML dataformat (`tools.jackson.dataformat:jackson-dataformat-yaml` / `-xml`). Record
  what you found in the review notes.
  - If both are verified and allowed for pack-doctor (`ModuleDependencyAllowlist`), use them.
  - Otherwise: YAML through the YAML engine the service already uses (SnakeYAML-engine via `:packs`, if pack-doctor
    may see it), and XML through `javax.xml.stream` (JDK).
  - **No new dependency and no new verification metadata (DIRECTIVE_051).** If neither path works without a new
    dependency, stop and report.
- XML mapping, our own shape (R-n): an element per key, an element per list item named after the singular of the
  list (`problems/problem`, `packs/pack`), and attributes only for scalar leaves you choose deliberately. Document the
  mapping in the schema's description. Keep it mechanical, so a parse back yields the same tree.
- An XUnit-compatible form is out of scope (R-n); ADR-0083 records it as a later option.

### T027 — Options, schema and contract test
- `Arguments` (owned by WP02; a justified edit): `--report <file>` and `--format yaml|xml` (default `yaml`).
  `--format` without `--report`, an unknown format, or a report path that cannot be written gives exit 2. Writing the
  report does not change the exit code of a valid run.
- `PackDoctorReport.yaml` gains `tool`, `inputs` and `summary` (required), with descriptions. It stays the only
  normative schema: no `.xsd`.
- `PackDoctorReportContractTest`: run over the example packs, and validate the JSON stdout, the YAML report and the
  XML report. The XML is parsed back into the tree and validated against the same schema. Assert the three trees are
  equal.
- Change fragment `2026-11-21-pack-doctor-report.md` under `### Changed` (additive keys, the new options).

## Test commands

```bash
./gradlew :pack-doctor:test :pack-doctor:packDoctorJar
./gradlew :deployable:test --tests '*PackDoctorReport*'
```

## Definition of Done
- [ ] One model, three serializations, and their trees are equal in the contract test.
- [ ] No new dependency; the choice is recorded.
- [ ] Usage errors exit 2.
- [ ] A fragment is written.
