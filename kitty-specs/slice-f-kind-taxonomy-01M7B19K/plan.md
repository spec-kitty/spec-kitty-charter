# Implementation Plan: Slice F: the kind taxonomy and the mission-type schema

**Branch**: `kitty/mission-slice-f-kind-taxonomy-01M7B19K` → `slice-f-537-kind-taxonomy` → `main` (one PR) | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/slice-f-kind-taxonomy-01M7B19K/spec.md`

**Branch note.** `setup-plan --json` reports `kitty/mission-slice-f-kind-taxonomy-01M7B19K` as its own target, with
the `single_branch` topology. `meta.json` names `slice-f-537-kind-taxonomy` as the target (`target_branch`,
`create_return_branch`), and that is the landing branch. The pull request runs from it into `main`.

**Implementation gate (operator, 2026-11-13).** No work package starts until slice C (#467) has merged, or this
branch is rebased onto slice C's rebased branch. Slice C changes the same classes (research R8).

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
| Adversarial squad at plan and before merge | A post-plan squad runs on this plan (`research/squad-post-plan.md`), and its findings are folded before tasks. A pre-merge squad runs on the aggregate diff. |
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
    RelationType.java        # endpoint table, retired values gone; absorbs Relation
    Relation.java            # removed (merged into RelationType)
    Relationship.java        # per-relation typed factories
    NodeRef.java / kinds/    # NodeRef<K>, sealed kind markers and groups (new)
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

The work packages run in this order. Each is test-first, and each leaves `verifyAll` green.

### IC-01: Kernel relation model (FR-012, FR-013, FR-017)

- **Retire the relations.** Remove `IMPACTS`, `REPLACES` and `DELEGATES_TO`.
  - Merge `kernel.domain.Relation` into `RelationType`: one relation enum, with one source per fact.
  - Remove the retired values from the contract's `Relation.yaml` and from the graph schemas' relation enums.
  - The `impacts` strength on `DrgEdge` goes, together with its rule at `PackPolicy.java:846`.
  - Tension (`Tension`, written `in_tension_with`) stays as it is.
- **Endpoint table.** Each `RelationType` constant holds its allowed `{source, target}` kind pairs, as data
  (research R6). `NodeKind` gains no new facts; the kind groups are sets.
- **Typing (`DM-01M7B3QB…`).**
  - Sealed marker interfaces, one per kind, plus kind groups as sealed interfaces. For example, `Guidance` permits
    the six guidance kinds, and `Activatable` permits the activatable kinds.
  - `NodeRef<K extends Kind>` is a typed node reference.
  - `Relationship` gains static factories per relation, typed by the allowed source and target groups, for example
    `requires(NodeRef<? extends RequiresSource>, NodeRef<? extends RequiresTarget>)`.
  - A relation whose rows are "the same kind" (`overrides`, same-kind `enhances`) uses one type parameter:
    `<K extends Activatable> overrides(NodeRef<K>, NodeRef<K>)`.
- **The checked bridge.** `EndpointRule.bind(RelationType, Urn source, Urn target)` returns either a typed
  `Relationship` or a `Fault` (the code and both kinds). It is the only path from untyped URNs to a relationship, and
  the readers and writers call it. A test asserts that the bridge and the factories agree on every
  (relation, source, target) triple. A positive control skews one row and must fail.
- **Gates.** `DOMAIN_IS_PURE` stays green. A source scan, extending `RetiredMissionFieldNamesTest`'s pattern, keeps
  the retired relation tokens out of the main sources (NFR-003).

### IC-02: Endpoint refusal at read and write (FR-014, FR-015, FR-016, FR-018)

- **Read.**
  - `PackPolicy.endpoints` calls `EndpointRule.bind`. A fault becomes a blocking finding at the edge's
    `/edges/<index>`, and `pack_unreadable` on a composing read.
  - The load path calls it too: `DomainRepositoryService.typedEdge` refuses where it silently dropped an edge before.
- **Write.** `RelationshipRegistrationService` and `RelationshipWire` answer `422 validation_failed` with the
  violation `relationship_endpoint_not_allowed` at `/relation`. The message names the relation and both kinds.
- **Slice C's rule.** `OverrideSourceRule` becomes the `overrides` row's first check and keeps
  `overrides_source_not_activatable` (FR-015). It is checked before the same-kind check, so the order is
  deterministic.
- **Violation code.** `relationship_endpoint_not_allowed` follows slice C's precedent: it is a violation code inside
  `validation_failed`, not a `RefusalCode`. If the violation-code registry (in slice C's shape) needs an entry, it
  gets one.
- **Invalid packs, one per refusal.** One per relation family, plus:
  - #350 (`procedure -requires-> action`);
  - `overrides` between two kinds;
  - a retired relation.

  Scenarios go in `pack-write-refusals.feature` and the read-refusal feature.
- **Migration.** Charter's example packs drop the 2 `impacts` edges, and `suggests styleguide → anti_pattern`
  becomes `rejects`. The error fixtures that would gain a second fault are rewritten (research R6).
- **After #554.** If #554 has landed by then, the read check is one rule in its graph ruleset (C-004).

### IC-03: Substeps on the mission step (FR-004, FR-005)

- **Schema.** `mission-type.schema.json` `Step` gains `substeps`: an array (`minItems: 1` when present) of closed
  objects `{id, description, command?, inputs?, delegates_to?, guidance?}`. Their shapes are carried over from
  `mission-step-contract.schema.json`'s step, input and delegation definitions. Ids are unique within a step.
- **Domain.** `MissionStep` gains `List<Substep>`. `Substep` and `SubstepDelegation(ArtifactKind kind, List<id>
  candidates)` are value objects in `missions.domain`, guarded by the kernel rules.
- **Codecs.** The mission-type YAML codec and its round trip carry the new field (ADR-0036).
- **Load rules.** Every candidate resolves to an artifact of `kind` in the closure. The step's
  `action:<type>/<step>` node has a `scope` edge to it. A pack that breaks either is refused with
  `substep_delegation_unresolved` or `substep_delegation_unscoped`. Both are `PackFindingCode`s on the existing
  `drg`/schema path; the names are confirmed by the post-plan squad.
- **Reads.** `MissionStep.yaml` (the contract) gains `substeps`, with `delegatesTo.candidates` linked to each
  artifact read. `getPackMissionStep` and `getEffectiveMissionStep` return them.

### IC-04: Retire `mission_step_contract` (FR-006, FR-007, FR-008)

- **Model and contract.** Delete the 21 kind-only Java files, and edit the mentioning files (research footprint).
  - The kind's value goes from `ArtifactKind`, `NodeKind` and the contract's kind enums.
  - The operation, its six schemas, `StepContractId`, the response, the example, and the refusal code
    `mission_step_contract_not_found` all go.
  - So do the `step-contract` link relation, `activatedMissionStepContracts` and the `selected_`/`deactivated_`
    members.
  - `ActivationConfig.schema.json` and corpus files 06 and 07 lose the kind.
- **Pack schemas.** `mission-step-contract.schema.json` is removed. The kind leaves the `graph-shard`,
  `org-fragment`, `pack-manifest`, `policy`, `preset` and `charter` schemas. `file-kinds.yaml` moves
  `**/*.step-contract.yaml` and `**/mission_step_contract.graph.yaml` to `retired:`, with a reason.
- **Migration.**
  - The `built-in` example pack's 5 contracts become substeps of their steps. The `review` gate becomes the
    foundation-equivalent lifecycle guard on `in_progress -> for_review`, if the example pack has that lifecycle;
    otherwise it is dropped, with a note.
  - The manifest, policy and preset entries go.
  - The planted and digest fixtures migrate, or become retired-file refusals.
- **Kind removal.** `WireTokenParityTest` needs no exemption, since the kind leaves both sides. A source scan proves
  it is gone (NFR-003).
- **#533.** It closes with the deleted route as evidence.

### IC-05: Mission-type rules and `template_set` (FR-009, FR-010, FR-011)

- **Complete `requires` edges.** Each step and on-demand step of a mission type needs an authored
  `mission_type:<t> -requires-> action:<t>/<s>` edge. A missing one is refused at load and at registration with
  `mission_step_unlinked`, naming the type and the step. Charter's example packs gain any missing edges.
- **Invalid packs.** One for an authored `action_sequence`, and one for an authored `template_set`, each refused as
  an unknown field. Plus one `@contract` scenario.
- **`template_set`.** It is removed from `charter.schema.json` (`CharterCatalog`, `GovernanceCharterConfig`) and
  from its fixtures (`ProjectCharterYamlSchemaTest`, `planted/charter-valid`).
- **Wording.** `RelationType.ENHANCES`'s javadoc drops "action sequence".

### IC-06: R18, the kind taxonomy read (FR-001, FR-002, FR-003)

- **Contract.** `GET /offering/kinds` (`getKindTaxonomy`, tag `Offering`), with the schemas `KindTaxonomy`,
  `KindRow`, `RelationRow` and `EndpointPair` (draft in `contracts/slice-f.openapi.yaml`). It is a static read:
  - the `ETag` is the contract version;
  - it takes `If-None-Match` and answers `304`;
  - it needs the worktree token, as every operation does;
  - it carries no fingerprint header, because it reads no pack.
- **Body.** Built from `NodeKind`, `ArtifactKind` and `RelationType`. A `kinds` row links to
  `/packs/{packId}/artifacts?kind=` only through templated link guidance, not as a concrete link, because the read
  names no pack. The post-plan squad confirms this.
- **Parity.** A case in `WireTokenParityTest` (or `VocabularyEqualityTest`) compares the served table with the enums
  and with `file-kinds.yaml`, with a skewed positive control.
- **Docs.** `service-contract.md` rows R18 and "the other reads" move to Decided/Built, and the "deferred to the
  merge minor" text goes. `resolution-model.md:100-104` points to the read.

### IC-07: Records, changelog and open-packs drafts (FR-019, FR-020)

- **The new ADR**, "Kind taxonomy and step authority" (number chosen when written). It holds:
  - R18's shape;
  - substeps on the step and the retired kind;
  - complete type-to-step edges;
  - the endpoint table;
  - the retired relations, including the note that `replaces` was sometimes used to mean `overrides`;
  - `template_set` dropped.
- **Short amendment notes** pointing at it: ADR-0044, ADR-0050, ADR-0054 (the gate's move), ADR-0064 (decision 13's
  table lives in the new ADR), ADR-0074 (decision 12's #533 gap closed), ADR-0075 decision 2
  (`MissionStepContractIdentifier`), and the other ADRs that name the kind.
- **Changelog.** One `contracts/charter/changes/<date>-kind-taxonomy-and-step-authority-breaking.md` fragment under
  `### Breaking`, plus `### Added` for the R18 read. The packs contract change is noted for #559.
