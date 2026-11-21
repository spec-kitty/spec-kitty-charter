---
work_package_id: WP10
title: The anti_pattern file kind (R-p, R-q; refs #154)
dependencies:
- WP02
requirement_refs:
- FR-013
- FR-010
- NFR-001
- C-002
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage unless the human explicitly redirects the landing branch.
subtasks:
- T037
- T038
- T039
- T040
- T041
phase: Phase 1 - Foundation
history:
- at: '2026-11-21T19:30:00Z'
  actor: planner-priti
  action: Prompt generated for rulings R-p and R-q (a new WP builds the anti_pattern file kind before WP03)
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- contracts/packs/schemas/anti-pattern.schema.json
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AntiPattern.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AntiPatternIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AntiPatternSeverity.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AntiPatternBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AntiPatternDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AntiPatternYaml.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/AntiPatternTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/AntiPatternBindingTest.java
- service/packs/src/test/resources/digest-baseline/fixture/anti_patterns/nu.anti-pattern.yaml
- service/packs/src/testFixtures/resources/example-packs/built-in/anti_patterns/indirection-for-repetition.anti-pattern.yaml
- contracts/charter/scenarios/registration/anti-pattern-files.feature
- contracts/charter/changes/2026-11-21-anti-pattern-file-kind.md
execution_mode: code_change
owned_files:
- contracts/packs/schemas/anti-pattern.schema.json
- contracts/packs/file-kinds.yaml
- contracts/packs/conformance/classifier-cases.yaml
- contracts/packs/CHANGELOG.md
- contracts/packs/README.md
- contracts/charter/scenarios/registration/anti-pattern-files.feature
- contracts/charter/changes/2026-11-21-anti-pattern-file-kind.md
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/ArtifactKind.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AntiPattern.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AntiPatternIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AntiPatternSeverity.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Pack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AntiPatternBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AntiPatternDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AntiPatternYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBindings.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlFormats.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/AntiPatternTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/AntiPatternBindingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/KindBindingsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileKindTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetExactnessTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDigestFixtureTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GeneratedPackTree.java
- service/packs/src/test/java/ai/speckitty/charter/packs/examples/ExamplePackCoverageTest.java
- service/packs/src/test/resources/digest-baseline/fixture/anti_patterns/**
- service/packs/src/test/resources/facts/example-packs.json
- service/packs/src/testFixtures/resources/example-packs/built-in/anti_patterns/**
- docs/architecture/logical-model.md
- docs/architecture/descriptors.md
- site/.contextive/definitions.yaml
- docs/glossary/charter/README.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP10 - The anti_pattern file kind (R-p, R-q; refs #154)

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md`, ADR-0052 (decisions 8 to 16 and both amendments), ADR-0053 decisions 30
to 32, ADR-0064 decision 12, ADR-0080 decision 5 (the precedent), the mission `spec.md` (FR-013) and `plan.md`
(**D12, read it whole**), `data-model.md` ("Anti-pattern file") and the rulings R-p and R-q in `decisions/`
(`DM-01M7ZPVTTGHYCSC2Y39BZV0FXT`, `DM-01M7ZQGHVNQXAQ0S3DMTK7EZCB`).

## Objective

Give the existing `anti_pattern` kind its file form, so a pack can **hold** an anti-pattern (rulings R-p and R-q;
FR-013; plan D12). WP03's rule (R-l) counts a `must_have` only when a closure pack holds it as a file, and
`built-in/presets/default.yaml` requires `anti_pattern:indirection-for-repetition`, which today is only a node of
`built-in/styleguide.graph.yaml`. When this WP is done, that preset item is held by a file, and WP03 runs as planned.

The shape is ruled (ADR-0052 d8, ADR-0053 d30): `anti_patterns/<id>.anti-pattern.yaml`, closed, with
`schema_version`, `id` (the file stem), `title`, `description`, `severity` (`smell`, `severe` or `breaking`) and
`bad_example` required, and `good_example` optional. There is no default severity (ADR-0053 d32).

## Branch Strategy

- Planning branch and merge target: `feat/pack-doctor-coverage` (PR to `main`). Mission topic branch:
  `kitty/mission-pack-doctor-coverage-01M7YMQK`.
- The workspace comes from `lanes.json`. Implement with
  `spec-kitty agent action implement WP10 --agent claude --mission pack-doctor-coverage-01M7YMQK`.
- Commits: no model names and no Co-Authored-By trailer (repository rule). Never push to `main`.

## Context

- **The precedent is the routing catalog** (ADR-0080): an underscore kind token with a kebab file token, placed at
  `<plural>/<id>.<token>.yaml`. Read `RoutingCatalogBinding.java:35-70` (`fileToken`, `schemaName`, `fileName`,
  `idIsFileStem`, `placedAtItsFileName`), its rule at `file-kinds.yaml:172-175`, its classifier case, its packs
  `CHANGELOG` line (`### Added`, "The routing catalog (ADR-0080 decision 5)"), its `KindBindingsTest` case
  (`theRoutingCatalogIsAppendedLast`) and its digest-fixture file
  (`src/test/resources/digest-baseline/fixture/routing_catalogs/mu.routing-catalog.yaml`). For the flat YAML form,
  follow `GuardYaml`/`GuardDso` (schema key order through `YamlDumpEmitter`, a DSO whose record equality is the
  round-trip assertion).
- **What already knows the kind, and does not change**: `NodeKind.ANTI_PATTERN` (an artifact kind, activatable,
  declarable in graphs, an edge target; `NodeKind.java:48`), `ArtifactKind.ANTI_PATTERN` (token `anti_pattern`, plural
  `anti_patterns`), the kernel `kinds.AntiPattern` type and its `GraphAuthored` membership, the `rejects` endpoint
  table, `file-kinds.yaml` `node_kinds`, `artifact_kinds` and `fragment_kinds`, the manifest constituent enum
  (`pack-manifest.schema.json`), the preset's `activated_anti_patterns`, `policy.schema.json`'s URN pattern, the
  kind taxonomy and every charter enum of kinds. **Only `ArtifactKind.ANTI_PATTERN`'s javadoc fact changes**
  (`ArtifactKind.java:36`: "never a standalone file").
- **Node and file at once is the norm** (plan D12): `paradigm:atomic-design` is a node of `paradigm.graph.yaml` and a
  file; `styleguide:boring-code-review` is a node of `styleguide.graph.yaml:11-15` and a file. The anti-pattern's node
  stays in `built-in/styleguide.graph.yaml:16-18` as the target of the `rejects` edge (`:19-24`). Do not move it and
  do not add a shard.
- **Not this WP** (plan D12, analysis Q1): refusing a graph-only `anti_pattern` node (ADR-0053 d31), dropping the
  inline `anti_patterns` of styleguides and procedures (ADR-0052 d16), and an HTTP read of the kind. Leave
  `CompositionFactsBuilder.declaredWithoutFile` (`CompositionFactsBuilder.java:445-462`) alone: it is WP03's file,
  and WP03's T011 replaces it with `held`/`declared`. `DomainRepositoryServiceTest.definedArtifactsNameEveryKindThePackHolds`
  (a graph-only node is still declared) stays green unchanged.

## Subtasks

### T037 — The packs contract: schema, file rule and classifier case
- Red first: a `PackFileKindTest` entry mapping `core/anti_patterns/x.anti-pattern.yaml` to `anti-pattern`, and a
  classifier case in `contracts/packs/conformance/classifier-cases.yaml`, both red before the rule exists.
- `contracts/packs/schemas/anti-pattern.schema.json`, Draft 2020-12, `additionalProperties: false`, exactly the
  `data-model.md` table: `schema_version` `^1\.0$`; `id` `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, `maxLength` 64;
  `title`, `description`, `bad_example` strings; `severity` the enum `smell`, `severe`, `breaking`; `good_example`
  an optional string. Required: all but `good_example`. The `description` names ADR-0052 d8 and ADR-0053 d30/d32, and
  says the stem rule is the store's (as `routing-catalog.schema.json` says of its content rules).
- `file-kinds.yaml` `files`: `- match: "**/*.anti-pattern.yaml"` with `schema: anti-pattern`, and a comment naming
  ADR-0052 d8 and the placement (`anti_patterns/<id>.anti-pattern.yaml`; a file anywhere else is refused by
  placement, as for guards). Read the header's matching and overlap rules first.
- `contracts/packs/CHANGELOG.md`: an `### Added` line in the top section. `contracts/packs/README.md`: line 5 ("a
  template and an anti-pattern have none") and the schema count at line 26 (27 → 28).
- Run `./gradlew :contracts:check` and the packs-module vocabulary tests (`VocabularyEqualityTest`): no kind list
  changes, so they stay green; if one goes red, it names a list you missed.

### T038 — The domain entity
- Red first: `AntiPatternTest`: a valid anti-pattern builds; a blank `title`, `description` or `bad_example`, or a
  schema version other than `1.0`, breaches a rule; `good_example` is optional; two with the same identity are equal.
- `packs.domain.AntiPattern implements CharterArtifact` (kind `ANTI_PATTERN`, URN `anti_pattern:<id>`), modelled on
  `Paradigm` (rules from one static `ModularRuleset`, identity equality, no relationship fields: its edges are the
  graph's, ADR-0064). `AntiPatternIdentifier` as the other identifiers. `AntiPatternSeverity`, a closed enum with
  tokens `smell`, `severe`, `breaking` and a `fromToken` parse (named so, not `Severity`: the kernel's
  `validation.Severity` is another concept).
- `Pack`: an `antiPatterns()` accessor and a builder method, as `paradigms()` (`Pack.java:302-307`) and
  `Builder.paradigm`.
- `ArtifactKind.ANTI_PATTERN`'s javadoc: an anti-pattern is authored as its own file,
  `anti_patterns/<id>.anti-pattern.yaml` (ADR-0052 d8); a graph node of it declares it and is a `rejects` target.

### T039 — The storage binding and its canonical form
- Red first: `AntiPatternBindingTest` (as `RoutingCatalogBindingTest`): a file reads to the DSO and the entity and
  writes back byte-equal in schema key order (`schema_version`, `id`, `title`, `description`, `severity`,
  `bad_example`, `good_example`); an absent `good_example` stays absent; an unknown severity token is refused;
  `idIsFileStem` and `placedAtItsFileName` refuse a stem mismatch and a misplaced file through `FileStems`.
- `AntiPatternDso` (a flat record mirror), `AntiPatternYaml` (`YamlFormat`), `AntiPatternBinding`: `fileToken()` and
  `schemaName()` `anti-pattern`; `fileName(id)` `anti_patterns/<id>.anti-pattern.yaml`; `idIsFileStem()` and
  `placedAtItsFileName()` true; no companions.
- Append the binding **last** to `KindBindings` and the format last to `YamlFormats` (append, never insert:
  `PackDigestBaselineTest`). `KindBindingsTest`: the routing catalog is no longer last; a case pins the anti-pattern
  appended last, spelled `anti-pattern`, placed at `anti_patterns/<id>.anti-pattern.yaml`.
- The digest fixture: add `digest-baseline/fixture/anti_patterns/nu.anti-pattern.yaml`, recapture both constants of
  `PackDigestFixtureTest` in one commit, and add a "Recaptures" line to its javadoc (as the routing catalog did).
- `GeneratedPackTree` and `PackFileSetExactnessTest`: the kind joins as the routing catalog did, so the generated
  trees and the exactness check cover it.

### T040 — The built-in fixture
- Red first: in `ExamplePackCoverageTest`, assert that `built-in` holds
  `anti_patterns/indirection-for-repetition.anti-pattern.yaml` and that every `activated_anti_patterns` item of an
  example preset names an anti-pattern file its pack holds. Red before the file exists.
- Write `built-in/anti_patterns/indirection-for-repetition.anti-pattern.yaml` in the canonical form the writer emits:
  `title: Indirection to remove a few repeated lines` (the node's label); `description`, `bad_example` and
  `good_example` from the inline entry of `styleguides/boring-code-review.styleguide.yaml` (the canonical writer's
  scalars, not the inline block's folded `>` text); `severity: smell`. The node and its `rejects` edge in
  `styleguide.graph.yaml` stay unchanged, and so does the inline entry (Q1).
- The built-in manifest (`built-in/pack-manifest.yaml`) must list the new constituent, or registration answers
  `manifest_membership`. That file is WP04's: stamp it with `ManifestMembership.stamp` (WP04's
  `ManifestStampHelper` once it exists), as a **justified out-of-map edit** listed in your review notes. The lane
  that merges second re-runs the stamp (analysis U1).
- `src/test/resources/facts/example-packs.json`: built-in's `defined` now lists `anti_pattern:indirection-for-repetition`
  among the held files in `ArtifactKind` order (`Pack.artifacts()` is an `EnumMap`, `Pack.java:84`), after
  `skill:review-checklist` and before `asset:carrier-lint`, instead of last. Re-record it and check that only that
  entry moves. (WP03 re-records the file again when `defined` becomes `held`/`declared`.)
- `./gradlew :pack-validation:wholePackRoundTrip`: green, with the new file round-tripping in its canonical form.

### T041 — Registration, validation, the charter contract and the docs
- New `contracts/charter/scenarios/registration/anti-pattern-files.feature`, with the existing whole-pack
  registration steps only:
  - a pack holding a valid anti-pattern file registers, and the file is not a `file_not_held` diagnostic;
  - a file without `severity`, one without `bad_example` and one with `severity: minor` are refused with the code and
    pointer the schema path answers today (read `whole-pack-registration.feature` for the code; do not add one);
  - a file outside `anti_patterns/`, and one whose `id` is not its stem, are refused by the placement and stem rule.

  If a step is missing, it is a justified out-of-map edit of the steps class; list it in the review notes.
- `pack-doctor validate` gives the same answer for the same planted files: a `pack-validation` or `pack-doctor` test
  over a copied example pack, asserting the same codes as the scenarios (ADR-0075 decision 9).
- Change fragment `contracts/charter/changes/2026-11-21-anti-pattern-file-kind.md` under `### Breaking`: whole-pack
  registration now holds and judges `anti_patterns/<id>.anti-pattern.yaml`, which it warned `file_not_held` before,
  so an invalid one is refused; core's seam needs no regeneration (no schema of the charter contract changes).
- Docs (live text only, C-004):
  - `docs/architecture/logical-model.md:324-325` (the anti-pattern is "never a standalone file");
  - the note at `docs/architecture/descriptors.md:313-315` (the anti-pattern file is built; the inline drop is not);
  - the glossary definition at `site/.contextive/definitions.yaml:35` ("until it is built an anti-pattern is only a
    graph node or an inline entry"), and its generated copy `docs/glossary/charter/README.md:70`. Regenerate the copy
    with the glossary tool if it is available; otherwise edit both to the same text.

## Test commands

```bash
./gradlew :contracts:check :kernel:test :packs:test :pack-validation:test :pack-doctor:test
./gradlew :deployable:test     # the contract scenarios
./gradlew :pack-validation:wholePackRoundTrip
./gradlew spotlessCheck
```

## Definition of Done
- [ ] `anti-pattern.schema.json`, its `files` rule and its classifier case exist; the packs `CHANGELOG` and `README`
      say so.
- [ ] The kind is bound, appended last; a file round-trips byte-equal; the stem and placement rules apply.
- [ ] `built-in` holds `indirection-for-repetition` as a file; its node and `rejects` edge are unchanged; the
      example packs and the whole-pack gate are green, with no fixture exempted.
- [ ] Registration and `pack-doctor validate` hold a valid file and refuse an invalid one with the same code.
- [ ] A `### Breaking` fragment; the live docs and the glossary no longer say the kind has no file.
- [ ] Every behavior change had a test that was red first (NFR-001).

## Stop conditions
- Holding the file beside its node makes any rule refuse a pack (a contest, a duplicate holding, a stem rule):
  stop and report. D12 rests on node and file coexisting as they do for paradigms and styleguides.
- The operator answers analysis Q1 with option (b) or (c) before you start: stop; the WP is re-planned.
- A test can only pass by excluding a field from a round-trip or parity check, or by exempting a fixture: stop and
  report (charter).

## Risks and reviewer guidance
- A reviewer checks that no enum of kinds changed (the kind was already in every list), that the binding is appended
  last, and that `CompositionFactsBuilder` and the inline entries are untouched.
- Out-of-map edits allowed and to be listed: `built-in/pack-manifest.yaml` (WP04's), and a missing scenario step.
- Open-packs' Python checker treats `anti_pattern` as graph-only (`validate_pack_schemas.py:84-88`, 447, 1181); the
  consequence is drafted in `research/open-packs-TBD-anti-pattern-files.md`, not filed.
