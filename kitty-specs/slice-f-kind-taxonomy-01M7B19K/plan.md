# Implementation Plan: Slice F: the kind taxonomy and the mission-type schema

**Branch**: `kitty/mission-slice-f-kind-taxonomy-01M7B19K` → `slice-f-537-kind-taxonomy` → `main` (one PR) | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/slice-f-kind-taxonomy-01M7B19K/spec.md`

**Branch note.** `setup-plan --json` reports `kitty/mission-slice-f-kind-taxonomy-01M7B19K` as its own target, with
the `single_branch` topology. `meta.json` names `slice-f-537-kind-taxonomy` as the target (`target_branch`,
`create_return_branch`), and that is the landing branch. The pull request runs from it into `main`.

**Implementation gate (operator, 2026-11-13).** Implementation starts only after slice C (#467) has merged. C
landed on `main` as PR #566 (`cdc62e1cf`). This branch merged `main` at `ffb6b4929` without a rebase, so nothing
pushed is force-pushed. This mission still stops before implementation, until the operator starts it.

## Summary

One model change, delivered in one pull request:

- **R18 (#112).** `GET /offering/kinds` serves every kind and every relation, with each relation's allowed endpoint
  kinds.
- **Steps are the single authoring surface (#514, #520).**
  - A mission-type step gains a flat, ordered `substeps` list that holds everything a step contract held.
  - `mission_step_contract` is retired outright.
  - Every step must have its `mission_type -requires-> action` edge.
  - `template_set` leaves the charter schema.
- **Endpoint kinds (#515, #350).**
  - Each relation names the kinds it may join.
  - Relationship constructors are typed by kind marker types, so a disallowed combination does not compile.
  - A disallowed edge is refused at read and at write.
  - Slice C's `OverrideSourceRule` (#447) folds in and keeps its code.
- **Retired relations.** `impacts`, `replaces` and `delegates_to` are retired.

The decisions are recorded in one new ADR. The ADRs they touch get short notes that point to it.

Rulings: specify decisions `DM-01M7B1YE…` to `DM-01M7B1Z3…` (spec, Intent Summary) and plan decisions
`DM-01M7B3Q8R1C6J89MM3YWSMF8XZ` (one new ADR), `DM-01M7B3QBD0W0KTBHPHX55RB33K` (kind markers and factories) and
`DM-01M7B3QE377FQXN0R02WNC829W` (one PR with ordered work packages).

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain). OpenAPI 3.1 contract in `contracts/charter/`. JSON Schema 2020-12
pack schemas in `contracts/packs/schemas/`.
**Primary Dependencies**: no new dependency (NFR-004). The work uses Spring Boot web, the generated
`ai.speckitty.charter.api` interfaces, Jackson/SnakeYAML codecs, the vendored `kernel.validation`, and Cucumber for
the contract scenarios, all of which are already in the build.
**Storage**: pack files on disk, through the existing `FilesystemPackStore` / `WholePackStore`. No new storage.
**Testing**:
- Unit tests per module: `:kernel:test`, `:packs:test`, `:missions:test`, `:missions-edge:test` and
  `:activations:test`.
- Planted and load-fixture invalid packs.
- Contract scenarios, run by `:deployable:test`.
- Parity tests: `WireTokenParityTest`, `VocabularyEqualityTest`, `RelationSetParityTest` and `KindBindingsTest`.
- ArchUnit (`HexagonalArchitectureTest`).
- The whole gate, `./gradlew verifyAll --max-workers=1` in the foreground, one run at a time.
**Target Platform**: the charter service (Linux container), and contract consumers through the generated client.
**Project Type**: a multi-module service (`service/*`) with a contract tree and pack schemas.
**Performance Goals**: no new budget. The endpoint check is an O(1) table lookup per edge, made where edges are
already bound. The taxonomy read is static.
**Constraints**:
- The charter's model is canonical: no legacy reader, and no core-first ordering.
- Slice C comes first.
- The read check stays where it is until #554 lands.
- The 0.x contract may break, with one `### Breaking` fragment.
**Scale/Scope**:
- 21 Java files removed, and about 70 edited, for the step contract.
- 1 new operation, and 1 operation removed.
- About 7 contract schemas removed and 3 added.
- 3 relations retired.
- 1 pack schema removed, 2 changed and 1 field dropped from `charter.schema.json`.
- About 10 new invalid example packs.

## Charter Check

| Gate | How this plan meets it |
| --- | --- |
| Divergence from the charter's own model | The shapes come from the charter's model. Core's org facts are not copied (R2). The step holds substeps per the operator's ruling, not core's `MissionStep`. |
| Breaking the contract | It breaks inside 0.x, as ADR-0022 allows. One dated `### Breaking` fragment lists every removal and every new refusal (FR-019). |
| Canonical model, no legacy | No deprecation window. There is no reader for `*.step-contract.yaml`, which is a retired file kind. Retired relations are unknown relations. |
| Tests use only canonical shapes | Fixtures and example packs migrate (FR-008, FR-018). Old shapes appear only in invalid packs for their refusal. |
| Adversarial squad at plan and before merge | The post-plan squad ran three lenses and found 4 blockers and 14 majors, all folded (`research/squad-post-plan.md`). A pre-merge squad runs on the aggregate diff. |
| Gates before merge | `verifyAll` on Java 25 with the whole-pack round trip, the docs checks, and the switch-gate register. |
| Issue rules | `Closes #112 #514 #520 #515 #533 #350`, and #94 and #537 are ticked. #513 is narrowed with a comment. #447 is closed by slice C. Squad minors are folded or recorded as "Not done", and never filed. Pack content goes to open-packs as drafts. |
| Orchestration | Open-packs drafts carry `open-packs#TBD-<slug>`. The ADR number is chosen when the ADR is written, against `main` and the open PRs (0076 is free today). |

Result: pass. C-002 overrides #514's text ("behind core #3420/#3419"), as charter.md requires.

## Project Structure

### Documentation (this mission)

```text
kitty-specs/slice-f-kind-taxonomy-01M7B19K/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/slice-f.openapi.yaml       # draft of the new and changed contract shapes
├── research/                            # evidence, squads, census, open-packs drafts (WP07)
├── decisions/                           # the 11 Decision Moments
└── checklists/requirements.md
```

### Source Code (repository root)

```text
service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/
    RelationType.java        # endpoint table (the single source), retired values gone
    Relation.java            # removed (0 main callers)
    Relationship.java        # one typed factory per table rectangle; raw constructor package-private
    KindRef.java / kinds/    # KindRef<K>, sealed kind markers and groups (new)
    EndpointRule.java        # the checked bridge (new)
    ArtifactKind.java, NodeKind.java, RefusalCode.java
service/packs/src/main/java/ai/speckitty/charter/packs/
    domain/PackPolicy.java, domain/OverrideSourceRule.java (from slice C; folded in)
    domain/MissionStepContract*.java, ContractStep, GateBinding, StepDelegation, StepInput   # removed
    application/RelationshipRegistrationService.java, ImpactRelations.java
    infra/api/RelationshipWire.java, ArtifactDtos, ArtifactJson, PresetMapper
    infra/storage/DomainRepositoryService.java, KindBindings.java, MissionStepContract{Binding,Dso,Yaml}.java (removed)
service/missions/src/main/java/ai/speckitty/charter/missions/domain/
    MissionStep.java, Substep.java (new), SubstepDelegation.java (new), MissionType.java
service/missions-edge/…/StepContracts, PacksStepContracts, EffectiveStepContract*, StepContractView (removed)
    MissionBodies, MissionLinks, MissionRelation, EffectiveMissionTypesController
service/activations/…/EffectiveStepContractWinner (removed), EffectiveReadService
service/charter-reports or packs/infra/api/KindTaxonomyController (new; module chosen in WP06)
contracts/charter/{openapi.yaml, paths/, schemas/, examples/, scenarios/, changes/}
contracts/packs/schemas/{mission-type, graph-shard, org-fragment, charter, …}.schema.json, file-kinds.yaml
service/packs/src/testFixtures/resources/example-packs/**   # migrated
service/packs/src/test/resources/{planted,load-fixtures}/** # new invalid packs, rewritten error fixtures
docs/adr/00NN-kind-taxonomy-and-step-authority.md (new), docs/api/service-contract.md
```

## Implementation Concern Map

The work packages run in the order below. **The order was revised after the post-plan squad**
(`research/squad-post-plan.md`): content moves first, and the relation model and its refusal land together. Each
work package is test-first, and each must leave `verifyAll` green when it lands.

Slice C is on `main` (`cdc62e1cf`, PR #566). This branch merged it at `ffb6b4929`. Every reference below is to `main`
at that commit.

### IC-00: Content first, valid under today's model

Migrate everything the new rules would refuse, while the old rules still accept it. Then each rule that follows
lands green.

- **Example packs** (`service/packs/src/testFixtures/resources/example-packs/`):
  - drop the 2 `impacts` edges;
  - change `suggests styleguide → anti_pattern` to `rejects`;
  - add the 8 missing action nodes and their `mission_type -requires-> action` edges (discovery, analyze, accept,
    charter, tasks-finalize, tasks-outline, tasks-packages, retrospect).
- **Contract scenarios and test fixtures.**
  - Rewrite the about 34 inline edges that the table refuses to allowed forms, keeping what each scenario tests:
    `enhances tactic→directive` ×17, `requires action→directive` ×7, `overrides action→directive` ×4, and the
    singletons the squad listed.
  - Give every inline mission type in the about 13 `.feature` files, and in the Java fixtures, its `requires` edges.
  - Re-run the census over `contracts/charter/scenarios/**` and every test fixture; the work package's acceptance
    is that 0 valid-path edges are refused.
- **Review gate in the example pack.**
  - Add `guards/spec-kitty-pre-review.guard.yaml`, with `implemented_by: builtin:spec-kitty-pre-review` and
    `on_indeterminate: pass` (this replaces `fail_open`; `provenance` is dropped, per ADR-0054 decision 12).
  - Add the guard entry on `work-package.lifecycle.yaml`'s `in_progress → for_review` transition, which already
    exists.
- **Delegations in the example pack.**
  - Point the step contracts' 31 unresolved candidates at artifacts the pack holds, or add the artifacts.
  - Add the `scope` edges from each action to each candidate.
- **Manifest.** Regenerate the `pack-manifest.yaml` content hashes in every work package that edits example files.

### IC-01: Relation model and endpoint refusal (FR-012 to FR-018), one work package

**Retire the relations.**
- Retire `IMPACTS`, `REPLACES` and `DELEGATES_TO`.
- `kernel.domain.Relation` has 0 callers in main code, so it is deleted, not merged. `Relation.yaml`'s `x-source`
  is repointed to `RelationType`, and gains `implemented_by`.
- The `impacts` strength goes from `DrgEdge`, `graph-shard.schema.json:46`, `org-fragment.schema.json:46`,
  `PackPolicy.java:846` and `RelationshipMerge.java:32`.
- The `shard-impacts-*` and `c12-*impacts*` fixtures are deleted, apart from one retired-relation refusal fixture.
  Their test classes are updated: `ExamplePacksComposeTest`, `DomainRoundTripTest`, `GraphShardWriterTest`,
  `PackPolicyGraphTest` and the others the squad listed.

**Typing: one factory per rectangle of the table** (squad B1). A row that is a union of rectangles gets one named
factory per rectangle:
- `requires`, `requiresAsset` (asset → asset) and `requiresStep` (mission_type → action);
- `enhancesStep` (step_enhancement → action) and `enhancesSame`.

Overloads would erase to the same signature, so they are not used. Supporting types:
- `KindRef<K extends Kind>`, named so that it does not clash with `activations.domain.NodeRef`. Its constructor is
  private; it is built only through per-kind leaf factories such as `KindRef.directive(id)`, which returns
  `KindRef<Directive>`. So no `KindRef<Guidance>` can exist, and same-kind relations such as
  `<K extends Activatable> overrides(KindRef<K>, KindRef<K>)` hold by invariance.
- Each factory maps to its row in the ADR.

**The bridge holds the guarantee** (squad M1).
- `Relationship`'s raw constructor becomes package-private. `EndpointRule.bind` builds through it after a lookup in
  the table.
- The 7 main call sites that build a `Relationship` move to `bind` or to typed factories:
  - `EdgeIndex:199/422` (a new `withToPack`);
  - `WholePackStore:693`;
  - `Guard:144`, through `implementedBy(KindRef<Guard>, KindRef<Asset>)`;
  - `DomainRepositoryService:633`;
  - `PackPolicy:776`;
  - `RelationshipWire:88`.
- An ArchUnit rule allows only `kernel.domain` to call the constructor.
- About 32 test files are updated.

**One source for the table** (squad M2).
- `RelationType`'s endpoint data is the table.
- The agreement test reflects over each factory's generic bounds, walking `getPermittedSubclasses()` recursively,
  and asserts that the union of the factories' products equals each row exactly. A skewed row is the positive
  control.

**Tension** (squad M9). `EndpointRule` gets a `bindTension` overload for `Tension` (G ↔ G).

**Fault order.** The order is fixed and every rule is tested against it:
1. `StepEnhancementRules`, which exists today;
2. `OverrideSourceRule`, from slice C;
3. the endpoint table.

Fixtures are rewritten so that each exercises one fault (`prompt-fragment-refusals.feature`).

**Where the checks run.**
- Read:
  - `PackPolicy.endpoints`, with `PackFindingCode.RELATIONSHIP_ENDPOINT_NOT_ALLOWED` mapped through
    `PackPolicy.violationKind`, as C does;
  - the load path, `DomainRepositoryService.typedEdge`, which now refuses where it used to drop silently.
  - The pointer is the graph document, as C's is.
- Write:
  - `RelationshipRegistrationService` answers `422 validation_failed`, with the violation at `/type`, the
    relation's member in `{from, to, type}`. C's fault keeps `/from`.

**Code registration** (squad contract finding 4). Every new code is registered the way C's is:
- a `PackFindingCode`;
- the `violationKind` mapping;
- a listing in `CharterRefusalCode.yaml`'s `validation_failed` description and its `x-refusal` text.

`ExampleCoverageTest` is extended so that such violation codes need an example. This covers
`relationship_endpoint_not_allowed`, `substep_delegation_unresolved`, `substep_delegation_unscoped` and
`mission_step_unlinked`.

**Rewrite C's scenario.** `override-source-kind.feature` says an `overrides` edge from an activatable kind is fine
"whatever its target". The same-kind rule now contradicts that, so the scenario is rewritten (FR-015).

**Invalid packs.** One per relation family, plus:
- #350 (`procedure -requires-> action`);
- `overrides` between two kinds;
- a retired relation.

**After #554.** If #554 has landed by then, the read check becomes one rule in its graph ruleset (C-004).

### IC-02: Mission-type rules and `template_set` (FR-009, FR-010, FR-011)

- **`mission_step_unlinked`, per pack.** Every step and on-demand step needs its authored `requires` edge. The
  content was already added in IC-00.
- **Compose-time check on the winning type** (squad M6). A `requires` edge that `EdgeIndex` repointed to a step the
  winning type lacks is refused, and so is an edge to a step that does not exist.
- **Invalid packs** for an authored `action_sequence` and an authored `template_set`, plus a scenario.
- **`template_set`** goes from `charter.schema.json` and its fixtures.
- **Javadoc.** `RelationType.ENHANCES` no longer mentions "action sequence".

### IC-03: Substeps on the step, and retire `mission_step_contract` (FR-004 to FR-008), one work package

The two land together. The example migration, the new schema properties and `ExamplePackCoverageTest` must change
in one go.

**Schema and domain.**
- `Step.substeps`, as in the data model.
- `delegates_to.kind` is limited to the `scope` targets from `action`: G or agent_profile (squad M4).
- Candidates follow the grammar `[<pack>::]<id>`. The kind is given once, by `kind` (squad M8).
- `PackFindingCode.DUPLICATE_STEP_ID` is retargeted to substep id uniqueness.

**Load rules** (squad M3).
- `PackPolicy` (`packs.domain`) cannot depend on `missions.domain`. So `PackContents` gains plain-data
  `MissionTypeFacts`, and the in-pack check (bare candidates and their `scope` edges) runs over those facts.
- A qualified candidate is checked at compose and registration through the closure (`RelationshipTargetCheck`, and
  `ClosureWalk` after #556).
- FR-005's "at load" covers in-pack candidates. A cross-pack candidate is refused at compose.

**Reads.**
- `MissionStep.yaml` gains `substeps`.
- The delegation components are kept, renamed `MissionSubstepDelegation` and `MissionSubstepDelegationCandidate`.
  They keep `id`, `$ref Links.yaml`, and `artifact` / `effective-artifact`, with empty links for an unserved kind.
  Their `x-source` is repointed (squad contract finding 6).

**Retire the kind.**
- The removal list is the research footprint plus the squad's additions:
  - `Problem.yaml:29`, `ArtifactSummary`, `SelectedArtifacts`, `DeactivatedArtifacts`, `ArtifactNotFound`,
    `parameters/ArtifactKind`;
  - both `*_kind_artifactId` and `*_references` paths;
  - `ActivationSkippedReport.each-bucket`, `MissionStep.specify`;
  - the `step-contract` link relation in `ruleset.yaml` and in the hypermedia table;
  - the `kind_not_found` prose;
  - the `file-kinds.yaml` `node_kinds`, `artifact_kinds` and `fragment_kinds` entries;
  - the scenario files.
- Slice C's step-contract code on `main` goes too: `ActivationMissionTypes#stepContract`, the
  `EffectiveReadService` winner and placement code, and the D11 scenarios.
- `removed-routes.feature` pins each retired route's answer: `404 kind_not_found` on the item routes, and `400` on
  `?kind=`.
- The `getEffectiveMissionStepContract` switch-gate key is removed, and the removal is listed as Breaking.

**Scans** (NFR-003, extended). One scan covers the main sources. Another covers `contracts/`, excluding
`changes/`, `CHANGELOG.md` and the `retired:` block of `file-kinds`.

### IC-04: R18, the kind taxonomy read (FR-001 to FR-003)

**Headers and caching** (squad contract B1 and B2).
- `GET /offering/kinds` (`getKindTaxonomy`). Its `ETag` is the sha256 of the served body, in the pattern
  `ContractETag.yaml` sets, and it takes `If-None-Match` and answers `NotModified`.
- It declares `Charter-Contract-Version` and `Charter-Worktree` on its 200 and 304, as `paths/openapi.yaml:15-57`
  does.
- The read has no source fingerprint, so `/offering/kinds` is added to the exceptions of the lint rules
  `fingerprint-on-verified-reads` and `fingerprint-only-on-verified-reads`, and `rest-resource-conventions.md` is
  updated to match.

**Links** (squad contract finding 7).
- `Offering` and the root gain a `kinds` link, and the hypermedia table marks `kinds` as in the contract.
- Each served kind's row carries a templated `artifacts` link, `/api/v1/packs/{packId}/artifacts?kind=<kind>` with
  `templated: true`.
- The 200 has an example.

**Schemas.**
- `KindRow.kind` references `NodeKind.yaml`, and `RelationRow.relation` references `Relation.yaml`.
- The tension row is `in_tension_with`, with `heldAs: tension`; every other row is `heldAs: relationship`.
- Every property has a `description` and an `x-source`.
- The `Offering` tag's description is widened to cover the model's taxonomy.

**Gates.**
- A switch-gate entry.
- 401 and 403 rows in `every-operation.feature`, and the 200 and 304 scenarios.
- A parity case in `WireTokenParityTest` (or `VocabularyEqualityTest`), with a positive control.

**Docs.** `service-contract.md`, and `resolution-model.md:100-104`.

### IC-05: Records, changelog and open-packs drafts (FR-019, FR-020)

**The new ADR, and notes on the touched ADRs.** As before:
- the decisions;
- the note that `replaces` was used to mean `overrides`;
- the M10 consequence: new `scope` edges put each delegation candidate in force for the step's effective context
  (`ActionContextWalk`).

**Changelog.** One `### Breaking` fragment, which includes the switch-gate key removal, and `### Added` for the
read.

**Open-packs drafts** (corrected sizes, squad M6):
1. Fold the 28 step contracts into substeps, then remove the files and the 2 fragments. Writing's 34 bare
   candidates, which name foundation artifacts, are written qualified as `foundation::<id>`.
2. Add 26 `requires` edges and 23 action nodes: cave-dev 8, qa 7, software-dev 8, research 2, documentation 1.
3. Add 80 `scope` edges (79 distinct) from each step's action.
4. Author foundation's work-package lifecycle and the `spec-kitty-pre-review` guard, then attach the gate. Open-packs
   has no lifecycle or guard artifact today.
5. Update `validate_pack_schemas.py` and the docs.

**Ordering note.** Open-packs can raise its pin only after F is merged and released (ADR-0070).

## Risks

| Risk | Mitigation |
| --- | --- |
| Slice C's shared files | C is on `main` and is merged into this branch at `ffb6b4929`. The plan's references are to `main`. |
| A rule lands before its content and turns `verifyAll` red | IC-00 migrates the content under today's rules. Each later work package re-runs the census over the scenarios and fixtures. |
| The new `scope` edges change what the effective step context returns (`ActionContextWalk` walks `SCOPE`) | This is a ruled consequence. It is recorded in the ADR and covered by an effective-context scenario. |
| #554 lands first and moves `PackPolicy`'s graph binding | The bridge (`EndpointRule`) is the single call. #554 calls it from its graph rule (C-004). |
| The kind markers ripple through the packs and activations modules | Only the factories and the bridge are typed. Existing code that holds a `Relationship` keeps doing so, and only construction changes. |
| Open-packs breaks until it applies the drafts | The drift watch shows it (#509). Open-packs proves its content against a pinned charter release (ADR-0070), so `main` is not blocked. |
| The substep `command` and `inputs` fields carry core's CLI surface into the model | The operator ruled to keep all fields (`DM-01M7B1YH…`). Recorded in the ADR as runtime data, like delegations (ADR-0064 decision 11). |
| #106 renames `glossary_pack` while F is in flight | The table follows `ArtifactKind`. Whichever lands second rebases. |
