---
work_package_id: WP04
title: org-charter.yaml and active_charter are retired
dependencies:
- WP03
requirement_refs:
- FR-009
- FR-010
- FR-011
- SC-002
planning_base_branch: feat/466-slice-b-real-packs-register
merge_target_branch: feat/466-slice-b-real-packs-register
branch_strategy: Planning artifacts for this mission were generated on feat/466-slice-b-real-packs-register. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/466-slice-b-real-packs-register unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-slice-b-real-packs-register-01M7649C
base_commit: bc56b23a99abd4e7696946721b44a41436814ab6
created_at: '2026-11-11T23:46:14.812986+00:00'
subtasks:
- T017
- T018
- T019
- T020
- T021
phase: Phase 2 - Registration accepts real packs
history:
- at: '2026-11-11T21:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/packs/schemas/
create_intent:
- contracts/charter/changes/2026-11-11-org-charter-is-retired.md
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/OrgCharterRetirementTest.java
execution_mode: code_change
model: sonnet
owned_files:
- contracts/packs/schemas/org-charter.schema.json
- contracts/packs/schemas/pack-descriptor.schema.json
- docs/architecture/pack-schemas/org-charter.md
- docs/architecture/pack-schemas/README.md
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackPaths.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackDescriptor.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/validation/ChecksTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorReader.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlSerializationStrategy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackFindingCode.java
- service/packs/src/testFixtures/resources/example-packs/project/pack.yaml
- service/packs/src/testFixtures/resources/example-packs/built-in/glossary_packs/spec-kitty-core.glossary-pack.yaml
- service/packs/src/test/resources/roundtrip-fixtures/**
- service/packs/src/test/resources/load-fixtures/descriptor-all-declared-keys-are-not-unknown/**
- service/packs/src/test/resources/planted/c10-org-charter-schema-version-quoted/**
- service/packs/src/test/resources/planted/core-verdicts.json
- scripts/packs/core_verdicts.py
- contracts/charter/scenarios/directives.feature
- contracts/charter/scenarios/toolguides.feature
- contracts/charter/scenarios/styleguides.feature
- contracts/charter/scenarios/paradigms.feature
- contracts/charter/scenarios/relationships.feature
- contracts/charter/scenarios/journeys/**
- tests/scenarios/charter/PROMISES.md
- contracts/charter/changes/2026-11-11-org-charter-is-retired.md
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyVocabularyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackValidationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ValueObjectsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackSchemasTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspectorTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorReaderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/OrgCharterRetirementTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/examples/ExamplePackCoverageTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP04 - org-charter.yaml and active_charter are retired

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` (or read `java-jenny.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/a79c36318b7a506ca2ab26d74705c5e3e406004f/packs/built-in/agent_profiles/`) and adopt
it. Read `.kittify/charter/charter.md`.

## Objective

Retire `org-charter.yaml` outright (operator ruling on #431): its `files` rule, its schema and schema page, its
validate-mode finding, and the descriptor key `active_charter`. A pack that ships the file is refused by name (a
`retired` rule), never `unmodelled`; a `pack.yaml` that declares `active_charter` is refused as an unknown key of the
closed descriptor. Requirements: FR-009, FR-010, FR-011, FR-019 (descriptor part). This also settles #381 (the schema's
reserved `skill_namespace`): the PR closes it as not planned.

## Context

`research.md` §B is the complete file:line map; read it first, plus D7 and `research/squad-plan-point.md` (A11, R4).
**Coordination:** the parallel #496 session edits `PackPolicy.java` (`escapes()`, fragment graph build) and
`PackFindingCode.java` (adds `FRAGMENT_BODY_PATH_ESCAPE`) and may add a `PackPaths.escapesRoot` helper. Keep this WP's
edits to the lines listed here (DESCRIPTOR_KEYS, `codeFor`, javadoc, the `ORG_CHARTER_SCHEMA_ERROR` constant, the
`activeCharter` component) so a rebase is mechanical.

## Subtasks

### T017 - Retire the file

- `contracts/packs/file-kinds.yaml` (owned by WP02; out-of-map): delete the `files` rule `*/org-charter.yaml`
  (:127-128); append to `retired` (after WP02's rules): `match: "*/org-charter.yaml"`, reason
  `org enforcement is an activation preset with must-have items plus policy.yaml levels (#431)`.
- Delete `contracts/packs/schemas/org-charter.schema.json` (`PackSchemasTest:122-133` needs the schemas on disk to equal
  the `files` rules' schemas), `docs/architecture/pack-schemas/org-charter.md` and its link in
  `docs/architecture/pack-schemas/README.md:34` (the docs site fails on a page whose schema is gone).
- `PackFindingCode.java:25-26`: delete `ORG_CHARTER_SCHEMA_ERROR`. `PackPolicy.java:1048-1050`: delete the `codeFor`
  branch so a retired `org-charter.yaml` gets `artifact_schema_error` like every retired file; fix javadoc :935, :963.
- `RetiredFileFixture` (owned by WP02; out-of-map): append the rule, the sample `org-charter.yaml`, and the namesake
  `assets/org-charter.yaml` (an asset payload, not retired).

### T018 - Remove `active_charter`

- `pack-descriptor.schema.json`: remove the property (:125) and its mention in the description (:5).
- `PackPaths.java` (kernel): remove the `activeCharter` component and the constructor that takes it; the record holds
  `packDrg` and `mutabilityPolicy`. `PackDescriptor.java:19,115` javadoc.
- `PackDescriptorReader.java:69`, `PackDescriptorDso.java` (field, constructors, `of`, `toDescriptor`),
  `YamlSerializationStrategy.java:114`, `PackPolicy.java:98` (`DESCRIPTOR_KEYS`).
- Tests: kernel `ChecksTest.java:14-48` (use `packDrg` as the sample), `ValueObjectsTest.java:57-59`,
  `PackDescriptorReaderTest` (:70-74, :192-213, and the exact property list :250-263),
  `PackRoundTripTest:42,158-159,213,241`, `DomainRepositoryServiceTest:147`.

### T019 - Fixtures, examples, scenarios, planted

- Drop `active_charter` from `example-packs/project/pack.yaml:13`,
  `src/test/resources/roundtrip-fixtures/pack-a/pack.yaml:7`,
  `load-fixtures/descriptor-all-declared-keys-are-not-unknown/files/pack.yaml:7`.
- `example-packs/built-in/glossary_packs/spec-kitty-core.glossary-pack.yaml:998`: reword the "Charter Pack" definition
  (org enforcement through a governing preset and `policy.yaml`).
- Drop the line `active_charter: org-charter.yaml` from the Background `pack.yaml` of: `directives.feature:20`,
  `toolguides.feature:22`, `styleguides.feature:21`, `paradigms.feature:21`, `relationships.feature:18`, and journeys
  01:17, 02:17, 04:17, 05:17, 06:18, 09:17. In `journeys/journey-04-org-house-rules.feature` reword the description
  (:4-6) to name the governing preset; leave its `@WIP` scenarios `@WIP` but reword their steps to the preset model.
- `tests/scenarios/charter/PROMISES.md:34`: reword.
- Delete `planted/c10-org-charter-schema-version-quoted/`, its record in `planted/core-verdicts.json:476` and its entry
  in `scripts/packs/core_verdicts.py:57`, together.
- `ExamplePackCoverageTest:120`: javadoc example.

### T020 - Agreement test (FR-011)

New `T/OrgCharterRetirementTest.java`: one pack (a copy of a canonical example plus a root `org-charter.yaml`).
Assert, for that file: `PackFileKind.classify` is Retired with the reason; validate mode reports
`artifact_schema_error` "is retired: <reason>"; the store load reports a `retired` violation with the reason;
`WholePackStore.loadStaged` (registration) refuses `422 validation_failed` with violation code `retired` at
`org-charter.yaml` and no `unmodelled`; the digest includes it in the retired tail. A second case: `pack.yaml` with
`active_charter: x` is refused by the closed-descriptor unknown-key check naming `active_charter` (pin the code the
load and the registration give, 500 `pack_unreadable` vs 422; squad A11), and add the registration case to
`whole-pack-registration.feature` (owned by WP03; out-of-map) if a scenario step exists for an inline `pack.yaml`.

### T021 - Retarget org-charter tests

`PackPolicyVocabularyTest:57,97-106` (delete the org-charter case; keep the code-by-file-kind test meaningful),
`PackValidationTest:108-137` (becomes a retired finding), `PackSchemasTest:446-451` (delete),
`PackFileKindTest:35` (owned by WP02; out-of-map: remove from MAPPED, add to retired samples),
`FileSystemPackInspectorTest:264-311,503` (a retired file is refused by name, not listed),
`PackFileSetTest:43` (owned by WP02; out-of-map: it moves into the retired tail), `GeneratedPackTree:61,166,731`
(owned by WP02; out-of-map: `DECOY_ORG_CHARTER` gets the RETIRED verdict, not deleted), `PackPolicyTest:337-355`.

Fragment `contracts/charter/changes/2026-11-11-org-charter-is-retired.md`, one `### Breaking` block:
`org-charter.yaml` is retired (422 `retired` at registration; a stored pack holding it reads as `pack_unreadable` until
re-registered); `pack.yaml` loses `active_charter` (an unknown key); org enforcement is a governing activation preset
plus `policy.yaml`; core's seam: core stops reading `org-charter.yaml` and `active_charter` and applies the preset's
required floor (already served).

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :kernel:test :packs:test
./gradlew --max-workers=1 :packs:wholePackRoundTrip
./gradlew --max-workers=1 :contracts:check
./gradlew --max-workers=1 :deployable:test --tests '*CharterContractSuite*'
grep -rn "org-charter\|active_charter\|activeCharter\|ORG_CHARTER" service contracts scripts --include=*.java --include=*.yaml --include=*.json --include=*.py --include=*.feature
```

The grep may only hit the retired rule, its fixture, the agreement test and the fragment.

## Branch Strategy

Planning base and merge target: `feat/466-slice-b-real-packs-register`; worktrees per lane. Implement with
`spec-kitty agent action implement WP04 --agent claude --mission slice-b-real-packs-register-01M7649C`. Commits signed,
no model trailers, refs `(#431)`.

## Definition of Done

FR-009 to FR-011 proven; no reader of the old shapes; scenarios green.

## Risks and reviewer guidance

Any leftover `active_charter` in a fixture is a 500. Check the PackPolicy/PackFindingCode edits stay in the listed lines.