- **Open-packs drafts**, in `research/open-packs-drafts/`, each with an `open-packs#TBD-<slug>` placeholder:
  1. fold the 28 step contracts into substeps, and remove the files and the 2 graph fragments;
  2. add the 72 missing `scope` edges, and the missing `requires` edges for steps;
  3. move the `review` gate to the foundation lifecycle guard;
  4. retire the three relations (none in use today);
  5. update `validate_pack_schemas.py` and the docs.
- **Docs.** The diagrams and pages listed in `research/footprint-mission-step-contract.md`, and the glossary
  (`substep`).

## Risks

| Risk | Mitigation |
| --- | --- |
| Rebasing onto slice C collides in about 25 shared files | Implement only on top of C (C-003). IC-02 and IC-04 start from C's versions. |
| #554 lands first and moves `PackPolicy`'s graph binding | The bridge (`EndpointRule`) is the single call. #554 calls it from its graph rule (C-004). |
| The kind markers ripple through the packs and activations modules | Only the factories and the bridge are typed. Existing code that holds a `Relationship` keeps doing so, and only construction changes. |
| Open-packs breaks until it applies the drafts | The drift watch shows it (#509). Open-packs proves its content against a pinned charter release (ADR-0070), so `main` is not blocked. |
| The substep `command` and `inputs` fields carry core's CLI surface into the model | The operator ruled to keep all fields (`DM-01M7B1YH…`). Recorded in the ADR as runtime data, like delegations (ADR-0064 decision 11). |
| #106 renames `glossary_pack` while F is in flight | The table follows `ArtifactKind`. Whichever lands second rebases. |
