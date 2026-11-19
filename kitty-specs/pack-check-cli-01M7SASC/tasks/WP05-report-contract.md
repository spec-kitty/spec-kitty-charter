---
work_package_id: WP05
title: The report contract and its parity tests
dependencies:
- WP04
requirement_refs:
- FR-005
- NFR-003
- NFR-005
planning_base_branch: feat/478-pack-check
merge_target_branch: feat/478-pack-check
branch_strategy: Planning artifacts for this mission were generated on feat/478-pack-check. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/478-pack-check unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-check-cli-01M7SASC
base_commit: 715f79825610398c1cf5068d429ee9ff54897bc9
created_at: '2026-11-19T19:18:06.770409+00:00'
subtasks:
- T019
- T020
- T021
- T022
phase: Phase 3 - CLI
history:
- at: '2026-11-19T08:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/schemas/
create_intent:
- contracts/charter/schemas/PackCheckReport.yaml
- contracts/charter/changes/2026-11-19-pack-check-report.md
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/PackCheckReportContractTest.java
- service/pack-check/src/test/java/ai/speckitty/charter/packcheck/parity/PlantedParityTest.java
execution_mode: code_change
owned_files:
- contracts/charter/schemas/PackCheckReport.yaml
- contracts/charter/schemas/_index.yaml
- contracts/src/test/java/ai/speckitty/charter/contracts/lint/ReachableContractFilesTest.java
- contracts/charter/changes/2026-11-19-pack-check-report.md
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/PackCheckReportContractTest.java
- service/pack-check/src/test/java/ai/speckitty/charter/packcheck/parity/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP05 — The report contract and its parity tests

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Make the report a contract, and pin the CLI's answers to the API's:

- `PackCheckReport` is an additive schema in `contracts/charter/schemas` that no operation serves (D2);
- real CLI output validates against it;
- its problems equal what `RefusalHandler` renders;
- every planted subject gets registration's codes through the CLI (NFR-003).

## Context

- Spec: FR-005, NFR-003, D2. research.md R5 (the contract sweep), R6, squad S3 and S11. The draft schema is
  `kitty-specs/pack-check-cli-01M7SASC/contracts/PackCheckReport.yaml`.
- `contracts/src/test/java/ai/speckitty/charter/contracts/lint/ReachableContractFilesTest.java` refuses any schema
  that `openapi.yaml` does not reach (lines 22 to 44 and 82 to 95). `_index.yaml` does not count.
- `ContractConformance` (`service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/`, package-private):
  - `vetEverySchema` walks every file under `schemas`, and allows only `$ref` (whole file), `allOf`, `oneOf`,
    `properties`, `items`, `additionalProperties`, the constraints `type, required, enum, pattern, minLength,
    maxLength, minItems, maxItems, minimum, maximum`, the annotations, and `x-*`;
  - `schemaViolations(String schemaResource, JsonNode value)` (about lines 268 to 282) resolves `../../_shared/...`.
- Deployable already has `testImplementation(project(":pack-check"))` and copies `charter/schemas/**` and
  `_shared/schemas/**` onto its test classpath.
- Fragments: `contracts/charter/changes/README.md`, with one `###` block per file.

## Branch Strategy

Planning base `feat/478-pack-check`, merge target `feat/478-pack-check`; worktrees per lane from `lanes.json`. Run
`spec-kitty agent action implement WP05 --agent <name> --mission pack-check-cli-01M7SASC`.

## Subtasks

### T019 — The schema and its exemption

1. Copy the draft to `contracts/charter/schemas/PackCheckReport.yaml`. Drop the draft's leading comment, and check
   that every keyword is in `ContractConformance`'s allowed set. `packs[].level` is optional (squad S14): it is not in
   `required`.
2. Add it to `contracts/charter/schemas/_index.yaml` (`docs/guides/service-build.md` about line 519 says new files are
   listed there).
3. `ReachableContractFilesTest`:
   - add `STANDALONE = Set.of("schemas/PackCheckReport.yaml")` with a javadoc citing ADR-0075 decision 9: the
     pack-check CLI's stdout, served by no operation;
   - filter it in `unreached()`;
   - add a test that each `STANDALONE` file exists **and** is not reached from `openapi.yaml`, so the exemption cannot
     rot or hide a served file.
4. Run `./gradlew :contracts:check`.

### T020 — The fragment

`contracts/charter/changes/2026-11-19-pack-check-report.md`: one `### Added` block. `PackCheckReport`, the stdout of
`pack-check` (#478), served by no operation; its problems are `Problem`s with the registration's codes.

### T021 — The deployable contract test

New `service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/PackCheckReportContractTest.java`, in the
package of `ContractConformance`. It runs `PackCheckMain.run(args, out, err)` in-process.

- **Validates:** for each case below, the stdout JSON has no violation of
  `ContractConformance.schemaViolations("…/charter/schemas/PackCheckReport.yaml", node)`:
  - the five example packs as a set;
  - one planted pack fault;
  - `project` alone (a composition fault);
  - a planted link;
  - a 101-violation pack, built in `@TempDir` (e.g. 101 orphan fragments, or whatever plant multiplies cheapest).
- **The whole problem equals the API's**, for one fixture per code family (`validation_failed`, `pack_unreadable`,
  `pack_dependency_unknown`, and one other composition code): `title`, `status`, `code`, `detail`, `packs` and
  `violations` equal `RefusalHandler`'s rendering of the same `CharterRefusal` for `getPackRegistration`.
  - Build the refusal with `PackRefusalMapping.of` (pack faults), or as the composing read raises it (composition).
  - Call `RefusalHandler` the way its own tests do (look for `RefusalHandlerTest`).
  - Compare as `JsonNode` trees, minus `instance`, which only the CLI sets.
- **Every code the CLI can emit:** title and status equal `RefusalHandler`'s for `getPackRegistration`. Enumerate the
  composition codes from `CompositionRules`/`PackCompositionResolver.Failure`, so a new code without a status fails
  the test.

### T022 — NFR-003 parity

New `service/pack-check/src/test/java/ai/speckitty/charter/packcheck/parity/PlantedParityTest.java`. For every
planted subject in `:packs`' test fixtures, the codes of `PackCheckMain.run --pack <subject>` equal the registration
codes recorded for it in `service/packs/src/test/resources/golden/surfaces.json`, as a multiset by file.

- Find how `SurfaceGoldenTest` reads `surfaces.json`. Reuse its reader through the test fixtures if one is shared;
  otherwise parse with Jackson. Placement codes, which validate mode adds and registration does not, are the expected
  difference: assert them separately.
- Subjects that plant links skip where the file system cannot, as elsewhere.

## Definition of Done

- `./gradlew verifyAll` is green, including `:contracts:check`.
- The schema is in the contract with a self-checking exemption. The CLI output validates for every case. The problems
  equal the API's. Parity holds for 100 % of planted subjects.

## Risks and reviewer guidance

- **A test that compares the CLI with itself.** The expected side must come from `RefusalHandler` or `surfaces.json`,
  never from `Problems`.
- **The exemption list growing silently.** Its own test must fail for a reached or missing file.
