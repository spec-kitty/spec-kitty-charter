---
work_package_id: WP10
title: The anti_pattern file kind and the example anti-patterns as files (R-p, R-q, R-r; issue 154)
dependencies:
- WP02
requirement_refs:
- FR-013
- FR-010
- NFR-001
- C-002
- SC-004
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-doctor-coverage-01M7YMQK
base_commit: 53900583e8a3df1c90b4eb383f506e81e017f34f
created_at: '2026-11-21T22:15:00.333984+00:00'
subtasks:
- T037
- T038
- T039
- T040
- T041
- T042
phase: Phase 1 - Foundation
history:
- at: '2026-11-21T19:30:00Z'
  actor: planner-priti
  action: Prompt generated for rulings R-p and R-q (a new WP builds the anti_pattern file kind before WP03)
- at: '2026-11-21T22:30:00Z'
  actor: planner-priti
  action: Re-planned for ruling R-r (all of issue 154 in this PR) - every example inline anti-pattern becomes a file (T042); the refusal and the inline drop are WP11 and WP12
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
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/antipattern/AntiPatternFileCheckTest.java
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
- service/packs/src/testFixtures/resources/example-packs/built-in/styleguide.graph.yaml
- service/packs/src/testFixtures/resources/example-packs/built-in/procedure.graph.yaml
- service/packs/src/testFixtures/resources/example-packs/built-in/procedures/adversarial-squad-deployment.procedure.yaml
- service/packs/src/testFixtures/resources/example-packs/built-in/procedures/example-mapping-workshop.procedure.yaml
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/antipattern/AntiPatternFileCheckTest.java
- docs/architecture/logical-model.md
- docs/context/domain.md
- site/.contextive/definitions.yaml
- docs/glossary/charter/README.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP10 - The anti_pattern file kind and the example anti-patterns as files (R-p, R-q, R-r; #154)

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md`, ADR-0052 (decisions 8 to 16 and both amendments), ADR-0053 decisions 30
to 32, ADR-0064 decision 12, ADR-0080 decision 5 (the precedent), the mission `spec.md` (FR-013) and `plan.md`
(**D12, read it whole**, and D13/D14 for what WP11 and WP12 do after you), `data-model.md` ("Anti-pattern file")
and the rulings R-p, R-q and R-r in `decisions/` (`DM-01M7ZPVTTGHYCSC2Y39BZV0FXT`, `DM-01M7ZQGHVNQXAQ0S3DMTK7EZCB`,
`DM-01M801H54DXR0447M3F2ZQWFPZ`). **R-r governs**: this PR delivers all of #154, so the example packs end this WP
with no inline anti-pattern and with a file behind every `anti_pattern` node.

## Objective

Give the existing `anti_pattern` kind its file form, so a pack can **hold** an anti-pattern (rulings R-p and R-q;
FR-013; plan D12). WP03's rule (R-l) counts a `must_have` only when a closure pack holds it as a file, and
`built-in/presets/default.yaml` requires `anti_pattern:indirection-for-repetition`, which today is only a node of
`built-in/styleguide.graph.yaml`. When this WP is done, that preset item is held by a file, and WP03 runs as planned.
Under R-r it also moves **every** inline anti-pattern of the example packs (18 entries in 5 files) to a file of
`built-in`, with its node and a `rejects` edge from its owner, so that WP11 (the file-less node refusal) and WP12 (the
inline drop) start from example packs that already carry only the canonical shape.

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
- **Not this WP** (plan D13, D14): the rule refusing a file-less `anti_pattern` node (WP11), dropping `anti_patterns`
  from the procedure and styleguide schemas, domain, storage and charter contract (WP12), and an HTTP read of the kind
  (analysis C2). You remove the inline **data** from the example packs; the **field** stays in the schemas until WP12.
  Leave `CompositionFactsBuilder.declaredWithoutFile` (`CompositionFactsBuilder.java:445-462`) alone: it is WP03's
  file, and WP03's T011 replaces it with `held`/`declared`. Leave `DomainRepositoryServiceTest` and
  `CompositionFactsTest` alone: WP11 rewrites their graph-only nodes.

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
  `styleguide.graph.yaml` stay unchanged. The inline entry is removed in T042, with the other 17.
- The built-in manifest (`built-in/pack-manifest.yaml`) must list the new constituents (this one and T042's), or
  registration answers `manifest_membership`. That file is WP04's: stamp it with `ManifestMembership.stamp` (WP04's
  `ManifestStampHelper` once it exists), as a **justified out-of-map edit** listed in your review notes. The lane
  that merges second re-runs the stamp (analysis U1).
- `src/test/resources/facts/example-packs.json`: built-in's `defined` lists the anti-pattern URNs among the held
  files in `ArtifactKind` order (`Pack.artifacts()` is an `EnumMap`, `Pack.java:84`), after `skill:review-checklist`
  and before `asset:carrier-lint`. Re-record it once T042 is done and check that only built-in's anti-pattern entries
  change (18 URNs, `indirection-for-repetition` moved, 17 new). (WP03 re-records it again for `held`/`declared`.)
- `./gradlew :pack-validation:wholePackRoundTrip`: green, with the new file round-tripping in its canonical form.

### T042 — Every example inline anti-pattern becomes a file (R-r)
- Red first, in `ExamplePackCoverageTest`:
  - no styleguide or procedure file of any example pack carries an `anti_patterns` key (a text scan of the YAML
    keys; red: five files carry one);
  - every `anti_pattern` node of every example pack's graph documents names a file the same pack holds (red only if
    T040 is not done; it guards T042's new nodes);
  - the three severities `smell`, `severe` and `breaking` each appear at least once among the example anti-pattern
    files, so the fixtures cover the enum.
- For each of the 17 remaining entries, write `built-in/anti_patterns/<id>.anti-pattern.yaml` in the writer's
  canonical form, add a node `{urn: anti_pattern:<id>, kind: anti_pattern, label: <title>}` and a `rejects` edge from
  its owner (`source: <styleguide|procedure>:<owner>`, `target: anti_pattern:<id>`, `relation: rejects`, a `reason`
  that names the owner) to the owner's shard: `built-in/styleguide.graph.yaml` for a styleguide's, and
  `built-in/procedure.graph.yaml` (today `edges: []`) for a procedure's. Field mapping: a styleguide entry's `name` is
  the `title`, its `description`, `bad_example` and `good_example` carry over; a procedure entry's `title` is the
  `title`, its `content` is the `description`, and its `bad_example`/`good_example` carry over.
- The ids and severities (the charter curates its own example packs, ADR-0070; each severity is written, never
  defaulted, ADR-0053 d32):

  | Owner | Entry | id | severity |
  |---|---|---|---|
  | `styleguide:boring-code-review` | Indirection to remove a few repeated lines | `indirection-for-repetition` (T040) | `smell` |
  | `styleguide:boring-code-review` | Clever over direct | `clever-over-direct` | `smell` |
  | `styleguide:boring-code-review` | Duplicated business rules called acceptable repetition | `duplicated-business-rules-called-acceptable-repetition` | `severe` |
  | `styleguide:aggregate-design-rules` | Direct Object References | `direct-object-references` | `severe` |
  | `styleguide:aggregate-design-rules` | Transaction Spanning | `transaction-spanning` | `severe` |
  | `styleguide:java-conventions` | Swallowed Exception | `swallowed-exception` | `severe` |
  | `procedure:adversarial-squad-deployment` | Persona without profile | `persona-without-profile` | `smell` |
  | `procedure:adversarial-squad-deployment` | Unbounded squad | `unbounded-squad` | `smell` |
  | `procedure:adversarial-squad-deployment` | Casting by habit | `casting-by-habit` | `smell` |
  | `procedure:adversarial-squad-deployment` | Averaging divergent verdicts | `averaging-divergent-verdicts` | `severe` |
  | `procedure:adversarial-squad-deployment` | Trusting a biased delegate | `trusting-a-biased-delegate` | `severe` |
  | `procedure:adversarial-squad-deployment` | Model-tier mismatch | `model-tier-mismatch` | `smell` |
  | `procedure:adversarial-squad-deployment` | Hard-wiring the squad as a gate | `hard-wiring-the-squad-as-a-gate` | `severe` |
  | `procedure:adversarial-squad-deployment` | Silently dropped finding | `silently-dropped-finding` | `breaking` |
  | `procedure:example-mapping-workshop` | Example theater | `example-theater` | `smell` |
  | `procedure:example-mapping-workshop` | Implementation leakage | `implementation-leakage` | `smell` |
  | `procedure:example-mapping-workshop` | Happy-path only | `happy-path-only` | `severe` |
  | `procedure:example-mapping-workshop` | Unvalidated examples | `unvalidated-examples` | `severe` |

- Eleven procedure entries have no `bad_example` (all but "Persona without profile"); `bad_example` is required
  (ADR-0053 d30). Write one short, concrete bad example for each, derived from its `content`, in the style of the
  existing `bad_example` of "Persona without profile". Do not leave it blank and do not copy the description.
- Remove the `anti_patterns` block from the five owners: the two procedures (owned) and the three styleguides
  `aggregate-design-rules`, `boring-code-review`, `java-conventions` (inside WP03's `built-in/styleguides/**` glob,
  which WP03 does not edit: a **justified out-of-map edit**, listed in your review notes). The schemas still allow the
  field (WP12 drops it); you remove the data so that every example is canonical.
- Re-stamp `built-in/pack-manifest.yaml` (WP04's; out-of-map, as in T040), re-record `facts/example-packs.json`, and
  run `:pack-validation:wholePackRoundTrip`: the 18 files round-trip in canonical form, and the two shards keep theirs.

### T041 — Registration, validation, the charter contract and the docs
- New `contracts/charter/scenarios/registration/anti-pattern-files.feature`, with the existing whole-pack
  registration steps only:
  - a pack holding a valid anti-pattern file registers, and the file is not a `file_not_held` diagnostic;
  - a file without `severity`, one without `bad_example` and one with `severity: minor` are refused with the code and
    pointer the schema path answers today (read `whole-pack-registration.feature` for the code; do not add one);
  - a file outside `anti_patterns/`, and one whose `id` is not its stem, are refused by the placement and stem rule.

  If a step is missing, it is a justified out-of-map edit of the steps class; list it in the review notes.
- `pack-doctor validate` gives the same answer for the same planted files: `AntiPatternFileCheckTest` (new, in
  `pack-validation`, validate mode over a copied example pack, as `PackCheckTest` does), asserting the same codes,
  pointers and details as the scenarios (ADR-0075 decision 9). The missing `severity` and missing `bad_example` cases
  are two of #154's four refusal tests; they answer `artifact_schema_error` (`Wording.codeFor`), a contract code
  (`CharterRefusalCode.yaml:419-421`). Do not mint a code.
- Change fragment `contracts/charter/changes/2026-11-21-anti-pattern-file-kind.md` under `### Breaking`: whole-pack
  registration now holds and judges `anti_patterns/<id>.anti-pattern.yaml`, which it warned `file_not_held` before,
  so an invalid one is refused; core's seam needs no regeneration (no schema of the charter contract changes).
- Docs (live text only, C-004), written for the model this PR ends with (R-r: the file is the only form; a
  file-less node and an inline entry are refused):
  - `docs/architecture/logical-model.md:324-325` (the anti-pattern is "never a standalone file");
  - `docs/context/domain.md:110` ("A node that good artifacts `reject`; it has no descriptor file");
  - the glossary definition at `site/.contextive/definitions.yaml:35` ("until it is built an anti-pattern is only a
    graph node or an inline entry"), and its generated copy `docs/glossary/charter/README.md:70`. Regenerate the copy
    with the glossary tool if it is available; otherwise edit both to the same text.
  - `docs/architecture/descriptors.md` is WP12's (it describes both the file and the dropped field).

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
- [ ] All 18 example inline anti-patterns are files with a node and a `rejects` edge; no example styleguide or
      procedure carries `anti_patterns`; every severity is written; every `bad_example` is real text.
- [ ] Registration and `pack-doctor validate` hold a valid file and refuse an invalid one with the same code.
- [ ] A `### Breaking` fragment; the live docs and the glossary no longer say the kind has no file.
- [ ] Every behavior change had a test that was red first (NFR-001).

## Stop conditions
- Holding the file beside its node makes any rule refuse a pack (a contest, a duplicate holding, a stem rule):
  stop and report. D12 rests on node and file coexisting as they do for paradigms and styleguides.
- A `rejects` edge from a procedure is refused (`relationship_endpoint_not_allowed`): stop. The endpoint table allows
  it (`EndpointTable.java:19-25, 69`); a refusal means the plan's evidence is wrong.
- The example packs cannot drop an inline block without a test outside your owned files going red for a reason other
  than the expected re-record (for example a test that pins an example styleguide's anti-pattern count): stop and
  report the test; do not exempt it.
- A test can only pass by excluding a field from a round-trip or parity check, or by exempting a fixture: stop and
  report (charter).

## Risks and reviewer guidance
- A reviewer checks that no enum of kinds changed (the kind was already in every list), that the binding is appended
  last, that `CompositionFactsBuilder` is untouched, and reads each written `bad_example` and severity.
- Out-of-map edits allowed and to be listed: `built-in/pack-manifest.yaml` (WP04's), the three built-in styleguides
  (inside WP03's glob), and a missing scenario step (`PackRegistrationSteps`, WP03's).
- Open-packs' Python checker treats `anti_pattern` as graph-only (`validate_pack_schemas.py:84-88`, 447, 1181); the
  consequence (with WP11's and WP12's refusals) is drafted in `research/open-packs-TBD-anti-pattern-files.md`, not
  filed.
